package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;

public class VitalsValueGeneratorTest {
  private Person person;
  private long time;

  private static final long ONE_DAY = 1 * 24 * 60 * 60 * 1000L;

  /**
   * Setup tests.
   */
  @Before
  public void setup() {
    person = new Person(0L);

    time = System.currentTimeMillis();
    long birthTime = time - Utilities.convertTime("years", 65);
    person.attributes.put(Person.BIRTHDATE, birthTime);
  }

  @Test
  public void testConstantValueGenerator() {
    ValueGenerator constValueGenerator = new ConstantValueGenerator(person, 100.0);

    for (long testTime = 0L; testTime < 10000000L; testTime += 100000L) {
      assertEquals(100.0, constValueGenerator.getValue(testTime), 0.0);
    }
  }

  @Test
  public void testZeroStdDevTrendGenerator() {
    TrendingValueGenerator boundedRandomVariable =
        new TrendingValueGenerator(person, 0.0, 0.0, 1000.0, 0L, 1000L, null, null);

    assertEquals(0.0, boundedRandomVariable.getValue(0L), 0.0);
    assertEquals(1000.0, boundedRandomVariable.getValue(1000L), 0.0);
    assertEquals(300.0, boundedRandomVariable.getValue(300L), 0.0);
  }

  @Test
  public void testNonZeroStdDevTrendGenerator() {
    TrendingValueGenerator boundedRandomVariable =
        new TrendingValueGenerator(person, 5.0, 0.0, 1000.0, 0L, 1000L, 2.0, 998.0);

    for (long testTime = -200L; testTime < 1200L; testTime += 100L) {
      double testValue = boundedRandomVariable.getValue(testTime);
      // System.out.println(testValue);
      assertTrue(testValue <= 998.0);
      assertTrue(testValue >= 2.0);
    }
  }

  @Test
  public void testToString() {
    TrendingValueGenerator boundedRandomVariable =
        new TrendingValueGenerator(person, 5.0, 0.0, 1000.0, 0L, 1000L, 2.0, 998.0);
    assertNotNull(boundedRandomVariable.toString());
  }

  @Test
  public void testSystolicTrendGenerator() {
    BloodPressureValueGenerator bloodPressureValueGenerator = new BloodPressureValueGenerator(
        person, BloodPressureValueGenerator.SysDias.SYSTOLIC);
    for (long time = 0L; time < ONE_DAY * 100; time += ONE_DAY) {
      double testValue = bloodPressureValueGenerator.getValue(time);
      assertTrue("Systolic BP should always be >= 70 mmHg", testValue >= 70.0);
    }
  }

  /**
   * Regression test for issue #1413: when multiple hypertension drugs are active,
   * combined medication impacts can drive the calculated BP below zero, causing a
   * RuntimeException in the Framingham lookup tables. The generator must clamp
   * the result to a physiologically plausible minimum.
   */
  @Test
  public void testSystolicBloodPressureNeverNegativeWithManyMedications() {
    person.attributes.put("hypertension", true);

    // Activate every HTN drug from htn_drugs.csv so medication impacts are maximised.
    String[] htnDrugCodes = {
        "197499", "313988", "313096", "310818", "977880", "310798",
        "314076", "979492", "1091646", "1235144", "830877", "197361",
        "308136", "866514", "866412", "197381", "197383", "905395",
        "197987", "197745", "197626"
    };
    for (String code : htnDrugCodes) {
      person.record.medicationStart(time, code, true);
    }

    BloodPressureValueGenerator systolicGenerator = new BloodPressureValueGenerator(
        person, BloodPressureValueGenerator.SysDias.SYSTOLIC);
    BloodPressureValueGenerator diastolicGenerator = new BloodPressureValueGenerator(
        person, BloodPressureValueGenerator.SysDias.DIASTOLIC);

    for (long t = time; t < time + ONE_DAY * 365; t += ONE_DAY) {
      double systolic = systolicGenerator.getValue(t);
      double diastolic = diastolicGenerator.getValue(t);
      assertTrue("Systolic BP must not go below 70 mmHg, got: " + systolic, systolic >= 70.0);
      assertTrue("Diastolic BP must not go below 40 mmHg, got: " + diastolic, diastolic >= 40.0);
    }
  }
}
