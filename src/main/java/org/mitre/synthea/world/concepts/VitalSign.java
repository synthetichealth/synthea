package org.mitre.synthea.world.concepts;

import com.google.gson.annotations.SerializedName;

public enum VitalSign {

  @SerializedName("Height") HEIGHT,
  @SerializedName("Weight") WEIGHT,
  @SerializedName("Height Percentile") HEIGHT_PERCENTILE,
  @SerializedName("Weight Percentile") WEIGHT_PERCENTILE,
  @SerializedName("BMI") BMI,
  @SerializedName("Head Circumference") HEAD,
  @SerializedName("Systolic Blood Pressure") SYSTOLIC_BLOOD_PRESSURE,
  @SerializedName("Diastolic Blood Pressure") DIASTOLIC_BLOOD_PRESSURE,
  @SerializedName("Oxygen Saturation") OXYGEN_SATURATION,
  @SerializedName("Blood Glucose") BLOOD_GLUCOSE,
  @SerializedName("Glucose") GLUCOSE,
  @SerializedName("Urea Nitrogen") UREA_NITROGEN,
  @SerializedName("Creatinine") CREATININE,
  @SerializedName("Calcium") CALCIUM,
  @SerializedName("Sodium") SODIUM,
  @SerializedName("Potassium") POTASSIUM,
  @SerializedName("Chloride") CHLORIDE,
  @SerializedName("Carbon Dioxide") CARBON_DIOXIDE,
  @SerializedName("Total Cholesterol") TOTAL_CHOLESTEROL,
  @SerializedName("Triglycerides") TRIGLYCERIDES,
  @SerializedName("LDL") LDL,
  @SerializedName("HDL") HDL,
  @SerializedName("Microalbumin Creatinine Ratio") MICROALBUMIN_CREATININE_RATIO,
  @SerializedName("EGFR") EGFR,
  @SerializedName("Left ventricular Ejection fraction") LVEF,
  @SerializedName("Heart Rate") HEART_RATE,
  @SerializedName("Respiration Rate") RESPIRATION_RATE;

  /**
   * Name of the VitalSign. Cached as a string for better lookup performance.
   */
  private final String name;

  private VitalSign() {
    String n;
    try {
      n = getClass().getField(this.name())
          .getAnnotation(SerializedName.class).value();

    } catch (Exception e) {
      // should never happen
      n = this.name();
    }
    this.name = n;
  }

  /**
   * Get the VitalSign enum matching the given string.
   * @param text Name of a Vital Sign, ex "Systolic Blood Pressure"
   * @return VitalSign with the given name
   */
  public static VitalSign fromString(String text) {
    for (VitalSign type : VitalSign.values()) {
      String typeText = type.name;
      if (text.equalsIgnoreCase(typeText)) {
        return type;
      }
    }
    return VitalSign.valueOf(text);
  }

  public String toString() {
    return this.name;
  }
}