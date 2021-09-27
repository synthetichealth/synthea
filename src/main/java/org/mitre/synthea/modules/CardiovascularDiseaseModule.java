package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.risk_calculators.ASCVD;
import org.mitre.synthea.modules.risk_calculators.CHADSVASC;
import org.mitre.synthea.modules.risk_calculators.Framingham;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

import org.mitre.synthea.world.concepts.VitalSign;

public final class CardiovascularDiseaseModule extends Module {
  public CardiovascularDiseaseModule() {
    this.name = "Cardiovascular Disease";
  }

  public Module clone() {
    return this;
  }

  private static final boolean USE_FRAMINGHAM = false;
  
  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }
    // run through all of the rules defined
    // ruby "rules" are converted to static functions here
    // since this is intended to only be temporary
    // until we can convert this module to GMF

    // TODO: make this a config parameter for which risk system we want to use
    if (USE_FRAMINGHAM) {
        calculateCardioRisk(person, time);
    } else {
        calculateAscvdRisk(person, time);
    }

    double framinghamCVD = Framingham.cvd10Year(person, time, false);
    person.attributes.put("framingham_cvd", framinghamCVD);
    person.attributes.put("framingham_cvd_pct", 100.0 * framinghamCVD);

//    onsetCoronaryHeartDisease(person, time);
//    coronaryHeartDiseaseProgression(person, time);
//    noCoronaryHeartDisease(person, time);
    calculateAtrialFibrillationRisk(person, time);
//    getAtrialFibrillation(person, time);
    calculateStrokeRisk(person, time);
//    getStroke(person, time);
//    endEmergency(person, time);

    // java modules will never "finish"
    return false;
  }

  //////////////
  // RESOURCES//
  //////////////

  private static final String CVD_ENCOUNTER = "cardiovascular_encounter";
  private static final Map<String, Code> LOOKUP;
  private static final Map<String, Integer> MEDICATION_AVAILABLE;
  private static final Map<String, List<String>> EMERGENCY_MEDS;
  private static final Map<String, List<String>> EMERGENCY_PROCEDURES;
  private static final Map<String, List<String>> HISTORY_CONDITIONS;

  static {
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
    LOOKUP.put("echocardiogram",
        new Code("SNOMED-CT", "40701008", "Echocardiography (procedure)"));

    // devices
    LOOKUP.put("defibrillator",
        new Code("SNOMED-CT", "72506001", "Implantable defibrillator, device (physical object)"));
    LOOKUP.put("stent",
        new Code("SNOMED-CT", "705643001", "Coronary artery stent (physical object)"));
    LOOKUP.put("pacemaker",
        new Code("SNOMED-CT", "706004007", "Implantable cardiac pacemaker (physical object)"));

    // medications
    LOOKUP.put("clopidogrel", new Code("RxNorm", "309362", "Clopidogrel 75 MG Oral Tablet"));
    LOOKUP.put("simvastatin", new Code("RxNorm", "312961", "Simvastatin 20 MG Oral Tablet"));
    LOOKUP.put("amlodipine", new Code("RxNorm", "197361", "Amlodipine 5 MG Oral Tablet"));
    LOOKUP.put("nitroglycerin",
        new Code("RxNorm", "705129", "Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray"));
    LOOKUP.put("atorvastatin", new Code("RxNorm", "259255", "Atorvastatin 80 MG Oral Tablet"));
    LOOKUP.put("captopril", new Code("RxNorm", "833036", "Captopril 25 MG Oral Tablet"));
    LOOKUP.put("warfarin", new Code("RxNorm", "855332", "Warfarin Sodium 5 MG Oral Tablet"));
    LOOKUP.put("verapamil", new Code("RxNorm", "897718", "Verapamil Hydrochloride 40 MG"));
    LOOKUP.put("digoxin", new Code("RxNorm", "197604", "Digoxin 0.125 MG Oral Tablet"));
    LOOKUP.put("epinephrine",
        new Code("RxNorm", "1660014", "1 ML Epinephrine 1 MG/ML Injection"));
    LOOKUP.put("amiodarone",
        new Code("RxNorm", "834357", "3 ML Amiodarone hydrocholoride 50 MG/ML Prefilled Syringe"));
    LOOKUP.put("atropine",
        new Code("RxNorm", "1190795", "Atropine Sulfate 1 MG/ML Injectable Solution"));
    LOOKUP.put("alteplase", new Code("RxNorm", "1804799", "Alteplase 100 MG Injection"));

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

  private static final long tenYearsInMS = TimeUnit.DAYS.toMillis(3652);
  private static final long oneYearInMS = TimeUnit.DAYS.toMillis(365);
  private static final long oneMonthInMS = TimeUnit.DAYS.toMillis(30); // roughly
  
  /**
   * Calculates the risk of cardiovascular disease using Framingham points
   * and look up tables, putting the current risk in a "cardio_risk" attribute.
   * @param person The patient.
   * @param time The risk is calculated for the given time.
   */
  private static void calculateCardioRisk(Person person, long time) {
    double framinghamRisk = Framingham.chd10Year(person, time, false);
    person.attributes.put("framingham_risk", framinghamRisk);

    double timestepRisk = Utilities.convertRiskToTimestep(framinghamRisk, tenYearsInMS);
    person.attributes.put("cardio_risk", timestepRisk);
    
    double monthlyRisk = Utilities.convertRiskToTimestep(framinghamRisk, tenYearsInMS, oneMonthInMS);
    person.attributes.put("mi_risk", monthlyRisk);
    // drives the myocardial_infarction module
    
    person.attributes.put("ihd_risk", monthlyRisk * 5);
    // drives the stable_ischemic_heart_disease module
    // multiply by 5 to account for the relative prevalence of the various outcomes
  }
  
 
  /**
   * Calculates the 10-year ASCVD Risk Estimates.
   */
  private static void calculateAscvdRisk(Person person, long time) {
	  double ascvdRisk = ASCVD.ascvd10Year(person, time, false);
    person.attributes.put("ascvd_risk", ascvdRisk);

    double timestepRisk = Utilities.convertRiskToTimestep(ascvdRisk, tenYearsInMS);
    person.attributes.put("cardio_risk", timestepRisk);
    
    double monthlyRisk = Utilities.convertRiskToTimestep(ascvdRisk, tenYearsInMS, oneMonthInMS);
    person.attributes.put("mi_risk", monthlyRisk * ASCVD.MI_RATIO);
    // drives the myocardial_infarction module
    
    person.attributes.put("ihd_risk", monthlyRisk * 5);
    // drives the stable_ischemic_heart_disease module
    // multiply by 5 to account for the relative prevalence of the various outcomes
  }


  // /**
  //  * The patient rolls the probability dice. If their roll is less than their
  //  * "cardio_risk" attribute, then coronary heart disease begins.
  //  * @param person The patient.
  //  * @param time The time.
  //  */
  // private static void onsetCoronaryHeartDisease(Person person, long time) {
  //   if (person.attributes.containsKey("coronary_heart_disease")) {
  //     return;
  //   }

  //   double cardioRisk = (double) person.attributes.getOrDefault("cardio_risk", -1.0);
  //   if (person.rand() < cardioRisk) {
  //     person.attributes.put("coronary_heart_disease", true);
  //   }
  // }

  /**
   * If the patient has "coronary_heart_disease", there is a small chance they
   * will have a cardiac emergency: "myocardial_infarction" or "cardiac_arrest".
   * @param person The patient.
   * @param time The time.
   */
  // private static void coronaryHeartDiseaseProgression(Person person, long time) {
  //   // numbers are from appendix:
  //   // http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
  //   boolean coronaryHeartDisease = (Boolean) person.attributes
  //       .getOrDefault("coronary_heart_disease", false);

  //   if (!coronaryHeartDisease) {
  //     return;
  //   }

  //   String gender = (String) person.attributes.get(Person.GENDER);

  //   double annualRisk;
  //   // http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
  //   // annual probability of coronary attack given history of angina
  //   if (gender.equals("M")) {
  //     annualRisk = 0.042;
  //   } else {
  //     annualRisk = 0.015;
  //   }

  //   double cardiacEventChance = Utilities.convertRiskToTimestep(annualRisk,
  //       TimeUnit.DAYS.toMillis(365));

  //   if (person.rand() < cardiacEventChance) {
  //     String cardiacEvent;

  //     // Proportion of coronary attacks that are MI, given history of CHD
  //     if (person.rand() < 0.8) {
  //       cardiacEvent = "myocardial_infarction";
  //     } else {
  //       cardiacEvent = "cardiac_arrest";
  //     }

  //     // Make sure the Emergency Encounter has started...
  //     Code code = LOOKUP.get(cardiacEvent);
  //     beginOrContinueEmergency(person, time, code);
  //     performEmergency(person, time, cardiacEvent);

  //     double survivalRate = 0.095; // http://cpr.heart.org/AHAECC/CPRAndECC/General/UCM_477263_Cardiac-Arrest-Statistics.jsp
  //     // survival rate triples if a bystander is present
  //     // http://cpr.heart.org/AHAECC/CPRAndECC/AboutCPRFirstAid/CPRFactsAndStats/UCM_475748_CPR-Facts-and-Stats.jsp
  //     if (person.rand() < 0.46) {
  //       survivalRate *= 3.0;
  //     }

  //     if (person.rand() > survivalRate) {
  //       person.recordDeath(time, LOOKUP.get(cardiacEvent));
  //     }
  //   }
  // }

  /**
   * If the patient does NOT have "coronary_heart_disease", there is a very small chance
   * they will have "cardiac_arrest".
   * @param person The patient.
   * @param time The time.
   */
  // private static void noCoronaryHeartDisease(Person person, long time) {
  //   // chance of getting a sudden cardiac arrest without heart disease. (Most probable cardiac event
  //   // w/o cause or history)
  //   if (person.attributes.containsKey("coronary_heart_disease")) {
  //     return;
  //   }

  //   double annualRisk = 0.00076;
  //   double cardiacEventChance = Utilities.convertRiskToTimestep(annualRisk,
  //       TimeUnit.DAYS.toMillis(365));
  //   if (person.rand() < cardiacEventChance) {
  //     // Make sure the Emergency Encounter has started...
  //     Code code = LOOKUP.get("cardiac_arrest");
  //     beginOrContinueEmergency(person, time, code);
  //     performEmergency(person, time, "cardiac_arrest");

  //     double survivalRate = 1 - (0.00069);
  //     if (person.rand() < 0.46) {
  //       survivalRate *= 3.0;
  //     }
  //     double annualDeathRisk = 1 - survivalRate;
  //     if (person.rand() < Utilities.convertRiskToTimestep(annualDeathRisk,
  //         TimeUnit.DAYS.toMillis(365))) {
  //       person.recordDeath(time, LOOKUP.get("cardiac_arrest"));
  //     }
  //   }
  // }

  /**
   * Depending on gender, BMI, and blood pressure, there is a small risk of
   * Atrial Fibrillation which is calculated and stored in "atrial_fibrillation_risk".
   * @param person The patient.
   * @param time The time.
   */
  private static void calculateAtrialFibrillationRisk(Person person, long time) {
    double afRisk = Framingham.atrialFibrillation10Year(person, time, false);
    person.attributes.put("atrial_fibrillation_risk",
        Utilities.convertRiskToTimestep(afRisk, tenYearsInMS, oneMonthInMS));
  }


  // /**
  //  * The patient rolls the probability dice. If their roll is less than their
  //  * "atrial_fibrillation_risk" attribute, then "atrial_fibrillation" begins.
  //  * @param person The patient.
  //  * @param time The time.
  //  */
  // private static void getAtrialFibrillation(Person person, long time) {
  //   if (!person.attributes.containsKey("atrial_fibrillation")
  //       && person.attributes.containsKey("atrial_fibrillation_risk")
  //       && person.rand() < (Double) person.attributes.get("atrial_fibrillation_risk")) {
  //     person.attributes.put("atrial_fibrillation", true);
  //   }
  // }


  /**
   * Depending on gender, age, smoking status, and various comorbidities (e.g. diabetes,
   * coronary heart disease, atrial fibrillation), this function calculates the risk
   * of a stroke and stores it in the "stroke_risk" attribute.
   * @param person The patient.
   * @param time The time.
   */
  private static void calculateStrokeRisk(Person person, long time) {
    // use the CHA₂DS₂-VASc Score for Atrial Fibrillation Stroke Risk
    // if the patient has AFib
    
    if (USE_FRAMINGHAM) {
      double framingham10YrRisk = Framingham.stroke10Year(person, time, false);
      person.attributes.put("stroke_risk",
          Utilities.convertRiskToTimestep(framingham10YrRisk, tenYearsInMS, oneMonthInMS));
    } else if (person.attributes.containsKey("atrial_fibrillation")) {
      double chadsvasc1YrRisk = CHADSVASC.strokeRisk1Year(person, time);
       person.attributes.put("stroke_risk",
           Utilities.convertRiskToTimestep(chadsvasc1YrRisk, oneYearInMS, oneMonthInMS));
    } else {
      double ascvdRisk = (double) person.attributes.get("ascvd_risk");
      double monthlyRisk = Utilities.convertRiskToTimestep(ascvdRisk, tenYearsInMS, oneMonthInMS);
      person.attributes.put("stroke_risk", monthlyRisk * ASCVD.STROKE_RATIO);
    }
  }

  /**
   * The patient rolls the probability dice. If their roll is less than their
   * "stroke_risk" attribute, then "stroke" and "stroke_history" begin.
   * @param person The patient.
   * @param time The time.
   */
  // private static void getStroke(Person person, long time) {
  //   if (person.attributes.containsKey("stroke_risk")
  //       && person.rand() < (Double) person.attributes.get("stroke_risk")) {
  //     // Make sure the Emergency Encounter has started...
  //     Code code = LOOKUP.get("stroke");
  //     beginOrContinueEmergency(person, time, code);
  //     performEmergency(person, time, "stroke");
  //     person.attributes.put("stroke_history", true);

  //     // Strokes are fatal 10-20 percent of cases
  //     // https://stroke.nih.gov/materials/strokechallenges.htm
  //     if (person.rand() < 0.15) {
  //       person.recordDeath(time, code);
  //     }
  //   }
  // }

  /**
   * Start or stop medication treatment depending on whether or not the patient has
   * "coronary_heart_disease" by looking for the attribute "coronary_heart_disease".
   * @param person The patient.
   * @param time The time.
   */
  private static void chdTreatment(Person person, long time) {
    List<String> meds = filter_meds_by_year(
        Arrays.asList("clopidogrel", "simvastatin", "amlodipine", "nitroglycerin"), time);

    if ((Boolean) person.attributes.getOrDefault("coronary_heart_disease", false)) {
      for (String med : meds) {
        prescribeMedication(med, person, time, true);
      }
    } else {
      for (String med : meds) {
        person.record.medicationEnd(time, med, LOOKUP.get("cardiovascular_improved"));
      }
    }
  }

  /**
   * Start or stop medication treatments and possibly perform a procedural intervention
   * depending on whether or not the patient has "atrial_fibrillation" by looking for
   * the attribute "atrial_fibrillation".
   * @param person The patient.
   * @param time The time.
   */
  private static void atrialFibrillationTreatment(Person person, long time) {
    List<String> meds = filter_meds_by_year(Arrays.asList("warfarin", "verapamil", "digoxin"),
        time);

    if (person.attributes.containsKey("atrial_fibrillation")) {
      for (String med : meds) {
        prescribeMedication(med, person, time, true);
      }

      // catheter ablation is a more extreme measure than electrical cardioversion and is usually
      // only performed
      // when medication and other procedures are not preferred or have failed. As a rough
      // simulation of this,
      // we arbitrarily chose a 20% chance of getting catheter ablation and 80% of getting
      // cardioversion
      String afibProcedure = person.rand() < 0.2 ? "catheter_ablation" : "electrical_cardioversion";
      Code code = LOOKUP.get(afibProcedure);
      Procedure procedure = person.record.procedure(time, code.display);
      procedure.name = "Atrial Fibrillation Treatment";
      procedure.codes.add(code);
      procedure.reasons.add(LOOKUP.get("atrial_fibrillation"));

      if (afibProcedure.equals("catheter_ablation") && person.rand() <= 0.1) {
        // 10.0% chance the patient will receive a pacemaker.
        if (!person.record.present.containsKey("pacemaker")) {
          Entry device = person.record.deviceImplant(time, "pacemaker");
          device.codes.add(LOOKUP.get("pacemaker"));
        }
      }
      // increment number of procedures by respective hospital
      Encounter encounter = (Encounter) person.attributes.get(CVD_ENCOUNTER);
      if (encounter != null) {
        int year = Utilities.getYear(time);
        encounter.provider.incrementProcedures(year);
      }
    } else {
      for (String med : meds) {
        person.record.medicationEnd(time, med, LOOKUP.get("cardiovascular_improved"));
      }
    }
  }

  private static void prescribeMedication(String med, Person person, long time, boolean chronic) {
    Medication entry = person.record.medicationStart(time, med, chronic);
    entry.codes.add(LOOKUP.get(med));
    // increment number of prescriptions prescribed
    Encounter encounter = (Encounter) person.attributes.get(CVD_ENCOUNTER);
    if (encounter != null) {
      int year = Utilities.getYear(time);
      encounter.provider.incrementPrescriptions(year);
    }
  }

  /**
   * Perform Cardiovascular Disease Encounter.
   * @param person The patient.
   * @param time The time of the encounter.
   */
  public static void performEncounter(Person person, long time, Encounter encounter) {
    person.attributes.put(CVD_ENCOUNTER, encounter);

    // step 1 - diagnosis
    for (String diagnosis : new String[] { "coronary_heart_disease" }) {
      if ((Boolean) person.attributes.getOrDefault(diagnosis, false)
          && !person.record.present.containsKey(diagnosis)) {
        Code code = LOOKUP.get(diagnosis);
        Entry conditionEntry = person.record.conditionStart(time, code.display);
        conditionEntry.codes.add(code);
      }
    }

    // step 2 - treat
    chdTreatment(person, time);
    atrialFibrillationTreatment(person, time);
  }

  private static void beginOrContinueEmergency(Person person, long time, Code code) {
    if (!person.attributes.containsKey(CVD_ENCOUNTER)) {
      Encounter encounter = EncounterModule.createEncounter(person, time, EncounterType.EMERGENCY,
          ClinicianSpecialty.GENERAL_PRACTICE, code);
      person.attributes.put(CVD_ENCOUNTER, encounter);
    }
  }

  private static void endEmergency(Person person, long time) {
    if (person.attributes.containsKey(CVD_ENCOUNTER)) {
      Encounter encounter = (Encounter) person.attributes.get(CVD_ENCOUNTER);
      EncounterType type = EncounterType.fromString(encounter.type);
      if (type == EncounterType.EMERGENCY) {
        person.record.encounterEnd(time, type);
      }
      person.attributes.remove(CVD_ENCOUNTER);
    }
  }

  /**
   * Perform an emergency cardiovascular disease encounter.
   * @param person The patient.
   * @param time The time of the emergency.
   * @param diagnosis The diagnosis to be made.
   */
  public static void performEmergency(Person person, long time, String diagnosis) {

    Encounter encounter = (Encounter) person.attributes.get(CVD_ENCOUNTER);
    int year = Utilities.getYear(time);

    Entry condition = person.record.conditionStart(time, diagnosis);
    condition.codes.add(LOOKUP.get(diagnosis));

    for (String med : filter_meds_by_year(EMERGENCY_MEDS.get(diagnosis), time)) {
      prescribeMedication(med, person, time, false);
      person.record.medicationEnd(time + TimeUnit.MINUTES.toMillis(15), med,
          LOOKUP.get("stop_drug"));
    }

    // In these type of emergencies, everyone gets an echocardiogram
    Procedure procedure = person.record.procedure(time, "echocardiogram");
    procedure.name = "Echocardiogram";
    procedure.codes.add(LOOKUP.get("echocardiogram"));
    procedure.reasons.add(LOOKUP.get(diagnosis));

    for (String proc : EMERGENCY_PROCEDURES.get(diagnosis)) {
      procedure = person.record.procedure(time, proc);
      procedure.name = "Cardiovascular Disease Emergency";
      procedure.codes.add(LOOKUP.get(proc));
      procedure.reasons.add(LOOKUP.get(diagnosis));

      if (proc.equals("implant_cardioverter_defib")) {
        if (!person.record.present.containsKey("defibrillator")) {
          Entry device = person.record.deviceImplant(time, "defibrillator");
          device.codes.add(LOOKUP.get("defibrillator"));
        }
      } else if (proc.equals("percutaneous_coronary_intervention")) {
        if (!person.record.present.containsKey("stent")) {
          Entry device = person.record.deviceImplant(time, "stent");
          device.codes.add(LOOKUP.get("stent"));
        }
      }
      // increment number of procedures performed by respective hospital
      encounter.provider.incrementProcedures(year);
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

  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String,Inventory> attributes) {
    String m = CardiovascularDiseaseModule.class.getSimpleName();
    // Read
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "M");
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "F");
    Attributes.inventory(attributes, m, Person.SMOKER, true, false, "true");
    Attributes.inventory(attributes, m, Person.SMOKER, true, false, "false");
    Attributes.inventory(attributes, m, "atrial_fibrillation", true, false, null);
    Attributes.inventory(attributes, m, "atrial_fibrillation_risk", true, false, null);
    Attributes.inventory(attributes, m, "cardio_risk", true, false, "-1.0");
    Attributes.inventory(attributes, m, "coronary_heart_disease", true, false, "false");
    Attributes.inventory(attributes, m, "cardiovascular_procedures", true, false, null);
    Attributes.inventory(attributes, m, "cardiovascular_disease_med_changes", true, false, null);
    Attributes.inventory(attributes, m, "diabetes", true, false, "false");
    Attributes.inventory(attributes, m, "framingham_cvd", false, true, "0.25");
    Attributes.inventory(attributes, m, "left_ventricular_hypertrophy", true, false, "false");
    Attributes.inventory(attributes, m, "stroke_risk", true, false, null);
    // Write
    Attributes.inventory(attributes, m, "atrial_fibrillation_risk", false, true, "Numeric");
    Attributes.inventory(attributes, m, "cardio_risk", false, true, "1.0");
    Attributes.inventory(attributes, m,
        "cardiovascular_procedures", false, true, "Map<String, List<String>>");
    Attributes.inventory(attributes, m,
        "cardiovascular_disease_med_changes", false, true, "Set<String>");
    Attributes.inventory(attributes, m, "coronary_heart_disease", false, true, "true");
    Attributes.inventory(attributes, m, "stroke_risk", false, true, "0.0");
    Attributes.inventory(attributes, m, "stroke_risk", false, true, "0.5");
    Attributes.inventory(attributes, m, "stroke_risk", false, true, "1.0");
    Attributes.inventory(attributes, m, "stroke_points", false, true, "Numeric");
    Attributes.inventory(attributes, m, "stroke_history", false, true, "true");
  }
}
