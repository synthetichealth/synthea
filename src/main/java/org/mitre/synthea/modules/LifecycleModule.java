package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.PhysiologyValueGenerator;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.BloodPressureValueGenerator.SysDias;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planeligibility.QualifyingConditionCodesEligibility;
import org.mitre.synthea.world.concepts.BMI;
import org.mitre.synthea.world.concepts.BiometricsConfig;
import org.mitre.synthea.world.concepts.BirthStatistics;
import org.mitre.synthea.world.concepts.Employment;
import org.mitre.synthea.world.concepts.GrowthChart;
import org.mitre.synthea.world.concepts.GrowthChartEntry;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.Names;
import org.mitre.synthea.world.concepts.PediatricGrowthTrajectory;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.Location;

/**
 * LifecycleModule is responsible for managing the lifecycle of a Person.
 */
public final class LifecycleModule extends Module {
  private static final Map<GrowthChart.ChartType, GrowthChart> growthChart =
      GrowthChart.loadCharts();
  private static final List<LinkedHashMap<String, String>> weightForLengthChart =
      loadWeightForLengthChart();
  private static final QualifyingConditionCodesEligibility disabilityCriteria =
      loadDisabilityData();
  private static final String AGE = "AGE";
  private static final String AGE_MONTHS = "AGE_MONTHS";
  /** Attribute key for days until a person will die */
  public static final String DAYS_UNTIL_DEATH = "days_until_death";
  /** Attribute key for the probability a person quits smoking per process call*/
  public static final String QUIT_SMOKING_PROBABILITY = "quit smoking probability";
  /** Attribute key for the age at which the person stopped smoking */
  public static final String QUIT_SMOKING_AGE = "quit smoking age";
  /** Attribute key for the probability a person stops drinking per process call */
  public static final String QUIT_ALCOHOLISM_PROBABILITY = "quit alcoholism probability";
  /** Attribute key for the age at which a person quit alcoholism */
  public static final String QUIT_ALCOHOLISM_AGE = "quit alcoholism age";
  /** Attribute key for a person's probability to follow doctor's orders and recommendations */
  public static final String ADHERENCE_PROBABILITY = "adherence probability";

  private static final String COUNTRY_CODE = Config.get("generate.geography.country_code");
  private static final Double MIDDLE_NAME_PROBABILITY =
      Config.getAsDouble("generate.middle_names", 0.80);

  private static RandomCollection<String> sexualOrientationData = loadSexualOrientationData();

  /**
   * Constructor for LifecycleModule.
   */
  public LifecycleModule() {
    this.name = "Lifecycle";
  }

  private static List<LinkedHashMap<String, String>> loadWeightForLengthChart() {
    String filename = "cdc_wtleninf.csv";
    try {
      String data = Utilities.readResource(filename);
      return SimpleCSV.parse(data);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load csv: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  private static RandomCollection<String> loadSexualOrientationData() {
    RandomCollection<String> soDistribution = new RandomCollection<String>();
    double[] soPercentages = BiometricsConfig.doubles("lifecycle.sexual_orientation");
    // [heterosexual, homosexual, bisexual]
    soDistribution.add(soPercentages[0], "heterosexual");
    soDistribution.add(soPercentages[1], "homosexual");
    soDistribution.add(soPercentages[2], "bisexual");
    return soDistribution;
  }

  private static QualifyingConditionCodesEligibility loadDisabilityData() {
    QualifyingConditionCodesEligibility criteria = null;
    String filename = "payers/eligibility_input_files/ssd_eligibility.csv";
    try {
      criteria = new QualifyingConditionCodesEligibility(filename);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load disability csv: " + filename);
      e.printStackTrace();
    }
    return criteria;
  }

  public Module clone() {
    return this;
  }

  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }
    // run through all of the rules defined
    // ruby "rules" are converted to static functions here
    // since this is intended to only be temporary

    // birth(person, time); intentionally left out - call it only once from Generator
    if (age(person, time)) {
      grow(person, time);
    }
    startSmoking(person, time);
    startAlcoholism(person, time);
    quitSmoking(person, time);
    quitAlcoholism(person, time);
    adherence(person, time);
    calculateVitalSigns(person, time);
    calculateFallRisk(person, time);
    person.attributes.put(Person.DISABLED, isDisabled(person, time));
    if (person.ageInYears(time) >= 18) {
      ((Employment) person.attributes.get(Person.EMPLOYMENT_MODEL)).checkEmployment(person, time);
    }
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

    attributes.put(Person.ID, person.randUUID().toString());
    String language = (String) attributes.get(Person.FIRST_LANGUAGE);
    String gender = (String) attributes.get(Person.GENDER);
    if (attributes.get(Person.ENTITY) == null) {
      attributes.put(Person.BIRTHDATE, time);
      String firstName = Names.fakeFirstName(gender, language, person);
      String lastName = Names.fakeLastName(language, person);
      attributes.put(Person.FIRST_NAME, firstName);
      String middleName = null;
      if (person.rand() <= MIDDLE_NAME_PROBABILITY) {
        middleName = Names.fakeFirstName(gender, language, person);
        attributes.put(Person.MIDDLE_NAME, middleName);
      }
      attributes.put(Person.LAST_NAME, lastName);
      attributes.put(Person.NAME, firstName + " " + lastName);

      String phoneNumber = "555-" + ((person.randInt(999 - 100 + 1) + 100)) + "-"
          + ((person.randInt(9999 - 1000 + 1) + 1000));
      attributes.put(Person.TELECOM, phoneNumber);

      boolean hasStreetAddress2 = person.rand() < 0.5;
      attributes.put(Person.ADDRESS, Names.fakeAddress(hasStreetAddress2, person));
    }

    String motherFirstName = Names.fakeFirstName("F", language, person);
    String motherLastName = Names.fakeLastName(language, person);
    attributes.put(Person.NAME_MOTHER, motherFirstName + " " + motherLastName);

    String fatherFirstName = Names.fakeFirstName("M", language, person);
    // this is anglocentric where the baby gets the father's last name
    attributes.put(Person.NAME_FATHER, fatherFirstName + " " + attributes.get(Person.LAST_NAME));

    double prevalenceOfTwins =
        (double) BiometricsConfig.get("lifecycle.prevalence_of_twins", 0.02);
    if ((person.rand() < prevalenceOfTwins)) {
      attributes.put(Person.MULTIPLE_BIRTH_STATUS, person.randInt(3) + 1);
    }

    String ssn = "999-" + ((person.randInt(99 - 10 + 1) + 10)) + "-"
        + ((person.randInt(9999 - 1000 + 1) + 1000));
    attributes.put(Person.IDENTIFIER_SSN, ssn);

    String city = (String) attributes.get(Person.CITY);
    Location location = (Location) attributes.get(Person.LOCATION);
    if (location != null) {
      // A null location should never happen in practice, but can happen in unit tests
      location.assignPoint(person, city);
      String zipCode = location.getZipCode(city, person);
      person.attributes.put(Person.ZIP, zipCode);
      person.attributes.put(Person.FIPS, Location.getFipsCodeByZipCode(zipCode));
      String[] birthPlace;
      if ("english".equalsIgnoreCase((String) attributes.get(Person.FIRST_LANGUAGE))) {
        birthPlace = location.randomBirthPlace(person);
      } else {
        birthPlace = location.randomBirthplaceByLanguage(
            person, (String) person.attributes.get(Person.FIRST_LANGUAGE));
      }
      attributes.put(Person.BIRTH_CITY, birthPlace[0]);
      attributes.put(Person.BIRTH_STATE, birthPlace[1]);
      attributes.put(Person.BIRTH_COUNTRY, birthPlace[2]);
      // For CSV exports so we don't break any existing schemas
      attributes.put(Person.BIRTHPLACE, birthPlace[3]);
    }

    attributes.put(Person.ACTIVE_WEIGHT_MANAGEMENT, false);
    // TODO: Why are the percentiles a vital sign? Sounds more like an attribute?
    double heightPercentile = person.rand();
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(person.getSeed(), time);
    double weightPercentile = pgt.reverseWeightPercentile(gender, heightPercentile);
    // make the head percentile within 5% of the height percentile
    double headPercentile = heightPercentile + person.rand(0.025, 0.025);
    if (headPercentile < 0.01) {
      headPercentile = 0.01;
    } else if (headPercentile > 1) {
      headPercentile = 1.0;
    }
    // Convert and store as percentage (0 to 100%) because it is recorded in an Observation.
    headPercentile = (100.0 * headPercentile);
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, heightPercentile);
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, weightPercentile);
    person.setVitalSign(VitalSign.HEAD_PERCENTILE, headPercentile);
    person.attributes.put(Person.GROWTH_TRAJECTORY, pgt);

    // Temporarily generate a mother
    Person mother = new Person(person.randLong());
    mother.attributes.put(Person.GENDER, "F");
    mother.attributes.put("pregnant", true);
    mother.attributes.put(Person.RACE, person.attributes.get(Person.RACE));
    mother.attributes.put(Person.ETHNICITY, person.attributes.get(Person.ETHNICITY));
    mother.attributes.put(BirthStatistics.BIRTH_SEX, person.attributes.get(Person.GENDER));
    BirthStatistics.setBirthStatistics(mother, time);

    person.setVitalSign(VitalSign.HEIGHT,
        (double) mother.attributes.get(BirthStatistics.BIRTH_HEIGHT)); // cm
    person.setVitalSign(VitalSign.WEIGHT,
        (double) mother.attributes.get(BirthStatistics.BIRTH_WEIGHT)); // kg
    person.setVitalSign(VitalSign.HEAD, childHeadCircumference(person, time)); // cm

    attributes.put(AGE, 0);
    attributes.put(AGE_MONTHS, 0);

    boolean isRHNeg = person.rand() < 0.15;
    attributes.put("RH_NEG", isRHNeg);

    double adherenceBaseline = Config.getAsDouble("lifecycle.adherence.baseline", 0.05);
    person.attributes.put(ADHERENCE_PROBABILITY, adherenceBaseline);

    grow(person, time); // set initial height and weight from percentiles
    calculateVitalSigns(person, time);  // Set initial values for many vital signs.

    String orientation = sexualOrientationData.next(person);
    attributes.put(Person.SEXUAL_ORIENTATION, orientation);

    // Setup vital signs which follow the generator approach
    setupVitalSignGenerators(person);
  }

  /**
   * Set up the generators for vital signs which use the generator-based approach already.
   * @param person The person to generate vital signs for.
   */
  private static void setupVitalSignGenerators(Person person) {

    person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE,
        new BloodPressureValueGenerator(person, SysDias.SYSTOLIC));
    person.setVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE,
        new BloodPressureValueGenerator(person, SysDias.DIASTOLIC));

    if (ENABLE_PHYSIOLOGY_GENERATORS) {
      List<PhysiologyValueGenerator> physioGenerators = PhysiologyValueGenerator.loadAll(person);

      for (PhysiologyValueGenerator physioGenerator : physioGenerators) {
        person.setVitalSign(physioGenerator.getVitalSign(), physioGenerator);
      }
    }
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
        if (person.attributes.get("veteran") != null) {
          if (person.attributes.get("veteran_provider_reset") == null) {
            // reset providers for veterans, they'll switch to VA facilities
            person.attributes.remove(Person.CURRENTPROVIDER);
            for (EncounterType type : EncounterType.values()) {
              person.attributes.remove(Person.PREFERREDYPROVIDER + type);
            }
            person.attributes.put("veteran_provider_reset", true);
          }
        }
        break;
      case 28:
        // get married
        if (person.attributes.get(Person.MARITAL_STATUS) == null) {
          Boolean getsMarried = (person.rand() < 0.8);
          if (getsMarried) {
            person.attributes.put(Person.MARITAL_STATUS, "M");
            if ("F".equals(person.attributes.get(Person.GENDER))) {
              person.attributes.put(Person.NAME_PREFIX, "Mrs.");
              person.attributes.put(Person.MAIDEN_NAME, person.attributes.get(Person.LAST_NAME));
              String firstName = ((String) person.attributes.get(Person.FIRST_NAME));
              String middleName = null;
              if (person.attributes.containsKey(Person.MIDDLE_NAME)) {
                middleName = (String) person.attributes.get(Person.MIDDLE_NAME);
              }
              String language = (String) person.attributes.get(Person.FIRST_LANGUAGE);
              String newLastName = Names.fakeLastName(language, person);
              person.attributes.put(Person.LAST_NAME, newLastName);
              if (middleName != null) {
                person.attributes.put(Person.NAME,
                    firstName + " " + middleName + " " + newLastName);
              } else {
                person.attributes.put(Person.NAME, firstName + " " + newLastName);
              }
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
      case 35:
        if (person.attributes.get(Person.MARITAL_STATUS + "Decade3") == null) {
          // divorce and widowing...
          // gross approximations for next 10 years based on:
          // https://www.census.gov/content/dam/Census/library/publications/2021/demo/p70-167.pdf
          if (person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
            double check = person.rand();
            if (check < 0.02) {
              // widow
              person.attributes.put(Person.MARITAL_STATUS, "W");
            } else if (check < 0.20) {
              // divorce
              person.attributes.put(Person.MARITAL_STATUS, "D");
            }
          }
          person.attributes.put(Person.MARITAL_STATUS + "Decade3", true);
        }
        break;
      case 45:
        if (person.attributes.get(Person.MARITAL_STATUS + "Decade4") == null) {
          // divorce and widowing...
          // gross approximations for next 10 years based on:
          // https://www.census.gov/content/dam/Census/library/publications/2021/demo/p70-167.pdf
          if (person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
            double check = person.rand();
            if (check < 0.03) {
              // widow
              person.attributes.put(Person.MARITAL_STATUS, "W");
            } else if (check < 0.05) {
              // divorce
              person.attributes.put(Person.MARITAL_STATUS, "D");
            }
          }
          person.attributes.put(Person.MARITAL_STATUS + "Decade4", true);
        }
        break;
      case 55:
        if (person.attributes.get(Person.MARITAL_STATUS + "Decade5") == null) {
          // divorce and widowing...
          // gross approximations for next 10 years based on:
          // https://www.census.gov/content/dam/Census/library/publications/2021/demo/p70-167.pdf
          if (person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
            double check = person.rand();
            if (check < 0.06) {
              // widow
              person.attributes.put(Person.MARITAL_STATUS, "W");
            } else if (check < 0.11) {
              // divorce
              person.attributes.put(Person.MARITAL_STATUS, "D");
            }
          }
          person.attributes.put(Person.MARITAL_STATUS + "Decade5", true);
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

  private static final int ADULT_MAX_WEIGHT_AGE =
      (int) BiometricsConfig.get("lifecycle.adult_max_weight_age", 49);

  private static final int GERIATRIC_WEIGHT_LOSS_AGE =
      (int) BiometricsConfig.get("lifecycle.geriatric_weight_loss_age", 60);

  private static final double[] ADULT_WEIGHT_GAIN_RANGE =
      BiometricsConfig.doubles("lifecycle.adult_weight_gain");

  private static final double[] GERIATRIC_WEIGHT_LOSS_RANGE =
      BiometricsConfig.doubles("lifecycle.geriatric_weight_loss");

  private static void grow(Person person, long time) {
    int age = person.ageInYears(time);
    int ageInMonths = 0; // we only need this if they are less than 20 years old.

    double height = person.getVitalSign(VitalSign.HEIGHT, time);

    if (age < 20) {
      height = childHeightGrowth(person, time);
      ageInMonths = person.ageInMonths(time);
    }
    double weight = adjustWeight(person, time);

    person.setVitalSign(VitalSign.HEIGHT, height);
    person.setVitalSign(VitalSign.WEIGHT, weight);
    double bmi = BMI.calculate(height, weight);
    person.setVitalSign(VitalSign.BMI, bmi);

    if (age <= 3) {
      setCurrentWeightForLengthPercentile(person, time);

      if (ageInMonths <= 36) {
        double headCircumference = childHeadCircumference(person, time);
        person.setVitalSign(VitalSign.HEAD, headCircumference);
      }
    }

    if (age >= 2 && age < 20) {
      String gender = (String) person.attributes.get(Person.GENDER);
      double percentile = percentileForBMI(bmi, gender, ageInMonths);
      person.attributes.put(Person.BMI_PERCENTILE, percentile * 100.0);
    }
  }

  private static double childHeightGrowth(Person person, long time) {
    String gender = (String) person.attributes.get(Person.GENDER);
    int ageInMonths = person.ageInMonths(time);
    return lookupGrowthChart("height", gender, ageInMonths,
        person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
  }

  private static double childHeadCircumference(Person person, long time) {
    String gender = (String) person.attributes.get(Person.GENDER);
    int ageInMonths = person.ageInMonths(time);
    return lookupGrowthChart("head", gender, ageInMonths,
        (person.getVitalSign(VitalSign.HEAD_PERCENTILE, time) / 100.0));
  }

  private static double adjustWeight(Person person, long time) {
    double weight = person.getVitalSign(VitalSign.WEIGHT, time);
    String gender = (String) person.attributes.get(Person.GENDER);
    double heightPercentile = person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time);
    PediatricGrowthTrajectory pgt =
        (PediatricGrowthTrajectory) person.attributes.get(Person.GROWTH_TRAJECTORY);
    int age = person.ageInYears(time);
    int ageInMonths = person.ageInMonths(time);
    if (age < 3 && pgt.beforeInitialSample(time)) {
      // follow growth charts
      weight = lookupGrowthChart("weight", gender, ageInMonths,
          person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time));
    } else if (age < 20) {
      double currentBMI = pgt.currentBMI(person, time);
      double height = growthChart.get(GrowthChart.ChartType.HEIGHT).lookUp(ageInMonths,
          gender, heightPercentile);
      weight = BMI.weightForHeightAndBMI(height, currentBMI);
    } else {
      Object weightManagement = person.attributes.get(Person.ACTIVE_WEIGHT_MANAGEMENT);
      // If there is active weight management,
      // changing of weight will be handled by the WeightLossModule
      if (weightManagement != null && ! (boolean) weightManagement) {
        if (age <= ADULT_MAX_WEIGHT_AGE) {
          // getting older and fatter
          double adultWeightGain = person.rand(ADULT_WEIGHT_GAIN_RANGE);
          weight += adultWeightGain;
        } else if (age >= GERIATRIC_WEIGHT_LOSS_AGE) {
          // getting older and wasting away
          double geriatricWeightLoss = person.rand(GERIATRIC_WEIGHT_LOSS_RANGE);
          weight -= geriatricWeightLoss;
        }
      }
      // If the person needs to gain weight that's been triggered by a module:
      Object kgToGain = person.attributes.get(Person.KILOGRAMS_TO_GAIN);
      if (kgToGain != null && ((double) kgToGain) > 0.0) {
        // We'll reuse the same adult weight gain used for standard adult weight gain.
        // This will result in about double weight gained per year until target kilograms to gain
        // has been reached.
        double adultWeightGain = person.rand(ADULT_WEIGHT_GAIN_RANGE);
        weight += adultWeightGain;
        // Update the weight they have yet to gain.
        double remainingKgToGain = ((double) kgToGain) - adultWeightGain;
        person.attributes.put(Person.KILOGRAMS_TO_GAIN, remainingKgToGain);
      }
    }
    return weight;
  }

  /**
   * Lookup and calculate values from the CDC growth charts, using the LMS
   * values to calculate the intermediate values.
   * Reference : https://www.cdc.gov/growthcharts/percentile_data_files.htm
   *
   * <p>Note: BMI values only available for ageInMonths 24 - 240 as BMI is
   * not typically useful in patients under 24 months.</p>
   *
   * @param chartType "height" | "weight" | "bmi" | "head"
   * @param gender "M" | "F"
   * @param ageInMonths 0 - 240
   * @param percentile 0.0 - 1.0
   * @return The height (cm), weight (kg), bmi (%), or head (cm)
   */
  public static double lookupGrowthChart(String chartType, String gender, int ageInMonths,
      double percentile) {
    switch (chartType) {
      case "height":
        return growthChart.get(GrowthChart.ChartType.HEIGHT).lookUp(ageInMonths,
            gender, percentile);
      case "weight":
        return growthChart.get(GrowthChart.ChartType.WEIGHT).lookUp(ageInMonths,
            gender, percentile);
      case "bmi":
        return growthChart.get(GrowthChart.ChartType.BMI).lookUp(ageInMonths, gender, percentile);
      case "head":
        return growthChart.get(GrowthChart.ChartType.HEAD).lookUp(ageInMonths, gender, percentile);
      default:
        throw new IllegalArgumentException("Unknown chart type: " + chartType);
    }
  }

  /**
   * Look up the percentile that a given BMI falls into based on gender and age in months.
   * @param bmi the BMI to find the percentile for
   * @param gender "M" | "F"
   * @param ageInMonths 24 - 240
   * @return 0 - 1.0
   */
  public static double percentileForBMI(double bmi, String gender, int ageInMonths) {
    return growthChart.get(GrowthChart.ChartType.BMI).percentileFor(ageInMonths, gender, bmi);
  }

  /**
   * If the person is 36 months old or less, then the "weight for length"
   * percentile attribute is set. Otherwise, it is removed.
   *
   * @param person The person.
   * @param time The time during the simulation.
   */
  public static void setCurrentWeightForLengthPercentile(Person person, long time) {
    if (person.ageInMonths(time) <= 36) {
      double height = person.getVitalSign(VitalSign.HEIGHT, time);
      double weight = person.getVitalSign(VitalSign.WEIGHT, time);
      String gender = (String) person.attributes.get(Person.GENDER);
      LinkedHashMap<String, String> entry = null;
      for (LinkedHashMap<String, String> row : weightForLengthChart) {
        if (row.get("Sex").equals(gender)
            && height < Double.parseDouble(row.get("Length"))) {
          entry = row;
          break;
        }
      }
      if (entry == null) {
        person.attributes.put(Person.CURRENT_WEIGHT_LENGTH_PERCENTILE, 99.0);
      } else {
        double l = Double.parseDouble(entry.get("L"));
        double m = Double.parseDouble(entry.get("M"));
        double s = Double.parseDouble(entry.get("S"));
        double z = new GrowthChartEntry(l, m, s).zscoreForValue(weight);
        double percentile = GrowthChart.zscoreToPercentile(z) * 100.0;
        person.attributes.put(Person.CURRENT_WEIGHT_LENGTH_PERCENTILE, percentile);
      }
    }
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

  private static final int[] CHOLESTEROL_RANGE =
      BiometricsConfig.ints("metabolic.lipid_panel.cholesterol");
  private static final int[] TRIGLYCERIDES_RANGE =
      BiometricsConfig.ints("metabolic.lipid_panel.triglycerides");
  private static final int[] HDL_RANGE =
      BiometricsConfig.ints("metabolic.lipid_panel.hdl");

  private static final int[] GLUCOSE_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.glucose");

  private static final int[] UREA_NITROGEN_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.normal.urea_nitrogen");
  private static final double[] CALCIUM_RANGE =
      BiometricsConfig.doubles("metabolic.basic_panel.normal.calcium");


  private static final int[] MILD_KIDNEY_DMG_CC_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.mild_kidney_damage");
  private static final int[] MODERATE_KIDNEY_DMG_CC_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.moderate_kidney_damage");
  private static final int[] SEVERE_KIDNEY_DMG_CC_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.severe_kidney_damage");
  private static final int[] ESRD_CC_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.esrd");
  private static final int[] NORMAL_FEMALE_CC_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.normal.female");
  private static final int[] NORMAL_MALE_CC_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.creatinine_clearance.normal.male");

  private static final int[] NORMAL_MCR_RANGE =
      BiometricsConfig.ints("metabolic.basic_panel.microalbumin_creatinine_ratio.normal");
  private static final int[] CONTROLLED_MCR_RANGE =
      BiometricsConfig
      .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.microalbuminuria_controlled");

  private static final int[] UNCONTROLLED_MCR_RANGE =
      BiometricsConfig
     .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.microalbuminuria_uncontrolled");

  private static final int[] PROTEINURIA_MCR_RANGE =
      BiometricsConfig
      .ints("metabolic.basic_panel.microalbumin_creatinine_ratio.proteinuria");

  private static final double[] CHLORIDE_RANGE =
      BiometricsConfig.doubles("metabolic.basic_panel.normal.chloride");
  private static final double[] POTASSIUM_RANGE =
      BiometricsConfig.doubles("metabolic.basic_panel.normal.potassium");
  private static final double[] CO2_RANGE =
      BiometricsConfig.doubles("metabolic.basic_panel.normal.carbon_dioxide");
  private static final double[] SODIUM_RANGE =
      BiometricsConfig.doubles("metabolic.basic_panel.normal.sodium");

  private static final int[] BLOOD_OXYGEN_SATURATION_NORMAL =
      BiometricsConfig.ints("cardiovascular.oxygen_saturation.normal");
  private static final int[] BLOOD_OXYGEN_SATURATION_HYPOXEMIA =
      BiometricsConfig.ints("cardiovascular.oxygen_saturation.hypoxemia");
  private static final double[] HEART_RATE_NORMAL =
      BiometricsConfig.doubles("cardiovascular.heart_rate.normal");
  private static final double[] RESPIRATION_RATE_NORMAL =
      BiometricsConfig.doubles("respiratory.respiration_rate.normal");

  /**
   * Calculate this person's vital signs,
   * based on their conditions, medications, body composition, etc.
   * @param person The person
   * @param time Current simulation timestamp
   */
  private static void calculateVitalSigns(Person person, long time) {
    int index = 0;
    if (person.attributes.containsKey("diabetes_severity")) {
      index = (Integer) person.attributes.getOrDefault("diabetes_severity", 1);
    }

    double totalCholesterol;
    double triglycerides = person.rand(TRIGLYCERIDES_RANGE[index], TRIGLYCERIDES_RANGE[index + 1]);
    double hdl;

    if (index == 0) {
      // for patients without diabetes
      // source for the below: https://www.cdc.gov/nchs/data/databriefs/db363-h.pdf
      // NCHS Data Brief - No. 363 - April 2020
      // Total and High-density Lipoprotein Cholesterol in Adults: United States, 2015-2018
      boolean lowHDL;
      boolean highTotalChol;
      if (person.attributes.containsKey("low_hdl")) {
        // cache low or high status, so it's consistent
        lowHDL = (boolean)person.attributes.get("low_hdl");
        highTotalChol = (boolean)person.attributes.get("high_total_chol");
      } else {
        boolean female = "F".equals(person.attributes.get(Person.GENDER));

        // gender is the largest factor, much more than age or race/ethnicity
        // from the above source ("Key findings" section):
        //  "Over one-quarter of men (26.6%) and 8.5% of women
        //   had low high-density lipoprotein cholesterol (HDL-C)."
        double chanceOfLowHDL = female ? .085 : .266;

        lowHDL = person.rand() < chanceOfLowHDL;

        person.attributes.put("low_hdl", lowHDL);

        // from the above source:
        //  "During 2015-2018, 11.4% of adults had high total cholesterol,
        //   and prevalence was similar by race and Hispanic origin."
        highTotalChol = person.rand() < .114;
        person.attributes.put("high_total_chol", highTotalChol);
      }

      // normal distribution: sd * randGaussian() + mean
      // these numbers do not come from any formal source
      // but are intended to generate a normal rather than uniform distribution
      if (lowHDL) {
        // low HDL is defined as < 40
        hdl = 3 * person.randGaussian() + 30;
      } else {
        hdl = 5 * person.randGaussian() + 55;
      }

      if (highTotalChol) {
        // high is 240 or more
        totalCholesterol = 20 * person.randGaussian() + 280;
      } else {
        totalCholesterol = 30 * person.randGaussian() + 170;
      }
    } else {
      totalCholesterol = person.rand(CHOLESTEROL_RANGE[index], CHOLESTEROL_RANGE[index + 1]);
      hdl = person.rand(HDL_RANGE[index], HDL_RANGE[index + 1]);
    }

    double ldl = totalCholesterol - hdl - (0.2 * triglycerides);

    person.setVitalSign(VitalSign.TOTAL_CHOLESTEROL, totalCholesterol);
    person.setVitalSign(VitalSign.TRIGLYCERIDES, triglycerides);
    person.setVitalSign(VitalSign.HDL, hdl);
    person.setVitalSign(VitalSign.LDL, ldl);

    double bmi = person.getVitalSign(VitalSign.BMI, time);
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

    int oxygenSaturation;
    if (person.attributes.containsKey("chf")) {
      oxygenSaturation = (int) person.rand(BLOOD_OXYGEN_SATURATION_HYPOXEMIA);
    } else {
      oxygenSaturation = (int) person.rand(BLOOD_OXYGEN_SATURATION_NORMAL);
    }
    person.setVitalSign(VitalSign.OXYGEN_SATURATION, oxygenSaturation);

    // CKD == stage of "Chronic Kidney Disease" or the level of diabetic kidney damage
    int kidneyDamage = (Integer) person.attributes.getOrDefault("ckd", 0);
    int[] ccRange;
    int[] mcrRange;
    switch (kidneyDamage) {
      case 1:
        ccRange = MILD_KIDNEY_DMG_CC_RANGE;
        mcrRange = NORMAL_MCR_RANGE;
        break;
      case 2:
        ccRange = MODERATE_KIDNEY_DMG_CC_RANGE;
        mcrRange = CONTROLLED_MCR_RANGE;
        break;
      case 3:
        ccRange = SEVERE_KIDNEY_DMG_CC_RANGE;
        mcrRange = UNCONTROLLED_MCR_RANGE;
        break;
      case 4:
        ccRange = ESRD_CC_RANGE;
        mcrRange = PROTEINURIA_MCR_RANGE;
        break;
      default:
        if ("F".equals(person.attributes.get(Person.GENDER))) {
          ccRange = NORMAL_FEMALE_CC_RANGE;
        } else {
          ccRange = NORMAL_MALE_CC_RANGE;
        }
        mcrRange = NORMAL_MCR_RANGE;
    }
    double creatinineClearance = person.rand(ccRange);
    person.setVitalSign(VitalSign.EGFR, creatinineClearance);

    double microalbuminCreatinineRatio = person.rand(mcrRange);
    person.setVitalSign(VitalSign.MICROALBUMIN_CREATININE_RATIO, microalbuminCreatinineRatio);

    double creatinine = reverseCalculateCreatinine(person, creatinineClearance, time);
    person.setVitalSign(VitalSign.CREATININE, creatinine);

    person.setVitalSign(VitalSign.UREA_NITROGEN, person.rand(UREA_NITROGEN_RANGE));
    person.setVitalSign(VitalSign.CALCIUM, person.rand(CALCIUM_RANGE));

    index = Math.min(index, 2); // note this continues from the index logic above

    double glucose = person.rand(GLUCOSE_RANGE[index], GLUCOSE_RANGE[index + 1]);
    person.setVitalSign(VitalSign.GLUCOSE, glucose);

    person.setVitalSign(VitalSign.CHLORIDE, person.rand(CHLORIDE_RANGE));
    person.setVitalSign(VitalSign.POTASSIUM, person.rand(POTASSIUM_RANGE));
    person.setVitalSign(VitalSign.CARBON_DIOXIDE, person.rand(CO2_RANGE));
    person.setVitalSign(VitalSign.SODIUM, person.rand(SODIUM_RANGE));

    long timestep = Long.parseLong(Config.get("generate.timestep"));
    double heartStart = person.rand(HEART_RATE_NORMAL);
    double heartEnd = person.rand(HEART_RATE_NORMAL);
    person.setVitalSign(VitalSign.HEART_RATE,
        new TrendingValueGenerator(person, 1.0, heartStart, heartEnd,
            time, time + timestep, HEART_RATE_NORMAL[0], HEART_RATE_NORMAL[1]));

    double respirationStart = person.rand(RESPIRATION_RATE_NORMAL);
    double respirationEnd = person.rand(RESPIRATION_RATE_NORMAL);
    person.setVitalSign(VitalSign.RESPIRATION_RATE,
        new TrendingValueGenerator(person, 1.0, respirationStart, respirationEnd,
            time, time + timestep, RESPIRATION_RATE_NORMAL[0], RESPIRATION_RATE_NORMAL[1]));
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
      double weight = person.getVitalSign(VitalSign.WEIGHT, time); // kg
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

  /**
   * Enables death by natural causes.
   */
  protected static boolean ENABLE_DEATH_BY_NATURAL_CAUSES =
      Config.getAsBoolean("lifecycle.death_by_natural_causes");

  /**
   * Enables death by loss of care.
   */
  protected static boolean ENABLE_DEATH_BY_LOSS_OF_CARE =
      Config.getAsBoolean("lifecycle.death_by_loss_of_care");

  /**
   * Enables physiology generators.
   */
  public static boolean ENABLE_PHYSIOLOGY_GENERATORS =
      Config.getAsBoolean("physiology.generators.enabled", false);

  /** Death From Natural Causes SNOMED Code */
  private static final Code NATURAL_CAUSES = new Code("SNOMED-CT", "9855000",
      "Natural death with unknown cause");
  /** Death From Lack of Treatment SNOMED Code (Due to a Payer not covering treatment)
    * Note: This SNOMED Code (397709008) is just for death - not death from lack of treatment.
    */
  public static final Code LOSS_OF_CARE = new Code("SNOMED-CT", "397709008",
      "Death due to Uncovered and Unreceived Treatment");

  /**
   * Mark a person as dead if they are old enough or if they roll a random number
   * that is less than the likelihood of death for their age.
   * @param person the person to check for death.
   * @param time the time of the simulation
   */
  protected static void death(Person person, long time) {
    if (ENABLE_DEATH_BY_NATURAL_CAUSES) {
      double roll = person.rand();
      double likelihoodOfDeath = likelihoodOfDeath(person.ageInYears(time));
      if (roll < likelihoodOfDeath) {
        person.recordDeath(time, NATURAL_CAUSES);
      }
    }

    if (ENABLE_DEATH_BY_LOSS_OF_CARE && deathFromLossOfCare(person)) {
      person.recordDeath(time, LOSS_OF_CARE);
    }

    if (person.attributes.containsKey(Person.DEATHDATE)) {
      Long deathDate = (Long) person.attributes.get(Person.DEATHDATE);
      long diff = deathDate - time;
      long days = TimeUnit.MILLISECONDS.toDays(diff);
      person.attributes.put(DAYS_UNTIL_DEATH, Long.valueOf(days));
    }
  }

  /**
   * Function that determines the probability of death based on age.
   * @param age the age of the person in years.
   * @return the probability of death in the next year.
   */
  protected static double likelihoodOfDeath(int age) {
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

  /**
   * Determines whether a person dies due to loss-of-care and lack of
   * necessary treatment.
   *
   * @param person the person to check for loss of care death.
   * @return true if the person dies from loss of care, false otherwise.
   */
  public static boolean deathFromLossOfCare(Person person) {
    // Search the person's lossOfCareHealthRecord for missed treatments.
    // Based on missed treatments, increase likelihood of death.
    if (person.lossOfCareEnabled) {
      for (Encounter encounter : person.lossOfCareRecord.encounters) {
        for (Procedure procedure : encounter.procedures) {
          for (Code code : procedure.codes) {
            /*
             * TODO USE A LOOKUP TABLE FOR DEATH PROBABILITIES FOR LACK OF TREATMENTS HERE
             */
            if (code.code.equals("33195004")) {
              return person.rand() < 0.6;
            }
          }
        }
      }
    }
    return false;
  }

  private static void startSmoking(Person person, long time) {
    // 9/10 smokers start before age 18. We will use 16.
    // http://www.cdc.gov/tobacco/data_statistics/fact_sheets/youth_data/tobacco_use/
    if (person.attributes.get(Person.SMOKER) == null && person.ageInYears(time) == 16) {
      int year = Utilities.getYear(time);
      Boolean smoker = person.rand() < likelihoodOfBeingASmoker(year);
      person.attributes.put(Person.SMOKER, smoker);
      double quitSmokingBaseline = Config.getAsDouble("lifecycle.quit_smoking.baseline", 0.01);
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

  private static void startAlcoholism(Person person, long time) {
    // there are various types of alcoholics with different characteristics
    // including age of onset of dependence. we pick 25 as a starting point
    // https://www.therecoveryvillage.com/alcohol-abuse/types-alcoholics/
    if (person.attributes.get(Person.ALCOHOLIC) == null && person.ageInYears(time) == 25) {
      // assume about 8 mil alcoholics/320 mil gen pop
      Boolean alcoholic = person.rand() < 0.025;
      person.attributes.put(Person.ALCOHOLIC, alcoholic);
      double quitAlcoholismBaseline =
              Config.getAsDouble("lifecycle.quit_alcoholism.baseline", 0.05);
      person.attributes.put(QUIT_ALCOHOLISM_PROBABILITY, quitAlcoholismBaseline);
    }
  }

  /**
   * If the person is a smoker, there is a small chance they will quit.
   * @param person The person who might quit smoking.
   * @param time The current time in the simulation.
   */
  public static void quitSmoking(Person person, long time) {
    int age = person.ageInYears(time);
    if (person.attributes.containsKey(Person.SMOKER)) {
      if (person.attributes.get(Person.SMOKER).equals(true)) {
        double probability = (double) person.attributes.get(QUIT_SMOKING_PROBABILITY);
        if (person.rand() < probability) {
          person.attributes.put(Person.SMOKER, false);
          person.attributes.put(QUIT_SMOKING_AGE, age);
        } else {
          double quitSmokingBaseline = Config.getAsDouble("lifecycle.quit_smoking.baseline", 0.01);
          double quitSmokingTimestepDelta =
                  Config.getAsDouble("lifecycle.quit_smoking.timestep_delta", -0.1);
          probability += quitSmokingTimestepDelta;
          if (probability < quitSmokingBaseline) {
            probability = quitSmokingBaseline;
          }
          person.attributes.put(QUIT_SMOKING_PROBABILITY, probability);
        }
      }
    }
  }

  /**
   * If the person is an alcoholic, there is a small chance they will quit.
   * @param person The person who might quit drinking.
   * @param time The current time in the simulation.
   */
  public static void quitAlcoholism(Person person, long time) {
    int age = person.ageInYears(time);

    if (person.attributes.containsKey(Person.ALCOHOLIC)) {
      if (person.attributes.get(Person.ALCOHOLIC).equals(true)) {
        double probability = (double) person.attributes.get(QUIT_ALCOHOLISM_PROBABILITY);
        if (person.rand() < probability) {
          person.attributes.put(Person.ALCOHOLIC, false);
          person.attributes.put(QUIT_ALCOHOLISM_AGE, age);
        } else {
          double quitAlcoholismBaseline =
                  Config.getAsDouble("lifecycle.quit_alcoholism.baseline", 0.01);
          double quitAlcoholismTimestepDelta =
                  Config.getAsDouble("lifecycle.quit_alcoholism.timestep_delta", -0.1);
          probability += quitAlcoholismTimestepDelta;
          if (probability < quitAlcoholismBaseline) {
            probability = quitAlcoholismBaseline;
          }
          person.attributes.put(QUIT_ALCOHOLISM_PROBABILITY, probability);
        }
      }
    }
  }

  /**
   * Adjust the probability of a patients adherence to Doctor orders, whether
   * medication, careplans, whatever.
   * @param person The patient to consider.
   * @param time The time in the simulation.
   */
  public static void adherence(Person person, long time) {
    if (person.attributes.containsKey(Person.ADHERENCE)) {
      double probability = (double) person.attributes.get(ADHERENCE_PROBABILITY);
      double adherenceBaseline = Config.getAsDouble("lifecycle.adherence.baseline", 0.05);
      double adherenceTimestepDelta =
              Config.getAsDouble("lifecycle.adherence.timestep_delta", -0.01);
      probability += adherenceTimestepDelta;
      if (probability < adherenceBaseline) {
        probability = adherenceBaseline;
      }
      person.attributes.put(ADHERENCE_PROBABILITY, probability);
    }
  }

  /**
   * Creates a "probability_of_fall_injury" attribute that gets referenced in the Injuries module
   * where adults > age 65 have multiple screenings that affect fall.
   * @param person The person to calculate risk for.
   * @param time The time within the simulation.
   */
  private void calculateFallRisk(Person person, long time) {
    if (person.ageInYears(time) >= 65) {
      boolean hasOsteoporosis = (boolean) person.attributes.getOrDefault("osteoporosis", false);
      double baselineFallRisk = hasOsteoporosis ? 0.06 : 0.035;// numbers from injuries module

      int activeInterventions = 0;

      // careplan for exercise or PT
      if (person.record.careplanActive("Physical therapy procedure")
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

  /**
   * Determines if the person is disabled according to input file
   * criteria. If the input file is unavailable, the default is false.
   * @param person The person.
   * @param time The time.
   * @return true or false.
   */
  public static boolean isDisabled(Person person, long time) {
    if (disabilityCriteria != null) {
      return disabilityCriteria.isPersonEligible(person, time);
    } else {
      return false;
    }
  }

  /**
   * Determines earliest disability diagnosis time according to input file
   * criteria. If the input file is unavailable, the default is Long.MAX_VALUE.
   * @param person The person.
   * @return Time of earliest disability diagnosis.
   */
  public static long getEarliestDisabilityDiagnosisTime(Person person) {
    if (disabilityCriteria != null) {
      return disabilityCriteria.getEarliestDiagnosis(person);
    } else {
      return Long.MAX_VALUE;
    }
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   *
   * @return Collection of all codes and concepts this module uses
   */
  public static Collection<Code> getAllCodes() {
    return Collections.singleton(NATURAL_CAUSES);
  }

  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String,Inventory> attributes) {
    String m = LifecycleModule.class.getSimpleName();
    // Read
    Attributes.inventory(attributes, m, Person.ADHERENCE, true, false, null);
    Attributes.inventory(attributes, m, ADHERENCE_PROBABILITY, true, false, "1.0");
    Attributes.inventory(attributes, m, AGE, true, false, null);
    Attributes.inventory(attributes, m, AGE_MONTHS, true, false, null);
    Attributes.inventory(attributes, m, Person.ALCOHOLIC, true, false, null);
    Attributes.inventory(attributes, m, "ckd", true, false, null);
    Attributes.inventory(attributes, m, "diabetes", true, false, null);
    Attributes.inventory(attributes, m, "diabetes_severity", true, false, null);
    Attributes.inventory(attributes, m, Person.EDUCATION_LEVEL, true, false, null);
    Attributes.inventory(attributes, m, Person.ETHNICITY, true, false, null);
    Attributes.inventory(attributes, m, Person.FIRST_NAME, true, false, null);
    Attributes.inventory(attributes, m, Person.FIRST_LANGUAGE, true, false, null);
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "M");
    Attributes.inventory(attributes, m, Person.IDENTIFIER_DRIVERS, true, false, null);
    Attributes.inventory(attributes, m, Person.IDENTIFIER_PASSPORT, true, false, null);
    Attributes.inventory(attributes, m, Person.LAST_NAME, true, false, null);
    Attributes.inventory(attributes, m, Person.NAME_PREFIX, true, false, null);
    Attributes.inventory(attributes, m, Person.NAME_SUFFIX, true, false, null);
    Attributes.inventory(attributes, m, Person.MARITAL_STATUS, true, false, null);
    Attributes.inventory(attributes, m, Person.MIDDLE_NAME, true, true, null);
    Attributes.inventory(attributes, m, "osteoporosis", true, false, null);
    Attributes.inventory(attributes, m, "prediabetes", true, false, null);
    Attributes.inventory(attributes, m, QUIT_ALCOHOLISM_PROBABILITY, true, false, null);
    Attributes.inventory(attributes, m, QUIT_SMOKING_PROBABILITY, true, false, null);
    Attributes.inventory(attributes, m, Person.RACE, true, false, null);
    Attributes.inventory(attributes, m, Person.SMOKER, true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.DEATHDATE, true, false, "1046327126000");
    // Write
    Attributes.inventory(attributes, m, "pregnant", false, true, "Boolean");
    Attributes.inventory(attributes, m, "probability_of_fall_injury", false, true, "1.0");
    Attributes.inventory(attributes, m, "RH_NEG", false, true, "Boolean");
    Attributes.inventory(attributes, m, ADHERENCE_PROBABILITY, false, true, "1.0");
    Attributes.inventory(attributes, m, AGE, false, true, "Numeric");
    Attributes.inventory(attributes, m, AGE_MONTHS, false, true, "Numeric");
    Attributes.inventory(attributes, m, BirthStatistics.BIRTH_SEX, false, true, "M");
    Attributes.inventory(attributes, m,
        LifecycleModule.QUIT_SMOKING_PROBABILITY, false, true, "1.0");
    Attributes.inventory(attributes, m, Person.ADDRESS, false, true, null);
    Attributes.inventory(attributes, m, Person.ALCOHOLIC, false, true, "Boolean");
    Attributes.inventory(attributes, m, Person.BIRTH_CITY, false, true, "Bedford");
    Attributes.inventory(attributes, m, Person.BIRTH_COUNTRY, false, true, COUNTRY_CODE);
    Attributes.inventory(attributes, m, Person.BIRTH_STATE, false, true, "Massachusetts");
    Attributes.inventory(attributes, m, Person.BIRTHDATE, false, true, null);
    Attributes.inventory(attributes, m, Person.BIRTHPLACE, false, true, "Boston");
    Attributes.inventory(attributes, m, Person.ETHNICITY, false, true, null);
    Attributes.inventory(attributes, m, Person.FIRST_NAME, false, true, null);
    Attributes.inventory(attributes, m, Person.GENDER, false, true, "F");
    Attributes.inventory(attributes, m, Person.ID, false, true, null);
    Attributes.inventory(attributes, m, Person.IDENTIFIER_DRIVERS, false, true, null);
    Attributes.inventory(attributes, m, Person.IDENTIFIER_PASSPORT, false, true, null);
    Attributes.inventory(attributes, m, Person.IDENTIFIER_SSN, false, true, "999-99-9999");
    Attributes.inventory(attributes, m, Person.LAST_NAME, false, true, null);
    Attributes.inventory(attributes, m, Person.MAIDEN_NAME, false, true, null);
    Attributes.inventory(attributes, m, Person.MARITAL_STATUS, false, true, "M");
    Attributes.inventory(attributes, m, Person.MULTIPLE_BIRTH_STATUS, false, true, "Boolean");
    Attributes.inventory(attributes, m, Person.NAME, false, true, null);
    Attributes.inventory(attributes, m, Person.NAME_FATHER, false, true, null);
    Attributes.inventory(attributes, m, Person.NAME_MOTHER, false, true, null);
    Attributes.inventory(attributes, m, Person.NAME_PREFIX, false, true, null);
    Attributes.inventory(attributes, m, Person.NAME_SUFFIX, false, true, null);
    Attributes.inventory(attributes, m, Person.RACE, false, true, null);
    Attributes.inventory(attributes, m, Person.SEXUAL_ORIENTATION, false, true, null);
    Attributes.inventory(attributes, m, Person.SMOKER, false, true, "Boolean");
    Attributes.inventory(attributes, m, Person.TELECOM, false, true, "555-555-5555");
    Attributes.inventory(attributes, m, Person.ZIP, false, true, "01730");
    Attributes.inventory(attributes, m, QUIT_ALCOHOLISM_AGE, false, true, "Numeric");
    Attributes.inventory(attributes, m, QUIT_ALCOHOLISM_PROBABILITY, false, true, "1.0");
    Attributes.inventory(attributes, m, QUIT_SMOKING_AGE, false, true, "Numeric");
    Attributes.inventory(attributes, m, QUIT_SMOKING_PROBABILITY, false, true, "1.0");
    Attributes.inventory(attributes, m, DAYS_UNTIL_DEATH, false, true, "42");
  }
}