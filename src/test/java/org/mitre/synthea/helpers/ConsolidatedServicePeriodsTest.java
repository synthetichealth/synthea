package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.ConsolidatedServicePeriods.ConsolidatedServicePeriod;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.geography.Location;

public class ConsolidatedServicePeriodsTest {
  @BeforeClass
  public static void setup() {
    String testStateDefault = Config.get("test_state.default", "Massachusetts");
    PayerManager.loadPayers(new Location(testStateDefault, null));
  }

  @Test
  public void testEncounterConsolidation() {
    Person person = new Person(System.currentTimeMillis());
    Long time = System.currentTimeMillis();
    int age = 67;
    long birthTime = time - Utilities.convertTime("years", age);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.coverage.setPlanToNoInsurance(birthTime);
    long now = Instant.now().toEpochMilli();
    person.coverage.setPlanToNoInsurance(now + Utilities.convertTime("days", 9));
    Provider p1 = new Provider();
    p1.npi = "p1";
    Provider p2 = new Provider();
    p2.npi = "p2";
    Encounter e = person.record.encounterStart(now, HealthRecord.EncounterType.HOME);
    e.provider = p1;
    e = person.record.encounterStart(now + Utilities.convertTime("days", 3),
            HealthRecord.EncounterType.HOME);
    e.provider = p1;
    e = person.record.encounterStart(now + Utilities.convertTime("days", 2),
            HealthRecord.EncounterType.HOME);
    e.provider = p1;
    e = person.record.encounterStart(now + Utilities.convertTime("days", 6),
            HealthRecord.EncounterType.HOME);
    e.provider = p1;
    e = person.record.encounterStart(now + Utilities.convertTime("days", 7),
            HealthRecord.EncounterType.HOME);
    e.provider = p2;
    e = person.record.encounterStart(now + Utilities.convertTime("days", 8),
            HealthRecord.EncounterType.HOME);
    e.provider = p2;

    ConsolidatedServicePeriods consolidated =
            new ConsolidatedServicePeriods(Utilities.convertTime("days", 2));
    for (HealthRecord.Encounter encounter: person.record.encounters) {
      consolidated.addEncounter(encounter);
    }

    List<ConsolidatedServicePeriod> periods = consolidated.getPeriods();
    assertEquals(3, periods.size());
    assertEquals(3, periods.get(0).getEncounters().size());
    assertEquals("p1", periods.get(0).getProvider().npi);
    assertEquals(1, periods.get(1).getEncounters().size());
    assertEquals("p1", periods.get(1).getProvider().npi);
    assertEquals(2, periods.get(2).getEncounters().size());
    assertEquals("p2", periods.get(2).getProvider().npi);
  }
}
