package org.mitre.synthea;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;

import ca.uhn.fhir.context.FhirContext;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.commons.io.IOUtils;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;

public abstract class TestHelper {

  public static final String SNOMED_URI = "http://snomed.info/sct";
  private static FhirContext r4FhirContext;

  /**
   * Returns a test fixture Module by filename.
   * @param filename The filename of the test fixture Module.
   * @return A Module.
   * @throws Exception On errors.
   */
  public static Module getFixture(String filename) throws Exception {
    Path modulesFolder = Paths.get("generic");
    Path module = modulesFolder.resolve(filename);
    return Module.loadFile(module, modulesFolder, null);
  }

  /**
   * Load the test.properties file.
   * @throws Exception on configuration loading errors.
   */
  public static void loadTestProperties() throws Exception {
    URI uri = Config.class.getResource("/test.properties").toURI();
    File file = new File(uri);
    Config.load(file);
  }

  /**
   * Load a file from test resources as an InputStream.
   * 
   * @param name the path of the file, relative to the test resources directory
   * @return an InputStream containing the contents of the file
   */
  public static InputStream getResourceAsStream(String name) {
    InputStream expectedStream = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(name);
    assertNotNull(expectedStream);
    return expectedStream;
  }

  /**
   * Load a file from test resources as a String.
   *
   * @param name the path of the file, relative to the test resources directory
   * @return a String containing the contents of the file
   */
  public static String getResourceAsString(String name) throws IOException {
    InputStream expectedStream = getResourceAsStream(name);
    StringWriter writer = new StringWriter();
    IOUtils.copy(expectedStream, writer, UTF_8);
    return writer.toString();
  }
  
  /**
   * Check whether the <code>synthea.test.httpRecording</code> property is set to enable HTTP 
   * recording, for tests with HTTP mocking.
   * 
   * @return true if HTTP recording is enabled
   */
  public static boolean isHttpRecordingEnabled() {
    String recordingProperty = System.getProperty("synthea.test.httpRecordingEnabled");
    return recordingProperty != null && recordingProperty.equals("true");
  }

  /**
   * Return the configured URL for recording terminology HTTP responses.
   * 
   * @return the configured terminology service URL
   */
  public static String getTxRecordingSource() {
    String recordingSource = System.getProperty("synthea.test.txRecordingSource");
    if (recordingSource == null) {
      throw new RuntimeException("No terminology service recording source configured");
    }
    return recordingSource;
  }

  /**
   * Get an R4 FHIR Context for testing, but only initialize it once.
   * 
   * @return an R4 FhirContext
   */
  public static FhirContext getR4FhirContext() {
    if (r4FhirContext == null) {
      r4FhirContext = FhirContext.forR4();
    }
    return r4FhirContext;
  }

  /**
   * Returns a WireMock response builder representing a response from a FHIR server.
   * 
   * @return a ResponseDefinitionBuilder object
   */
  public static ResponseDefinitionBuilder fhirResponse() {
    return WireMock.aResponse().withHeader("Content-Type", "application/fhir+json");
  }

  /**
   * Helper method to disable export of all data formats and database output.
   * Ensures that unit tests do not pollute the output folders.
   */
  public static void exportOff() {
    Config.set("generate.database_type", "none"); // ensure we don't write to a file-based DB
    Config.set("exporter.use_uuid_filenames", "false");
    Config.set("exporter.fhir.use_shr_extensions", "false");
    Config.set("exporter.subfolders_by_id_substring", "false");
    Config.set("exporter.ccda.export", "false");
    Config.set("exporter.fhir_stu3.export", "false");
    Config.set("exporter.fhir_dstu2.export", "false");
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.fhir.transaction_bundle", "false");
    Config.set("exporter.text.export", "false");
    Config.set("exporter.text.per_encounter_export", "false");
    Config.set("exporter.csv.export", "false");
    Config.set("exporter.symptoms.csv.export", "false");
    Config.set("exporter.symptoms.text.export", "false");
    Config.set("exporter.cpcds.export", "false");
    Config.set("exporter.cdw.export", "false");
    Config.set("exporter.hospital.fhir_stu3.export", "false");
    Config.set("exporter.hospital.fhir_dstu2.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.practitioner.fhir_stu3.export", "false");
    Config.set("exporter.practitioner.fhir_dstu2.export", "false");
    Config.set("exporter.practitioner.fhir.export", "false");
    Config.set("exporter.cost_access_outcomes_report", "false");
  }

  public static long timestamp(int year, int month, int day, int hr, int min, int sec) {
    return LocalDateTime.of(year, month, day, hr, min, sec).toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }
}
