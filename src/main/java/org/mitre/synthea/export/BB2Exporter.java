package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.nextFriday;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * BlueButton 2 Exporter.
 */
public class BB2Exporter implements Flushable {
  
  private SynchronizedBBLineWriter beneficiary;
  private SynchronizedBBLineWriter outpatient;
  private SynchronizedBBLineWriter inpatient;
  private AtomicInteger claimId; // per claim per encounter
  private AtomicInteger claimGroupId; // per encounter

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
  private BB2Exporter() {
    claimId = new AtomicInteger();
    claimGroupId = new AtomicInteger();
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
    if (inpatient != null) {
      inpatient.close();
    }
    if (outpatient != null) {
      outpatient.close();
    }

    // Initialize output files
    File output = Exporter.getOutputFolder("bb2", null);
    output.mkdirs();
    Path outputDirectory = output.toPath();
    
    File beneficiaryFile = outputDirectory.resolve("beneficiary.csv").toFile();
    beneficiary = new SynchronizedBBLineWriter(beneficiaryFile);
    beneficiary.writeHeader(BeneficiaryFields.class);
    
    File outpatientFile = outputDirectory.resolve("outpatient.csv").toFile();
    outpatient = new SynchronizedBBLineWriter(outpatientFile);
    outpatient.writeHeader(OutpatientFields.class);
    
    File inpatientFile = outputDirectory.resolve("inpatient.csv").toFile();
    inpatient = new SynchronizedBBLineWriter(inpatientFile);
    inpatient.writeHeader(InpatientFields.class);
  }
  
  /**
   * Export a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  void export(Person person, long stopTime) throws IOException {
    exportBeneficiary(person, stopTime);
    exportOutpatient(person, stopTime);
    exportInpatient(person, stopTime);
  }
  
  /**
   * Export a beneficiary details for single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportBeneficiary(Person person, long stopTime) throws IOException {
    HashMap<BeneficiaryFields, String> fieldValues = new HashMap<>();
    fieldValues.put(BeneficiaryFields.DML_IND, "INSERT");
    String personId = (String)person.attributes.get(Person.ID);
    String beneId = personId.split("-")[4]; // last segment of UUID
    person.attributes.put(BB2_BENE_ID, beneId);
    fieldValues.put(BeneficiaryFields.BENE_ID, beneId);
    String hicId = personId.split("-")[0]; // first segment of UUID
    person.attributes.put(BB2_HIC_ID, hicId);
    fieldValues.put(BeneficiaryFields.BENE_CRNT_HIC_NUM, hicId);
    fieldValues.put(BeneficiaryFields.BENE_SEX_IDENT_CD,
            (String)person.attributes.get(Person.GENDER));
    fieldValues.put(BeneficiaryFields.BENE_COUNTY_CD,
            (String)person.attributes.get("county"));
    fieldValues.put(BeneficiaryFields.STATE_CODE,
            (String)person.attributes.get(Person.STATE));
    fieldValues.put(BeneficiaryFields.BENE_ZIP_CD,
            (String)person.attributes.get(Person.ZIP));
    fieldValues.put(BeneficiaryFields.BENE_SRNM_NAME, 
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BeneficiaryFields.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    fieldValues.put(BeneficiaryFields.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
    beneficiary.writeValues(BeneficiaryFields.class, fieldValues);
  }

  /**
   * Export outpatient claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportOutpatient(Person person, long stopTime) throws IOException {
    HashMap<OutpatientFields, String> fieldValues = new HashMap<>();
    // TODO
    // for each claim {
    //   for each field {
    //     fieldValues.put(fieldName, value)
    //   }
    //   outpatient.writeValues(OutpatientFields.class, fieldValues);
    // }
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
      fieldValues.put(InpatientFields.CLM_PMT_AMT, "" + encounter.claim.getTotalClaimCost());
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(InpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(InpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT,
            "" + encounter.claim.getCoveredCost());
      }
      fieldValues.put(InpatientFields.PRVDR_STATE_CD, encounter.provider.state);
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
      fieldValues.put(InpatientFields.CLM_TOT_CHRG_AMT, "" + encounter.claim.getTotalClaimCost());
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
          "" + encounter.claim.getDeductiblePaid());
      fieldValues.put(InpatientFields.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          "" + encounter.claim.getCoinsurancePaid());
      fieldValues.put(InpatientFields.NCH_BENE_BLOOD_DDCTBL_LBLTY_AM, "0");
      fieldValues.put(InpatientFields.NCH_PROFNL_CMPNT_CHRG_AMT, "4"); // fixed $ amount?
      fieldValues.put(InpatientFields.NCH_IP_NCVRD_CHRG_AMT,
          "" + encounter.claim.getPatientCost());
      fieldValues.put(InpatientFields.NCH_IP_TOT_DDCTN_AMT,
          "" + encounter.claim.getPatientCost());
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
          "" + encounter.claim.getCoveredCost());
      fieldValues.put(InpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
          "" + encounter.claim.getPatientCost());

      previous = encounter;
      previousInpatient = isInpatient;
      previousEmergency = isEmergency;

      inpatient.writeValues(InpatientFields.class, fieldValues);
    }
  }

  /**
   * Flush contents of any buffered streams to disk.
   * @throws IOException if something goes wrong
   */
  @Override
  public void flush() throws IOException {
    beneficiary.flush();
    inpatient.flush();
    outpatient.flush();
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
  
  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final BB2Exporter instance = new BB2Exporter();
  }

  /**
   * Get the current instance of the BBExporter.
   * 
   * @return the current instance of the BBExporter.
   */
  public static BB2Exporter getInstance() {
    return SingletonHolder.instance;
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
     * @param fieldValues a sparse map of column names to values, missing values will result in
     *     empty values in the corresponding column
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
