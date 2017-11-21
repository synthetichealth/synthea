package org.mitre.synthea.modules;

import com.github.javafaker.Faker;
import com.google.gson.Gson;

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
import org.mitre.synthea.world.concepts.BiometricsConfig;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.Location;

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

  private static final boolean appendNumbersToNames =
      Boolean.parseBoolean(Config.get("generate.append_numbers_to_person_names", "false"));

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

  /**
   * For unto us a child is born.
   * @param person The baby.
   * @param time The time of birth.
   */
  public static void birth(Person person, long time) {
    Map<String, Object> attributes = person.attributes;

    attributes.put(Person.ID, UUID.randomUUID().toString());
    attributes.put(Person.BIRTHDATE, time);
    person.events.create(time, Event.BIRTH, "Generator.run", true);

    String firstName = faker.name().firstName();
    String lastName = faker.name().lastName();
    if (appendNumbersToNames) {
      // randInt(1000) produces 1-3 digits
      firstName = firstName + person.randInt(1000);
      lastName = lastName + person.randInt(1000);
    }
    attributes.put(Person.FIRST_NAME, firstName);
    attributes.put(Person.LAST_NAME, lastName);
    attributes.put(Person.NAME, firstName + " " + lastName);

    String motherFirstName = faker.name().firstName();
    String motherLastName = faker.name().lastName();
    if (appendNumbersToNames) {
      motherFirstName = motherFirstName + person.randInt(1000);
      motherLastName = motherLastName + person.randInt(1000);
    }
    attributes.put(Person.NAME_MOTHER, motherFirstName + " " + motherLastName);

    double prevalenceOfTwins = 
        (double) BiometricsConfig.get("lifecycle.prevalence_of_twins", 0.02);
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

    double heightPercentile = person.rand();
    double weightPercentile = person.rand();
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, heightPercentile);
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, weightPercentile);
    person.setVitalSign(VitalSign.HEIGHT, 51.0); // cm
    person.setVitalSign(VitalSign.WEIGHT, 3.5); // kg

    attributes.put(AGE, 0);
    attributes.put(AGE_MONTHS, 0);

    boolean isRHNeg = person.rand() < 0.15;
    attributes.put("RH_NEG", isRHNeg);

    double adherenceBaseline = Double
        .parseDouble(Config.get("lifecycle.adherence.baseline", ".05"));
    person.attributes.put(ADHERENCE_PROBABILITY, adherenceBaseline);

    grow(person, time); // set initial height and weight from percentiles
  }

  /**
   * Age the patient.
   * 
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
              if (appendNumbersToNames) {
                newLastName = newLastName + person.randInt(1000);
              }
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
      default:
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
    int adultMaxWeightAge = (int) BiometricsConfig.get("lifecycle.adult_max_weight_age", 49);
    int geriatricWeightLossAge = 
        (int) BiometricsConfig.get("lifecycle.geriatric_weight_loss_age", 60);

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
    } else if (age <= adultMaxWeightAge) {
      // getting older and fatter
      double[] adultWeightGainRange = BiometricsConfig.doubles("lifecycle.adult_weight_gain");
      double adultWeightGain = person.rand(adultWeightGainRange);
      weight += adultWeightGain;
    } else if (age >= geriatricWeightLossAge) {
      // getting older and wasting away
      double[] geriatricWeightLossRange = 
          BiometricsConfig.doubles("lifecycle.geriatric_weight_loss");
      double geriatricWeightLoss = person.rand(geriatricWeightLossRange);
      weight -= geriatricWeightLoss;
    }

    person.setVitalSign(VitalSign.HEIGHT, height);
    person.setVitalSign(VitalSign.WEIGHT, weight);
    person.setVitalSign(VitalSign.BMI, bmi(height, weight));
  }
  
  @SuppressWarnings("rawtypes")
  public static double lookupGrowthChart(String heightOrWeight, String gender, int ageInMonths,
      double percentile) {
    String[] percentileBuckets = { "3", "5", "10", "25", "50", "75", "90", "95", "97" };

    Map chart = (Map) growthChart.get(heightOrWeight);
    Map byGender = (Map) chart.get(gender);
    Map byAge = (Map) byGender.get(Integer.toString(ageInMonths));
    int bucket = 0;
    for (int i = 0; i < percentileBuckets.length; i++) {
      if ((Double.parseDouble(percentileBuckets[i]) / 100.0) <= percentile) {
        bucket = i;
      } else {
        break;
      }
    }
    return Double.parseDouble((String) byAge.get(percentileBuckets[bucket]));
  }

  private static double bmi(double heightCM, double weightKG) {
    return (weightKG / ((heightCM / 100.0) * (heightCM / 100.0)));
  }

  /**
   * Map of RxNorm drug codes to the expected impact to HbA1c.
   * Impacts should be negative numbers.
   */
  private static final Map<String, Double> DIABETES_DRUG_HBA1C_IMPACTS = createDrugImpactsMap();

  /**
   * Populate the entries of the drug -> impacts map.
   * @return a map of drug code -> expected hba1c delta
   */
  private static Map<String, Double> createDrugImpactsMap() {
    // How much does A1C need to be lowered to get to goal?
    // Metformin and sulfonylureas may lower A1C 1.5 to 2 percentage points,
    // GLP-1 agonists and DPP-4 inhibitors 0.5 to 1 percentage point on average, and
    // insulin as much as 6 points or more, depending on where you start.
    // -- http://www.diabetesforecast.org/2013/mar/your-a1c-achieving-personal-blood-glucose-goals.html
    // [:metformin, :glp1ra, :sglt2i, :basal_insulin, :prandial_insulin]
    //     mono        bi      tri        insulin          insulin++
    Map<String,Double> impacts = new HashMap<>();
    // key is the RxNorm code
    impacts.put("860975", -1.5); // metformin
    impacts.put("897122", -0.5); // liraglutide
    impacts.put("1373463", -0.5); // canagliflozin
    impacts.put("106892", -3.0); // basal insulin
    impacts.put("865098", -6.0); // prandial insulin
    
    return impacts;
  }
  
  /**
   * Calculate this person's vital signs, 
   * based on their conditions, medications, body composition, etc.
   * @param person The person
   * @param time Current simulation timestamp
   */
  private static void diabeticVitalSigns(Person person, long time) {
    boolean hypertension = (Boolean)person.attributes.getOrDefault("hypertension", false);

    String bpConfigLoc;
    if (hypertension) {
      bpConfigLoc = "metabolic.blood_pressure.hypertensive";
    } else {
      bpConfigLoc = "metabolic.blood_pressure.normal";
    }
    int[] sysRange = BiometricsConfig.ints(bpConfigLoc + ".systolic");
    int[] diaRange = BiometricsConfig.ints(bpConfigLoc + ".diastolic");
    person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, person.rand(sysRange));
    person.setVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, person.rand(diaRange));
    
    int index = 0;
    if (person.attributes.containsKey("diabetes_severity")) {
      index = (Integer) person.attributes.getOrDefault("diabetes_severity", 1);
    }
    
    int[] cholRange = BiometricsConfig.ints("metabolic.lipid_panel.cholesterol");
    int[] triglyceridesRange  = BiometricsConfig.ints("metabolic.lipid_panel.triglycerides");
    int[] hdlRange  = BiometricsConfig.ints("metabolic.lipid_panel.hdl");
    
    double totalCholesterol = person.rand(cholRange[index], cholRange[index + 1]);
    double triglycerides = person.rand(triglyceridesRange[index], triglyceridesRange[index + 1]);
    double hdl = person.rand(hdlRange[index], hdlRange[index + 1]);
    double ldl = totalCholesterol - hdl - (0.2 * triglycerides);
    
    person.setVitalSign(VitalSign.TOTAL_CHOLESTEROL, totalCholesterol);
    person.setVitalSign(VitalSign.TRIGLYCERIDES, triglycerides);
    person.setVitalSign(VitalSign.HDL, hdl);
    person.setVitalSign(VitalSign.LDL, ldl);
    
    double bmi = person.getVitalSign(VitalSign.BMI);
    boolean prediabetes = (boolean)person.attributes.getOrDefault("prediabetes", false);
    boolean diabetes = (boolean)person.attributes.getOrDefault("diabetes", false);
    double hbA1c = estimateHbA1c(bmi, prediabetes, diabetes, person);
    
    if (prediabetes || diabetes) {
      // drugs reduce hbA1c.
      // only do this for people that have pre/diabetes, 
      // because these drugs are only prescribed if they do
      for (Map.Entry<String, Double> e : DIABETES_DRUG_HBA1C_IMPACTS.entrySet()) {
        String medicationCode = e.getKey();
        double impact = e.getValue();
        if (person.record.medicationActive(medicationCode)) {
          // impacts are negative, so add them
          hbA1c += impact;
        }
      }
    }

    person.setVitalSign(VitalSign.BLOOD_GLUCOSE, hbA1c);
    
    int kidneyDamage = (Integer) person.attributes.getOrDefault("diabetic_kidney_damage", 0); 
    int[] ccRange;
    int[] mcrRange;
    switch (kidneyDamage) {
      case 1:
        ccRange = BiometricsConfig
          .ints("metabolic.basic_panel.creatinine_clearance.mild_kidney_damage");
        mcrRange = BiometricsConfig
          .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.normal");
        break;
      case 2:
        ccRange = BiometricsConfig
          .ints("metabolic.basic_panel.creatinine_clearance.moderate_kidney_damage");
        mcrRange = BiometricsConfig
          .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.microalbuminuria_controlled");
        break;
      case 3:
        ccRange = BiometricsConfig
          .ints("metabolic.basic_panel.creatinine_clearance.severe_kidney_damage");
        mcrRange = BiometricsConfig
         .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.microalbuminuria_uncontrolled");
        break;
      case 4:
        ccRange = BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.esrd");
        mcrRange = BiometricsConfig
          .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.proteinuria");
        break;
      default:
        if ("F".equals(person.attributes.get(Person.GENDER))) {
          ccRange = 
              BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.normal.female");
        } else {
          ccRange = BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.normal.male");
        }
        mcrRange = BiometricsConfig
          .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.normal");
    }
    double creatinineClearance = person.rand(ccRange);
    person.setVitalSign(VitalSign.EGFR, creatinineClearance);
    
    double microalbuminCreatinineRatio = person.rand(mcrRange);
    person.setVitalSign(VitalSign.MICROALBUMIN_CREATININE_RATIO, microalbuminCreatinineRatio);
    
    double creatinine = reverseCalculateCreatinine(person, creatinineClearance, time);
    person.setVitalSign(VitalSign.CREATININE, creatinine);
    
    int[] unRange = BiometricsConfig.ints("metabolic.basic_panel.normal.urea_nitrogen");
    person.setVitalSign(VitalSign.UREA_NITROGEN, person.rand(unRange));
    double[] calcRange = BiometricsConfig.doubles("metabolic.basic_panel.normal.calcium");
    person.setVitalSign(VitalSign.CALCIUM, person.rand(calcRange));
    
    index = Math.min(index, 2); // note this continues from the index logic above
    
    int[] glucoseRange = BiometricsConfig.ints("metabolic.basic_panel.glucose");
    double glucose = person.rand(glucoseRange[index], glucoseRange[index + 1]);
    person.setVitalSign(VitalSign.GLUCOSE, glucose);

    // these are upper case so the enum can recognize them (especially carbon dioxide)
    for (String electrolyte : new String[] {"CHLORIDE", "POTASSIUM", "CARBON_DIOXIDE", "SODIUM"}) {
      VitalSign electrolyteVS = VitalSign.fromString(electrolyte);
      
      double[] elecRange = 
          BiometricsConfig.doubles("metabolic.basic_panel.normal." + electrolyte.toLowerCase());
      person.setVitalSign(electrolyteVS, person.rand(elecRange));
    }
  }

  /**
   * Estimate the person's HbA1c using BMI and whether or not they have diabetes or prediabetes as a
   * rough guideline.
   * 
   * @param bmi
   *          The person's BMI.
   * @param prediabetes
   *          Whether or not the person is prediabetic. (Diagnosed or undiagnosed)
   * @param diabetes
   *          Whether or not the person is diabetic. (Diagnosed or undiagnosed)
   * @param p
   *          The person
   * @return A calculated HbA1c value.
   */
  private static double estimateHbA1c(double bmi, boolean prediabetes, boolean diabetes, Person p) {
    if (diabetes) {
      if (bmi > 48.0) {
        return 12.0;
      } else if (bmi <= 27.0) {
        return 6.6;
      } else {
        return bmi / 4.0;
        // very simple BMI function so that BMI 40 --> blood glucose ~ 10,
        // but with a bounded min at 6.6 and bounded max at 12.0
      }
    } else if (prediabetes) {
      return p.rand(5.8, 6.4);
    } else {
      return p.rand(5.0, 5.7);
    }
  }

  /**
   * Calculate Creatinine from Creatinine Clearance.
   *  Source: http://www.mcw.edu/calculators/creatinine.htm
   * @param person The person
   * @param crcl Creatinine Clearance
   * @param time Current Time
   * @return Estimated Creatinine
   */
  private static double reverseCalculateCreatinine(Person person, double crcl, long time) {
    try {
      int age = person.ageInYears(time);
      boolean female = "F".equals(person.attributes.get(Person.GENDER));
      double weight = person.getVitalSign(VitalSign.WEIGHT); // kg
      crcl = Math.max(1, Math.min(crcl, 100)); // clamp between 1-100
      double creatinine = ((140.0 - age) * weight) / (72.0 * crcl);
      if (female) {
        creatinine *= 0.85;
      }
      return creatinine;
    } catch (Exception e) {
      return 1.0;
    }
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
      double quitSmokingBaseline = Double
          .parseDouble(Config.get("lifecycle.quit_smoking.baseline", "0.01"));
      person.attributes.put(LifecycleModule.QUIT_SMOKING_PROBABILITY, quitSmokingBaseline);
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
      double quitAlcoholismBaseline = Double
          .parseDouble(Config.get("lifecycle.quit_alcoholism.baseline", "0.05"));
      person.attributes.put(QUIT_ALCOHOLISM_PROBABILITY, quitAlcoholismBaseline);
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
          double quitSmokingBaseline = Double
              .parseDouble(Config.get("lifecycle.quit_smoking.baseline", "0.01"));
          double quitSmokingTimestepDelta = Double
              .parseDouble(Config.get("lifecycle.quit_smoking.timestep_delta", "-0.1"));
          probability += quitSmokingTimestepDelta;
          if (probability < quitSmokingBaseline) {
            probability = quitSmokingBaseline;
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
          double quitAlcoholismBaseline = Double
              .parseDouble(Config.get("lifecycle.quit_alcoholism.baseline", "0.01"));
          double quitAlcoholismTimestepDelta = Double
              .parseDouble(Config.get("lifecycle.quit_alcoholism.timestep_delta", "-0.1"));
          probability += quitAlcoholismTimestepDelta;
          if (probability < quitAlcoholismBaseline) {
            probability = quitAlcoholismBaseline;
          }
          person.attributes.put(QUIT_ALCOHOLISM_PROBABILITY, probability);
        }
      }
    }
  }

  public static void adherence(Person person, long time) {

    if (person.attributes.containsKey(Person.ADHERENCE)) {
      double probability = (double) person.attributes.get(ADHERENCE_PROBABILITY);

      double aherenceBaseline = Double
          .parseDouble(Config.get("lifecycle.adherence.baseline", "0.05"));
      double adherenceTimestepDelta = Double
          .parseDouble(Config.get("lifecycle.aherence.timestep_delta", "-.01"));
      probability += adherenceTimestepDelta;
      if (probability < aherenceBaseline) {
        probability = aherenceBaseline;
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
