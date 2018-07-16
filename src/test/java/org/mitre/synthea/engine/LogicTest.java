package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.VitalSign;

public class LogicTest {
  private Person person;
  private long time;

  private JsonObject tests;

  /**
   * Setup logic tests.
   * @throws IOException On File IO errors.
   */
  @Before
  public void setup() throws IOException {
    person = new Person(0L);
    time = System.currentTimeMillis();

    Path modulesFolder = Paths.get("src/test/resources/generic");
    Path logicFile = modulesFolder.resolve("logic.json");
    JsonReader reader = new JsonReader(new FileReader(logicFile.toString()));
    tests = new JsonParser().parse(reader).getAsJsonObject();
    reader.close();
  }

  private boolean doTest(String testName) {
    JsonObject definition = tests.getAsJsonObject(testName);
    Logic logic = Utilities.getGson().fromJson(definition, Logic.class);

    return logic.test(person, time);
  }

  @Test
  public void testTrue() {
    assertTrue(doTest("trueTest"));
  }

  @Test
  public void testFalse() {
    assertFalse(doTest("falseTest"));
  }

  @Test
  public void testGenderCondition() {
    person.attributes.put(Person.GENDER, "M");
    assertTrue(doTest("genderIsMaleTest"));

    person.attributes.put(Person.GENDER, "F");
    assertFalse(doTest("genderIsMaleTest"));
  }

  private void setPatientAge(int age) {
    LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of("UTC"));

    LocalDateTime bday = now.minus(age, ChronoUnit.YEARS);
    long birthdate = bday.toInstant(ZoneOffset.UTC).toEpochMilli();
    person.attributes.put(Person.BIRTHDATE, birthdate);
  }

  @Test
  public void testAgeConditionsOnAge35() {
    setPatientAge(35);
    assertTrue(doTest("ageLt40Test"));
    assertTrue(doTest("ageLte40Test"));
    assertFalse(doTest("ageEq40Test"));
    assertFalse(doTest("ageGte40Test"));
    assertFalse(doTest("ageGt40Test"));
    assertTrue(doTest("ageNe40Test"));
  }

  @Test
  public void testAgeConditionsOnAge40() {
    setPatientAge(40);
    assertFalse(doTest("ageLt40Test"));
    assertTrue(doTest("ageLte40Test"));
    assertTrue(doTest("ageEq40Test"));
    assertTrue(doTest("ageGte40Test"));
    assertFalse(doTest("ageGt40Test"));
    assertFalse(doTest("ageNe40Test"));
  }

  @Test
  public void testAgeConditionsOnAge45() {
    setPatientAge(45);
    assertFalse(doTest("ageLt40Test"));
    assertFalse(doTest("ageLte40Test"));
    assertFalse(doTest("ageEq40Test"));
    assertTrue(doTest("ageGte40Test"));
    assertTrue(doTest("ageGt40Test"));
    assertTrue(doTest("ageNe40Test"));
  }

  @Test
  public void test_race_exists() {
    person.attributes.put(Person.RACE, "white");
    assertTrue(doTest("raceExistsTest"));

    person.attributes.put(Person.RACE, "native");
    assertFalse(doTest("raceDoesNotExistTest"));
  }

  @Test
  public void test_date() {
    time = TestHelper.timestamp(2016, 9, 21, 0, 0, 0);
    assertFalse(doTest("before2016Test"));
    assertTrue(doTest("after2000Test"));

    time = TestHelper.timestamp(1981, 4, 28, 0, 0, 0);
    assertTrue(doTest("before2016Test"));
    assertFalse(doTest("after2000Test"));

    time = TestHelper.timestamp(2002, 2, 22, 0, 0, 0);
    assertTrue(doTest("before2016Test"));
    assertTrue(doTest("after2000Test"));

    time = TestHelper.timestamp(2000, 12, 10, 0, 0, 0);
    assertFalse(doTest("beforeSeptemberTest"));
    assertTrue(doTest("afterAprilTest"));
    assertFalse(doTest("inJulyTest"));

    time = TestHelper.timestamp(2004, 2, 8, 0, 0, 0);
    assertTrue(doTest("beforeSeptemberTest"));
    assertFalse(doTest("afterAprilTest"));
    assertFalse(doTest("inJulyTest"));

    time = TestHelper.timestamp(2012, 7, 17, 0, 0, 0);
    assertTrue(doTest("beforeSeptemberTest"));
    assertTrue(doTest("afterAprilTest"));
    assertTrue(doTest("inJulyTest"));

    time = TestHelper.timestamp(2016, 12, 30, 0, 0, 0);
    assertFalse(doTest("beforeHalloween2016Test"));
    assertTrue(doTest("afterIndependenceDay2000Test"));

    time = TestHelper.timestamp(2000, 4, 4, 0, 0, 0);
    assertTrue(doTest("beforeHalloween2016Test"));
    assertFalse(doTest("afterIndependenceDay2000Test"));

    time = TestHelper.timestamp(2007, 9, 20, 0, 0, 0);
    assertTrue(doTest("beforeHalloween2016Test"));
    assertTrue(doTest("afterIndependenceDay2000Test"));
  }

  @Test
  public void test_attribute() {
    String attribute = "Test_Attribute_Key";

    person.attributes.remove(attribute);
    assertFalse(doTest("attributeEqualTo_TestValue_Test"));
    assertTrue(doTest("attributeNilTest"));
    assertFalse(doTest("attributeNotNilTest"));

    person.attributes.put(attribute, "Wrong Value");
    assertFalse(doTest("attributeEqualTo_TestValue_Test"));
    assertFalse(doTest("attributeNilTest"));
    assertTrue(doTest("attributeNotNilTest"));

    person.attributes.put(attribute, "TestValue");
    assertTrue(doTest("attributeEqualTo_TestValue_Test"));
    assertFalse(doTest("attributeNilTest"));
    assertTrue(doTest("attributeNotNilTest"));

    person.attributes.put(attribute, 120);
    assertFalse(doTest("attributeEqualTo_TestValue_Test"));
    assertTrue(doTest("attributeGt100Test"));
    assertFalse(doTest("attributeNilTest"));
    assertTrue(doTest("attributeNotNilTest"));
  }

  @Test
  public void test_symptoms() {
    person.setSymptom("Appendicitis", "PainLevel", 60, false);
    assertTrue(doTest("symptomPainLevelGt50"));
    assertTrue(doTest("symptomPainLevelLte80"));

    // painlevel still 60 here
    person.setSymptom("Appendicitis", "LackOfAppetite", 100, false);
    assertTrue(doTest("symptomPainLevelGt50"));
    assertTrue(doTest("symptomPainLevelLte80"));

    person.setSymptom("Appendicitis", "PainLevel", 10, false);
    assertFalse(doTest("symptomPainLevelGt50"));
    assertTrue(doTest("symptomPainLevelLte80"));

    person.setSymptom("Appicitis", "PainLevel", 100, false);
    assertTrue(doTest("symptomPainLevelGt50"));
    assertFalse(doTest("symptomPainLevelLte80"));
  }

  @Test
  public void test_vital_signs() {
    person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, 100);
    assertFalse(doTest("SystolicBloodPressureGt120"));

    person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, 140);
    assertTrue(doTest("SystolicBloodPressureGt120"));
  }

  @Test(expected = NullPointerException.class)
  public void test_vital_sign_missing() {
    // starts out vital signs clear
    doTest("SystolicBloodPressureGt120");
  }

  @Test(expected = NullPointerException.class)
  public void test_missing_observation() {
    // starts out with no observations
    doTest("mmseObservationGt22");
  }

  @Test
  public void test_observations() {

    HealthRecord.Code mmseCode = new HealthRecord.Code("LOINC", "72107-6",
        "Mini Mental State Examination");

    Observation obs = person.record.observation(time, mmseCode.code, 12);
    obs.codes.add(mmseCode);
    assertFalse(doTest("mmseObservationGt22"));

    person.record = new HealthRecord(person); // clear it out

    obs = person.record.observation(time, mmseCode.code, 29);
    obs.codes.add(mmseCode);
    assertTrue(doTest("mmseObservationGt22"));

    person.record = new HealthRecord(person); // clear it out
    assertFalse(doTest("hasDiabetesObservation"));

    obs = person.record.observation(time, "Blood Panel", "blah blah");
    person.attributes.put("Blood Test Performed", obs);
    assertFalse(doTest("hasDiabetesObservation"));

    obs = person.record.observation(time, "Glucose Panel", "12345");
    person.attributes.put("Diabetes Test Performed", obs);
    assertTrue(doTest("hasDiabetesObservation"));
  }

  @Test
  public void test_condition_condition() {
    person.record = new HealthRecord(person);
    assertFalse(doTest("diabetesConditionTest"));
    assertFalse(doTest("alzheimersConditionTest"));

    HealthRecord.Code diabetesCode = new HealthRecord.Code("SNOMED-CT", "73211009",
        "Diabetes mellitus");

    person.record.conditionStart(time, diabetesCode.code);
    assertTrue(doTest("diabetesConditionTest"));
    assertFalse(doTest("alzheimersConditionTest"));

    time += Utilities.convertTime("years", 10);

    person.record.conditionEnd(time, diabetesCode.code);
    assertFalse(doTest("diabetesConditionTest"));

    HealthRecord.Code alzCode = new HealthRecord.Code("SNOMED-CT", "26929004",
        "Alzheimer's disease (disorder)");

    HealthRecord.Entry cond = person.record.conditionStart(time, alzCode.code);
    person.attributes.put("Alzheimer's Variant", cond);

    assertTrue(doTest("alzheimersConditionTest"));
  }

  @Test
  public void test_careplan_condition() {

    HealthRecord.Code diabetesCode = new HealthRecord.Code("SNOMED-CT", "698360004",
        "Diabetes self management plan");

    person.record = new HealthRecord(person);
    assertFalse(doTest("diabetesCarePlanTest"));
    assertFalse(doTest("anginaCarePlanTest"));

    CarePlan dcp = person.record.careplanStart(time, diabetesCode.code);
    dcp.codes.add(diabetesCode);
    assertTrue(doTest("diabetesCarePlanTest"));
    assertFalse(doTest("anginaCarePlanTest"));

    time += Utilities.convertTime("years", 10);

    person.record.careplanEnd(time, diabetesCode.code, new HealthRecord.Code("SNOMED-CT",
        "444110003", "Type II Diabetes Mellitus Well Controlled"));
    assertFalse(doTest("diabetesCarePlanTest"));

    HealthRecord.Code anginaCode = new HealthRecord.Code("SNOMED-CT", "698360004",
        "Diabetes self management plan");

    CarePlan acp = person.record.careplanStart(time, anginaCode.code);
    acp.codes.add(anginaCode);
    person.attributes.put("Angina_CarePlan", acp);
    assertTrue(doTest("anginaCarePlanTest"));
  }

  @Test
  public void test_ses_category() {
    person.attributes.put(Person.SOCIOECONOMIC_CATEGORY, "High");
    assertTrue(doTest("sesHighTest"));
    assertFalse(doTest("sesMiddleTest"));
    assertFalse(doTest("sesLowTest"));

    person.attributes.put(Person.SOCIOECONOMIC_CATEGORY, "Middle");
    assertFalse(doTest("sesHighTest"));
    assertTrue(doTest("sesMiddleTest"));
    assertFalse(doTest("sesLowTest"));

    person.attributes.put(Person.SOCIOECONOMIC_CATEGORY, "Low");
    assertFalse(doTest("sesHighTest"));
    assertFalse(doTest("sesMiddleTest"));
    assertTrue(doTest("sesLowTest"));
  }

  @Test
  public void test_prior_state() {
    person.history = new LinkedList<>();
    assertFalse(doTest("priorStateDoctorVisitTest"));
    assertFalse(doTest("priorStateCarePlanSinceDoctorVisitTest"));
    assertFalse(doTest("priorStateDoctorVisitWithin3YearsTest"));
    assertFalse(doTest("priorStateCarePlanSinceDoctorVisitWithin3YearsTest"));

    State state = new State.Simple();
    state.name = "CarePlan";
    state.entered = state.exited = time;
    person.history.add(0, state);
    assertFalse(doTest("priorStateDoctorVisitTest"));
    assertTrue(doTest("priorStateCarePlanSinceDoctorVisitTest"));
    assertFalse(doTest("priorStateDoctorVisitWithin3YearsTest"));
    assertTrue(doTest("priorStateCarePlanSinceDoctorVisitWithin3YearsTest"));

    state = new State.Simple();
    state.name = "DoctorVisit";
    state.entered = state.exited = time;
    person.history.add(0, state);
    assertTrue(doTest("priorStateDoctorVisitTest"));
    assertFalse(doTest("priorStateCarePlanSinceDoctorVisitTest"));
    assertTrue(doTest("priorStateDoctorVisitWithin3YearsTest"));
    assertFalse(doTest("priorStateCarePlanSinceDoctorVisitWithin3YearsTest"));

    time += Utilities.convertTime("years", 2);

    state = new State.Simple();
    state.name = "CarePlan";
    state.entered = state.exited = time;
    person.history.add(0, state);
    assertTrue(doTest("priorStateDoctorVisitTest"));
    assertTrue(doTest("priorStateCarePlanSinceDoctorVisitTest"));
    assertTrue(doTest("priorStateDoctorVisitWithin3YearsTest"));
    assertTrue(doTest("priorStateCarePlanSinceDoctorVisitWithin3YearsTest"));

    time += Utilities.convertTime("years", 5);

    assertTrue(doTest("priorStateDoctorVisitTest"));
    assertTrue(doTest("priorStateCarePlanSinceDoctorVisitTest"));
    assertFalse(doTest("priorStateDoctorVisitWithin3YearsTest"));
    assertFalse(doTest("priorStateCarePlanSinceDoctorVisitWithin3YearsTest"));
  }

  @Test
  public void test_and_conditions() {
    assertTrue(doTest("andAllTrueTest"));
    assertFalse(doTest("andOneFalseTest"));
    assertFalse(doTest("andAllFalseTest"));
  }

  @Test
  public void test_or_conditions() {
    assertTrue(doTest("orAllTrueTest"));
    assertTrue(doTest("orOneTrueTest"));
    assertFalse(doTest("orAllFalseTest"));
  }

  @Test
  public void test_at_least_condition() {
    assertTrue(doTest("atLeast3_AllTrueTest"));
    assertTrue(doTest("atLeast3_3TrueTest"));
    assertFalse(doTest("atLeast3_2TrueTest"));
    assertFalse(doTest("atLeast3_NoneTrueTest"));
  }

  @Test
  public void test_at_most_condition() {
    assertFalse(doTest("atMost2_AllTrueTest"));
    assertFalse(doTest("atMost2_3TrueTest"));
    assertTrue(doTest("atMost2_2TrueTest"));
    assertTrue(doTest("atMost2_NoneTrueTest"));
  }

  @Test
  public void test_not_conditions() {
    assertFalse(doTest("notTrueTest"));
    assertTrue(doTest("notFalseTest"));
  }
}
