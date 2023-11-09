package org.mitre.synthea.world.concepts;

import java.io.Serializable;

/**
 * Represents the LMS parameters on the CDC growth chart for a particular sex and age in months.
 */
public class GrowthChartEntry implements Serializable {

  /**
   * The highest percentile to use when attempting to calculate a value. Tests with the BMI growth
   * charts for females at 240 months show that values greater than 0.99739 cause a term in the
   * equation to go negative causing the code to return NaN. Values slightly less than that can
   * return unrealistic values. This provides a limit to the maximum percentile that will be looked
   * up.
   */
  public static double MAX_PERCENTILE = 0.995;

  private double lboxCox;
  private double median;
  private double scov;

  /**
   * Create a new growth chart entry based on LMS values.
   * @param l the power in the Box-Cox transformation
   * @param m median
   * @param s the generalized coefficient of variation
   */
  public GrowthChartEntry(double l, double m, double s) {
    this.lboxCox = l;
    this.median = m;
    this.scov = s;
  }

  /**
   * Compute the z-score given a value and the LMS parameters.
   * @param value the actual value, for example a weight, height or BMI
   * @return z-score
   */
  public double zscoreForValue(double value) {
    if (this.lboxCox == 0) {
      return Math.log(value / this.median) / this.scov;
    } else {
      return (Math.pow((value / this.median), this.lboxCox) - 1)
          / (this.lboxCox * this.scov);
    }
  }

  /**
   * Look up the percentile that a given value falls into.
   * @param value the value to find the percentile for
   * @return 0 - 1.0
   */
  public double percentileForValue(double value) {
    return GrowthChart.zscoreToPercentile(zscoreForValue(value));
  }

  /**
   * Look up the value for a particular percentile.
   * @param percentile 0 - 1.0
   * @return The value for the given percentile
   */
  public double lookUp(double percentile) {
    double percentileToUse = percentile;
    if (percentile > MAX_PERCENTILE) {
      percentileToUse = MAX_PERCENTILE;
    }
    double z = GrowthChart.calculateZScore(percentileToUse);
    if (this.lboxCox == 0) {
      return this.median * Math.exp((this.scov * z));
    } else {
      double expressionTerm = this.lboxCox * this.scov * z;
      return this.median * Math.pow((1 + expressionTerm), (1.0 / this.lboxCox));
    }
  }
}
