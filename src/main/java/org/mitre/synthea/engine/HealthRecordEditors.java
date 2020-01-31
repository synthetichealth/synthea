package org.mitre.synthea.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * A singleton class for managing all of the implementations of HealthRecordEditor.
 */
public class HealthRecordEditors {
  private static HealthRecordEditors instance;
  private List<HealthRecordEditor> registeredEditors;

  private HealthRecordEditors() {
    this.registeredEditors = new ArrayList<>();
  }

  /**
   * Get the singleton instance of HealthRecordEditors.
   * @return the one
   */
  public static HealthRecordEditors getInstance() {
    if (instance == null) {
      instance = new HealthRecordEditors();
    }
    return instance;
  }

  /**
   * Add a HealthRecordEditor to be run during the simulation.
   * @param editor The editor to add
   */
  public void registerEditor(HealthRecordEditor editor) {
    this.registeredEditors.add(editor);
  }

  /**
   * Runs all of the registered implementations of HealthRecordEditor. Will first check to see if
   * the editor should be run by invoking... shouldRun. If it should run, will call process on
   * the editor.
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
    if (this.registeredEditors.size() > 0) {
      long start = time - step;
      List<HealthRecord.Encounter> encountersThisStep = record.encounters.stream()
          .filter(e -> e.start >= start)
          .collect(Collectors.toList());
      this.registeredEditors.forEach(m -> {
        if (m.shouldRun(person, record, time)) {
          m.process(person, encountersThisStep, time, random);
        }
      });
    }
  }

  /**
   * Remove all registered editors
   */
  public void resetEditors() {
    this.registeredEditors = new ArrayList();
  }
}
