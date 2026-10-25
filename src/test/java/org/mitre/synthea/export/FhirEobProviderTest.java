package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/** Tests that EOB provider references identify the resources the exporters actually publish. */
public class FhirEobProviderTest {
  private static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
  private static final long ENCOUNTER_TIME = Utilities.convertTime("years", 30);
  private boolean r4Transaction;
  private boolean stu3Transaction;
  private boolean usCore;
  private String bulkData;
  private String includedResources;
  private String excludedResources;

  /** Save exporter settings and enable all resources for the fixture. */
  @Before
  public void setUp() {
    r4Transaction = FhirR4.TRANSACTION_BUNDLE;
    stu3Transaction = FhirStu3.TRANSACTION_BUNDLE;
    usCore = FhirR4.USE_US_CORE_IG;
    bulkData = Config.get("exporter.fhir.bulk_data");
    includedResources = Config.get("exporter.fhir.included_resources");
    excludedResources = Config.get("exporter.fhir.excluded_resources");
    FhirR4.USE_US_CORE_IG = false;
    Config.set("exporter.fhir.included_resources", "");
    Config.set("exporter.fhir.excluded_resources", "");
    FhirR4.reloadIncludeExclude();
    PayerManager.loadNoInsurance();
  }

  /** Restore settings shared with the other exporter tests. */
  @After
  public void tearDown() {
    FhirR4.TRANSACTION_BUNDLE = r4Transaction;
    FhirStu3.TRANSACTION_BUNDLE = stu3Transaction;
    FhirR4.USE_US_CORE_IG = usCore;
    Config.set("exporter.fhir.bulk_data", bulkData);
    Config.set("exporter.fhir.included_resources", includedResources);
    Config.set("exporter.fhir.excluded_resources", excludedResources);
    FhirR4.reloadIncludeExclude();
  }

  @Test
  public void r4UsesOrganizationWithoutClinician() {
    assertR4Provider(false);
  }

  @Test
  public void r4UsesPractitionerWithClinician() {
    assertR4Provider(true);
  }

  private void assertR4Provider(boolean withClinician) {
    for (boolean transaction : new boolean[] {false, true}) {
      for (boolean bulk : new boolean[] {false, true}) {
        FhirR4.TRANSACTION_BUNDLE = transaction;
        Config.set("exporter.fhir.bulk_data", Boolean.toString(bulk));
        Person person = person(withClinician);
        Encounter encounter = person.record.encounters.get(0);
        org.hl7.fhir.r4.model.Bundle bundle = FhirR4.getContext().newJsonParser()
            .parseResource(org.hl7.fhir.r4.model.Bundle.class,
                FhirR4.convertToFHIRJson(person, ENCOUNTER_TIME + 60000));
        org.hl7.fhir.r4.model.ExplanationOfBenefit eob = bundle.getEntry().stream()
            .map(entry -> entry.getResource())
            .filter(resource -> resource instanceof org.hl7.fhir.r4.model.ExplanationOfBenefit)
            .map(resource -> (org.hl7.fhir.r4.model.ExplanationOfBenefit) resource)
            .findFirst().get();

        // Transaction bundles refer to separately exported hospital/practitioner resources.
        // Use those exporters' resource mappers to verify both the resource type and identifier.
        org.hl7.fhir.r4.model.Bundle providerBundle = new org.hl7.fhir.r4.model.Bundle();
        org.hl7.fhir.r4.model.Bundle.BundleEntryComponent target = withClinician
            ? FhirR4.practitioner(providerBundle, encounter.clinician)
            : FhirR4.provider(providerBundle, encounter.provider);
        String expected;
        if (transaction) {
          org.hl7.fhir.r4.model.Identifier identifier = withClinician
              ? ((org.hl7.fhir.r4.model.Practitioner) target.getResource()).getIdentifierFirstRep()
              : ((org.hl7.fhir.r4.model.Organization) target.getResource()).getIdentifierFirstRep();
          expected = target.getResource().fhirType() + "?identifier="
              + identifier.getSystem() + "|" + identifier.getValue();
        } else {
          expected = target.getFullUrl();
          assertTrue(bundle.getEntry().stream().anyMatch(entry ->
              expected.equals(entry.getFullUrl())
                  && entry.getResource().fhirType().equals(target.getResource().fhirType())));
        }
        assertEquals(expected, eob.getProvider().getReference());
        assertEquals(expected, eob.getCareTeamFirstRep().getProvider().getReference());
        org.hl7.fhir.r4.model.ServiceRequest referral =
            (org.hl7.fhir.r4.model.ServiceRequest) eob.getContained().stream()
                .filter(resource -> resource instanceof org.hl7.fhir.r4.model.ServiceRequest)
                .findFirst().get();
        assertEquals(expected, referral.getRequester().getReference());
        assertEquals(expected, referral.getPerformerFirstRep().getReference());
      }
    }
  }

  @Test
  public void stu3ClinicianIdentifierMatchesPublishedPractitioner() {
    for (boolean transaction : new boolean[] {false, true}) {
      for (boolean bulk : new boolean[] {false, true}) {
        FhirStu3.TRANSACTION_BUNDLE = transaction;
        Config.set("exporter.fhir.bulk_data", Boolean.toString(bulk));
        Person person = person(true);
        Clinician clinician = person.record.encounters.get(0).clinician;
        org.hl7.fhir.dstu3.model.Bundle bundle = stu3Bundle(person);
        org.hl7.fhir.dstu3.model.ExplanationOfBenefit eob = stu3Eob(bundle);
        org.hl7.fhir.dstu3.model.Identifier identifier = eob.getProvider().getIdentifier();
        assertEquals(NPI_SYSTEM, identifier.getSystem());
        assertEquals(clinician.npi, identifier.getValue());
        org.hl7.fhir.dstu3.model.Bundle providerBundle = new org.hl7.fhir.dstu3.model.Bundle();
        org.hl7.fhir.dstu3.model.Practitioner practitioner =
            (org.hl7.fhir.dstu3.model.Practitioner)
                FhirStu3.practitioner(providerBundle, clinician).getResource();
        assertTrue(practitioner.getIdentifier().stream().anyMatch(published ->
            identifier.getSystem().equals(published.getSystem())
                && identifier.getValue().equals(published.getValue())));
        assertEquals(identifier.getSystem(),
            eob.getCareTeamFirstRep().getProvider().getIdentifier().getSystem());
        assertEquals(identifier.getValue(),
            eob.getCareTeamFirstRep().getProvider().getIdentifier().getValue());
        if (!transaction) {
          assertTrue(bundle.getEntry().stream().map(entry -> entry.getResource())
              .filter(resource -> resource instanceof org.hl7.fhir.dstu3.model.Practitioner)
              .map(resource -> (org.hl7.fhir.dstu3.model.Practitioner) resource)
              .flatMap(resource -> resource.getIdentifier().stream())
              .anyMatch(published -> identifier.getSystem().equals(published.getSystem())
                  && identifier.getValue().equals(published.getValue())));
        }
      }
    }
  }

  @Test
  public void stu3KeepsOrganizationSeparateWithoutClinician() {
    for (boolean transaction : new boolean[] {false, true}) {
      for (boolean bulk : new boolean[] {false, true}) {
        FhirStu3.TRANSACTION_BUNDLE = transaction;
        Config.set("exporter.fhir.bulk_data", Boolean.toString(bulk));
        Person person = person(false);
        org.hl7.fhir.dstu3.model.ExplanationOfBenefit eob = stu3Eob(stu3Bundle(person));
        assertEquals("Unknown", eob.getProvider().getIdentifier().getValue());
        assertFalse(eob.getProvider().hasReference());
        assertEquals(person.record.encounters.get(0).provider.getResourceID(),
            eob.getOrganization().getIdentifier().getValue());
      }
    }
  }

  private org.hl7.fhir.dstu3.model.Bundle stu3Bundle(Person person) {
    return FhirStu3.getContext().newJsonParser()
        .parseResource(org.hl7.fhir.dstu3.model.Bundle.class,
            FhirStu3.convertToFHIRJson(person, ENCOUNTER_TIME + 60000));
  }

  private org.hl7.fhir.dstu3.model.ExplanationOfBenefit stu3Eob(
      org.hl7.fhir.dstu3.model.Bundle bundle) {
    return bundle.getEntry().stream().map(entry -> entry.getResource())
        .filter(resource -> resource instanceof org.hl7.fhir.dstu3.model.ExplanationOfBenefit)
        .map(resource -> (org.hl7.fhir.dstu3.model.ExplanationOfBenefit) resource)
        .findFirst().get();
  }

  private Person person(boolean withClinician) {
    Person person = new Person(1L);
    person.attributes.put(Person.ID, person.randUUID().toString());
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.attributes.put(Person.FIRST_LANGUAGE, "english");
    person.coverage.setPlanToNoInsurance(0L);
    person.coverage.setPlanToNoInsurance(ENCOUNTER_TIME + 1);
    Provider provider = new Provider();
    provider.name = "Test Hospital";
    provider.npi = "1234567893";
    person.setProvider(EncounterType.WELLNESS, provider);
    Encounter encounter = person.record.encounterStart(ENCOUNTER_TIME, EncounterType.WELLNESS);
    encounter.provider = provider;
    encounter.stop = ENCOUNTER_TIME + 60000;
    if (withClinician) {
      Clinician clinician = new Clinician(1L, person, 1L, provider);
      clinician.attributes.put(Person.GENDER, "F");
      clinician.attributes.put(Clinician.FIRST_NAME, "Test");
      clinician.attributes.put(Clinician.LAST_NAME, "Doctor");
      encounter.clinician = clinician;
    }
    return person;
  }
}
