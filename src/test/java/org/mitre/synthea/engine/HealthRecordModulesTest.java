package org.mitre.synthea.engine;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class HealthRecordModulesTest {
  class Dummy implements HealthRecordModule {

    @Override
    public boolean shouldRun(Person person, HealthRecord record, long time) {
      return time > 1000;
    }

    @Override
    public void process(Person person, List<HealthRecord.Encounter> encounters, long time, Random random) {
      person.attributes.put(Person.ZIP, "01730");
    }
  }

  @Test
  public void getInstance() {
    assertNotNull(HealthRecordModules.getInstance());
  }

  @Test
  public void executeAll() {
    HealthRecordModules hrm = HealthRecordModules.getInstance();
    hrm.registerModule(new Dummy());
    Person p = new Person(1);
    hrm.executeAll(p, new HealthRecord(p), 1, 1, new Random());
    assertNull(p.attributes.get(Person.ZIP));
    hrm.executeAll(p, new HealthRecord(p), 1100, 1, new Random());
    assertEquals("01730", p.attributes.get(Person.ZIP));
  }
}