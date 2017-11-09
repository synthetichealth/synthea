package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.mitre.synthea.TestHelper.timestamp;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

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
}
