package org.mitre.synthea.modules.risk_calculators;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

public class Framingham {

  public static final long TEN_YEARS_IN_MS = TimeUnit.DAYS.toMillis(3650);

  private static int bound(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }
  
  // estimate cardiovascular risk of developing coronary heart disease (CHD)
  // http://www.nhlbi.nih.gov/health-pro/guidelines/current/cholesterol-guidelines/quick-desk-reference-html/10-year-risk-framingham-table

  // Indices in the array correspond to these age ranges: 20-24, 25-29, 30-34 35-39, 40-44, 45-49,
  // 50-54, 55-59, 60-64, 65-69, 70-74, 75-79
  private static final int[] age_chd_m = { -9, -9, -9, -4, 0, 3, 6, 8, 10, 11, 12, 13 };
  private static final int[] age_chd_f = { -7, -7, -7, -3, 0, 3, 6, 8, 10, 12, 14, 16 };

  private static final int[][] age_chol_chd_m = {
      // <160, 160-199, 200-239, 240-279, >280
      { 0, 4, 7, 9, 11 }, // 20-29 years
      { 0, 4, 7, 9, 11 }, // 30-39 years
      { 0, 3, 5, 6, 8 }, // 40-49 years
      { 0, 2, 3, 4, 5 }, // 50-59 years
      { 0, 1, 1, 2, 3 }, // 60-69 years
      { 0, 0, 0, 1, 1 } // 70-79 years
  };

  private static final int[][] age_chol_chd_f = {
      // <160, 160-199, 200-239, 240-279, >280
      { 0, 4, 8, 11, 13 }, // 20-29 years
      { 0, 4, 8, 11, 13 }, // 30-39 years
      { 0, 3, 6, 8, 10 }, // 40-49 years
      { 0, 2, 4, 5, 7 }, // 50-59 years
      { 0, 1, 2, 3, 4 }, // 60-69 years
      { 0, 1, 1, 2, 2 } // 70-79 years
  };

  // 20-29, 30-39, 40-49, 50-59, 60-69, 70-79 age ranges
  private static final int[] age_smoke_chd_m = { 8, 8, 5, 3, 1, 1 };
  private static final int[] age_smoke_chd_f = { 9, 9, 7, 4, 2, 1 };

  // true/false refers to whether or not blood pressure is treated
  private static final int[][] sys_bp_chd_m = {
      // true, false
      { 0, 0 }, // <120
      { 1, 0 }, // 120-129
      { 2, 1 }, // 130-139
      { 2, 1 }, // 140-149
      { 2, 1 }, // 150-159
      { 3, 2 } // >=160
  };
  private static final int[][] sys_bp_chd_f = {
      // true, false
      { 0, 0 }, // <120
      { 3, 1 }, // 120-129
      { 4, 2 }, // 130-139
      { 5, 3 }, // 140-149
      { 5, 3 }, // 150-159
      { 6, 4 } // >=160
  };

  private static final int[] hdl_lookup_chd = { 2, 1, 0, -1 }; // <40, 40-49, 50-59, >60

  private static final Map<Integer, Double> risk_chd_m;
  private static final Map<Integer, Double> risk_chd_f;
  
  
  
  // 10 year risk of CVD, based on 2008 Framingham update
  // https://www.ahajournals.org/doi/10.1161/CIRCULATIONAHA.107.699579
  // male = 0, female = 1
  
  // Indices in the array correspond to these age ranges: 
  // 30-34, 35-39, 40-44, 45-49, 50-54, 55-59, 60-64, 65-69, 70-74, 75+
  private static final int[][] age_cvd_points = { 
      { 0, 2, 5, 6, 8, 10, 11, 12, 14, 15 }, 
      { 0, 2, 4, 5, 7,  8,  9, 10, 11, 12 }
  };
  
  // indices correspond to these ranges:
  // <35, 35-39, 40-44, 45-49, 50-54, 55-59, 60+
  // note that 2 of the entries in the source table are a range of 10
  private static int[] hdl_cvd_points = { 2, 1, 1, 0, -1, -1, -2 };
  
  // indices correspond to these cholesterol ranges:
  // <160, 160-199, 200-239, 240-279, 280+
  private static int[][] totalChol_cvd_points = {
      { 0, 1, 2, 3, 4 },
      { 0, 1, 3, 4, 5 }
  };
  
  // indices in the array correspond to these SBP ranges:
  // <120, 120-130, 130-140, 140-150, 150-160, 160+
  private static final int[][][] sbp_cvd_points = {
      { // male (note the source table shows 140-159 as one entry)
        { -2, 0, 1, 2, 2, 3 }, // not treated
        {  0, 2, 3, 4, 4, 5 } // treated
      },
      { // female
        { -3, 0, 1, 2, 4, 5 }, // not treated
        { -1, 2, 3, 5, 6, 7 } // treated
      }
  };
  
  private static final int[] cvd_smoker_points = { 4, 3 };
  private static final int[] cvd_diabetes_points = { 3, 4 };
  
  private static final Map<Integer, Double> risk_cvd_m;
  private static final Map<Integer, Double> risk_cvd_f;
  
  static {
    // framingham point scores gives a 10-year risk
    risk_chd_m = new HashMap<>();
    risk_chd_m.put(-1, 0.005); // '-1' represents all scores <0
    risk_chd_m.put(0, 0.01);
    risk_chd_m.put(1, 0.01);
    risk_chd_m.put(2, 0.01);
    risk_chd_m.put(3, 0.01);
    risk_chd_m.put(4, 0.01);
    risk_chd_m.put(5, 0.02);
    risk_chd_m.put(6, 0.02);
    risk_chd_m.put(7, 0.03);
    risk_chd_m.put(8, 0.04);
    risk_chd_m.put(9, 0.05);
    risk_chd_m.put(10, 0.06);
    risk_chd_m.put(11, 0.08);
    risk_chd_m.put(12, 0.1);
    risk_chd_m.put(13, 0.12);
    risk_chd_m.put(14, 0.16);
    risk_chd_m.put(15, 0.20);
    risk_chd_m.put(16, 0.25);
    risk_chd_m.put(17, 0.3); // '17' represents all scores >16

    risk_chd_f = new HashMap<>();
    risk_chd_f.put(8, 0.005); // '8' represents all scores <9
    risk_chd_f.put(9, 0.01);
    risk_chd_f.put(10, 0.01);
    risk_chd_f.put(11, 0.01);
    risk_chd_f.put(12, 0.01);
    risk_chd_f.put(13, 0.02);
    risk_chd_f.put(14, 0.02);
    risk_chd_f.put(15, 0.03);
    risk_chd_f.put(16, 0.04);
    risk_chd_f.put(17, 0.05);
    risk_chd_f.put(18, 0.06);
    risk_chd_f.put(19, 0.08);
    risk_chd_f.put(20, 0.11);
    risk_chd_f.put(21, 0.14);
    risk_chd_f.put(22, 0.17);
    risk_chd_f.put(23, 0.22);
    risk_chd_f.put(24, 0.27);
    risk_chd_f.put(25, 0.3); // '25' represents all scores >24
    
    // -------
    
    // see Table 8 in https://www.ahajournals.org/doi/10.1161/CIRCULATIONAHA.107.699579
    risk_cvd_m = new HashMap<>();
    risk_cvd_m.put(-3, 0.01); // '-3' represents all scores <-2
    risk_cvd_m.put(-2, 0.011);
    risk_cvd_m.put(-1, 0.014);
    risk_cvd_m.put(0, 0.016);
    risk_cvd_m.put(1, 0.019);
    risk_cvd_m.put(2, 0.023);
    risk_cvd_m.put(3, 0.028);
    risk_cvd_m.put(4, 0.033);
    risk_cvd_m.put(5, 0.039);
    risk_cvd_m.put(6, 0.047);
    risk_cvd_m.put(7, 0.056);
    risk_cvd_m.put(8, 0.067);
    risk_cvd_m.put(9, 0.079);
    risk_cvd_m.put(10, 0.094);
    risk_cvd_m.put(11, 0.112);
    risk_cvd_m.put(12, 0.132);
    risk_cvd_m.put(13, 0.156);
    risk_cvd_m.put(14, 0.184);
    risk_cvd_m.put(15, 0.216);
    risk_cvd_m.put(16, 0.253);
    risk_cvd_m.put(17, 0.294);
    risk_cvd_m.put(18, 0.3); // '18' represents all scores >17

    // see Table 6 in https://www.ahajournals.org/doi/10.1161/CIRCULATIONAHA.107.699579
    risk_cvd_f = new HashMap<>();
    risk_cvd_f.put(-2, 0.005); // '-2' represents all scores <-1
    risk_cvd_f.put(-1, 0.01);
    risk_cvd_f.put(0, 0.012);
    risk_cvd_f.put(1, 0.015);
    risk_cvd_f.put(2, 0.017);
    risk_cvd_f.put(3, 0.02);
    risk_cvd_f.put(4, 0.024);
    risk_cvd_f.put(5, 0.028);
    risk_cvd_f.put(6, 0.033);
    risk_cvd_f.put(7, 0.039);
    risk_cvd_f.put(8, 0.045);
    risk_cvd_f.put(9, 0.053);
    risk_cvd_f.put(10, 0.063);
    risk_cvd_f.put(11, 0.073);
    risk_cvd_f.put(12, 0.086);
    risk_cvd_f.put(13, 0.10);
    risk_cvd_f.put(14, 0.117);
    risk_cvd_f.put(15, 0.137);
    risk_cvd_f.put(16, 0.159);
    risk_cvd_f.put(17, 0.185);
    risk_cvd_f.put(18, 0.215);
    risk_cvd_f.put(19, 0.248);
    risk_cvd_f.put(20, 0.285);
    risk_cvd_f.put(21, 0.30); // '21' represents all scores >20
    
  }
  

  /**
   * Calculates a patient's risk of coronary heart disease, based on the 1998 Framingham study group.
   * This includes events such as Angina Pectoris, Unstable Angina, MI, and CHD Death.
   * @param person The patient
   * @param time Time to calculate risk as of
   * @param perTimestep Whether to return the risk per timestep (default 7 days) or per 10 years
   * @return The patient's risk of CHD
   */
  public static double chd10Year(Person person, long time, boolean perTimestep) {
    int age = person.ageInYears(time);
    String gender = (String) person.attributes.get(Person.GENDER);
    Double sysBP = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
    Double diaBP = person.getVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, time);
    Double chol = person.getVitalSign(VitalSign.TOTAL_CHOLESTEROL, time);
    if (sysBP == null || diaBP == null || chol == null) {
      return -1;
    }

    Boolean bpTreated = (Boolean)
        person.attributes.getOrDefault("blood_pressure_controlled", false);

    Double hdl = person.getVitalSign(VitalSign.HDL, time);

    // calculate which index in a lookup array a number corresponds to based on ranges in scoring
    int shortAgeRange = bound((age - 20) / 5, 0, 11);
    int longAgeRange = bound((age - 20) / 10, 0, 5);

    // 0: <160, 1: 160-199, 2: 200-239, 3: 240-279, 4: >280
    int cholRange = bound((chol.intValue() - 160) / 40 + 1, 0, 4);

    // 0: <120, 1: 120-129, 2: 130-139, 3: 140-149, 4: 150-159, 5: >=160
    int bpRange = bound((sysBP.intValue() - 120) / 10 + 1, 0, 5);
    int framinghamPoints = 0;

    int[] ageChd;
    int[][] ageCholChd;
    int[] ageSmokeChd;
    int[][] sysBpChd;

    if (gender.equals("M")) {
      ageChd = age_chd_m;
      ageCholChd = age_chol_chd_m;
      ageSmokeChd = age_smoke_chd_m;
      sysBpChd = sys_bp_chd_m;
    } else {
      ageChd = age_chd_f;
      ageCholChd = age_chol_chd_f;
      ageSmokeChd = age_smoke_chd_f;
      sysBpChd = sys_bp_chd_f;
    }

    framinghamPoints += ageChd[shortAgeRange];
    framinghamPoints += ageCholChd[longAgeRange][cholRange];

    if ((Boolean) person.attributes.getOrDefault(Person.SMOKER, false)) {
      framinghamPoints += ageSmokeChd[longAgeRange];
    }

    // 0: <40, 1: 40-49, 2: 50-59, 3: >60
    int hdlRange = bound((hdl.intValue() - 40) / 10 + 1, 0, 3);
    framinghamPoints += hdl_lookup_chd[hdlRange];

    int treated = bpTreated ? 0 : 1;
    framinghamPoints += sysBpChd[bpRange][treated];
    double framinghamRisk;
    // restrict lower and upper bound of framingham score
    if (gender.equals("M")) {
      framinghamPoints = bound(framinghamPoints, 0, 17);
      framinghamRisk = risk_chd_m.get(framinghamPoints);
    } else {
      framinghamPoints = bound(framinghamPoints, 8, 25);
      framinghamRisk = risk_chd_f.get(framinghamPoints);
    }
    
    if (perTimestep) {
      framinghamRisk = Utilities.convertRiskToTimestep(framinghamRisk, TEN_YEARS_IN_MS);
    }
    
    return framinghamRisk;
  }
  
  
  /**
   * Calculates a patient's risk of cardiovascular disease, based on the 2008 Framingham study group.
   * This includes "Hard ASCVD" events: MI, CHD Death, Stroke, and Stroke Death.
   * @param person The patient
   * @param time Time to calculate risk as of
   * @param perTimestep Whether to return the risk per timestep (default 7 days) or per 10 years
   * @return The patient's risk of CVD
   */
  public static double cvd10Year(Person person, long time, boolean perTimestep) {
    int age = person.ageInYears(time);
    
    if (age < 30) {
      return -1;
    }
    
    String gender = (String) person.attributes.get(Person.GENDER);
    Double sysBP = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
    Double chol = person.getVitalSign(VitalSign.TOTAL_CHOLESTEROL, time);
    if (sysBP == null || chol == null) {
      return -1;
    }

    boolean bpTreated = (boolean)
        person.attributes.getOrDefault("blood_pressure_controlled", false);
    
    Double hdl = person.getVitalSign(VitalSign.HDL, time);
    
    int genderIndex = (person.attributes.get(Person.GENDER).equals("M")) ? 0 : 1;
    
    int framinghamPoints = 0;
    
    // age
    int ageIndex = bound((age - 30) / 5, 0, 9);
    framinghamPoints += age_cvd_points[genderIndex][ageIndex];
    
    // hdl
    int hdlIndex = bound((hdl.intValue() - 35) / 5 + 1, 0, 6);
    framinghamPoints += hdl_cvd_points[hdlIndex];
    
    // total cholesterol
    int cholIndex = bound(((chol.intValue() - 160) / 40) + 1, 0, 4);
    framinghamPoints += totalChol_cvd_points[genderIndex][cholIndex];
    
    // sbp
    int bpTreatedIndex = bpTreated ? 1 : 0; // true = 1, false = 0
    int bpIndex = bound(((sysBP.intValue() - 120) / 10) + 1, 0, 5);
    framinghamPoints += sbp_cvd_points[genderIndex][bpTreatedIndex][bpIndex];
    
    
    if ((boolean) person.attributes.getOrDefault(Person.SMOKER, false)) {
      framinghamPoints += cvd_smoker_points[genderIndex];
    }
    
    if ((boolean) person.attributes.getOrDefault("diabetes", false)) {
      framinghamPoints += cvd_diabetes_points[genderIndex];
    }
    
    double framinghamRisk;
    // restrict lower and upper bound of framingham score
    if (gender.equals("M")) {
      framinghamPoints = bound(framinghamPoints, -3, 18);
      framinghamRisk = risk_cvd_m.get(framinghamPoints);
    } else {
      framinghamPoints = bound(framinghamPoints, -2, 21);
      framinghamRisk = risk_cvd_f.get(framinghamPoints);
    }
    
    if (perTimestep) {
      framinghamRisk = Utilities.convertRiskToTimestep(framinghamRisk, TEN_YEARS_IN_MS);
    }
    
    return framinghamRisk;
    
  }
  
  // Framingham score system for calculating atrial fibrillation (significant factor for stroke
  // risk)
  private static final int[][] age_af = {
      // age ranges: 45-49, 50-54, 55-59, 60-64, 65-69, 70-74, 75-79, 80-84, >84
      { 1, 2, 3, 4, 5, 6, 7, 7, 8 }, // male
      { -3, -2, 0, 1, 3, 4, 6, 7, 8 } // female
  };

  // only covers points 1-9. <=0 and >= 10 are in if statement
  private static final double[] risk_af_table = { 0.01, // 0 or less
      0.02, 0.02, 0.03, 0.04, 0.06, 0.08, 0.12, 0.16, 0.22, 0.3 // 10 or greater
  };
  
  public static double atrialFibrillation10Year(Person person, long time, boolean perTimestep) {
    int age = person.ageInYears(time);
    if (age < 45 || person.attributes.containsKey("atrial_fibrillation")
        || person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time) == null
        || person.getVitalSign(VitalSign.BMI, time) == null) {
      return -1;
    }

    int afScore = 0;
    int ageRange = Math.min((age - 45) / 5, 8);
    int genderIndex = (person.attributes.get(Person.GENDER).equals("M")) ? 0 : 1;
    afScore += age_af[genderIndex][ageRange];
    if (person.getVitalSign(VitalSign.BMI, time) >= 30) {
      afScore += 1;
    }

    if (person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time) >= 160) {
      afScore += 1;
    }

    if ((Boolean) person.attributes.getOrDefault("blood_pressure_controlled", false)) {
      afScore += 1;
    }

    afScore = bound(afScore, 0, 10);

    double afRisk = risk_af_table[afScore]; // 10-yr risk
    
    if (perTimestep) {
      afRisk = Utilities.convertRiskToTimestep(afRisk, TEN_YEARS_IN_MS);
    }
    
    return afRisk;
  }
  
  // https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
  // Prevalence of stroke by age and sex (Male, Female)
  private static final double[] stroke_rate_20_39 = { 0.002, 0.007 };
  private static final double[] stroke_rate_40_59 = { 0.019, 0.022 };

  private static final double[][] ten_year_stroke_risk = {
      { 0, 0.03, 0.03, 0.04, 0.04, 0.05, 0.05, 0.06, 0.07, 0.08, 0.1, // male section
          0.11, 0.13, 0.15, 0.17, 0.2, 0.22, 0.26, 0.29, 0.33, 0.37, 0.42, 0.47, 0.52, 0.57, 0.63,
          0.68, 0.74, 0.79, 0.84, 0.88 },
      { 0, 0.01, 0.01, 0.02, 0.02, 0.02, 0.03, 0.04, 0.04, 0.05, 0.06, // female
          0.08, 0.09, 0.11, 0.13, 0.16, 0.19, 0.23, 0.27, 0.32, 0.37, 0.43, 0.5, 0.57, 0.64, 0.71,
          0.78, 0.84 } };

  // the index for each range corresponds to the number of points
  private static final int[][] age_stroke = { 
      { 54, 57, 60, 63, 66, 69, 73, 76, 79, 82, 85 }, // male
      { 54, 57, 60, 63, 65, 68, 71, 74, 77, 79, 82 } // female
  };

  private static final int[][] untreated_sys_bp_stroke = {
      { 0, 106, 116, 126, 136, 146, 156, 166, 176, 185, 196 }, // male
      { 0, 95, 107, 119, 131, 144, 156, 168, 181, 193, 205 } // female
  };

  private static final int[][] treated_sys_bp_stroke = {
      { 0, 106, 113, 118, 124, 130, 136, 143, 151, 162, 177 }, // male
      { 0, 95, 107, 114, 120, 126, 132, 140, 149, 161, 205 } // female
  };

  private static final int getIndexForValueInRangelist(int value, int[] data) {
    for (int i = 0; i < data.length - 1; i++) {
      if (data[i] <= value && value <= data[i + 1]) {
        return i;
      }
    }
    // the last segment is open-ended
    if (value >= data[data.length - 1]) {
      return data.length - 1;
    }

    // shouldn't be possible to get here if we do everything right
    throw new RuntimeException("unexpected value " + value + " for data " + Arrays.toString(data));
  }

  private static final double[] diabetes_stroke = { 2, 3 };
  private static final double[] chd_stroke_points = { 4, 2 };
  private static final double[] atrial_fibrillation_stroke_points = { 4, 6 };

  
  public static double stroke10Year(Person person, long time, boolean perTimestep) {
    Double bloodPressure = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
    if (bloodPressure == null) {
      return -1;
    }

    // https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
    // calculate stroke risk based off of prevalence of stroke in age group for people younger than
    // 54. Framingham score system does not cover these.

    int genderIndex = ((String) person.attributes.get(Person.GENDER)).equals("M") ? 0 : 1;

    int age = person.ageInYears(time);
    double strokeRisk;
    
    if (age < 20) {
      // no risk set
      return -1;
    } else if (age < 40) {
      strokeRisk = stroke_rate_20_39[genderIndex];
    } else if (age < 55) {
      strokeRisk = stroke_rate_40_59[genderIndex];
    } else {
      int strokePoints = 0;
      if ((Boolean) person.attributes.getOrDefault(Person.SMOKER, false)) {
        strokePoints += 3;
      }
      if ((Boolean) person.attributes.getOrDefault("left_ventricular_hypertrophy", false)) {
        strokePoints += 5;
      }

      strokePoints += getIndexForValueInRangelist(age, age_stroke[genderIndex]);

      int bp = bloodPressure.intValue();
      
      if ((Boolean) person.attributes.getOrDefault("blood_pressure_controlled", false)) {
        strokePoints += getIndexForValueInRangelist(bp, treated_sys_bp_stroke[genderIndex]);
      } else {
        strokePoints += getIndexForValueInRangelist(bp, untreated_sys_bp_stroke[genderIndex]);
      }

      if ((Boolean) person.attributes.getOrDefault("diabetes", false)) {
        strokePoints += diabetes_stroke[genderIndex];
      }

      if ((Boolean) person.attributes.getOrDefault("coronary_heart_disease", false)) {
        strokePoints += chd_stroke_points[genderIndex];
      }

      if ((Boolean) person.attributes.getOrDefault("atrial_fibrillation", false)) {
        strokePoints += atrial_fibrillation_stroke_points[genderIndex];
      }

      if (strokePoints >= ten_year_stroke_risk[genderIndex].length) {
        // off the charts
        int worstCase = ten_year_stroke_risk[genderIndex].length - 1;
        strokeRisk = ten_year_stroke_risk[genderIndex][worstCase];
      } else {
        strokeRisk = ten_year_stroke_risk[genderIndex][strokePoints];
      }
    }

    if (perTimestep) {
      strokeRisk = Utilities.convertRiskToTimestep(strokeRisk, TEN_YEARS_IN_MS);
    }
   
    return strokeRisk;
  }
  
  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String,Inventory> attributes) {
    
  }
}
