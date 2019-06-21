package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mitre.synthea.modules.LifecycleModule.bmi;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

public class WeightLossModuleTest {
  private WeightLossModule mod;

  @Before
  public void before() {
    this.mod = new WeightLossModule();
  }

  @Test
  public void testFirstYearOfManagement() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long nextMonth = TestHelper.timestamp(2000, 2, 1, 0, 0, 0);
    long nextYearMonth = TestHelper.timestamp(2001, 2, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    assertTrue(mod.firstYearOfManagement(person, nextMonth));
    assertFalse(mod.firstYearOfManagement(person, nextYearMonth));
  }

  @Test
  public void testFirstFiveYearsOfManagement() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long nextYearMonth = TestHelper.timestamp(2001, 2, 1, 0, 0, 0);
    long tooLong = TestHelper.timestamp(2005, 2, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    assertTrue(mod.firstFiveYearsOfManagement(person, nextYearMonth));
    assertFalse(mod.firstYearOfManagement(person, tooLong));
  }

  @Test
  public void testAdultWeightLoss() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long sixMonths = TestHelper.timestamp(2000, 7, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 200d);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_PERCENTAGE, 0.1d);
    double weight = mod.adultWeightLoss(person, sixMonths);
    assertEquals(190, weight, 0.1);
  }

  @Test
  public void testAdultRegression() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long twoYears = TestHelper.timestamp(2002, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 200d);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_PERCENTAGE, 0.1d);
    double weight = mod.adultRegression(person, twoYears);
    assertEquals(185, weight, 0.1);
  }

  @Test
  public void testPediatricRegression() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long twoYears = TestHelper.timestamp(2002, 1, 1, 0, 0, 0);
    long threeYears = TestHelper.timestamp(2003, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, 0.9);
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, 0.75);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_BMI_CHANGE, 1.1d);
    double weight = mod.pediatricRegression(person, twoYears);
    assertEquals(53.83, weight, 0.1);
    weight = mod.pediatricRegression(person, threeYears);
    assertEquals(56.24, weight, 0.1);
  }

  @Test
  public void testPediatricWeightLoss() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long sixMonths = TestHelper.timestamp(2000, 7, 2, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, 0.9);
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, 0.75);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_BMI_CHANGE, 1.1d);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 41.96d);
    double weight = mod.pediatricWeightLoss(person, sixMonths);
    assertEquals(42.32, weight, 0.1);
  }

  @Test
  public void testProcess() {
    long sixMonths = TestHelper.timestamp(2000, 7, 2, 0, 0, 0);
    long oneYear = TestHelper.timestamp(2000, 12, 31, 0, 0, 0);
    long sixYears = TestHelper.timestamp(2006, 1, 1, 0, 0, 0);
    Person person = thirtyYearOld();
    person.attributes.put(WeightLossModule.ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_ADHERENCE, true);
    person.attributes.put(WeightLossModule.LONG_TERM_WEIGHT_LOSS, true);
    mod.process(person, sixMonths);
    double weight = person.getVitalSign(VitalSign.WEIGHT, sixMonths);
    assertEquals(129.2, weight, 0.1);
    mod.process(person, oneYear);
    mod.process(person, sixYears);
    weight = person.getVitalSign(VitalSign.WEIGHT, sixYears);
    assertEquals(122.4, weight,0.1);
    person = thirtyYearOld();
    person.attributes.put(WeightLossModule.ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_ADHERENCE, false);
    person.attributes.put(WeightLossModule.LONG_TERM_WEIGHT_LOSS, true);
    mod.process(person, sixMonths);
    weight = person.getVitalSign(VitalSign.WEIGHT, sixMonths);
    assertEquals(136, weight, 0.1);

    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, 0.9);
    person.setVitalSign(VitalSign.HEIGHT, 143d);
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, 0.75);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_BMI_CHANGE, 1.1d);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 41.96d);
    person.attributes.put(WeightLossModule.ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_ADHERENCE, true);
    person.attributes.put(WeightLossModule.LONG_TERM_WEIGHT_LOSS, true);
    mod.process(person, sixMonths);
    weight = person.getVitalSign(VitalSign.WEIGHT, sixMonths);
    assertEquals(42.32, weight, 0.1);
  }

  @Test
  public void testMeetsWeightManagementThresholds() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.HEIGHT, 139);
    person.setVitalSign(VitalSign.WEIGHT, 41);
    person.setVitalSign(VitalSign.BMI, bmi(139, 41));
    assertTrue(mod.meetsWeightManagementThresholds(person, start));
    person.setVitalSign(VitalSign.WEIGHT, 35);
    person.setVitalSign(VitalSign.BMI, bmi(139, 35));
    assertFalse(mod.meetsWeightManagementThresholds(person, start));
    assertTrue(mod.meetsWeightManagementThresholds(thirtyYearOld(), start));
    Person lighterThirty = thirtyYearOld();
    lighterThirty.setVitalSign(VitalSign.WEIGHT, 85);
    lighterThirty.setVitalSign(VitalSign.BMI, bmi(175, 85));
    assertFalse(mod.meetsWeightManagementThresholds(lighterThirty, start));
  }

  /*
  30 year old 300 lb man in the year 2000
   */
  private Person thirtyYearOld() {
    long birthDay = TestHelper.timestamp(1970, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, 0.9);
    person.setVitalSign(VitalSign.WEIGHT, 136);
    person.setVitalSign(VitalSign.HEIGHT, 175);
    person.setVitalSign(VitalSign.BMI, bmi(175, 136));
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_PERCENTAGE, 0.1d);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 136d);
    return person;
  }
}
