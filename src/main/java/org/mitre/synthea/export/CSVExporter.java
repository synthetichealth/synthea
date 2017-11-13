package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
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

/**
 * Researchers have requested a simple table-based format 
 * that could easily be imported into any database for analysis. 
 * Unlike other formats which export a single record per patient, 
 * this format generates 9 total files, 
 * and adds lines to each based on the clinical events for each patient. 
 * These files are intended to be analogous to database tables, 
 * with the patient UUID being a foreign key. 
 * Files include: 
 * patients.csv, encounters.csv, allergies.csv, 
 * medications.csv, conditions.csv, careplans.csv, 
 * observations.csv, procedures.csv, and immunizations.csv .
 * 
 * Sample:  
 * - patients.csv <pre>
 * ID,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
 * 5e0d195e-1cd9-494d-8f9a-757c15da2aed,1946-12-14,2015-10-03,999-12-2377,S99962866,false,Mrs.,Miracle267,Ledner332,,Raynor597,M,white,irish,F,Millbury MA,2502 Fisher Manor Boston MA 02132
 * 52082709-06ce-4fde-9c93-cfb4e6542ae1,1968-05-23,,999-17-1808,S99941406,X41451685X,Mrs.,Alda869,Gorczany848,,Funk527,M,white,italian,F,Gardner MA,46973 Velda Gateway Franklin Town MA 02038
 * 8b4c62c8-b116-4b58-9259-466485b0345c,1967-06-22,1985-07-04,999-11-1173,S99955795,,Ms.,Moshe832,Zulauf396,,,,white,english,F,Boston MA,250 Reba Park Carver MA 02330
 * 965c5539-598b-4a9b-a670-e0259667deb8,1934-11-04,2015-06-19,999-63-2195,S99931866,X71888970X,Mr.,Verla554,Roberts329,,,S,white,irish,M,Fall River MA,321 Abdullah Bridge Needham MA 02492
 * 2b28d6c3-9e0c-48d4-99f9-292488133101,1964-08-13,,999-55-5054,S99990374,X68574707X,Ms.,Henderson277,Labadie810,,,S,black,dominican,F,North Attleborough MA,55825 Barrows Prairie Suite 144 Boston MA 02134
 * </pre>
 * 
 * - conditions.csv <pre>
 * START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
 * 1965-10-10,,5e0d195e-1cd9-494d-8f9a-757c15da2aed,918b17f4-e815-44ef-9eeb-41953bbcf7e9,38341003,Hypertension
 * 1966-09-09,,5e0d195e-1cd9-494d-8f9a-757c15da2aed,918b17f4-e815-44ef-9eeb-41953bbcf7e9,15777000,Prediabetes
 * 1988-09-25,,5e0d195e-1cd9-494d-8f9a-757c15da2aed,918b17f4-e815-44ef-9eeb-41953bbcf7e9,239872002,Osteoarthritis of hip
 * 1990-09-01,,5e0d195e-1cd9-494d-8f9a-757c15da2aed,918b17f4-e815-44ef-9eeb-41953bbcf7e9,410429000,Cardiac Arrest
 * </pre>
 */
public class CSVExporter {
  /**
   * Writer for patients.csv.
   */
	private FileWriter patients;
  /**
   * Writer for allergies.csv.
   */
	private FileWriter allergies;
  /**
   * Writer for medications.csv.
   */
	private FileWriter medications;
  /**
   * Writer for conditions.csv.
   */
	private FileWriter conditions;
  /**
   * Writer for careplans.csv.
   */
	private FileWriter careplans;
  /**
   * Writer for observations.csv.
   */
	private FileWriter observations;
  /**
   * Writer for procedures.csv.
   */
	private FileWriter procedures;
  /**
   * Writer for immunizations.csv.
   */
	private FileWriter immunizations;
  /**
   * Writer for encounters.csv.
   */
	private FileWriter encounters;
	
	/**
	 * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
	 */
	private static final String NEWLINE = System.lineSeparator();
	
	/**
	 * Constructor for the CSVExporter -
	 *  initialize the 9 specified files and store the writers in fields.
	 */
	private CSVExporter() {
		try {
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
		} catch (IOException e) {
		  // wrap the exception in a runtime exception.
		  // the singleton pattern below doesn't work if the constructor can throw
		  // and if these do throw ioexceptions there's nothing we can do anyway
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Write the headers to each of the CSV files.
	 * @throws IOException if any IO error occurs
	 */
	private void writeCSVHeaders() throws IOException {
	  patients.write("ID,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS");
	  patients.write(NEWLINE);
	  allergies.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
	  allergies.write(NEWLINE);
	  medications.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION");
	  medications.write(NEWLINE);
	  conditions.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
	  conditions.write(NEWLINE);
	  careplans.write("ID,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION");
	  careplans.write(NEWLINE);
	  observations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS");
	  observations.write(NEWLINE);
	  procedures.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION");
	  procedures.write(NEWLINE);
	  immunizations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
	  immunizations.write(NEWLINE);
	  encounters.write("ID,DATE,PATIENT,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION");
	  encounters.write(NEWLINE);
	}
	
	/**
	 *  Thread safe singleton pattern adopted from 
	 *  https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
	 */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final CSVExporter instance = new CSVExporter();
  }
  
  /**
   * @return the current instance of the CSVExporter. 
   */
  public static CSVExporter getInstance() {
      return SingletonHolder.instance;
  }
	
	/**
	 * Add a single Person's health record info to the CSV records.
	 * @param person Person to write record data for
	 * @param time Time the simulation ended
	 * @throws IOException if any IO error occurs
	 */
	public void export(Person person, long time) throws IOException {
		String personID = patient(person, time);
		
		for (Encounter encounter : person.record.encounters) {
			String encounterID = encounter(personID, encounter);

			for (HealthRecord.Entry condition : encounter.conditions) {
				condition(personID, encounterID, condition);
			}
			
			for (HealthRecord.Entry allergy : encounter.allergies) {
				allergy(personID, encounterID, allergy);
			}
			
			for (Observation observation : encounter.observations) {
				observation(personID, encounterID, observation);
			}
			
			for (Procedure procedure : encounter.procedures) {
				procedure(personID, encounterID, procedure);
			}
			
			for (Medication medication : encounter.medications) {
				medication(personID, encounterID, medication);
			}
			
			for (HealthRecord.Entry immunization : encounter.immunizations) {
				immunization(personID, encounterID, immunization);
			}
			
			for (CarePlan careplan : encounter.careplans) {
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

	/**
	 * Write a single Patient line, to patients.csv.
	 * 
	 * @param person Person to write data for
	 * @param time Time the simulation ended, to calculate age/deceased status
	 * @return the patient's ID, to be referenced as a "foreign key" if necessary
	 * @throws IOException if any IO error occurs
	 */
	private String patient(Person person, long time) throws IOException {
		// ID,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
		StringBuilder s = new StringBuilder();
		
    String personID = (String) person.attributes.get(Person.ID);
		s.append(personID).append(',');
		s.append(dateFromTimestamp((long)person.attributes.get(Person.BIRTHDATE))).append(',');
		if (!person.alive(time)) {
			s.append(dateFromTimestamp(person.record.death));
		}

		for (String attribute : new String[] {
		    Person.IDENTIFIER_SSN,
		    Person.IDENTIFIER_DRIVERS,
		    Person.IDENTIFIER_PASSPORT,
		    Person.NAME_PREFIX,
		    Person.FIRST_NAME,
		    Person.LAST_NAME,
		    Person.NAME_SUFFIX,
		    Person.MAIDEN_NAME,
		    Person.MARITAL_STATUS,
		    Person.RACE,
		    Person.ETHNICITY,
		    Person.GENDER,
		    Person.BIRTHPLACE,
		    Person.ADDRESS
		}) {
		  String value = (String) person.attributes.getOrDefault(attribute, "");
		  s.append(',').append( clean(value) );
		}

		s.append(NEWLINE);
		write(s.toString(), patients);
		
		return personID;
	}

  /**
   * Write a single Encounter line to encounters.csv.
   * 
   * @param personID The ID of the person that had this encounter
   * @param encounter The encounter itself
   * @return The encounter ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
	private String encounter(String personID, Encounter encounter) throws IOException {
		// ID,DATE,PATIENT,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
		String encounterID = UUID.randomUUID().toString();
		s.append(encounterID).append(',');
		s.append(dateFromTimestamp(encounter.start)).append(',');
		s.append(personID).append(',');
		
		Code coding = encounter.codes.get(0);
		s.append(coding.code).append(',');
		s.append(clean(coding.display)).append(',');
		
		if (encounter.reason == null) {
			s.append(','); // reason code & desc
		} else {
			s.append(encounter.reason.code).append(',');
			s.append(clean(encounter.reason.display));
		}

		s.append(NEWLINE);
		write(s.toString(), encounters);
		
		return encounterID;
	}

	/**
	 * Write a single Condition to conditions.csv.
	 * 
	 * @param personID ID of the person that has the condition.
	 * @param encounterID ID of the encounter where the condition was diagnosed
	 * @param condition The condition itself
	 * @throws IOException if any IO error occurs
	 */
	private void condition(String personID, String encounterID,
			Entry condition) throws IOException {
		// START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
		StringBuilder s = new StringBuilder();

		s.append(dateFromTimestamp(condition.start)).append(',');
		if (condition.stop == 0L) {
			s.append(' ').append(',');
		} else {
			s.append(dateFromTimestamp(condition.stop)).append(',');
		}
		
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = condition.codes.get(0);

		s.append(coding.code).append(',');
		s.append(clean(coding.display));

		s.append(NEWLINE);
		write(s.toString(), conditions);
	}

	/**
   * Write a single Allergy to allergies.csv.
   * 
   * @param personID ID of the person that has the allergy.
   * @param encounterID ID of the encounter where the allergy was diagnosed
   * @param allergy The allergy itself
   * @throws IOException if any IO error occurs
   */
	private void allergy(String personID, String encounterID,
			Entry allergy) throws IOException {
		// START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(allergy.start)).append(',');
		if (allergy.stop == 0L) {
			s.append(' ').append(',');
		} else {
			s.append(dateFromTimestamp(allergy.stop)).append(',');
		}
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = allergy.codes.get(0);

		s.append(coding.code).append(',');
		s.append(clean(coding.display));
		
		s.append(NEWLINE);
		write(s.toString(), allergies);
	}

  /**
   * Write a single Observation to observations.csv.
   * 
   * @param personID ID of the person to whom the observation applies.
   * @param encounterID ID of the encounter where the observation was taken
   * @param observation The observation itself
   * @throws IOException if any IO error occurs
   */
	private void observation(String personID, String encounterID,
			Observation observation) throws IOException {
	  
	   if (observation.value == null) {
	      if (observation.observations != null && !observation.observations.isEmpty()) {
	        // just loop through the child observations
	        
	        for (Observation subObs : observation.observations) {
	          observation(personID, encounterID, subObs);  
	        }
	      }
	      
	      // no value so nothing more to report here
	      return;
	    }
	  
		// DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(observation.start)).append(',');
		s.append(personID).append(',');
		s.append(encounterID).append(',');
		
		Code coding = observation.codes.get(0);

		s.append(coding.code).append(',');
		s.append(clean(coding.display)).append(',');
		
		String value = ExportHelper.getObservationValue(observation);
		s.append(value).append(',');
		s.append(observation.unit);
		
		s.append(NEWLINE);
		write(s.toString(), observations);
	}

  /**
   * Write a single Procedure to procedures.csv.
   * 
   * @param personID ID of the person on whom the procedure was performed.
   * @param encounterID ID of the encounter where the procedure was performed
   * @param procedure The procedure itself
   * @throws IOException if any IO error occurs
   */
	private void procedure(String personID, String encounterID,
			Procedure procedure) throws IOException {
		// DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(procedure.start)).append(',');
		s.append(personID).append(',');
		s.append(encounterID).append(',');
		
		Code coding = procedure.codes.get(0);

		s.append(coding.code).append(',');
		s.append(clean(coding.display)).append(',');
		
		if (procedure.reasons.isEmpty()) {
			s.append(','); // reason code & desc
		} else {
			Code reason = procedure.reasons.get(0);
			s.append(reason.code).append(',');
			s.append(clean(reason.display));
		}
		
		s.append(NEWLINE);
		write(s.toString(), procedures);
	}

  /**
   * Write a single Medication to medications.csv.
   * 
   * @param personID ID of the person prescribed the medication.
   * @param encounterID ID of the encounter where the medication was prescribed
   * @param medication The medication itself
   * @throws IOException if any IO error occurs
   */
	private void medication(String personID, String encounterID,
			Medication medication) throws IOException {
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
		s.append(clean(coding.display)).append(',');
		
		if (medication.reasons.isEmpty())
		{
			s.append(','); // reason code & desc
		} else
		{
			Code reason = medication.reasons.get(0);
			s.append(reason.code).append(',');
			s.append(clean(reason.display));
		}
		
		s.append(NEWLINE);
		write(s.toString(), medications);
	}

  /**
   * Write a single Immunization to immunizations.csv.
   * 
   * @param personID ID of the person on whom the immunization was performed.
   * @param encounterID ID of the encounter where the immunization was performed
   * @param immunization The immunization itself
   * @throws IOException if any IO error occurs
   */
	private void immunization(String personID, String encounterID,
			Entry immunization) throws IOException  {
		// DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION
		StringBuilder s = new StringBuilder();
		
		s.append(dateFromTimestamp(immunization.start)).append(',');
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = immunization.codes.get(0);

		s.append(coding.code).append(',');
		s.append(clean(coding.display));
		
		s.append(NEWLINE);
		write(s.toString(), immunizations);
	}

  /**
   * Write a single CarePlan to careplans.csv.
   * 
   * @param personID ID of the person prescribed the careplan.
   * @param encounterID ID of the encounter where the careplan was prescribed
   * @param careplan The careplan itself
   * @throws IOException if any IO error occurs
   */
	private String careplan(String personID, String encounterID,
			CarePlan careplan) throws IOException {
		// ID,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
		StringBuilder s = new StringBuilder();
		
    String careplanID = UUID.randomUUID().toString();
		s.append(careplanID).append(',');
		s.append(dateFromTimestamp(careplan.start)).append(',');
		if (careplan.stop == 0L) {
			s.append(' ').append(',');
		} else {
			s.append(dateFromTimestamp(careplan.stop)).append(',');
		}
		s.append(personID).append(',');
		s.append(encounterID).append(',');

		Code coding = careplan.codes.get(0);

		s.append(coding.code).append(',');
		s.append(coding.display).append(',');

		if (careplan.reasons.isEmpty()) {
			s.append(','); // reason code & desc
		} else {
			Code reason = careplan.reasons.get(0);
			s.append(reason.code).append(',');
			s.append(clean(reason.display));
		}
		s.append(NEWLINE);
		
		write(s.toString(), careplans);
		
		return careplanID;
	}
	
	/**
	 * Replaces commas and line breaks in the source string with a single space.
	 * Null is replaced with the empty string.
	 */
	private static String clean(String src) {
		if (src == null) {
			return "";
		} else {
			return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
		}
	}
	
	/**
	 * Helper method to write a line to a File.
	 * Extracted to a separate method here to make it a little easier to replace implementations.
	 * 
	 * @param line The line to write
	 * @param writer The place to write it
	 * @throws IOException if an I/O error occurs
	 */
	private static void write(String line, FileWriter writer) throws IOException {
	  synchronized (writer) {
	    writer.write(line);
	  }
	}
}
