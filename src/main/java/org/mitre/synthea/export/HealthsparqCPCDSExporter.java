package org.mitre.synthea.export;

import com.opencsv.CSVWriter;
import org.apache.commons.lang3.ObjectUtils;
import org.hl7.fhir.r4.model.Enumerations;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.SnomedConversion;
import org.mitre.synthea.world.geography.Location;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.world.agents.Clinician.*;


public class HealthsparqCPCDSExporter {

  private static final String CLAIM_CODE_DISPLAY = "display";
  private static final String CLAIM_CODE_CODE = "code";
  private static final String CLAIM_CODE_SYSTEM = "system";

  private CSVWriter patientsWriter;
  private CSVWriter coverageWriter;
  private CSVWriter claimWriter;
  private CSVWriter claimLineWriter;

  private SnomedConversion snomedConversion;

  /**
   * Constructor to be used for testing purposes.  Allows testing of other methods without file IO.
   *
   * @param patientsWriter CSV writer for Patient
   * @param coverageWriter CSV writer for Coverage
   * @param claimWriter CSV writer for Claims
   * @param claimLineWriter CSV writer for Claim Lines
   */
  protected HealthsparqCPCDSExporter(CSVWriter patientsWriter,
                                     CSVWriter coverageWriter,
                                     CSVWriter claimWriter,
                                     CSVWriter claimLineWriter) {
    this.patientsWriter = patientsWriter;
    this.coverageWriter = coverageWriter;
    this.claimWriter = claimWriter;
    this.claimLineWriter = claimLineWriter;
  }

  /**
   * Constructor that sets up the output directory, CSV files, and file writers.
   */
  private HealthsparqCPCDSExporter() {
    try {
      // load snomed converter once (expensive operation)
      snomedConversion = new SnomedConversion();
      snomedConversion.loadSnomedMap("src/main/resources/snomed_mapping.txt",
          "src/main/resources/icd10_displays_fy2016.csv");
      Path outputDirectory = createOutputDir();

      // if folder per run is wanted, folder structure:
      // output/cpcds/yyyy-mm-dd-time/clientcode(if provided)/Member/patients.csv
      if (Boolean.parseBoolean(Config.get("exporter.healthsparq.cpcds.folder_per_run"))) {
        String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
        String subfolderName = timestamp.replaceAll("\\W+", ""); // make sure it's filename-safe
        String clientCode = Config.get("exporter.healthsparq.cpcds.clientcd");
        outputDirectory = outputDirectory.resolve(subfolderName + "/" + clientCode);
        outputDirectory.toFile().mkdirs();
        patientsWriter = makeCsvWriter("patients", getPath(outputDirectory, "Member"));
        coverageWriter = makeCsvWriter("coverage", getPath(outputDirectory, "Coverage"));
        claimWriter = makeCsvWriter("claim", getPath(outputDirectory, "Claim"));
        claimLineWriter = makeCsvWriter("claimLine", getPath(outputDirectory, "ClaimLine"));
      } else {
        patientsWriter = makeCsvWriter("patients", outputDirectory);
        coverageWriter = makeCsvWriter("coverage", outputDirectory);
        claimWriter = makeCsvWriter("claim", outputDirectory);
        claimLineWriter = makeCsvWriter("claimLine", outputDirectory);
      }
      writeCpcdsHeaders();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Path getPath(Path outputDirectory, String folderName) {
    Path folderNameDirectory = outputDirectory.resolve(folderName);
    folderNameDirectory.toFile().mkdirs();
    return folderNameDirectory;
  }

  /**
   * Creates a folder for the CPCDS format export files.
   *
   * @return Path to the folder
   */
  private Path createOutputDir() {
    File output = Exporter.getOutputFolder("cpcds", null);
    output.mkdirs();
    return output.toPath();
  }

  /**
   * Writes all static CSV headers to their respective files.
   */
  private void writeCpcdsHeaders() {
    patientsWriter.writeNext(patientHeaders);
    coverageWriter.writeNext(coverageHeaders);
    claimWriter.writeNext(claimHeaders);
    claimLineWriter.writeNext(claimLineHeaders);
  }

  /**
   * Creates CSVWriter and file with the given string and directory.
   *
   * @param fileWriterName name of the output CSV file
   * @param outputDirectory directory location to put the CSV file
   * @return CSVWriter to write CSV contents
   * @throws IOException if IO error occurs
   */
  private CSVWriter makeCsvWriter(String fileWriterName, Path outputDirectory) throws IOException {
    File file = outputDirectory.resolve(fileWriterName + ".csv").toFile();
    return new CSVWriter(
        new FileWriter(file),
        '|',
        CSVWriter.NO_QUOTE_CHARACTER,
        CSVWriter.NO_QUOTE_CHARACTER,
        CSVWriter.DEFAULT_LINE_END);
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {

    /**
     * Singleton instance of the HealthsparqCPCDSExporter.
     */
    private static final HealthsparqCPCDSExporter instance = new HealthsparqCPCDSExporter();

  }

  /**
   * Get the current instance of the HealthsparqCPCDSExporter.
   *
   * @return the current instance of the HealthsparqCPCDSExporter.
   */
  public static HealthsparqCPCDSExporter getInstance() {
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
    patientsWriter.writeNext(buildPatientCpcdsStringArray(person));
    writeCoverageToCpcdsCsv(person, time);
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      String claimId = clean(UUID.randomUUID().toString());
      claimWriter.writeNext(buildClaimCpcdsStringArray(encounter, claimId, encounter.claim));
      claimWriter.flush();
      int sequenceNumber = 1;
      //top claim line
      claimLineWriter.writeNext(buildClaimLineCpcdsStringArray(sequenceNumber,
          encounter,
          encounter,
          claimId));
      sequenceNumber++;
      for (HealthRecord.Entry claimItem: encounter.claim.items) {
        claimLineWriter.writeNext(buildClaimLineCpcdsStringArray(sequenceNumber,
            claimItem,
            encounter,
            claimId));
        claimLineWriter.flush();
        sequenceNumber++;
      }
    }
    patientsWriter.flush();
  }

  /**
   * Write a single Patient line, to patients file.
   *
   * @param person Person to write data for
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   */
  protected String[] buildPatientCpcdsStringArray(Person person) {

    return  new String[] {
        Config.get("exporter.healthsparq.cpcds.clientcd"), // client_cd
        clean((String) person.attributes.get(Person.ID)), //cpc patient_uuid
        clean((String) person.attributes.get(Person.ID)), // patient_id
        clean(dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE))), // dob
        (person.attributes.get(Person.DEATHDATE) != null
            ? clean(dateFromTimestamp((long) person.attributes.get(Person.DEATHDATE)))
            : ""), // dod
        clean((String) person.attributes.getOrDefault("county", "")), // county
        clean(Location.getAbbreviation(
            person.attributes.getOrDefault(Person.STATE, "").toString())), // state
        "US", // country
        clean((String) person.attributes.getOrDefault(Person.RACE, "")), // race_cd
        clean((String) person.attributes.getOrDefault(Person.ETHNICITY, "")), // ethnicity_cd
        (person.attributes.get(Person.GENDER) == "M"
            ? Enumerations.AdministrativeGender.MALE.toCode()
            : Enumerations.AdministrativeGender.FEMALE.toCode()), // birth_sex
        clean((String) person.attributes.getOrDefault(Person.GENDER, "")), // gender_cd
        clean((String) person.attributes.getOrDefault(Person.NAME, "")), // name
        clean((String) person.attributes.getOrDefault(Person.FIRST_NAME, "")), // first_name
        "", // middle_name
        clean((String) person.attributes.getOrDefault(Person.LAST_NAME, "")), // last_name
        clean((String) person.attributes.getOrDefault(Person.NAME_SUFFIX, "")), // name_suffix
        clean((String) person.attributes.getOrDefault(Person.ZIP, "")) // zip
    };
  }

  /**
   * Iterate over a Person's history and write coverage for every year of life in the record.
   *
   * @param person Person to write data for
   * @param stopTime time simulation ended
   */
  private void writeCoverageToCpcdsCsv(Person person, long stopTime) throws IOException {
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
        coverageWriter.writeNext(
            buildCoverageCpcdsStringArray(
                person,
                startYear,
                currentYear,
                currentPayer));

        coverageWriter.flush();
        previousPayerID = currentPayer.getResourceID();
        previousOwnership = currentOwnership;
        startYear = currentYear + 1;
      }
      currentYear++;
    }
  }

  /**
   * Builds up the coverage string array for printing to the Coverage CSV file.
   * Coverage period month and day is hard-coded for now.
   * If subscriber relationship is Guardian, change to Dependent
   * (patient relationship to subscriber in CPCDS)
   *
   * @param person Person to write coverage data
   * @param startYear Year of start of coverage
   * @param currentYear Year of end of coverage
   * @param currentPayer Payer for coverage
   * @return built up coverage string for a person for writing to Coverage CPCDS format file
   */
  protected String[] buildCoverageCpcdsStringArray(Person person,
                                                   int startYear,
                                                   int currentYear,
                                                   Payer currentPayer) {

    return new String[] {
        Config.get("exporter.healthsparq.cpcds.clientcd"), // client_cd
        // temporary solution to needed data. coverage id is the same as the
        // subscriber id
        determineCoverageAndSubscriberUuid(person, startYear), // coverage_uuid
        clean((String) person.attributes.get(Person.ID)), // patient_uuid
        // temporary solution to needed data
        determineCoverageAndSubscriberUuid(person, startYear), // subscriber_id
        getPatientRelationshipToSubscriber(person.getPayerOwnershipAtTime(
            Utilities.convertCalendarYearsToTime(
                startYear))), // relationship_cd
        "", // coverage_type
        (currentPayer.getName().equals("NO_INSURANCE")
            ? ""
            : (currentYear == Calendar.getInstance().get(Calendar.YEAR)
            ? "active"
            : "cancelled")), // coverage_status
        // Hard Code month and day for coverage period for now
        (startYear + "-01-01"),  // eff_dt
        (currentYear + "-12-31"), // end_dt
        "", // group_id
        "", // group_name
        currentPayer.getName(), // plan
        currentPayer.uuid // payer_id
    };
  }

  /**
   * Will change relationship to dependent if the subscriber is the guardian.
   * Handles null case too.
   *
   * @param relationship String of subscriber relationship to patient
   * @return String of the patient relationship to subscriber
   */
  protected String getPatientRelationshipToSubscriber(String relationship) {
    return (relationship == null ? "" :
        (relationship.equals("Guardian")
            ? "Dependent" : relationship));
  }

  /**
   * Temporary data solution for coverage UUID and subscriberID
   * Gives the UUID of the coverage owner. If the patient is the
   * coverage owner, the returned value will be the patient ID.
   * All other cases returns a new UUID created from the Patient's
   * last name.
   * @param person used to get payer ownership and determine id
   * @param year year of coverage to determine ownership
   * @return Generated ID of person who owns the coverage based on patient last name
   */
  private String determineCoverageAndSubscriberUuid(Person person, int year) {
    String owner = person.getPayerOwnershipAtTime(
        Utilities.convertCalendarYearsToTime(
            year));
    if (owner.equals("Self")) {
      return person.attributes.get(Person.ID).toString();
    } else {
      return (UUID.nameUUIDFromBytes(person.attributes.get(Person.LAST_NAME)
          .toString().getBytes())).toString();
    }
  }

  /**
   * Builds up claim content string array for writing to the output CSV file.
   *
   * @param encounter individual health record enounter to be written to claim
   * @param claimId claim UUID generated at start of processing encounter
   * @param claim the claim associated with this encounter to be processed
   * @return built claim string array
   */
  protected String[] buildClaimCpcdsStringArray(HealthRecord.Encounter encounter,
                                                String claimId,
                                                Claim claim) {
    Random random = new Random(encounter.stop);

    return new String[]{
        Config.get("exporter.healthsparq.cpcds.clientcd"), // client_cd
        clean((String) claim.person.attributes.get(Person.ID)), // patient_uuid
        // temporary solution to needed data value
        determineCoverageAndSubscriberUuid(encounter.claim.person,
            Utilities.getYear(encounter.stop)), // coverage_uuid
        // temporary solution to needed data value
        claimId, //claim_uuid
        clean(dateFromTimestamp(encounter.start)), // claim_start_dt
        clean(dateFromTimestamp(encounter.stop)), // claim_end_dt
        clean(dateFromTimestamp((long) (encounter.stop
            + rand(432000000, 2592000000D, random)))), // claim_paid_dt
        clean(dateFromTimestamp((long) (encounter.stop
            + rand(259200000,432000000, random)))), // claim_rcvd_dt
        clean(dateFromTimestamp(encounter.start)), // claim_adm_dt
        clean(dateFromTimestamp(encounter.stop)), // claim_disch_dt
        clean((String) claim.person.attributes.get(Person.ID)), // ptnt_acct_num
        clean((String) claim.person.attributes.get(Person.ID)), // mrn
        claimId, // claim_id
        "", // claim_adju_from_id
        "", // claim_adj_to_id
        // TODO: translate encounter code to drg code
        (ObjectUtils.isEmpty(encounter.reason) ? "" : clean(encounter.reason.code)), //DRG code
        "", // claim_src_ip_admit_cd
        "", // claim_ip_type_admit_cd
        "", // claim_bill_fac_type_code
        "", // claim_svc_cls_type_cd need clarification on this code
        "", // claim_freq_cd
        "active", // claim_proc_status_cd Hard-coded as active in fhir exporter
        "institutional", // claim_type_cd Hard-coded in fhir exporter
        (ObjectUtils.isEmpty(encounter.discharge)
            ? ""
            : encounter.discharge.code), // ptnt_disch_status_cd
        "", // claim_adj_dnl_cd
        (ObjectUtils.isEmpty(claim.payer)
            ? ""
            : clean(claim.payer.getResourceID())), // claim_prm_payer_id
        "provider", // claim_payee_type_cd seems that provider would be only option
        (ObjectUtils.isEmpty(encounter.provider)
            ? ""
            : clean(encounter.provider.getResourceID())), // claim_payee_id
        "", // claim_pay_status_cd
        (ObjectUtils.isEmpty(claim.payer)
            ? ""
            : clean(claim.payer.getResourceID())), // claim_payer_id
        "", // day_supply
        "", // rx_srv_ref_num
        "", // daw_prod_slctn_cd
        "", // fill_num
        "", // rx_orgn_cd
        "", // brnd_gnrc_cd
        "", // rx_srv_type_cd
        "", // ptnt_rsdnc_cd
        (ObjectUtils.isEmpty(encounter.provider) ? "" : clean(encounter.provider.id)), // bill_npi
        "", // bill_prov_ntwk_flag
        (ObjectUtils.isEmpty(encounter.clinician)
            ? ""
            : clean(String.valueOf(encounter.clinician.identifier))), // attnd_npi
        "", // attnd_prov_ntwk_flag
        "", // site_of_svc_npi
        "", // site_of_svc_prov_ntwk_flag
        "", // refer_npi
        "", // refer_prov_ntwk_flag,
        (ObjectUtils.isEmpty(encounter.clinician)
            ? ""
            : clean(String.valueOf(encounter.clinician.identifier))), // render_npi
        "", // render_prov_ntwk_flag
        "", // prscrb_npi
        "", // prscrb_provider_ntwk_flag
        (ObjectUtils.isEmpty(encounter.clinician)
            ? ""
            : clean(String.valueOf(encounter.clinician.identifier))), // pcp_npi questionable w/fhir
        String.format("%.2f", claim.getTotalClaimCost()), // tot_amt_req
        String.format("%.2f", claim.claimCosts.getCoveredCost()), // tot_amt_eqv check on this
        // point of service payment. use copay for now
        String.format("%.2f", claim.claimCosts.getPatientPointOfServicePaid()), // tot_amt_ptnt_pay
        String.format("%.2f", claim.claimCosts.getOverallCost()), // tot_amt_prov_pay
        String.format("%.2f", claim.claimCosts.getMemberReimbursementAmount()), // tot_amt_reimb
        String.format("%.2f", claim.getTotalClaimCost()), // tot_amt_pay
        // factors in copay and deductible
        String.format("%.2f", claim.claimCosts.getUncoveredCost()), // tot_amt_dsalwd
        String.format("%.2f", claim.claimCosts.getDeductiblePaid()), // tot_amt_ded
        String.format("%.2f", claim.claimCosts.getPatientCoinsurance()), // tot_amt_coin
        String.format("%.2f", claim.claimCosts.getCopayPaid()), // tot_amt_copay
        // member liability.  just use member coinsurance amount
        String.format("%.2f", claim.claimCosts.getMemberLiability()), // tot_amt_memb
        String.format("%.2f", claim.claimCosts.getClaimPrimaryPayerPaid()), // tot_amt_prm_pay
        String.format("%.2f", claim.claimCosts.getClaimDiscountAmount()) // tot_amt_dscnt
    };
  }

  /**
   * Returns a random long in the given range.
   * Borrowed from Person.java to generate random dates to
   * temporarily fill in dates in claim
   * @param low low end of desired random number range
   * @param high high end of desired random number range
   * @param random random generator with seed
   * @return random number in the low and high range
   */
  public double rand(double low, double high, Random random) {
    return (low + ((high - low) * random.nextDouble()));
  }

  /**
   * Builds claim line string array for writing to a CSV file.
   * @param sequenceNumber Claim Line item number for overall Claim
   * @param claimItem individual claim line item from overall claim
   * @param encounter overall encounter including all claims details
   * @param claimId generated UUID to track a claim and its items
   * @return claim line string array for writing to a CSV file
   */
  protected String[] buildClaimLineCpcdsStringArray(int sequenceNumber,
                                                    HealthRecord.Entry claimItem,
                                                    HealthRecord.Encounter encounter,
                                                    String claimId) {
    // Get class names to compare if a claimItem is of the type. Outputs vary based on type
    String entryTypeItemName = HealthRecord.Entry.class.getSimpleName();
    String immunizationTypeItemName = HealthRecord.Immunization.class.getSimpleName();

    return new String[] {
        Config.get("exporter.healthsparq.cpcds.clientcd"), // client_cd
        claimId, // claim_uuid
        clean(dateFromTimestamp(claimItem.start)), // dos_from
        String.valueOf(sequenceNumber), // claim_line_n
        // if claim item is a diagnosis, only use start time. stop time seems to track disease
        ((compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            || compareClaimItemEntryClassType(immunizationTypeItemName, claimItem))
            ? clean(dateFromTimestamp(claimItem.start))
            : clean(dateFromTimestamp(claimItem.stop))), // dos_to
        "1", // type_srv_cd hard-coded as 1: Medical Care in FhirR4.java:1169
        getPlaceOfServiceCodeFromEncounterType(encounter.type), // pos_cd check on code standards
        "", // rev_cd clinician organization?
        "", // max_alwd_unit
        ((claimItem instanceof HealthRecord.Immunization)
            ? claimItem.codes.get(0).code
            : ""), // ndc_cd. reference is vaccine administered code. put in cmpnd_cd spot?
        "", // cmpnd_cd
        "", // qty_dspnsd could hard code to one for now on vaccine?
        "", // qty_qual_cd medication entries has this but is not populated in item list
        "", // line_benefit_pay_status
        "", // line_pay_dnl_cd
        // if no insurance, default to disallowed? cover total cost right now if covered
        String.format("%.2f", claimItem.claimLineCosts.getUncoveredCost()), // amt_dsalwd
        String.format("%.2f", claimItem.claimLineCosts.getMemberReimbursementAmount()), // amt_reimb
        // paid at point of service. for now, just copay
        String.format("%.2f",
            claimItem.claimLineCosts.getPatientPointOfServicePaid()), // amt_ptnt_pay
        "", // amt_rx significance? Vaccine add to cost or does it matter?
        String.format("%.2f",
            claimItem.claimLineCosts.getOverallCost()), // amt_pay total amount paid to provider
        String.format("%.2f",
            claimItem.claimLineCosts.getOverallCost()), // amt_prov_pay ???????????

        // always set to zero in FHIRR4 Exporter
        String.format("%.2f",
            claimItem.claimLineCosts.getDeductiblePaid()), // amt_ded

        // zero for now: only one payer assumed
        String.format("%.2f", claimItem.claimLineCosts.getClaimPrimaryPayerPaid()), // amt_prm_pay

        String.format("%.2f",
            claimItem.claimLineCosts.getPatientCoinsurance()), // amt_coin member portion
        String.format("%.2f",
            claimItem.claimLineCosts.getOverallCost()), // amt_req total cost provider asks for
        String.format("%.2f", claimItem.claimLineCosts.getCoveredCost()), // amt_eqv
        String.format("%.2f",
            claimItem.claimLineCosts.getMemberLiability()), // amt_memb member liability
        String.format("%.2f", claimItem.claimLineCosts.getCopayPaid()), // amt_copay
        String.format("%.2f", claimItem.claimLineCosts.getClaimDiscountAmount()), // amt_dscnt

        // will use ICD-10 code if translation files loaded and translation found
        (compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? snomedConversion.findIcd10Code(claimItem.codes.get(0)).code
            : ""), // diag_cd

        // display needs to be ICD, currently always SNOMED
        (compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? clean(claimItem.codes.get(0).display)
            : ""), // diag_desc
        "", // poa_ind

        // if ICD-10 translation found, system will be ICD-10, else original system
        (compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? (snomedConversion.findIcd10Code(claimItem.codes.get(0)).system)
            : ""), // diag_vsn
        "", // diag_type
        "", // is_e_cd
        (!compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? buildMultiValueEntryStringClaimCodes(claimItem.codes, CLAIM_CODE_CODE)
            : ""), // proc_cd
        (!compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? buildMultiValueEntryStringClaimCodes(claimItem.codes, CLAIM_CODE_DISPLAY)
            : ""), // proc_desc
        (!compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? clean(dateFromTimestamp(claimItem.start))
            : ""), // proc_dt ???? use start/end time?
        (!compareClaimItemEntryClassType(entryTypeItemName, claimItem)
            ? buildMultiValueEntryStringClaimCodes(claimItem.codes, CLAIM_CODE_SYSTEM)
            : ""), // proc_cd_type
        "", // proc_type
        "", // proc_mod1
        "", // proc_mod2
        "", // proc_mod3
        "" // proc_mod4
    };
  }

  /**
   * Builds a string for dynamic multivalued CPCDS columns.  Values are
   * followed and separated by semicolons.
   * @param codeList a list of procedure/diagnosis Codes from a Claim line item
   * @param codeComponent flag for output string content
   * @return a string of Code attributes values separated by semicolons
   */
  protected String buildMultiValueEntryStringClaimCodes(List<HealthRecord.Code> codeList,
                                                        String codeComponent) {
    String codeString = "";
    for (int i = 0; i < codeList.size(); i++) {
      switch (codeComponent) {
        case CLAIM_CODE_CODE:
          codeString = ((i == codeList.size() - 1)
              ? codeString.concat(codeList.get(i).code.replace(';', '['))
              : codeString.concat(codeList.get(i).code.replace(';','[')).concat(";"));
          break;
        case CLAIM_CODE_DISPLAY:
          codeString = ((i == codeList.size() - 1)
              ? codeString.concat(codeList.get(i).display.replace(';', '['))
              : codeString.concat(codeList.get(i).display.replace(';','[')).concat(";"));
          break;
        case CLAIM_CODE_SYSTEM:
          codeString = ((i == codeList.size() - 1)
              ? codeString.concat(codeList.get(i).system.replace(';', '['))
              : codeString.concat(codeList.get(i).system.replace(';','[')).concat(";"));
          break;
        default:
          return codeString;
      }
    }
    return codeString;
  }

  /**
   * Different HealthRecord.Entry types contain different types of data.
   * Checking entry type class name is one of the few
   * ways to know what a given claim line item's codes and dates will pertain to.
   * @param entryTypeToCheckAgainst type of entry to check for
   * @param entryTypeToCheck Claim line item to check type of
   * @return boolean of true if the entryTypeToCheck is of type entryTypeToCheckAgainst
   */
  protected boolean compareClaimItemEntryClassType(String entryTypeToCheckAgainst,
                                                   HealthRecord.Entry entryTypeToCheck) {
    return (entryTypeToCheckAgainst.equals(entryTypeToCheck.getClass().getSimpleName()));
  }


  // TODO: check code values for HealthSparq correspondence
  /**
   * Maps encountertype string to the code.  CPCDS specifies a code.
   * @param encounterType type of encounter to be mapped to a code
   * @return string facility type code
   */
  protected String getPlaceOfServiceCodeFromEncounterType(String encounterType) {
    String code;
    switch (encounterType) {
      case EMERGENCY:
      case URGENTCARE:
        code = "20"; // "Urgent Care Facility";
        break;
      case WELLNESS:
        code = "19"; // "Off Campus-Outpatient Hospital";
        break;
      case AMBULATORY:
      case INPATIENT:
      default:
        code = "21"; // "Inpatient Hospital";
    }
    return code;
  }

  /**
   * Replaces commas and line breaks in the source string with a single space.
   * Null is replaced with the empty string.
   *
   * @param src string to be processed for writing to CSV
   * @return cleaned string appropriate for writing to CSV
   */
  protected static String clean(String src) {
    if (src == null) {
      return "";
    } else {
      return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
    }
  }

  private static final String[] patientHeaders = new String[] {
      "client_cd",
      "patient_uuid",
      "patient_id",
      "dob",
      "dod",
      "county",
      "state",
      "country",
      "race_cd",
      "ethnicity_cd",
      "birth_sex",
      "gender_cd",
      "name",
      "first_name",
      "middle_name",
      "last_name",
      "name_suffix",
      "zip"
  };

  private static final String[] coverageHeaders = new String[] {
      "client_cd",
      "coverage_uuid",
      "patient_uuid",
      "subscriber_id",
      "relationship_cd",
      "coverage_type",
      "coverage_status",
      "eff_dt",
      "end_dt",
      "group_id",
      "group_name",
      "plan",
      "payer_id"
  };

  private static final String[] claimHeaders = new String[] {
      "client_cd",
      "patient_uuid",
      "coverage_uuid",
      "claim_uuid",
      "claim_start_dt",
      "claim_end_dt",
      "claim_paid_dt",
      "claim_rcvd_dt",
      "claim_adm_dt",
      "claim_disch_dt",
      "ptnt_acct_num",
      "mrn",
      "claim_id",
      "claim_adj_from_id",
      "claim_adj_to_id",
      "drg_id",
      "claim_src_ip_admit_cd",
      "claim_ip_type_admit_cd",
      "claim_bill_fac_type_cd",
      "claim_svc_cls_type_cd",
      "claim_freq_cd",
      "claim_proc_status_cd",
      "claim_type_cd",
      "ptnt_disch_status_cd",
      "claim_adj_dnl_cd",
      "claim_prm_payer_id",
      "claim_payee_type_cd",
      "claim_payee_id",
      "claim_pay_status_cd",
      "claim_payer_id",
      "day_supply",
      "rx_srv_ref_num",
      "daw_prod_slctn_cd",
      "fill_num",
      "rx_orgn_cd",
      "brnd_gnrc_cd",
      "rx_srv_type_cd",
      "ptnt_rsdnc_cd",
      "bill_npi",
      "bill_prov_ntwk_flag",
      "attnd_npi",
      "attnd_prov_ntwk_flag",
      "site_of_svc_npi",
      "site_of_svc_prov_ntwk_flag",
      "refer_npi",
      "refer_prov_ntwk_flag",
      "render_npi",
      "render_prov_ntwk_flag",
      "prscrb_npi",
      "prscrb_provider_ntwk_flag",
      "pcp_npi",
      "tot_amt_req",
      "tot_amt_eqv",
      "tot_amt_ptnt_pay",
      "tot_amt_prov_pay",
      "tot_amt_reimb",
      "tot_amt_pay",
      "tot_amt_dsalwd",
      "tot_amt_ded",
      "tot_amt_coin",
      "tot_amt_copay",
      "tot_amt_memb",
      "tot_amt_prm_pay",
      "tot_amt_dscnt"
  };

  private static final String[] claimLineHeaders = new String[] {
      "client_cd",
      "claim_uuid",
      "dos_from",
      "claim_line_n",
      "dos_to",
      "type_srv_cd",
      "pos_cd",
      "rev_cd",
      "max_alwd_unit",
      "ndc_cd",
      "cmpnd_cd",
      "qty_dspnsd",
      "qty_qual_cd",
      "line_benefit_pay_status",
      "line_pay_dnl_cd",
      "amt_dsalwd",
      "amt_reimb",
      "amt_ptnt_pay",
      "amt_rx",
      "amt_pay",
      "amt_prov_pay",
      "amt_ded",
      "amt_prm_pay",
      "amt_coin",
      "amt_req",
      "amt_eqv",
      "amt_memb",
      "amt_copay",
      "amt_dscnt",
      "diag_cd",
      "diag_desc",
      "poa_ind",
      "diag_vsn",
      "diag_type",
      "is_e_cd",
      "proc_cd",
      "proc_desc",
      "proc_dt",
      "proc_cd_type",
      "proc_type",
      "proc_mod1",
      "proc_mod2",
      "proc_mod3",
      "proc_mod4"
  };
}
