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
  // https://aspe.hhs.gov/sites/default/files/documents/f81aafbba0b331c71c6e8bc66512e25d/medicare-beneficiary-enrollment-ib.pdf
  private static final double PART_B_ENROLLEE_PERCENT = 92.5;
  private static final String[] ESRD_SNOMEDS = new String[] {"46177005", "431857002", "204949001"};

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
    long dateOfESRD = getEarliestDiagnosis(person, ESRD_CODES);
    long coverageStartDate = Long.min(dateOf65thBirthday, dateOfESRD);
    long dateOfUnmappedESRD = getEarliestUnmappedDiagnosis(person, ESRD_SNOMEDS);
    coverageStartDate = Long.min(coverageStartDate, dateOfUnmappedESRD);
    boolean disabled = isDisabled(person);
    long dateOfDisability = getDateOfDisability(person);
    coverageStartDate = Long.min(coverageStartDate, dateOfDisability);

    String partDCostSharingCode = PartDContractHistory.getPartDCostSharingCode(person);
    person.attributes.put(RIFExporter.COVERAGE_START_DATE, coverageStartDate);
    boolean lowIncome = partDCostSharingCode.equals("01");

    boolean firstYearOutput = true;
    String initialBeneEntitlementReason = null;
    for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
      long startOfYearTimeStamp = Utilities.convertCalendarYearsToTime(year);
      long endOfYearTimeStamp = Utilities.convertCalendarYearsToTime(year + 1) - 1;
      if (endOfYearTimeStamp < CLAIM_CUTOFF) {
        continue;
      }
      int ageThisYear = ageAtEndOfYear(birthdate, year);
      if (!hasPartABCoverage(person, endOfYearTimeStamp)) {
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
      boolean disabledNow = (dateOfDisability < endOfYearTimeStamp);
      boolean esrdNow = hasESRD(person, year);
      String crntBic = getCurrentBeneficiaryIdCode(person, ageThisYear, disabledNow, esrdNow,
              lowIncome);
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
      int ageYearEnd = RIFExporter.ageAtEndOfYear(birthdate, year);
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

  // Dual eligible codes weighted by observed frequency. Weights are % but do not add up to 100%
  // since codes 00 (15.6%) and NA (68.3%) are dealt with separately based on patient properties
  private static final RandomCollection<String> dualCodes = new RandomCollection<>();
  // BIC Codes
  private static final RandomCollection<String> bicCodes = new RandomCollection<>();

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

    // A = Primary claimant
    bicCodes.add(8190, "A");
    // codes based on marital status
    bicCodes.add(500, "MARITAL_STATUS");
    // M = Uninsured not qualified for deemed HIB
    bicCodes.add(170, "M");
    // C1 = Child includes minor student or disabled child 1st claimant
    bicCodes.add(120, "C1");
    // TA = Medicare Qualified Government Employment (MQGE) primary claimant
    bicCodes.add(90, "TA");
    // 10 = Railroad Retirement Board (RRB) Retirement employee or annuitant
    bicCodes.add(40, "10");
    // C2 = Child includes minor student or disabled child 2nd claimant
    bicCodes.add(30, "C2");
    // C3 = Child includes minor student or disabled child 3rd claimant
    bicCodes.add(15, "C3");
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
    if (esrd) {
      // T = Uninsured entitled to HIB under deemed or renal provisions
      return "T";
    }

    String currentBeneIdCode = bicCodes.next(person);
    if (currentBeneIdCode.startsWith("C")) {
      if (ageThisYear >= 65) {
        currentBeneIdCode = pickMaritalStatusBIC(person);
      }
    } else if (currentBeneIdCode.startsWith("MARITAL_STATUS")) {
      currentBeneIdCode = pickMaritalStatusBIC(person);
    }
    return currentBeneIdCode;
  }

  private String pickMaritalStatusBIC(Person person) {
    String maritalStatus = (String)
        person.attributes.getOrDefault(Person.MARITAL_STATUS, "S");
    String gender = (String) person.attributes.get(Person.GENDER);

    if (maritalStatus.equals("S")) {
      return "A";
    } else if (gender.equals("F")) {
      // female
      if (maritalStatus.equals("M")) {
        return person.rand(new String[] {"B","B","B3","B2"});
      } else if (maritalStatus.equals("D")) {
        return person.rand(new String[] {"D6","D6","B6","B9"});
      } else if (maritalStatus.equals("W")) {
        return person.rand(new String[] {"D","D","D2"});
      }
    } else {
      // male
      if (maritalStatus.equals("M")) {
        return person.rand(new String[] {"B1","B1","BY"});
      } else if (maritalStatus.equals("D")) {
        return person.rand(new String[] {"BR","BR","BT"});
      } else if (maritalStatus.equals("W")) {
        return person.rand(new String[] {"D1","D1","D3"});
      }
    }
    return "M";
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
    for (String code : ESRD_CODES) {
      if (mappedDiagnosisCodes.contains(code)) {
        return true;
      }
    }
    long esrdOnset = getEarliestUnmappedDiagnosis(person, ESRD_SNOMEDS);
    return (esrdOnset <= timestamp);
  }
}
