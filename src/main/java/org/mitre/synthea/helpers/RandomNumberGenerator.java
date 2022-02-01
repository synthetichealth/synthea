package org.mitre.synthea.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.UUID;

public interface RandomNumberGenerator {
  /** Returns a double between 0-1 from a uniform distribution. */
  public double rand();

  /**
   * Returns a random double in the given range.
   */
  public default double rand(double low, double high) {
    return (low + ((high - low) * rand()));
  }

  /**
   * Returns a random double in the given range with no more that the specified
   * number of decimal places.
   */
  public default double rand(double low, double high, Integer decimals) {
    double value = rand(low, high);
    if (decimals != null) {
      value = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
    return value;
  }

  /**
   * Helper function to get a random number based on an array of [min, max]. This
   * should be used primarily when pulling ranges from YML.
   *
   * @param range array [min, max]
   * @return random double between min and max
   */
  public default double rand(double[] range) {
    if (range == null || range.length != 2) {
      throw new IllegalArgumentException(
          "input range must be of length 2 -- got " + Arrays.toString(range));
    }

    if (range[0] > range[1]) {
      throw new IllegalArgumentException(
          "range must be of the form {low, high} -- got " + Arrays.toString(range));
    }

    return rand(range[0], range[1]);
  }

  /**
   * Return one of the options randomly with uniform distribution.
   *
   * @param choices The options to be returned.
   * @return One of the options randomly selected.
   */
  public default String rand(String[] choices) {
    int value = randInt(choices.length);
    return choices[value];
  }

  /**
   * Helper function to get a random number based on an integer array of [min,
   * max]. This should be used primarily when pulling ranges from YML.
   *
   * @param range array [min, max]
   * @return random double between min and max
   */
  public default double rand(int[] range) {
    if (range == null || range.length != 2) {
      throw new IllegalArgumentException(
          "input range must be of length 2 -- got " + Arrays.toString(range));
    }

    if (range[0] > range[1]) {
      throw new IllegalArgumentException(
          "range must be of the form {low, high} -- got " + Arrays.toString(range));
    }

    return rand(range[0], range[1]);
  }

  /** Returns a random boolean. */
  public boolean randBoolean();

  /**
   * Returns a double between from a normal distribution
   * with mean of 0 and standard deviation of 1.
   */
  public double randGaussian();

  /** Returns a random integer. */
  public int randInt();

  /** Returns a random integer in the given bound. */
  public int randInt(int bound);

  /** Return a random long. */
  public long randLong();

  /** Return a random UUID. */
  public UUID randUUID();
}
