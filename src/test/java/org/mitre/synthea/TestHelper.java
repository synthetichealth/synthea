package org.mitre.synthea;

import ca.uhn.fhir.context.FhirContext;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;

public abstract class TestHelper {

  public static final String SNOMED_URI = "http://snomed.info/sct";
  public static final String LOINC_URI = "http://loinc.org";
  public static final String SNOMED_OID = "2.16.840.1.113883.6.96";
  public static final String LOINC_OID = "2.16.840.1.113883.6.1";
  private static FhirContext dstu2FhirContext;
  private static FhirContext stu3FhirContext;
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
  
  public static WireMockConfiguration wiremockOptions() {
    return WireMockConfiguration.options().port(5566);
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
    Config.set("exporter.split_records", "false");
    Config.set("exporter.split_records.duplicate_data", "false");
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
    Config.set("generate.terminology_service_url", "");
  }

  public static long timestamp(int year, int month, int day, int hr, int min, int sec) {
    return LocalDateTime.of(year, month, day, hr, min, sec).toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }

  public static long years(long numYears) {
    return Utilities.convertTime("years", numYears);
  }
}
