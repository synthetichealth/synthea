package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

/**
 * A value generator that returns a set constant value for all eternity.
 */
public class ConstantValueGenerator extends ValueGenerator {
  /** The value this generator generates */
  private double value;

  /**
   * Constructor for ConstantValueGenerator.
   * @param person the person for whom this value is generated
   * @param value the constant value to be returned
   */
  public ConstantValueGenerator(Person person, double value) {
    super(person);
    this.value = value;
  }

  @Override
  public double getValue(long time) {
    return this.value;
  }
}