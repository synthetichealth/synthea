package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;
import org.powermock.reflect.Whitebox;

public class LostCareImpactsTest {

  private double defaultEncounterCost = Config.getAsDouble("generate.costs.default_encounter_cost");
  // Modules (including lost care test module)
  private static Map<String, Module.ModuleSupplier> modules;

  @Before
  public void setup() throws Exception {
    // Hack in the lost care test module
    modules = Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    Module testLostCareModule = TestHelper.getFixture("lost_care/lost_care_test.json");
    modules.put("lost_care_test", new Module.ModuleSupplier(testLostCareModule));

    // Clear any Payers that may have already been statically loaded.
    PayerManager.clear();
    TestHelper.loadTestProperties();
    String testState = Config.get("test_state.default", "Massachusetts");
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Config.set("generate.payers.loss_of_care", "true");
    Config.set("lifecycle.death_by_loss_of_care", "true");
    HealthRecord.lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);
    // Load in the csv list of Payers for MA.
    PayerManager.loadPayers(new Location(testState, null));
  }

  @AfterClass
  public static void clean() {
    Config.set("generate.payers.loss_of_care", "false");
    Config.set("lifecycle.death_by_loss_of_care", "false");
    HealthRecord.lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);
  }

  /**
   * Tests that a person should die to lost care.
   */
  @Test
  public void personDiesDueToLostCare() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    Config.set("generate.payers.loss_of_care", "true");
    Config.set("lifecycle.death_by_loss_of_care", "true");
    HealthRecord.lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);
    // Person setup.
    Map<String, Object>  attributes = new HashMap<>();
    attributes.put(Person.BIRTHDATE, time);
    attributes.put(Person.GENDER, "F");
    attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    // Set person's income to $1 lower than the encounter cost to cause debt after an encounter.
    attributes.put(Person.INCOME, (int) defaultEncounterCost-1);
    Person person = new Person(0L);
    person.attributes.putAll(attributes);
    HealthInsuranceModule healthInsuranceModule = new HealthInsuranceModule();
    healthInsuranceModule.process(person, time);
    person.coverage.setPlanAtTime(time, PayerManager.getNoInsurancePlan(), PayerManager.getNoInsurancePlan());
    time = time + Utilities.convertTime("months", 2);
    assertEquals(PayerManager.getNoInsurancePlan(), person.coverage.getPlanAtTime(time));
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT", "4815162342", "Fake Code that, if missed, results in death.");

    // First encounter is uncovered but affordable.
    Encounter coveredEncounter = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter.codes.add(code);
    coveredEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    assertTrue("The person's default record should contain the code.",
        person.defaultRecord.encounters.contains(coveredEncounter));
    assertFalse("The person's loss of care record should not contain the code.",
        person.lossOfCareRecord.encounters.contains(coveredEncounter));

    Module lostCareModule = modules.get("lost_care_test").get();
    lostCareModule.process(person, time);
    time = time + Utilities.convertTime("months", 2);
    lostCareModule.process(person, time);

    assertTrue("Test that the person is still alive. They got the initial code, should not die yet.",
        person.alive(time));

    // Second encounter is uncovered and unaffordable because they are $1 in debt.
    Encounter uncoveredEncounter = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounter.codes.add(code);
    uncoveredEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    assertFalse("The uncovered encounter should not be in the person's covered health record.",
        person.defaultRecord.encounters.contains(uncoveredEncounter));
    assertTrue("The person's loss of care record should now contain the uncovered encounter.",
        person.lossOfCareRecord.encounters.contains(uncoveredEncounter));

    time = time + Utilities.convertTime("months", 13);
    lostCareModule.process(person, time);
    assertFalse(
        "Test that the person is no longer alive because the SNOMED code is now in the lost care record.",
        person.alive(time));

    // The person's cause of death should be lost care.
    Code lostCareCode = new Code("SNOMED-CT", "LOST_CARE_TEST_CODE", "LOST_CARE_TEST_DISPLAY");
    assertEquals(lostCareCode, person.attributes.get(Person.CAUSE_OF_DEATH));
  }

}