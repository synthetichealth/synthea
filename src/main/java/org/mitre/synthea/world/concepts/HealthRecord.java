package org.mitre.synthea.world.concepts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Provider;

/**
 * HealthRecord contains all the coded entries in a person's health record. This class represents a
 * logical health record. Exporters will convert this health record into various standardized
 * formats.
 */
public class HealthRecord {
  /**
   * HealthRecord.Code represents a system, code, and display value.
   */
  public static class Code implements Comparable<Code> {
    /** Code System (e.g. LOINC, RxNorm, SNOMED) identifier (typically a URI) */
    public String system;
    /** The code itself. */
    public String code;
    /** The human-readable description of the code. */
    public String display;

    /**
     * Create a new code.
     *
     * @param system
     *          the URI identifier of the code system
     * @param code
     *          the code itself
     * @param display
     *          human-readable description of the coe
     */
    public Code(String system, String code, String display) {
      this.system = system;
      this.code = code;
      this.display = display;
    }

    /**
     * Create a new code from JSON.
     *
     * @param definition
     *          JSON object that contains 'system', 'code', and 'display' attributes.
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
      return String.format("%s %s %s", system, code, display);
    }

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
   * All things within a HealthRecord are instances of Entry. For example, Observations, Reports,
   * Medications, etc. All Entries have a name, start and stop times, a type, and a list of
   * associated codes.
   */
  public class Entry {
    public String fullUrl;
    public String name;
    public long start;
    public long stop;
    public String type;
    public List<Code> codes;

    public Entry(long start, String type) {
      this.start = start;
      this.type = type;
      this.codes = new ArrayList<Code>();
    }

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

    public Observation(long time, String type, Object value) {
      super(time, type);
      this.value = value;
      this.observations = new ArrayList<Observation>();
    }
  }

  public class Report extends Entry {
    public List<Observation> observations;

    public Report(long time, String type, List<Observation> observations) {
      super(time, type);
      this.observations = observations;
    }
  }

  public class Medication extends Entry {
    public List<Code> reasons;
    public Code stopReason;
    public JsonObject prescriptionDetails;
    public Claim claim;

    public Medication(long time, String type) {
      super(time, type);
      this.reasons = new ArrayList<Code>();
      this.claim = new Claim(this);
    }
  }

  public class Immunization extends Entry {
    public Immunization(long start, String type) {
      super(start, type);
    }
  }

  public class Procedure extends Entry {
    public List<Code> reasons;

    public Procedure(long time, String type) {
      super(time, type);
      this.reasons = new ArrayList<Code>();
    }
  }

  public class CarePlan extends Entry {
    public Set<Code> activities;
    public List<Code> reasons;
    public Set<JsonObject> goals;
    public Code stopReason;

    public CarePlan(long time, String type) {
      super(time, type);
      this.activities = new LinkedHashSet<Code>();
      this.reasons = new ArrayList<Code>();
      this.goals = new LinkedHashSet<JsonObject>();
    }
  }

  public class ImagingStudy extends Entry {
    public String dicomUid;
    public List<Series> series;

    public ImagingStudy(long time, String type) {
      super(time, type);
      this.dicomUid = Utilities.randomDicomUid(0, 0);
      this.series = new ArrayList<Series>();
    }

    /**
     * ImagingStudy.Series represents a series of images that were taken of
     * a specific part of the body.
     */
    public class Series {
      /** A randomly assigned DICOM UID. */
      public transient String dicomUid;
      /** A SNOMED-CT body structures code. */
      public Code bodySite;
      /** A DICOM acquisition modality code.
       * @see <a href="https://www.hl7.org/fhir/valueset-dicom-cid29.html">DICOM modality codes</a>
       */
      public Code modality;
      /** One or more imaging Instances that belong to this Series. */
      public List<Instance> instances;
    }

    /**
     * ImagingStudy.Instance represents a single imaging Instance taken as
     * part of a Series of images.
     */
    public class Instance {
      /** A randomly assigned DICOM UID. */
      public transient String dicomUid;
      /** A title for this image. */
      public String title;
      /**
       * A DICOM Service-Object Pair (SOP) class.
       * @see <a href="https://www.dicomlibrary.com/dicom/sop/">DICOM SOP codes</a>
       */
      public Code sopClass;
    }
  }

  public class Claim {
    public double baseCost;
    public Encounter encounter;
    public Medication medication;
    public List<ClaimItem> items;

    public Claim(Encounter encounter) {
      // Encounter inpatient
      if (EncounterType.INPATIENT.equals(encounter.type)) {
        baseCost = 75.00;
      } else {
        // Outpatient Encounter, Encounter for 'checkup', Encounter for symptom, Encounter for
        // problem,
        // patient initiated encounter, patient encounter procedure
        baseCost = 125.00;
      }
      this.encounter = encounter;
      items = new ArrayList<>();
    }

    public Claim(Medication medication) {
      baseCost = 255.0;
      this.medication = medication;
      items = new ArrayList<>();
    }

    public void addItem(Entry entry) {
      items.add(new ClaimItem(entry, null));
    }

    public BigDecimal total() {
      BigDecimal total = BigDecimal.valueOf(baseCost);

      for (ClaimItem lineItem : items) {
        total = total.add(lineItem.cost());
      }
      return total;
    }
  }

  public class ClaimItem {
    public Entry entry;
    private BigDecimal cost;

    public ClaimItem(Entry entry, BigDecimal cost) {
      this.entry = entry;
      this.cost = cost;
    }

    public BigDecimal cost() {
      if (cost == null) {
        cost = BigDecimal.valueOf(Costs.calculateCost(entry, true));
        cost = cost.setScale(2, RoundingMode.DOWN); // truncate to 2 decimal places
      }
      return cost;
    }
  }

  public enum EncounterType {
    WELLNESS, EMERGENCY, INPATIENT, AMBULATORY;

    public boolean equals(String type) {
      return this.toString().equalsIgnoreCase(type);
    }
  }

  public class Encounter extends Entry {
    public List<Observation> observations;
    public List<Report> reports;
    public List<Entry> conditions;
    public List<Entry> allergies;
    public List<Procedure> procedures;
    public List<Entry> immunizations;
    public List<Medication> medications;
    public List<CarePlan> careplans;
    public List<ImagingStudy> imagingStudies;
    public Claim claim; // for now assume 1 claim per encounter
    public Code reason;
    public Code discharge;
    public Provider provider;

    public Encounter(long time, String type) {
      super(time, type);
      observations = new ArrayList<Observation>();
      reports = new ArrayList<Report>();
      conditions = new ArrayList<Entry>();
      allergies = new ArrayList<Entry>();
      procedures = new ArrayList<Procedure>();
      immunizations = new ArrayList<Entry>();
      medications = new ArrayList<Medication>();
      careplans = new ArrayList<CarePlan>();
      imagingStudies = new ArrayList<ImagingStudy>();
      claim = new Claim(this);
    }
  }

  public List<Encounter> encounters;
  public Map<String, Entry> present;
  /** recorded death date/time. */
  public Long death;

  public HealthRecord() {
    encounters = new ArrayList<Encounter>();
    present = new HashMap<String, Entry>();
  }

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

  public Encounter currentEncounter(long time) {
    Encounter encounter = null;
    if (encounters.size() >= 1) {
      encounter = encounters.get(encounters.size() - 1);
    } else {
      encounter = encounterStart(time, EncounterType.WELLNESS);
      encounter.name = "First Wellness";
    }
    return encounter;
  }

  public long timeSinceLastWellnessEncounter(long time) {
    for (int i = encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = encounters.get(i);
      if (EncounterType.WELLNESS.equals(encounter.type)) {
        return (time - encounter.start);
      }
    }
    return Long.MAX_VALUE;
  }

  public Observation observation(long time, String type, Object value) {
    Observation observation = new Observation(time, type, value);
    currentEncounter(time).observations.add(observation);
    return observation;
  }

  public Observation multiObservation(long time, String type, int numberOfObservations) {
    Observation observation = new Observation(time, type, null);
    Encounter encounter = currentEncounter(time);
    int count = numberOfObservations;
    if (encounter.observations.size() >= numberOfObservations) {
      while (count > 0) {
        observation.observations
            .add(encounter.observations.remove(encounter.observations.size() - 1));
        count--;
      }
    }
    encounter.observations.add(observation);
    return observation;
  }

  public Observation getLatestObservation(String type) {
    for (int i = encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = encounters.get(i);
      for (Observation observation : encounter.observations) {
        if (observation.type.equals(type)) {
          return observation;
        }
      }
    }
    return null;
  }

  public Entry conditionStart(long time, String primaryCode) {
    if (!present.containsKey(primaryCode)) {
      Entry condition = new Entry(time, primaryCode);
      Encounter encounter = currentEncounter(time);
      encounter.conditions.add(condition);
      encounter.claim.addItem(condition);
      present.put(primaryCode, condition);
    }
    return present.get(primaryCode);
  }

  public void conditionEnd(long time, String primaryCode) {
    if (present.containsKey(primaryCode)) {
      present.get(primaryCode).stop = time;
      present.remove(primaryCode);
    }
  }

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

  public boolean conditionActive(String type) {
    return present.containsKey(type) && present.get(type).stop == 0L;
  }

  public Entry allergyStart(long time, String primaryCode) {
    if (!present.containsKey(primaryCode)) {
      Entry allergy = new Entry(time, primaryCode);
      currentEncounter(time).allergies.add(allergy);
      present.put(primaryCode, allergy);
    }
    return present.get(primaryCode);
  }

  public void allergyEnd(long time, String primaryCode) {
    if (present.containsKey(primaryCode)) {
      present.get(primaryCode).stop = time;
      present.remove(primaryCode);
    }
  }

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

  public Procedure procedure(long time, String type) {
    Procedure procedure = new Procedure(time, type);
    Encounter encounter = currentEncounter(time);
    encounter.procedures.add(procedure);
    encounter.claim.addItem(procedure);
    present.put(type, procedure);
    return procedure;
  }

  public Report report(long time, String type, int numberOfObservations) {
    Encounter encounter = currentEncounter(time);
    List<Observation> observations = new ArrayList<Observation>();
    if (encounter.observations.size() > numberOfObservations) {
      int fromIndex = encounter.observations.size() - numberOfObservations - 1;
      int toIndex = encounter.observations.size() - 1;
      observations.addAll(encounter.observations.subList(fromIndex, toIndex));
    } else {
      observations.addAll(encounter.observations);
    }
    Report report = new Report(time, type, observations);
    encounter.reports.add(report);
    observations.forEach(o -> o.report = report);
    return report;
  }

  public Encounter encounterStart(long time, EncounterType type) {
    return encounterStart(time, type.toString().toLowerCase());
  }

  public Encounter encounterStart(long time, String type) {
    Encounter encounter = new Encounter(time, type);
    encounters.add(encounter);
    return encounter;
  }

  public void encounterEnd(long time, EncounterType type) {
    encounterEnd(time, type.toString().toLowerCase());
  }

  public void encounterEnd(long time, String type) {
    for (int i = encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = encounters.get(i);
      if (encounter.type.equalsIgnoreCase(type) && encounter.stop == 0L) {
        encounter.stop = time;
        return;
      }
    }
  }

  public Immunization immunization(long time, String type) {
    Immunization immunization = new Immunization(time, type);
    Encounter encounter = currentEncounter(time);
    encounter.immunizations.add(immunization);
    encounter.claim.addItem(immunization);
    return immunization;
  }

  public Medication medicationStart(long time, String type) {
    Medication medication;
    if (!present.containsKey(type)) {
      medication = new Medication(time, type);
      currentEncounter(time).medications.add(medication);
      present.put(type, medication);
    } else {
      medication = (Medication) present.get(type);
    }
    return medication;
  }

  public void medicationEnd(long time, String type, Code reason) {
    if (present.containsKey(type)) {
      Medication medication = (Medication) present.get(type);
      medication.stop = time;
      medication.stopReason = reason;
      present.remove(type);
    }
  }

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
      present.remove(medication.type);
    }
  }

  public boolean medicationActive(String type) {
    return present.containsKey(type) && ((Medication) present.get(type)).stop == 0L;
  }

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

  public void careplanEnd(long time, String type, Code reason) {
    if (present.containsKey(type)) {
      CarePlan careplan = (CarePlan) present.get(type);
      careplan.stop = time;
      careplan.stopReason = reason;
      present.remove(type);
    }
  }

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

  public ImagingStudy imagingStudy(long time, String type, List<ImagingStudy.Series> series) {
    ImagingStudy study = new ImagingStudy(time, type);
    study.series = series;
    assignImagingStudyDicomUids(study);
    currentEncounter(time).imagingStudies.add(study);
    return study;
  }

  /**
   * Assigns random DICOM UIDs to each Series and Instance in an imaging study after creation.
   * @param study the ImagingStudy to populate with DICOM UIDs.
   */
  private void assignImagingStudyDicomUids(ImagingStudy study) {

    int seriesNo = 1;
    for (ImagingStudy.Series series : study.series) {
      series.dicomUid = Utilities.randomDicomUid(seriesNo, 0);

      int instanceNo = 1;
      for (ImagingStudy.Instance instance : series.instances) {
        instance.dicomUid = Utilities.randomDicomUid(seriesNo, instanceNo);
        instanceNo += 1;
      }
      seriesNo += 1;
    }
  }
}
