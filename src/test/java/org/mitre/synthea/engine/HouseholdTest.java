package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedList;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecordGroupManager;
import org.mitre.synthea.input.Household;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

public class HouseholdTest {

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
        "src/test/resources/fixed_demographics/households_fixed_demographics_test.json");
    go.state = "California";  // Examples are based on California.
    go.population = 100;  // Should be overwritten by number of patients in input file.
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
    // List of raw RecordGroups imported directly from the input file for later comparison.
    fixedRecordGroupManager = generator.importFixedDemographicsFile();
    // Generate each patient from the fixed record input file.
    for (int i = 0; i < generator.options.population; i++) {
      generator.generatePerson(i);
    }
  }

  @Test
  public void checkHouseholdsTest() {

    // Make sure that the correct number of people and households were imported from the fixed records.
    assertEquals(7, generator.internalStore.size());
    assertEquals(7, fixedRecordGroupManager.getPopulationSize());
    assertEquals(2, generator.households.size());

    Map<Integer, Household> households = generator.households;
    // household 1 should have the following members:
    assertTrue(households.get(1).getAdults().stream().anyMatch(adult -> adult.attributes.get(Person.FIRST_NAME).equals("Jane")));
    assertTrue(households.get(1).getAdults().stream().anyMatch(adult -> adult.attributes.get(Person.NAME).equals("John Doe")));
    assertTrue(households.get(1).getAdults().size() == 2);
    assertTrue(households.get(1).getDependents().stream().anyMatch(dependent -> dependent.attributes.get(Person.NAME).equals("Robert Doe")));
    assertTrue(households.get(1).getDependents().stream().anyMatch(dependent -> dependent.attributes.get(Person.NAME).equals("Sally Doe")));
    assertTrue(households.get(1).getDependents().size() == 2);
    // household 2 should have the following members:
    assertTrue(households.get(2).getAdults().stream().anyMatch(adult -> adult.attributes.get(Person.FIRST_NAME).equals("Kate")));
    assertTrue(households.get(2).getAdults().stream().anyMatch(adult -> adult.attributes.get(Person.NAME).equals("Frank Smith")));
    assertTrue(households.get(2).getAdults().size() == 2);
    assertTrue(households.get(2).getDependents().stream().anyMatch(dependent -> dependent.attributes.get(Person.NAME).equals("Billy Smith")));
    assertTrue(households.get(2).getDependents().size() == 1);
  }
}