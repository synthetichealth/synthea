package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Person;

/**
 * At a wellness encounter, both the Wellness Encounters module and the kidney_conditions
 * submodule of Metabolic Syndrome Care can record a basic metabolic panel. A patient should
 * get only one of them in the same encounter.
 */
public class MetabolicPanelTest {
  private static final String DIABETES = "44054006";

  private static Module wellness;
  private static Module kidneyConditions;
  private Person person;
  private long time;

  /**
   * Load the two modules that record a metabolic panel at a wellness encounter.
   * @throws Exception on configuration loading error
   */
  @BeforeClass
  public static void loadModules() throws Exception {
    TestHelper.loadTestProperties();
    wellness = Module.getModuleByPath("wellness_encounters");
    kidneyConditions = Module.getModuleByPath("metabolic_syndrome/kidney_conditions");
  }

  /**
   * Create a patient with diabetes, who gets a metabolic panel from the Wellness Encounters
   * module.
   */
  @Before
  public void setup() {
    person = new Person(0L);
    time = System.currentTimeMillis();
    person.record.present.put(DIABETES, person.record.new Entry(time, DIABETES));
  }

  private String next(Module module, String state) {
    return module.getState(state).getTransition().follow(person, time);
  }

  private boolean wellnessRecordsPanel() {
    return next(wellness, "Lab_MetabolicPanel").equals("Record_MetabolicPanel");
  }

  private boolean kidneyConditionsRecordsPanel() {
    String diagnosis = next(kidneyConditions, "check CKD");
    if (diagnosis.equals("Check_Nephropathy")) {
      return false;
    }
    return next(kidneyConditions, diagnosis).startsWith("Record_MetabolicPanel");
  }

  @Test
  public void oneMetabolicPanelPerWellnessEncounter() {
    for (int ckd = 0; ckd <= 5; ckd++) {
      person.attributes.put("ckd", ckd);
      int panels = (wellnessRecordsPanel() ? 1 : 0) + (kidneyConditionsRecordsPanel() ? 1 : 0);
      assertEquals("metabolic panels for a patient with diabetes and ckd " + ckd, 1, panels);
    }
  }
}
