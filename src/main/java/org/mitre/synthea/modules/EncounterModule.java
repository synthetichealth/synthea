package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.engine.Event;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public final class EncounterModule extends Module {

  public static final String ACTIVE_WELLNESS_ENCOUNTER = "active_wellness_encounter";
  public static final int SYMPTOM_THRESHOLD = 200;

  public static final Code ENCOUNTER_CHECKUP = new Code("SNOMED-CT", "185349003",
      "Encounter for check up (procedure)");
  public static final Code ENCOUNTER_EMERGENCY = new Code("SNOMED-CT", "50849002",
      "Emergency Encounter");
  public static final Code WELL_CHILD_VISIT = new Code("SNOMED-CT", "410620009",
      "Well child visit (procedure)");
  public static final Code GENERAL_EXAM = new Code("SNOMED-CT", "162673000",
      "General examination of patient (procedure)");
  // NOTE: if new codes are added, be sure to update getAllCodes below

  public EncounterModule() {
    this.name = "Encounter";
  }

  @Override
  public boolean process(Person person, long time) {
    boolean startedEncounter = false;

    // add a wellness encounter if this is the right time
    if (person.record.timeSinceLastWellnessEncounter(time) >= recommendedTimeBetweenWellnessVisits(
        person, time)) {
      Encounter encounter = person.record.encounterStart(time, EncounterType.WELLNESS.toString());
      encounter.name = "Encounter Module Scheduled Wellness";
      encounter.codes.add(ENCOUNTER_CHECKUP);
      encounter.codes.add(getWellnessVisitCode(person, time));
      person.attributes.put(ACTIVE_WELLNESS_ENCOUNTER, true);
      startedEncounter = true;
    } else if (person.symptomTotal() > SYMPTOM_THRESHOLD) {
      // add a symptom driven encounter if symptoms are severe
      person.resetSymptoms();
      Encounter encounter = person.record.encounterStart(time, EncounterType.WELLNESS.toString());
      encounter.name = "Encounter Module Symptom Driven";
      encounter.codes.add(ENCOUNTER_CHECKUP);
      encounter.codes.add(getWellnessVisitCode(person, time));
      person.attributes.put(ACTIVE_WELLNESS_ENCOUNTER, true);
      startedEncounter = true;
    }

    if (startedEncounter) {
      CardiovascularDiseaseModule.performEncounter(person, time);
      Immunizations.performEncounter(person, time);
    }

    // java modules will never "finish"
    return false;
  }

  public static Code getWellnessVisitCode(Person person, long time) {
    int age = person.ageInYears(time);
    if (age < 18) {
      return WELL_CHILD_VISIT;
    } else {
      return GENERAL_EXAM;
    }
  }

  public static void emergencyVisit(Person person, long time) {
    // processes all emergency events. Implemented as a function instead of a rule because emergency
    // events must be procesed
    // immediately rather than waiting til the next time period. Patient may die, resulting in rule
    // not being called.

    for (Event event : person.events.before(time, "emergency_encounter")) {
      if (event.processed) {
        continue;
      }

      event.processed = true;

      emergencyEncounter(person, time);
    }

    for (Event event : person.events.before(time)) {
      if (event.processed || !(event.type.equals("myocardial_infarction")
          || event.type.equals("cardiac_arrest") || event.type.equals("stroke"))) {
        continue;
      }

      event.processed = true;

      CardiovascularDiseaseModule.performEmergency(person, time, event.type);

    }
  }

  public static void emergencyEncounter(Person person, long time) {
    // find closest service provider with emergency service
    Provider provider = Provider.findClosestService(person, "emergency");
    provider.incrementEncounters("emergency", Utilities.getYear(time));

    Encounter encounter = person.record.encounterStart(time, "emergency");
    encounter.codes.add(ENCOUNTER_EMERGENCY);
    // TODO: emergency encounters need their duration to be defined by the activities performed
    // based on the emergencies given here (heart attack, stroke)
    // assume people will be in the hospital for observation for a few days
    person.record.encounterEnd(time + TimeUnit.DAYS.toMillis(4), "emergency");
  }

  public long recommendedTimeBetweenWellnessVisits(Person person, long time) {
    int ageInYears = person.ageInYears(time);
    if (ageInYears <= 3) {
      int ageInMonths = person.ageInMonths(time);
      if (ageInMonths <= 1) {
        return Utilities.convertTime("months", 1);
      } else if (ageInMonths <= 5) {
        return Utilities.convertTime("months", 2);
      } else if (ageInMonths <= 17) {
        return Utilities.convertTime("months", 3);
      } else {
        return Utilities.convertTime("months", 6);
      }
    } else if (ageInYears <= 19) {
      return Utilities.convertTime("years", 1);
    } else if (ageInYears <= 39) {
      return Utilities.convertTime("years", 3);
    } else if (ageInYears <= 49) {
      return Utilities.convertTime("years", 2);
    } else {
      return Utilities.convertTime("years", 1);
    }
  }

  public void endWellnessEncounter(Person person, long time) {
    person.record.encounterEnd(time, EncounterType.WELLNESS.toString());
    person.attributes.remove(ACTIVE_WELLNESS_ENCOUNTER);
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   * 
   * @return Collection of all codes and concepts this module uses
   */
  public static Collection<Code> getAllCodes() {
    return Arrays.asList(ENCOUNTER_CHECKUP, ENCOUNTER_EMERGENCY, 
                         WELL_CHILD_VISIT, GENERAL_EXAM);
  }

}
