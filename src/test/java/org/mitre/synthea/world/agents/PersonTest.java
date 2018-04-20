package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mitre.synthea.TestHelper.timestamp;

import java.io.IOException;

import org.apache.sis.geometry.DirectPosition2D;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.concepts.HealthRecord;

public class PersonTest {
  private Person person;

  @Before
  public void setup() throws IOException {
    person = new Person(0L);
  }

  @Test
  public void testAge() {
    long birthdate;
    long now;

    // first set of test cases, birthdate = 0, (1/1/1970)
    birthdate = 0;

    now = 0;
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 0);

    now = timestamp(2017, 10, 10, 10, 10, 10);
    testAgeYears(birthdate, now, 47);

    now = timestamp(1970, 1, 29, 5, 5, 5); // less than a month has passed
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 0);

    // second set of test cases, birthdate = Apr 7, 2016 (Synthea repo creation date)
    birthdate = timestamp(2016, 4, 7, 17, 14, 0);

    now = birthdate;
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 0);

    now = timestamp(2016, 5, 7, 17, 14, 0);
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 1);

    now = timestamp(2017, 4, 6, 17, 14, 0);
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 11);
  }

  private void testAgeYears(long birthdate, long now, long expectedAge) {
    person.attributes.put(Person.BIRTHDATE, birthdate);
    assertEquals(expectedAge, person.ageInYears(now));
  }

  private void testAgeMonths(long birthdate, long now, long expectedAge) {
    person.attributes.put(Person.BIRTHDATE, birthdate);
    assertEquals(expectedAge, person.ageInMonths(now));
  }
  
  @Test
  public void testCareSeekingBehavior() {
    // careseeking depends on insurance, so run the insurance module once to set it up
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.INCOME, 100_000);
    person.attributes.put(Person.INCOME_LEVEL, 1.0);
    person.attributes.put(Person.EDUCATION_LEVEL, 1.0);
    person.attributes.put(Person.COORDINATE, new DirectPosition2D(-71, 42));
    long now = System.currentTimeMillis();
    new HealthInsuranceModule().process(person, now);
    
    assertTrue(person.doesSeekCare(true, now));
    assertFalse(person.doesSeekCare(false, now));
    
    HealthRecord.Code med = new HealthRecord.Code("RxNorm", "12345", "Examplitol");
    assertEquals(1.0, person.adherenceLevel(med, now), 0); // TODO: pick some actual numbers from the literature
  }
}
