package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.hl7.fhir.r4.model.Patient;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.PhysiologyValueGenerator;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.SimpleYML;
import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.RecordGroup;
import org.mitre.synthea.modules.BloodPressureValueGenerator.SysDias;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.BMI;
import org.mitre.synthea.world.concepts.BiometricsConfig;
import org.mitre.synthea.world.concepts.BirthStatistics;
import org.mitre.synthea.world.concepts.GrowthChart;
import org.mitre.synthea.world.concepts.GrowthChartEntry;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.NHANESSample;
import org.mitre.synthea.world.concepts.PediatricGrowthTrajectory;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.Location;

public final class LifecycleModule extends Module {
  private static final Map<GrowthChart.ChartType, GrowthChart> growthChart =
      GrowthChart.loadCharts();
  private static final List<LinkedHashMap<String, String>> weightForLengthChart =
      loadWeightForLengthChart();
  private static final String AGE = "AGE";
  private static final String AGE_MONTHS = "AGE_MONTHS";
  public static final String QUIT_SMOKING_PROBABILITY = "quit smoking probability";
  public static final String QUIT_SMOKING_AGE = "quit smoking age";
  public static final String QUIT_ALCOHOLISM_PROBABILITY = "quit alcoholism probability";
  public static final String QUIT_ALCOHOLISM_AGE = "quit alcoholism age";
  public static final String ADHERENCE_PROBABILITY = "adherence probability";

  public static final boolean appendNumbersToNames =
      Boolean.parseBoolean(Config.get("generate.append_numbers_to_person_names", "false"));

  private static RandomCollection<String> sexualOrientationData = loadSexualOrientationData();

  private static SimpleYML names = loadNames();

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

  private static SimpleYML loadNames() {
    String filename = "names.yml";
    try {
      String namesData = Utilities.readResource(filename);
      return new SimpleYML(namesData);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load yml: " + filename);
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
    death(person, time);

    // java modules will never "finish"
    return false;
  }

  /**
   * For unto us a child is born.
   * @param person The baby.
   * @param time The time of birth.
   */
  public static void birth(Person person, long time, int index) {
    Map<String, Object> attributes = person.attributes;

    attributes.put(Person.ID, UUID.randomUUID().toString());
    attributes.put(Person.BIRTHDATE, time);
    String gender = (String) attributes.get(Person.GENDER);
    String language = (String) attributes.get(Person.FIRST_LANGUAGE);
    String firstName = patientFirstName(gender, language, person.random, person);
    String lastName = patientLastName(language, person.random, person);
    if (appendNumbersToNames) {
      firstName = addHash(firstName);
      lastName = addHash(lastName);
    }
    attributes.put(Person.FIRST_NAME, firstName);
    attributes.put(Person.LAST_NAME, lastName);
    attributes.put(Person.NAME, firstName + " " + lastName);

    String motherFirstName = fakeFirstName("F", language, person.random);
    String motherLastName = fakeLastName(language, person.random);
    if (appendNumbersToNames) {
      motherFirstName = addHash(motherFirstName);
      motherLastName = addHash(motherLastName);
    }
    attributes.put(Person.NAME_MOTHER, motherFirstName + " " + motherLastName);

    String fatherFirstName = fakeFirstName("M", language, person.random);
    if (appendNumbersToNames) {
      fatherFirstName = addHash(fatherFirstName);
    }
    // this is anglocentric where the baby gets the father's last name
    attributes.put(Person.NAME_FATHER, fatherFirstName + " " + lastName);

    double prevalenceOfTwins =
        (double) BiometricsConfig.get("lifecycle.prevalence_of_twins", 0.02);
    if ((person.rand() < prevalenceOfTwins)) {
      attributes.put(Person.MULTIPLE_BIRTH_STATUS, person.randInt(3) + 1);
    }
    if (person.attributes.get(Person.RECORD_GROUP) != null) {
      RecordGroup recordGroup = (RecordGroup) person.attributes.get(Person.RECORD_GROUP);
      FixedRecord fr = recordGroup.records.get(0);

      attributes.put(Person.TELECOM, fr.getTelecom());
      attributes.put(Person.IDENTIFIER_RECORD_ID, fr.recordId);
      attributes.put(Person.IDENTIFIER_SITE, fr.site);
      attributes.put(Person.CONTACT_GIVEN_NAME, fr.parentFirstName);
      attributes.put(Person.CONTACT_FAMILY_NAME, fr.parentLastName);
      attributes.put(Person.CONTACT_EMAIL, fr.parentEmail);

    } else {
      String phoneNumber = "555-" + ((person.randInt(999 - 100 + 1) + 100)) + "-"
          + ((person.randInt(9999 - 1000 + 1) + 1000));
      attributes.put(Person.TELECOM, phoneNumber);
    }

    String ssn = "999-" + ((person.randInt(99 - 10 + 1) + 10)) + "-"
        + ((person.randInt(9999 - 1000 + 1) + 1000));
    attributes.put(Person.IDENTIFIER_SSN, ssn);

    String city = (String) attributes.get(Person.CITY);
    Location location = (Location) attributes.get(Person.LOCATION);
    if (location != null) {
      // should never happen in practice, but can happen in unit tests
      location.assignPoint(person, city);
      person.attributes.put(Person.ZIP, location.getZipCode(city, person));
      String[] birthPlace;
      if ("english".equalsIgnoreCase((String) attributes.get(Person.FIRST_LANGUAGE))) {
        birthPlace = location.randomBirthPlace(person.random);
      } else {
        birthPlace = location.randomBirthplaceByLanguage(
            person.random, (String) person.attributes.get(Person.FIRST_LANGUAGE));
      }
      attributes.put(Person.BIRTH_CITY, birthPlace[0]);
      attributes.put(Person.BIRTH_STATE, birthPlace[1]);
      attributes.put(Person.BIRTH_COUNTRY, birthPlace[2]);
      // For CSV exports so we don't break any existing schemas
      attributes.put(Person.BIRTHPLACE, birthPlace[3]);
    }

    boolean hasStreetAddress2 = person.rand() < 0.5;
    attributes.put(Person.ADDRESS, patientAddress(hasStreetAddress2, person.random, person));

    attributes.put(Person.ACTIVE_WEIGHT_MANAGEMENT, false);
    // TODO: Why are the percentiles a vital sign? Sounds more like an attribute?
    double heightPercentile = person.rand();
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(person.seed, time);
    double weightPercentile = pgt.reverseWeightPercentile(gender, heightPercentile);
    person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, heightPercentile);
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, weightPercentile);
    person.attributes.put(Person.GROWTH_TRAJECTORY, pgt);

    // Temporarily generate a mother
    Person mother = new Person(person.random.nextLong());
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

    double adherenceBaseline = Double
        .parseDouble(Config.get("lifecycle.adherence.baseline", ".05"));
    person.attributes.put(ADHERENCE_PROBABILITY, adherenceBaseline);

    grow(person, time); // set initial height and weight from percentiles
    calculateVitalSigns(person, time);  // Set initial values for many vital signs.

    String orientation = sexualOrientationData.next(person.random);
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
   * Generate a first name appropriate for a given gender and language.
   * @param gender Gender of the name, "M" or "F"
   * @param language Origin language of the name, "english", "spanish"
   * @param random Random number generator to use.
   * @return First name.
   */
  @SuppressWarnings("unchecked")
  public static String fakeFirstName(String gender, String language, Random random) {
    List<String> choices;
    if ("spanish".equalsIgnoreCase(language)) {
      choices = (List<String>) names.get("spanish." + gender);
    } else {
      choices = (List<String>) names.get("english." + gender);
    }
    // pick a random item from the list
    return choices.get(random.nextInt(choices.size()));
  }

  /**
   * Generate a surname appropriate for a given language.
   * @param language Origin language of the name, "english", "spanish"
   * @param random Random number generator to use.
   * @return Surname or Family Name.
   */
  @SuppressWarnings("unchecked")
  public static String fakeLastName(String language, Random random) {
    List<String> choices;
    if ("spanish".equalsIgnoreCase(language)) {
      choices = (List<String>) names.get("spanish.family");
    } else {
      choices = (List<String>) names.get("english.family");
    }
    // pick a random item from the list
    return choices.get(random.nextInt(choices.size()));
  }

  public static String patientFirstName(String gender, String language, Random random, Person person) {
    if (person.attributes.get(Person.RECORD_GROUP) != null) {
      RecordGroup recordGroup = (RecordGroup) person.attributes.get(Person.RECORD_GROUP);
      FixedRecord fr = recordGroup.records.get(0);
      return fr.firstName;
    }
    return fakeFirstName(gender, language, random);
  }

  public static String patientLastName(String language, Random random, Person person) {
    if (person.attributes.get(Person.RECORD_GROUP) != null) {
      RecordGroup recordGroup = (RecordGroup) person.attributes.get(Person.RECORD_GROUP);
      FixedRecord fr = recordGroup.records.get(0);
      return fr.lastName;
    }
    return fakeLastName(language, random);
  }

  public static String patientAddress(boolean includeLine2, Random random, Person person) {
    if (person.attributes.get(Person.RECORD_GROUP) != null) {
      RecordGroup recordGroup = (RecordGroup) person.attributes.get(Person.RECORD_GROUP);
      FixedRecord fr = recordGroup.records.get(0);
      return fr.addressLineOne;
    }
    return fakeAddress(includeLine2, random);
  }

  /**
   * Generate a Street Address.
   * @param includeLine2 Whether or not the address should have a second line,
   *     which can take the form of an apartment, unit, or suite number.
   * @param random Random number generator to use.
   * @return First name.
   */
  @SuppressWarnings("unchecked")
  public static String fakeAddress(boolean includeLine2, Random random) {
    int number = random.nextInt(1000) + 100;
    List<String> n = (List<String>)names.get("english.family");
    // for now just use family names as the street name.
    // could expand with a few more but probably not worth it
    String streetName = n.get(random.nextInt(n.size()));
    List<String> a = (List<String>)names.get("street.type");
    String streetType = a.get(random.nextInt(a.size()));

    if (includeLine2) {
      int addtlNum = random.nextInt(100);
      List<String> s = (List<String>)names.get("street.secondary");
      String addtlType = s.get(random.nextInt(s.size()));
      return number + " " + streetName + " " + streetType + " " + addtlType + " " + addtlNum;
    } else {
      return number + " " + streetName + " " + streetType;
    }
  }

  /**
   * Adds a 1- to 3-digit hashcode to the end of the name.
   * @param name Person's name
   * @return The name with a hash appended, ex "John123" or "Smith22"
   */
  public static String addHash(String name) {
    // note that this value should be deterministic
    // It cannot be a random number. It needs to be a hash value or something deterministic.
    // We do not want John10 and John52 -- we want all the Johns to have the SAME numbers. e.g. All
    // people named John become John52
    // Why? Because we do not know how using systems will index names. Say a user of an system
    // loaded with Synthea data wants to find all the people named John Smith. This will be easier
    // if John Smith always resolves to John52 Smith32 and not [John52 Smith32, John10 Smith22, ...]
    return name + Integer.toString(Math.abs(name.hashCode() % 1000));
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
              String language = (String) person.attributes.get(Person.FIRST_LANGUAGE);
              String newLastName = fakeLastName(language, person.random);
              if (appendNumbersToNames) {
                newLastName = addHash(newLastName);
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
        person.getVitalSign(VitalSign.HEIGHT_PERCENTILE, time));
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
      double currentBMI = pgt.currentBMI(person, time, person.random);
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

    double totalCholesterol = person.rand(CHOLESTEROL_RANGE[index], CHOLESTEROL_RANGE[index + 1]);
    double triglycerides = person.rand(TRIGLYCERIDES_RANGE[index], TRIGLYCERIDES_RANGE[index + 1]);
    double hdl = person.rand(HDL_RANGE[index], HDL_RANGE[index + 1]);
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

  protected static boolean ENABLE_DEATH_BY_NATURAL_CAUSES =
      Boolean.parseBoolean(Config.get("lifecycle.death_by_natural_causes"));
  protected static boolean ENABLE_DEATH_BY_LOSS_OF_CARE =
      Boolean.parseBoolean(Config.get("lifecycle.death_by_loss_of_care"));
  protected static boolean ENABLE_PHYSIOLOGY_GENERATORS =
      Boolean.parseBoolean(Config.get("physiology.generators.enabled", "false"));

  // Death From Natural Causes SNOMED Code
  private static final Code NATURAL_CAUSES = new Code("SNOMED-CT", "9855000",
      "Natural death with unknown cause");
  // Death From Lack of Treatment SNOMED Code (Due to a Payer not covering treatment)
  // Note: This SNOMED Code (397709008) is just for death - not death from lack of treatment.
  public static final Code LOSS_OF_CARE = new Code("SNOMED-CT", "397709008",
      "Death due to Uncovered and Unreceived Treatment");

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
  }

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

  private static void startAlcoholism(Person person, long time) {
    // there are various types of alcoholics with different characteristics
    // including age of onset of dependence. we pick 25 as a starting point
    // https://www.therecoveryvillage.com/alcohol-abuse/types-alcoholics/
    if (person.attributes.get(Person.ALCOHOLIC) == null && person.ageInYears(time) == 25) {
      // assume about 8 mil alcoholics/320 mil gen pop
      Boolean alcoholic = person.rand() < 0.025;
      person.attributes.put(Person.ALCOHOLIC, alcoholic);
      double quitAlcoholismBaseline = Double
          .parseDouble(Config.get("lifecycle.quit_alcoholism.baseline", "0.05"));
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

  /**
   * Adjust the probability of a patients adherence to Doctor orders, whether
   * medication, careplans, whatever.
   * @param person The patient to consider.
   * @param time The time in the simulation.
   */
  public static void adherence(Person person, long time) {
    if (person.attributes.containsKey(Person.ADHERENCE)) {
      double probability = (double) person.attributes.get(ADHERENCE_PROBABILITY);
      double adherenceBaseline = Double
          .parseDouble(Config.get("lifecycle.adherence.baseline", "0.05"));
      double adherenceTimestepDelta = Double
          .parseDouble(Config.get("lifecycle.adherence.timestep_delta", "-.01"));
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
    Attributes.inventory(attributes, m, "osteoporosis", true, false, null);
    Attributes.inventory(attributes, m, "prediabetes", true, false, null);
    Attributes.inventory(attributes, m, QUIT_ALCOHOLISM_PROBABILITY, true, false, null);
    Attributes.inventory(attributes, m, QUIT_SMOKING_PROBABILITY, true, false, null);
    Attributes.inventory(attributes, m, Person.RACE, true, false, null);
    Attributes.inventory(attributes, m, Person.SMOKER, true, false, "Boolean");
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
    Attributes.inventory(attributes, m, Person.BIRTH_COUNTRY, false, true, "USA");
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
  }
}
