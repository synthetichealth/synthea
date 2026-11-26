package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.withSettings;

import java.nio.file.Paths;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;
import org.mockito.Mockito;

/**
 * Checks states that read an attribute set elsewhere in the same module.
 */
public class ModuleAttributeNameTest {
  private static final long TIME = 1577836800000L;
  private Person person;

  @BeforeClass
  public static void loadPayers() {
    PayerManager.loadPayers(new Location(Config.get("test_state.default", "Massachusetts"), null));
  }

  /**
   * Create a 70 year old patient with a wellness provider and no insurance.
   */
  @Before
  public void setup() {
    person = new Person(0L);
    person.history = new LinkedList<>();
    Provider provider = Mockito.mock(Provider.class, withSettings().serializable());
    Mockito.when(provider.getResourceID()).thenReturn("Mock-UUID");
    person.setProvider(EncounterType.WELLNESS, provider);
    long birthTime = TIME - Utilities.convertTime("years", 70);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.coverage.setPlanToNoInsurance(birthTime);
    person.coverage.setPlanToNoInsurance(TIME + Utilities.convertTime("years", 1));
  }

  private static Module load(String... path) throws Exception {
    return Module.loadFile(Paths.get("modules", path), path.length > 1, null, false);
  }

  private void assignCondition(String attribute, String code, String display) {
    HealthRecord.Entry condition = person.record.conditionStart(TIME, code);
    condition.codes.add(new Code("SNOMED-CT", code, display));
    person.attributes.put(attribute, condition);
  }

  private String reasonCode(Module module, String procedureState) {
    State procedure = module.getState(procedureState).clone();
    procedure.process(person, TIME);
    HealthRecord.Procedure recorded = (HealthRecord.Procedure) procedure.entry;
    assertEquals(procedureState + " reasons", 1, recorded.reasons.size());
    return recorded.reasons.get(0).code;
  }

  @Test
  public void denturesSurgeryProceduresUseTheSurgeryReason() throws Exception {
    Module dentures = load("dentures.json");
    // set by "Needs Torus Palatinus Removed" (assign_to_attribute: denture_surgery_reason)
    assignCondition("denture_surgery_reason", "46752004", "Torus palatinus (disorder)");
    for (String state : new String[] {"Dental_Anesthesia", "Dental_surgical_procedure",
        "Postoperative_care_for_dental_procedure"}) {
      assertEquals(state, "46752004", reasonCode(dentures, state));
    }
  }

  @Test
  public void cabgDischargeToWardUsesCabgHistory() throws Exception {
    Module postop = load("heart", "cabg", "postop.json");
    // set by History_of_CABG in heart/cabg/operation.json
    assignCondition("cabg_history", "399261000",
        "History of coronary artery bypass grafting (situation)");
    assertEquals("399261000", reasonCode(postop, "Discharge to Ward"));
  }

  @Test
  public void savrrLastIcuDayRecordsEcg() throws Exception {
    Module postop = load("heart", "avrr", "savrr_postop.json");
    person.attributes.put("savrr_icu_los", 2);
    State beginDay = postop.getState("Begin Day").clone();
    beginDay.process(person, TIME); // decrements savrr_icu_los to 1
    assertEquals("ICU_ECG", beginDay.transition(person, TIME));
  }
}
