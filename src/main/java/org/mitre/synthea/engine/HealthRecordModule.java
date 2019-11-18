package org.mitre.synthea.engine;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.Random;

/**
 * The HealthRecordModule offers an interface that can be implemented to modify a Sythea Person's
 * HealthRecord. At the end of every time step in the simulation, the Synthea framework will invoke
 * the shouldRun method. If the shouldRun function returns true, the framework will then invoke
 * the process method.
 *
 * HealthRecordModules are intended to simulate actions that happen to an individual's health record.
 * This includes loss or corruption of information through user entry error or information system
 * defects.
 *
 * HealthRecordModules SHOULD NOT be used to simulate clinical interactions on the underlying physical
 * state / circumstances of the Synthea Person. Those should be implemented in Synthea modules.
 */
public interface HealthRecordModule {
  /**
   * Determine whether the module should be run at the given time step.
   * This will be invoked for the module at each time step. It is possible for modules to "skip"
   * steps. So a module may return false and then return true later. An example of this would be
   * for a module that only works on adults.
   *
   * @param person The Synthea person to check on whether the module should be run
   * @param record The person's HealthRecord
   * @param time The current time in the simulation
   * @return True if the module should be invoked by calling process. False otherwise.
   */
  boolean shouldRun(Person person, HealthRecord record, long time);

  /**
   * Perform the operation on the HealthRecord. HealthRecordModules do not need to modify the HealthRecord.
   * HealthRecordModules SHOULD NOT modify the Person. If the person must be modified, then the desired
   * functionality should be implemented as a regular Synthea module.
   *
   * @param person The Synthea person to check on whether the module should be run
   * @param record The person's HealthRecord
   * @param time The current time in the simulation
   * @param random Random generator that should be used when randomness is needed
   */
  void process(Person person, HealthRecord record, long time, Random random);
}
