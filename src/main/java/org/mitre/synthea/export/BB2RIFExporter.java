package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.nextFriday;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.mitre.synthea.helpers.RandomNumberGenerator;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider.ProviderType;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

/**
 * BlueButton 2 RIF format exporter. The data format is described here:
 * <a href="https://github.com/CMSgov/beneficiary-fhir-data/tree/master/apps/bfd-model">
 * https://github.com/CMSgov/beneficiary-fhir-data/tree/master/apps/bfd-model</a>.
 */
public class BB2RIFExporter implements Flushable {
  
  private SynchronizedBBLineWriter beneficiary;
  private SynchronizedBBLineWriter beneficiaryHistory;
  private SynchronizedBBLineWriter outpatient;
  private SynchronizedBBLineWriter inpatient;
  private SynchronizedBBLineWriter carrier;
  private SynchronizedBBLineWriter prescription;

  private AtomicInteger beneId; // per patient identifier
  private AtomicInteger claimId; // per claim per encounter
  private AtomicInteger claimGroupId; // per encounter
  private AtomicInteger pdeId; // per medication claim

  private List<LinkedHashMap<String, String>> carrierLookup;
  private CodeMapper conditionCodeMapper;
  private CodeMapper medicationCodeMapper;

  private StateCodeMapper locationMapper;

  private static final String BB2_BENE_ID = "BB2_BENE_ID";
  private static final String BB2_HIC_ID = "BB2_HIC_ID";
  
  /**
   * Day-Month-Year date format.
   */
  private static final SimpleDateFormat BB2_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

  /**
   * Get a date string in the format DD-MMM-YY from the given time stamp.
   */
  private static String bb2DateFromTimestamp(long time) {
    synchronized (BB2_DATE_FORMAT) {
      // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6231579
      return BB2_DATE_FORMAT.format(new Date(time));
    }
  }
  
  /**
   * Create the output folder and files. Write headers to each file.
   */
  private BB2RIFExporter() {
    beneId = new AtomicInteger();
    claimId = new AtomicInteger();
    claimGroupId = new AtomicInteger();
    pdeId = new AtomicInteger();
    try {
      String csv = Utilities.readResource("payers/carriers.csv");

      if (csv.startsWith("\uFEFF")) {
        csv = csv.substring(1); // Removes BOM.
      }
      carrierLookup = SimpleCSV.parse(csv);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    conditionCodeMapper = new CodeMapper("condition_code_map.json");
    medicationCodeMapper = new CodeMapper("medication_code_map.json");
    locationMapper = new StateCodeMapper();
    try {
      prepareOutputFiles();
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Create the output folder and files. Write headers to each file.
   */
  final void prepareOutputFiles() throws IOException {
    // Clean up any existing output files
    if (beneficiary != null) {
      beneficiary.close();
    }
    if (beneficiaryHistory != null) {
      beneficiaryHistory.close();
    }
    if (inpatient != null) {
      inpatient.close();
    }
    if (outpatient != null) {
      outpatient.close();
    }
    if (carrier != null) {
      carrier.close();
    }
    if (prescription != null) {
      prescription.close();
    }

    // Initialize output files
    File output = Exporter.getOutputFolder("bfd", null);
    output.mkdirs();
    Path outputDirectory = output.toPath();
    
    File beneficiaryFile = outputDirectory.resolve("beneficiary.csv").toFile();
    beneficiary = new SynchronizedBBLineWriter(beneficiaryFile);
    beneficiary.writeHeader(BeneficiaryFields.class);

    //    File beneficiaryHistoryFile = outputDirectory.resolve("beneficiary_history.csv").toFile();
    //    beneficiaryHistory = new SynchronizedBBLineWriter(beneficiaryHistoryFile);
    //    beneficiaryHistory.writeHeader(BeneficiaryHistoryFields.class);
    //
    //    File outpatientFile = outputDirectory.resolve("outpatient.csv").toFile();
    //    outpatient = new SynchronizedBBLineWriter(outpatientFile);
    //    outpatient.writeHeader(OutpatientFields.class);

    File inpatientFile = outputDirectory.resolve("inpatient.csv").toFile();
    inpatient = new SynchronizedBBLineWriter(inpatientFile);
    inpatient.writeHeader(InpatientFields.class);

    //    File carrierFile = outputDirectory.resolve("carrier.csv").toFile();
    //    carrier = new SynchronizedBBLineWriter(carrierFile);
    //    carrier.writeHeader(CarrierFields.class);
    //
    //    File prescriptionFile = outputDirectory.resolve("prescription.csv").toFile();
    //    prescription = new SynchronizedBBLineWriter(prescriptionFile);
    //    prescription.writeHeader(PrescriptionFields.class);
  }
  
  /**
   * Export a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  public void export(Person person, long stopTime) throws IOException {
    exportBeneficiary(person, stopTime);
    //    exportBeneficiaryHistory(person, stopTime);
    //    exportOutpatient(person, stopTime);
    exportInpatient(person, stopTime);
    //    exportCarrier(person, stopTime);
    //    exportPrescription(person, stopTime);
  }
  
  /**
   * Export a beneficiary details for single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportBeneficiary(Person person, long stopTime) throws IOException {
    HashMap<BeneficiaryFields, String> fieldValues = new HashMap<>();
    // Optional fields that must be zero
    fieldValues.put(BeneficiaryFields.RFRNC_YR, String.valueOf(getYear(stopTime)));
    fieldValues.put(BeneficiaryFields.A_MO_CNT, String.valueOf(getMonth(stopTime)));
    fieldValues.put(BeneficiaryFields.B_MO_CNT, String.valueOf(getMonth(stopTime)));
    fieldValues.put(BeneficiaryFields.BUYIN_MO_CNT, String.valueOf(getMonth(stopTime)));
    fieldValues.put(BeneficiaryFields.HMO_MO_CNT, "0");
    fieldValues.put(BeneficiaryFields.RDS_MO_CNT, String.valueOf(getMonth(stopTime)));
    fieldValues.put(BeneficiaryFields.AGE, "0");
    fieldValues.put(BeneficiaryFields.DUAL_MO_CNT, "0");
    fieldValues.put(BeneficiaryFields.PLAN_CVRG_MO_CNT, String.valueOf(getMonth(stopTime)));

    // Now put in the real data, some of which might overwrite the above
    fieldValues.put(BeneficiaryFields.DML_IND, "INSERT");
    String personId = (String)person.attributes.get(Person.ID);
    String beneIdStr = Integer.toString(beneId.decrementAndGet());
    person.attributes.put(BB2_BENE_ID, beneIdStr);
    fieldValues.put(BeneficiaryFields.BENE_ID, beneIdStr);
    String hicId = personId.split("-")[0]; // first segment of UUID
    person.attributes.put(BB2_HIC_ID, hicId);
    fieldValues.put(BeneficiaryFields.BENE_CRNT_HIC_NUM, hicId);
    fieldValues.put(BeneficiaryFields.BENE_SEX_IDENT_CD,
            getBB2SexCode((String)person.attributes.get(Person.GENDER)));
    String zipCode = (String)person.attributes.get(Person.ZIP);
    fieldValues.put(BeneficiaryFields.BENE_COUNTY_CD,
            (String)locationMapper.zipToCountyCode(zipCode));
    fieldValues.put(BeneficiaryFields.STATE_CODE,
            locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
    fieldValues.put(BeneficiaryFields.BENE_ZIP_CD,
            (String)person.attributes.get(Person.ZIP));
    fieldValues.put(BeneficiaryFields.BENE_RACE_CD,
            bb2RaceCode(
                    (String)person.attributes.get(Person.ETHNICITY),
                    (String)person.attributes.get(Person.RACE)));
    fieldValues.put(BeneficiaryFields.BENE_SRNM_NAME, 
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BeneficiaryFields.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    fieldValues.put(BeneficiaryFields.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
    fieldValues.put(BeneficiaryFields.RFRNC_YR, String.valueOf(getYear(stopTime)));
    fieldValues.put(BeneficiaryFields.AGE, String.valueOf(ageAtEndOfYear(birthdate, stopTime)));
    if (person.attributes.get(Person.DEATHDATE) != null) {
      long deathDate = (long) person.attributes.get(Person.DEATHDATE);
      fieldValues.put(BeneficiaryFields.DEATH_DT, bb2DateFromTimestamp(deathDate));      
    }
    beneficiary.writeValues(BeneficiaryFields.class, fieldValues);
  }
  
  private String getBB2SexCode(String sex) {
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
   * Export a beneficiary history for single person. Assumes exportBeneficiary
   * was called first to set up various ID on person
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportBeneficiaryHistory(Person person, long stopTime) throws IOException {
    HashMap<BeneficiaryHistoryFields, String> fieldValues = new HashMap<>();
    fieldValues.put(BeneficiaryHistoryFields.DML_IND, "INSERT");
    String beneIdStr = (String)person.attributes.get(BB2_BENE_ID);
    fieldValues.put(BeneficiaryHistoryFields.BENE_ID, beneIdStr);
    String hicId = (String)person.attributes.get(BB2_HIC_ID);
    fieldValues.put(BeneficiaryHistoryFields.BENE_CRNT_HIC_NUM, hicId);
    fieldValues.put(BeneficiaryHistoryFields.BENE_SEX_IDENT_CD,
            getBB2SexCode((String)person.attributes.get(Person.GENDER)));
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    fieldValues.put(BeneficiaryHistoryFields.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
    String zipCode = (String)person.attributes.get(Person.ZIP);
    fieldValues.put(BeneficiaryHistoryFields.BENE_COUNTY_CD,
            (String)locationMapper.zipToCountyCode(zipCode));
    fieldValues.put(BeneficiaryHistoryFields.STATE_CODE,
            locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
    fieldValues.put(BeneficiaryHistoryFields.BENE_ZIP_CD,
            (String)person.attributes.get(Person.ZIP));
    fieldValues.put(BeneficiaryHistoryFields.BENE_RACE_CD,
            bb2RaceCode(
                    (String)person.attributes.get(Person.ETHNICITY),
                    (String)person.attributes.get(Person.RACE)));
    fieldValues.put(BeneficiaryHistoryFields.BENE_SRNM_NAME, 
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BeneficiaryHistoryFields.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
    beneficiaryHistory.writeValues(BeneficiaryHistoryFields.class, fieldValues);
  }

  /**
   * Get the year of a point in time.
   * @param time point in time specified as number of milliseconds since the epoch
   * @return the year as a four figure value, e.g. 1971
   */
  private static int getYear(long time) {
    return 1900 + new Date(time).getYear();
  }

  /**
   * Get the month of a point in time.
   * @param time point in time specified as number of milliseconds since the epoch
   * @return the month of the year
   */
  private static int getMonth(long time) {
    return 1 + new Date(time).getMonth();
  }
  
  /**
   * Calculate the age of a person at the end of the year of a reference point in time.
   * @param birthdate a person's birthdate specified as number of milliseconds since the epoch
   * @param stopTime a reference point in time specified as number of milliseconds since the epoch
   * @return the person's age
   */
  private static int ageAtEndOfYear(long birthdate, long stopTime) {
    return getYear(stopTime) - getYear(birthdate);
  }

  /**
   * Export outpatient claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportOutpatient(Person person, long stopTime) throws IOException {
    HashMap<OutpatientFields, String> fieldValues = new HashMap<>();

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      boolean isAmbulatory = encounter.type.equals(EncounterType.AMBULATORY.toString());
      boolean isOutpatient = encounter.type.equals(EncounterType.OUTPATIENT.toString());
      boolean isUrgent = encounter.type.equals(EncounterType.URGENTCARE.toString());
      boolean isWellness = encounter.type.equals(EncounterType.WELLNESS.toString());
      boolean isPrimary = (ProviderType.PRIMARY == encounter.provider.type);
      int claimId = this.claimId.incrementAndGet();
      int claimGroupId = this.claimGroupId.incrementAndGet();

      if (isPrimary || !(isAmbulatory || isOutpatient || isUrgent || isWellness)) {
        continue;
      }

      fieldValues.clear();
      // The REQUIRED fields
      fieldValues.put(OutpatientFields.DML_IND, "INSERT");
      fieldValues.put(OutpatientFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(OutpatientFields.CLM_ID, "" + claimId);
      fieldValues.put(OutpatientFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(OutpatientFields.FINAL_ACTION, "F");
      fieldValues.put(OutpatientFields.NCH_NEAR_LINE_REC_IDENT_CD, "W"); // W=outpatient
      fieldValues.put(OutpatientFields.NCH_CLM_TYPE_CD, "40"); // 40=outpatient
      fieldValues.put(OutpatientFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(OutpatientFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(OutpatientFields.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(nextFriday(encounter.stop)));
      fieldValues.put(OutpatientFields.CLAIM_QUERY_CODE, "3"); // 1=Interim, 3=Final, 5=Debit
      fieldValues.put(OutpatientFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(OutpatientFields.CLM_FAC_TYPE_CD, "1"); // 1=Hospital, 2=SNF, 7=Dialysis
      fieldValues.put(OutpatientFields.CLM_SRVC_CLSFCTN_TYPE_CD, "3"); // depends on value of above
      fieldValues.put(OutpatientFields.CLM_FREQ_CD, "1"); // 1=Admit-Discharge, 9=Final
      fieldValues.put(OutpatientFields.CLM_PMT_AMT, String.format("%.2f",
              encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(OutpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(OutpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      //fieldValues.put(OutpatientFields.PRVDR_STATE_CD, encounter.provider.state);
      fieldValues.put(OutpatientFields.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      if (encounter.ended) {
        field = "1";
      } else {
        field = "30"; // the patient is still here
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
      }
      fieldValues.put(OutpatientFields.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(OutpatientFields.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      // TODO required in the mapping, but not in the Enum
      // fieldValues.put(OutpatientFields.CLM_IP_ADMSN_TYPE_CD, null);
      // fieldValues.put(OutpatientFields.CLM_PASS_THRU_PER_DIEM_AMT, null);
      // fieldValues.put(OutpatientFields.NCH_BENE_IP_DDCTBL_AMT, null);
      // fieldValues.put(OutpatientFields.NCH_BENE_PTA_COINSRNC_LBLTY_AM, null);
      fieldValues.put(OutpatientFields.NCH_BENE_BLOOD_DDCTBL_LBLTY_AM, "0");
      fieldValues.put(OutpatientFields.NCH_PROFNL_CMPNT_CHRG_AMT, "4"); // fixed $ amount?
      // TODO required in the mapping, but not in the Enum
      // fieldValues.put(OutpatientFields.NCH_IP_NCVRD_CHRG_AMT, null);
      // fieldValues.put(OutpatientFields.NCH_IP_TOT_DDCTN_AMT, null);
      // fieldValues.put(OutpatientFields.CLM_UTLZTN_DAY_CNT, null);
      // fieldValues.put(OutpatientFields.BENE_TOT_COINSRNC_DAYS_CNT, null);
      // fieldValues.put(OutpatientFields.CLM_NON_UTLZTN_DAYS_CNT, null);
      // fieldValues.put(OutpatientFields.NCH_BLOOD_PNTS_FRNSHD_QTY, null);
      // fieldValues.put(OutpatientFields.CLM_DRG_OUTLIER_STAY_CD, null);
      fieldValues.put(OutpatientFields.CLM_LINE_NUM, "1");
      fieldValues.put(OutpatientFields.REV_CNTR, "0001"); // total charge, lots of alternatives
      fieldValues.put(OutpatientFields.REV_CNTR_UNIT_CNT, "0");
      fieldValues.put(OutpatientFields.REV_CNTR_RATE_AMT, "0");
      fieldValues.put(OutpatientFields.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(OutpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      outpatient.writeValues(OutpatientFields.class, fieldValues);
    }
  }
  
  /**
   * Export inpatient claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportInpatient(Person person, long stopTime) throws IOException {
    HashMap<InpatientFields, String> fieldValues = new HashMap<>();

    HealthRecord.Encounter previous = null;
    boolean previousInpatient = false;
    boolean previousEmergency = false;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      boolean isInpatient = encounter.type.equals(EncounterType.INPATIENT.toString());
      boolean isEmergency = encounter.type.equals(EncounterType.EMERGENCY.toString());
      int claimId = this.claimId.incrementAndGet();
      int claimGroupId = this.claimGroupId.incrementAndGet();

      if (!(isInpatient || isEmergency)) {
        previous = encounter;
        previousInpatient = false;
        previousEmergency = false;
        continue;
      }

      fieldValues.clear();
      // The REQUIRED fields
      fieldValues.put(InpatientFields.DML_IND, "INSERT");
      fieldValues.put(InpatientFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(InpatientFields.CLM_ID, "" + claimId);
      fieldValues.put(InpatientFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(InpatientFields.FINAL_ACTION, "F"); // F or V
      fieldValues.put(InpatientFields.NCH_NEAR_LINE_REC_IDENT_CD, "V"); // V = inpatient
      fieldValues.put(InpatientFields.NCH_CLM_TYPE_CD, "60"); // Always 60 for inpatient claims
      fieldValues.put(InpatientFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(InpatientFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(InpatientFields.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(nextFriday(encounter.stop)));
      fieldValues.put(InpatientFields.CLAIM_QUERY_CODE, "3"); // 1=Interim, 3=Final, 5=Debit
      fieldValues.put(InpatientFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(InpatientFields.CLM_FAC_TYPE_CD, "1"); // 1=Hospital, 2=SNF, 7=Dialysis
      fieldValues.put(InpatientFields.CLM_SRVC_CLSFCTN_TYPE_CD, "1"); // depends on value of above
      fieldValues.put(InpatientFields.CLM_FREQ_CD, "1"); // 1=Admit-Discharge, 9=Final
      fieldValues.put(InpatientFields.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(InpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(InpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(InpatientFields.PRVDR_STATE_CD, encounter.provider.state);
      fieldValues.put(InpatientFields.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      if (encounter.ended) {
        field = "1"; // TODO 2=transfer if the next encounter is also inpatient
      } else {
        field = "30"; // the patient is still here
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
      }
      fieldValues.put(InpatientFields.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(InpatientFields.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (isEmergency) {
        field = "1"; // emergency
      } else if (previousEmergency) {
        field = "2"; // urgent
      } else {
        field = "3"; // elective
      }
      fieldValues.put(InpatientFields.CLM_IP_ADMSN_TYPE_CD, field);
      fieldValues.put(InpatientFields.CLM_PASS_THRU_PER_DIEM_AMT, "10"); // fixed $ amount?
      fieldValues.put(InpatientFields.NCH_BENE_IP_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(InpatientFields.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(InpatientFields.NCH_BENE_BLOOD_DDCTBL_LBLTY_AM, "0");
      fieldValues.put(InpatientFields.NCH_PROFNL_CMPNT_CHRG_AMT, "4"); // fixed $ amount?
      fieldValues.put(InpatientFields.NCH_IP_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(InpatientFields.NCH_IP_TOT_DDCTN_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      fieldValues.put(InpatientFields.CLM_UTLZTN_DAY_CNT, "" + days);
      if (days > 60) {
        field = "" + (days - 60);
      } else {
        field = "0";
      }
      fieldValues.put(InpatientFields.BENE_TOT_COINSRNC_DAYS_CNT, field);
      fieldValues.put(InpatientFields.CLM_NON_UTLZTN_DAYS_CNT, "0");
      fieldValues.put(InpatientFields.NCH_BLOOD_PNTS_FRNSHD_QTY, "0");
      if (days > 60) {
        field = "1"; // days outlier
      } else if (encounter.claim.getTotalClaimCost() > 100_000) {
        field = "2"; // cost outlier
      } else {
        field = "0"; // no outlier
      }
      fieldValues.put(InpatientFields.CLM_DRG_OUTLIER_STAY_CD, field);
      fieldValues.put(InpatientFields.CLM_LINE_NUM, "1");
      fieldValues.put(InpatientFields.REV_CNTR, "0001"); // total charge, lots of alternatives
      fieldValues.put(InpatientFields.REV_CNTR_UNIT_CNT, "0");
      fieldValues.put(InpatientFields.REV_CNTR_RATE_AMT, "0");
      fieldValues.put(InpatientFields.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(InpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      // OPTIONAL FIELDS
      // Optional numeric fields apparently need to be filled with zeroes.
      fieldValues.put(InpatientFields.CLM_TOT_PPS_CPTL_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_CPTL_FSP_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_CPTL_OUTLIER_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_CPTL_DSPRPRTNT_SHR_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_CPTL_IME_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_CPTL_EXCPTN_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_OLD_CPTL_HLD_HRMLS_AMT, "0");
      fieldValues.put(InpatientFields.CLM_PPS_CPTL_DRG_WT_NUM, "0");
      fieldValues.put(InpatientFields.BENE_LRD_USED_CNT, "0");
      fieldValues.put(InpatientFields.NCH_DRG_OUTLIER_APRVD_PMT_AMT, "0");
      fieldValues.put(InpatientFields.IME_OP_CLM_VAL_AMT, "0");
      fieldValues.put(InpatientFields.DSH_OP_CLM_VAL_AMT, "0");
      fieldValues.put(InpatientFields.REV_CNTR_NDC_QTY, "0");
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          fieldValues.put(InpatientFields.PRNCPAL_DGNS_CD, conditionCodeMapper.getMapped(
                  encounter.reason.code, person));
        }
      }
      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      if (person.record.present != null && !person.record.present.isEmpty()) {
        List<String> diagnoses = new ArrayList<String>();
        for (String key : person.record.present.keySet()) {
          if (person.record.conditionActive(key)) {
            if (conditionCodeMapper.canMap(key)) {
              diagnoses.add(conditionCodeMapper.getMapped(key, person));
            }
          }
        }
        if (!diagnoses.isEmpty()) {
          for (int i = 0; i < diagnoses.size(); i++) {
            InpatientFields dxField = inpatientDxFields[i];
            fieldValues.put(dxField, diagnoses.get(i));
          }
        }
      }
      // Use the procedures in this encounter to enter mapped values
      if (!encounter.procedures.isEmpty()) {
        List<HealthRecord.Procedure> mappableProcedures = new ArrayList<HealthRecord.Procedure>();
        List<String> mappedCodes = new ArrayList<String>();
        for (HealthRecord.Procedure procedure : encounter.procedures) {
          for (HealthRecord.Code code : procedure.codes) {
            if (conditionCodeMapper.canMap(code.code)) {
              mappableProcedures.add(procedure);
              mappedCodes.add(conditionCodeMapper.getMapped(code.code, person));
              break;
            }
          }
        }
        if (!mappableProcedures.isEmpty()) {
          for (int i = 0; i < mappableProcedures.size(); i++) {
            InpatientFields[] pxField = inpatientPxFields[i];
            fieldValues.put(pxField[0], mappedCodes.get(i));
            fieldValues.put(pxField[1], "0"); // 0=ICD10
            fieldValues.put(pxField[2], bb2DateFromTimestamp(mappableProcedures.get(i).start));
          }
        }
      }
      previous = encounter;
      previousInpatient = isInpatient;
      previousEmergency = isEmergency;

      inpatient.writeValues(InpatientFields.class, fieldValues);
    }
  }

  /**
   * Export carrier claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportCarrier(Person person, long stopTime) throws IOException {
    HashMap<CarrierFields, String> fieldValues = new HashMap<>();

    HealthRecord.Encounter previous = null;
    double latestHemoglobin = 0;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      boolean isPrimary = (ProviderType.PRIMARY == encounter.provider.type);

      int claimId = this.claimId.incrementAndGet();
      int claimGroupId = this.claimGroupId.incrementAndGet();

      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      if (!isPrimary) {
        previous = encounter;
        continue;
      }

      fieldValues.clear();
      // The REQUIRED fields
      fieldValues.put(CarrierFields.DML_IND, "INSERT");
      fieldValues.put(CarrierFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(CarrierFields.CLM_ID, "" + claimId);
      fieldValues.put(CarrierFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(CarrierFields.FINAL_ACTION, "F"); // F or V
      fieldValues.put(CarrierFields.NCH_NEAR_LINE_REC_IDENT_CD, "O"); // O=physician
      fieldValues.put(CarrierFields.NCH_CLM_TYPE_CD, "71"); // local carrier, non-DME
      fieldValues.put(CarrierFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(CarrierFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(CarrierFields.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(nextFriday(encounter.stop)));
      fieldValues.put(CarrierFields.CARR_CLM_ENTRY_CD, "1");
      fieldValues.put(CarrierFields.CLM_DISP_CD, "01");
      fieldValues.put(CarrierFields.CARR_NUM,
          getCarrier(encounter.provider.state, CarrierFields.CARR_NUM));
      fieldValues.put(CarrierFields.CARR_CLM_PMT_DNL_CD, "1"); // 1=paid to physician
      fieldValues.put(CarrierFields.CLM_PMT_AMT, 
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(CarrierFields.CARR_CLM_PRMRY_PYR_PD_AMT, "0");
      } else {
        fieldValues.put(CarrierFields.CARR_CLM_PRMRY_PYR_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(CarrierFields.NCH_CLM_PRVDR_PMT_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CarrierFields.NCH_CLM_BENE_PMT_AMT, "0");
      fieldValues.put(CarrierFields.NCH_CARR_CLM_SBMTD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CarrierFields.NCH_CARR_CLM_ALOWD_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(CarrierFields.CARR_CLM_CASH_DDCTBL_APLD_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(CarrierFields.CARR_CLM_RFRNG_PIN_NUM, encounter.provider.id);
      fieldValues.put(CarrierFields.LINE_NUM, "1");
      fieldValues.put(CarrierFields.CARR_PRFRNG_PIN_NUM, encounter.provider.id);
      fieldValues.put(CarrierFields.CARR_LINE_PRVDR_TYPE_CD, "0");
      fieldValues.put(CarrierFields.TAX_NUM,
          "" + encounter.clinician.attributes.get(Person.IDENTIFIER_SSN));
      fieldValues.put(CarrierFields.CARR_LINE_RDCD_PMT_PHYS_ASTN_C, "0");
      fieldValues.put(CarrierFields.LINE_SRVC_CNT, "" + encounter.claim.items.size());
      fieldValues.put(CarrierFields.LINE_CMS_TYPE_SRVC_CD, "1");
      fieldValues.put(CarrierFields.LINE_PLACE_OF_SRVC_CD, "11"); // 11=office
      fieldValues.put(CarrierFields.CARR_LINE_PRCNG_LCLTY_CD,
          getCarrier(encounter.provider.state, CarrierFields.CARR_LINE_PRCNG_LCLTY_CD));
      fieldValues.put(CarrierFields.LINE_NCH_PMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(CarrierFields.LINE_BENE_PMT_AMT, "0");
      fieldValues.put(CarrierFields.LINE_PRVDR_PMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(CarrierFields.LINE_BENE_PTB_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(CarrierFields.LINE_BENE_PRMRY_PYR_PD_AMT, "0");
      fieldValues.put(CarrierFields.LINE_COINSRNC_AMT,
          String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(CarrierFields.LINE_SBMTD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CarrierFields.LINE_ALOWD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      // length of encounter in minutes
      fieldValues.put(CarrierFields.CARR_LINE_MTUS_CNT,
          "" + ((encounter.stop - encounter.start) / (1000 * 60)));

      fieldValues.put(CarrierFields.LINE_HCT_HGB_RSLT_NUM,
          "" + latestHemoglobin);
      fieldValues.put(CarrierFields.CARR_LINE_ANSTHSA_UNIT_CNT, "0");

      carrier.writeValues(CarrierFields.class, fieldValues);
    }
  }

  /**
   * Export prescription claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportPrescription(Person person, long stopTime) throws IOException {
    HashMap<PrescriptionFields, String> fieldValues = new HashMap<>();
    HashMap<String, Integer> fillNum = new HashMap<>();
    double costs = 0;
    int costYear = 0;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      for (Medication medication : encounter.medications) {
        if (!medicationCodeMapper.canMap(medication.codes.get(0).code)) {
          continue; // skip codes that can't be mapped to NDC
        }

        int pdeId = this.pdeId.incrementAndGet();
        int claimGroupId = this.claimGroupId.incrementAndGet();

        fieldValues.clear();
        // The REQUIRED fields
        fieldValues.put(PrescriptionFields.DML_IND, "INSERT");
        fieldValues.put(PrescriptionFields.PDE_ID, "" + pdeId);
        fieldValues.put(PrescriptionFields.CLM_GRP_ID, "" + claimGroupId);
        fieldValues.put(PrescriptionFields.FINAL_ACTION, "F");
        fieldValues.put(PrescriptionFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
        fieldValues.put(PrescriptionFields.SRVC_DT, bb2DateFromTimestamp(encounter.start));
        fieldValues.put(PrescriptionFields.SRVC_PRVDR_ID_QLFYR_CD, "01"); // undefined
        fieldValues.put(PrescriptionFields.SRVC_PRVDR_ID, encounter.provider.id);
        fieldValues.put(PrescriptionFields.PRSCRBR_ID_QLFYR_CD, "01"); // undefined
        fieldValues.put(PrescriptionFields.PRSCRBR_ID,
            "" + (9_999_999_999L - encounter.clinician.identifier));
        fieldValues.put(PrescriptionFields.RX_SRVC_RFRNC_NUM, "" + pdeId);
        fieldValues.put(PrescriptionFields.PROD_SRVC_ID, 
                medicationCodeMapper.getMapped(medication.codes.get(0).code, person));
        // H=hmo, R=ppo, S=stand-alone, E=employer direct, X=limited income
        fieldValues.put(PrescriptionFields.PLAN_CNTRCT_REC_ID,
            ("R" + Math.abs(
                UUID.fromString(medication.claim.payer.uuid)
                .getMostSignificantBits())).substring(0, 5));
        fieldValues.put(PrescriptionFields.PLAN_PBP_REC_NUM, "999");
        // 0=not specified, 1=not compound, 2=compound
        fieldValues.put(PrescriptionFields.CMPND_CD, "0");
        fieldValues.put(PrescriptionFields.DAW_PROD_SLCTN_CD, "" + (int) person.rand(0, 9));
        fieldValues.put(PrescriptionFields.QTY_DSPNSD_NUM, "" + getQuantity(medication, stopTime));
        fieldValues.put(PrescriptionFields.DAYS_SUPLY_NUM, "" + getDays(medication, stopTime));
        Integer fill = 1;
        if (fillNum.containsKey(medication.codes.get(0).code)) {
          fill = 1 + fillNum.get(medication.codes.get(0).code);
        }
        fillNum.put(medication.codes.get(0).code, fill);
        fieldValues.put(PrescriptionFields.FILL_NUM, "" + fill);
        fieldValues.put(PrescriptionFields.DRUG_CVRG_STUS_CD, "C");
        int year = Utilities.getYear(medication.start);
        if (year != costYear) {
          costYear = year;
          costs = 0;
        }
        costs += medication.claim.getPatientCost();
        if (costs <= 4550.00) {
          fieldValues.put(PrescriptionFields.GDC_BLW_OOPT_AMT, String.format("%.2f", costs));
          fieldValues.put(PrescriptionFields.GDC_ABV_OOPT_AMT, "0");
        } else {
          fieldValues.put(PrescriptionFields.GDC_BLW_OOPT_AMT, "4550.00");
          fieldValues.put(PrescriptionFields.GDC_ABV_OOPT_AMT,
                  String.format("%.2f", (costs - 4550)));
        }
        fieldValues.put(PrescriptionFields.PTNT_PAY_AMT, 
                String.format("%.2f", medication.claim.getPatientCost()));
        fieldValues.put(PrescriptionFields.OTHR_TROOP_AMT, "0");
        fieldValues.put(PrescriptionFields.LICS_AMT, "0");
        fieldValues.put(PrescriptionFields.PLRO_AMT, "0");
        fieldValues.put(PrescriptionFields.CVRD_D_PLAN_PD_AMT,
            String.format("%.2f", medication.claim.getCoveredCost()));
        fieldValues.put(PrescriptionFields.NCVRD_PLAN_PD_AMT,
            String.format("%.2f", medication.claim.getPatientCost()));
        fieldValues.put(PrescriptionFields.TOT_RX_CST_AMT,
            String.format("%.2f", medication.claim.getTotalClaimCost()));
        fieldValues.put(PrescriptionFields.RPTD_GAP_DSCNT_NUM, "0");
        fieldValues.put(PrescriptionFields.PHRMCY_SRVC_TYPE_CD, "0" + (int) person.rand(1, 8));
        // 00=not specified, 01=home, 02=SNF, 03=long-term, 11=hospice, 14=homeless
        if (person.attributes.containsKey("homeless")
            && ((Boolean) person.attributes.get("homeless") == true)) {
          fieldValues.put(PrescriptionFields.PTNT_RSDNC_CD, "14");
        } else {
          fieldValues.put(PrescriptionFields.PTNT_RSDNC_CD, "01");
        }

        prescription.writeValues(PrescriptionFields.class, fieldValues);
      }
    }
  }

  /**
   * Flush contents of any buffered streams to disk.
   * @throws IOException if something goes wrong
   */
  @Override
  public void flush() throws IOException {
    beneficiary.flush();
    //    beneficiaryHistory.flush();
    inpatient.flush();
    //    outpatient.flush();
    //    carrier.flush();
    //    prescription.flush();
  }

  /**
   * Get the BB2 race code. BB2 uses a single code to represent race and ethnicity, we assume
   * ethnicity gets priority here.
   * @param ethnicity the Synthea ethnicity
   * @param race the Synthea race
   * @return the BB2 race code
   */
  private String bb2RaceCode(String ethnicity, String race) {
    if ("hispanic".equals(ethnicity)) {
      return "5";
    } else {
      String bbRaceCode = "0"; // unknown
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

  private String getCarrier(String state, CarrierFields column) {
    for (LinkedHashMap<String, String> row : carrierLookup) {
      if (row.get("STATE").equals(state) || row.get("STATE_CODE").equals(state)) {
        return row.get(column.toString());
      }
    }
    return "0";
  }

  private int getQuantity(Medication medication, long stopTime) {
    double amountPerDay = 1;
    double days = getDays(medication, stopTime);

    if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("dosage")) {
      JsonObject dosage = medication.prescriptionDetails.getAsJsonObject("dosage");
      long amount = dosage.get("amount").getAsLong();
      long frequency = dosage.get("frequency").getAsLong();
      long period = dosage.get("period").getAsLong();
      String units = dosage.get("unit").getAsString();
      long periodTime = Utilities.convertTime(units, period);

      long perPeriod = amount * frequency;
      amountPerDay = (double) ((double) (perPeriod * periodTime) / (1000.0 * 60 * 60 * 24));
      if (amountPerDay == 0) {
        amountPerDay = 1;
      }
    }

    return (int) (amountPerDay * days);
  }

  private int getDays(Medication medication, long stopTime) {
    double days = 1;
    long stop = medication.stop;
    if (stop == 0L) {
      stop = stopTime;
    }
    long medDuration = stop - medication.start;
    days = (double) (medDuration / (1000 * 60 * 60 * 24));

    if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("duration")) {
      JsonObject duration = medication.prescriptionDetails.getAsJsonObject("duration");
      long quantity = duration.get("quantity").getAsLong();
      String unit = duration.get("unit").getAsString();
      long durationTime = Utilities.convertTime(unit, quantity);
      double durationTimeInDays = (double) (durationTime / (1000 * 60 * 60 * 24));
      if (durationTimeInDays > days) {
        days = durationTimeInDays;
      }
    }
    return (int) days;
  }

  /**
   * Utility class for converting state names and abbreviations to provider state codes.
   */
  class StateCodeMapper {
    private HashMap<String, String> providerStateCodes;
    private Map<String, String> stateToAbbrev = this.buildStateAbbrevTable();
    private Map<String, String> abbrevToState;
    private HashMap<String, String> ssaTable;

    public StateCodeMapper() {
      this.providerStateCodes = this.buildProviderStateTable();
      this.stateToAbbrev = this.buildStateAbbrevTable();
      // support two-way conversion between state name and abbreviations
      Map<String, String> abbrevToState = new HashMap<String, String>();
      for (Map.Entry<String, String> entry : stateToAbbrev.entrySet()) {
        abbrevToState.put(entry.getValue(), entry.getKey());
      }
      this.abbrevToState = abbrevToState;
      this.ssaTable = buildSSATable();
    }

    /**
     * Return state code for a given state.
     * @param state (either state name or abbreviation)
     * @return 2-digit state code
     */
    private String getStateCode(String state) {
      if (state.length() == 2) {
        state = this.changeStateFormat(state);
      } else {
        state = this.capitalizeWords(state);
      }
      String res = this.providerStateCodes.getOrDefault(state, "NONE");
      return res;
    }
    
    /**
     * Switch between state name and abbreviation. If state is abbreviation, will return name,
     * and vice versa
     * @param state abbreviation or name of state
     * @return
     */
    private String changeStateFormat(String state) {
      if (state.length() == 2) {
        return this.abbrevToState.getOrDefault(state.toUpperCase(), null);
      } else {
        String stateClean = this.capitalizeWords(state.toLowerCase());
        return this.stateToAbbrev.getOrDefault(stateClean, null);
      }
    }

    private Map<String, String> buildStateAbbrevTable() {
      Map<String, String> states = new HashMap<String, String>();
      states.put("Alabama","AL");
      states.put("Alaska","AK");
      states.put("Alberta","AB");
      states.put("American Samoa","AS");
      states.put("Arizona","AZ");
      states.put("Arkansas","AR");
      states.put("Armed Forces (AE)","AE");
      states.put("Armed Forces Americas","AA");
      states.put("Armed Forces Pacific","AP");
      states.put("British Columbia","BC");
      states.put("California","CA");
      states.put("Colorado","CO");
      states.put("Connecticut","CT");
      states.put("Delaware","DE");
      states.put("District Of Columbia","DC");
      states.put("Florida","FL");
      states.put("Georgia","GA");
      states.put("Guam","GU");
      states.put("Hawaii","HI");
      states.put("Idaho","ID");
      states.put("Illinois","IL");
      states.put("Indiana","IN");
      states.put("Iowa","IA");
      states.put("Kansas","KS");
      states.put("Kentucky","KY");
      states.put("Louisiana","LA");
      states.put("Maine","ME");
      states.put("Manitoba","MB");
      states.put("Maryland","MD");
      states.put("Massachusetts","MA");
      states.put("Michigan","MI");
      states.put("Minnesota","MN");
      states.put("Mississippi","MS");
      states.put("Missouri","MO");
      states.put("Montana","MT");
      states.put("Nebraska","NE");
      states.put("Nevada","NV");
      states.put("New Brunswick","NB");
      states.put("New Hampshire","NH");
      states.put("New Jersey","NJ");
      states.put("New Mexico","NM");
      states.put("New York","NY");
      states.put("Newfoundland","NF");
      states.put("North Carolina","NC");
      states.put("North Dakota","ND");
      states.put("Northwest Territories","NT");
      states.put("Nova Scotia","NS");
      states.put("Nunavut","NU");
      states.put("Ohio","OH");
      states.put("Oklahoma","OK");
      states.put("Ontario","ON");
      states.put("Oregon","OR");
      states.put("Pennsylvania","PA");
      states.put("Prince Edward Island","PE");
      states.put("Puerto Rico","PR");
      states.put("Quebec","QC");
      states.put("Rhode Island","RI");
      states.put("Saskatchewan","SK");
      states.put("South Carolina","SC");
      states.put("South Dakota","SD");
      states.put("Tennessee","TN");
      states.put("Texas","TX");
      states.put("Utah","UT");
      states.put("Vermont","VT");
      states.put("Virgin Islands","VI");
      states.put("Virginia","VA");
      states.put("Washington","WA");
      states.put("West Virginia","WV");
      states.put("Wisconsin","WI");
      states.put("Wyoming","WY");
      states.put("Yukon Territory","YT");
      return states;
    }
    
    private  HashMap<String, String> buildProviderStateTable() {
      HashMap<String, String> providerStateCode = new HashMap<String, String>();
      providerStateCode.put("Alabama", "01");
      providerStateCode.put("Alaska", "02");
      providerStateCode.put("Arizona", "03");
      providerStateCode.put("Arkansas", "04");
      providerStateCode.put("California", "05");
      providerStateCode.put("Colorado", "06");
      providerStateCode.put("Connecticut", "07");
      providerStateCode.put("Delaware", "08");
      providerStateCode.put("District of Columbia", "09");
      providerStateCode.put("Florida", "10");
      providerStateCode.put("Georgia", "11");
      providerStateCode.put("Hawaii", "12");
      providerStateCode.put("Idaho", "13");
      providerStateCode.put("Illinois", "14");
      providerStateCode.put("Indiana", "15");
      providerStateCode.put("Iowa", "16");
      providerStateCode.put("Kansas", "17");
      providerStateCode.put("Kentucky", "18");
      providerStateCode.put("Louisiana", "19");
      providerStateCode.put("Maine", "20");
      providerStateCode.put("Maryland", "21");
      providerStateCode.put("Massachusetts", "22");
      providerStateCode.put("Michigan", "23");
      providerStateCode.put("Minnesota", "24");
      providerStateCode.put("Mississippi", "25");
      providerStateCode.put("Missouri", "26");
      providerStateCode.put("Montana", "27");
      providerStateCode.put("Nebraska", "28");
      providerStateCode.put("Nevada", "29");
      providerStateCode.put("New Hampshire", "30");
      providerStateCode.put("New Jersey", "31");
      providerStateCode.put("New Mexico", "32");
      providerStateCode.put("New York", "33");
      providerStateCode.put("North Carolina", "34");
      providerStateCode.put("North Dakota", "35");
      providerStateCode.put("Ohio", "36");
      providerStateCode.put("Oklahoma", "37");
      providerStateCode.put("Oregon", "38");
      providerStateCode.put("Pennsylvania", "39");
      providerStateCode.put("Puerto Rico", "40");
      providerStateCode.put("Rhode Island", "41");
      providerStateCode.put("South Carolina", "42");
      providerStateCode.put("South Dakota", "43");
      providerStateCode.put("Tennessee", "44");
      providerStateCode.put("Texas", "45");
      providerStateCode.put("Utah", "46");
      providerStateCode.put("Vermont", "47");
      providerStateCode.put("Virgin Islands", "48");
      providerStateCode.put("Virginia", "49");
      providerStateCode.put("Washington", "50");
      providerStateCode.put("West Virginia", "51");
      providerStateCode.put("Wisconsin", "52");
      providerStateCode.put("Wyoming", "53");
      providerStateCode.put("Africa", "54");
      providerStateCode.put("California", "55");
      providerStateCode.put("Canada & Islands", "56");
      providerStateCode.put("Central America and West Indies", "57");
      providerStateCode.put("Europe", "58");
      providerStateCode.put("Mexico", "59");
      providerStateCode.put("Oceania", "60");
      providerStateCode.put("Philippines", "61");
      providerStateCode.put("South America", "62");
      providerStateCode.put("U.S. Possessions", "63");
      providerStateCode.put("American Samoa", "64");
      providerStateCode.put("Guam", "65");
      providerStateCode.put("Commonwealth of the Northern Marianas Islands", "66");
      return providerStateCode;
    }

    /**
     * Get the SSA county code for a given zipcode. Will eventually use countyname, but wanted
     * to use a unique key
     * @param zipcode the ZIP
     * @return
     */
    private String zipToCountyCode(String zipcode) {
      // TODO: fix this. Currently hard-coding default because required field, but will
      // eventually add name-based matching as fallback
      return ssaTable.getOrDefault(zipcode, "22090");
    }

    private HashMap<String, String> buildSSATable() {
      HashMap<String, String> ssaTable = new HashMap<String, String>();
      List<LinkedHashMap<String, String>> csvData;

      try {
        String csv = Utilities.readResource("geography/fipscodes.csv");

        if (csv.startsWith("\uFEFF")) {
          csv = csv.substring(1); // Removes BOM.
        }
        csvData = SimpleCSV.parse(csv);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      for (LinkedHashMap<String, String> row : csvData) {
        String zipcode = row.get("zip");

        if (zipcode.length() > 3) {
          if (zipcode.length() == 4) {
            zipcode = "0" + zipcode;
          }
        }

        String ssaCode = row.get("ssacounty");
        ssaTable.put(zipcode, ssaCode);
      }
      return ssaTable;
    }
    
    private String capitalizeWords(String str) {
      String[] words = str.split("\\s");
      String capitalizeWords = "";
      for (String w: words) {
        String first = w.substring(0,1);
        String afterFirst = w.substring(1);
        capitalizeWords += first.toUpperCase() + afterFirst + " ";
      }
      return capitalizeWords.trim();
    }
  }

  /**
   * Defines the fields used in the beneficiary file. Note that order is significant, columns will
   * be written in the order specified.
   */
  private enum BeneficiaryFields {
    DML_IND,
    BENE_ID,
    STATE_CODE,
    BENE_COUNTY_CD,
    BENE_ZIP_CD,
    BENE_BIRTH_DT,
    BENE_SEX_IDENT_CD,
    BENE_RACE_CD,
    BENE_ENTLMT_RSN_ORIG,
    BENE_ENTLMT_RSN_CURR,
    BENE_ESRD_IND,
    BENE_MDCR_STATUS_CD,
    BENE_PTA_TRMNTN_CD,
    BENE_PTB_TRMNTN_CD,
    // BENE_PTD_TRMNTN_CD, // The spreadsheet has a gap for this column, examples do not include it
    BENE_CRNT_HIC_NUM,
    BENE_SRNM_NAME,
    BENE_GVN_NAME,
    BENE_MDL_NAME,
    MBI_NUM,
    DEATH_DT,
    RFRNC_YR,
    A_MO_CNT,
    B_MO_CNT,
    BUYIN_MO_CNT,
    HMO_MO_CNT,
    RDS_MO_CNT,
    ENRL_SRC,
    SAMPLE_GROUP,
    EFIVEPCT,
    CRNT_BIC,
    AGE,
    COVSTART,
    DUAL_MO_CNT,
    FIPS_STATE_CNTY_JAN_CD,
    FIPS_STATE_CNTY_FEB_CD,
    FIPS_STATE_CNTY_MAR_CD,
    FIPS_STATE_CNTY_APR_CD,
    FIPS_STATE_CNTY_MAY_CD,
    FIPS_STATE_CNTY_JUN_CD,
    FIPS_STATE_CNTY_JUL_CD,
    FIPS_STATE_CNTY_AUG_CD,
    FIPS_STATE_CNTY_SEPT_CD,
    FIPS_STATE_CNTY_OCT_CD,
    FIPS_STATE_CNTY_NOV_CD,
    FIPS_STATE_CNTY_DEC_CD,
    V_DOD_SW,
    RTI_RACE_CD,
    MDCR_STUS_JAN_CD,
    MDCR_STUS_FEB_CD,
    MDCR_STUS_MAR_CD,
    MDCR_STUS_APR_CD,
    MDCR_STUS_MAY_CD,
    MDCR_STUS_JUN_CD,
    MDCR_STUS_JUL_CD,
    MDCR_STUS_AUG_CD,
    MDCR_STUS_SEPT_CD,
    MDCR_STUS_OCT_CD,
    MDCR_STUS_NOV_CD,
    MDCR_STUS_DEC_CD,
    PLAN_CVRG_MO_CNT,
    MDCR_ENTLMT_BUYIN_1_IND,
    MDCR_ENTLMT_BUYIN_2_IND,
    MDCR_ENTLMT_BUYIN_3_IND,
    MDCR_ENTLMT_BUYIN_4_IND,
    MDCR_ENTLMT_BUYIN_5_IND,
    MDCR_ENTLMT_BUYIN_6_IND,
    MDCR_ENTLMT_BUYIN_7_IND,
    MDCR_ENTLMT_BUYIN_8_IND,
    MDCR_ENTLMT_BUYIN_9_IND,
    MDCR_ENTLMT_BUYIN_10_IND,
    MDCR_ENTLMT_BUYIN_11_IND,
    MDCR_ENTLMT_BUYIN_12_IND,
    HMO_1_IND,
    HMO_2_IND,
    HMO_3_IND,
    HMO_4_IND,
    HMO_5_IND,
    HMO_6_IND,
    HMO_7_IND,
    HMO_8_IND,
    HMO_9_IND,
    HMO_10_IND,
    HMO_11_IND,
    HMO_12_IND,
    PTC_CNTRCT_JAN_ID,
    PTC_CNTRCT_FEB_ID,
    PTC_CNTRCT_MAR_ID,
    PTC_CNTRCT_APR_ID,
    PTC_CNTRCT_MAY_ID,
    PTC_CNTRCT_JUN_ID,
    PTC_CNTRCT_JUL_ID,
    PTC_CNTRCT_AUG_ID,
    PTC_CNTRCT_SEPT_ID,
    PTC_CNTRCT_OCT_ID,
    PTC_CNTRCT_NOV_ID,
    PTC_CNTRCT_DEC_ID,
    PTC_PBP_JAN_ID,
    PTC_PBP_FEB_ID,
    PTC_PBP_MAR_ID,
    PTC_PBP_APR_ID,
    PTC_PBP_MAY_ID,
    PTC_PBP_JUN_ID,
    PTC_PBP_JUL_ID,
    PTC_PBP_AUG_ID,
    PTC_PBP_SEPT_ID,
    PTC_PBP_OCT_ID,
    PTC_PBP_NOV_ID,
    PTC_PBP_DEC_ID,
    PTC_PLAN_TYPE_JAN_CD,
    PTC_PLAN_TYPE_FEB_CD,
    PTC_PLAN_TYPE_MAR_CD,
    PTC_PLAN_TYPE_APR_CD,
    PTC_PLAN_TYPE_MAY_CD,
    PTC_PLAN_TYPE_JUN_CD,
    PTC_PLAN_TYPE_JUL_CD,
    PTC_PLAN_TYPE_AUG_CD,
    PTC_PLAN_TYPE_SEPT_CD,
    PTC_PLAN_TYPE_OCT_CD,
    PTC_PLAN_TYPE_NOV_CD,
    PTC_PLAN_TYPE_DEC_CD,
    PTD_CNTRCT_JAN_ID,
    PTD_CNTRCT_FEB_ID,
    PTD_CNTRCT_MAR_ID,
    PTD_CNTRCT_APR_ID,
    PTD_CNTRCT_MAY_ID,
    PTD_CNTRCT_JUN_ID,
    PTD_CNTRCT_JUL_ID,
    PTD_CNTRCT_AUG_ID,
    PTD_CNTRCT_SEPT_ID,
    PTD_CNTRCT_OCT_ID,
    PTD_CNTRCT_NOV_ID,
    PTD_CNTRCT_DEC_ID,
    PTD_PBP_JAN_ID,
    PTD_PBP_FEB_ID,
    PTD_PBP_MAR_ID,
    PTD_PBP_APR_ID,
    PTD_PBP_MAY_ID,
    PTD_PBP_JUN_ID,
    PTD_PBP_JUL_ID,
    PTD_PBP_AUG_ID,
    PTD_PBP_SEPT_ID,
    PTD_PBP_OCT_ID,
    PTD_PBP_NOV_ID,
    PTD_PBP_DEC_ID,
    PTD_SGMT_JAN_ID,
    PTD_SGMT_FEB_ID,
    PTD_SGMT_MAR_ID,
    PTD_SGMT_APR_ID,
    PTD_SGMT_MAY_ID,
    PTD_SGMT_JUN_ID,
    PTD_SGMT_JUL_ID,
    PTD_SGMT_AUG_ID,
    PTD_SGMT_SEPT_ID,
    PTD_SGMT_OCT_ID,
    PTD_SGMT_NOV_ID,
    PTD_SGMT_DEC_ID,
    RDS_JAN_IND,
    RDS_FEB_IND,
    RDS_MAR_IND,
    RDS_APR_IND,
    RDS_MAY_IND,
    RDS_JUN_IND,
    RDS_JUL_IND,
    RDS_AUG_IND,
    RDS_SEPT_IND,
    RDS_OCT_IND,
    RDS_NOV_IND,
    RDS_DEC_IND,
    META_DUAL_ELGBL_STUS_JAN_CD,
    META_DUAL_ELGBL_STUS_FEB_CD,
    META_DUAL_ELGBL_STUS_MAR_CD,
    META_DUAL_ELGBL_STUS_APR_CD,
    META_DUAL_ELGBL_STUS_MAY_CD,
    META_DUAL_ELGBL_STUS_JUN_CD,
    META_DUAL_ELGBL_STUS_JUL_CD,
    META_DUAL_ELGBL_STUS_AUG_CD,
    META_DUAL_ELGBL_STUS_SEPT_CD,
    META_DUAL_ELGBL_STUS_OCT_CD,
    META_DUAL_ELGBL_STUS_NOV_CD,
    META_DUAL_ELGBL_STUS_DEC_CD,
    CST_SHR_GRP_JAN_CD,
    CST_SHR_GRP_FEB_CD,
    CST_SHR_GRP_MAR_CD,
    CST_SHR_GRP_APR_CD,
    CST_SHR_GRP_MAY_CD,
    CST_SHR_GRP_JUN_CD,
    CST_SHR_GRP_JUL_CD,
    CST_SHR_GRP_AUG_CD,
    CST_SHR_GRP_SEPT_CD,
    CST_SHR_GRP_OCT_CD,
    CST_SHR_GRP_NOV_CD,
    CST_SHR_GRP_DEC_CD
  }
  
  private enum BeneficiaryHistoryFields {
    DML_IND,
    BENE_ID,
    STATE_CODE,
    BENE_COUNTY_CD,
    BENE_ZIP_CD,
    BENE_BIRTH_DT,
    BENE_SEX_IDENT_CD,
    BENE_RACE_CD,
    BENE_ENTLMT_RSN_ORIG,
    BENE_ENTLMT_RSN_CURR,
    BENE_ESRD_IND,
    BENE_MDCR_STATUS_CD,
    BENE_PTA_TRMNTN_CD,
    BENE_PTB_TRMNTN_CD,
    BENE_CRNT_HIC_NUM,
    BENE_SRNM_NAME,
    BENE_GVN_NAME,
    BENE_MDL_NAME,
    MBI_NUM      
  }
  
  private enum OutpatientFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    CLAIM_QUERY_CODE,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    OP_PHYSN_UPIN,
    OP_PHYSN_NPI,
    OT_PHYSN_UPIN,
    OT_PHYSN_NPI,
    CLM_MCO_PD_SW,
    PTNT_DSCHRG_STUS_CD,
    CLM_TOT_CHRG_AMT,
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    NCH_PROFNL_CMPNT_CHRG_AMT,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    ICD_PRCDR_CD1,
    ICD_PRCDR_VRSN_CD1,
    PRCDR_DT1,
    ICD_PRCDR_CD2,
    ICD_PRCDR_VRSN_CD2,
    PRCDR_DT2,
    ICD_PRCDR_CD3,
    ICD_PRCDR_VRSN_CD3,
    PRCDR_DT3,
    ICD_PRCDR_CD4,
    ICD_PRCDR_VRSN_CD4,
    PRCDR_DT4,
    ICD_PRCDR_CD5,
    ICD_PRCDR_VRSN_CD5,
    PRCDR_DT5,
    ICD_PRCDR_CD6,
    ICD_PRCDR_VRSN_CD6,
    PRCDR_DT6,
    ICD_PRCDR_CD7,
    ICD_PRCDR_VRSN_CD7,
    PRCDR_DT7,
    ICD_PRCDR_CD8,
    ICD_PRCDR_VRSN_CD8,
    PRCDR_DT8,
    ICD_PRCDR_CD9,
    ICD_PRCDR_VRSN_CD9,
    PRCDR_DT9,
    ICD_PRCDR_CD10,
    ICD_PRCDR_VRSN_CD10,
    PRCDR_DT10,
    ICD_PRCDR_CD11,
    ICD_PRCDR_VRSN_CD11,
    PRCDR_DT11,
    ICD_PRCDR_CD12,
    ICD_PRCDR_VRSN_CD12,
    PRCDR_DT12,
    ICD_PRCDR_CD13,
    ICD_PRCDR_VRSN_CD13,
    PRCDR_DT13,
    ICD_PRCDR_CD14,
    ICD_PRCDR_VRSN_CD14,
    PRCDR_DT14,
    ICD_PRCDR_CD15,
    ICD_PRCDR_VRSN_CD15,
    PRCDR_DT15,
    ICD_PRCDR_CD16,
    ICD_PRCDR_VRSN_CD16,
    PRCDR_DT16,
    ICD_PRCDR_CD17,
    ICD_PRCDR_VRSN_CD17,
    PRCDR_DT17,
    ICD_PRCDR_CD18,
    ICD_PRCDR_VRSN_CD18,
    PRCDR_DT18,
    ICD_PRCDR_CD19,
    ICD_PRCDR_VRSN_CD19,
    PRCDR_DT19,
    ICD_PRCDR_CD20,
    ICD_PRCDR_VRSN_CD20,
    PRCDR_DT20,
    ICD_PRCDR_CD21,
    ICD_PRCDR_VRSN_CD21,
    PRCDR_DT21,
    ICD_PRCDR_CD22,
    ICD_PRCDR_VRSN_CD22,
    PRCDR_DT22,
    ICD_PRCDR_CD23,
    ICD_PRCDR_VRSN_CD23,
    PRCDR_DT23,
    ICD_PRCDR_CD24,
    ICD_PRCDR_VRSN_CD24,
    PRCDR_DT24,
    ICD_PRCDR_CD25,
    ICD_PRCDR_VRSN_CD25,
    PRCDR_DT25,
    RSN_VISIT_CD1,
    RSN_VISIT_VRSN_CD1,
    RSN_VISIT_CD2,
    RSN_VISIT_VRSN_CD2,
    RSN_VISIT_CD3,
    RSN_VISIT_VRSN_CD3,
    NCH_BENE_PTB_DDCTBL_AMT,
    NCH_BENE_PTB_COINSRNC_AMT,
    CLM_OP_PRVDR_PMT_AMT,
    CLM_OP_BENE_PMT_AMT,
    CLM_LINE_NUM,
    REV_CNTR,
    REV_CNTR_DT,
    REV_CNTR_1ST_ANSI_CD,
    REV_CNTR_2ND_ANSI_CD,
    REV_CNTR_3RD_ANSI_CD,
    REV_CNTR_4TH_ANSI_CD,
    REV_CNTR_APC_HIPPS_CD,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    REV_CNTR_PMT_MTHD_IND_CD,
    REV_CNTR_DSCNT_IND_CD,
    REV_CNTR_PACKG_IND_CD,
    REV_CNTR_OTAF_PMT_CD,
    REV_CNTR_IDE_NDC_UPC_NUM,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_BLOOD_DDCTBL_AMT,
    REV_CNTR_CASH_DDCTBL_AMT,
    REV_CNTR_COINSRNC_WGE_ADJSTD_C,
    REV_CNTR_RDCD_COINSRNC_AMT,
    REV_CNTR_1ST_MSP_PD_AMT,
    REV_CNTR_2ND_MSP_PD_AMT,
    REV_CNTR_PRVDR_PMT_AMT,
    REV_CNTR_BENE_PMT_AMT,
    REV_CNTR_PTNT_RSPNSBLTY_PMT,
    REV_CNTR_PMT_AMT_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_STUS_IND_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }
  
  private enum InpatientFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    CLAIM_QUERY_CODE,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    FI_CLM_ACTN_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    OP_PHYSN_UPIN,
    OP_PHYSN_NPI,
    OT_PHYSN_UPIN,
    OT_PHYSN_NPI,
    CLM_MCO_PD_SW,
    PTNT_DSCHRG_STUS_CD,
    CLM_PPS_IND_CD,
    CLM_TOT_CHRG_AMT,
    CLM_ADMSN_DT,
    CLM_IP_ADMSN_TYPE_CD,
    CLM_SRC_IP_ADMSN_CD,
    NCH_PTNT_STATUS_IND_CD,
    CLM_PASS_THRU_PER_DIEM_AMT,
    NCH_BENE_IP_DDCTBL_AMT,
    NCH_BENE_PTA_COINSRNC_LBLTY_AM,
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    NCH_PROFNL_CMPNT_CHRG_AMT,
    NCH_IP_NCVRD_CHRG_AMT,
    NCH_IP_TOT_DDCTN_AMT,
    CLM_TOT_PPS_CPTL_AMT,
    CLM_PPS_CPTL_FSP_AMT,
    CLM_PPS_CPTL_OUTLIER_AMT,
    CLM_PPS_CPTL_DSPRPRTNT_SHR_AMT,
    CLM_PPS_CPTL_IME_AMT,
    CLM_PPS_CPTL_EXCPTN_AMT,
    CLM_PPS_OLD_CPTL_HLD_HRMLS_AMT,
    CLM_PPS_CPTL_DRG_WT_NUM,
    CLM_UTLZTN_DAY_CNT,
    BENE_TOT_COINSRNC_DAYS_CNT,
    BENE_LRD_USED_CNT,
    CLM_NON_UTLZTN_DAYS_CNT,
    NCH_BLOOD_PNTS_FRNSHD_QTY,
    NCH_VRFD_NCVRD_STAY_FROM_DT,
    NCH_VRFD_NCVRD_STAY_THRU_DT,
    NCH_ACTV_OR_CVRD_LVL_CARE_THRU,
    NCH_BENE_MDCR_BNFTS_EXHTD_DT_I,
    NCH_BENE_DSCHRG_DT,
    CLM_DRG_CD,
    CLM_DRG_OUTLIER_STAY_CD,
    NCH_DRG_OUTLIER_APRVD_PMT_AMT,
    ADMTG_DGNS_CD,
    ADMTG_DGNS_VRSN_CD,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    CLM_POA_IND_SW1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    CLM_POA_IND_SW2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    CLM_POA_IND_SW3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    CLM_POA_IND_SW4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    CLM_POA_IND_SW5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    CLM_POA_IND_SW6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    CLM_POA_IND_SW7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    CLM_POA_IND_SW8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    CLM_POA_IND_SW9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    CLM_POA_IND_SW10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    CLM_POA_IND_SW11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    CLM_POA_IND_SW12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    CLM_POA_IND_SW13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    CLM_POA_IND_SW14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    CLM_POA_IND_SW15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    CLM_POA_IND_SW16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    CLM_POA_IND_SW17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    CLM_POA_IND_SW18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    CLM_POA_IND_SW19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    CLM_POA_IND_SW20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    CLM_POA_IND_SW21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    CLM_POA_IND_SW22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    CLM_POA_IND_SW23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    CLM_POA_IND_SW24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    CLM_POA_IND_SW25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    CLM_E_POA_IND_SW1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    CLM_E_POA_IND_SW2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    CLM_E_POA_IND_SW3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    CLM_E_POA_IND_SW4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    CLM_E_POA_IND_SW5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    CLM_E_POA_IND_SW6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    CLM_E_POA_IND_SW7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    CLM_E_POA_IND_SW8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    CLM_E_POA_IND_SW9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    CLM_E_POA_IND_SW10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    CLM_E_POA_IND_SW11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    CLM_E_POA_IND_SW12,
    ICD_PRCDR_CD1,
    ICD_PRCDR_VRSN_CD1,
    PRCDR_DT1,
    ICD_PRCDR_CD2,
    ICD_PRCDR_VRSN_CD2,
    PRCDR_DT2,
    ICD_PRCDR_CD3,
    ICD_PRCDR_VRSN_CD3,
    PRCDR_DT3,
    ICD_PRCDR_CD4,
    ICD_PRCDR_VRSN_CD4,
    PRCDR_DT4,
    ICD_PRCDR_CD5,
    ICD_PRCDR_VRSN_CD5,
    PRCDR_DT5,
    ICD_PRCDR_CD6,
    ICD_PRCDR_VRSN_CD6,
    PRCDR_DT6,
    ICD_PRCDR_CD7,
    ICD_PRCDR_VRSN_CD7,
    PRCDR_DT7,
    ICD_PRCDR_CD8,
    ICD_PRCDR_VRSN_CD8,
    PRCDR_DT8,
    ICD_PRCDR_CD9,
    ICD_PRCDR_VRSN_CD9,
    PRCDR_DT9,
    ICD_PRCDR_CD10,
    ICD_PRCDR_VRSN_CD10,
    PRCDR_DT10,
    ICD_PRCDR_CD11,
    ICD_PRCDR_VRSN_CD11,
    PRCDR_DT11,
    ICD_PRCDR_CD12,
    ICD_PRCDR_VRSN_CD12,
    PRCDR_DT12,
    ICD_PRCDR_CD13,
    ICD_PRCDR_VRSN_CD13,
    PRCDR_DT13,
    ICD_PRCDR_CD14,
    ICD_PRCDR_VRSN_CD14,
    PRCDR_DT14,
    ICD_PRCDR_CD15,
    ICD_PRCDR_VRSN_CD15,
    PRCDR_DT15,
    ICD_PRCDR_CD16,
    ICD_PRCDR_VRSN_CD16,
    PRCDR_DT16,
    ICD_PRCDR_CD17,
    ICD_PRCDR_VRSN_CD17,
    PRCDR_DT17,
    ICD_PRCDR_CD18,
    ICD_PRCDR_VRSN_CD18,
    PRCDR_DT18,
    ICD_PRCDR_CD19,
    ICD_PRCDR_VRSN_CD19,
    PRCDR_DT19,
    ICD_PRCDR_CD20,
    ICD_PRCDR_VRSN_CD20,
    PRCDR_DT20,
    ICD_PRCDR_CD21,
    ICD_PRCDR_VRSN_CD21,
    PRCDR_DT21,
    ICD_PRCDR_CD22,
    ICD_PRCDR_VRSN_CD22,
    PRCDR_DT22,
    ICD_PRCDR_CD23,
    ICD_PRCDR_VRSN_CD23,
    PRCDR_DT23,
    ICD_PRCDR_CD24,
    ICD_PRCDR_VRSN_CD24,
    PRCDR_DT24,
    ICD_PRCDR_CD25,
    ICD_PRCDR_VRSN_CD25,
    PRCDR_DT25,
    IME_OP_CLM_VAL_AMT,
    DSH_OP_CLM_VAL_AMT,
    CLM_LINE_NUM,
    REV_CNTR,
    HCPCS_CD,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_DDCTBL_COINSRNC_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }

  private InpatientFields[] inpatientDxFields = {
    InpatientFields.ICD_DGNS_CD1,
    InpatientFields.ICD_DGNS_CD2,
    InpatientFields.ICD_DGNS_CD3,
    InpatientFields.ICD_DGNS_CD4,
    InpatientFields.ICD_DGNS_CD5,
    InpatientFields.ICD_DGNS_CD6,
    InpatientFields.ICD_DGNS_CD7,
    InpatientFields.ICD_DGNS_CD8,
    InpatientFields.ICD_DGNS_CD9,
    InpatientFields.ICD_DGNS_CD10,
    InpatientFields.ICD_DGNS_CD11,
    InpatientFields.ICD_DGNS_CD12,
    InpatientFields.ICD_DGNS_CD13,
    InpatientFields.ICD_DGNS_CD14,
    InpatientFields.ICD_DGNS_CD15,
    InpatientFields.ICD_DGNS_CD16,
    InpatientFields.ICD_DGNS_CD17,
    InpatientFields.ICD_DGNS_CD18,
    InpatientFields.ICD_DGNS_CD19,
    InpatientFields.ICD_DGNS_CD20,
    InpatientFields.ICD_DGNS_CD21,
    InpatientFields.ICD_DGNS_CD22,
    InpatientFields.ICD_DGNS_CD23,
    InpatientFields.ICD_DGNS_CD24,
    InpatientFields.ICD_DGNS_CD25
  };

  private InpatientFields[][] inpatientPxFields = {
    { InpatientFields.ICD_PRCDR_CD1, InpatientFields.ICD_PRCDR_VRSN_CD1,
      InpatientFields.PRCDR_DT1 },
    { InpatientFields.ICD_PRCDR_CD2, InpatientFields.ICD_PRCDR_VRSN_CD2,
      InpatientFields.PRCDR_DT2 },
    { InpatientFields.ICD_PRCDR_CD3, InpatientFields.ICD_PRCDR_VRSN_CD3,
      InpatientFields.PRCDR_DT3 },
    { InpatientFields.ICD_PRCDR_CD4, InpatientFields.ICD_PRCDR_VRSN_CD4,
      InpatientFields.PRCDR_DT4 },
    { InpatientFields.ICD_PRCDR_CD5, InpatientFields.ICD_PRCDR_VRSN_CD5,
      InpatientFields.PRCDR_DT5 },
    { InpatientFields.ICD_PRCDR_CD6, InpatientFields.ICD_PRCDR_VRSN_CD6,
      InpatientFields.PRCDR_DT6 },
    { InpatientFields.ICD_PRCDR_CD7, InpatientFields.ICD_PRCDR_VRSN_CD7,
      InpatientFields.PRCDR_DT7 },
    { InpatientFields.ICD_PRCDR_CD8, InpatientFields.ICD_PRCDR_VRSN_CD8,
      InpatientFields.PRCDR_DT8 },
    { InpatientFields.ICD_PRCDR_CD9, InpatientFields.ICD_PRCDR_VRSN_CD9,
      InpatientFields.PRCDR_DT9 },
    { InpatientFields.ICD_PRCDR_CD10, InpatientFields.ICD_PRCDR_VRSN_CD10,
      InpatientFields.PRCDR_DT10 },
    { InpatientFields.ICD_PRCDR_CD11, InpatientFields.ICD_PRCDR_VRSN_CD11,
      InpatientFields.PRCDR_DT11 },
    { InpatientFields.ICD_PRCDR_CD12, InpatientFields.ICD_PRCDR_VRSN_CD12,
      InpatientFields.PRCDR_DT12 },
    { InpatientFields.ICD_PRCDR_CD13, InpatientFields.ICD_PRCDR_VRSN_CD13,
      InpatientFields.PRCDR_DT13 },
    { InpatientFields.ICD_PRCDR_CD14, InpatientFields.ICD_PRCDR_VRSN_CD14,
      InpatientFields.PRCDR_DT14 },
    { InpatientFields.ICD_PRCDR_CD15, InpatientFields.ICD_PRCDR_VRSN_CD15,
      InpatientFields.PRCDR_DT15 },
    { InpatientFields.ICD_PRCDR_CD16, InpatientFields.ICD_PRCDR_VRSN_CD16,
      InpatientFields.PRCDR_DT16 },
    { InpatientFields.ICD_PRCDR_CD17, InpatientFields.ICD_PRCDR_VRSN_CD17,
      InpatientFields.PRCDR_DT17 },
    { InpatientFields.ICD_PRCDR_CD18, InpatientFields.ICD_PRCDR_VRSN_CD18,
      InpatientFields.PRCDR_DT18 },
    { InpatientFields.ICD_PRCDR_CD19, InpatientFields.ICD_PRCDR_VRSN_CD19,
      InpatientFields.PRCDR_DT19 },
    { InpatientFields.ICD_PRCDR_CD20, InpatientFields.ICD_PRCDR_VRSN_CD20,
      InpatientFields.PRCDR_DT20 },
    { InpatientFields.ICD_PRCDR_CD21, InpatientFields.ICD_PRCDR_VRSN_CD21,
      InpatientFields.PRCDR_DT21 },
    { InpatientFields.ICD_PRCDR_CD22, InpatientFields.ICD_PRCDR_VRSN_CD22,
      InpatientFields.PRCDR_DT22 },
    { InpatientFields.ICD_PRCDR_CD23, InpatientFields.ICD_PRCDR_VRSN_CD23,
      InpatientFields.PRCDR_DT23 },
    { InpatientFields.ICD_PRCDR_CD24, InpatientFields.ICD_PRCDR_VRSN_CD24,
      InpatientFields.PRCDR_DT24 },
    { InpatientFields.ICD_PRCDR_CD25, InpatientFields.ICD_PRCDR_VRSN_CD25,
      InpatientFields.PRCDR_DT25 }
  };

  private enum CarrierFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    CARR_CLM_ENTRY_CD,
    CLM_DISP_CD,
    CARR_NUM,
    CARR_CLM_PMT_DNL_CD,
    CLM_PMT_AMT,
    CARR_CLM_PRMRY_PYR_PD_AMT,
    RFR_PHYSN_UPIN,
    RFR_PHYSN_NPI,
    CARR_CLM_PRVDR_ASGNMT_IND_SW,
    NCH_CLM_PRVDR_PMT_AMT,
    NCH_CLM_BENE_PMT_AMT,
    NCH_CARR_CLM_SBMTD_CHRG_AMT,
    NCH_CARR_CLM_ALOWD_AMT,
    CARR_CLM_CASH_DDCTBL_APLD_AMT,
    CARR_CLM_HCPCS_YR_CD,
    CARR_CLM_RFRNG_PIN_NUM,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    CLM_CLNCL_TRIL_NUM,
    LINE_NUM,
    CARR_PRFRNG_PIN_NUM,
    PRF_PHYSN_UPIN,
    PRF_PHYSN_NPI,
    ORG_NPI_NUM,
    CARR_LINE_PRVDR_TYPE_CD,
    TAX_NUM,
    PRVDR_STATE_CD,
    PRVDR_ZIP,
    PRVDR_SPCLTY,
    PRTCPTNG_IND_CD,
    CARR_LINE_RDCD_PMT_PHYS_ASTN_C,
    LINE_SRVC_CNT,
    LINE_CMS_TYPE_SRVC_CD,
    LINE_PLACE_OF_SRVC_CD,
    CARR_LINE_PRCNG_LCLTY_CD,
    LINE_1ST_EXPNS_DT,
    LINE_LAST_EXPNS_DT,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    BETOS_CD,
    LINE_NCH_PMT_AMT,
    LINE_BENE_PMT_AMT,
    LINE_PRVDR_PMT_AMT,
    LINE_BENE_PTB_DDCTBL_AMT,
    LINE_BENE_PRMRY_PYR_CD,
    LINE_BENE_PRMRY_PYR_PD_AMT,
    LINE_COINSRNC_AMT,
    LINE_SBMTD_CHRG_AMT,
    LINE_ALOWD_CHRG_AMT,
    LINE_PRCSG_IND_CD,
    LINE_PMT_80_100_CD,
    LINE_SERVICE_DEDUCTIBLE,
    CARR_LINE_MTUS_CNT,
    CARR_LINE_MTUS_CD,
    LINE_ICD_DGNS_CD,
    LINE_ICD_DGNS_VRSN_CD,
    HPSA_SCRCTY_IND_CD,
    CARR_LINE_RX_NUM,
    LINE_HCT_HGB_RSLT_NUM,
    LINE_HCT_HGB_TYPE_CD,
    LINE_NDC_CD,
    CARR_LINE_CLIA_LAB_NUM,
    CARR_LINE_ANSTHSA_UNIT_CNT
  }

  public enum PrescriptionFields {
    DML_IND,
    PDE_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    BENE_ID,
    SRVC_DT,
    PD_DT,
    SRVC_PRVDR_ID_QLFYR_CD,
    SRVC_PRVDR_ID,
    PRSCRBR_ID_QLFYR_CD,
    PRSCRBR_ID,
    RX_SRVC_RFRNC_NUM,
    PROD_SRVC_ID,
    PLAN_CNTRCT_REC_ID,
    PLAN_PBP_REC_NUM,
    CMPND_CD,
    DAW_PROD_SLCTN_CD,
    QTY_DSPNSD_NUM,
    DAYS_SUPLY_NUM,
    FILL_NUM,
    DSPNSNG_STUS_CD,
    DRUG_CVRG_STUS_CD,
    ADJSTMT_DLTN_CD,
    NSTD_FRMT_CD,
    PRCNG_EXCPTN_CD,
    CTSTRPHC_CVRG_CD,
    GDC_BLW_OOPT_AMT,
    GDC_ABV_OOPT_AMT,
    PTNT_PAY_AMT,
    OTHR_TROOP_AMT,
    LICS_AMT,
    PLRO_AMT,
    CVRD_D_PLAN_PD_AMT,
    NCVRD_PLAN_PD_AMT,
    TOT_RX_CST_AMT,
    RX_ORGN_CD,
    RPTD_GAP_DSCNT_NUM,
    BRND_GNRC_CD,
    PHRMCY_SRVC_TYPE_CD,
    PTNT_RSDNC_CD,
    SUBMSN_CLR_CD
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final BB2RIFExporter instance = new BB2RIFExporter();
  }

  /**
   * Get the current instance of the BBExporter.
   * 
   * @return the current instance of the BBExporter.
   */
  public static BB2RIFExporter getInstance() {
    return SingletonHolder.instance;
  }
  
  static class CodeMapper {
    private HashMap<String, List<List<String>>> map;
    
    public CodeMapper(String jsonMap) {
      try {
        String json = Utilities.readResource(jsonMap);
        Gson g = new Gson();
        Type type = new TypeToken<HashMap<String,List<List<String>>>>(){}.getType();
        map = g.fromJson(json, type);
      } catch (Exception e) {
        System.out.println("BB2Exporter is running without " + jsonMap);
        // No worries. The optional mapping file is not present.
      }      
    }
    
    public boolean canMap(String codeToMap) {
      if (map == null) {
        return false;
      }
      return map.containsKey(codeToMap);
    }
    
    public String getMapped(String codeToMap, RandomNumberGenerator rand) {
      if (!canMap(codeToMap)) {
        return null;
      }
      List<List<String>> options = map.get(codeToMap);
      int choice = rand.randInt(options.size());
      return options.get(choice).get(0);
    }
  }

  /**
   * Utility class for writing to BB2 files.
   */
  private static class SynchronizedBBLineWriter extends BufferedWriter {
    
    private static final String BB_FIELD_SEPARATOR = "|";
    
    /**
     * Construct a new instance.
     * @param file the file to write to
     * @throws IOException if something goes wrong
     */
    public SynchronizedBBLineWriter(File file) throws IOException {
      super(new FileWriter(file));
    }
    
    /**
     * Write a line of output consisting of one or more fields separated by '|' and terminated with
     * a system new line.
     * @param fields the fields that will be concatenated into the line
     * @throws IOException if something goes wrong
     */
    private void writeLine(String... fields) throws IOException {
      String line = String.join(BB_FIELD_SEPARATOR, fields);
      synchronized (lock) {
        write(line);
        newLine();
      }
    }
    
    /**
     * Write a BB2 file header.
     * @param enumClass the enumeration class whose members define the column names
     * @throws IOException if something goes wrong
     */
    public <E extends Enum<E>> void writeHeader(Class<E> enumClass) throws IOException {
      String[] fields = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name)
              .toArray(String[]::new);
      writeLine(fields);
    }

    /**
     * Write a BB2 file line.
     * @param enumClass the enumeration class whose members define the column names
     * @param fieldValues a sparse conditionCodeMap of column names to values, missing values will
     *     result in empty values in the corresponding column
     * @throws IOException if something goes wrong 
     */
    public <E extends Enum<E>> void writeValues(Class<E> enumClass, Map<E, String> fieldValues)
            throws IOException {
      String[] fields = Arrays.stream(enumClass.getEnumConstants())
              .map((e) -> fieldValues.getOrDefault(e, "")).toArray(String[]::new);
      writeLine(fields);
    }

  }
  
}
