package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.VitalSign;

public final class CardiovascularDiseaseModule extends Module {
  public CardiovascularDiseaseModule() {
    this.name = "Cardiovascular Disease";
  }

  @Override
  public boolean process(Person person, long time) {
    // run through all of the rules defined
    // ruby "rules" are converted to static functions here
    // since this is intended to only be temporary
    // until we can convert this module to GMF

    calculateCardioRisk(person, time);
    onsetCoronaryHeartDisease(person, time);
    coronaryHeartDiseaseProgression(person, time);
    noCoronaryHeartDisease(person, time);
    calculateAtrialFibrillationRisk(person, time);
    getAtrialFibrillation(person, time);
    calculateStrokeRisk(person, time);
    getStroke(person, time);
    heartHealthyLifestyle(person, time);
    chdTreatment(person, time);
    atrialFibrillationTreatment(person, time);

    // java modules will never "finish"
    return false;
  }

  //////////////
  // RESOURCES//
  //////////////

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

  private static final Map<Integer, Double> risk_chd_m;
  private static final Map<Integer, Double> risk_chd_f;

  private static final int[] hdl_lookup_chd = new int[] { 2, 1, 0, -1 }; // <40, 40-49, 50-59, >60

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

  private static final Map<String, Code> LOOKUP;
  private static final Map<String, Integer> MEDICATION_AVAILABLE;
  private static final Map<String, List<String>> EMERGENCY_MEDS;
  private static final Map<String, List<String>> EMERGENCY_PROCEDURES;
  private static final Map<String, List<String>> HISTORY_CONDITIONS;

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

    MEDICATION_AVAILABLE = new HashMap<>();
    MEDICATION_AVAILABLE.put("clopidogrel", 1997);
    MEDICATION_AVAILABLE.put("simvastatin", 1991);
    MEDICATION_AVAILABLE.put("amlodipine", 1994);
    MEDICATION_AVAILABLE.put("nitroglycerin", 1878);
    MEDICATION_AVAILABLE.put("warfarin", 1954);
    MEDICATION_AVAILABLE.put("verapamil", 1981);
    MEDICATION_AVAILABLE.put("digoxin", 1954);
    MEDICATION_AVAILABLE.put("atorvastatin", 1996);
    MEDICATION_AVAILABLE.put("captopril", 1981);
    MEDICATION_AVAILABLE.put("alteplase", 1987);
    MEDICATION_AVAILABLE.put("epinephrine", 1906);
    MEDICATION_AVAILABLE.put("amiodarone", 1962);
    MEDICATION_AVAILABLE.put("atropine", 1903);

    LOOKUP = new HashMap<>();
    // conditions
    LOOKUP.put("stroke", new Code("SNOMED-CT", "230690007", "Stroke"));
    LOOKUP.put("natural_causes",
        new Code("SNOMED-CT", "9855000", "Natural death with unknown cause"));
    LOOKUP.put("coronary_heart_disease",
        new Code("SNOMED-CT", "53741008", "Coronary Heart Disease"));
    LOOKUP.put("myocardial_infarction", new Code("SNOMED-CT", "22298006", "Myocardial Infarction"));
    LOOKUP.put("cardiac_arrest", new Code("SNOMED-CT", "410429000", "Cardiac Arrest"));
    LOOKUP.put("atrial_fibrillation", new Code("SNOMED-CT", "49436004", "Atrial Fibrillation"));
    LOOKUP.put("cardiovascular_disease",
        new Code("SNOMED-CT", "49601007", "Disorder of cardiovascular system"));
    LOOKUP.put("history_of_myocardial_infarction",
        new Code("SNOMED-CT", "399211009", "History of myocardial infarction (situation)"));
    LOOKUP.put("history_of_cardiac_arrest",
        new Code("SNOMED-CT", "429007001", "History of cardiac arrest (situation)"));

    // procedures
    LOOKUP.put("defibrillation", new Code("SNOMED-CT", "429500007", "Monophasic defibrillation"));
    LOOKUP.put("implant_cardioverter_defib", new Code("SNOMED-CT", "447365002",
        "Insertion of biventricular implantable cardioverter defibrillator"));
    LOOKUP.put("catheter_ablation",
        new Code("SNOMED-CT", "18286008", "Catheter ablation of tissue of heart"));
    LOOKUP.put("percutaneous_coronary_intervention",
        new Code("SNOMED-CT", "415070008", "Percutaneous coronary intervention"));
    LOOKUP.put("coronary_artery_bypass_grafting",
        new Code("SNOMED-CT", "232717009", "Coronary artery bypass grafting"));
    LOOKUP.put("mechanical_thrombectomy", new Code("SNOMED-CT", "433112001",
        "Percutaneous mechanical thrombectomy of portal vein using fluoroscopic guidance"));
    LOOKUP.put("electrical_cardioversion",
        new Code("SNOMED-CT", "180325003", "Electrical cardioversion"));

    // medications
    LOOKUP.put("clopidogrel", new Code("RxNorm", "309362", "Clopidogrel 75 MG Oral Tablet"));
    LOOKUP.put("simvastatin", new Code("RxNorm", "312961", "Simvastatin 20 MG Oral Tablet"));
    LOOKUP.put("amlodipine", new Code("RxNorm", "197361", "Amlodipine 5 MG Oral Tablet"));
    LOOKUP.put("nitroglycerin",
        new Code("RxNorm", "564666", "Nitroglycerin 0.4 MG/ACTUAT [Nitrolingual]"));
    LOOKUP.put("atorvastatin", new Code("RxNorm", "259255", "Atorvastatin 80 MG Oral Tablet"));
    LOOKUP.put("captopril", new Code("RxNorm", "833036", "Captopril 25 MG Oral Tablet"));
    LOOKUP.put("warfarin", new Code("RxNorm", "855332", "Warfarin Sodium 5 MG Oral Tablet"));
    LOOKUP.put("verapamil", new Code("RxNorm", "897718", "Verapamil Hydrochloride 40 MG"));
    LOOKUP.put("digoxin", new Code("RxNorm", "197604", "Digoxin 0.125 MG Oral Tablet"));
    LOOKUP.put("epinephrine",
        new Code("RxNorm", "727374", "1 ML Epinephrine 1 MG/ML Prefilled Syringe"));
    LOOKUP.put("amiodarone",
        new Code("RxNorm", "834357", "3 ML Amiodarone hydrocholoride 50 MG/ML Prefilled Syringe"));
    LOOKUP.put("atropine",
        new Code("RxNorm", "1190795", "Atropine Sulfate 1 MG/ML Injectable Solution"));
    LOOKUP.put("alteplase", new Code("RxNorm", "308056", "Alteplase 1 MG/ML Injectable Solution"));

    // reasons
    LOOKUP.put("stop_drug",
        new Code("SNOMED-CT", "182846007", "Dr stopped drug - medical aim achieved"));
    LOOKUP.put("cardiovascular_improved", new Code("SNOMED-CT", "413757005",
        "Cardiac status is consistent with or improved from preoperative baseline"));

    EMERGENCY_MEDS = new HashMap<>();
    EMERGENCY_MEDS.put("myocardial_infarction",
        Arrays.asList("nitroglycerin", "atorvastatin", "captopril", "clopidogrel"));
    EMERGENCY_MEDS.put("stroke", Arrays.asList("clopidogrel", "alteplase"));
    EMERGENCY_MEDS.put("cardiac_arrest", Arrays.asList("epinephrine", "amiodarone", "atropine"));

    EMERGENCY_PROCEDURES = new HashMap<>();
    EMERGENCY_PROCEDURES.put("myocardial_infarction",
        Arrays.asList("percutaneous_coronary_intervention", "coronary_artery_bypass_grafting"));
    EMERGENCY_PROCEDURES.put("stroke", Arrays.asList("mechanical_thrombectomy"));
    EMERGENCY_PROCEDURES.put("cardiac_arrest",
        Arrays.asList("implant_cardioverter_defib", "catheter_ablation"));

    HISTORY_CONDITIONS = new HashMap<>();
    HISTORY_CONDITIONS.put("myocardial_infarction",
        Arrays.asList("history_of_myocardial_infarction"));
    HISTORY_CONDITIONS.put("stroke", Arrays.asList());
    HISTORY_CONDITIONS.put("cardiac_arrest", Arrays.asList("history_of_cardiac_arrest"));
  }

  private static List<String> filter_meds_by_year(List<String> meds, long time) {
    int year = Utilities.getYear(time);
    return meds.stream().filter(med -> year >= MEDICATION_AVAILABLE.get(med))
        .collect(Collectors.toList());
  }

  ////////////////////
  // MIGRATED RULES //
  ////////////////////
  private static int bound(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }

  private static void calculateCardioRisk(Person person, long time) {
    int age = person.ageInYears(time);
    String gender = (String) person.attributes.get(Person.GENDER);
    Double sysBP = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE);
    Double chol = person.getVitalSign(VitalSign.TOTAL_CHOLESTEROL);
    if (sysBP == null || chol == null) {
      return;
    }

    Boolean bpTreated = (Boolean) person.attributes.getOrDefault("bp_treated?", false);

    Double hdl = person.getVitalSign(VitalSign.HDL);

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
    double risk;
    // restrict lower and upper bound of framingham score
    if (gender.equals("M")) {
      framinghamPoints = bound(framinghamPoints, 0, 17);
      risk = risk_chd_m.get(framinghamPoints);
    } else {
      framinghamPoints = bound(framinghamPoints, 8, 25);
      risk = risk_chd_f.get(framinghamPoints);
    }

    person.attributes.put("cardio_risk",
        Utilities.convertRiskToTimestep(risk, TimeUnit.DAYS.toMillis(3650)));
  }

  private static void onsetCoronaryHeartDisease(Person person, long time) {
    if (person.attributes.containsKey("coronary_heart_disease")) {
      return;
    }

    double cardioRisk = (double) person.attributes.getOrDefault("cardio_risk", -1.0);
    if (person.rand() < cardioRisk) {
      person.attributes.put("coronary_heart_disease", true);
      person.events.create(time, "coronary_heart_disease", "onsetCoronaryHeartDisease", true);
    }
  }

  private static void coronaryHeartDiseaseProgression(Person person, long time) {
    // numbers are from appendix:
    // http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
    boolean coronaryHeartDisease = (Boolean) person.attributes
        .getOrDefault("coronary_heart_disease", false);

    if (!coronaryHeartDisease) {
      return;
    }

    String gender = (String) person.attributes.get(Person.GENDER);

    double annualRisk;
    // http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
    // annual probability of coronary attack given history of angina
    if (gender.equals("M")) {
      annualRisk = 0.042;
    } else {
      annualRisk = 0.015;
    }

    double cardiacEventChance = Utilities.convertRiskToTimestep(annualRisk,
        TimeUnit.DAYS.toMillis(365));

    if (person.rand() < cardiacEventChance) {
      String cardiacEvent;

      // Proportion of coronary attacks that are MI ,given history of CHD
      if (person.rand() < 0.8) {
        cardiacEvent = "myocardial_infarction";
      } else {
        cardiacEvent = "cardiac_arrest";
      }

      person.events.create(time, cardiacEvent, "coronaryHeartDiseaseProgression", false);
      // creates unprocessed emergency encounter. Will be processed at next time step.
      person.events.create(time, "emergency_encounter", "coronaryHeartDiseaseProgression", false);

      EncounterModule.emergencyVisit(person, time);

      double survivalRate = 0.095; // http://cpr.heart.org/AHAECC/CPRAndECC/General/UCM_477263_Cardiac-Arrest-Statistics.jsp
      // survival rate triples if a bystander is present
      // http://cpr.heart.org/AHAECC/CPRAndECC/AboutCPRFirstAid/CPRFactsAndStats/UCM_475748_CPR-Facts-and-Stats.jsp
      if (person.rand() < 0.46) {
        survivalRate *= 3.0;
      }

      if (person.rand() > survivalRate) {
        person.recordDeath(time, LOOKUP.get(cardiacEvent), "coronaryHeartDiseaseProgression");
      }
    }
  }

  private static void noCoronaryHeartDisease(Person person, long time) {
    // chance of getting a sudden cardiac arrest without heart disease. (Most probable cardiac event
    // w/o cause or history)
    if (person.attributes.containsKey("coronary_heart_disease")) {
      return;
    }

    double annualRisk = 0.00076;
    double cardiacEventChance = Utilities.convertRiskToTimestep(annualRisk,
        TimeUnit.DAYS.toMillis(365));
    if (person.rand() < cardiacEventChance) {
      person.events.create(time, "cardiac_arrest", "noCoronaryHeartDisease", false);
      person.events.create(time, "emergency_encounter", "noCoronaryHeartDisease", false);
      EncounterModule.emergencyVisit(person, time);
      double survivalRate = 1 - (0.00069);
      if (person.rand() < 0.46) {
        survivalRate *= 3.0;
      }
      double annualDeathRisk = 1 - survivalRate;
      if (person.rand() < Utilities.convertRiskToTimestep(annualDeathRisk,
          TimeUnit.DAYS.toMillis(365))) {
        person.recordDeath(time, LOOKUP.get("cardiac_arrest"), "noCoronaryHeartDisease");
      }
    }
  }

  private static void calculateAtrialFibrillationRisk(Person person, long time) {
    int age = person.ageInYears(time);
    if (age < 45 || person.attributes.containsKey("atrial_fibrillation")
        || person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE) == null
        || person.getVitalSign(VitalSign.BMI) == null) {
      return;
    }

    int afScore = 0;
    int ageRange = Math.min((age - 45) / 5, 8);
    int genderIndex = (person.attributes.get(Person.GENDER).equals("M")) ? 0 : 1;
    afScore += age_af[genderIndex][ageRange];
    if (person.getVitalSign(VitalSign.BMI) >= 30) {
      afScore += 1;
    }

    if (person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE) >= 160) {
      afScore += 1;
    }

    if ((Boolean) person.attributes.getOrDefault("bp_treated?", false)) {
      afScore += 1;
    }

    afScore = bound(afScore, 0, 10);

    double afRisk = risk_af_table[afScore]; // 10-yr risk
    person.attributes.put("atrial_fibrillation_risk",
        Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
  }

  private static void getAtrialFibrillation(Person person, long time) {
    if (!person.attributes.containsKey("atrial_fibrillation")
        && person.attributes.containsKey("atrial_fibrillation_risk")
        && person.rand() < (Double) person.attributes.get("atrial_fibrillation_risk")) {
      person.events.create(time, "atrial_fibrillation", "getAtrialFibrillation", false);
      person.attributes.put("atrial_fibrillation", true);
    }
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

  private static void calculateStrokeRisk(Person person, long time) {
    Double bloodPressure = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE);
    if (bloodPressure == null) {
      return;
    }

    // https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
    // calculate stroke risk based off of prevalence of stroke in age group for people younger than
    // 54. Framingham score system does not cover these.

    int genderIndex = ((String) person.attributes.get(Person.GENDER)).equals("M") ? 0 : 1;

    int age = person.ageInYears(time);

    if (age < 20) {
      // no risk set
      return;
    } else if (age < 40) {
      double rate = stroke_rate_20_39[genderIndex];
      person.attributes.put("stroke_risk",
          Utilities.convertRiskToTimestep(rate, TimeUnit.DAYS.toMillis(3650)));
      return;
    } else if (age < 55) {
      double rate = stroke_rate_40_59[genderIndex];
      person.attributes.put("stroke_risk",
          Utilities.convertRiskToTimestep(rate, TimeUnit.DAYS.toMillis(3650)));
      return;
    }

    int strokePoints = 0;
    if ((Boolean) person.attributes.getOrDefault(Person.SMOKER, false)) {
      strokePoints += 3;
    }
    if ((Boolean) person.attributes.getOrDefault("left_ventricular_hypertrophy", false)) {
      strokePoints += 5;
    }

    strokePoints += getIndexForValueInRangelist(age, age_stroke[genderIndex]);

    int bp = bloodPressure.intValue();
    // TODO treating blood pressure currently is not a feature. Modify this for when it is.
    if ((Boolean) person.attributes.getOrDefault("bp_treated?", false)) {
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

    double tenStrokeRisk;

    if (strokePoints >= ten_year_stroke_risk[genderIndex].length) {
      // off the charts
      int worstCase = ten_year_stroke_risk[genderIndex].length - 1;
      tenStrokeRisk = ten_year_stroke_risk[genderIndex][worstCase];
    } else {
      tenStrokeRisk = ten_year_stroke_risk[genderIndex][strokePoints];
    }

    // divide 10 year risk by 365 * 10 to get daily risk.
    person.attributes.put("stroke_risk",
        Utilities.convertRiskToTimestep(tenStrokeRisk, TimeUnit.DAYS.toMillis(3650)));
    person.attributes.put("stroke_points", strokePoints);
  }

  private static void getStroke(Person person, long time) {
    if (person.attributes.containsKey("stroke_risk")
        && person.rand() < (Double) person.attributes.get("stroke_risk")) {
      person.events.create(time, "stroke", "getStroke", false);
      person.attributes.put("stroke_history", true);
      person.events.create(time + TimeUnit.MINUTES.toMillis(10), "emergency_encounter", "getStroke",
          false);
      EncounterModule.emergencyVisit(person, time);
      // Strokes are fatal 10-20 percent of cases
      // https://stroke.nih.gov/materials/strokechallenges.htm
      if (person.rand() < 0.15) {
        person.recordDeath(time, LOOKUP.get("stroke"), "getStroke");
      }
    }
  }

  private static void heartHealthyLifestyle(Person person, long time) {
    // TODO - intentionally ignoring this rule for now; it only sets careplan activities and reasons
  }

  private static void chdTreatment(Person person, long time) {
    List<String> meds = filter_meds_by_year(
        Arrays.asList("clopidogrel", "simvastatin", "amlodipine", "nitroglycerin"), time);

    if ((Boolean) person.attributes.getOrDefault("coronary_heart_disease", false)) {
      for (String med : meds) {
        prescribeMedication(med, person, time);
      }
    } else {
      for (String med : meds) {
        stopMedication(med, person, time);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void atrialFibrillationTreatment(Person person, long time) {
    List<String> meds = filter_meds_by_year(Arrays.asList("warfarin", "verapamil", "digoxin"),
        time);

    if ((Boolean) person.attributes.getOrDefault("atrial_fibrillation", false)) {
      for (String med : meds) {
        prescribeMedication(med, person, time);
      }

      // catheter ablation is a more extreme measure than electrical cardioversion and is usually
      // only performed
      // when medication and other procedures are not preferred or have failed. As a rough
      // simulation of this,
      // we arbitrarily chose a 20% chance of getting catheter ablation and 80% of getting
      // cardioversion
      String afibProcedure = person.rand() < 0.2 ? "catheter_ablation" : "electrical_cardioversion";

      Map<String, List<String>> cardiovascularProcedures = 
          (Map<String, List<String>>) person.attributes.get("cardiovascular_procedures");

      if (cardiovascularProcedures == null) {
        cardiovascularProcedures = new HashMap<String, List<String>>();
        person.attributes.put("cardiovascular_procedures", cardiovascularProcedures);
      }
      cardiovascularProcedures.put("atrial_fibrillation", Arrays.asList(afibProcedure));
    } else {
      for (String med : meds) {
        stopMedication(med, person, time);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void prescribeMedication(String med, Person person, long time) {
    if (!person.record.medicationActive(med)) {
      // add med to med_changes
      Set<String> medChanges = 
          (Set<String>) person.attributes.get("cardiovascular_disease_med_changes");

      if (medChanges == null) {
        medChanges = new HashSet<String>();
        person.attributes.put("cardiovascular_disease_med_changes", medChanges);
      }
      medChanges.add(med);
    }
  }

  @SuppressWarnings("unchecked")
  private static void stopMedication(String med, Person person, long time) {
    if (person.record.medicationActive(med)) {
      // add med to med_changes
      Set<String> medChanges = 
          (Set<String>) person.attributes.get("cardiovascular_disease_med_changes");

      if (medChanges == null) {
        medChanges = new HashSet<String>();
        person.attributes.put("cardiovascular_disease_med_changes", medChanges);
      }
      medChanges.add(med);
    }
  }

  /**
   * Perform Cardiovasular Disease Encounter.
   * @param person The patient.
   * @param time The time of the encounter.
   */
  @SuppressWarnings("unchecked")
  public static void performEncounter(Person person, long time) {
    int year = Utilities.getYear(time);

    // step 1 - diagnosis
    for (String diagnosis : new String[] { "coronary_heart_disease", "atrial_fibrillation" }) {
      if ((Boolean) person.attributes.getOrDefault(diagnosis, false)
          && !person.record.present.containsKey(diagnosis)) {
        Code code = LOOKUP.get(diagnosis);
        Entry conditionEntry = person.record.conditionStart(time, code.display);
        conditionEntry.codes.add(code);
      }
    }

    // step 2 - care plan
    // TODO - intentionally ignored at the moment

    // step 3 - medications
    Set<String> medChanges = 
        (Set<String>) person.attributes.get("cardiovascular_disease_med_changes");

    if (medChanges != null) {
      for (String med : medChanges) {
        if (person.record.medicationActive(med)) {
          // This prescription can be stopped...
          person.record.medicationEnd(time, med, LOOKUP.get("cardiovascular_improved"));
        } else {
          Medication entry = person.record.medicationStart(time, med);
          entry.codes.add(LOOKUP.get(med));
          // increment number of prescriptions prescribed by respective hospital
          Provider provider = person.getCurrentProvider("Cardiovascular Disease Module");
          // no provider associated with encounter or procedure
          if (provider == null) {
            provider = person.getAmbulatoryProvider(time);
          }
          provider.incrementPrescriptions(year);
        }
      }

      medChanges.clear();
    }

    // step 4 - procedures
    Map<String, List<String>> cardiovascularProcedures = 
        (Map<String, List<String>>) person.attributes.get("cardiovascular_procedures");

    if (cardiovascularProcedures != null) {
      for (Map.Entry<String, List<String>> entry : cardiovascularProcedures.entrySet()) {
        String reason = entry.getKey();
        List<String> procedures = entry.getValue();

        for (String proc : procedures) {
          if (!person.record.present.containsKey(proc)) {
            // TODO: assumes a procedure will only be performed once, might need to be revisited
            Code code = LOOKUP.get(proc);
            Procedure procedure = person.record.procedure(time, code.display);
            procedure.name = "CardiovascularDisease_Encounter";
            procedure.codes.add(code);
            procedure.reasons.add(LOOKUP.get(reason));

            // increment number of procedures by respective hospital
            Provider provider = person.getCurrentProvider("Cardiovascular Disease Module");
            // no provider associated with encounter or procedure
            if (provider == null) {
              provider = person.getAmbulatoryProvider(time);
            }
            provider.incrementProcedures(year);
          }
        }
      }
    }
  }

  /**
   * Perform an emergency cardiovascular disease encounter.
   * @param person The patient.
   * @param time The time of the emergency.
   * @param diagnosis The diagnosis to be made.
   */
  public static void performEmergency(Person person, long time, String diagnosis) {
    Provider provider = person.getEmergencyProvider(time);

    int year = Utilities.getYear(time);

    Entry condition = person.record.conditionStart(time, diagnosis);
    condition.codes.add(LOOKUP.get(diagnosis));

    for (String med : filter_meds_by_year(EMERGENCY_MEDS.get(diagnosis), time)) {
      Medication medication = person.record.medicationStart(time, med);
      medication.codes.add(LOOKUP.get(med));
      // increment number of prescriptions prescribed by respective hospital

      provider.incrementPrescriptions(year);
      person.record.medicationEnd(time + TimeUnit.MINUTES.toMillis(15), med,
          LOOKUP.get("stop_drug"));
    }

    for (String proc : EMERGENCY_PROCEDURES.get(diagnosis)) {
      Procedure procedure = person.record.procedure(time, proc);
      procedure.name = "CardiovascularDisease_Emergency";
      procedure.codes.add(LOOKUP.get(proc));
      procedure.reasons.add(LOOKUP.get(diagnosis));
      // increment number of procedures performed by respective hospital
      provider.incrementProcedures(year);
    }

    for (String cond : HISTORY_CONDITIONS.get(diagnosis)) {
      Entry historyCond = person.record.conditionStart(time, cond);
      historyCond.codes.add(LOOKUP.get(cond));
    }
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   * 
   * @return Collection of all codes and concepts this module uses
   */
  public static Collection<Code> getAllCodes() {
    return LOOKUP.values();
  }
}
