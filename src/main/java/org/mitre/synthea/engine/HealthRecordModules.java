package org.mitre.synthea.engine;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HealthRecordModules {
  private static HealthRecordModules instance;
  private List<HealthRecordModule> registeredModules;

  private HealthRecordModules() {
    this.registeredModules = new ArrayList<>();
  }

  public static HealthRecordModules getInstance() {
    if(instance == null) {
      instance = new HealthRecordModules();
    }
    return instance;
  }

  public void registerModule(HealthRecordModule module) {
    this.registeredModules.add(module);
  }

  public void executeAll(Person person, HealthRecord record, long time, Random random) {
    this.registeredModules.forEach(m -> {
      if (m.shouldRun(person, record, time)) {
        m.process(person, record, time, random);
      }
    });
  }
}
