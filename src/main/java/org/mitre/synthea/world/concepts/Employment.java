package org.mitre.synthea.world.concepts;

import static java.util.stream.Collectors.toList;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * A model of employment / unemployment that is expected to be run as a part of the LifecycleModule.
 * The model is created with a probability of unemployment. This is likely to come from the SDoH
 * information that is loaded from Location. If a person is employed, every time checkEmployment is
 * called, there is a chance the person becomes unemployed.
 */
public class Employment implements Serializable {
  private static EnumeratedDistribution<LengthOfUnemployment> distribution =
          loadUnemploymentLengthDistro();

  // Minimum employment length in months
  public static long MIN_EMPLOYMENT_LENGTH = 6;

  private double chanceOfUnemployment;
  private long nextTimeToCheck;
  private boolean unemployed;

  /**
   * https://www.bls.gov/news.release/empsit.t12.htm
   */
  public enum LengthOfUnemployment {
    LESS_THAN_FIVE_WEEKS(1, 5, 38.2),
    FIVE_TO_FOURTEEN(5, 14, 30.8),
    FIFTEEN_TO_TWENTY_SIX(15, 26, 13.4),
    TWENTY_SEVEN_OR_MORE(27, 52, 17.6);

    private final int min;
    private final int max;
    private final double probability;

    LengthOfUnemployment(int min, int max, double probability) {
      this.min = min;
      this.max = max;
      this.probability = probability;
    }
  }

  public Employment(double chanceOfUnemployment) {
    this.chanceOfUnemployment = chanceOfUnemployment;
    this.unemployed = false;
  }

  /**
   * Checks on the employment status for the person. First it will see if it is time to change
   * employment status. If it is and the person is employed, it will make
   * weighted random selection as to whether the person should be unemployed, based on the provided
   * distribution. If the person becomes unemployed, it will select a length of unemployment, based
   * on information from the Bureau of Labor Statistics.
   * If the person is unemployed, it will check to see if their unemployment should be over. If so,
   * it will set their unemployed property to false.
   * @param person hoping to keep their job
   * @param time in simulation
   */
  public void checkEmployment(Person person, long time) {
    if (time >= nextTimeToCheck) {
      if (unemployed == true) {
        unemployed = false;
        person.attributes.put(Person.UNEMPLOYED, false);
        nextTimeToCheck = time + Utilities.convertTime("months", MIN_EMPLOYMENT_LENGTH);
      } else {
        if (person.rand() < chanceOfUnemployment) {
          unemployed = true;
          person.attributes.put(Person.UNEMPLOYED, true);
          LengthOfUnemployment loe = null;
          synchronized (distribution) {
            distribution.reseedRandomGenerator(person.randLong());
            loe = distribution.sample();
          }
          int weeksOfUnemployment = loe.min + person.randInt(loe.max - loe.min);
          nextTimeToCheck = time + Utilities.convertTime("weeks", weeksOfUnemployment);
        } else {
          nextTimeToCheck = time + Utilities.convertTime("months", MIN_EMPLOYMENT_LENGTH);
        }

      }
    }
  }

  /**
   * Load the EnumeratedDistribution with BLS data by pulling it from the enum.
   * @return A populated EnumeratedDistribution
   */
  public static EnumeratedDistribution<LengthOfUnemployment> loadUnemploymentLengthDistro() {
    List distroWeights = Arrays.stream(LengthOfUnemployment.values())
            .map(e -> new Pair(e, e.probability)).collect(toList());
    return new EnumeratedDistribution<LengthOfUnemployment>(distroWeights);
  }
}
