package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.mitre.synthea.helpers.ConsolidatedServicePeriods.ConsolidatedServicePeriod;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

public class ConsolodatedServicePeriodsTest {
  @Test
  public void testEncounterConsolidation() {
    Person person = new Person(System.currentTimeMillis());
    long now = Instant.now().toEpochMilli();
    person.record.encounterStart(now, HealthRecord.EncounterType.HOME);
    person.record.encounterStart(now + Utilities.convertTime("days", 3),
            HealthRecord.EncounterType.HOME);
    person.record.encounterStart(now + Utilities.convertTime("days", 2),
            HealthRecord.EncounterType.HOME);
    person.record.encounterStart(now + Utilities.convertTime("days", 6),
            HealthRecord.EncounterType.HOME);

    ConsolidatedServicePeriods consolidated =
            new ConsolidatedServicePeriods(Utilities.convertTime("days", 2));
    for (HealthRecord.Encounter encounter: person.record.encounters) {
      consolidated.addEncounter(encounter);
    }

    List<ConsolidatedServicePeriod> periods = consolidated.getPeriods();
    assertEquals(2, periods.size());
    assertEquals(3, periods.get(0).getEncounters().size());
    assertEquals(1, periods.get(1).getEncounters().size());
  }
}
