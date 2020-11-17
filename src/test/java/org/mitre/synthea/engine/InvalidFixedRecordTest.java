package org.mitre.synthea.engine;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecordGroupManager;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Provider;

public class InvalidFixedRecordTest {

  // The generator.
  private static Generator generator;
  private static FixedRecordGroupManager fixedRecordGroupManager;

  /**
   * Configure settings across these tests.
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Colorado");
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
  public void invalidSeedBirthDateTest() {
    // The first person's seed birthdate is invalid.
    fixedRecordGroupManager.getRecordGroup(0).getSeedBirthdate();
  }

  @Test(expected = java.lang.RuntimeException.class)
  public void invalidSeedCityTest() {
    // The second person's seed city is invalid.
    String city = fixedRecordGroupManager.getRecordGroup(1).getSeedCity();
    System.out.println(city);
  }
}