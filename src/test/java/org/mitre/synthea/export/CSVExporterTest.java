package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;

public class CSVExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testCSVExport() throws Exception {
    TestHelper.exportOff();
    Config.set("exporter.csv.export", "true");
    File tempOutputFolder = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", tempOutputFolder.toString());

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator);

    // if we get here we at least had no exceptions

    File expectedExportFolder = tempOutputFolder.toPath().resolve("csv").toFile();

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

      count++;
    }

    assertEquals("Expected 12 CSV files in the output directory, found " + count, 12, count);
  }
}
