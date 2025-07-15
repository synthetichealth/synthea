package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.HashMap;

import org.mitre.synthea.world.agents.Person;

/**
 * Represents different types of distributions that can be used to model
 * random variables in Synthea States.
 */
public class Distribution implements Serializable {

  /**
   * Enum representing the types of distributions supported.
   */
  public enum Kind {
    /** A distribution with a single exact value. */
    EXACT,
    /** A Gaussian (normal) distribution. */
    GAUSSIAN,
    /** A uniform distribution. */
    UNIFORM,
    /** An exponential distribution. */
    EXPONENTIAL,
    /** A triangular distribution. */
    TRIANGULAR
  }

  /** The type of distribution. */
  public Kind kind;

  /** Whether the generated value should be rounded to the nearest integer. */
  public Boolean round;

  /** Parameters defining the distribution (e.g., mean, standard deviation). */
  public HashMap<String, Double> parameters;

  /**
   * Generate a sample from the random variable.
   *
   * @param person the person object to obtain a repeatable source of randomness
   * @return the generated value
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
