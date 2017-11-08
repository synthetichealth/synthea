package org.mitre.synthea.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.text.WordUtils;
import org.hl7.fhir.dstu3.model.Condition;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

import com.google.common.base.Strings;

public class TextExporter {

	public static void export(Person person, long time) throws IOException
	{
		// in the text exporter, items are not grouped by encounter
		// so we collect them all into lists grouped by type
		List<Encounter> encounters = person.record.encounters;
		List<Entry> conditions = new ArrayList<>();
		List<Entry> allergies = new ArrayList<>();
		List<Observation> observations = new ArrayList<>();
		List<Procedure> procedures = new ArrayList<>();
		List<Medication> medications = new ArrayList<>();
		List<Entry> immunizations = new ArrayList<>();
		List<CarePlan> careplans = new ArrayList<>();
		
		for (Encounter encounter : person.record.encounters)
		{
			conditions.addAll(encounter.conditions);
			allergies.addAll(encounter.allergies);
			observations.addAll(encounter.observations);
			procedures.addAll(encounter.procedures);
			medications.addAll(encounter.medications);
			immunizations.addAll(encounter.immunizations);
			careplans.addAll(encounter.careplans);
		}
		
		// there may be more Java-y ways to do this, DRY, etc, but this is the easiest to understand
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
		for (Entry allergy : allergies)
		{
			condition(textRecord, allergy);
		}
		breakline(textRecord);
		
		textRecord.add("MEDICATIONS:");
		for (Medication medication : medications)
		{
			medication(textRecord, medication);
		}
		breakline(textRecord);
		
		textRecord.add("CONDITIONS:");
		for (Entry condition : conditions)
		{
			condition(textRecord, condition);
		}
		breakline(textRecord);
		
		textRecord.add("CARE PLANS:");
		for (CarePlan careplan : careplans)
		{
			careplan(textRecord, careplan);
		}
		breakline(textRecord);
		
		textRecord.add("OBSERVATIONS:");
		for (Observation observation : observations)
		{
			observation(textRecord, observation);
		}
		breakline(textRecord);
		
		textRecord.add("PROCEDURES:");
		for (Procedure procedure : procedures)
		{
			procedure(textRecord, procedure);
		}
		breakline(textRecord);
		
		textRecord.add("IMMUNIZATIONS:");
		for (Entry immunization : immunizations)
		{
			immunization(textRecord, immunization);
		}
		breakline(textRecord);
		
		textRecord.add("ENCOUNTERS:");
		for (Encounter encounter : encounters)
		{
			encounter(textRecord, encounter);
		}
		
		// finally write to the file
		File outDirectory = Exporter.getOutputFolder("text", person);
		Path outFilePath = outDirectory.toPath().resolve(Exporter.filename(person, "txt"));
		
		try 
		{
			Files.write(outFilePath, textRecord, StandardOpenOption.CREATE_NEW);
		} catch (IOException e) 
		{
			e.printStackTrace();
		}
	}

	private static void basicInfo(List<String> textRecord, Person person, long endTime)
	{
		String name = (String) person.attributes.get(Person.NAME);
		
		textRecord.add(name);
		textRecord.add(name.replaceAll("[A-Za-z0-9 ]", "="));
		
		String race = (String)person.attributes.get(Person.RACE);
		if (race.equals("hispanic"))
		{
			textRecord.add("Race:                Other");
			String ethnicity = ""; // TODO person.attributes.get(Person.ETHNICITY)
			ethnicity = WordUtils.capitalize( ethnicity.replace('_', ' ') );
			textRecord.add("Ethnicity:           " + ethnicity);
		} else {
			textRecord.add("Race:                " + WordUtils.capitalize(race));
			textRecord.add("Ethnicity:           Non-Hispanic");
		}
		
		textRecord.add("Gender:              " + person.attributes.get(Person.GENDER));
		
		String age = person.alive(endTime) ? Integer.toString(person.ageInYears(endTime)) : "DECEASED";
        textRecord.add("Age:                 " + age);
        
        String birthdate = dateFromTimestamp( (long)person.attributes.get(Person.BIRTHDATE) );
        textRecord.add("Birth Date:          " + birthdate);
        textRecord.add("Marital Status:      " + ""); // TODO person.attributes.get(Person.MARITAL_STATUS)
        
        Provider prov = person.getAmbulatoryProvider();
        if (prov != null)
        {
        	textRecord.add("Outpatient Provider: " + prov.attributes.get("name"));
        }
	}

	private static void encounter(List<String> textRecord, Encounter encounter)
	{
		String encounterTime = dateFromTimestamp(encounter.start);
		
		if (encounter.reason == null)
		{
			textRecord.add(encounterTime + " : " + encounter.codes.get(0).display);
		} else
		{
			textRecord.add(encounterTime + " : Encounter for " + encounter.reason.display);
		}
	}

	private static void condition(List<String> textRecord, Entry condition)
	{
		String start = dateFromTimestamp(condition.start);
        String stop; 
        if (condition.stop == 0L)
        {
        	//     "YYYY-MM-DD"
        	stop = "          ";
        } else
        {
        	stop = dateFromTimestamp(condition.stop);
        }
        String description = condition.codes.get(0).display;
        
		textRecord.add(start + " - " + stop + " : " + description);
	}

	private static void observation(List<String> textRecord, Observation observation)
	{		
		String value = getObservationValue(observation);
		
		if (value == null)
		{
			if (observation.observations != null && !observation.observations.isEmpty())
			{
				// handoff to multiobservation, ex for blood pressure
				multiobservation(textRecord, observation);	
			}
			
			// no value so nothing to report here
			return;
		}

		
		String obsTime = dateFromTimestamp(observation.start);
		String obsDesc = observation.codes.get(0).display;
		
		textRecord.add(obsTime + " : " + Strings.padEnd(obsDesc, 40, ' ') + " " + value);
	}
	
	private static void multiobservation(List<String> textRecord, Observation observation)
	{
		String obsTime = dateFromTimestamp(observation.start);
		String obsDesc = observation.codes.get(0).display;
		
		textRecord.add(obsTime + " : " + obsDesc);
		
		for (Observation subObs : observation.observations)
		{
			String value = getObservationValue(subObs);
			String subObsDesc = subObs.codes.get(0).display;
			textRecord.add("           - " + Strings.padEnd(subObsDesc, 40, ' ') + " " + value);
		}
	}
	
	private static String getObservationValue(Observation observation)
	{
		String value = null;
		
		if (observation.value instanceof Condition)
		{
			Code conditionCode = ((HealthRecord.Entry)observation.value).codes.get(0); 
			value = conditionCode.display;
		} else if (observation.value instanceof Code)
		{
			value = ((Code)observation.value).display;
		} else if (observation.value instanceof String)
		{
			value = (String)observation.value;
		} else if (observation.value instanceof Double)
		{
			// round to 1 decimal place
			value = String.format("%.1f %s ", observation.value, observation.unit);
		} else if (observation.value instanceof Number)
		{
			value = observation.value.toString() + " " + observation.unit;
		} else if (observation.value != null)
		{
			value = observation.value.toString();
		}
		
		return value;
	}

	private static void procedure(List<String> textRecord, Procedure procedure)
	{
		String procedureTime = dateFromTimestamp(procedure.start);
		String procedureDesc = procedure.codes.get(0).display;
		if (procedure.reasons == null || procedure.reasons.isEmpty())
		{
			textRecord.add(procedureTime + " : " + procedureDesc);
		} else
		{
			String reason = procedure.reasons.get(0).display;
			textRecord.add(procedureTime + " : " + procedureDesc + " for " + reason);
		}
	}

	private static void medication(List<String> textRecord,	Medication medication)
	{
		String medTime = dateFromTimestamp(medication.start);
		String medDesc = medication.codes.get(0).display;
		String status = (medication.stop == 0L) ? "CURRENT" : "STOPPED";
		if (medication.reasons == null || medication.reasons.isEmpty())
		{
			textRecord.add(medTime + "[" + status + "] : " + medDesc);
		} else
		{
			String reason = medication.reasons.get(0).display;
			textRecord.add(medTime + "[" + status + "] : " + medDesc + " for " + reason);
		}
	}

	private static void immunization(List<String> textRecord, Entry immunization)
	{
		String immTime = dateFromTimestamp(immunization.start);
		String immDesc = immunization.codes.get(0).display;
		textRecord.add(immTime + " : " + immDesc);
	}

	private static void careplan(List<String> textRecord, CarePlan careplan)
	{
		String cpTime = dateFromTimestamp(careplan.start);
		String cpDesc = careplan.codes.get(0).display;
		String status = (careplan.stop == 0L) ? "CURRENT" : "STOPPED";
		textRecord.add(cpTime + "[" + status + "] : " + cpDesc);
		
		if (careplan.reasons != null && !careplan.reasons.isEmpty())
		{
			for (Code reason : careplan.reasons)
			{
				textRecord.add("                         Reason: " + reason.display);
			}
		}
		
		if (careplan.activities != null && !careplan.activities.isEmpty())
		{
			for (Code activity : careplan.activities)
			{
				textRecord.add("                         Activity: " + activity.display);
			}
		}
	}
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYY-MM-dd");

	/**
	 * Get a date string in the format YYYY-MM-DD from the given time stamp.
	 */
	private static String dateFromTimestamp(long time) {
	  synchronized (DATE_FORMAT) {
	    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6231579
      return DATE_FORMAT.format( new Date(time) );
	  }
	}
	
	// equivalent to '-' * 80 + '\n'
	private static final String SECTION_SEPARATOR = String.join("", Collections.nCopies(80, "-"));
	private static void breakline(List<String> textRecord)
	{
		textRecord.add(SECTION_SEPARATOR);
	}
}
