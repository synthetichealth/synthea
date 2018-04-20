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
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
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

  public final Random random;
  public final long seed;
  public long populationSeed;
  public Map<String, Object> attributes;
  public Map<VitalSign, Double> vitalSigns;
  private Map<String, Map<String, Integer>> symptoms;
  public EventList events;
  public HealthRecord record;
  /** history of the currently active module. */
  public List<State> history;

  public Person(long seed) {
    this.seed = seed; // keep track of seed so it can be exported later
    random = new Random(seed);
    attributes = new ConcurrentHashMap<String, Object>();
    vitalSigns = new ConcurrentHashMap<VitalSign, Double>();
    symptoms = new ConcurrentHashMap<String, Map<String, Integer>>();
    events = new EventList();
    record = new HealthRecord();
  }

  public double rand() {
    return random.nextDouble();
  }

  public double rand(double low, double high) {
    return (low + ((high - low) * random.nextDouble()));
  }
  
  /**
   * Helper function to get a random number based on an array of [min, max].
   * This should be used primarily when pulling ranges from YML.
   * 
   * @param range array [min, max]
   * @return random double between min and max
   */
  public double rand(double[] range) {
    if (range == null || range.length != 2) {
      throw new IllegalArgumentException("input range must be of length 2 -- got "
          + Arrays.toString(range));
    }
    
    if (range[0] > range[1]) {
      throw new IllegalArgumentException("range must be of the form {low, high} -- got "
          + Arrays.toString(range));
    }
    
    return rand(range[0], range[1]);
  }
  
  // no good way to share code between the double[] and int[] version unfortunately....
  
  /**
   * Helper function to get a random number based on an integer array of [min, max].
   * This should be used primarily when pulling ranges from YML.
   * 
   * @param range array [min, max]
   * @return random double between min and max
   */
  public double rand(int[] range) {
    if (range == null || range.length != 2) {
      throw new IllegalArgumentException("input range must be of length 2 -- got "
          + Arrays.toString(range));
    }
    
    if (range[0] > range[1]) {
      throw new IllegalArgumentException("range must be of the form {low, high} -- got "
          + Arrays.toString(range));
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
      LocalDate birthdate = Instant.ofEpochMilli((long) attributes.get(BIRTHDATE))
          .atZone(ZoneId.systemDefault()).toLocalDate();
      age = Period.between(birthdate, now);
    }
    return age;
  }

  public int ageInMonths(long time) {
    return (int) age(time).toTotalMonths();
  }

  public int ageInYears(long time) {
    return age(time).getYears();
  }

  public boolean alive(long time) {
    return (events.event(Event.BIRTH) != null && events.before(time, Event.DEATH).isEmpty());
  }

  public void setSymptom(String cause, String type, int value) {
    if (!symptoms.containsKey(type)) {
      symptoms.put(type, new ConcurrentHashMap<String, Integer>());
    }
    symptoms.get(type).put(cause, value);
  }

  public int getSymptom(String type) {
    int max = 0;
    if (symptoms.containsKey(type)) {
      Map<String, Integer> typedSymptoms = symptoms.get(type);
      for (String cause : typedSymptoms.keySet()) {
        if (typedSymptoms.get(cause) > max) {
          max = typedSymptoms.get(cause);
        }
      }
    }
    return max;
  }

  public Double getVitalSign(VitalSign vitalSign) {
    return vitalSigns.get(vitalSign);
  }

  public void setVitalSign(VitalSign vitalSign, double value) {
    vitalSigns.put(vitalSign, value);
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
   * The total number of all symptom severities.
   * @return total : sum of all the symptom severities. This number drives care-seeking behaviors.
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

  public static final String CURRENT_ENCOUNTERS = "current-encounters";

  @SuppressWarnings("unchecked")
  public HealthRecord.Encounter getCurrentEncounter(Module module) {
    Map<String, Encounter> moduleToCurrentEncounter = 
        (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

    if (moduleToCurrentEncounter == null) {
      moduleToCurrentEncounter = new HashMap<>();
      attributes.put(CURRENT_ENCOUNTERS, moduleToCurrentEncounter);
    }

    return moduleToCurrentEncounter.get(module.name);
  }

  @SuppressWarnings("unchecked")
  public void setCurrentEncounter(Module module, Encounter encounter) {
    Map<String, Encounter> moduleToCurrentEncounter = 
        (Map<String, Encounter>) attributes.get(CURRENT_ENCOUNTERS);

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

  // Care-Seeking Behavior
  
  private static final double PATIENT_WEIGHT =
      Double.parseDouble(Config.get("person.adherence.weights.patient"));
  
  private static final double SOCIOECONOMIC_WEIGHT =
      Double.parseDouble(Config.get("person.adherence.weights.socieconomic"));
  
  private static final double CONDITION_WEIGHT =
      Double.parseDouble(Config.get("person.adherence.weights.condition"));
  
  private static final double THERAPY_WEIGHT =
      Double.parseDouble(Config.get("person.adherence.weights.therapy"));
  
  private static final double PROVIDER_SYSTEM_WEIGHT =
      Double.parseDouble(Config.get("person.adherence.weights.provider_system"));
  
  /**
   * Get this person's level of adherence to the given medication, at the given time.
   * Adherence is a number between 0.0 and 1.0, where 0.0 means they never took the medication,
   * and 1.0 indicates perfect adherence to guidance.
   * 
   * @param medication Medication prescribed
   * @param time Timestamp
   * @return adherence rate between 0.0 and 1.0
   */
  public double adherenceLevel(Code medication, long time) {
    // notes: http://www.who.int/chp/knowledge/publications/adherence_report/en/
    
    // WHO finds that 
    // "Adherence to long-term therapy for chronic illnesses in developed countries averages 50%.
    // In developing countries, the rates are even lower." 
    // We aim for this function to average ~50% across the population
    
    // the WHO groups adherence factors into 5 categories:
    // patient-related factors, socioeconomic factors,
    // condition-related factors, therapy-related factors,
    // and health care team/health system factors

    // these factors can vary based on condition
    // (for instance, higher age is correlated with 
    //   better adherence for certain conditions and worse adherence in others,
    //   there are gender-based differences in various conditions, etc)
    // but for version 1 we're just going to consider them uniformly
    // and implement a weighted sum, bounded to [0, 1)
    
    // PATIENT-RELATED FACTORS
    // ex. belief in the efficacy of treatment, motivation, forgetfulness, depression or stress
    // TODO: implement some of these
    // for starters set it at 50%, to center around a baseline adherence rate of 50%
    double patientFactors = 0.5;

    // SOCIOECONOMIC FACTORS
    // ex. cost of the treatment, education level, distance from treatment location,
    // cultural beliefs about the illness/treatment, demographics

    //  - education level, this is already scaled 0-1 for socioeconomic status
    double edLevel = (double) attributes.get(EDUCATION_LEVEL);
    
    //  - income level, this is already scaled 0-1 for socioeconomic status
    double income = (double) attributes.get(INCOME_LEVEL);
    
    //  - demographics (age, race, ethnicity, gender)
    // TODO - make this numeric
    // this is likely to be "people in age range X are Y% more likely to see a doctor" etc
    double demographics = 0.0;

    // TODO: make weights at this level configurable too?
    double socioeconomicFactors = edLevel * 0.5 
                                + income * 0.5 
                                + demographics * 0.0; 
    
    // CONDITION-RELATED FACTORS
    // ex. symptom levels, or is the person asymptomatic?
    // TODO: link to the condition that this medication is being taken for
    // for starters set it at 50%, to center around a baseline adherence rate of 50%
    double conditionFactors = 0.5;
    
    // THERAPY-RELATED FACTORS
    // ex. side effects, complexity of regimen
    // TODO: link the medication to side effects -- maybe http://sideeffects.embl.de/  ?
    // for starters set it at 50%, to center around a baseline adherence rate of 50%
    double therapyFactors = 0.5;
    
    // HEALTH CARE TEAM / HEALTH SYSTEM FACTORS
    // ex. relationship between patient + physician, quality of care, 
    // TODO: make this a factor of # of visits or some other measurable criteria
    // for starters set it at 50%, to center around a baseline adherence rate of 50%
    double providerOrSystemFactors = 0.5;
    
    double adherenceSum = (patientFactors * 0.0) // TODO: NYI  -- PATIENT_WEIGHT) 
                        + (socioeconomicFactors * SOCIOECONOMIC_WEIGHT)
                        + (conditionFactors * 0.0) // TODO: NYI -- CONDITION_WEIGHT) 
                        + (therapyFactors * 0.0) // TODO: NYI -- THERAPY_WEIGHT)
                        + (providerOrSystemFactors * 0.0); // TODO: NYI -- PROVIDER_SYSTEM_WEIGHT);

    adherenceSum /= (SOCIOECONOMIC_WEIGHT); 
    // divide by the weights actually used to scale it in 0-1
    // TODO: remove this if all weights are used
    
    return adherenceSum;
  }
  
  private static final double CARESEEKING_THRESHOLD =
      Double.parseDouble(Config.get("person.careseeking.threshold"));
  
  /**
   * Whether or not the person seeks care at the given time.
   * 
   * @param emergency Is the person experiencing an emergency?
   * @param time Timestamp
   * @return whether or not the person will seek care
   */
  public boolean doesSeekCare(boolean emergency, long time) {
    // inspired by the above notes in adherenceLevel,
    // we group the relevant factors in careseeking behavior into 5 categories
    
    // for v1, if it's an emergency they will always seek care
    if (emergency) {
      return true;
    }

    // PATIENT-RELATED FACTORS
    final double patientFactors = 1.0;

    // SOCIOECONOMIC FACTORS
    double insuranceScore;
    List<String> insHistory = (List<String>) attributes.get(HealthInsuranceModule.INSURANCE);
    int age = ageInYears(time);
    String insurance = insHistory.get(age);
    if (insurance == null) {
      insurance = insHistory.get(age - 1);
    }
    
    switch (insurance) {
      case HealthInsuranceModule.PRIVATE:
        insuranceScore = 1;
        break;
      case HealthInsuranceModule.DUAL_ELIGIBLE:
      case HealthInsuranceModule.MEDICAID:
      case HealthInsuranceModule.MEDICARE:
        insuranceScore = 0.5;
        break;
      case HealthInsuranceModule.NO_INSURANCE:
        insuranceScore = -1;
        break;
      default:
        // should never happen
        throw new IllegalStateException("Unknown health insurance value: " + insurance);
    }
    
    double abilityToPay = (double) attributes.get(INCOME_LEVEL) - 0.5; // scale to -0.5 to 0.5

    Provider provider = getAmbulatoryProvider();
    double distanceToProvider = Double.MAX_VALUE;
    if (provider != null) {
      distanceToProvider = this.getLatLon().distance(provider.getLatLon());
    }
    // assume ~ 25 miles is the cutoff for acceptable distance to the provider
    // based on https://www.ofm.wa.gov/sites/default/files/public/legacy/researchbriefs/2013/brief070.pdf
    // "How Long and How Far Do Adults Travel and Will Adults Travel for Primary Care?",
    // - Wei Yen, The Health Care Research Group 
    // map distance to a score 0-1, where 1 = 0 distance, and 0 = 25 miles or more
    double distanceScore = Math.max(0, Math.min(1, 1 - distanceToProvider / 25)); // clamp to [0,1]
    
    final double socioeconomicFactors = (insuranceScore * 0.4)
                                      + (abilityToPay * 0.1)
                                      + (distanceScore * 0.5);
    
    // CONDITION-RELATED FACTORS
    // severity of symptoms, etc
    final double conditionFactors = 1.0;

    // THERAPY-RELATED FACTORS
    // for an initial visit, nothing to do here
    // but for followups and referrals we should consider various factors
    final double therapyFactors = 1.0;

    // HEALTH CARE TEAM / HEALTH SYSTEM FACTORS
    // for simplicity we roll up the concept of provider capacity into this step,
    // even though it's technically separate
    double availableProviderScore = 1.0; // TODO: provider.capacity???
    
    final double providerOrSystemFactors = (availableProviderScore * 1.0);
    
    // version 1. implement a weighted sum, 
    // and if that sum > some threshold, return true

    double careSeekingSum = (patientFactors * 0.0) // TODO: NYI  -- PATIENT_WEIGHT) 
                          + (socioeconomicFactors * SOCIOECONOMIC_WEIGHT)
                          + (conditionFactors * 0.0) // TODO: NYI -- CONDITION_WEIGHT) 
                          + (therapyFactors * 0.0) // TODO: NYI -- THERAPY_WEIGHT)
                          + (providerOrSystemFactors * 0.0); // TODO: NYI -- PROVIDER_SYSTEM_WEIGHT);
    
    careSeekingSum /= (SOCIOECONOMIC_WEIGHT); 
    // divide by the weights actually used to scale it in 0-1
    // TODO: remove this if all weights are used
    
    // TODO - could we incorporate wellness encounters into this?
    // that way we'd have these 2 concepts in 1 place instead of 2 places
    return careSeekingSum > CARESEEKING_THRESHOLD;
  }
  
  // Providers API -----------------------------------------------------------
  public static final String CURRENTPROVIDER = "currentProvider";
  public static final String PREFERREDAMBULATORYPROVIDER = "preferredAmbulatoryProvider";
  public static final String PREFERREDINPATIENTPROVIDER = "preferredInpatientProvider";
  public static final String PREFERREDEMERGENCYPROVIDER = "preferredEmergencyProvider";

  public Provider getProvider(String encounterClass) {
    switch (encounterClass) {
      case Provider.AMBULATORY:
        return this.getAmbulatoryProvider();
      case Provider.EMERGENCY:
        return this.getEmergencyProvider();
      case Provider.INPATIENT:
        return this.getInpatientProvider();
      case Provider.WELLNESS:
        return this.getAmbulatoryProvider();
      default:
        return this.getAmbulatoryProvider();
    }
  }

  public Provider getAmbulatoryProvider() {
    if (!attributes.containsKey(PREFERREDAMBULATORYPROVIDER)) {
      setAmbulatoryProvider();
    }
    return (Provider) attributes.get(PREFERREDAMBULATORYPROVIDER);
  }

  private void setAmbulatoryProvider() {
    Provider provider = Provider.findClosestService(this, Provider.AMBULATORY);
    setAmbulatoryProvider(provider);
  }

  public void setAmbulatoryProvider(Provider provider) {
    if (provider == null) {
      attributes.remove(PREFERREDAMBULATORYPROVIDER);
    } else {
      attributes.put(PREFERREDAMBULATORYPROVIDER, provider);
    }
  }

  public Provider getInpatientProvider() {
    if (!attributes.containsKey(PREFERREDINPATIENTPROVIDER)) {
      setInpatientProvider();
    }
    return (Provider) attributes.get(PREFERREDINPATIENTPROVIDER);
  }

  private void setInpatientProvider() {
    Provider provider = Provider.findClosestService(this, Provider.INPATIENT);
    setInpatientProvider(provider);
  }

  public void setInpatientProvider(Provider provider) {
    if (provider == null) {
      attributes.remove(PREFERREDINPATIENTPROVIDER);
    } else {
      attributes.put(PREFERREDINPATIENTPROVIDER, provider);
    }
  }

  public Provider getEmergencyProvider() {
    if (!attributes.containsKey(PREFERREDEMERGENCYPROVIDER)) {
      setEmergencyProvider();
    }
    return (Provider) attributes.get(PREFERREDEMERGENCYPROVIDER);
  }

  private void setEmergencyProvider() {
    Provider provider = Provider.findClosestService(this, Provider.EMERGENCY);
    setEmergencyProvider(provider);
  }

  public void setEmergencyProvider(Provider provider) {
    if (provider == null) {
      attributes.remove(PREFERREDEMERGENCYPROVIDER);
    } else {
      attributes.put(PREFERREDEMERGENCYPROVIDER, provider);
    }
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
   * @see org.apache.sis.index.tree.QuadTreeData#getX()
   */
  @Override
  public double getX() {
    return getLatLon().getX();
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getY()
   */
  @Override
  public double getY() {
    return getLatLon().getY();
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getLatLon()
   */
  @Override
  public DirectPosition2D getLatLon() {
    return (DirectPosition2D) attributes.get(Person.COORDINATE);
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getFileName()
   */
  @Override
  public String getFileName() {
    return null;
  }
}
