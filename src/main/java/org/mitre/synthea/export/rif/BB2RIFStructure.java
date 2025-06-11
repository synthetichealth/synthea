package org.mitre.synthea.export.rif;

/**
 * A collection of enums and related that describe the structure of RIF files. Note that order
 * is significant, columns will be written in the order specified.
 */
public class BB2RIFStructure {

  /**
   * Defines the structure and field mappings for RIF (Research Identifiable Files) export format.
   * Contains enumerations for all supported RIF file types used in Medicare claims data.
   */
  public static final Class<?>[] RIF_FILES = {
    BENEFICIARY.class,
    CARRIER.class,
    DME.class,
    HHA.class,
    HOSPICE.class,
    INPATIENT.class,
    OUTPATIENT.class,
    PDE.class,
    SNF.class
  };

  /**
   * Summary information exported with RIF files.
   * Contains metadata about the export process and data quality metrics,
   * providing counts of different claim types generated for each beneficiary.
   */
  public enum EXPORT_SUMMARY {
      /** Unique beneficiary identifier. */
      BENE_ID,
      
      /** Number of Carrier claims exported for this beneficiary. */
      CARRIER_CLAIMS,
      
      /** Number of Durable Medical Equipment (DME) claims exported for this beneficiary. */
      DME_CLAIMS,
      
      /** Number of Home Health Agency (HHA) claims exported for this beneficiary. */
      HHA_CLAIMS,
      
      /** Number of Hospice claims exported for this beneficiary. */
      HOSPICE_CLAIMS,
      
      /** Number of Inpatient hospital claims exported for this beneficiary. */
      INPATIENT_CLAIMS,
      
      /** Number of Outpatient facility claims exported for this beneficiary. */
      OUTPATIENT_CLAIMS,
      
      /** Number of Prescription Drug Event (PDE) claims exported for this beneficiary. */
      PDE_CLAIMS,
      
      /** Number of Skilled Nursing Facility (SNF) claims exported for this beneficiary. */
      SNF_CLAIMS
  }

  /**
   * Beneficiary enrollment and demographic information fields.
   * Maps to Medicare beneficiary master data including eligibility periods,
   * demographics, and enrollment status across different coverage types.
   */
  public enum BENEFICIARY {
    /** Data Management Layer indicator for record processing. */
    DML_IND,

    /** Unique beneficiary identifier. */
    BENE_ID,

    /** State code of beneficiary residence. */
    STATE_CODE,

    /** Beneficiary county code of residence. */
    BENE_COUNTY_CD,

    /** Beneficiary ZIP code of residence. */
    BENE_ZIP_CD,

    /** Beneficiary date of birth. */
    BENE_BIRTH_DT,

    /** Beneficiary sex/gender identity code. */
    BENE_SEX_IDENT_CD,

    /** Beneficiary race code classification. */
    BENE_RACE_CD,

    /** Original reason for Medicare entitlement. */
    BENE_ENTLMT_RSN_ORIG,

    /** Current reason for Medicare entitlement. */
    BENE_ENTLMT_RSN_CURR,

    /** End-Stage Renal Disease indicator. */
    BENE_ESRD_IND,

    /** Medicare status code for beneficiary. */
    BENE_MDCR_STATUS_CD,

    /** Part A termination code. */
    BENE_PTA_TRMNTN_CD,

    /** Part B termination code. */
    BENE_PTB_TRMNTN_CD,

    // BENE_PTD_TRMNTN_CD, // The spreadsheet has a gap for this column, examples do not include it

    /** Current Health Insurance Claim number. */
    BENE_CRNT_HIC_NUM,

    /** Beneficiary surname (last name). */
    BENE_SRNM_NAME,

    /** Beneficiary given name (first name). */
    BENE_GVN_NAME,

    /** Beneficiary middle name. */
    BENE_MDL_NAME,

    /** Medicare Beneficiary Identifier number. */
    MBI_NUM,

    /** Date of death if applicable. */
    DEATH_DT,

    /** Reference year for the data record. */
    RFRNC_YR,

    /** Medicare Part A coverage months count. */
    A_MO_CNT,

    /** Medicare Part B coverage months count. */
    B_MO_CNT,

    /** Buy-in coverage months count for state Medicaid programs. */
    BUYIN_MO_CNT,

    /** Health Maintenance Organization coverage months count. */
    HMO_MO_CNT,

    /** Retiree Drug Subsidy coverage months count. */
    RDS_MO_CNT,

    /** Enrollment source indicator. */
    ENRL_SRC,

    /** Sample group identifier for research purposes. */
    SAMPLE_GROUP,

    /** Five percent sample flag indicator. */
    EFIVEPCT,

    /** Current Beneficiary Identification Code. */
    CRNT_BIC,

    /** Beneficiary's age at end of reference year. */
    AGE,

    /** Coverage start date for Medicare benefits. */
    COVSTART,

    /** Dual eligible coverage months count (Medicare-Medicaid). */
    DUAL_MO_CNT,

    /** Federal Information Processing Standards state/county code for January. */
    FIPS_STATE_CNTY_JAN_CD,

    /** Federal Information Processing Standards state/county code for February. */
    FIPS_STATE_CNTY_FEB_CD,

    /** Federal Information Processing Standards state/county code for March. */
    FIPS_STATE_CNTY_MAR_CD,

    /** Federal Information Processing Standards state/county code for April. */
    FIPS_STATE_CNTY_APR_CD,

    /** Federal Information Processing Standards state/county code for May. */
    FIPS_STATE_CNTY_MAY_CD,

    /** Federal Information Processing Standards state/county code for June. */
    FIPS_STATE_CNTY_JUN_CD,

    /** Federal Information Processing Standards state/county code for July. */
    FIPS_STATE_CNTY_JUL_CD,

    /** Federal Information Processing Standards state/county code for August. */
    FIPS_STATE_CNTY_AUG_CD,

    /** Federal Information Processing Standards state/county code for September. */
    FIPS_STATE_CNTY_SEPT_CD,

    /** Federal Information Processing Standards state/county code for October. */
    FIPS_STATE_CNTY_OCT_CD,

    /** Federal Information Processing Standards state/county code for November. */
    FIPS_STATE_CNTY_NOV_CD,

    /** Federal Information Processing Standards state/county code for December. */
    FIPS_STATE_CNTY_DEC_CD,

    /** Vital status (Date of Death) switch indicator. */
    V_DOD_SW,

    /** Research Triangle Institute race code classification. */
    RTI_RACE_CD,

    /** Medicare status code for January. */
    MDCR_STUS_JAN_CD,

    /** Medicare status code for February. */
    MDCR_STUS_FEB_CD,

    /** Medicare status code for March. */
    MDCR_STUS_MAR_CD,

    /** Medicare status code for April. */
    MDCR_STUS_APR_CD,

    /** Medicare status code for May. */
    MDCR_STUS_MAY_CD,

    /** Medicare status code for June. */
    MDCR_STUS_JUN_CD,

    /** Medicare status code for July. */
    MDCR_STUS_JUL_CD,

    /** Medicare status code for August. */
    MDCR_STUS_AUG_CD,

    /** Medicare status code for September. */
    MDCR_STUS_SEPT_CD,

    /** Medicare status code for October. */
    MDCR_STUS_OCT_CD,

    /** Medicare status code for November. */
    MDCR_STUS_NOV_CD,

    /** Medicare status code for December. */
    MDCR_STUS_DEC_CD,

    /** Plan coverage months count. */
    PLAN_CVRG_MO_CNT,

    /** Medicare entitlement buy-in indicator for month 1. */
    MDCR_ENTLMT_BUYIN_1_IND,

    /** Medicare entitlement buy-in indicator for month 2. */
    MDCR_ENTLMT_BUYIN_2_IND,

    /** Medicare entitlement buy-in indicator for month 3. */
    MDCR_ENTLMT_BUYIN_3_IND,

    /** Medicare entitlement buy-in indicator for month 4. */
    MDCR_ENTLMT_BUYIN_4_IND,

    /** Medicare entitlement buy-in indicator for month 5. */
    MDCR_ENTLMT_BUYIN_5_IND,

    /** Medicare entitlement buy-in indicator for month 6. */
    MDCR_ENTLMT_BUYIN_6_IND,

    /** Medicare entitlement buy-in indicator for month 7. */
    MDCR_ENTLMT_BUYIN_7_IND,

    /** Medicare entitlement buy-in indicator for month 8. */
    MDCR_ENTLMT_BUYIN_8_IND,

    /** Medicare entitlement buy-in indicator for month 9. */
    MDCR_ENTLMT_BUYIN_9_IND,

    /** Medicare entitlement buy-in indicator for month 10. */
    MDCR_ENTLMT_BUYIN_10_IND,

    /** Medicare entitlement buy-in indicator for month 11. */
    MDCR_ENTLMT_BUYIN_11_IND,

    /** Medicare entitlement buy-in indicator for month 12. */
    MDCR_ENTLMT_BUYIN_12_IND,

    /** Health Maintenance Organization indicator for month 1. */
    HMO_1_IND,

    /** Health Maintenance Organization indicator for month 2. */
    HMO_2_IND,

    /** Health Maintenance Organization indicator for month 3. */
    HMO_3_IND,

    /** Health Maintenance Organization indicator for month 4. */
    HMO_4_IND,

    /** Health Maintenance Organization indicator for month 5. */
    HMO_5_IND,

    /** Health Maintenance Organization indicator for month 6. */
    HMO_6_IND,

    /** Health Maintenance Organization indicator for month 7. */
    HMO_7_IND,

    /** Health Maintenance Organization indicator for month 8. */
    HMO_8_IND,

    /** Health Maintenance Organization indicator for month 9. */
    HMO_9_IND,

    /** Health Maintenance Organization indicator for month 10. */
    HMO_10_IND,

    /** Health Maintenance Organization indicator for month 11. */
    HMO_11_IND,

    /** Health Maintenance Organization indicator for month 12. */
    HMO_12_IND,

    /** Part C contract identifier for January. */
    PTC_CNTRCT_JAN_ID,

    /** Part C contract identifier for February. */
    PTC_CNTRCT_FEB_ID,

    /** Part C contract identifier for March. */
    PTC_CNTRCT_MAR_ID,

    /** Part C contract identifier for April. */
    PTC_CNTRCT_APR_ID,

    /** Part C contract identifier for May. */
    PTC_CNTRCT_MAY_ID,

    /** Part C contract identifier for June. */
    PTC_CNTRCT_JUN_ID,

    /** Part C contract identifier for July. */
    PTC_CNTRCT_JUL_ID,

    /** Part C contract identifier for August. */
    PTC_CNTRCT_AUG_ID,

    /** Part C contract identifier for September. */
    PTC_CNTRCT_SEPT_ID,

    /** Part C contract identifier for October. */
    PTC_CNTRCT_OCT_ID,

    /** Part C contract identifier for November. */
    PTC_CNTRCT_NOV_ID,

    /** Part C contract identifier for December. */
    PTC_CNTRCT_DEC_ID,

    /** Part C Prescription Benefit Package identifier for January. */
    PTC_PBP_JAN_ID,

    /** Part C Prescription Benefit Package identifier for February. */
    PTC_PBP_FEB_ID,

    /** Part C Prescription Benefit Package identifier for March. */
    PTC_PBP_MAR_ID,

    /** Part C Prescription Benefit Package identifier for April. */
    PTC_PBP_APR_ID,

    /** Part C Prescription Benefit Package identifier for May. */
    PTC_PBP_MAY_ID,

    /** Part C Prescription Benefit Package identifier for June. */
    PTC_PBP_JUN_ID,

    /** Part C Prescription Benefit Package identifier for July. */
    PTC_PBP_JUL_ID,

    /** Part C Prescription Benefit Package identifier for August. */
    PTC_PBP_AUG_ID,

    /** Part C Prescription Benefit Package identifier for September. */
    PTC_PBP_SEPT_ID,

    /** Part C Prescription Benefit Package identifier for October. */
    PTC_PBP_OCT_ID,

    /** Part C Prescription Benefit Package identifier for November. */
    PTC_PBP_NOV_ID,

    /** Part C Prescription Benefit Package identifier for December. */
    PTC_PBP_DEC_ID,

    /** Part C plan type code for January. */
    PTC_PLAN_TYPE_JAN_CD,

    /** Part C plan type code for February. */
    PTC_PLAN_TYPE_FEB_CD,

    /** Part C plan type code for March. */
    PTC_PLAN_TYPE_MAR_CD,

    /** Part C plan type code for April. */
    PTC_PLAN_TYPE_APR_CD,

    /** Part C plan type code for May. */
    PTC_PLAN_TYPE_MAY_CD,

    /** Part C plan type code for June. */
    PTC_PLAN_TYPE_JUN_CD,

    /** Part C plan type code for July. */
    PTC_PLAN_TYPE_JUL_CD,

    /** Part C plan type code for August. */
    PTC_PLAN_TYPE_AUG_CD,

    /** Part C plan type code for September. */
    PTC_PLAN_TYPE_SEPT_CD,

    /** Part C plan type code for October. */
    PTC_PLAN_TYPE_OCT_CD,

    /** Part C plan type code for November. */
    PTC_PLAN_TYPE_NOV_CD,

    /** Part C plan type code for December. */
    PTC_PLAN_TYPE_DEC_CD,

    /** Part D contract identifier for January. */
    PTD_CNTRCT_JAN_ID,

    /** Part D contract identifier for February. */
    PTD_CNTRCT_FEB_ID,

    /** Part D contract identifier for March. */
    PTD_CNTRCT_MAR_ID,

    /** Part D contract identifier for April. */
    PTD_CNTRCT_APR_ID,

    /** Part D contract identifier for May. */
    PTD_CNTRCT_MAY_ID,

    /** Part D contract identifier for June. */
    PTD_CNTRCT_JUN_ID,

    /** Part D contract identifier for July. */
    PTD_CNTRCT_JUL_ID,

    /** Part D contract identifier for August. */
    PTD_CNTRCT_AUG_ID,

    /** Part D contract identifier for September. */
    PTD_CNTRCT_SEPT_ID,

    /** Part D contract identifier for October. */
    PTD_CNTRCT_OCT_ID,

    /** Part D contract identifier for November. */
    PTD_CNTRCT_NOV_ID,

    /** Part D contract identifier for December. */
    PTD_CNTRCT_DEC_ID,

    /** Part D Prescription Benefit Package identifier for January. */
    PTD_PBP_JAN_ID,

    /** Part D Prescription Benefit Package identifier for February. */
    PTD_PBP_FEB_ID,

    /** Part D Prescription Benefit Package identifier for March. */
    PTD_PBP_MAR_ID,

    /** Part D Prescription Benefit Package identifier for April. */
    PTD_PBP_APR_ID,

    /** Part D Prescription Benefit Package identifier for May. */
    PTD_PBP_MAY_ID,

    /** Part D Prescription Benefit Package identifier for June. */
    PTD_PBP_JUN_ID,

    /** Part D Prescription Benefit Package identifier for July. */
    PTD_PBP_JUL_ID,

    /** Part D Prescription Benefit Package identifier for August. */
    PTD_PBP_AUG_ID,

    /** Part D Prescription Benefit Package identifier for September. */
    PTD_PBP_SEPT_ID,

    /** Part D Prescription Benefit Package identifier for October. */
    PTD_PBP_OCT_ID,

    /** Part D Prescription Benefit Package identifier for November. */
    PTD_PBP_NOV_ID,

    /** Part D Prescription Benefit Package identifier for December. */
    PTD_PBP_DEC_ID,

    /** Part D segment identifier for January. */
    PTD_SGMT_JAN_ID,

    /** Part D segment identifier for February. */
    PTD_SGMT_FEB_ID,

    /** Part D segment identifier for March. */
    PTD_SGMT_MAR_ID,

    /** Part D segment identifier for April. */
    PTD_SGMT_APR_ID,

    /** Part D segment identifier for May. */
    PTD_SGMT_MAY_ID,

    /** Part D segment identifier for June. */
    PTD_SGMT_JUN_ID,

    /** Part D segment identifier for July. */
    PTD_SGMT_JUL_ID,

    /** Part D segment identifier for August. */
    PTD_SGMT_AUG_ID,

    /** Part D segment identifier for September. */
    PTD_SGMT_SEPT_ID,

    /** Part D segment identifier for October. */
    PTD_SGMT_OCT_ID,

    /** Part D segment identifier for November. */
    PTD_SGMT_NOV_ID,

    /** Part D segment identifier for December. */
    PTD_SGMT_DEC_ID,

    /** Retiree Drug Subsidy indicator for January. */
    RDS_JAN_IND,

    /** Retiree Drug Subsidy indicator for February. */
    RDS_FEB_IND,

    /** Retiree Drug Subsidy indicator for March. */
    RDS_MAR_IND,

    /** Retiree Drug Subsidy indicator for April. */
    RDS_APR_IND,

    /** Retiree Drug Subsidy indicator for May. */
    RDS_MAY_IND,

    /** Retiree Drug Subsidy indicator for June. */
    RDS_JUN_IND,

    /** Retiree Drug Subsidy indicator for July. */
    RDS_JUL_IND,

    /** Retiree Drug Subsidy indicator for August. */
    RDS_AUG_IND,

    /** Retiree Drug Subsidy indicator for September. */
    RDS_SEPT_IND,

    /** Retiree Drug Subsidy indicator for October. */
    RDS_OCT_IND,

    /** Retiree Drug Subsidy indicator for November. */
    RDS_NOV_IND,

    /** Retiree Drug Subsidy indicator for December. */
    RDS_DEC_IND,

    /** Medicare-Medicaid dual eligible status code for January. */
    META_DUAL_ELGBL_STUS_JAN_CD,

    /** Medicare-Medicaid dual eligible status code for February. */
    META_DUAL_ELGBL_STUS_FEB_CD,

    /** Medicare-Medicaid dual eligible status code for March. */
    META_DUAL_ELGBL_STUS_MAR_CD,

    /** Medicare-Medicaid dual eligible status code for April. */
    META_DUAL_ELGBL_STUS_APR_CD,

    /** Medicare-Medicaid dual eligible status code for May. */
    META_DUAL_ELGBL_STUS_MAY_CD,

    /** Medicare-Medicaid dual eligible status code for June. */
    META_DUAL_ELGBL_STUS_JUN_CD,

    /** Medicare-Medicaid dual eligible status code for July. */
    META_DUAL_ELGBL_STUS_JUL_CD,

    /** Medicare-Medicaid dual eligible status code for August. */
    META_DUAL_ELGBL_STUS_AUG_CD,

    /** Medicare-Medicaid dual eligible status code for September. */
    META_DUAL_ELGBL_STUS_SEPT_CD,

    /** Medicare-Medicaid dual eligible status code for October. */
    META_DUAL_ELGBL_STUS_OCT_CD,

    /** Medicare-Medicaid dual eligible status code for November. */
    META_DUAL_ELGBL_STUS_NOV_CD,

    /** Medicare-Medicaid dual eligible status code for December. */
    META_DUAL_ELGBL_STUS_DEC_CD,

    /** Cost sharing group code for January. */
    CST_SHR_GRP_JAN_CD,

    /** Cost sharing group code for February. */
    CST_SHR_GRP_FEB_CD,

    /** Cost sharing group code for March. */
    CST_SHR_GRP_MAR_CD,

    /** Cost sharing group code for April. */
    CST_SHR_GRP_APR_CD,

    /** Cost sharing group code for May. */
    CST_SHR_GRP_MAY_CD,

    /** Cost sharing group code for June. */
    CST_SHR_GRP_JUN_CD,

    /** Cost sharing group code for July. */
    CST_SHR_GRP_JUL_CD,

    /** Cost sharing group code for August. */
    CST_SHR_GRP_AUG_CD,

    /** Cost sharing group code for September. */
    CST_SHR_GRP_SEPT_CD,

    /** Cost sharing group code for October. */
    CST_SHR_GRP_OCT_CD,

    /** Cost sharing group code for November. */
    CST_SHR_GRP_NOV_CD,

    /** Cost sharing group code for December. */
    CST_SHR_GRP_DEC_CD,

    /** Derived address line 1. */
    DRVD_LINE_1_ADR,

    /** Derived address line 2. */
    DRVD_LINE_2_ADR,

    /** Derived address line 3. */
    DRVD_LINE_3_ADR,

    /** Derived address line 4. */
    DRVD_LINE_4_ADR,

    /** Derived address line 5. */
    DRVD_LINE_5_ADR,

    /** Derived address line 6. */
    DRVD_LINE_6_ADR,

    /** City name from address. */
    CITY_NAME,

    /** State code from address. */
    STATE_CD,

    /** State, county, and ZIP code combination. */
    STATE_CNTY_ZIP_CD,

    /** Effective begin date for the record. */
    EFCTV_BGN_DT,

    /** Effective end date for the record. */
    EFCTV_END_DT,

    /** Beneficiary link key for record matching. */
    BENE_LINK_KEY,

    /** Part A coverage start date. */
    PTA_CVRG_STRT_DT,

    /** Part A coverage end date. */
    PTA_CVRG_END_DT,

    /** Part B coverage start date. */
    PTB_CVRG_STRT_DT,

    /** Part B coverage end date. */
    PTB_CVRG_END_DT,

    /** Part D coverage start date. */
    PTD_CVRG_STRT_DT,

    /** Part D coverage end date. */
    PTD_CVRG_END_DT
  }

  /**
   * Medicare enrollment status fields for Parts A and B coverage.
   * Tracks monthly enrollment status in traditional Medicare.
   */
  public static final BENEFICIARY[] beneficiaryMedicareStatusFields = {
    /** Medicare status code for January. */
    BENEFICIARY.MDCR_STUS_JAN_CD,
    
    /** Medicare status code for February. */
    BENEFICIARY.MDCR_STUS_FEB_CD,
    
    /** Medicare status code for March. */
    BENEFICIARY.MDCR_STUS_MAR_CD,
    
    /** Medicare status code for April. */
    BENEFICIARY.MDCR_STUS_APR_CD,
    
    /** Medicare status code for May. */
    BENEFICIARY.MDCR_STUS_MAY_CD,
    
    /** Medicare status code for June. */
    BENEFICIARY.MDCR_STUS_JUN_CD,
    
    /** Medicare status code for July. */
    BENEFICIARY.MDCR_STUS_JUL_CD,
    
    /** Medicare status code for August. */
    BENEFICIARY.MDCR_STUS_AUG_CD,
    
    /** Medicare status code for September. */
    BENEFICIARY.MDCR_STUS_SEPT_CD,
    
    /** Medicare status code for October. */
    BENEFICIARY.MDCR_STUS_OCT_CD,
    
    /** Medicare status code for November. */
    BENEFICIARY.MDCR_STUS_NOV_CD,
    
    /** Medicare status code for December. */
    BENEFICIARY.MDCR_STUS_DEC_CD
  };

  /**
   * Medicare entitlement status fields tracking eligibility reasons.
   * Documents the basis for Medicare eligibility (age, disability, ESRD).
   */
  public static final BENEFICIARY[] beneficiaryMedicareEntitlementFields = {
    /** Medicare entitlement buy-in indicator for month 1 (January). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_1_IND,
    
    /** Medicare entitlement buy-in indicator for month 2 (February). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_2_IND,
    
    /** Medicare entitlement buy-in indicator for month 3 (March). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_3_IND,
    
    /** Medicare entitlement buy-in indicator for month 4 (April). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_4_IND,
    
    /** Medicare entitlement buy-in indicator for month 5 (May). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_5_IND,
    
    /** Medicare entitlement buy-in indicator for month 6 (June). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_6_IND,
    
    /** Medicare entitlement buy-in indicator for month 7 (July). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_7_IND,
    
    /** Medicare entitlement buy-in indicator for month 8 (August). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_8_IND,
    
    /** Medicare entitlement buy-in indicator for month 9 (September). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_9_IND,
    
    /** Medicare entitlement buy-in indicator for month 10 (October). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_10_IND,
    
    /** Medicare entitlement buy-in indicator for month 11 (November). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_11_IND,
    
    /** Medicare entitlement buy-in indicator for month 12 (December). */
    BENEFICIARY.MDCR_ENTLMT_BUYIN_12_IND
  };

  /**
   * Medicare Part C (Medicare Advantage) contract identifier fields.
   * Links beneficiaries to specific Medicare Advantage plans by month.
   */
  public static final BENEFICIARY[] beneficiaryPartCContractFields = {
    /** Part C contract identifier for January. */
    BENEFICIARY.PTC_CNTRCT_JAN_ID,
    
    /** Part C contract identifier for February. */
    BENEFICIARY.PTC_CNTRCT_FEB_ID,
    
    /** Part C contract identifier for March. */
    BENEFICIARY.PTC_CNTRCT_MAR_ID,
    
    /** Part C contract identifier for April. */
    BENEFICIARY.PTC_CNTRCT_APR_ID,
    
    /** Part C contract identifier for May. */
    BENEFICIARY.PTC_CNTRCT_MAY_ID,
    
    /** Part C contract identifier for June. */
    BENEFICIARY.PTC_CNTRCT_JUN_ID,
    
    /** Part C contract identifier for July. */
    BENEFICIARY.PTC_CNTRCT_JUL_ID,
    
    /** Part C contract identifier for August. */
    BENEFICIARY.PTC_CNTRCT_AUG_ID,
    
    /** Part C contract identifier for September. */
    BENEFICIARY.PTC_CNTRCT_SEPT_ID,
    
    /** Part C contract identifier for October. */
    BENEFICIARY.PTC_CNTRCT_OCT_ID,
    
    /** Part C contract identifier for November. */
    BENEFICIARY.PTC_CNTRCT_NOV_ID,
    
    /** Part C contract identifier for December. */
    BENEFICIARY.PTC_CNTRCT_DEC_ID
  };

  /**
   * Medicare Part C Prescription Benefit Package (PBP) fields.
   * Identifies prescription drug coverage within Medicare Advantage plans.
   */
  public static final BENEFICIARY[] beneficiaryPartCPBPFields = {
    /** Part C Prescription Benefit Package identifier for January. */
    BENEFICIARY.PTC_PBP_JAN_ID,
    
    /** Part C Prescription Benefit Package identifier for February. */
    BENEFICIARY.PTC_PBP_FEB_ID,
    
    /** Part C Prescription Benefit Package identifier for March. */
    BENEFICIARY.PTC_PBP_MAR_ID,
    
    /** Part C Prescription Benefit Package identifier for April. */
    BENEFICIARY.PTC_PBP_APR_ID,
    
    /** Part C Prescription Benefit Package identifier for May. */
    BENEFICIARY.PTC_PBP_MAY_ID,
    
    /** Part C Prescription Benefit Package identifier for June. */
    BENEFICIARY.PTC_PBP_JUN_ID,
    
    /** Part C Prescription Benefit Package identifier for July. */
    BENEFICIARY.PTC_PBP_JUL_ID,
    
    /** Part C Prescription Benefit Package identifier for August. */
    BENEFICIARY.PTC_PBP_AUG_ID,
    
    /** Part C Prescription Benefit Package identifier for September. */
    BENEFICIARY.PTC_PBP_SEPT_ID,
    
    /** Part C Prescription Benefit Package identifier for October. */
    BENEFICIARY.PTC_PBP_OCT_ID,
    
    /** Part C Prescription Benefit Package identifier for November. */
    BENEFICIARY.PTC_PBP_NOV_ID,
    
    /** Part C Prescription Benefit Package identifier for December. */
    BENEFICIARY.PTC_PBP_DEC_ID
  };


  /**
   * Medicare Part D prescription drug plan contract fields.
   * Links beneficiaries to standalone prescription drug plans by month.
   */
  public static final BENEFICIARY[] beneficiaryPartDContractFields = {
    /** Part D contract identifier for January. */
    BENEFICIARY.PTD_CNTRCT_JAN_ID,
    
    /** Part D contract identifier for February. */
    BENEFICIARY.PTD_CNTRCT_FEB_ID,
    
    /** Part D contract identifier for March. */
    BENEFICIARY.PTD_CNTRCT_MAR_ID,
    
    /** Part D contract identifier for April. */
    BENEFICIARY.PTD_CNTRCT_APR_ID,
    
    /** Part D contract identifier for May. */
    BENEFICIARY.PTD_CNTRCT_MAY_ID,
    
    /** Part D contract identifier for June. */
    BENEFICIARY.PTD_CNTRCT_JUN_ID,
    
    /** Part D contract identifier for July. */
    BENEFICIARY.PTD_CNTRCT_JUL_ID,
    
    /** Part D contract identifier for August. */
    BENEFICIARY.PTD_CNTRCT_AUG_ID,
    
    /** Part D contract identifier for September. */
    BENEFICIARY.PTD_CNTRCT_SEPT_ID,
    
    /** Part D contract identifier for October. */
    BENEFICIARY.PTD_CNTRCT_OCT_ID,
    
    /** Part D contract identifier for November. */
    BENEFICIARY.PTD_CNTRCT_NOV_ID,
    
    /** Part D contract identifier for December. */
    BENEFICIARY.PTD_CNTRCT_DEC_ID
  };

  /**
   * Medicare Part D Prescription Benefit Package fields.
   * Detailed prescription drug plan benefit structures and coverage.
   */
  public static final BENEFICIARY[] beneficiaryPartDPBPFields = {
    /** Part D Prescription Benefit Package identifier for January. */
    BENEFICIARY.PTD_PBP_JAN_ID,
    
    /** Part D Prescription Benefit Package identifier for February. */
    BENEFICIARY.PTD_PBP_FEB_ID,
    
    /** Part D Prescription Benefit Package identifier for March. */
    BENEFICIARY.PTD_PBP_MAR_ID,
    
    /** Part D Prescription Benefit Package identifier for April. */
    BENEFICIARY.PTD_PBP_APR_ID,
    
    /** Part D Prescription Benefit Package identifier for May. */
    BENEFICIARY.PTD_PBP_MAY_ID,
    
    /** Part D Prescription Benefit Package identifier for June. */
    BENEFICIARY.PTD_PBP_JUN_ID,
    
    /** Part D Prescription Benefit Package identifier for July. */
    BENEFICIARY.PTD_PBP_JUL_ID,
    
    /** Part D Prescription Benefit Package identifier for August. */
    BENEFICIARY.PTD_PBP_AUG_ID,
    
    /** Part D Prescription Benefit Package identifier for September. */
    BENEFICIARY.PTD_PBP_SEPT_ID,
    
    /** Part D Prescription Benefit Package identifier for October. */
    BENEFICIARY.PTD_PBP_OCT_ID,
    
    /** Part D Prescription Benefit Package identifier for November. */
    BENEFICIARY.PTD_PBP_NOV_ID,
    
    /** Part D Prescription Benefit Package identifier for December. */
    BENEFICIARY.PTD_PBP_DEC_ID
  };

  /**
   * Medicare Part D low-income cost sharing segment fields.
   * Tracks cost sharing categories for beneficiaries with subsidies.
   */
  public static final BENEFICIARY[] beneficiaryPartDSegmentFields = {
    /** Part D segment identifier for January. */
    BENEFICIARY.PTD_SGMT_JAN_ID,
    
    /** Part D segment identifier for February. */
    BENEFICIARY.PTD_SGMT_FEB_ID,
    
    /** Part D segment identifier for March. */
    BENEFICIARY.PTD_SGMT_MAR_ID,
    
    /** Part D segment identifier for April. */
    BENEFICIARY.PTD_SGMT_APR_ID,
    
    /** Part D segment identifier for May. */
    BENEFICIARY.PTD_SGMT_MAY_ID,
    
    /** Part D segment identifier for June. */
    BENEFICIARY.PTD_SGMT_JUN_ID,
    
    /** Part D segment identifier for July. */
    BENEFICIARY.PTD_SGMT_JUL_ID,
    
    /** Part D segment identifier for August. */
    BENEFICIARY.PTD_SGMT_AUG_ID,
    
    /** Part D segment identifier for September. */
    BENEFICIARY.PTD_SGMT_SEPT_ID,
    
    /** Part D segment identifier for October. */
    BENEFICIARY.PTD_SGMT_OCT_ID,
    
    /** Part D segment identifier for November. */
    BENEFICIARY.PTD_SGMT_NOV_ID,
    
    /** Part D segment identifier for December. */
    BENEFICIARY.PTD_SGMT_DEC_ID
  };

  /**
   * Medicare Part D cost sharing group fields.
   * Identifies low-income subsidy and cost sharing categories.
   */
  public static final BENEFICIARY[] beneficiaryPartDCostSharingFields = {
    /** Cost sharing group code for January. */
    BENEFICIARY.CST_SHR_GRP_JAN_CD,
    
    /** Cost sharing group code for February. */
    BENEFICIARY.CST_SHR_GRP_FEB_CD,
    
    /** Cost sharing group code for March. */
    BENEFICIARY.CST_SHR_GRP_MAR_CD,
    
    /** Cost sharing group code for April. */
    BENEFICIARY.CST_SHR_GRP_APR_CD,
    
    /** Cost sharing group code for May. */
    BENEFICIARY.CST_SHR_GRP_MAY_CD,
    
    /** Cost sharing group code for June. */
    BENEFICIARY.CST_SHR_GRP_JUN_CD,
    
    /** Cost sharing group code for July. */
    BENEFICIARY.CST_SHR_GRP_JUL_CD,
    
    /** Cost sharing group code for August. */
    BENEFICIARY.CST_SHR_GRP_AUG_CD,
    
    /** Cost sharing group code for September. */
    BENEFICIARY.CST_SHR_GRP_SEPT_CD,
    
    /** Cost sharing group code for October. */
    BENEFICIARY.CST_SHR_GRP_OCT_CD,
    
    /** Cost sharing group code for November. */
    BENEFICIARY.CST_SHR_GRP_NOV_CD,
    
    /** Cost sharing group code for December. */
    BENEFICIARY.CST_SHR_GRP_DEC_CD
  };

  /**
   * Medicare Part D retiree drug subsidy program fields.
   * Employer-sponsored prescription drug coverage coordination.
   */
  public static final BENEFICIARY[] benficiaryPartDRetireeDrugSubsidyFields = {
    /** Retiree Drug Subsidy indicator for January. */
    BENEFICIARY.RDS_JAN_IND,
    
    /** Retiree Drug Subsidy indicator for February. */
    BENEFICIARY.RDS_FEB_IND,
    
    /** Retiree Drug Subsidy indicator for March. */
    BENEFICIARY.RDS_MAR_IND,
    
    /** Retiree Drug Subsidy indicator for April. */
    BENEFICIARY.RDS_APR_IND,
    
    /** Retiree Drug Subsidy indicator for May. */
    BENEFICIARY.RDS_MAY_IND,
    
    /** Retiree Drug Subsidy indicator for June. */
    BENEFICIARY.RDS_JUN_IND,
    
    /** Retiree Drug Subsidy indicator for July. */
    BENEFICIARY.RDS_JUL_IND,
    
    /** Retiree Drug Subsidy indicator for August. */
    BENEFICIARY.RDS_AUG_IND,
    
    /** Retiree Drug Subsidy indicator for September. */
    BENEFICIARY.RDS_SEPT_IND,
    
    /** Retiree Drug Subsidy indicator for October. */
    BENEFICIARY.RDS_OCT_IND,
    
    /** Retiree Drug Subsidy indicator for November. */
    BENEFICIARY.RDS_NOV_IND,
    
    /** Retiree Drug Subsidy indicator for December. */
    BENEFICIARY.RDS_DEC_IND
  };

  /**
   * Beneficiary dual eligible status fields for Medicare-Medicaid enrollees.
   * Tracks monthly dual eligibility status throughout the benefit year.
   */
  public static final BENEFICIARY[] beneficiaryDualEligibleStatusFields = {
    /** Medicare-Medicaid dual eligible status code for January. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_JAN_CD,
    
    /** Medicare-Medicaid dual eligible status code for February. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_FEB_CD,
    
    /** Medicare-Medicaid dual eligible status code for March. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_MAR_CD,
    
    /** Medicare-Medicaid dual eligible status code for April. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_APR_CD,
    
    /** Medicare-Medicaid dual eligible status code for May. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_MAY_CD,
    
    /** Medicare-Medicaid dual eligible status code for June. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_JUN_CD,
    
    /** Medicare-Medicaid dual eligible status code for July. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_JUL_CD,
    
    /** Medicare-Medicaid dual eligible status code for August. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_AUG_CD,
    
    /** Medicare-Medicaid dual eligible status code for September. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_SEPT_CD,
    
    /** Medicare-Medicaid dual eligible status code for October. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_OCT_CD,
    
    /** Medicare-Medicaid dual eligible status code for November. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_NOV_CD,
    
    /** Medicare-Medicaid dual eligible status code for December. */
    BENEFICIARY.META_DUAL_ELGBL_STUS_DEC_CD
  };

  /**
   * Federal Information Processing Standards (FIPS) state and county codes.
   * Geographic identifiers for beneficiary residence locations by month.
   */
  public static final BENEFICIARY[] beneficiaryFipsStateCntyFields = {
    /** Federal Information Processing Standards state/county code for January. */
    BENEFICIARY.FIPS_STATE_CNTY_JAN_CD,
    
    /** Federal Information Processing Standards state/county code for February. */
    BENEFICIARY.FIPS_STATE_CNTY_FEB_CD,
    
    /** Federal Information Processing Standards state/county code for March. */
    BENEFICIARY.FIPS_STATE_CNTY_MAR_CD,
    
    /** Federal Information Processing Standards state/county code for April. */
    BENEFICIARY.FIPS_STATE_CNTY_APR_CD,
    
    /** Federal Information Processing Standards state/county code for May. */
    BENEFICIARY.FIPS_STATE_CNTY_MAY_CD,
    
    /** Federal Information Processing Standards state/county code for June. */
    BENEFICIARY.FIPS_STATE_CNTY_JUN_CD,
    
    /** Federal Information Processing Standards state/county code for July. */
    BENEFICIARY.FIPS_STATE_CNTY_JUL_CD,
    
    /** Federal Information Processing Standards state/county code for August. */
    BENEFICIARY.FIPS_STATE_CNTY_AUG_CD,
    
    /** Federal Information Processing Standards state/county code for September. */
    BENEFICIARY.FIPS_STATE_CNTY_SEPT_CD,
    
    /** Federal Information Processing Standards state/county code for October. */
    BENEFICIARY.FIPS_STATE_CNTY_OCT_CD,
    
    /** Federal Information Processing Standards state/county code for November. */
    BENEFICIARY.FIPS_STATE_CNTY_NOV_CD,
    
    /** Federal Information Processing Standards state/county code for December. */
    BENEFICIARY.FIPS_STATE_CNTY_DEC_CD
  };

  /**
   * Outpatient hospital and clinic claims data structure.
   * Represents ambulatory care services, emergency department visits,
   * and outpatient procedures.
   */
  public enum OUTPATIENT {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Claim identifier uniquely identifying the outpatient claim. */
    CLM_ID,
    
    /** Claim group identifier for related outpatient claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,
    
    /** NCH claim type code identifying the type of outpatient claim. */
    NCH_CLM_TYPE_CD,
    
    /** Claim from date (start of service period). */
    CLM_FROM_DT,
    
    /** Claim through date (end of service period). */
    CLM_THRU_DT,
    
    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,
    
    /** Fiscal intermediary claim processing date. */
    FI_CLM_PROC_DT,
    
    /** Claim query code for claim inquiries. */
    CLAIM_QUERY_CODE,
    
    /** Provider number for the outpatient facility. */
    PRVDR_NUM,
    
    /** Claim facility type code. */
    CLM_FAC_TYPE_CD,
    
    /** Claim service classification type code. */
    CLM_SRVC_CLSFCTN_TYPE_CD,
    
    /** Claim frequency code indicating billing frequency. */
    CLM_FREQ_CD,
    
    /** Fiscal intermediary number. */
    FI_NUM,
    
    /** Claim Medicare non-payment reason code. */
    CLM_MDCR_NON_PMT_RSN_CD,
    
    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,
    
    /** NCH primary payer claim paid amount. */
    NCH_PRMRY_PYR_CLM_PD_AMT,
    
    /** NCH primary payer code. */
    NCH_PRMRY_PYR_CD,
    
    /** Provider state code. */
    PRVDR_STATE_CD,
    
    /** Organization National Provider Identifier number. */
    ORG_NPI_NUM,
    
    /** Attending physician Unique Provider Identification Number (legacy). */
    AT_PHYSN_UPIN,
    
    /** Attending physician National Provider Identifier. */
    AT_PHYSN_NPI,
    
    /** Operating physician Unique Provider Identification Number (legacy). */
    OP_PHYSN_UPIN,
    
    /** Operating physician National Provider Identifier. */
    OP_PHYSN_NPI,
    
    /** Other physician Unique Provider Identification Number (legacy). */
    OT_PHYSN_UPIN,
    
    /** Other physician National Provider Identifier. */
    OT_PHYSN_NPI,
    
    /** Claim Managed Care Organization paid switch. */
    CLM_MCO_PD_SW,
    
    /** Patient discharge status code. */
    PTNT_DSCHRG_STUS_CD,
    
    /** Claim total charge amount. */
    CLM_TOT_CHRG_AMT,
    
    /** NCH beneficiary blood deductible liability amount. */
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    
    /** NCH professional component charge amount. */
    NCH_PROFNL_CMPNT_CHRG_AMT,
    
    /** Principal diagnosis code for the outpatient visit. */
    PRNCPAL_DGNS_CD,
    
    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,
    
    /** Other diagnosis code 1. */
    ICD_DGNS_CD1,
    
    /** Other diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,
    
    /** Other diagnosis code 2. */
    ICD_DGNS_CD2,
    
    /** Other diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,
    
    /** Other diagnosis code 3. */
    ICD_DGNS_CD3,
    
    /** Other diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,
    
    /** Other diagnosis code 4. */
    ICD_DGNS_CD4,
    
    /** Other diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,
    
    /** Other diagnosis code 5. */
    ICD_DGNS_CD5,
    
    /** Other diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,
    
    /** Other diagnosis code 6. */
    ICD_DGNS_CD6,
    
    /** Other diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,
    
    /** Other diagnosis code 7. */
    ICD_DGNS_CD7,
    
    /** Other diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,
    
    /** Other diagnosis code 8. */
    ICD_DGNS_CD8,
    
    /** Other diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,
    
    /** Other diagnosis code 9. */
    ICD_DGNS_CD9,
    
    /** Other diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,
    
    /** Other diagnosis code 10. */
    ICD_DGNS_CD10,
    
    /** Other diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,
    
    /** Other diagnosis code 11. */
    ICD_DGNS_CD11,
    
    /** Other diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,
    
    /** Other diagnosis code 12. */
    ICD_DGNS_CD12,
    
    /** Other diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,
    
    /** Other diagnosis code 13. */
    ICD_DGNS_CD13,
    
    /** Other diagnosis version code 13. */
    ICD_DGNS_VRSN_CD13,
    
    /** Other diagnosis code 14. */
    ICD_DGNS_CD14,
    
    /** Other diagnosis version code 14. */
    ICD_DGNS_VRSN_CD14,
    
    /** Other diagnosis code 15. */
    ICD_DGNS_CD15,
    
    /** Other diagnosis version code 15. */
    ICD_DGNS_VRSN_CD15,
    
    /** Other diagnosis code 16. */
    ICD_DGNS_CD16,
    
    /** Other diagnosis version code 16. */
    ICD_DGNS_VRSN_CD16,
    
    /** Other diagnosis code 17. */
    ICD_DGNS_CD17,
    
    /** Other diagnosis version code 17. */
    ICD_DGNS_VRSN_CD17,
    
    /** Other diagnosis code 18. */
    ICD_DGNS_CD18,
    
    /** Other diagnosis version code 18. */
    ICD_DGNS_VRSN_CD18,
    
    /** Other diagnosis code 19. */
    ICD_DGNS_CD19,
    
    /** Other diagnosis version code 19. */
    ICD_DGNS_VRSN_CD19,
    
    /** Other diagnosis code 20. */
    ICD_DGNS_CD20,
    
    /** Other diagnosis version code 20. */
    ICD_DGNS_VRSN_CD20,
    
    /** Other diagnosis code 21. */
    ICD_DGNS_CD21,
    
    /** Other diagnosis version code 21. */
    ICD_DGNS_VRSN_CD21,
    
    /** Other diagnosis code 22. */
    ICD_DGNS_CD22,
    
    /** Other diagnosis version code 22. */
    ICD_DGNS_VRSN_CD22,
    
    /** Other diagnosis code 23. */
    ICD_DGNS_CD23,
    
    /** Other diagnosis version code 23. */
    ICD_DGNS_VRSN_CD23,
    
    /** Other diagnosis code 24. */
    ICD_DGNS_CD24,
    
    /** Other diagnosis version code 24. */
    ICD_DGNS_VRSN_CD24,
    
    /** Other diagnosis code 25. */
    ICD_DGNS_CD25,
    
    /** Other diagnosis version code 25. */
    ICD_DGNS_VRSN_CD25,
    
    /** First external cause of injury code. */
    FST_DGNS_E_CD,
    
    /** First external cause of injury version code. */
    FST_DGNS_E_VRSN_CD,
    
    /** External cause of injury diagnosis code 1. */
    ICD_DGNS_E_CD1,
    
    /** External cause of injury diagnosis version code 1. */
    ICD_DGNS_E_VRSN_CD1,
    
    /** External cause of injury diagnosis code 2. */
    ICD_DGNS_E_CD2,
    
    /** External cause of injury diagnosis version code 2. */
    ICD_DGNS_E_VRSN_CD2,
    
    /** External cause of injury diagnosis code 3. */
    ICD_DGNS_E_CD3,
    
    /** External cause of injury diagnosis version code 3. */
    ICD_DGNS_E_VRSN_CD3,
    
    /** External cause of injury diagnosis code 4. */
    ICD_DGNS_E_CD4,
    
    /** External cause of injury diagnosis version code 4. */
    ICD_DGNS_E_VRSN_CD4,
    
    /** External cause of injury diagnosis code 5. */
    ICD_DGNS_E_CD5,
    
    /** External cause of injury diagnosis version code 5. */
    ICD_DGNS_E_VRSN_CD5,
    
    /** External cause of injury diagnosis code 6. */
    ICD_DGNS_E_CD6,
    
    /** External cause of injury diagnosis version code 6. */
    ICD_DGNS_E_VRSN_CD6,
    
    /** External cause of injury diagnosis code 7. */
    ICD_DGNS_E_CD7,
    
    /** External cause of injury diagnosis version code 7. */
    ICD_DGNS_E_VRSN_CD7,
    
    /** External cause of injury diagnosis code 8. */
    ICD_DGNS_E_CD8,
    
    /** External cause of injury diagnosis version code 8. */
    ICD_DGNS_E_VRSN_CD8,
    
    /** External cause of injury diagnosis code 9. */
    ICD_DGNS_E_CD9,
    
    /** External cause of injury diagnosis version code 9. */
    ICD_DGNS_E_VRSN_CD9,
    
    /** External cause of injury diagnosis code 10. */
    ICD_DGNS_E_CD10,
    
    /** External cause of injury diagnosis version code 10. */
    ICD_DGNS_E_VRSN_CD10,
    
    /** External cause of injury diagnosis code 11. */
    ICD_DGNS_E_CD11,
    
    /** External cause of injury diagnosis version code 11. */
    ICD_DGNS_E_VRSN_CD11,
    
    /** External cause of injury diagnosis code 12. */
    ICD_DGNS_E_CD12,
    
    /** External cause of injury diagnosis version code 12. */
    ICD_DGNS_E_VRSN_CD12,
    
    /** ICD procedure code 1. */
    ICD_PRCDR_CD1,
    
    /** ICD procedure version code 1. */
    ICD_PRCDR_VRSN_CD1,
    
    /** Procedure date 1. */
    PRCDR_DT1,
    
    /** ICD procedure code 2. */
    ICD_PRCDR_CD2,
    
    /** ICD procedure version code 2. */
    ICD_PRCDR_VRSN_CD2,
    
    /** Procedure date 2. */
    PRCDR_DT2,
    
    /** ICD procedure code 3. */
    ICD_PRCDR_CD3,
    
    /** ICD procedure version code 3. */
    ICD_PRCDR_VRSN_CD3,
    
    /** Procedure date 3. */
    PRCDR_DT3,
    
    /** ICD procedure code 4. */
    ICD_PRCDR_CD4,
    
    /** ICD procedure version code 4. */
    ICD_PRCDR_VRSN_CD4,
    
    /** Procedure date 4. */
    PRCDR_DT4,
    
    /** ICD procedure code 5. */
    ICD_PRCDR_CD5,
    
    /** ICD procedure version code 5. */
    ICD_PRCDR_VRSN_CD5,
    
    /** Procedure date 5. */
    PRCDR_DT5,
    
    /** ICD procedure code 6. */
    ICD_PRCDR_CD6,
    
    /** ICD procedure version code 6. */
    ICD_PRCDR_VRSN_CD6,
    
    /** Procedure date 6. */
    PRCDR_DT6,
    
    /** ICD procedure code 7. */
    ICD_PRCDR_CD7,
    
    /** ICD procedure version code 7. */
    ICD_PRCDR_VRSN_CD7,
    
    /** Procedure date 7. */
    PRCDR_DT7,
    
    /** ICD procedure code 8. */
    ICD_PRCDR_CD8,
    
    /** ICD procedure version code 8. */
    ICD_PRCDR_VRSN_CD8,
    
    /** Procedure date 8. */
    PRCDR_DT8,
    
    /** ICD procedure code 9. */
    ICD_PRCDR_CD9,
    
    /** ICD procedure version code 9. */
    ICD_PRCDR_VRSN_CD9,
    
    /** Procedure date 9. */
    PRCDR_DT9,
    
    /** ICD procedure code 10. */
    ICD_PRCDR_CD10,
    
    /** ICD procedure version code 10. */
    ICD_PRCDR_VRSN_CD10,
    
    /** Procedure date 10. */
    PRCDR_DT10,
    
    /** ICD procedure code 11. */
    ICD_PRCDR_CD11,
    
    /** ICD procedure version code 11. */
    ICD_PRCDR_VRSN_CD11,
    
    /** Procedure date 11. */
    PRCDR_DT11,
    
    /** ICD procedure code 12. */
    ICD_PRCDR_CD12,
    
    /** ICD procedure version code 12. */
    ICD_PRCDR_VRSN_CD12,
    
    /** Procedure date 12. */
    PRCDR_DT12,
    
    /** ICD procedure code 13. */
    ICD_PRCDR_CD13,
    
    /** ICD procedure version code 13. */
    ICD_PRCDR_VRSN_CD13,
    
    /** Procedure date 13. */
    PRCDR_DT13,
    
    /** ICD procedure code 14. */
    ICD_PRCDR_CD14,
    
    /** ICD procedure version code 14. */
    ICD_PRCDR_VRSN_CD14,
    
    /** Procedure date 14. */
    PRCDR_DT14,
    
    /** ICD procedure code 15. */
    ICD_PRCDR_CD15,
    
    /** ICD procedure version code 15. */
    ICD_PRCDR_VRSN_CD15,
    
    /** Procedure date 15. */
    PRCDR_DT15,
    
    /** ICD procedure code 16. */
    ICD_PRCDR_CD16,
    
    /** ICD procedure version code 16. */
    ICD_PRCDR_VRSN_CD16,
    
    /** Procedure date 16. */
    PRCDR_DT16,
    
    /** ICD procedure code 17. */
    ICD_PRCDR_CD17,
    
    /** ICD procedure version code 17. */
    ICD_PRCDR_VRSN_CD17,
    
    /** Procedure date 17. */
    PRCDR_DT17,
    
    /** ICD procedure code 18. */
    ICD_PRCDR_CD18,
    
    /** ICD procedure version code 18. */
    ICD_PRCDR_VRSN_CD18,
    
    /** Procedure date 18. */
    PRCDR_DT18,
    
    /** ICD procedure code 19. */
    ICD_PRCDR_CD19,
    
    /** ICD procedure version code 19. */
    ICD_PRCDR_VRSN_CD19,
    
    /** Procedure date 19. */
    PRCDR_DT19,
    
    /** ICD procedure code 20. */
    ICD_PRCDR_CD20,
    
    /** ICD procedure version code 20. */
    ICD_PRCDR_VRSN_CD20,
    
    /** Procedure date 20. */
    PRCDR_DT20,
    
    /** ICD procedure code 21. */
    ICD_PRCDR_CD21,
    
    /** ICD procedure version code 21. */
    ICD_PRCDR_VRSN_CD21,
    
    /** Procedure date 21. */
    PRCDR_DT21,
    
    /** ICD procedure code 22. */
    ICD_PRCDR_CD22,
    
    /** ICD procedure version code 22. */
    ICD_PRCDR_VRSN_CD22,
    
    /** Procedure date 22. */
    PRCDR_DT22,
    
    /** ICD procedure code 23. */
    ICD_PRCDR_CD23,
    
    /** ICD procedure version code 23. */
    ICD_PRCDR_VRSN_CD23,
    
    /** Procedure date 23. */
    PRCDR_DT23,
    
    /** ICD procedure code 24. */
    ICD_PRCDR_CD24,
    
    /** ICD procedure version code 24. */
    ICD_PRCDR_VRSN_CD24,
    
    /** Procedure date 24. */
    PRCDR_DT24,
    
    /** ICD procedure code 25. */
    ICD_PRCDR_CD25,
    
    /** ICD procedure version code 25. */
    ICD_PRCDR_VRSN_CD25,
    
    /** Procedure date 25. */
    PRCDR_DT25,
    
    /** Reason for visit code 1. */
    RSN_VISIT_CD1,
    
    /** Reason for visit version code 1. */
    RSN_VISIT_VRSN_CD1,
    
    /** Reason for visit code 2. */
    RSN_VISIT_CD2,
    
    /** Reason for visit version code 2. */
    RSN_VISIT_VRSN_CD2,
    
    /** Reason for visit code 3. */
    RSN_VISIT_CD3,
    
    /** Reason for visit version code 3. */
    RSN_VISIT_VRSN_CD3,
    
    /** NCH beneficiary Part B deductible amount. */
    NCH_BENE_PTB_DDCTBL_AMT,
    
    /** NCH beneficiary Part B coinsurance amount. */
    NCH_BENE_PTB_COINSRNC_AMT,
    
    /** Claim outpatient provider payment amount. */
    CLM_OP_PRVDR_PMT_AMT,
    
    /** Claim outpatient beneficiary payment amount. */
    CLM_OP_BENE_PMT_AMT,
    
    /** Fiscal intermediary document claim control number. */
    FI_DOC_CLM_CNTL_NUM,
    
    /** Fiscal intermediary original claim control number. */
    FI_ORIG_CLM_CNTL_NUM,
    
    /** Claim line number. */
    CLM_LINE_NUM,
    
    /** Revenue center code for the service line. */
    REV_CNTR,
    
    /** Revenue center date when service was provided. */
    REV_CNTR_DT,
    
    /** Revenue center first ANSI code. */
    REV_CNTR_1ST_ANSI_CD,
    
    /** Revenue center second ANSI code. */
    REV_CNTR_2ND_ANSI_CD,
    
    /** Revenue center third ANSI code. */
    REV_CNTR_3RD_ANSI_CD,
    
    /** Revenue center fourth ANSI code. */
    REV_CNTR_4TH_ANSI_CD,
    
    /** Revenue center APC HIPPS code. */
    REV_CNTR_APC_HIPPS_CD,
    
    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,
    
    /** HCPCS first modifier code. */
    HCPCS_1ST_MDFR_CD,
    
    /** HCPCS second modifier code. */
    HCPCS_2ND_MDFR_CD,
    
    /** Revenue center payment method indicator code. */
    REV_CNTR_PMT_MTHD_IND_CD,
    
    /** Revenue center discount indicator code. */
    REV_CNTR_DSCNT_IND_CD,
    
    /** Revenue center packaging indicator code. */
    REV_CNTR_PACKG_IND_CD,
    
    /** Revenue center Outpatient Transitional Add-on Payment code. */
    REV_CNTR_OTAF_PMT_CD,
    
    /** Revenue center IDE NDC UPC number. */
    REV_CNTR_IDE_NDC_UPC_NUM,
    
    /** Revenue center service unit count. */
    REV_CNTR_UNIT_CNT,
    
    /** Revenue center rate amount. */
    REV_CNTR_RATE_AMT,
    
    /** Revenue center blood deductible amount. */
    REV_CNTR_BLOOD_DDCTBL_AMT,
    
    /** Revenue center cash deductible amount. */
    REV_CNTR_CASH_DDCTBL_AMT,
    
    /** Revenue center coinsurance wage adjusted code. */
    REV_CNTR_COINSRNC_WGE_ADJSTD_C,
    
    /** Revenue center reduced coinsurance amount. */
    REV_CNTR_RDCD_COINSRNC_AMT,
    
    /** Revenue center first Medicare Secondary Payer paid amount. */
    REV_CNTR_1ST_MSP_PD_AMT,
    
    /** Revenue center second Medicare Secondary Payer paid amount. */
    REV_CNTR_2ND_MSP_PD_AMT,
    
    /** Revenue center provider payment amount. */
    REV_CNTR_PRVDR_PMT_AMT,
    
    /** Revenue center beneficiary payment amount. */
    REV_CNTR_BENE_PMT_AMT,
    
    /** Revenue center patient responsibility payment. */
    REV_CNTR_PTNT_RSPNSBLTY_PMT,
    
    /** Revenue center payment amount. */
    REV_CNTR_PMT_AMT_AMT,
    
    /** Revenue center total charge amount. */
    REV_CNTR_TOT_CHRG_AMT,
    
    /** Revenue center non-covered charge amount. */
    REV_CNTR_NCVRD_CHRG_AMT,
    
    /** Revenue center status indicator code. */
    REV_CNTR_STUS_IND_CD,
    
    /** Revenue center National Drug Code quantity. */
    REV_CNTR_NDC_QTY,
    
    /** Revenue center NDC quantity qualifier code. */
    REV_CNTR_NDC_QTY_QLFR_CD,
    
    /** Rendering physician Unique Provider Identification Number (legacy). */
    RNDRNG_PHYSN_UPIN,
    
    /** Rendering physician National Provider Identifier. */
    RNDRNG_PHYSN_NPI
  }

  /**
   * Inpatient hospital claims data structure.
   * Represents acute care hospital stays including procedures,
   * diagnoses, and associated charges.
   */
  public enum INPATIENT {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Claim identifier uniquely identifying the inpatient claim. */
    CLM_ID,
    
    /** Claim group identifier for related inpatient claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,
    
    /** NCH claim type code identifying the type of inpatient claim. */
    NCH_CLM_TYPE_CD,
    
    /** Claim from date (admission date). */
    CLM_FROM_DT,
    
    /** Claim through date (discharge date). */
    CLM_THRU_DT,
    
    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,
    
    /** Fiscal intermediary claim processing date. */
    FI_CLM_PROC_DT,
    
    /** Claim query code for claim inquiries. */
    CLAIM_QUERY_CODE,
    
    /** Provider number for the hospital. */
    PRVDR_NUM,
    
    /** Claim facility type code. */
    CLM_FAC_TYPE_CD,
    
    /** Claim service classification type code. */
    CLM_SRVC_CLSFCTN_TYPE_CD,
    
    /** Claim frequency code indicating billing frequency. */
    CLM_FREQ_CD,
    
    /** Fiscal intermediary number. */
    FI_NUM,
    
    /** Claim Medicare non-payment reason code. */
    CLM_MDCR_NON_PMT_RSN_CD,
    
    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,
    
    /** NCH primary payer claim paid amount. */
    NCH_PRMRY_PYR_CLM_PD_AMT,
    
    /** NCH primary payer code. */
    NCH_PRMRY_PYR_CD,
    
    /** Facility intermediary claim action code. */
    FI_CLM_ACTN_CD,
    
    /** Provider state code. */
    PRVDR_STATE_CD,
    
    /** Organization National Provider Identifier number. */
    ORG_NPI_NUM,
    
    /** Attending physician Unique Provider Identification Number (legacy). */
    AT_PHYSN_UPIN,
    
    /** Attending physician National Provider Identifier. */
    AT_PHYSN_NPI,
    
    /** Operating physician Unique Provider Identification Number (legacy). */
    OP_PHYSN_UPIN,
    
    /** Operating physician National Provider Identifier. */
    OP_PHYSN_NPI,
    
    /** Other physician Unique Provider Identification Number (legacy). */
    OT_PHYSN_UPIN,
    
    /** Other physician National Provider Identifier. */
    OT_PHYSN_NPI,
    
    /** Claim Managed Care Organization paid switch. */
    CLM_MCO_PD_SW,
    
    /** Patient discharge status code. */
    PTNT_DSCHRG_STUS_CD,
    
    /** Claim Prospective Payment System indicator code. */
    CLM_PPS_IND_CD,
    
    /** Claim total charge amount. */
    CLM_TOT_CHRG_AMT,
    
    /** Claim admission date. */
    CLM_ADMSN_DT,
    
    /** Claim inpatient admission type code. */
    CLM_IP_ADMSN_TYPE_CD,
    
    /** Claim source of inpatient admission code. */
    CLM_SRC_IP_ADMSN_CD,
    
    /** NCH patient status indicator code. */
    NCH_PTNT_STATUS_IND_CD,
    
    /** Claim pass through per diem amount. */
    CLM_PASS_THRU_PER_DIEM_AMT,
    
    /** NCH beneficiary inpatient deductible amount. */
    NCH_BENE_IP_DDCTBL_AMT,
    
    /** NCH beneficiary Part A coinsurance liability amount. */
    NCH_BENE_PTA_COINSRNC_LBLTY_AM,
    
    /** NCH beneficiary blood deductible liability amount. */
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    
    /** NCH professional component charge amount. */
    NCH_PROFNL_CMPNT_CHRG_AMT,
    
    /** NCH inpatient non-covered charge amount. */
    NCH_IP_NCVRD_CHRG_AMT,
    
    /** NCH inpatient total deduction amount. */
    NCH_IP_TOT_DDCTN_AMT,
    
    /** Claim total PPS capital amount. */
    CLM_TOT_PPS_CPTL_AMT,
    
    /** Claim PPS capital Federal Specific Portion amount. */
    CLM_PPS_CPTL_FSP_AMT,
    
    /** Claim PPS capital outlier amount. */
    CLM_PPS_CPTL_OUTLIER_AMT,
    
    /** Claim PPS capital disproportionate share amount. */
    CLM_PPS_CPTL_DSPRPRTNT_SHR_AMT,
    
    /** Claim PPS capital Indirect Medical Education amount. */
    CLM_PPS_CPTL_IME_AMT,
    
    /** Claim PPS capital exception amount. */
    CLM_PPS_CPTL_EXCPTN_AMT,
    
    /** Claim PPS old capital hold harmless amount. */
    CLM_PPS_OLD_CPTL_HLD_HRMLS_AMT,
    
    /** Claim PPS capital DRG weight number. */
    CLM_PPS_CPTL_DRG_WT_NUM,
    
    /** Claim utilization day count. */
    CLM_UTLZTN_DAY_CNT,
    
    /** Beneficiary total coinsurance days count. */
    BENE_TOT_COINSRNC_DAYS_CNT,
    
    /** Beneficiary lifetime reserve days used count. */
    BENE_LRD_USED_CNT,
    
    /** Claim non-utilization days count. */
    CLM_NON_UTLZTN_DAYS_CNT,
    
    /** NCH blood pints furnished quantity. */
    NCH_BLOOD_PNTS_FRNSHD_QTY,
    
    /** NCH verified non-covered stay from date. */
    NCH_VRFD_NCVRD_STAY_FROM_DT,
    
    /** NCH verified non-covered stay through date. */
    NCH_VRFD_NCVRD_STAY_THRU_DT,
    
    /** NCH active or covered level of care through date. */
    NCH_ACTV_OR_CVRD_LVL_CARE_THRU,
    
    /** NCH beneficiary Medicare benefits exhausted date for inpatient. */
    NCH_BENE_MDCR_BNFTS_EXHTD_DT_I,
    
    /** NCH beneficiary discharge date. */
    NCH_BENE_DSCHRG_DT,
    
    /** Claim Diagnosis Related Group (DRG) code. */
    CLM_DRG_CD,
    
    /** Claim DRG outlier stay code. */
    CLM_DRG_OUTLIER_STAY_CD,
    
    /** NCH DRG outlier approved payment amount. */
    NCH_DRG_OUTLIER_APRVD_PMT_AMT,
    
    /** Admitting diagnosis code. */
    ADMTG_DGNS_CD,
    
    /** Admitting diagnosis version code. */
    ADMTG_DGNS_VRSN_CD,
    
    /** Principal diagnosis code for the admission. */
    PRNCPAL_DGNS_CD,
    
    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,
    
    /** Other diagnosis code 1. */
    ICD_DGNS_CD1,
    
    /** Other diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,
    
    /** Claim Present on Admission indicator switch 1. */
    CLM_POA_IND_SW1,
    
    /** Other diagnosis code 2. */
    ICD_DGNS_CD2,
    
    /** Other diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,
    
    /** Claim Present on Admission indicator switch 2. */
    CLM_POA_IND_SW2,
    
    /** Other diagnosis code 3. */
    ICD_DGNS_CD3,
    
    /** Other diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,
    
    /** Claim Present on Admission indicator switch 3. */
    CLM_POA_IND_SW3,
    
    /** Other diagnosis code 4. */
    ICD_DGNS_CD4,
    
    /** Other diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,
    
    /** Claim Present on Admission indicator switch 4. */
    CLM_POA_IND_SW4,
    
    /** Other diagnosis code 5. */
    ICD_DGNS_CD5,
    
    /** Other diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,
    
    /** Claim Present on Admission indicator switch 5. */
    CLM_POA_IND_SW5,
    
    /** Other diagnosis code 6. */
    ICD_DGNS_CD6,
    
    /** Other diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,
    
    /** Claim Present on Admission indicator switch 6. */
    CLM_POA_IND_SW6,
    
    /** Other diagnosis code 7. */
    ICD_DGNS_CD7,
    
    /** Other diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,
    
    /** Claim Present on Admission indicator switch 7. */
    CLM_POA_IND_SW7,
    
    /** Other diagnosis code 8. */
    ICD_DGNS_CD8,
    
    /** Other diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,
    
    /** Claim Present on Admission indicator switch 8. */
    CLM_POA_IND_SW8,
    
    /** Other diagnosis code 9. */
    ICD_DGNS_CD9,
    
    /** Other diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,
    
    /** Claim Present on Admission indicator switch 9. */
    CLM_POA_IND_SW9,
    
    /** Other diagnosis code 10. */
    ICD_DGNS_CD10,
    
    /** Other diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,
    
    /** Claim Present on Admission indicator switch 10. */
    CLM_POA_IND_SW10,
    
    /** Other diagnosis code 11. */
    ICD_DGNS_CD11,
    
    /** Other diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,
    
    /** Claim Present on Admission indicator switch 11. */
    CLM_POA_IND_SW11,
    
    /** Other diagnosis code 12. */
    ICD_DGNS_CD12,
    
    /** Other diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,
    
    /** Claim Present on Admission indicator switch 12. */
    CLM_POA_IND_SW12,
    
    /** Other diagnosis code 13. */
    ICD_DGNS_CD13,
    
    /** Other diagnosis version code 13. */
    ICD_DGNS_VRSN_CD13,
    
    /** Claim Present on Admission indicator switch 13. */
    CLM_POA_IND_SW13,
    
    /** Other diagnosis code 14. */
    ICD_DGNS_CD14,
    
    /** Other diagnosis version code 14. */
    ICD_DGNS_VRSN_CD14,
    
    /** Claim Present on Admission indicator switch 14. */
    CLM_POA_IND_SW14,
    
    /** Other diagnosis code 15. */
    ICD_DGNS_CD15,
    
    /** Other diagnosis version code 15. */
    ICD_DGNS_VRSN_CD15,
    
    /** Claim Present on Admission indicator switch 15. */
    CLM_POA_IND_SW15,
    
    /** Other diagnosis code 16. */
    ICD_DGNS_CD16,
    
    /** Other diagnosis version code 16. */
    ICD_DGNS_VRSN_CD16,
    
    /** Claim Present on Admission indicator switch 16. */
    CLM_POA_IND_SW16,
    
    /** Other diagnosis code 17. */
    ICD_DGNS_CD17,
    
    /** Other diagnosis version code 17. */
    ICD_DGNS_VRSN_CD17,
    
    /** Claim Present on Admission indicator switch 17. */
    CLM_POA_IND_SW17,
    
    /** Other diagnosis code 18. */
    ICD_DGNS_CD18,
    
    /** Other diagnosis version code 18. */
    ICD_DGNS_VRSN_CD18,
    
    /** Claim Present on Admission indicator switch 18. */
    CLM_POA_IND_SW18,
    
    /** Other diagnosis code 19. */
    ICD_DGNS_CD19,
    
    /** Other diagnosis version code 19. */
    ICD_DGNS_VRSN_CD19,
    
    /** Claim Present on Admission indicator switch 19. */
    CLM_POA_IND_SW19,
    
    /** Other diagnosis code 20. */
    ICD_DGNS_CD20,
    
    /** Other diagnosis version code 20. */
    ICD_DGNS_VRSN_CD20,
    
    /** Claim Present on Admission indicator switch 20. */
    CLM_POA_IND_SW20,
    
    /** Other diagnosis code 21. */
    ICD_DGNS_CD21,
    
    /** Other diagnosis version code 21. */
    ICD_DGNS_VRSN_CD21,
    
    /** Claim Present on Admission indicator switch 21. */
    CLM_POA_IND_SW21,
    
    /** Other diagnosis code 22. */
    ICD_DGNS_CD22,
    
    /** Other diagnosis version code 22. */
    ICD_DGNS_VRSN_CD22,
    
    /** Claim Present on Admission indicator switch 22. */
    CLM_POA_IND_SW22,
    
    /** Other diagnosis code 23. */
    ICD_DGNS_CD23,
    
    /** Other diagnosis version code 23. */
    ICD_DGNS_VRSN_CD23,
    
    /** Claim Present on Admission indicator switch 23. */
    CLM_POA_IND_SW23,
    
    /** Other diagnosis code 24. */
    ICD_DGNS_CD24,
    
    /** Other diagnosis version code 24. */
    ICD_DGNS_VRSN_CD24,
    
    /** Claim Present on Admission indicator switch 24. */
    CLM_POA_IND_SW24,
    
    /** Other diagnosis code 25. */
    ICD_DGNS_CD25,
    
    /** Other diagnosis version code 25. */
    ICD_DGNS_VRSN_CD25,
    
    /** Claim Present on Admission indicator switch 25. */
    CLM_POA_IND_SW25,
    
    /** First external cause of injury code. */
    FST_DGNS_E_CD,
    
    /** First external cause of injury version code. */
    FST_DGNS_E_VRSN_CD,
    
    /** External cause of injury diagnosis code 1. */
    ICD_DGNS_E_CD1,
    
    /** External cause of injury diagnosis version code 1. */
    ICD_DGNS_E_VRSN_CD1,
    
    /** Claim external cause Present on Admission indicator switch 1. */
    CLM_E_POA_IND_SW1,
    
    /** External cause of injury diagnosis code 2. */
    ICD_DGNS_E_CD2,
    
    /** External cause of injury diagnosis version code 2. */
    ICD_DGNS_E_VRSN_CD2,
    
    /** Claim external cause Present on Admission indicator switch 2. */
    CLM_E_POA_IND_SW2,
    
    /** External cause of injury diagnosis code 3. */
    ICD_DGNS_E_CD3,
    
    /** External cause of injury diagnosis version code 3. */
    ICD_DGNS_E_VRSN_CD3,
    
    /** Claim external cause Present on Admission indicator switch 3. */
    CLM_E_POA_IND_SW3,
    
    /** External cause of injury diagnosis code 4. */
    ICD_DGNS_E_CD4,
    
    /** External cause of injury diagnosis version code 4. */
    ICD_DGNS_E_VRSN_CD4,
    
    /** Claim external cause Present on Admission indicator switch 4. */
    CLM_E_POA_IND_SW4,
    
    /** External cause of injury diagnosis code 5. */
    ICD_DGNS_E_CD5,
    
    /** External cause of injury diagnosis version code 5. */
    ICD_DGNS_E_VRSN_CD5,
    
    /** Claim external cause Present on Admission indicator switch 5. */
    CLM_E_POA_IND_SW5,
    
    /** External cause of injury diagnosis code 6. */
    ICD_DGNS_E_CD6,
    
    /** External cause of injury diagnosis version code 6. */
    ICD_DGNS_E_VRSN_CD6,
    
    /** Claim external cause Present on Admission indicator switch 6. */
    CLM_E_POA_IND_SW6,
    
    /** External cause of injury diagnosis code 7. */
    ICD_DGNS_E_CD7,
    
    /** External cause of injury diagnosis version code 7. */
    ICD_DGNS_E_VRSN_CD7,
    
    /** Claim external cause Present on Admission indicator switch 7. */
    CLM_E_POA_IND_SW7,
    
    /** External cause of injury diagnosis code 8. */
    ICD_DGNS_E_CD8,
    
    /** External cause of injury diagnosis version code 8. */
    ICD_DGNS_E_VRSN_CD8,
    
    /** Claim external cause Present on Admission indicator switch 8. */
    CLM_E_POA_IND_SW8,
    
    /** External cause of injury diagnosis code 9. */
    ICD_DGNS_E_CD9,
    
    /** External cause of injury diagnosis version code 9. */
    ICD_DGNS_E_VRSN_CD9,
    
    /** Claim external cause Present on Admission indicator switch 9. */
    CLM_E_POA_IND_SW9,
    
    /** External cause of injury diagnosis code 10. */
    ICD_DGNS_E_CD10,
    
    /** External cause of injury diagnosis version code 10. */
    ICD_DGNS_E_VRSN_CD10,
    
    /** Claim external cause Present on Admission indicator switch 10. */
    CLM_E_POA_IND_SW10,
    
    /** External cause of injury diagnosis code 11. */
    ICD_DGNS_E_CD11,
    
    /** External cause of injury diagnosis version code 11. */
    ICD_DGNS_E_VRSN_CD11,
    
    /** Claim external cause Present on Admission indicator switch 11. */
    CLM_E_POA_IND_SW11,
    
    /** External cause of injury diagnosis code 12. */
    ICD_DGNS_E_CD12,
    
    /** External cause of injury diagnosis version code 12. */
    ICD_DGNS_E_VRSN_CD12,
    
    /** Claim external cause Present on Admission indicator switch 12. */
    CLM_E_POA_IND_SW12,
    
    /** ICD procedure code 1. */
    ICD_PRCDR_CD1,
    
    /** ICD procedure version code 1. */
    ICD_PRCDR_VRSN_CD1,
    
    /** Procedure date 1. */
    PRCDR_DT1,
    
    /** ICD procedure code 2. */
    ICD_PRCDR_CD2,
    
    /** ICD procedure version code 2. */
    ICD_PRCDR_VRSN_CD2,
    
    /** Procedure date 2. */
    PRCDR_DT2,
    
    /** ICD procedure code 3. */
    ICD_PRCDR_CD3,
    
    /** ICD procedure version code 3. */
    ICD_PRCDR_VRSN_CD3,
    
    /** Procedure date 3. */
    PRCDR_DT3,
    
    /** ICD procedure code 4. */
    ICD_PRCDR_CD4,
    
    /** ICD procedure version code 4. */
    ICD_PRCDR_VRSN_CD4,
    
    /** Procedure date 4. */
    PRCDR_DT4,
    
    /** ICD procedure code 5. */
    ICD_PRCDR_CD5,
    
    /** ICD procedure version code 5. */
    ICD_PRCDR_VRSN_CD5,
    
    /** Procedure date 5. */
    PRCDR_DT5,
    
    /** ICD procedure code 6. */
    ICD_PRCDR_CD6,
    
    /** ICD procedure version code 6. */
    ICD_PRCDR_VRSN_CD6,
    
    /** Procedure date 6. */
    PRCDR_DT6,
    
    /** ICD procedure code 7. */
    ICD_PRCDR_CD7,
    
    /** ICD procedure version code 7. */
    ICD_PRCDR_VRSN_CD7,
    
    /** Procedure date 7. */
    PRCDR_DT7,
    
    /** ICD procedure code 8. */
    ICD_PRCDR_CD8,
    
    /** ICD procedure version code 8. */
    ICD_PRCDR_VRSN_CD8,
    
    /** Procedure date 8. */
    PRCDR_DT8,
    
    /** ICD procedure code 9. */
    ICD_PRCDR_CD9,
    
    /** ICD procedure version code 9. */
    ICD_PRCDR_VRSN_CD9,
    
    /** Procedure date 9. */
    PRCDR_DT9,
    
    /** ICD procedure code 10. */
    ICD_PRCDR_CD10,
    
    /** ICD procedure version code 10. */
    ICD_PRCDR_VRSN_CD10,
    
    /** Procedure date 10. */
    PRCDR_DT10,
    
    /** ICD procedure code 11. */
    ICD_PRCDR_CD11,
    
    /** ICD procedure version code 11. */
    ICD_PRCDR_VRSN_CD11,
    
    /** Procedure date 11. */
    PRCDR_DT11,
    
    /** ICD procedure code 12. */
    ICD_PRCDR_CD12,
    
    /** ICD procedure version code 12. */
    ICD_PRCDR_VRSN_CD12,
    
    /** Procedure date 12. */
    PRCDR_DT12,
    
    /** ICD procedure code 13. */
    ICD_PRCDR_CD13,
    
    /** ICD procedure version code 13. */
    ICD_PRCDR_VRSN_CD13,
    
    /** Procedure date 13. */
    PRCDR_DT13,
    
    /** ICD procedure code 14. */
    ICD_PRCDR_CD14,
    
    /** ICD procedure version code 14. */
    ICD_PRCDR_VRSN_CD14,
    
    /** Procedure date 14. */
    PRCDR_DT14,
    
    /** ICD procedure code 15. */
    ICD_PRCDR_CD15,
    
    /** ICD procedure version code 15. */
    ICD_PRCDR_VRSN_CD15,
    
    /** Procedure date 15. */
    PRCDR_DT15,
    
    /** ICD procedure code 16. */
    ICD_PRCDR_CD16,
    
    /** ICD procedure version code 16. */
    ICD_PRCDR_VRSN_CD16,
    
    /** Procedure date 16. */
    PRCDR_DT16,
    
    /** ICD procedure code 17. */
    ICD_PRCDR_CD17,
    
    /** ICD procedure version code 17. */
    ICD_PRCDR_VRSN_CD17,
    
    /** Procedure date 17. */
    PRCDR_DT17,
    
    /** ICD procedure code 18. */
    ICD_PRCDR_CD18,
    
    /** ICD procedure version code 18. */
    ICD_PRCDR_VRSN_CD18,
    
    /** Procedure date 18. */
    PRCDR_DT18,
    
    /** ICD procedure code 19. */
    ICD_PRCDR_CD19,
    
    /** ICD procedure version code 19. */
    ICD_PRCDR_VRSN_CD19,
    
    /** Procedure date 19. */
    PRCDR_DT19,
    
    /** ICD procedure code 20. */
    ICD_PRCDR_CD20,
    
    /** ICD procedure version code 20. */
    ICD_PRCDR_VRSN_CD20,
    
    /** Procedure date 20. */
    PRCDR_DT20,
    
    /** ICD procedure code 21. */
    ICD_PRCDR_CD21,
    
    /** ICD procedure version code 21. */
    ICD_PRCDR_VRSN_CD21,
    
    /** Procedure date 21. */
    PRCDR_DT21,
    
    /** ICD procedure code 22. */
    ICD_PRCDR_CD22,
    
    /** ICD procedure version code 22. */
    ICD_PRCDR_VRSN_CD22,
    
    /** Procedure date 22. */
    PRCDR_DT22,
    
    /** ICD procedure code 23. */
    ICD_PRCDR_CD23,
    
    /** ICD procedure version code 23. */
    ICD_PRCDR_VRSN_CD23,
    
    /** Procedure date 23. */
    PRCDR_DT23,
    
    /** ICD procedure code 24. */
    ICD_PRCDR_CD24,
    
    /** ICD procedure version code 24. */
    ICD_PRCDR_VRSN_CD24,
    
    /** Procedure date 24. */
    PRCDR_DT24,
    
    /** ICD procedure code 25. */
    ICD_PRCDR_CD25,
    
    /** ICD procedure version code 25. */
    ICD_PRCDR_VRSN_CD25,
    
    /** Procedure date 25. */
    PRCDR_DT25,
    
    /** Indirect Medical Education operating claim value amount. */
    IME_OP_CLM_VAL_AMT,
    
    /** Disproportionate Share Hospital operating claim value amount. */
    DSH_OP_CLM_VAL_AMT,
    
    /** Claim uncompensated care payment amount. */
    CLM_UNCOMPD_CARE_PMT_AMT,
    
    /** Fiscal intermediary document claim control number. */
    FI_DOC_CLM_CNTL_NUM,
    
    /** Fiscal intermediary original claim control number. */
    FI_ORIG_CLM_CNTL_NUM,
    
    /** Claim line number. */
    CLM_LINE_NUM,
    
    /** Revenue center code for the service line. */
    REV_CNTR,
    
    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,
    
    /** Revenue center service unit count. */
    REV_CNTR_UNIT_CNT,
    
    /** Revenue center rate amount. */
    REV_CNTR_RATE_AMT,
    
    /** Revenue center total charge amount. */
    REV_CNTR_TOT_CHRG_AMT,
    
    /** Revenue center non-covered charge amount. */
    REV_CNTR_NCVRD_CHRG_AMT,
    
    /** Revenue center deductible coinsurance code. */
    REV_CNTR_DDCTBL_COINSRNC_CD,
    
    /** Revenue center National Drug Code quantity. */
    REV_CNTR_NDC_QTY,
    
    /** Revenue center NDC quantity qualifier code. */
    REV_CNTR_NDC_QTY_QLFR_CD,
    
    /** Rendering physician Unique Provider Identification Number (legacy). */
    RNDRNG_PHYSN_UPIN,
    
    /** Rendering physician National Provider Identifier. */
    RNDRNG_PHYSN_NPI
  }

  /**
   * Inpatient hospital claims diagnosis code arrays.
   * Principal and secondary diagnoses for hospital admissions.
   */
  public static final INPATIENT[][] inpatientDxFields = {
    { INPATIENT.ICD_DGNS_CD1, INPATIENT.ICD_DGNS_VRSN_CD1,
      INPATIENT.CLM_POA_IND_SW1 },
    { INPATIENT.ICD_DGNS_CD2, INPATIENT.ICD_DGNS_VRSN_CD2,
      INPATIENT.CLM_POA_IND_SW2 },
    { INPATIENT.ICD_DGNS_CD3, INPATIENT.ICD_DGNS_VRSN_CD3,
      INPATIENT.CLM_POA_IND_SW3 },
    { INPATIENT.ICD_DGNS_CD4, INPATIENT.ICD_DGNS_VRSN_CD4,
      INPATIENT.CLM_POA_IND_SW4 },
    { INPATIENT.ICD_DGNS_CD5, INPATIENT.ICD_DGNS_VRSN_CD5,
      INPATIENT.CLM_POA_IND_SW5 },
    { INPATIENT.ICD_DGNS_CD6, INPATIENT.ICD_DGNS_VRSN_CD6,
      INPATIENT.CLM_POA_IND_SW6 },
    { INPATIENT.ICD_DGNS_CD7, INPATIENT.ICD_DGNS_VRSN_CD7,
      INPATIENT.CLM_POA_IND_SW7 },
    { INPATIENT.ICD_DGNS_CD8, INPATIENT.ICD_DGNS_VRSN_CD8,
      INPATIENT.CLM_POA_IND_SW8 },
    { INPATIENT.ICD_DGNS_CD9, INPATIENT.ICD_DGNS_VRSN_CD9,
      INPATIENT.CLM_POA_IND_SW9 },
    { INPATIENT.ICD_DGNS_CD10, INPATIENT.ICD_DGNS_VRSN_CD10,
      INPATIENT.CLM_POA_IND_SW10 },
    { INPATIENT.ICD_DGNS_CD11, INPATIENT.ICD_DGNS_VRSN_CD11,
      INPATIENT.CLM_POA_IND_SW11 },
    { INPATIENT.ICD_DGNS_CD12, INPATIENT.ICD_DGNS_VRSN_CD12,
      INPATIENT.CLM_POA_IND_SW12 },
    { INPATIENT.ICD_DGNS_CD13, INPATIENT.ICD_DGNS_VRSN_CD13,
      INPATIENT.CLM_POA_IND_SW13 },
    { INPATIENT.ICD_DGNS_CD14, INPATIENT.ICD_DGNS_VRSN_CD14,
      INPATIENT.CLM_POA_IND_SW14 },
    { INPATIENT.ICD_DGNS_CD15, INPATIENT.ICD_DGNS_VRSN_CD15,
      INPATIENT.CLM_POA_IND_SW15 },
    { INPATIENT.ICD_DGNS_CD16, INPATIENT.ICD_DGNS_VRSN_CD16,
      INPATIENT.CLM_POA_IND_SW16 },
    { INPATIENT.ICD_DGNS_CD17, INPATIENT.ICD_DGNS_VRSN_CD17,
      INPATIENT.CLM_POA_IND_SW17 },
    { INPATIENT.ICD_DGNS_CD18, INPATIENT.ICD_DGNS_VRSN_CD18,
      INPATIENT.CLM_POA_IND_SW18 },
    { INPATIENT.ICD_DGNS_CD19, INPATIENT.ICD_DGNS_VRSN_CD19,
      INPATIENT.CLM_POA_IND_SW19 },
    { INPATIENT.ICD_DGNS_CD20, INPATIENT.ICD_DGNS_VRSN_CD20,
      INPATIENT.CLM_POA_IND_SW20 },
    { INPATIENT.ICD_DGNS_CD21, INPATIENT.ICD_DGNS_VRSN_CD21,
      INPATIENT.CLM_POA_IND_SW21 },
    { INPATIENT.ICD_DGNS_CD22, INPATIENT.ICD_DGNS_VRSN_CD22,
      INPATIENT.CLM_POA_IND_SW22 },
    { INPATIENT.ICD_DGNS_CD23, INPATIENT.ICD_DGNS_VRSN_CD23,
      INPATIENT.CLM_POA_IND_SW23 },
    { INPATIENT.ICD_DGNS_CD24, INPATIENT.ICD_DGNS_VRSN_CD24,
      INPATIENT.CLM_POA_IND_SW24 },
    { INPATIENT.ICD_DGNS_CD25, INPATIENT.ICD_DGNS_VRSN_CD25,
      INPATIENT.CLM_POA_IND_SW25 }
  };

  /**
   * Inpatient hospital procedure code arrays.
   * Surgical and medical procedures performed during hospital stays.
   */
  public static final INPATIENT[][] inpatientPxFields = {
    { INPATIENT.ICD_PRCDR_CD1, INPATIENT.ICD_PRCDR_VRSN_CD1,
      INPATIENT.PRCDR_DT1 },
    { INPATIENT.ICD_PRCDR_CD2, INPATIENT.ICD_PRCDR_VRSN_CD2,
      INPATIENT.PRCDR_DT2 },
    { INPATIENT.ICD_PRCDR_CD3, INPATIENT.ICD_PRCDR_VRSN_CD3,
      INPATIENT.PRCDR_DT3 },
    { INPATIENT.ICD_PRCDR_CD4, INPATIENT.ICD_PRCDR_VRSN_CD4,
      INPATIENT.PRCDR_DT4 },
    { INPATIENT.ICD_PRCDR_CD5, INPATIENT.ICD_PRCDR_VRSN_CD5,
      INPATIENT.PRCDR_DT5 },
    { INPATIENT.ICD_PRCDR_CD6, INPATIENT.ICD_PRCDR_VRSN_CD6,
      INPATIENT.PRCDR_DT6 },
    { INPATIENT.ICD_PRCDR_CD7, INPATIENT.ICD_PRCDR_VRSN_CD7,
      INPATIENT.PRCDR_DT7 },
    { INPATIENT.ICD_PRCDR_CD8, INPATIENT.ICD_PRCDR_VRSN_CD8,
      INPATIENT.PRCDR_DT8 },
    { INPATIENT.ICD_PRCDR_CD9, INPATIENT.ICD_PRCDR_VRSN_CD9,
      INPATIENT.PRCDR_DT9 },
    { INPATIENT.ICD_PRCDR_CD10, INPATIENT.ICD_PRCDR_VRSN_CD10,
      INPATIENT.PRCDR_DT10 },
    { INPATIENT.ICD_PRCDR_CD11, INPATIENT.ICD_PRCDR_VRSN_CD11,
      INPATIENT.PRCDR_DT11 },
    { INPATIENT.ICD_PRCDR_CD12, INPATIENT.ICD_PRCDR_VRSN_CD12,
      INPATIENT.PRCDR_DT12 },
    { INPATIENT.ICD_PRCDR_CD13, INPATIENT.ICD_PRCDR_VRSN_CD13,
      INPATIENT.PRCDR_DT13 },
    { INPATIENT.ICD_PRCDR_CD14, INPATIENT.ICD_PRCDR_VRSN_CD14,
      INPATIENT.PRCDR_DT14 },
    { INPATIENT.ICD_PRCDR_CD15, INPATIENT.ICD_PRCDR_VRSN_CD15,
      INPATIENT.PRCDR_DT15 },
    { INPATIENT.ICD_PRCDR_CD16, INPATIENT.ICD_PRCDR_VRSN_CD16,
      INPATIENT.PRCDR_DT16 },
    { INPATIENT.ICD_PRCDR_CD17, INPATIENT.ICD_PRCDR_VRSN_CD17,
      INPATIENT.PRCDR_DT17 },
    { INPATIENT.ICD_PRCDR_CD18, INPATIENT.ICD_PRCDR_VRSN_CD18,
      INPATIENT.PRCDR_DT18 },
    { INPATIENT.ICD_PRCDR_CD19, INPATIENT.ICD_PRCDR_VRSN_CD19,
      INPATIENT.PRCDR_DT19 },
    { INPATIENT.ICD_PRCDR_CD20, INPATIENT.ICD_PRCDR_VRSN_CD20,
      INPATIENT.PRCDR_DT20 },
    { INPATIENT.ICD_PRCDR_CD21, INPATIENT.ICD_PRCDR_VRSN_CD21,
      INPATIENT.PRCDR_DT21 },
    { INPATIENT.ICD_PRCDR_CD22, INPATIENT.ICD_PRCDR_VRSN_CD22,
      INPATIENT.PRCDR_DT22 },
    { INPATIENT.ICD_PRCDR_CD23, INPATIENT.ICD_PRCDR_VRSN_CD23,
      INPATIENT.PRCDR_DT23 },
    { INPATIENT.ICD_PRCDR_CD24, INPATIENT.ICD_PRCDR_VRSN_CD24,
      INPATIENT.PRCDR_DT24 },
    { INPATIENT.ICD_PRCDR_CD25, INPATIENT.ICD_PRCDR_VRSN_CD25,
      INPATIENT.PRCDR_DT25 }
  };

  /**
   * Outpatient facility diagnosis code arrays.
   * Diagnoses associated with outpatient visits and procedures.
   */
  public static final OUTPATIENT[][] outpatientDxFields = {
    { OUTPATIENT.ICD_DGNS_CD1, OUTPATIENT.ICD_DGNS_VRSN_CD1 },
    { OUTPATIENT.ICD_DGNS_CD2, OUTPATIENT.ICD_DGNS_VRSN_CD2 },
    { OUTPATIENT.ICD_DGNS_CD3, OUTPATIENT.ICD_DGNS_VRSN_CD3 },
    { OUTPATIENT.ICD_DGNS_CD4, OUTPATIENT.ICD_DGNS_VRSN_CD4 },
    { OUTPATIENT.ICD_DGNS_CD5, OUTPATIENT.ICD_DGNS_VRSN_CD5 },
    { OUTPATIENT.ICD_DGNS_CD6, OUTPATIENT.ICD_DGNS_VRSN_CD6 },
    { OUTPATIENT.ICD_DGNS_CD7, OUTPATIENT.ICD_DGNS_VRSN_CD7 },
    { OUTPATIENT.ICD_DGNS_CD8, OUTPATIENT.ICD_DGNS_VRSN_CD8 },
    { OUTPATIENT.ICD_DGNS_CD9, OUTPATIENT.ICD_DGNS_VRSN_CD9 },
    { OUTPATIENT.ICD_DGNS_CD10, OUTPATIENT.ICD_DGNS_VRSN_CD10 },
    { OUTPATIENT.ICD_DGNS_CD11, OUTPATIENT.ICD_DGNS_VRSN_CD11 },
    { OUTPATIENT.ICD_DGNS_CD12, OUTPATIENT.ICD_DGNS_VRSN_CD12 },
    { OUTPATIENT.ICD_DGNS_CD13, OUTPATIENT.ICD_DGNS_VRSN_CD13 },
    { OUTPATIENT.ICD_DGNS_CD14, OUTPATIENT.ICD_DGNS_VRSN_CD14 },
    { OUTPATIENT.ICD_DGNS_CD15, OUTPATIENT.ICD_DGNS_VRSN_CD15 },
    { OUTPATIENT.ICD_DGNS_CD16, OUTPATIENT.ICD_DGNS_VRSN_CD16 },
    { OUTPATIENT.ICD_DGNS_CD17, OUTPATIENT.ICD_DGNS_VRSN_CD17 },
    { OUTPATIENT.ICD_DGNS_CD18, OUTPATIENT.ICD_DGNS_VRSN_CD18 },
    { OUTPATIENT.ICD_DGNS_CD19, OUTPATIENT.ICD_DGNS_VRSN_CD19 },
    { OUTPATIENT.ICD_DGNS_CD20, OUTPATIENT.ICD_DGNS_VRSN_CD20 },
    { OUTPATIENT.ICD_DGNS_CD21, OUTPATIENT.ICD_DGNS_VRSN_CD21 },
    { OUTPATIENT.ICD_DGNS_CD22, OUTPATIENT.ICD_DGNS_VRSN_CD22 },
    { OUTPATIENT.ICD_DGNS_CD23, OUTPATIENT.ICD_DGNS_VRSN_CD23 },
    { OUTPATIENT.ICD_DGNS_CD24, OUTPATIENT.ICD_DGNS_VRSN_CD24 },
    { OUTPATIENT.ICD_DGNS_CD25, OUTPATIENT.ICD_DGNS_VRSN_CD25 }
  };

  /**
   * Outpatient facility procedure code arrays.
   * Procedures and services provided in outpatient settings.
   */
  public static final OUTPATIENT[][] outpatientPxFields = {
    { OUTPATIENT.ICD_PRCDR_CD1, OUTPATIENT.ICD_PRCDR_VRSN_CD1,
      OUTPATIENT.PRCDR_DT1 },
    { OUTPATIENT.ICD_PRCDR_CD2, OUTPATIENT.ICD_PRCDR_VRSN_CD2,
      OUTPATIENT.PRCDR_DT2 },
    { OUTPATIENT.ICD_PRCDR_CD3, OUTPATIENT.ICD_PRCDR_VRSN_CD3,
      OUTPATIENT.PRCDR_DT3 },
    { OUTPATIENT.ICD_PRCDR_CD4, OUTPATIENT.ICD_PRCDR_VRSN_CD4,
      OUTPATIENT.PRCDR_DT4 },
    { OUTPATIENT.ICD_PRCDR_CD5, OUTPATIENT.ICD_PRCDR_VRSN_CD5,
      OUTPATIENT.PRCDR_DT5 },
    { OUTPATIENT.ICD_PRCDR_CD6, OUTPATIENT.ICD_PRCDR_VRSN_CD6,
      OUTPATIENT.PRCDR_DT6 },
    { OUTPATIENT.ICD_PRCDR_CD7, OUTPATIENT.ICD_PRCDR_VRSN_CD7,
      OUTPATIENT.PRCDR_DT7 },
    { OUTPATIENT.ICD_PRCDR_CD8, OUTPATIENT.ICD_PRCDR_VRSN_CD8,
      OUTPATIENT.PRCDR_DT8 },
    { OUTPATIENT.ICD_PRCDR_CD9, OUTPATIENT.ICD_PRCDR_VRSN_CD9,
      OUTPATIENT.PRCDR_DT9 },
    { OUTPATIENT.ICD_PRCDR_CD10, OUTPATIENT.ICD_PRCDR_VRSN_CD10,
      OUTPATIENT.PRCDR_DT10 },
    { OUTPATIENT.ICD_PRCDR_CD11, OUTPATIENT.ICD_PRCDR_VRSN_CD11,
      OUTPATIENT.PRCDR_DT11 },
    { OUTPATIENT.ICD_PRCDR_CD12, OUTPATIENT.ICD_PRCDR_VRSN_CD12,
      OUTPATIENT.PRCDR_DT12 },
    { OUTPATIENT.ICD_PRCDR_CD13, OUTPATIENT.ICD_PRCDR_VRSN_CD13,
      OUTPATIENT.PRCDR_DT13 },
    { OUTPATIENT.ICD_PRCDR_CD14, OUTPATIENT.ICD_PRCDR_VRSN_CD14,
      OUTPATIENT.PRCDR_DT14 },
    { OUTPATIENT.ICD_PRCDR_CD15, OUTPATIENT.ICD_PRCDR_VRSN_CD15,
      OUTPATIENT.PRCDR_DT15 },
    { OUTPATIENT.ICD_PRCDR_CD16, OUTPATIENT.ICD_PRCDR_VRSN_CD16,
      OUTPATIENT.PRCDR_DT16 },
    { OUTPATIENT.ICD_PRCDR_CD17, OUTPATIENT.ICD_PRCDR_VRSN_CD17,
      OUTPATIENT.PRCDR_DT17 },
    { OUTPATIENT.ICD_PRCDR_CD18, OUTPATIENT.ICD_PRCDR_VRSN_CD18,
      OUTPATIENT.PRCDR_DT18 },
    { OUTPATIENT.ICD_PRCDR_CD19, OUTPATIENT.ICD_PRCDR_VRSN_CD19,
      OUTPATIENT.PRCDR_DT19 },
    { OUTPATIENT.ICD_PRCDR_CD20, OUTPATIENT.ICD_PRCDR_VRSN_CD20,
      OUTPATIENT.PRCDR_DT20 },
    { OUTPATIENT.ICD_PRCDR_CD21, OUTPATIENT.ICD_PRCDR_VRSN_CD21,
      OUTPATIENT.PRCDR_DT21 },
    { OUTPATIENT.ICD_PRCDR_CD22, OUTPATIENT.ICD_PRCDR_VRSN_CD22,
      OUTPATIENT.PRCDR_DT22 },
    { OUTPATIENT.ICD_PRCDR_CD23, OUTPATIENT.ICD_PRCDR_VRSN_CD23,
      OUTPATIENT.PRCDR_DT23 },
    { OUTPATIENT.ICD_PRCDR_CD24, OUTPATIENT.ICD_PRCDR_VRSN_CD24,
      OUTPATIENT.PRCDR_DT24 },
    { OUTPATIENT.ICD_PRCDR_CD25, OUTPATIENT.ICD_PRCDR_VRSN_CD25,
      OUTPATIENT.PRCDR_DT25 }
  };

  /**
   * Medicare Carrier claims data structure.
   * Represents physician and supplier claims including office visits,
   * procedures, and durable medical equipment.
   */
  public enum CARRIER {
    /** Data Management Layer indicator for record processing. */
    DML_IND,

    /** Unique beneficiary identifier. */
    BENE_ID,

    /** Claim identifier uniquely identifying the claim. */
    CLM_ID,

    /** Claim group identifier for related claims. */
    CLM_GRP_ID,

    /** Final action indicator for claim processing status. */
    FINAL_ACTION,

    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,

    /** NCH claim type code identifying the type of claim. */
    NCH_CLM_TYPE_CD,

    /** Claim from date (start of service period). */
    CLM_FROM_DT,

    /** Claim through date (end of service period). */
    CLM_THRU_DT,

    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,

    /** Carrier claim entry code indicating how claim was submitted. */
    CARR_CLM_ENTRY_CD,

    /** Claim disposition code indicating final claim status. */
    CLM_DISP_CD,

    /** Carrier number identifying the Medicare contractor. */
    CARR_NUM,

    /** Carrier claim payment denial code. */
    CARR_CLM_PMT_DNL_CD,

    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,

    /** Carrier claim primary payer paid amount. */
    CARR_CLM_PRMRY_PYR_PD_AMT,

    /** Referring physician Unique Provider Identification Number (legacy). */
    RFR_PHYSN_UPIN,

    /** Referring physician National Provider Identifier. */
    RFR_PHYSN_NPI,

    /** Carrier claim provider assignment indicator switch. */
    CARR_CLM_PRVDR_ASGNMT_IND_SW,

    /** NCH claim provider payment amount. */
    NCH_CLM_PRVDR_PMT_AMT,

    /** NCH claim beneficiary payment amount. */
    NCH_CLM_BENE_PMT_AMT,

    /** NCH carrier claim submitted charge amount. */
    NCH_CARR_CLM_SBMTD_CHRG_AMT,

    /** NCH carrier claim allowed amount. */
    NCH_CARR_CLM_ALOWD_AMT,

    /** Carrier claim cash deductible applied amount. */
    CARR_CLM_CASH_DDCTBL_APLD_AMT,

    /** Carrier claim HCPCS year code. */
    CARR_CLM_HCPCS_YR_CD,

    /** Carrier claim referring Provider Identification Number. */
    CARR_CLM_RFRNG_PIN_NUM,

    /** Principal diagnosis code for the claim. */
    PRNCPAL_DGNS_CD,

    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,

    /** ICD diagnosis code 1. */
    ICD_DGNS_CD1,

    /** ICD diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,

    /** ICD diagnosis code 2. */
    ICD_DGNS_CD2,

    /** ICD diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,

    /** ICD diagnosis code 3. */
    ICD_DGNS_CD3,

    /** ICD diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,

    /** ICD diagnosis code 4. */
    ICD_DGNS_CD4,

    /** ICD diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,

    /** ICD diagnosis code 5. */
    ICD_DGNS_CD5,

    /** ICD diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,

    /** ICD diagnosis code 6. */
    ICD_DGNS_CD6,

    /** ICD diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,

    /** ICD diagnosis code 7. */
    ICD_DGNS_CD7,

    /** ICD diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,

    /** ICD diagnosis code 8. */
    ICD_DGNS_CD8,

    /** ICD diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,

    /** ICD diagnosis code 9. */
    ICD_DGNS_CD9,

    /** ICD diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,

    /** ICD diagnosis code 10. */
    ICD_DGNS_CD10,

    /** ICD diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,

    /** ICD diagnosis code 11. */
    ICD_DGNS_CD11,

    /** ICD diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,

    /** ICD diagnosis code 12. */
    ICD_DGNS_CD12,

    /** ICD diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,

    /** Claim clinical trial number if applicable. */
    CLM_CLNCL_TRIL_NUM,

    /** Carrier claim control number. */
    CARR_CLM_CNTL_NUM,

    /** Carrier claim billing National Provider Identifier number. */
    CARR_CLM_BLG_NPI_NUM,

    /** Line number for the service line item. */
    LINE_NUM,

    /** Carrier performing Provider Identification Number. */
    CARR_PRFRNG_PIN_NUM,

    /** Performing physician Unique Provider Identification Number (legacy). */
    PRF_PHYSN_UPIN,

    /** Performing physician National Provider Identifier. */
    PRF_PHYSN_NPI,

    /** Organization National Provider Identifier number. */
    ORG_NPI_NUM,

    /** Carrier line provider type code. */
    CARR_LINE_PRVDR_TYPE_CD,

    /** Tax identification number of the provider. */
    TAX_NUM,

    /** Provider state code. */
    PRVDR_STATE_CD,

    /** Provider ZIP code. */
    PRVDR_ZIP,

    /** Provider specialty code. */
    PRVDR_SPCLTY,

    /** Participating provider indicator code. */
    PRTCPTNG_IND_CD,

    /** Carrier line reduced payment physician assistant code. */
    CARR_LINE_RDCD_PMT_PHYS_ASTN_C,

    /** Line service count (number of services provided). */
    LINE_SRVC_CNT,

    /** Line CMS type of service code. */
    LINE_CMS_TYPE_SRVC_CD,

    /** Line place of service code. */
    LINE_PLACE_OF_SRVC_CD,

    /** Carrier line pricing locality code. */
    CARR_LINE_PRCNG_LCLTY_CD,

    /** Line first expense date (start of service). */
    LINE_1ST_EXPNS_DT,

    /** Line last expense date (end of service). */
    LINE_LAST_EXPNS_DT,

    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,

    /** HCPCS first modifier code. */
    HCPCS_1ST_MDFR_CD,

    /** HCPCS second modifier code. */
    HCPCS_2ND_MDFR_CD,

    /** Berenson-Eggers Type of Service (BETOS) code. */
    BETOS_CD,

    /** Line NCH payment amount. */
    LINE_NCH_PMT_AMT,

    /** Line beneficiary payment amount. */
    LINE_BENE_PMT_AMT,

    /** Line provider payment amount. */
    LINE_PRVDR_PMT_AMT,

    /** Line beneficiary Part B deductible amount. */
    LINE_BENE_PTB_DDCTBL_AMT,

    /** Line beneficiary primary payer code. */
    LINE_BENE_PRMRY_PYR_CD,

    /** Line beneficiary primary payer paid amount. */
    LINE_BENE_PRMRY_PYR_PD_AMT,

    /** Line coinsurance amount. */
    LINE_COINSRNC_AMT,

    /** Line submitted charge amount. */
    LINE_SBMTD_CHRG_AMT,

    /** Line allowed charge amount. */
    LINE_ALOWD_CHRG_AMT,

    /** Line processing indicator code. */
    LINE_PRCSG_IND_CD,

    /** Line payment 80/100 percent code. */
    LINE_PMT_80_100_CD,

    /** Line service deductible amount. */
    LINE_SERVICE_DEDUCTIBLE,

    /** Carrier line Miles, Time, Units, or Services count. */
    CARR_LINE_MTUS_CNT,

    /** Carrier line Miles, Time, Units, or Services code. */
    CARR_LINE_MTUS_CD,

    /** Line ICD diagnosis code. */
    LINE_ICD_DGNS_CD,

    /** Line ICD diagnosis version code. */
    LINE_ICD_DGNS_VRSN_CD,

    /** Health Professional Shortage Area scarcity indicator code. */
    HPSA_SCRCTY_IND_CD,

    /** Carrier line prescription number. */
    CARR_LINE_RX_NUM,

    /** Line hematocrit or hemoglobin test result number. */
    LINE_HCT_HGB_RSLT_NUM,

    /** Line hematocrit or hemoglobin test type code. */
    LINE_HCT_HGB_TYPE_CD,

    /** Line National Drug Code. */
    LINE_NDC_CD,

    /** Carrier line Clinical Laboratory Improvement Amendments lab number. */
    CARR_LINE_CLIA_LAB_NUM,

    /** Carrier line anesthesia unit count. */
    CARR_LINE_ANSTHSA_UNIT_CNT
  }

  /**
   * Carrier claims diagnosis code field arrays.
   * Multiple diagnosis code positions for physician and supplier claims.
   */
  public static final CARRIER[][] carrierDxFields = {
    { CARRIER.ICD_DGNS_CD1, CARRIER.ICD_DGNS_VRSN_CD1 },
    { CARRIER.ICD_DGNS_CD2, CARRIER.ICD_DGNS_VRSN_CD2 },
    { CARRIER.ICD_DGNS_CD3, CARRIER.ICD_DGNS_VRSN_CD3 },
    { CARRIER.ICD_DGNS_CD4, CARRIER.ICD_DGNS_VRSN_CD4 },
    { CARRIER.ICD_DGNS_CD5, CARRIER.ICD_DGNS_VRSN_CD5 },
    { CARRIER.ICD_DGNS_CD6, CARRIER.ICD_DGNS_VRSN_CD6 },
    { CARRIER.ICD_DGNS_CD7, CARRIER.ICD_DGNS_VRSN_CD7 },
    { CARRIER.ICD_DGNS_CD8, CARRIER.ICD_DGNS_VRSN_CD8 },
    { CARRIER.ICD_DGNS_CD9, CARRIER.ICD_DGNS_VRSN_CD9 },
    { CARRIER.ICD_DGNS_CD10, CARRIER.ICD_DGNS_VRSN_CD10 },
    { CARRIER.ICD_DGNS_CD11, CARRIER.ICD_DGNS_VRSN_CD11 },
    { CARRIER.ICD_DGNS_CD12, CARRIER.ICD_DGNS_VRSN_CD12 }
  };

  /**
   * Prescription Drug Event (PDE) claims data structure.
   * Covers Medicare Part D prescription drug claims including
   * medication details, costs, and coverage information.
   */
  public enum PDE {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Prescription drug event identifier. */
    PDE_ID,
    
    /** Claim group identifier for related PDE claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Service date when prescription was filled. */
    SRVC_DT,
    
    /** Prescription dispensing date. */
    PD_DT,
    
    /** Service provider identifier qualifier code. */
    SRVC_PRVDR_ID_QLFYR_CD,
    
    /** Service provider identifier. */
    SRVC_PRVDR_ID,
    
    /** Prescriber identifier qualifier code. */
    PRSCRBR_ID_QLFYR_CD,
    
    /** Prescriber identifier (DEA number or NPI). */
    PRSCRBR_ID,
    
    /** Prescription service reference number. */
    RX_SRVC_RFRNC_NUM,
    
    /** Product service identifier (National Drug Code). */
    PROD_SRVC_ID,
    
    /** Plan contract record identifier. */
    PLAN_CNTRCT_REC_ID,
    
    /** Plan Prescription Benefit Package record number. */
    PLAN_PBP_REC_NUM,
    
    /** Compound code indicator. */
    CMPND_CD,
    
    /** Dispense as Written product selection code. */
    DAW_PROD_SLCTN_CD,
    
    /** Quantity dispensed number. */
    QTY_DSPNSD_NUM,
    
    /** Days supply number for prescription. */
    DAYS_SUPLY_NUM,
    
    /** Fill number (original fill or refill number). */
    FILL_NUM,
    
    /** Dispensing status code. */
    DSPNSNG_STUS_CD,
    
    /** Drug coverage status code. */
    DRUG_CVRG_STUS_CD,
    
    /** Adjustment deletion code. */
    ADJSTMT_DLTN_CD,
    
    /** Non-standard format code. */
    NSTD_FRMT_CD,
    
    /** Pricing exception code. */
    PRCNG_EXCPTN_CD,
    
    /** Catastrophic coverage code. */
    CTSTRPHC_CVRG_CD,
    
    /** Generic Drug Copay below Out-of-Pocket threshold amount. */
    GDC_BLW_OOPT_AMT,
    
    /** Generic Drug Copay above Out-of-Pocket threshold amount. */
    GDC_ABV_OOPT_AMT,
    
    /** Patient payment amount for prescription. */
    PTNT_PAY_AMT,
    
    /** Other True Out-of-Pocket amount. */
    OTHR_TROOP_AMT,
    
    /** Low-Income Cost Sharing subsidy amount. */
    LICS_AMT,
    
    /** Patient Liability Reduction due to Other Payer amount. */
    PLRO_AMT,
    
    /** Covered Part D plan paid amount. */
    CVRD_D_PLAN_PD_AMT,
    
    /** Non-covered plan paid amount. */
    NCVRD_PLAN_PD_AMT,
    
    /** Total prescription cost amount. */
    TOT_RX_CST_AMT,
    
    /** Prescription origination code. */
    RX_ORGN_CD,
    
    /** Reported gap discount number. */
    RPTD_GAP_DSCNT_NUM,
    
    /** Brand/Generic code indicator. */
    BRND_GNRC_CD,
    
    /** Pharmacy service type code. */
    PHRMCY_SRVC_TYPE_CD,
    
    /** Patient residence code. */
    PTNT_RSDNC_CD,
    
    /** Submission clarification code. */
    SUBMSN_CLR_CD
  }

  /**
   * Durable Medical Equipment (DME) claims data structure.
   * Covers medical equipment, prosthetics, orthotics, and supplies
   * provided through Medicare Part B.
   */
  public enum DME {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Claim identifier uniquely identifying the DME claim. */
    CLM_ID,
    
    /** Claim group identifier for related DME claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,
    
    /** NCH claim type code identifying the type of DME claim. */
    NCH_CLM_TYPE_CD,
    
    /** Claim from date (start of service period). */
    CLM_FROM_DT,
    
    /** Claim through date (end of service period). */
    CLM_THRU_DT,
    
    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,
    
    /** Carrier claim entry code indicating how claim was submitted. */
    CARR_CLM_ENTRY_CD,
    
    /** Claim disposition code indicating final claim status. */
    CLM_DISP_CD,
    
    /** Carrier number identifying the Medicare contractor. */
    CARR_NUM,
    
    /** Carrier claim payment denial code. */
    CARR_CLM_PMT_DNL_CD,
    
    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,
    
    /** Carrier claim primary payer paid amount. */
    CARR_CLM_PRMRY_PYR_PD_AMT,
    
    /** Carrier claim provider assignment indicator switch. */
    CARR_CLM_PRVDR_ASGNMT_IND_SW,
    
    /** NCH claim provider payment amount. */
    NCH_CLM_PRVDR_PMT_AMT,
    
    /** NCH claim beneficiary payment amount. */
    NCH_CLM_BENE_PMT_AMT,
    
    /** NCH carrier claim submitted charge amount. */
    NCH_CARR_CLM_SBMTD_CHRG_AMT,
    
    /** NCH carrier claim allowed amount. */
    NCH_CARR_CLM_ALOWD_AMT,
    
    /** Carrier claim cash deductible applied amount. */
    CARR_CLM_CASH_DDCTBL_APLD_AMT,
    
    /** Carrier claim HCPCS year code. */
    CARR_CLM_HCPCS_YR_CD,
    
    /** Principal diagnosis code for the DME claim. */
    PRNCPAL_DGNS_CD,
    
    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,
    
    /** ICD diagnosis code 1. */
    ICD_DGNS_CD1,
    
    /** ICD diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,
    
    /** ICD diagnosis code 2. */
    ICD_DGNS_CD2,
    
    /** ICD diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,
    
    /** ICD diagnosis code 3. */
    ICD_DGNS_CD3,
    
    /** ICD diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,
    
    /** ICD diagnosis code 4. */
    ICD_DGNS_CD4,
    
    /** ICD diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,
    
    /** ICD diagnosis code 5. */
    ICD_DGNS_CD5,
    
    /** ICD diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,
    
    /** ICD diagnosis code 6. */
    ICD_DGNS_CD6,
    
    /** ICD diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,
    
    /** ICD diagnosis code 7. */
    ICD_DGNS_CD7,
    
    /** ICD diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,
    
    /** ICD diagnosis code 8. */
    ICD_DGNS_CD8,
    
    /** ICD diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,
    
    /** ICD diagnosis code 9. */
    ICD_DGNS_CD9,
    
    /** ICD diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,
    
    /** ICD diagnosis code 10. */
    ICD_DGNS_CD10,
    
    /** ICD diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,
    
    /** ICD diagnosis code 11. */
    ICD_DGNS_CD11,
    
    /** ICD diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,
    
    /** ICD diagnosis code 12. */
    ICD_DGNS_CD12,
    
    /** ICD diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,
    
    /** Referring physician Unique Provider Identification Number (legacy). */
    RFR_PHYSN_UPIN,
    
    /** Referring physician National Provider Identifier. */
    RFR_PHYSN_NPI,
    
    /** Claim clinical trial number if applicable. */
    CLM_CLNCL_TRIL_NUM,
    
    /** Carrier claim control number. */
    CARR_CLM_CNTL_NUM,
    
    /** Line number for the service line item. */
    LINE_NUM,
    
    /** Tax identification number of the provider. */
    TAX_NUM,
    
    /** Provider specialty code. */
    PRVDR_SPCLTY,
    
    /** Participating provider indicator code. */
    PRTCPTNG_IND_CD,
    
    /** Line service count (number of services provided). */
    LINE_SRVC_CNT,
    
    /** Line CMS type of service code. */
    LINE_CMS_TYPE_SRVC_CD,
    
    /** Line place of service code. */
    LINE_PLACE_OF_SRVC_CD,
    
    /** Line first expense date (start of service). */
    LINE_1ST_EXPNS_DT,
    
    /** Line last expense date (end of service). */
    LINE_LAST_EXPNS_DT,
    
    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,
    
    /** HCPCS first modifier code. */
    HCPCS_1ST_MDFR_CD,
    
    /** HCPCS second modifier code. */
    HCPCS_2ND_MDFR_CD,
    
    /** Berenson-Eggers Type of Service (BETOS) code. */
    BETOS_CD,
    
    /** Line NCH payment amount. */
    LINE_NCH_PMT_AMT,
    
    /** Line beneficiary payment amount. */
    LINE_BENE_PMT_AMT,
    
    /** Line provider payment amount. */
    LINE_PRVDR_PMT_AMT,
    
    /** Line beneficiary Part B deductible amount. */
    LINE_BENE_PTB_DDCTBL_AMT,
    
    /** Line beneficiary primary payer code. */
    LINE_BENE_PRMRY_PYR_CD,
    
    /** Line beneficiary primary payer paid amount. */
    LINE_BENE_PRMRY_PYR_PD_AMT,
    
    /** Line coinsurance amount. */
    LINE_COINSRNC_AMT,
    
    /** Line primary allowed charge amount. */
    LINE_PRMRY_ALOWD_CHRG_AMT,
    
    /** Line submitted charge amount. */
    LINE_SBMTD_CHRG_AMT,
    
    /** Line allowed charge amount. */
    LINE_ALOWD_CHRG_AMT,
    
    /** Line processing indicator code. */
    LINE_PRCSG_IND_CD,
    
    /** Line payment 80/100 percent code. */
    LINE_PMT_80_100_CD,
    
    /** Line service deductible amount. */
    LINE_SERVICE_DEDUCTIBLE,
    
    /** Line ICD diagnosis code. */
    LINE_ICD_DGNS_CD,
    
    /** Line ICD diagnosis version code. */
    LINE_ICD_DGNS_VRSN_CD,
    
    /** Line DME purchase price amount. */
    LINE_DME_PRCHS_PRICE_AMT,
    
    /** Provider number for the DME supplier. */
    PRVDR_NUM,
    
    /** Provider National Provider Identifier. */
    PRVDR_NPI,
    
    /** DMERC line pricing state code. */
    DMERC_LINE_PRCNG_STATE_CD,
    
    /** Provider state code. */
    PRVDR_STATE_CD,
    
    /** DMERC line supplier type code. */
    DMERC_LINE_SUPPLR_TYPE_CD,
    
    /** HCPCS third modifier code. */
    HCPCS_3RD_MDFR_CD,
    
    /** HCPCS fourth modifier code. */
    HCPCS_4TH_MDFR_CD,
    
    /** DMERC line screen savings amount. */
    DMERC_LINE_SCRN_SVGS_AMT,
    
    /** DMERC line Miles, Time, Units, or Services count. */
    DMERC_LINE_MTUS_CNT,
    
    /** DMERC line Miles, Time, Units, or Services code. */
    DMERC_LINE_MTUS_CD,
    
    /** Line hematocrit or hemoglobin test result number. */
    LINE_HCT_HGB_RSLT_NUM,
    
    /** Line hematocrit or hemoglobin test type code. */
    LINE_HCT_HGB_TYPE_CD,
    
    /** Line National Drug Code. */
    LINE_NDC_CD
  }

  /**
   * Durable Medical Equipment claims diagnosis code arrays.
   * Diagnosis justification for DME claims and equipment needs.
   */
  public static final DME[][] dmeDxFields = {
    { DME.ICD_DGNS_CD1, DME.ICD_DGNS_VRSN_CD1 },
    { DME.ICD_DGNS_CD2, DME.ICD_DGNS_VRSN_CD2 },
    { DME.ICD_DGNS_CD3, DME.ICD_DGNS_VRSN_CD3 },
    { DME.ICD_DGNS_CD4, DME.ICD_DGNS_VRSN_CD4 },
    { DME.ICD_DGNS_CD5, DME.ICD_DGNS_VRSN_CD5 },
    { DME.ICD_DGNS_CD6, DME.ICD_DGNS_VRSN_CD6 },
    { DME.ICD_DGNS_CD7, DME.ICD_DGNS_VRSN_CD7 },
    { DME.ICD_DGNS_CD8, DME.ICD_DGNS_VRSN_CD8 },
    { DME.ICD_DGNS_CD9, DME.ICD_DGNS_VRSN_CD9 },
    { DME.ICD_DGNS_CD10, DME.ICD_DGNS_VRSN_CD10 },
    { DME.ICD_DGNS_CD11, DME.ICD_DGNS_VRSN_CD11 },
    { DME.ICD_DGNS_CD12, DME.ICD_DGNS_VRSN_CD12 }
  };

  /**
   * National Provider Identifier (NPI) data structure.
   * Contains healthcare provider identification and taxonomy information
   * for organizations and individual practitioners.
   */
  public enum NPI {
    /** National Provider Identifier number. */
    NPI,
    
    /** Provider entity type code (1=Individual, 2=Organization). */
    ENTITY_TYPE_CODE,
    
    /** Replacement NPI number if provider NPI was deactivated. */
    REPLACEMENT_NPI,
    
    /** Employer Identification Number (for organizations). */
    EIN,
    
    /** Provider organization name (for organizations). */
    ORG_NAME,
    
    /** Provider last name (for individuals). */
    LAST_NAME,
    
    /** Provider first name (for individuals). */
    FIRST_NAME,
    
    /** Provider middle name (for individuals). */
    MIDDLE_NAME,
    
    /** Provider name prefix (Dr., Mr., etc.). */
    PREFIX,
    
    /** Provider name suffix (Jr., Sr., etc.). */
    SUFFIX,
    
    /** Provider credential text (MD, RN, etc.). */
    CREDENTIALS
  }

  /**
   * Home Health Agency (HHA) claims data structure.
   * Represents home health services including nursing care,
   * therapy services, and medical social services.
   */
  public enum HHA {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Claim identifier uniquely identifying the HHA claim. */
    CLM_ID,
    
    /** Claim group identifier for related HHA claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,
    
    /** NCH claim type code identifying the type of HHA claim. */
    NCH_CLM_TYPE_CD,
    
    /** Claim from date (start of service period). */
    CLM_FROM_DT,
    
    /** Claim through date (end of service period). */
    CLM_THRU_DT,
    
    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,
    
    /** Fiscal intermediary claim processing date. */
    FI_CLM_PROC_DT,
    
    /** Provider number for the home health agency. */
    PRVDR_NUM,
    
    /** Claim facility type code. */
    CLM_FAC_TYPE_CD,
    
    /** Claim service classification type code. */
    CLM_SRVC_CLSFCTN_TYPE_CD,
    
    /** Claim frequency code indicating billing frequency. */
    CLM_FREQ_CD,
    
    /** Fiscal intermediary number. */
    FI_NUM,
    
    /** Claim Medicare non-payment reason code. */
    CLM_MDCR_NON_PMT_RSN_CD,
    
    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,
    
    /** NCH primary payer claim paid amount. */
    NCH_PRMRY_PYR_CLM_PD_AMT,
    
    /** NCH primary payer code. */
    NCH_PRMRY_PYR_CD,
    
    /** Provider state code. */
    PRVDR_STATE_CD,
    
    /** Organization National Provider Identifier number. */
    ORG_NPI_NUM,
    
    /** Attending physician Unique Provider Identification Number (legacy). */
    AT_PHYSN_UPIN,
    
    /** Attending physician National Provider Identifier. */
    AT_PHYSN_NPI,
    
    /** Patient discharge status code. */
    PTNT_DSCHRG_STUS_CD,
    
    /** Claim Prospective Payment System indicator code. */
    CLM_PPS_IND_CD,
    
    /** Claim total charge amount. */
    CLM_TOT_CHRG_AMT,
    
    /** Principal diagnosis code for the HHA episode. */
    PRNCPAL_DGNS_CD,
    
    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,
    
    /** Other diagnosis code 1. */
    ICD_DGNS_CD1,
    
    /** Other diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,
    
    /** Other diagnosis code 2. */
    ICD_DGNS_CD2,
    
    /** Other diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,
    
    /** Other diagnosis code 3. */
    ICD_DGNS_CD3,
    
    /** Other diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,
    
    /** Other diagnosis code 4. */
    ICD_DGNS_CD4,
    
    /** Other diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,
    
    /** Other diagnosis code 5. */
    ICD_DGNS_CD5,
    
    /** Other diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,
    
    /** Other diagnosis code 6. */
    ICD_DGNS_CD6,
    
    /** Other diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,
    
    /** Other diagnosis code 7. */
    ICD_DGNS_CD7,
    
    /** Other diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,
    
    /** Other diagnosis code 8. */
    ICD_DGNS_CD8,
    
    /** Other diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,
    
    /** Other diagnosis code 9. */
    ICD_DGNS_CD9,
    
    /** Other diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,
    
    /** Other diagnosis code 10. */
    ICD_DGNS_CD10,
    
    /** Other diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,
    
    /** Other diagnosis code 11. */
    ICD_DGNS_CD11,
    
    /** Other diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,
    
    /** Other diagnosis code 12. */
    ICD_DGNS_CD12,
    
    /** Other diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,
    
    /** Other diagnosis code 13. */
    ICD_DGNS_CD13,
    
    /** Other diagnosis version code 13. */
    ICD_DGNS_VRSN_CD13,
    
    /** Other diagnosis code 14. */
    ICD_DGNS_CD14,
    
    /** Other diagnosis version code 14. */
    ICD_DGNS_VRSN_CD14,
    
    /** Other diagnosis code 15. */
    ICD_DGNS_CD15,
    
    /** Other diagnosis version code 15. */
    ICD_DGNS_VRSN_CD15,
    
    /** Other diagnosis code 16. */
    ICD_DGNS_CD16,
    
    /** Other diagnosis version code 16. */
    ICD_DGNS_VRSN_CD16,
    
    /** Other diagnosis code 17. */
    ICD_DGNS_CD17,
    
    /** Other diagnosis version code 17. */
    ICD_DGNS_VRSN_CD17,
    
    /** Other diagnosis code 18. */
    ICD_DGNS_CD18,
    
    /** Other diagnosis version code 18. */
    ICD_DGNS_VRSN_CD18,
    
    /** Other diagnosis code 19. */
    ICD_DGNS_CD19,
    
    /** Other diagnosis version code 19. */
    ICD_DGNS_VRSN_CD19,
    
    /** Other diagnosis code 20. */
    ICD_DGNS_CD20,
    
    /** Other diagnosis version code 20. */
    ICD_DGNS_VRSN_CD20,
    
    /** Other diagnosis code 21. */
    ICD_DGNS_CD21,
    
    /** Other diagnosis version code 21. */
    ICD_DGNS_VRSN_CD21,
    
    /** Other diagnosis code 22. */
    ICD_DGNS_CD22,
    
    /** Other diagnosis version code 22. */
    ICD_DGNS_VRSN_CD22,
    
    /** Other diagnosis code 23. */
    ICD_DGNS_CD23,
    
    /** Other diagnosis version code 23. */
    ICD_DGNS_VRSN_CD23,
    
    /** Other diagnosis code 24. */
    ICD_DGNS_CD24,
    
    /** Other diagnosis version code 24. */
    ICD_DGNS_VRSN_CD24,
    
    /** Other diagnosis code 25. */
    ICD_DGNS_CD25,
    
    /** Other diagnosis version code 25. */
    ICD_DGNS_VRSN_CD25,
    
    /** First external cause of injury code. */
    FST_DGNS_E_CD,
    
    /** First external cause of injury version code. */
    FST_DGNS_E_VRSN_CD,
    
    /** External cause of injury diagnosis code 1. */
    ICD_DGNS_E_CD1,
    
    /** External cause of injury diagnosis version code 1. */
    ICD_DGNS_E_VRSN_CD1,
    
    /** External cause of injury diagnosis code 2. */
    ICD_DGNS_E_CD2,
    
    /** External cause of injury diagnosis version code 2. */
    ICD_DGNS_E_VRSN_CD2,
    
    /** External cause of injury diagnosis code 3. */
    ICD_DGNS_E_CD3,
    
    /** External cause of injury diagnosis version code 3. */
    ICD_DGNS_E_VRSN_CD3,
    
    /** External cause of injury diagnosis code 4. */
    ICD_DGNS_E_CD4,
    
    /** External cause of injury diagnosis version code 4. */
    ICD_DGNS_E_VRSN_CD4,
    
    /** External cause of injury diagnosis code 5. */
    ICD_DGNS_E_CD5,
    
    /** External cause of injury diagnosis version code 5. */
    ICD_DGNS_E_VRSN_CD5,
    
    /** External cause of injury diagnosis code 6. */
    ICD_DGNS_E_CD6,
    
    /** External cause of injury diagnosis version code 6. */
    ICD_DGNS_E_VRSN_CD6,
    
    /** External cause of injury diagnosis code 7. */
    ICD_DGNS_E_CD7,
    
    /** External cause of injury diagnosis version code 7. */
    ICD_DGNS_E_VRSN_CD7,
    
    /** External cause of injury diagnosis code 8. */
    ICD_DGNS_E_CD8,
    
    /** External cause of injury diagnosis version code 8. */
    ICD_DGNS_E_VRSN_CD8,
    
    /** External cause of injury diagnosis code 9. */
    ICD_DGNS_E_CD9,
    
    /** External cause of injury diagnosis version code 9. */
    ICD_DGNS_E_VRSN_CD9,
    
    /** External cause of injury diagnosis code 10. */
    ICD_DGNS_E_CD10,
    
    /** External cause of injury diagnosis version code 10. */
    ICD_DGNS_E_VRSN_CD10,
    
    /** External cause of injury diagnosis code 11. */
    ICD_DGNS_E_CD11,
    
    /** External cause of injury diagnosis version code 11. */
    ICD_DGNS_E_VRSN_CD11,
    
    /** External cause of injury diagnosis code 12. */
    ICD_DGNS_E_CD12,
    
    /** External cause of injury diagnosis version code 12. */
    ICD_DGNS_E_VRSN_CD12,
    
    /** Claim Home Health Agency Low Utilization Payment Adjustment indicator code. */
    CLM_HHA_LUPA_IND_CD,
    
    /** Claim Home Health Agency referral code. */
    CLM_HHA_RFRL_CD,
    
    /** Claim Home Health Agency total visit count. */
    CLM_HHA_TOT_VISIT_CNT,
    
    /** Claim admission date. */
    CLM_ADMSN_DT,
    
    /** Fiscal intermediary document claim control number. */
    FI_DOC_CLM_CNTL_NUM,
    
    /** Fiscal intermediary original claim control number. */
    FI_ORIG_CLM_CNTL_NUM,
    
    /** Claim query code for claim inquiries. */
    CLAIM_QUERY_CODE,
    
    /** Claim line number. */
    CLM_LINE_NUM,
    
    /** Revenue center code for the service line. */
    REV_CNTR,
    
    /** Revenue center date when service was provided. */
    REV_CNTR_DT,
    
    /** Revenue center first ANSI code. */
    REV_CNTR_1ST_ANSI_CD,
    
    /** Revenue center APC HIPPS code. */
    REV_CNTR_APC_HIPPS_CD,
    
    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,
    
    /** HCPCS first modifier code. */
    HCPCS_1ST_MDFR_CD,
    
    /** HCPCS second modifier code. */
    HCPCS_2ND_MDFR_CD,
    
    /** Revenue center payment method indicator code. */
    REV_CNTR_PMT_MTHD_IND_CD,
    
    /** Revenue center service unit count. */
    REV_CNTR_UNIT_CNT,
    
    /** Revenue center rate amount. */
    REV_CNTR_RATE_AMT,
    
    /** Revenue center payment amount. */
    REV_CNTR_PMT_AMT_AMT,
    
    /** Revenue center total charge amount. */
    REV_CNTR_TOT_CHRG_AMT,
    
    /** Revenue center non-covered charge amount. */
    REV_CNTR_NCVRD_CHRG_AMT,
    
    /** Revenue center deductible coinsurance code. */
    REV_CNTR_DDCTBL_COINSRNC_CD,
    
    /** Revenue center status indicator code. */
    REV_CNTR_STUS_IND_CD,
    
    /** Revenue center National Drug Code quantity. */
    REV_CNTR_NDC_QTY,
    
    /** Revenue center NDC quantity qualifier code. */
    REV_CNTR_NDC_QTY_QLFR_CD,
    
    /** Rendering physician Unique Provider Identification Number (legacy). */
    RNDRNG_PHYSN_UPIN,
    
    /** Rendering physician National Provider Identifier. */
    RNDRNG_PHYSN_NPI
  }

  
  /**
   * Home Health Agency claims diagnosis code arrays.
   * Primary and secondary diagnoses for home health episodes.
   */
  public static final HHA[][] homeDxFields = {
    { HHA.ICD_DGNS_CD1, HHA.ICD_DGNS_VRSN_CD1 },
    { HHA.ICD_DGNS_CD2, HHA.ICD_DGNS_VRSN_CD2 },
    { HHA.ICD_DGNS_CD3, HHA.ICD_DGNS_VRSN_CD3 },
    { HHA.ICD_DGNS_CD4, HHA.ICD_DGNS_VRSN_CD4 },
    { HHA.ICD_DGNS_CD5, HHA.ICD_DGNS_VRSN_CD5 },
    { HHA.ICD_DGNS_CD6, HHA.ICD_DGNS_VRSN_CD6 },
    { HHA.ICD_DGNS_CD7, HHA.ICD_DGNS_VRSN_CD7 },
    { HHA.ICD_DGNS_CD8, HHA.ICD_DGNS_VRSN_CD8 },
    { HHA.ICD_DGNS_CD9, HHA.ICD_DGNS_VRSN_CD9 },
    { HHA.ICD_DGNS_CD10, HHA.ICD_DGNS_VRSN_CD10 },
    { HHA.ICD_DGNS_CD11, HHA.ICD_DGNS_VRSN_CD11 },
    { HHA.ICD_DGNS_CD12, HHA.ICD_DGNS_VRSN_CD12 },
    { HHA.ICD_DGNS_CD13, HHA.ICD_DGNS_VRSN_CD13 },
    { HHA.ICD_DGNS_CD14, HHA.ICD_DGNS_VRSN_CD14 },
    { HHA.ICD_DGNS_CD15, HHA.ICD_DGNS_VRSN_CD15 },
    { HHA.ICD_DGNS_CD16, HHA.ICD_DGNS_VRSN_CD16 },
    { HHA.ICD_DGNS_CD17, HHA.ICD_DGNS_VRSN_CD17 },
    { HHA.ICD_DGNS_CD18, HHA.ICD_DGNS_VRSN_CD18 },
    { HHA.ICD_DGNS_CD19, HHA.ICD_DGNS_VRSN_CD19 },
    { HHA.ICD_DGNS_CD20, HHA.ICD_DGNS_VRSN_CD20 },
    { HHA.ICD_DGNS_CD21, HHA.ICD_DGNS_VRSN_CD21 },
    { HHA.ICD_DGNS_CD22, HHA.ICD_DGNS_VRSN_CD22 },
    { HHA.ICD_DGNS_CD23, HHA.ICD_DGNS_VRSN_CD23 },
    { HHA.ICD_DGNS_CD24, HHA.ICD_DGNS_VRSN_CD24 },
    { HHA.ICD_DGNS_CD25, HHA.ICD_DGNS_VRSN_CD25 }
  };



  /**
   * Hospice care claims data structure.
   * Covers end-of-life care services including pain management,
   * medical equipment, and support services.
   */
  public enum HOSPICE {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Claim identifier uniquely identifying the hospice claim. */
    CLM_ID,
    
    /** Claim group identifier for related hospice claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,
    
    /** NCH claim type code identifying the type of hospice claim. */
    NCH_CLM_TYPE_CD,
    
    /** Claim from date (start of service period). */
    CLM_FROM_DT,
    
    /** Claim through date (end of service period). */
    CLM_THRU_DT,
    
    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,
    
    /** Fiscal intermediary claim processing date. */
    FI_CLM_PROC_DT,
    
    /** Provider number for the hospice organization. */
    PRVDR_NUM,
    
    /** Claim facility type code. */
    CLM_FAC_TYPE_CD,
    
    /** Claim service classification type code. */
    CLM_SRVC_CLSFCTN_TYPE_CD,
    
    /** Claim frequency code indicating billing frequency. */
    CLM_FREQ_CD,
    
    /** Fiscal intermediary number. */
    FI_NUM,
    
    /** Claim Medicare non-payment reason code. */
    CLM_MDCR_NON_PMT_RSN_CD,
    
    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,
    
    /** NCH primary payer claim paid amount. */
    NCH_PRMRY_PYR_CLM_PD_AMT,
    
    /** NCH primary payer code. */
    NCH_PRMRY_PYR_CD,
    
    /** Provider state code. */
    PRVDR_STATE_CD,
    
    /** Organization National Provider Identifier number. */
    ORG_NPI_NUM,
    
    /** Attending physician Unique Provider Identification Number (legacy). */
    AT_PHYSN_UPIN,
    
    /** Attending physician National Provider Identifier. */
    AT_PHYSN_NPI,
    
    /** Patient discharge status code. */
    PTNT_DSCHRG_STUS_CD,
    
    /** Claim total charge amount. */
    CLM_TOT_CHRG_AMT,
    
    /** NCH patient status indicator code. */
    NCH_PTNT_STATUS_IND_CD,
    
    /** Claim utilization day count. */
    CLM_UTLZTN_DAY_CNT,
    
    /** NCH beneficiary discharge date. */
    NCH_BENE_DSCHRG_DT,
    
    /** Principal diagnosis code for the hospice episode. */
    PRNCPAL_DGNS_CD,
    
    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,
    
    /** Other diagnosis code 1. */
    ICD_DGNS_CD1,
    
    /** Other diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,
    
    /** Other diagnosis code 2. */
    ICD_DGNS_CD2,
    
    /** Other diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,
    
    /** Other diagnosis code 3. */
    ICD_DGNS_CD3,
    
    /** Other diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,
    
    /** Other diagnosis code 4. */
    ICD_DGNS_CD4,
    
    /** Other diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,
    
    /** Other diagnosis code 5. */
    ICD_DGNS_CD5,
    
    /** Other diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,
    
    /** Other diagnosis code 6. */
    ICD_DGNS_CD6,
    
    /** Other diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,
    
    /** Other diagnosis code 7. */
    ICD_DGNS_CD7,
    
    /** Other diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,
    
    /** Other diagnosis code 8. */
    ICD_DGNS_CD8,
    
    /** Other diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,
    
    /** Other diagnosis code 9. */
    ICD_DGNS_CD9,
    
    /** Other diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,
    
    /** Other diagnosis code 10. */
    ICD_DGNS_CD10,
    
    /** Other diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,
    
    /** Other diagnosis code 11. */
    ICD_DGNS_CD11,
    
    /** Other diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,
    
    /** Other diagnosis code 12. */
    ICD_DGNS_CD12,
    
    /** Other diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,
    
    /** Other diagnosis code 13. */
    ICD_DGNS_CD13,
    
    /** Other diagnosis version code 13. */
    ICD_DGNS_VRSN_CD13,
    
    /** Other diagnosis code 14. */
    ICD_DGNS_CD14,
    
    /** Other diagnosis version code 14. */
    ICD_DGNS_VRSN_CD14,
    
    /** Other diagnosis code 15. */
    ICD_DGNS_CD15,
    
    /** Other diagnosis version code 15. */
    ICD_DGNS_VRSN_CD15,
    
    /** Other diagnosis code 16. */
    ICD_DGNS_CD16,
    
    /** Other diagnosis version code 16. */
    ICD_DGNS_VRSN_CD16,
    
    /** Other diagnosis code 17. */
    ICD_DGNS_CD17,
    
    /** Other diagnosis version code 17. */
    ICD_DGNS_VRSN_CD17,
    
    /** Other diagnosis code 18. */
    ICD_DGNS_CD18,
    
    /** Other diagnosis version code 18. */
    ICD_DGNS_VRSN_CD18,
    
    /** Other diagnosis code 19. */
    ICD_DGNS_CD19,
    
    /** Other diagnosis version code 19. */
    ICD_DGNS_VRSN_CD19,
    
    /** Other diagnosis code 20. */
    ICD_DGNS_CD20,
    
    /** Other diagnosis version code 20. */
    ICD_DGNS_VRSN_CD20,
    
    /** Other diagnosis code 21. */
    ICD_DGNS_CD21,
    
    /** Other diagnosis version code 21. */
    ICD_DGNS_VRSN_CD21,
    
    /** Other diagnosis code 22. */
    ICD_DGNS_CD22,
    
    /** Other diagnosis version code 22. */
    ICD_DGNS_VRSN_CD22,
    
    /** Other diagnosis code 23. */
    ICD_DGNS_CD23,
    
    /** Other diagnosis version code 23. */
    ICD_DGNS_VRSN_CD23,
    
    /** Other diagnosis code 24. */
    ICD_DGNS_CD24,
    
    /** Other diagnosis version code 24. */
    ICD_DGNS_VRSN_CD24,
    
    /** Other diagnosis code 25. */
    ICD_DGNS_CD25,
    
    /** Other diagnosis version code 25. */
    ICD_DGNS_VRSN_CD25,
    
    /** First external cause of injury code. */
    FST_DGNS_E_CD,
    
    /** First external cause of injury version code. */
    FST_DGNS_E_VRSN_CD,
    
    /** External cause of injury diagnosis code 1. */
    ICD_DGNS_E_CD1,
    
    /** External cause of injury diagnosis version code 1. */
    ICD_DGNS_E_VRSN_CD1,
    
    /** External cause of injury diagnosis code 2. */
    ICD_DGNS_E_CD2,
    
    /** External cause of injury diagnosis version code 2. */
    ICD_DGNS_E_VRSN_CD2,
    
    /** External cause of injury diagnosis code 3. */
    ICD_DGNS_E_CD3,
    
    /** External cause of injury diagnosis version code 3. */
    ICD_DGNS_E_VRSN_CD3,
    
    /** External cause of injury diagnosis code 4. */
    ICD_DGNS_E_CD4,
    
    /** External cause of injury diagnosis version code 4. */
    ICD_DGNS_E_VRSN_CD4,
    
    /** External cause of injury diagnosis code 5. */
    ICD_DGNS_E_CD5,
    
    /** External cause of injury diagnosis version code 5. */
    ICD_DGNS_E_VRSN_CD5,
    
    /** External cause of injury diagnosis code 6. */
    ICD_DGNS_E_CD6,
    
    /** External cause of injury diagnosis version code 6. */
    ICD_DGNS_E_VRSN_CD6,
    
    /** External cause of injury diagnosis code 7. */
    ICD_DGNS_E_CD7,
    
    /** External cause of injury diagnosis version code 7. */
    ICD_DGNS_E_VRSN_CD7,
    
    /** External cause of injury diagnosis code 8. */
    ICD_DGNS_E_CD8,
    
    /** External cause of injury diagnosis version code 8. */
    ICD_DGNS_E_VRSN_CD8,
    
    /** External cause of injury diagnosis code 9. */
    ICD_DGNS_E_CD9,
    
    /** External cause of injury diagnosis version code 9. */
    ICD_DGNS_E_VRSN_CD9,
    
    /** External cause of injury diagnosis code 10. */
    ICD_DGNS_E_CD10,
    
    /** External cause of injury diagnosis version code 10. */
    ICD_DGNS_E_VRSN_CD10,
    
    /** External cause of injury diagnosis code 11. */
    ICD_DGNS_E_CD11,
    
    /** External cause of injury diagnosis version code 11. */
    ICD_DGNS_E_VRSN_CD11,
    
    /** External cause of injury diagnosis code 12. */
    ICD_DGNS_E_CD12,
    
    /** External cause of injury diagnosis version code 12. */
    ICD_DGNS_E_VRSN_CD12,
    
    /** Claim hospice start date identifier. */
    CLM_HOSPC_START_DT_ID,
    
    /** Beneficiary hospice period count. */
    BENE_HOSPC_PRD_CNT,
    
    /** Fiscal intermediary document claim control number. */
    FI_DOC_CLM_CNTL_NUM,
    
    /** Fiscal intermediary original claim control number. */
    FI_ORIG_CLM_CNTL_NUM,
    
    /** Claim query code for claim inquiries. */
    CLAIM_QUERY_CODE,
    
    /** Claim line number. */
    CLM_LINE_NUM,
    
    /** Revenue center code for the service line. */
    REV_CNTR,
    
    /** Revenue center date when service was provided. */
    REV_CNTR_DT,
    
    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,
    
    /** HCPCS first modifier code. */
    HCPCS_1ST_MDFR_CD,
    
    /** HCPCS second modifier code. */
    HCPCS_2ND_MDFR_CD,
    
    /** Revenue center service unit count. */
    REV_CNTR_UNIT_CNT,
    
    /** Revenue center rate amount. */
    REV_CNTR_RATE_AMT,
    
    /** Revenue center provider payment amount. */
    REV_CNTR_PRVDR_PMT_AMT,
    
    /** Revenue center beneficiary payment amount. */
    REV_CNTR_BENE_PMT_AMT,
    
    /** Revenue center payment amount. */
    REV_CNTR_PMT_AMT_AMT,
    
    /** Revenue center total charge amount. */
    REV_CNTR_TOT_CHRG_AMT,
    
    /** Revenue center non-covered charge amount. */
    REV_CNTR_NCVRD_CHRG_AMT,
    
    /** Revenue center deductible coinsurance code. */
    REV_CNTR_DDCTBL_COINSRNC_CD,
    
    /** Revenue center National Drug Code quantity. */
    REV_CNTR_NDC_QTY,
    
    /** Revenue center NDC quantity qualifier code. */
    REV_CNTR_NDC_QTY_QLFR_CD,
    
    /** Rendering physician Unique Provider Identification Number (legacy). */
    RNDRNG_PHYSN_UPIN,
    
    /** Rendering physician National Provider Identifier. */
    RNDRNG_PHYSN_NPI
  }



  /**
   * Hospice claims diagnosis code arrays.
   * Terminal diagnoses and related conditions for hospice care.
   */
  public static final HOSPICE[][] hospiceDxFields = {
    { HOSPICE.ICD_DGNS_CD1, HOSPICE.ICD_DGNS_VRSN_CD1 },
    { HOSPICE.ICD_DGNS_CD2, HOSPICE.ICD_DGNS_VRSN_CD2 },
    { HOSPICE.ICD_DGNS_CD3, HOSPICE.ICD_DGNS_VRSN_CD3 },
    { HOSPICE.ICD_DGNS_CD4, HOSPICE.ICD_DGNS_VRSN_CD4 },
    { HOSPICE.ICD_DGNS_CD5, HOSPICE.ICD_DGNS_VRSN_CD5 },
    { HOSPICE.ICD_DGNS_CD6, HOSPICE.ICD_DGNS_VRSN_CD6 },
    { HOSPICE.ICD_DGNS_CD7, HOSPICE.ICD_DGNS_VRSN_CD7 },
    { HOSPICE.ICD_DGNS_CD8, HOSPICE.ICD_DGNS_VRSN_CD8 },
    { HOSPICE.ICD_DGNS_CD9, HOSPICE.ICD_DGNS_VRSN_CD9 },
    { HOSPICE.ICD_DGNS_CD10, HOSPICE.ICD_DGNS_VRSN_CD10 },
    { HOSPICE.ICD_DGNS_CD11, HOSPICE.ICD_DGNS_VRSN_CD11 },
    { HOSPICE.ICD_DGNS_CD12, HOSPICE.ICD_DGNS_VRSN_CD12 },
    { HOSPICE.ICD_DGNS_CD13, HOSPICE.ICD_DGNS_VRSN_CD13 },
    { HOSPICE.ICD_DGNS_CD14, HOSPICE.ICD_DGNS_VRSN_CD14 },
    { HOSPICE.ICD_DGNS_CD15, HOSPICE.ICD_DGNS_VRSN_CD15 },
    { HOSPICE.ICD_DGNS_CD16, HOSPICE.ICD_DGNS_VRSN_CD16 },
    { HOSPICE.ICD_DGNS_CD17, HOSPICE.ICD_DGNS_VRSN_CD17 },
    { HOSPICE.ICD_DGNS_CD18, HOSPICE.ICD_DGNS_VRSN_CD18 },
    { HOSPICE.ICD_DGNS_CD19, HOSPICE.ICD_DGNS_VRSN_CD19 },
    { HOSPICE.ICD_DGNS_CD20, HOSPICE.ICD_DGNS_VRSN_CD20 },
    { HOSPICE.ICD_DGNS_CD21, HOSPICE.ICD_DGNS_VRSN_CD21 },
    { HOSPICE.ICD_DGNS_CD22, HOSPICE.ICD_DGNS_VRSN_CD22 },
    { HOSPICE.ICD_DGNS_CD23, HOSPICE.ICD_DGNS_VRSN_CD23 },
    { HOSPICE.ICD_DGNS_CD24, HOSPICE.ICD_DGNS_VRSN_CD24 },
    { HOSPICE.ICD_DGNS_CD25, HOSPICE.ICD_DGNS_VRSN_CD25 }
  };

  /**
   * Skilled Nursing Facility (SNF) claims data structure.
   * Represents post-acute care in skilled nursing facilities
   * including rehabilitation and long-term care services.
   */
  public enum SNF {
    /** Data Management Layer indicator for record processing. */
    DML_IND,
    
    /** Unique beneficiary identifier. */
    BENE_ID,
    
    /** Claim identifier uniquely identifying the SNF claim. */
    CLM_ID,
    
    /** Claim group identifier for related SNF claims. */
    CLM_GRP_ID,
    
    /** Final action indicator for claim processing status. */
    FINAL_ACTION,
    
    /** NCH near line record identification code. */
    NCH_NEAR_LINE_REC_IDENT_CD,
    
    /** NCH claim type code identifying the type of SNF claim. */
    NCH_CLM_TYPE_CD,
    
    /** Claim from date (admission date). */
    CLM_FROM_DT,
    
    /** Claim through date (discharge date). */
    CLM_THRU_DT,
    
    /** NCH weekly processing date when claim was processed. */
    NCH_WKLY_PROC_DT,
    
    /** Fiscal intermediary claim processing date. */
    FI_CLM_PROC_DT,
    
    /** Claim query code for claim inquiries. */
    CLAIM_QUERY_CODE,
    
    /** Provider number for the skilled nursing facility. */
    PRVDR_NUM,
    
    /** Claim facility type code. */
    CLM_FAC_TYPE_CD,
    
    /** Claim service classification type code. */
    CLM_SRVC_CLSFCTN_TYPE_CD,
    
    /** Claim frequency code indicating billing frequency. */
    CLM_FREQ_CD,
    
    /** Fiscal intermediary number. */
    FI_NUM,
    
    /** Claim Medicare non-payment reason code. */
    CLM_MDCR_NON_PMT_RSN_CD,
    
    /** Claim payment amount paid by Medicare. */
    CLM_PMT_AMT,
    
    /** NCH primary payer claim paid amount. */
    NCH_PRMRY_PYR_CLM_PD_AMT,
    
    /** NCH primary payer code. */
    NCH_PRMRY_PYR_CD,
    
    /** Facility intermediary claim action code. */
    FI_CLM_ACTN_CD,
    
    /** Provider state code. */
    PRVDR_STATE_CD,
    
    /** Organization National Provider Identifier number. */
    ORG_NPI_NUM,
    
    /** Attending physician Unique Provider Identification Number (legacy). */
    AT_PHYSN_UPIN,
    
    /** Attending physician National Provider Identifier. */
    AT_PHYSN_NPI,
    
    /** Operating physician Unique Provider Identification Number (legacy). */
    OP_PHYSN_UPIN,
    
    /** Operating physician National Provider Identifier. */
    OP_PHYSN_NPI,
    
    /** Other physician Unique Provider Identification Number (legacy). */
    OT_PHYSN_UPIN,
    
    /** Other physician National Provider Identifier. */
    OT_PHYSN_NPI,
    
    /** Claim Managed Care Organization paid switch. */
    CLM_MCO_PD_SW,
    
    /** Patient discharge status code. */
    PTNT_DSCHRG_STUS_CD,
    
    /** Claim Prospective Payment System indicator code. */
    CLM_PPS_IND_CD,
    
    /** Claim total charge amount. */
    CLM_TOT_CHRG_AMT,
    
    /** Claim admission date. */
    CLM_ADMSN_DT,
    
    /** Claim inpatient admission type code. */
    CLM_IP_ADMSN_TYPE_CD,
    
    /** Claim source of inpatient admission code. */
    CLM_SRC_IP_ADMSN_CD,
    
    /** NCH patient status indicator code. */
    NCH_PTNT_STATUS_IND_CD,
    
    /** NCH beneficiary inpatient deductible amount. */
    NCH_BENE_IP_DDCTBL_AMT,
    
    /** NCH beneficiary Part A coinsurance liability amount. */
    NCH_BENE_PTA_COINSRNC_LBLTY_AM,
    
    /** NCH beneficiary blood deductible liability amount. */
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    
    /** NCH inpatient non-covered charge amount. */
    NCH_IP_NCVRD_CHRG_AMT,
    
    /** NCH inpatient total deduction amount. */
    NCH_IP_TOT_DDCTN_AMT,
    
    /** Claim PPS capital Federal Specific Portion amount. */
    CLM_PPS_CPTL_FSP_AMT,
    
    /** Claim PPS capital outlier amount. */
    CLM_PPS_CPTL_OUTLIER_AMT,
    
    /** Claim PPS capital disproportionate share amount. */
    CLM_PPS_CPTL_DSPRPRTNT_SHR_AMT,
    
    /** Claim PPS capital Indirect Medical Education amount. */
    CLM_PPS_CPTL_IME_AMT,
    
    /** Claim PPS capital exception amount. */
    CLM_PPS_CPTL_EXCPTN_AMT,
    
    /** Claim PPS old capital hold harmless amount. */
    CLM_PPS_OLD_CPTL_HLD_HRMLS_AMT,
    
    /** Claim utilization day count. */
    CLM_UTLZTN_DAY_CNT,
    
    /** Beneficiary total coinsurance days count. */
    BENE_TOT_COINSRNC_DAYS_CNT,
    
    /** Claim non-utilization days count. */
    CLM_NON_UTLZTN_DAYS_CNT,
    
    /** NCH blood pints furnished quantity. */
    NCH_BLOOD_PNTS_FRNSHD_QTY,
    
    /** NCH qualified stay from date. */
    NCH_QLFYD_STAY_FROM_DT,
    
    /** NCH qualified stay through date. */
    NCH_QLFYD_STAY_THRU_DT,
    
    /** NCH verified non-covered stay from date. */
    NCH_VRFD_NCVRD_STAY_FROM_DT,
    
    /** NCH verified non-covered stay through date. */
    NCH_VRFD_NCVRD_STAY_THRU_DT,
    
    /** NCH active or covered level of care through date. */
    NCH_ACTV_OR_CVRD_LVL_CARE_THRU,
    
    /** NCH beneficiary Medicare benefits exhausted date for inpatient. */
    NCH_BENE_MDCR_BNFTS_EXHTD_DT_I,
    
    /** NCH beneficiary discharge date. */
    NCH_BENE_DSCHRG_DT,
    
    /** Claim Diagnosis Related Group (DRG) code. */
    CLM_DRG_CD,
    
    /** Admitting diagnosis code. */
    ADMTG_DGNS_CD,
    
    /** Admitting diagnosis version code. */
    ADMTG_DGNS_VRSN_CD,
    
    /** Principal diagnosis code for the SNF stay. */
    PRNCPAL_DGNS_CD,
    
    /** Principal diagnosis version code (ICD-9 or ICD-10). */
    PRNCPAL_DGNS_VRSN_CD,
    
    /** Other diagnosis code 1. */
    ICD_DGNS_CD1,
    
    /** Other diagnosis version code 1. */
    ICD_DGNS_VRSN_CD1,
    
    /** Other diagnosis code 2. */
    ICD_DGNS_CD2,
    
    /** Other diagnosis version code 2. */
    ICD_DGNS_VRSN_CD2,
    
    /** Other diagnosis code 3. */
    ICD_DGNS_CD3,
    
    /** Other diagnosis version code 3. */
    ICD_DGNS_VRSN_CD3,
    
    /** Other diagnosis code 4. */
    ICD_DGNS_CD4,
    
    /** Other diagnosis version code 4. */
    ICD_DGNS_VRSN_CD4,
    
    /** Other diagnosis code 5. */
    ICD_DGNS_CD5,
    
    /** Other diagnosis version code 5. */
    ICD_DGNS_VRSN_CD5,
    
    /** Other diagnosis code 6. */
    ICD_DGNS_CD6,
    
    /** Other diagnosis version code 6. */
    ICD_DGNS_VRSN_CD6,
    
    /** Other diagnosis code 7. */
    ICD_DGNS_CD7,
    
    /** Other diagnosis version code 7. */
    ICD_DGNS_VRSN_CD7,
    
    /** Other diagnosis code 8. */
    ICD_DGNS_CD8,
    
    /** Other diagnosis version code 8. */
    ICD_DGNS_VRSN_CD8,
    
    /** Other diagnosis code 9. */
    ICD_DGNS_CD9,
    
    /** Other diagnosis version code 9. */
    ICD_DGNS_VRSN_CD9,
    
    /** Other diagnosis code 10. */
    ICD_DGNS_CD10,
    
    /** Other diagnosis version code 10. */
    ICD_DGNS_VRSN_CD10,
    
    /** Other diagnosis code 11. */
    ICD_DGNS_CD11,
    
    /** Other diagnosis version code 11. */
    ICD_DGNS_VRSN_CD11,
    
    /** Other diagnosis code 12. */
    ICD_DGNS_CD12,
    
    /** Other diagnosis version code 12. */
    ICD_DGNS_VRSN_CD12,
    
    /** Other diagnosis code 13. */
    ICD_DGNS_CD13,
    
    /** Other diagnosis version code 13. */
    ICD_DGNS_VRSN_CD13,
    
    /** Other diagnosis code 14. */
    ICD_DGNS_CD14,
    
    /** Other diagnosis version code 14. */
    ICD_DGNS_VRSN_CD14,
    
    /** Other diagnosis code 15. */
    ICD_DGNS_CD15,
    
    /** Other diagnosis version code 15. */
    ICD_DGNS_VRSN_CD15,
    
    /** Other diagnosis code 16. */
    ICD_DGNS_CD16,
    
    /** Other diagnosis version code 16. */
    ICD_DGNS_VRSN_CD16,
    
    /** Other diagnosis code 17. */
    ICD_DGNS_CD17,
    
    /** Other diagnosis version code 17. */
    ICD_DGNS_VRSN_CD17,
    
    /** Other diagnosis code 18. */
    ICD_DGNS_CD18,
    
    /** Other diagnosis version code 18. */
    ICD_DGNS_VRSN_CD18,
    
    /** Other diagnosis code 19. */
    ICD_DGNS_CD19,
    
    /** Other diagnosis version code 19. */
    ICD_DGNS_VRSN_CD19,
    
    /** Other diagnosis code 20. */
    ICD_DGNS_CD20,
    
    /** Other diagnosis version code 20. */
    ICD_DGNS_VRSN_CD20,
    
    /** Other diagnosis code 21. */
    ICD_DGNS_CD21,
    
    /** Other diagnosis version code 21. */
    ICD_DGNS_VRSN_CD21,
    
    /** Other diagnosis code 22. */
    ICD_DGNS_CD22,
    
    /** Other diagnosis version code 22. */
    ICD_DGNS_VRSN_CD22,
    
    /** Other diagnosis code 23. */
    ICD_DGNS_CD23,
    
    /** Other diagnosis version code 23. */
    ICD_DGNS_VRSN_CD23,
    
    /** Other diagnosis code 24. */
    ICD_DGNS_CD24,
    
    /** Other diagnosis version code 24. */
    ICD_DGNS_VRSN_CD24,
    
    /** Other diagnosis code 25. */
    ICD_DGNS_CD25,
    
    /** Other diagnosis version code 25. */
    ICD_DGNS_VRSN_CD25,
    
    /** First external cause of injury code. */
    FST_DGNS_E_CD,
    
    /** First external cause of injury version code. */
    FST_DGNS_E_VRSN_CD,
    
    /** External cause of injury diagnosis code 1. */
    ICD_DGNS_E_CD1,
    
    /** External cause of injury diagnosis version code 1. */
    ICD_DGNS_E_VRSN_CD1,
    
    /** External cause of injury diagnosis code 2. */
    ICD_DGNS_E_CD2,
    
    /** External cause of injury diagnosis version code 2. */
    ICD_DGNS_E_VRSN_CD2,
    
    /** External cause of injury diagnosis code 3. */
    ICD_DGNS_E_CD3,
    
    /** External cause of injury diagnosis version code 3. */
    ICD_DGNS_E_VRSN_CD3,
    
    /** External cause of injury diagnosis code 4. */
    ICD_DGNS_E_CD4,
    
    /** External cause of injury diagnosis version code 4. */
    ICD_DGNS_E_VRSN_CD4,
    
    /** External cause of injury diagnosis code 5. */
    ICD_DGNS_E_CD5,
    
    /** External cause of injury diagnosis version code 5. */
    ICD_DGNS_E_VRSN_CD5,
    
    /** External cause of injury diagnosis code 6. */
    ICD_DGNS_E_CD6,
    
    /** External cause of injury diagnosis version code 6. */
    ICD_DGNS_E_VRSN_CD6,
    
    /** External cause of injury diagnosis code 7. */
    ICD_DGNS_E_CD7,
    
    /** External cause of injury diagnosis version code 7. */
    ICD_DGNS_E_VRSN_CD7,
    
    /** External cause of injury diagnosis code 8. */
    ICD_DGNS_E_CD8,
    
    /** External cause of injury diagnosis version code 8. */
    ICD_DGNS_E_VRSN_CD8,
    
    /** External cause of injury diagnosis code 9. */
    ICD_DGNS_E_CD9,
    
    /** External cause of injury diagnosis version code 9. */
    ICD_DGNS_E_VRSN_CD9,
    
    /** External cause of injury diagnosis code 10. */
    ICD_DGNS_E_CD10,
    
    /** External cause of injury diagnosis version code 10. */
    ICD_DGNS_E_VRSN_CD10,
    
    /** External cause of injury diagnosis code 11. */
    ICD_DGNS_E_CD11,
    
    /** External cause of injury diagnosis version code 11. */
    ICD_DGNS_E_VRSN_CD11,
    
    /** External cause of injury diagnosis code 12. */
    ICD_DGNS_E_CD12,
    
    /** External cause of injury diagnosis version code 12. */
    ICD_DGNS_E_VRSN_CD12,
    
    /** ICD procedure code 1. */
    ICD_PRCDR_CD1,
    
    /** ICD procedure version code 1. */
    ICD_PRCDR_VRSN_CD1,
    
    /** Procedure date 1. */
    PRCDR_DT1,
    
    /** ICD procedure code 2. */
    ICD_PRCDR_CD2,
    
    /** ICD procedure version code 2. */
    ICD_PRCDR_VRSN_CD2,
    
    /** Procedure date 2. */
    PRCDR_DT2,
    
    /** ICD procedure code 3. */
    ICD_PRCDR_CD3,
    
    /** ICD procedure version code 3. */
    ICD_PRCDR_VRSN_CD3,
    
    /** Procedure date 3. */
    PRCDR_DT3,
    
    /** ICD procedure code 4. */
    ICD_PRCDR_CD4,
    
    /** ICD procedure version code 4. */
    ICD_PRCDR_VRSN_CD4,
    
    /** Procedure date 4. */
    PRCDR_DT4,
    
    /** ICD procedure code 5. */
    ICD_PRCDR_CD5,
    
    /** ICD procedure version code 5. */
    ICD_PRCDR_VRSN_CD5,
    
    /** Procedure date 5. */
    PRCDR_DT5,
    
    /** ICD procedure code 6. */
    ICD_PRCDR_CD6,
    
    /** ICD procedure version code 6. */
    ICD_PRCDR_VRSN_CD6,
    
    /** Procedure date 6. */
    PRCDR_DT6,
    
    /** ICD procedure code 7. */
    ICD_PRCDR_CD7,
    
    /** ICD procedure version code 7. */
    ICD_PRCDR_VRSN_CD7,
    
    /** Procedure date 7. */
    PRCDR_DT7,
    
    /** ICD procedure code 8. */
    ICD_PRCDR_CD8,
    
    /** ICD procedure version code 8. */
    ICD_PRCDR_VRSN_CD8,
    
    /** Procedure date 8. */
    PRCDR_DT8,
    
    /** ICD procedure code 9. */
    ICD_PRCDR_CD9,
    
    /** ICD procedure version code 9. */
    ICD_PRCDR_VRSN_CD9,
    
    /** Procedure date 9. */
    PRCDR_DT9,
    
    /** ICD procedure code 10. */
    ICD_PRCDR_CD10,
    
    /** ICD procedure version code 10. */
    ICD_PRCDR_VRSN_CD10,
    
    /** Procedure date 10. */
    PRCDR_DT10,
    
    /** ICD procedure code 11. */
    ICD_PRCDR_CD11,
    
    /** ICD procedure version code 11. */
    ICD_PRCDR_VRSN_CD11,
    
    /** Procedure date 11. */
    PRCDR_DT11,
    
    /** ICD procedure code 12. */
    ICD_PRCDR_CD12,
    
    /** ICD procedure version code 12. */
    ICD_PRCDR_VRSN_CD12,
    
    /** Procedure date 12. */
    PRCDR_DT12,
    
    /** ICD procedure code 13. */
    ICD_PRCDR_CD13,
    
    /** ICD procedure version code 13. */
    ICD_PRCDR_VRSN_CD13,
    
    /** Procedure date 13. */
    PRCDR_DT13,
    
    /** ICD procedure code 14. */
    ICD_PRCDR_CD14,
    
    /** ICD procedure version code 14. */
    ICD_PRCDR_VRSN_CD14,
    
    /** Procedure date 14. */
    PRCDR_DT14,
    
    /** ICD procedure code 15. */
    ICD_PRCDR_CD15,
    
    /** ICD procedure version code 15. */
    ICD_PRCDR_VRSN_CD15,
    
    /** Procedure date 15. */
    PRCDR_DT15,
    
    /** ICD procedure code 16. */
    ICD_PRCDR_CD16,
    
    /** ICD procedure version code 16. */
    ICD_PRCDR_VRSN_CD16,
    
    /** Procedure date 16. */
    PRCDR_DT16,
    
    /** ICD procedure code 17. */
    ICD_PRCDR_CD17,
    
    /** ICD procedure version code 17. */
    ICD_PRCDR_VRSN_CD17,
    
    /** Procedure date 17. */
    PRCDR_DT17,
    
    /** ICD procedure code 18. */
    ICD_PRCDR_CD18,
    
    /** ICD procedure version code 18. */
    ICD_PRCDR_VRSN_CD18,
    
    /** Procedure date 18. */
    PRCDR_DT18,
    
    /** ICD procedure code 19. */
    ICD_PRCDR_CD19,
    
    /** ICD procedure version code 19. */
    ICD_PRCDR_VRSN_CD19,
    
    /** Procedure date 19. */
    PRCDR_DT19,
    
    /** ICD procedure code 20. */
    ICD_PRCDR_CD20,
    
    /** ICD procedure version code 20. */
    ICD_PRCDR_VRSN_CD20,
    
    /** Procedure date 20. */
    PRCDR_DT20,
    
    /** ICD procedure code 21. */
    ICD_PRCDR_CD21,
    
    /** ICD procedure version code 21. */
    ICD_PRCDR_VRSN_CD21,
    
    /** Procedure date 21. */
    PRCDR_DT21,
    
    /** ICD procedure code 22. */
    ICD_PRCDR_CD22,
    
    /** ICD procedure version code 22. */
    ICD_PRCDR_VRSN_CD22,
    
    /** Procedure date 22. */
    PRCDR_DT22,
    
    /** ICD procedure code 23. */
    ICD_PRCDR_CD23,
    
    /** ICD procedure version code 23. */
    ICD_PRCDR_VRSN_CD23,
    
    /** Procedure date 23. */
    PRCDR_DT23,
    
    /** ICD procedure code 24. */
    ICD_PRCDR_CD24,
    
    /** ICD procedure version code 24. */
    ICD_PRCDR_VRSN_CD24,
    
    /** Procedure date 24. */
    PRCDR_DT24,
    
    /** ICD procedure code 25. */
    ICD_PRCDR_CD25,
    
    /** ICD procedure version code 25. */
    ICD_PRCDR_VRSN_CD25,
    
    /** Procedure date 25. */
    PRCDR_DT25,
    
    /** Fiscal intermediary document claim control number. */
    FI_DOC_CLM_CNTL_NUM,
    
    /** Fiscal intermediary original claim control number. */
    FI_ORIG_CLM_CNTL_NUM,
    
    /** Claim line number. */
    CLM_LINE_NUM,
    
    /** Revenue center code for the service line. */
    REV_CNTR,
    
    /** Healthcare Common Procedure Coding System (HCPCS) code. */
    HCPCS_CD,
    
    /** Revenue center service unit count. */
    REV_CNTR_UNIT_CNT,
    
    /** Revenue center rate amount. */
    REV_CNTR_RATE_AMT,
    
    /** Revenue center total charge amount. */
    REV_CNTR_TOT_CHRG_AMT,
    
    /** Revenue center non-covered charge amount. */
    REV_CNTR_NCVRD_CHRG_AMT,
    
    /** Revenue center deductible coinsurance code. */
    REV_CNTR_DDCTBL_COINSRNC_CD,
    
    /** Revenue center National Drug Code quantity. */
    REV_CNTR_NDC_QTY,
    
    /** Revenue center NDC quantity qualifier code. */
    REV_CNTR_NDC_QTY_QLFR_CD,
    
    /** Rendering physician Unique Provider Identification Number (legacy). */
    RNDRNG_PHYSN_UPIN,
    
    /** Rendering physician National Provider Identifier. */
    RNDRNG_PHYSN_NPI
  }

  /**
   * Skilled Nursing Facility diagnosis code arrays.
   * Diagnoses for SNF admissions and ongoing care needs.
   */
  public static final SNF[][] snfDxFields = {
    { SNF.ICD_DGNS_CD1, SNF.ICD_DGNS_VRSN_CD1 },
    { SNF.ICD_DGNS_CD2, SNF.ICD_DGNS_VRSN_CD2 },
    { SNF.ICD_DGNS_CD3, SNF.ICD_DGNS_VRSN_CD3 },
    { SNF.ICD_DGNS_CD4, SNF.ICD_DGNS_VRSN_CD4 },
    { SNF.ICD_DGNS_CD5, SNF.ICD_DGNS_VRSN_CD5 },
    { SNF.ICD_DGNS_CD6, SNF.ICD_DGNS_VRSN_CD6 },
    { SNF.ICD_DGNS_CD7, SNF.ICD_DGNS_VRSN_CD7 },
    { SNF.ICD_DGNS_CD8, SNF.ICD_DGNS_VRSN_CD8 },
    { SNF.ICD_DGNS_CD9, SNF.ICD_DGNS_VRSN_CD9 },
    { SNF.ICD_DGNS_CD10, SNF.ICD_DGNS_VRSN_CD10 },
    { SNF.ICD_DGNS_CD11, SNF.ICD_DGNS_VRSN_CD11 },
    { SNF.ICD_DGNS_CD12, SNF.ICD_DGNS_VRSN_CD12 },
    { SNF.ICD_DGNS_CD13, SNF.ICD_DGNS_VRSN_CD13 },
    { SNF.ICD_DGNS_CD14, SNF.ICD_DGNS_VRSN_CD14 },
    { SNF.ICD_DGNS_CD15, SNF.ICD_DGNS_VRSN_CD15 },
    { SNF.ICD_DGNS_CD16, SNF.ICD_DGNS_VRSN_CD16 },
    { SNF.ICD_DGNS_CD17, SNF.ICD_DGNS_VRSN_CD17 },
    { SNF.ICD_DGNS_CD18, SNF.ICD_DGNS_VRSN_CD18 },
    { SNF.ICD_DGNS_CD19, SNF.ICD_DGNS_VRSN_CD19 },
    { SNF.ICD_DGNS_CD20, SNF.ICD_DGNS_VRSN_CD20 },
    { SNF.ICD_DGNS_CD21, SNF.ICD_DGNS_VRSN_CD21 },
    { SNF.ICD_DGNS_CD22, SNF.ICD_DGNS_VRSN_CD22 },
    { SNF.ICD_DGNS_CD23, SNF.ICD_DGNS_VRSN_CD23 },
    { SNF.ICD_DGNS_CD24, SNF.ICD_DGNS_VRSN_CD24 },
    { SNF.ICD_DGNS_CD25, SNF.ICD_DGNS_VRSN_CD25 }
  };

  /**
   * Skilled Nursing Facility procedure code arrays.
   * Procedures and therapies provided in skilled nursing facilities.
   */
  public static final SNF[][] snfPxFields = {
    { SNF.ICD_PRCDR_CD1, SNF.ICD_PRCDR_VRSN_CD1,  SNF.PRCDR_DT1 },
    { SNF.ICD_PRCDR_CD2, SNF.ICD_PRCDR_VRSN_CD2, SNF.PRCDR_DT2 },
    { SNF.ICD_PRCDR_CD3, SNF.ICD_PRCDR_VRSN_CD3, SNF.PRCDR_DT3 },
    { SNF.ICD_PRCDR_CD4, SNF.ICD_PRCDR_VRSN_CD4, SNF.PRCDR_DT4 },
    { SNF.ICD_PRCDR_CD5, SNF.ICD_PRCDR_VRSN_CD5, SNF.PRCDR_DT5 },
    { SNF.ICD_PRCDR_CD6, SNF.ICD_PRCDR_VRSN_CD6, SNF.PRCDR_DT6 },
    { SNF.ICD_PRCDR_CD7, SNF.ICD_PRCDR_VRSN_CD7, SNF.PRCDR_DT7 },
    { SNF.ICD_PRCDR_CD8, SNF.ICD_PRCDR_VRSN_CD8, SNF.PRCDR_DT8 },
    { SNF.ICD_PRCDR_CD9, SNF.ICD_PRCDR_VRSN_CD9, SNF.PRCDR_DT9 },
    { SNF.ICD_PRCDR_CD10, SNF.ICD_PRCDR_VRSN_CD10, SNF.PRCDR_DT10 },
    { SNF.ICD_PRCDR_CD11, SNF.ICD_PRCDR_VRSN_CD11, SNF.PRCDR_DT11 },
    { SNF.ICD_PRCDR_CD12, SNF.ICD_PRCDR_VRSN_CD12, SNF.PRCDR_DT12 },
    { SNF.ICD_PRCDR_CD13, SNF.ICD_PRCDR_VRSN_CD13, SNF.PRCDR_DT13 },
    { SNF.ICD_PRCDR_CD14, SNF.ICD_PRCDR_VRSN_CD14, SNF.PRCDR_DT14 },
    { SNF.ICD_PRCDR_CD15, SNF.ICD_PRCDR_VRSN_CD15, SNF.PRCDR_DT15 },
    { SNF.ICD_PRCDR_CD16, SNF.ICD_PRCDR_VRSN_CD16, SNF.PRCDR_DT16 },
    { SNF.ICD_PRCDR_CD17, SNF.ICD_PRCDR_VRSN_CD17, SNF.PRCDR_DT17 },
    { SNF.ICD_PRCDR_CD18, SNF.ICD_PRCDR_VRSN_CD18, SNF.PRCDR_DT18 },
    { SNF.ICD_PRCDR_CD19, SNF.ICD_PRCDR_VRSN_CD19, SNF.PRCDR_DT19 },
    { SNF.ICD_PRCDR_CD20, SNF.ICD_PRCDR_VRSN_CD20, SNF.PRCDR_DT20 },
    { SNF.ICD_PRCDR_CD21, SNF.ICD_PRCDR_VRSN_CD21, SNF.PRCDR_DT21 },
    { SNF.ICD_PRCDR_CD22, SNF.ICD_PRCDR_VRSN_CD22, SNF.PRCDR_DT22 },
    { SNF.ICD_PRCDR_CD23, SNF.ICD_PRCDR_VRSN_CD23, SNF.PRCDR_DT23 },
    { SNF.ICD_PRCDR_CD24, SNF.ICD_PRCDR_VRSN_CD24, SNF.PRCDR_DT24 },
    { SNF.ICD_PRCDR_CD25, SNF.ICD_PRCDR_VRSN_CD25, SNF.PRCDR_DT25 }
  };
}
