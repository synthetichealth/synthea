package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class WellnessEncounterEgfrTest {
  private static final long TIME = 1577836800000L;
  private static Module wellness;

  /**
   * Load the production wellness encounters module.
   * @throws Exception if the module cannot be loaded
   */
  @BeforeClass
  public static void loadModule() throws Exception {
    wellness = Module.loadFile(Paths.get("modules", "wellness_encounters.json"),
        false, null, false);
  }

  private static String stateAfterEgfrCheck(String activeConditionCode) {
    Person person = new Person(0L);
    if (activeConditionCode != null) {
      person.record.present.put(activeConditionCode,
          person.record.new Entry(TIME, activeConditionCode));
    }
    return wellness.getState("check_eGFR").transition(person, TIME);
  }

  @Test
  public void noQualifyingConditionSkipsEgfr() {
    assertEquals("Smoker_Status_Observation", stateAfterEgfrCheck(null));
  }

  @Test
  public void diabetesRecordsEgfr() {
    assertEquals("Record_EGFR", stateAfterEgfrCheck("44054006"));
  }

  @Test
  public void hypertensionRecordsEgfr() {
    // 59621000 is the condition that hypertension.json diagnoses (Diagnose_Hypertension).
    assertEquals("Record_EGFR", stateAfterEgfrCheck("59621000"));
  }
}
