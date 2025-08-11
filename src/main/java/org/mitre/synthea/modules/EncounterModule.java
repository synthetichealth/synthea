package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Module for managing patient encounters, including wellness visits,
 * symptom-driven visits, and emergency care.
 */
public final class EncounterModule extends Module {

  /** Attribute key for active wellness encounters. */
  public static final String ACTIVE_WELLNESS_ENCOUNTER = "active_wellness_encounter";

  /** Attribute key for active urgent care encounters. */
  public static final String ACTIVE_URGENT_CARE_ENCOUNTER = "active_urgent_care_encounter";

  /** Attribute key for active emergency encounters. */
  public static final String ACTIVE_EMERGENCY_ENCOUNTER = "active_emergency_encounter";

  /** Module name. */
  public static final String NAME = "Encounter";
  /**
   * These are thresholds for patients to seek symptom-driven care - they'll go to
   * the appropriate provider based on which threshold they meet.
   * By CDC statistics (https://www.cdc.gov/nchs/data/ahcd/namcs_summary/2015_namcs_web_tables.pdf),
   * a person goes to an average of
   * 24,904,00/(US adult population = 249485228) = .0998 urgent visits per year.
   * The goal for the number of symptom-driven encounters (urgent care, PCP, and ER) is .0998 * age.
   */
  public static final int PCP_SYMPTOM_THRESHOLD = 300;
  /** Threshold for symptoms to trigger an urgent care visit */
  public static final int URGENT_CARE_SYMPTOM_THRESHOLD = 350;

  /** Threshold for symptoms to trigger an emergency room visit. */
  public static final int EMERGENCY_SYMPTOM_THRESHOLD = 500;

  /** Attribute key for the last visit symptom total. */
  public static final String LAST_VISIT_SYMPTOM_TOTAL = "last_visit_symptom_total";

  /** Code for a check-up encounter. */
  public static final Code ENCOUNTER_CHECKUP = new Code("SNOMED-CT", "185349003",
      "Encounter for check up (procedure)");

  /** Code for an emergency room admission. */
  public static final Code ENCOUNTER_EMERGENCY = new Code("SNOMED-CT", "50849002",
      "Emergency room admission (procedure)");

  /** Code for a well-child visit. */
  public static final Code WELL_CHILD_VISIT = new Code("SNOMED-CT", "410620009",
      "Well child visit (procedure)");

  /** Code for a general examination. */
  public static final Code GENERAL_EXAM = new Code("SNOMED-CT", "162673000",
      "General examination of patient (procedure)");

  /** Code for an urgent care clinic visit. */
  public static final Code ENCOUNTER_URGENTCARE = new Code("SNOMED-CT", "702927004",
      "Urgent care clinic (environment)");
  // NOTE: if new codes are added, be sure to update getAllCodes below

  /**
   * Constructor for the EncounterModule.
   */
  public EncounterModule() {
    this.name = EncounterModule.NAME;
  }

  /**
   * Clone the module.
   *
   * @return a clone of the module
   */
  public Module clone() {
    return this;
  }

  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }
    if (person.hasCurrentEncounter()) {
      // Don't start a new encounter here if there is already an active encounter
      return false;
    }
    boolean startedEncounter = false;
    Encounter encounter = null;

    // add a wellness encounter if this is the right time
    if (person.record.timeSinceLastWellnessEncounter(time)
        >= recommendedTimeBetweenWellnessVisits(person, time)) {
      Code code = getWellnessVisitCode(person, time);
      encounter = createEncounter(person, time, EncounterType.WELLNESS,
          ClinicianSpecialty.GENERAL_PRACTICE, code, name);
      encounter.name = "Encounter Module Scheduled Wellness";
      person.attributes.put(ACTIVE_WELLNESS_ENCOUNTER, true);
      startedEncounter = true;
    } else if (person.symptomTotal() > EMERGENCY_SYMPTOM_THRESHOLD) {
      if (!person.attributes.containsKey(LAST_VISIT_SYMPTOM_TOTAL)) {
        person.attributes.put(LAST_VISIT_SYMPTOM_TOTAL, 0);
      }
      if (person.symptomTotal() != (int)person.attributes.get(LAST_VISIT_SYMPTOM_TOTAL)) {
        person.attributes.put(LAST_VISIT_SYMPTOM_TOTAL, person.symptomTotal());
        person.addressLargestSymptom();
        encounter = createEncounter(person, time, EncounterType.EMERGENCY,
            ClinicianSpecialty.GENERAL_PRACTICE, ENCOUNTER_EMERGENCY, name);
        encounter.name = "Encounter Module Symptom Driven";
        person.attributes.put(ACTIVE_EMERGENCY_ENCOUNTER, true);
        startedEncounter = true;
      }
    } else if (person.symptomTotal() > URGENT_CARE_SYMPTOM_THRESHOLD) {
      if (!person.attributes.containsKey(LAST_VISIT_SYMPTOM_TOTAL)) {
        person.attributes.put(LAST_VISIT_SYMPTOM_TOTAL, 0);
      }
      if (person.symptomTotal() != (int)person.attributes.get(LAST_VISIT_SYMPTOM_TOTAL)) {
        person.attributes.put(LAST_VISIT_SYMPTOM_TOTAL, person.symptomTotal());
        person.addressLargestSymptom();
        encounter = createEncounter(person, time, EncounterType.URGENTCARE,
            ClinicianSpecialty.GENERAL_PRACTICE, ENCOUNTER_URGENTCARE, name);
        encounter.name = "Encounter Module Symptom Driven";
        person.attributes.put(ACTIVE_URGENT_CARE_ENCOUNTER, true);
        startedEncounter = true;
      }
    } else if (person.symptomTotal() > PCP_SYMPTOM_THRESHOLD) {
      if (!person.attributes.containsKey(LAST_VISIT_SYMPTOM_TOTAL)) {
        person.attributes.put(LAST_VISIT_SYMPTOM_TOTAL, 0);
      }
      if (person.symptomTotal() != (int)person.attributes.get(LAST_VISIT_SYMPTOM_TOTAL)) {
        person.attributes.put(LAST_VISIT_SYMPTOM_TOTAL, person.symptomTotal());
        person.addressLargestSymptom();
        encounter = createEncounter(person, time, EncounterType.OUTPATIENT,
            ClinicianSpecialty.GENERAL_PRACTICE, ENCOUNTER_CHECKUP, name);
        encounter.name = "Encounter Module Symptom Driven";
        person.attributes.put(ACTIVE_WELLNESS_ENCOUNTER, true);
        startedEncounter = true;
      }
    }

    if (startedEncounter) {
      Immunizations.performEncounter(person, time);
    }

    // java modules will never "finish"
    return false;
  }

  /**
   * Create an Encounter that is coded, with a provider organization, and a clinician.
   * @param person The patient.
   * @param time The time of the encounter.
   * @param type The type of encounter (e.g. emergency).
   * @param specialty The clinician specialty (e.g. "General Practice")
   * @param code The code to assign to the encounter.
   * @param module The module creating this encounter.
   * @return The encounter, or null if the person is already in an active encounter
   */
  public static Encounter createEncounter(Person person, long time, EncounterType type,
      String specialty, Code code, String module) {
    // Do not create an encounter if the person is already in an active encounter.
    if (person.hasCurrentEncounter()) {
      return null;
    }

    // what year is it?
    int year = Utilities.getYear(time);

    // create the encounter
    person.reserveCurrentEncounter(time, module);
    Encounter encounter = person.encounterStart(time, type);

    if (code != null) {
      encounter.codes.add(code);
    }
    // assign a provider organization
    Provider prov = null;
    if (specialty.equalsIgnoreCase(ClinicianSpecialty.CARDIOLOGY)) {
      // Get the first provider in the list that was loaded
      prov = Provider.getProviderList().get(0);
    } else {
      prov = person.getProvider(type, time);
    }
    prov.incrementEncounters(type, year);
    encounter.provider = prov;
    // assign a clinician
    encounter.clinician = prov.chooseClinicianList(specialty, person);
    return encounter;
  }

  /**
   * Get the correct Wellness Visit Code by age of patient.
   * @param person The patient.
   * @param time The time of the encounter which we translate to age of patient.
   * @return SNOMED-CT code for Wellness Visit.
   */
  public static Code getWellnessVisitCode(Person person, long time) {
    int age = person.ageInYears(time);
    if (age < 18) {
      return WELL_CHILD_VISIT;
    } else {
      return GENERAL_EXAM;
    }
  }

  /**
   * Recommended time between Wellness Visits by age of patient and whether
   * they have chronic medications.
   * @param person The patient.
   * @param time The time of the encounter which we translate to age of patient.
   * @return Recommended time between Wellness Visits in milliseconds
   */
  public long recommendedTimeBetweenWellnessVisits(Person person, long time) {
    int ageInYears = person.ageInYears(time);
    boolean hasChronicMeds = !person.chronicMedications.isEmpty();

    long interval; // return variable

    if (ageInYears <= 3) {
      int ageInMonths = person.ageInMonths(time);
      if (ageInMonths <= 1) {
        interval = Utilities.convertTime("months", 1);
      } else if (ageInMonths <= 5) {
        interval = Utilities.convertTime("months", 2);
      } else if (ageInMonths <= 17) {
        interval = Utilities.convertTime("months", 3);
      } else {
        interval = Utilities.convertTime("months", 6);
      }
    } else if (ageInYears <= 19) {
      interval = Utilities.convertTime("years", 1);
    } else if (ageInYears <= 39) {
      interval = Utilities.convertTime("years", 3);
    } else if (ageInYears <= 49) {
      interval = Utilities.convertTime("years", 2);
    } else {
      interval = Utilities.convertTime("years", 1);
    }

    // If the patients has chronic medications, they need to see their
    // provider at least once a year to get their medications renewed.
    // TODO: In the future, we need even more frequent wellness encounters
    // by condition: e.g. Diabetes every 3 months or 6 month; but
    // Contraception every 1 year.
    if (hasChronicMeds && interval > Utilities.convertTime("years", 1)) {
      interval = Utilities.convertTime("years", 1);
    }

    return interval;
  }

  /**
   * End a wellness encounter if currently active.
   * @param person The patient.
   * @param time The time of the encounter end.
   */
  public void endEncounterModuleEncounters(Person person, long time) {
    if (person.hasCurrentEncounter()
        && person.getCurrentEncounterModule().equals(name)) {
      if (person.attributes.get(ACTIVE_WELLNESS_ENCOUNTER) != null) {
        person.record.encounterEnd(time, EncounterType.WELLNESS);
        person.record.encounterEnd(time, EncounterType.OUTPATIENT);
        person.attributes.remove(ACTIVE_WELLNESS_ENCOUNTER);
      }
      if (person.attributes.get(ACTIVE_EMERGENCY_ENCOUNTER) != null) {
        person.record.encounterEnd(time, EncounterType.EMERGENCY);
        person.attributes.remove(ACTIVE_EMERGENCY_ENCOUNTER);
      }
      if (person.attributes.get(ACTIVE_URGENT_CARE_ENCOUNTER) != null) {
        person.record.encounterEnd(time, EncounterType.URGENTCARE);
        person.attributes.remove(ACTIVE_URGENT_CARE_ENCOUNTER);
      }
      person.releaseCurrentEncounter(time, name);
    }
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   *
   * @return Collection of all codes and concepts this module uses
   */
  public static Collection<Code> getAllCodes() {
    return Arrays.asList(ENCOUNTER_CHECKUP, ENCOUNTER_EMERGENCY,
        WELL_CHILD_VISIT, GENERAL_EXAM, ENCOUNTER_URGENTCARE);
  }

  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Inventory> attributes) {
    String m = EncounterModule.class.getSimpleName();
    // Read
    Attributes.inventory(attributes, m, LAST_VISIT_SYMPTOM_TOTAL, true, true, "Integer");
    // Write
    Attributes.inventory(attributes, m, ACTIVE_WELLNESS_ENCOUNTER, false, true, "Boolean");
    Attributes.inventory(attributes, m, ACTIVE_URGENT_CARE_ENCOUNTER, false, true, "Boolean");
    Attributes.inventory(attributes, m, ACTIVE_EMERGENCY_ENCOUNTER, false, true, "Boolean");
  }
}