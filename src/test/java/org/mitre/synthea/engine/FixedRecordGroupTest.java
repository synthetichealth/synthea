package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecordGroup;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Provider;


public class FixedRecordGroupTest {

  // The generator.
  private static Generator generator;
  private static List<FixedRecordGroup> recordGroups;

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
    recordGroups = generator.importFixedPatientDemographicsFile();
  }

  @Test(expected = java.lang.RuntimeException.class)
  public void invalidBirthDateTest() {
    // The first person has an invalid birthdate for each FixedRecord in the FixedRecordGroup.
    recordGroups.get(0).getValidBirthdate();
  }

  @Test
  public void onlyThirdBirthDateIsValidTest() {
    // The second person's only valid birthdate is the third one.
    long date = recordGroups.get(1).getValidBirthdate();
    assertEquals(date, recordGroups.get(1).records.get(2).getBirthDate());
  }

  @Test(expected = java.lang.RuntimeException.class)
  public void invalidCityTest() {
    // The second person has an invalid city for each FixedRecord in the FixedRecordGroup.
    String city = recordGroups.get(1).getSafeCity();
    System.out.println(city);
  }

  @Test
  public void onlyThirdCityIsValidTest() {
    // The first person's only valid city is the third one.
    String city = recordGroups.get(0).getSafeCity();
    assertEquals(city, recordGroups.get(0).records.get(2).getSafeCity());
  }
}