package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class CarePlanReasonMergeTest {

  @Test
  public void conditionStateReasonIsRecordedWithoutChangingCarePlanCodes() throws Exception {
    Module module = carePlanModule(true);
    Person person = person();
    assertTrue(module.process(person, 1L));
    CarePlan plan = (CarePlan) person.attributes.get("plan");
    assertEquals(1, plan.reasons.size());
    assertEquals(new Code("SNOMED-CT", "73211009", "Diabetes mellitus"), plan.reasons.get(0));
    assertEquals(1, plan.codes.size());
    assertEquals("698360004", plan.codes.get(0).code);
  }

  @Test
  public void repeatedCarePlanStartDoesNotDuplicateReason() throws Exception {
    Module module = carePlanModule(true);
    Person person = person();
    assertTrue(module.process(person, 1L));
    CarePlan original = (CarePlan) person.attributes.get("plan");
    assertTrue(module.getState("Plan").clone().process(person, 2L));
    assertEquals(original, person.attributes.get("plan"));
    assertEquals(1, original.reasons.size());
    assertEquals(1, original.codes.size());
  }

  @Test
  public void reasonMergeDeduplicatesByCodeAndSystem() {
    HealthRecord record = new Person(0L).record;
    CarePlan plan = record.new CarePlan(1L, "careplan");
    Code primary = new Code("SNOMED-CT", "careplan", "Care plan");
    plan.codes.add(primary);
    plan.mergeReasonList(Arrays.asList(
        new Code("SNOMED-CT", "reason", "First description"),
        new Code("SNOMED-CT", "reason", "Alternate description"),
        new Code("ICD-10-CM", "reason", "Different code system")));
    assertEquals(2, plan.reasons.size());
    assertTrue(plan.containsReason("reason", "SNOMED-CT"));
    assertTrue(plan.containsReason("reason", "ICD-10-CM"));
    assertEquals(Collections.singletonList(primary), plan.codes);
  }

  @Test
  public void reasonMergePreservesPreviouslyRecordedReasons() {
    CarePlan plan = new Person(0L).record.new CarePlan(1L, "careplan");
    Code existing = new Code("SNOMED-CT", "existing", "Existing reason");
    Code additional = new Code("SNOMED-CT", "additional", "Additional reason");
    plan.reasons.add(existing);
    plan.mergeReasonList(Arrays.asList(existing, additional));
    plan.mergeReasonList(Collections.singletonList(additional));
    assertEquals(Arrays.asList(existing, additional), plan.reasons);
    assertTrue(plan.codes.isEmpty());
  }

  @Test
  public void carePlanWithoutReasonKeepsItsOriginalCodes() throws Exception {
    Module module = carePlanModule(false);
    Person person = person();
    assertTrue(module.process(person, 1L));
    CarePlan plan = (CarePlan) person.attributes.get("plan");
    assertTrue(plan.reasons.isEmpty());
    assertEquals(1, plan.codes.size());
    assertEquals("698360004", plan.codes.get(0).code);
  }

  private Person person() {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(0L);
    person.record.encounterStart(1L, EncounterType.AMBULATORY);
    return person;
  }

  private Module carePlanModule(boolean withReason) throws Exception {
    JsonObject definition = JsonParser.parseString("{\"name\":\"CarePlan Reason Merge\","
        + "\"states\":{\"Initial\":{\"type\":\"Initial\","
        + "\"direct_transition\":\"Diabetes\"},"
        + "\"Diabetes\":{\"type\":\"ConditionOnset\",\"codes\":[{"
        + "\"system\":\"SNOMED-CT\",\"code\":\"73211009\","
        + "\"display\":\"Diabetes mellitus\"}],\"direct_transition\":\"Plan\"},"
        + "\"Plan\":{\"type\":\"CarePlanStart\",\"codes\":[{"
        + "\"system\":\"SNOMED-CT\",\"code\":\"698360004\","
        + "\"display\":\"Diabetes self management plan\"}],"
        + "\"assign_to_attribute\":\"plan\",\"direct_transition\":\"Terminal\"},"
        + "\"Terminal\":{\"type\":\"Terminal\"}}}").getAsJsonObject();
    if (withReason) {
      definition.getAsJsonObject("states").getAsJsonObject("Plan")
          .addProperty("reason", "Diabetes");
    }
    return new Module(definition, false);
  }
}
