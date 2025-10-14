package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
}
