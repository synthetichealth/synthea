package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

/**
 * Generate random values within a defined range.
 */
public class RandomValueGenerator extends ValueGenerator {
  private double low;
  private double high;


  /**
   * 
   * @param person
   * @param low
   * @param high
   */
  public RandomValueGenerator(Person person, double low, double high) {
    super(person);
    this.low = low;
    this.high = high;
  }


  @Override
  public double getValue(long time) {
    // TODO: Using the person.random could return a different value for the same timepoint. 
    // Use the time as seed instead for repeatability?
    return person.rand(low, high);    
  }
}
