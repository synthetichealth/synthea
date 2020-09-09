package org.mitre.synthea.engine;

import org.mitre.synthea.world.agents.Person;

import java.io.Serializable;
import java.util.HashMap;

public class Distribution implements Serializable {
  public enum Kind {
    EXACT, GAUSSIAN, UNIFORM
  }

  public Kind kind;
  public HashMap<String, Double> parameters;

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

  public boolean validate() {
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
