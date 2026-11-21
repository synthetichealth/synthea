package org.mitre.synthea.world.agents.behaviors.planfinder;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.TimeZone;

import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;

public class PlanFinderBestRatesTest {

  private static long time(String instant) {
    return Instant.parse(instant).toEpochMilli();
  }

  // "The last 12 months" should not depend on the default time zone of the JVM.
  // Daylight saving time started on 8 March 2009 and on 14 March 2010 in New York, so
  // 10 March was on EDT in 2009 and on EST in 2010.
  @Test
  public void twelveMonthEncounterCountInNewYork() throws Exception {
    TestHelper.loadTestProperties();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time("2009-01-01T00:00:00Z"));
    person.attributes.put(Person.STATE, Config.get("test_state.default", "Massachusetts"));
    PayerManager.clear();
    PayerManager.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    person.coverage.setPlanToNoInsurance(time("2009-03-10T11:30:00Z"));
    // One visit 30 minutes before the start of the last 12 months, one 30 minutes after it.
    person.record.encounterStart(time("2009-03-10T11:30:00Z"), EncounterType.WELLNESS);
    person.record.encounterStart(time("2009-03-10T12:30:00Z"), EncounterType.WELLNESS);

    TimeZone defaultZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    try {
      assertEquals(1, new PlanFinderBestRates().twelveMonthEncounterCount(person.record,
          time("2010-03-10T12:00:00Z")));
    } finally {
      TimeZone.setDefault(defaultZone);
    }
  }
}
