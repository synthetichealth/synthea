package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;

import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.text.WordUtils;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

/**
 * Exporter for a simple human-readable text format.
 * Sample: <pre>
 * Colleen Wilkinson
 * =================
 * Race:                White
 * Ethnicity:           Non-Hispanic
 * Gender:              M
 * Age:                 51
 * Birth Date:          1966-10-26
 * Marital Status:      
 * Outpatient Provider: MCLEAN HOSPITAL CORPORATION
 * --------------------------------------------------------------------------------
 * ALLERGIES:
 * --------------------------------------------------------------------------------
 * MEDICATIONS:
 * 2016-12-28[STOPPED] : Amoxicillin 250 MG for Sinusitis
 * 2016-10-26[CURRENT] : 3 ML liraglutide 6 MG/ML Pen Injector for Diabetes
 * 2016-10-26[CURRENT] : 24 HR Metformin hydrochloride for Diabetes
 * --------------------------------------------------------------------------------
 * CONDITIONS:
 * 2016-12-28 - 2017-01-18 : Viral sinusitis (disorder)
 * 2016-10-26 -            : Diabetes
 * 2015-11-04 - 2015-11-18 : Acute viral pharyngitis (disorder)
 * --------------------------------------------------------------------------------
 * CARE PLANS:
 * 2016-10-26[CURRENT] : Diabetes self management plan
 *                          Reason: Diabetes
 *                          Activity: Diabetic diet
 *                          Activity: Exercise therapy
 * 2010-09-15[STOPPED] : Fracture care
 *                          Reason: Fracture subluxation of wrist
 *                          Activity: Recommendation to rest
 *                          Activity: Physical activity target light exercise
 * --------------------------------------------------------------------------------
 * OBSERVATIONS:
 * 2017-11-01 : Blood Pressure
 *            - Diastolic Blood Pressure                 76.0 mmHg 
 *            - Systolic Blood Pressure                  117.8 mmHg 
 * 2017-11-01 : Body Mass Index                          37.2 kg/m2 
 * 2017-11-01 : Body Weight                              122.7 kg 
 * 2017-11-01 : Body Height                              181.6 cm 
 * --------------------------------------------------------------------------------
 * ENCOUNTERS:
 * 2017-11-01 : Encounter for check up (procedure)
 * 2016-12-28 : Encounter for Viral sinusitis (disorder)
 * 2016-10-26 : Encounter for check up (procedure)
 * </pre>
 */
public class TextExporter {
  /**
   * Produce and export a person's record in the text format.
   * 
   * @param person Person to export
   * @param time Time the simulation ended
   * @throws IOException if any error occurs writing to the standard export location
   */
  public static void export(Person person, long time) throws IOException {
    // in the text exporter, items are not grouped by encounter
    // so we collect them all into lists grouped by type
    // and then displayed in reverse chronological order
    List<Encounter> encounters = person.record.encounters;
    List<Entry> conditions = new ArrayList<>();
    List<Entry> allergies = new ArrayList<>();
    List<Observation> observations = new ArrayList<>();
    List<Procedure> procedures = new ArrayList<>();
    List<Medication> medications = new ArrayList<>();
    List<Entry> immunizations = new ArrayList<>();
    List<CarePlan> careplans = new ArrayList<>();

    for (Encounter encounter : person.record.encounters) {
      conditions.addAll(encounter.conditions);
      allergies.addAll(encounter.allergies);
      observations.addAll(encounter.observations);
      procedures.addAll(encounter.procedures);
      medications.addAll(encounter.medications);
      immunizations.addAll(encounter.immunizations);
      careplans.addAll(encounter.careplans);
    }

    // reverse these items so they are displayed in reverse chrono order
    Collections.reverse(encounters);
    Collections.reverse(conditions);
    Collections.reverse(allergies);
    Collections.reverse(observations);
    Collections.reverse(procedures);
    Collections.reverse(medications);
    Collections.reverse(immunizations);
    Collections.reverse(careplans);

    // now we finally start writing things
    List<String> textRecord = new LinkedList<>();

    basicInfo(textRecord, person, time);
    breakline(textRecord);

    textRecord.add("ALLERGIES:");
    if (allergies.isEmpty()) {
      textRecord.add("No Known Allergies");
    } else {
      for (Entry allergy : allergies) {
        condition(textRecord, allergy);
      }
    }
    breakline(textRecord);

    textRecord.add("MEDICATIONS:");
    for (Medication medication : medications) {
      medication(textRecord, medication);
    }
    breakline(textRecord);

    textRecord.add("CONDITIONS:");
    for (Entry condition : conditions) {
      condition(textRecord, condition);
    }
    breakline(textRecord);

    textRecord.add("CARE PLANS:");
    for (CarePlan careplan : careplans) {
      careplan(textRecord, careplan);
    }
    breakline(textRecord);

    textRecord.add("OBSERVATIONS:");
    for (Observation observation : observations) {
      observation(textRecord, observation);
    }
    breakline(textRecord);

    textRecord.add("PROCEDURES:");
    for (Procedure procedure : procedures) {
      procedure(textRecord, procedure);
    }
    breakline(textRecord);

    textRecord.add("IMMUNIZATIONS:");
    for (Entry immunization : immunizations) {
      immunization(textRecord, immunization);
    }
    breakline(textRecord);

    textRecord.add("ENCOUNTERS:");
    for (Encounter encounter : encounters) {
      encounter(textRecord, encounter);
    }

    // finally write to the file
    File outDirectory = Exporter.getOutputFolder("text", person);
    Path outFilePath = outDirectory.toPath().resolve(Exporter.filename(person, "txt"));
    Files.write(outFilePath, textRecord, StandardOpenOption.CREATE_NEW);
  }

  /**
   * Add the basic information to the record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param person
   *          The person to export
   * @param endTime
   *          Time the simulation ended (to calculate age/deceased status)
   */
  private static void basicInfo(List<String> textRecord, Person person, long endTime) {
    String name = (String) person.attributes.get(Person.NAME);

    textRecord.add(name);
    textRecord.add(name.replaceAll("[A-Za-z0-9 ]", "=")); // "underline" the characters in the name

    String race = (String) person.attributes.get(Person.RACE);
    if (race.equals("hispanic")) {
      textRecord.add("Race:                Other");
      String ethnicity = (String) person.attributes.get(Person.ETHNICITY);
      ethnicity = WordUtils.capitalize(ethnicity.replace('_', ' '));
      textRecord.add("Ethnicity:           " + ethnicity);
    } else {
      textRecord.add("Race:                " + WordUtils.capitalize(race));
      textRecord.add("Ethnicity:           Non-Hispanic");
    }

    textRecord.add("Gender:              " + person.attributes.get(Person.GENDER));

    String age = person.alive(endTime) ? Integer.toString(person.ageInYears(endTime)) : "DECEASED";
    textRecord.add("Age:                 " + age);

    String birthdate = dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE));
    textRecord.add("Birth Date:          " + birthdate);
    textRecord.add("Marital Status:      "
        + person.attributes.getOrDefault(Person.MARITAL_STATUS, "S"));

    Provider prov = person.getAmbulatoryProvider();
    if (prov != null) {
      textRecord.add("Outpatient Provider: " + prov.name);
    }
  }

  /**
   * Write a line for a single Encounter to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param encounter
   *          The Encounter to add to the export
   */
  private static void encounter(List<String> textRecord, Encounter encounter) {
    String encounterTime = dateFromTimestamp(encounter.start);

    if (encounter.reason == null) {
      textRecord.add(encounterTime + " : " + encounter.codes.get(0).display);
    } else {
      textRecord.add(encounterTime + " : Encounter for " + encounter.reason.display);
    }
  }

  /**
   * Write a line for a single Condition to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param condition
   *          The condition to add to the export
   */
  private static void condition(List<String> textRecord, Entry condition) {
    String start = dateFromTimestamp(condition.start);
    String stop;
    if (condition.stop == 0L) {
      //     "YYYY-MM-DD"
      stop = "          ";
    } else {
      stop = dateFromTimestamp(condition.stop);
    }
    String description = condition.codes.get(0).display;

    textRecord.add(start + " - " + stop + " : " + description);
  }

  /**
   * Write a line for a single Observation to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param observation
   *          The Observation to add to the export
   */
  private static void observation(List<String> textRecord, Observation observation) {
    String value = ExportHelper.getObservationValue(observation);

    if (value == null) {
      if (observation.observations != null && !observation.observations.isEmpty()) {
        // handoff to multiobservation, ex for blood pressure
        multiobservation(textRecord, observation);
      }

      // no value so nothing more to report here
      return;
    }

    String obsTime = dateFromTimestamp(observation.start);
    String obsDesc = observation.codes.get(0).display;

    String unit = observation.unit != null ? observation.unit : "";

    textRecord.add(obsTime + " : " + Strings.padEnd(obsDesc, 40, ' ') + " " + value + " " + unit);
  }

  /**
   * Write lines for an Observation with multiple parts to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param observation
   *          The Observation to add to the export
   */
  private static void multiobservation(List<String> textRecord, Observation observation) {
    String obsTime = dateFromTimestamp(observation.start);
    String obsDesc = observation.codes.get(0).display;

    textRecord.add(obsTime + " : " + obsDesc);

    for (Observation subObs : observation.observations) {
      String value = ExportHelper.getObservationValue(subObs);
      String unit = subObs.unit != null ? subObs.unit : "";
      String subObsDesc = subObs.codes.get(0).display;
      textRecord.add("           - " + Strings.padEnd(subObsDesc, 40, ' ') + " " + value + " "
          + unit);
    }
  }

  /**
   * Write a line for a single Procedure to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param procedure
   *          The Procedure to add to the export
   */
  private static void procedure(List<String> textRecord, Procedure procedure) {
    String procedureTime = dateFromTimestamp(procedure.start);
    String procedureDesc = procedure.codes.get(0).display;
    if (procedure.reasons == null || procedure.reasons.isEmpty()) {
      textRecord.add(procedureTime + " : " + procedureDesc);
    } else {
      String reason = procedure.reasons.get(0).display;
      textRecord.add(procedureTime + " : " + procedureDesc + " for " + reason);
    }
  }

  /**
   * Write a line for a single Medication to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param medication
   *          The Medication to add to the export
   */
  private static void medication(List<String> textRecord, Medication medication) {
    String medTime = dateFromTimestamp(medication.start);
    String medDesc = medication.codes.get(0).display;
    String status = (medication.stop == 0L) ? "CURRENT" : "STOPPED";
    if (medication.reasons == null || medication.reasons.isEmpty()) {
      textRecord.add(medTime + "[" + status + "] : " + medDesc);
    } else {
      String reason = medication.reasons.get(0).display;
      textRecord.add(medTime + "[" + status + "] : " + medDesc + " for " + reason);
    }
  }

  /**
   * Write a line for a single Immunization to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param immunization
   *          The immunization to add to the export
   */
  private static void immunization(List<String> textRecord, Entry immunization) {
    String immTime = dateFromTimestamp(immunization.start);
    String immDesc = immunization.codes.get(0).display;
    textRecord.add(immTime + " : " + immDesc);
  }

  /**
   * Write lines for a single Encounter to the exported record.
   * 
   * @param textRecord
   *          Text format record, as a list of lines
   * @param careplan
   *          The CarePlan to add to the export
   */
  private static void careplan(List<String> textRecord, CarePlan careplan) {
    String cpTime = dateFromTimestamp(careplan.start);
    String cpDesc = careplan.codes.get(0).display;
    String status = (careplan.stop == 0L) ? "CURRENT" : "STOPPED";
    textRecord.add(cpTime + "[" + status + "] : " + cpDesc);

    if (careplan.reasons != null && !careplan.reasons.isEmpty()) {
      for (Code reason : careplan.reasons) {
        textRecord.add("                         Reason: " + reason.display);
      }
    }

    if (careplan.activities != null && !careplan.activities.isEmpty()) {
      for (Code activity : careplan.activities) {
        textRecord.add("                         Activity: " + activity.display);
      }
    }
  }

  /**
   * Section separator (80 dashes).
   */
  private static final String SECTION_SEPARATOR = String.join("", Collections.nCopies(80, "-"));

  /**
   * Add a section separator line to the record.
   * 
   * @param textRecord
   *          Record to add separator line to
   */
  private static void breakline(List<String> textRecord) {
    textRecord.add(SECTION_SEPARATOR);
  }
}
