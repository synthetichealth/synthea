package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.export.ExportHelper.getSystemFromURI;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import com.google.common.collect.Table;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.export.CSVConstants;
import org.mitre.synthea.export.CSVFileManager;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Device;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Supply;
import org.mitre.synthea.world.concepts.healthinsurance.PlanRecord;

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
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  private CSVFileManager fileManager;

  /**
   * Thread-safe monotonically increasing transactionId.
   * NOTE: This is intentionally unique per-run rather than per-patient,
   * which means that the claim_transactions.chargeid column will not be consistent
   * when recreating a population using multiple threads.
   */
  private AtomicLong transactionId;

  /**
   * Constructor for the CSVExporter - initialize the specified files and store
   * the writers in fields.
   */
  private CSVExporter() {
    init();
  }

  void init() {
    fileManager = new CSVFileManager();

    this.transactionId = new AtomicLong();
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
        exportOrganization(org, totalEncounters);
        Map<String, ArrayList<Clinician>> providers = org.clinicianMap;
        for (String speciality : providers.keySet()) {
          ArrayList<Clinician> clinicians = providers.get(speciality);
          for (Clinician clinician : clinicians) {
            exportProvider(clinician, org.getResourceID());
          }
        }
      }
      fileManager.flushWriter(CSVConstants.ORGANIZATION_KEY);
      fileManager.flushWriter(CSVConstants.PROVIDER_KEY);
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
    for (Payer payer : PayerManager.getAllPayers()) {
      exportPayer(payer);
      fileManager.flushWriter(CSVConstants.PAYER_KEY);
    }
    // Export No Insurance statistics
    exportPayer(PayerManager.getNoInsurancePlan().getPayer());
    fileManager.flushWriter(CSVConstants.PAYER_KEY);
  }

  /**
   * Export the payerTransitions.csv file. This method should be called once after all the
   * Patient records have been exported using the export(Person,long) method.
   *
   * @throws IOException if any IO errors occur.
   */
  private void exportPayerTransitions(Person person, long cutOffTime, long stopTime)
      throws IOException {
    List<PlanRecord> sortedPlanRecords = person.coverage.getPlanHistory().stream()
        .sorted(Comparator.comparingLong(PlanRecord::getStartTime))
        .collect(Collectors.toList());
    for (PlanRecord planRecord : sortedPlanRecords) {
      if ((planRecord.getStartTime() <= stopTime) && (planRecord.getStopTime() >= cutOffTime)) {
        exportPayerTransition(person, planRecord);
      }
    }
    fileManager.flushWriter(CSVConstants.PAYER_TRANSITION_KEY);
  }

  /**
   * Export the payerTransitions.csv file. This method should be called once after all the
   * Patient records have been exported using the export(Person,long) method.
   *
   * @throws IOException if any IO errors occur.
   */
  private void exportPatientExpenses(Person person, long cutOffTime, long stopTime)
      throws IOException {
    List<PlanRecord> sortedPlanRecords = person.coverage.getPlanHistory().stream()
        .sorted(Comparator.comparingLong(PlanRecord::getStartTime))
        .collect(Collectors.toList());
    for (PlanRecord planRecord : sortedPlanRecords) {
      if ((planRecord.getStartTime() <= stopTime) && (planRecord.getStopTime() >= cutOffTime)) {
        exportPatientExpense(person, planRecord);
      }
    }
    fileManager.flushWriter(CSVConstants.PATIENT_EXPENSE_KEY);
  }

  /**
   * Add a single Person's health record info to the CSV records.
   *
   * @param person Person to write record data for
   * @param time   Time the simulation ended
   * @throws IOException if any IO error occurs
   */
  public void export(Person person, long time) throws IOException {

    String personID = exportPatient(person, time);

    for (Encounter encounter : person.record.encounters) {

      String encounterID = exportEncounter(personID, encounter);
      String payerID = encounter.claim.getPayer().uuid;

      exportClaim(person, encounter.claim, encounter, encounterID, time);

      for (HealthRecord.Entry condition : encounter.conditions) {
        /* condition to ignore codes other then retrieved from terminology url */
        if (!StringUtils.isEmpty(Config.get("generate.terminology_service_url"))
            && !RandomCodeGenerator.selectedCodes.isEmpty()) {
          if (RandomCodeGenerator.selectedCodes.stream()
              .filter(code -> code.code.equals(condition.codes.get(0).code))
              .findFirst().isPresent()) {
            exportCondition(personID, encounterID, condition);
          }
        } else {
          exportCondition(personID, encounterID, condition);
        }
      }

      for (HealthRecord.Allergy allergy : encounter.allergies) {
        exportAllergy(personID, encounterID, allergy);
      }

      for (Observation observation : encounter.observations) {
        exportObservation(personID, encounterID, observation);
      }

      for (Procedure procedure : encounter.procedures) {
        exportProcedure(personID, encounterID, procedure);
      }

      for (Medication medication : encounter.medications) {
        exportMedication(personID, encounterID, payerID, medication, time);
        exportClaim(person, medication.claim, encounter, encounterID, time);
      }

      for (HealthRecord.Entry immunization : encounter.immunizations) {
        exportImmunization(personID, encounterID, immunization);
      }

      for (CarePlan carePlan : encounter.careplans) {
        exportCarePlan(personID, encounterID, carePlan);
      }

      for (ImagingStudy imagingStudy : encounter.imagingStudies) {
        exportImagingStudy(personID, encounterID, imagingStudy);
      }

      for (Device device : encounter.devices) {
        exportDevice(personID, encounterID, device);
      }

      for (Supply supply : encounter.supplies) {
        exportSupply(personID, encounterID, encounter, supply);
      }
    }
    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    Calendar cutOff = new GregorianCalendar(1900, 0, 1);
    if (yearsOfHistory > 0) {
      cutOff = Calendar.getInstance();
      cutOff.set(cutOff.get(Calendar.YEAR) - yearsOfHistory, 0, 1);
    }
    CSVExporter.getInstance().exportPayerTransitions(person, 0L, time);
    CSVExporter.getInstance().exportPatientExpenses(person, cutOff.getTimeInMillis(), time);
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
          exportObservation(personID, "", obs);
        }
      }
    }

    fileManager.flushWriter(CSVConstants.PATIENT_KEY);
    fileManager.flushWriter(CSVConstants.ENCOUNTER_KEY);
    fileManager.flushWriter(CSVConstants.CONDITION_KEY);
    fileManager.flushWriter(CSVConstants.ALLERGY_KEY);
    fileManager.flushWriter(CSVConstants.MEDICATION_KEY);
    fileManager.flushWriter(CSVConstants.CAREPLAN_KEY);
    fileManager.flushWriter(CSVConstants.OBSERVATION_KEY);
    fileManager.flushWriter(CSVConstants.PROCEDURE_KEY);
    fileManager.flushWriter(CSVConstants.IMMUNIZATION_KEY);
    fileManager.flushWriter(CSVConstants.IMAGING_STUDY_KEY);
    fileManager.flushWriter(CSVConstants.DEVICE_KEY);
    fileManager.flushWriter(CSVConstants.SUPPLY_KEY);
    fileManager.flushWriter(CSVConstants.CLAIM_KEY);
    fileManager.flushWriter(CSVConstants.CLAIM_TRANSACTION_KEY);
    fileManager.flushWriter(CSVConstants.PATIENT_EXPENSE_KEY);
  }

  /**
   * Write a single Patient line, to patients.csv.
   *
   * @param person Person to write data for
   * @param time Time the simulation ended, to calculate age/deceased status
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private String exportPatient(Person person, long time) throws IOException {
    // Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,
    // FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS
    // CITY,STATE,COUNTY,FIPS,ZIP,LAT,LON,HEALTHCARE_EXPENSES,HEALTHCARE_COVERAGE,INCOME
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
        Person.MIDDLE_NAME,
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
        Person.COUNTY,
        Person.FIPS,
        Person.ZIP,
    }) {
      String value = (String) person.attributes.getOrDefault(attribute, "");
      s.append(',').append(clean(value));
    }
    // LAT,LON
    s.append(',').append(person.getY()).append(',').append(person.getX()).append(',');
    // HEALTHCARE_EXPENSES
    s.append(person.coverage.getTotalOutOfPocketExpenses()).append(',');
    // HEALTHCARE_COVERAGE
    s.append(person.coverage.getTotalCoverage()).append(',');
    // INCOME
    s.append(person.attributes.get(Person.INCOME));
    // QALYS
    // s.append(person.attributes.get("most-recent-qaly")).append(',');
    // DALYS
    // s.append(person.attributes.get("most-recent-daly"));

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.PATIENT_KEY);

    return personID;
  }

  /**
   * Write a single Encounter line to encounters.csv.
   *
   * @param personID  The ID of the person that had this encounter
   * @param encounter The encounter itself
   * @return The encounter ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private String exportEncounter(String personID,
          Encounter encounter) throws IOException {
    // Id,START,STOP,PATIENT,ORGANIZATION,PROVIDER,PAYER,ENCOUNTERCLASS,CODE,DESCRIPTION,
    // BASE_ENCOUNTER_COST,TOTAL_CLAIM_COST,PAYER_COVERAGE,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    String encounterID = encounter.uuid.toString();
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
    if (encounter.claim.getPayer() != null) {
      s.append(encounter.claim.getPayer().getResourceID()).append(',');
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
    Code coding = null;

    coding = encounter.codes.get(0);
    // CODE
    s.append(coding.code).append(',');
    // DESCRIPTION
    s.append(clean(coding.display)).append(',');

    // BASE_ENCOUNTER_COST
    s.append(String.format(Locale.US, "%.2f", encounter.getCost())).append(',');
    // TOTAL_COST
    s.append(String.format(Locale.US, "%.2f", encounter.claim.getTotalClaimCost())).append(',');
    // PAYER_COVERAGE
    s.append(String.format(Locale.US, "%.2f", encounter.claim.getTotalCoveredCost())).append(',');
    // REASONCODE & REASONDESCRIPTION
    if (encounter.reason == null) {
      s.append(",");
    } else {
      s.append(encounter.reason.code).append(',');
      s.append(clean(encounter.reason.display));
    }

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.ENCOUNTER_KEY);

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
  private void exportCondition(String personID, String encounterID, Entry condition)
      throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,SYSTEM,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(condition.start)).append(',');
    if (condition.stop != 0L) {
      s.append(dateFromTimestamp(condition.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = condition.codes.get(0);

    s.append(coding.system).append(',');
    s.append(coding.code).append(',');
    s.append(clean(coding.display));

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.CONDITION_KEY);
  }

  /**
   * Write a single Allergy to allergies.csv.
   *
   * @param personID    ID of the person that has the allergy.
   * @param encounterID ID of the encounter where the allergy was diagnosed
   * @param allergy     The allergy itself
   * @throws IOException if any IO error occurs
   */
  private void exportAllergy(String personID, String encounterID, HealthRecord.Allergy allergy)
      throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,SYSTEM,DESCRIPTION,TYPE,CATEGORY
    // REACTION1,DESCRIPTION1,SEVERITY1,
    // REACTION2,DESCRIPTION2,SEVERITY2
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
    s.append(getSystemFromURI(coding.system)).append(',');
    s.append(clean(coding.display)).append(',');
    if (allergy.allergyType != null) {
      s.append(allergy.allergyType);
    }
    s.append(',');
    if (allergy.category != null) {
      s.append(allergy.category);
    }
    s.append(',');

    int reactionsSize = 0;
    if (allergy.reactions != null) {
      reactionsSize = allergy.reactions.size();
    }
    Function<Map.Entry<HealthRecord.Code, HealthRecord.ReactionSeverity>, String> template =
        mapEntry -> {
          StringBuilder reactionBuilder = new StringBuilder();
          reactionBuilder.append(mapEntry.getKey().code).append(',');
          reactionBuilder.append(clean(mapEntry.getKey().display)).append(',');
          reactionBuilder.append(mapEntry.getValue());
          return reactionBuilder.toString();
        };

    switch (reactionsSize) {
      case 0:
        s.append(",,,,,");
        break;
      case 1:
        s.append(allergy.reactions.entrySet().stream().map(template).collect(Collectors.joining()));
        s.append(",,,");
        break;
      default:
        //case where there are more than two reactions so we need to support by severity
        s.append(allergy.reactions.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getValue))
            .limit(2)
            .map(template)
            .collect(Collectors.joining(",")));
    }

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.ALLERGY_KEY);
  }

  /**
   * Write a single Observation to observations.csv.
   *
   * @param personID    ID of the person to whom the observation applies.
   * @param encounterID ID of the encounter where the observation was taken
   * @param observation The observation itself
   * @throws IOException if any IO error occurs
   */
  private void exportObservation(String personID,
      String encounterID, Observation observation) throws IOException {

    if (observation.value == null) {
      if (observation.observations != null && !observation.observations.isEmpty()) {
        // just loop through the child observations

        for (Observation subObs : observation.observations) {
          exportObservation(personID, encounterID, subObs);
        }
      }

      // no value so nothing more to report here
      return;
    }

    // DATE,PATIENT,ENCOUNTER,CATEGORY,CODE,DESCRIPTION,VALUE,UNITS
    StringBuilder s = new StringBuilder();

    s.append(iso8601Timestamp(observation.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');
    if (observation.category != null) {
      s.append(observation.category);
    }
    s.append(',');

    Code coding = observation.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    String value = ExportHelper.getObservationValue(observation);
    String type = ExportHelper.getObservationType(observation);
    s.append(clean(value)).append(',');
    s.append(clean(observation.unit)).append(',');
    s.append(type);

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.OBSERVATION_KEY);
  }

  /**
   * Write a single Procedure to procedures.csv.
   *
   * @param personID    ID of the person on whom the procedure was performed.
   * @param encounterID ID of the encounter where the procedure was performed
   * @param procedure   The procedure itself
   * @throws IOException if any IO error occurs
   */
  private void exportProcedure(String personID, String encounterID,
      Procedure procedure) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,SYSTEM,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(iso8601Timestamp(procedure.start)).append(',');
    if (procedure.stop != 0L) {
      s.append(iso8601Timestamp(procedure.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');
    // CODE
    Code coding = procedure.codes.get(0);
    s.append(coding.system).append(',');
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
    fileManager.writeResourceLine(s.toString(), CSVConstants.PROCEDURE_KEY);
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
  private void exportMedication(String personID, String encounterID, String payerID,
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
    s.append(String.format(Locale.US, "%.2f", medication.claim.getTotalCoveredCost())).append(',');
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
    fileManager.writeResourceLine(s.toString(), CSVConstants.MEDICATION_KEY);
  }

  /**
   * Write a single Immunization to immunizations.csv.
   *
   * @param personID     ID of the person on whom the immunization was performed.
   * @param encounterID  ID of the encounter where the immunization was performed.
   * @param immunization The immunization itself
   * @throws IOException if any IO error occurs
   */
  private void exportImmunization(String personID, String encounterID,
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
    fileManager.writeResourceLine(s.toString(), CSVConstants.IMMUNIZATION_KEY);
  }

  /**
   * Write a single CarePlan to careplans.csv.
   *
   * @param personID    ID of the person prescribed the carePlan.
   * @param encounterID ID of the encounter where the carePlan was prescribed
   * @param carePlan    The carePlan itself
   * @throws IOException if any IO error occurs
   */
  private String exportCarePlan(String personID, String encounterID,
      CarePlan carePlan) throws IOException {
    // Id,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    String carePlanID = carePlan.uuid.toString();
    s.append(carePlanID).append(',');
    s.append(dateFromTimestamp(carePlan.start)).append(',');
    if (carePlan.stop != 0L) {
      s.append(dateFromTimestamp(carePlan.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = carePlan.codes.get(0);

    s.append(coding.code).append(',');
    s.append(coding.display).append(',');

    if (carePlan.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = carePlan.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }
    s.append(NEWLINE);

    fileManager.writeResourceLine(s.toString(), CSVConstants.CAREPLAN_KEY);

    return carePlanID;
  }

  /**
   * Write a single ImagingStudy to imaging_studies.csv.
   *
   * @param personID     ID of the person the ImagingStudy was taken of.
   * @param encounterID  ID of the encounter where the ImagingStudy was performed
   * @param imagingStudy The ImagingStudy itself
   * @throws IOException if any IO error occurs
   */
  private String exportImagingStudy(String personID, String encounterID,
      ImagingStudy imagingStudy) throws IOException {
    // Id,DATE,PATIENT,ENCOUNTER,SERIES_UID,BODYSITE_CODE,BODYSITE_DESCRIPTION,
    // MODALITY_CODE,MODALITY_DESCRIPTION,INSTANCE_UID,SOP_CODE,SOP_DESCRIPTION,PROCEDURE_CODE
    StringBuilder s = new StringBuilder();

    String studyID = imagingStudy.uuid.toString();

    for (ImagingStudy.Series series: imagingStudy.series) {
      String seriesDicomUid = series.dicomUid;
      Code bodySite = series.bodySite;
      Code modality = series.modality;
      for (ImagingStudy.Instance instance: series.instances) {
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
        s.append(sopClass.display).append(',');
        s.append(imagingStudy.codes.get(0).code);

        s.append(NEWLINE);
      }
    }

    fileManager.writeResourceLine(s.toString(), CSVConstants.IMAGING_STUDY_KEY);

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
  private void exportDevice(String personID, String encounterID, Device device)
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

    fileManager.writeResourceLine(s.toString(), CSVConstants.DEVICE_KEY);
  }

  /**
   * Write a single Supply to supplies.csv.
   *
   * @param personID     ID of the person the supply was used for.
   * @param encounterID  ID of the encounter where the supply was used
   * @param supply       The supply itself
   * @throws IOException if any IO error occurs
   */
  private void exportSupply(String personID, String encounterID, Encounter encounter, Supply supply)
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

    fileManager.writeResourceLine(s.toString(), CSVConstants.SUPPLY_KEY);
  }

  /**
   * Write a single organization to organizations.csv
   *
   * @param org         The organization to be written
   * @param utilization The total number of encounters for the org
   * @throws IOException if any IO error occurs
   */
  private void exportOrganization(Provider org, int utilization) throws IOException {
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

    fileManager.writeResourceLine(s.toString(), CSVConstants.ORGANIZATION_KEY);
  }

  /**
   * Write a single clinician to providers.csv
   *
   * @param provider The provider information to be written
   * @param orgId    ID of the organization the provider belongs to
   * @throws IOException if any IO error occurs
   */
  private void exportProvider(Clinician provider, String orgId) throws IOException {
    // Id,ORGANIZATION,NAME,GENDER,SPECIALITY,ADDRESS,CITY,STATE,ZIP,ENCOUNTERS,PROCEDURES

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
    s.append(provider.getEncounterCount()).append(',');
    s.append(provider.getProcedureCount());

    s.append(NEWLINE);

    fileManager.writeResourceLine(s.toString(), CSVConstants.PROVIDER_KEY);
  }

  /**
   * Write a single payer to payers.csv.
   *
   * @param payer The payer to be exported.
   * @throws IOException if any IO error occurs.
   */
  private void exportPayer(Payer payer) throws IOException {
    // Id,NAME,OWNERSHIP,ADDRESS,CITY,STATE_HEADQUARTERED,ZIP,PHONE,AMOUNT_COVERED,
    // AMOUNT_UNCOVERED,REVENUE,
    // COVERED_ENCOUNTERS,UNCOVERED_ENCOUNTERS,COVERED_MEDICATIONS,UNCOVERED_MEDICATIONS,
    // COVERED_PROCEDURES,UNCOVERED_PROCEDURES,COVERED_IMMUNIZATIONS,UNCOVERED_IMMUNIZATIONS,
    // UNIQUE_CUSTOMERS,QOLS_AVG,MEMBER_MONTHS

    StringBuilder s = new StringBuilder();
    // UUID
    s.append(payer.getResourceID()).append(',');
    // NAME
    s.append(payer.getName()).append(',');
    // OWNERSHIP
    s.append(payer.getOwnership()).append(',');
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
    // UNIQUE_CUSTOMERS
    s.append(payer.getUniqueCustomers()).append(",");
    // QOLS_AVG
    s.append(payer.getQolsAverage()).append(",");
    // MEMBER_MONTHS (Note that this converts the number of years covered to months)
    s.append(payer.getNumYearsCovered() * 12);

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.PAYER_KEY);
  }

  /**
   * Write a single range of unchanged payer history to payer_transitions.csv
   *
   * @param person The person whose payer history to write.
   * @param planRecord The plan
   * @throws IOException if any IO error occurs
   */
  private void exportPayerTransition(Person person, PlanRecord planRecord) throws IOException {
    // PATIENT_ID,MEMBER_ID,START_YEAR,END_YEAR,PAYER_ID,SECONDARY_PAYER_ID,OWNERSHIP,OWNERNAME

    StringBuilder s = new StringBuilder();
    // PATIENT_ID
    s.append(person.attributes.get(Person.ID)).append(",");
    // MEMBER_ID
    if (planRecord.id != null) {
      s.append(planRecord.id);
    }
    s.append(",");
    // START_YEAR
    s.append(iso8601Timestamp(planRecord.getStartTime())).append(',');
    // END_YEAR
    s.append(iso8601Timestamp(planRecord.getStopTime())).append(',');
    // PAYER_ID
    s.append(planRecord.getPlan().getPayer().getResourceID()).append(',');
    // SECONDARY_PAYER_ID
    if (planRecord.getSecondaryPlan().getPayer() != null
        && !planRecord.getSecondaryPlan().isNoInsurance()) {
      s.append(planRecord.getSecondaryPlan().getPayer().getResourceID());
    }
    s.append(',');
    // PLAN_OWNERSHIP
    if (planRecord.ownership != null) {
      s.append(planRecord.ownership);
    }
    s.append(',');
    // OWNERNAME
    if (planRecord.ownerName != null) {
      s.append(planRecord.ownerName);
    }
    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.PAYER_TRANSITION_KEY);
  }

  private void exportPatientExpense(Person person, PlanRecord planRecord) throws IOException {
    // PATIENT_ID,YEAR,PAYER_ID,HEALTHCARE_EXPENSES,INSURANCE_COSTS,COVERED_COSTS

    StringBuilder s = new StringBuilder();
    // PATIENT_ID
    s.append(person.attributes.get(Person.ID)).append(",");
    // YEAR
    s.append(iso8601Timestamp(planRecord.getStartTime())).append(',');
    // PAYER_ID
    s.append(planRecord.getPlan().getPayer().getResourceID()).append(',');
    // HEALTHCARE_EXPENSES
    double healthcareExpenses = planRecord.getOutOfPocketExpenses().doubleValue();
    s.append(healthcareExpenses).append(',');
    // INSURANCE_COSTS
    double insuranceCosts = planRecord.getInsuranceExpenses().doubleValue();
    s.append(insuranceCosts).append(',');
    // COVERED_COSTS
    double coveredExpenses = planRecord.getCoveredExpenses().doubleValue();
    s.append(coveredExpenses);

    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.PATIENT_EXPENSE_KEY);
  }

  /**
   * Return a department code for the claim.
   * @param encounter The encounter being billed.
   * @param patient The patient.
   * @return The department code.
   */
  private String claimDepartmentCode(Encounter encounter, Person patient) {
    String dept = "99";
    if (encounter.type != null) {
      EncounterType type = EncounterType.fromString(encounter.type);
      if (type != null) {
        dept = "" + type.ordinal();
        if (type == EncounterType.WELLNESS) {
          if (patient.ageInYears(encounter.start) < 18) {
            dept = "10"; // pediatric
          } else {
            dept = "20"; // adult primary
          }
        }
      }
    }
    return dept;
  }

  /**
   * Write a single claim to claims.csv.
   *
   * @param claim The claim to be exported.
   * @throws IOException if any IO error occurs.
   */
  private void exportClaim(Person person, Claim claim, Encounter encounter,
      String encounterID, long time) throws IOException {
    // Id,PATIENTID,PROVIDERID,PRIMARYPATIENTINSURANCEID,SECONDARYPATIENTINSURANCEID,
    // DEPARTMENTID,PATIENTDEPARTMENTID,DIAGNOSIS1,DIAGNOSIS2,DIAGNOSIS3,DIAGNOSIS4,
    // DIAGNOSIS5,DIAGNOSIS6,DIAGNOSIS7,DIAGNOSIS8,REFERRINGPROVIDERID,APPOINTMENTID,
    // CURRENTILLNESSDATE,SERVICEDATE,SUPERVISINGPROVIDERID,STATUS1,STATUS2,STATUSP,
    // OUTSTANDING1,OUTSTANDING2,OUTSTANDINGP,LASTBILLEDDATE1,LASTBILLEDDATE2,LASTBILLEDDATEP,
    // HEALTHCARECLAIMTYPEID1,HEALTHCARECLAIMTYPEID2

    // Institutional or Professional?
    boolean institutional = true;
    if (encounter.provider != null) {
      institutional = encounter.provider.institutional;
    }

    StringBuilder s = new StringBuilder();
    // Claim Id. Should be a number.
    String claimId = claim.uuid.toString();
    s.append(claimId).append(',');
    // PATIENTID
    s.append(claim.person.attributes.get(Person.ID)).append(',');
    // Provider, should not be null.
    if (encounter.clinician != null) {
      s.append(encounter.clinician.getResourceID()).append(',');
    } else {
      s.append(',');
    }
    // PRIMARYPATIENTINSURANCEID
    if (encounter.claim.getPayer() == null
        || encounter.claim.getPayer().isNoInsurance()) {
      s.append(","); // Empty for no insurance
    } else {
      s.append(claim.getPayer().getResourceID()).append(',');
    }
    // SECONDARYPATIENTINSURANCEID (0 default if none)
    if (encounter.claim.getSecondaryPayer() == null
        || encounter.claim.getSecondaryPayer().isNoInsurance()) {
      s.append(",");
    } else {
      s.append(claim.getSecondaryPayer().getResourceID()).append(',');
    }
    // DEPARTMENTID
    String departmentId = claimDepartmentCode(encounter, claim.person);
    s.append(departmentId).append(',');
    s.append(departmentId).append(',');
    // Diagnosis codes and illness onset
    int dxCode = 0;
    Long[] onset = new Long[8];
    String[] diagnosisCodes = new String[8];
    if (encounter.reason != null) {
      diagnosisCodes[dxCode] = encounter.reason.code;
      onset[dxCode] = claim.person.record.presentOnset(diagnosisCodes[dxCode]);
      dxCode++;
    }
    Iterator<HealthRecord.Entry> items = encounter.conditions.iterator();
    while ((dxCode < diagnosisCodes.length) && items.hasNext()) {
      Entry item = items.next();
      diagnosisCodes[dxCode] = item.codes.get(0).code;
      onset[dxCode] = claim.person.record.presentOnset(diagnosisCodes[dxCode]);
      dxCode++;
    }
    if (dxCode == 0) {
      // There must be a diagnosis code, if there aren't any (e.g. wellness visit
      // where nothing is wrong) then add the encounter code.
      diagnosisCodes[dxCode] = encounter.codes.get(0).code;
      onset[dxCode] = claim.person.record.presentOnset(diagnosisCodes[dxCode]);
    }
    for (String diagnosisCode : diagnosisCodes) {
      if (diagnosisCode != null && !diagnosisCode.isEmpty()) {
        s.append(diagnosisCode).append(',');
      } else {
        s.append(',');
      }
    }
    Long onsetIllness = encounter.start;
    for (Long onsetTime : onset) {
      if (onsetTime != null && onsetTime < onsetIllness) {
        onsetIllness = onsetTime;
      }
    }
    // TODO REFERRINGPROVIDERID
    s.append(',');
    // APPOINTMENTID
    s.append(encounterID).append(',');
    // CURRENTILLNESSDATE
    s.append(iso8601Timestamp(onsetIllness)).append(',');
    // SERVICEDATE
    s.append(iso8601Timestamp(encounter.start)).append(',');
    // SUPERVISINGPROVIDERID
    if (encounter.clinician != null) {
      s.append(encounter.clinician.getResourceID()).append(',');
    } else {
      s.append(',');
    }
    if (time > encounter.stop) {
      // STATUS1 for Payer1
      s.append("CLOSED,");
      // STATUS2 for Payer2
      if (claim.getSecondaryPayer() != null
          && claim.getSecondaryPayer().isNoInsurance()) {
        s.append("CLOSED,");
      } else {
        s.append(',');
      }
      // STATUSP for Patient as Payer
      s.append("CLOSED,");
      // OUTSTANDING1
      s.append("0,");
      // OUTSTANDING2
      if (claim.getSecondaryPayer() != null
          && claim.getSecondaryPayer().isNoInsurance()) {
        s.append("0,");
      } else {
        s.append(',');
      }
      // OUTSTANDINGP
      s.append("0,");
      // LASTBILLEDDATE1
      s.append(iso8601Timestamp(encounter.stop)).append(',');
      // LASTBILLEDDATE2
      if (claim.getSecondaryPayer() != null
          && claim.getSecondaryPayer().isNoInsurance()) {
        s.append(iso8601Timestamp(encounter.stop)).append(',');
      } else {
        s.append(',');
      }
      // LASTBILLEDDATEP
      s.append(iso8601Timestamp(encounter.stop)).append(',');

    } else {
      // TODO this may not get triggered
      // STATUS1 for Payer1
      s.append("BILLED,");
      // STATUS2 for Payer2
      if (claim.getSecondaryPayer() != null
          && claim.getSecondaryPayer().isNoInsurance()) {
        s.append("BILLED,");
      } else {
        s.append(',');
      }
      // STATUSP for Patient as Payer
      s.append("BILLED,");
      // OUTSTANDING1 (TODO this should be the outstanding payer balance)
      s.append(String.format(Locale.US, "%.2f", encounter.claim.getTotalCoveredCost()))
              .append(',');
      // OUTSTANDING2
      if (claim.getSecondaryPayer() != null
          && claim.getSecondaryPayer().isNoInsurance()) {
        // TODO this is not correct
        s.append(String.format(Locale.US, "%.2f", encounter.claim.getTotalCoveredCost()))
                .append(',');
      } else {
        s.append(',');
      }
      // OUTSTANDINGP (TODO this should be the outstanding patient balance)
      BigDecimal patientCost = claim.getTotalClaimCost().subtract(claim.getTotalCoveredCost());
      s.append(String.format(Locale.US, "%.2f", patientCost)).append(',');
      // LASTBILLEDDATE1
      s.append(iso8601Timestamp(encounter.start)).append(',');
      // LASTBILLEDDATE2
      if (claim.getSecondaryPayer() != null
          && claim.getSecondaryPayer().isNoInsurance()) {
        s.append(iso8601Timestamp(encounter.start)).append(',');
      } else {
        s.append(',');
      }
      // LASTBILLEDDATEP
      s.append(iso8601Timestamp(encounter.start)).append(',');
    }

    // HEALTHCARECLAIMTYPEID1
    if (institutional) {
      s.append("2,");
    } else {
      s.append("1,");
    }
    // HEALTHCARECLAIMTYPEID2
    if (claim.getSecondaryPayer() != null
        && claim.getSecondaryPayer().isNoInsurance()) {
      if (institutional) {
        s.append('2');
      } else {
        s.append('1');
      }
    } else {
      s.append('0');
    }
    s.append(NEWLINE);
    fileManager.writeResourceLine(s.toString(), CSVConstants.CLAIM_KEY);

    // Main Claim
    simulateClaimProcess(person, claim, claimId, encounter, encounterID, claim.mainEntry,
        diagnosisCodes, departmentId, true);

    // Each Entry...
    for (int i = 0; i < claim.items.size(); i++) {
      Claim.ClaimEntry claimEntry = claim.items.get(i);
      Entry entry = claimEntry.entry;
      if ((entry instanceof HealthRecord.Procedure)
          || (entry instanceof HealthRecord.Immunization)
          || (entry instanceof HealthRecord.Medication)) {
        simulateClaimProcess(person, claim, claimId, encounter, encounterID, claimEntry,
            diagnosisCodes, departmentId, false);
      }
    }
  }

  private void simulateClaimProcess(Person person, Claim claim, String claimId,
      Encounter encounter, String encounterId, Claim.ClaimEntry claimEntry,
      String[] diagnosisCodes, String departmentId, boolean mainEntry) throws IOException {
    long chargeId = transactionId.getAndIncrement();
    // CHARGE
    ClaimTransaction t = new ClaimTransaction(encounter, encounterId,
        claim, claimId, chargeId, claimEntry, person);
    t.type = ClaimTransactionType.CHARGE;
    t.setAmount(claimEntry.cost);
    t.departmentId = departmentId;
    t.diagnosisCodes = diagnosisCodes;
    if (claim.getPayer().isNoInsurance()) {
      t.transferType = "p";
    } else {
      t.transferType = "1";
    }

    fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
    chargeId = transactionId.getAndIncrement();

    BigDecimal remainder = claimEntry.cost;
    if (mainEntry) {
      if (claimEntry.copayPaidByPatient.compareTo(Claim.ZERO_CENTS) > 0) {
        // COPAY
        remainder = remainder.subtract(claimEntry.copayPaidByPatient);
        if (remainder.compareTo(Claim.ZERO_CENTS) < 0) {
          // If the cost of the copay is greater than the medication cost.
          remainder = Claim.ZERO_CENTS;
        }
        t = new ClaimTransaction(encounter, encounterId,
            claim, claimId, chargeId, claimEntry, person);
        t.type = ClaimTransactionType.PAYMENT;
        t.method = PaymentMethod.COPAY;
        t.payment = claimEntry.copayPaidByPatient;
        t.unpaid = remainder;
        t.departmentId = departmentId;
        t.diagnosisCodes = diagnosisCodes;
        fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
        chargeId = transactionId.getAndIncrement();
      }
    }

    // ADJUSTMENTS
    if (claimEntry.adjustment.compareTo(Claim.ZERO_CENTS) > 0) {
      remainder = remainder.subtract(claimEntry.adjustment);
      if (remainder.compareTo(Claim.ZERO_CENTS) < 0) {
        remainder = Claim.ZERO_CENTS;
      }
      t = new ClaimTransaction(encounter, encounterId,
          claim, claimId, chargeId, claimEntry, person);
      t.type = ClaimTransactionType.ADJUSTMENT;
      t.method = PaymentMethod.SYSTEM;
      t.adjustment = claimEntry.adjustment;
      t.unpaid = remainder;
      t.departmentId = departmentId;
      t.diagnosisCodes = diagnosisCodes;
      fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
      chargeId = transactionId.getAndIncrement();
    }

    BigDecimal payerAmount = claimEntry.paidByPayer.add(claimEntry.coinsurancePaidByPayer);
    if (payerAmount.compareTo(Claim.ZERO_CENTS) > 0) {
      // PAYMENT FROM INSURANCE
      remainder = remainder.subtract(payerAmount);
      t = new ClaimTransaction(encounter, encounterId,
          claim, claimId, chargeId, claimEntry, person);
      t.type = ClaimTransactionType.PAYMENT;
      t.method = PaymentMethod.ECHECK;
      t.payment = payerAmount;
      t.unpaid = remainder;
      t.departmentId = departmentId;
      t.diagnosisCodes = diagnosisCodes;
      fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
      chargeId = transactionId.getAndIncrement();
    }

    BigDecimal secondaryPayerAmount = claimEntry.paidBySecondaryPayer;
    if (secondaryPayerAmount.compareTo(Claim.ZERO_CENTS) > 0) {
      // TRANSFEROUT
      t = new ClaimTransaction(encounter, encounterId,
          claim, claimId, chargeId, claimEntry, person);
      t.type = ClaimTransactionType.TRANSFEROUT;
      t.amount = remainder;
      t.unpaid = remainder;
      t.departmentId = departmentId;
      t.diagnosisCodes = diagnosisCodes;
      fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
      long transferOut = chargeId;
      chargeId = transactionId.getAndIncrement();

      // TRANSFERIN
      t = new ClaimTransaction(encounter, encounterId,
          claim, claimId, chargeId, claimEntry, person);
      t.type = ClaimTransactionType.TRANSFERIN;
      t.transferType = "2"; // "2" if secondary insurance
      t.transferId = transferOut;
      t.amount = remainder;
      t.unpaid = remainder;
      t.departmentId = departmentId;
      t.diagnosisCodes = diagnosisCodes;
      fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
      chargeId = transactionId.getAndIncrement();

      // PAYMENT FROM SECONDARY INSURANCE
      remainder = remainder.subtract(secondaryPayerAmount);

      // PAYMENT
      t = new ClaimTransaction(encounter, encounterId,
          claim, claimId, chargeId, claimEntry, person);
      t.type = ClaimTransactionType.PAYMENT;
      t.method = PaymentMethod.ECHECK;
      t.payment = secondaryPayerAmount;
      t.unpaid = remainder;
      t.departmentId = departmentId;
      t.diagnosisCodes = diagnosisCodes;
      fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
      chargeId = transactionId.getAndIncrement();
    }

    if (remainder.compareTo(Claim.ZERO_CENTS) > 0) {
      if (!claim.getPayer().isNoInsurance()) {
        // TRANSFEROUT
        t = new ClaimTransaction(encounter, encounterId,
            claim, claimId, chargeId, claimEntry, person);
        t.type = ClaimTransactionType.TRANSFEROUT;
        t.amount = remainder;
        t.unpaid = remainder;
        t.departmentId = departmentId;
        t.diagnosisCodes = diagnosisCodes;
        fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
        long transferOut = chargeId;
        chargeId = transactionId.getAndIncrement();

        // TRANSFERIN
        t = new ClaimTransaction(encounter, encounterId,
            claim, claimId, chargeId, claimEntry, person);
        t.type = ClaimTransactionType.TRANSFERIN;
        t.transferType = "p"; // patient
        t.transferId = transferOut;
        t.amount = remainder;
        t.unpaid = remainder;
        t.departmentId = departmentId;
        t.diagnosisCodes = diagnosisCodes;
        fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
        chargeId = transactionId.getAndIncrement();
      }
      // PAYMENT
      t = new ClaimTransaction(encounter, encounterId,
          claim, claimId, chargeId, claimEntry, person);
      t.type = ClaimTransactionType.PAYMENT;
      PaymentMethod[] opts = { PaymentMethod.CASH,
          PaymentMethod.CHECK,
          PaymentMethod.CC};
      // a choice that "looks random" but is consistent when chargeID is consistent
      t.method = opts[(int)(chargeId % opts.length)];
      t.payment = remainder;
      t.unpaid = Claim.ZERO_CENTS;
      t.departmentId = departmentId;
      t.diagnosisCodes = diagnosisCodes;
      fileManager.writeResourceLine(t.toString(), CSVConstants.CLAIM_TRANSACTION_KEY);
      chargeId = transactionId.getAndIncrement();
    }
  }

  public enum ClaimTransactionType {
    CHARGE, PAYMENT, ADJUSTMENT, TRANSFERIN, TRANSFEROUT;
  }

  public enum PaymentMethod {
    CASH, CHECK, COPAY, SYSTEM, CC, ECHECK;
  }

  public class ClaimTransaction {
    String id;
    String encounterId;
    String claimId;
    long chargeId;
    long transferId;
    String transferType;
    String patientId;
    String memberId;
    ClaimTransactionType type;
    BigDecimal amount = Claim.ZERO_CENTS;
    Integer units;
    BigDecimal unitAmount = Claim.ZERO_CENTS;
    BigDecimal payment = Claim.ZERO_CENTS;
    BigDecimal adjustment = Claim.ZERO_CENTS;
    BigDecimal unpaid = Claim.ZERO_CENTS;
    PaymentMethod method;
    long start;
    long stop;
    String organizationId;
    String departmentId;
    String clinicianId;
    String procedureCode;
    String procedureDisplay;
    String[] diagnosisCodes;
    String notes;

    /**
     * Create a new ClaimTransaction.
     * @param encounter The Encounter.
     * @param encounterId The Encounter ID.
     * @param claim The Claim.
     * @param claimId The Claim ID.
     * @param chargeId The Charge ID.
     * @param claimEntry The entry for the transactions.
     * @param person The Person.
     */
    public ClaimTransaction(Encounter encounter, String encounterId, Claim claim, String claimId,
        long chargeId, Claim.ClaimEntry claimEntry, Person person) {
      // NOTE: see note above about the transactionId field
      // ID here will only be consistent if chargeID is consistent
      this.id = ExportHelper.buildUUID(person, encounter.start,
          "ClaimTransaction for Claim " + claimId + " " + chargeId);
      this.encounterId = encounterId;
      this.claimId = claimId;
      this.chargeId = chargeId;
      this.patientId = (String) claim.person.attributes.get(Person.ID);
      this.memberId = claim.getPlanRecordMemberId();
      this.units = 1;
      this.start = claimEntry.entry.start;
      this.stop = claimEntry.entry.stop;
      if (encounter.provider != null) {
        this.organizationId = encounter.provider.getResourceID();
      }
      if (encounter.clinician != null) {
        this.clinicianId = encounter.clinician.getResourceID();
      }
      this.procedureCode = clean(claimEntry.entry.codes.get(0).code);
      this.procedureDisplay = clean(claimEntry.entry.codes.get(0).display);
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
      this.unitAmount = amount;
    }

    /**
     * Convert this ClaimTransaction into a CSV row.
     */
    public String toString() {
      // ID,CLAIMID,CHARGEID,PATIENTID,TYPE,AMOUNT,METHOD,FROMDATE,TODATE,
      // PLACEOFSERVICE,PROCEDURECODE,MODIFIER1,MODIFIER2,DIAGNOSISREF1,DIAGNOSISREF2,
      // DIAGNOSISREF3,DIAGNOSISREF4,UNITS,DEPARTMENTID,NOTES,UNITAMOUNT,TRANSFEROUTID,
      // TRANSFERTYPE,PAYMENTS,ADJUSTMENTS,TRANSFERS,OUTSTANDING,APPOINTMENTID,LINENOTE,
      // PATIENTINSURANCEID,FEESCHEDULEID,PROVIDERID,SUPERVISINGPROVIDERID
      StringBuilder s = new StringBuilder();
      // ID
      s.append(id).append(',');
      // CLAIMID
      s.append(claimId).append(',');
      // CHARGEID
      s.append(chargeId).append(',');
      // PATIENTID
      s.append(patientId).append(',');
      // TYPE: CHARGE, PAYMENT, ADJUSTMENT, TRANSFERIN, TRANSFEROUT
      s.append(type.toString()).append(',');
      // AMOUNT
      if (type == ClaimTransactionType.CHARGE || type == ClaimTransactionType.TRANSFERIN) {
        s.append(String.format(Locale.US, "%.2f", amount));
      }
      s.append(',');
      // METHOD
      if (type == ClaimTransactionType.PAYMENT || type == ClaimTransactionType.ADJUSTMENT) {
        s.append(method);
      }
      s.append(',');
      // FROMDATE
      s.append(iso8601Timestamp(start)).append(',');
      // TODATE
      s.append(iso8601Timestamp(stop)).append(',');
      // PLACEOFSERVICE
      s.append(organizationId).append(',');
      // PROCEDURECODE
      s.append(procedureCode).append(',');
      // MODIFIER1
      s.append(',');
      // MODIFIER2
      s.append(',');
      // DIAGNOSISREF1, DIAGNOSISREF2, DIAGNOSISREF3, DIAGNOSISREF4
      // Diagnosis codes
      for (int i = 0; i < 4; i++) {
        String diagnosisCode = diagnosisCodes[i];
        if (diagnosisCode != null && !diagnosisCode.isEmpty()) {
          s.append(i + 1).append(','); // don't write the code, write the reference number
        } else {
          s.append(',');
        }
      }
      // UNITS
      if (units != null) {
        s.append(units);
      }
      s.append(',');
      // DEPARTMENTID
      s.append(departmentId).append(',');
      // NOTES
      s.append(clean(procedureDisplay)).append(',');
      // UNITAMOUNT
      if (unitAmount != null) {
        s.append(String.format(Locale.US, "%.2f", unitAmount));
      }
      s.append(',');
      // TRANSFEROUTID
      if (type == ClaimTransactionType.TRANSFERIN) {
        s.append(transferId);
      }
      s.append(',');
      // TRANSFERTYPE. 1=primary insurance, 2=secondary, p==patient
      if (type == ClaimTransactionType.CHARGE || type == ClaimTransactionType.TRANSFERIN) {
        s.append(transferType);
      }
      s.append(',');
      // PAYMENTS
      if (payment != null) {
        s.append(String.format(Locale.US, "%.2f", payment));
      }
      s.append(',');
      // ADJUSTMENTS
      if (adjustment != null) {
        s.append(String.format(Locale.US, "%.2f", adjustment));
      }
      s.append(',');
      // TRANSFERS
      if (type == ClaimTransactionType.TRANSFERIN || type == ClaimTransactionType.TRANSFEROUT) {
        s.append(String.format(Locale.US, "%.2f", amount));
      }
      s.append(',');
      // OUTSTANDING
      if (unpaid != null) {
        s.append(String.format(Locale.US, "%.2f", unpaid));
      }
      s.append(',');
      // APPOINTMENTID
      s.append(encounterId).append(',');
      // LINENOTE
      s.append(',');
      // PATIENTINSURANCEID
      if (memberId != null) {
        s.append(memberId);
      }
      s.append(',');
      // TODO FEESCHEDULEID
      s.append("1").append(',');
      // PROVIDERID
      s.append(clinicianId).append(',');
      // SUPERVISINGPROVIDERID
      s.append(clinicianId);
      s.append(NEWLINE);
      return s.toString();
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
}
