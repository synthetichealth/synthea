package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

/** Tests the optional ICD-10-CM columns in conditions.csv. */
public class CodeTranslationTest {

  private Map<String, String> originalConfig;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  /** Configure an isolated CSV output directory for each test. */
  @Before
  public void setUp() throws Exception {
    originalConfig = new HashMap<>();
    for (String key : Config.allPropertyNames()) {
      originalConfig.put(key, Config.get(key));
    }
    TestHelper.exportOff();
    TestHelper.loadTestProperties();
    Config.set("exporter.baseDirectory", tempFolder.getRoot().toString());
    Config.set("exporter.csv.folder_per_run", "false");
    Config.set("exporter.csv.append_mode", "false");
    Config.set("exporter.csv.max_lines_per_file", "");
    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "");
    Config.remove("exporter.code_map.icd10-cm");
    Exporter.loadCodeMappers();
  }

  /** Restore the configuration that was in place before this test. */
  @After
  public void tearDown() {
    for (String key : Config.allPropertyNames()) {
      if (!originalConfig.containsKey(key)) {
        Config.remove(key);
      }
    }
    originalConfig.forEach(Config::set);
    Exporter.loadCodeMappers();
  }

  @Test
  public void testMappedAndUnmappedConditions() throws Exception {
    Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
    Exporter.loadCodeMappers();

    Person person = new Person(42L);
    CSVFileManager files = new CSVFileManager();
    writeCondition(files, person, "38341003", "Hypertension");
    writeCondition(files, person, "99999999", "Unmapped condition");
    files.flushWriter(CSVConstants.CONDITION_KEY);

    String csv = readConditions();
    assertTrue(SimpleCSV.isValid(csv));
    assertTrue(csv.startsWith(CSVConstants.CONDITION_HEADER_LINE_WITH_ICD10));
    List<? extends Map<String, String>> rows = SimpleCSV.parse(csv);
    assertEquals(2, rows.size());
    assertEquals("I10", rows.get(0).get("ICD10_CODE"));
    assertEquals("Essential (primary) hypertension", rows.get(0).get("ICD10_DESCRIPTION"));
    assertEquals("", rows.get(1).get("ICD10_CODE"));
    assertEquals("", rows.get(1).get("ICD10_DESCRIPTION"));
  }

  @Test
  public void testConditionsWithoutMapKeepOriginalSchema() throws Exception {
    Config.remove("exporter.code_map.icd10-cm");
    Exporter.loadCodeMappers();

    Person person = new Person(42L);
    CSVFileManager files = new CSVFileManager();
    writeCondition(files, person, "38341003", "Hypertension");
    files.flushWriter(CSVConstants.CONDITION_KEY);

    String csv = readConditions();
    assertTrue(SimpleCSV.isValid(csv));
    assertTrue(csv.startsWith(CSVConstants.CONDITION_HEADER_LINE));
    assertFalse(csv.contains("ICD10_CODE"));
    assertEquals(7, SimpleCSV.parse(csv).get(0).size());
  }

  @Test
  public void testEmptyCodeMapSettingIsIgnored() {
    Config.set("exporter.code_map.icd10-cm", "");
    Exporter.loadCodeMappers();
    assertNull(Exporter.getCodeMapper("ICD10-CM"));
  }

  @Test
  public void testCodeMapKeyIsLocaleIndependent() {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
      Exporter.loadCodeMappers();
      assertTrue(Exporter.getCodeMapper(Exporter.ICD10_CM_CODE_SYSTEM) != null);
    } finally {
      Locale.setDefault(originalLocale);
    }
  }

  @Test
  public void testAppendRejectsDifferentConditionSchema() throws Exception {
    Config.remove("exporter.code_map.icd10-cm");
    Exporter.loadCodeMappers();
    Person person = new Person(42L);
    CSVFileManager originalFiles = new CSVFileManager();
    writeCondition(originalFiles, person, "38341003", "Hypertension");
    originalFiles.flushWriter(CSVConstants.CONDITION_KEY);

    Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
    Exporter.loadCodeMappers();
    Config.set("exporter.csv.append_mode", "true");
    assertThrows(IllegalStateException.class, CSVFileManager::new);
    assertEquals(1, SimpleCSV.parse(readConditions()).size());
  }

  @Test
  public void testAppendToHeaderlessConditionsFile() throws Exception {
    // earlier versions never wrote a header in single-file append mode
    File file = tempFolder.getRoot().toPath().resolve("csv/conditions.csv").toFile();
    file.getParentFile().mkdirs();
    Files.writeString(file.toPath(),
        "2020-01-01,,patient,encounter,http://snomed.info/sct,38341003,Hypertension"
        + System.lineSeparator());
    Config.set("exporter.csv.append_mode", "true");
    Person person = new Person(42L);

    Config.remove("exporter.code_map.icd10-cm");
    Exporter.loadCodeMappers();
    CSVFileManager sameSchemaFiles = new CSVFileManager();
    writeCondition(sameSchemaFiles, person, "38341003", "Hypertension");
    sameSchemaFiles.flushWriter(CSVConstants.CONDITION_KEY);
    assertEquals(2, readConditions().split(System.lineSeparator()).length);

    Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
    Exporter.loadCodeMappers();
    assertThrows(IllegalStateException.class, CSVFileManager::new);
  }

  @Test
  public void testAppendChecksEveryNumberedConditionFileBeforeWriting() throws Exception {
    File directory = tempFolder.getRoot().toPath().resolve("csv").toFile();
    directory.mkdirs();
    Files.writeString(new File(directory, "conditions-1.csv").toPath(),
        CSVConstants.CONDITION_HEADER_LINE);
    Files.writeString(new File(directory, "conditions-2.csv").toPath(),
        CSVConstants.CONDITION_HEADER_LINE_WITH_ICD10);
    Config.set("exporter.csv.max_lines_per_file", "2");
    Config.set("exporter.csv.append_mode", "true");
    Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
    Exporter.loadCodeMappers();

    assertThrows(IllegalStateException.class, CSVFileManager::new);
    assertEquals(CSVConstants.CONDITION_HEADER_LINE,
        Files.readString(new File(directory, "conditions-1.csv").toPath()));
  }

  @Test
  public void testFailedNumberedFileCheckCanBeRetried() throws Exception {
    Config.set("exporter.csv.max_lines_per_file", "2");
    Config.set("exporter.csv.append_mode", "true");
    Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
    Exporter.loadCodeMappers();
    CSVFileManager files = new CSVFileManager();
    File file = tempFolder.getRoot().toPath().resolve("csv/conditions-1.csv").toFile();
    Files.writeString(file.toPath(), CSVConstants.CONDITION_HEADER_LINE);

    Person person = new Person(42L);
    assertThrows(IOException.class,
        () -> writeCondition(files, person, "38341003", "Hypertension"));

    Files.writeString(file.toPath(), CSVConstants.CONDITION_HEADER_LINE_WITH_ICD10);
    writeCondition(files, person, "38341003", "Hypertension");
    files.flushWriter(CSVConstants.CONDITION_KEY);
    assertEquals(1, SimpleCSV.parse(Files.readString(file.toPath())).size());
  }

  @Test
  public void testEmptyConditionsFileGetsHeaderInAppendMode() throws Exception {
    File file = tempFolder.getRoot().toPath().resolve("csv/conditions.csv").toFile();
    file.getParentFile().mkdirs();
    Files.createFile(file.toPath());
    Config.set("exporter.csv.append_mode", "true");
    Config.set("exporter.code_map.icd10-cm", "export/test_icd10cm_code_map.json");
    Exporter.loadCodeMappers();

    CSVFileManager files = new CSVFileManager();
    writeCondition(files, new Person(42L), "38341003", "Hypertension");
    files.flushWriter(CSVConstants.CONDITION_KEY);
    assertTrue(readConditions().startsWith(CSVConstants.CONDITION_HEADER_LINE_WITH_ICD10));
  }

  @Test
  public void testOtherCsvFilesKeepAppendModeHeaderBehavior() throws Exception {
    Config.set("exporter.csv.append_mode", "true");
    CSVFileManager files = new CSVFileManager();
    files.writeResourceLine("patient-id" + System.lineSeparator(), CSVConstants.PATIENT_KEY);
    files.flushWriter(CSVConstants.PATIENT_KEY);

    File file = tempFolder.getRoot().toPath().resolve("csv/patients.csv").toFile();
    assertEquals("patient-id" + System.lineSeparator(), Files.readString(file.toPath()));
  }

  @Test
  public void testAppendWithoutMapKeepsLegacyConditionHeaderBehavior() throws Exception {
    Config.set("exporter.csv.append_mode", "true");
    CSVFileManager files = new CSVFileManager();
    writeCondition(files, new Person(42L), "38341003", "Hypertension");
    files.flushWriter(CSVConstants.CONDITION_KEY);

    assertFalse(readConditions().startsWith(CSVConstants.CONDITION_HEADER_LINE));
  }

  private void writeCondition(CSVFileManager files, Person person, String code, String display)
      throws Exception {
    Entry condition = person.record.new Entry(1000L, display);
    condition.codes.add(new Code("SNOMED-CT", code, display));
    files.writeResourceLine(CSVExporter.conditionLine("patient", "encounter", condition, person),
        CSVConstants.CONDITION_KEY);
  }

  private String readConditions() throws Exception {
    File file = tempFolder.getRoot().toPath().resolve("csv/conditions.csv").toFile();
    assertTrue(file.exists());
    return Files.readString(file.toPath());
  }
}
