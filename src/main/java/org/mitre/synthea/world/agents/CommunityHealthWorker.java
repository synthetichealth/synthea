package org.mitre.synthea.world.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.Location;

public class CommunityHealthWorker extends Provider {

  public static final String LUNG_CANCER_SCREENING = "Lung cancer screening";
  public static final String TOBACCO_SCREENING = "Tobacco screening";
  public static final String ALCOHOL_SCREENING = "Alcohol screening";
  public static final String OBESITY_SCREENING = "Obesity screening";
  public static final String BLOOD_PRESSURE_SCREENING = "Blood pressure screening";
  public static final String DIABETES_SCREENING = "Diabetes screening";
  public static final String COLORECTAL_CANCER_SCREENING = "Colorectal cancer screening";
  public static final String PREECLAMPSIA_SCREENING = "Preeclampsia screening";
  public static final String OSTEOPOROSIS_SCREENING = "Osteoporosis screening";

  public static final String ASPIRIN_MEDICATION = "Aspirin Medication";
  public static final String PREECLAMPSIA_ASPIRIN = "Preeclampsia aspirin";
  public static final String EXERCISE_PT_INJURY_SCREENING = 
      "Fall prevention in older adults: Exercise or physical therapy";
  public static final String VITAMIN_D_INJURY_SCREENING = 
      "Fall prevention in older adults: Vitamin D";
  public static final String DIET_PHYSICAL_ACTIVITY = "Diet and physical activity counseling";
  public static final String STATIN_MEDICATION = "Statin preventive medication";

  public static final String CITY = "city";
  public static final String DEPLOYMENT = "deployment";
  public static final String DEPLOYMENT_COMMUNITY = "community";
  public static final String DEPLOYMENT_EMERGENCY = "emergency";
  public static final String DEPLOYMENT_POSTDISCHARGE = "postdischarge";

  public static int cost = Integer.parseInt(Config.get("generate.chw.cost"));
  public static int budget = Integer.parseInt(Config.get("generate.chw.budget"));
  public static double community = Double.parseDouble(Config.get("generate.chw.community", "0.50"));
  public static double emergency = Double.parseDouble(Config.get("generate.chw.emergency", "0.25"));
  public static double postdischarge = Double
      .parseDouble(Config.get("generate.chw.postdischarge", "0.25"));

  public static int yearIntroduced = Integer.parseInt(Config.get("generate.chw.year_introduced"));

  public static Map<String, List<CommunityHealthWorker>> workers;


  public static void initalize(Location location, Random random) {
    workers = generateWorkers(location, random);
  }
  
  private CommunityHealthWorker(String deploymentType, Location location, Random random) {
    // don't allow anyone else to instantiate this

    attributes.put(CommunityHealthWorker.ALCOHOL_SCREENING,
        Boolean.parseBoolean(Config.get("chw.alcohol_screening")));
    attributes.put(CommunityHealthWorker.ASPIRIN_MEDICATION,
        Boolean.parseBoolean(Config.get("chw.aspirin_medication")));
    attributes.put(CommunityHealthWorker.BLOOD_PRESSURE_SCREENING,
        Boolean.parseBoolean(Config.get("chw.blood_pressure_screening")));
    attributes.put(CommunityHealthWorker.COLORECTAL_CANCER_SCREENING,
        Boolean.parseBoolean(Config.get("chw.colorectal_cancer_screening")));
    attributes.put(CommunityHealthWorker.DIABETES_SCREENING,
        Boolean.parseBoolean(Config.get("chw.diabetes_screening")));
    attributes.put(CommunityHealthWorker.DIET_PHYSICAL_ACTIVITY,
        Boolean.parseBoolean(Config.get("chw.diet_physical_activity")));
    attributes.put(CommunityHealthWorker.EXERCISE_PT_INJURY_SCREENING,
        Boolean.parseBoolean(Config.get("chw.exercise_pt_injury_screening")));
    attributes.put(CommunityHealthWorker.LUNG_CANCER_SCREENING,
        Boolean.parseBoolean(Config.get("chw.lung_cancer_screening")));
    attributes.put(CommunityHealthWorker.OBESITY_SCREENING,
        Boolean.parseBoolean(Config.get("chw.obesity_screening")));
    attributes.put(CommunityHealthWorker.OSTEOPOROSIS_SCREENING,
        Boolean.parseBoolean(Config.get("chw.osteoporosis_screening")));
    attributes.put(CommunityHealthWorker.PREECLAMPSIA_ASPIRIN,
        Boolean.parseBoolean(Config.get("chw.preeclampsia_aspirin")));
    attributes.put(CommunityHealthWorker.PREECLAMPSIA_SCREENING,
        Boolean.parseBoolean(Config.get("chw.preeclampsia_screening")));
    attributes.put(CommunityHealthWorker.STATIN_MEDICATION,
        Boolean.parseBoolean(Config.get("chw.statin_medication")));
    attributes.put(CommunityHealthWorker.TOBACCO_SCREENING,
        Boolean.parseBoolean(Config.get("chw.tobacco_screening")));
    attributes.put(CommunityHealthWorker.VITAMIN_D_INJURY_SCREENING,
        Boolean.parseBoolean(Config.get("chw.vitamin_d_injury_screening")));

    String city = location.randomCityName(random);
    attributes.put(CITY, city);
    
    attributes.put(DEPLOYMENT, deploymentType);

    // resourceID so that it's the same as Provider.
    attributes.put("resourceID", UUID.randomUUID().toString());

    attributes.put("name",
        "CHW providing " + deploymentType + " services in " + attributes.get(CITY));
  }

  private static Map<String, List<CommunityHealthWorker>> generateWorkers(Location location, Random random) {
    Map<String, List<CommunityHealthWorker>> workers = 
        new HashMap<String, List<CommunityHealthWorker>>();
    int numWorkers = budget / cost;
    int numWorkersGenerated = 0;
    CommunityHealthWorker worker;
    for (int i = 0; i < Math.round(numWorkers * community); i++) {
      worker = new CommunityHealthWorker(DEPLOYMENT_COMMUNITY, location, random);
      String city = (String) worker.attributes.get(CITY);
      if (!workers.containsKey(city)) {
        workers.put(city, new ArrayList<CommunityHealthWorker>());
      }
      workers.get(city).add(worker);
      numWorkersGenerated++;
    }
    for (int i = 0; i < Math.round(numWorkers * emergency); i++) {
      worker = new CommunityHealthWorker(DEPLOYMENT_EMERGENCY, location, random);
      String city = (String) worker.attributes.get(CITY);
      if (!workers.containsKey(city)) {
        workers.put(city, new ArrayList<CommunityHealthWorker>());
      }
      workers.get(city).add(worker);
      numWorkersGenerated++;
    }
    for (int i = numWorkersGenerated; i < numWorkers; i++) {
      worker = new CommunityHealthWorker(DEPLOYMENT_POSTDISCHARGE, location, random);
      String city = (String) worker.attributes.get(CITY);
      if (!workers.containsKey(city)) {
        workers.put(city, new ArrayList<CommunityHealthWorker>());
      }
      workers.get(city).add(worker);
    }

    return workers;
  }

  public static CommunityHealthWorker findNearbyCHW(Person person, long time,
      String deploymentType, Location location) {
    int year = Utilities.getYear(time);

    if (year < yearIntroduced) {
      // CHWs not introduced to the system yet
      return null;
    }

    CommunityHealthWorker worker = null;
    String city = (String) person.attributes.get(Person.CITY);

    double probability = 0.0;
    switch (deploymentType) {
      case DEPLOYMENT_COMMUNITY:
        if (workers.containsKey(city)) {
          probability = (double) (workers.get(city).size()) / (double) location.getPopulation(city);
        }
        break;
      case DEPLOYMENT_EMERGENCY:
        probability = 0.9;
        break;
      case DEPLOYMENT_POSTDISCHARGE:
        probability = 0.9;
        break;
      default:
        // nothing
    }
    if (person.rand() < probability && workers.containsKey(city)) {
      List<CommunityHealthWorker> candidates = workers.get(city).stream()
          .filter(p -> p.attributes.get(DEPLOYMENT).equals(deploymentType))
          .collect(Collectors.toList());
      if (!candidates.isEmpty()) {
        worker = candidates.get((int) person.rand(0, candidates.size() - 1));
      }
    }
    return worker;
  }

  /**
   * Check whether this CHW offers the given service.
   * 
   * @param service
   *          Service name
   * 
   * @return true if the service is offered by this CHW
   */
  public boolean offers(String service) {
    return (boolean) this.attributes.getOrDefault(service, false);
  }

  public void performEncounter(Person person, long time, String deploymentType) {
    // encounter class doesn't fit into the FHIR-prescribed set
    // so we use our own "community" encounter class
    Encounter enc = person.record.encounterStart(time, "community");
    enc.chw = this;
    // TODO - different codes based on different services offered?
    enc.codes.add(new Code("SNOMED-CT", "389067005", "Community health procedure"));

    this.incrementEncounters(deploymentType, Utilities.getYear(time));

    int chwInterventions = (int) person.attributes.getOrDefault(Person.CHW_INTERVENTION, 0);
    chwInterventions++;
    person.attributes.put(Person.CHW_INTERVENTION, chwInterventions);

    tobaccoScreening(person, time);
    alcoholScreening(person, time);
    lungCancerScreening(person, time);
    bloodPressureScreening(person, time);
    dietPhysicalActivityCounseling(person, time);
    obesityScreening(person, time);
    aspirinMedication(person, time);
    statinMedication(person, time);

    fallsPreventionExercise(person, time);
    fallsPreventionVitaminD(person, time);
    osteoporosisScreening(person, time);

    double adherenceChwDelta = Double
        .parseDouble(Config.get("lifecycle.aherence.chw_delta", "0.3"));
    double probability = (double) person.attributes.get(LifecycleModule.ADHERENCE_PROBABILITY);
    probability += (adherenceChwDelta);
    person.attributes.put(LifecycleModule.ADHERENCE_PROBABILITY, probability);

    enc.stop = time + TimeUnit.MINUTES.toMillis(35); // encounter lasts 35 minutes on avg
  }

  ///////////////////////////
  // INDIVIDUAL SCREENINGS //
  ///////////////////////////

  // TODO published studies and numbers to support impact of CHW on various conditions

  // The USPSTF recommends that clinicians ask all adults about tobacco use, advise them to stop
  // using tobacco, and provide
  // behavioral interventions and U.S. Food and Drug Administration (FDA)–approved pharmacotherapy
  // for cessation to adults who use tobacco.
  private void tobaccoScreening(Person person, long time) {
    int age = person.ageInYears(time);

    if (this.offers(TOBACCO_SCREENING) && age >= 18) {
      Procedure ct = person.record.procedure(time, "Tobacco usage screening (procedure)");

      ct.codes.add(new Code("SNOMED-CT", "171209009", "Tobacco usage screening (procedure)"));

      double quitSmokingChwDelta = Double
          .parseDouble(Config.get("lifecycle.quit_smoking.chw_delta", "0.3"));
      double smokingDurationFactorPerYear = Double.parseDouble(
          Config.get("lifecycle.quit_smoking.smoking_duration_factor_per_year", "1.0"));
      double probability = (double) person.attributes.get(LifecycleModule.QUIT_SMOKING_PROBABILITY);
      int numberOfYearsSmoking = (int) person.ageInYears(time) - 15;
      probability += (quitSmokingChwDelta
          / (smokingDurationFactorPerYear * numberOfYearsSmoking));
      person.attributes.put(LifecycleModule.QUIT_SMOKING_PROBABILITY, probability);

      if (person.attributes.containsKey("cardio_risk")) {
        double cardioRisk = (double) person.attributes.get("cardio_risk");
        cardioRisk = cardioRisk / (4 + quitSmokingChwDelta);
        person.attributes.put("cardio_risk",
            Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")) {
        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + quitSmokingChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")) {
        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + quitSmokingChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")) {
        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }
    }
  }

  // The USPSTF recommends that clinicians screen adults age 18 years or older for alcohol misuse
  // and provide persons engaged
  // in risky or hazardous drinking with brief behavioral counseling interventions to reduce alcohol
  // misuse.
  private void alcoholScreening(Person person, long time) {
    int age = person.ageInYears(time);

    if (this.offers(ALCOHOL_SCREENING) && age >= 18) {
      Procedure ct = person.record.procedure(time, "Screening for alcohol abuse (procedure)");

      ct.codes.add(new Code("SNOMED-CT", "713107002", "Screening for alcohol abuse (procedure)"));

      double quitAlcoholismChwDelta = Double
          .parseDouble(Config.get("lifecycle.quit_alcoholism.chw_delta", "0.3"));
      double alcoholismDurationFactorPerYear = Double.parseDouble(
          Config.get("lifecycle.quit_alcoholism.alcoholism_duration_factor_per_year", "1.0"));

      if (person.attributes.containsKey("cardio_risk")) {
        double cardioRisk = (double) person.attributes.get("cardio_risk");
        cardioRisk = cardioRisk / (4 + quitAlcoholismChwDelta);
        person.attributes.put("cardio_risk",
            Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")) {
        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + quitAlcoholismChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")) {
        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + quitAlcoholismChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")) {
        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }

      if ((boolean) person.attributes.getOrDefault(Person.ALCOHOLIC, false)) {
        Procedure ct2 = person.record.procedure(time, "Alcoholism counseling (procedure)");

        ct2.codes.add(new Code("SNOMED-CT", "24165007", "Alcoholism counseling (procedure)"));

        double probability = (double) person.attributes
            .get(LifecycleModule.QUIT_ALCOHOLISM_PROBABILITY);
        int numberOfYearsAlcoholic = (int) person.ageInYears(time) - 25;
        probability += (quitAlcoholismChwDelta
            / (alcoholismDurationFactorPerYear * numberOfYearsAlcoholic));
        person.attributes.put(LifecycleModule.QUIT_ALCOHOLISM_PROBABILITY, probability);

      }
    }
  }

  // The USPSTF recommends annual screening for lung cancer with low-dose computed tomography
  // in adults ages 55 to 80 years who have a 30 pack-year smoking history and currently smoke
  // or have quit within the past 15 years.
  // Screening should be discontinued once a person has not smoked for 15 years or
  // develops a health problem that substantially limits life expectancy or the ability or
  // willingness to have curative lung surgery.
  private void lungCancerScreening(Person person, long time) {
    int age = person.ageInYears(time);
    boolean isSmoker = (boolean) person.attributes.getOrDefault(Person.SMOKER, false);
    int quitSmokingAge = (int) person.attributes.getOrDefault(LifecycleModule.QUIT_SMOKING_AGE, 0);
    int yearsSinceQuitting = age - quitSmokingAge;

    // TODO: 30-year pack history
    if (this.offers(LUNG_CANCER_SCREENING) && age >= 55 && age <= 80
        && (isSmoker || yearsSinceQuitting <= 15)) {
      Procedure ct = person.record.procedure(time,
          "Low dose computed tomography of thorax (procedure)");

      ct.codes.add(
          new Code("SNOMED-CT", "16334891000119106", "Low dose computed tomography of thorax"));

      if ((boolean) person.attributes.getOrDefault("lung_cancer", false)) {
        // screening caught lung cancer, send them to treatment
        person.attributes.put("probability_of_lung_cancer_treatment", 1.0);
      }
    }
  }

  // The USPSTF recommends screening for high blood pressure in adults aged 18 years or older.
  // The USPSTF recommends obtaining measurements outside of the clinical setting for diagnostic
  // confirmation before starting treatment.
  private void bloodPressureScreening(Person person, long time) {
    if (this.offers(BLOOD_PRESSURE_SCREENING)) {
      // TODO metabolic syndrome module

      Procedure ct = person.record.procedure(time,
          "Blood pressure screening - first call (procedure)");

      ct.codes.add(
          new Code("SNOMED-CT", "185665008", "Blood pressure screening - first call (procedure)"));

      double bloodPressureChwDelta = Double
          .parseDouble(Config.get("lifecycle.blood_pressure.chw_delta", "0.1"));

      if (person.attributes.containsKey("cardio_risk")) {
        double cardioRisk = (double) person.attributes.get("cardio_risk");
        cardioRisk = cardioRisk / (4 + bloodPressureChwDelta);
        person.attributes.put("cardio_risk",
            Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")) {
        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + bloodPressureChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")) {
        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + bloodPressureChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")) {
        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }
    }
  }

  // The USPSTF recommends offering or referring adults who are overweight or obese and have
  // additional cardiovascular disease (CVD) risk factors to intensive behavioral counseling
  // interventions to promote a healthful diet and physical activity for CVD prevention.
  private void dietPhysicalActivityCounseling(Person person, long time) {
    int age = person.ageInYears(time);

    if (this.offers(DIET_PHYSICAL_ACTIVITY) && age >= 18
        && person.getVitalSign(VitalSign.BMI) >= 25.0) {
      // TODO metabolic syndrome module, colorectal cancer module

      // only for adults who have CVD risk factors
      // the exact threshold for CVD risk factors can be determined later

      double dietPhysicalActivityChwDelta = Double
          .parseDouble(Config.get("lifecycle.diet_physical_activity.chw_delta", "0.1"));

      if (person.attributes.containsKey("cardio_risk")) {
        if (person.attributes.get(Person.GENDER).equals("M")
            && (double) person.attributes.get("cardio_risk") > .0000002) {
          Procedure ct = person.record.procedure(time,
              "Referral to physical activity program (procedure)");
          ct.codes.add(new Code("SNOMED-CT", "390893007",
              "Referral to physical activity program (procedure)"));

          Procedure ct2 = person.record.procedure(time, "Healthy eating education (procedure)");
          ct2.codes.add(new Code("SNOMED-CT", "699849008", "Healthy eating education (procedure)"));

          double cardioRisk = (double) person.attributes.get("cardio_risk");
          cardioRisk = cardioRisk / (4 + dietPhysicalActivityChwDelta);
          person.attributes.put("cardio_risk",
              Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));

        }

        if (person.attributes.get(Person.GENDER).equals("F")
            && (double) person.attributes.get("cardio_risk") > .0000004) {
          Procedure ct = person.record.procedure(time,
              "Referral to physical activity program (procedure)");
          ct.codes.add(new Code("SNOMED-CT", "390893007",
              "Referral to physical activity program (procedure)"));

          Procedure ct2 = person.record.procedure(time, "Healthy eating education (procedure)");
          ct2.codes.add(new Code("SNOMED-CT", "699849008", "Healthy eating education (procedure)"));

          double cardioRisk = (double) person.attributes.get("cardio_risk");
          cardioRisk = cardioRisk / (4 + dietPhysicalActivityChwDelta);
          person.attributes.put("cardio_risk",
              Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));

        }
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")
          && (double) person.attributes.get("atrial_fibrillation_risk") > .00000003) {
        Procedure ct = person.record.procedure(time,
            "Referral to physical activity program (procedure)");
        ct.codes.add(new Code("SNOMED-CT", "390893007",
            "Referral to physical activity program (procedure)"));

        Procedure ct2 = person.record.procedure(time, "Healthy eating education (procedure)");
        ct2.codes.add(new Code("SNOMED-CT", "699849008", "Healthy eating education (procedure)"));

        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + dietPhysicalActivityChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")
          && (double) person.attributes.get("stroke_risk") > .00000003) {
        Procedure ct = person.record.procedure(time,
            "Referral to physical activity program (procedure)");
        ct.codes.add(new Code("SNOMED-CT", "390893007",
            "Referral to physical activity program (procedure)"));

        Procedure ct2 = person.record.procedure(time, "Healthy eating education (procedure)");
        ct2.codes.add(new Code("SNOMED-CT", "699849008", "Healthy eating education (procedure)"));

        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + dietPhysicalActivityChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")
          && (int) person.attributes.get("stroke_points") > 3) {
        Procedure ct = person.record.procedure(time,
            "Referral to physical activity program (procedure)");
        ct.codes.add(new Code("SNOMED-CT", "390893007",
            "Referral to physical activity program (procedure)"));

        Procedure ct2 = person.record.procedure(time, "Healthy eating education (procedure)");
        ct2.codes.add(new Code("SNOMED-CT", "699849008", "Healthy eating education (procedure)"));

        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }
    }
  }

  // The USPSTF recommends screening all adults for obesity. Clinicians should offer or refer
  // patients with a body mass index of 30 kg/m2 or higher to intensive, multicomponent behavioral
  // interventions.
  private void obesityScreening(Person person, long time) {
    int age = person.ageInYears(time);

    if (this.offers(OBESITY_SCREENING) && age >= 18) {
      // TODO metabolic syndrome module, diabetes

      Procedure ct = person.record.procedure(time, "Obesity screening (procedure)");

      ct.codes.add(new Code("SNOMED-CT", "268551005", "Obesity screening (procedure)"));

      double obesityChwDelta = Double
          .parseDouble(Config.get("lifecycle.obesity_screening.chw_delta", "0.1"));

      if (person.attributes.containsKey("cardio_risk")) {
        double cardioRisk = (double) person.attributes.get("cardio_risk");
        cardioRisk = cardioRisk / (4 + obesityChwDelta);
        person.attributes.put("cardio_risk",
            Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")) {
        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + obesityChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")) {
        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + obesityChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")) {
        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }

      if (person.getVitalSign(VitalSign.BMI) >= 30.0) {
        // "Clinicians should offer or refer patients with a body mass index of 30 kg/m2 or higher
        // to intensive, multicomponent behavioral interventions."(USPSTF)
        Procedure ct2 = person.record.procedure(time, "Obesity monitoring invitation (procedure)");

        ct2.codes
            .add(new Code("SNOMED-CT", "310428009", "Obesity monitoring invitation (procedure)"));
      }
    }
  }

  // The USPSTF recommends initiating low-dose aspirin use for the primary prevention of
  // cardiovascular disease
  // and colorectal cancer in adults aged 50 to 59 years who have a 10% or greater 10-year
  // cardiovascular risk,
  // are not at increased risk for bleeding, have a life expectancy of at least 10 years, and are
  // willing to take low-dose aspirin daily for at least 10 years.
  private void aspirinMedication(Person person, long time) {
    int age = person.ageInYears(time);

    // From QualityofLifeModule:
    // life expectancy equation derived from IHME GBD 2015 Reference Life Table
    // 6E-5x^3 - 0.0054x^2 - 0.8502x + 86.16
    // R^2 = 0.99978
    double lifeExpectancy = ((0.00006 * Math.pow(age, 3)) - (0.0054 * Math.pow(age, 2))
        - (0.8502 * age) + 86.16);
    double tenYearStrokeRisk = (double) person.attributes.getOrDefault("cardio_risk", 0.0) * 3650;

    if (this.offers(ASPIRIN_MEDICATION) && age >= 50 && age < 60 && tenYearStrokeRisk >= .1
        && lifeExpectancy >= 10) {

      Procedure ct = person.record.procedure(time, "Administration of aspirin (procedure)");

      ct.codes.add(new Code("SNOMED-CT", "431463004", "Administration of aspirin (procedure)"));

      double aspirinChwDelta = Double
          .parseDouble(Config.get("lifecycle.aspirin_medication.chw_delta", "0.1"));

      if (person.attributes.containsKey("cardio_risk")) {
        double cardioRisk = (double) person.attributes.get("cardio_risk");
        cardioRisk = cardioRisk / (4 + aspirinChwDelta);
        person.attributes.put("cardio_risk",
            Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")) {
        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + aspirinChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")) {
        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + aspirinChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")) {
        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }
    }
  }

  // The USPSTF recommends that adults without a history of cardiovascular disease (CVD) (i.e.,
  // symptomatic coronary artery disease or ischemic stroke)
  // use a low- to moderate-dose statin for the prevention of CVD events and mortality when all of
  // the following criteria are met:
  // 1) they are ages 40 to 75 years; 2) they have 1 or more CVD risk factors (i.e., dyslipidemia,
  // diabetes, hypertension, or smoking);
  // and 3) they have a calculated 10-year risk of a cardiovascular event of 10% or greater.
  // Identification of dyslipidemia and calculation
  // of 10-year CVD event risk requires universal lipids screening in adults ages 40 to 75 years.
  private void statinMedication(Person person, long time) {
    int age = person.ageInYears(time);

    double tenYearStrokeRisk = (double) person.attributes.getOrDefault("cardio_risk", 0.0) * 3650;

    // TODO check for history of CVD
    if (this.offers(STATIN_MEDICATION) && age >= 40 && age <= 75 && tenYearStrokeRisk >= .1) {

      // TODO may need a better snomed code
      Procedure ct = person.record.procedure(time, "Over the counter statin therapy (procedure)");

      ct.codes
          .add(new Code("SNOMED-CT", "414981001", "Over the counter statin therapy (procedure)"));

      double statinChwDelta = Double
          .parseDouble(Config.get("lifecycle.statin_medication.chw_delta", "0.1"));

      if (person.attributes.containsKey("cardio_risk")) {
        double cardioRisk = (double) person.attributes.get("cardio_risk");
        cardioRisk = cardioRisk / (4 + statinChwDelta);
        person.attributes.put("cardio_risk",
            Utilities.convertRiskToTimestep(cardioRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("atrial_fibrillation_risk")) {
        double afRisk = (double) person.attributes.get("atrial_fibrillation_risk");
        afRisk = afRisk / (4 + statinChwDelta);
        person.attributes.put("atrial_fibrillation_risk",
            Utilities.convertRiskToTimestep(afRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_risk")) {
        double strokeRisk = (double) person.attributes.get("stroke_risk");
        strokeRisk = strokeRisk / (4 + statinChwDelta);
        person.attributes.put("stroke_risk",
            Utilities.convertRiskToTimestep(strokeRisk, TimeUnit.DAYS.toMillis(3650)));
      }

      if (person.attributes.containsKey("stroke_points")) {
        int strokePoints = (int) person.attributes.get("stroke_points");
        strokePoints = strokePoints - 2;
        person.attributes.put("stroke_points", Math.max(0, strokePoints));
      }
    }
  }

  // The USPSTF recommends exercise or physical therapy to prevent falls
  // in community-dwelling adults age 65 years and older who are at increased risk for falls.
  private void fallsPreventionExercise(Person person, long time) {
    int age = person.ageInYears(time);
    if (this.offers(EXERCISE_PT_INJURY_SCREENING) && age > 65) {
      // TODO - implement fall risk using "Morse Fall Scale" -
      // http://www.networkofcare.org/library/Morse%20Fall%20Scale.pdf
      // technically this refers to patients receiving care in acute situations, ie hospitals
      // 1. History of falling; immediate or within 3 months : No = 0, Yes = 25
      // 2. Secondary diagnosis : No = 0, Yes = 15
      // 3. Ambulatory aid : Bed rest/nurse assist = 0, Crutches/cane/walker = 15, Furniture = 30
      // 4. IV/Heparin Lock : No = 0, Yes = 20
      // 5. Gait/Transferring : Normal/bedrest/immobile = 0, Weak = 10, Impaired = 20
      // 6. Mental status : Oriented to own ability = 0, Forgets limitations = 15

      // Risk Level | MFS Score | Action
      // -----------+-----------+-------
      // No Risk | 0 - 24 | Basic Care
      // Low Risk | 25 - 50 | Standard Fall Prevention Interventions
      // High Risk | 51 - 135 | High Risk Fall Prevention Interventions
      // These #s may be tailored to specific patients or situations.

      int fallRisk = (int) person.rand(0, 60);

      if (fallRisk > 50) {
        CarePlan pt = person.record.careplanStart(time, "Physical therapy");
        pt.codes.add(new Code("SNOMED-CT", "91251008", "Physical therapy"));
      } else if (fallRisk > 40) {
        CarePlan exercise = person.record.careplanStart(time,
            "Physical activity target light exercise");
        exercise.codes
            .add(new Code("SNOMED-CT", "408580007", "Physical activity target light exercise"));
      }
    }
  }

  // The USPSTF recommends vitamin D supplementation to prevent falls
  // in community-dwelling adults age 65 years and older who are at increased risk for falls.
  private void fallsPreventionVitaminD(Person person, long time) {
    // https://www.uspreventiveservicestaskforce.org/Page/Document/RecommendationStatementFinal/falls-prevention-in-older-adults-counseling-and-preventive-medication#consider

    int age = person.ageInYears(time);
    if (this.offers(VITAMIN_D_INJURY_SCREENING) && age > 65) {
      // CHW interaction will decrease probability of injuries by g(x) % (this adds medication for
      // vitamin D)

      // According to the Institute of Medicine, the recommended daily allowance for vitamin D
      // is 600 IU for adults aged 51 to 70 years and 800 IU for adults older than 70 years 7.
      // The AGS recommends 800 IU per day for persons at increased risk for falls.

      if (!person.record.medicationActive("Cholecalciferol 600 UNT")) {
        Medication vitD = person.record.medicationStart(time, "Cholecalciferol 600 UNT");
        vitD.codes.add(new Code("RxNorm", "994830", "Cholecalciferol 600 UNT"));
      }
    }
  }

  // The USPSTF recommends screening for osteoporosis in women age 65 years
  // and older and in younger women whose fracture risk is equal
  // to or greater than that of a 65-year-old white woman
  // who has no additional risk factors.
  private void osteoporosisScreening(Person person, long time) {
    // https://www.uspreventiveservicestaskforce.org/Page/Document/RecommendationStatementFinal/osteoporosis-screening
    int age = person.ageInYears(time);
    String gender = (String) person.attributes.get(Person.GENDER);
    boolean elevatedFractureRisk = (person.rand() < 0.05);
    // TODO - use FRAX instead of just guessing https://en.wikipedia.org/wiki/FRAX
    boolean alreadyDiagnosedOsteoporosis = person.record.conditionActive("Osteoporosis (disorder)");
    if (this.offers(OSTEOPOROSIS_SCREENING) && "F".equals(gender)
        && (age > 65 || elevatedFractureRisk) && !alreadyDiagnosedOsteoporosis) {
      // note that a lot of this already exists in the injuries & osteoporosis modules
      // TODO: ideally we would rework all 3 such that this CHW intervention would trigger an
      // osteoporosis workup later
      // but this is the approach for now

      // bone density test
      Procedure proc = person.record.procedure(time, "Bone density scan (procedure)");
      proc.codes.add(new Code("SNOMED-CT", "312681000", "Bone density scan (procedure)"));

      boolean hasOsteoporosis = (boolean) person.attributes.getOrDefault("osteoporosis", false);

      double boneDensity;
      Observation obs;

      if (hasOsteoporosis) {
        boneDensity = person.rand(-3.8, -2.5);
        obs = person.record.observation(time, "DXA [T-score] Bone density", boneDensity);

        Entry condition = person.record.conditionStart(time, "osteoporosis");
        condition.codes.add(new Code("SNOMED-CT", "64859006", "Osteoporosis (disorder)"));
      } else {
        boneDensity = person.rand(-0.5, 0.5);
        obs = person.record.observation(time, "DXA [T-score] Bone density", boneDensity);
      }

      obs.codes.add(new Code("LOINC", "38265-5", "DXA [T-score] Bone density"));
    }
  }
}
