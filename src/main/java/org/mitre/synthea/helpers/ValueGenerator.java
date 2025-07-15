package org.mitre.synthea.helpers;

import java.io.Serializable;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.world.agents.Person;

/**
 * The base for all value generators.
 * A value generator can determine a numerical value as a function of a given timestamp.
 */
public abstract class ValueGenerator implements Serializable {
  /** The person associated with this value generator */
  @JSONSkip
  protected final Person person;

  /**
   * Constructor for ValueGenerator.
   * @param person the person associated with this value generator
   */
  protected ValueGenerator(Person person) {
    this.person = person;
  }

  /**
   * Get a value at a given point in time.
   *
   * @param time the time, needs to be current or in the future.
   * @return a numerical value
   */
  public abstract double getValue(long time);
}