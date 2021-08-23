package org.mitre.synthea.modules.risk_calculators;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

public class ASCVD {
  
  public static final long TEN_YEARS_IN_MS = TimeUnit.DAYS.toMillis(3650);

  /**
   * Equation Parameters of the Pooled Cohort Equations for Estimation of 10-Year Risk of Hard ASCVD
   * See Appendix 7, Table A  https://doi.org/10.1161/01.cir.0000437741.48606.98
   * "N/A" becomes 0
   */
  private static final double[][] ASCVD_COEFFICIENTS = {
                          // sex:  ------women-----   ----men----
                          // race: ---w---  --aa--  ---w--  --aa--
/* Ln Age (y) */                 { -29.799, 17.114,  12.344, 2.469 },
/* (Ln Age)^2 */                 { 4.884,   0,       0,      0 },
/* Ln Total Chol */              { 13.540,  0.940,   11.853, 0.302 },
/* Ln Age x Ln Total Chol */     { -3.114,  0,       -2.664, 0 },
/* Ln HDL-C */                   { -13.578, -18.920, -7.990, -0.307 },
/* Ln Age x Ln HDL-C */          { 3.149,   4.475,   1.769,  0 },
/* Ln Treated SysBP */           { 2.019,   29.291,  1.797,  1.916 },
/* Ln Age x Ln Treated SysBP */  { 0,       -6.432,  0,      0 },
/* Ln Untreated SysBP */         { 1.957,   27.820,  1.764,  1.809 },
/* Ln Age x Ln Untreat SysBP */  { 0,       -6.087,  0,      0 },
/* Current Smoker (1=Y, 0=N) */  { 7.574,   0.691,   7.837,  0.549 },
/* Ln Age x Current Smoker */    { -1.665,  0,       -1.795, 0 },
/* Diabetes (1=Y, 0=N) */        { 0.661,   0.874,   0.658,  0.645 },

/* Mean (Coefficient x Value) */ { -29.18,  86.61,   61.18,  19.54 },
/* Baseline Survival */          { 0.9665,  0.9533,  0.9144, 0.8954 }
  };


  // relative ratios of how often each of these events occurs
  // these should add up to 1.0
  public static final double MI_RATIO = 0.5;
  public static final double STROKE_RATIO = 0.5;
  // TODO: other outcomes?
  
  
  /**
   * Calculates the 10-year ASCVD Risk Estimates, based on the
   * Pooled Cohort Equations for Estimation of 10-Year Risk of Hard ASCVD.
   * https://doi.org/10.1161/01.cir.0000437741.48606.98
   * @param person The patient.
   * @param time Time to calculate risk as of
   * @param perTimestep Whether to return the risk per timestep (default 7 days) or per 10 years
   * @return the patient's ASCVD risk score
   */
  public static double ascvd10Year(Person person, long time, boolean perTimestep) {
    int age = person.ageInYears(time);
    String gender = (String) person.attributes.get(Person.GENDER);
    String race = (String) person.attributes.get(Person.RACE);
    Double sysBP = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
    Double diaBP = person.getVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, time);
    Double totalChol = person.getVitalSign(VitalSign.TOTAL_CHOLESTEROL, time);
    Double hdl = person.getVitalSign(VitalSign.HDL, time);

    boolean smoker = (Boolean) person.attributes.getOrDefault(Person.SMOKER, false);
    boolean diabetic = (Boolean) person.attributes.getOrDefault("diabetes", false);
    boolean hypertensive = (Boolean) person.attributes.getOrDefault("hypertension", false);
    if (sysBP == null || diaBP == null || totalChol == null) {
      return -1;
    }
    if (age < 40 || age > 79) {
      return -1;
    }
    double lnAge = Math.log(age);
    double lnTotalChol = Math.log(totalChol);
    double lnHdl = Math.log(hdl);
    double lnTreatedSBP = hypertensive ? Math.log(sysBP) : 0;
    double lnUntreatSBP = hypertensive ? 0 : Math.log(sysBP);
    int smokerInt = smoker ? 1 : 0;
    int diabeticInt = diabetic ? 1 : 0;

    double[] values = {
/* Ln Age (y) */                lnAge,
/* (Ln Age)^2 */                (lnAge * lnAge),
/* Ln Total Chol */             lnTotalChol,
/* Ln Age x Ln Total Chol */    (lnAge * lnTotalChol),
/* Ln HDL-C */                  lnHdl,
/* Ln Age x Ln HDL-C */         (lnAge * lnHdl),
/* Ln Treated SysBP */          lnTreatedSBP,
/* Ln Age x Ln Treated SysBP */ (lnAge * lnTreatedSBP),
/* Ln Untreated SysBP */        lnUntreatSBP,
/* Ln Age x Ln Untreat SysBP */ (lnAge * lnUntreatSBP),
/* Current Smoker (1=Y, 0=N) */ smokerInt,
/* Ln Age x Current Smoker */   (lnAge * smokerInt),
/* Diabetes (1=Y, 0=N) */       diabeticInt
    };

    int raceSexIndex = 0; // index in ASCVD_COEFFICIENTS above
    if (gender.equals("M"))
      raceSexIndex += 2;
    if (race.equals("black"))
      raceSexIndex += 1;

    double raceSexMean = ASCVD_COEFFICIENTS[13][raceSexIndex];
    double baselineSurvival = ASCVD_COEFFICIENTS[14][raceSexIndex];
    double individualSum = 0;

    for (int i = 0; i < 13; i++) {
      individualSum += ASCVD_COEFFICIENTS[i][raceSexIndex] * values[i];
    }

    double ascvdRisk = (1 - Math.pow(baselineSurvival, Math.exp(individualSum - raceSexMean)));

    if (perTimestep) {
      ascvdRisk = Utilities.convertRiskToTimestep(ascvdRisk, TEN_YEARS_IN_MS);
    }
    
    return ascvdRisk;
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
