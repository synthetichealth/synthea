package org.mitre.synthea.identity;

import com.google.common.io.Resources;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;

public class EntityManagerTest {

  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void fromJSON() throws IOException {
    String rawJSON = Utilities.readResource("identity/test_records.json");
    EntityManager em = EntityManager.fromJSON(rawJSON);
    Assert.assertEquals(1, em.getRecords().size());
    Assert.assertEquals("F", em.getRecords().get(0).getGender());
    Seed firstSeed = em.getRecords().get(0).getSeeds().get(0);
    Assert.assertEquals("Rita Ebony", firstSeed.getGivenName());
    Assert.assertNotNull(firstSeed.getDateOfBirth());
    Variant firstVariant = firstSeed.getVariants().get(0);
    Assert.assertEquals("Margarita Ebony", firstVariant.getGivenName());
  }

  @Test
  public void validate() throws IOException {
    String rawJSON = Utilities.readResource("identity/test_records.json");
    EntityManager em = EntityManager.fromJSON(rawJSON);
    Assert.assertTrue(em.validate());
  }

  /**
   * Clean up and reset the Generator.
   */
  @AfterClass
  public static void cleanUp() throws Exception {
    Generator.DEFAULT_STATE =  Config.get("test_state.default", "Massachusetts");
    Generator.entityManager = null;
    TestHelper.loadTestProperties();
    PayerManager.clear();
  }

  @Test
  public void endToEnd() throws Exception {
    // This test uses the JSON and FHIR exports to check the behavior of the fixed record
    // functionality. It pulls in one of the JSON exports and then counts the records in that
    // export. It checks the count of records against the number of exported FHIR records.
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Config.set("exporter.fhir.export", "true");
    Config.set("exporter.json.export", "true");
    Generator.DEFAULT_STATE = "North Carolina";
    Path baseDirectory = tempFolder.newFolder().toPath();
    Config.set("exporter.baseDirectory", baseDirectory.toString());
    PayerManager.clear();
    Generator generator = new Generator(0);
    generator.options.overflow = false;
    URL url = Resources.getResource("identity/test_records.json");
    generator.options.fixedRecordPath = new File(url.toURI());
    generator.run();
    Path jsonExportFolder = baseDirectory.resolve("json");
    Optional<Path> jsonExport = Files.list(jsonExportFolder)
        .filter(path -> path.toString().endsWith(".json")).findFirst();
    if (jsonExport.isPresent()) {
      Path jsonExportPath = jsonExport.get();
      String rawJson = new String(Files.readAllBytes(jsonExportPath));
      JsonElement record = JsonParser.parseString(rawJson);
      int recordCount = record.getAsJsonObject().get("records").getAsJsonObject().size();
      String fhirExportFolder = Config.get("exporter.baseDirectory") + "/fhir";
      long actualRecordCount = Files.list(FileSystems.getDefault().getPath(fhirExportFolder))
          .filter(path -> path.toString().endsWith(".json")).count();
      Assert.assertEquals(recordCount, actualRecordCount);
    } else {
      Assert.fail("Couldn't find the exported JSON version of the record");
    }
  }
}