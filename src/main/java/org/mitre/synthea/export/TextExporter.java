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
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
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
  public static void exportAll(Person person, long time) throws IOException {

    List<Encounter> encounters = person.record.encounters;
    List<Entry> conditions = new ArrayList<>();
    List<Entry> allergies = new ArrayList<>();
    List<Observation> observations = new ArrayList<>();
    List<Procedure> procedures = new ArrayList<>();
    List<Medication> medications = new ArrayList<>();
    List<Entry> immunizations = new ArrayList<>();
    List<CarePlan> careplans = new ArrayList<>();
    List<ImagingStudy> imagingStudies = new ArrayList<>();

    for (Encounter encounter : person.record.encounters) {
      conditions.addAll(encounter.conditions);
      allergies.addAll(encounter.allergies);
      observations.addAll(encounter.observations);
      procedures.addAll(encounter.procedures);
      medications.addAll(encounter.medications);
      immunizations.addAll(encounter.immunizations);
      careplans.addAll(encounter.careplans);
      imagingStudies.addAll(encounter.imagingStudies);
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
    Collections.reverse(imagingStudies);

    // now we finally start writing things
    List<String> textRecord = new LinkedList<>();

    basicInfo(textRecord, person, time);
    breakline(textRecord);

    textRecord.add("ALLERGIES:");
    if (allergies.isEmpty()) {
      textRecord.add("No Known Allergies");
    } else {
      for (Entry allergy : allergies) {
        condition(textRecord, allergy, true);
      }
    }
    breakline(textRecord);

    textRecord.add("MEDICATIONS:");
    for (Medication medication : medications) {
      medication(textRecord, medication, true);
    }
    breakline(textRecord);

    textRecord.add("CONDITIONS:");
    for (Entry condition : conditions) {
      condition(textRecord, condition, true);
    }
    breakline(textRecord);

    textRecord.add("CARE PLANS:");
    for (CarePlan careplan : careplans) {
      careplan(textRecord, careplan, true);
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
    breakline(textRecord);

    textRecord.add("IMAGING STUDIES:");
    for (ImagingStudy imagingStudy : imagingStudies) {
      imagingStudy(textRecord, imagingStudy);
    }
    breakline(textRecord);

    // finally write to the file
    File outDirectory = Exporter.getOutputFolder("text", person);
    Path outFilePath = outDirectory.toPath().resolve(Exporter.filename(person, "txt"));
    Files.write(outFilePath, textRecord, StandardOpenOption.CREATE_NEW);
  }
    
  /**
   * Chia293 Rohan584
   * ================
   * Race:                White
   * Ethnicity:           Non-Hispanic
   * Gender:              F
   * Age:                 4
   * Birth Date:          2014-04-12
   * Marital Status:      S
   * Outpatient Provider: BEVERLY HOSPITAL CORPORATION
   * --------------------------------------------------------------------------------
   * ALLERGIES:
   *  2015-01-27 : Allergy to wheat
   *  2015-01-27 : Allergy to tree pollen
   *  2015-01-27 : Allergy to grass pollen
   *  2015-01-27 : Dander (animal) allergy
   *  2015-01-27 : Allergy to mould
   *  2015-01-27 : Allergy to bee venom
   * --------------------------------------------------------------------------------
   * ENCOUNTER
   * 2016-06-01 : Encounter for Acute bronchitis (disorder)
   * Location: BEVERLY HOSPITAL CORPORATION
   * Type: ambulatory
   *   
   *  MEDICATIONS:
   *  2016-06-01 : Acetaminophen 160 MG for Acute bronchitis (disorder)
   *   
   *  CONDITIONS:
   *  2016-06-01 : Acute bronchitis (disorder)
   *  
   *  CARE PLANS:
   *  2016-06-01 : Respiratory therapy
   *                        Reason: Acute bronchitis (disorder)
   *                        Activity: Recommendation to avoid exercise
   *                        Activity: Deep breathing and coughing exercises
   *  
   *  OBSERVATIONS:
   *   
   *  PROCEDURES:
   *  2016-06-01 : Measurement of respiratory function (procedure) for Acute bronchitis (disorder)
   *   
   *  IMMUNIZATIONS:
   *   
   *  IMAGING STUDIES:
   *   
   * --------------------------------------------------------------------------------
   * CONTINUING
   *   
   *  CONDITIONS:
   *  2015-01-14 : Atopic dermatitis
   *  2016-04-18 : Childhood asthma
   *  
   *  MEDICATIONS:
   *  2015-01-27 : 0.3 ML EPINEPHrine 0.5 MG/ML Auto-Injector
   *  2015-01-27 : Loratadine 5 MG Chewable Tablet
   *  2016-04-18 + 200 ACTUAT Albuterol 0.09 MG/ACTUAT Metered Dose Inhaler for Childhood asthma
   *  2016-04-18 + 120 ACTUAT Fluticasone propionate 0.044 MG/ACTUAT Metered Dose Inhaler for Childhood asthma
   *   
   *  CAREPLANS:
   *  2015-01-14 : Skin condition care
   *                        Reason: Atopic dermatitis
   *                        Activity: Application of moisturizer to skin
   *  2015-01-27 : Self care
   *                        Activity: Allergy education
   *                        Activity: Food allergy diet
   *                        Activity: Allergy education
   *  2016-04-18 : Asthma self management
   *                        Reason: Childhood asthma
   *                        Activity: Inhaled steroid therapy
   *                        Activity: Home nebulizer therapy
   *                        Activity: Breathing control
   *   
   * --------------------------------------------------------------------------------
   */
  
  public static void exportEncounter(Person person, long time) throws IOException {
  
  /**
   * Produce and export a person's record in text format
   * 
   * @param person Person
   * @param time Time the simulation ended
   * @throws IOException if any error occurs writing to the standard export location
   */

    List<Encounter> encounters = person.record.encounters;
    List<Entry> conditions = new ArrayList<>();
    List<Entry> allergies = new ArrayList<>();
    List<Medication> medications = new ArrayList<>();
    List<CarePlan> careplans = new ArrayList<>();

    for (Encounter encounter : person.record.encounters) {
      conditions.addAll(encounter.conditions);
      allergies.addAll(encounter.allergies);
      medications.addAll(encounter.medications);
      careplans.addAll(encounter.careplans);
    }

    // reverse these items so they are displayed in reverse chrono order
    Collections.reverse(encounters);
    Collections.reverse(conditions);
    Collections.reverse(allergies);
    Collections.reverse(medications);
    Collections.reverse(careplans);
    
    //set an integer that will be used as a counter for file naming purposes
    int encounterNumber = 0;

    for (Encounter encounter : encounters) {
      //make a record for each encounter to write information
      List<String> textRecord = new LinkedList<>();
            
      basicInfo(textRecord, person, time);
      breakline(textRecord);

      textRecord.add("ALLERGIES:");
      if (allergies.isEmpty()) {
        textRecord.add("No Known Allergies");
      } else {
        for (Entry allergy : allergies) {
          condition(textRecord, allergy, false);
        }
      }
      breakline(textRecord);

      textRecord.add("ENCOUNTER");
      encounterReport(textRecord, person, encounter);
      breakline(textRecord);

      textRecord.add("CONTINUING");
      textRecord.add("   ");

      textRecord.add("   CONDITIONS:");
      for (Entry condition : conditions) {
        conditionpast(textRecord, condition, encounter);
      }
      textRecord.add("   ");

      textRecord.add("   MEDICATIONS:");
      for (Medication medication : medications) {
        medicationpast(textRecord, medication, encounter);
      }
      textRecord.add("   ");

      textRecord.add("   CAREPLANS:");
      for (CarePlan careplan : careplans){
        careplanpast(textRecord, careplan, encounter);
      }
      textRecord.add("   ");
      breakline(textRecord);

      encounterNumber ++;

      //write to the file
      File outDirectory2 = Exporter.getOutputFolder("text2", person);
      Path outFilePath2 = outDirectory2.toPath().resolve(Exporter.filename2(person, Integer.toString(encounterNumber), "txt"));
      Files.write(outFilePath2, textRecord, StandardOpenOption.CREATE_NEW);
    }      
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

    Provider prov = person.getAmbulatoryProvider(endTime);
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
      textRecord.add("  " + encounterTime + " : " + encounter.codes.get(0).display);
    } else {
      textRecord.add("  " + encounterTime + " : Encounter for " + encounter.reason.display);
    }
  }

  /**
   * Add all info from the encounter to the record.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param person
   *          The person to export
   * @param encounter
   *          The encounter all of the information refers to
   */
  private static void encounterReport(List<String> textRecord, Person person, Encounter encounter) {
    String encounterTime = dateFromTimestamp(encounter.start);

    if (encounter.reason == null){
      textRecord.add(encounterTime + " : " + encounter.codes.get(0).display);
    } else {
      textRecord.add(encounterTime + " : Encounter for " + encounter.reason.display);
    }

    Provider provider;
    switch (encounter.type){
      case "inpatient" : provider = person.getInpatientProvider(encounter.start);
      break;
      case "ambulatory" : provider = person.getAmbulatoryProvider(encounter.start);
      break;
      case "emergency" : provider = person.getEmergencyProvider(encounter.start);
      break;
      case "WELLNESS" : provider = person.getAmbulatoryProvider(encounter.start);
      break;
      default : provider = person.getAmbulatoryProvider(encounter.start);
      break;
    }
    
    textRecord.add("Location: " + provider.name);
    textRecord.add("Type: " + encounter.type);
    textRecord.add("   ");

    //Create lists for only the items that occurred at the encounter
    List<Entry> encounterConditions = new ArrayList<>();
    List<Observation> encounterObservations = new ArrayList<>();
    List<Procedure> encounterProcedures = new ArrayList<>();
    List<Medication> encounterMedications = new ArrayList<>();
    List<Entry> encounterImmunizations = new ArrayList<>();
    List<CarePlan> encounterCareplans = new ArrayList<>();
    List<ImagingStudy> encounterImagingStudies = new ArrayList<>();

    encounterConditions.addAll(encounter.conditions);
    encounterObservations.addAll(encounter.observations);
    encounterProcedures.addAll(encounter.procedures);
    encounterMedications.addAll(encounter.medications);
    encounterImmunizations.addAll(encounter.immunizations);
    encounterCareplans.addAll(encounter.careplans);
    encounterImagingStudies.addAll(encounter.imagingStudies);

    Collections.reverse(encounterConditions);
    Collections.reverse(encounterObservations);
    Collections.reverse(encounterProcedures);
    Collections.reverse(encounterMedications);
    Collections.reverse(encounterImmunizations);
    Collections.reverse(encounterCareplans);
    Collections.reverse(encounterImagingStudies);

    textRecord.add("   MEDICATIONS:");
    for (Medication medication : encounterMedications) {
      medication(textRecord, medication, false);
    }
    textRecord.add("   ");

    textRecord.add("   CONDITIONS:");
    for (Entry condition : encounterConditions) {
      condition(textRecord, condition, false);
    }
    textRecord.add("   ");

    textRecord.add("   CARE PLANS:");
    for (CarePlan careplan : encounterCareplans) {
      careplan(textRecord, careplan, false);
    }
    textRecord.add("   ");

    textRecord.add("   OBSERVATIONS:");
    for (Observation observation : encounterObservations) {
        observation(textRecord, observation);
    }
    textRecord.add("   ");

    textRecord.add("   PROCEDURES:");
    for (Procedure procedure : encounterProcedures) {
        procedure(textRecord, procedure);
    }
    textRecord.add("   ");

    textRecord.add("   IMMUNIZATIONS:");
    for (Entry immunization : encounterImmunizations) {
        immunization(textRecord, immunization);
    }
    textRecord.add("   ");

    textRecord.add("   IMAGING STUDIES:");
    for (ImagingStudy imagingStudy : encounterImagingStudies) {
        imagingStudy(textRecord, imagingStudy);
    }
    textRecord.add("   ");
  }

  /**
   * Write a line for a single Condition to the exported record.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param condition
   *          The condition to add to the export
   */
  private static void condition(List<String> textRecord, Entry condition, Boolean end) {
    String start = dateFromTimestamp(condition.start);
    String stop;
    String description = condition.codes.get(0).display;
    if (end) {
      if (condition.stop == 0L) {
        //     "YYYY-MM-DD"
        stop = "          ";
      } else {
        stop = dateFromTimestamp(condition.stop);
      }
      textRecord.add("  " + start + " - " + stop + " : " + description);
    }
    else {
      textRecord.add("  " + start + " : " + description);
    }
  }

  /**
   * Write a line for a condition that has not ended at the time of the encounter.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param condition
   *          The condition to add to the export
   * @param encounter
   *          The encounter at which the continuing condition is reported
   */
  private static void conditionpast(List<String> textRecord, Entry condition, Encounter encounter) {
    String start = dateFromTimestamp(condition.start);
    if ((condition.stop == 0L || condition.stop > encounter.stop) && (condition.start < encounter.start)) {
      //checks that the condition hasn't ended by the time of the encounter
      //and began prior to the encounter
      String description = condition.codes.get(0).display;
        textRecord.add("  " + start + " : " + description);
    } else {
      return;
    }
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

    textRecord.add("  " + obsTime + " : " + Strings.padEnd(obsDesc, 40, ' ') + " " + value + " " + unit);
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

    textRecord.add("  " + obsTime + " : " + obsDesc);

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
      textRecord.add("  " + procedureTime + " : " + procedureDesc);
    } else {
      String reason = procedure.reasons.get(0).display;
      textRecord.add("  " + procedureTime + " : " + procedureDesc + " for " + reason);
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
  private static void medication(List<String> textRecord, Medication medication, Boolean end) {
    String medTime = dateFromTimestamp(medication.start);
    String medDesc = medication.codes.get(0).display;
    String status = (medication.stop == 0L) ? "CURRENT" : "STOPPED";
    if (end) {
      if (medication.reasons == null || medication.reasons.isEmpty()) {
        textRecord.add("  " + medTime + "[" + status + "] : " + medDesc);
      } else {
        String reason = medication.reasons.get(0).display;
        textRecord.add("  " + medTime + "[" + status + "] : " + medDesc + " for " + reason);
      }
    } else {
      if (medication.reasons == null || medication.reasons.isEmpty()) {
        textRecord.add("  " + medTime + " : " + medDesc);
      } else {
        String reason = medication.reasons.get(0).display;
        textRecord.add("  " + medTime + " : " + medDesc + " for " + reason);
      }
    }
  }

  /**
   * Write a line for a medication that is still being taken at the time of the encounter.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param medication
   *          The medication to add to the export
   * @param encounter
   *          The encounter at which the continuing medication is reported
   */
  private static void medicationpast(List<String> textRecord, Medication medication, Encounter encounter) {
    String medTime = dateFromTimestamp(medication.start);
    String medDesc = medication.codes.get(0).display;
    if ((medication.stop == 0L || medication.stop > encounter.stop) && (medication.start < encounter.start)) {
      //checks that the medication is still being taken at the time of the encounter
      //and began prior to the encounter
      if (medication.reasons == null || medication.reasons.isEmpty()) {
        textRecord.add("  " + medTime + " : " + medDesc);
      } else {
        String reason = medication.reasons.get(0).display;
        textRecord.add("  " + medTime + " + " + medDesc  + " for " + reason);
      }
    } else {
      return;
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
    textRecord.add("  " + immTime + " : " + immDesc);
  }

  /**
   * Write lines for a single CarePlan to the exported record.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param careplan
   *          The CarePlan to add to the export
   */
  private static void careplan(List<String> textRecord, CarePlan careplan, Boolean end) {
    String cpTime = dateFromTimestamp(careplan.start);
    String cpDesc = careplan.codes.get(0).display;
    String status = (careplan.stop == 0L) ? "CURRENT" : "STOPPED";
    if (end) {
      textRecord.add("  " + cpTime + "[" + status + "] : " + cpDesc);
    } else {
      textRecord.add("  " + cpTime + " : " + cpDesc);
    }
    

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
   * Write a line for a careplan that is still being followed at the time of the encounter.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param careplan
   *          The careplan to add to the export
   * @param encounter
   *          The encounter at which the continuing careplan is reported
   */
  private static void careplanpast(List<String> textRecord, CarePlan careplan, Encounter encounter) {
    String cpTime = dateFromTimestamp(careplan.start);
    String cpDesc = careplan.codes.get(0).display;
    if ((careplan.stop == 0L || careplan.stop > encounter.stop) && (careplan.start < encounter.start)){
      //checks that the careplan is still being followed at the time of the encounter
      //and began prior to the encounter
      textRecord.add("  " + cpTime + " : " + cpDesc);
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
    } else {
      return;
    }
  }

  /**
   * Write lines for a single ImagingStudy to the exported record.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param imagingstudy
   *          The ImagingStudy to add to the export
   */
  private static void imagingStudy(List<String> textRecord, ImagingStudy imagingStudy) {
    String studyTime = dateFromTimestamp(imagingStudy.start);
    String modality = imagingStudy.series.get(0).modality.display;
    String bodySite = imagingStudy.series.get(0).bodySite.display;

    textRecord.add("  " + studyTime + " : " + modality + ", " + bodySite);
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
