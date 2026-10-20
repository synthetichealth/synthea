package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Allergy;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

public class ClinicalNoteActiveRecordsTest {

  private static final long DAY = TimeUnit.DAYS.toMillis(1);
  private static final long NOTE_TIME = 20 * DAY;
  private static final String[] ENTRY_TYPES = {"allergy", "condition", "medication", "procedure"};
  private static final String[] ACTIVE_ATTRIBUTES = {"ehr_activeAllergies", "ehr_activeConditions",
      "ehr_activeMedications", "ehr_activeProcedures"};

  private Person person;
  private Encounter previousEncounter;
  private Encounter noteEncounter;

  /** Set up the minimum demographics and coverage required by the real note template. */
  @Before
  public void setup() {
    person = new Person(1L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    PayerManager.loadNoInsurance();
    person.coverage.setPlanToNoInsurance(0L);
    previousEncounter = person.record.encounterStart(NOTE_TIME - 10 * DAY, EncounterType.WELLNESS);
    previousEncounter.stop = NOTE_TIME - 9 * DAY;
    noteEncounter = person.record.encounterStart(NOTE_TIME, EncounterType.WELLNESS);
  }

  @Test
  public void includesOpenEndedRecords() {
    addEntries(previousEncounter, "ongoing", previousEncounter.start, 0L);

    String note = ClinicalNoteExporter.export(person, noteEncounter);

    assertActiveRecords("ongoing");
    assertRenderedHistory(note, "ongoing", true);
  }

  @Test
  public void excludesRecordsEndedAtOrBeforeNoteTime() {
    addEntries(previousEncounter, "ended", previousEncounter.start, NOTE_TIME - 1L);
    addEntries(previousEncounter, "boundary", previousEncounter.start, NOTE_TIME);

    String note = ClinicalNoteExporter.export(person, noteEncounter);

    assertActiveRecords();
    assertRenderedHistory(note, "ended", false);
    assertRenderedHistory(note, "boundary", false);
  }

  @Test
  public void includesRecordsEndingAfterNoteTime() {
    addEntries(previousEncounter, "later", previousEncounter.start, NOTE_TIME + 1L);

    String note = ClinicalNoteExporter.export(person, noteEncounter);

    assertActiveRecords("later");
    assertRenderedHistory(note, "later", true);
  }

  @Test
  public void excludesRecordsStartingAfterNoteTime() {
    addEntries(previousEncounter, "future", NOTE_TIME + 1L, NOTE_TIME + DAY);

    String note = ClinicalNoteExporter.export(person, noteEncounter);

    assertActiveRecords();
    assertRenderedHistory(note, "future", false);
  }

  @Test
  public void includesRecordsStartingAtNoteTime() {
    addEntries(previousEncounter, "starting", NOTE_TIME, NOTE_TIME + DAY);

    String note = ClinicalNoteExporter.export(person, noteEncounter);

    assertActiveRecords("starting");
    assertRenderedHistory(note, "starting", true);
  }

  @Test
  public void overlappingEncounterDoesNotHideSubsequentHistory() {
    previousEncounter.stop = NOTE_TIME + DAY;
    addEntries(previousEncounter, "overlap", previousEncounter.start, 0L);
    Encounter laterEncounter = person.record.new Encounter(NOTE_TIME - 5 * DAY,
        EncounterType.WELLNESS.toString());
    laterEncounter.stop = NOTE_TIME - 4 * DAY;
    person.record.encounters.add(1, laterEncounter);
    addEntries(laterEncounter, "intervening", laterEncounter.start, 0L);
    addEntries(noteEncounter, "current", NOTE_TIME, 0L);
    Encounter futureEncounter = person.record.encounterStart(NOTE_TIME + DAY,
        EncounterType.WELLNESS);
    addEntries(futureEncounter, "future", futureEncounter.start, 0L);

    String note = ClinicalNoteExporter.export(person, noteEncounter);

    assertActiveRecords("overlap", "intervening");
    assertRenderedHistory(note, "overlap", true);
    assertRenderedHistory(note, "intervening", true);
    assertRenderedHistory(note, "future", false);
    assertTrue("Current-encounter assessment is still rendered",
        note.contains("current condition"));
  }

  private void addEntries(Encounter encounter, String prefix, long start, long stop) {
    Allergy allergy = person.record.new Allergy(start, prefix + " allergy");
    encounter.allergies.add(allergy);
    Entry condition = person.record.new Entry(start, prefix + " condition");
    encounter.conditions.add(condition);
    Medication medication = person.record.new Medication(start, prefix + " medication");
    encounter.medications.add(medication);
    Procedure procedure = person.record.new Procedure(start, prefix + " procedure");
    encounter.procedures.add(procedure);
    for (Entry entry : Arrays.asList(allergy, condition, medication, procedure)) {
      entry.stop = stop;
      entry.codes.add(new Code("test", entry.type, entry.type));
    }
  }

  private void assertActiveRecords(String... prefixes) {
    for (int i = 0; i < ACTIVE_ATTRIBUTES.length; i++) {
      Set<String> expected = new HashSet<>();
      for (String prefix : prefixes) {
        expected.add(prefix + " " + ENTRY_TYPES[i]);
      }
      assertEquals(ACTIVE_ATTRIBUTES[i], expected, person.attributes.get(ACTIVE_ATTRIBUTES[i]));
    }
  }

  private void assertRenderedHistory(String note, String prefix, boolean active) {
    // Previous procedures are made available to templates but not displayed by note.ftl.
    for (int i = 0; i < ENTRY_TYPES.length - 1; i++) {
      String display = prefix + " " + ENTRY_TYPES[i];
      if (active) {
        assertTrue(display + " should be in the note", note.contains(display));
      } else {
        assertFalse(display + " should not be in the note", note.contains(display));
      }
    }
  }
}
