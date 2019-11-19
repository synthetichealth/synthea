package org.mitre.synthea.engine;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

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

  public void executeAll(Person person, HealthRecord record, long time, long step, Random random) {
    long start = time - step;
    List<HealthRecord.Encounter> encountersThisStep = record.encounters.stream()
        .filter(e -> e.start > start )
        .collect(Collectors.toList());
    this.registeredModules.forEach(m -> {
      if (m.shouldRun(person, record, time)) {
        m.process(person, encountersThisStep, time, random);
      }
    });
  }
}
