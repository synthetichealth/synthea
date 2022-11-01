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
    // TODO: Right now this only tests for a lack of crashes. What might be other good criteria?
    BloodPressureValueGenerator bloodPressureValueGenerator = new BloodPressureValueGenerator(
        person, BloodPressureValueGenerator.SysDias.SYSTOLIC);
    for (long time = 0L; time < ONE_DAY * 100; time += ONE_DAY) {
      double testValue = bloodPressureValueGenerator.getValue(time);
      System.out.println("Value @ " + time + ": " + testValue);
    }
  }
}
