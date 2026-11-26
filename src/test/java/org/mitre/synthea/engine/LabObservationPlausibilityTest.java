package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.concepts.healthinsurance.PlanRecord;

@RunWith(Parameterized.class)
public class LabObservationPlausibilityTest {
  private static final long TIME = 1577836800000L;

  private final String modulePath;
  private final String stateName;
  private final String code;
  private final String unit;
  private final double lowerBound;
  private final double upperBound;

  /**
   * Cover every copied high bilirubin default, normal controls and both troponin scale errors.
   * @return Production panels and their expected observation units and value bounds.
   */
  @Parameters(name = "{0}: {1}: {2}")
  public static Collection<Object[]> panels() {
    return Arrays.asList(new Object[][] {
        {"heart/cabg/labs_common", "Comprehensive_Metabolic_Panel_with_Kidney_Damage",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"heart/cardiac_labs", "Comprehensive_Metabolic_Panel_with_Kidney_Damage",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"encounter/hospital_basic_labs", "Comprehensive Metabolic Panel with Kidney Damage",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"covid19/measurements_daily", "Comprehensive Metabolic Panel with Kidney Damage",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"hiv/hiv_baseline", "Comprehensive_Metabolic_Panel_with_Kidney_Damage",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"uti/ed_bundle", "Comprehensive_Metabolic_Panel",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"uti/ed_bundle", "Comprehensive_Metabolic_Panel_with_Kidney_Damage",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"heart/cabg/labs_common", "Comprehensive_Metabolic_Panel_with_Normal_Kidney",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"heart/cardiac_labs", "Comprehensive_Metabolic_Panel_with_Normal_Kidney",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"encounter/hospital_basic_labs", "Comprehensive Metabolic Panel with Normal Kidney",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"covid19/measurements_daily", "Comprehensive Metabolic Panel with Normal Kidney",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"hiv/hiv_baseline", "Comprehensive_Metabolic_Panel_with_Normal_Kidney",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"heart/chf_lab_work", "Comprehensive_Metabolic_Panel_with_Normal_Kidney",
            "1975-2", "mg/dL", 0.3, 1.2},
        {"heart/cabg/preoperative", "High_Sensitivity_Cardiac_Troponin_I",
            "89579-7", "pg/mL", 0.0, 40.0},
        {"heart/chf_lab_work", "High_Sensitivity_Cardiac_Troponin_I",
            "89579-7", "pg/mL", 0.0, 40.0}
    });
  }

  /**
   * Construct a test for a production laboratory observation.
   * @param modulePath Module resource name.
   * @param stateName DiagnosticReport state to execute.
   * @param code Target observation LOINC code.
   * @param unit Expected unit.
   * @param lowerBound Expected lower generation bound.
   * @param upperBound Expected upper generation bound.
   */
  public LabObservationPlausibilityTest(String modulePath, String stateName, String code,
      String unit, double lowerBound, double upperBound) {
    this.modulePath = modulePath;
    this.stateName = stateName;
    this.code = code;
    this.unit = unit;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
  }

  @Test
  public void generatedObservationHasExpectedUnitAndScale() throws Exception {
    Module module = Module.loadFile(Paths.get("modules", modulePath + ".json"), false, null, false);
    double sampleMaximum = Double.NEGATIVE_INFINITY;
    for (long seed = 0; seed < 32; seed++) {
      Person person = newPerson(seed);
      HealthRecord.Encounter encounter = person.record.encounterStart(TIME, EncounterType.WELLNESS);

      // Execute the panel directly so unrelated preceding states do not affect this regression.
      assertTrue(module.getState(stateName).process(person, TIME));
      assertEquals(1, encounter.reports.size());
      HealthRecord.Observation observation = observationWithCode(encounter, code);
      assertEquals(unit, observation.unit);
      assertTrue("Lab result must be numeric", observation.value instanceof Number);
      double value = ((Number) observation.value).doubleValue();
      assertTrue("Unexpected " + code + " value for seed " + seed + ": " + value,
          Double.isFinite(value) && value >= lowerBound && value <= upperBound);
      sampleMaximum = Math.max(sampleMaximum, value);

      if ("heart/cabg/labs_common".equals(modulePath)
          && "Comprehensive_Metabolic_Panel_with_Kidney_Damage".equals(stateName)) {
        // Check that the bilirubin repair preserves renal value ranges without fixing
        // unrelated renal-unit metadata in place. These are not universal CKD intervals.
        assertRenalRange(encounter, "2160-0", 2.5, 3.0);
        assertRenalRange(encounter, "33914-3", 5.0, 14.0);
      }
    }
    if ("89579-7".equals(code)) {
      // Restore the copied 0-0.04 ng/mL scale in pg/mL, not a universal normal hs-troponin cutoff.
      // An upper-bound-only check would incorrectly accept the original 0-0.04 pg/mL values.
      assertTrue("Troponin samples must reach the pg/mL scale", sampleMaximum > 1.0);
    }
  }

  private static Person newPerson(long seed) {
    Person person = new Person(seed);
    person.attributes.put(Person.BIRTHDATE, TIME - 60L * 365 * 24 * 60 * 60 * 1000);
    person.attributes.put(Person.GENDER, "F");
    InsurancePlan plan = mock(InsurancePlan.class);
    when(plan.getDeductible()).thenReturn(BigDecimal.ZERO);
    person.coverage.getPlanHistory().add(new PlanRecord(TIME, plan, TIME + 1));
    person.setProvider(EncounterType.WELLNESS, mock(Provider.class));
    person.setVitalSign(VitalSign.GLUCOSE, 90.0);
    person.setVitalSign(VitalSign.UREA_NITROGEN, 15.0);
    person.setVitalSign(VitalSign.CREATININE, 0.9);
    person.setVitalSign(VitalSign.CALCIUM, 9.5);
    person.setVitalSign(VitalSign.SODIUM, 140.0);
    person.setVitalSign(VitalSign.POTASSIUM, 4.0);
    person.setVitalSign(VitalSign.CHLORIDE, 102.0);
    person.setVitalSign(VitalSign.CARBON_DIOXIDE, 25.0);
    person.setVitalSign(VitalSign.EGFR, 90.0);
    return person;
  }

  private static HealthRecord.Observation observationWithCode(HealthRecord.Encounter encounter,
      String code) {
    List<HealthRecord.Observation> observations = encounter.reports.get(0).observations.stream()
        .filter(observation -> observation.codes.stream()
            .anyMatch(entry -> "LOINC".equals(entry.system) && code.equals(entry.code)))
        .collect(Collectors.toList());
    assertEquals("Expected one observation for " + code, 1, observations.size());
    return observations.get(0);
  }

  private static void assertRenalRange(HealthRecord.Encounter encounter, String code,
      double low, double high) {
    HealthRecord.Observation observation = observationWithCode(encounter, code);
    double value = ((Number) observation.value).doubleValue();
    assertTrue("Unexpected renal branch value for " + code, value >= low && value <= high);
  }
}
