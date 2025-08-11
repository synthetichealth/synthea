package org.mitre.synthea.world.agents;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.synthea.engine.ExpressedConditionRecord;
import org.mitre.synthea.engine.ExpressedSymptom;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.DefaultRandomNumberGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.identity.Entity;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.healthinsurance.CoverageRecord;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.quadtree.QuadTreeElement;

/**
 * Represents a person in the simulation.
 */
public class Person implements Serializable, RandomNumberGenerator, QuadTreeElement {
  private static final long serialVersionUID = 4322116644425686379L;

  /** Represents the birthdate of the person. */
  public static final String BIRTHDATE = "birthdate";
  /** Represents the birthdate of the person as a LocalDate object. */
  public static final String BIRTHDATE_AS_LOCALDATE = "birthdate_as_localdate";
  /** Represents the death date of the person. */
  public static final String DEATHDATE = "deathdate";
  /** Represents the first name of the person. */
  public static final String FIRST_NAME = "first_name";
  /** Represents the middle name of the person. */
  public static final String MIDDLE_NAME = "middle_name";
  /** Represents the last name of the person. */
  public static final String LAST_NAME = "last_name";
  /** Represents the maiden name of the person. */
  public static final String MAIDEN_NAME = "maiden_name";
  /** Represents the prefix of the person's name. */
  public static final String NAME_PREFIX = "name_prefix";
  /** Represents the suffix of the person's name. */
  public static final String NAME_SUFFIX = "name_suffix";
  /** Represents the name of the person. */
  public static final String NAME = "name";
  /** Represents the race of the person. */
  public static final String RACE = "race";
  /** Represents the ethnicity of the person. */
  public static final String ETHNICITY = "ethnicity";
  /** Represents the first language of the person. */
  public static final String FIRST_LANGUAGE = "first_language";
  /** Represents the gender of the person. */
  public static final String GENDER = "gender";
  /** Represents whether the person is part of a multiple birth. */
  public static final String MULTIPLE_BIRTH_STATUS = "multiple_birth_status";
  /** Represents the telecom information of the person. */
  public static final String TELECOM = "telecom";
  /** Represents the ID of the person. */
  public static final String ID = "id";
  /** Represents the address of the person. */
  public static final String ADDRESS = "address";
  /** Represents the city of birth of the person. */
  public static final String BIRTH_CITY = "birth_city";
  /** Represents the country of birth of the person. */
  public static final String BIRTH_COUNTRY = "birth_country";
  /** Represents the state of birth of the person. */
  public static final String BIRTH_STATE = "birth_state";
  /** Represents the birthplace of the person. */
  public static final String BIRTHPLACE = "birthplace";
  /** Represents the cause of death of the person. */
  public static final String CAUSE_OF_DEATH = "cause_of_death";
  /** Represents the city of residence of the person. */
  public static final String CITY = "city";
  /** Represents the county of residence of the person. */
  public static final String COUNTY = "county";
  /** Represents the state of residence of the person. */
  public static final String STATE = "state";
  /** Represents the ZIP code of the person's residence. */
  public static final String ZIP = "zip";
  /** Represents the FIPS code of the person's location. */
  public static final String FIPS = "fips";
  /** Represents the geographic coordinates of the person. */
  public static final String COORDINATE = "coordinate";
  /** Represents the mother's name of the person. */
  public static final String NAME_MOTHER = "name_mother";
  /** Represents the father's name of the person. */
  public static final String NAME_FATHER = "name_father";
  /** Represents the marital status of the person. */
  public static final String MARITAL_STATUS = "marital_status";
  /** Represents the socioeconomic score of the person. */
  public static final String SOCIOECONOMIC_SCORE = "socioeconomic_score";
  /** Represents the socioeconomic category of the person. */
  public static final String SOCIOECONOMIC_CATEGORY = "socioeconomic_category";
  /** Represents the income of the person. */
  public static final String INCOME = "income";
  /** Represents the income level of the person. */
  public static final String INCOME_LEVEL = "income_level";
  /** Represents the poverty ratio of the person. */
  public static final String POVERTY_RATIO = "poverty_ratio";
  /** Represents the education level of the person. */
  public static final String EDUCATION = "education";
  /** Represents the education level category of the person. */
  public static final String EDUCATION_LEVEL = "education_level";
  /** Represents the occupation level of the person. */
  public static final String OCCUPATION_LEVEL = "occupation_level";
  /** Represents whether the person is a smoker. */
  public static final String SMOKER = "smoker";
  /** Represents whether the person is an alcoholic. */
  public static final String ALCOHOLIC = "alcoholic";
  /** Represents the adherence level of the person. */
  public static final String ADHERENCE = "adherence";
  /** Represents the social security number identifier of the person. */
  public static final String IDENTIFIER_SSN = "identifier_ssn";
  /** Represents the driver's license identifier of the person. */
  public static final String IDENTIFIER_DRIVERS = "identifier_drivers";
  /** Represents the passport identifier of the person. */
  public static final String IDENTIFIER_PASSPORT = "identifier_passport";
  /** Represents the site identifier of the person. */
  public static final String IDENTIFIER_SITE = "identifier_site";
  /** Represents the variant ID identifier of the person. */
  public static final String IDENTIFIER_VARIANT_ID = "identifier_variant_id";
  /** Represents the seed ID identifier of the person. */
  public static final String IDENTIFIER_SEED_ID = "identifier_seed_id";
  /** Represents the family name of the emergency contact for the person. */
  public static final String CONTACT_FAMILY_NAME = "contact_family_name";
  /** Represents the given name of the emergency contact for the person. */
  public static final String CONTACT_GIVEN_NAME = "contact_given_name";
  /** Represents the email contact information of the person. */
  public static final String CONTACT_EMAIL = "contact_email";
  /** Represents the sexual orientation of the person. */
  public static final String SEXUAL_ORIENTATION = "sexual_orientation";
  /** Represents the location of the person. */
  public static final String LOCATION = "location";
  /** Represents whether the person is actively managing their weight. */
  public static final String ACTIVE_WEIGHT_MANAGEMENT = "active_weight_management";
  /** Represents the BMI percentile of the person. */
  public static final String BMI_PERCENTILE = "bmi_percentile";
  /** Represents the growth trajectory of the person. */
  public static final String GROWTH_TRAJECTORY = "growth_trajectory";
  /** Represents the current weight-length percentile of the person. */
  public static final String CURRENT_WEIGHT_LENGTH_PERCENTILE = "current_weight_length_percentile";
  /** Represents the household associated with the person. */
  public static final String HOUSEHOLD = "household";
  /** Represents the link ID associated with the person. */
  public static final String LINK_ID = "link_id";
  /** Represents whether the person is a veteran. */
  public static final String VETERAN = "veteran";
  /** Represents whether the person is blind. */
  public static final String BLINDNESS = "blindness";
  /** Represents whether the person is disabled. */
  public static final String DISABLED = "disabled";
  /** Represents the role of the person within the household. */
  public static final String HOUSEHOLD_ROLE = "household_role";
  /** Represents the target weight loss for the person. */
  public static final String TARGET_WEIGHT_LOSS = "target_weight_loss";
  /** Represents the kilograms to gain for the person. */
  public static final String KILOGRAMS_TO_GAIN = "kilograms_to_gain";
  /** Represents the entity associated with the person. */
  public static final String ENTITY = "ENTITY";
  /** Represents the insurance status of the person. */
  public static final String INSURANCE_STATUS = "insurance_status";
  /** Represents whether the person experiences food insecurity. */
  public static final String FOOD_INSECURITY = "food_insecurity";
  /** Represents whether the person experiences severe housing cost burden. */
  public static final String SEVERE_HOUSING_COST_BURDEN = "severe_housing_cost_burden";
  /** Represents whether the person is unemployed. */
  public static final String UNEMPLOYED = "unemployed";
  /** Represents the employment model associated with the person. */
  public static final String EMPLOYMENT_MODEL = "employment_model";
  /** Represents whether the person has no vehicle access. */
  public static final String NO_VEHICLE_ACCESS = "no_vehicle_access";
  /** Represents whether the person is uninsured. */
  public static final String UNINSURED = "uninsured";
  /** Represents the last month the premium was paid. */
  private static final String LAST_MONTH_PAID = "last_month_paid";

  /** The random number generator to default to */
  private final DefaultRandomNumberGenerator random;
  /** The random number generator seed for generating the population */
  public long populationSeed;
  /**
   * Tracks the last time that the person was updated over a serialize/deserialize.
   */
  public long lastUpdated;
  /**
   * Tracks the remaining modules for a person over a serialize/deserialize.
   */
  public List<Module> currentModules;
  /** Map of attributes for the person. */
  public Map<String, Object> attributes;
  /** Map of vital signs to their value generators. */
  public Map<VitalSign, ValueGenerator> vitalSigns;
  /** Data structure for storing symptoms faced by a person.
   * Adding the Long keyset to keep track of the time a symptom is set. */
  Map<String, ExpressedSymptom> symptoms;
  /** Data structure for storing onset conditions (init_time, end_time).*/
  public ExpressedConditionRecord onsetConditionRecord;
  /** Map of chronic medications for the person. */
  public Map<String, HealthRecord.Medication> chronicMedications;
  /** The active health record. */
  public HealthRecord record;
  /** Default health record. If "lossOfCareEnabled" is true, this is also the
   * record with entries that were covered by insurance. */
  public HealthRecord defaultRecord;
  /** Only used if "lossOfCareEnabled" is true. In that case, this health record
   * contains entries that should have, but did not, occur. */
  public HealthRecord lossOfCareRecord;
  /** Experimental feature flag. When "lossOfCareEnabled" is true, patients can miss
   * care due to cost or lack of health insurance coverage. */
  public boolean lossOfCareEnabled;
  /** Individual provider health records (if "hasMultipleRecords" is enabled). */
  public Map<String, HealthRecord> records;
  /** Flag that enables each provider having a different health record for each patient.
   * In other words, the patients entire record is split across provider systems. */
  public boolean hasMultipleRecords;
  /** History of the currently active module. */
  public List<State> history;
  /** Record of insurance coverage. */
  public final CoverageRecord coverage;

  /**
   * Person constructor.
   * @param seed the seed for random number generation
   */
  public Person(long seed) {
    random = new DefaultRandomNumberGenerator(seed);
    attributes = new ConcurrentHashMap<String, Object>();
    vitalSigns = new ConcurrentHashMap<VitalSign, ValueGenerator>();
    symptoms = new ConcurrentHashMap<String, ExpressedSymptom>();
    /* initialized the onsetConditions field */
    onsetConditionRecord = new ExpressedConditionRecord(this);
    /* Chronic Medications which will be renewed at each Wellness Encounter */
    chronicMedications = new ConcurrentHashMap<String, HealthRecord.Medication>();
    hasMultipleRecords = Config.getAsBoolean("exporter.split_records", false);
    if (hasMultipleRecords) {
      records = new ConcurrentHashMap<String, HealthRecord>();
    }
    this.initializeDefaultHealthRecords();
    coverage = new CoverageRecord(this);
  }

  /**
   * Initializes person's default health records. May need to be called if attributes
   * change due to fixed demographics.
   */
  public void initializeDefaultHealthRecords() {
    this.defaultRecord = new HealthRecord(this);
    this.record = this.defaultRecord;
    this.lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);
    if (this.lossOfCareEnabled) {
      this.lossOfCareRecord = new HealthRecord(this);
    }
  }

  /**
   * Returns a random double.
   */
  public double rand() {
    return random.rand();
  }

  /**
   * Returns a random boolean.
   */
  public boolean randBoolean() {
    return random.randBoolean();
  }

  /**
   * Returns a random integer.
   */
  public int randInt() {
    return random.randInt();
  }

  /**
   * Returns a random integer in the given bound.
   */
  public int randInt(int bound) {
    return random.randInt(bound);
  }

  /**
   * Returns a double from a normal distribution.
   */
  public double randGaussian() {
    return random.randGaussian();
  }

  /**
   * Return a random long.
   */
  public long randLong() {
    return random.randLong();
  }

  /**
   * Return a random UUID.
   */
  public UUID randUUID() {
    return random.randUUID();
  }

  @Override
  public long getCount() {
    return random.getCount();
  }

  @Override
  public long getSeed() {
    return random.getSeed();
  }

  /**
   * Returns a person's age in Period form.
   *
   * @param time The time at which to calculate the age.
   * @return The age of the person as a Period.
   */
  public Period age(long time) {
    Period age = Period.ZERO;

    if (attributes.containsKey(BIRTHDATE)) {
      LocalDate now = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate();

      // we call age() a lot, so caching the birthdate as a LocalDate saves some translation
      LocalDate birthdate = (LocalDate) attributes.get(BIRTHDATE_AS_LOCALDATE);
      if (birthdate == null) {
        birthdate = Instant.ofEpochMilli((long) attributes.get(BIRTHDATE))
            .atZone(ZoneOffset.UTC).toLocalDate();
        attributes.put(BIRTHDATE_AS_LOCALDATE, birthdate);
      }

      age = Period.between(birthdate, now);
    }
    return age;
  }

  /**
   * Returns a person's age in decimal years. (ex. 7.5 ~ 7 years 6 months old)
   *
   * @param time The time when their age should be calculated.
   * @return decimal age in years
   */
  public double ageInDecimalYears(long time) {
    Period agePeriod = age(time);

    double years = agePeriod.getYears() + agePeriod.getMonths() / 12.0
        + agePeriod.getDays() / 365.2425;

    if (years < 0) {
      years = 0;
    }

    return years;
  }

  /**
   * Return the persons age in months at a given time.
   *
   * @param time The time when their age should be calculated.
   * @return age in months. Can never be less than zero, even if given a time
   *         before they were born.
   */
  public int ageInMonths(long time) {
    int months = (int) age(time).toTotalMonths();
    if (months < 0) {
      months = 0;
    }
    return months;
  }

  /**
   * Returns the persons age in years at the given time.
   *
   * @param time The time when their age should be calculated.
   * @return age in years. Can never be less than zero, even if given a time
   *         before they were born.
   */
  public int ageInYears(long time) {
    int years = age(time).getYears();
    if (years < 0) {
      years = 0;
    }
    return years;
  }

  /**
   * Returns whether a person is alive at the given time.
   * @param time the current time in milliseconds
   * @return true if the person is alive, false otherwise
   */
  public boolean alive(long time) {
    boolean born = attributes.containsKey(Person.BIRTHDATE);
    Long died = (Long) attributes.get(Person.DEATHDATE);
    return (born && (died == null || died > time));
  }

  /**
   * Gets the expressed symptoms of the person.
   * @return a map of symptom names to their expressed symptoms
   */
  public Map<String, ExpressedSymptom> getExpressedSymptoms() {
    return symptoms;
  }

  /**
   * Gets the onset condition record of the person.
   * @return the onset condition record
   */
  public ExpressedConditionRecord getOnsetConditionRecord() {
    return onsetConditionRecord;
  }

  /**
   * Gets the count of providers associated with the person.
   * @return the number of providers
   */
  public int providerCount() {
    int count = 1;
    if (hasMultipleRecords) {
      List<String> uuids = new ArrayList<String>(records.keySet());
      Set<String> uniqueUuids = new HashSet<String>(uuids);
      count = uniqueUuids.size();
    } else {
      count = record.providerCount();
    }
    return count;
  }

  /** Updating the method for accounting of the time on which
   * the symptom is set.
   * Sets a symptom for the person.
   * @param module The module that triggered the symptom.
   * @param cause The cause of the symptom.
   * @param type The type of symptom.
   * @param time The time the symptom occurred.
   * @param value The severity or value of the symptom.
   * @param addressed Whether the symptom has been addressed.
   */
  public void setSymptom(String module, String cause, String type,
      long time, int value, Boolean addressed) {
    if (!symptoms.containsKey(type)) {
      symptoms.put(type, new ExpressedSymptom(type));
    }
    ExpressedSymptom expressedSymptom = symptoms.get(type);
    expressedSymptom.onSet(module, cause, time, value, addressed);
  }

  /**
   * Method for retrieving the last time a given symptom has been updated from a given module.
   * @param module the module containing the symptom
   * @param symptom the symptom to check
   * @return the last updated time of the symptom
   */
  public Long getSymptomLastUpdatedTime(String module, String symptom) {
    Long result = null;
    if (symptoms.containsKey(symptom)) {
      ExpressedSymptom expressedSymptom = symptoms.get(symptom);
      result = expressedSymptom.getSymptomLastUpdatedTime(module);
    }
    return result;
  }

  /**
   * Method for retrieving the value associated to a given symptom.
   * This corresponds to the maximum value across all potential causes.
   * @param type the symptom type
   * @return the value of the symptom
   */
  public int getSymptom(String type) {
    int max = 0;
    if (symptoms.containsKey(type)) {
      ExpressedSymptom expressedSymptom = symptoms.get(type);
      max = expressedSymptom.getSymptom();
    }
    return max;
  }

  /**
   * Get active symptoms above some threshold.
   * TODO These symptoms are not filtered by time.
   * @return list of active symptoms above the threshold.
   */
  public List<String> getSymptoms() {
    List<String> active = symptoms.keySet().stream()
        // map each symptom text to a pair (text, severity)
        .map(symptom -> Pair.of(symptom, getSymptom(symptom)))
        // sort by severity, descending
        .sorted(Comparator.comparing(Pair::getRight, Comparator.reverseOrder()))
        .filter(p -> p.getRight() >= 20) // filter by severity >= 20
        .map(Pair::getLeft) // map back to symptom text
        .collect(Collectors.toList());

    return active;
  }

  /**
   * Mark the largest valued symptom as addressed.
   */
  public void addressLargestSymptom() {
    String highestType = "";
    String highestCause = "";
    int maxValue = 0;
    for (String type : symptoms.keySet()) {
      ExpressedSymptom expressedSymptom = symptoms.get(type);
      String cause = expressedSymptom.getSourceWithHighValue();
      if (cause != null) {
        int value = expressedSymptom.getValueFromSource(cause);
        if (value > maxValue) {
          maxValue = value;
          highestCause = cause;
          highestType = type;
        }
      }
    }
    symptoms.get(highestType).addressSource(highestCause);
  }

  /**
   * Get a vital sign value.
   *
   * @param vitalSign The vital sign to retrieve.
   * @param time The time at which to retrieve the vital sign value.
   * @return The value of the vital sign at the specified time.
   */
  public Double getVitalSign(VitalSign vitalSign, long time) {
    ValueGenerator valueGenerator = vitalSigns.get(vitalSign);
    if (valueGenerator == null) {
      throw new NullPointerException(
          "Vital sign '" + vitalSign + "' not set. Valid vital signs: " + vitalSigns.keySet());
    }
    double value = valueGenerator.getValue(time);
    int decimalPlaces;
    switch (vitalSign) {
      case DIASTOLIC_BLOOD_PRESSURE:
      case SYSTOLIC_BLOOD_PRESSURE:
      case HEART_RATE:
      case RESPIRATION_RATE:
        decimalPlaces = 0;
        break;
      case HEIGHT:
      case WEIGHT:
        decimalPlaces = 1;
        break;
      default:
        decimalPlaces = 2;
    }
    Double retVal = value;
    try {
      retVal = BigDecimal.valueOf(value)
              .setScale(decimalPlaces, RoundingMode.HALF_UP)
              .doubleValue();
    } catch (NumberFormatException e) {
      // Ignore, value was NaN or infinity.
    }
    return retVal;
  }

  /**
   * Set a vital sign's value generator.
   *
   * @param vitalSign The vital sign to set.
   * @param valueGenerator The value generator to use for the vital sign.
   */
  public void setVitalSign(VitalSign vitalSign, ValueGenerator valueGenerator) {
    vitalSigns.put(vitalSign, valueGenerator);
  }

  /**
   * Convenience function to set a vital sign to a constant value.
   *
   * @param vitalSign The vital sign to set.
   * @param value The constant value to set for the vital sign.
   */
  public void setVitalSign(VitalSign vitalSign, double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(String.format(
              "Vital signs must have finite values - %s is invalid",
              Double.valueOf(value).toString()));
    }
    setVitalSign(vitalSign, new ConstantValueGenerator(this, value));
  }

  /**
   * Records a person's death.
   *
   * @param time     the time of death.
   * @param cause    the cause of death.
   */
  public void recordDeath(long time, Code cause) {
    if (alive(time)) {
      long deathTime = time;
      attributes.put(Person.DEATHDATE, Long.valueOf(deathTime));
      if (cause == null) {
        attributes.remove(CAUSE_OF_DEATH);
      } else {
        attributes.put(CAUSE_OF_DEATH, cause);
      }
      record.death = deathTime;
    }
  }

  /**
   * The total number of all unaddressed symptom severities.
   *
   * @return total : sum of all the symptom severities. This number drives
   *         care-seeking behaviors.
   */
  public int symptomTotal() {
    int total = 0;
    for (String type : symptoms.keySet()) {
      total += getSymptom(type);
    }
    return total;
  }

  /**
   * Check for prior existence of specified state.
   * @param name The name of the state.
   * @return True if the state exists, false otherwise.
   */
  public boolean hadPriorState(String name) {
    return hadPriorState(name, null, null);
  }

  /**
   * Check for prior existence of specified state.
   *
   * @param name The name of the state.
   * @param since The name of the state which marks a boundary for checking the history
   * @param within The time within which to check.
   * @return True if the state exists, false otherwise.
   */
  public boolean hadPriorState(String name, String since, Long within) {
    if (history == null) {
      return false;
    }
    for (State state : history) {
      if (within != null && state.exited != null && state.exited <= within) {
        return false;
      }
      if (since != null && state.name.equals(since)) {
        return false;
      }
      if (state.name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Start an encounter for the current provider.
   *
   * @param time The time of the encounter.
   * @param type The type of encounter.
   * @return The started encounter.
   */
  public Encounter encounterStart(long time, EncounterType type) {
    // Set the record for the current provider as active
    Provider provider = getProvider(type, time);
    record = getHealthRecord(provider, time);
    // Start the encounter
    return record.encounterStart(time, type);
  }

  /**
   * Returns the current HealthRecord based on the provider. If the person has no more remaining
   * income, Uncovered HealthRecord is returned.
   *
   * @param provider the provider of the encounter
   * @param time the current time (To determine person's current income and payer)
   * @return The health record for the provider.

   */
  public synchronized HealthRecord getHealthRecord(Provider provider, long time) {

    // If the person has no more income at this time, then operate on the UncoveredHealthRecord.
    // Note: If person has no more income then they can no longer afford copays/premiums/etc.
    // meaning we can guarantee that they currently have no insurance.
    if (lossOfCareEnabled && !this.stillHasIncome(time)) {
      return this.lossOfCareRecord;
    }

    HealthRecord returnValue = this.defaultRecord;
    if (hasMultipleRecords) {
      String key = provider.getResourceID();
      // Check If the given provider does not have a health record for this person.
      if (!records.containsKey(key)) {
        HealthRecord record = null;
        if (this.record != null && this.record.provider == null) {
          // If the active healthrecord does not have a provider, assign it as the active record.
          record = this.record;
        } else {
          record = new HealthRecord(this);
        }
        record.provider = provider;
        records.put(key, record);
      }
      returnValue = records.get(key);
    }
    return returnValue;
  }

  /**
   * Key for tracking the current encounter module.
   */
  public static final String CURRENT_ENCOUNTER_MODULE = "current-encounter-module";

  /**
   * Get the module with the current encounter or null if no module is
   * currently in an encounter.
   *
   * @return The module with the current encounter, or null if none.
   */
  public String getCurrentEncounterModule() {
    return (String) attributes.get(CURRENT_ENCOUNTER_MODULE);
  }

  /**
   * Check if there is a current encounter.
   * @return true if there is a current encounter, false otherwise.
   */
  public boolean hasCurrentEncounter() {
    return attributes.containsKey(CURRENT_ENCOUNTER_MODULE);
  }

  /**
   * Releases the current encounter reservation.
   * This always succeeds, so any calls should make sure they are
   * the owners using 'getCurrentEncounterModule()'.
   * Currently the parameters are unused.
   * @param time The time in the simulation.
   * @param module The name of the module releasing the reservation.
   */
  public void releaseCurrentEncounter(long time, String module) {
    attributes.remove(CURRENT_ENCOUNTER_MODULE);
  }

  /**
   * Reserve the current Encounter... no other module can run an encounter
   * (except for Wellness Encounters).
   * @param time The time in the simulation.
   * @param module The name of the module making the reservation.
   * @return true if the encounter is reserved, false otherwise.
   */
  public boolean reserveCurrentEncounter(long time, String module) {
    if (hasCurrentEncounter()) {
      return false;
    } else {
      attributes.put(CURRENT_ENCOUNTER_MODULE, module);
      return true;
    }
  }

  // Providers API -----------------------------------------------------------
  /** The current provider key */
  public static final String CURRENTPROVIDER = "currentProvider";
  /** The preferred provider key */
  public static final String PREFERREDYPROVIDER = "preferredProvider";

  /**
   * Get the preferred provider for the specified encounter type. If none is set the
   * provider at the specified time as the preferred provider for this encounter type.
   *
   * @param type The type of encounter.
   * @param time The time of the encounter.
   * @return The preferred provider.
   */
  public Provider getProvider(EncounterType type, long time) {
    String key = PREFERREDYPROVIDER + type;
    if (!attributes.containsKey(key)) {
      setProvider(type, time);
    } else {
      Entity entity = (Entity) attributes.get(ENTITY);
      // check to see if this is a fixed identity
      if (entity != null) {
        Provider provider = (Provider) attributes.get(key);
        HealthRecord healthRecord = getHealthRecord(provider, time);
        long lastEncounterTime = healthRecord.lastEncounterTime();
        // check to see if the provider is valid for this see range
        if (lastEncounterTime != Long.MIN_VALUE
            && !entity.seedAt(time).getPeriod().contains(lastEncounterTime)) {
          // The provider is not in the seed range. Force finding a new provider.
          System.out.println("Move reset for " + type);
          setProvider(type, time);
        }
      }
    }
    return (Provider) attributes.get(key);
  }

  /**
   * Set the preferred provider for the specified encounter type.
   *
   * @param type The type of encounter.
   * @param provider The provider to set as preferred.
   */
  public void setProvider(EncounterType type, Provider provider) {
    if (provider == null) {
      throw new RuntimeException("Unable to find provider: " + type);
    }
    String key = PREFERREDYPROVIDER + type;
    attributes.put(key, provider);
  }

  /**
   * Set the preferred provider for the specified encounter type to be the provider
   * at the specified time.
   *
   * @param type The type of encounter.
   * @param time The time of the encounter.
   */
  public void setProvider(EncounterType type, long time) {
    Provider provider = Provider.findService(this, type, time);
    if (provider == null && Provider.USE_HOSPITAL_AS_DEFAULT) {
      // Default to Hospital
      provider = Provider.findService(this, EncounterType.INPATIENT, time);
    }
    setProvider(type, provider);
  }

  /**
   * Set the current provider to be the supplied provider.
   * @param context The context for the provider.
   * @param provider The provider to set as current.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void addCurrentProvider(String context, Provider provider) {
    Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
    if (currentProviders == null) {
      currentProviders = new HashMap<String, Provider>();
      currentProviders.put(context, provider);
    }
    attributes.put(CURRENTPROVIDER, currentProviders);
  }

  /**
   * Remove the current provider for the specified module.
   *
   * @param module The module for which to remove the provider.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void removeCurrentProvider(String module) {
    Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
    if (currentProviders != null) {
      currentProviders.remove(module);
    }
  }

  /**
   * Get the current provider for the specified module.
   *
   * @param module The module for which to get the provider.
   * @return The current provider for the module.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Provider getCurrentProvider(String module) {
    Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
    if (currentProviders == null) {
      return null;
    } else {
      return currentProviders.get(module);
    }
  }

  /**
  * Returns the sum of QALYS of this person's life.
  *
  * @return The sum of QALYS.
  */
  public double getQalys() {

    Map<Integer, Double> qalys
        = (Map<Integer, Double>) this.attributes.get(QualityOfLifeModule.QALY);

    double sum = 0.0;
    for (double currQaly : qalys.values()) {
      sum += currQaly;
    }
    return sum;
  }

  /**
   * Returns the sum of DALYS of this person's life.
   *
   * @return The sum of DALYs.
   */
  public double getDalys() {

    Map<Integer, Double> dalys
        = (Map<Integer, Double>) this.attributes.get(QualityOfLifeModule.DALY);

    double sum = 0.0;
    for (double currDaly : dalys.values()) {
      sum += currDaly;
    }
    return sum;
  }

  /**
   * Returns whether or not a person can afford a given payer.
   * If a person's income is greater than a year of montlhy premiums + deductible
   * then they can afford the insurance.
   *
   * @param plan the plan to check.
   * @return true if the person can afford the plan, false otherwise.
   */
  public boolean canAffordPlan(InsurancePlan plan) {
    double incomePercentage
        = Config.getAsDouble("generate.payers.insurance_plans.income_premium_ratio");
    int income = (int) this.attributes.get(Person.INCOME);
    BigDecimal yearlyCost = plan.getYearlyCost(income);
    return BigDecimal.valueOf(income)
        .multiply(BigDecimal.valueOf(incomePercentage)).compareTo(yearlyCost) >= 0;
  }

  /**
   * Returns whether the person's yearly expenses exceed their income. If they do,
   * then they will switch to No Insurance.
   * Note: This could result in person being kicked off Medicaid/Medicare.
   *
   * @param time the current time
   */
  private boolean stillHasIncome(long time) {
    int incomeRemaining = this.coverage.incomeRemaining(time);
    boolean stillHasIncome = incomeRemaining > 0;
    if (!stillHasIncome && !this.coverage.getPlanAtTime(time).isNoInsurance()) {
      // Person no longer has income for the year. They will switch to No Insurance.
      this.coverage.setPlanToNoInsurance(time);
    }
    return stillHasIncome;
  }

  /**
   * Checks if the person has paid their monthly premium. If not, the person pays
   * the premium to their current payer.
   *
   * @param time the time that the person checks to pay premium.
   */
  public void checkToPayMonthlyPremium(long time) {

    if (!this.attributes.containsKey(Person.LAST_MONTH_PAID)) {
      this.attributes.put(Person.LAST_MONTH_PAID, 0);
    }

    int currentMonth = Utilities.getMonth(time);
    int lastMonthPaid = (int) this.attributes.get(Person.LAST_MONTH_PAID);

    if (currentMonth > lastMonthPaid || (currentMonth == 1 && lastMonthPaid == 12)) {

      // TODO - Check that they can still afford the premium due to any newly incurred health costs.

      // Pay the payer.
      this.coverage.payMonthlyPremiumsAtTime(time,
          (double) this.attributes.get(Person.OCCUPATION_LEVEL),
          (int) this.attributes.get(Person.INCOME));
      // Update the last monthly premium paid.
      this.attributes.put(Person.LAST_MONTH_PAID, currentMonth);
      // Check if person has gone in debt. If yes, then they receive no insurance.
      this.stillHasIncome(time);
    }
  }

  /**
   * Returns the person's QOL at the given time.
   *
   * @param year The year to get QOL data.
   * @return The QOL for the year.
   */
  @SuppressWarnings("unchecked")
  public double getQolsForYear(int year) {
    double retVal = 0;
    Map<Integer, Double> qols = (Map<Integer, Double>)
        this.attributes.get(QualityOfLifeModule.QOLS);
    if (qols != null && qols.containsKey(year)) {
      retVal = qols.get(year);
    }
    return retVal;
  }

  @Override
  public double getX() {
    return getLonLat().getX();
  }

  @Override
  public double getY() {
    return getLonLat().getY();
  }

  /**
   * Returns the geographic coordinates of the person.
   *
   * @return The geographic coordinates.
   */
  public Point2D.Double getLonLat() {
    return (Point2D.Double) attributes.get(Person.COORDINATE);
  }

}
