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

    PartCContractHistory partCContracts = new PartCContractHistory(person,
            deathDate == -1 ? stopTime : deathDate, yearsOfHistory);
    PartDContractHistory partDContracts = new PartDContractHistory(person,
            deathDate == -1 ? stopTime : deathDate, yearsOfHistory);
    // following is also used in exportPrescription
    person.attributes.put(RIFExporter.BB2_PARTD_CONTRACTS, partDContracts);

    boolean firstYearOutput = true;
    String initialBeneEntitlementReason = null;
    for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
      HashMap<BB2RIFStructure.BENEFICIARY, String> fieldValues = new HashMap<>();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.BENEFICIARY.class, person);
      if (!firstYearOutput) {
        // The first year output is set via staticFieldConfig to "INSERT", subsequent years
        // need to be "UPDATE"
        fieldValues.put(BB2RIFStructure.BENEFICIARY.DML_IND, "UPDATE");
      }

      fieldValues.put(BB2RIFStructure.BENEFICIARY.RFRNC_YR, String.valueOf(year));
      int monthCount = year == endYear ? endMonth : 12;
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
      long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_BIRTH_DT,
              RIFExporter.bb2DateFromTimestamp(birthdate));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.AGE,
              String.valueOf(ageAtEndOfYear(birthdate, year)));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTA_TRMNTN_CD, "0");
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTB_TRMNTN_CD, "0");
      if (deathDate != -1) {
        // only add death date for years when it was (presumably) known. E.g. If we are outputting
        // record for 2005 and patient died in 2007 we don't include the death date.
        if (Utilities.getYear(deathDate) <= year) {
          fieldValues.put(BB2RIFStructure.BENEFICIARY.DEATH_DT,
                  RIFExporter.bb2DateFromTimestamp(deathDate));
          fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTA_TRMNTN_CD, "1");
          fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_PTB_TRMNTN_CD, "1");
        }
      }
      boolean medicareAgeThisYear = ageAtEndOfYear(birthdate, year) >= 65;
      boolean esrdThisYear = hasESRD(person, year);
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ESRD_IND, esrdThisYear ? "Y" : "0");
      // "0" = old age, "2" = ESRD
      if (medicareAgeThisYear) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "0");
      } else if (esrdThisYear) {
        fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_ENTLMT_RSN_CURR, "2");
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

      String partDCostSharingCode = PartDContractHistory.getPartDCostSharingCode(person);
      int rdsMonthCount = 0;
      for (PartDContractHistory.ContractPeriod period:
              partDContracts.getContractPeriods(year)) {
        PartDContractID partDContractID = period.getContractID();
        String partDDrugSubsidyIndicator =
                partDContracts.getEmployeePDPIndicator(partDContractID);
        if (partDContractID != null) {
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

      String dualEligibleStatusCode = getDualEligibilityCode(person, year);
      String medicareStatusCode = getMedicareStatusCode(medicareAgeThisYear, esrdThisYear,
              isBlind(person));
      fieldValues.put(BB2RIFStructure.BENEFICIARY.BENE_MDCR_STATUS_CD, medicareStatusCode);
      String buyInIndicator = getEntitlementBuyIn(dualEligibleStatusCode, medicareStatusCode);
      for (int month = 0; month < monthCount; month++) {
        fieldValues.put(BB2RIFStructure.beneficiaryDualEligibleStatusFields[month],
                dualEligibleStatusCode);
        fieldValues.put(BB2RIFStructure.beneficiaryMedicareStatusFields[month],
                medicareStatusCode);
        fieldValues.put(BB2RIFStructure.beneficiaryMedicareEntitlementFields[month],
                buyInIndicator);
      }
      exporter.rifWriters.writeValues(BB2RIFStructure.BENEFICIARY.class, fieldValues, year);
      firstYearOutput = false;
    }
    return beneIdStr;
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
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_COUNTY_CD,
            exporter.locationMapper.zipToCountyCode(zipCode));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.STATE_CODE,
            exporter.locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ZIP_CD,
            (String)person.attributes.get(Person.ZIP));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_RACE_CD,
            bb2RaceCode(
                    (String)person.attributes.get(Person.ETHNICITY),
                    (String)person.attributes.get(Person.RACE)));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_SRNM_NAME,
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
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
    boolean medicareAge = ageAtEndOfYear(birthdate, year) >= 65;
    boolean esrd = hasESRD(person, year);
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ESRD_IND, esrd ? "Y" : "0");
    // "0" = old age, "2" = ESRD
    if (medicareAge) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "0");
    } else if (esrd) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "2");
    }
    String initialBeneEntitlementReason = fieldValues.get(
            BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR);
    if (initialBeneEntitlementReason != null) {
      fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG,
              initialBeneEntitlementReason);
    }
    String medicareStatusCode = getMedicareStatusCode(medicareAge, esrd,
              isBlind(person));
    fieldValues.put(BB2RIFStructure.BENEFICIARY_HISTORY.BENE_MDCR_STATUS_CD, medicareStatusCode);
    exporter.rifWriters.writeValues(BB2RIFStructure.BENEFICIARY_HISTORY.class, fieldValues);
  }

  private static String getMedicareStatusCode(boolean medicareAge, boolean esrd, boolean blind) {
    if (medicareAge) {
      if (esrd) {
        return "11";
      } else {
        return "10";
      }
    } else if (blind) {
      if (esrd) {
        return "21";
      } else {
        return "20";
      }
    } else if (esrd) {
      return "31";
    } else {
      return "00"; // Not enrolled
    }
  }

  private static String getEntitlementBuyIn(String dualEligibleStatusCode,
          String medicareStatusCode) {
    if (medicareStatusCode.equals("00")) {
      return "0"; // not enrolled
    } else {
      if (dualEligibleStatusCode.equals("NA")) {
        // not dual eligible
        return "3"; // Part A and Part B
      } else {
        // dual eligible
        return "C"; // Part A and Part B state buy-in
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

  // Income level < 0.3
  private static final RandomCollection<String> incomeBandOneDualCodes = new RandomCollection<>();
  // 0.3 <= Income level < 0.6
  private static final RandomCollection<String> incomeBandTwoDualCodes = new RandomCollection<>();
  // 0.6 <= Income level < 1.0
  private static final RandomCollection<String> incomeBandThreeDualCodes = new RandomCollection<>();

  static {
    // Specified Low-Income Medicare Beneficiary (SLMB)-only
    incomeBandOneDualCodes.add(1.3, "03");
    // SLMB and full Medicaid coverage, including prescription drugs
    incomeBandOneDualCodes.add(0.5, "04");
    // Other dual eligible, but without Medicaid coverage
    incomeBandOneDualCodes.add(0.007, "09");
    // Unknown
    incomeBandOneDualCodes.add(0.05, "99");
    // Not in code book but present in database
    incomeBandOneDualCodes.add(0.04, "AA");
    // Not in code book but present in database
    incomeBandOneDualCodes.add(0.1, "");

    // QMB and full Medicaid coverage, including prescription drugs
    incomeBandTwoDualCodes.add(8.2, "02");
    // Other dual eligible, but without Medicaid coverage
    incomeBandTwoDualCodes.add(0.007, "09");
    // Unknown
    incomeBandTwoDualCodes.add(0.05, "99");
    // Not in code book but present in database
    incomeBandTwoDualCodes.add(0.04, "AA");
    // Not in code book but present in database
    incomeBandTwoDualCodes.add(0.1, "");

    // Qualified Medicare Beneficiary (QMB)-only
    incomeBandThreeDualCodes.add(2.2, "01");
    // Qualifying individuals (QI)
    incomeBandThreeDualCodes.add(0.8, "06");
    // Other dual eligible (not QMB, SLMB, QWDI, or QI) with full Medicaid coverage, including
    // prescription Drugs
    incomeBandThreeDualCodes.add(2.9, "08");
    // Other dual eligible, but without Medicaid coverage
    incomeBandThreeDualCodes.add(0.007, "09");
    // Unknown
    incomeBandThreeDualCodes.add(0.05, "99");
    // Not in code book but present in database
    incomeBandThreeDualCodes.add(0.04, "AA");
    // Not in code book but present in database
    incomeBandThreeDualCodes.add(0.1, "");
  }

  String getDualEligibilityCode(Person person, int year) {
    // TBD add support for the following additional code (%-age in brackets is observed
    // frequency in CMS data):
    // 00 (15.6%) - Not enrolled in Medicare for the month
    String partDCostSharingCode = PartDContractHistory.getPartDCostSharingCode(person);
    if (partDCostSharingCode.equals("03")) {
      return incomeBandThreeDualCodes.next(person);
    } else if (partDCostSharingCode.equals("02")) {
      return incomeBandTwoDualCodes.next(person);
    } else if (partDCostSharingCode.equals("01")) {
      return incomeBandOneDualCodes.next(person);
    } else if (hasESRD(person, year) || isBlind(person)) {
      return "05"; // (0.001%) Qualified Disabled Working Individual (QDWI)
    } else {
      return "NA"; // (68.3%) Non-Medicaid
    }
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
    return mappedDiagnosisCodes.contains("N18.6");
  }

  private static boolean isBlind(Person person) {
    return person.attributes.containsKey(Person.BLINDNESS)
            && person.attributes.get(Person.BLINDNESS).equals(true);
  }
}
