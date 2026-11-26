package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class ClinicalNoteExporterTest {

  /**
   * Load the test configuration and the no insurance plan used by the notes.
   * @throws Exception on configuration loading error.
   */
  @BeforeClass
  public static void setup() throws Exception {
    TestHelper.loadTestProperties();
    PayerManager.clear();
    PayerManager.loadNoInsurance();
  }

  /**
   * Create a person born at time 0. The lifestyle attributes set by the tests
   * are the values left at the end of the simulation.
   */
  private static Person createPerson() {
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.coverage.setPlanToNoInsurance(0L);
    return person;
  }

  /**
   * Export the note of an encounter at the given age and return the first line
   * of its Social History section, which covers smoking and drinking.
   */
  private static String socialHistoryAtAge(Person person, int age) {
    long time = Utilities.convertTime("years", age) + Utilities.convertTime("days", 30);
    // move the enrollment period past the encounter, so that a plan covers it
    boolean newPeriod;
    do {
      newPeriod = person.coverage.newEnrollmentPeriod(time);
    } while (newPeriod);
    person.coverage.setPlanToNoInsurance(time);
    Encounter encounter = person.record.encounterStart(time, EncounterType.WELLNESS);

    String[] lines = ClinicalNoteExporter.export(person, encounter).split("\\R");
    for (int i = 0; i < lines.length - 1; i++) {
      if (lines[i].equals("# Social History")) {
        return lines[i + 1].trim();
      }
    }
    fail("Note has no Social History section");
    return null;
  }

  @Test
  public void testSmokingBeforeSmokingStarted() {
    Person person = createPerson();
    person.attributes.put(Person.SMOKER, false);
    person.attributes.put(LifecycleModule.QUIT_SMOKING_AGE, 40);
    assertEquals("Patient has never smoked.", socialHistoryAtAge(person, 1));
  }

  @Test
  public void testSmokingBeforeQuitting() {
    Person person = createPerson();
    person.attributes.put(Person.SMOKER, false);
    person.attributes.put(LifecycleModule.QUIT_SMOKING_AGE, 40);
    assertEquals("Patient is an active smoker.", socialHistoryAtAge(person, 30));
  }

  @Test
  public void testSmokingAfterQuitting() {
    Person person = createPerson();
    person.attributes.put(Person.SMOKER, false);
    person.attributes.put(LifecycleModule.QUIT_SMOKING_AGE, 40);
    assertEquals("Patient quit smoking at age 40.", socialHistoryAtAge(person, 50));
  }

  @Test
  public void testActiveSmokerAtAgeSixteen() {
    Person person = createPerson();
    person.attributes.put(Person.SMOKER, true);
    assertEquals("Patient is an active smoker.", socialHistoryAtAge(person, 16));
  }

  @Test
  public void testAlcoholismBeforeItStarted() {
    Person person = createPerson();
    person.attributes.put(Person.ALCOHOLIC, true);
    assertEquals("Patient has never smoked.", socialHistoryAtAge(person, 1));
  }

  @Test
  public void testAlcoholismAfterItStarted() {
    Person person = createPerson();
    person.attributes.put(Person.ALCOHOLIC, true);
    assertEquals("Patient has never smoked and is an alcoholic.",
        socialHistoryAtAge(person, 30));
  }

  @Test
  public void testAlcoholismBeforeQuitting() {
    Person person = createPerson();
    person.attributes.put(Person.ALCOHOLIC, false);
    person.attributes.put(LifecycleModule.QUIT_ALCOHOLISM_AGE, 35);
    assertEquals("Patient has never smoked and is an alcoholic.",
        socialHistoryAtAge(person, 30));
  }

  @Test
  public void testAlcoholismAfterQuitting() {
    Person person = createPerson();
    person.attributes.put(Person.ALCOHOLIC, false);
    person.attributes.put(LifecycleModule.QUIT_ALCOHOLISM_AGE, 35);
    assertEquals("Patient has never smoked.", socialHistoryAtAge(person, 40));
  }
}
