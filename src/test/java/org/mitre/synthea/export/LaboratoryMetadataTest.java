package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.FhirContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.geom.Point2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;

/** Verify the units and codes of RDW and indexed eGFR in production modules. */
@RunWith(Parameterized.class)
public class LaboratoryMetadataTest {
  private static final long TIME = 1577836800000L;
  private static final String EGFR_UNIT = "mL/min/{1.73_m2}";

  private final Path modulePath;
  private final String stateName;
  private final String expectedCode;
  private final String expectedUnit;
  private final JsonObject definition;
  private final Map<String, String> originalConfig = new HashMap<>();
  private boolean useUsCoreIg;
  private boolean transactionBundle;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Discover all applicable production states, including already-correct definitions.
   * @return Module paths, state names and the expected LOINC code/unit combinations.
   * @throws Exception If module resources cannot be read.
   */
  @Parameters(name = "{0}: {1} ({2})")
  public static Collection<Object[]> observations() throws Exception {
    List<Object[]> parameters = new ArrayList<>();
    Path resources = Paths.get("src", "main", "resources");
    try (Stream<Path> files = Files.walk(resources.resolve("modules"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".json"))
          .sorted().collect(Collectors.toList())) {
        JsonObject module = JsonParser.parseString(new String(Files.readAllBytes(file),
            StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> state : module.getAsJsonObject("states").entrySet()) {
          JsonObject stateDefinition = state.getValue().getAsJsonObject();
          collect(parameters, resources.relativize(file), state.getKey(), stateDefinition);
          if (stateDefinition.has("observations")) {
            for (JsonElement observation : stateDefinition.getAsJsonArray("observations")) {
              collect(parameters, resources.relativize(file), state.getKey(),
                  observation.getAsJsonObject());
            }
          }
        }
      }
    }
    assertTrue("Production RDW and eGFR observations must be exercised", !parameters.isEmpty());
    return parameters;
  }

  private static void collect(List<Object[]> parameters, Path modulePath, String stateName,
      JsonObject definition) {
    if (!definition.has("codes") || !definition.has("unit")) {
      return;
    }
    for (JsonElement element : definition.getAsJsonArray("codes")) {
      JsonObject code = element.getAsJsonObject();
      if (!"LOINC".equals(code.get("system").getAsString())) {
        continue;
      }
      String value = code.get("code").getAsString();
      if ("33914-3".equals(value)) {
        parameters.add(new Object[] {modulePath, stateName, value, EGFR_UNIT, definition});
      } else if ("788-0".equals(value) || "115742-9".equals(value)) {
        String unit = definition.get("unit").getAsString();
        // LOINC 788-0 describes RDW-CV (%); 115742-9 explicitly describes RDW-SD (fL).
        assertTrue("RDW must retain a ratio or volume scale",
            "%".equals(unit) || "fL".equals(unit));
        parameters.add(new Object[] {modulePath, stateName,
            "fL".equals(unit) ? "115742-9" : "788-0", unit, definition});
      }
    }
  }

  /**
   * Construct a check for one observation definition.
   * @param modulePath Path relative to the resource root.
   * @param stateName Production state to execute.
   * @param expectedCode LOINC code for the intended measurement.
   * @param expectedUnit Unit required by that measurement.
   * @param definition Production observation definition, for its unchanged numeric scale.
   */
  public LaboratoryMetadataTest(Path modulePath, String stateName, String expectedCode,
      String expectedUnit, JsonObject definition) {
    this.modulePath = modulePath;
    this.stateName = stateName;
    this.expectedCode = expectedCode;
    this.expectedUnit = expectedUnit;
    this.definition = definition;
  }

  /** Preserve exporter settings modified by the representative serialization checks. */
  @Before
  public void saveExporterSettings() {
    useUsCoreIg = FhirR4.USE_US_CORE_IG;
    transactionBundle = FhirR4.TRANSACTION_BUNDLE;
  }

  /** Restore exporter settings so this test does not change other export tests. */
  @After
  public void restoreExporterSettings() {
    originalConfig.forEach((key, value) -> {
      if (value == null) {
        Config.remove(key);
      } else {
        Config.set(key, value);
      }
    });
    FhirR4.USE_US_CORE_IG = useUsCoreIg;
    FhirR4.TRANSACTION_BUNDLE = transactionBundle;
    FhirR4.reloadIncludeExclude();
  }

  private void configure(String key, String value) {
    originalConfig.put(key, Config.get(key));
    Config.set(key, value);
  }

  @Test
  public void generatedObservationRetainsMeasurementMeaning() throws Exception {
    PayerManager.loadNoInsurance();
    Person person = new Person(1731L);
    person.attributes.put(Person.ID, "lab-metadata-test");
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.attributes.put(Person.FIRST_LANGUAGE, "english");
    person.attributes.put(Person.COORDINATE, new Point2D.Double(-71.0, 42.0));
    person.coverage.setPlanToNoInsurance(0L);
    person.coverage.getLastPlanRecord().updateStopTime(TIME + 1);
    Provider provider = new Provider();
    provider.name = "Test provider";
    provider.npi = "0";
    person.setProvider(EncounterType.WELLNESS, provider);
    person.record.provider = provider;
    HealthRecord.Encounter encounter = person.record.encounterStart(TIME, EncounterType.WELLNESS);
    encounter.provider = provider;
    encounter.codes.add(new Code("SNOMED-CT", "185349003", "Encounter for check up"));
    for (VitalSign vitalSign : VitalSign.values()) {
      person.setVitalSign(vitalSign, 90.0);
    }

    Module module = Module.loadFile(modulePath, false, null, false);
    assertTrue(module.getState(stateName).process(person, TIME));
    HealthRecord.Observation observation = encounter.observations.stream()
        .filter(item -> item.codes.stream().anyMatch(code -> expectedCode.equals(code.code)
            || ("115742-9".equals(expectedCode) && "788-0".equals(code.code))))
        .findFirst().orElse(null);
    assertNotNull("Production state must record its laboratory observation", observation);
    assertEquals(expectedCode, observation.codes.get(0).code);
    assertEquals(expectedUnit, observation.unit);
    double value = ((Number) observation.value).doubleValue();
    if (definition.has("range")) {
      JsonObject range = definition.getAsJsonObject("range");
      assertTrue(value >= range.get("low").getAsDouble());
      assertTrue(value <= range.get("high").getAsDouble());
    } else {
      assertEquals("eGFR vital-sign values must not be rescaled", 90.0, value, 0.0);
    }

    if ((modulePath.endsWith("wellness_encounters.json") && "Record_CBC_Panel".equals(stateName))
        || (modulePath.endsWith("heart/cardiac_labs.json") && "CBC".equals(stateName))
        || (modulePath.endsWith("heart/cardiac_labs.json")
            && "Comprehensive_Metabolic_Panel_with_Normal_Kidney".equals(stateName))) {
      verifyExports(person, observation);
    }
  }

  private void verifyExports(Person person, HealthRecord.Observation observation) throws Exception {
    configure("exporter.fhir.included_resources", "");
    configure("exporter.fhir.excluded_resources", "");
    FhirR4.USE_US_CORE_IG = false;
    FhirR4.TRANSACTION_BUNDLE = false;
    FhirR4.reloadIncludeExclude();
    String json = FhirR4.convertToFHIRJson(person, TIME);
    Bundle bundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, json);
    Observation exported = bundle.getEntry().stream().map(entry -> entry.getResource())
        .filter(resource -> resource instanceof Observation).map(resource -> (Observation) resource)
        .filter(item -> expectedCode.equals(item.getCode().getCodingFirstRep().getCode()))
        .findFirst().orElse(null);
    assertNotNull("FHIR must retain the measurement code", exported);
    assertEquals("http://loinc.org", exported.getCode().getCodingFirstRep().getSystem());
    Quantity quantity = exported.getValueQuantity();
    assertEquals(expectedUnit, quantity.getUnit());
    assertEquals(expectedUnit, quantity.getCode());
    assertEquals("http://unitsofmeasure.org", quantity.getSystem());
    assertEquals(((Number) observation.value).doubleValue(),
        quantity.getValue().doubleValue(), 0.01);

    Path output = tempFolder.newFolder().toPath();
    configure("exporter.baseDirectory", output.toString());
    configure("exporter.csv.folder_per_run", "false");
    configure("exporter.csv.append_mode", "false");
    configure("exporter.csv.included_files", "patients.csv,observations.csv");
    configure("exporter.csv.excluded_files", "");
    configure("exporter.csv.include_quality_of_life_observations", "false");
    CSVExporter exporter = CSVExporter.getInstance();
    exporter.init();
    exporter.export(person, TIME);
    String csv = new String(Files.readAllBytes(output.resolve("csv/observations.csv")),
        StandardCharsets.UTF_8);
    Map<String, String> row = SimpleCSV.parse(csv).stream()
        .filter(item -> expectedCode.equals(item.get("CODE"))).findFirst().orElse(null);
    assertNotNull("CSV must retain the measurement code", row);
    assertEquals(expectedUnit, row.get("UNITS"));
    assertEquals(((Number) observation.value).doubleValue(), Double.parseDouble(row.get("VALUE")),
        0.051);
  }
}
