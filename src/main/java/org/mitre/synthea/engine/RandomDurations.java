package org.mitre.synthea.engine;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.Serializable;
import java.lang.reflect.Type;

import org.mitre.synthea.world.agents.Person;

/**
 * A place for all random duration types for Delay and Procedure states.
 */
public abstract class RandomDurations {

  /**
   * This is essentially a DurationProvider factory. Given JSON, it will provide the correct
   * DurationProvider by using their detect methods.
   * @param definition The JSON to look at
   * @return The appropriate DurationProvider. null if there aren't any properties that will
   *         satisfy any DurationProvider
   *
   */
  public static DurationProvider detectDurationType(JsonObject definition) {
    Uniform u = new Uniform();
    if (u.detect(definition)) {
      return u;
    }
    Gaussian g = new Gaussian();
    if (g.detect(definition)) {
      return g;
    }
    return null;
  }

  /**
   * Class to work with Gson to properly parse DurationProviders.
   */
  public static class DurationDeserializer implements JsonDeserializer<DurationProvider> {

    @Override
    public DurationProvider deserialize(JsonElement json, Type typeOfT,
                                        JsonDeserializationContext context)
        throws JsonParseException {
      JsonObject definition = json.getAsJsonObject();
      DurationProvider dp = detectDurationType(definition);
      if (dp != null) {
        dp.load(definition);
      }
      return dp;
    }
  }

  /**
   * Base class for building DurationProviders. Handles the duration unit.
   */
  public abstract static class AbstractDurationProvider implements DurationProvider, Serializable {
    protected String unit;

    @Override
    public void setUnit(String unit) {
      this.unit = unit;
    }

    @Override
    public String getUnit() {
      return this.unit;
    }

    @Override
    public void load(JsonObject definition) {
      this.unit = definition.get("unit").getAsString();
    }
  }

  /**
   * DurationProvider that supplies a random length of time based on a uniform distribution.
   * Expected properties are "low" and "high" representing the minimum and maximum values
   * for the duration.
   */
  public static class Uniform extends AbstractDurationProvider implements Serializable {
    private double low;
    private double high;

    @Override
    public void load(JsonObject definition) {
      super.load(definition);
      this.low = definition.get("low").getAsDouble();
      this.high = definition.get("high").getAsDouble();
    }

    @Override
    public long generate(Person person) {
      return (long) person.rand(this.low, this.high);
    }

    @Override
    public boolean detect(JsonObject definition) {
      return definition.has("low") && definition.has("high");
    }
  }

  /**
   * DurationProvider that supplies a random length of time based on a Gaussian or normal
   * distribution. Expected properties are "mean" and "standardDeviation" representing the mean and
   * standard deviation for the distribution.
   */
  public static class Gaussian extends AbstractDurationProvider implements Serializable {
    private double mean;
    private double standardDeviation;

    @Override
    public void load(JsonObject definition) {
      super.load(definition);
      this.mean = definition.get("mean").getAsDouble();
      this.standardDeviation = definition.get("standardDeviation").getAsDouble();
    }

    @Override
    public long generate(Person person) {
      return (long) ((person.randGaussian() * this.standardDeviation) + this.mean);
    }

    @Override
    public boolean detect(JsonObject definition) {
      return definition.has("mean") && definition.has("standardDeviation");
    }
  }
}
