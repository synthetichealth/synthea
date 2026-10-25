package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

public class TextExportRecordOrderTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private final Map<String, String> previousSettings = new LinkedHashMap<>();
  private static final long DAY = 86400000L;
  private static final long END = 4 * DAY;

  /** Isolate output configuration from the other exporters' tests. */
  @Before
  public void setUp() throws Exception {
    for (String key : new String[] {"exporter.baseDirectory", "exporter.use_uuid_filenames",
        "exporter.subfolders_by_id_substring"}) {
      previousSettings.put(key, Config.get(key));
    }
    Config.set("exporter.use_uuid_filenames", "true");
    Config.set("exporter.subfolders_by_id_substring", "false");
    freshOutput();
    PayerManager.loadNoInsurance();
  }

  /** Restore shared exporter configuration. */
  @After
  public void tearDown() {
    for (Map.Entry<String, String> setting : previousSettings.entrySet()) {
      if (setting.getValue() == null) {
        Config.remove(setting.getKey());
      } else {
        Config.set(setting.getKey(), setting.getValue());
      }
    }
  }

  @Test
  public void exportAllKeepsRecordedEncounterOrder() throws Exception {
    Person person = person(3);
    List<Encounter> original = new ArrayList<>(person.record.encounters);
    TextExporter.exportAll(person, "", END);
    assertEquals(original, person.record.encounters);
    String output = read("text", person, "");
    assertTrue(output.indexOf("Encounter 2") < output.indexOf("Encounter 1"));
    assertTrue(output.indexOf("Encounter 1") < output.indexOf("Encounter 0"));
  }

  @Test
  public void exportEncounterKeepsRecordedEncounterOrder() throws Exception {
    Person person = person(3);
    List<Encounter> original = new ArrayList<>(person.record.encounters);
    TextExporter.exportEncounter(person, END);
    assertEquals(original, person.record.encounters);
    assertTrue(read("text_encounters", person, "1").contains("Encounter 2"));
  }

  @Test
  public void repeatedWholeRecordExportsAreIdentical() throws Exception {
    Person person = person(3);
    TextExporter.exportAll(person, "first", END);
    String first = read("text", person, "first");
    TextExporter.exportAll(person, "second", END);
    assertEquals(first, read("text", person, "second"));
  }

  @Test
  public void repeatedPerEncounterExportsAreIdentical() throws Exception {
    Person person = person(3);
    TextExporter.exportEncounter(person, END);
    List<String> first = encounterFiles(person);
    freshOutput();
    TextExporter.exportEncounter(person, END);
    assertEquals(first, encounterFiles(person));
  }

  @Test
  public void wholeRecordExportKeepsLatestObservation() throws Exception {
    Person person = person(3);
    Observation latest = person.record.getLatestObservation("repeated-observation");
    assertEquals(3.0, (Double) latest.value, 0.0);
    TextExporter.exportAll(person, "", END);
    assertSame(latest, person.record.getLatestObservation("repeated-observation"));
  }

  @Test
  public void perEncounterExportKeepsLatestObservation() throws Exception {
    Person person = person(3);
    Observation latest = person.record.getLatestObservation("repeated-observation");
    TextExporter.exportEncounter(person, END);
    assertSame(latest, person.record.getLatestObservation("repeated-observation"));
  }

  @Test
  public void wholeRecordThenPerEncounterStillStartsWithNewestEncounter() throws Exception {
    Person person = person(3);
    TextExporter.exportAll(person, "", END);
    TextExporter.exportEncounter(person, END);
    assertTrue(read("text_encounters", person, "1").contains("Encounter 2"));
  }

  @Test
  public void singleEncounterKeepsBothExportFormats() throws Exception {
    Person person = person(1);
    Encounter encounter = person.record.encounters.get(0);
    TextExporter.exportAll(person, "", END);
    TextExporter.exportEncounter(person, END);
    assertSame(encounter, person.record.encounters.get(0));
    assertTrue(read("text", person, "").contains("Encounter 0"));
    assertTrue(read("text_encounters", person, "1").contains("Encounter 0"));
  }

  private void freshOutput() throws Exception {
    Config.set("exporter.baseDirectory", folder.newFolder().getAbsolutePath());
  }

  private List<String> encounterFiles(Person person) throws Exception {
    List<String> files = new ArrayList<>();
    for (int i = 1; i <= person.record.encounters.size(); i++) {
      files.add(read("text_encounters", person, Integer.toString(i)));
    }
    return files;
  }

  private String read(String directory, Person person, String tag) throws Exception {
    Path path = Exporter.getOutputFolder(directory, person).toPath()
        .resolve(Exporter.filename(person, tag, "txt"));
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private Person person(int encounters) {
    Person person = new Person(0L);
    person.attributes.put(Person.ID, "text-order-patient");
    person.attributes.put(Person.NAME, "Test Person");
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.attributes.put(Person.GENDER, "F");
    person.coverage.setPlanToNoInsurance(0L);
    for (int i = 0; i < encounters; i++) {
      long time = (i + 1) * DAY;
      Encounter encounter = person.record.encounterStart(time, EncounterType.AMBULATORY);
      encounter.codes.add(new Code("SNOMED-CT", "encounter-" + i, "Encounter " + i));
      Observation observation = person.record.observation(time, "repeated-observation", i + 1.0);
      observation.codes.add(new Code("LOINC", "repeated-observation", "Test observation"));
      person.record.encounterEnd(time + 1000, EncounterType.AMBULATORY);
    }
    return person;
  }
}
