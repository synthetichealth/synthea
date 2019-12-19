package org.mitre.synthea.world.concepts;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class SnomedConversionTest {
  public static final String FAKE_CODE = "Fake_Code";
  public static final String FAKE_DISPLAY = "Fake_Display";

  private static SnomedConversion snomedConversion;
  private static boolean snomedMapLoaded;

  /**
   * Attempt to load test snomed mapping file for following tests.
   * Store success/failure of loading to determine which tests to skip.
   */
  @BeforeClass public static void loadSnomedMap() {
    snomedConversion = new SnomedConversion();
    snomedMapLoaded = snomedConversion.loadSnomedMap(
        "src/test/resources/generic/concepts/snomed_mapping_test.txt",
        "src/test/resources/generic/concepts/icd10_display_test.csv");
  }

  @Test
  public void loadSnomedMapAndFindIcd10Code_validSnomedCode_icd10DisplayMapSuccess() {
    Assume.assumeTrue("If SNOMED map loaded, test will run, otherwise skipped.", snomedMapLoaded);
    HealthRecord.Code responseCode = snomedConversion
        .findIcd10Code(createMockCode("32121007","hypertrophy"));
    Assert.assertEquals("Q55.29", responseCode.code);
    Assert.assertEquals("Other congenital malformations of testis and scrotum",
        responseCode.display);
    Assert.assertEquals(SnomedConversion.ICD10, responseCode.system);
    Assert.assertEquals(SnomedConversion.ICD10, responseCode.displaySystem);
  }

  @Test
  public void loadSnomedMapAndFindIcd10Code_realSnomed_noDisplayMap() {
    Assume.assumeTrue("If SNOMED map loaded, test will run, otherwise skipped.", snomedMapLoaded);
    HealthRecord.Code responseCode = snomedConversion
        .findIcd10Code(createMockCode("126850006","neoplasm"));
    Assert.assertEquals("C21.8", responseCode.code);
    Assert.assertEquals("neoplasm", responseCode.display);
    Assert.assertEquals(SnomedConversion.ICD10, responseCode.system);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.displaySystem);
  }

  @Test
  public void findIcd10Code_fakeCodeAndDisplay() {
    Assume.assumeTrue("If SNOMED map loaded, test will run, otherwise skipped.", snomedMapLoaded);
    HealthRecord.Code responseCode = snomedConversion
        .findIcd10Code(createMockCode(FAKE_CODE, FAKE_DISPLAY));
    Assert.assertEquals(FAKE_CODE, responseCode.code);
    Assert.assertEquals(FAKE_DISPLAY, responseCode.display);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.system);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.displaySystem);
  }

  @Test
  public void findIcd10Code_realSnomed_fakeDisplay_correctIcd10Display() {
    Assume.assumeTrue("If SNOMED map loaded, test will run, otherwise skipped.", snomedMapLoaded);
    HealthRecord.Code responseCode = snomedConversion
        .findIcd10Code(createMockCode("389271000", FAKE_DISPLAY));
    Assert.assertEquals("Q78.4", responseCode.code);
    Assert.assertEquals("Enchondromatosis", responseCode.display);
    Assert.assertEquals(SnomedConversion.ICD10, responseCode.system);
    Assert.assertEquals(SnomedConversion.ICD10, responseCode.displaySystem);
  }

  @Test
  public void findIcd10Code_fakeCode() {
    Assume.assumeTrue("If SNOMED map loaded, test will run, otherwise skipped.", snomedMapLoaded);
    HealthRecord.Code responseCode = snomedConversion
        .findIcd10Code(createMockCode(FAKE_CODE,"neoplasm"));
    Assert.assertEquals(FAKE_CODE, responseCode.code);
    Assert.assertEquals("neoplasm", responseCode.display);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.system);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.displaySystem);
  }

  @Test
  public void findIcd10Code_noFile() {
    SnomedConversion snomedConversionNoFile = new SnomedConversion();
    HealthRecord.Code responseCode = snomedConversionNoFile
        .findIcd10Code(createMockCode("126850006", "neoplasm"));
    Assert.assertEquals("126850006", responseCode.code);
    Assert.assertEquals("neoplasm", responseCode.display);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.system);
    Assert.assertEquals(SnomedConversion.SNOMED, responseCode.displaySystem);
  }

  private HealthRecord.Code createMockCode(String code, String display) {
    return new HealthRecord.Code(SnomedConversion.SNOMED, code, display);
  }
}