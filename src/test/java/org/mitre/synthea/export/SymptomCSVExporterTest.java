package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;

public class SymptomCSVExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @ClassRule
  public static TemporaryFolder tempFolder = new TemporaryFolder();

  private static File exportDir;

  /**
   * Setup function for running the test.
   */
  @BeforeClass
  public static void setUpExportDir() throws Exception {
    TestHelper.exportOff();
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.symptoms.csv.export", "true");
    Config.set("exporter.symptoms.csv.folder_per_run", "false");
    exportDir = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", exportDir.toString());
  }


  /**
   * Function for testing if the symptom export is done perfectly.
   */
  @Test
  public void testSymptomCSVExport() throws Exception {

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("symptoms/csv").toFile();

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

    assertEquals("Expected 1 CSV file in the output directory, found " + count, 1, count);
  }
}