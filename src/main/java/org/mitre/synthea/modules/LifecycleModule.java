package org.mitre.synthea.modules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.engine.Event;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.CommunityHealthWorker;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.geography.Location;

import com.github.javafaker.Faker;
import com.google.gson.Gson;

public final class LifecycleModule extends Module {
  @SuppressWarnings("rawtypes")
  private static final Map growthChart = loadGrowthChart();
  private static final Faker faker = new Faker();
  private static final String AGE = "AGE";
  private static final String AGE_MONTHS = "AGE_MONTHS";
  public static final String QUIT_SMOKING_PROBABILITY = "quit smoking probability";
  public static final String QUIT_SMOKING_AGE = "quit smoking age";
  public static final String QUIT_ALCOHOLISM_PROBABILITY = "quit alcoholism probability";
  public static final String QUIT_ALCOHOLISM_AGE = "quit alcoholism age";
  public static final String ADHERENCE_PROBABILITY = "adherence probability";

  public LifecycleModule() {
    this.name = "Lifecycle";
  }

  @SuppressWarnings("rawtypes")
  private static Map loadGrowthChart() {
    String filename = "/cdc_growth_charts.json";
    try {
      InputStream stream = LifecycleModule.class.getResourceAsStream(filename);
      String json = new BufferedReader(new InputStreamReader(stream)).lines().parallel()
          .collect(Collectors.joining("\n"));
      Gson g = new Gson();
      return g.fromJson(json, HashMap.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public boolean process(Person person, long time) {
    // run through all of the rules defined
    // ruby "rules" are converted to static functions here
    // since this is intended to only be temporary

    // birth(person, time); intentionally left out - call it only once from Generator
    if (age(person, time)) {
      grow(person, time);
    }
    person.chwEncounter(time, CommunityHealthWorker.DEPLOYMENT_COMMUNITY);
    startSmoking(person, time);
    chanceOfLungCancer(person, time);
    startAlcoholism(person, time);
    quitSmoking(person, time);
    quitAlcoholism(person, time);
    adherence(person, time);
    diabeticVitalSigns(person, time);
    calculateFallRisk(person, time);
    death(person, time);

    // java modules will never "finish"
    return false;
  }

  public static void birth(Person person, long time) {
    Map<String, Object> attributes = person.attributes;

    attributes.put(Person.ID, UUID.randomUUID().toString());
    attributes.put(Person.BIRTHDATE, time);
    person.events.create(time, Event.BIRTH, "Generator.run", true);

    String firstName = faker.name().firstName();
    String lastName = faker.name().lastName();
    attributes.put(Person.FIRST_NAME, firstName);
    attributes.put(Person.LAST_NAME, lastName);
    attributes.put(Person.NAME, firstName + " " + lastName);

    String motherFirstName = faker.name().firstName();
    String motherLastName = faker.name().lastName();
    attributes.put(Person.NAME_MOTHER, motherFirstName + " " + motherLastName);

    double prevalenceOfTwins = Double
        .parseDouble(Config.get("lifecycle.prevalence_of_twins", "0.02"));
    if ((person.rand() < prevalenceOfTwins)) {
      attributes.put(Person.MULTIPLE_BIRTH_STATUS, person.randInt(3) + 1);
    }

    attributes.put(Person.TELECOM, faker.phoneNumber().phoneNumber());

    String ssn = "999-" + ((person.randInt(99 - 10 + 1) + 10)) + "-"
        + ((person.randInt(9999 - 1000 + 1) + 1000));
    attributes.put(Person.IDENTIFIER_SSN, ssn);

    Location.assignPoint(person, (String) attributes.get(Person.CITY));
    boolean hasStreetAddress2 = person.rand() < 0.5;
    attributes.put(Person.ADDRESS, faker.address().streetAddress(hasStreetAddress2));

    attributes.put(Person.BIRTHPLACE, Location.randomCityName(person.random));

    double height_percentile = person.rand();
    double weight_percentile = person.rand();
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, height_percentile);
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, weight_percentile);
    person.setVitalSign(VitalSign.HEIGHT, 51.0); // cm
    person.setVitalSign(VitalSign.WEIGHT, 3.5); // kg

    attributes.put(AGE, 0);
    attributes.put(AGE_MONTHS, 0);

    boolean isRHNeg = person.rand() < 0.15;
    attributes.put("RH_NEG", isRHNeg);

    double aherence_baseline = Double
        .parseDouble(Config.get("lifecycle.adherence.baseline", ".05"));
    person.attributes.put(ADHERENCE_PROBABILITY, aherence_baseline);

    grow(person, time); // set initial height and weight from percentiles
  }

  /**
   * @return whether or not the patient should grow
   */
  private static boolean age(Person person, long time) {
    int prevAge = (int) person.attributes.get(AGE);
    int prevAgeMos = (int) person.attributes.get(AGE_MONTHS);

    int newAge = person.ageInYears(time);
    int newAgeMos = person.ageInMonths(time);
    person.attributes.put(AGE, newAge);
    person.attributes.put(AGE_MONTHS, newAgeMos);

    switch (newAge) {
      case 16:
        // driver's license
        if (person.attributes.get(Person.IDENTIFIER_DRIVERS) == null) {
          String identifierDrivers = "S999" + ((person.randInt(99999 - 10000 + 1) + 10000));
          person.attributes.put(Person.IDENTIFIER_DRIVERS, identifierDrivers);
        }
        break;
      case 18:
        // name prefix
        if (person.attributes.get(Person.NAME_PREFIX) == null) {
          String namePrefix;
          if ("M".equals(person.attributes.get(Person.GENDER))) {
            namePrefix = "Mr.";
          } else {
            namePrefix = "Ms.";
          }
          person.attributes.put(Person.NAME_PREFIX, namePrefix);
        }
        break;
      case 20:
        // passport number
        if (person.attributes.get(Person.IDENTIFIER_PASSPORT) == null) {
          Boolean getsPassport = (person.rand() < 0.5);
          if (getsPassport) {
            String identifierPassport = "X" + (person.randInt(99999999 - 10000000 + 1) + "X");
            person.attributes.put(Person.IDENTIFIER_PASSPORT, identifierPassport);
          }
        }
        break;
      case 27:
        // get married
        if (person.attributes.get(Person.MARITAL_STATUS) == null) {
          Boolean getsMarried = (person.rand() < 0.8);
          if (getsMarried) {
            person.attributes.put(Person.MARITAL_STATUS, "M");
            if ("F".equals(person.attributes.get(Person.GENDER))) {
              person.attributes.put(Person.NAME_PREFIX, "Mrs.");
              person.attributes.put(Person.MAIDEN_NAME, person.attributes.get(Person.LAST_NAME));
              String firstName = ((String) person.attributes.get(Person.FIRST_NAME));
              String newLastName = faker.name().lastName();
              person.attributes.put(Person.LAST_NAME, newLastName);
              person.attributes.put(Person.NAME, firstName + " " + newLastName);
            }
          } else {
            person.attributes.put(Person.MARITAL_STATUS, "S");
          }
        }
        break;
      case 30:
        // "overeducated" -> suffix
        if ((person.attributes.get(Person.NAME_SUFFIX) == null)
            && ((double) person.attributes.get(Person.EDUCATION_LEVEL) >= 0.95)) {
          List<String> suffixList = Arrays.asList("PhD", "JD", "MD");
          person.attributes.put(Person.NAME_SUFFIX,
              suffixList.get(person.randInt(suffixList.size())));
        }
        break;
    }

    boolean shouldGrow;
    if (newAge >= 20) {
      // adults 20 and over grow once per year
      shouldGrow = (newAge > prevAge);
    } else {
      // people under 20 grow once per month
      shouldGrow = (newAgeMos > prevAgeMos);
    }
    return shouldGrow;
  }

  private static void grow(Person person, long time) {
    int age = person.ageInYears(time);
    int adult_max_weight_age = Integer.parseInt(Config.get("lifecycle.adult_max_weight_age", "49"));
    int geriatric_weight_loss_age = Integer
        .parseInt(Config.get("lifecycle.geriatric_weight_loss_age", "60"));

    double height = person.getVitalSign(VitalSign.HEIGHT);
    double weight = person.getVitalSign(VitalSign.WEIGHT);

    if (age < 20) {
      // follow growth charts
      String gender = (String) person.attributes.get(Person.GENDER);
      int ageInMonths = person.ageInMonths(time);
      height = lookupGrowthChart("height", gender, ageInMonths,
          person.getVitalSign(VitalSign.HEIGHT_PERCENTILE));
      weight = lookupGrowthChart("weight", gender, ageInMonths,
          person.getVitalSign(VitalSign.WEIGHT_PERCENTILE));
    } else if (age <= adult_max_weight_age) {
      // getting older and fatter
      double min = Double.parseDouble(Config.get("lifecycle.adult_weight_gain.min", "1.0"));
      double max = Double.parseDouble(Config.get("lifecycle.adult_weight_gain.max", "2.0"));
      double adult_weight_gain = person.rand(min, max);
      weight += adult_weight_gain;
    } else if (age >= geriatric_weight_loss_age) {
      // getting older and wasting away
      double min = Double.parseDouble(Config.get("lifecycle.geriatric_weight_loss.min", "1.0"));
      double max = Double.parseDouble(Config.get("lifecycle.geriatric_weight_loss.max", "2.0"));
      double geriatric_weight_loss = person.rand(min, max);
      weight -= geriatric_weight_loss;
    }

    person.setVitalSign(VitalSign.HEIGHT, height);
    person.setVitalSign(VitalSign.WEIGHT, weight);
    person.setVitalSign(VitalSign.BMI, bmi(height, weight));
  }

  @SuppressWarnings("rawtypes")
  public static double lookupGrowthChart(String heightOrWeight, String gender, int ageInMonths,
      double percentile) {
    String[] percentile_buckets = { "3", "5", "10", "25", "50", "75", "90", "95", "97" };

    Map chart = (Map) growthChart.get(heightOrWeight);
    Map byGender = (Map) chart.get(gender);
    Map byAge = (Map) byGender.get(Integer.toString(ageInMonths));
    int bucket = 0;
    for (int i = 0; i < percentile_buckets.length; i++) {
      if ((Double.parseDouble(percentile_buckets[i]) / 100.0) <= percentile) {
        bucket = i;
      } else {
        break;
      }
    }
    return Double.parseDouble((String) byAge.get(percentile_buckets[bucket]));
  }

  private static double bmi(double heightCM, double weightKG) {
    return (weightKG / ((heightCM / 100.0) * (heightCM / 100.0)));
  }

  // LIPID PANEL
  // https://www.nlm.nih.gov/medlineplus/magazine/issues/summer12/articles/summer12pg6-7.html
  private static final int[] CHOLESTEROL = new int[] { 160, 200, 239, 259, 279, 300 }; // # mg/dL
  private static final int[] TRIGLYCERIDES = new int[] { 100, 150, 199, 499, 550, 600 }; // mg/dL
  private static final int[] HDL = new int[] { 80, 59, 40, 20, 10, 0 }; // mg/dL

  private static void diabeticVitalSigns(Person person, long time) {
    // TODO - most of the rest of the vital signs
    boolean hypertension = (Boolean) person.attributes.getOrDefault("hypertension", false);
    /*
     * blood_pressure: 
     * normal:
     * systolic: [100,139] # mmHg
     * diastolic: [70,89] # mmHg
     * hypertensive:
     * systolic: [140,200] # mmHg
     * diastolic: [90,120] # mmHg
     */
    if (hypertension) {
      person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, person.rand(140, 200));
      person.setVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, person.rand(90, 120));
    } else {
      person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, person.rand(100, 139));
      person.setVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, person.rand(70, 89));
    }

    int index = 0;
    if (person.attributes.containsKey("diabetes_severity")) {
      index = (Integer) person.attributes.getOrDefault("diabetes_severity", 1);
    }

    double total_cholesterol = person.rand(CHOLESTEROL[index], CHOLESTEROL[index + 1]);
    double triglycerides = person.rand(TRIGLYCERIDES[index], TRIGLYCERIDES[index + 1]);
    double hdl = person.rand(HDL[index], HDL[index + 1]);
    double ldl = total_cholesterol - hdl - (0.2 * triglycerides);

    person.setVitalSign(VitalSign.TOTAL_CHOLESTEROL, total_cholesterol);
    person.setVitalSign(VitalSign.TRIGLYCERIDES, triglycerides);
    person.setVitalSign(VitalSign.HDL, hdl);
    person.setVitalSign(VitalSign.LDL, ldl);
  }

  private static final Code NATURAL_CAUSES = new Code("SNOMED-CT", "9855000",
      "Natural death with unknown cause");

  private static void death(Person person, long time) {
    double roll = person.rand();
    double likelihoodOfDeath = likelihoodOfDeath(person.ageInYears(time));
    if (roll < likelihoodOfDeath) {
      person.recordDeath(time, NATURAL_CAUSES, "death");
    }
  }

  private static double likelihoodOfDeath(int age) {
    double yearlyRisk;

    if (age < 1) {
      yearlyRisk = 508.1 / 100_000.0;
    } else if (age >= 1 && age <= 4) {
      yearlyRisk = 15.6 / 100_000.0;
    } else if (age >= 5 && age <= 14) {
      yearlyRisk = 10.6 / 100_000.0;
    } else if (age >= 15 && age <= 24) {
      yearlyRisk = 56.4 / 100_000.0;
    } else if (age >= 25 && age <= 34) {
      yearlyRisk = 74.7 / 100_000.0;
    } else if (age >= 35 && age <= 44) {
      yearlyRisk = 145.7 / 100_000.0;
    } else if (age >= 45 && age <= 54) {
      yearlyRisk = 326.5 / 100_000.0;
    } else if (age >= 55 && age <= 64) {
      yearlyRisk = 737.8 / 100_000.0;
    } else if (age >= 65 && age <= 74) {
      yearlyRisk = 1817.0 / 100_000.0;
    } else if (age >= 75 && age <= 84) {
      yearlyRisk = 4877.3 / 100_000.0;
    } else if (age >= 85 && age <= 94) {
      yearlyRisk = 13_499.4 / 100_000.0;
    } else {
      yearlyRisk = 50_000.0 / 100_000.0;
    }

    double oneYearInMs = TimeUnit.DAYS.toMillis(365);
    double adjustedRisk = Utilities.convertRiskToTimestep(yearlyRisk, oneYearInMs);

    return adjustedRisk;
  }

  private static void startSmoking(Person person, long time) {
    // 9/10 smokers start before age 18. We will use 16.
    // http://www.cdc.gov/tobacco/data_statistics/fact_sheets/youth_data/tobacco_use/
    if (person.attributes.get(Person.SMOKER) == null && person.ageInYears(time) == 16) {
      int year = Utilities.getYear(time);
      Boolean smoker = person.rand() < likelihoodOfBeingASmoker(year);
      person.attributes.put(Person.SMOKER, smoker);
      double quit_smoking_baseline = Double
          .parseDouble(Config.get("lifecycle.quit_smoking.baseline", "0.01"));
      person.attributes.put(LifecycleModule.QUIT_SMOKING_PROBABILITY, quit_smoking_baseline);
    }
  }

  private static double likelihoodOfBeingASmoker(int year) {
    // 16.1% of MA are smokers in 2016.
    // http://www.cdc.gov/tobacco/data_statistics/state_data/state_highlights/2010/states/massachusetts/
    // but the rate is decreasing over time
    // http://www.cdc.gov/tobacco/data_statistics/tables/trends/cig_smoking/
    // selected #s:
    // 1965 - 42.4%
    // 1975 - 37.1%
    // 1985 - 30.1%
    // 1995 - 24.7%
    // 2005 - 20.9%
    // 2015 - 16.1%
    // assume that it was never significantly higher than 42% pre-1960s, but will continue to drop
    // slowly after 2016
    // it's decreasing about .5% per year
    if (year < 1965) {
      return 0.424;
    }

    return ((year * -0.4865) + 996.41) / 100.0;
  }

  private static void chanceOfLungCancer(Person person, long time) {
    // TODO - make this calculation more elaborate, based on duration smoking, timestep-dependent

    /*
     * Overall, the chance that a man will develop lung cancer in his lifetime is about 1 in 14;
     * for a woman, the risk is about 1 in 17.
     * http://www.cancer.org/cancer/lungcancer-non-smallcell/detailedguide/non-small-cell-lung-
     * cancer-key-statistics
     * 
     * Men who smoke are 23 times more likely to develop lung cancer.
     * Women are 13 times more likely, compared to never smokers.
     * http://www.lung.org/lung-health-and-diseases/lung-disease-lookup/lung-cancer/learn-about-
     * lung-cancer/lung-cancer-fact-sheet.html
     * 
     * In a 2006 European study, the risk of developing lung cancer was:
     * 0.2 percent for men who never smoked (0.4% for women);
     * 5.5 percent of male former smokers (2.6% in women);
     * 15.9 percent of current male smokers (9.5% for women);
     * 24.4 percent for male "heavy smokers" defined as smoking more than 5 cigarettes per day
     * (18.5 percent for women) 
     * https://www.verywell.com/what-percentage-of-smokers-get-lung-cancer-2248868
     */

    boolean isSmoker = (boolean) person.attributes.getOrDefault(Person.SMOKER, false);
    boolean quitSmoking = person.attributes.containsKey(QUIT_SMOKING_AGE);
    boolean isMale = "M".equals(person.attributes.get(Person.GENDER));

    double probabilityOfLungCancer;

    if (isMale) {
      // male rates
      if (isSmoker) {
        probabilityOfLungCancer = 0.244;
      } else if (quitSmoking) {
        probabilityOfLungCancer = 0.055;
      } else {
        probabilityOfLungCancer = 0.002;
      }
    } else {
      // female rates
      if (isSmoker) {
        probabilityOfLungCancer = 0.185;
      } else if (quitSmoking) {
        probabilityOfLungCancer = 0.026;
      } else {
        probabilityOfLungCancer = 0.004;
      }
    }

    person.attributes.put("probability_of_lung_cancer", probabilityOfLungCancer);
  }

  private static void startAlcoholism(Person person, long time) {
    // TODO there are various types of alcoholics with different characteristics
    // including age of onset of dependence. we pick 25 as a starting point
    // https://www.therecoveryvillage.com/alcohol-abuse/types-alcoholics/
    if (person.attributes.get(Person.ALCOHOLIC) == null && person.ageInYears(time) == 25) {
      // TODO assume about 8 mil alcoholics/320 mil gen pop
      Boolean alcoholic = person.rand() < 0.025;
      person.attributes.put(Person.ALCOHOLIC, alcoholic);
      double quit_alcoholism_baseline = Double
          .parseDouble(Config.get("lifecycle.quit_alcoholism.baseline", "0.05"));
      person.attributes.put(QUIT_ALCOHOLISM_PROBABILITY, quit_alcoholism_baseline);
    }
  }

  public static void quitSmoking(Person person, long time) {

    int age = person.ageInYears(time);

    if (person.attributes.containsKey(Person.SMOKER)) {
      if (person.attributes.get(Person.SMOKER).equals(true)) {
        double probability = (double) person.attributes.get(QUIT_SMOKING_PROBABILITY);
        if (person.rand() < probability) {
          person.attributes.put(Person.SMOKER, false);
          person.attributes.put(QUIT_SMOKING_AGE, age);
        } else {
          double quit_smoking_baseline = Double
              .parseDouble(Config.get("lifecycle.quit_smoking.baseline", "0.01"));
          double quit_smoking_timestep_delta = Double
              .parseDouble(Config.get("lifecycle.quit_smoking.timestep_delta", "-0.1"));
          probability += quit_smoking_timestep_delta;
          if (probability < quit_smoking_baseline) {
            probability = quit_smoking_baseline;
          }
          person.attributes.put(QUIT_SMOKING_PROBABILITY, probability);
        }
      }
    }
  }

  public static void quitAlcoholism(Person person, long time) {

    int age = person.ageInYears(time);

    if (person.attributes.containsKey(Person.ALCOHOLIC)) {
      if (person.attributes.get(Person.ALCOHOLIC).equals(true)) {
        double probability = (double) person.attributes.get(QUIT_ALCOHOLISM_PROBABILITY);
        if (person.rand() < probability) {
          person.attributes.put(Person.ALCOHOLIC, false);
          person.attributes.put(QUIT_ALCOHOLISM_AGE, age);
        } else {
          double quit_alcoholism_baseline = Double
              .parseDouble(Config.get("lifecycle.quit_alcoholism.baseline", "0.01"));
          double quit_alcoholism_timestep_delta = Double
              .parseDouble(Config.get("lifecycle.quit_alcoholism.timestep_delta", "-0.1"));
          probability += quit_alcoholism_timestep_delta;
          if (probability < quit_alcoholism_baseline) {
            probability = quit_alcoholism_baseline;
          }
          person.attributes.put(QUIT_ALCOHOLISM_PROBABILITY, probability);
        }
      }
    }
  }

  public static void adherence(Person person, long time) {

    if (person.attributes.containsKey(Person.ADHERENCE)) {
      double probability = (double) person.attributes.get(ADHERENCE_PROBABILITY);

      double aherence_baseline = Double
          .parseDouble(Config.get("lifecycle.adherence.baseline", "0.05"));
      double adherence_timestep_delta = Double
          .parseDouble(Config.get("lifecycle.aherence.timestep_delta", "-.01"));
      probability += adherence_timestep_delta;
      if (probability < aherence_baseline) {
        probability = aherence_baseline;
      }
      person.attributes.put(ADHERENCE_PROBABILITY, probability);

    }
  }

  // referenced in the Injuries module - adults > age 65 have multiple screenings that affect fall
  // risk
  private void calculateFallRisk(Person person, long time) {
    if (person.ageInYears(time) >= 65) {
      boolean hasOsteoporosis = (boolean) person.attributes.getOrDefault("osteporosis", false);
      double baselineFallRisk = hasOsteoporosis ? 0.06 : 0.035;// numbers from injuries module

      int activeInterventions = 0;

      // careplan for exercise or PT
      if (person.record.careplanActive("Physical therapy")
          || person.record.careplanActive("Physical activity target light exercise")) {
        activeInterventions++;
      }

      // taking vitamin D
      if (person.record.medicationActive("Cholecalciferol 600 UNT")) {
        activeInterventions++;
      }

      // osteoporosis diagnosis makes them more careful
      if (person.record.conditionActive("Osteoporosis (disorder)")) {
        activeInterventions++;
      }

      double fallRisk = baselineFallRisk * (1 - 0.02 * activeInterventions);
      // reduce the fall risk by 2% per intervention
      // TODO - research actual effectiveness of these interventions

      person.attributes.put("probability_of_fall_injury", fallRisk);
    }
  }
}
