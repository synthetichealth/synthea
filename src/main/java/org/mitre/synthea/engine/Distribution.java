package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.HashMap;

import org.mitre.synthea.world.agents.Person;

/**
 * Representation of different types of distributions that can be used to represent
 * random variables used in Synthea States.
 */
public class Distribution implements Serializable {
  public enum Kind {
    EXACT, GAUSSIAN, UNIFORM
  }

  public Kind kind;
  public HashMap<String, Double> parameters;

  /**
   * Generate a sample from the random variable.
   * @param person The place to obtain a repeatable source of randomness
   * @return The value
   */
  public double generate(Person person) {
    switch (this.kind) {
      case EXACT:
        return this.parameters.get("value");
      case UNIFORM:
        return person.rand(this.parameters.get("low"), this.parameters.get("high"));
      case GAUSSIAN:
        return (this.parameters.get("standardDeviation") * person.randGaussian())
            + this.parameters.get("mean");
      default:
        return -1;
    }
  }

  /**
   * Determine whether the Distribution has all of the information it needs to generate a sample
   * value.
   * @return True if it is valid, false otherwise
   */
  public boolean validate() {
    if (parameters == null) {
      return false;
    }
    switch (this.kind) {
      case EXACT:
        return this.parameters.containsKey("value");
      case UNIFORM:
        return this.parameters.containsKey("low") && this.parameters.containsKey("high");
      case GAUSSIAN:
        return this.parameters.containsKey("mean")
            && this.parameters.containsKey("standardDeviation");
      default:
        return false;
    }
  }
}
