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
    EXACT, GAUSSIAN, UNIFORM, EXPONENTIAL, TRIANGULAR
  }

  public Kind kind;
  public Boolean round;
  public HashMap<String, Double> parameters;

  /**
   * Generate a sample from the random variable.
   * @param person The place to obtain a repeatable source of randomness
   * @return The value
   */
  public double generate(Person person) {
    double value;
    switch (this.kind) {
      case EXACT:
        value = this.parameters.get("value");
        break;
      case UNIFORM:
        value = person.rand(this.parameters.get("low"), this.parameters.get("high"));
        break;
      case GAUSSIAN:
        value = (this.parameters.get("standardDeviation") * person.randGaussian())
            + this.parameters.get("mean");
        if (this.parameters.containsKey("min")) {
          double min = this.parameters.get("min");
          if (value < min) {
            value = min;
          }
        }
        if (this.parameters.containsKey("max")) {
          double max = this.parameters.get("max");
          if (value > max) {
            value = max;
          }
        }
        break;
      case EXPONENTIAL:
        double average = this.parameters.get("mean");
        double lambda = (-1.0d / average);
        value = 1.0d + Math.log(1.0d - person.rand()) / lambda;
        break;
      case TRIANGULAR:
        /* Pick a single value based on a triangular distribution. See:
         * https://en.wikipedia.org/wiki/Triangular_distribution
         */
        double min = this.parameters.get("min");
        double mode = this.parameters.get("mode");
        double max = this.parameters.get("max");
        double f = (mode - min) / (max - min);
        double rand = person.rand();
        if (rand < f) {
          value = min + Math.sqrt(rand * (max - min) * (mode - min));
        } else {
          value = max - Math.sqrt((1 - rand) * (max - min) * (max - mode));
        }
        break;
      default:
        value = -1;
    }
    if (round != null && round.booleanValue()) {
      value = Math.round(value);
    }
    return value;
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
      case EXPONENTIAL:
        return this.parameters.containsKey("mean");
      case TRIANGULAR:
        return this.parameters.containsKey("min")
            && this.parameters.containsKey("mode")
            && this.parameters.containsKey("max");
      default:
        return false;
    }
  }
}
