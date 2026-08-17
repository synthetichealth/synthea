package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

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

/**
 * Verifies that organizations.csv and providers.csv carry the NPI that Synthea already
 * generates for each organization and clinician.
 */
public class CSVExporterNpiTest {

  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @ClassRule
  public static TemporaryFolder tempFolder = new TemporaryFolder();

  private static File exportDir;

  /**
   * Configure the exporter to write CSV into a temporary folder.
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
  public void testOrganizationAndProviderNpiExport() throws Exception {
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

    File csvFolder = exportDir.toPath().resolve("csv").toFile();
    assertNpiColumn(new File(csvFolder, "organizations.csv"));
    assertNpiColumn(new File(csvFolder, "providers.csv"));
  }

  /**
   * Assert that the given CSV file has an NPI column and that every row holds a
   * well-formed NPI.
   *
   * @param csvFile the file to check
   * @throws Exception if the file cannot be read or parsed
   */
  private void assertNpiColumn(File csvFile) throws Exception {
    assertTrue(csvFile.getName() + " was not exported", csvFile.exists());
    String csvData = new String(Files.readAllBytes(csvFile.toPath()));
    assertTrue("CSV validation: " + csvFile.getName(), SimpleCSV.isValid(csvData));

    List<? extends Map<String, String>> rows = SimpleCSV.parse(csvData);
    assertTrue(csvFile.getName() + " has no rows", rows.size() > 0);
    for (Map<String, String> row : rows) {
      String npi = row.get("NPI");
      assertTrue(csvFile.getName() + " has no NPI column", npi != null);
      assertTrue("NPI '" + npi + "' in " + csvFile.getName() + " is not 10 digits",
          npi.matches("\\d{10}"));
      assertEquals("NPI '" + npi + "' in " + csvFile.getName() + " has a bad check digit",
          checkDigit(npi.substring(0, 9)), npi.charAt(9) - '0');
    }
  }

  /**
   * Calculate the NPI check digit for a 9 digit identifier, per
   * https://www.cms.gov/Regulations-and-Guidance/Administrative-Simplification/NationalProvIdentStand/Downloads/NPIcheckdigit.pdf
   * The constant 24 is the contribution of the 80840 prefix that the algorithm prepends.
   *
   * @param base the first 9 digits of the NPI
   * @return the expected check digit
   */
  private static int checkDigit(String base) {
    int sum = 24;
    boolean doubled = true;
    for (int i = base.length() - 1; i >= 0; i--) {
      int digit = base.charAt(i) - '0';
      if (doubled) {
        digit = digit * 2;
        if (digit >= 10) {
          digit = digit - 9;
        }
      }
      sum += digit;
      doubled = !doubled;
    }
    return (10 - (sum % 10)) % 10;
  }
}
