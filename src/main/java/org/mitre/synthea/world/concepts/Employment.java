package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * A model of employment / unemployment that is expected to be run as a part of the LifecycleModule.
 * The model is created with a probability of unemployment. This is likely to come from the SDoH
 * information that is loaded from Location. If a person is employed, every time checkEmployment is
 * called, there is a chance the person becomes unemployed.
 */
public class Employment implements Serializable {

  // Minimum length a person can have an employment condition
  public static long MIN_EMPLOYMENT_STATE_LENGTH = 6;

  private double chanceOfUnemployment;
  private long nextTimeToCheck;
  private boolean unemployed;

  public Employment(double chanceOfUnemployment) {
    this.chanceOfUnemployment = chanceOfUnemployment;
    this.unemployed = false;
  }

  /**
   * Checks on the employment status for the person. First it will see if it is time to change
   * employment status. If it is and the person is employed, it will make
   * weighted random selection as to whether the person should be unemployed, based on the provided
   * distribution.
   * @param person hoping to keep their job
   * @param time in simulation
   */
  public void checkEmployment(Person person, long time) {
    if (time >= nextTimeToCheck) {
      if (unemployed == true) {
        unemployed = false;
        person.attributes.put(Person.UNEMPLOYED, false);
      } else {
        if (person.rand() < chanceOfUnemployment) {
          unemployed = true;
          person.attributes.put(Person.UNEMPLOYED, true);
        }
      }
      nextTimeToCheck = time + Utilities.convertTime("months", MIN_EMPLOYMENT_STATE_LENGTH);
    }
  }
}
