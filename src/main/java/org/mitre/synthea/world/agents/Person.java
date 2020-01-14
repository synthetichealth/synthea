package org.mitre.synthea.world.agents;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.helpers.physiology.PhysiologyGeneratorConfig;
import org.mitre.synthea.helpers.physiology.SimRunner;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.quadtree.QuadTreeElement;

public class Person implements Serializable, QuadTreeElement {
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
  public static final String CAUSE_OF_DEATH = "cause_of_death";
  public static final String SEXUAL_ORIENTATION = "sexual_orientation";
  public static final String LOCATION = "location";
  public static final String ACTIVE_WEIGHT_MANAGEMENT = "active_weight_management";
  public static final String BMI_PERCENTILE = "bmi_percentile";
  public static final String CURRENT_WEIGHT_LENGTH_PERCENTILE = "current_weight_length_percentile";
  private static final String DEDUCTIBLE = "deductible";
  private static final String LAST_MONTH_PAID = "last_month_paid";

  public final Random random;
  public final long seed;
  public long populationSeed;
  public Map<String, Object> attributes;
  public Map<VitalSign, ValueGenerator> vitalSigns;
  private Map<String, Map<String, Integer>> symptoms;
  private Map<String, Map<String, Boolean>> symptomStatuses;
  public Map<String, HealthRecord.Medication> chronicMedications;
  /** the active health record. */
  public HealthRecord record;
  public Map<String, HealthRecord> records;
  public boolean hasMultipleRecords;
  /** History of the currently active module. */
  public List<State> history;
  /* Person's Payer History. */
  // Each element in payerHistory array corresponds to the insurance held at that age.
  public Payer[] payerHistory;
  // Each element in payerOwnerHistory array corresponds to the owner of the insurance at that age. 
  private String[] payerOwnerHistory;
  /* Yearly Healthcare Expenses. */
  private Map<Integer, Double> healthcareExpensesYearly;
  /* Yearly Healthcare Coverage. */
  private Map<Integer, Double> healthcareCoverageYearly;

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
    /* Chronic Medications which will be renewed at each Wellness Encounter */
    chronicMedications = new ConcurrentHashMap<String, HealthRecord.Medication>();
    hasMultipleRecords =
        Boolean.parseBoolean(Config.get("exporter.split_records", "false"));
    if (hasMultipleRecords) {
      records = new ConcurrentHashMap<String, HealthRecord>();
    }
    record = new HealthRecord(this);
    // 128 because it's a nice power of 2, and nobody will reach that age
    payerHistory = new Payer[128];
    payerOwnerHistory = new String[128];
    healthcareExpensesYearly = new HashMap<Integer, Double>();
    healthcareCoverageYearly = new HashMap<Integer, Double>();
  }

  /**
   * Retuns a random double.
   */
  public double rand() {
    return random.nextDouble();
  }

  /**
   * Returns a random double in the given range.
   */
  public double rand(double low, double high) {
    return (low + ((high - low) * random.nextDouble()));
  }

  /**
   * Returns a random double in the given range with no more that the specified
   * number of decimal places.
   */
  public double rand(double low, double high, Integer decimals) {
    double value = rand(low, high);
    if (decimals != null) {
      value = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
    return value;
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
      throw new IllegalArgumentException(
          "input range must be of length 2 -- got " + Arrays.toString(range));
    }

    if (range[0] > range[1]) {
      throw new IllegalArgumentException(
          "range must be of the form {low, high} -- got " + Arrays.toString(range));
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
      throw new IllegalArgumentException(
          "input range must be of length 2 -- got " + Arrays.toString(range));
    }

    if (range[0] > range[1]) {
      throw new IllegalArgumentException(
          "range must be of the form {low, high} -- got " + Arrays.toString(range));
    }

    return rand(range[0], range[1]);
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

  /**
   * Get active symptoms above some threshold.
   * TODO These symptoms are not filtered by time.
   * @return list of active symptoms above the threshold.
   */
  public Set<String> getSymptoms() {
    Set<String> active = new HashSet<String>(symptoms.keySet());
    for (String symptom : symptomStatuses.keySet()) {
      int severity = getSymptom(symptom);
      if (severity < 20) {
        active.remove(symptom);
      }
    }
    return active;
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
    return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.HALF_UP).doubleValue();
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

  /**
   * Records a person's death.
   * 
   * @param time     the time of death.
   * @param cause    the cause of death.
   */
  public void recordDeath(long time, Code cause) {
    if (alive(time)) {
      attributes.put(Person.DEATHDATE, Long.valueOf(time));
      if (cause == null) {
        attributes.remove(CAUSE_OF_DEATH);
      } else {
        attributes.put(CAUSE_OF_DEATH, cause);
      }
      record.death = time;
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
    Map<String, Encounter> moduleToCurrentEncounter
        = (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

    if (moduleToCurrentEncounter == null) {
      moduleToCurrentEncounter = new HashMap<>();
      attributes.put(CURRENT_ENCOUNTERS, moduleToCurrentEncounter);
    }

    return moduleToCurrentEncounter.get(module.name);
  }

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
    if (payerHistory[age] != null) {
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
    return this.payerHistory[this.ageInYears(time)];
  }

  /**
   * Returns the person's Payer at the given age.
   */
  public Payer getPayerAtAge(int personAge) {
    return this.payerHistory[personAge];
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
   * Returns whether or not the person can afford to pay out of pocket for the given encounter.
   * Defaults to return false for everyone. For now.
   * 
   * @param entry the entry to pay for.
   */
  public boolean canAffordCare(Entry entry) {
    // TODO determine if they can afford the care
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
    healthcareExpensesYearly.merge(age, costToPatient, Double::sum);
  }

  /**
   * Adds the given cost to the person's coverage.
   * 
   * @param payerCoverage the cost, after insurance, to this patient.
   * @param time the time that the expense was incurred.
   */
  public void addCoverage(double payerCoverage, long time) {
    int age = this.ageInYears(time);
    healthcareCoverageYearly.merge(age, payerCoverage, Double::sum);
  }

  /**
   * Returns the total healthcare expenses for this person.
   */
  public double getHealthcareExpenses() {
    return healthcareExpensesYearly.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  /**
   * Returns the total healthcare coverage for this person.
   */
  public double getHealthcareCoverage() {
    return healthcareCoverageYearly.values().stream().mapToDouble(Double::doubleValue).sum();
  }

  @SuppressWarnings("unchecked")
  /**
   * Returns the person's QOLS at the given time.
   * 
   * @param time the time to retrive the qols for.
   */
  public double getQolsForYear(int year) {
    return ((Map<Integer, Double>) this.attributes.get("QOL")).get(year);
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