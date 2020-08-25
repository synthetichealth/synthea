package org.mitre.synthea.engine;

import com.google.gson.JsonObject;
import org.mitre.synthea.world.agents.Person;

/**
 * DurationProvider is an interface that should be implemented to provide durations for Delay and
 * Procedure states. It is assumed that implementations will provide different ways of determining
 * a duration for a given situation.
 */
public interface DurationProvider {
  /**
   * Set the unit of the duration: hour, day, year, etc.
   * @param unit hour, day, year, etc
   */
  void setUnit(String unit);

  /**
   * Get the unit of the duration.
   * @return the unit
   */
  String getUnit();

  /**
   * Provides the implementation with the JSON it should look through to grab whatever it needs to
   * fully populate an instance of itself.
   * @param definition The range property for Delay states or
   *                   duration property for Procedure states
   */
  void load(JsonObject definition);

  /**
   * Called to obtain the duration for the state.
   * @param person The person to use to obtain randomness, if needed
   * @return How long the thing should be
   */
  long generate(Person person);

  /**
   * Given some JSON, does it look like it has the correct properties for the implementation.
   * @param definition The JSON to look through
   * @return true if this the JSON has the correct properties for a distribution
   */
  boolean detect(JsonObject definition);
}
