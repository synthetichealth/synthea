package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.engine.Event;
import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;

public class VitalsValueGeneratorTest {
  private Person person;
  private long time;
  
    /**
     * Setup tests
     */
    @Before
    public void setup() {
      person = new Person(0L);
  
      time = System.currentTimeMillis();
      long birthTime = time - Utilities.convertTime("years", 65);
      person.attributes.put(Person.BIRTHDATE, birthTime);
      person.events.create(birthTime, Event.BIRTH, "Generator.run", true);  
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
      TrendingValueGenerator boundedRandomVariable = new TrendingValueGenerator(person, 0.0, 0.0, 1000.0, 0L, 1000L, null, null);

      assertEquals(0.0, boundedRandomVariable.getValue(0L), 0.0);
      assertEquals(1000.0, boundedRandomVariable.getValue(1000L), 0.0);
      assertEquals(300.0, boundedRandomVariable.getValue(300L), 0.0);
  }

  @Test
  public void testNonZeroStdDevTrendGenerator() {
      TrendingValueGenerator boundedRandomVariable = new TrendingValueGenerator(person, 5.0, 0.0, 1000.0, 0L, 1000L, 2.0, 998.0);

      for (long testTime = -200L; testTime < 1200L; testTime += 100L) {
          double testValue = boundedRandomVariable.getValue(testTime);
          System.out.println(testValue);
          assertTrue(testValue <= 998.0);
          assertTrue(testValue >= 2.0);
      }
  }

  @Test
  public void testSystolicTrendGenerator() {
      TrendingValueGenerator boundedRandomVariable = new TrendingValueGenerator(person, 1.0, 140.0, 140.0, 0L, 1000L, null, null);

      for (long testTime = 0L; testTime < 1200L; testTime += 100L) {
          double systolicValue = boundedRandomVariable.getValue(testTime);
          System.out.println(systolicValue);
      }
  }
}
