package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.concepts.healthinsurance.PlanRecord;

@RunWith(Parameterized.class)
public class CabgBloodGasTest {
  private static final long TIME = 1577836800000L;
  private static final double TOLERANCE = 0.00001;

  private final String modulePath;
  private final String stateName;
  private final String preparationState;
  private final boolean venous;

  /**
   * Exercise each production CABG gas report, including both arterial callers.
   * @return Module, report and preparation states, and specimen type.
   */
  @Parameters(name = "{0}: {1}")
  public static Collection<Object[]> panels() {
    return Arrays.asList(new Object[][] {
        {"heart/cabg/labs_common", "Arterial_Blood_Gas", "Prepare_Arterial_Blood_Gas", false},
        {"heart/cabg/or_labs_meds", "Arterial_Blood_Gas", "Prepare_Arterial_Blood_Gas", false},
        {"heart/cabg/or_labs_meds", "Venous Blood Gas", "Prepare_Venous_Blood_Gas", true}
    });
  }

  /**
   * Construct a test for one production report.
   * @param modulePath Module resource name.
   * @param stateName DiagnosticReport name.
   * @param preparationState Name of the preceding preparation state.
   * @param venous Whether this is the venous report.
   */
  public CabgBloodGasTest(String modulePath, String stateName,
      String preparationState, boolean venous) {
    this.modulePath = modulePath;
    this.stateName = stateName;
    this.preparationState = preparationState;
    this.venous = venous;
  }

  @Test
  public void generatedGasValuesStayInRangeAndSatisfyBloodGasEquations() throws Exception {
    Module module = Module.loadFile(Paths.get("modules", modulePath + ".json"), false, null, false);
    for (int seed = 0; seed < 64; seed++) {
      Person person = createPerson(seed);
      HealthRecord.Encounter encounter = person.record.encounterStart(TIME, EncounterType.WELLNESS);
      double firstPh = Double.NaN;
      for (int occurrence = 0; occurrence < 2; occurrence++) {
        long time = TIME + occurrence * 86400000L;
        State preparation = module.getState(preparationState);
        // Execute the original report too, so this regression exposes the bad generated values
        // when run against the pre-fix modules instead of failing on a missing preparation state.
        if (preparation != null) {
          assertTrue(preparation.process(person, time));
        }
        assertTrue(module.getState(stateName).process(person, time));
        assertEquals(occurrence + 1, encounter.reports.size());
        HealthRecord.Report report = encounter.reports.get(occurrence);
        double ph = assertGas(report);
        if (occurrence == 0) {
          firstPh = ph;
        } else {
          assertNotEquals("A later specimen must refresh the sampled pH", firstPh, ph, 0.0);
        }
      }
    }
    // Force both ends of the pH and conditional PCO2 draws through the production sampler.
    // The generator uses [0, 1), so nextDown(1.0) is its largest permitted fraction.
    for (double phFraction : new double[] {0.0, Math.nextDown(1.0)}) {
      for (double pco2Fraction : new double[] {0.0, Math.nextDown(1.0)}) {
        Person person = spy(createPerson(0L));
        when(person.rand()).thenReturn(phFraction, pco2Fraction, 0.5);
        HealthRecord.Encounter encounter = person.record.encounterStart(TIME,
            EncounterType.WELLNESS);
        assertTrue(module.getState(preparationState).process(person, TIME));
        assertTrue(module.getState(stateName).process(person, TIME));
        assertEquals(1, encounter.reports.size());
        assertGas(encounter.reports.get(0));
      }
    }
  }

  @Test
  public void everyIncomingTransitionPreparesTheSpecimen() throws Exception {
    String resource = Utilities.readResource("modules/" + modulePath + ".json");
    JsonObject states = JsonParser.parseString(resource).getAsJsonObject()
        .getAsJsonObject("states");
    assertTrue("Gas report must have a preparation state", states.has(preparationState));
    JsonObject preparation = states.getAsJsonObject(preparationState);
    assertEquals("CallSubmodule", preparation.get("type").getAsString());
    assertEquals("heart/cabg/" + (venous ? "sample_venous_blood_gas" : "sample_arterial_blood_gas"),
        preparation.get("submodule").getAsString());
    assertEquals(stateName, preparation.get("direct_transition").getAsString());
    int incoming = 0;
    for (Map.Entry<String, JsonElement> entry : states.entrySet()) {
      JsonObject state = entry.getValue().getAsJsonObject();
      if (state.has("direct_transition")) {
        String next = state.get("direct_transition").getAsString();
        if (stateName.equals(next)) {
          assertEquals("Only preparation may enter the gas report",
              preparationState, entry.getKey());
        }
        if (preparationState.equals(next)) {
          incoming++;
        }
      }
    }
    assertTrue("The preparation state must be reachable", incoming > 0);
  }

  private static Person createPerson(long seed) {
    Person person = new Person(seed);
    person.history = new LinkedList<>();
    person.attributes.put(Person.BIRTHDATE, TIME - 60L * 365 * 86400000L);
    person.attributes.put(Person.GENDER, "F");
    InsurancePlan plan = mock(InsurancePlan.class);
    when(plan.getDeductible()).thenReturn(BigDecimal.ZERO);
    person.coverage.getPlanHistory().add(new PlanRecord(TIME, plan, TIME + 1));
    person.setProvider(EncounterType.WELLNESS, mock(Provider.class));
    person.setVitalSign(VitalSign.OXYGEN_SATURATION, 98.0);
    return person;
  }

  private static double value(HealthRecord.Report report, String code, String unit) {
    HealthRecord.Observation observation = report.observations.stream()
        .filter(item -> item.codes.stream()
            .anyMatch(coding -> "LOINC".equals(coding.system) && code.equals(coding.code)))
        .findFirst().orElseThrow(() -> new AssertionError("Missing observation " + code));
    assertEquals(unit, observation.unit);
    assertTrue("Observation " + code + " must be numeric", observation.value instanceof Number);
    assertEquals("CSV type must preserve numeric computed observations",
        "numeric", ExportHelper.getObservationType(observation));
    assertEquals(report, observation.report);
    return ((Number) observation.value).doubleValue();
  }

  private double assertGas(HealthRecord.Report report) {
    assertEquals(5, report.observations.size());
    assertEquals(venous ? "24339-4" : "24336-0", report.codes.get(0).code);
    double ph = value(report, venous ? "2746-6" : "2744-1", "[pH]");
    double pco2 = value(report, venous ? "2021-4" : "2019-8", "mm[Hg]");
    double bicarbonate = value(report, venous ? "14627-4" : "1960-4", "mmol/L");
    if (venous) {
      double totalCo2 = value(report, "2027-1", "mmol/L");
      assertTrue("Total CO2 must include bicarbonate", totalCo2 > bicarbonate);
      assertEquals(bicarbonate + 0.03 * pco2, totalCo2, TOLERANCE);
    }
    inRange("pH", ph, venous ? 7.31 : 7.35, 7.45);
    inRange("pCO2", pco2, venous ? 37 : 35, venous ? 50 : 45);
    inRange("bicarbonate", bicarbonate, 22, venous ? 28 : 27);
    double expectedBicarbonate = Math.pow(10.0, ph + Math.log10(pco2) - 7.608);
    assertEquals("Bicarbonate must use the pH and pCO2 from this specimen",
        expectedBicarbonate, bicarbonate, TOLERANCE);
    double po2 = value(report, venous ? "2705-2" : "2703-7", "mm[Hg]");
    inRange("pO2", po2, venous ? 37 : 80, venous ? 47 : 100);
    if (!venous) {
      assertEquals(98.0, value(report, "2708-6", "%"), 0.0);
    }
    return ph;
  }

  private static void inRange(String name, double value, double low, double high) {
    assertTrue(name + " outside [" + low + ", " + high + "]: " + value,
        value >= low - TOLERANCE && value <= high + TOLERANCE);
  }
}
