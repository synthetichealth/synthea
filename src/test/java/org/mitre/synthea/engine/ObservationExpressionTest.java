package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;

public class ObservationExpressionTest {
  private static final long TIME = 1577836800000L;

  @BeforeClass
  public static void setup() {
    PayerManager.loadNoInsurance();
  }

  private Person person(double factor) {
    Person person = new Person(1L);
    person.history = new LinkedList<>();
    person.attributes.put(Person.BIRTHDATE, TIME - 1000L);
    person.attributes.put("factor", factor);
    person.setVitalSign(VitalSign.BMI, 25.0);
    person.coverage.setPlanToNoInsurance(TIME);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    person.record.encounterStart(TIME, EncounterType.WELLNESS);
    return person;
  }

  private void assertValue(double expected, HealthRecord.Observation observation) {
    assertNotNull("An expression observation must have a value", observation.value);
    assertTrue("Expression results must remain numeric", observation.value instanceof Number);
    assertEquals(expected, ((Number) observation.value).doubleValue(), 0.000001);
  }

  @Test
  public void standaloneExpressionUsesTheCurrentPersonOnEachEvaluation() throws Exception {
    Module module = TestHelper.getFixture("observation_expression.json");
    State state = module.getState("Standalone");
    Person person = person(3.0);
    assertTrue(state.process(person, TIME));
    assertValue(8.0, person.record.getLatestObservation("2345-7"));

    person.attributes.put("factor", 9.0);
    assertTrue(state.process(person, TIME + 1));
    assertEquals(2, person.record.encounters.get(0).observations.size());
    assertValue(20.0, person.record.encounters.get(0).observations.get(1));
  }

  @Test
  public void reportAndComponentExpressionsAreEvaluatedThroughModuleProcessing() throws Exception {
    Module module = TestHelper.getFixture("observation_expression.json");
    Person person = person(3.0);
    assertTrue(module.process(person, TIME));

    Encounter encounter = person.record.encounters.get(0);
    assertEquals(1, encounter.reports.size());
    assertValue(28.0, encounter.reports.get(0).observations.get(0));
    HealthRecord.Observation panel = person.record.getLatestObservation("85354-9");
    assertEquals(1, panel.observations.size());
    assertValue(73.0, panel.observations.get(0));
  }

  @Test
  public void clonedExpressionInitializesItsProcessorOnAnotherThread() throws Exception {
    Module module = TestHelper.getFixture("observation_expression.json");
    State original = module.getState("Standalone");
    Person first = person(3.0);
    assertTrue(original.process(first, TIME));
    assertValue(8.0, first.record.getLatestObservation("2345-7"));

    State clone = original.clone();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Person> result = executor.submit(() -> {
        Person second = person(9.0);
        assertTrue(clone.process(second, TIME));
        return second;
      });
      assertValue(20.0, result.get().record.getLatestObservation("2345-7"));
    } finally {
      executor.shutdownNow();
    }
  }
}
