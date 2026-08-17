package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Tests the code translations that the FHIR R4 exporter emits for the code maps
 * configured via {@code exporter.code_map.<code system>}.
 */
public class FhirR4CodeMapTest {

  /** Maps SNOMED 10509002 to ABC20.9, see src/test/resources/export/. */
  private static final String CODE_MAP = "export/unweighted_code_map.json";
  private static final String CPT_PROPERTY = "exporter.code_map.cpt";
  private static final String ICD10_PROPERTY = "exporter.code_map.icd10-cm";

  private boolean transactionBundle;
  private boolean useUsCoreIg;

  /**
   * Load the test configuration and record the exporter state that this test changes.
   */
  @Before
  public void setUp() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    PayerManager.clear();
    PayerManager.loadNoInsurance();
    transactionBundle = FhirR4.TRANSACTION_BUNDLE;
    useUsCoreIg = FhirR4.USE_US_CORE_IG;
    FhirR4.TRANSACTION_BUNDLE = false;
    FhirR4.USE_US_CORE_IG = false;
    FhirR4.reloadIncludeExclude();
  }

  /**
   * Put back everything this test changed, so that it cannot affect other tests.
   */
  @After
  public void tearDown() {
    Config.remove(CPT_PROPERTY);
    Config.remove(ICD10_PROPERTY);
    Exporter.loadCodeMappers();
    FhirR4.TRANSACTION_BUNDLE = transactionBundle;
    FhirR4.USE_US_CORE_IG = useUsCoreIg;
    FhirR4.reloadIncludeExclude();
  }

  /**
   * A code map configured for a code system other than ICD-10-CM should be applied,
   * and the resulting translation should carry that code system's URI rather than
   * the ICD-10-CM URI.
   */
  @Test
  public void testTranslationUsesConfiguredCodeSystem() throws Exception {
    Config.set(CPT_PROPERTY, CODE_MAP);
    Exporter.loadCodeMappers();

    Coding translation = translationOfEncounterReason();
    assertNotNull("Code map configured under exporter.code_map.cpt was not applied", translation);
    assertEquals("ABC20.9", translation.getCode());
    assertEquals("http://www.ama-assn.org/go/cpt", translation.getSystem());
  }

  /**
   * The pre-existing ICD-10-CM configuration must keep emitting the ICD-10-CM URI.
   */
  @Test
  public void testIcd10TranslationUnchanged() throws Exception {
    Config.set(ICD10_PROPERTY, CODE_MAP);
    Exporter.loadCodeMappers();

    Coding translation = translationOfEncounterReason();
    assertNotNull("Code map configured under exporter.code_map.icd10-cm was not applied",
        translation);
    assertEquals("ABC20.9", translation.getCode());
    assertEquals("http://hl7.org/fhir/sid/icd-10-cm", translation.getSystem());
  }

  /**
   * Export a person with a single encounter whose reason is a code the test code map
   * knows about, and return the translated coding that was added to that reason.
   *
   * @return the translated coding, or null if no translation was added
   */
  private Coding translationOfEncounterReason() {
    Person person = new Person(0L);
    person.attributes.put(Person.RACE, "dummy value to prevent NPE");
    person.attributes.put(Person.ETHNICITY, "dummy value to prevent NPE");
    person.attributes.put(Person.FIRST_LANGUAGE, "english");
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.coverage.setPlanToNoInsurance(0L);

    Provider stub = new Provider();
    stub.name = "Fake Provider";
    stub.npi = "0";
    Clinician doc = new Clinician(0, person, 0, stub);
    ArrayList<Clinician> docs = new ArrayList<Clinician>();
    docs.add(doc);
    stub.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, docs);
    person.setProvider(EncounterType.AMBULATORY, stub);
    person.setProvider(EncounterType.WELLNESS, stub);
    person.record.provider = stub;

    HealthRecord.Encounter encounter = person.record.encounterStart(0, EncounterType.AMBULATORY);
    encounter.provider = person.record.provider;
    encounter.reason = new Code("SNOMED-CT", "10509002", "Acute bronchitis (disorder)");

    Bundle bundle = FhirR4.convertToFHIR(person, 0);

    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof org.hl7.fhir.r4.model.Encounter) {
        org.hl7.fhir.r4.model.Encounter resource =
            (org.hl7.fhir.r4.model.Encounter) entry.getResource();
        for (Coding coding : resource.getReasonCodeFirstRep().getCoding()) {
          if ("ABC20.9".equals(coding.getCode())) {
            return coding;
          }
        }
      }
    }
    return null;
  }
}
