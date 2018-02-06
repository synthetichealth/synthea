package org.mitre.synthea.world.concepts;

import com.google.gson.annotations.SerializedName;

public enum VitalSign {

  @SerializedName("Height") HEIGHT,
  @SerializedName("Weight") WEIGHT, 
  @SerializedName("Height Percentile") HEIGHT_PERCENTILE, 
  @SerializedName("Weight Percentile") WEIGHT_PERCENTILE, 
  @SerializedName("BMI") BMI, 
  @SerializedName("Systolic Blood Pressure") SYSTOLIC_BLOOD_PRESSURE, 
  @SerializedName("Diastolic Blood Pressure") DIASTOLIC_BLOOD_PRESSURE, 
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
  @SerializedName("EGFR") EGFR;

  public static VitalSign fromString(String text) {
    for (VitalSign type : VitalSign.values()) {
      String typeText = type.toString();
      if (text.equalsIgnoreCase(typeText)) {
        return type;
      }
    }
    return VitalSign.valueOf(text);
  }

  public String toString() {
    try {
      return getClass().getField(this.name()).getAnnotation(SerializedName.class).value();
    } catch (Exception e) {
      return this.name();
    }
  }
}