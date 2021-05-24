package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

/**
 * HealthRecord contains all the coded entries in a person's health record. This
 * class represents a logical health record. Exporters will convert this health
 * record into various standardized formats.
 */
public class HealthRecord implements Serializable {

  public static final String ENCOUNTERS = "encounters";
  public static final String PROCEDURES = "procedures";
  public static final String MEDICATIONS = "medications";
  public static final String IMMUNIZATIONS = "immunizations";

  /**
   * HealthRecord.Code represents a system, code, and display value.
   */
  public static class Code implements Comparable<Code>, Serializable {
    /** Code System (e.g. LOINC, RxNorm, SNOMED) identifier (typically a URI) */
    public String system;
    /** The code itself. */
    public String code;
    /** The human-readable description of the code. */
    public String display;
    /**
     * A ValueSet URI that defines a set of possible codes, one of which should be selected at
     * random.
     */
    public String valueSet;

    /**
     * Create a new code.
     *
     * @param system  the URI identifier of the code system
     * @param code    the code itself
     * @param display human-readable description of the coe
     */
    public Code(String system, String code, String display) {
      this.system = system;
      this.code = code;
      this.display = display;
    }

    /**
     * Create a new code from JSON.
     *
     * @param definition JSON object that contains 'system', 'code', and 'display'
     *                   attributes.
     */
    public Code(JsonObject definition) {
      this.system = definition.get("system").getAsString();
      this.code = definition.get("code").getAsString();
      this.display = definition.get("display").getAsString();
    }

    public boolean equals(Code other) {
      return this.system.equals(other.system) && this.code.equals(other.code);
    }

    public String toString() {
      return String
          .format("system=%s, code=%s, display=%s, valueSet=%s", system, code, display, valueSet);
    }

    /**
     * Parse a JSON array of codes.
     * @param jsonCodes the codes.
     * @return a list of Code objects.
     */
    public static List<Code> fromJson(JsonArray jsonCodes) {
      List<Code> codes = new ArrayList<>();
      jsonCodes.forEach(item -> {
        codes.add(new Code((JsonObject) item));
      });
      return codes;
    }

    @Override
    public int compareTo(Code other) {
      int compare = this.system.compareTo(other.system);
      if (compare == 0) {
        compare = this.code.compareTo(other.code);
      }
      return compare;
    }
  }

  /**
   * All things within a HealthRecord are instances of Entry. For example,
   * Observations, Reports, Medications, etc. All Entries have a name, start and
   * stop times, a type, and a list of associated codes.
   */
  public class Entry implements Serializable {
    /** reference to the HealthRecord this entry belongs to. */
    HealthRecord record = HealthRecord.this;
    public String fullUrl;
    public String name;
    public long start;
    public long stop;
    public String type;
    public List<Code> codes;
    private BigDecimal cost;

    /**
     * Constructor for Entry.
     */
    public Entry(long start, String type) {
      this.start = start;
      this.type = type;
      this.codes = new ArrayList<Code>();
    }

    /**
     * Determines the cost of the entry based on type and location adjustment factors.
     */
    void determineCost() {
      this.cost = BigDecimal.valueOf(Costs.determineCostOfEntry(this, this.record.person));
      this.cost = this.cost.setScale(2, RoundingMode.DOWN); // truncate to 2 decimal places
    }

    /**
     * Returns the base cost of the entry.
     */
    public BigDecimal getCost() {
      if ((this.cost == null)) {
        this.determineCost();
      }
      return this.cost;
    }

    /**
     * Determines if the given entry contains the provided code in its list of codes.
     * @param code clinical term
     * @param system system for the code
     * @return true if the code is there
     */
    public boolean containsCode(String code, String system) {
      return this.codes.stream().anyMatch(c -> code.equals(c.code) && system.equals(c.system));
    }

    /**
     * Converts the entry to a String.
     */
    @Override
    public String toString() {
      return String.format("%s %s", Instant.ofEpochMilli(start).toString(), type);
    }
  }

  public class Observation extends Entry {
    public Object value;
    public String category;
    public String unit;
    public List<Observation> observations;
    public Report report;

    /**
     * Constructor for Observation HealthRecord Entry.
     */
    public Observation(long time, String type, Object value) {
      super(time, type);
      this.value = value;
      this.observations = new ArrayList<Observation>();
    }
  }

  public class Report extends Entry {
    public List<Observation> observations;

    /**
     * Constructor for Report HealthRecord Entry.
     */
    public Report(long time, String type, List<Observation> observations) {
      super(time, type);
      this.observations = observations;
    }
  }

  public class Medication extends Entry {
    public List<Code> reasons;
    public Code stopReason;
    public transient JsonObject prescriptionDetails;
    public Claim claim;
    public boolean administration;
    public boolean chronic;

    /**
     * Constructor for Medication HealthRecord Entry.
     */
    public Medication(long time, String type) {
      super(time, type);
      this.reasons = new ArrayList<Code>();
      // Create a medication claim.
      this.claim = new Claim(this, person);
    }
    
    /**
     * Java Serialization support for the prescriptionDetails field.
     * @param oos stream to write to
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
      oos.defaultWriteObject();
      if (prescriptionDetails != null) {
        oos.writeObject(prescriptionDetails.toString());
      } else {
        oos.writeObject(null);
      }
    }
    
    /**
     * Java Serialization support for the prescriptionDetails field.
     * @param ois stream to read from
     */
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
      ois.defaultReadObject();
      String prescriptionJson = (String) ois.readObject();
      if (prescriptionJson != null) {
        Gson gson = Utilities.getGson();
        this.prescriptionDetails = gson.fromJson(prescriptionJson, JsonObject.class);
      }
    }
  }

  public class Immunization extends Entry {
    public int series = -1;

    /**
     * Constructor for Immunization HealthRecord Entry.
     */
    public Immunization(long start, String type) {
      super(start, type);
    }
  }

  public class Procedure extends Entry {
    public List<Code> reasons;
    public Provider provider;
    public Clinician clinician;

    /**
     * Constructor for Procedure HealthRecord Entry.
     */
    public Procedure(long time, String type) {
      super(time, type);
      this.reasons = new ArrayList<Code>();
      this.stop = this.start + TimeUnit.MINUTES.toMillis(15);
    }
  }

  public class CarePlan extends Entry {
    public Set<Code> activities;
    public List<Code> reasons;
    public transient Set<JsonObject> goals;
    public Code stopReason;

    /**
     * Constructor for CarePlan HealthRecord Entry.
     */
    public CarePlan(long time, String type) {
      super(time, type);
      this.activities = new LinkedHashSet<Code>();
      this.reasons = new ArrayList<Code>();
      this.goals = new LinkedHashSet<JsonObject>();
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
      oos.defaultWriteObject();
      ArrayList<String> stringifiedGoals = new ArrayList<>(this.goals.size());
      for (JsonObject o: goals) {
        stringifiedGoals.add(o.toString());
      }
      oos.writeObject(stringifiedGoals);
    }
    
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
      ois.defaultReadObject();
      ArrayList<String> stringifiedGoals = (ArrayList<String>) ois.readObject();
      Gson gson = Utilities.getGson();
      this.goals = new LinkedHashSet<JsonObject>();
      for (String stringifiedGoal: stringifiedGoals) {
        goals.add(gson.fromJson(stringifiedGoal, JsonObject.class));
      }
    }
  }

  public class ImagingStudy extends Entry {
    public String dicomUid;
    public List<Series> series;

    /**
     * Constructor for ImagingStudy HealthRecord Entry.
     */
    public ImagingStudy(Person person, long time, String type) {
      super(time, type);
      this.dicomUid = Utilities.randomDicomUid(person, time, 0, 0);
      this.series = new ArrayList<Series>();
    }

    /**
     * ImagingStudy.Series represents a series of images that were taken of a
     * specific part of the body.
     */
    public class Series implements Cloneable, Serializable {
      /** A randomly assigned DICOM UID. */
      public String dicomUid;
      /** A SNOMED-CT body structures code. */
      public Code bodySite;
      /**
       * A DICOM acquisition modality code.
       *
       * @see <a href="https://www.hl7.org/fhir/valueset-dicom-cid29.html">DICOM
       *      modality codes</a>
       */
      public Code modality;
      /** One or more imaging Instances that belong to this Series. */
      public List<Instance> instances;
      /** Minimum and maximum number of instances in this series.
       * Actual number is picked uniformly randomly from this range, copying instance data from
       * the first instance provided. */
      public int minNumberInstances = 0;
      public int maxNumberInstances = 0;

      @Override
      public Series clone() {
        Series clone = new Series();
        clone.dicomUid = dicomUid;
        clone.bodySite = bodySite;
        clone.modality = modality;
        clone.instances = instances;
        clone.minNumberInstances = minNumberInstances;
        clone.maxNumberInstances = maxNumberInstances;
        return clone;
      }
    }

    /**
     * ImagingStudy.Instance represents a single imaging Instance taken as part of a
     * Series of images.
     */
    public class Instance implements Cloneable, Serializable {
      /** A randomly assigned DICOM UID. */
      public String dicomUid;
      /** A title for this image. */
      public String title;
      /**
       * A DICOM Service-Object Pair (SOP) class.
       *
       * @see <a href="https://www.dicomlibrary.com/dicom/sop/">DICOM SOP codes</a>
       */
      public Code sopClass;

      @Override
      public Instance clone() {
        Instance clone = new Instance();
        clone.dicomUid = dicomUid;
        clone.title = title;
        clone.sopClass = sopClass;
        return clone;
      }
    }
  }

  /**
   * Device is an implantable device such as a coronary stent, artificial knee
   * or hip, heart pacemaker, or implantable defibrillator.
   */
  public class Device extends Entry {
    public String manufacturer;
    public String model;
    /** UDI == Unique Device Identifier. */
    public String udi;
    public long manufactureTime;
    public long expirationTime;
    public String deviceIdentifier;
    public String lotNumber;
    public String serialNumber;

    public Device(long start, String type) {
      super(start, type);
    }

    /**
     * Set the human readable form of the UDI for this Person's device.
     * @param random the random number generator
     */
    public void generateUDI(RandomNumberGenerator random) {
      deviceIdentifier = trimLong(random.randLong(), 14);
      manufactureTime = start - Utilities.convertTime("weeks", 3);
      expirationTime = start + Utilities.convertTime("years", 25);
      lotNumber = trimLong(random.randLong(), (int) random.rand(4, 20));
      serialNumber = trimLong(random.randLong(), (int) random.rand(4, 20));

      udi = "(01)" + deviceIdentifier;
      udi += "(11)" + udiDate(manufactureTime);
      udi += "(17)" + udiDate(expirationTime);
      udi += "(10)" + lotNumber;
      udi += "(21)" + serialNumber;
    }

    private String udiDate(long time) {
      SimpleDateFormat format = new SimpleDateFormat("YYMMdd");
      return format.format(new Date(time));
    }

    private String trimLong(Long value, int length) {
      String retVal = Long.toString(value);
      if (retVal.startsWith("-")) {
        retVal = retVal.substring(1);
      }
      if (retVal.length() > length) {
        retVal = retVal.substring(0, length);
      }
      return retVal;
    }
  }

  public class Supply extends Entry {
    public Supply(long start, String type) {
      super(start, type);
    }

    public int quantity;
  }

  public enum EncounterType {
    WELLNESS("AMB"), AMBULATORY("AMB"), OUTPATIENT("AMB"),
        INPATIENT("IMP"), EMERGENCY("EMER"), URGENTCARE("AMB");

    // http://www.hl7.org/implement/standards/fhir/v3/ActEncounterCode/vs.html
    private final String code;

    EncounterType(String code) {
      this.code = code;
    }

    /**
     * Convert the given string into an EncounterType.
     *
     * @param value the string to convert.
     */
    public static EncounterType fromString(String value) {
      if (value == null) {
        return EncounterType.AMBULATORY;
      } else if (value.equals("super")) {
        return EncounterType.INPATIENT;
      } else {
        return EncounterType.valueOf(value.toUpperCase());
      }
    }

    public String code() {
      return this.code;
    }

    /**
     * Convert this EncounterType into a string.
     */
    @Override
    public String toString() {
      return this.name().toLowerCase();
    }
  }

  public class Encounter extends Entry {
    public List<Observation> observations;
    public List<Report> reports;
    public List<Entry> conditions;
    public List<Allergy> allergies;
    public List<Procedure> procedures;
    public List<Immunization> immunizations;
    public List<Medication> medications;
    public List<CarePlan> careplans;
    public List<ImagingStudy> imagingStudies;
    public List<Device> devices;
    public List<Supply> supplies;
    public Claim claim; // for now assume 1 claim per encounter
    public Code reason;
    public Code discharge;
    public Provider provider;
    public Clinician clinician;
    public boolean ended;
    // Track if we renewed meds at this encounter. Used in State.java encounter state.
    public boolean chronicMedsRenewed;
    public String clinicalNote;

    /**
     * Construct an encounter.
     * @param time the time of the encounter.
     * @param type the type of the encounter.
     */
    public Encounter(long time, String type) {
      super(time, type);
      if (type.equalsIgnoreCase(EncounterType.EMERGENCY.toString())) {
        // Emergency encounters should take at least an hour.
        this.stop = this.start + TimeUnit.MINUTES.toMillis(60);
      } else if (type.equalsIgnoreCase(EncounterType.INPATIENT.toString())) {
        // Inpatient encounters should last at least a day (1440 minutes).
        this.stop = this.start + TimeUnit.MINUTES.toMillis(1440);
      } else {
        // Other encounters will default to 15 minutes.
        this.stop = this.start + TimeUnit.MINUTES.toMillis(15);
      }
      ended = false;
      chronicMedsRenewed = false;
      observations = new ArrayList<Observation>();
      reports = new ArrayList<Report>();
      conditions = new ArrayList<Entry>();
      allergies = new ArrayList<Allergy>();
      procedures = new ArrayList<Procedure>();
      immunizations = new ArrayList<Immunization>();
      medications = new ArrayList<Medication>();
      careplans = new ArrayList<CarePlan>();
      imagingStudies = new ArrayList<ImagingStudy>();
      devices = new ArrayList<Device>();
      supplies = new ArrayList<Supply>();
      this.claim = new Claim(this, person);
    }

    /**
     * Add an observation to the encounter. In this case, no codes are added to the observation.
     * It appears that some code in Synthea likes it this way (and does not like good old OO-style
     * encapsulation).
     * @param time The time of the observation
     * @param type The type of the observation
     * @param value The observation value
     * @return The newly created observation.
     */
    public Observation addObservation(long time, String type, Object value) {
      Observation observation = new Observation(time, type, value);
      this.observations.add(observation);
      return observation;
    }

    /**
     * Add an observation to the encounter and uses the type to set the first code.
     * @param time The time of the observation
     * @param type The LOINC code for the observation
     * @param value The observation value
     * @param display The display text for the first code
     * @return The newly created observation.
     */
    public Observation addObservation(long time, String type, Object value, String display) {
      Observation observation = new Observation(time, type, value);
      this.observations.add(observation);
      observation.codes.add(new Code("LOINC", type, display));
      return observation;
    }

    /**
     * Find the first observation in the encounter with the given LOINC code.
     * @param code The LOINC code to look for
     * @return A single observation or null
     */
    public Observation findObservation(String code) {
      return observations
          .stream()
          .filter(o -> o.type.equals(code))
          .findFirst()
          .orElse(null);
    }

    /**
     * Find the encounter that happened before this one.
     * @return The previous encounter or null if this is the first
     */
    public Encounter previousEncounter() {
      if (record.encounters.size() < 2) {
        return null;
      } else {
        int index = record.encounters.indexOf(this);
        if (index == 0) {
          return null;
        } else {
          return record.encounters.get(index - 1);
        }
      }
    }
  }

  public enum ReactionSeverity {
    SEVERE("24484000", "Severe"),
    MODERATE("6736007", "Moderate"),
    MILD("255604002", "Mild");

    public String code;
    public String display;

    ReactionSeverity(String code, String display) {
      this.code = code;
      this.display = display;
    }
  }

  public class Allergy extends Entry {
    public String allergyType;
    public String category;
    public HashMap<Code, ReactionSeverity> reactions;

    /**
     * Constructor for Entry.
     *
     * @param start Time when the allergy starts
     * @param type Substance that the person is allergic or intolerant to
     */
    public Allergy(long start, String type) {
      super(start, type);
    }
  }
  
  private Person person;
  public Provider provider;
  public List<Encounter> encounters;
  public Map<String, Entry> present;
  /** recorded death date/time. */
  public Long death;

  /**
   * Construct a health record for the supplied person.
   * @param person the person.
   */
  public HealthRecord(Person person) {
    this.person = person;
    encounters = new ArrayList<Encounter>();
    present = new HashMap<String, Entry>();
  }

  /**
   * Returns the number of providers associated with this healthrecord.
   */
  public int providerCount() {
    List<String> uuids = new ArrayList<String>();
    for (Encounter enc : encounters) {
      if (enc.provider != null) {
        uuids.add(enc.provider.uuid);
      }
    }
    Set<String> uniqueUuids = new HashSet<String>(uuids);
    return uniqueUuids.size();
  }

  /**
   * Create a text summary of the health record containing counts of each time of entry.
   * @return text summary.
   */
  public String textSummary() {
    int observations = 0;
    int reports = 0;
    int conditions = 0;
    int allergies = 0;
    int procedures = 0;
    int immunizations = 0;
    int medications = 0;
    int careplans = 0;
    int imagingStudies = 0;
    for (Encounter enc : encounters) {
      observations += enc.observations.size();
      reports += enc.reports.size();
      conditions += enc.conditions.size();
      allergies += enc.allergies.size();
      procedures += enc.procedures.size();
      immunizations += enc.immunizations.size();
      medications += enc.medications.size();
      careplans += enc.careplans.size();
      imagingStudies += enc.imagingStudies.size();
    }
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Encounters:      %d\n", encounters.size()));
    sb.append(String.format("Observations:    %d\n", observations));
    sb.append(String.format("Reports:         %d\n", reports));
    sb.append(String.format("Conditions:      %d\n", conditions));
    sb.append(String.format("Allergies:       %d\n", allergies));
    sb.append(String.format("Procedures:      %d\n", procedures));
    sb.append(String.format("Immunizations:   %d\n", immunizations));
    sb.append(String.format("Medications:     %d\n", medications));
    sb.append(String.format("Care Plans:      %d\n", careplans));
    sb.append(String.format("Imaging Studies: %d\n", imagingStudies));
    return sb.toString();
  }

  /**
   * Get the latest encounter or, if none exists, create a new wellness encounter.
   * @param time the time of the encounter if a new one is created.
   * @return the latest encounter (possibly newly created).
   */
  public Encounter currentEncounter(long time) {
    Encounter encounter = null;
    if (encounters.size() >= 1) {
      encounter = encounters.get(encounters.size() - 1);
    } else {
      encounter = new Encounter(time, EncounterType.WELLNESS.toString());
      encounter.name = "First Wellness";
      encounters.add(encounter);
    }
    return encounter;
  }

  /**
   * Return the time between the supplied time and the time of the last wellness encounter.
   * If there are no wellness encounter return Long.MAX_VALUE.
   * @param time the time to measure from
   * @return the time difference, negative if time is before the first wellness encounter).
   */
  public long timeSinceLastWellnessEncounter(long time) {
    for (int i = encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = encounters.get(i);
      if (encounter.type.equals(EncounterType.WELLNESS.toString())) {
        return (time - encounter.start);
      }
    }
    return Long.MAX_VALUE;
  }

  /**
   * Add an observation with the supplied properties to the current encounter.
   * @param time time of the observation.
   * @param type type of the observation.
   * @param value value of the observation.
   * @return the new observation.
   */
  public Observation observation(long time, String type, Object value) {
    return currentEncounter(time).addObservation(time, type, value);
  }

  /**
   * Add a new observation for the specified time and type, move the specified number of
   * observations (in reverse order) from the encounter to sub observations of the new
   * observation. If the encounter does not have the specified number of observations then
   * none are moved and a new empty observation results.
   * @param time time of the new observation.
   * @param type type of the new observation.
   * @param numberOfObservations the number of observations to move from the encounter to the
   *     new observation.
   * @return the new observation.
   */
  public Observation multiObservation(long time, String type, int numberOfObservations) {
    Observation observation = new Observation(time, type, null);
    Encounter encounter = currentEncounter(time);
    int count = numberOfObservations;
    if (encounter.observations.size() >= numberOfObservations) {
      while (count > 0) {
        observation.observations.add(encounter.observations.remove(
            encounter.observations.size() - 1));
        count--;
      }
    }
    encounter.observations.add(observation);
    return observation;
  }

  /**
   * Get the latest observation of the specified type or null if none exists.
   * @param type the type of observation.
   * @return the latest observation or null if none exists.
   */
  public Observation getLatestObservation(String type) {
    for (int i = encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = encounters.get(i);
      Observation obs = encounter.findObservation(type);
      if (obs != null) {
        return obs;
      }
    }
    return null;
  }

  /**
   * Return an existing Entry for the specified code or create a new Entry if none exists.
   * @param time the time of the new entry if one is created.
   * @param primaryCode the type of the entry.
   * @return the entry (existing or new).
   */
  public Entry conditionStart(long time, String primaryCode) {
    if (!present.containsKey(primaryCode)) {
      Entry condition = new Entry(time, primaryCode);
      Encounter encounter = currentEncounter(time);
      encounter.conditions.add(condition);
      encounter.claim.addLineItem(condition);
      present.put(primaryCode, condition);
    }
    return present.get(primaryCode);
  }

  /**
   * End an existing condition if one exists.
   * @param time the end time of the condition.
   * @param primaryCode the type of the condition to search for.
   */
  public void conditionEnd(long time, String primaryCode) {
    if (present.containsKey(primaryCode)) {
      present.get(primaryCode).stop = time;
      present.remove(primaryCode);
    }
  }

  /**
   * End an existing condition with the supplied state if one exists.
   * @param time the end time of the condition.
   * @param stateName the state to search for.
   */
  public void conditionEndByState(long time, String stateName) {
    Entry condition = null;
    Iterator<Entry> iter = present.values().iterator();
    while (iter.hasNext()) {
      Entry e = iter.next();
      if (e.name != null && e.name.equals(stateName)) {
        condition = e;
        break;
      }
    }
    if (condition != null) {
      condition.stop = time;
      present.remove(condition.type);
    }
  }

  /**
   * Check whether the specified condition type is currently active (end is not specified).
   * @param type the type of the condition.
   * @return true of the condition exists and does not have a specified stop time, false
   *     otherwise.
   */
  public boolean conditionActive(String type) {
    return present.containsKey(type) && present.get(type).stop == 0L;
  }

  /**
   * Return the current allergy of the specified type or create a new one if none exists.
   * @param time the start time of the new allergy if one is created.
   * @param primaryCode the type of allergy.
   * @return the existing or new allergy entry.
   */
  public Allergy allergyStart(long time, String primaryCode) {
    if (!present.containsKey(primaryCode)) {
      Allergy allergy = new Allergy(time, primaryCode);
      currentEncounter(time).allergies.add(allergy);
      present.put(primaryCode, allergy);
    }
    return (Allergy) present.get(primaryCode);
  }

  /**
   * End the current allergy of the specified type if one exists.
   * @param time end time of the allergy.
   * @param primaryCode type of the allergy.
   */
  public void allergyEnd(long time, String primaryCode) {
    if (present.containsKey(primaryCode)) {
      present.get(primaryCode).stop = time;
      present.remove(primaryCode);
    }
  }

  /**
   * End an existing allergy with the supplied state if one exists.
   * @param time the end time of the allergy.
   * @param stateName the state to search for.
   */
  public void allergyEndByState(long time, String stateName) {
    Entry allergy = null;
    Iterator<Entry> iter = present.values().iterator();
    while (iter.hasNext()) {
      Entry e = iter.next();
      if (e.name != null && e.name.equals(stateName)) {
        allergy = e;
        break;
      }
    }
    if (allergy != null) {
      allergy.stop = time;
      present.remove(allergy.type);
    }
  }

  /**
   * Checks whether the specified allergy is active.
   * Note that this functionality already exited in the conditionActive method, but adding
   * this method makes the intention of the caller more clear. This is method simply calls
   * conditionActive and returns the result.
   *
   * @param type The type of allergy to look for
   * @return true if there is an active allergy for the type
   */
  public boolean allergyActive(String type) {
    return conditionActive(type);
  }

  /**
   * Create a new procedure of the specified type.
   * @param time the time of the procedure.
   * @param type the type of the procedure.
   * @return the new procedure.
   */
  public Procedure procedure(long time, String type) {
    Procedure procedure = new Procedure(time, type);
    Encounter encounter = currentEncounter(time);
    encounter.procedures.add(procedure);
    encounter.claim.addLineItem(procedure);
    present.put(type, procedure);
    return procedure;
  }

  /**
   * Implant or assign a device to this patient.
   * @param time The time the device is implanted or assigned.
   * @param type The type of device.
   * @return The device entry.
   */
  public Device deviceImplant(long time, String type) {
    Device device = new Device(time, type);
    device.generateUDI(person);
    Encounter encounter = currentEncounter(time);
    encounter.devices.add(device);
    present.put(type, device);
    return device;
  }

  /**
   * Remove a device from the patient.
   * @param time The time the device is removed.
   * @param type The type of device.
   */
  public void deviceRemove(long time, String type) {
    if (present.containsKey(type)) {
      present.get(type).stop = time;
      present.remove(type);
    }
  }
  
  /**
   * Remove a device from the patient based on the state where it was assigned.
   * @param time The time the device is removed.
   * @param stateName The state where the device was implanted or assigned.
   */
  public void deviceRemoveByState(long time, String stateName) {
    Device device = null;
    Iterator<Entry> iter = present.values().iterator();
    while (iter.hasNext()) {
      Entry e = iter.next();
      if (e.name != null && e.name.equals(stateName)) {
        device = (Device)e;
        break;
      }
    }
    if (device != null) {
      device.stop = time;
      present.remove(device.type);
    }
  }

  /**
   * Track the use of a supply in the provision of care for this patient.
   * @param time Time the supply was used
   * @param code SNOMED Code to identify the supply
   * @param quantity Number of this supply used
   * @return the new Supply entry
   */
  public Supply useSupply(long time, Code code, int quantity) {
    Encounter encounter = currentEncounter(time);
    Supply supply = new Supply(time, code.display);
    supply.codes.add(code);
    supply.quantity = quantity;
    encounter.supplies.add(supply);
    return supply;
  }

  /**
   * Add a new report for the specified time and type, copy the specified number of
   * observations (in reverse order) from the encounter to the new
   * report. If the encounter does not have the specified number of observations then
   * all of the encounter observations are copied to the report.
   * @param time time of the new report.
   * @param type type of the new report.
   * @param numberOfObservations the number of observations to copy from the encounter to the
   *     new report.
   * @return the new report.
   */
  public Report report(long time, String type, int numberOfObservations) {
    Encounter encounter = currentEncounter(time);
    List<Observation> observations = new ArrayList<Observation>();
    if (encounter.observations.size() > numberOfObservations) {
      int fromIndex = encounter.observations.size() - numberOfObservations;
      int toIndex = encounter.observations.size();
      observations.addAll(encounter.observations.subList(fromIndex, toIndex));
    } else {
      observations.addAll(encounter.observations);
    }
    Report report = new Report(time, type, observations);
    encounter.reports.add(report);
    observations.forEach(o -> o.report = report);
    return report;
  }

  /**
   * Starts an encounter of the given type at the given time.
   *
   * @param time the start time of the encounter.
   * @param type the type of the encounter.
   * @return
   */
  public Encounter encounterStart(long time, EncounterType type) {
    Encounter encounter = new Encounter(time, type.toString());
    encounters.add(encounter);
    return encounter;
  }

  /**
   * Ends an encounter.
   *
   * @param time the end time of the encounter.
   * @param type the type of the encounter.
   */
  public void encounterEnd(long time, EncounterType type) {

    for (int i = encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = encounters.get(i);
      EncounterType encounterType = EncounterType.fromString(encounter.type);
      if (encounterType == type && !encounter.ended) {
        encounter.ended = true;
        // Only override the stop time if it is longer than the default.
        if (time > encounter.stop) {
          encounter.stop = time;
        }
        // Update Costs/Claim infomation.
        encounter.determineCost();
        encounter.claim.assignCosts();
        return;
      }
    }
  }

  /**
   * Create a new immunization and add it to the current encounter.
   * @param time the time of the immunization.
   * @param type the type of the immunization.
   * @return the new immunization.
   */
  public Immunization immunization(long time, String type) {
    Immunization immunization = new Immunization(time, type);
    Encounter encounter = currentEncounter(time);
    encounter.immunizations.add(immunization);
    encounter.claim.addLineItem(immunization);
    return immunization;
  }

  /**
   * Get an existing medication of the specified type or create one if none exists. If chronic
   * is true the medication will be added to the list of chronic medications whether it already
   * exists or is created.
   * @param time the time of the medication if a new one is created.
   * @param type the type of the medication to find or create.
   * @param chronic whether the medication is chronic.
   * @return existing or new medication of the specified type.
   */
  public Medication medicationStart(long time, String type, boolean chronic) {
    Medication medication;
    if (!present.containsKey(type)) {
      medication = new Medication(time, type);
      medication.chronic = chronic;
      currentEncounter(time).medications.add(medication);
      present.put(type, medication);
    } else {
      medication = (Medication) present.get(type);
    }

    // Add Chronic Medications to Map
    if (chronic) {
      person.chronicMedications.put(type, medication);
    }

    return medication;
  }

  /**
   * End a current medication of the specified type if one exists.
   * @param time the end time of the medication.
   * @param type the type of the medication.
   * @param reason the reason for ending the medication.
   */
  public void medicationEnd(long time, String type, Code reason) {
    if (present.containsKey(type)) {
      Medication medication = (Medication) present.get(type);
      medication.stop = time;
      medication.stopReason = reason;

      chronicMedicationEnd(type);

      // Update Costs/Claim infomation.
      medication.determineCost();
      medication.claim.assignCosts();
      present.remove(type);
    }
  }

  /**
   * End an existing medication with the supplied state if one exists.
   * @param time the end time of the medication.
   * @param stateName the state to search for.
   * @param reason the reason for ending the medication.
   */
  public void medicationEndByState(long time, String stateName, Code reason) {
    Medication medication = null;
    Iterator<Entry> iter = present.values().iterator();
    while (iter.hasNext()) {
      Entry e = iter.next();
      if (e.name != null && e.name.equals(stateName)) {
        medication = (Medication) e;
        break;
      }
    }
    if (medication != null) {
      medication.stop = time;
      medication.stopReason = reason;
      chronicMedicationEnd(medication.type);
      present.remove(medication.type);
    }
  }

  /**
   * Remove Chronic Medication if stopped medication is a Chronic Medication.
   *
   * @param type Primary code (RxNorm) for the medication.
   */
  private void chronicMedicationEnd(String type) {
    if (person.chronicMedications.containsKey(type)) {
      person.chronicMedications.remove(type);
    }
  }

  public boolean medicationActive(String type) {
    return present.containsKey(type) && ((Medication) present.get(type)).stop == 0L;
  }

  /**
   * Get the current care plan of the specified type or create a new one if none found.
   * @param time the start time of the care plan if one is created.
   * @param type the type of the care plan.
   * @return existing or new care plan.
   */
  public CarePlan careplanStart(long time, String type) {
    CarePlan careplan;
    if (!present.containsKey(type)) {
      careplan = new CarePlan(time, type);
      currentEncounter(time).careplans.add(careplan);
      present.put(type, careplan);
    } else {
      careplan = (CarePlan) present.get(type);
    }
    return careplan;
  }

  /**
   * End the current care plan of the specified type if one exists.
   * @param time the end time of the care plan.
   * @param type the type of the care plan.
   * @param reason the reason for ending the care plan.
   */
  public void careplanEnd(long time, String type, Code reason) {
    if (present.containsKey(type)) {
      CarePlan careplan = (CarePlan) present.get(type);
      careplan.stop = time;
      careplan.stopReason = reason;
      present.remove(type);
    }
  }

  /**
   * End an existing care plan with the supplied state if one exists.
   * @param time the end time of the care plan.
   * @param stateName the state to search for.
   * @param reason the reason for ending the care plan.
   */
  public void careplanEndByState(long time, String stateName, Code reason) {
    CarePlan careplan = null;
    Iterator<Entry> iter = present.values().iterator();
    while (iter.hasNext()) {
      Entry e = iter.next();
      if (e.name != null && e.name.equals(stateName)) {
        careplan = (CarePlan) e;
        break;
      }
    }
    if (careplan != null) {
      careplan.stop = time;
      careplan.stopReason = reason;
      present.remove(careplan.type);
    }
  }

  public boolean careplanActive(String type) {
    return present.containsKey(type) && ((CarePlan) present.get(type)).stop == 0L;
  }

  /**
   * Create a new imaging study.
   * @param time the time of the study.
   * @param type the type of the study.
   * @param series the series associated with the study.
   * @return 
   */
  public ImagingStudy imagingStudy(long time, String type,
      List<ImagingStudy.Series> series) {
    ImagingStudy study = new ImagingStudy(this.person, time, type);
    study.series = series;
    assignImagingStudyDicomUids(time, study);
    currentEncounter(time).imagingStudies.add(study);
    return study;
  }

  /**
   * Assigns random DICOM UIDs to each Series and Instance in an imaging study
   * after creation.
   * @param time the time of the study.
   * @param study the ImagingStudy to populate with DICOM UIDs.
   */
  private void assignImagingStudyDicomUids(long time, ImagingStudy study) {

    int seriesNo = 1;
    for (ImagingStudy.Series series : study.series) {
      series.dicomUid = Utilities.randomDicomUid(this.person, time, seriesNo, 0);

      int instanceNo = 1;
      for (ImagingStudy.Instance instance : series.instances) {
        instance.dicomUid = Utilities.randomDicomUid(this.person, time, seriesNo, instanceNo);
        instanceNo += 1;
      }
      seriesNo += 1;
    }
  }
}
