package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

public class CSVExporterPanelObservationTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private CSVExporter exporter;
  private Method exportObservation;
  private CSVFileManager fileManager;
  private File exportDirectory;

  /** Configure the real CSV exporter to write to a fresh temporary directory. */
  @Before
  public void setUp() throws Exception {
    TestHelper.loadTestProperties();
    exportDirectory = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", exportDirectory.toString());
    Config.set("exporter.csv.folder_per_run", "false");
    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "");
    exporter = CSVExporter.getInstance();
    exporter.init();
    exportObservation = CSVExporter.class.getDeclaredMethod("exportObservation", String.class,
        String.class, HealthRecord.Observation.class);
    exportObservation.setAccessible(true);
    Field fileManagerField = CSVExporter.class.getDeclaredField("fileManager");
    fileManagerField.setAccessible(true);
    fileManager = (CSVFileManager) fileManagerField.get(exporter);
  }

  @Test
  public void exportsPanelAndComponents() throws Exception {
    Person person = new Person(1L);
    HealthRecord panel = person.record;
    HealthRecord.Observation parent = panel.new Observation(1000L, "PANEL", null);
    parent.category = "laboratory";
    parent.unit = "panel-unit";
    parent.codes.add(new HealthRecord.Code("LOINC", "PANEL", "Panel"));
    parent.observations.add(observation(person, "COMPONENT_A", "Component A", 1.0));
    parent.observations.add(observation(person, "COMPONENT_B", "Component B", 2.0));

    write(person, parent);
    List<Map<String, String>> rows = rows();
    assertEquals(3, rows.size());
    assertEquals("PANEL", rows.get(0).get("CODE"));
    assertEquals("Panel", rows.get(0).get("DESCRIPTION"));
    assertEquals("laboratory", rows.get(0).get("CATEGORY"));
    assertEquals("1970-01-01T00:00:01Z", rows.get(0).get("DATE"));
    assertEquals("patient-1", rows.get(0).get("PATIENT"));
    assertEquals("encounter-1", rows.get(0).get("ENCOUNTER"));
    assertEquals("", rows.get(0).get("VALUE"));
    assertEquals("", rows.get(0).get("UNITS"));
    assertEquals("", rows.get(0).get("TYPE"));
    assertEquals("COMPONENT_A", rows.get(1).get("CODE"));
    assertEquals("COMPONENT_B", rows.get(2).get("CODE"));
  }

  @Test
  public void exportsScalarObservation() throws Exception {
    Person person = new Person(2L);
    HealthRecord.Observation scalar = observation(person, "SCALAR", "Scalar", 42.0);
    scalar.unit = "mg/dL";
    scalar.observations.add(observation(person, "CHILD", "Child", 7.0));

    write(person, scalar);
    List<Map<String, String>> rows = rows();
    assertEquals(1, rows.size());
    assertEquals("SCALAR", rows.get(0).get("CODE"));
    assertEquals("42.0", rows.get(0).get("VALUE"));
    assertEquals("mg/dL", rows.get(0).get("UNITS"));
    assertEquals("numeric", rows.get(0).get("TYPE"));
  }

  @Test
  public void skipsUncodedNullObservationButExportsItsChildren() throws Exception {
    Person person = new Person(3L);
    HealthRecord.Observation container = person.record.new Observation(1000L, "CONTAINER", null);
    container.observations.add(observation(person, "CHILD", "Child", "text"));
    HealthRecord.Observation empty = person.record.new Observation(1000L, "EMPTY", null);

    write(person, container);
    write(person, empty);
    List<Map<String, String>> rows = rows();
    assertEquals(1, rows.size());
    assertEquals("CHILD", rows.get(0).get("CODE"));
    assertTrue(rows.get(0).get("VALUE").contains("text"));
  }

  @Test
  public void exportsNestedPanelsBeforeTheirDescendants() throws Exception {
    Person person = new Person(4L);
    HealthRecord.Observation outer = observation(person, "OUTER", "Outer", null);
    HealthRecord.Observation container = person.record.new Observation(1000L, "CONTAINER", null);
    HealthRecord.Observation inner = observation(person, "INNER", "Inner", null);
    inner.observations.add(observation(person, "LEAF", "Leaf", 3.0));
    container.observations.add(inner);
    outer.observations.add(container);
    outer.observations.add(observation(person, "SIBLING", "Sibling", 4.0));

    write(person, outer);
    List<Map<String, String>> rows = rows();
    assertEquals(Arrays.asList("OUTER", "INNER", "LEAF", "SIBLING"),
        rows.stream().map(row -> row.get("CODE")).collect(Collectors.toList()));
    for (Map<String, String> row : rows) {
      assertEquals("patient-1", row.get("PATIENT"));
      assertEquals("encounter-1", row.get("ENCOUNTER"));
    }
  }

  @Test
  public void preservesCodedAndTextScalarSemanticsWithChildren() throws Exception {
    Person person = new Person(5L);
    HealthRecord.Observation coded = observation(person, "CODED", "Coded",
        new HealthRecord.Code("SNOMED-CT", "POSITIVE", "Positive"));
    coded.observations.add(observation(person, "HIDDEN_CODED_CHILD", "Hidden", 1.0));
    HealthRecord.Observation text = observation(person, "TEXT", "Text", "result");
    text.observations.add(observation(person, "HIDDEN_TEXT_CHILD", "Hidden", 2.0));

    write(person, coded);
    write(person, text);
    List<Map<String, String>> rows = rows();
    assertEquals(2, rows.size());
    assertEquals("CODED", rows.get(0).get("CODE"));
    assertEquals("Positive", rows.get(0).get("VALUE"));
    assertEquals("text", rows.get(0).get("TYPE"));
    assertEquals("TEXT", rows.get(1).get("CODE"));
    assertEquals("result", rows.get(1).get("VALUE"));
    assertEquals("text", rows.get(1).get("TYPE"));
  }

  @Test
  public void skipsCodedNullLeavesAndNullChildLists() throws Exception {
    Person person = new Person(6L);
    HealthRecord.Observation empty = observation(person, "EMPTY", "Empty", null);
    HealthRecord.Observation absent = observation(person, "ABSENT", "Absent", null);
    absent.observations = null;

    write(person, empty);
    write(person, absent);
    write(person, observation(person, "VISIBLE", "Visible", 5.0));
    List<Map<String, String>> rows = rows();
    assertEquals(1, rows.size());
    assertEquals("VISIBLE", rows.get(0).get("CODE"));
  }

  private HealthRecord.Observation observation(Person person, String code, String display,
      Object value) {
    HealthRecord.Observation observation = person.record.new Observation(1000L, code, value);
    observation.codes.add(new HealthRecord.Code("LOINC", code, display));
    return observation;
  }

  private void write(Person person, HealthRecord.Observation observation) throws Exception {
    exportObservation.invoke(exporter, "patient-1", "encounter-1", observation);
  }

  private List<Map<String, String>> rows() throws Exception {
    fileManager.flushWriter(CSVConstants.OBSERVATION_KEY);
    File csv = new File(exportDirectory, "csv/observations.csv");
    String csvData = new String(Files.readAllBytes(csv.toPath()), StandardCharsets.UTF_8);
    assertTrue(SimpleCSV.isValid(csvData));
    return (List<Map<String, String>>) (List<?>) SimpleCSV.parse(csvData);
  }
}
