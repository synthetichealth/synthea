package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Device;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

public class CPCDSExporter {

  /**
   * CONSTANTS.
   */
  private static final String[] COVERAGE_TYPES = { "HMO", "PPO", "EPO", "POS" };
  private static final String[] GROUP_NAMES = {
    "Freya Analytics", "Thorton Industries", "Apollo Dynamics", "Cryocast Technologies", 
    "Draugr Expeditions", "Odin Group LLC", "LowKey", "Black Castle Securities",
    "NewWave Technologies", "Realms Financial" };

  private static final UUID[] GROUPIDS = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
    UUID.randomUUID(), UUID.randomUUID() };

  private static final String[] PLAN_NAMES = { "Bronze", "Silver", "Gold" };
  private static final String[] PLAN_IDS = { "00000001", "00000002", "00000003" };
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
   * Writer for Hospitals.csv
   */
  private FileWriter hospitals;
  
  /**
   * Writer for Practitioners.csv
   */
  private FileWriter practitioners;

  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  /**
   * Trackers for Practitioner and Hospital outputs.
   */
  public ArrayList<String> exportedPractitioners = new ArrayList<String>();
  public ArrayList<String> exportedHospitals = new ArrayList<String>();
  public Map<String, String> overwrittenNPIs = new HashMap<String, String>();

  /**
   * Constructor for the CSVExporter - initialize the 9 specified files and store
   * the writers in fields.
   */
  private CPCDSExporter() {
    try {
      File output = Exporter.getOutputFolder("cpcds", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      if (Config.getAsBoolean("exporter.cpcds.folder_per_run")) {
        // we want a folder per run, so name it based on the timestamp
        String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
        String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
        outputDirectory = outputDirectory.resolve(subfolderName);
        outputDirectory.toFile().mkdirs();
      }

      File patientsFile = outputDirectory.resolve("CPCDS_Members.csv").toFile();

      boolean append = patientsFile.exists()
              && Config.getAsBoolean("exporter.cpcds.append_mode");

      File coverageFile = outputDirectory.resolve("CPCDS_Coverages.csv").toFile();
      File claimsFile = outputDirectory.resolve("CPCDS_Claims.csv").toFile();
      File hospitalFile = outputDirectory.resolve("Organizations.csv").toFile();
      File practitionerFile = outputDirectory.resolve("PractitionerRoles.csv").toFile();

      coverages = new FileWriter(coverageFile, append);
      patients = new FileWriter(patientsFile, append);
      claims = new FileWriter(claimsFile, append);
      hospitals = new FileWriter(hospitalFile, append);
      practitioners = new FileWriter(practitionerFile, append);

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
   * 
   * @throws IOException if any IO error occurs
   */
  private void writeCPCDSHeaders() throws IOException {
    patients
        .write("Member id,Date of birth,Date of death,Home_County,Home_State,Home_Country,"
                + "Home_Zip code,Bill_County,Bill_State,Bill_Country,Bill_Zip code,"
                + "Work_County,Work_State,Work_Country,Work_Zip code,"
                + "Race code,Ethnicity,Gender code,Birth sex,Name");
    patients.write(NEWLINE);

    coverages
        .write("Coverage id,Member id,Subscriber id,Dependent number,Coverage type,"
                + "Coverage status,Start date,End date,Group id,Group name,Plan identifier,"
                + "Plan name,Payer identifier,Payer primary identifier,Relationship to subscriber");
    coverages.write(NEWLINE);

    String cpcdsClaimColumnHeaders = "Claim service start date,Claim service end date,"
        + "Claim paid date,Claim received date,Member admission date,Member discharge date,"
        + "Patient account number,Medical record number,Claim unique identifier,"
        + "Claim adjusted from identifier,Claim adjusted to identifier,"
        + "Claim diagnosis related group,"
        + "Claim source inpatient admission code,Claim inpatient admission type code,"
        + "Claim bill facility type code,Claim service classification type code,"
        + "Claim frequency code,Claim processing status code,Claim type,"
        + "Patient discharge status code,Claim payment denial code,Claim primary payer identifier,"
        + "Claim payee type code,Claim payee,Claim payment status code,Claim payer identifier,"
        + "Days supply,RX service reference number,DAW product selection code,Refill number,"
        + "Prescription origin code,Plan reported brand generic code,Pharmacy service type code,"
        + "Patient residence code,Claim billing provider NPI,Claim billing provider network status,"
        + "Claim attending provider NPI,Claim attending provider network status,"
        + "Claim site of service NPI,Claim site of service network status,"
        + "Claim referring provider NPI,Claim referring provider network status,"
        + "Claim performing provider NPI,Claim performing provider network status,"
        + "Claim prescribing provider NPI,Claim prescribing provider network status,Claim PCP NPI,"
        + "Claim total submitted amount,Claim total allowed amount,Amount paid by patient,"
        + "Claim amount paid to provider,Member reimbursement,Claim payment amount,"
        + "Claim disallowed amount,Member paid deductible,Co-insurance liability amount,"
        + "Copay amount,Member liability,Claim primary payer paid amount,Claim discount amount,"
        + "Service (from) date,Line number,Service to date,Type of service,Place of service code,"
        + "Revenue center code,Allowed number of units,Number of units,National drug code,"
        + "Compound code,"
        + "Quantity dispensed,Quantity qualifier code,Line benefit payment status,"
        + "Line payment denial code,Line disallowed amount,Line member reimbursement,"
        + "Line amount paid by patient,Drug cost,Line payment amount,Line amount paid to provider,"
        + "Line patient deductible,Line primary payer paid amount,Line coinsurance amount,"
        + "Line submitted amount,Line allowed amount,Line member liability,Line copay amount,"
        + "Line discount amount,Diagnosis code,Diagnosis description,Present on admission,"
        + "Diagnosis code type,Diagnosis type,Is E code,Procedure code,Procedure description,"
        + "Procedure date,Procedure code type,Procedure type,Modifier Code-1,Modifier Code-2,"
        + "Modifier Code-3,Modifier Code-4";

    claims.write(cpcdsClaimColumnHeaders);
    claims.write(NEWLINE);

    hospitals.write("Id,Name,Address,City,State,ZIP,Phone,Type");
    hospitals.write(NEWLINE);

    practitioners.write("Practitioner NPI,Name,Organization NPI,Code,Specialty");
    practitioners.write(NEWLINE);
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
    String payerId = "";
    String payerName = "";
    String type = COVERAGE_TYPES[(int) randomLongWithBounds(0, COVERAGE_TYPES.length - 1)];
    int groupSelect = (int) randomLongWithBounds(0, GROUPIDS.length - 1);
    UUID groupId = GROUPIDS[groupSelect];
    String groupName = GROUP_NAMES[groupSelect];
    int planSelect = (int) randomLongWithBounds(0, PLAN_NAMES.length - 1);
    String planName = PLAN_NAMES[planSelect];
    String planId = PLAN_IDS[planSelect];
    long start = 999999999999999999L;
    long end = 0;

    for (Encounter encounter : person.record.encounters) {
      String encounterID = person.randUUID().toString();
      UUID medRecordNumber = person.randUUID();
      CPCDSAttributes encounterAttributes = new CPCDSAttributes(encounter);


      if (Config.getAsBoolean("exporter.cpcds.single_payer")) {
        payerId = "b1c428d6-4f07-31e0-90f0-68ffa6ff8c76";
        payerName = clean(Config.get("single_payer.name"));
      } else {
        payerId = encounter.claim.payer.uuid.toString();
        payerName = encounter.claim.payer.getName();
      }

      for (CarePlan careplan : encounter.careplans) {
        if (careplan.start < start) {
          start = careplan.start;
        }
        if (careplan.stop > end) {
          end = careplan.stop;
        }
      }
      if (start == 999999999999999999L) {
        start = end;
      }
      String coverageID = coverage(person, personID, start, end, payerId, type, groupId, groupName,
              planName, planId);
      claim(person, encounter, personID, encounterID, medRecordNumber, encounterAttributes, payerId,
              coverageID);
      hospital(encounter, encounterAttributes, payerName);
    }

    
    patients.flush();
    coverages.flush();
    claims.flush();
    practitioners.flush();
    hospitals.flush();
  }

  /**
   * Write a single Patient line, to CPCDS_Members.csv.
   *
   * @param person Person to write data for
   * @param time   Time the simulation ended, to calculate age/deceased status
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
      s.append(dateFromTimestamp((long) person.attributes.get(Person.DEATHDATE))).append(',');
    } else {
      s.append(',');
    }
    s.append(person.attributes.getOrDefault("county", "")).append(',');
    s.append(person.attributes.getOrDefault(Person.STATE, "")).append(',');
    s.append(person.attributes.getOrDefault("country", "United States")).append(',');
    s.append(person.attributes.getOrDefault(Person.ZIP, "")).append(',');

    s.append(person.attributes.getOrDefault("county", "")).append(',');
    s.append(person.attributes.getOrDefault(Person.STATE, "")).append(',');
    s.append(person.attributes.getOrDefault("country", "United States")).append(',');
    s.append(person.attributes.getOrDefault(Person.ZIP, ""));

    s.append(",,,,");

    String[] attributes = { Person.RACE, Person.ETHNICITY, Person.GENDER,
      Person.GENDER, Person.NAME };
    for (String attribute : attributes) {
      String value = (String) person.attributes.getOrDefault(attribute, "");
      s.append(',').append(clean(value));
    }

    s.append(NEWLINE);
    write(s.toString(), patients);

    return personID;
  }

  /**
   * Write a single Coverage CPCDS file.
   *
   * @param rand        Source of randomness to use when generating ids etc
   * @param personID    ID of the person prescribed the careplan.
   * @param encounterID ID of the encounter where the careplan was prescribed
   * @param careplan    The careplan itself
   * @throws IOException if any IO error occurs
   */
  private String coverage(RandomNumberGenerator rand, String personID, long start, long stop,
          String payerId, String type, UUID groupId, String groupName, String name, 
          String planId) throws IOException {

    StringBuilder s = new StringBuilder();
    String coverageID = rand.randUUID().toString();
    s.append(coverageID).append(',');
    s.append(personID).append(',');
    s.append(personID).append(',');
    s.append('0').append(',');
    s.append(type).append(',');

    if (stop != 0L) {
      s.append("inactive").append(',');
    } else {
      s.append("active").append(',');
    }

    s.append(dateFromTimestamp(start)).append(',');
    if (stop != 0L) {
      s.append(dateFromTimestamp(stop));
    }

    s.append(',');
    s.append(groupId).append(',');
    s.append(groupName).append(',');
    s.append(planId).append(',');
    s.append(name).append(',');
    s.append(payerId).append(',');
    s.append(payerId).append(',');
    s.append("self");
    s.append(NEWLINE);
    write(s.toString(), coverages);
    return coverageID;
  }

  /**
   * Method to write a single Claims file. Take an encounter in the parameters and
   * processes Diagnoses, Procedures, and Pharmacy claims for each one, in order.
   * 
   * @param rand            Source of randomness to use when generating ids etc
   * @param encounter       The encounter object itself
   * @param personID        The Id of the involved patient
   * @param encounterID     The Id of the encounter
   * @param medRecordNumber The patients Medical Record Number
   * @param attributes      Calculated attributes for the entire encounter
   * @param payerId         The Id of the payer
   * @throws IOException Throws this exception
   */
  private void claim(RandomNumberGenerator rand, Encounter encounter, String personID, 
          String encounterID, UUID medRecordNumber, CPCDSAttributes attributes, String payerId,
          String coverageID) throws IOException {

    StringBuilder s = new StringBuilder();

    int i = 1;

    while (i <= attributes.getLength()) {
      // admin
      String billType = attributes.getBillTypeCode();
      String[] adminSection = {
        String.valueOf(dateFromTimestamp(encounter.start)),
        String.valueOf(dateFromTimestamp(encounter.stop)),
        String.valueOf(dateFromTimestamp(encounter.stop)),
        String.valueOf(dateFromTimestamp(encounter.start)),
        String.valueOf(dateFromTimestamp(encounter.start)),
        String.valueOf(dateFromTimestamp(encounter.stop)),
        personID.toString(),
        medRecordNumber.toString(),
        encounterID,
        "",
        "",
        "",
        attributes.getSourceAdminCode(),
        attributes.getAdmissionTypeCode(),
        Character.toString(billType.charAt(0)),
        Character.toString(billType.charAt(1)),
        Character.toString(billType.charAt(2)),
        attributes.getProcStatus(),
        attributes.getClaimType(),
        attributes.getDischarge(),
        attributes.getDenialCode(),
        coverageID,
        attributes.getPayeeType(),
        personID,
        attributes.getPaymentType(),
        coverageID
      };

      StringBuilder admin = new StringBuilder();
      for (String item : adminSection) {
        admin.append(item).append(',');
      }
      String adminString = admin.toString();

      // provider
      practitioner(
              encounter.clinician.attributes.get("specialty").toString(),
              attributes.getNpiProvider(),
              attributes.getServiceSiteNPI(),
              encounter.clinician.getFullname());

      String[] providerSection = {
        attributes.getNpiProvider(),
        attributes.getNetworkStatus(),
        attributes.getNpiProvider(),
        attributes.getNetworkStatus(),
        attributes.getServiceSiteNPI(),
        attributes.getNetworkStatus(),
        attributes.getNpiProvider(),
        attributes.getNetworkStatus(),
        attributes.getNpiProvider(),
        attributes.getNetworkStatus(),
        attributes.getNpiPrescribingProvider(),
        attributes.getNetworkStatus(),
        attributes.getNpiProvider()
      };

      StringBuilder provider = new StringBuilder();
      for (String item : providerSection) {
        provider.append(item).append(',');
      }
      String providerString = provider.toString();

      // totals
      double totalCost =  attributes.getTotalClaimCost(); //encounter.claim.getTotalClaimCost();
      double coveredCost = encounter.claim.getCoveredCost();
      double disallowed = totalCost - coveredCost;
      double patientPaid;
      double memberReimbursement;
      double paymentAmount;
      double toProvider;
      double deductible = encounter.claim.person.getHealthcareCoverage();
      double liability;
      double copay = 0.00;

      if (disallowed > 0) {
        memberReimbursement = 0.00;
        patientPaid = disallowed;
      } else {
        memberReimbursement = disallowed - 2 * disallowed;
        disallowed = 0.00;
        patientPaid = 0.00;
      }

      paymentAmount = coveredCost + patientPaid;
      toProvider = paymentAmount;
      liability = totalCost - paymentAmount;

      String[] claimTotalsSection = {
        String.valueOf(paymentAmount),
        String.valueOf(totalCost),
        String.valueOf(patientPaid),
        String.valueOf(toProvider),
        String.valueOf(memberReimbursement),
        String.valueOf(paymentAmount),
        String.valueOf(disallowed),
        String.valueOf(deductible),
        String.valueOf(""),
        String.valueOf(copay),
        String.valueOf(liability),
        String.valueOf(coveredCost),
        String.valueOf(0.00)
      };

      StringBuilder totals = new StringBuilder();
      for (String item : claimTotalsSection) {
        totals.append(item).append(',');
      }
      String totalsString = totals.toString();

      String pharmacyEMPTY = ",,,,,,," + attributes.getResidence() + ",";
      String procedureEMPTY = ",,,,,,,,";

      // diagnosis
      for (Entry condition : encounter.conditions) {
        StringBuilder cond = new StringBuilder();
        String presentOnAdmission;

        String[] poaCodes = { "Y", "N", "U", "W" };
        presentOnAdmission = poaCodes[(int) randomLongWithBounds(0, 3)];
        cond.append(adminString);
        cond.append(pharmacyEMPTY);
        cond.append(providerString);
        cond.append(totalsString);

        cond.append(dateFromTimestamp(condition.start)).append(',');
        cond.append(i).append(',');
        cond.append(dateFromTimestamp(condition.stop)).append(',');
        cond.append("").append(',');
        cond.append(attributes.getPlaceOfService()).append(',');
        cond.append(attributes.getRevenueCenterCode()).append(',');
        cond.append("").append(',');
        cond.append("").append(',');
        cond.append("").append(',');
        cond.append("").append(',');
        cond.append("").append(',');
        cond.append("").append(',');
        cond.append(attributes.getBenefitPaymentStatus()).append(',');
        cond.append(attributes.getDenialCode()).append(',');

        BigDecimal cost = condition.getCost();

        cond.append(0.00).append(',');
        cond.append(0.00).append(',');
        cond.append(0.00).append(',');
        cond.append("").append(',');
        cond.append(cost).append(',');
        cond.append(cost).append(',');
        cond.append(encounter.claim.person.getHealthcareCoverage()).append(',');
        cond.append(cost).append(',');
        cond.append(0.00).append(',');
        cond.append(cost).append(',');
        cond.append(cost).append(',');
        cond.append(0.00).append(',');
        cond.append(0.00).append(',');
        cond.append(0.00).append(',');

        Code coding = condition.codes.get(0);
        String diagnosisCode = "SNOMED";
        String diagnosisType = "principal";

        cond.append(coding.code).append(',');
        cond.append(clean(coding.display)).append(',');
        cond.append(presentOnAdmission).append(',');
        cond.append(diagnosisCode).append(',');
        cond.append(diagnosisType).append(',');
        cond.append("").append(',');

        cond.append(procedureEMPTY).append(NEWLINE);
        s.append(cond.toString());
        i++;
      }
      // procedures
      int k = 0;
      for (Procedure procedure : encounter.procedures) {
        String presentOnAdmission;
        String diagnosisCode = "SNOMED";
        String diagnosisType = "principal";
        String procedureType;
        if (k == 0) {
          procedureType = "primary";
        } else {
          procedureType = "secondary";
        }

        String[] poaCodes = { "Y", "N", "U", "W" };
        presentOnAdmission = poaCodes[(int) randomLongWithBounds(0, 3)];

        StringBuilder proc = new StringBuilder();
        proc.append(adminString);
        proc.append(",").append(",").append(",").append(",").append(",").append(",");
        proc.append("01,").append(attributes.getResidence()).append(',');
        proc.append(providerString);
        proc.append(totalsString);

        String typeOfService = "01";
        if (attributes.getNetworkStatus().equals("out")) {
          typeOfService = "11";
        }

        proc.append(dateFromTimestamp(procedure.start)).append(',');
        proc.append(i).append(',');
        proc.append(dateFromTimestamp(procedure.stop)).append(',');
        proc.append(typeOfService).append(',');
        proc.append(attributes.getPlaceOfService()).append(',');
        proc.append(attributes.getRevenueCenterCode()).append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append(attributes.getBenefitPaymentStatus()).append(',');
        proc.append(attributes.getDenialCode()).append(',');

        BigDecimal cost = procedure.getCost();

        proc.append(0.00).append(',');
        proc.append(0.00).append(',');
        proc.append(0.00).append(',');
        proc.append("").append(',');
        proc.append(cost).append(',');
        proc.append(cost).append(',');
        proc.append(encounter.claim.person.getHealthcareCoverage()).append(',');
        proc.append(cost).append(',');
        proc.append(0.00).append(',');
        proc.append(cost).append(',');
        proc.append(cost).append(',');
        proc.append(0.00).append(',');
        proc.append(0.00).append(',');
        proc.append(0.00).append(',');

        if (procedure.reasons.size() != 0) {
          Code reasons = procedure.reasons.get(0);
          proc.append(reasons.code).append(',');
          proc.append(clean(reasons.display)).append(',');
          proc.append(presentOnAdmission).append(',');
          proc.append(diagnosisCode).append(',');
          proc.append(diagnosisType).append(',');
        } else {
          proc.append("").append(',');
          proc.append("").append(',');
          proc.append("").append(',');
          proc.append("").append(',');
          proc.append("").append(',');
        }

        proc.append("").append(',');

        Code procedureCode = procedure.codes.get(0);
        proc.append(procedureCode.code).append(',');
        proc.append(clean(procedureCode.display)).append(',');
        proc.append(dateFromTimestamp(procedure.start)).append(',');
        proc.append(diagnosisCode).append(',');
        proc.append(procedureType).append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append("").append(',');
        proc.append("").append(NEWLINE);

        s.append(proc.toString());
        i++;
      }

      // pharmacy
      for (Medication medication : encounter.medications) {
        StringBuilder med = new StringBuilder();
        String presentOnAdmission;
        String diagnosisCode = "SNOMED";
        String diagnosisType = "principal";

        String[] poaCodes = { "Y", "N", "U", "W" };
        presentOnAdmission = poaCodes[(int) randomLongWithBounds(0, 3)];

        String[] brandGenericList = { "b", "g" };
        String brandGenericCode = brandGenericList[(int) randomLongWithBounds(0, 1)];
        String[] dawCodeList = { "1", "2", "3", "4", "7", "1", "3", "5", "8" };
        String dawCode;
        if (brandGenericCode.equals("b")) {
          dawCode = dawCodeList[(int) randomLongWithBounds(0, 4)];
        } else {
          if (brandGenericCode.equals("g")) {
            dawCode = dawCodeList[(int) randomLongWithBounds(5, 8)];
          } else {
            dawCode = "0";
          }
        }
        /*
         * {"dosage": {"amount":1,"frequency":2,"period":1,"unit":"days"},
         * "duration":{"quantity":2,"unit":"weeks"}, "instructions":[ {
         * "system":"SNOMED-CT", "code":"code", "display":"display string"} ] }
         */

        JsonObject medicationDetails = medication.prescriptionDetails;
        Dictionary<String, Integer> dayMultiplier = new Hashtable<String, Integer>();
        dayMultiplier.put("hours", 1);
        dayMultiplier.put("days", 1);
        dayMultiplier.put("weeks", 2);
        dayMultiplier.put("months", 30);
        dayMultiplier.put("years", 365);

        int dailyDosage;
        int daysSupply;
        JsonObject dosage;
        JsonObject duration;

        if (medicationDetails == null || medicationDetails.has("as_needed")) {
          dailyDosage = 0;
          daysSupply = 0;
        } else {
          if (medicationDetails != null && medicationDetails.has("dosage")) {
            dosage = medicationDetails.get("dosage").getAsJsonObject();
            if (dosage.has("amount") == false) {
              dosage.addProperty("amount", 0);
            }
            if (dosage.has("frequency") == false) {
              dosage.addProperty("frequency", 0);
            }
            if (dosage.has("period") == false) {
              dosage.addProperty("period", 0);
            }
            if (dosage.has("unit") == false) {
              dosage.addProperty("unit", "days");
            }
          } else {
            dosage = new JsonObject();
            dosage.addProperty("amount", 0);
            dosage.addProperty("frequency", 0);
            dosage.addProperty("period", 0);
            dosage.addProperty("unit", "days");
          }
          if (medicationDetails != null && medicationDetails.has("duration")) {
            duration = medicationDetails.get("duration").getAsJsonObject();
            if (duration.has("quantity") == false) {
              duration.addProperty("quantity", 0);
            }
            if (duration.has("unit") == false) {
              duration.addProperty("unit", "days");
            }
          } else {
            duration = new JsonObject();
            duration.addProperty("quantity", 0);
            duration.addProperty("unit", "days");
          }

          dailyDosage = dosage.get("amount").getAsInt()
                  * dosage.get("frequency").getAsInt()
                  * dosage.get("period").getAsInt()
                  * (int) dayMultiplier.get(dosage.get("unit").getAsString());
          daysSupply = duration.get("quantity").getAsInt()
                  * dayMultiplier.get(duration.get("unit").getAsString());
        }

        UUID rxRef = rand.randUUID();

        String[] serviceTypeList = { "01", "04", "06" };
        String serviceType = serviceTypeList[(int) randomLongWithBounds(0, 2)];

        med.append(adminString);

        med.append(daysSupply).append(',');
        med.append(rxRef).append(',');
        med.append(dawCode).append(',');
        med.append("0").append(',');
        med.append("0").append(',');
        med.append(brandGenericCode).append(',');
        med.append(serviceType).append(',');
        med.append(attributes.getResidence()).append(',');

        med.append(providerString);
        med.append(totalsString);

        Code coding = medication.codes.get(0);

        med.append(dateFromTimestamp(medication.start)).append(',');
        med.append(i).append(',');
        med.append(dateFromTimestamp(medication.stop)).append(',');
        med.append("16").append(',');
        med.append("01").append(',');
        med.append(attributes.getRevenueCenterCode()).append(',');
        med.append(dailyDosage * daysSupply).append(',');
        med.append(dailyDosage * daysSupply).append(',');
        med.append(coding.code).append(',');
        med.append(randomLongWithBounds(0, 2)).append(',');
        med.append(dailyDosage * daysSupply).append(',');
        med.append("UN").append(',');
        med.append(attributes.getBenefitPaymentStatus()).append(',');
        med.append(attributes.getDenialCode()).append(',');

        BigDecimal cost = medication.getCost();

        med.append(0.00).append(',');
        med.append(0.00).append(',');
        med.append(0.00).append(',');
        med.append((dailyDosage == 0 || daysSupply == 0
                ? 0 : cost.longValue() / (dailyDosage * daysSupply))).append(',');
        med.append(cost).append(',');
        med.append(cost).append(',');
        med.append(encounter.claim.person.getHealthcareCoverage()).append(',');
        med.append(cost).append(',');
        med.append(0.00).append(',');
        med.append(cost).append(',');
        med.append(cost).append(',');
        med.append(0.00).append(',');
        med.append(0.00).append(',');
        med.append(0.00).append(',');

        if (medication.reasons.size() != 0) {
          Code reasons = medication.reasons.get(0);
          med.append(reasons.code).append(',');
          med.append(clean(reasons.display)).append(',');
          med.append(presentOnAdmission).append(',');
          med.append(diagnosisCode).append(',');
          med.append(diagnosisType).append(',');
        } else {
          med.append("").append(',');
          med.append("").append(',');
          med.append("").append(',');
          med.append("").append(',');
          med.append("").append(',');
        }

        med.append("").append(',');
        med.append(procedureEMPTY).append(NEWLINE);

        s.append(med.toString());
        i++;
      }

      // Devices
      for (Device device : encounter.devices) {
        StringBuilder dev = new StringBuilder();
        dev.append(adminString);
        dev.append(",").append(",").append(",").append(",").append(",").append(",");
        dev.append("01,").append(attributes.getResidence()).append(',');
        dev.append(providerString);
        dev.append(totalsString);

        String typeOfService = "01";
        if (attributes.getNetworkStatus().equals("out")) {
          typeOfService = "11";
        }

        dev.append(dateFromTimestamp(device.start)).append(',');
        dev.append(i).append(',');
        dev.append(dateFromTimestamp(device.stop)).append(',');
        dev.append(typeOfService).append(',');
        dev.append(attributes.getPlaceOfService()).append(',');
        dev.append(attributes.getRevenueCenterCode()).append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append(attributes.getBenefitPaymentStatus()).append(',');
        dev.append(attributes.getDenialCode()).append(',');

        BigDecimal cost = device.getCost();

        dev.append(0.00).append(',');
        dev.append(0.00).append(',');
        dev.append(0.00).append(',');
        dev.append("").append(',');
        dev.append(cost).append(',');
        dev.append(cost).append(',');
        dev.append(encounter.claim.person.getHealthcareCoverage()).append(',');
        dev.append(cost).append(',');
        dev.append(0.00).append(',');
        dev.append(cost).append(',');
        dev.append(cost).append(',');
        dev.append(0.00).append(',');
        dev.append(0.00).append(',');
        dev.append(0.00).append(',');

        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');

        dev.append("").append(',');

        String diagnosisCode = "SNOMED";
        String deviceType = "";

        Code deviceCode = device.codes.get(0);
        dev.append(deviceCode.code).append(',');
        dev.append(clean(deviceCode.display)).append(',');
        dev.append(dateFromTimestamp(device.start)).append(',');
        dev.append(diagnosisCode).append(',');
        dev.append(deviceType).append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(',');
        dev.append("").append(NEWLINE);

        s.append(dev.toString());
        i++;
      }

    }

    write(s.toString(), claims);
  }

  /**
   * Write practitioner data to csv file.
   * 
   * @param encounter the encounter
   * @param attributes the attributes
   * @throws IOException on failure
   */
  private void practitioner(String specialty, String providerNPI, String organizationNPI,
          String providerName) throws IOException {
    
    StringBuilder s = new StringBuilder();
    // Practitioner NPI,Organization NPI,Specialty

    Boolean continueFlag = true;
    if (exportedPractitioners.contains(providerNPI + organizationNPI)) {
      continueFlag = false;
    }
    if (continueFlag == true) {
      exportedPractitioners.add(providerNPI + organizationNPI);
      s.append(clean(providerNPI)).append(',');
      s.append(providerName).append(',');
      s.append(clean(organizationNPI)).append(',');
      s.append("provider").append(',');
      s.append(clean(specialty)).append(NEWLINE);
      
      write(s.toString(), practitioners);
    }
  }

  /**
   * Write data for hospitals to csv file.
   * @param encounter the encounter
   * @param attributes the attributes
   * @param payerName payers name (not currently used)
   * @throws IOException on failure
   */
  private void hospital(Encounter encounter, CPCDSAttributes attributes, String payerName)
          throws IOException {
    StringBuilder s = new StringBuilder();
    // Id,Name,Address,City,State,ZIP,Phone,Type,Ownership
    
    Boolean continueFlag = true;
    if (exportedHospitals.contains(attributes.getServiceSiteNPI())) {
      continueFlag = false;
    }
    
    if (continueFlag && encounter.provider != null) {
      s.append(clean(attributes.getServiceSiteNPI())).append(',');
      s.append(clean(encounter.provider.name)).append(',');
      s.append(clean(encounter.provider.address)).append(',');
      s.append(clean(encounter.provider.city)).append(',');
      s.append(clean(encounter.provider.state)).append(',');
      s.append(clean(encounter.provider.zip)).append(',');
      s.append(clean(encounter.provider.phone)).append(',');
      s.append(clean(encounter.provider.rawType)).append(NEWLINE);
      
      exportedHospitals.add(attributes.getServiceSiteNPI());

      write(s.toString(), hospitals);
    }
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
   * Create a random long between an upper and lower bound. Utilizing longs to
   * cope with 10+ digit integers.
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

  /**
   * A helper class for storing CPCDS derived encounter attributes to eliminate
   * reusing the same code in multiple areas.
   */
  private class CPCDSAttributes {
    private String sourceAdminCode;
    private String billTypeCode;
    private String procStatus;
    private String networkStatus;
    private String claimType;
    private final String admissionTypeCode = "other";
    private final String discharge = "home";
    private final String denialCode = "";
    private String benefitPaymentStatus;
    private final String payeeType = "subscriber";
    private final String paymentType = "complete";
    private String serviceSiteNPI;
    private Integer length;
    private final String residence = "01";
    private String placeOfService;
    private String revenueCenterCode;
    private String npiProvider;
    private String npiPrescribingProvider;
    private double totalClaimCost = 0.00;

    /**
     * Constructor. Takes the encounter and processes relevant encounters based on
     * its data.
     * 
     * @param encounter The encounter object
     */
    public CPCDSAttributes(Encounter encounter) {
      isInpatient(encounter.type);

      String doctorNPI = (encounter.clinician != null
              ? String.valueOf(encounter.clinician.identifier) : "");
      String hospitalNPI = (encounter.provider != null
              ? String.valueOf(encounter.provider.id) : "");
      String newHospitalID = String.valueOf(randomLongWithBounds(100000, 999999))
              + String.valueOf(randomLongWithBounds(100000, 999999));
      String newPractitionerID = String.valueOf(randomLongWithBounds(100000, 999999))
              + String.valueOf(randomLongWithBounds(100000, 999999));

      if (overwrittenNPIs.containsKey(hospitalNPI)) {
        hospitalNPI = overwrittenNPIs.get(hospitalNPI);
      } else {
        overwrittenNPIs.put(hospitalNPI, newHospitalID);
        hospitalNPI = newHospitalID;
      }
      if (overwrittenNPIs.containsKey(doctorNPI)) {
        doctorNPI = overwrittenNPIs.get(doctorNPI);
      } else {
        overwrittenNPIs.put(doctorNPI, newPractitionerID);
        doctorNPI = newPractitionerID;
      }
      

      if (encounter.medications.size() != 0 && encounter.procedures.size() == 0) {
        setClaimType("pharmacy");
        setNpiPrescribingProvider(doctorNPI);
      } else {
        if (encounter.devices.size() > 0
                && encounter.medications.size() == 0
                && encounter.procedures.size() == 0) {
          setClaimType("professional-nonclinician");
        } else {
          if (this.sourceAdminCode.equals("outp")) {
            setClaimType("outpatient-facility");
          } else {
            setClaimType("inpatient-facility");
          }
        }
        setNpiPrescribingProvider("");
      }

      String[] statuses = { "ar001", "ar002" };
      setBenefitPaymentStatus(statuses[(int) randomLongWithBounds(0, 1)]);

      setServiceSiteNPI(hospitalNPI);
      setLength(encounter.medications.size()
              + encounter.procedures.size()
              + encounter.conditions.size()
              + encounter.devices.size());

      if (networkStatus == "out") {
        setPlaceOfService("19");
      } else {
        if (networkStatus == "in") {
          setPlaceOfService("21");
        } else {
          setPlaceOfService("20");
        }
      }

      if (getSourceAdminCode() == "emd") {
        setRevenueCenterCode("0450");
      } else {
        setRevenueCenterCode("");
      }

      setNpiProvider(doctorNPI);

      for (Entry condition : encounter.conditions) {
        totalClaimCost = totalClaimCost + condition.getCost().doubleValue();
      }
      for (Procedure procedure : encounter.procedures) {
        totalClaimCost = totalClaimCost + procedure.getCost().doubleValue();
      }
      for (Medication medication : encounter.medications) {
        totalClaimCost = totalClaimCost + medication.getCost().doubleValue();
      }
      for (Device device : encounter.devices) {
        totalClaimCost = totalClaimCost + device.getCost().doubleValue();
      }
    }

    public double getTotalClaimCost() {
      return totalClaimCost;
    }

    public String getNpiPrescribingProvider() {
      return npiPrescribingProvider;
    }

    public void setNpiPrescribingProvider(String npiPrescribingProvider) {
      this.npiPrescribingProvider = npiPrescribingProvider;
    }

    /**
     * Helper method to generate appropriate code bundles for inpatient, outpatient,
     * and emergency claims.
     * 
     * @param type The encounter class
     */
    public void isInpatient(String type) {
      if (type.equals("emergency") || type.equals("ambulatory")) {
        setSourceAdminCode("emd");
        setBillTypeCode("852");
        setProcStatus("active");
        setNetworkStatus("out");
      } else {
        if (type.equals("inpatient") || type.equals("wellness") || type.equals("urgentcare")) {
          String[] admCode = {"gp", "mp"};
          setSourceAdminCode(admCode[(int) randomLongWithBounds(0, 1)]);
          setBillTypeCode("112");
          setProcStatus("active");
          setNetworkStatus("in");
        } else {
          setSourceAdminCode("outp");
          setBillTypeCode("112");
          setProcStatus("active");
          setNetworkStatus("out");
        }
      }
    }

    /**
     * Helper method to get the Provider NPI.
     * @return Provider NPI as String
     */
    public String getNpiProvider() {
      return npiProvider;
    }

    public void setNpiProvider(String npiProvider) {
      this.npiProvider = npiProvider;
    }

    public String getSourceAdminCode() {
      return this.sourceAdminCode;
    }

    private void setSourceAdminCode(String code) {
      this.sourceAdminCode = code;
    }

    public String getBillTypeCode() {
      return this.billTypeCode;
    }

    private void setBillTypeCode(String code) {
      this.billTypeCode = code;
    }

    public String getProcStatus() {
      return this.procStatus;
    }

    private void setProcStatus(String code) {
      this.procStatus = code;
    }

    public String getNetworkStatus() {
      return this.networkStatus;
    }

    private void setNetworkStatus(String code) {
      this.networkStatus = code;
    }
    
    public String getRevenueCenterCode() {
      return revenueCenterCode;
    }

    public void setRevenueCenterCode(String revenueCenterCode) {
      this.revenueCenterCode = revenueCenterCode;
    }

    public String getPlaceOfService() {
      return placeOfService;
    }

    public void setPlaceOfService(String placeOfService) {
      this.placeOfService = placeOfService;
    }

    public String getResidence() {
      return residence;
    }

    public Integer getLength() {
      return length;
    }

    public void setLength(Integer length) {
      this.length = length;
    }

    public String getServiceSiteNPI() {
      return serviceSiteNPI;
    }

    public void setServiceSiteNPI(String serviceSiteNPI) {
      this.serviceSiteNPI = serviceSiteNPI;
    }

    public String getPaymentType() {
      return paymentType;
    }

    public String getPayeeType() {
      return payeeType;
    }

    public String getBenefitPaymentStatus() {
      return benefitPaymentStatus;
    }

    public void setBenefitPaymentStatus(String benefitPaymentStatus) {
      this.benefitPaymentStatus = benefitPaymentStatus;
    }

    public String getDenialCode() {
      return denialCode;
    }

    public String getDischarge() {
      return discharge;
    }

    public String getAdmissionTypeCode() {
      return admissionTypeCode;
    }

    public String getClaimType() {
      return this.claimType;
    }

    public void setClaimType(String claimType) {
      this.claimType = claimType;
    }
  }
}