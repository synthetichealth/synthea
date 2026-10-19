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
public class GlobulinObservationTest {
  private final String modulePath;
  private final String stateName;
  private final double upperBound;

  /**
   * Cover each affected panel and the existing correctly labelled gallstones panel.
   * @return Module paths, panel states and expected upper bounds in g/dL.
   */
  @Parameters(name = "{0}: {1}")
  public static Collection<Object[]> panels() {
    return Arrays.asList(new Object[][] {
        {"colorectal_cancer", "Record_CMP", 3.5},
        {"dialysis", "Record_CMP", 3.5},
        {"lung_cancer", "Record_CMP", 3.5},
        {"lung_cancer", "Record_CMP_2", 3.5},
        {"veteran_hyperlipidemia", "Record_CMP", 3.5},
        {"veteran_hyperlipidemia", "Record_CMP_2", 3.5},
        {"veteran_prostate_cancer", "Record_CMP", 3.5},
        {"gallstones", "Comprehensive_Metabolic_Panel", 4.0}
    });
  }

  /**
   * Construct a test for one production panel.
   * @param modulePath Module resource name.
   * @param stateName DiagnosticReport state to execute.
   * @param upperBound Expected upper bound in g/dL.
   */
  public GlobulinObservationTest(String modulePath, String stateName, double upperBound) {
    this.modulePath = modulePath;
    this.stateName = stateName;
    this.upperBound = upperBound;
  }

  @Test
  public void globulinUnitMatchesGeneratedValue() throws Exception {
    Module module = Module.loadFile(Paths.get("modules", modulePath + ".json"), false, null, false);
    long time = 1577836800000L;
    Person person = new Person(1738L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    InsurancePlan plan = mock(InsurancePlan.class);
    when(plan.getDeductible()).thenReturn(BigDecimal.ZERO);
    person.coverage.getPlanHistory().add(new PlanRecord(time, plan, time + 1));
    person.setProvider(EncounterType.WELLNESS, mock(Provider.class));
    HealthRecord.Encounter encounter = person.record.encounterStart(time, EncounterType.WELLNESS);

    // These values provide the other analytes required by the production panels.
    VitalSign[] vitalSigns = {VitalSign.GLUCOSE, VitalSign.UREA_NITROGEN, VitalSign.CREATININE,
        VitalSign.CALCIUM, VitalSign.SODIUM, VitalSign.POTASSIUM, VitalSign.CHLORIDE,
        VitalSign.CARBON_DIOXIDE};
    for (VitalSign vitalSign : vitalSigns) {
      person.setVitalSign(vitalSign, 1.0);
    }

    assertTrue(module.getState(stateName).process(person, time));
    assertEquals(1, encounter.reports.size());
    List<HealthRecord.Observation> globulins = encounter.reports.get(0).observations.stream()
        .filter(observation -> observation.codes.stream()
            .anyMatch(code -> "LOINC".equals(code.system) && "10834-0".equals(code.code)))
        .collect(Collectors.toList());
    assertEquals(1, globulins.size());
    HealthRecord.Observation globulin = globulins.get(0);
    assertEquals("g/dL", globulin.unit);
    double value = ((Number) globulin.value).doubleValue();
    assertTrue("Globulin value must be on the g/dL scale", value >= 2.0 && value <= upperBound);
  }
}
