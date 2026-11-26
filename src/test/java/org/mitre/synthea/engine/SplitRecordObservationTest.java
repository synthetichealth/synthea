package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

public class SplitRecordObservationTest {
  private static final long TIME = 1577836800000L;
  private static final String SPLIT = "exporter.split_records";
  private static final String DUPLICATE = "exporter.split_records.duplicate_data";
  private static final String LOSS_OF_CARE = "generate.payers.loss_of_care";
  private static final String MISSING = "test-missing";
  private static final String PRESENT = "test-present";

  private final Map<String, String> originalConfig = new LinkedHashMap<>();
  private Person person;
  private Encounter encounter;

  /** Save shared configuration and create a record with valid insurance coverage. */
  @Before
  public void setup() {
    for (String key : new String[] {SPLIT, DUPLICATE, LOSS_OF_CARE}) {
      originalConfig.put(key, Config.get(key));
    }
    Config.set(SPLIT, "true");
    Config.set(DUPLICATE, "true");
    Config.set(LOSS_OF_CARE, "false");
    PayerManager.loadNoInsurance();
    createPerson();
  }

  /** Restore the exact configuration, including keys that were previously absent. */
  @After
  public void restoreConfig() {
    for (Map.Entry<String, String> entry : originalConfig.entrySet()) {
      if (entry.getValue() == null) {
        Config.remove(entry.getKey());
      } else {
        Config.set(entry.getKey(), entry.getValue());
      }
    }
  }

  private void createPerson() {
    person = new Person(1L);
    person.history = new LinkedList<>();
    person.attributes.put(Person.BIRTHDATE, TIME - 1000L);
    person.coverage.setPlanToNoInsurance(TIME - 1000L);
    encounter = person.record.encounterStart(TIME, EncounterType.AMBULATORY);
  }

  private Logic condition(String operator, String... codes) {
    JsonObject definition = new JsonObject();
    definition.addProperty("condition_type", "Observation");
    definition.addProperty("operator", operator);
    definition.addProperty("value", 10.0);
    JsonArray codeDefinitions = new JsonArray();
    for (String code : codes) {
      JsonObject codeDefinition = new JsonObject();
      codeDefinition.addProperty("system", "LOINC");
      codeDefinition.addProperty("code", code);
      codeDefinition.addProperty("display", "Test observation");
      codeDefinitions.add(codeDefinition);
    }
    definition.add("codes", codeDefinitions);
    return Utilities.getGson().fromJson(definition, Logic.class);
  }

  private Observation historicalObservation() {
    HealthRecord previousRecord = new HealthRecord(person);
    previousRecord.encounterStart(TIME - 10L, EncounterType.AMBULATORY);
    Observation observation = previousRecord.observation(TIME - 10L, PRESENT, 12.0);
    observation.codes.add(new Code("LOINC", PRESENT, "Test observation"));
    person.records.put("previous-provider", previousRecord);
    State state = new State.Simple();
    state.name = "Previous observation";
    state.entry = observation;
    person.history.add(state);
    return observation;
  }

  @Test
  public void missingObservationRemainsNilOnRepeatedChecks() {
    Logic condition = condition("is nil", MISSING);
    assertTrue(condition.test(person, TIME));
    assertTrue(condition.test(person, TIME));
    assertTrue(encounter.observations.isEmpty());
    assertNull(person.record.getLatestObservation(MISSING));
  }

  @Test
  public void missingObservationRemainsNotPresentOnRepeatedChecks() {
    Logic condition = condition("is not nil", MISSING);
    assertFalse(condition.test(person, TIME));
    assertFalse(condition.test(person, TIME));
    assertTrue(encounter.observations.isEmpty());
  }

  @Test
  public void multipleMissingCodesDoNotChangeTheCurrentRecord() {
    Logic condition = condition("is nil", MISSING, "another-missing-code");
    assertTrue(condition.test(person, TIME));
    assertTrue(encounter.observations.isEmpty());
  }

  @Test
  public void missingFirstCodeCanFallBackToALaterHistoricalCode() {
    Observation observation = historicalObservation();
    Logic condition = condition(">", MISSING, PRESENT);
    assertTrue(condition.test(person, TIME));
    assertEquals(1, encounter.observations.size());
    assertSame(observation, encounter.observations.get(0));
    assertTrue(condition.test(person, TIME));
    assertEquals(1, encounter.observations.size());
  }

  @Test
  public void missingNumericComparisonPreservesTheSplitRecordFallback() {
    Logic condition = condition(">", MISSING);
    assertFalse(condition.test(person, TIME));
    assertFalse(condition.test(person, TIME));
    assertTrue(encounter.observations.isEmpty());
  }

  @Test
  public void anExistingHistoricalObservationIsCopiedOnlyOnce() {
    Observation observation = historicalObservation();
    Logic condition = condition(">", PRESENT);
    assertTrue(condition.test(person, TIME));
    assertTrue(condition.test(person, TIME));
    assertEquals(1, encounter.observations.size());
    assertSame(observation, person.record.getLatestObservation(PRESENT));
  }

  @Test
  public void disablingDuplicationStillFindsHistoricalObservationsWithoutCopying() {
    Config.set(DUPLICATE, "false");
    historicalObservation();
    assertTrue(condition(">", MISSING, PRESENT).test(person, TIME));
    assertTrue(condition("is nil", MISSING).test(person, TIME));
    assertTrue(encounter.observations.isEmpty());
  }

  @Test(expected = NullPointerException.class)
  public void missingRequiredObservationStillThrowsWithoutSplitRecords() {
    Config.set(SPLIT, "false");
    createPerson();
    assertFalse(person.hasMultipleRecords);
    condition(">", MISSING).test(person, TIME);
  }
}
