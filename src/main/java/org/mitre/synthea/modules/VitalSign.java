package org.mitre.synthea.modules;

public enum VitalSign {
	
	HEIGHT("Height"),
	WEIGHT("Weight"),
	BMI("BMI"),
	SYSTOLIC_BLOOD_PRESSURE("Systolic Blood Pressure"),
	DIASTOLIC_BLOOD_PRESSURE("Diastolic Blood Pressure"),
	BLOOD_GLUCOSE("Blood Glucose"),
	GLUCOSE("Glucose"),
	UREA_NITROGEN("Urea Nitrogen"),
	CREATININE("Creatinine"),
	CALCIUM("Calcium"),
	SODIUM("Sodium"),
	POTASSIUM("Potassium"),
	CHLORIDE("Chloride"),
	CARBON_DIOXIDE("Carbon Dioxide"),
	TOTAL_CHOLESTEROL("Total Cholesterol"),
	TRIGLYCERIDES("Triglycerides"),
	LDL("LDL"),
	HDL("HDL"),
	MICROALBUMIN_CREATININE_RATIO("Microalbumin Creatinine Ratio"),
	EGFR("EGFR");
	
	private String text;
	
	VitalSign(String text) {
		this.text = text;
	}
	
	public static VitalSign fromString(String text) {
		for(VitalSign type : VitalSign.values()) {
			if(type.text.equalsIgnoreCase(text)) {
				return type;
			}
		}
		return VitalSign.valueOf(text);
	}
	
	public String toString() {
		return text;
	}
}