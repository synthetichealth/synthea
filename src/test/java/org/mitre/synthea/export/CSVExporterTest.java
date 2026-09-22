package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.geom.Point2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.export.Exporter.ExporterRuntimeOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.healthinsurance.PlanRecord;

public class CSVExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @ClassRule
  public static TemporaryFolder tempFolder = new TemporaryFolder();

  private static File exportDir;

  private static final int NUMBER_OF_FILES = 19;

  /**
   * Global setup for export tests.
   * @throws Exception if something goes wrong
   */
  @Before
  public void setUpExportDir() throws Exception {
    TestHelper.exportOff();
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.csv.export", "true");
    Config.set("exporter.csv.folder_per_run", "false");

    exportDir = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", exportDir.toString());

    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "");
    Config.set("exporter.csv.max_lines_per_file", "");
    Config.set("exporter.csv.append_mode", "false");
    Config.set("exporter.csv.file_number_digits", "");
    Config.set("exporter.csv.include_quality_of_life_observations", "true");
  }

  @Test
  public void testDeferredCSVExport() throws Exception {
    CSVExporter.getInstance().init();

    int numberOfPeople = 10;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("csv").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    List<String> expectedResourceFiles = new ArrayList(CSVConstants.HEADER_LINE_MAP.keySet());

    int count = 0;
    for (File csvFile : expectedExportFolder.listFiles()) {
      String filename = csvFile.getName();

      if (!filename.endsWith(".csv")) {
        continue;
      }

      String csvData = new String(Files.readAllBytes(csvFile.toPath()));

      // the CSV exporter doesn't use the SimpleCSV class to write the data,
      // so we can use it here for a level of validation
      SimpleCSV.parse(csvData);
      assertTrue("CSV Validation: " + csvFile.getName(), SimpleCSV.isValid(csvData));

      int lastSeparatorIndex = filename.lastIndexOf(File.pathSeparator);
      String resourceKey = filename.substring(lastSeparatorIndex + 1, filename.length() - 4);

      expectedResourceFiles.remove(resourceKey);

      count++;
    }

    // patient may not have allergies, so don't fail if allergies isn't generated
    if (expectedResourceFiles.size() == 1 && expectedResourceFiles.contains("allergies")) {
      count++;
    }

    assertEquals("Expected " + NUMBER_OF_FILES + " CSV files in the output directory, but found: "
                 + count + "\nMissing resource files:\n " + String.join(", ", expectedResourceFiles)
                 + "\n", NUMBER_OF_FILES, count);
  }

  @Test
  public void testCSVExportIncludes() throws Exception {
    Config.set("exporter.csv.included_files", "patients.csv,medications.csv,procedures.csv");
    CSVExporter.getInstance().init();

    int numberOfPeople = 10;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("csv").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    boolean foundPatients = false;
    boolean foundMedications = false;
    boolean foundProcedures = false;

    int count = 0;
    for (File csvFile : expectedExportFolder.listFiles()) {
      if (!csvFile.getName().endsWith(".csv")) {
        continue;
      }

      switch (csvFile.getName()) {
        case "patients.csv":
          foundPatients = true;
          break;
        case "medications.csv":
          foundMedications = true;
          break;
        case "procedures.csv":
          foundProcedures = true;
          break;
        default:
          // do nothing
      }

      String csvData = new String(Files.readAllBytes(csvFile.toPath()));

      // the CSV exporter doesn't use the SimpleCSV class to write the data,
      // so we can use it here for a level of validation
      SimpleCSV.parse(csvData);
      assertTrue(SimpleCSV.isValid(csvData));

      count++;
    }

    assertEquals("Expected 3 CSV files in the output directory, found " + count, 3, count);
    assertTrue("patients.csv file missing but should have been included", foundPatients);
    assertTrue("medications.csv file missing but should have been included", foundMedications);
    assertTrue("procedures.csv file missing but should have been included", foundProcedures);
  }

  @Test
  public void testCSVExportExcludes() throws Exception {
    Config.set("exporter.csv.excluded_files", "patients.csv, medications, payers, providers,"
        + "patient_expenses.csv");
    CSVExporter.getInstance().init();

    int numberOfPeople = 10;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("csv").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    boolean foundPatients = false;
    boolean foundMedications = false;
    boolean foundPayers = false;
    boolean foundProviders = false;
    boolean foundExpenses = false;

    List<String> expectedResourceFiles = new ArrayList(CSVConstants.HEADER_LINE_MAP.keySet());
    expectedResourceFiles.remove("patients");
    expectedResourceFiles.remove("medications");
    expectedResourceFiles.remove("payers");
    expectedResourceFiles.remove("providers");
    expectedResourceFiles.remove("patient_expenses");

    int count = 0;
    for (File csvFile : expectedExportFolder.listFiles()) {
      String filename = csvFile.getName();

      if (!filename.endsWith(".csv")) {
        continue;
      }

      switch (csvFile.getName()) {
        case "patients.csv":
          foundPatients = true;
          break;
        case "medications.csv":
          foundMedications = true;
          break;
        case "payers.csv":
          foundPayers = true;
          break;
        case "providers.csv":
          foundProviders = true;
          break;
        case "patient_expenses.csv":
          foundExpenses = true;
          break;
        default:
          // do nothing
      }

      String csvData = new String(Files.readAllBytes(csvFile.toPath()));

      // the CSV exporter doesn't use the SimpleCSV class to write the data,
      // so we can use it here for a level of validation
      SimpleCSV.parse(csvData);
      assertTrue("CSV validation: " + csvFile.getName(), SimpleCSV.isValid(csvData));

      int lastSeparatorIndex = filename.lastIndexOf(File.pathSeparator);
      String resourceKey = filename.substring(lastSeparatorIndex + 1, filename.length() - 4);

      expectedResourceFiles.remove(resourceKey);

      count++;
    }

    int expected = NUMBER_OF_FILES - 5;
    assertTrue("patients.csv is present but should have been excluded", !foundPatients);
    assertTrue("medications.csv is present but should have been excluded", !foundMedications);
    assertTrue("payers.csv is present but should have been excluded", !foundPayers);
    assertTrue("providers.csv is present but should have been excluded", !foundProviders);
    assertTrue("patient_expoenses.csv is present but should have been excluded", !foundExpenses);

    // patient may not have allergies, so don't fail if allergies isn't generated
    if (expectedResourceFiles.size() == 1 && expectedResourceFiles.contains("allergies")) {
      count++;
    }
    assertEquals("Expected " + expected + " CSV files in the output directory, but found: "
                 + count + "\nMissing resource files:\n " + String.join(", ", expectedResourceFiles)
                 + "\n", expected, count);
  }

  @Test
  public void testClaimTransactionsReferenceExportedPayerTransitions() throws Exception {
    // Keep the entire history so that claims made under insurance plans that ended
    // before 1970 are exported, and only generate people who were born well before then.
    String yearsOfHistory = Config.get("exporter.years_of_history");
    Config.set("exporter.years_of_history", "0");
    Config.set("exporter.csv.included_files", "payer_transitions.csv,claims_transactions.csv");
    try {
      CSVExporter.getInstance().init();

      int numberOfPeople = 10;
      ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
      exportOpts.deferExports = true;
      GeneratorOptions generatorOpts = new GeneratorOptions();
      generatorOpts.population = numberOfPeople;
      generatorOpts.seed = 1725L;
      generatorOpts.clinicianSeed = 1725L;
      generatorOpts.ageSpecified = true;
      generatorOpts.minAge = 60;
      generatorOpts.maxAge = 90;
      Generator generator = new Generator(generatorOpts, exportOpts);
      generator.options.overflow = false;
      for (int i = 0; i < numberOfPeople; i++) {
        generator.generatePerson(i);
      }
      Exporter.runPostCompletionExports(generator, exportOpts);

      File exportFolder = exportDir.toPath().resolve("csv").toFile();
      String transitionsData = new String(Files.readAllBytes(
          exportFolder.toPath().resolve("payer_transitions.csv")));
      String transactionsData = new String(Files.readAllBytes(
          exportFolder.toPath().resolve("claims_transactions.csv")));
      assertTrue("CSV validation: payer_transitions.csv", SimpleCSV.isValid(transitionsData));
      assertTrue("CSV validation: claims_transactions.csv", SimpleCSV.isValid(transactionsData));

      Set<String> memberIds = new HashSet<>();
      for (LinkedHashMap<String, String> row : SimpleCSV.parse(transitionsData)) {
        memberIds.add(row.get("PATIENT") + "|" + row.get("MEMBERID"));
      }

      boolean foundPre1970 = false;
      List<String> unresolved = new ArrayList<>();
      for (LinkedHashMap<String, String> row : SimpleCSV.parse(transactionsData)) {
        String memberId = row.get("PATIENTINSURANCEID");
        if (memberId == null || memberId.isEmpty()) {
          continue;
        }
        String fromDate = row.get("FROMDATE");
        if (fromDate.compareTo("1970") < 0) {
          foundPre1970 = true;
        }
        if (!memberIds.contains(row.get("PATIENTID") + "|" + memberId)) {
          unresolved.add(row.get("PATIENTID") + " " + memberId + " " + fromDate);
        }
      }

      assertTrue("Expected claims transactions dated before 1970", foundPre1970);
      assertTrue("claims_transactions.PATIENTINSURANCEID values with no matching "
          + "payer_transitions.MEMBERID (PATIENTID MEMBERID FROMDATE):\n "
          + String.join("\n ", unresolved), unresolved.isEmpty());
    } finally {
      if (yearsOfHistory == null) {
        Config.remove("exporter.years_of_history");
      } else {
        Config.set("exporter.years_of_history", yearsOfHistory);
      }
    }
  }

  @Test
  public void testCSVExportMultipleFiles() throws Exception {
    Config.set("exporter.csv.included_files", "patients.csv");
    Config.set("exporter.csv.max_lines_per_file", "2");
    CSVExporter.getInstance().init();

    int numberOfPeople = 3;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("csv").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    File patientFile1 = expectedExportFolder.toPath().resolve("patients-1.csv").toFile();
    File patientFile2 = expectedExportFolder.toPath().resolve("patients-2.csv").toFile();
    File patientFile3 = expectedExportFolder.toPath().resolve("patients-3.csv").toFile();

    assertTrue("No patient export file found.", patientFile1.exists());
    assertTrue("No second patient export file found.", patientFile2.exists());
    assertTrue("No third patient export file should not exist.", !patientFile3.exists());

    String patientDataString1 = new String(Files.readAllBytes(patientFile1.toPath()));

    List patientData1 = SimpleCSV.parse(patientDataString1);

    assertTrue("CSV validation: " + patientFile1.getName(), SimpleCSV.isValid(patientDataString1));

    int length1 = patientData1.size();
    assertTrue("Expected two Patients in the first export file, but found " + length1,
               length1 == 2);

    String patientDataString2 = new String(Files.readAllBytes(patientFile2.toPath()));

    List patientData2 = SimpleCSV.parse(patientDataString2);

    assertTrue("CSV validation: " + patientFile2.getName(), SimpleCSV.isValid(patientDataString2));

    int length2 = patientData2.size();
    assertTrue("Expected one Patient in the second export file, but found " + length2,
               length2 == 1);
  }

  @Test
  public void testCSVExportAppendMultipleFiles() throws Exception {
    Config.set("exporter.csv.included_files", "patients.csv");
    Config.set("exporter.csv.max_lines_per_file", "2");
    Config.set("exporter.csv.append_mode", "true");
    CSVExporter.getInstance().init();

    // Export 3 patients
    int numberOfPeople = 3;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    Exporter.runPostCompletionExports(generator, exportOpts);

    // Export 2 patients
    CSVExporter.getInstance().init();
    numberOfPeople = 2;
    exportOpts.deferExports = true;
    generatorOpts.population = numberOfPeople;
    generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    Exporter.runPostCompletionExports(generator, exportOpts);

    File expectedExportFolder = exportDir.toPath().resolve("csv").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    File patientFile1 = expectedExportFolder.toPath().resolve("patients-1.csv").toFile();
    File patientFile2 = expectedExportFolder.toPath().resolve("patients-2.csv").toFile();
    File patientFile3 = expectedExportFolder.toPath().resolve("patients-3.csv").toFile();
    File patientFile4 = expectedExportFolder.toPath().resolve("patients-4.csv").toFile();

    assertTrue("No patient export file found.", patientFile1.exists());
    assertTrue("No second patient export file found.", patientFile2.exists());
    assertTrue("No third patient export file found.", patientFile3.exists());
    assertTrue("Fourth patient export file should not exist.", !patientFile4.exists());

    String patientDataString1 = new String(Files.readAllBytes(patientFile1.toPath()));

    List patientData1 = SimpleCSV.parse(patientDataString1);

    assertTrue("CSV validation: " + patientFile1.getName(), SimpleCSV.isValid(patientDataString1));

    int length1 = patientData1.size();
    assertTrue("Expected two Patients in the first export file, but found " + length1,
               length1 == 2);

    String patientDataString2 = new String(Files.readAllBytes(patientFile2.toPath()));

    List patientData2 = SimpleCSV.parse(patientDataString2);

    assertTrue("CSV validation: " + patientFile2.getName(), SimpleCSV.isValid(patientDataString2));

    int length2 = patientData2.size();
    assertTrue("Expected two Patients in the second export file, but found " + length2,
               length2 == 2);

    String patientDataString3 = new String(Files.readAllBytes(patientFile3.toPath()));

    List patientData3 = SimpleCSV.parse(patientDataString3);

    assertTrue("CSV validation: " + patientFile3.getName(), SimpleCSV.isValid(patientDataString3));

    int length3 = patientData3.size();
    assertTrue("Expected one Patient in the third export file, but found " + length3,
               length3 == 1);
  }

  @Test
  public void testCSVExportFileNumberDigits() throws Exception {
    Config.set("exporter.csv.included_files", "patients.csv");
    Config.set("exporter.csv.max_lines_per_file", "2");
    Config.set("exporter.csv.file_number_digits", "3");
    CSVExporter.getInstance().init();

    int numberOfPeople = 1;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("csv").toFile();

    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    File patientFile1 = expectedExportFolder.toPath().resolve("patients-001.csv").toFile();

    assertTrue("No patient export file found.", patientFile1.exists());
  }

  @Test
  public void throwsExceptionWhenFileIncludedAndExcluded() {
    Config.set("exporter.csv.included_files", "patients.csv,medications.csv");
    Config.set("exporter.csv.excluded_files", "medications.csv,procedures.csv");

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      CSVExporter.getInstance().init();
    });

    assertEquals("CSV exporter cannot include and exclude the same file:"
            + " medications.csv", exception.getMessage());
  }

  @Test
  public void doesNotThrowWhenNoOverlapBetweenIncludedAndExcludedFiles() {
    Config.set("exporter.csv.included_files", "patients.csv,medications.csv");
    Config.set("exporter.csv.excluded_files", "procedures.csv,observations.csv");

    try {
      CSVExporter.getInstance().init();
    } catch (IllegalArgumentException e) {
      fail("CSV exporter should not throw an exception when "
              + "there is no overlap between included and excluded files");
    }
  }

  @Test
  public void doesNotThrowWhenOnlyIncludedFilesAreSet() {
    Config.set("exporter.csv.included_files", "patients.csv,medications.csv");
    Config.set("exporter.csv.excluded_files", "");

    try {
      CSVExporter.getInstance().init();
    } catch (IllegalArgumentException e) {
      fail("CSV exporter should not throw an exception when "
              + "only included files are set");
    }
  }

  @Test
  public void doesNotThrowWhenOnlyExcludedFilesAreSet() {
    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "patients.csv,medications.csv");

    try {
      CSVExporter.getInstance().init();
    } catch (IllegalArgumentException e) {
      fail("CSV exporter should not throw an exception when "
             + "only included files are set");
    }
  }

  @Test
  public void doesNotThrowWhenBothIncludedAndExcludedFilesAreEmpty() {
    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "");

    try {
      CSVExporter.getInstance().init();
    } catch (IllegalArgumentException e) {
      fail("CSV exporter should not throw an exception when only included files are set");
    }
  }

  @Test
  public void testQualityOfLifeObservationsIncludedByDefault() throws Exception {
    List<? extends Map<String, String>> observations = exportObservations();

    List<String> qualityOfLifeCodes = Arrays.asList(QualityOfLifeModule.QALY,
        QualityOfLifeModule.DALY, QualityOfLifeModule.QOLS);
    int qualityOfLifeRows = 0;
    for (Map<String, String> observation : observations) {
      if (qualityOfLifeCodes.contains(observation.get("CODE"))) {
        qualityOfLifeRows++;
        assertEquals("Quality of life metrics are not tied to an encounter",
            "", observation.get("ENCOUNTER"));
      }
    }
    assertTrue("Expected QALY, DALY, and QOLS rows in observations.csv", qualityOfLifeRows > 0);
  }

  @Test
  public void testQualityOfLifeObservationsCanBeExcluded() throws Exception {
    Config.set("exporter.csv.include_quality_of_life_observations", "false");

    List<? extends Map<String, String>> observations = exportObservations();

    List<String> qualityOfLifeCodes = Arrays.asList(QualityOfLifeModule.QALY,
        QualityOfLifeModule.DALY, QualityOfLifeModule.QOLS);
    assertFalse("Expected clinical observations in observations.csv", observations.isEmpty());
    for (Map<String, String> observation : observations) {
      assertFalse("Quality of life metrics should not be exported: " + observation,
          qualityOfLifeCodes.contains(observation.get("CODE")));
      assertFalse("Every observation must reference an encounter: " + observation,
          observation.get("ENCOUNTER").isEmpty());
    }
  }

  /**
   * Generate a small population, export it to CSV, and parse the resulting observations file.
   * @return the rows of observations.csv
   * @throws Exception if something goes wrong
   */
  private List<? extends Map<String, String>> exportObservations() throws Exception {
    CSVExporter.getInstance().init();

    int numberOfPeople = 10;
    ExporterRuntimeOptions exportOpts = new ExporterRuntimeOptions();
    exportOpts.deferExports = true;
    GeneratorOptions generatorOpts = new GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    Exporter.runPostCompletionExports(generator, exportOpts);

    File observationsFile = exportDir.toPath().resolve("csv").resolve("observations.csv").toFile();
    assertTrue("observations.csv was not exported", observationsFile.exists());
    String csvData = new String(Files.readAllBytes(observationsFile.toPath()));
    return SimpleCSV.parse(csvData);
  }

  /**
   * Field of a parsed CSV row, looked up by the column name the exporter's own header declares.
   *
   * @param row the parsed row
   * @param column the column name, as written in {@link CSVConstants}
   * @return the value, or an empty string when the field holds nothing
   */
  private static String field(LinkedHashMap<String, String> row, String column) {
    assertTrue("column " + column + " is missing from the row: " + row.keySet(),
        row.containsKey(column));
    String value = row.get(column);
    return value == null ? "" : value;
  }

  /**
   * Regression test: an entry that was never stopped must not write an epoch timestamp.
   *
   * <p>When the internal stop time is unset ({@code 0L}), the CSV exporter should leave the
   * end-date field empty rather than serialize the Unix epoch, consistent with the existing
   * handling of unset stop times elsewhere in {@code CSVExporter}. It does not: the row is built
   * by {@code ClaimTransaction.toString()}, which formats the unset stop through
   * {@code ExportHelper.iso8601Timestamp(0L)}, and the Unix epoch is what that call renders.
   *
   * <p>The state is built explicitly and deterministically: a fixed seed, a fixed birth date, a
   * medication entry the simulation never stopped ({@code stop == 0L}, the unset sentinel this
   * code base checks everywhere), and the same row construction the exporter performs in
   * {@code exportClaim(...)}. No population is generated.
   *
   * @throws Exception if the export fails
   */
  @Test
  public void testClaimTransactionToDateEmptyWhenStopUnset() throws Exception {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ID, "test-patient");
    person.coverage.setPlanToNoInsurance(0L);

    long start = 86_400_000L;
    Encounter encounter = person.record.encounterStart(start, EncounterType.EMERGENCY);
    encounter.codes.add(new Code("system", "code", "display"));
    // a medication that was never stopped: its entry keeps the unset stop time
    Medication medication = person.record.new Medication(start, "test-medication");
    medication.codes.add(new Code("system", "code", "display"));
    assertEquals("fixture: the medication must have no stop time", 0L, medication.stop);
    assertTrue("fixture: the medication start must be a real timestamp", medication.start > 0L);

    Claim claim = medication.claim;
    CSVExporter.ClaimTransaction transaction = CSVExporter.getInstance().new ClaimTransaction(
        encounter, "test-encounter-id", claim, "test-claim-id", 0L, claim.mainEntry, person);
    // the same field sequence exportClaim(...) applies to a CHARGE transaction
    transaction.type = CSVExporter.ClaimTransactionType.CHARGE;
    transaction.setAmount(claim.mainEntry.cost);
    transaction.departmentId = "";
    transaction.diagnosisCodes = new String[8];
    transaction.transferType = claim.getPayer().isNoInsurance() ? "p" : "1";

    List<LinkedHashMap<String, String>> parsed = SimpleCSV.parse(
        CSVConstants.CLAIM_TRANSACTION_HEADER_LINE + transaction.toString());
    assertEquals("the fixture must produce exactly one transaction row", 1, parsed.size());
    String todate = field(parsed.get(0), "TODATE");

    assertEquals("claims_transactions.TODATE must be empty when the entry has no stop time; "
        + "the exporter wrote the Unix epoch into the end-date field instead: " + todate,
        "", todate);
  }

  /**
   * Regression test: a plan whose end was never set must not write an epoch END_DATE.
   *
   * <p>When the plan's internal stop time is unset ({@code 0L}), the CSV exporter should leave
   * the end-date field empty rather than serialize the Unix epoch, consistent with the existing
   * handling of unset stop times elsewhere in {@code CSVExporter}. It does not:
   * {@code exportPayerTransition(...)} formats {@code planRecord.getStopTime()} through
   * {@code ExportHelper.iso8601Timestamp(0L)}, which renders the Unix epoch.
   *
   * <p>The state is built explicitly and deterministically (fixed seed, fixed birth date, one
   * plan record stopped at {@code 0L} through the public {@code PlanRecord.updateStopTime}), and
   * the CSV file is produced by {@code CSVExporter.export(Person,long)}, the method production
   * calls in {@code Exporter}. No population is generated.
   *
   * @throws Exception if the export fails
   */
  @Test
  public void testPayerTransitionEndDateEmptyWhenStopUnset() throws Exception {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ID, "test-patient");
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.COORDINATE, new Point2D.Double(0.0, 0.0));
    person.attributes.put(QualityOfLifeModule.QALY, new HashMap<Integer, Double>());
    person.attributes.put(QualityOfLifeModule.DALY, new HashMap<Integer, Double>());
    person.attributes.put(QualityOfLifeModule.QOLS, new HashMap<Integer, Double>());
    person.coverage.setPlanToNoInsurance(0L);

    PlanRecord planRecord = person.coverage.getLastPlanRecord();
    planRecord.updateStopTime(0L); // the plan's end was never set
    assertEquals("fixture: the plan record must have no stop time", 0L, planRecord.getStopTime());

    CSVExporter.getInstance().init();
    CSVExporter.getInstance().export(person, 86_400_000L);

    File exported = exportDir.toPath().resolve("csv")
        .resolve(CSVConstants.PAYER_TRANSITION_KEY + ".csv").toFile();
    assertTrue("the export must have written " + exported, exported.exists());
    List<LinkedHashMap<String, String>> rows = SimpleCSV.parse(
        new String(Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8));
    assertEquals("the fixture must export exactly one payer transition", 1, rows.size());
    String endDate = field(rows.get(0), "END_DATE");

    assertEquals("payer_transitions.END_DATE must be empty when the plan has no stop time; "
        + "the exporter wrote the Unix epoch into the end-date field instead: " + endDate,
        "", endDate);
  }
}
