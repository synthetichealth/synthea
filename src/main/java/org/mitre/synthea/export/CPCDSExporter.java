package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.modules.HealthInsuranceModule;

import com.google.gson.JsonObject;

public class CPCDSExporter {
	
	/**
	 * CONSTANTS
	 */
	private static final String[] COVERAGE_TYPES = {"HMO", "PPO", "EPO", "POS"};
	private static final String[] GROUP_NAMES = {"Targaryan Analytics",
		  "Bolton Industries",
		  "Tarly Dynamics",
		  "Stark Cryogenic Technologies",
		  "Drogo Expeditions",
		  "Baratheon LLC",
		  "Walker Corp",
		  "Castle Black Securities",
		  "Frey IT",
		  "Lannister Financial"};
	
	private final int[] GROUP_IDS = {(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999),
			(int) randomLongWithBounds(100000000, 999999999)};

	/**
	 * Writer for CPCDS_Patients.csv
	 */
	private FileWriter patients;
	
	/**
	 * Writer for CPCDS_Coverages.csv
	 */
	private FileWriter coverages;
	
	/**
	 * Writer for CPCDS_Claims.csv
	 */
	private FileWriter claims;
	
	/**
	 * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
	 */
	private static final String NEWLINE = System.lineSeparator();
	  
	/**
	 * Constructor for the CSVExporter - initialize the 9 specified files and store
	 * the writers in fields.
	 */
	private CPCDSExporter() {
		try {
      File output = Exporter.getOutputFolder("cpcds", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      if (Boolean.parseBoolean(Config.get("exporter.csv.folder_per_run"))) {
        // we want a folder per run, so name it based on the timestamp
        String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
        String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
        outputDirectory = outputDirectory.resolve(subfolderName);
        outputDirectory.toFile().mkdirs();
      }

      File patientsFile = outputDirectory.resolve("CPCDS_Patients.csv").toFile();
      
      boolean append =
          patientsFile.exists() && Boolean.parseBoolean(Config.get("exporter.csv.append_mode"));
      

      File coverageFile = outputDirectory.resolve("CPCDS_Coverages.csv").toFile();
      File claimsFile = outputDirectory.resolve("CPCDS_Claims.csv").toFile();

      coverages = new FileWriter(coverageFile, append);
      patients = new FileWriter(patientsFile, append);
      claims = new FileWriter(claimsFile, append);

      if (!append) {
        writeCPCDSHeaders();
      }
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
  private void writeCPCDSHeaders() throws IOException {
    patients.write("Member id,Date of birth,Date of death,County,State,Country,Zip,"
        + "Race,Ethnicity,Gender,Name,Relationship to subscriber,Subscriber id");
    patients.write(NEWLINE);
    
    coverages.write("Subscriber id,Coverage type,Coverage status,Start date,"
    		+ "End date,Group id,Group name,Plan,Payer");
    coverages.write(NEWLINE);
    
    String cpcdsClaimColumnHeaders = "Claim service start date,Claim service end date,Claim paid date,"
    		+ "Claim received date,Member admission date,Member discharge date,"
    		+ "Patient account number,Medical record number,"
    		+ "Claim unique identifier,Claim adjusted from identifier,"
    		+ "Claim adjusted to identifier,Claim source inpatient admission code,"
    		+ "Claim inpatient admission type code,Claim bill facility type code,"
    		+ "Claim service classification type code,Claim frequency code,"
    		+ "Claim status code,Claim type,Claim sub type,"
    		+ "Patient discharge status code,Claim billing provider NPI,"
    		+ "Claim billing provider network status,"
    		+ "Claim attending physician NPI,"
    		+ "Claim attending physician network status,Claim site of service NPI,"
    		+ "Claim referring provider NPI,"
    		+ "Claim referring provider network status,"
    		+ "Claim performing provider NPI,"
    		+ "Claim performing provider network status,"
    		+ "Claim operating physician NPI,"
    		+ "Claim operating physician network status,Claim other physician NPI,"
    		+ "Claim other physician network status,Claim rendering physician NPI,"
    		+ "Claim rendering physician network status,"
    		+ "Claim service location NPI,Claim PCP,"
    		+ "Claim total submitted amount,Claim total allowed amount,"
    		+ "Amount paid by patient,Claim amount paid to provider,"
    		+ "Member reimbursement,Claim payment amount,"
    		+ "Claim payment denial code,Claim disallowed amount,"
    		+ "Member paid deductable,Co-insurance liability amount,"
    		+ "Copay amount,Member liability,Claim primary payer code,"
    		+ "Claim primary payer paid amount,Claim secondary payer paid amount,"
    		+ "NDC code,Fill date,Quantity,Days supply,Units,"
    		+ "RX service reference number,Compound code,"
    		+ "DAW product selection code,Fill number,Dispensing status code,"
    		+ "Drug cost,Prescription origin code,IsBrand,"
    		+ "Pharmacy service type code,Patient residence code,"
    		+ "Submission clarification code,Service from date,Line number,"
    		+ "Service to date,Type of service,Place of service code,"
    		+ "Revenue center code,Quantity qualifier code,"
    		+ "Line non covered charged amount,Line amount paid to member,"
    		+ "Line patient paid amount,Line payment amount,"
    		+ "Line member reimbursement,Line payment amount to provider,"
    		+ "Line patient deductible,Line primary payer paid amount,"
    		+ "Line secondary payer paid amount,Line coinsurance amount,"
    		+ "Line submitted amount,Line allowed amount,Line member liability,"
    		+ "Line copay amount,Diagnosis code,Is admitting diagnosis code,"
    		+ "Diagnosis code type,Diagnosis type,Is E code,Procedure code,"
    		+ "Procedure date,Procedure code type,Modifier Code-1,Modifier Code-2,"
    		+ "Modifier Code-3,Modifier Code-4";
    
    claims.write(cpcdsClaimColumnHeaders);
    claims.write(NEWLINE);
  }
  
  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final CPCDSExporter instance = new CPCDSExporter();
  }

  /**
   * Get the current instance of the CSVExporter.
   * 
   * @return the current instance of the CSVExporter.
   */
  public static CPCDSExporter getInstance() {
    return SingletonHolder.instance;
  }
  
  /**
   * Add a single Person's health record info to the CSV records.
   * 
   * @param person Person to write record data for
   * @param time   Time the simulation ended
   * @throws IOException if any IO error occurs
   */
  public void export(Person person, long time) throws IOException {
    String personID = patient(person, time);    
    String type = COVERAGE_TYPES[(int) randomLongWithBounds(0, COVERAGE_TYPES.length - 1)];
    int groupId = GROUP_IDS[(int) randomLongWithBounds(0, GROUP_IDS.length - 1)];
    String groupName = GROUP_NAMES[(int) randomLongWithBounds(0, GROUP_NAMES.length - 1)];
    
    int i = 1;
    for (Encounter encounter : person.record.encounters) {

      String encounterID = UUID.randomUUID().toString();
      String payerId = encounter.claim.payer.uuid.toString();
      long medRecordNumber = randomLongWithBounds(1000000000, 9999999999L);
      CPCDSAttributes encounterAttributes = new CPCDSAttributes(encounter);
    		  
      for (CarePlan careplan : encounter.careplans) {
        coverage(personID, encounterID, careplan, payerId, type, groupId, groupName);
      }
      
      if (encounter.medications.size() == 0 && encounter.procedures.size() == 0) {
      	claim(encounter, personID, encounterID, medRecordNumber, encounterAttributes, i, payerId);
      }
      
      int j = 1;
      for (Medication medication : encounter.medications) {
        medication(encounter, personID, encounterID, medRecordNumber, medication, encounterAttributes, j, time, payerId);
        j++;
      }
      
      int k = 1;
      for (Procedure procedure : encounter.procedures) {
      	procedure(encounter, personID, encounterID, medRecordNumber, procedure, encounterAttributes, k, payerId);
      	k++;
      }
      
      i++;
    }
    
    patients.flush();
    coverages.flush();
    claims.flush();
  }
  
  /**
   * Write a single Patient line, to CPCDS_Patients.csv.
   *
   * @param person Person to write data for
   * @param time Time the simulation ended, to calculate age/deceased status
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private String patient(Person person, long time) throws IOException {
    String personID = (String) person.attributes.get(Person.ID);

    // check if we've already exported this patient demographic data yet,
    // otherwise the "split record" feature could add a duplicate entry.
    if (person.attributes.containsKey("exported_to_cpcds")) {
      return personID;
    } else {
      person.attributes.put("exported_to_cpcds", personID);
    }

    StringBuilder s = new StringBuilder();
    s.append(personID).append(',');
    s.append(dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE))).append(',');
    if (!person.alive(time)) {
      s.append(dateFromTimestamp((Long) person.attributes.get(Person.DEATHDATE))).append(',');
    } else {
    	s.append(',');
    }
    s.append(person.attributes.getOrDefault("county", "")).append(',');
    s.append(person.attributes.getOrDefault(Person.STATE, "")).append(',');
    s.append(person.attributes.getOrDefault("country", "United States"));
    
	  for (String attribute : new String[] {
	      Person.ZIP,
	      Person.RACE,
	      Person.ETHNICITY,
	      Person.GENDER,
	      Person.NAME,
	  }) {
	    String value = (String) person.attributes.getOrDefault(attribute, "");
	    s.append(',').append(clean(value));
	  }

    // TODO: Relationship to Subscriber and Subscriber ID
	  s.append(',').append(person.attributes.getOrDefault("Relationship", "self")).append(',');
	  s.append(personID).append(',');

    s.append(NEWLINE);
    write(s.toString(), patients);

    return personID;
  }
  
  /**
	 * Write a single Coverage CPCDS file
	 *
	 * @param personID    ID of the person prescribed the careplan.
	 * @param encounterID ID of the encounter where the careplan was prescribed
	 * @param careplan    The careplan itself
	 * @throws IOException if any IO error occurs
	 */
	private void coverage(String personID, String encounterID, CarePlan careplan, String payerId, String type, int groupId, String groupName)
			throws IOException {

		StringBuilder s = new StringBuilder();
		s.append(personID).append(',');
		s.append(type).append(',');
		
		if (careplan.stop != 0L) {
			s.append("inactive").append(',');
		} else {
			s.append("active").append(',');
		}
		
		s.append(dateFromTimestamp(careplan.start)).append(',');
		if (careplan.stop != 0L) {
			s.append(dateFromTimestamp(careplan.stop));
		}
		
		s.append(',');
		s.append(groupId).append(',');
		s.append(groupName).append(',');
		s.append(careplan.name).append(',');
		s.append(payerId);
		s.append(NEWLINE);
		write(s.toString(), coverages);
	}

	/**
	 * Write a single Encounter to CPCDS_Claims.csv.
   *
	 * @param encounter The encounter itself
	 * @param personID ID of the person on whom the procedure was performed.
	 * @param encounterID ID of the encounter where the procedure was performed
	 * @param medRecordNumber The medical record number of the person on whom
	 * 												the procedure was performed.
	 * @param attributes 
	 * @param index An index for keeping track of line number
	 * @throws IOException if any IO error occurs
	 */
	private void claim(Encounter encounter, String personID, String encounterID,
			long medRecordNumber, CPCDSAttributes attributes, int index, String payerId)
			throws IOException{
		
    StringBuilder s = new StringBuilder();
    
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(',');
    s.append(personID).append(',');
    s.append(medRecordNumber).append(',');
    s.append(encounterID).append(',');
    s.append(',').append(',');
    Code encounterCoding = encounter.codes.get(0);
    s.append(encounterCoding.code).append(',');
    if (encounter.reason == null) {
      s.append(",");
    } else {
      s.append(encounter.reason.code).append(',');
    }
    s.append(attributes.getCode().toString() + '9').append(',');
    s.append(attributes.getCode()).append(',');
    s.append('9').append(',');
    s.append("complete").append(',');
    s.append(attributes.getType()).append(',');
    s.append(attributes.getSubtype()).append(',');
    s.append("discharged").append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(randomLongWithBounds(1000000000, 9999999999L)).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(',').append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(randomLongWithBounds(1000000000, 9999999999L)).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(encounter.getCost()).append(',');
    s.append(encounter.claim.getTotalClaimCost()).append(',');
    s.append(encounter.claim.getTotalClaimCost() - encounter.claim.getCoveredCost()).append(',');
    s.append("0.00").append(',');
    s.append("0.00").append(',');
    s.append(encounter.claim.getTotalClaimCost()).append(',');
    s.append("approved").append(',');
    s.append(attributes.getDisallowed()).append(',');
    s.append(attributes.getDeductable()).append(',');
    s.append("0.00").append(',');
    s.append(attributes.getCopay()).append(',');
    s.append(attributes.getLiability()).append(',');
    s.append(payerId).append(',');
    s.append(encounter.claim.getTotalClaimCost() - attributes.getLiability()).append(',');
    s.append(attributes.getLiability()).append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',');
    s.append(index).append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',').append(',');
    
    s.append(NEWLINE);
    write(s.toString(), claims);
	}

	/**
	 * Write a single Medication to CPCDS_Claims.csv.
   *
	 * @param encounter The encounter itself
	 * @param personID ID of the person on whom the procedure was performed.
	 * @param encounterID ID of the encounter where the procedure was performed
	 * @param medRecordNumber The medical record number of the person on whom
	 * 												the procedure was performed.
	 * @param medication The medication itself
	 * @param Attributes 
	 * @param index An index for keeping track of line number
	 * @param stopTime Time the simulation ended, to calculate medication stop
	 * @throws IOException if any IO error occurs
	 */
	private void medication(Encounter encounter, String personID, String encounterID,
			long medRecordNumber, Medication medication, CPCDSAttributes attributes,
			int index, long stopTime, String payerId)
			throws IOException {
    
    Map<String, String> msiscodes = new HashMap<String, String>();
    msiscodes.put("outpatient", "11");
    msiscodes.put("inpatient", "01");
    msiscodes.put("ambulatory", "99");
    msiscodes.put("wellness", "13");
    msiscodes.put("emergency", "26");
    msiscodes.put("urgentcare", "12");
    
    long cop;
    String type = attributes.getType();
    String subtype = attributes.getSubtype();
    try {
    	cop = attributes.getCopay() / medication.codes.size();
      type = "PHARMACY";
      subtype = "4700";
    } catch(Exception e) {
    	cop = 0;
    }

    long dispenses = 1;
    long stop = medication.stop;
    if (stop == 0L) {
      stop = stopTime;
    }
    long medDuration = stop - medication.start;
    if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("refills")) {
      dispenses = medication.prescriptionDetails.get("refills").getAsInt();
    } else if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("duration")) {
      JsonObject duration = medication.prescriptionDetails.getAsJsonObject("duration");

      long quantity = duration.get("quantity").getAsLong();
      String unit = duration.get("unit").getAsString();
      long durationMs = Utilities.convertTime(unit, quantity);
      dispenses = medDuration / durationMs;
    } else {
      // assume 1 refill / month
      long durationMs = Utilities.convertTime("months", 1);
      dispenses = medDuration / durationMs;
    }

    if (dispenses < 1) {
      // integer division could leave us with 0,
      // ex. if the active time (start->stop) is less than the provided duration
      // or less than a month if no duration provided
      dispenses = 1;
    }
    
    BigDecimal costDiff = medication.getCost().subtract(BigDecimal.valueOf(medication.claim.getCoveredCost() * dispenses));
    BigDecimal totalCost = medication.getCost().multiply(
        BigDecimal.valueOf(dispenses)).setScale(2, RoundingMode.DOWN);
    
    StringBuilder s = new StringBuilder();
    
      s.append(iso8601Timestamp(encounter.start)).append(',');
      s.append(iso8601Timestamp(encounter.stop)).append(',');
      s.append(iso8601Timestamp(encounter.stop)).append(',');
      s.append(iso8601Timestamp(encounter.start)).append(',');
      s.append(iso8601Timestamp(encounter.start)).append(',');
      s.append(iso8601Timestamp(encounter.stop)).append(',');
      s.append(personID).append(',');
      s.append(medRecordNumber).append(',');
      s.append(encounterID).append(',');
      s.append(',').append(',');
      Code encounterCoding = encounter.codes.get(0);
      s.append(encounterCoding.code).append(',');
      if (encounter.reason == null) {
        s.append(",");
      } else {
        s.append(encounter.reason.code).append(',');
      }
      s.append(attributes.getCode().toString() + '9').append(',');
      s.append(attributes.getCode()).append(',');
      s.append('9').append(',');
      s.append("complete").append(',');
      s.append(type).append(',');
      s.append(subtype).append(',');
      s.append("discharged").append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(attributes.getInout()).append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(attributes.getInout()).append(',');
      s.append(randomLongWithBounds(1000000000, 9999999999L)).append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(attributes.getInout()).append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(attributes.getInout()).append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(attributes.getInout()).append(',');
      s.append(',').append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(attributes.getInout()).append(',');
      s.append(randomLongWithBounds(1000000000, 9999999999L)).append(',');
      s.append(attributes.getNpiProvider()).append(',');
      s.append(encounter.getCost()).append(',');
      s.append(encounter.claim.getTotalClaimCost()).append(',');
      s.append(encounter.claim.getTotalClaimCost() - encounter.claim.getCoveredCost()).append(',');
      s.append("0.00").append(',');
      s.append("0.00").append(',');
      s.append(encounter.claim.getTotalClaimCost()).append(',');
      s.append("approved").append(',');
      s.append(attributes.getDisallowed()).append(',');
      s.append(attributes.getDeductable()).append(',');
      s.append("0.00").append(',');
      s.append(attributes.getCopay()).append(',');
      s.append(attributes.getLiability()).append(',');
      s.append(payerId).append(',');
      s.append(encounter.claim.getTotalClaimCost() - attributes.getLiability()).append(',');
      s.append(attributes.getLiability()).append(',');
      Code medicationCoding = medication.codes.get(0);
      s.append(medicationCoding.code).append(',');
      s.append(dateFromTimestamp(medication.start)).append(',');
      s.append(dispenses).append(',');
      s.append(',').append(',');
      s.append(encounterID).append(',');
      s.append((int) randomLongWithBounds(0, 2)).append(',');
      s.append((int) randomLongWithBounds(0, 9)).append(',');
      s.append(dispenses).append(',');
      String[] dispenseStatusCode = {"", "P", "C"};
      s.append(dispenseStatusCode[(int) randomLongWithBounds(0, dispenseStatusCode.length - 1)]).append(',');
      s.append(medication.getCost()).append(',');
      String[] prescriptionOrigCode = {"", "0", "1", "2", "3", "4", "5"};
      s.append(prescriptionOrigCode[(int) randomLongWithBounds(0, prescriptionOrigCode.length - 1)]).append(',');
      s.append(',');
      String[] pharmacyServiceTypeCode = {"", "01", "02", "03", "04", "05", "06", "07", "08", "99"};
      s.append(pharmacyServiceTypeCode[(int) randomLongWithBounds(0, pharmacyServiceTypeCode.length - 1)]).append(',');
      s.append(',');
      String[] submissionClarificationCode = {"00", "05", "07", "08", "14", "16", "17", "18", "19", "21", "22"};
      s.append(submissionClarificationCode[(int) randomLongWithBounds(0, submissionClarificationCode.length - 1)]).append(',');
      s.append(dateFromTimestamp(medication.start)).append(',');
      s.append(index).append(',');
      s.append(dateFromTimestamp(medication.stop)).append(',');
      s.append(msiscodes.get(encounter.type.toLowerCase())).append(',');
      s.append((int) randomLongWithBounds(1, 99)).append(',');
      s.append((int) randomLongWithBounds(0, 9044)).append(',');
      s.append("UN").append(',');
      s.append(costDiff).append(',');
      s.append("0.00").append(',');
      s.append(costDiff).append(',');
      s.append(totalCost).append(',');
      s.append("0.00").append(',');
      s.append("0.00").append(',');
      s.append(attributes.getDeductable()).append(',');
      s.append(medication.claim.getCoveredCost() * dispenses).append(',');
      s.append(costDiff).append(',');
      s.append("0.00").append(',');
      s.append(totalCost).append(',');
      s.append(totalCost).append(',');
      s.append(costDiff).append(',');
      s.append(cop).append(',');
      
    if (encounter.procedures.size() == 0) {
      s.append(',').append(',').append(',').append(',').append(',').append(',');
      s.append(',').append(',').append(',').append(',').append(',').append(',');
      
      s.append(NEWLINE);
      write(s.toString(), claims);
		} else {
			for (Procedure procedure : encounter.procedures) {
				// TODO: Figure out how to do the index for each of these
				StringBuilder proc = new StringBuilder();
				proc.append(s);
				
				StringBuilder procDiag = procDiagBuilder(procedure);
	      proc.append(procDiag);
	      
	      proc.append(NEWLINE);
	      write(proc.toString(), claims);
			}
		}
	}

	/**
	 * Write a single Procedure to CPCDS_Claims.csv.
   *
	 * @param encounter The encounter itself
	 * @param personID ID of the person on whom the procedure was performed.
	 * @param encounterID ID of the encounter where the procedure was performed
	 * @param medRecordNumber The medical record number of the person on whom
	 * 												the procedure was performed.
	 * @param procedure The procedure itself
	 * @param attributes 
	 * @param index An index for keeping track of line number
	 * @throws IOException if any IO error occurs
	 */
	private void procedure(Encounter encounter, String personID, String encounterID,
			long medRecordNumber, Procedure procedure, CPCDSAttributes attributes, int index, String payerId)
			throws IOException {
		 
    StringBuilder procDiag = procDiagBuilder(procedure);
    StringBuilder s = new StringBuilder();
    
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(',');
    s.append(personID).append(',');
    s.append(medRecordNumber).append(',');
    s.append(encounterID).append(',');
    s.append(',').append(',');
    Code encounterCoding = encounter.codes.get(0);
    s.append(encounterCoding.code).append(',');
    if (encounter.reason == null) {
      s.append(",");
    } else {
      s.append(encounter.reason.code).append(',');
    }
    s.append(attributes.getCode().toString() + '9').append(',');
    s.append(attributes.getCode()).append(',');
    s.append('9').append(',');
    s.append("complete").append(',');
    s.append(attributes.getType()).append(',');
    s.append(attributes.getSubtype()).append(',');
    s.append("discharged").append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(randomLongWithBounds(1000000000, 9999999999L)).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(',').append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(attributes.getInout()).append(',');
    s.append(randomLongWithBounds(1000000000, 9999999999L)).append(',');
    s.append(attributes.getNpiProvider()).append(',');
    s.append(encounter.getCost()).append(',');
    s.append(encounter.claim.getTotalClaimCost()).append(',');
    s.append(encounter.claim.getTotalClaimCost() - encounter.claim.getCoveredCost()).append(',');
    s.append("0.00").append(',');
    s.append("0.00").append(',');
    s.append(encounter.claim.getTotalClaimCost()).append(',');
    s.append("approved").append(',');
    s.append(attributes.getDisallowed()).append(',');
    s.append(attributes.getDeductable()).append(',');
    s.append("0.00").append(',');
    s.append(attributes.getCopay()).append(',');
    s.append(attributes.getLiability()).append(',');
    s.append(payerId).append(',');
    s.append(encounter.claim.getTotalClaimCost() - attributes.getLiability()).append(',');
    s.append(attributes.getLiability()).append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',');
    s.append(index).append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',').append(',');
    s.append(',').append(',').append(',').append(',');
    s.append(procDiag);
    
    s.append(NEWLINE);
    write(s.toString(), claims);
	}
	
	/**
	 * A helper method for creating the procedure diagnosis string.
	 * 
	 * @param procedure The procedure itself
	 * @return a StringBuilder for the procedure diagnosis
	 */
	private StringBuilder procDiagBuilder(Procedure procedure) {
		StringBuilder procDiag = new StringBuilder();
		
		Code procCoding = procedure.codes.get(0);
    procDiag.append(procCoding.code).append(',');
		String[] isAdmittingDiagCode = {"Y", "N", "Y", "Y"};
    procDiag.append(isAdmittingDiagCode[(int) randomLongWithBounds(0, isAdmittingDiagCode.length - 1)]).append(',');
    procDiag.append("SNOMED").append(',');
    procDiag.append("primary").append(',');
    procDiag.append(',');
    procDiag.append(clean(procedure.codes.get(0).toString())).append(',');
    procDiag.append(dateFromTimestamp(procedure.start)).append(',');
    procDiag.append("primary").append(',');
    procDiag.append(',').append(',').append(',').append(',');
    
    return procDiag;
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
   * Helper method to write a line to a File. Extracted to a separate method here
   * to make it a little easier to replace implementations.
   *
   * @param line   The line to write
   * @param writer The place to write it
   * @throws IOException if an I/O error occurs
   */
  private static void write(String line, FileWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
    }
  }
  
  /**
	 * Create a random long between an upper and lower bound. Utilizing
	 * longs to cope with 10+ digit integers.
	 * 
	 * @param lower the lower bound for the integer, inclusive
	 * @param upper the upper bound for the integer, inclusive
	 * @return a random long between the lower and upper bounds
	 */
	private long randomLongWithBounds(long lower, long upper) {
		if (lower >= upper) {
			throw new IllegalArgumentException("upper bound must be greater than lower");
		}

		Random random = new Random();
		long range = upper - lower + 1;
		long fraction = (long) (range * random.nextDouble());
		return fraction + lower;
	}

	private boolean randomTrueOrFalse() {
		Random random = new Random();
		return random.nextBoolean();
	}
	
	/**
	 * A helper class for storing CPCDS derived encounter attributes
	 * to eliminate reusing the same code in multiple areas.
	 */
	private class CPCDSAttributes {

		private Encounter ENCOUNTER;
		private String inout;
		private String subtype;
		private String code;
		private String type;
		
		private long disallowed;
		private long liability;
		private String deductable;
		private long copay;
		
		public CPCDSAttributes(Encounter encounter) {
			ENCOUNTER = encounter;
			
			if (encounter.reason == null) {
	      setInout("out of network");
	      setSubtype("4013");
	      setCode("3");
	      setType("OUTPATIENT");
			} else {
	      setInout("in network");
	      if (randomTrueOrFalse()) {
	        setSubtype("4011");
	        setCode("1");
	      } else {
	        setSubtype("4041");
	        setCode("2");
	      }
	      setType("INPATIENT");
			}
			
	    long baseEcounterCost = encounter.getCost().longValue();
	    long totalClaimCost = (long) encounter.claim.getTotalClaimCost();
	    long payerCoverage = (long) encounter.claim.getCoveredCost();
			if (totalClaimCost >= baseEcounterCost) {
				setDisallowed(0);
			  setLiability(0);
			} else {
				setDisallowed(baseEcounterCost - totalClaimCost);
			  setLiability(baseEcounterCost - totalClaimCost);
			}
	    if (payerCoverage == totalClaimCost) {
	    	setDeductable("True");
	      setCopay(0);
	    } else {
	    	setDeductable("False");
	      setCopay(totalClaimCost - payerCoverage);
	    }
		}
		
		public String getNpiProvider() {
			String npiProvider;
			if (ENCOUNTER.provider == null) {
	      npiProvider = "";
	    } else {
	      npiProvider = ENCOUNTER.provider.getResourceID();
	    }
			return npiProvider;
		}

		public String getInout() {
			return inout;
		}

		private void setInout(String inout) {
			this.inout = inout;
		}

		public String getSubtype() {
			return subtype;
		}

		private void setSubtype(String subtype) {
			this.subtype = subtype;
		}

		public String getCode() {
			return code;
		}

		private void setCode(String code) {
			this.code = code;
		}

		public String getType() {
			return type;
		}

		private void setType(String type) {
			this.type = type;
		}

		public long getDisallowed() {
			return disallowed;
		}

		private void setDisallowed(long disallowed) {
			this.disallowed = disallowed;
		}

		public long getLiability() {
			return liability;
		}

		private void setLiability(long liability) {
			this.liability = liability;
		}

		public String getDeductable() {
			return deductable;
		}

		private void setDeductable(String deductable) {
			this.deductable = deductable;
		}

		public long getCopay() {
			return copay;
		}

		private void setCopay(long copay) {
			this.copay = copay;
		}
	}
}