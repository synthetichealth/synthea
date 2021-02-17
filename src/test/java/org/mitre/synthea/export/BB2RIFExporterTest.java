package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.BB2RIFExporter.CodeMapper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

public class BB2RIFExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @ClassRule
  public static TemporaryFolder tempFolder = new TemporaryFolder();
  
  private static File exportDir;
  private static String bb2ExportEnabled;

  /**
   * Global setup for export tests.
   * @throws Exception if something goes wrong
   */
  @BeforeClass
  public static void setUpExportDir() throws Exception {
    TestHelper.exportOff();
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    bb2ExportEnabled = Config.get("exporter.bfd.export");
    Config.set("exporter.bfd.export", "true");
    exportDir = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", exportDir.toString());
    BB2RIFExporter.getInstance().prepareOutputFiles();
  }
  
  @Test
  public void testBB2Export() throws Exception {
    int numberOfPeople = 10;
    Exporter.ExporterRuntimeOptions exportOpts = new Exporter.ExporterRuntimeOptions();
    Generator.GeneratorOptions generatorOpts = new Generator.GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);
    Exporter.flushFlushables();

    // if we get here we at least had no exceptions

    File expectedExportFolder = exportDir.toPath().resolve("bfd").toFile();
    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    File beneficiaryFile = expectedExportFolder.toPath().resolve("beneficiary.csv").toFile();
    assertTrue(beneficiaryFile.exists() && beneficiaryFile.isFile());
    String csvData = new String(Files.readAllBytes(beneficiaryFile.toPath()));

    // the BB2 exporter doesn't use the SimpleCSV class to write the data,
    // so we can use it here for a level of validation
    List<LinkedHashMap<String, String>> rows = SimpleCSV.parse(csvData, '|');
    assertEquals(
            "Expected " + numberOfPeople + " rows in the beneficiary file, found " + rows.size(),
            numberOfPeople, rows.size());
    for (LinkedHashMap<String, String> row: rows) {
      assertTrue("Expected non-zero length surname", 
              row.containsKey("BENE_SRNM_NAME") && row.get("BENE_SRNM_NAME").length() > 0);
    }
    
    File inpatientFile = expectedExportFolder.toPath().resolve("inpatient.csv").toFile();
    assertTrue(inpatientFile.exists() && inpatientFile.isFile());
    // TODO: more meaningful testing of contents (e.g. count of inpatient claims)

    //    File outpatientFile = expectedExportFolder.toPath().resolve("outpatient.csv").toFile();
    //    assertTrue(outpatientFile.exists() && outpatientFile.isFile());
    //    // TODO: more meaningful testing of contents (e.g. count of outpatient claims)
    //
    //    File carrierFile = expectedExportFolder.toPath().resolve("carrier.csv").toFile();
    //    assertTrue(carrierFile.exists() && carrierFile.isFile());
    //    // TODO: more meaningful testing of contents
    //
    //    File beneficiaryHistoryFile = expectedExportFolder.toPath()
    //            .resolve("beneficiary_history.csv").toFile();
    //    assertTrue(beneficiaryHistoryFile.exists() && beneficiaryHistoryFile.isFile());
    //    // TODO: more meaningful testing of contents
  }
  
  @Test
  public void testCodeMapper() {
    try {
      String json = Utilities.readResource("condition_code_map.json");
      Exporter.ExporterRuntimeOptions exportOpts = new Exporter.ExporterRuntimeOptions();
      Generator.GeneratorOptions generatorOpts = new Generator.GeneratorOptions();
      generatorOpts.population = 1;
      Generator generator = new Generator(generatorOpts, exportOpts);
      CodeMapper mapper = new CodeMapper("condition_code_map.json");
      assertTrue(mapper.canMap("10509002"));
      assertEquals("J20.9", mapper.getMapped("10509002", generator));
      assertFalse(mapper.canMap("not a code"));
    } catch (IOException e) {
      // No worries. The optional mapping file is not present.
    }      
  }
}
