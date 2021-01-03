package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import com.google.common.collect.Table;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Device;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Supply;


/**
 * Researchers have requested a simple table-based format that could easily be
 * imported into any database for analysis. Unlike other formats which export a
 * single record per patient, this format generates 9 total files, and adds
 * lines to each based on the clinical events for each patient. These files are
 * intended to be analogous to database tables, with the patient UUID being a
 * foreign key. Files include: patients.csv, encounters.csv, allergies.csv,
 * medications.csv, conditions.csv, careplans.csv, observations.csv,
 * procedures.csv, and immunizations.csv.
 */
public class CSVExporter {
  /**
   * Writer for patients.csv.
   */
  private OutputStreamWriter patients;
  /**
   * Writer for allergies.csv.
   */
  private OutputStreamWriter allergies;
  /**
   * Writer for medications.csv.
   */
  private OutputStreamWriter medications;
  /**
   * Writer for conditions.csv.
   */
  private OutputStreamWriter conditions;
  /**
   * Writer for careplans.csv.
   */
  private OutputStreamWriter careplans;
  /**
   * Writer for observations.csv.
   */
  private OutputStreamWriter observations;
  /**
   * Writer for procedures.csv.
   */
  private OutputStreamWriter procedures;
  /**
   * Writer for immunizations.csv.
   */
  private OutputStreamWriter immunizations;
  /**
   * Writer for encounters.csv.
   */
  private OutputStreamWriter encounters;
  /**
   * Writer for imaging_studies.csv
   */
  private OutputStreamWriter imagingStudies;
  /**
   * Writer for devices.csv
   */
  private OutputStreamWriter devices;
  /**
   * Writer for supplies.csv
   */
  private OutputStreamWriter supplies;

  /**
   * Writer for organizations.csv
   */
  private OutputStreamWriter organizations;
  /**
   * Writer for providers.csv
   */
  private OutputStreamWriter providers;

  /**
   * Writer for payers.csv
   */
  private OutputStreamWriter payers;
  /**
   * Writer for payerTransitions.csv
   */
  private OutputStreamWriter payerTransitions;

  /**
   * Charset for specifying the character set of the output files.
   */
  private Charset charset = Charset.forName(Config.get("exporter.encoding"));

  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  /**
   * Constructor for the CSVExporter - initialize the 9 specified files and store
   * the writers in fields.
   */
  private CSVExporter() {
    try {
      File output = Exporter.getOutputFolder("csv", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      if (Config.getAsBoolean("exporter.csv.folder_per_run")) {
        // we want a folder per run, so name it based on the timestamp
        // TODO: do we want to consider names based on the current generation options?
        String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
        String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
        outputDirectory = outputDirectory.resolve(subfolderName);
        outputDirectory.toFile().mkdirs();
      }

      File patientsFile = outputDirectory.resolve("patients.csv").toFile();
      boolean append =
          patientsFile.exists() && Config.getAsBoolean("exporter.csv.append_mode");
      patients = new OutputStreamWriter(new FileOutputStream(patientsFile, append), charset);

      File allergiesFile = outputDirectory.resolve("allergies.csv").toFile();
      allergies = new OutputStreamWriter(new FileOutputStream(allergiesFile, append), charset);

      File medicationsFile = outputDirectory.resolve("medications.csv").toFile();
      medications = new OutputStreamWriter(new FileOutputStream(medicationsFile, append), charset);

      File conditionsFile = outputDirectory.resolve("conditions.csv").toFile();
      conditions = new OutputStreamWriter(new FileOutputStream(conditionsFile, append), charset);

      File careplansFile = outputDirectory.resolve("careplans.csv").toFile();
      careplans = new OutputStreamWriter(new FileOutputStream(careplansFile, append), charset);

      File observationsFile = outputDirectory.resolve("observations.csv").toFile();
      observations = new OutputStreamWriter(
          new FileOutputStream(observationsFile, append), charset);

      File proceduresFile = outputDirectory.resolve("procedures.csv").toFile();
      procedures = new OutputStreamWriter(new FileOutputStream(proceduresFile, append), charset);

      File immunizationsFile = outputDirectory.resolve("immunizations.csv").toFile();
      immunizations = new OutputStreamWriter(
          new FileOutputStream(immunizationsFile, append), charset);

      File encountersFile = outputDirectory.resolve("encounters.csv").toFile();
      encounters = new OutputStreamWriter(new FileOutputStream(encountersFile, append), charset);

      File imagingStudiesFile = outputDirectory.resolve("imaging_studies.csv").toFile();
      imagingStudies = new OutputStreamWriter(
          new FileOutputStream(imagingStudiesFile, append), charset);

      File devicesFile = outputDirectory.resolve("devices.csv").toFile();
      devices = new OutputStreamWriter(new FileOutputStream(devicesFile, append), charset);

      File suppliesFile = outputDirectory.resolve("supplies.csv").toFile();
      supplies = new OutputStreamWriter(new FileOutputStream(suppliesFile, append), charset);

      File organizationsFile = outputDirectory.resolve("organizations.csv").toFile();
      File providersFile = outputDirectory.resolve("providers.csv").toFile();
      organizations = new OutputStreamWriter(
          new FileOutputStream(organizationsFile, append), charset);
      providers = new OutputStreamWriter(new FileOutputStream(providersFile, append), charset);
      File payersFile = outputDirectory.resolve("payers.csv").toFile();
      File payerTransitionsFile = outputDirectory.resolve("payer_transitions.csv").toFile();
      payers = new OutputStreamWriter(new FileOutputStream(payersFile, append), charset);
      payerTransitions = new OutputStreamWriter(
          new FileOutputStream(payerTransitionsFile, append), charset);

      if (!append) {
        writeCSVHeaders();
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
  private void writeCSVHeaders() throws IOException {
    patients.write("Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,"
        + "PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,"
        + "ADDRESS,CITY,STATE,COUNTY,ZIP,LAT,LON,HEALTHCARE_EXPENSES,HEALTHCARE_COVERAGE");
    patients.write(NEWLINE);
    allergies.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
    allergies.write(NEWLINE);
    medications.write(
        "START,STOP,PATIENT,PAYER,ENCOUNTER,CODE,DESCRIPTION,BASE_COST,PAYER_COVERAGE,DISPENSES,"
        + "TOTALCOST,REASONCODE,REASONDESCRIPTION");
    medications.write(NEWLINE);
    conditions.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION");
    conditions.write(NEWLINE);
    careplans.write(
        "Id,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION");
    careplans.write(NEWLINE);
    observations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS,TYPE");
    observations.write(NEWLINE);
    procedures.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,BASE_COST,"
        + "REASONCODE,REASONDESCRIPTION");
    procedures.write(NEWLINE);
    immunizations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,BASE_COST");
    immunizations.write(NEWLINE);
    encounters.write(
        "Id,START,STOP,PATIENT,ORGANIZATION,PROVIDER,PAYER,ENCOUNTERCLASS,CODE,DESCRIPTION,"
        + "BASE_ENCOUNTER_COST,TOTAL_CLAIM_COST,PAYER_COVERAGE,REASONCODE,REASONDESCRIPTION");
    encounters.write(NEWLINE);
    imagingStudies.write("Id,DATE,PATIENT,ENCOUNTER,SERIES_UID,BODYSITE_CODE,BODYSITE_DESCRIPTION,"
        + "MODALITY_CODE,MODALITY_DESCRIPTION,INSTANCE_UID,SOP_CODE,SOP_DESCRIPTION");
    imagingStudies.write(NEWLINE);
    devices.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,UDI");
    devices.write(NEWLINE);
    supplies.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,QUANTITY");
    supplies.write(NEWLINE);

    organizations.write("Id,NAME,ADDRESS,CITY,STATE,ZIP,LAT,LON,PHONE,REVENUE,UTILIZATION");
    organizations.write(NEWLINE);
    providers.write("Id,ORGANIZATION,NAME,GENDER,SPECIALITY,ADDRESS,CITY,STATE,ZIP,LAT,LON,"
        + "UTILIZATION");
    providers.write(NEWLINE);
    payers.write("Id,NAME,ADDRESS,CITY,STATE_HEADQUARTERED,ZIP,PHONE,AMOUNT_COVERED,"
        + "AMOUNT_UNCOVERED,REVENUE,COVERED_ENCOUNTERS,UNCOVERED_ENCOUNTERS,COVERED_MEDICATIONS,"
        + "UNCOVERED_MEDICATIONS,COVERED_PROCEDURES,UNCOVERED_PROCEDURES,"
        + "COVERED_IMMUNIZATIONS,UNCOVERED_IMMUNIZATIONS,"
        + "UNIQUE_CUSTOMERS,QOLS_AVG,MEMBER_MONTHS");
    payers.write(NEWLINE);
    payerTransitions.write("PATIENT,START_YEAR,END_YEAR,PAYER,OWNERSHIP");
    payerTransitions.write(NEWLINE);
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final CSVExporter instance = new CSVExporter();
  }

  /**
   * Get the current instance of the CSVExporter.
   *
   * @return the current instance of the CSVExporter.
   */
  public static CSVExporter getInstance() {
    return SingletonHolder.instance;
  }

  /**
   * Export the organizations.csv and providers.csv files. This method should be
   * called once after all the Patient records have been exported using the
   * export(Person,long) method.
   *
   * @throws IOException if any IO errors occur.
   */
  public void exportOrganizationsAndProviders() throws IOException {
    for (Provider org : Provider.getProviderList()) {
      // Check utilization for hospital before we export
      Table<Integer, String, AtomicInteger> utilization = org.getUtilization();
      int totalEncounters =
          utilization.column(Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
      if (totalEncounters > 0) {
        organization(org, totalEncounters);
        Map<String, ArrayList<Clinician>> providers = org.clinicianMap;
        for (String speciality : providers.keySet()) {
          ArrayList<Clinician> clinicians = providers.get(speciality);
          for (Clinician clinician : clinicians) {
            provider(clinician, org.getResourceID());
          }
        }
      }
      organizations.flush();
      providers.flush();
    }
  }

  /**
   * Export the payers.csv file. This method should be called once after all the
   * Patient records have been exported using the export(Person,long) method.
   *
   * @throws IOException if any IO errors occur.
   */
  public void exportPayers() throws IOException {
    // Export All Payers
    for (Payer payer : Payer.getAllPayers()) {
      payer(payer);
      payers.flush();
    }
    // Export No Insurance statistics
    payer(Payer.noInsurance);
    payers.flush();
  }

  /**
   * Export the payerTransitions.csv file. This method should be called once after all the
   * Patient records have been exported using the export(Person,long) method.
   *
   * @throws IOException if any IO errors occur.
   */
  private void exportPayerTransitions(Person person, long stopTime) throws IOException {

    // The current year starts with the year of the person's birth.
    int currentYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));

    String previousPayerID = person.getPayerHistory()[0].getResourceID();
    String previousOwnership = "Guardian";
    int startYear = currentYear;

    for (int personAge = 0; personAge < 128; personAge++) {
      Payer currentPayer = person.getPayerAtAge(personAge);
      String currentOwnership = person.getPayerOwnershipAtAge(personAge);
      if (currentPayer == null) {
        return;
      }
      // Only write a new line if these conditions are met to export for year ranges of payers.
      if (!currentPayer.getResourceID().equals(previousPayerID)
          || !currentOwnership.equals(previousOwnership)
          || Utilities.convertCalendarYearsToTime(currentYear) >= stopTime
          || !person.alive(Utilities.convertCalendarYearsToTime(currentYear + 1))) {
        payerTransition(person, currentPayer, startYear, currentYear);
        previousPayerID = currentPayer.getResourceID();
        previousOwnership = currentOwnership;
        startYear = currentYear + 1;
        payerTransitions.flush();
      }
      currentYear++;
    }
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

    for (Encounter encounter : person.record.encounters) {

      String encounterID = encounter(person, personID, encounter);
      String payerID = encounter.claim.payer.uuid;

      for (HealthRecord.Entry condition : encounter.conditions) {
        /* condition to ignore codes other then retrieved from terminology url */
        if (!StringUtils.isEmpty(Config.get("generate.terminology_service_url"))
            && !RandomCodeGenerator.selectedCodes.isEmpty()) {
          if (RandomCodeGenerator.selectedCodes.stream()
              .filter(code -> code.code.equals(condition.codes.get(0).code))
              .findFirst().isPresent()) {
            condition(personID, encounterID, condition);
          }
        } else {
          condition(personID, encounterID, condition);
        }
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
        medication(personID, encounterID, payerID, medication, time);
      }

      for (HealthRecord.Entry immunization : encounter.immunizations) {
        immunization(personID, encounterID, immunization);
      }

      for (CarePlan careplan : encounter.careplans) {
        careplan(person, personID, encounterID, careplan);
      }

      for (ImagingStudy imagingStudy : encounter.imagingStudies) {
        imagingStudy(person, personID, encounterID, imagingStudy);
      }

      for (Device device : encounter.devices) {
        device(personID, encounterID, device);
      }

      for (Supply supply : encounter.supplies) {
        supply(personID, encounterID, encounter, supply);
      }
    }
    CSVExporter.getInstance().exportPayerTransitions(person, time);

    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    Calendar cutOff = new GregorianCalendar(1900, 0, 1);
    if (yearsOfHistory > 0) {
      cutOff = Calendar.getInstance();
      cutOff.set(cutOff.get(Calendar.YEAR) - yearsOfHistory, 0, 1);
    }
    Calendar now = Calendar.getInstance();
    Calendar birthDay = Calendar.getInstance();
    birthDay.setTimeInMillis((long) person.attributes.get(Person.BIRTHDATE));
    String[] gbdMetrics = { QualityOfLifeModule.QALY, QualityOfLifeModule.DALY,
        QualityOfLifeModule.QOLS };
    String unit = null;
    for (String score : gbdMetrics) {
      if (score.equals(QualityOfLifeModule.QOLS)) {
        unit = "{score}";
      } else {
        // years in UCUM is "a" for Latin "Annus"
        unit = "a";
      }
      @SuppressWarnings("unchecked")
      Map<Integer, Double> scores = (Map<Integer, Double>) person.attributes.get(score);
      for (Integer year : scores.keySet()) {
        birthDay.set(Calendar.YEAR, year);
        if (birthDay.after(cutOff) && birthDay.before(now)) {
          Observation obs = person.record.new Observation(
              birthDay.getTimeInMillis(), score, scores.get(year));
          obs.unit = unit;
          Code code = new Code("GBD", score, score);
          obs.codes.add(code);
          observation(personID, "", obs);
        }
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
    imagingStudies.flush();
    devices.flush();
    supplies.flush();
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
    // Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,
    // FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
    // CITY,STATE,COUNTY,ZIP,LAT,LON,HEALTHCARE_EXPENSES,HEALTHCARE_COVERAGE
    String personID = (String) person.attributes.get(Person.ID);

    // check if we've already exported this patient demographic data yet,
    // otherwise the "split record" feature could add a duplicate entry.
    if (person.attributes.containsKey("exported_to_csv")) {
      return personID;
    } else {
      person.attributes.put("exported_to_csv", personID);
    }

    StringBuilder s = new StringBuilder();
    s.append(personID).append(',');
    s.append(dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE))).append(',');
    if (!person.alive(time)) {
      s.append(dateFromTimestamp((Long) person.attributes.get(Person.DEATHDATE)));
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
        Person.ADDRESS,
        Person.CITY,
        Person.STATE,
        "county",
        Person.ZIP
    }) {
      String value = (String) person.attributes.getOrDefault(attribute, "");
      s.append(',').append(clean(value));
    }
    // LAT,LON
    s.append(',').append(person.getY()).append(',').append(person.getX()).append(',');
    // HEALTHCARE_EXPENSES
    s.append(person.getHealthcareExpenses()).append(',');
    // HEALTHCARE_COVERAGE
    s.append(person.getHealthcareCoverage());
    // QALYS
    // s.append(person.attributes.get("most-recent-qaly")).append(',');
    // DALYS
    // s.append(person.attributes.get("most-recent-daly"));

    s.append(NEWLINE);
    write(s.toString(), patients);

    return personID;
  }

  /**
   * Write a single Encounter line to encounters.csv.
   *
   * @param rand      Source of randomness to use when generating ids etc
   * @param personID  The ID of the person that had this encounter
   * @param encounter The encounter itself
   * @return The encounter ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private String encounter(RandomNumberGenerator rand, String personID,
          Encounter encounter) throws IOException {
    // Id,START,STOP,PATIENT,ORGANIZATION,PROVIDER,PAYER,ENCOUNTERCLASS,CODE,DESCRIPTION,
    // BASE_ENCOUNTER_COST,TOTAL_CLAIM_COST,PAYER_COVERAGE,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    String encounterID = rand.randUUID().toString();
    // ID
    s.append(encounterID).append(',');
    // START
    s.append(iso8601Timestamp(encounter.start)).append(',');
    // STOP
    if (encounter.stop != 0L) {
      s.append(iso8601Timestamp(encounter.stop)).append(',');
    } else {
      s.append(',');
    }
    // PATIENT
    s.append(personID).append(',');
    // ORGANIZATION
    if (encounter.provider != null) {
      s.append(encounter.provider.getResourceID()).append(',');
    } else {
      s.append(',');
    }
    // PROVIDER
    if (encounter.clinician != null) {
      s.append(encounter.clinician.getResourceID()).append(',');
    } else {
      s.append(',');
    }
    // PAYER
    if (encounter.claim.payer != null) {
      s.append(encounter.claim.payer.getResourceID()).append(',');
    } else {
      s.append(',');
    }
    // ENCOUNTERCLASS
    if (encounter.type != null) {
      s.append(encounter.type.toLowerCase()).append(',');
    } else {
      s.append(',');
    }
    // CODE
    Code coding = encounter.codes.get(0);
    s.append(coding.code).append(',');
    // DESCRIPTION
    s.append(clean(coding.display)).append(',');
    // BASE_ENCOUNTER_COST
    s.append(String.format(Locale.US, "%.2f", encounter.getCost())).append(',');
    // TOTAL_COST
    s.append(String.format(Locale.US, "%.2f", encounter.claim.getTotalClaimCost())).append(',');
    // PAYER_COVERAGE
    s.append(String.format(Locale.US, "%.2f", encounter.claim.getCoveredCost())).append(',');
    // REASONCODE & REASONDESCRIPTION
    if (encounter.reason == null) {
      s.append(",");
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
   * @param personID    ID of the person that has the condition.
   * @param encounterID ID of the encounter where the condition was diagnosed
   * @param condition   The condition itself
   * @throws IOException if any IO error occurs
   */
  private void condition(String personID, String encounterID, Entry condition) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(condition.start)).append(',');
    if (condition.stop != 0L) {
      s.append(dateFromTimestamp(condition.stop));
    }
    s.append(',');
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
   * @param personID    ID of the person that has the allergy.
   * @param encounterID ID of the encounter where the allergy was diagnosed
   * @param allergy     The allergy itself
   * @throws IOException if any IO error occurs
   */
  private void allergy(String personID, String encounterID, Entry allergy) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(allergy.start)).append(',');
    if (allergy.stop != 0L) {
      s.append(dateFromTimestamp(allergy.stop));
    }
    s.append(',');
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
   * @param personID    ID of the person to whom the observation applies.
   * @param encounterID ID of the encounter where the observation was taken
   * @param observation The observation itself
   * @throws IOException if any IO error occurs
   */
  private void observation(String personID,
      String encounterID, Observation observation) throws IOException {

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

    s.append(iso8601Timestamp(observation.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = observation.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    String value = ExportHelper.getObservationValue(observation);
    String type = ExportHelper.getObservationType(observation);
    s.append(value).append(',');
    s.append(observation.unit).append(',');
    s.append(type);

    s.append(NEWLINE);
    write(s.toString(), observations);
  }

  /**
   * Write a single Procedure to procedures.csv.
   *
   * @param personID    ID of the person on whom the procedure was performed.
   * @param encounterID ID of the encounter where the procedure was performed
   * @param payerID      ID of the payer who covered the immunization.
   * @param procedure   The procedure itself
   * @throws IOException if any IO error occurs
   */
  private void procedure(String personID, String encounterID,
      Procedure procedure) throws IOException {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(iso8601Timestamp(procedure.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');
    // CODE
    Code coding = procedure.codes.get(0);
    s.append(coding.code).append(',');
    // DESCRIPTION
    s.append(clean(coding.display)).append(',');
    // BASE_COST
    s.append(String.format(Locale.US, "%.2f", procedure.getCost())).append(',');
    // REASONCODE & REASONDESCRIPTION
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
   * @param personID    ID of the person prescribed the medication.
   * @param encounterID ID of the encounter where the medication was prescribed
   * @param payerID     ID of the payer who covered the immunization.
   * @param medication  The medication itself
   * @param stopTime    End time
   * @throws IOException if any IO error occurs
   */
  private void medication(String personID, String encounterID, String payerID,
      Medication medication, long stopTime)
      throws IOException {
    // START,STOP,PATIENT,PAYER,ENCOUNTER,CODE,DESCRIPTION,
    // BASE_COST,PAYER_COVERAGE,DISPENSES,TOTALCOST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(iso8601Timestamp(medication.start)).append(',');
    if (medication.stop != 0L) {
      s.append(iso8601Timestamp(medication.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(payerID).append(',');
    s.append(encounterID).append(',');
    // CODE
    Code coding = medication.codes.get(0);
    s.append(coding.code).append(',');
    // DESCRIPTION
    s.append(clean(coding.display)).append(',');
    // BASE_COST
    BigDecimal cost = medication.getCost();
    s.append(String.format(Locale.US, "%.2f", cost)).append(',');
    // PAYER_COVERAGE
    s.append(String.format(Locale.US, "%.2f", medication.claim.getCoveredCost())).append(',');
    long dispenses = 1; // dispenses = refills + original
    // makes the math cleaner and more explicit. dispenses * unit cost = total cost

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

    s.append(dispenses).append(',');
    BigDecimal totalCost = cost.multiply(
        BigDecimal.valueOf(dispenses)).setScale(2, RoundingMode.DOWN); //Truncate 2 decimal places
    s.append(String.format(Locale.US, "%.2f", totalCost)).append(',');

    if (medication.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
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
   * @param personID     ID of the person on whom the immunization was performed.
   * @param encounterID  ID of the encounter where the immunization was performed.
   * @param payerID      ID of the payer who covered the immunization.
   * @param immunization The immunization itself
   * @throws IOException if any IO error occurs
   */
  private void immunization(String personID, String encounterID,
      Entry immunization) throws IOException {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,BASE_COST
    StringBuilder s = new StringBuilder();

    s.append(iso8601Timestamp(immunization.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');
    // CODE
    Code coding = immunization.codes.get(0);
    s.append(coding.code).append(',');
    // DESCRIPTION
    s.append(clean(coding.display)).append(',');
    // BASE_COST
    s.append(String.format(Locale.US, "%.2f", immunization.getCost()));

    s.append(NEWLINE);
    write(s.toString(), immunizations);
  }

  /**
   * Write a single CarePlan to careplans.csv.
   *
   * @param rand        Source of randomness to use when generating ids etc
   * @param personID    ID of the person prescribed the careplan.
   * @param encounterID ID of the encounter where the careplan was prescribed
   * @param careplan    The careplan itself
   * @throws IOException if any IO error occurs
   */
  private String careplan(RandomNumberGenerator rand, String personID, String encounterID,
      CarePlan careplan) throws IOException {
    // Id,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    String careplanID = rand.randUUID().toString();
    s.append(careplanID).append(',');
    s.append(dateFromTimestamp(careplan.start)).append(',');
    if (careplan.stop != 0L) {
      s.append(dateFromTimestamp(careplan.stop));
    }
    s.append(',');
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
   * Write a single ImagingStudy to imaging_studies.csv.
   *
   * @param rand         Source of randomness to use when generating ids etc
   * @param personID     ID of the person the ImagingStudy was taken of.
   * @param encounterID  ID of the encounter where the ImagingStudy was performed
   * @param imagingStudy The ImagingStudy itself
   * @throws IOException if any IO error occurs
   */
  private String imagingStudy(RandomNumberGenerator rand, String personID, String encounterID,
      ImagingStudy imagingStudy) throws IOException {
    // Id,DATE,PATIENT,ENCOUNTER,SERIES_UID,BODYSITE_CODE,BODYSITE_DESCRIPTION,
    // MODALITY_CODE,MODALITY_DESCRIPTION,INSTANCE_UID,SOP_CODE,SOP_DESCRIPTION
    StringBuilder s = new StringBuilder();

    String studyID = rand.randUUID().toString();

    for(ImagingStudy.Series series: imagingStudy.series) {
      String seriesDicomUid = series.dicomUid;
      Code bodySite = series.bodySite;
      Code modality = series.modality;
      for(ImagingStudy.Instance instance: series.instances) {
        String instanceDicomUid = instance.dicomUid;
        Code sopClass = instance.sopClass;
        s.append(studyID).append(',');
        s.append(iso8601Timestamp(imagingStudy.start)).append(',');
        s.append(personID).append(',');
        s.append(encounterID).append(',');

        s.append(seriesDicomUid).append(',');

        s.append(bodySite.code).append(',');
        s.append(bodySite.display).append(',');

        s.append(modality.code).append(',');
        s.append(modality.display).append(',');

        s.append(instanceDicomUid).append(',');

        s.append(sopClass.code).append(',');
        s.append(sopClass.display);

        s.append(NEWLINE);

      }
    }

    write(s.toString(), imagingStudies);

    return studyID;
  }

  /**
   * Write a single Device to devices.csv.
   *
   * @param personID     ID of the person the Device is affixed to.
   * @param encounterID  ID of the encounter where the Device was associated
   * @param device       The Device itself
   * @throws IOException if any IO error occurs
   */
  private void device(String personID, String encounterID, Device device)
      throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,UDI
    StringBuilder s = new StringBuilder();

    s.append(iso8601Timestamp(device.start)).append(',');
    if (device.stop != 0L) {
      s.append(iso8601Timestamp(device.stop));
    }
    s.append(',');

    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code code = device.codes.get(0);
    s.append(code.code).append(',');
    s.append(clean(code.display)).append(',');

    s.append(device.udi);

    s.append(NEWLINE);

    write(s.toString(), devices);
  }

  /**
   * Write a single Supply to supplies.csv.
   *
   * @param personID     ID of the person the supply was used for.
   * @param encounterID  ID of the encounter where the supply was used
   * @param supply       The supply itself
   * @throws IOException if any IO error occurs
   */
  private void supply(String personID, String encounterID, Encounter encounter, Supply supply)
          throws IOException {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,QUANTITY
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(supply.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code code = supply.codes.get(0);
    s.append(code.code).append(',');
    s.append(clean(code.display)).append(',');

    s.append(supply.quantity);

    s.append(NEWLINE);

    write(s.toString(), supplies);
  }

  /**
   * Write a single organization to organizations.csv
   *
   * @param org         The organization to be written
   * @param utilization The total number of encounters for the org
   * @throws IOException if any IO error occurs
   */
  private void organization(Provider org, int utilization) throws IOException {
    // Id,NAME,ADDRESS,CITY,STATE,ZIP,PHONE,REVENUE,UTILIZATION
    StringBuilder s = new StringBuilder();
    s.append(org.getResourceID()).append(',');
    s.append(clean(org.name)).append(',');
    s.append(clean(org.address)).append(',');
    s.append(org.city).append(',');
    s.append(org.state).append(',');
    s.append(org.zip).append(',');
    s.append(org.getY()).append(',');
    s.append(org.getX()).append(',');
    s.append(org.phone).append(',');
    s.append(org.getRevenue()).append(',');
    s.append(utilization);
    s.append(NEWLINE);

    write(s.toString(), organizations);
  }

  /**
   * Write a single clinician to providers.csv
   *
   * @param provider The provider information to be written
   * @param orgId    ID of the organization the provider belongs to
   * @throws IOException if any IO error occurs
   */
  private void provider(Clinician provider, String orgId) throws IOException {
    // Id,ORGANIZATION,NAME,GENDER,SPECIALITY,ADDRESS,CITY,STATE,ZIP,UTILIZATION

    StringBuilder s = new StringBuilder();
    s.append(provider.getResourceID()).append(',');
    s.append(orgId).append(',');
    for (String attribute : new String[] { Clinician.NAME, Clinician.GENDER,
        Clinician.SPECIALTY, Clinician.ADDRESS, Clinician.CITY, Clinician.STATE,
        Clinician.ZIP }) {
      String value = (String) provider.attributes.getOrDefault(attribute, "");
      s.append(clean(value)).append(',');
    }
    s.append(provider.getY()).append(',');
    s.append(provider.getX()).append(',');
    s.append(provider.getEncounterCount());

    s.append(NEWLINE);

    write(s.toString(), providers);
  }

  /**
   * Write a single payer to payers.csv.
   *
   * @param payer The payer to be exported.
   * @throws IOException if any IO error occurs.
   */
  private void payer(Payer payer) throws IOException {
    // Id,NAME,ADDRESS,CITY,STATE_HEADQUARTERED,ZIP,PHONE,AMOUNT_COVERED,AMOUNT_UNCOVERED,REVENUE,
    // COVERED_ENCOUNTERS,UNCOVERED_ENCOUNTERS,COVERED_MEDICATIONS,UNCOVERED_MEDICATIONS,
    // COVERED_PROCEDURES,UNCOVERED_PROCEDURES,COVERED_IMMUNIZATIONS,UNCOVERED_IMMUNIZATIONS,
    // UNIQUE_CUSTOMERS,QOLS_AVG,MEMBER_MONTHS

    StringBuilder s = new StringBuilder();
    // UUID
    s.append(payer.getResourceID()).append(',');
    // NAME
    s.append(payer.getName()).append(',');
    // Second Class Attributes
    for (String attribute : new String[]
        { "address", "city", "state_headquartered", "zip", "phone" }) {
      String value = (String) payer.getAttributes().getOrDefault(attribute, "");
      s.append(clean(value)).append(',');
    }
    // AMOUNT_COVERED
    s.append(String.format(Locale.US, "%.2f", payer.getAmountCovered())).append(',');
    // AMOUNT_UNCOVERED
    s.append(String.format(Locale.US, "%.2f", payer.getAmountUncovered())).append(',');
    // REVENUE
    s.append(String.format(Locale.US, "%.2f", payer.getRevenue())).append(',');
    // Covered/Uncovered Encounters/Medications/Procedures/Immunizations
    s.append(payer.getEncountersCoveredCount()).append(",");
    s.append(payer.getEncountersUncoveredCount()).append(",");
    s.append(payer.getMedicationsCoveredCount()).append(",");
    s.append(payer.getMedicationsUncoveredCount()).append(",");
    s.append(payer.getProceduresCoveredCount()).append(",");
    s.append(payer.getProceduresUncoveredCount()).append(",");
    s.append(payer.getImmunizationsCoveredCount()).append(",");
    s.append(payer.getImmunizationsUncoveredCount()).append(",");
    // UNIQUE CUSTOMERS
    s.append(payer.getUniqueCustomers()).append(",");
    // QOLS_AVG
    s.append(payer.getQolsAverage()).append(",");
    // MEMBER_MONTHS (Note that this converts the number of years covered to months)
    s.append(payer.getNumYearsCovered() * 12);

    s.append(NEWLINE);
    write(s.toString(), payers);
  }

  /**
   * Write a single range of unchanged payer history to payer_transitions.csv
   *
   * @param person The person whose payer history to write.
   * @param payer The payer of the person's current range of payer history.
   * @param startYear The first year of this payer history.
   * @param endYear The final year of this payer history.
   * @throws IOException if any IO error occurs
   */
  private void payerTransition(Person person, Payer payer, int startYear, int endYear)
      throws IOException {
    // PATIENT_ID,START_YEAR,END_YEAR,PAYER_ID,OWNERSHIP

    StringBuilder s = new StringBuilder();
    // PATIENT_ID
    s.append(person.attributes.get(Person.ID)).append(",");
    // START_YEAR
    s.append(startYear).append(',');
    // END_YEAR
    s.append(endYear).append(',');
    // PAYER_ID
    s.append(payer.getResourceID()).append(',');
    // OWNERSHIP
    s.append(person.getPayerOwnershipAtTime(Utilities.convertCalendarYearsToTime(startYear)));
    s.append(NEWLINE);
    write(s.toString(), payerTransitions);
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
  private static void write(String line, OutputStreamWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
    }
  }
}
