package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

/**
 * Generate random values within a defined range.
 */
public class RandomValueGenerator extends ValueGenerator {
  private double low;
  private double high;

  /**
   * Create a new RandomValueGenerator.
   * @param person The person to generate data for.
   * @param low The lower bound for the generator.
   * @param high The upper bound for the generator.
   */
  public RandomValueGenerator(Person person, double low, double high) {
    super(person);
    this.low = low;
    this.high = high;
  }

  @Override
  public double getValue(long time) {
    return person.rand(low, high);
  }
}
