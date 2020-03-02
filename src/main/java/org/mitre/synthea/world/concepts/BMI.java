package org.mitre.synthea.world.concepts;

public class BMI {
  public static double calculate(double heightCM, double weightKG) {
    return (weightKG / ((heightCM / 100.0) * (heightCM / 100.0)));
  }

  public static double weightForHeightAndBMI(double heightCM, double bmi) {
    return (heightCM / 100.0) * (heightCM / 100.0) * bmi;
  }
}
