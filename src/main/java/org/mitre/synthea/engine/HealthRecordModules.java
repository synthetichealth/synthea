package org.mitre.synthea.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * A singleton class for managing all of the implementations of HealthRecordModule.
 */
public class HealthRecordModules {
  private static HealthRecordModules instance;
  private List<HealthRecordModule> registeredModules;

  private HealthRecordModules() {
    this.registeredModules = new ArrayList<>();
  }

  /**
   * Get the singleton instance of HealthRecordModules.
   * @return the one
   */
  public static HealthRecordModules getInstance() {
    if (instance == null) {
      instance = new HealthRecordModules();
    }
    return instance;
  }

  /**
   * Add a HealthRecordModule to be run during the simulation.
   * @param module The module to add
   */
  public void registerModule(HealthRecordModule module) {
    this.registeredModules.add(module);
  }

  /**
   * Runs all of the registered implementations of HealthRecordModule. Will first check to see if
   * the module should be run by invoking... shouldRun. If it should run, will call process on
   * the module.
   * <p>
   * It's unlikely that this method should be called by anything outside of Generator.
   * </p>
   * @param person The Person to run on
   * @param record The HealthRecord to potentially modify
   * @param time The current time in the simulation
   * @param step The time step for the simulation
   * @param random The source of randomness that modules should use
   */
  public void executeAll(Person person, HealthRecord record, long time, long step, Random random) {
    long start = time - step;
    List<HealthRecord.Encounter> encountersThisStep = record.encounters.stream()
        .filter(e -> e.start >= start)
        .collect(Collectors.toList());
    this.registeredModules.forEach(m -> {
      if (m.shouldRun(person, record, time)) {
        m.process(person, encountersThisStep, time, random);
      }
    });
  }
}
