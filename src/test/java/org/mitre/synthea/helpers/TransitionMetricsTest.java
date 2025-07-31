package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.TransitionMetrics.Metric;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.agents.behaviors.planeligibility.PlanEligibilityFinder;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class TransitionMetricsTest {

  @BeforeClass
  public static void setup() {
    PlanEligibilityFinder.buildPlanEligibilities(Generator.DEFAULT_STATE,
        Config.get("generate.payers.insurance_plans.eligibilities_file"));
  }

  @Test
  public void testExampleModule() throws Exception {
    TransitionMetrics.enabled = true;
    TransitionMetrics.clear();

    Provider mockProvider = TestHelper.buildMockProvider();
    Person person = new Person(0L);
    person.attributes.put(Person.RACE, "black");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.attributes.put(Person.GENDER, "F");
    person.setProvider(EncounterType.WELLNESS, mockProvider);
    person.setProvider(EncounterType.AMBULATORY, mockProvider);
    long time = System.currentTimeMillis();
    LifecycleModule.birth(person, time);
    PayerManager.loadNoInsurance();
    person.coverage.setPlanToNoInsurance(time);

    Module example = TestHelper.getFixture("example_module.json");
    // some notes about this example module:
    // 1. gender != M --> transition immediately to terminal
    // 2. Age_Guard waits until age 40 and transitions 90% to terminal
    //    so we need a fixed seed to ensure we hit things

    time = run(person, example, time);

    Metric m = TransitionMetrics.getMetric(example.name, "Initial");
    assertEquals(1, m.entered.get()); // 1 person entered the state
    assertEquals(0, m.current.get()); // none currently in this state

    Map<String,AtomicInteger> dests = m.destinations;
    assertEquals(null, dests.get("Age_Guard")); // the 1 person did not go here
    assertEquals(1, dests.get("Terminal").get()); // they went here

    m = TransitionMetrics.getMetric(example.name, "Pre_Examplitis");
    assertEquals(0, m.entered.get()); // nobody hit this

    m = TransitionMetrics.getMetric(example.name, "Terminal");
    assertEquals(1, m.entered.get()); // 1 person hit this
    assertEquals(1, m.current.get()); // and is still there

    TransitionMetrics.clear();

    for (long seed : new long[] {31255L, 12L, 12345L}) {
      // seeds chosen by experimentation, to ensure we hit "Pre_Examplitis" at least once
      person = new Person(seed);
      person.attributes.put(Person.GENDER, "M");
      person.setProvider(EncounterType.WELLNESS, mockProvider);
      person.setProvider(EncounterType.AMBULATORY, mockProvider);
      time = System.currentTimeMillis();
      person.attributes.put(Person.BIRTHDATE, time);
      person.coverage.setPlanToNoInsurance(time);

      time = run(person, example, time);
    }

    m = TransitionMetrics.getMetric(example.name, "Initial");
    assertEquals(3, m.entered.get()); // 3 people entered the state
    assertEquals(0, m.current.get()); // none currently in this state

    dests = m.destinations;
    assertEquals(null, dests.get("Terminal")); // the 3 people did not go here
    assertEquals(3, dests.get("Age_Guard").get()); // they went here

    m = TransitionMetrics.getMetric(example.name, "Pre_Examplitis");
    assertEquals(2, m.entered.get());

    m = TransitionMetrics.getMetric(example.name, "Terminal");
    assertEquals(3, m.entered.get());
    assertEquals(3, m.current.get());
    TransitionMetrics.enabled = false;
  }

  private long run(Person person, Module singleModule, long start) {
    long time = start;
    // hack the wellness encounter just in case
    person.releaseCurrentEncounter(time, EncounterModule.NAME);
    EncounterModule.createEncounter(person, time, EncounterType.WELLNESS,
        ClinicianSpecialty.GENERAL_PRACTICE,
        EncounterModule.ENCOUNTER_CHECKUP, EncounterModule.NAME);
    // "true" indicates that we are in a wellness encounter (true)
    person.attributes.put(EncounterModule.ACTIVE_WELLNESS_ENCOUNTER, true);
    // run until the module completes (it has no loops so it is guaranteed to)
    // reminder that process returns true when the module is "done"
    while (person.alive(time) && !singleModule.process(person, time)) {
      // Give the person No Insurance to prevent null pointers.
      String key = EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + singleModule.name;
      // If the person had a wellness encounter, end it, so they can start
      // the 'Examplotomy_Encounter'
      if ((Boolean) person.attributes.getOrDefault(key, true)) {
        person.attributes.remove(key);
        person.attributes.remove(EncounterModule.ACTIVE_WELLNESS_ENCOUNTER);
        person.releaseCurrentEncounter(time, EncounterModule.NAME);
      }
      time += Utilities.convertTime("years", 1);
      person.coverage.setPlanToNoInsurance(time);
    }
    return time;
  }
}
