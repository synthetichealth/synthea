package org.mitre.synthea.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.UUID;

/**
 * RandomNumberGenerator provides methods for generating random numbers
 * with various distributions and ranges.
 */
public interface RandomNumberGenerator {
  /** Returns a double between 0-1 from a uniform distribution.
   * @return a random double in the range [0.0, 1.0)
  */
  public double rand();

  /**
   * Returns a random double in the given range.
   *
   * @param low the lower bound of the range
   * @param high the upper bound of the range
   * @return a random double between low and high
   */
  public default double rand(double low, double high) {
    return (low + ((high - low) * rand()));
  }

  /**
   * Returns a random double in the given range with no more than the specified
   * number of decimal places.
   *
   * @param low the lower bound of the range
   * @param high the upper bound of the range
   * @param decimals the number of decimal places to round to, or null for no rounding
   * @return a random double between low and high, rounded to the specified number of decimal places
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
  public default String rand(String... choices) {
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

  /**
   * Returns a random boolean value.
   *
   * @return a random boolean
   */
  public boolean randBoolean();

  /**
   * Returns a double from a normal distribution with a mean of 0 and a standard deviation of 1.
   *
   * @return a random double from a standard normal distribution
   */
  public double randGaussian();

  /**
   * Returns a random integer.
   *
   * @return a random integer
   */
  public int randInt();

  /**
   * Returns a random integer within the given bound.
   *
   * @param bound the upper bound (exclusive) for the random integer
   * @return a random integer between 0 (inclusive) and bound (exclusive)
   */
  public int randInt(int bound);

  /**
   * Returns a random long value.
   *
   * @return a random long
   */
  public long randLong();

  /**
   * Returns a random UUID.
   *
   * @return a random UUID
   */
  public UUID randUUID();

  /**
   * Returns the number of times this random number generator has been called.
   *
   * @return the count of random number generation calls
   */
  public long getCount();

  /**
   * Returns the seed value used by this random number generator.
   *
   * @return the seed value
   */
  public long getSeed();
}
