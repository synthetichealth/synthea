package org.mitre.synthea.world.agents;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.engine.ExpressedConditionRecord;
import org.mitre.synthea.engine.ExpressedSymptom;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.quadtree.QuadTreeElement;

public class Person implements Serializable, RandomNumberGenerator, QuadTreeElement {
  private static final long serialVersionUID = 4322116644425686379L;
  private static final ZoneId timeZone = ZoneId.systemDefault();

  public static final String BIRTHDATE = "birthdate";
  public static final String DEATHDATE = "deathdate";
  public static final String FIRST_NAME = "first_name";
  public static final String LAST_NAME = "last_name";
  public static final String MAIDEN_NAME = "maiden_name";
  public static final String NAME_PREFIX = "name_prefix";
  public static final String NAME_SUFFIX = "name_suffix";
  public static final String NAME = "name";
  public static final String RACE = "race";
  public static final String ETHNICITY = "ethnicity";
  public static final String FIRST_LANGUAGE = "first_language";
  public static final String GENDER = "gender";
  public static final String MULTIPLE_BIRTH_STATUS = "multiple_birth_status";
  public static final String TELECOM = "telecom";
  public static final String ID = "id";
  public static final String ADDRESS = "address";
  public static final String CITY = "city";
  public static final String STATE = "state";
  public static final String ZIP = "zip";
  public static final String BIRTHPLACE = "birthplace";
  public static final String BIRTH_CITY = "birth_city";
  public static final String BIRTH_STATE = "birth_state";
  public static final String BIRTH_COUNTRY = "birth_country";
  public static final String COORDINATE = "coordinate";
  public static final String NAME_MOTHER = "name_mother";
  public static final String NAME_FATHER = "name_father";
  public static final String MARITAL_STATUS = "marital_status";
  public static final String SOCIOECONOMIC_SCORE = "socioeconomic_score";
  public static final String SOCIOECONOMIC_CATEGORY = "socioeconomic_category";
  public static final String INCOME = "income";
  public static final String INCOME_LEVEL = "income_level";
  public static final String EDUCATION = "education";
  public static final String EDUCATION_LEVEL = "education_level";
  public static final String OCCUPATION_LEVEL = "occupation_level";
  public static final String SMOKER = "smoker";
  public static final String ALCOHOLIC = "alcoholic";
  public static final String ADHERENCE = "adherence";
  public static final String IDENTIFIER_SSN = "identifier_ssn";
  public static final String IDENTIFIER_DRIVERS = "identifier_drivers";
  public static final String IDENTIFIER_PASSPORT = "identifier_passport";
  public static final String IDENTIFIER_SITE = "identifier_site";
  public static final String IDENTIFIER_RECORD_ID = "identifier_record_id";
  public static final String CONTACT_FAMILY_NAME = "contact_family_name";
  public static final String CONTACT_GIVEN_NAME = "contact_given_name";
  public static final String CONTACT_EMAIL = "contact_email";
  public static final String CAUSE_OF_DEATH = "cause_of_death";
  public static final String SEXUAL_ORIENTATION = "sexual_orientation";
  public static final String LOCATION = "location";
  public static final String ACTIVE_WEIGHT_MANAGEMENT = "active_weight_management";
  public static final String BMI_PERCENTILE = "bmi_percentile";
  public static final String GROWTH_TRAJECTORY = "growth_trajectory";
  public static final String CURRENT_WEIGHT_LENGTH_PERCENTILE = "current_weight_length_percentile";
  public static final String RECORD_GROUP = "record_group";
  public static final String LINK_ID = "link_id";
  private static final String DEDUCTIBLE = "deductible";
  private static final String LAST_MONTH_PAID = "last_month_paid";

  private final Random random;
  public final long seed;
  public long populationSeed;
  /** 
   * Tracks the last time that the person was updated over a serialize/deserialize.
   */
  public long lastUpdated;
  /**
   * Tracks the remaining modules for a person over a serialize/deserialize.
   */
  public List<Module> currentModules;
  public Map<String, Object> attributes;
  public Map<VitalSign, ValueGenerator> vitalSigns;
  /** Data structure for storing symptoms faced by a person.
   * Adding the Long keyset to keep track of the time a symptom is set. */
  Map<String, ExpressedSymptom> symptoms;
  /** Data structure for storing onset conditions (init_time, end_time).*/
  public ExpressedConditionRecord onsetConditionRecord;
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
  /** Person's Payer History.
   * Each element in payerHistory array corresponds to the insurance held at that age.
   */
  public Payer[] payerHistory;
  // Each element in payerOwnerHistory array corresponds to the owner of the insurance at that age.
  private String[] payerOwnerHistory;
  /* Annual Health Expenses. */
  private Map<Integer, Double> annualHealthExpenses;
  /* Annual Health Coverage. */
  private Map<Integer, Double> annualHealthCoverage;

  /**
   * Person constructor.
   */
  public Person(long seed) {
    this.seed = seed;
    random = new Random(seed);
    attributes = new ConcurrentHashMap<String, Object>();
    vitalSigns = new ConcurrentHashMap<VitalSign, ValueGenerator>();
    symptoms = new ConcurrentHashMap<String, ExpressedSymptom>();
    /* initialized the onsetConditions field */
    onsetConditionRecord = new ExpressedConditionRecord(this);
    /* Chronic Medications which will be renewed at each Wellness Encounter */
    chronicMedications = new ConcurrentHashMap<String, HealthRecord.Medication>();
    hasMultipleRecords =
        Config.getAsBoolean("exporter.split_records", false);
    if (hasMultipleRecords) {
      records = new ConcurrentHashMap<String, HealthRecord>();
    }
    defaultRecord = new HealthRecord(this);
    lossOfCareEnabled =
        Config.getAsBoolean("generate.payers.loss_of_care", false);
    if (lossOfCareEnabled) {
      lossOfCareRecord = new HealthRecord(this);
    }
    record = defaultRecord;
    // 128 because it's a nice power of 2, and nobody will reach that age
    payerHistory = new Payer[128];
    payerOwnerHistory = new String[128];
    annualHealthExpenses = new HashMap<Integer, Double>();
    annualHealthCoverage = new HashMap<Integer, Double>();
  }

  /**
   * Returns a random double.
   */
  public double rand() {
    return random.nextDouble();
  }

  /**
   * Returns a random boolean.
   */
  public boolean randBoolean() {
    return random.nextBoolean();
  }

  /**
   * Returns a random integer.
   */
  public int randInt() {
    return random.nextInt();
  }

  /**
   * Returns a random integer in the given bound.
   */
  public int randInt(int bound) {
    return random.nextInt(bound);
  }

  /**
   * Returns a double from a normal distribution.
   */
  public double randGaussian() {
    return random.nextGaussian();
  }

  /**
   * Return a random long.
   */
  public long randLong() {
    return random.nextLong();
  }
  
  /**
   * Return a random UUID.
   */
  public UUID randUUID() {
    return new UUID(randLong(), randLong());
  }
  
  /**
   * Returns a person's age in Period form.
   */
  public Period age(long time) {
    Period age = Period.ZERO;

    if (attributes.containsKey(BIRTHDATE)) {
      LocalDate now = Instant.ofEpochMilli(time).atZone(timeZone).toLocalDate();
      LocalDate birthdate = Instant.ofEpochMilli((long) attributes.get(BIRTHDATE))
          .atZone(timeZone).toLocalDate();
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
   */
  public boolean alive(long time) {
    boolean born = attributes.containsKey(Person.BIRTHDATE);
    Long died = (Long) attributes.get(Person.DEATHDATE);
    return (born && (died == null || died > time));
  }
  
  /**
  * Get the expressed symptoms.
  */
  public Map<String, ExpressedSymptom> getExpressedSymptoms() {
    return symptoms;
  }
  
  /**
  * Get the onsetonditionRecord.
  */
  public ExpressedConditionRecord getOnsetConditionRecord() {
    return onsetConditionRecord;
  }

  /**
   * Returns the number of providers that this person has.
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
   * This correspond to the maximum value across all potential causes.
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
  public Set<String> getSymptoms() {
    Set<String> active = new HashSet<String>(symptoms.keySet());
    for (String symptom : symptoms.keySet()) {
      int severity = getSymptom(symptom);
      if (severity < 20) {
        active.remove(symptom);
      }
    }
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

  public void setVitalSign(VitalSign vitalSign, ValueGenerator valueGenerator) {
    vitalSigns.put(vitalSign, valueGenerator);
  }

  /**
   * Convenience function to set a vital sign to a constant value.
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

  public boolean hadPriorState(String name) {
    return hadPriorState(name, null, null);
  }

  /**
   * Check for prior existence of specified state. 
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
   */
  private synchronized HealthRecord getHealthRecord(Provider provider, long time) {

    // If the person has no more income at this time, then operate on the UncoveredHealthRecord.
    // Note: If person has no more income then they can no longer afford copays/premiums/etc.
    // meaning we can guarantee that they currently have no insurance.
    if (lossOfCareEnabled && !this.stillHasIncome(time)) {
      return this.lossOfCareRecord;
    }

    HealthRecord returnValue = this.defaultRecord;
    if (hasMultipleRecords) {
      String key = provider.getResourceID();
      if (!records.containsKey(key)) {
        HealthRecord record = null;
        if (this.record != null && this.record.provider == null) {
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

  public static final String CURRENT_ENCOUNTERS = "current-encounters";

  /**
   * Get the current encounter for the specified module or null if none exists.
   */
  @SuppressWarnings("unchecked")
  public Encounter getCurrentEncounter(Module module) {
    Map<String, Encounter> moduleToCurrentEncounter
        = (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

    if (moduleToCurrentEncounter == null) {
      moduleToCurrentEncounter = new HashMap<>();
      attributes.put(CURRENT_ENCOUNTERS, moduleToCurrentEncounter);
    }

    return moduleToCurrentEncounter.get(module.name);
  }
  
  /**
   * Check if there are any current encounters.
   * @return true if there current encounters, false otherwise
   */
  public boolean hasCurrentEncounter() {
    if (attributes != null) {
      Map<String, Encounter> moduleToCurrentEncounter
              = (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

      if (moduleToCurrentEncounter != null && !moduleToCurrentEncounter.isEmpty()) {
        // Uncomment the following lines to see which module encounters are blocking the start
        // of wellness encounters in the encounter module.
        // System.out.println("Pre-wellness Encounter Check Failed:");
        // for (String module: moduleToCurrentEncounter.keySet()) {
        //   Encounter encounter = moduleToCurrentEncounter.get(module);
        //   System.out.printf("%s, %s\n", module, encounter.codes.get(0).code);
        // }
        return true;
      }
    }
    return false;
  }

  /**
   * Set the current encounter for the specified module.
   */
  @SuppressWarnings("unchecked")
  public void setCurrentEncounter(Module module, Encounter encounter) {
    Map<String, Encounter> moduleToCurrentEncounter
        = (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

    if (moduleToCurrentEncounter == null) {
      moduleToCurrentEncounter = new HashMap<>();
      attributes.put(CURRENT_ENCOUNTERS, moduleToCurrentEncounter);
    }
    if (encounter == null) {
      moduleToCurrentEncounter.remove(module.name);
    } else {
      moduleToCurrentEncounter.put(module.name, encounter);
    }
  }

  // Providers API -----------------------------------------------------------
  public static final String CURRENTPROVIDER = "currentProvider";
  public static final String PREFERREDYPROVIDER = "preferredProvider";

  /**
   * Get the preferred provider for the specified encounter type. If none is set the
   * provider at the specified time as the preferred provider for this encounter type.
   */
  public Provider getProvider(EncounterType type, long time) {
    String key = PREFERREDYPROVIDER + type;
    if (!attributes.containsKey(key)) {
      setProvider(type, time);
    }
    return (Provider) attributes.get(key);
  }

  /**
   * Set the preferred provider for the specified encounter type.
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
   */
  public void setProvider(EncounterType type, long time) {
    Provider provider = Provider.findService(this, type, time);
    setProvider(type, provider);
  }

  /**
   * Set the current provider to be the supplied provider.
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
   * Returns the list of this person's Payer history.
   */
  public Payer[] getPayerHistory() {
    return this.payerHistory;
  }

  /**
   * Sets the person's payer history at the given time to the given payer.
   */
  public void setPayerAtTime(long time, Payer newPayer) {
    this.setPayerAtAge(this.ageInYears(time), newPayer);
  }

  /**
   * Sets the person's payer history at the given age to the given payer.
   */
  public void setPayerAtAge(int age, Payer payer) {
    // Allows for insurance to be overwritten when the person gets no insurance.
    if (payerHistory[age] != null && !payer.equals(Payer.noInsurance)) {
      throw new RuntimeException("ERROR: Overwriting a person's insurance at age " + age);
    }
    this.payerHistory[age] = payer;
    this.payerOwnerHistory[age] = determinePayerOwnership(payer, age);
  }

  /**
   * Determines and returns what the ownership of the person's insurance at this age.
   */
  private String determinePayerOwnership(Payer payer, int age) {

    // Keep previous year's ownership if payer is unchanged and person has not just turned 18.
    if (this.getPreviousPayerAtAge(age) != null
        && this.getPreviousPayerAtAge(age).equals(payer)
        && age != 18) {
      return this.payerOwnerHistory[age - 1];
    }
    // No owner for no insurance.
    if (payer.equals(Payer.noInsurance)) {
      return "";
    }
    // Standard payer ownership check.
    if (age < 18 && !payer.getName().equals("Medicaid")) {
      // If a person is a minor, their Guardian owns their health plan unless it is Medicaid.
      return "Guardian";
    } else if ((this.attributes.containsKey(Person.MARITAL_STATUS))
        && this.attributes.get(Person.MARITAL_STATUS).equals("M")) {
      // TODO: ownership shouldn't be a coin toss every year
      // If a person is married, there is a 50% chance their spouse owns their insurance.
      if (this.rand(0.0, 1.0) < .5) {
        return "Spouse";
      }
    }
    // If a person is unmarried and over 18, they own their insurance.
    return "Self";
  }

  /**
   * Returns the person's Payer at the given time.
   */
  public Payer getPayerAtTime(long time) {
    int ageInYears = this.ageInYears(time);
    if (this.payerHistory.length > ageInYears) {
      return this.payerHistory[ageInYears];
    } else {
      return null;
    }
  }

  /**
   * Returns the person's Payer at the given age.
   */
  public Payer getPayerAtAge(int personAge) {
    if (this.payerHistory.length > personAge) {
      return this.payerHistory[personAge];
    } else {
      return null;
    }
  }

  /**
   * Returns the person's last year's payer from the given time.
   */
  public Payer getPreviousPayerAtTime(long time) {
    return this.getPreviousPayerAtAge(this.ageInYears(time));
  }

  /**
   * Returns the person's last year's payer from the given time.
   */
  public Payer getPreviousPayerAtAge(int age) {
    return age > 0 ? this.getPayerAtAge(age - 1) : null;
  }

  /**
   * Returns the owner of the peron's payer at the given time.
   */
  public String getPayerOwnershipAtTime(long time) {
    return this.payerOwnerHistory[this.ageInYears(time)];
  }

  /**
   * Returns the owner of the peron's payer at the given age.
   */
  public String getPayerOwnershipAtAge(int age) {
    return this.payerOwnerHistory[age];
  }

  /**
  * Returns the sum of QALYS of this person's life.
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
   * @param payer the payer to check.
   */
  public boolean canAffordPayer(Payer payer) {
    int income = (Integer) this.attributes.get(Person.INCOME);
    double yearlyPremiumTotal = payer.getMonthlyPremium() * 12;
    double yearlyDeductible = payer.getDeductible();
    return income > (yearlyPremiumTotal + yearlyDeductible);
  }

  /**
   * Returns whether the person's yearly expenses exceed their income. If they do,
   * then they will switch to No Insurance.
   * NOTE: This could result in person being kicked off Medicaid/Medicare.
   * 
   * @param time the current time
   */
  private boolean stillHasIncome(long time) {

    double currentYearlyExpenses;
    if (this.annualHealthExpenses.containsKey(this.ageInYears(time))) {
      currentYearlyExpenses = this.annualHealthExpenses.get(this.ageInYears(time));
    } else {
      currentYearlyExpenses = 0.0;
    }

    if ((int) this.attributes.get(Person.INCOME) - currentYearlyExpenses > 0) {
      // Person has remaining income for the year.
      return true;
    }
    // Person no longer has income for the year. They will switch to No Insurance.
    this.setPayerAtTime(time, Payer.noInsurance);
    return false;
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
      Payer currentPayer = this.getPayerAtTime(time);
      this.addExpense(currentPayer.payMonthlyPremium(), time);
      // Update the last monthly premium paid.
      this.attributes.put(Person.LAST_MONTH_PAID, currentMonth);
      // Check if person has gone in debt. If yes, then they recieve no insurance.
      this.stillHasIncome(time);
    }
  }

  /**
   * Resets a person's deductible.
   * 
   * @param time the time that the person's deductible is reset.
   */
  public void resetDeductible(long time) {
    double deductible = this.getPayerAtTime(time).getDeductible();
    this.attributes.put(Person.DEDUCTIBLE, deductible);
  }

  /**
   * Adds the given cost to the person's expenses.
   * 
   * @param costToPatient the cost, after insurance, to this patient.
   * @param time the time that the expense was incurred.
   */
  public void addExpense(double costToPatient, long time) {
    int age = this.ageInYears(time);
    annualHealthExpenses.merge(age, costToPatient, Double::sum);
  }

  /**
   * Adds the given cost to the person's coverage.
   * 
   * @param payerCoverage the cost, after insurance, to this patient.
   * @param time the time that the expense was incurred.
   */
  public void addCoverage(double payerCoverage, long time) {
    int age = this.ageInYears(time);
    annualHealthCoverage.merge(age, payerCoverage, Double::sum);
  }

  /**
   * Returns the total healthcare expenses for this person.
   */
  public double getHealthcareExpenses() {
    return annualHealthExpenses.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  /**
   * Returns the total healthcare coverage for this person.
   */
  public double getHealthcareCoverage() {
    return annualHealthCoverage.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  @SuppressWarnings("unchecked")
  /**
   * Returns the person's QOLS at the given time.
   * 
   * @param time the time to retrive the qols for.
   */
  public double getQolsForYear(int year) {
    return ((Map<Integer, Double>) this.attributes.get(QualityOfLifeModule.QOLS)).get(year);
  }

  @Override
  public double getX() {
    return getLonLat().getX();
  }

  @Override
  public double getY() {
    return getLonLat().getY();
  }

  public Point2D.Double getLonLat() {
    return (Point2D.Double) attributes.get(Person.COORDINATE);
  }
}
