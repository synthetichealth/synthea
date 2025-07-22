package org.mitre.synthea.world.concepts;

/**
 * A utility class for calculating Body Mass Index (BMI) and related metrics.
 */
public class BMI {

  /**
   * Calculate the Body Mass Index (BMI) given height and weight.
   *
   * @param heightCM The height in centimeters.
   * @param weightKG The weight in kilograms.
   * @return The calculated BMI.
   */
  public static double calculate(double heightCM, double weightKG) {
    return (weightKG / ((heightCM / 100.0) * (heightCM / 100.0)));
  }

  /**
   * Calculate the weight for a given height and BMI.
   *
   * @param heightCM The height in centimeters.
   * @param bmi The desired BMI.
   * @return The weight in kilograms corresponding to the given height and BMI.
   */
  public static double weightForHeightAndBMI(double heightCM, double bmi) {
    return (heightCM / 100.0) * (heightCM / 100.0) * bmi;
  }
}
