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
import org.mitre.synthea.export.TextExporter;

/**
 * Arlen68 Predovic534
===================
Race:                White
Ethnicity:           Non-Hispanic
Gender:              M
Age:                 19
Birth Date:          1999-03-22
Marital Status:      S
Outpatient Provider: SIGNATURE HEALTHCARE BROCKTON HOSPITAL
--------------------------------------------------------------------------------
ALLERGIES:
No Known Allergies
--------------------------------------------------------------------------------
ENCOUNTER:
2013-11-03 : Emergency room admission
   
       MEDICATIONS:
       2013-11-03 : Acetaminophen 325 MG Oral Tablet
       2013-11-03 : Meperidine Hydrochloride 50 MG Oral Tablet
   
       CONDITIONS:
       2013-11-03 : Fracture of ankle
   
       CARE PLANS:
       2013-11-03 : Fracture care
                         Reason: Fracture of ankle
                         Activity: Recommendation to rest
                         Activity: Physical activity target light exercise
   
       OBSERVATIONS:
   
       PROCEDURES:
       2013-11-03 : Bone immobilization for Fracture of ankle
       2013-11-03 : Ankle X-ray
   
       IMMUNIZATIONS:
   
       IMAGING STUDIES:
       2013-11-03 : Digital Radiography, Ankle
   
--------------------------------------------------------------------------------
CONTINUING
   
       CONDITIONS:
       2006-03-20 : Child attention deficit disorder
   
       MEDICATIONS:
       2006-03-20 + Methylphenidate Hydrochloride 20 MG [Ritalin] for Child attention deficit disorder
   
       CAREPLANS:
       2006-03-20 : Overactivity/inattention behavior management
                         Reason: Child attention deficit disorder
                         Activity: Counseling
                         Activity: Psychological assessment
   
--------------------------------------------------------------------------------
 */

public class TextExporter2 extends TextExporter {
    /**
      * Exports a readable text format, dividing the health record into encounters.
      * 
      * @param person
      * @param time
      * @param encounter
      * @throws IOException
     */
    public static void export2(Person person, long time) throws IOException {
        //Collect items in individuals list that will be used
        //to show what items still currently exist at each encounter
        List<Encounter> encounters_all = person.record.encounters;
        List<Entry> conditions_all = new ArrayList<>();
        List<Entry> allergies_all = new ArrayList<>();
        List<Medication> medications_all = new ArrayList<>();
        List<CarePlan> careplans_all = new ArrayList<>();

        for (Encounter encounter : person.record.encounters) {
            conditions_all.addAll(encounter.conditions);
            allergies_all.addAll(encounter.allergies);
            medications_all.addAll(encounter.medications);
            careplans_all.addAll(encounter.careplans);
        }

        // reverse these items so they are displayed in reverse chrono order
        Collections.reverse(encounters_all);
        Collections.reverse(conditions_all);
        Collections.reverse(allergies_all);
        Collections.reverse(medications_all);
        Collections.reverse(careplans_all);

        //set an integer that will be used as a counter for file naming purposes
        int encounterNumber = 0;

        for (Encounter encounter : encounters_all) {
            
            //make a record for each encounter to write information
            List<String> textRecord2 = new LinkedList<>();
            
            basicInfo(textRecord2, person, time);
            breakline(textRecord2);

            textRecord2.add("ALLERGIES:");
            if (allergies_all.isEmpty()) {
                textRecord2.add("No Known Allergies");
            } else {
                for (Entry allergy : allergies_all) {
                    condition(textRecord2, allergy);
                }
            }
            breakline(textRecord2);

            textRecord2.add("ENCOUNTER:");
            encounter(textRecord2, person, encounter);

            textRecord2.add("CONTINUING");
            textRecord2.add("   ");

            textRecord2.add("       CONDITIONS:");
            for (Entry condition : conditions_all) {
                conditionpast(textRecord2, condition, encounter);
            }
            textRecord2.add("   ");
            
            textRecord2.add("       MEDICATIONS:");
            for (Medication medication : medications_all) {
                medicationpast(textRecord2, medication, encounter);
            }
            textRecord2.add("   ");

            textRecord2.add("       CAREPLANS:");
            for (CarePlan careplan : careplans_all){
                careplanpast(textRecord2, careplan, encounter);
            }
            textRecord2.add("   ");
            breakline(textRecord2);

            encounterNumber ++;

            //write to the file
            File outDirectory = Exporter.getOutputFolder("text2", person);
            Path outFilePath2 = outDirectory.toPath().resolve(Exporter.filename2(person, Integer.toString(encounterNumber), "txt"));
            Files.write(outFilePath2, textRecord2, StandardOpenOption.CREATE_NEW);
            
        }

        
    
    }

    /**
     * Add the basic information to the record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param person
     *          The person to export
     * @param endTime
     *          Time the simulation ended (to calculate age/deceased status)
     */
    private static void basicInfo(List<String> textRecord2, Person person, long endTime) {
        String name = (String) person.attributes.get(Person.NAME);

        textRecord2.add(name);
        textRecord2.add(name.replaceAll("[A-Za-z0-9 ]", "=")); // "underline" the characters in the name

        String race = (String) person.attributes.get(Person.RACE);
        if (race.equals("hispanic")) {
            textRecord2.add("Race:                Other");
            String ethnicity = (String) person.attributes.get(Person.ETHNICITY);
            ethnicity = WordUtils.capitalize(ethnicity.replace('_', ' '));
            textRecord2.add("Ethnicity:           " + ethnicity);
        } else {
            textRecord2.add("Race:                " + WordUtils.capitalize(race));
            textRecord2.add("Ethnicity:           Non-Hispanic");
        }

        textRecord2.add("Gender:              " + person.attributes.get(Person.GENDER));

        String age = person.alive(endTime) ? Integer.toString(person.ageInYears(endTime)) : "DECEASED";
        textRecord2.add("Age:                 " + age);

        String birthdate = dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE));
        textRecord2.add("Birth Date:          " + birthdate);
        textRecord2.add("Marital Status:      "
            + person.attributes.getOrDefault(Person.MARITAL_STATUS, "S"));

        Provider prov = person.getAmbulatoryProvider(endTime);
        if (prov != null) {
            textRecord2.add("Outpatient Provider: " + prov.name);
        }
    }

    /**
     * Add all info from the encounter to the record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param person
     *          The person to export
     * @param encounter
     *          The encounter all of the information refers to
     */
    private static void encounter(List<String> textRecord2, Person person, Encounter encounter) {
        String encounterTime = dateFromTimestamp(encounter.start);

        if (encounter.reason == null){
            textRecord2.add(encounterTime + " : " + encounter.codes.get(0).display);
        } else {
            textRecord2.add(encounterTime + " : Encounter for " + encounter.reason.display);
        }
        textRecord2.add("   ");

        //Create lists for only the items that occurred at the encounter
        List<Entry> conditions = new ArrayList<>();
        List<Observation> observations = new ArrayList<>();
        List<Procedure> procedures = new ArrayList<>();
        List<Medication> medications = new ArrayList<>();
        List<Entry> immunizations = new ArrayList<>();
        List<CarePlan> careplans = new ArrayList<>();
        List<ImagingStudy> imagingStudies = new ArrayList<>();
        conditions.addAll(encounter.conditions);
        observations.addAll(encounter.observations);
        procedures.addAll(encounter.procedures);
        medications.addAll(encounter.medications);
        immunizations.addAll(encounter.immunizations);
        careplans.addAll(encounter.careplans);
        imagingStudies.addAll(encounter.imagingStudies);
        Collections.reverse(conditions);
        Collections.reverse(observations);
        Collections.reverse(procedures);
        Collections.reverse(medications);
        Collections.reverse(immunizations);
        Collections.reverse(careplans);
        Collections.reverse(imagingStudies);

        textRecord2.add("       MEDICATIONS:");
        for (Medication medication : medications) {
            medication(textRecord2, medication);
        }
        textRecord2.add("   ");

        textRecord2.add("       CONDITIONS:");
        for (Entry condition : conditions) {
            condition(textRecord2, condition);
        }
        textRecord2.add("   ");

        textRecord2.add("       CARE PLANS:");
        for (CarePlan careplan : careplans) {
            careplan(textRecord2, careplan);
        }
        textRecord2.add("   ");

        textRecord2.add("       OBSERVATIONS:");
        for (Observation observation : observations) {
            observation(textRecord2, observation);
        }
        textRecord2.add("   ");

        textRecord2.add("       PROCEDURES:");
        for (Procedure procedure : procedures) {
            procedure(textRecord2, procedure);
        }
        textRecord2.add("   ");

        textRecord2.add("       IMMUNIZATIONS:");
        for (Entry immunization : immunizations) {
            immunization(textRecord2, immunization);
        }
        textRecord2.add("   ");

        textRecord2.add("       IMAGING STUDIES:");
        for (ImagingStudy imagingStudy : imagingStudies) {
            imagingStudy(textRecord2, imagingStudy);
        }
        textRecord2.add("   ");
        breakline(textRecord2);

    }


    /**
     * Write a line for a single Condition to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param condition
     *          The condition to add to the export
     */
    private static void condition(List<String> textRecord2, Entry condition) {
        String start = dateFromTimestamp(condition.start);
        String description = condition.codes.get(0).display;

        textRecord2.add("       " + start + " : " + description);
    }

    /**
     * Write a line for a condition that has not ended at the time of the encounter.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param condition
     *          The condition to add to the export
     * @param encounter
     *          The encounter at which the continuing condition is reported
     */
    private static void conditionpast(List<String> textRecord2, Entry condition, Encounter encounter) {
        String start = dateFromTimestamp(condition.start);
        if ((condition.stop == 0L || condition.stop > encounter.stop) && (condition.start < encounter.start)) {
            //checks that the condition hasn't ended by the time of the encounter
            //and began prior to the encounter
            String description = condition.codes.get(0).display;
            textRecord2.add("       " + start + " : " + description);
        } else {
            return;
        }
    }

    /**
     * Write a line for a single Observation to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param observation
     *          The Observation to add to the export
     */
    private static void observation(List<String> textRecord2, Observation observation) {
        String value = ExportHelper.getObservationValue(observation);

        if (value == null) {
            if (observation.observations != null && !observation.observations.isEmpty()) {
            // handoff to multiobservation, ex for blood pressure
            multiobservation(textRecord2, observation);
            }

            // no value so nothing more to report here
            return;
        }

        String obsTime = dateFromTimestamp(observation.start);
        String obsDesc = observation.codes.get(0).display;

        String unit = observation.unit != null ? observation.unit : "";

        textRecord2.add("       " + obsTime + " : " + Strings.padEnd(obsDesc, 40, ' ') + " " + value + " " + unit);
    }
    

    /**
     * Write lines for an Observation with multiple parts to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param observation
     *          The Observation to add to the export
     */
    private static void multiobservation(List<String> textRecord2, Observation observation) {
        String obsTime = dateFromTimestamp(observation.start);
        String obsDesc = observation.codes.get(0).display;

        textRecord2.add("       " + obsTime + " : " + obsDesc);

        for (Observation subObs : observation.observations) {
            String value = ExportHelper.getObservationValue(subObs);
            String unit = subObs.unit != null ? subObs.unit : "";
            String subObsDesc = subObs.codes.get(0).display;
            textRecord2.add("           - " + Strings.padEnd(subObsDesc, 40, ' ') + " " + value + " "
              + unit);
        }
    }

    /**
     * Write a line for a single Procedure to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param procedure
     *          The Procedure to add to the export
     */
    private static void procedure(List<String> textRecord2, Procedure procedure) {
        String procedureTime = dateFromTimestamp(procedure.start);
        String procedureDesc = procedure.codes.get(0).display;
        if (procedure.reasons == null || procedure.reasons.isEmpty()) {
            textRecord2.add("       " + procedureTime + " : " + procedureDesc);
        } else {
            String reason = procedure.reasons.get(0).display;
            textRecord2.add("       " + procedureTime + " : " + procedureDesc + " for " + reason);
        }
    }

    /**
     * Write a line for a single Medication to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param medication
     *          The Medication to add to the export
     */
    private static void medication(List<String> textRecord2, Medication medication) {
        String medTime = dateFromTimestamp(medication.start);
        String medDesc = medication.codes.get(0).display;
        if (medication.reasons == null || medication.reasons.isEmpty()) {
            textRecord2.add("       " + medTime + " : " + medDesc);
        } else {
            String reason = medication.reasons.get(0).display;
            textRecord2.add("       " + medTime + " : " + medDesc + " for " + reason);
        }
    }

    /**
     * Write a line for a medication that is still being taken at the time of the encounter.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param medication
     *          The medication to add to the export
     * @param encounter
     *          The encounter at which the continuing medication is reported
     */
    private static void medicationpast(List<String> textRecord2, Medication medication, Encounter encounter) {
        String medTime = dateFromTimestamp(medication.start);
        String medDesc = medication.codes.get(0).display;
        if ((medication.stop == 0L || medication.stop > encounter.stop) && (medication.start < encounter.start)) {
            //checks that the medication is still being taken at the time of the encounter
            //and began prior to the encounter
            if (medication.reasons == null || medication.reasons.isEmpty()) {
                textRecord2.add("       " + medTime + " : " + medDesc);
            } else {
                String reason = medication.reasons.get(0).display;
                textRecord2.add("       " + medTime + " + " + medDesc  + " for " + reason);
            }
        } else {
            return;
        }
    }

    /**
     * Write a line for a single Immunization to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param immunization
     *          The immunization to add to the export
     */
    private static void immunization(List<String> textRecord2, Entry immunization) {
        String immTime = dateFromTimestamp(immunization.start);
        String immDesc = immunization.codes.get(0).display;
        textRecord2.add("       " + immTime + " : " + immDesc);
    }

    /**
     * Write lines for a single CarePlan to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param careplan
     *          The CarePlan to add to the export
     */
    private static void careplan(List<String> textRecord2, CarePlan careplan) {
        String cpTime = dateFromTimestamp(careplan.start);
        String cpDesc = careplan.codes.get(0).display;
        textRecord2.add("       " + cpTime + " : " + cpDesc);

        if (careplan.reasons != null && !careplan.reasons.isEmpty()) {
            for (Code reason : careplan.reasons) {
                textRecord2.add("                         Reason: " + reason.display);
            }
        }

        if (careplan.activities != null && !careplan.activities.isEmpty()) {
            for (Code activity : careplan.activities) {
            textRecord2.add("                         Activity: " + activity.display);
            }
        }
    }

    /**
     * Write a line for a careplan that is still being followed at the time of the encounter.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param careplan
     *          The careplan to add to the export
     * @param encounter
     *          The encounter at which the continuing careplan is reported
     */
    private static void careplanpast(List<String> textRecord2, CarePlan careplan, Encounter encounter) {
        String cpTime = dateFromTimestamp(careplan.start);
        String cpDesc = careplan.codes.get(0).display;
        if ((careplan.stop == 0L || careplan.stop > encounter.stop) && (careplan.start < encounter.start)){
            //checks that the careplan is still being followed at the time of the encounter
            //and began prior to the encounter
            textRecord2.add("       " + cpTime + " : " + cpDesc);
            if (careplan.reasons != null && !careplan.reasons.isEmpty()) {
                for (Code reason : careplan.reasons) {
                    textRecord2.add("                         Reason: " + reason.display);
                }
            }
            if (careplan.activities != null && !careplan.activities.isEmpty()) {
                for (Code activity : careplan.activities) {
                    textRecord2.add("                         Activity: " + activity.display);
                }   
            }
        } else {
            return;
        }
    }

    /**
     * Write lines for a single ImagingStudy to the exported record.
     *
     * @param textRecord2
     *          Text format record, as a list of lines
     * @param imagingstudy
     *          The ImagingStudy to add to the export
     */
    private static void imagingStudy(List<String> textRecord2, ImagingStudy imagingStudy) {
        String studyTime = dateFromTimestamp(imagingStudy.start);
        String modality = imagingStudy.series.get(0).modality.display;
        String bodySite = imagingStudy.series.get(0).bodySite.display;

        textRecord2.add("       " + studyTime + " : " + modality + ", " + bodySite);
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