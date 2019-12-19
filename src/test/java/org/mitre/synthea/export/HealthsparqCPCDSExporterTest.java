package org.mitre.synthea.export;

import com.opencsv.CSVWriter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;


public class HealthsparqCPCDSExporterTest {

  private static final String PATIENT_ID = "123";
  private static final String TEST_FIRST_NAME = "Test_First_Name";
  private static final String TEST_LAST_NAME = "Test_Last_Name";
  private static final String EXPECTED_PAYER_OWNER =
      (UUID.nameUUIDFromBytes(TEST_LAST_NAME.getBytes())).toString();
  private static final String SUFFIX = "JR.";
  private static final String TEST_ETHNICITY = "irish";
  private static final String TEST_RACE = "white";
  private static final String TEST_COUNTRY = "US";
  private static final String TEST_COUNTY = "Test_County";
  private static final String TEST_STATE_ABBREVIATION =  "NY";
  private static final String TEST_NAME = "Test_Name";
  private static final String TEST_ZIP = "30905";
  private static final String SEX_M = "M";
  private static final String SEX_F = "F";
  private static final String EMPTY_STRING = "";
  private static final String TEST_ID = "1000";
  private static final String TEST_ENCOUNTER = "Test_Encounter";
  private static final String PAYER_UUID = "b1c428d6-4f07-31e0-90f0-68ffa6ff8c76";
  private static final int TIME_START = -700000000;
  private static final String START_DATE = "1969-12-23";
  private static final String END_DATE = "1969-12-24";
  private static final String NUM_ONE_STR = "1";
  private static final String ZERO_DOLLARS = "0.00";
  private static final String EXPECTED_AMOUNT = "576.67";

  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void constructorCPCDSExporter() throws IOException {
    TestHelper.exportOff();
    Config.set("exporter.healthsparq.cpcds.export", "true");
    File tempOutputFolder = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", tempOutputFolder.toString());

    HealthsparqCPCDSExporter.getInstance();
    File expectedExportFolder = tempOutputFolder.toPath().resolve("cpcds").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());
    assertEquals(4, Objects.requireNonNull(expectedExportFolder.listFiles()).length);
  }

  @Test
  public void buildPatientCpcdsStringArray_Male() {
    String[] returnedArray = getMockExporter()
        .buildPatientCpcdsStringArray(TestHelper.generateMockPerson(SEX_M, true));
    assertEquals(18, returnedArray.length);
    assertEquals(EMPTY_STRING, returnedArray[0]);

    assertEquals(PATIENT_ID, returnedArray[1]);
    assertEquals(PATIENT_ID, returnedArray[2]);
    assertEquals("1969-12-21", returnedArray[3]);
    assertEquals("2019-10-25", returnedArray[4]);
    assertEquals(TEST_COUNTY, returnedArray[5]);
    assertEquals(TEST_STATE_ABBREVIATION, returnedArray[6]);
    assertEquals(TEST_COUNTRY, returnedArray[7]);
    assertEquals(TEST_RACE, returnedArray[8]);
    assertEquals(TEST_ETHNICITY, returnedArray[9]);
    assertEquals("male", returnedArray[10]);
    assertEquals(SEX_M, returnedArray[11]);
    assertEquals(TEST_NAME, returnedArray[12]);
    assertEquals(TEST_FIRST_NAME, returnedArray[13]);

    assertEquals(EMPTY_STRING, returnedArray[14]);

    assertEquals(TEST_LAST_NAME, returnedArray[15]);
    assertEquals(SUFFIX, returnedArray[16]);
    assertEquals(TEST_ZIP, returnedArray[17]);
  }

  @Test
  public void buildPatientCpcdsStringArray_Female() {
    String[] returnedArray = getMockExporter()
        .buildPatientCpcdsStringArray(
            TestHelper.generateMockPerson(SEX_F, false));
    assertEquals(18, returnedArray.length);
    assertEquals(EMPTY_STRING, returnedArray[0]);

    assertEquals(PATIENT_ID, returnedArray[1]);
    assertEquals(PATIENT_ID, returnedArray[2]);
    assertEquals("1969-12-21", returnedArray[3]);

    assertEquals(EMPTY_STRING, returnedArray[4]);

    assertEquals(TEST_COUNTY, returnedArray[5]);
    assertEquals(TEST_STATE_ABBREVIATION, returnedArray[6]);
    assertEquals(TEST_COUNTRY, returnedArray[7]);
    assertEquals(TEST_RACE, returnedArray[8]);
    assertEquals(TEST_ETHNICITY, returnedArray[9]);
    assertEquals("female", returnedArray[10]);
    assertEquals(SEX_F, returnedArray[11]);
    assertEquals(TEST_NAME, returnedArray[12]);
    assertEquals(TEST_FIRST_NAME, returnedArray[13]);

    assertEquals(EMPTY_STRING, returnedArray[14]);

    assertEquals(TEST_LAST_NAME, returnedArray[15]);
    assertEquals(SUFFIX, returnedArray[16]);
    assertEquals(TEST_ZIP, returnedArray[17]);
  }

  @Test
  public void buildCoverageStringArray() {
    Person person = TestHelper.generateMockPerson(SEX_M, true);
    Payer.loadNoInsurance();
    person.setPayerAtAge(0, Payer.noInsurance);
    String[] returnedArray = getMockExporter().buildCoverageCpcdsStringArray(
        person,
        1969,
        1969,
        Payer.noInsurance);
    assertEquals(13, returnedArray.length);
    assertEquals(EMPTY_STRING, returnedArray[0]);
    assertEquals(EXPECTED_PAYER_OWNER, returnedArray[1]);

    assertEquals(PATIENT_ID, returnedArray[2]);

    assertEquals(EXPECTED_PAYER_OWNER, returnedArray[3]);
    assertEquals(EMPTY_STRING, returnedArray[4]);
    assertEquals(EMPTY_STRING, returnedArray[5]);
    assertEquals(EMPTY_STRING, returnedArray[6]);

    assertEquals("1969-01-01", returnedArray[7]);
    assertEquals("1969-12-31", returnedArray[8]);

    assertEquals(EMPTY_STRING, returnedArray[9]);
    assertEquals(EMPTY_STRING, returnedArray[10]);

    assertEquals("NO_INSURANCE", returnedArray[11]);
    assertEquals(PAYER_UUID, returnedArray[12]);
  }

  @Test
  public void getPatientRelationshipToSubscriber_guardian() {
    String returnString = getMockExporter()
        .getPatientRelationshipToSubscriber("Guardian");

    assertEquals("Dependent", returnString);
  }

  @Test
  public void getPatientRelationshipToSubscriber_null() {
    String returnString = getMockExporter()
        .getPatientRelationshipToSubscriber(null);

    assertEquals("", returnString);
  }

  @Test
  public void getPatientRelationshipToSubscriber_any() {
    String returnString = getMockExporter()
        .getPatientRelationshipToSubscriber(TEST_NAME);

    assertEquals(TEST_NAME, returnString);
  }

  @Test
  public void buildClaimCpcdsStringArray() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(
        TestHelper.getNoInsurancePayer());
    String[] returnedArray = getMockExporter()
        .buildClaimCpcdsStringArray(encounter,
            TEST_ID,
            encounter.claim);
    assertEquals(64, returnedArray.length);
    assertEquals(EMPTY_STRING, returnedArray[0]); // client_cd

    assertEquals(PATIENT_ID, returnedArray[1]); // patient_uuid

    assertEquals(EXPECTED_PAYER_OWNER, returnedArray[2]); // coverage_uuid

    assertEquals(TEST_ID, returnedArray[3]); // claim_uuid
    assertEquals(START_DATE, returnedArray[4]); // claim_start_dt
    assertEquals(END_DATE, returnedArray[5]); // claim_end_dt

    // following two fields are random temporarily
    //    assertEquals(EMPTY_STRING, returnedArray[6]); // claim_paid_dt
    //    assertEquals(EMPTY_STRING, returnedArray[7]); // claim_rcvd_dt

    assertEquals(START_DATE, returnedArray[8]); // claim_adm_dt
    assertEquals(END_DATE, returnedArray[9]); // claim_disch_dt
    assertEquals(PATIENT_ID, returnedArray[10]); // ptnt_acct_num
    assertEquals(PATIENT_ID, returnedArray[11]); // mrn
    assertEquals(TEST_ID, returnedArray[12]); // claim_id

    assertEquals(EMPTY_STRING, returnedArray[13]); // claim_adj_from_id
    assertEquals(EMPTY_STRING, returnedArray[14]); // claim_adj_to_id
    assertEquals(EMPTY_STRING, returnedArray[15]); // drg_id
    assertEquals(EMPTY_STRING, returnedArray[16]); // claim_src_ip_admit_cd
    assertEquals(EMPTY_STRING, returnedArray[17]); // claim_ip_type_admit_cd
    assertEquals(EMPTY_STRING, returnedArray[18]); // claim_bill_fac_type_cd
    assertEquals(EMPTY_STRING, returnedArray[19]); // claim_svc_cls_type_cd
    assertEquals(EMPTY_STRING, returnedArray[20]); // claim_freq_cd

    assertEquals("active", returnedArray[21]); // claim_proc_status_cd
    assertEquals("institutional", returnedArray[22]); // claim_type_cd

    assertEquals(EMPTY_STRING, returnedArray[23]); // ptnt_disch_status_cd
    assertEquals(EMPTY_STRING, returnedArray[24]); // claim_adj_dnl_cd

    assertEquals(PAYER_UUID, returnedArray[25]); // claim_prm_payer_id
    assertEquals("provider", returnedArray[26]); // claim_payee_type_cd

    // 27 is random uuid claim_payee_id
    assertEquals(EMPTY_STRING, returnedArray[28]); // claim_pay_status_cd

    assertEquals(PAYER_UUID, returnedArray[29]); // claim_payer_id

    assertEquals(EMPTY_STRING, returnedArray[30]); // day_supply
    assertEquals(EMPTY_STRING, returnedArray[31]); // rx_srv_ref_num
    assertEquals(EMPTY_STRING, returnedArray[32]); // daw_prod_slctn_cd
    assertEquals(EMPTY_STRING, returnedArray[33]); // fill_num
    assertEquals(EMPTY_STRING, returnedArray[34]); // rx_orgn_cd
    assertEquals(EMPTY_STRING, returnedArray[35]); // brnd_gnrc_cd
    assertEquals(EMPTY_STRING, returnedArray[36]); // rx_srv_type_cd
    assertEquals(EMPTY_STRING, returnedArray[37]); // ptnt_rsdnc_cd

    assertEquals(TEST_ID, returnedArray[38]); // bill_npi

    assertEquals(EMPTY_STRING, returnedArray[39]); // bill_prov_ntwk_flag

    assertEquals(TEST_ID, returnedArray[40]); // attnd_npi

    assertEquals(EMPTY_STRING, returnedArray[41]); // attnd_prov_ntwk_flag
    assertEquals(EMPTY_STRING, returnedArray[42]); // site_of_svc_npi
    assertEquals(EMPTY_STRING, returnedArray[43]); // site_of_svc_prov_ntwk_flag
    assertEquals(EMPTY_STRING, returnedArray[44]); // refer_npi
    assertEquals(EMPTY_STRING, returnedArray[45]); // refer_prov_ntwk_flag

    assertEquals(TEST_ID, returnedArray[46]); // render_npi

    assertEquals(EMPTY_STRING, returnedArray[47]); // render_prov_ntwk_flag
    assertEquals(EMPTY_STRING, returnedArray[48]); // prscrb_npi
    assertEquals(EMPTY_STRING, returnedArray[49]); // prscrb_provider_ntwk_flag

    assertEquals(TEST_ID, returnedArray[50]); // pcp_npi
    assertEquals(EXPECTED_AMOUNT, returnedArray[51]); // tot_amt_req
    assertEquals(ZERO_DOLLARS, returnedArray[52]); // tot_amt_eqv
    assertEquals(ZERO_DOLLARS, returnedArray[53]); // tot_amt_ptnt_pay
    assertEquals(ZERO_DOLLARS, returnedArray[54]); // tot_amt_prov_pay
    assertEquals(ZERO_DOLLARS, returnedArray[55]); // tot_amt_reimb
    assertEquals(EXPECTED_AMOUNT, returnedArray[56]); // tot_amt_pay
    assertEquals(ZERO_DOLLARS, returnedArray[57]); // tot_amt_dsalwd
    assertEquals(ZERO_DOLLARS, returnedArray[58]); // tot_amt_ded
    assertEquals(ZERO_DOLLARS, returnedArray[59]); // tot_amt_coin
    assertEquals(ZERO_DOLLARS, returnedArray[60]); // tot_amt_copay
    assertEquals(ZERO_DOLLARS, returnedArray[61]); // tot_amt_memb
    assertEquals(ZERO_DOLLARS, returnedArray[62]); // tot_amt_prm_pay
    assertEquals(ZERO_DOLLARS, returnedArray[63]); // tot_amt_dscnt
  }

  @Test
  public void buildMultiValueEntryStringClaimCodes_all() {
    List<HealthRecord.Code> codeList = TestHelper.generateCodes();
    String returnCodeString = getMockExporter()
        .buildMultiValueEntryStringClaimCodes(codeList, "code");
    String returnDisplayString = getMockExporter()
        .buildMultiValueEntryStringClaimCodes(codeList, "display");
    String returnSystemString = getMockExporter()
        .buildMultiValueEntryStringClaimCodes(codeList, "system");

    assertEquals("Test_Code0;Test_Code1;Test_Code2", returnCodeString);
    assertEquals("Test_Display0;Test_Display1;Test_Display2", returnDisplayString);
    assertEquals("Test_System0;Test_System1;Test_System2", returnSystemString);
  }

  @Test
  public void buildMultiValueEntryStringClaimCodes_invalidCodeField() {
    List<HealthRecord.Code> codeList = TestHelper.generateCodes();
    String returnCodeString = getMockExporter()
        .buildMultiValueEntryStringClaimCodes(codeList, "invalid");
    assertEquals(EMPTY_STRING, returnCodeString);
  }

  @Test
  public void buildClaimLineCpcdsStringArray() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(
        TestHelper.getNoInsurancePayer());
    String[] returnedArray = getMockExporter()
        .buildClaimLineCpcdsStringArray(1, encounter.claim.items.get(0),
            encounter,
            TEST_ID);
    assertEquals(44, returnedArray.length);

    assertEquals(EMPTY_STRING, returnedArray[0]); // client_cd

    assertEquals(TEST_ID, returnedArray[1]); // claim_uuid
    assertEquals(START_DATE, returnedArray[2]); // dos_from
    assertEquals(NUM_ONE_STR, returnedArray[3]); // claim_line_n
    assertEquals(END_DATE, returnedArray[4]); // dos_to
    assertEquals(NUM_ONE_STR, returnedArray[5]); // type_srv_cd
    assertEquals("21", returnedArray[6]); // pos_cd

    assertEquals(EMPTY_STRING, returnedArray[7]); // rev_cd
    assertEquals(EMPTY_STRING, returnedArray[8]); // max_alwd_unit
    assertEquals(EMPTY_STRING, returnedArray[9]); // ndc_cd
    assertEquals(EMPTY_STRING, returnedArray[10]); // cmpnd_cd
    assertEquals(EMPTY_STRING, returnedArray[11]); // qty_dspnsd
    assertEquals(EMPTY_STRING, returnedArray[12]); // qty_qual_cd
    assertEquals(EMPTY_STRING, returnedArray[13]); // line_benefit_pay_status
    assertEquals(EMPTY_STRING, returnedArray[14]); // line_pay_dnl_cd

    assertEquals(ZERO_DOLLARS, returnedArray[15]); // amt_dsalwd
    assertEquals(ZERO_DOLLARS, returnedArray[16]); // amt_reimb
    assertEquals(ZERO_DOLLARS, returnedArray[17]); // amt_ptnt_pay

    assertEquals(EMPTY_STRING, returnedArray[18]); // amt_rx

    assertEquals(ZERO_DOLLARS, returnedArray[19]); // amt_pay
    assertEquals(ZERO_DOLLARS, returnedArray[20]); // amt_prov_pay
    assertEquals(ZERO_DOLLARS, returnedArray[21]); // amt_ded
    assertEquals(ZERO_DOLLARS, returnedArray[22]); // amt_prm_pay

    assertEquals(ZERO_DOLLARS, returnedArray[23]); // amt_coin
    assertEquals(ZERO_DOLLARS, returnedArray[24]); // amt_req
    assertEquals(ZERO_DOLLARS, returnedArray[25]); // amt_eqv

    assertEquals(ZERO_DOLLARS, returnedArray[26]); // amt_memb
    assertEquals(ZERO_DOLLARS, returnedArray[27]); // amt_copay
    assertEquals(ZERO_DOLLARS, returnedArray[28]); // amt_dscnt

    assertEquals(EMPTY_STRING, returnedArray[29]); // diag_cd
    assertEquals(EMPTY_STRING, returnedArray[30]); // diag_desc
    assertEquals(EMPTY_STRING, returnedArray[31]); // poa_ind
    assertEquals(EMPTY_STRING, returnedArray[32]); // diag_vsn
    assertEquals(EMPTY_STRING, returnedArray[33]); // diag_type
    assertEquals(EMPTY_STRING, returnedArray[34]); // is_e_cd

    assertEquals("Test_Code0;Test_Code1;Test_Code2",
        returnedArray[35]); // proc_cd
    assertEquals("Test_Display0;Test_Display1;Test_Display2",
        returnedArray[36]); // proc_desc
    assertEquals(START_DATE, returnedArray[37]); // proc_dt
    assertEquals("Test_System0;Test_System1;Test_System2",
        returnedArray[38]); // proc_cd_type

    assertEquals(EMPTY_STRING, returnedArray[39]); // proc_type
    assertEquals(EMPTY_STRING, returnedArray[40]); // proc_mod1
    assertEquals(EMPTY_STRING, returnedArray[41]); // proc_mod2
    assertEquals(EMPTY_STRING, returnedArray[42]); // proc_mod3
    assertEquals(EMPTY_STRING, returnedArray[43]); // proc_mod4
  }

  @Test
  public void checkIfClaimItemIsEntryClassType_true() {
    HealthRecord healthRecord = new HealthRecord(TestHelper.generateMockPerson(SEX_M, true));
    HealthRecord.Entry entry = healthRecord.new Entry(TIME_START, TEST_ENCOUNTER);

    boolean returnedBool = getMockExporter().compareClaimItemEntryClassType(
        HealthRecord.Entry.class.getSimpleName(),
        entry);

    assertTrue(returnedBool);
  }

  @Test
  public void checkIfClaimItemIsEntryClassType_false() {
    HealthRecord healthRecord = new HealthRecord(TestHelper.generateMockPerson(SEX_M, true));
    HealthRecord.Procedure procedure = healthRecord.new Procedure(TIME_START, TEST_ENCOUNTER);

    boolean returnedBool = getMockExporter().compareClaimItemEntryClassType(
        HealthRecord.Entry.class.getSimpleName(),
        procedure);

    assertFalse(returnedBool);
  }

  @Test
  public void getPlaceOfServiceCodeFromEncounterType_emergency() {
    String returnedString = getMockExporter()
        .getPlaceOfServiceCodeFromEncounterType(Clinician.EMERGENCY);

    assertEquals("20", returnedString);
  }

  @Test
  public void getPlaceOfServiceCodeFromEncounterType_default() {
    String returnedString = getMockExporter().getPlaceOfServiceCodeFromEncounterType(TEST_NAME);

    assertEquals("21", returnedString);
  }

  @Test
  public void clean_null() {
    String returnedString = HealthsparqCPCDSExporter.clean(null);

    assertEquals(EMPTY_STRING, returnedString);
  }

  @Test
  public void clean_crlf() {
    String returnedString = HealthsparqCPCDSExporter.clean(" test\r\nstring\rtest\nstring,test ");

    assertEquals("test string test string test", returnedString);
  }

  private HealthsparqCPCDSExporter getMockExporter() {
    return new HealthsparqCPCDSExporter(
        mock(CSVWriter.class),
        mock(CSVWriter.class),
        mock(CSVWriter.class),
        mock(CSVWriter.class));
  }

}
