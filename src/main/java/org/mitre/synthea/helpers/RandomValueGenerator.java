package org.mitre.synthea.helpers;

import java.util.HashMap;

import org.mitre.synthea.engine.Distribution;
import org.mitre.synthea.world.agents.Person;

/**
 * Generate random values within a defined range.
 */
public class RandomValueGenerator extends ValueGenerator {
  /**
   * The distribution used to generate random values.
   * Typically a uniform distribution with low and high bounds.
   */
  private Distribution distribution;

  /**
   * Create a new RandomValueGenerator.
   * @param person The person to generate data for.
   * @param low The lower bound for the generator.
   * @param high The upper bound for the generator.
   */
  public RandomValueGenerator(Person person, double low, double high) {
    super(person);
    this.distribution = new Distribution();
    distribution.kind = Distribution.Kind.UNIFORM;
    HashMap<String, Double> parameters = new HashMap();
    parameters.put("low", low);
    parameters.put("high", high);
    distribution.parameters = parameters;
  }

  /**
   * Create a new RandomValueGenerator with a specific distribution.
   * @param person The person to generate data for.
   * @param distribution The distribution type used to generate random values.
   */
  public RandomValueGenerator(Person person, Distribution distribution) {
    super(person);
    this.distribution = distribution;
  }

  @Override
  public double getValue(long time) {
    return distribution.generate(this.person);
  }
}
