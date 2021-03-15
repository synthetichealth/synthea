package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

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
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.geography.Location;

public class CSVExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @ClassRule
  public static TemporaryFolder tempFolder = new TemporaryFolder();
  
  private static File exportDir;

  private static final int NUMBER_OF_FILES = 18;

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
  }
  
  @Test
  public void testDeferredCSVExport() throws Exception {
    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "");
    CSVExporter.getInstance().init();
    
    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Payer.loadPayers(new Location(Generator.DEFAULT_STATE, null));

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

    int count = 0;
    for (File csvFile : expectedExportFolder.listFiles()) {
      if (!csvFile.getName().endsWith(".csv")) {
        continue;
      }

      String csvData = new String(Files.readAllBytes(csvFile.toPath()));

      // the CSV exporter doesn't use the SimpleCSV class to write the data,
      // so we can use it here for a level of validation
      SimpleCSV.parse(csvData);
      assertTrue(SimpleCSV.isValid(csvData));

      count++;
    }

    assertEquals("Expected " + NUMBER_OF_FILES
        + " CSV files in the output directory, found " + count, NUMBER_OF_FILES, count);
  }
  
  @Test
  public void testCSVExportIncludes() throws Exception {
    Config.set("exporter.csv.included_files", "patients.csv,medications.csv,procedures.csv");
    Config.set("exporter.csv.excluded_files", "");
    CSVExporter.getInstance().init();

    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Payer.loadPayers(new Location(Generator.DEFAULT_STATE, null));

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
    Config.set("exporter.csv.included_files", "");
    Config.set("exporter.csv.excluded_files", "patients.csv, medications, payers, providers");
    CSVExporter.getInstance().init();

    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Payer.loadPayers(new Location(Generator.DEFAULT_STATE, null));

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
        case "payers.csv":
          foundPayers = true;
          break;
        case "providers.csv":
          foundProviders = true;
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

    int expected = NUMBER_OF_FILES - 4;
    assertEquals("Expected " + expected + " CSV files in the output directory, found " + count,
        expected, count);
    assertTrue("patients.csv is present but should have been excluded", !foundPatients);
    assertTrue("medications.csv is present but should have been excluded", !foundMedications);
    assertTrue("payers.csv is present but should have been excluded", !foundPayers);
    assertTrue("providers.csv is present but should have been excluded", !foundProviders);
  }
  
  
}