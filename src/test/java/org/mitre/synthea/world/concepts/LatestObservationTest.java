package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.engine.Logic;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

/** Regression checks for selecting the latest recorded observation within an encounter. */
public class LatestObservationTest {
  private static final long TIME = 1577836800000L;
  private static final String LACTATE = "32693-4";

  private Person person;
  private Encounter encounter;

  /** Create one encounter with enough coverage for repeated measurements. */
  @Before
  public void setup() {
    PayerManager.loadNoInsurance();
    person = new Person(1L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(0L);
    person.coverage.getLastPlanRecord().updateStopTime(TIME + 100L);
    encounter = person.record.encounterStart(TIME, EncounterType.INPATIENT);
  }

  private Observation lactate(long time, double value) {
    Observation observation = person.record.observation(time, LACTATE, value);
    observation.codes.add(new Code("LOINC", LACTATE, "Lactate [Moles/volume] in Blood"));
    observation.unit = "mmol/L";
    return observation;
  }

  @Test
  public void returnsRepeatedMeasurementFromSameEncounter() {
    lactate(TIME, 1.0);
    Observation repeat = lactate(TIME + 1L, 3.0);

    assertSame(repeat, person.record.getLatestObservation(LACTATE));
  }

  @Test
  public void observationLogicUsesRepeatResult() {
    Logic condition = Utilities.getGson().fromJson("{\"condition_type\":\"Observation\","
        + "\"codes\":[{\"system\":\"LOINC\",\"code\":\"32693-4\","
        + "\"display\":\"Lactate [Moles/volume] in Blood\"}],\"operator\":\">=\",\"value\":2}",
        Logic.class);
    lactate(TIME, 1.0);
    assertFalse(condition.test(person, TIME));

    lactate(TIME + 1L, 3.0);

    assertTrue("The clinical condition must use the repeat result",
        condition.test(person, TIME + 1L));
  }

  @Test
  public void otherObservationCodesDoNotHideRepeatedMeasurement() {
    lactate(TIME, 1.0);
    person.record.observation(TIME + 1L, "other-code", 4.0);
    Observation repeat = lactate(TIME + 2L, 3.0);
    person.record.observation(TIME + 3L, "other-code", 5.0);

    assertSame(repeat, person.record.getLatestObservation(LACTATE));
  }

  @Test
  public void fallsBackToLatestMatchingMeasurementInEarlierEncounter() {
    lactate(TIME, 1.0);
    Observation repeat = lactate(TIME + 1L, 3.0);
    encounter.stop = TIME + 2L;
    person.record.encounterStart(TIME + 3L, EncounterType.WELLNESS);
    person.record.observation(TIME + 3L, "other-code", 4.0);

    assertSame(repeat, person.record.getLatestObservation(LACTATE));
  }

  @Test
  public void newerEncounterStillTakesPriority() {
    lactate(TIME, 1.0);
    encounter.stop = TIME + 1L;
    person.record.encounterStart(TIME + 2L, EncounterType.WELLNESS);
    Observation latest = lactate(TIME + 2L, 3.0);

    assertSame(latest, person.record.getLatestObservation(LACTATE));
  }

  @Test
  public void sameTimeMeasurementsPreferLastRecorded() {
    lactate(TIME, 1.0);
    Observation repeat = lactate(TIME, 3.0);

    assertSame(repeat, person.record.getLatestObservation(LACTATE));
  }

  @Test
  public void encounterFindObservationStillReturnsFirstMatch() {
    Observation first = lactate(TIME, 1.0);
    lactate(TIME + 1L, 3.0);

    assertSame(first, encounter.findObservation(LACTATE));
  }

  @Test
  public void missingObservationReturnsNull() {
    assertNull(person.record.getLatestObservation(LACTATE));
    person.record.observation(TIME, "other-code", 4.0);

    assertNull(person.record.getLatestObservation(LACTATE));
  }
}
