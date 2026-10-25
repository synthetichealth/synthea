package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Provider;

public class MetadataExporterTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private final Map<String, String> originalConfig = new HashMap<>();
  private Generator generator;

  /** Prepare a real generator without generating any patients. */
  @Before
  public void setUp() throws Exception {
    for (String key : Config.allPropertyNames()) {
      originalConfig.put(key, Config.get(key));
    }
    TestHelper.loadTestProperties();
    GeneratorOptions options = new GeneratorOptions();
    options.population = 0;
    options.seed = 123L;
    options.clinicianSeed = 456L;
    options.referenceTime = 1577836800000L;
    options.endTime = 1609459200000L;
    options.state = Config.get("test_state.default", "Massachusetts");
    options.threadPoolSize = 1;
    generator = new Generator(options);
  }

  /** Restore global configuration so settings cannot leak into other tests. */
  @After
  public void tearDown() {
    for (String key : Config.allPropertyNames()) {
      Config.remove(key);
    }
    originalConfig.forEach(Config::set);
  }

  @Test
  public void exportFlagsFollowCurrentConfiguration() throws Exception {
    Config.set("exporter.csv.export", "true");
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.fhir_stu3.export", "true");
    Config.set("exporter.fhir_dstu2.export", "false");
    Config.set("exporter.fhir.bulk_data", "true");
    JsonObject first = export();
    assertFlag(first, "exporter.csv.export", true);
    assertFlag(first, "exporter.fhir.export", false);
    assertFlag(first, "exporter.fhir_stu3.export", true);
    assertFlag(first, "exporter.fhir_dstu2.export", false);
    assertFlag(first, "exporter.fhir.bulk_data", true);

    Config.set("exporter.csv.export", "false");
    Config.set("exporter.fhir.export", "true");
    Config.set("exporter.fhir_stu3.export", "false");
    Config.set("exporter.fhir_dstu2.export", "true");
    Config.set("exporter.fhir.bulk_data", "false");
    JsonObject second = export();
    assertFlag(second, "exporter.csv.export", false);
    assertFlag(second, "exporter.fhir.export", true);
    assertFlag(second, "exporter.fhir_stu3.export", false);
    assertFlag(second, "exporter.fhir_dstu2.export", true);
    assertFlag(second, "exporter.fhir.bulk_data", false);
  }

  @Test
  public void flagsUseExporterBooleanSemantics() throws Exception {
    Config.remove("exporter.csv.export");
    Config.set("exporter.fhir.export", "TRUE");
    Config.set("exporter.fhir_stu3.export", "not-a-boolean");
    Config.set("exporter.fhir_dstu2.export", "false");
    Config.set("exporter.fhir.bulk_data", "true");
    JsonObject metadata = export();
    assertFlag(metadata, "exporter.csv.export", false);
    assertFlag(metadata, "exporter.fhir.export", true);
    assertFlag(metadata, "exporter.fhir_stu3.export", false);
    assertFlag(metadata, "exporter.fhir_dstu2.export", false);
    assertFlag(metadata, "exporter.fhir.bulk_data", true);
  }

  @Test
  public void preservesLegacyMetadataWithoutDumpingConfiguration() throws Exception {
    Config.set("exporter.years_of_history", "7");
    Config.set("exporter.private_setting", "PRIVATE_CONFIG_SENTINEL");
    JsonObject metadata = export();
    assertEquals(generator.id.toString(), metadata.get("runID").getAsString());
    assertEquals(123L, metadata.get("seed").getAsLong());
    assertEquals(456L, metadata.get("clinicianSeed").getAsLong());
    assertEquals("20200101", metadata.get("referenceTime").getAsString());
    assertEquals("20210101", metadata.get("endTime").getAsString());
    assertEquals(Utilities.SYNTHEA_VERSION, metadata.get("version").getAsString());
    assertEquals(0, metadata.get("patientCount").getAsInt());
    assertEquals(Provider.getProviderList().size(), metadata.get("providerCount").getAsInt());
    assertEquals(PayerManager.getAllPayers().size(), metadata.get("payerCount").getAsInt());
    assertEquals(System.getProperty("java.version"), metadata.get("javaVersion").getAsString());
    assertEquals(1, metadata.get("generate.thread_pool_size").getAsInt());
    assertEquals(1, metadata.get("generatorThreads").getAsInt());
    assertEquals(ExportHelper.iso8601Timestamp(generator.options.runStartTime),
        metadata.get("runStartTime").getAsString());
    assertTrue(metadata.get("runTimeInSeconds").getAsLong() >= 0);
    assertTrue(metadata.getAsJsonPrimitive("exporter.years_of_history").isString());
    assertEquals("7", metadata.get("exporter.years_of_history").getAsString());
    assertEquals(generator.options.state, metadata.get("state").getAsString());
    assertEquals("*", metadata.get("modules").getAsString());
    assertFalse(metadata.has("gender"));
    assertFalse(metadata.has("age"));
    assertFalse(metadata.has("city"));
    assertFalse(metadata.has("exporter.private_setting"));
    assertFalse(metadata.has("exporter.baseDirectory"));
    assertFalse(metadata.toString().contains("PRIVATE_CONFIG_SENTINEL"));
    assertFalse(metadata.toString().contains(Config.get("exporter.baseDirectory")));
  }

  private JsonObject export() throws Exception {
    File directory = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", directory.toString());
    MetadataExporter.exportMetadata(generator);
    String filename = (ExportHelper.iso8601Timestamp(generator.options.runStartTime)
        + "_0_" + generator.options.state + "_" + generator.id).replaceAll("\\W+", "_") + ".json";
    File metadataDirectory = new File(directory, "metadata");
    assertEquals(1, metadataDirectory.list().length);
    String json = new String(Files.readAllBytes(new File(metadataDirectory, filename).toPath()),
        StandardCharsets.UTF_8);
    return JsonParser.parseString(json).getAsJsonObject();
  }

  private void assertFlag(JsonObject metadata, String key, boolean expected) {
    assertTrue("Missing metadata flag: " + key, metadata.has(key));
    assertTrue(key + " must be a JSON boolean", metadata.getAsJsonPrimitive(key).isBoolean());
    assertEquals(key, expected, metadata.get(key).getAsBoolean());
  }
}
