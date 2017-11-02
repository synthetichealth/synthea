package org.mitre.synthea.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public class CSVExporter 
{
	private FileWriter patients;
	private FileWriter allergies;
	private FileWriter medications;
	private FileWriter conditions;
	private FileWriter careplans;
	private FileWriter observations;
	private FileWriter procedures;
	private FileWriter immunizations;
	private FileWriter encounters;
	
	private static final String NEWLINE = System.lineSeparator();
	
	private CSVExporter()
	{
		try
		{
			File output = Exporter.getOutputFolder("csv", null);
			output.mkdirs();
			Path outputDirectory = output.toPath();
			File patientsFile = outputDirectory.resolve("patients.csv").toFile();
			File allergiesFile = outputDirectory.resolve("allergies.csv").toFile();
			File medicationsFile = outputDirectory.resolve("medications.csv").toFile();
			File conditionsFile = outputDirectory.resolve("conditions.csv").toFile();
			File careplansFile = outputDirectory.resolve("careplans.csv").toFile();
			File observationsFile = outputDirectory.resolve("observations.csv").toFile();
			File proceduresFile = outputDirectory.resolve("procedures.csv").toFile();
			File immunizationsFile = outputDirectory.resolve("immunizations.csv").toFile();
			File encountersFile = outputDirectory.resolve("encounters.csv").toFile();
	
			patients = new FileWriter(patientsFile);
			allergies = new FileWriter(allergiesFile);
			medications = new FileWriter(medicationsFile);
			conditions = new FileWriter(conditionsFile);
			careplans = new FileWriter(careplansFile);
			observations = new FileWriter(observationsFile);
			procedures = new FileWriter(proceduresFile);
			immunizations = new FileWriter(immunizationsFile);
			encounters = new FileWriter(encountersFile);
			writeCSVHeaders();
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private void writeCSVHeaders() throws IOException 
	{
		patients.write("ID,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS\n");
        allergies.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION\n");
        medications.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n");
        conditions.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION\n");
        careplans.write("ID,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n");
        observations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS\n");
        procedures.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n");
        immunizations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION\n");
        encounters.write("ID,DATE,PATIENT,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n");
	}
	
	// thread safe singleton pattern adopted from 
	// https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
	private static class SingletonHolder { 
        public static final CSVExporter instance = new CSVExporter();
    }

    public static CSVExporter getInstance() {
        return SingletonHolder.instance;
    }
	
	
	public synchronized void export(Person person, long time) throws IOException
	{
		String personID = patient(person, time);
		
		for (Encounter encounter : person.record.encounters)
		{
			String encounterID = encounter(personID, encounter);

			for (HealthRecord.Entry condition : encounter.conditions)
			{
				condition(personID, encounterID, condition);
			}
			
			for (HealthRecord.Entry allergy : encounter.allergies)
			{
				allergy(personID, encounterID, allergy);
			}
			
			for (Observation observation : encounter.observations)
			{
				observation(personID, encounterID, observation);
			}
			
			for (Procedure procedure : encounter.procedures)
			{
				procedure(personID, encounterID, procedure);
			}
			
			for (Medication medication : encounter.medications)
			{
				medication(personID, encounterID, medication);
			}
			
			for (HealthRecord.Entry immunization : encounter.immunizations)
			{
				immunization(personID, encounterID, immunization);
			}
			
			for (Report report : encounter.reports)
			{
				report(personID, encounterID, report);
			}
			
			for (CarePlan careplan : encounter.careplans)
			{
				careplan(personID, encounterID, careplan);
			}
		}
		
		patients.flush();
		encounters.flush();
		conditions.flush();
		allergies.flush();
		medications.flush();
		careplans.flush();
		observations.flush();
		procedures.flush();
		immunizations.flush();
	}

	private String patient(Person person, long time) throws IOException 
	{
		String personID = (String) person.attributes.get(Person.ID);
		// ID,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
		StringBuilder s = new StringBuilder();

		Map<String, Object> attr = person.attributes;
		
		s.append(personID).append(',');
		s.append(dateFromTimestamp((long)attr.get(Person.BIRTHDATE))).append(',');
		if (person.alive(time))
		{
			s.append(" ").append(',');
		} else
		{
			s.append(dateFromTimestamp(person.record.death)).append(',');
		}
		
		s.append(0).append(','); // TODO drivers person.attributes.get(Person.IDENTIFIER_DRIVERS)
		s.append(0).append(','); // TODO passport person.attributes.get(Person.IDENTIFIER_PASSPORT)
		s.append(0).append(','); // TODO prefix person.attributes.get(Person.NAME_PREFIX)
		s.append(0).append(','); // TODO first person.attributes.get(Person.FIRST_NAME)
		s.append(0).append(','); // TODO last person.attributes.get(Person.LAST_NAME)
		s.append(0).append(','); // TODO suffix person.attributes.get(Person.NAME_SUFFIX)
		s.append(0).append(','); // TODO maiden person.attributes.get(Person.MAIDEN_NAME)
		s.append(0).append(','); // TODO marital person.attributes.get(Person.MARITAL_STATUS)
		s.append(attr.get(Person.RACE)).append(',');
		s.append(0).append(','); // TODO ethnicity person.attributes.get(Person.ETHNICITY)
		s.append(attr.get(Person.GENDER)).append(',');
		s.append(0).append(','); // TODO birthplace
		
		String address = (String)attr.get(Person.ADDRESS);
		s.append(safeString(address)).append(',');

		s.append(NEWLINE);
		write(s.toString(), patients);
		
		return personID;
	}

	private String encounter(String personID, Encounter encounter) throws IOException  
	{
		String encounterID = UUID.randomUUID().toString();
		// ID,DATE,PATIENT,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(encounterID).append(',');
		s.append(dateFromTimestamp(encounter.start)).append(',');
		s.append(personID).append(',');
		
		Code coding = encounter.codes.get(0);
		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');
		
		if (encounter.reason == null)
		{
			s.append(" , ,"); // reason code & desc
		} else
		{
			s.append(encounter.reason.code).append(',');
			s.append(safeString(encounter.reason.display)).append(',');
		}

		s.append(NEWLINE);
		write(s.toString(), encounters);
		
		return encounterID;
	}

	private void condition(String personID, String encounterID,
			Entry condition) throws IOException  
	{
		// START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
		StringBuilder s = new StringBuilder();

		s.append(dateFromTimestamp(condition.start)).append(',');
		if (condition.stop == 0L)
		{
			s.append(' ').append(',');
		} else
		{
			s.append(dateFromTimestamp(condition.stop)).append(',');
		}
		
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = condition.codes.get(0);

		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');

		s.append(NEWLINE);
		write(s.toString(), conditions);
	}

	private void allergy(String personID, String encounterID,
			Entry allergy) throws IOException 
	{
		// START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(allergy.start)).append(',');
		if (allergy.stop == 0L)
		{
			s.append(' ').append(',');
		} else
		{
			s.append(dateFromTimestamp(allergy.stop)).append(',');
		}
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = allergy.codes.get(0);

		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');
		
		s.append(NEWLINE);
		write(s.toString(), allergies);
	}

	private void observation(String personID, String encounterID,
			Observation observation) throws IOException  
	{
		// DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(observation.start)).append(',');
		s.append(personID).append(',');
		s.append(encounterID).append(',');
		
		Code coding = observation.codes.get(0);

		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');
		
		s.append(observation.value).append(',');
		s.append(observation.unit).append(',');
		
		s.append(NEWLINE);
		write(s.toString(), observations);
	}

	private void procedure(String personID, String encounterID,
			Procedure procedure) throws IOException  
	{
		// DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(procedure.start)).append(',');
		s.append(personID).append(',');
		s.append(encounterID).append(',');
		
		Code coding = procedure.codes.get(0);

		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');
		
		if (procedure.reasons.isEmpty())
		{
			s.append(" , ,"); // reason code & desc
		} else
		{
			Code reason = procedure.reasons.get(0);
			s.append(reason.code).append(',');
			s.append(safeString(reason.display)).append(',');
		}
		
		s.append(NEWLINE);
		write(s.toString(), procedures);
	}

	private void medication(String personID, String encounterID,
			Medication medication) throws IOException 
	{
		// START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(medication.start)).append(',');
		if (medication.stop == 0L)
		{
			s.append(' ').append(',');
		} else
		{
			s.append(dateFromTimestamp(medication.stop)).append(',');
		}
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = medication.codes.get(0);

		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');
		
		if (medication.reasons.isEmpty())
		{
			s.append(" , ,"); // reason code & desc
		} else
		{
			Code reason = medication.reasons.get(0);
			s.append(reason.code).append(',');
			s.append(safeString(reason.display)).append(',');
		}
		
		s.append(NEWLINE);
		write(s.toString(), medications);
	}

	private void immunization(String personID, String encounterID,
			Entry immunization) throws IOException 
	{
		// DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(immunization.start)).append(',');
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = immunization.codes.get(0);

		s.append(coding.code).append(',');
		s.append(safeString(coding.display)).append(',');
		
		s.append(NEWLINE);
		write(s.toString(), immunizations);
	}

	private void report(String personID, String encounterID, Report report) throws IOException 
	{
		// do nothing - reports not exported
	}

	private String careplan(String personID, String encounterID,
			CarePlan careplan) throws IOException 
	{
		String careplanID = UUID.randomUUID().toString();
		// ID,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(careplanID).append(',');
		s.append(dateFromTimestamp(careplan.start)).append(',');
		if (careplan.stop == 0L)
		{
			s.append(' ').append(',');
		} else
		{
			s.append(dateFromTimestamp(careplan.stop)).append(',');
		}
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = careplan.codes.get(0);

		s.append(coding.code).append(',');
		s.append(coding.display).append(',');

		if (careplan.reasons.isEmpty())
		{
			s.append(" , ,"); // reason code & desc
		} else
		{
			Code reason = careplan.reasons.get(0);
			s.append(reason.code).append(',');
			s.append(safeString(reason.display)).append(',');
		}
		
		write(s.toString(), careplans);
		
		return careplanID;
	}
	
	/**
	 * Replaces commas and line breaks in the source string with a single space.
	 * Null is replaced with the empty string.
	 */
	private static String safeString(String src)
	{
		if (src == null)
		{
			return "";
		} else
		{
			return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
		}
	}
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYY-MM-dd");

	private static String dateFromTimestamp(long time)
	{
        return DATE_FORMAT.format( new Date(time) );
	}
	
	private static void write(String line, FileWriter writer) throws IOException
	{
		writer.write(line);
	}
}
