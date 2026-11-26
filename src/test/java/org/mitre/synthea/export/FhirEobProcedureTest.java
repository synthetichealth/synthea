package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

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

/** Check that EOBs retain the procedures and line associations of their source claims. */
public class FhirEobProcedureTest {
  private static final long TIME = Utilities.convertTime("days", 100);
  private final Map<String, String> settings = new HashMap<>();
  private boolean r4Transaction;
  private boolean stu3Transaction;
  private boolean usCore;

  /** Save shared configuration and enable all resources for these controlled exports. */
  @Before
  public void setUp() {
    r4Transaction = FhirR4.TRANSACTION_BUNDLE;
    stu3Transaction = FhirStu3.TRANSACTION_BUNDLE;
    usCore = FhirR4.USE_US_CORE_IG;
    for (String key : new String[] {"exporter.fhir.bulk_data", "exporter.fhir.included_resources",
        "exporter.fhir.excluded_resources"}) {
      settings.put(key, Config.get(key));
    }
    FhirR4.USE_US_CORE_IG = false;
    Config.set("exporter.fhir.included_resources", "");
    Config.set("exporter.fhir.excluded_resources", "");
    FhirR4.reloadIncludeExclude();
    PayerManager.loadNoInsurance();
  }

  /** Restore configuration so the fixture does not affect other exporter tests. */
  @After
  public void tearDown() {
    FhirR4.TRANSACTION_BUNDLE = r4Transaction;
    FhirStu3.TRANSACTION_BUNDLE = stu3Transaction;
    FhirR4.USE_US_CORE_IG = usCore;
    settings.forEach((key, value) -> {
      if (value == null) {
        Config.remove(key);
      } else {
        Config.set(key, value);
      }
    });
    FhirR4.reloadIncludeExclude();
  }

  @Test
  public void r4PreservesProcedureReferencesAndLineSequences() {
    assertExports(true, 2);
  }

  @Test
  public void stu3PreservesProcedureReferencesAndLineLinks() {
    assertExports(false, 2);
  }

  @Test
  public void r4DoesNotInventProceduresForAnEmptyClaim() {
    assertExports(true, 0);
  }

  @Test
  public void stu3DoesNotInventProceduresForAnEmptyClaim() {
    assertExports(false, 0);
  }

  private void assertExports(boolean r4, int count) {
    for (boolean transaction : new boolean[] {false, true}) {
      for (boolean bulk : new boolean[] {false, true}) {
        FhirR4.TRANSACTION_BUNDLE = transaction;
        FhirStu3.TRANSACTION_BUNDLE = transaction;
        Config.set("exporter.fhir.bulk_data", Boolean.toString(bulk));
        Person person = person(count);
        String json = r4 ? FhirR4.convertToFHIRJson(person, TIME + 60000)
            : FhirStu3.convertToFHIRJson(person, TIME + 60000);
        JsonObject bundle = JsonParser.parseString(json).getAsJsonObject();
        Map<String, JsonObject> resources = new HashMap<>();
        JsonObject eob = null;
        for (JsonElement entryElement : bundle.getAsJsonArray("entry")) {
          JsonObject entry = entryElement.getAsJsonObject();
          JsonObject resource = entry.getAsJsonObject("resource");
          resources.put(entry.get("fullUrl").getAsString(), resource);
          if ("ExplanationOfBenefit".equals(resource.get("resourceType").getAsString())) {
            eob = resource;
          }
        }
        assertNotNull("The real exporter must publish an EOB", eob);
        // STU3 institutional EOBs do not publish a claim reference. Match the source
        // Claim by its existing ID instead of changing that separate exporter behavior.
        String claimId = person.record.encounters.get(0).claim.uuid.toString();
        JsonObject claim = resources.values().stream()
            .filter(resource -> "Claim".equals(resource.get("resourceType").getAsString())
                && claimId.equals(resource.get("id").getAsString()))
            .findFirst().orElse(null);
        assertNotNull("The exporter must publish the source Claim", claim);
        JsonArray claimProcedures = array(claim, "procedure");
        JsonArray eobProcedures = array(eob, "procedure");
        assertEquals(count, claimProcedures.size());
        assertEquals("EOB procedures must survive JSON serialization", count, eobProcedures.size());
        for (int i = 0; i < count; i++) {
          JsonObject expected = claimProcedures.get(i).getAsJsonObject();
          JsonObject actual = eobProcedures.get(i).getAsJsonObject();
          assertEquals(i + 1, actual.get("sequence").getAsInt());
          assertEquals(expected.get("sequence"), actual.get("sequence"));
          assertEquals(expected.get("procedureReference"), actual.get("procedureReference"));
          String reference = actual.getAsJsonObject("procedureReference").get("reference")
              .getAsString();
          assertNotNull("Procedure reference must resolve in the bundle", resources.get(reference));
          assertEquals("Procedure", resources.get(reference).get("resourceType").getAsString());
        }

        String linkField = r4 ? "procedureSequence" : "procedureLinkId";
        Map<Integer, JsonObject> eobItems = new HashMap<>();
        for (JsonElement element : array(eob, "item")) {
          JsonObject item = element.getAsJsonObject();
          eobItems.put(item.get("sequence").getAsInt(), item);
        }
        int linked = 0;
        for (JsonElement element : array(claim, "item")) {
          JsonObject item = element.getAsJsonObject();
          JsonObject exportedItem = eobItems.get(item.get("sequence").getAsInt());
          assertNotNull(exportedItem);
          assertEquals(array(item, linkField), array(exportedItem, linkField));
          for (JsonElement link : array(exportedItem, linkField)) {
            int sequence = link.getAsInt();
            assertTrue(sequence > 0 && sequence <= eobProcedures.size());
            linked++;
          }
        }
        assertEquals("Every procedure line must retain its EOB association", count, linked);
      }
    }
  }

  private static JsonArray array(JsonObject object, String name) {
    return object.has(name) ? object.getAsJsonArray(name) : new JsonArray();
  }

  private Person person(int procedureCount) {
    Person person = new Person(1L);
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
    Encounter encounter = person.record.encounterStart(TIME, EncounterType.WELLNESS);
    encounter.provider = provider;
    encounter.stop = TIME + 60000;
    for (int i = 0; i < procedureCount; i++) {
      String code = i == 0 ? "73761001" : "33367005";
      person.record.procedure(TIME + i + 1, code)
          .codes.add(new Code("SNOMED-CT", code, "Test procedure " + i));
    }
    return person;
  }
}
