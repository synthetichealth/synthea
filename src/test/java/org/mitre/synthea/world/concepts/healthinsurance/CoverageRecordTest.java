package org.mitre.synthea.world.concepts.healthinsurance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.TimeZone;

import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class CoverageRecordTest {

  // The enrollment period should start exactly on each birthday (UTC),
  // whatever the default time zone of the JVM is.
  private static void assertEnrollmentOnEachBirthday(String zone, LocalDate birthDate,
      int years) {
    TimeZone defaultZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(zone));
    try {
      long birthTime = Utilities.localDateToTimestamp(birthDate);
      Person person = new Person(0L);
      person.attributes.put(Person.BIRTHDATE, birthTime);
      CoverageRecord coverage = new CoverageRecord(person);
      for (int age = 0; age <= years; age++) {
        long birthday = Utilities.getAnniversary(birthTime, age);
        assertFalse("Enrollment before birthday " + age + " in " + zone,
            coverage.newEnrollmentPeriod(birthday - 1));
        assertTrue("No enrollment on birthday " + age + " in " + zone,
            coverage.newEnrollmentPeriod(birthday));
      }
    } finally {
      TimeZone.setDefault(defaultZone);
    }
  }

  @Test
  public void enrollmentOnEachBirthdayInSeoul() {
    // Asia/Seoul was UTC+8:30 from 1954 to 1961.
    assertEnrollmentOnEachBirthday("Asia/Seoul", LocalDate.of(1950, 1, 1), 65);
  }

  @Test
  public void enrollmentOnEachBirthdayInNewYork() {
    // Daylight saving time started on 8 March 2009 and on 14 March 2010.
    assertEnrollmentOnEachBirthday("America/New_York", LocalDate.of(2009, 3, 10), 10);
  }
}
