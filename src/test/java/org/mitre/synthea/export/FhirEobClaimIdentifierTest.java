package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

/** Checks the claim identities in the actual serialized R4 and STU3 EOB resources. */
public class FhirEobClaimIdentifierTest {
  private static final String CLAIM_ID = "https://bluebutton.cms.gov/resources/variables/clm_id";
  private static final String CLAIM_GROUP =
      "https://bluebutton.cms.gov/resources/identifier/claim-group";
  private static final long FIRST_ENCOUNTER = Utilities.convertTime("days", 30);
  private static final long SECOND_ENCOUNTER = Utilities.convertTime("days", 31);
  private static final long EXPORT_TIME = SECOND_ENCOUNTER + 60000;
  private boolean r4Transaction;
  private boolean stu3Transaction;
  private boolean usCore;
  private String bulkData;
  private String includedResources;
  private String excludedResources;

  /** Save the shared exporter options before constructing the small deterministic records. */
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

  /** Restore options used by the other exporter tests. */
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
  public void r4DoesNotInventClaimGroups() {
    assertNoClaimGroups(true);
  }

  @Test
  public void stu3DoesNotInventClaimGroups() {
    assertNoClaimGroups(false);
  }

  private void assertNoClaimGroups(boolean r4) {
    for (boolean transaction : new boolean[] {false, true}) {
      for (boolean bulk : new boolean[] {false, true}) {
        configure(transaction, bulk);
        int count = 0;
        for (long seed : new long[] {1L, 2L}) {
          JsonObject bundle = export(person(seed), r4);
          for (JsonElement entry : bundle.getAsJsonArray("entry")) {
            JsonObject resource = entry.getAsJsonObject().getAsJsonObject("resource");
            if ("ExplanationOfBenefit".equals(resource.get("resourceType").getAsString())) {
              count++;
              for (JsonElement identifier : resource.getAsJsonArray("identifier")) {
                assertFalse("Unversioned claims must not share an invented claim group",
                    CLAIM_GROUP.equals(identifier.getAsJsonObject().get("system").getAsString()));
              }
            }
          }
        }
        // R4 emits an EOB for the medication claim as well as both encounter claims.
        // STU3 emits the medication Claim, but only the two encounter EOBs.
        assertEquals(r4 ? 6 : 4, count);
      }
    }
  }

  @Test
  public void r4PreservesDistinctStableClaimIdentities() {
    assertClaimIdentities(true);
  }

  @Test
  public void stu3PreservesDistinctStableClaimIdentities() {
    assertClaimIdentities(false);
  }

  private void assertClaimIdentities(boolean r4) {
    Set<String> acrossPeople = new HashSet<>();
    for (long seed : new long[] {1L, 2L}) {
      Person person = person(seed);
      Set<String> expected = new HashSet<>();
      for (Encounter encounter : person.record.encounters) {
        expected.add(encounter.claim.uuid.toString());
        if (r4) {
          for (Medication medication : encounter.medications) {
            expected.add(medication.claim.uuid.toString());
          }
        }
      }
      for (String id : expected) {
        assertTrue("Distinct patients must keep distinct claim identities", acrossPeople.add(id));
      }
      for (boolean transaction : new boolean[] {false, true}) {
        for (boolean bulk : new boolean[] {false, true}) {
          configure(transaction, bulk);
          assertEquals(expected, claimIds(export(person, r4)));
          assertEquals("Re-exporting a record must preserve its claim identities",
              expected, claimIds(export(person, r4)));
        }
      }
    }
    assertEquals(r4 ? 6 : 4, acrossPeople.size());
  }

  private Set<String> claimIds(JsonObject bundle) {
    Map<String, String> claims = new HashMap<>();
    for (JsonElement element : bundle.getAsJsonArray("entry")) {
      JsonObject entry = element.getAsJsonObject();
      JsonObject resource = entry.getAsJsonObject("resource");
      if ("Claim".equals(resource.get("resourceType").getAsString())) {
        String id = resource.get("id").getAsString();
        assertFalse("Each Claim must have a distinct resource ID", claims.containsKey(id));
        claims.put(id, entry.get("fullUrl").getAsString());
      }
    }
    Set<String> ids = new HashSet<>();
    for (JsonElement element : bundle.getAsJsonArray("entry")) {
      JsonObject resource = element.getAsJsonObject().getAsJsonObject("resource");
      if (!"ExplanationOfBenefit".equals(resource.get("resourceType").getAsString())) {
        continue;
      }
      int count = 0;
      for (JsonElement identifier : resource.getAsJsonArray("identifier")) {
        JsonObject id = identifier.getAsJsonObject();
        if (CLAIM_ID.equals(id.get("system").getAsString())) {
          count++;
          String value = id.get("value").getAsString();
          assertTrue("Each EOB must retain a distinct claim identifier", ids.add(value));
          assertTrue("The claim identifier must identify an exported Claim",
              claims.containsKey(value));
          if (resource.has("claim")) {
            assertEquals(claims.get(value),
                resource.getAsJsonObject("claim").get("reference").getAsString());
          }
        }
      }
      assertEquals("Each EOB must retain exactly one claim identifier", 1, count);
    }
    return ids;
  }

  private void configure(boolean transaction, boolean bulk) {
    FhirR4.TRANSACTION_BUNDLE = transaction;
    FhirStu3.TRANSACTION_BUNDLE = transaction;
    Config.set("exporter.fhir.bulk_data", Boolean.toString(bulk));
  }

  private JsonObject export(Person person, boolean r4) {
    String json = r4 ? FhirR4.convertToFHIRJson(person, EXPORT_TIME)
        : FhirStu3.convertToFHIRJson(person, EXPORT_TIME);
    return JsonParser.parseString(json).getAsJsonObject();
  }

  private Person person(long seed) {
    Person person = new Person(seed);
    person.attributes.put(Person.ID, person.randUUID().toString());
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.attributes.put(Person.FIRST_LANGUAGE, "english");
    person.coverage.setPlanToNoInsurance(0L);
    Provider provider = new Provider();
    provider.name = "Test Hospital";
    provider.npi = "1234567893";
    person.setProvider(EncounterType.WELLNESS, provider);
    person.setProvider(EncounterType.EMERGENCY, provider);
    Encounter first = person.record.encounterStart(FIRST_ENCOUNTER, EncounterType.WELLNESS);
    first.provider = provider;
    first.stop = FIRST_ENCOUNTER + 60000;
    Medication medication = person.record.medicationStart(FIRST_ENCOUNTER + 1000,
        "Test medication", false);
    medication.codes.add(new Code("RxNorm", "313782", "Acetaminophen 325 MG Oral Tablet"));
    Encounter second = person.record.encounterStart(SECOND_ENCOUNTER, EncounterType.EMERGENCY);
    second.provider = provider;
    second.stop = EXPORT_TIME;
    return person;
  }
}
