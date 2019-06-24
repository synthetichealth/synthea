package org.mitre.synthea.world.agents;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.engine.Event;
import org.mitre.synthea.engine.EventList;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;

public class Person implements Serializable, QuadTreeData {
  private static final long serialVersionUID = 4322116644425686379L;

  public static final String BIRTHDATE = "birthdate";
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
  public static final String CAUSE_OF_DEATH = "cause_of_death";
  public static final String SEXUAL_ORIENTATION = "sexual_orientation";
  public static final String LOCATION = "location";
  public static final String ACTIVE_WEIGHT_MANAGEMENT = "active_weight_management";

  public final Random random;
  public final long seed;
  public long populationSeed;
  public Map<String, Object> attributes;
  public Map<VitalSign, ValueGenerator> vitalSigns;
  private Map<String, Map<String, Integer>> symptoms;
  private Map<String, Map<String, Boolean>> symptomStatuses;
  public EventList events;
  /** the active health record. */
  public HealthRecord record;
  public Map<String, HealthRecord> records;
  public boolean hasMultipleRecords;
  /** history of the currently active module. */
  public List<State> history;
  /** person's insurance company. */
  // Each entry in the payerHistory List corresponds to the insurance held at that
  // age
  public List<Payer> payerHistory;

  /**
   * Person constructor.
   */
  public Person(long seed) {
    this.seed = seed; // keep track of seed so it can be exported later
    random = new Random(seed);
    attributes = new ConcurrentHashMap<String, Object>();
    vitalSigns = new ConcurrentHashMap<VitalSign, ValueGenerator>();
    symptoms = new ConcurrentHashMap<String, Map<String, Integer>>();
    symptomStatuses = new ConcurrentHashMap<String, Map<String, Boolean>>();
    events = new EventList();
    hasMultipleRecords = Boolean.parseBoolean(Config.get("exporter.split_records", "false"));
    if (hasMultipleRecords) {
      records = new ConcurrentHashMap<String, HealthRecord>();
    }
    record = new HealthRecord(this);
    // 128 because it's a nice power of 2, and nobody will reach that age
    payerHistory = Arrays.asList(new Payer[128]);
  }

  /**
   * Retuns a random double.
   */
  public double rand() {
    return random.nextDouble();
  }

  /**
   * Retuns a random double in the given range.
   */
  public double rand(double low, double high) {
    return (low + ((high - low) * random.nextDouble()));
  }

  /**
   * Helper function to get a random number based on an array of [min, max]. This
   * should be used primarily when pulling ranges from YML.
   * 
   * @param range array [min, max]
   * @return random double between min and max
   */
  public double rand(double[] range) {
    if (range == null || range.length != 2) {
      throw new IllegalArgumentException("input range must be of length 2 -- got " + Arrays.toString(range));
    }

    if (range[0] > range[1]) {
      throw new IllegalArgumentException("range must be of the form {low, high} -- got " + Arrays.toString(range));
    }

    return rand(range[0], range[1]);
  }

  /**
   * Return one of the options randomly with uniform distribution.
   * 
   * @param choices The options to be returned.
   * @return One of the options randomly selected.
   */
  public String rand(String[] choices) {
    return choices[random.nextInt(choices.length)];
  }

  /**
   * Helper function to get a random number based on an integer array of [min,
   * max]. This should be used primarily when pulling ranges from YML.
   * 
   * @param range array [min, max]
   * @return random double between min and max
   */
  public double rand(int[] range) {
    if (range == null || range.length != 2) {
      throw new IllegalArgumentException("input range must be of length 2 -- got " + Arrays.toString(range));
    }

    if (range[0] > range[1]) {
      throw new IllegalArgumentException("range must be of the form {low, high} -- got " + Arrays.toString(range));
    }

    return rand(range[0], range[1]);
  }

  public int randInt() {
    return random.nextInt();
  }

  public int randInt(int bound) {
    return random.nextInt(bound);
  }

  public Period age(long time) {
    Period age = Period.ZERO;

    if (attributes.containsKey(BIRTHDATE)) {
      LocalDate now = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
      LocalDate birthdate = Instant.ofEpochMilli((long) attributes.get(BIRTHDATE)).atZone(ZoneId.systemDefault())
          .toLocalDate();
      age = Period.between(birthdate, now);
    }
    return age;
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
   * Return the persons age in years at a given time.
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

  public boolean alive(long time) {
    return (events.event(Event.BIRTH) != null && events.before(time, Event.DEATH).isEmpty());
  }

  public void setSymptom(String cause, String type, int value, Boolean addressed) {
    if (!symptoms.containsKey(type)) {
      symptoms.put(type, new ConcurrentHashMap<String, Integer>());
      symptomStatuses.put(type, new ConcurrentHashMap<String, Boolean>());
    }
    symptoms.get(type).put(cause, value);
    symptomStatuses.get(type).put(cause, addressed);
  }

  public int getSymptom(String type) {
    int max = 0;
    if (symptoms.containsKey(type) && symptomStatuses.containsKey(type)) {
      Map<String, Integer> typedSymptoms = symptoms.get(type);
      for (String cause : typedSymptoms.keySet()) {
        if (typedSymptoms.get(cause) > max && !symptomStatuses.get(type).get(cause)) {
          max = typedSymptoms.get(cause);
        }
      }
    }
    return max;
  }

  // Mark the largest valued symptom as addressed.
  public void addressLargestSymptom() {
    String highestType = "";
    String highestCause = "";
    int maxValue = 0;
    for (String type : symptoms.keySet()) {
      if (symptoms.containsKey(type) && symptomStatuses.containsKey(type)) {
        Map<String, Integer> typedSymptoms = symptoms.get(type);
        for (String cause : typedSymptoms.keySet()) {
          if (typedSymptoms.get(cause) > maxValue && !symptomStatuses.get(type).get(cause)) {
            maxValue = typedSymptoms.get(cause);
            highestCause = cause;
            highestType = type;
          }
        }
      }
    }
    symptomStatuses.get(highestType).put(highestCause, true);
  }

  public Double getVitalSign(VitalSign vitalSign, long time) {
    ValueGenerator valueGenerator = vitalSigns.get(vitalSign);
    if (valueGenerator == null) {
      throw new NullPointerException(
          "Vital sign '" + vitalSign + "' not set. Valid vital signs: " + vitalSigns.keySet());
    }
    return valueGenerator.getValue(time);
  }

  public void setVitalSign(VitalSign vitalSign, ValueGenerator valueGenerator) {
    vitalSigns.put(vitalSign, valueGenerator);
  }

  /**
   * Convenience function to set a vital sign to a constant value.
   */
  public void setVitalSign(VitalSign vitalSign, double value) {
    setVitalSign(vitalSign, new ConstantValueGenerator(this, value));
  }

  public void recordDeath(long time, Code cause, String ruleName) {
    events.create(time, Event.DEATH, ruleName, true);
    if (record.death == null || record.death > time) {
      // it's possible for a person to have a death date in the future
      // (ex, a condition with some life expectancy sets a future death date)
      // but then the patient dies sooner because of something else
      record.death = time;
      if (cause == null) {
        attributes.remove(CAUSE_OF_DEATH);
      } else {
        attributes.put(CAUSE_OF_DEATH, cause);
      }
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

  public void resetSymptoms() {
    symptoms.clear();
  }

  public boolean hadPriorState(String name) {
    return hadPriorState(name, null, null);
  }

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

  public Encounter encounterStart(long time, EncounterType type) {
    // Set the record for the current provider as active
    Provider provider = getProvider(type, time);
    record = getHealthRecord(provider);
    // Start the encounter
    return record.encounterStart(time, type);
  }

  public synchronized HealthRecord getHealthRecord(Provider provider) {
    HealthRecord returnValue = this.record;
    if (hasMultipleRecords) {
      String key = provider.uuid;
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

  @SuppressWarnings("unchecked")
  public Encounter getCurrentEncounter(Module module) {
    Map<String, Encounter> moduleToCurrentEncounter = (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

    if (moduleToCurrentEncounter == null) {
      moduleToCurrentEncounter = new HashMap<>();
      attributes.put(CURRENT_ENCOUNTERS, moduleToCurrentEncounter);
    }

    return moduleToCurrentEncounter.get(module.name);
  }

  @SuppressWarnings("unchecked")
  public void setCurrentEncounter(Module module, Encounter encounter) {
    Map<String, Encounter> moduleToCurrentEncounter = (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

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

  public Provider getProvider(EncounterType type, long time) {
    String key = PREFERREDYPROVIDER + type;
    if (!attributes.containsKey(key)) {
      setProvider(type, time);
    }
    return (Provider) attributes.get(key);
  }

  public void setProvider(EncounterType type, Provider provider) {
    String key = PREFERREDYPROVIDER + type;
    attributes.put(key, provider);
  }

  public void setProvider(EncounterType type, long time) {
    Provider provider = Provider.findService(this, type, time);
    setProvider(type, provider);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void addCurrentProvider(String context, Provider provider) {
    Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
    if (currentProviders == null) {
      currentProviders = new HashMap<String, Provider>();
      currentProviders.put(context, provider);
    }
    attributes.put(CURRENTPROVIDER, currentProviders);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void removeCurrentProvider(String module) {
    Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
    if (currentProviders != null) {
      currentProviders.remove(module);
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Provider getCurrentProvider(String module) {
    Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
    if (currentProviders == null) {
      return null;
    } else {
      return currentProviders.get(module);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getX()
   */
  @Override
  public double getX() {
    return getLatLon().getX();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getY()
   */
  @Override
  public double getY() {
    return getLatLon().getY();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getLatLon()
   */
  @Override
  public DirectPosition2D getLatLon() {
    return (DirectPosition2D) attributes.get(Person.COORDINATE);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getFileName()
   */
  @Override
  public String getFileName() {
    return null;
  }

  /**
   * Returns this person's Payer at the given time.
   */
  public Payer getInsurance(long time) {
    int age = this.ageInYears(time);
    if (this.payerHistory.get(age) != null) {
      return this.payerHistory.get(age);
    }
    return this.payerHistory.get(0);
  }

  /**
   * Returns the list of this person's Payer history.
   */
  public List<Payer> getPayerHistory() {
    return this.payerHistory;
  }

  /**
   * Set's the person's payer history at the given age to the given payer.
   */
  public void setPayerAtAge(int age, Payer currentPayer) {
    if (payerHistory.get(age) != null) {
      // System.out.println("ERROR: Overwriting a person's insurance at age " + age +
      // ".");
    }
    this.payerHistory.set(age, currentPayer);
  }

  /**
   * Gets the Payer of the person in the given year.
   */
  public Payer getPayerAtAge(int age) {
    return this.payerHistory.get(age);
  }
}
