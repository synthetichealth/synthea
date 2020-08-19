package org.mitre.synthea.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.mitre.synthea.world.agents.Person;

/**
 * BlueButton 2 Exporter
 */
public class BB2Exporter implements Flushable {
  
  private SynchronizedBBLineWriter beneficiary;
  
  /**
   * Create the output folder and files. Write headers to each file.
   */
  private BB2Exporter() {
    try {
      prepareOutputFiles();
    } catch(IOException e) {
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

    // Initialize output files
    File output = Exporter.getOutputFolder("bb2", null);
    output.mkdirs();
    Path outputDirectory = output.toPath();
    File beneficiaryFile = outputDirectory.resolve("beneficiary.csv").toFile();
    beneficiary = new SynchronizedBBLineWriter(beneficiaryFile);
    beneficiary.writeHeader(BeneficiaryFields.class);
  }
  
  /**
   * Export a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  void export(Person person, long stopTime) throws IOException {
    HashMap<BeneficiaryFields, String> fieldValues = new HashMap<>();
    fieldValues.put(BeneficiaryFields.BENE_SRNM_NAME, 
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BeneficiaryFields.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
    beneficiary.writeValues(BeneficiaryFields.class, fieldValues);
  }

  /**
   * Flush contents of any buffered streams to disk.
   * @throws IOException if something goes wrong
   */
  @Override
  public void flush() throws IOException {
    beneficiary.flush();
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
    BENE_PTD_TRMNTN_CD,
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
   * Utility class for writing to BB2 files
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
      synchronized(lock) {
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
     * Write a BB2 file line
     * @param enumClass the enumeration class whose members define the column names
     * @param fieldValues a sparse map of column names to values, missing values will result in
     * empty values in the corresponding column
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
