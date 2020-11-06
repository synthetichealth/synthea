package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecordGroupManager;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Provider;


public class FixedRecordGroupTest {

  // The generator.
  private static Generator generator;
  private static FixedRecordGroupManager fixedRecordGroupManager;

  /**
   * Configure settings across these tests.
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("generate.only_dead_patients", "false"); 
    Config.set("exporter.split_records", "true");
    Provider.clear();
    Payer.clear();
    // Create a generator with the preset fixed demographics test file.
    GeneratorOptions go = new GeneratorOptions();
    go.fixedRecordPath = new File(
        "src/test/resources/fixed_demographics/invalid_fixed_demographics_test.json");
    go.state = "Colorado";  // Examples are based on Colorado.
    go.population = 100;  // Should be overwritten by number of patients in input file.
    generator = new Generator(go);
    fixedRecordGroupManager = generator.importFixedDemographicsFile();
  }

  @Test(expected = java.lang.RuntimeException.class)
  public void invalidBirthDateTest() {
    // The first person has an invalid birthdate for each FixedRecord in the FixedRecordGroup.
    fixedRecordGroupManager.getRecordGroup(0).getValidBirthdate();
  }

  @Test
  public void onlyThirdBirthDateIsValidTest() {
    // The second person's only valid birthdate is the third one.
    long date = fixedRecordGroupManager.getRecordGroup(1).getValidBirthdate();
    assertEquals(date, fixedRecordGroupManager.getRecordGroup(1).variantRecords.get(2).getBirthDate());
  }

  @Test(expected = java.lang.RuntimeException.class)
  public void invalidCityTest() {
    // The second person has an invalid city for each FixedRecord in the FixedRecordGroup.
    String city = fixedRecordGroupManager.getRecordGroup(1).getSafeCity();
    System.out.println(city);
  }

  @Test
  public void onlyThirdCityIsValidTest() {
    // The first person's only valid city is the third one.
    String city = fixedRecordGroupManager.getRecordGroup(0).getSafeCity();
    assertEquals(city, fixedRecordGroupManager.getRecordGroup(0).variantRecords.get(2).getSafeCity());
  }
}