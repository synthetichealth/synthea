package org.mitre.synthea;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.DefaultRandomNumberGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public abstract class TestHelper {

  public static final String SNOMED_URI = "http://snomed.info/sct";
  public static final String LOINC_URI = "http://loinc.org";
  public static final String SNOMED_OID = "2.16.840.1.113883.6.96";
  public static final String LOINC_OID = "2.16.840.1.113883.6.1";

  private static byte[] serializedPatients;

  /**
   * Returns a test fixture Module by filename.
   * @param filename The filename of the test fixture Module.
   * @return A Module.
   * @throws Exception On errors.
   */
  public static Module getFixture(String filename) throws Exception {
    Path modulesFolder = Paths.get("generic");
    Path module = modulesFolder.resolve(filename);
    return Module.loadFile(module, false, null, false);
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
    Config.set("exporter.split_records", "false");
    Config.set("exporter.split_records.duplicate_data", "false");
    Config.set("exporter.metadata.export", "false");
    Config.set("exporter.ccda.export", "false");
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.fhir_stu3.export", "false");
    Config.set("exporter.fhir_dstu2.export", "false");
    Config.set("exporter.fhir.transaction_bundle", "false");
    Config.set("exporter.fhir.bulk_data", "false");
    Config.set("exporter.fhir.included_resources", "");
    Config.set("exporter.fhir.excluded_resources", "");
    Config.set("exporter.groups.fhir.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.hospital.fhir_stu3.export", "false");
    Config.set("exporter.hospital.fhir_dstu2.export", "false");
    Config.set("exporter.practitioner.fhir.export", "false");
    Config.set("exporter.practitioner.fhir_stu3.export", "false");
    Config.set("exporter.practitioner.fhir_dstu2.export", "false");
    Config.set("exporter.json.export", "false");
    Config.set("exporter.csv.export", "false");
    Config.set("exporter.cpcds.export", "false");
    Config.set("exporter.bfd.export", "false");
    Config.set("exporter.cdw.export", "false");
    Config.set("exporter.text.export", "false");
    Config.set("exporter.custom.export", "false");
    Config.set("exporter.text.per_encounter_export", "false");
    Config.set("exporter.clinical_note.export", "false");
    Config.set("exporter.symptoms.csv.export", "false");
    Config.set("exporter.symptoms.text.export", "false");
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

  /**
   * Create a provider that can assigned to Patients.
   * @return General practice provider with all services.
   */
  public static Provider buildMockProvider() {
    Provider provider = new Provider();
    for (EncounterType type : EncounterType.values()) {
      provider.servicesProvided.add(type);
    }
    RandomNumberGenerator rng = new DefaultRandomNumberGenerator(0L);
    Clinician doc = new Clinician(0L, rng, 0L, provider);
    ArrayList<Clinician> clinicians = new ArrayList<Clinician>();
    clinicians.add(doc);
    provider.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, clinicians);
    return provider;
  }

  /**
   * This method generates 10 people and then serializes them out into memory as byte arrays. For
   * tests that need a generated patient, they can call this method to grab a fresh copy of a person
   * which is rehydrated from the byte array to ensure an unmodified copy of the original. This
   * eliminates regeneration of people in the test suite for many of the exporters.
   * @return the people array
   * @throws IOException when there is a problem rehydrating a person
   * @throws ClassNotFoundException when there is a problem rehydrating a person
   */
  public static synchronized Person[] getGeneratedPeople() throws IOException,
      ClassNotFoundException {
    if (serializedPatients == null) {
      // Ensure Physiology state is enabled
      boolean physStateEnabled = State.ENABLE_PHYSIOLOGY_STATE;
      State.ENABLE_PHYSIOLOGY_STATE = true;

      int numberOfPeople = 10;
      Generator generator = new Generator(numberOfPeople);
      generator.options.overflow = false;
      exportOff();
      Person[] people = new Person[10];
      for (int i = 0; i < numberOfPeople; i++) {
        people[i] = generator.generatePerson(i);
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(people);
      oos.close();
      serializedPatients = baos.toByteArray();

      // Reset state after exporter test.
      State.ENABLE_PHYSIOLOGY_STATE = physStateEnabled;
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(serializedPatients);
    ObjectInputStream ois = new ObjectInputStream(bais);
    Person[] rehydrated = (Person[]) ois.readObject();
    ois.close();
    return rehydrated;
  }
}