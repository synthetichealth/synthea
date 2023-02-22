package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;

import org.mitre.synthea.export.rif.enrollment.PartCContractHistory;
import org.mitre.synthea.export.rif.enrollment.PartDContractHistory;
import org.mitre.synthea.export.rif.identifiers.FixedLengthIdentifier;
import org.mitre.synthea.export.rif.identifiers.HICN;
import org.mitre.synthea.export.rif.identifiers.MBI;
import org.mitre.synthea.export.rif.identifiers.PartCContractID;
import org.mitre.synthea.export.rif.identifiers.PartDContractID;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planeligibility.QualifyingConditionCodesEligibility;

/**
 * Export beneficiary and beneficiary history files.
 */
public class BeneficiaryExporter extends RIFExporter {

  public static final AtomicLong nextBeneId = new AtomicLong(
          Config.getAsLong("exporter.bfd.bene_id_start", -1));
  public static final AtomicReference<HICN> nextHicn = new AtomicReference<>(
          HICN.parse(Config.get("exporter.bfd.hicn_start", "T00000000A")));
  protected static final AtomicReference<MBI> nextMbi = new AtomicReference<>(
          MBI.parse(Config.get("exporter.bfd.mbi_start", "1S00-A00-AA00")));
  private static final double PART_B_ENROLLEE_PERCENT = 90.0;
  private static final String ESRD_CODE = "N18.6";
  private static final String ESRD_SNOMED = "46177005";
  private static final String CKD4_SNOMED = "431857002";
  private static final QualifyingConditionCodesEligibility ssd =
      new QualifyingConditionCodesEligibility(
          "payers/eligibility_input_files/ssd_eligibility.csv");

  static String getBB2SexCode(String sex) {
    switch (sex) {
      case "M":
        return "1";
      case "F":
        return "2";
      default:
        return "0";
    }
  }

  /**
   * Construct an exporter for Beneficiary and BeneficiaryHostory file.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public BeneficiaryExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export a beneficiary details for single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return the beneficiary ID
   * @throws IOException if something goes wrong
   */
  public String export(Person person, long startTime, long stopTime) throws IOException {
    String beneIdStr = Long.toString(nextBeneId.getAndDecrement());
    person.attributes.put(RIFExporter.BB2_BENE_ID, beneIdStr);
    String hicId = FixedLengthIdentifier.getAndUpdateId(nextHicn);
    person.attributes.put(RIFExporter.BB2_HIC_ID, hicId);
    String mbiStr = FixedLengthIdentifier.getAndUpdateId(nextMbi);
    person.attributes.put(RIFExporter.BB2_MBI, mbiStr);
    int yearsOfHistory = Config.getAsInteger("exporter.years_of_history");
    int endYear = Utilities.getYear(stopTime);
    int endMonth = Utilities.getMonth(stopTime);
    long deathDate = -1;
    if (person.attributes.get(Person.DEATHDATE) != null) {
      deathDate = (long)person.attributes.get(Person.DEATHDATE);
      if (deathDate > stopTime) {
        deathDate = -1; // Ignore future death date that may have been set by a module
      } else {
        endYear = Utilities.getYear(deathDate);
        endMonth = Utilities.getMonth(deathDate);
      }
    }

    // About 7% of non-dual-eligible beneficiaries decline Medicare Part B
    // Carrier claims won't be generated unless the beneficiary is enrolled in Part B
    boolean partBEnrollee = person.rand(0.0, 100.0) < PART_B_ENROLLEE_PERCENT;
    person.attributes.put(RIFExporter.BB2_PARTB_ENROLLEE, partBEnrollee);

    PartCContractHistory partCContracts = new PartCContractHistory(person,
            deathDate == -1 ? stopTime : deathDate, yearsOfHistory);
    PartDContractHistory partDContracts = new PartDContractHistory(person,
            deathDate == -1 ? stopTime : deathDate, yearsOfHistory);
    // following is also used in exportPrescription
    person.attributes.put(RIFExporter.BB2_PARTD_CONTRACTS, partDContracts);

    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    long dateOf65thBirthday = Utilities.getAnniversary(birthdate, 65);
    int monthOf65thBirthday = Utilities.getMonth(dateOf65thBirthday) - 1;
    long dateOfESRD = getEarliestDiagnosis(person, ESRD_CODE);
    long coverageStartDate = Long.min(dateOf65thBirthday, dateOfESRD);
    boolean disabled = isDisabled(person);
    long dateOfDisability = Long.MAX_VALUE;
    if (disabled) {
      dateOfDisability = ssd.getEarliestDiagnosis(person);
      coverageStartDate = Long.min(coverageStartDate, dateOfDisability);
    }
    // if child or spouse, date of primary beneficiary start
    String partDCostSharingCode = PartDContractHistory.getPartDCostSharingCode(person);
    boolean lowIncome = partDCostSharingCode.equals("01");
    boolean disabledNow = (dateOfDisability < stopTime);
    boolean esrdNow = (dateOfESRD < stopTime);
    int ageThisYear = ageAtEndOfYear(birthdate, (endYear - yearsOfHistory));
    String crntBic = getCurrentBeneficiaryIdCode(
        person, ageThisYear, disabledNow, esrdNow, lowIncome);
    if (!crntBic.equals("A")) {
      // if child or spouse, date of primary beneficiary start
      if (yearsOfHistory > ageThisYear) {
        coverageStartDate = stopTime - Utilities.convertTime("years", ageThisYear);
      } else {
        coverageStartDate = stopTime - Utilities.convertTime("years", yearsOfHistory);
      }
    }
    person.attributes.put(RIFExporter.COVERAGE_START_DATE, coverageStartDate);

    boolean firstYearOutput = true;
    String initialBeneEntitlementReason = null;
    for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
      long startOfYearTimeStamp = Utilities.convertCalendarYearsToTime(year);
      long endOfYearTimeStamp = Utilities.convertCalendarYearsToTime(year + 1) - 1;
      if (endOfYearTimeStamp < CLAIM_CUTOFF) {
        continue;
      }
      if (!hasPartABCoverage(person, endOfYearTimeStamp)) {
        continue;
      }
      ageThisYear = ageAtEndOfYear(birthdate, year);
      // if too young to have been married to a primary beneficiary
      if ((ageThisYear < 50)
          && !(dateOfESRD < endOfYearTimeStamp) // and they don't have ESRD
          && !(dateOfDisability < endOfYearTimeStamp)) { // and they aren't disabled
        continue;
      }

      HashMap<BB2RIFStructure.BENEFICIARY, String> fieldValues = new HashMap<>();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.BENEFICIARY.class, person);
      if (!firstYearOutput) {
        // The first year output is set via staticFieldConfig to "INSERT", subsequent years
        // need to be "UPDATE"
        fieldValues.put(BB2RIFStructure.BENEFICIARY.DML_IND, "UPDATE");
      }
      // CRNT_BIC
      disabledNow = (dateOfDisability < endOfYearTimeStamp);
      esrdNow = (dateOfESRD < endOfYearTimeStamp);
      crntBic = getCurrentBeneficiaryIdCode(person, ageThisYear, disabledNow, esrdNow, lowIncome);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.CRNT_BIC, crntBic);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.RFRNC_YR, String.valueOf(year));
      String coverageStartStr = bb2DateFromTimestamp(coverageStartDate);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.COVSTART, coverageStartStr);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.PTA_CVRG_STRT_DT, coverageStartStr);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.PTB_CVRG_STRT_DT, coverageStartStr);
      int monthCount = year == endYear ? endMonth : 12;
      // TODO: subtract the months before coverage started
      // e.g., before 65 or before disability
      // e.g., if they turned 65 in March, then January and February should not count
      // while they were not enrolled (which will effect mdcr_stus*, mdcr_entlmnt*, etc.
      String monthCountStr = String.valueOf(monthCount);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.A_MO_CNT, monthCountStr);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.B_MO_CNT, monthCountStr);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BUYIN_MO_CNT, monthCountStr);
      int partDMonthsCovered = partDContracts.getCoveredMonthsCount(year);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.PLAN_CVRG_MO_CNT,
              String.valueOf(partDMonthsCovered));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ID, beneIdStr);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_CRNT_HIC_NUM, hicId);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.MBI_NUM, mbiStr);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_SEX_IDENT_CD,
              getBB2SexCode((String)person.attributes.get(Person.GENDER)));
      String zipCode = (String)person.attributes.get(Person.ZIP);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ZIP_CD, zipCode);
      String countyCode = exporter.locationMapper.zipToCountyCode(zipCode);
      if (countyCode == null) {
        countyCode = exporter.locationMapper.stateCountyNameToCountyCode(
            (String)person.attributes.get(Person.STATE),
            (String)person.attributes.get(Person.COUNTY), person);
      }
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_COUNTY_CD, countyCode);
      for (int i = 0; i < monthCount; i++) {
        fieldValues.put(BB2RIFStructure.beneficiaryFipsStateCntyFields[i],
            exporter.locationMapper.zipToFipsCountyCode(zipCode));
      }
      fieldValues.put(BB2RIFStructure.BENEFICIARY.STATE_CODE,
              exporter.locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
      String raceCode = bb2RaceCode(
              (String)person.attributes.get(Person.ETHNICITY),
              (String)person.attributes.get(Person.RACE));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_RACE_CD, raceCode);
      // TODO: implement RTI algorithm
      fieldValues.put(BB2RIFStructure.BENEFICIARY.RTI_RACE_CD, raceCode);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_SRNM_NAME,
              (String)person.attributes.get(Person.LAST_NAME));
      String givenName = (String)person.attributes.get(Person.FIRST_NAME);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_GVN_NAME,
              StringUtils.truncate(givenName, 15));
      if (person.attributes.containsKey(Person.MIDDLE_NAME)) {
        String middleName = (String) person.attributes.get(Person.MIDDLE_NAME);
        middleName = middleName.substring(0, 1);
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_MDL_NAME, middleName);
      }
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_BIRTH_DT,
              RIFExporter.bb2DateFromTimestamp(birthdate));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.AGE,
              String.valueOf(ageThisYear));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTA_TRMNTN_CD, "0");
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTB_TRMNTN_CD, "0");
      if (deathDate != -1) {
        // only add death date for years when it was (presumably) known. E.g. If we are outputting
        // record for 2005 and patient died in 2007 we don't include the death date.
        if (Utilities.getYear(deathDate) <= year) {
          String deathDateStr = bb2DateFromTimestamp(deathDate);
          fieldValues.put(BB2RIFStructure.BENEFICIARY.DEATH_DT, deathDateStr);
          fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTA_TRMNTN_CD, "1");
          fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTB_TRMNTN_CD, "1");
          fieldValues.put(BB2RIFStructure.BENEFICIARY.PTA_CVRG_END_DT, deathDateStr);
          fieldValues.put(BB2RIFStructure.BENEFICIARY.PTB_CVRG_END_DT, deathDateStr);
        }
      }
      int ageYearEnd = ageAtEndOfYear(birthdate, year);
      int ageYearBegin = ageYearEnd - 1;
      boolean medicareAgeThisYear = (ageYearEnd >= 65);
      boolean esrdThisYear = hasESRD(person, year);
      // Technically, disabled should be checked year by year, but we don't currently
      // have that level of resolution.
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ESRD_IND, esrdThisYear ? "Y" : "0");
      // "0" = old age, "1" = Disabled, "2" = ESRD, "3" = ESRD && Disabled
      if (medicareAgeThisYear) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "0");
      } else if (esrdThisYear && disabled) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "3");
      } else if (esrdThisYear) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "2");
      } else if (disabled) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "1");
      } else {
        // They are a child or spouse of the beneficiary.
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "0");
      }
      if (initialBeneEntitlementReason == null) {
        initialBeneEntitlementReason = fieldValues.get(
                BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR);
        person.attributes.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG.toString(),
            initialBeneEntitlementReason);
      }
      if (initialBeneEntitlementReason != null) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_ORIG,
                initialBeneEntitlementReason);
      }

      for (PartCContractHistory.ContractPeriod period:
              partCContracts.getContractPeriods(year)) {
        PartCContractID partCContractID = period.getContractID();
        if (partCContractID != null) {
          String partCContractIDStr = partCContractID.toString();
          String partCPBPIDStr = period.getPlanBenefitPackageID().toString();
          List<Integer> coveredMonths = period.getCoveredMonths(year);
          for (int i: coveredMonths) {
            fieldValues.put(BB2RIFStructure.beneficiaryPartCContractFields[i - 1],
                    partCContractIDStr);
            fieldValues.put(BB2RIFStructure.beneficiaryPartCPBPFields[i - 1],
                    partCPBPIDStr);
          }
        }
      }

      int rdsMonthCount = 0;
      long partDCoverageStart = Long.MAX_VALUE;
      long partDCoverageEnd = Long.MIN_VALUE;
      for (PartDContractHistory.ContractPeriod period:
              partDContracts.getContractPeriods(year)) {
        PartDContractID partDContractID = period.getContractID();
        String partDDrugSubsidyIndicator =
                partDContracts.getEmployeePDPIndicator(partDContractID);
        if (partDContractID != null) {
          partDCoverageStart = Long.min(partDCoverageStart, period.getStart());
          partDCoverageEnd = Long.max(partDCoverageEnd, period.getEnd());
          String partDContractIDStr = partDContractID.toString();
          String partDPBPIDStr = period.getPlanBenefitPackageID().toString();
          List<Integer> coveredMonths = period.getCoveredMonths(year);
          if (partDDrugSubsidyIndicator != null && partDDrugSubsidyIndicator.equals("Y")) {
            rdsMonthCount += coveredMonths.size();
          }
          for (int i: coveredMonths) {
            fieldValues.put(BB2RIFStructure.beneficiaryPartDContractFields[i - 1],
                    partDContractIDStr);
            fieldValues.put(BB2RIFStructure.beneficiaryPartDPBPFields[i - 1],
                    partDPBPIDStr);
            fieldValues.put(BB2RIFStructure.beneficiaryPartDSegmentFields[i - 1], "000");
            fieldValues.put(BB2RIFStructure.beneficiaryPartDCostSharingFields[i - 1],
                    partDCostSharingCode);
            if (partDDrugSubsidyIndicator != null) {
              fieldValues.put(BB2RIFStructure.benficiaryPartDRetireeDrugSubsidyFields[i - 1],
                      partDDrugSubsidyIndicator);
            }
          }
        } else {
          for (int i: period.getCoveredMonths(year)) {
            // Not enrolled this month
            fieldValues.put(BB2RIFStructure.beneficiaryPartDCostSharingFields[i - 1], "00");
          }
        }
      }
      fieldValues.put(BB2RIFStructure.BENEFICIARY.RDS_MO_CNT, Integer.toString(rdsMonthCount));
      if (partDCoverageStart != Long.MAX_VALUE) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.PTD_CVRG_STRT_DT,
                bb2DateFromTimestamp(Long.max(partDCoverageStart, startOfYearTimeStamp)));
      }
      if (partDCoverageEnd != Long.MIN_VALUE) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.PTD_CVRG_END_DT,
                bb2DateFromTimestamp(Long.min(partDCoverageEnd, endOfYearTimeStamp)));
      }

      String dualEligibleStatusCode = getDualEligibilityCode(person, medicareAgeThisYear,
          esrdThisYear, disabled);
      String medicareStatusCode = getMedicareStatusCode(ageYearBegin, medicareAgeThisYear,
          esrdThisYear, disabled);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_MDCR_STATUS_CD, medicareStatusCode);
      for (int month = 0; month < monthCount; month++) {
        fieldValues.put(BB2RIFStructure.beneficiaryDualEligibleStatusFields[month],
                dualEligibleStatusCode);
        if (ageYearBegin == 64) {
          boolean medicareThisMonth = (month >= monthOf65thBirthday);
          medicareStatusCode = getMedicareStatusCode(ageYearEnd, medicareThisMonth,
              esrdThisYear, disabled);
          if (!medicareThisMonth) {
            // Not enrolled
            fieldValues.put(BB2RIFStructure.beneficiaryDualEligibleStatusFields[month],"00");
            fieldValues.put(BB2RIFStructure.beneficiaryPartDContractFields[month],"0");
            fieldValues.put(BB2RIFStructure.beneficiaryPartDPBPFields[month],"");
            fieldValues.put(BB2RIFStructure.beneficiaryPartDCostSharingFields[month],"00");
            fieldValues.put(BB2RIFStructure.beneficiaryPartDSegmentFields[month], "");
            fieldValues.put(BB2RIFStructure.benficiaryPartDRetireeDrugSubsidyFields[month],"0");
          }
        }
        fieldValues.put(BB2RIFStructure.beneficiaryMedicareStatusFields[month],
                medicareStatusCode);
        String buyInIndicator = getEntitlementBuyIn(dualEligibleStatusCode, medicareStatusCode,
                partBEnrollee);
        fieldValues.put(BB2RIFStructure.beneficiaryMedicareEntitlementFields[month],
                buyInIndicator);
      }
      exporter.rifWriters.writeValues(BB2RIFStructure.BENEFICIARY.class, fieldValues, year);
      exporter.rifWriters.writeValues(BB2RIFStructure.BENEFICIARY.class, fieldValues, 9999);
      firstYearOutput = false;
    }
    if (firstYearOutput) {
      // person was never a beneficiary
      return null;
    } else {
      return beneIdStr;
    }
  }

  /**
   * Export a beneficiary history for single person. Assumes exportBeneficiary
   * was called first to set up various ID on person
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  public void exportHistory(Person person, long startTime, long stopTime) throws IOException {
    HashMap<BB2RIFStructure.BENEFICIARY_HISTORY, String> fieldValues = new HashMap<>();

    exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.BENEFICIARY_HISTORY.class,
            person);

    String beneIdStr = (String)person.attributes.get(RIFExporter.BB2_BENE_ID);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ID, beneIdStr);
    String hicId = (String)person.attributes.get(RIFExporter.BB2_HIC_ID);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_CRNT_HIC_NUM, hicId);
    String mbiStr = (String)person.attributes.get(RIFExporter.BB2_MBI);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.MBI_NUM, mbiStr);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_SEX_IDENT_CD,
            getBB2SexCode((String)person.attributes.get(Person.GENDER)));
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_BIRTH_DT,
            RIFExporter.bb2DateFromTimestamp(birthdate));
    String zipCode = (String)person.attributes.get(Person.ZIP);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ZIP_CD, zipCode);
    String countyCode = exporter.locationMapper.zipToCountyCode(zipCode);
    if (countyCode == null) {
      countyCode = exporter.locationMapper.stateCountyNameToCountyCode(
          (String)person.attributes.get(Person.STATE),
          (String)person.attributes.get(Person.COUNTY), person);
    }
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_COUNTY_CD, countyCode);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.STATE_CODE,
            exporter.locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_RACE_CD,
            bb2RaceCode(
                    (String)person.attributes.get(Person.ETHNICITY),
                    (String)person.attributes.get(Person.RACE)));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_SRNM_NAME,
            (String)person.attributes.get(Person.LAST_NAME));
    String givenName = (String)person.attributes.get(Person.FIRST_NAME);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_GVN_NAME,
            StringUtils.truncate(givenName, 15));
    if (person.attributes.containsKey(Person.MIDDLE_NAME)) {
      String middleName = (String) person.attributes.get(Person.MIDDLE_NAME);
      middleName = middleName.substring(0, 1);
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_MDL_NAME, middleName);
    }
    String terminationCode = "0";
    if (person.attributes.get(Person.DEATHDATE) != null) {
      long deathDate = (long)person.attributes.get(Person.DEATHDATE);
      if (deathDate <= stopTime) {
        terminationCode = "1"; // Ignore future death date that may have been set by a module
      }
    }
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_PTA_TRMNTN_CD, terminationCode);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_PTB_TRMNTN_CD, terminationCode);
    int year = Utilities.getYear(stopTime);
    int age = ageAtEndOfYear(birthdate, year);
    boolean medicareAge = (age >= 65);
    boolean esrd = hasESRD(person, year);
    // Technically, disabled should be checked year by year, but we don't currently
    // have that level of resolution.
    boolean disabled = isDisabled(person);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ESRD_IND, esrd ? "Y" : "0");
    // "0" = old age, "1" = Disabled, "2" = ESRD, "3" = ESRD && Disabled
    if (medicareAge) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "0");
    } else if (esrd && disabled) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "3");
    } else if (esrd) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "2");
    } else if (disabled) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "1");
    } else {
      // They are a child or spouse of the beneficiary.
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "0");
    }
    String originalReason = (String) person.attributes.get(
        BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG.toString());
    if (originalReason != null) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG, originalReason);
    } else if (esrd && disabled) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG, "3");
    } else if (esrd) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG, "2");
    } else if (disabled) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG, "1");
    } else {
      String initialBeneEntitlementReason = fieldValues.get(
          BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR);
      if (initialBeneEntitlementReason != null) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG,
            initialBeneEntitlementReason);
      }
    }
    String medicareStatusCode = getMedicareStatusCode(age, medicareAge, esrd, disabled);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_MDCR_STATUS_CD, medicareStatusCode);
    exporter.rifWriters.writeValues(BB2RIFStructure.BENEFICIARY_HISTORY.class, fieldValues);
  }

  /**
   * Get the medicare status code.
   * @param ageAtEndOfYear Age at the end of the year.
   * @param medicareAge Whether they qualify for medicare based on age this specific month.
   * @param esrd Whether or not they have ESRD.
   * @param disabled Whether or not they are disabled.
   * @return One of 00,10,11,20,21,31
   */
  private static String getMedicareStatusCode(
      int ageAtEndOfYear, boolean medicareAge, boolean esrd, boolean disabled) {
    if (medicareAge) {
      if (esrd) {
        return "11";
      } else {
        return "10";
      }
    } else if (disabled) {
      if (esrd) {
        return "21";
      } else {
        return "20";
      }
    } else if (esrd) {
      return "31";
    } else {
      if ((ageAtEndOfYear <= 18) || (ageAtEndOfYear >= 50)) {
        return "10"; // Under 65 dependent of a primary beneficiary
      } else {
        return "00"; // Not enrolled
      }
    }
  }

  // TODO missing 1, 2, A, B
  private static String getEntitlementBuyIn(String dualEligibleStatusCode,
          String medicareStatusCode, boolean partBEnrollee) {
    if (medicareStatusCode.equals("00")) {
      return "0"; // not enrolled
    } else {
      if (dualEligibleStatusCode.equals("NA") || dualEligibleStatusCode.equals("09")) {
        // NA = not dual eligible, 09 = dual eligible but not medicaid
        if (partBEnrollee) {
          return "3"; // Part A and Part B
        } else {
          return "1"; // Part A only
        }
      } else {
        // dual eligible
        return "C"; // Part A and B state buy-in
      }
    }
  }

  /**
   * Get the BB2 race code. BB2 uses a single code to represent race and ethnicity, we assume
   * ethnicity gets priority here.
   * @param ethnicity the Synthea ethnicity
   * @param race the Synthea race
   * @return the BB2 race code
   */
  private static String bb2RaceCode(String ethnicity, String race) {
    if ("hispanic".equals(ethnicity)) {
      return "5";
    } else {
      String bbRaceCode; // unknown
      switch (race) {
        case "white":
          bbRaceCode = "1";
          break;
        case "black":
          bbRaceCode = "2";
          break;
        case "asian":
          bbRaceCode = "4";
          break;
        case "native":
          bbRaceCode = "6";
          break;
        case "other":
        default:
          bbRaceCode = "3";
          break;
      }
      return bbRaceCode;
    }
  }

  /**
   * Calculate the age of a person at the end of the given year.
   * @param birthdate a person's birthdate specified as number of milliseconds since the epoch
   * @param year the year
   * @return the person's age
   */
  private static int ageAtEndOfYear(long birthdate, int year) {
    return year - Utilities.getYear(birthdate);
  }

  // Dual eligible codes weighted by observed frequency. Weights are % but do not add up to 100%
  // since codes 00 (15.6%) and NA (68.3%) are dealt with separately based on patient properties
  private static final RandomCollection<String> dualCodes = new RandomCollection<>();

  static {
    // Qualified Medicare Beneficiary (QMB)-only
    dualCodes.add(2.2, "01");
    // QMB and full Medicaid coverage, including prescription drugs
    dualCodes.add(8.2, "02");
    // Specified Low-Income Medicare Beneficiary (SLMB)-only
    dualCodes.add(1.3, "03");
    // SLMB and full Medicaid coverage, including prescription drugs
    dualCodes.add(0.5, "04");
    // Qualified Disabled Working Individual (QDWI)
    dualCodes.add(0.001, "05");
    // Qualifying individuals (QI)
    dualCodes.add(0.8, "06");
    // Other dual eligible (not QMB, SLMB, QWDI, or QI) with full Medicaid coverage, including
    // prescription Drugs
    dualCodes.add(2.9, "08");
    // Other dual eligible, but without Medicaid coverage
    dualCodes.add(0.007, "09");
    // Unknown
    dualCodes.add(0.05, "99");
    // Not in code book but present in database
    dualCodes.add(0.04, "AA");
    // Not in code book but present in database
    dualCodes.add(0.1, "");
  }

  static final double POVERTY_LEVEL =
        Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 12880);
  // 1.8 multiplier chosen to fit generated % of medicaid eligible patients to real dual
  // eligibility code distribution
  static final double MEDICAID_THRESHOLD = POVERTY_LEVEL * 2.0;

  String getDualEligibilityCode(Person person, boolean medicareAge,
          boolean esrd, boolean disabled) {
    double income = Double.parseDouble(person.attributes.get(Person.INCOME).toString());
    boolean medicareEligible = medicareAge || esrd || disabled;
    boolean medicaidEligible = income < MEDICAID_THRESHOLD;

    if (medicareEligible && medicaidEligible) {
      return dualCodes.next(person);
    } else if (medicaidEligible) {
      return "00"; // Not enrolled in Medicare (medicaid only)
    } else {
      return "NA"; // Non-medicaid (medicare only)
    }
  }

  /**
   * The the current beneficiary ID code (CRNT_BIC).
   * @param person The person.
   * @param ageThisYear The person's current age (at the end of the year).
   * @param disabled If the person is disabled according to SSD.
   * @param esrd If the person has ESRD.
   * @param lowIncome If the person is low income (e.g., Part D cost sharing).
   * @return The current beneficiary ID code.
   */
  private String getCurrentBeneficiaryIdCode(Person person, int ageThisYear,
      boolean disabled, boolean esrd, boolean lowIncome) {
    // Default to "A" primary claimant (over 65 or (esrd and/or disabled))
    // T if partDCostSharingCode=="01" or under-65 w/ ESRD
    // else if under-65
    //   - if minor, pick child code
    //   - if male, pick husband or widower code
    //   - if female, pick wife or widow code
    String currentBeneIdCode = "A"; // primary claimant
    String maritalStatus = (String)
        person.attributes.getOrDefault(Person.MARITAL_STATUS, "S");
    if (ageThisYear < 65) {
      // Under 65
      if (lowIncome || esrd) {
        // Uninsured entitled to HIB or renal provisions
        currentBeneIdCode = "T";
      } else if (disabled) {
        currentBeneIdCode = "A";
      } else if (ageThisYear <= 18) {
        // child codes
        currentBeneIdCode = person.rand(new String[] {"C1", "C1", "C1", "C2", "C2", "C3"});
      } else if (person.attributes.get(Person.GENDER).equals("F")) {
        if (maritalStatus.equals("M")) {
          // Married Woman
          if (ageThisYear >= 62) {
            // Aged wife age 62 or over 1st claimant
            currentBeneIdCode = "B";
          } else {
            currentBeneIdCode = person.rand(new String[] {"B2", "B3"});
          }
        } else if (maritalStatus.equals("D")) {
          // Divorced Woman
          if (ageThisYear >= 62) {
            // Divorced wife age 62 or over 1st claimant
            currentBeneIdCode = "B6";
          } else {
            // Divorced wife 2nd claimant
            currentBeneIdCode = "B9";
          }
        } else if (maritalStatus.equals("W")) {
          if (ageThisYear >= 60) {
            // Aged widow 60 or over 1st claimant
            currentBeneIdCode = "D";
          } else {
            // Aged widow 2nd claimant
            currentBeneIdCode = "D2";
          }
        } else {
          // Uninsured not qualified for deemed HIB
          currentBeneIdCode = "M";
        }
      } else {
        // Adult male under 65
        if (maritalStatus.equals("M")) {
          // Married man
          if (ageThisYear >= 62) {
            // Aged husband age 62 or over 1st claimant
            currentBeneIdCode = "B1";
          } else {
            // Young husband 1st claimant
            currentBeneIdCode = "BY";
          }
        } else if (maritalStatus.equals("D")) {
          // Divorced husband 1st claimant
          currentBeneIdCode = "BR";
        } else if (maritalStatus.equals("W")) {
          if (ageThisYear >= 60) {
            // Aged widower age 60 or over 1st claimant
            currentBeneIdCode = "D1";
          } else {
            // Aged widower 2nd claimant
            currentBeneIdCode = "D3";
          }
        } else {
          // Uninsured not qualified for deemed HIB
          currentBeneIdCode = "M";
        }
      }
    }
    return currentBeneIdCode;
  }

  /**
   * Determines whether the person has end stage renal disease at the end of the supplied year.
   * @param person the person
   * @param year the year
   * @return true if has ESRD, false otherwise
   */
  private boolean hasESRD(Person person, int year) {
    long timestamp = Utilities.convertCalendarYearsToTime(year + 1); // +1 for end of year
    List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, timestamp);
    boolean esrdGivenYear = mappedDiagnosisCodes.contains(ESRD_CODE);
    if (esrdGivenYear) {
      return esrdGivenYear;
    }
    // boolean esrdGivenAttribute = person.attributes.containsKey("dialysis_reason");
    // or attribute "ckd" == 5
    // boolean esrdGivenPresent = person.record.conditionActive(ESRD_SNOMED);
    Long esrdPresentOnset = person.record.presentOnset(ESRD_SNOMED);
    if (esrdPresentOnset != null) {
      return (Utilities.getYear(esrdPresentOnset) <= year);
    }
    // Widen the fishing net a little bit to include more folks...
    Long ckd4PresentOnset = person.record.presentOnset(CKD4_SNOMED);
    if (ckd4PresentOnset != null) {
      return (Utilities.getYear(ckd4PresentOnset) <= year);
    }
    return false;
  }

  private static boolean isDisabled(Person person) {
    return (person.attributes.containsKey(Person.BLINDNESS)
            && person.attributes.get(Person.BLINDNESS).equals(true))
        || ssd.isPersonEligible(person, 0L);
  }
}
