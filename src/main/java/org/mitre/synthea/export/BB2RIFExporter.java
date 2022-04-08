package org.mitre.synthea.export;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.export.BB2RIFStructure.BENEFICIARY;
import org.mitre.synthea.export.BB2RIFStructure.BENEFICIARY_HISTORY;
import org.mitre.synthea.export.BB2RIFStructure.CARRIER;
import org.mitre.synthea.export.BB2RIFStructure.DME;
import org.mitre.synthea.export.BB2RIFStructure.HHA;
import org.mitre.synthea.export.BB2RIFStructure.HOSPICE;
import org.mitre.synthea.export.BB2RIFStructure.INPATIENT;
import org.mitre.synthea.export.BB2RIFStructure.NPI;
import org.mitre.synthea.export.BB2RIFStructure.OUTPATIENT;
import org.mitre.synthea.export.BB2RIFStructure.PDE;
import org.mitre.synthea.export.BB2RIFStructure.SNF;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.agents.Provider.ProviderType;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Device;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Supply;
import org.mitre.synthea.world.geography.CMSStateCodeMapper;

/**
 * BlueButton 2 RIF format exporter. The data format is described here:
 * <a href="https://github.com/CMSgov/beneficiary-fhir-data/tree/master/apps/bfd-model">
 * https://github.com/CMSgov/beneficiary-fhir-data/tree/master/apps/bfd-model</a>.
 * Workflow:
 *    This exporter now takes advantage of an analyst-centered, config approach
 *      to setting exported data.
 *    Specifically,
 *    1. An analyst uses a spreadsheet to enter information from official documents
 *      (see syntax below)
 *    2. Spreadsheet is exported as a tab-separated values file (TSV)
 *    3. TSV file is copied to ./src/main/resources/export as bfd_field_values.tsv
 *        (or whatever is specified in synthea.properties)
 *    4. when this exporter runs, it will read in the config file
 *        to set the values needed for the export
 *    5. the resulting values are then written out to the export files
 *        in the order the export files require
 * Syntax for TSV file:
 *    empty cell   : Not yet analyzed, exporter prints a reminder
 *    N/A          : Not applicable, ignored by exporter
 *    (ccccc)      : General comments are enclosed in parenthesis, ignored by exporter
 *                 : If this is the only content, exporter prints a reminder
 *                 : If a value precedes the comment, exporter will use value,
 *                 : and ignore the comment (e.g., 0001 (always 0001 for inpatient) will
 *                 : result in just 0001)
 *    vvvvv        : Literal replacement value to be used by exporter (e.g., INSERT)
 *    a,b,c,d      : A "flat" distribution, exporter will randomly pick one
 *                 : of the comma delimited values. E.g., B,G (B = brand, G = generic)
 *                 : exporter randomly chooses either B or G and ignores comment
 *    Coded        : Implementation in exporter source code, ignored
 *    [Blank]      : Cell was analyzed and value should be empty
 */
public class BB2RIFExporter {

  private RifWriters rifWriters;

  private static AtomicLong beneId =
      new AtomicLong(Config.getAsLong("exporter.bfd.bene_id_start", -1));
  private static AtomicLong claimId =
      new AtomicLong(Config.getAsLong("exporter.bfd.clm_id_start", -1));
  private static AtomicInteger claimGroupId =
      new AtomicInteger(Config.getAsInteger("exporter.bfd.clm_grp_id_start", -1));
  private static AtomicLong pdeId =
      new AtomicLong(Config.getAsLong("exporter.bfd.pde_id_start", -1));
  private static AtomicLong fiDocCntlNum =
      new AtomicLong(Config.getAsLong("exporter.bfd.fi_doc_cntl_num_start", -1));
  private static AtomicLong carrClmCntlNum =
      new AtomicLong(Config.getAsLong("exporter.bfd.carr_clm_cntl_num_start", -1));
  private static AtomicReference<MBI> mbi =
      new AtomicReference<>(MBI.parse(Config.get("exporter.bfd.mbi_start", "1S00-A00-AA00")));
  private static AtomicReference<HICN> hicn =
      new AtomicReference<>(HICN.parse(Config.get("exporter.bfd.hicn_start", "T00000000A")));
  private final CLIA[] cliaLabNumbers;
  private final long claimCutoff;

  private List<LinkedHashMap<String, String>> carrierLookup;
  CodeMapper conditionCodeMapper;
  CodeMapper medicationCodeMapper;
  CodeMapper drgCodeMapper;
  CodeMapper dmeCodeMapper;
  CodeMapper hcpcsCodeMapper;
  CodeMapper betosCodeMapper;
  private Map<String, RandomCollection<String>> externalCodes;

  private CMSStateCodeMapper locationMapper;
  private StaticFieldConfig staticFieldConfig;

  private static final String BB2_BENE_ID = "BB2_BENE_ID";
  private static final String BB2_PARTD_CONTRACTS = "BB2_PARTD_CONTRACTS";
  private static final String BB2_HIC_ID = "BB2_HIC_ID";
  private static final String BB2_MBI = "BB2_MBI";

  /**
   * Day-Month-Year date format. Note that SimpleDateFormat is not thread safe so we need one
   * per generator thread.
   */
  private static final ThreadLocal<SimpleDateFormat> BB2_DATE_FORMAT = new ThreadLocal<>();

  /**
   * Get a date string in the format DD-MMM-YY from the given time stamp.
   */
  private static String bb2DateFromTimestamp(long time) {
    SimpleDateFormat dateFormat = BB2_DATE_FORMAT.get();
    if (dateFormat == null) {
      dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
      BB2_DATE_FORMAT.set(dateFormat);
    }
    return dateFormat.format(new Date(time));
  }

  /**
   * Create the output folder and files. Write headers to each file.
   */
  private BB2RIFExporter() {
    cliaLabNumbers = initCliaLabNumbers();
    conditionCodeMapper = new CodeMapper("export/condition_code_map.json");
    medicationCodeMapper = new CodeMapper("export/medication_code_map.json");
    drgCodeMapper = new CodeMapper("export/drg_code_map.json");
    dmeCodeMapper = new CodeMapper("export/dme_code_map.json");
    hcpcsCodeMapper = new CodeMapper("export/hcpcs_code_map.json");
    betosCodeMapper = new CodeMapper("export/betos_code_map.json");
    locationMapper = new CMSStateCodeMapper();
    externalCodes = loadExternalCodes();
    try {
      String csv = Utilities.readResourceAndStripBOM("payers/carriers.csv");
      carrierLookup = SimpleCSV.parse(csv);
      staticFieldConfig = new StaticFieldConfig();
      prepareOutputFiles();
      for (String tsvIssue: staticFieldConfig.validateTSV()) {
        System.out.println(tsvIssue);
      }
      SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
      format.setTimeZone(TimeZone.getTimeZone("UTC"));
      claimCutoff = format.parse(Config.get("exporter.bfd.cutoff_date", "20140529")).getTime();
    } catch (IOException | ParseException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  private Map<String, RandomCollection<String>> loadExternalCodes() {
    Map<String, RandomCollection<String>> data = new HashMap<String, RandomCollection<String>>();
    try {
      String fileData = Utilities.readResourceAndStripBOM("export/external_codes.csv");
      List<LinkedHashMap<String, String>> csv = SimpleCSV.parse(fileData);
      for (LinkedHashMap<String, String> row : csv) {
        String primary = row.get("primary");
        String external = row.get("external");
        double count = Double.parseDouble(row.get("count"));
        if (!data.containsKey(primary)) {
          data.put(primary, new RandomCollection<String>());
        }
        data.get(primary).add(count, external);
      }
    } catch (Exception e) {
      if (Config.getAsBoolean("exporter.bfd.require_code_maps", true)) {
        throw new MissingResourceException(
            "Unable to read external code file 'external_codes.csv'",
            "BB2RIFExporter", "external_codes.csv");
      } else {
        // For testing, the external codes are not present.
        System.out.println("BB2RIFExporter is running without 'external_codes.csv'");
      }
      return null;
    }
    return data;
  }

  private <E extends Enum<E>> void setExternalCode(Person person,
      Map<E,String> fieldValues, E diagnosisCodeKey,
      E externalCodeKey, E externalVersionKey,
      E externalPOACodeKey, List<String> presentOnAdmission) {
    // set the external code...
    boolean set = setExternalCode(person, fieldValues, diagnosisCodeKey,
        externalCodeKey, externalVersionKey);
    // ... and also set the 'present on admission' flag...
    if (set && externalPOACodeKey != null && presentOnAdmission != null) {
      String primary = fieldValues.get(diagnosisCodeKey);
      if (primary != null) {
        String present = presentOnAdmission.contains(primary) ? "Y" : "U";
        fieldValues.put(externalPOACodeKey, present);
      }
    }
  }

  private <E extends Enum<E>> boolean setExternalCode(Person person,
      Map<E,String> fieldValues, E diagnosisCodeKey,
      E externalCodeKey, E externalVersionKey) {
    String primary = fieldValues.get(diagnosisCodeKey);
    if (primary != null) {
      String prefix = primary.substring(0, 3);
      if (externalCodes != null && externalCodes.containsKey(prefix)) {
        String externalCode = externalCodes.get(prefix).next(person);
        fieldValues.put(externalCodeKey, externalCode);
        fieldValues.put(externalVersionKey, "0");
        return true;
      }
    }
    return false;
  }

  private static CLIA[] initCliaLabNumbers() {
    int numLabs = Config.getAsInteger("exporter.bfd.clia_labs_count",1);
    CLIA[] labNumbers = new CLIA[numLabs];
    CLIA labNumber = CLIA.parse(Config.get("exporter.bfd.clia_labs_start", "00A0000000"));
    for (int i = 0; i < numLabs; i++) {
      labNumbers[i] = labNumber;
      labNumber = labNumber.next();
    }
    return labNumbers;
  }

  /**
   * Create the output folder. Files will be added as needed.
   */
  final void prepareOutputFiles() throws IOException {
    // Initialize output writers
    File output = Exporter.getOutputFolder("bfd", null);
    output.mkdirs();
    Path outputDirectory = output.toPath();

    rifWriters = new RifWriters(outputDirectory);
  }

  /**
   * Export a manifest file that lists all of the other BFD files (except the NPI file
   * which is special).
   * @throws IOException if something goes wrong
   */
  public void exportManifest() throws IOException {
    File output = Exporter.getOutputFolder("bfd", null);
    output.mkdirs();
    StringWriter manifest = new StringWriter();
    manifest.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
    manifest.write("<dataSetManifest xmlns=\"http://cms.hhs.gov/bluebutton/api/schema/ccw-rif/v9\"");
    manifest.write(String.format(" timestamp=\"%s\" ",
             java.time.Instant.now()
                     .atZone(java.time.ZoneId.of("Z"))
                     .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                     .toString()));
    manifest.write("sequenceId=\"0\">\n");
    for (Class<?> rifFile: BB2RIFStructure.RIF_FILES) {
      for (RifEntryStatus status: RifEntryStatus.values()) {
        // generics and arrays are weird so need to cast below rather than declare on the array
        Class<? extends Enum> rifEnum = (Class<? extends Enum>) rifFile;
        SynchronizedBBLineWriter writer = rifWriters.getWriter(rifEnum, status);
        if (writer != null) {
          manifest.write(String.format("  <entry name=\"%s\" type=\"%s\"/>\n",
                  writer.getFile().getName(), rifEnum.getSimpleName()));
        }
      }
    }
    manifest.write("</dataSetManifest>");
    Path manifestPath = output.toPath().resolve("manifest.xml");
    Exporter.overwriteFile(manifestPath, manifest.toString());
  }

  /**
   * Export NPI writer with synthetic providers.
   * @throws IOException if something goes horribly wrong.
   */
  public void exportNPIs() throws IOException {
    HashMap<NPI, String> fieldValues = new HashMap<>();
    SynchronizedBBLineWriter rifWriter = rifWriters.getOrCreateWriter(NPI.class,
            RifEntryStatus.INITIAL, "tsv", "\t");

    for (Provider h : Provider.getProviderList()) {

      // filter - exports only those organizations in use
      Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
      int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
          .mapToInt(ai -> ai.get()).sum();

      if (totalEncounters > 0) {
        // export organization
        fieldValues.clear();
        fieldValues.put(NPI.NPI, h.npi);
        fieldValues.put(NPI.ENTITY_TYPE_CODE, "2");
        fieldValues.put(NPI.EIN, "<UNAVAIL>");
        fieldValues.put(NPI.ORG_NAME, h.name);
        rifWriter.writeValues(fieldValues);

        Map<String, ArrayList<Clinician>> clinicians = h.clinicianMap;
        for (String specialty : clinicians.keySet()) {
          ArrayList<Clinician> docs = clinicians.get(specialty);
          for (Clinician doc : docs) {
            if (doc.getEncounterCount() > 0) {
              // export each doc
              Map<String,Object> attributes = doc.getAttributes();
              fieldValues.clear();
              fieldValues.put(NPI.NPI, doc.npi);
              fieldValues.put(NPI.ENTITY_TYPE_CODE, "1");
              fieldValues.put(NPI.LAST_NAME,
                  attributes.get(Clinician.LAST_NAME).toString());
              fieldValues.put(NPI.FIRST_NAME,
                  attributes.get(Clinician.FIRST_NAME).toString());
              fieldValues.put(NPI.PREFIX,
                  attributes.get(Clinician.NAME_PREFIX).toString());
              fieldValues.put(NPI.CREDENTIALS, "M.D.");
              rifWriter.writeValues(fieldValues);
            }
          }
        }
      }
    }
  }

  /**
   * Export the current values of IDs so subsequent runs can use them as a starting point.
   * @throws IOException if something goes wrong
   */
  public void exportEndState() throws IOException {
    Properties endState = new Properties();
    endState.setProperty("exporter.bfd.bene_id_start", beneId.toString());
    endState.setProperty("exporter.bfd.clm_id_start", claimId.toString());
    endState.setProperty("exporter.bfd.clm_grp_id_start", claimGroupId.toString());
    endState.setProperty("exporter.bfd.pde_id_start", pdeId.toString());
    endState.setProperty("exporter.bfd.mbi_start", mbi.toString());
    endState.setProperty("exporter.bfd.hicn_start", hicn.toString());
    endState.setProperty("exporter.bfd.fi_doc_cntl_num_start", fiDocCntlNum.toString());
    endState.setProperty("exporter.bfd.carr_clm_cntl_num_start", carrClmCntlNum.toString());
    File outputDir = Exporter.getOutputFolder("bfd", null);
    FileOutputStream f = new FileOutputStream(new File(outputDir, "end_state.properties"));
    endState.store(f, "BFD Properties End State");
    f.close();
  }

  /**
   * Export a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @param yearsOfHistory number of years of claims to export
   * @throws IOException if something goes wrong
   */
  public void export(Person person, long stopTime, int yearsOfHistory) throws IOException {
    exportBeneficiary(person, stopTime);
    exportBeneficiaryHistory(person, stopTime);
    long startTime = stopTime - Utilities.convertTime("years", yearsOfHistory);
    exportInpatient(person, startTime, stopTime);
    exportOutpatient(person, startTime, stopTime);
    exportCarrier(person, startTime, stopTime);
    exportPrescription(person, startTime, stopTime);
    exportDME(person, startTime, stopTime);
    exportHome(person, startTime, stopTime);
    exportHospice(person, startTime, stopTime);
    exportSNF(person, startTime, stopTime);
  }

  /**
   * Export a beneficiary details for single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportBeneficiary(Person person,
        long stopTime) throws IOException {
    String beneIdStr = Long.toString(BB2RIFExporter.beneId.getAndDecrement());
    person.attributes.put(BB2_BENE_ID, beneIdStr);
    String hicId = BB2RIFExporter.hicn.getAndUpdate((v) -> v.next()).toString();
    person.attributes.put(BB2_HIC_ID, hicId);
    String mbiStr = BB2RIFExporter.mbi.getAndUpdate((v) -> v.next()).toString();
    person.attributes.put(BB2_MBI, mbiStr);
    int yearsOfHistory = Config.getAsInteger("exporter.years_of_history");
    int endYear = Utilities.getYear(stopTime);
    int endMonth = Utilities.getMonth(stopTime);
    long deathDate = -1;
    if (person.attributes.get(Person.DEATHDATE) != null) {
      deathDate = (long)person.attributes.get(Person.DEATHDATE);
      if (deathDate > stopTime) {
        deathDate = -1; // Ignore future death date that may have been set by a module
      } else {
        endYear = Utilities.getYear(deathDate);
        endMonth = Utilities.getMonth(deathDate);
      }
    }

    PartCContractHistory partCContracts = new PartCContractHistory(person,
            deathDate == -1 ? stopTime : deathDate, yearsOfHistory);
    PartDContractHistory partDContracts = new PartDContractHistory(person,
            deathDate == -1 ? stopTime : deathDate, yearsOfHistory);
    person.attributes.put(BB2_PARTD_CONTRACTS, partDContracts); // used in exportPrescription

    RifEntryStatus entryStatus = RifEntryStatus.INITIAL;
    String initialBeneEntitlementReason = null;
    synchronized (rifWriters.getOrCreateWriter(BENEFICIARY.class, entryStatus)) {
      for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
        HashMap<BENEFICIARY, String> fieldValues = new HashMap<>();
        staticFieldConfig.setValues(fieldValues, BENEFICIARY.class, person);
        if (entryStatus != RifEntryStatus.INITIAL) {
          // The first year output is set via staticFieldConfig to "INSERT", subsequent years
          // need to be "UPDATE"
          fieldValues.put(BENEFICIARY.DML_IND, "UPDATE");
        }

        fieldValues.put(BENEFICIARY.RFRNC_YR, String.valueOf(year));
        int monthCount = year == endYear ? endMonth : 12;
        String monthCountStr = String.valueOf(monthCount);
        fieldValues.put(BENEFICIARY.A_MO_CNT, monthCountStr);
        fieldValues.put(BENEFICIARY.B_MO_CNT, monthCountStr);
        fieldValues.put(BENEFICIARY.BUYIN_MO_CNT, monthCountStr);
        int partDMonthsCovered = partDContracts.getCoveredMonthsCount(year);
        fieldValues.put(BENEFICIARY.PLAN_CVRG_MO_CNT, String.valueOf(partDMonthsCovered));
        fieldValues.put(BENEFICIARY.BENE_ID, beneIdStr);
        fieldValues.put(BENEFICIARY.BENE_CRNT_HIC_NUM, hicId);
        fieldValues.put(BENEFICIARY.MBI_NUM, mbiStr);
        fieldValues.put(BENEFICIARY.BENE_SEX_IDENT_CD,
                getBB2SexCode((String)person.attributes.get(Person.GENDER)));
        String zipCode = (String)person.attributes.get(Person.ZIP);
        fieldValues.put(BENEFICIARY.BENE_ZIP_CD, zipCode);
        fieldValues.put(BENEFICIARY.BENE_COUNTY_CD,
                locationMapper.zipToCountyCode(zipCode));
        for (int i = 0; i < monthCount; i++) {
          fieldValues.put(BB2RIFStructure.beneficiaryFipsStateCntyFields[i],
              locationMapper.zipToFipsCountyCode(zipCode));
        }
        fieldValues.put(BENEFICIARY.STATE_CODE,
                locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
        String raceCode = bb2RaceCode(
                (String)person.attributes.get(Person.ETHNICITY),
                (String)person.attributes.get(Person.RACE));
        fieldValues.put(BENEFICIARY.BENE_RACE_CD, raceCode);
        fieldValues.put(BENEFICIARY.RTI_RACE_CD, raceCode); // TODO: implement RTI alogorithm
        fieldValues.put(BENEFICIARY.BENE_SRNM_NAME,
                (String)person.attributes.get(Person.LAST_NAME));
        String givenName = (String)person.attributes.get(Person.FIRST_NAME);
        fieldValues.put(BENEFICIARY.BENE_GVN_NAME, StringUtils.truncate(givenName, 15));
        long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
        fieldValues.put(BENEFICIARY.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
        fieldValues.put(BENEFICIARY.AGE, String.valueOf(ageAtEndOfYear(birthdate, year)));
        fieldValues.put(BENEFICIARY.BENE_PTA_TRMNTN_CD, "0");
        fieldValues.put(BENEFICIARY.BENE_PTB_TRMNTN_CD, "0");
        if (deathDate != -1) {
          // only add death date for years when it was (presumably) known. E.g. If we are outputting
          // record for 2005 and patient died in 2007 we don't include the death date.
          if (Utilities.getYear(deathDate) <= year) {
            fieldValues.put(BENEFICIARY.DEATH_DT, bb2DateFromTimestamp(deathDate));
            fieldValues.put(BENEFICIARY.BENE_PTA_TRMNTN_CD, "1");
            fieldValues.put(BENEFICIARY.BENE_PTB_TRMNTN_CD, "1");
          }
        }
        boolean medicareAgeThisYear = ageAtEndOfYear(birthdate, year) >= 65;
        boolean esrdThisYear = hasESRD(person, year);
        fieldValues.put(BENEFICIARY.BENE_ESRD_IND, esrdThisYear ? "Y" : "0");
        // "0" = old age, "2" = ESRD
        if (medicareAgeThisYear) {
          fieldValues.put(BENEFICIARY.BENE_ENTLMT_RSN_CURR, "0");
        } else if (esrdThisYear) {
          fieldValues.put(BENEFICIARY.BENE_ENTLMT_RSN_CURR, "2");
        }
        if (initialBeneEntitlementReason == null) {
          initialBeneEntitlementReason = fieldValues.get(BENEFICIARY.BENE_ENTLMT_RSN_CURR);
        }
        if (initialBeneEntitlementReason != null) {
          fieldValues.put(BENEFICIARY.BENE_ENTLMT_RSN_ORIG, initialBeneEntitlementReason);
        }

        for (PartCContractHistory.ContractPeriod period:
                partCContracts.getContractPeriods(year)) {
          PartCContractID partCContractID = period.getContractID();
          if (partCContractID != null) {
            String partCContractIDStr = partCContractID.toString();
            String partCPBPIDStr = period.getPlanBenefitPackageID().toString();
            List<Integer> coveredMonths = period.getCoveredMonths(year);
            for (int i: coveredMonths) {
              fieldValues.put(BB2RIFStructure.beneficiaryPartCContractFields[i - 1],
                      partCContractIDStr);
              fieldValues.put(BB2RIFStructure.beneficiaryPartCPBPFields[i - 1],
                      partCPBPIDStr);
            }
          }
        }

        // TODO: make claim copay match the designated cost sharing code
        String partDCostSharingCode = getPartDCostSharingCode(person);
        int rdsMonthCount = 0;
        for (PartDContractHistory.ContractPeriod period:
                partDContracts.getContractPeriods(year)) {
          PartDContractID partDContractID = period.getContractID();
          String partDDrugSubsidyIndicator =
                  partDContracts.getEmployeePDPIndicator(partDContractID);
          if (partDContractID != null) {
            String partDContractIDStr = partDContractID.toString();
            String partDPBPIDStr = period.getPlanBenefitPackageID().toString();
            List<Integer> coveredMonths = period.getCoveredMonths(year);
            if (partDDrugSubsidyIndicator != null && partDDrugSubsidyIndicator.equals("Y")) {
              rdsMonthCount += coveredMonths.size();
            }
            for (int i: coveredMonths) {
              fieldValues.put(BB2RIFStructure.beneficiaryPartDContractFields[i - 1],
                      partDContractIDStr);
              fieldValues.put(BB2RIFStructure.beneficiaryPartDPBPFields[i - 1],
                      partDPBPIDStr);
              fieldValues.put(BB2RIFStructure.beneficiaryPartDSegmentFields[i - 1], "000");
              fieldValues.put(BB2RIFStructure.beneficiaryPartDCostSharingFields[i - 1],
                      partDCostSharingCode);
              if (partDDrugSubsidyIndicator != null) {
                fieldValues.put(BB2RIFStructure.benficiaryPartDRetireeDrugSubsidyFields[i - 1],
                        partDDrugSubsidyIndicator);
              }
            }
          } else {
            for (int i: period.getCoveredMonths(year)) {
              // Not enrolled this month
              fieldValues.put(BB2RIFStructure.beneficiaryPartDCostSharingFields[i - 1], "00");
            }
          }
        }
        fieldValues.put(BENEFICIARY.RDS_MO_CNT, Integer.toString(rdsMonthCount));

        String dualEligibleStatusCode = getDualEligibilityCode(person, year);
        String medicareStatusCode = getMedicareStatusCode(medicareAgeThisYear, esrdThisYear,
                isBlind(person));
        String buyInIndicator = getEntitlementBuyIn(dualEligibleStatusCode, medicareStatusCode);
        for (int month = 0; month < monthCount; month++) {
          fieldValues.put(BB2RIFStructure.beneficiaryDualEligibleStatusFields[month],
                  dualEligibleStatusCode);
          fieldValues.put(BB2RIFStructure.beneficiaryMedicareStatusFields[month],
                  medicareStatusCode);
          fieldValues.put(BB2RIFStructure.beneficiaryMedicareEntitlementFields[month],
                  buyInIndicator);
        }
        rifWriters.writeValues(BENEFICIARY.class, fieldValues, entryStatus);
        if (year == (endYear - 1)) {
          entryStatus = RifEntryStatus.FINAL;
        } else {
          entryStatus = RifEntryStatus.INTERIM;
        }
      }
    }
  }

  private static String getEntitlementBuyIn(String dualEligibleStatusCode,
          String medicareStatusCode) {
    if (medicareStatusCode.equals("00")) {
      return "0"; // not enrolled
    } else {
      if (dualEligibleStatusCode.equals("NA")) {
        // not dual eligible
        return "3"; // Part A and Part B
      } else {
        // dual eligible
        return "C"; // Part A and Part B state buy-in
      }
    }

  }

  private static String getMedicareStatusCode(boolean medicareAge, boolean esrd, boolean blind) {
    if (medicareAge) {
      if (esrd) {
        return "11";
      } else {
        return "10";
      }
    } else if (blind) {
      if (esrd) {
        return "21";
      } else {
        return "20";
      }
    } else if (esrd) {
      return "31";
    } else {
      return "00"; // Not enrolled
    }
  }

  private static String getPartDCostSharingCode(Person person) {
    double incomeLevel = Double.parseDouble(
            person.attributes.get(Person.INCOME_LEVEL).toString());
    if (incomeLevel >= 1.0) {
      // Beneficiary enrolled in Parts A and/or B, and Part D; no premium or cost sharing subsidy
      return "09";
    } else if (incomeLevel >= 0.6) {
      // Beneficiary enrolled in Parts A and/or B, and Part D; deemed eligible for LIS with 100%
      // premium subsidy and high copayment
      return "03";
    } else if (incomeLevel >= 0.3) {
      // Beneficiary enrolled in Parts A and/or B, and Part D; deemed eligible for LIS with 100%
      // premium subsidy and low copayment
      return "02";
    }
    // Beneficiary enrolled in Parts A and/or B, and Part D; deemed eligible for LIS with 100%
    // premium subsidy and no copayment
    return "01";
  }

  // Income level < 0.3
  private static final RandomCollection<String> incomeBandOneDualCodes = new RandomCollection<>();
  // 0.3 <= Income level < 0.6
  private static final RandomCollection<String> incomeBandTwoDualCodes = new RandomCollection<>();
  // 0.6 <= Income level < 1.0
  private static final RandomCollection<String> incomeBandThreeDualCodes = new RandomCollection<>();

  static {
    // Specified Low-Income Medicare Beneficiary (SLMB)-only
    incomeBandOneDualCodes.add(1.3, "03");
    // SLMB and full Medicaid coverage, including prescription drugs
    incomeBandOneDualCodes.add(0.5, "04");
    // Other dual eligible, but without Medicaid coverage
    incomeBandOneDualCodes.add(0.007, "09");
    // Unknown
    incomeBandOneDualCodes.add(0.05, "99");
    // Not in code book but present in database
    incomeBandOneDualCodes.add(0.04, "AA");
    // Not in code book but present in database
    incomeBandOneDualCodes.add(0.1, "");

    // QMB and full Medicaid coverage, including prescription drugs
    incomeBandTwoDualCodes.add(8.2, "02");
    // Other dual eligible, but without Medicaid coverage
    incomeBandTwoDualCodes.add(0.007, "09");
    // Unknown
    incomeBandTwoDualCodes.add(0.05, "99");
    // Not in code book but present in database
    incomeBandTwoDualCodes.add(0.04, "AA");
    // Not in code book but present in database
    incomeBandTwoDualCodes.add(0.1, "");

    // Qualified Medicare Beneficiary (QMB)-only
    incomeBandThreeDualCodes.add(2.2, "01");
    // Qualifying individuals (QI)
    incomeBandThreeDualCodes.add(0.8, "06");
    // Other dual eligible (not QMB, SLMB, QWDI, or QI) with full Medicaid coverage, including
    // prescription Drugs
    incomeBandThreeDualCodes.add(2.9, "08");
    // Other dual eligible, but without Medicaid coverage
    incomeBandThreeDualCodes.add(0.007, "09");
    // Unknown
    incomeBandThreeDualCodes.add(0.05, "99");
    // Not in code book but present in database
    incomeBandThreeDualCodes.add(0.04, "AA");
    // Not in code book but present in database
    incomeBandThreeDualCodes.add(0.1, "");
  }

  private String getDualEligibilityCode(Person person, int year) {
    // TBD add support for the following additional code (%-age in brackets is observed
    // frequency in CMS data):
    // 00 (15.6%) - Not enrolled in Medicare for the month
    String partDCostSharingCode = getPartDCostSharingCode(person);
    if (partDCostSharingCode.equals("03")) {
      return incomeBandThreeDualCodes.next(person);
    } else if (partDCostSharingCode.equals("02")) {
      return incomeBandTwoDualCodes.next(person);
    } else if (partDCostSharingCode.equals("01")) {
      return incomeBandOneDualCodes.next(person);
    } else if (hasESRD(person, year) || isBlind(person)) {
      return "05"; // (0.001%) Qualified Disabled Working Individual (QDWI)
    } else {
      return "NA"; // (68.3%) Non-Medicaid
    }
  }

  private String getBB2SexCode(String sex) {
    switch (sex) {
      case "M":
        return "1";
      case "F":
        return "2";
      default:
        return "0";
    }
  }

  private static final Comparator<Entry> ENTRY_SORTER = new Comparator<Entry>() {
    @Override
    public int compare(Entry o1, Entry o2) {
      return (int)(o2.start - o1.start);
    }
  };

  /**
   * This returns the list of active diagnoses, sorted by most recent first
   * and oldest last.
   * @param person patient with the diagnoses.
   * @return the list of active diagnoses, sorted by most recent first and oldest last.
   */
  private List<String> getDiagnosesCodes(Person person, long time) {
    // Collect the active diagnoses at the given time,
    // keeping only those diagnoses that are mappable.
    List<Entry> diagnoses = new ArrayList<Entry>();
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.start <= time) {
        for (Entry dx : encounter.conditions) {
          if (dx.start <= time && (dx.stop == 0L || dx.stop > time)) {
            if (conditionCodeMapper.canMap(dx.codes.get(0).code)) {
              String mapped = conditionCodeMapper.map(dx.codes.get(0).code, person, true);
              // Temporarily add the mapped code... we'll remove it later.
              Code mappedCode = new Code("ICD10", mapped, dx.codes.get(0).display);
              dx.codes.add(mappedCode);
              diagnoses.add(dx);
            }
          }
        }
      }
    }
    // Sort them by date and then return only the mapped codes (not the entire Entry).
    diagnoses.sort(ENTRY_SORTER);
    List<String> mappedDiagnosisCodes = new ArrayList<String>();
    for (Entry dx : diagnoses) {
      mappedDiagnosisCodes.add(dx.codes.remove(dx.codes.size() - 1).code);
    }
    return mappedDiagnosisCodes;
  }

  /**
   * Determines whether the person has end stage renal disease at the end of the supplied year.
   * @param person the person
   * @param year the year
   * @return true if has ESRD, false otherwise
   */
  private boolean hasESRD(Person person, int year) {
    long timestamp = Utilities.convertCalendarYearsToTime(year + 1); // +1 for end of year
    List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, timestamp);
    return mappedDiagnosisCodes.contains("N18.6");
  }

  private boolean isBlind(Person person) {
    return person.attributes.containsKey(Person.BLINDNESS)
            && person.attributes.get(Person.BLINDNESS).equals(true);
  }

  /**
   * Export a beneficiary history for single person. Assumes exportBeneficiary
   * was called first to set up various ID on person
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportBeneficiaryHistory(Person person,
        long stopTime) throws IOException {
    HashMap<BENEFICIARY_HISTORY, String> fieldValues = new HashMap<>();

    staticFieldConfig.setValues(fieldValues, BENEFICIARY_HISTORY.class, person);

    String beneIdStr = (String)person.attributes.get(BB2_BENE_ID);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_ID, beneIdStr);
    String hicId = (String)person.attributes.get(BB2_HIC_ID);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_CRNT_HIC_NUM, hicId);
    String mbiStr = (String)person.attributes.get(BB2_MBI);
    fieldValues.put(BENEFICIARY_HISTORY.MBI_NUM, mbiStr);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_SEX_IDENT_CD,
            getBB2SexCode((String)person.attributes.get(Person.GENDER)));
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
    String zipCode = (String)person.attributes.get(Person.ZIP);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_COUNTY_CD,
            locationMapper.zipToCountyCode(zipCode));
    fieldValues.put(BENEFICIARY_HISTORY.STATE_CODE,
            locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
    fieldValues.put(BENEFICIARY_HISTORY.BENE_ZIP_CD,
            (String)person.attributes.get(Person.ZIP));
    fieldValues.put(BENEFICIARY_HISTORY.BENE_RACE_CD,
            bb2RaceCode(
                    (String)person.attributes.get(Person.ETHNICITY),
                    (String)person.attributes.get(Person.RACE)));
    fieldValues.put(BENEFICIARY_HISTORY.BENE_SRNM_NAME,
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BENEFICIARY_HISTORY.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
    String terminationCode = "0";
    if (person.attributes.get(Person.DEATHDATE) != null) {
      long deathDate = (long)person.attributes.get(Person.DEATHDATE);
      if (deathDate <= stopTime) {
        terminationCode = "1"; // Ignore future death date that may have been set by a module
      }
    }
    fieldValues.put(BENEFICIARY_HISTORY.BENE_PTA_TRMNTN_CD, terminationCode);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_PTB_TRMNTN_CD, terminationCode);
    int year = Utilities.getYear(stopTime);
    boolean medicareAge = ageAtEndOfYear(birthdate, year) >= 65;
    boolean esrd = hasESRD(person, year);
    fieldValues.put(BENEFICIARY_HISTORY.BENE_ESRD_IND, esrd ? "Y" : "0");
    // "0" = old age, "2" = ESRD
    if (medicareAge) {
      fieldValues.put(BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "0");
    } else if (esrd) {
      fieldValues.put(BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR, "2");
    }
    String initialBeneEntitlementReason = fieldValues.get(BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_CURR);
    if (initialBeneEntitlementReason != null) {
      fieldValues.put(BENEFICIARY_HISTORY.BENE_ENTLMT_RSN_ORIG, initialBeneEntitlementReason);
    }
    rifWriters.writeValues(BENEFICIARY_HISTORY.class, fieldValues);
  }

  /**
   * Calculate the age of a person at the end of the given year.
   * @param birthdate a person's birthdate specified as number of milliseconds since the epoch
   * @param year the year
   * @return the person's age
   */
  private static int ageAtEndOfYear(long birthdate, int year) {
    return year - Utilities.getYear(birthdate);
  }

  /**
   * Export outpatient claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportOutpatient(Person person, long startTime, long stopTime)
        throws IOException {
    HashMap<OUTPATIENT, String> fieldValues = new HashMap<>();

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }
      boolean isVirtual = encounter.type.equals(EncounterType.VIRTUAL.toString());
      boolean isVirtualOutpatient = isVirtual &&
          (ProviderType.HOSPITAL == encounter.provider.type);
      boolean isAmbulatory = encounter.type.equals(EncounterType.AMBULATORY.toString());
      boolean isOutpatient = encounter.type.equals(EncounterType.OUTPATIENT.toString());
      boolean isUrgent = encounter.type.equals(EncounterType.URGENTCARE.toString());
      boolean isPrimary = (ProviderType.PRIMARY == encounter.provider.type);
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);

      if (isVA || isIHSCenter || isPrimary || isUrgent) {
        continue;
      }
      if (!isAmbulatory && !isOutpatient && !isVirtualOutpatient) {
        continue;
      }

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long fiDocId = BB2RIFExporter.fiDocCntlNum.getAndDecrement();

      staticFieldConfig.setValues(fieldValues, OUTPATIENT.class, person);

      // The REQUIRED fields
      fieldValues.put(OUTPATIENT.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(OUTPATIENT.CLM_ID, "" + claimId);
      fieldValues.put(OUTPATIENT.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(OUTPATIENT.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(OUTPATIENT.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(OUTPATIENT.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(OUTPATIENT.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(OUTPATIENT.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(OUTPATIENT.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(OUTPATIENT.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(OUTPATIENT.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(OUTPATIENT.OP_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(OUTPATIENT.CLM_PMT_AMT, String.format("%.2f",
              encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer(HealthInsuranceModule.MEDICARE)) {
        fieldValues.put(OUTPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(OUTPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(OUTPATIENT.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      if (encounter.ended) {
        field = "1";
      } else {
        field = "30"; // the patient is still here
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
      }
      fieldValues.put(OUTPATIENT.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(OUTPATIENT.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OUTPATIENT.CLM_OP_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OUTPATIENT.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(OUTPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(OUTPATIENT.NCH_BENE_PTB_DDCTBL_AMT,
              String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(OUTPATIENT.REV_CNTR_CASH_DDCTBL_AMT,
              String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(OUTPATIENT.REV_CNTR_COINSRNC_WGE_ADJSTD_C,
              String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(OUTPATIENT.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OUTPATIENT.REV_CNTR_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OUTPATIENT.REV_CNTR_PTNT_RSPNSBLTY_PMT,
              String.format("%.2f",
                      encounter.claim.getDeductiblePaid() + encounter.claim.getCoinsurancePaid()));
      fieldValues.put(OUTPATIENT.REV_CNTR_RDCD_COINSRNC_AMT,
              String.format("%.2f", encounter.claim.getCoinsurancePaid()));

      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          icdReasonCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(OUTPATIENT.PRNCPAL_DGNS_CD, icdReasonCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(),
                BB2RIFStructure.outpatientDxFields.length);
        for (int i = 0; i < smallest; i++) {
          OUTPATIENT[] dxField = BB2RIFStructure.outpatientDxFields[i];
          fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
          fieldValues.put(dxField[1], "0"); // 0=ICD10
        }
        if (!fieldValues.containsKey(OUTPATIENT.PRNCPAL_DGNS_CD)) {
          fieldValues.put(OUTPATIENT.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
      }

      // Check for external code...
      setExternalCode(person, fieldValues,
          OUTPATIENT.PRNCPAL_DGNS_CD, OUTPATIENT.ICD_DGNS_E_CD1, OUTPATIENT.ICD_DGNS_E_VRSN_CD1);
      setExternalCode(person, fieldValues,
          OUTPATIENT.PRNCPAL_DGNS_CD, OUTPATIENT.FST_DGNS_E_CD, OUTPATIENT.FST_DGNS_E_VRSN_CD);

      // Use the procedures in this encounter to enter mapped values
      boolean noProcedures = false;
      if (!encounter.procedures.isEmpty()) {
        List<HealthRecord.Procedure> mappableProcedures = new ArrayList<>();
        List<String> mappedProcedureCodes = new ArrayList<>();
        for (HealthRecord.Procedure procedure : encounter.procedures) {
          for (HealthRecord.Code code : procedure.codes) {
            if (conditionCodeMapper.canMap(code.code)) {
              mappableProcedures.add(procedure);
              mappedProcedureCodes.add(conditionCodeMapper.map(code.code, person, true));
              break; // take the first mappable code for each procedure
            }
          }
        }
        if (!mappableProcedures.isEmpty()) {
          int smallest = Math.min(mappableProcedures.size(),
                  BB2RIFStructure.outpatientPxFields.length);
          for (int i = 0; i < smallest; i++) {
            OUTPATIENT[] pxField = BB2RIFStructure.outpatientPxFields[i];
            fieldValues.put(pxField[0], mappedProcedureCodes.get(i));
            fieldValues.put(pxField[1], "0"); // 0=ICD10
            fieldValues.put(pxField[2], bb2DateFromTimestamp(mappableProcedures.get(i).start));
          }
        } else {
          noProcedures = true;
        }
      }
      if (icdReasonCode == null && noDiagnoses && noProcedures) {
        continue; // skip this encounter
      }
      String revCenter = fieldValues.get(OUTPATIENT.REV_CNTR);
      if (isVirtual) {
        revCenter = person.randBoolean() ? "0780" : "0789";
        fieldValues.put(OUTPATIENT.REV_CNTR, revCenter);
      }

      synchronized (rifWriters.getOrCreateWriter(OUTPATIENT.class)) {
        int claimLine = 1;
        for (ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (hcpcsCodeMapper.canMap(code.code)) {
                hcpcsCode = hcpcsCodeMapper.map(code.code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
            fieldValues.put(OUTPATIENT.REV_CNTR, revCenter);
            fieldValues.remove(OUTPATIENT.REV_CNTR_IDE_NDC_UPC_NUM);
            fieldValues.remove(OUTPATIENT.REV_CNTR_NDC_QTY);
            fieldValues.remove(OUTPATIENT.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(OUTPATIENT.REV_CNTR, "0636"); // Drugs requiring specific id
              String ndcCode = medicationCodeMapper.map(med.codes.get(0).code, person);
              fieldValues.put(OUTPATIENT.REV_CNTR_IDE_NDC_UPC_NUM, ndcCode);
              fieldValues.put(OUTPATIENT.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(OUTPATIENT.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(OUTPATIENT.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(OUTPATIENT.REV_CNTR_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(OUTPATIENT.HCPCS_CD, hcpcsCode);
          fieldValues.put(OUTPATIENT.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost)));
          fieldValues.put(OUTPATIENT.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(OUTPATIENT.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(OUTPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          rifWriters.writeValues(OUTPATIENT.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(OUTPATIENT.CLM_LINE_NUM, Integer.toString(claimLine));
          fieldValues.put(OUTPATIENT.REV_CNTR_DT, bb2DateFromTimestamp(encounter.start));
          // 99241: "Office consultation for a new or established patient"
          fieldValues.put(OUTPATIENT.HCPCS_CD, "99241");
          rifWriters.writeValues(OUTPATIENT.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export inpatient claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportInpatient(Person person, long startTime, long stopTime)
        throws IOException {
    HashMap<INPATIENT, String> fieldValues = new HashMap<>();

    boolean previousEmergency = false;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }
      boolean isInpatient = encounter.type.equals(EncounterType.INPATIENT.toString());
      boolean isEmergency = encounter.type.equals(EncounterType.EMERGENCY.toString());
      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      if (isVA || isIHSCenter || !(isInpatient || isEmergency)) {
        previousEmergency = false;
        continue;
      }

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long fiDocId = BB2RIFExporter.fiDocCntlNum.getAndDecrement();

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, INPATIENT.class, person);

      // The REQUIRED fields
      fieldValues.put(INPATIENT.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(INPATIENT.CLM_ID, "" + claimId);
      fieldValues.put(INPATIENT.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(INPATIENT.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(INPATIENT.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(INPATIENT.CLM_ADMSN_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(INPATIENT.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(INPATIENT.NCH_BENE_DSCHRG_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(INPATIENT.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(INPATIENT.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(INPATIENT.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(INPATIENT.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(INPATIENT.OP_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(INPATIENT.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer(HealthInsuranceModule.MEDICARE)) {
        fieldValues.put(INPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(INPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(INPATIENT.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      String patientStatus = null;
      if (encounter.ended) {
        field = "1"; // TODO 2=transfer if the next encounter is also inpatient
        patientStatus = "A"; // discharged
      } else {
        field = "30"; // the patient is still here
        patientStatus = "C"; // still a patient
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
        patientStatus = "B"; // died
      }
      fieldValues.put(INPATIENT.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(INPATIENT.NCH_PTNT_STATUS_IND_CD, patientStatus);
      fieldValues.put(INPATIENT.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (isEmergency) {
        field = "1"; // emergency
        fieldValues.put(INPATIENT.REV_CNTR, "0450"); // emergency
      } else if (previousEmergency) {
        field = "2"; // urgent
      } else {
        field = "3"; // elective
      }
      fieldValues.put(INPATIENT.CLM_IP_ADMSN_TYPE_CD, field);
      fieldValues.put(INPATIENT.NCH_BENE_IP_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(INPATIENT.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(INPATIENT.NCH_IP_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(INPATIENT.NCH_IP_TOT_DDCTN_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      fieldValues.put(INPATIENT.CLM_UTLZTN_DAY_CNT, "" + days);
      if (days > 60) {
        field = "" + (days - 60);
      } else {
        field = "0";
      }
      fieldValues.put(INPATIENT.BENE_TOT_COINSRNC_DAYS_CNT, field);
      if (days > 60) {
        field = "1"; // days outlier
      } else if (encounter.claim.getTotalClaimCost() > 100_000) {
        field = "2"; // cost outlier
      } else {
        field = "0"; // no outlier
      }
      fieldValues.put(INPATIENT.CLM_DRG_OUTLIER_STAY_CD, field);
      fieldValues.put(INPATIENT.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(INPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      // OPTIONAL FIELDS
      fieldValues.put(INPATIENT.RNDRNG_PHYSN_NPI, encounter.clinician.npi);

      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          icdReasonCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(INPATIENT.PRNCPAL_DGNS_CD, icdReasonCode);
          fieldValues.put(INPATIENT.ADMTG_DGNS_CD, icdReasonCode);
        }
      }
      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> presentOnAdmission = getDiagnosesCodes(person, encounter.start);
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(),
                BB2RIFStructure.inpatientDxFields.length);
        for (int i = 0; i < smallest; i++) {
          INPATIENT[] dxField = BB2RIFStructure.inpatientDxFields[i];
          String dxCode = mappedDiagnosisCodes.get(i);
          fieldValues.put(dxField[0], dxCode);
          fieldValues.put(dxField[1], "0"); // 0=ICD10
          String present = presentOnAdmission.contains(dxCode) ? "Y" : "N";
          fieldValues.put(dxField[2], present);
        }
        if (!fieldValues.containsKey(INPATIENT.PRNCPAL_DGNS_CD)) {
          fieldValues.put(INPATIENT.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
      }

      if (fieldValues.containsKey(INPATIENT.PRNCPAL_DGNS_CD)) {
        String icdCode = fieldValues.get(INPATIENT.PRNCPAL_DGNS_CD);
        // Add a DRG code, if applicable
        if (drgCodeMapper.canMap(icdCode)) {
          fieldValues.put(INPATIENT.CLM_DRG_CD, drgCodeMapper.map(icdCode, person));
        }
        // Check for external code...
        setExternalCode(person, fieldValues,
            INPATIENT.PRNCPAL_DGNS_CD, INPATIENT.ICD_DGNS_E_CD1, INPATIENT.ICD_DGNS_E_VRSN_CD1,
            INPATIENT.CLM_E_POA_IND_SW1, presentOnAdmission);
        setExternalCode(person, fieldValues,
            INPATIENT.PRNCPAL_DGNS_CD, INPATIENT.FST_DGNS_E_CD, INPATIENT.FST_DGNS_E_VRSN_CD);
      }

      // Use the procedures in this encounter to enter mapped values
      boolean noProcedures = false;
      if (!encounter.procedures.isEmpty()) {
        List<HealthRecord.Procedure> mappableProcedures = new ArrayList<>();
        List<String> mappedProcedureCodes = new ArrayList<>();
        for (HealthRecord.Procedure procedure : encounter.procedures) {
          for (HealthRecord.Code code : procedure.codes) {
            if (conditionCodeMapper.canMap(code.code)) {
              mappableProcedures.add(procedure);
              mappedProcedureCodes.add(conditionCodeMapper.map(code.code, person, true));
              break; // take the first mappable code for each procedure
            }
          }
        }
        if (!mappableProcedures.isEmpty()) {
          int smallest = Math.min(mappableProcedures.size(),
                  BB2RIFStructure.inpatientPxFields.length);
          for (int i = 0; i < smallest; i++) {
            INPATIENT[] pxField = BB2RIFStructure.inpatientPxFields[i];
            fieldValues.put(pxField[0], mappedProcedureCodes.get(i));
            fieldValues.put(pxField[1], "0"); // 0=ICD10
            fieldValues.put(pxField[2], bb2DateFromTimestamp(mappableProcedures.get(i).start));
          }
        } else {
          noProcedures = true;
        }
      }
      if (icdReasonCode == null && noDiagnoses && noProcedures) {
        continue; // skip this encounter
      }
      previousEmergency = isEmergency;
      String revCenter = fieldValues.get(INPATIENT.REV_CNTR);

      synchronized (rifWriters.getOrCreateWriter(INPATIENT.class)) {
        int claimLine = 1;
        for (ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (hcpcsCodeMapper.canMap(code.code)) {
                hcpcsCode = hcpcsCodeMapper.map(code.code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
            fieldValues.put(INPATIENT.REV_CNTR, revCenter);
            fieldValues.remove(INPATIENT.REV_CNTR_NDC_QTY);
            fieldValues.remove(INPATIENT.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(INPATIENT.REV_CNTR, "0250"); // Pharmacy-general classification
              fieldValues.put(INPATIENT.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(INPATIENT.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(INPATIENT.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(INPATIENT.HCPCS_CD, hcpcsCode);
          fieldValues.put(INPATIENT.REV_CNTR_UNIT_CNT, "" + Integer.max(1, days));
          fieldValues.put(INPATIENT.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(INPATIENT.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(INPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          rifWriters.writeValues(INPATIENT.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(INPATIENT.CLM_LINE_NUM, Integer.toString(claimLine));
          // HCPCS 99221: "Inpatient hospital visits: Initial and subsequent"
          fieldValues.put(INPATIENT.HCPCS_CD, "99221");
          rifWriters.writeValues(INPATIENT.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export carrier claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportCarrier(Person person, long startTime, long stopTime) throws IOException {
    HashMap<CARRIER, String> fieldValues = new HashMap<>();

    double latestHemoglobin = 0;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }
      boolean isPrimary = (ProviderType.PRIMARY == encounter.provider.type);
      boolean isWellness = encounter.type.equals(EncounterType.WELLNESS.toString());
      boolean isUrgent = encounter.type.equals(EncounterType.URGENTCARE.toString());
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      if (isVA || !(isIHSCenter || isPrimary || isWellness || isUrgent)) {
        continue;
      }

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long carrClmId = BB2RIFExporter.carrClmCntlNum.getAndDecrement();

      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      staticFieldConfig.setValues(fieldValues, CARRIER.class, person);
      fieldValues.put(CARRIER.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));

      // The REQUIRED fields
      fieldValues.put(CARRIER.CLM_ID, "" + claimId);
      fieldValues.put(CARRIER.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(CARRIER.CARR_CLM_CNTL_NUM, "" + carrClmId);
      fieldValues.put(CARRIER.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(CARRIER.LINE_1ST_EXPNS_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(CARRIER.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(CARRIER.LINE_LAST_EXPNS_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(CARRIER.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(CARRIER.CARR_NUM,
              getCarrier(encounter.provider.state, CARRIER.CARR_NUM));
      fieldValues.put(CARRIER.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer(HealthInsuranceModule.MEDICARE)) {
        fieldValues.put(CARRIER.CARR_CLM_PRMRY_PYR_PD_AMT, "0");
      } else {
        fieldValues.put(CARRIER.CARR_CLM_PRMRY_PYR_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(CARRIER.NCH_CLM_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CARRIER.NCH_CARR_CLM_SBMTD_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CARRIER.NCH_CARR_CLM_ALOWD_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(CARRIER.CARR_CLM_CASH_DDCTBL_APLD_AMT,
              String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(CARRIER.CARR_CLM_RFRNG_PIN_NUM, encounter.provider.id);
      fieldValues.put(CARRIER.CARR_PRFRNG_PIN_NUM, encounter.provider.id);
      fieldValues.put(CARRIER.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(CARRIER.PRF_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(CARRIER.RFR_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(CARRIER.PRVDR_SPCLTY,
          ClinicianSpecialty.getCMSProviderSpecialtyCode(
              (String) encounter.clinician.attributes.get(Clinician.SPECIALTY)));
      fieldValues.put(CARRIER.TAX_NUM,
              bb2TaxId((String)encounter.clinician.attributes.get(Person.IDENTIFIER_SSN)));
      fieldValues.put(CARRIER.LINE_SRVC_CNT, "" + encounter.claim.items.size());
      fieldValues.put(CARRIER.CARR_LINE_PRCNG_LCLTY_CD,
              getCarrier(encounter.provider.state, CARRIER.CARR_LINE_PRCNG_LCLTY_CD));
      fieldValues.put(CARRIER.LINE_NCH_PMT_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));
      // length of encounter in minutes
      fieldValues.put(CARRIER.CARR_LINE_MTUS_CNT,
              "" + ((encounter.stop - encounter.start) / (1000 * 60)));

      fieldValues.put(CARRIER.LINE_HCT_HGB_RSLT_NUM,
              "" + latestHemoglobin);
      fieldValues.put(CARRIER.LINE_PLACE_OF_SRVC_CD, getPlaceOfService(encounter));

      // OPTIONAL
      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          icdReasonCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(CARRIER.PRNCPAL_DGNS_CD, icdReasonCode);
          fieldValues.put(CARRIER.LINE_ICD_DGNS_CD, icdReasonCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty() && icdReasonCode == null) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(),
              BB2RIFStructure.carrierDxFields.length);
      for (int i = 0; i < smallest; i++) {
        CARRIER[] dxField = BB2RIFStructure.carrierDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(CARRIER.PRNCPAL_DGNS_CD)) {
        fieldValues.put(CARRIER.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      synchronized (rifWriters.getOrCreateWriter(CARRIER.class)) {
        int lineNum = 1;
        CLIA cliaLab = cliaLabNumbers[person.randInt(cliaLabNumbers.length)];
        for (ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = "";
          String ndcCode = "";
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (hcpcsCodeMapper.canMap(code.code)) {
                hcpcsCode = hcpcsCodeMapper.map(code.code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              ndcCode = medicationCodeMapper.map(med.codes.get(0).code, person);
            }
          }
          // TBD: decide whether line item skip logic is needed here and in other files
          // TBD: affects ~80% of carrier claim lines, so left out for now
          // if (hcpcsCode == null) {
          //   continue; // skip this line item
          // }
          fieldValues.put(CARRIER.HCPCS_CD, hcpcsCode);
          if (betosCodeMapper.canMap(hcpcsCode)) {
            fieldValues.put(CARRIER.BETOS_CD, betosCodeMapper.map(hcpcsCode, person));
          } else {
            fieldValues.put(CARRIER.BETOS_CD, "");
          }
          fieldValues.put(CARRIER.LINE_NDC_CD, ndcCode);
          fieldValues.put(CARRIER.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", lineItem.deductible));
          fieldValues.put(CARRIER.LINE_COINSRNC_AMT,
                  String.format("%.2f", lineItem.coinsurance));
          fieldValues.put(CARRIER.LINE_BENE_PMT_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          fieldValues.put(CARRIER.LINE_PRVDR_PMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(CARRIER.LINE_SBMTD_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(CARRIER.LINE_ALOWD_CHRG_AMT,
              String.format("%.2f", lineItem.cost - lineItem.adjustment));

          // If this item is a lab report, add the number of the clinical lab...
          if  (lineItem.entry instanceof HealthRecord.Report) {
            fieldValues.put(CARRIER.CARR_LINE_CLIA_LAB_NUM, cliaLab.toString());
          }

          // set the line number and write out field values
          fieldValues.put(CARRIER.LINE_NUM, Integer.toString(lineNum++));
          rifWriters.writeValues(CARRIER.class, fieldValues);
        }

        if (lineNum == 1) {
          // If lineNum still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(CARRIER.LINE_NUM, Integer.toString(lineNum));
          fieldValues.put(CARRIER.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", encounter.claim.getDeductiblePaid()));
          fieldValues.put(CARRIER.LINE_COINSRNC_AMT,
                  String.format("%.2f", encounter.claim.getCoinsurancePaid()));
          fieldValues.put(CARRIER.LINE_SBMTD_CHRG_AMT,
                  String.format("%.2f", encounter.claim.getTotalClaimCost()));
          fieldValues.put(CARRIER.LINE_ALOWD_CHRG_AMT,
                  String.format("%.2f", encounter.claim.getCoveredCost()));
          fieldValues.put(CARRIER.LINE_PRVDR_PMT_AMT,
                  String.format("%.2f", encounter.claim.getCoveredCost()));
          fieldValues.put(CARRIER.LINE_BENE_PMT_AMT,
                  String.format("%.2f", encounter.claim.getPatientCost()));
          // 99241: "Office consultation for a new or established patient"
          fieldValues.put(CARRIER.HCPCS_CD, "99241");
          rifWriters.writeValues(CARRIER.class, fieldValues);
        }
      }
    }
  }

  private static String bb2TaxId(String ssn) {
    if (ssn != null) {
      return ssn.replaceAll("-", "");
    } else {
      return "";
    }
  }

  private String getCarrier(String state, CARRIER column) {
    for (LinkedHashMap<String, String> row : carrierLookup) {
      if (row.get("STATE").equals(state) || row.get("STATE_CODE").equals(state)) {
        return row.get(column.toString());
      }
    }
    return "0";
  }

  /**
   * Get the Line Place of Service Code. This is a required field for
   * Carrier and DME claims.
   * @param encounter The encounter.
   * @return non-null place of service code.
   */
  private String getPlaceOfService(HealthRecord.Encounter encounter) {
    String placeOfServiceCode = "11"; // Default to "11" = Office
    if (encounter.type.equalsIgnoreCase(EncounterType.VIRTUAL.toString())) {
      placeOfServiceCode = "02"; // telehealth
    } else if (encounter.provider.type == ProviderType.IHS) {
      if (encounter.provider.hasService(EncounterType.WELLNESS)) {
        placeOfServiceCode = "05"; // no hospitalization
      } else {
        placeOfServiceCode = "06"; // hospitalization
      }
    } else if (encounter.provider.type == ProviderType.VETERAN) {
      placeOfServiceCode = "26"; // military
    } else if (encounter.provider.hasService(EncounterType.SNF)) {
      placeOfServiceCode = "31"; // skilled nursing facility
    } else if (encounter.provider.hasService(EncounterType.HOSPICE)) {
      placeOfServiceCode = "34"; // hospice
    } else if (encounter.provider.hasService(EncounterType.HOME)) {
      placeOfServiceCode = "12"; // home
    } else if (encounter.provider.hasService(EncounterType.URGENTCARE)) {
      placeOfServiceCode = "20"; // urgent care
    } else if (encounter.type.equalsIgnoreCase(EncounterType.EMERGENCY.toString())) {
      placeOfServiceCode = "23"; // emergency room
    } else if (encounter.type.equalsIgnoreCase(EncounterType.INPATIENT.toString())) {
      placeOfServiceCode = "21"; // inpatient
    } else if (encounter.type.equalsIgnoreCase(EncounterType.OUTPATIENT.toString())) {
      placeOfServiceCode = "22"; // outpatient
    } else if (encounter.type.equalsIgnoreCase(EncounterType.AMBULATORY.toString())) {
      placeOfServiceCode = "22";
    } else if (encounter.type.equalsIgnoreCase(EncounterType.WELLNESS.toString())) {
      placeOfServiceCode = "11"; // office
    }
    return placeOfServiceCode;
  }

  /**
   * Utility class to manage a beneficiary's part D contract history.
   * Utility class to manage a beneficiary's part C contract history.
   */
  static class PartCContractHistory extends ContractHistory<PartCContractID> {
    private static final PartCContractID[] partCContractIDs = initContractIDs();

    /**
     * Create a new random Part C contract history.
     * @param person source of randomness
     * @param stopTime when the history should end (as ms since epoch)
     * @param yearsOfHistory how many years should be covered
     */
    public PartCContractHistory(Person person, long stopTime, int yearsOfHistory) {
      super(person, stopTime, yearsOfHistory, 20, 1);
    }

    /**
     * Get a random contract ID or null.
     * @param rand source of randomness
     * @return a contract ID (58% or the time) or null (42% of the time)
     */
    @Override
    protected PartCContractID getRandomContractID(RandomNumberGenerator rand) {
      if (rand.randInt(100) < 42) {
        // 42% chance of not enrolling in Part C
        // see https://www.kff.org/medicare/issue-brief/medicare-advantage-in-2021-enrollment-update-and-key-trends/
        return null;
      }
      return partCContractIDs[rand.randInt(partCContractIDs.length)];
    }

    /**
     * Initialize an array containing all of the configured contract IDs.
     * @return
     */
    private static PartCContractID[] initContractIDs() {
      int numContracts = Config.getAsInteger("exporter.bfd.partc_contract_count", 10);
      PartCContractID[] contractIDs = new PartCContractID[numContracts];
      PartCContractID contractID = PartCContractID.parse(
              Config.get("exporter.bfd.partc_contract_start", "Y0001"));
      for (int i = 0; i < numContracts; i++) {
        contractIDs[i] = contractID;
        contractID = contractID.next();
      }
      return contractIDs;
    }
  }

  /**
   * Utility class to manage a beneficiary's part D contract history.
   */
  static class PartDContractHistory extends ContractHistory<PartDContractID> {
    private static final PartDContractID[] partDContractIDs = initContractIDs();
    private boolean employeePDP;

    /**
     * Create a new random Part D contract history.
     * @param person source of randomness
     * @param stopTime when the history should end (as ms since epoch)
     * @param yearsOfHistory how many years should be covered
     */
    public PartDContractHistory(Person person, long stopTime, int yearsOfHistory) {
      super(person, stopTime, yearsOfHistory, 20, 1);

      // 1% chance of being enrolled in employer PDP if person's income is above threshold
      // TBD determine real % of employer PDP enrollment
      employeePDP = getPartDCostSharingCode(person).equals("09") && person.randInt(100) == 1;
    }

    /**
     * Check if person has employer sponsored PDP.
     * @return true if has employer PDP, false otherwise.
     */
    public boolean hasEmployeePDP() {
      return employeePDP;
    }

    /**
     * Get the RDS indicator based on whether person is enrolled in Part D and has employee
     * coverage.
     * @param contractID Part D contract ID or null if not enrolled
     * @return the RDS indicator code or null if contract id is null
     */
    public String getEmployeePDPIndicator(PartDContractID contractID) {
      if (contractID == null) {
        return null;
      } else if (hasEmployeePDP()) {
        return "Y";
      } else {
        return "N";
      }
    }

    /**
     * Get a random contract ID or null.
     * @param rand source of randomness
     * @return a contract ID (70% or the time) or null (30% of the time)
     */
    @Override
    protected PartDContractID getRandomContractID(RandomNumberGenerator rand) {
      if (rand.randInt(100) < 30) {
        // 30% chance of not enrolling in Part D
        // see https://www.kff.org/medicare/issue-brief/10-things-to-know-about-medicare-part-d-coverage-and-costs-in-2019/
        return null;
      }
      return partDContractIDs[rand.randInt(partDContractIDs.length)];
    }

    /**
     * Initialize an array containing all of the configured contract IDs.
     * @return the contract IDs
     */
    private static PartDContractID[] initContractIDs() {
      int numContracts = Config.getAsInteger("exporter.bfd.partd_contract_count", 10);
      PartDContractID[] contractIDs = new PartDContractID[numContracts];
      PartDContractID contractID = PartDContractID.parse(
              Config.get("exporter.bfd.partd_contract_start", "Z0001"));
      for (int i = 0; i < numContracts; i++) {
        contractIDs[i] = contractID;
        contractID = contractID.next();
      }
      return contractIDs;
    }
  }

  /**
   * Utility class to manage a beneficiary's contract history.
   */
  abstract static class ContractHistory<T extends FixedLengthIdentifier> {
    private List<ContractPeriod> contractPeriods;
    private static final PlanBenefitPackageID[] planBenefitPackageIDs = initPlanBenefitPackageIDs();

    /**
     * Create a new random contract history.
     * @param person source of randomness
     * @param stopTime when the history should end (as ms since epoch)
     * @param yearsOfHistory how many years should be covered
     * @param percentChangeOpenEnrollment percent chance contract will change at open enrollment
     * @param percentChangeMidYear percent chance contract will change mid year
     */
    public ContractHistory(Person person, long stopTime, int yearsOfHistory,
            int percentChangeOpenEnrollment, int percentChangeMidYear) {
      int endYear = Utilities.getYear(stopTime);
      int endMonth = 12;

      contractPeriods = new ArrayList<>();
      ContractPeriod currentContractPeriod =
              new ContractPeriod(endYear - yearsOfHistory, person);
      for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
        if (year == endYear) {
          endMonth = Utilities.getMonth(stopTime);
        }
        for (int month = 1; month <= endMonth; month++) {
          if ((month == 1 && person.randInt(100) < percentChangeOpenEnrollment)
                  || person.randInt(100) < percentChangeMidYear) {
            ContractPeriod newContractPeriod = new ContractPeriod(year, month, person);
            T currentContractID = currentContractPeriod.getContractID();
            T newContractID = newContractPeriod.getContractID();
            if ((currentContractID != null && !currentContractID.equals(newContractID))
                    || currentContractID != newContractID) {
              currentContractPeriod.setEndBefore(newContractPeriod);
              contractPeriods.add(currentContractPeriod);
              currentContractPeriod = newContractPeriod;
            }
          }
        }
      }
      currentContractPeriod.setEnd(stopTime);
      contractPeriods.add(currentContractPeriod);
    }

    /**
     * Get the contract ID for the specified point in time.
     * @param timeStamp the point in time
     * @return the contract ID or null if not enrolled at the specified point in time
     */
    public T getContractID(long timeStamp) {
      for (ContractPeriod contractPeriod: contractPeriods) {
        if (contractPeriod.covers(timeStamp)) {
          return contractPeriod.getContractID();
        }
      }
      return null;
    }

    /**
     * Get a list of contract periods that were active during the specified year.
     * @param year the year
     * @return the list
     */
    public List<ContractPeriod> getContractPeriods(int year) {
      List<ContractPeriod> periods = new ArrayList<>();
      for (ContractPeriod period: contractPeriods) {
        if (period.coversYear(year)) {
          periods.add(period);
        }
      }
      return Collections.unmodifiableList(periods);
    }

    /**
     * Get a count of months that were covered by Part D in the specified year.
     * @param year the year
     * @return the count
     */
    public int getCoveredMonthsCount(int year) {
      int count = 0;
      for (ContractPeriod period: getContractPeriods(year)) {
        if (period.getContractID() != null) {
          count += period.getCoveredMonths(year).size();
        }
      }
      return count;
    }

    /**
     * Get a random contract ID or null if bene is not enrolled. Implementations of this method
     * should use rand to model the likelihood of a bene being enrolled.
     * @param rand source of randomness
     * @return a contract ID or null
     */
    protected abstract T getRandomContractID(RandomNumberGenerator rand);

    /**
     * Get a random plan benefit package ID or null if the supplied contract ID is null.
     * @param rand a source of randomness
     * @param contractID the contract ID
     * @return a random plan benefit package ID
     */
    protected PlanBenefitPackageID getRandomPlanBenefitPackageID(RandomNumberGenerator rand,
            T contractID) {
      if (contractID == null) {
        return null; // no benefit package if not on contract
      } else {
        return planBenefitPackageIDs[rand.randInt(planBenefitPackageIDs.length)];
      }
    }

    /**
     * Initialize an array containing all of the configured plan benefit package IDs.
     * @return the package IDs
     */
    private static PlanBenefitPackageID[] initPlanBenefitPackageIDs() {
      int numPackages = Config.getAsInteger("exporter.bfd.plan_benefit_package_count", 5);
      PlanBenefitPackageID[] packageIDs = new PlanBenefitPackageID[numPackages];
      PlanBenefitPackageID packageID = PlanBenefitPackageID.parse(
              Config.get("exporter.bfd.plan_benefit_package_start", "800"));
      for (int i = 0; i < numPackages; i++) {
        packageIDs[i] = packageID;
        packageID = packageID.next();
      }
      return packageIDs;
    }

    /**
     * Utility class that represents a period of time and an associated contract id.
     */
    public class ContractPeriod {
      private LocalDate startDate;
      private LocalDate endDate;
      private T contractID;
      private PlanBenefitPackageID planBenefitPackageID;

      /**
       * Create a new contract period. Contract periods have a one month granularity so the
       * supplied start and end are adjusted to the first day of the start month and last day of
       * the end month.
       * @param start the start of the contract period
       * @param end the end of the contract period
       * @param contractID the contract id
       * @param planBenefitPackageID the plan benefit package id
       */
      public ContractPeriod(LocalDate start, LocalDate end, T contractID,
              PlanBenefitPackageID planBenefitPackageID) {
        if (start != null) {
          this.startDate = LocalDate.of(start.getYear(), start.getMonthValue(), 1);
        }
        if (end != null) {
          this.endDate = LocalDate.of(end.getYear(), end.getMonthValue(), 1)
                  .plusMonths(1).minusDays(1);
        }
        this.contractID = contractID;
        this.planBenefitPackageID = planBenefitPackageID;
      }

      /**
       * Create a new contract period. Contract periods have a one month granularity so the
       * supplied start and end are adjusted to the first day of the start month and last day of
       * the end month. A random plan benefit package ID is chosen
       * @param start the start of the contract period
       * @param end the end of the contract period
       * @param contractID the contract id
       * @param rand source of randomness
       */
      public ContractPeriod(LocalDate start, LocalDate end, T contractID,
              RandomNumberGenerator rand) {
        this(start, end, contractID, getRandomPlanBenefitPackageID(rand, contractID));
      }

      /**
       * Create a new contract period starting on the first days of the specified month. A contract
       * id is randomly assigned.
       * @param year the year
       * @param month the month
       * @param rand source of randomness
       */
      public ContractPeriod(int year, int month, RandomNumberGenerator rand) {
        this(LocalDate.of(year, month, 1), null, getRandomContractID(rand), rand);
      }

      /**
       * Create a new contract period starting on the first days of the specified year. A contract
       * id is randomly assigned.
       * @param year the year
       * @param rand source of randomness
       */
      public ContractPeriod(int year, RandomNumberGenerator rand) {
        this(year, 1, rand);
      }

      /**
       * Get the contract id.
       * @return the contract id or null if not enrolled during this period
       */
      public T getContractID() {
        return contractID;
      }

      public PlanBenefitPackageID getPlanBenefitPackageID() {
        return planBenefitPackageID;
      }

      /**
       * Get a list of years covered by this period.
       * @return list of years
       * @throws IllegalStateException if the period has a null start and end
       */
      public List<Integer> getCoveredYears() {
        if (startDate == null || endDate == null) {
          throw new IllegalStateException(
                  "Contract period is unbounded (either start or end is null)");
        }
        ArrayList<Integer> years = new ArrayList<>();
        for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
          years.add(year);
        }
        return years;
      }

      /**
       * Get the list of months that are covered by this contract period in the specified year.
       * @param year the year
       * @return the list
       */
      public List<Integer> getCoveredMonths(int year) {
        ArrayList<Integer> months = new ArrayList<>();
        if (year < startDate.getYear() || year > endDate.getYear()) {
          return months;
        }
        int startMonth = 1;
        int endMonth = 12;
        if (year == startDate.getYear()) {
          startMonth = startDate.getMonthValue();
        }
        if (year == endDate.getYear()) {
          endMonth = endDate.getMonthValue();
        }
        for (int i = startMonth; i <= endMonth; i++) {
          months.add(i);
        }
        return months;
      }

      /**
       * Set the end of this period to occur the month before the start of the specified period.
       * @param newContractPeriod the period to end one before
       * @throws IllegalStateException if the supplied period has a null start
       */
      public void setEndBefore(ContractPeriod newContractPeriod) {
        if (newContractPeriod.startDate == null) {
          throw new IllegalStateException(
                  "Contract period has an unbounded start (start is null)");
        }
        this.endDate = newContractPeriod.startDate.minusDays(1);
      }

      /**
       * Set the end of this period to the supplied point in time (in ms since the epoch).
       * @param stopTime the point in time
       */
      public void setEnd(long stopTime) {
        endDate = Instant.ofEpochMilli(stopTime).atZone(ZoneId.systemDefault()).toLocalDate();
      }

      /**
       * Check whether this period includes the specified point in time. Unbounded periods are
       * assumed to cover all times before, after or both.
       * @param timeStamp point in time
       * @return true of the period covers the point in time, false otherwise
       */
      public boolean covers(long timeStamp) {
        LocalDate date = Instant.ofEpochMilli(timeStamp)
                .atZone(ZoneId.systemDefault()).toLocalDate();
        return (startDate == null || startDate.isBefore(date) || startDate.isEqual(date))
                && (endDate == null || endDate.isAfter(date) || endDate.isEqual(date));
      }

      /**
       * Check if this period has any overlap with the specified year.
       * @param year the year
       * @return true if the period overlap with any point in the year, false otherwise.
       */
      public boolean coversYear(int year) {
        return (startDate == null || startDate.getYear() <= year)
                && (endDate == null || endDate.getYear() >= year);
      }
    }
  }

  /**
   * Export prescription claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportPrescription(Person person, long startTime, long stopTime)
        throws IOException {
    PartDContractHistory partDContracts =
            (PartDContractHistory) person.attributes.get(BB2_PARTD_CONTRACTS);
    // Build a chronologically ordered list of prescription fills (including refills where
    // specified).
    List<PrescriptionFill> prescriptionFills = new LinkedList<>();
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }

      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      if (isVA || isIHSCenter) {
        continue;
      }
      for (Medication medication : encounter.medications) {
        if (!medicationCodeMapper.canMap(medication.codes.get(0).code)) {
          continue; // skip codes that can't be mapped to NDC
        }
        long supplyDaysMax = 90; // TBD - 30, 60, 90 day refil schedules?
        long supplyInterval = supplyDaysMax * 24 * 60 * 60 * 1000;
        long finishTime = medication.stop == 0L ? stopTime : Long.min(medication.stop, stopTime);
        String medicationCode = medicationCodeMapper.map(medication.codes.get(0).code, person);
        long time = medication.start;
        int fillNo = 1;
        while (time < finishTime) {
          PartDContractID partDContractID = partDContracts.getContractID(time);
          PrescriptionFill fill = new PrescriptionFill(time, encounter, medication,
                    medicationCode, fillNo, partDContractID, supplyInterval, finishTime);
          if (partDContractID != null) {
            prescriptionFills.add(fill);
          }
          if (!fill.refillsRemaining()) {
            break;
          }
          time += Long.min((long)fill.days * 24 * 60 * 60 * 1000, supplyInterval);
          fillNo++;
        }
      }
    }
    Collections.sort(prescriptionFills);

    // Export each prescription fill to RIF format
    HashMap<PDE, String> fieldValues = new HashMap<>();
    double costs = 0;
    int costYear = 0;
    for (PrescriptionFill fill: prescriptionFills) {

      long pdeId = BB2RIFExporter.pdeId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, PDE.class, person);

      // The REQUIRED fields
      fieldValues.put(PDE.PDE_ID, "" + pdeId);
      fieldValues.put(PDE.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(PDE.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(PDE.SRVC_DT, bb2DateFromTimestamp(fill.time));
      fieldValues.put(PDE.SRVC_PRVDR_ID, fill.encounter.provider.id);
      fieldValues.put(PDE.PRSCRBR_ID,
          "" + (9_999_999_999L - fill.encounter.clinician.identifier));
      fieldValues.put(PDE.RX_SRVC_RFRNC_NUM, "" + pdeId);
      fieldValues.put(PDE.PROD_SRVC_ID, fill.medicationCode);
      // The following field was replaced by the PartD contract ID, leaving this here for now
      // until this is validated
      // H=hmo, R=ppo, S=stand-alone, E=employer direct, X=limited income
      // fieldValues.put(PrescriptionFields.PLAN_CNTRCT_REC_ID,
      //     ("R" + Math.abs(
      //         UUID.fromString(medication.claim.payer.uuid)
      //         .getMostSignificantBits())).substring(0, 5));
      fieldValues.put(PDE.PLAN_CNTRCT_REC_ID, fill.partDContractID.toString());
      fieldValues.put(PDE.DAW_PROD_SLCTN_CD, "" + (int) person.rand(0, 9));
      fieldValues.put(PDE.QTY_DSPNSD_NUM, "" + fill.quantity);
      fieldValues.put(PDE.DAYS_SUPLY_NUM, "" + fill.days);
      fieldValues.put(PDE.FILL_NUM, "" + fill.fillNo);
      int year = Utilities.getYear(fill.time);
      if (year != costYear) {
        costYear = year;
        costs = 0;
      }
      costs += fill.medication.claim.getPatientCost();
      if (costs <= 4550.00) {
        fieldValues.put(PDE.GDC_BLW_OOPT_AMT, String.format("%.2f", costs));
        fieldValues.put(PDE.GDC_ABV_OOPT_AMT, "0");
      } else {
        fieldValues.put(PDE.GDC_BLW_OOPT_AMT, "4550.00");
        fieldValues.put(PDE.GDC_ABV_OOPT_AMT,
                String.format("%.2f", (costs - 4550)));
      }
      fieldValues.put(PDE.PTNT_PAY_AMT,
              String.format("%.2f", fill.medication.claim.getPatientCost()));
      fieldValues.put(PDE.CVRD_D_PLAN_PD_AMT,
          String.format("%.2f", fill.medication.claim.getCoveredCost()));
      fieldValues.put(PDE.NCVRD_PLAN_PD_AMT,
          String.format("%.2f", fill.medication.claim.getPatientCost()));
      fieldValues.put(PDE.TOT_RX_CST_AMT,
          String.format("%.2f", fill.medication.claim.getTotalClaimCost()));
      fieldValues.put(PDE.PHRMCY_SRVC_TYPE_CD, "0" + (int) person.rand(1, 8));
      fieldValues.put(PDE.PD_DT, bb2DateFromTimestamp(fill.time));
      // 00=not specified, 01=home, 02=SNF, 03=long-term, 11=hospice, 14=homeless
      if (person.attributes.containsKey("homeless")
          && ((Boolean) person.attributes.get("homeless") == true)) {
        fieldValues.put(PDE.PTNT_RSDNC_CD, "14");
      } else {
        fieldValues.put(PDE.PTNT_RSDNC_CD, "01");
      }

      rifWriters.writeValues(PDE.class, fieldValues);
    }
  }

  private static class PrescriptionFill implements Comparable<PrescriptionFill> {
    long time;
    HealthRecord.Encounter encounter;
    Medication medication;
    PartDContractID partDContractID;
    int quantity;
    int days;
    int fillNo;
    String medicationCode;
    int refills = 0;

    PrescriptionFill(long time, HealthRecord.Encounter encounter, Medication medication,
            String medicationCode, int fillNo, PartDContractID partDContractID,
            long supplyInterval, long end) {
      this.time = time;
      this.encounter = encounter;
      this.medication = medication;
      this.medicationCode = medicationCode;
      this.fillNo = fillNo;
      this.partDContractID = partDContractID;
      if (medication.prescriptionDetails != null && medication.prescriptionDetails.has("refills")) {
        refills = medication.prescriptionDetails.get("refills").getAsInt();
      }
      if (end > time + supplyInterval || fillNo > 1) {
        end = time + supplyInterval;
      }
      initDaysAndQuantity(end);
    }

    boolean refillsRemaining() {
      return refills - fillNo + 1 > 0;
    }

    private void initDaysAndQuantity(long stopTime) {
      this.days = getDays(stopTime);
      double amountPerDay = 1;

      if (medication.prescriptionDetails != null
          && medication.prescriptionDetails.has("dosage")) {
        JsonObject dosage = medication.prescriptionDetails.getAsJsonObject("dosage");
        long amount = dosage.get("amount").getAsLong();
        long frequency = dosage.get("frequency").getAsLong();
        long period = dosage.get("period").getAsLong();
        String units = dosage.get("unit").getAsString();
        long periodTime = Utilities.convertTime(units, period);

        long perPeriod = amount * frequency;
        amountPerDay = (double) ((double) (perPeriod * periodTime) / (1000.0 * 60 * 60 * 24));
        if (amountPerDay == 0) {
          amountPerDay = 1;
        }
      }

      this.quantity = (int) (amountPerDay * days);
    }

    private int getDays(long stopTime) {
      long medDuration = stopTime - time;
      double calcDays = (double) (medDuration / (1000 * 60 * 60 * 24));

      if (medication.prescriptionDetails != null
          && medication.prescriptionDetails.has("duration")) {
        JsonObject duration = medication.prescriptionDetails.getAsJsonObject("duration");
        long medQuantity = duration.get("quantity").getAsLong();
        String unit = duration.get("unit").getAsString();
        long durationTime = Utilities.convertTime(unit, medQuantity);
        double durationTimeInDays = (double) (durationTime / (1000 * 60 * 60 * 24));
        if (durationTimeInDays < calcDays) {
          calcDays = durationTimeInDays;
        }
      }
      return (int) calcDays;
    }

    @Override
    public int compareTo(PrescriptionFill o) {
      // This method is only intended to be used to order prescriptions by time.
      // Note that this is inconsistent with PrescriptionEvent.equals, see warnings at
      // https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html
      return Long.compare(time, o.time);
    }
  }

  /**
   * Export DME details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportDME(Person person, long startTime, long stopTime)
        throws IOException {
    HashMap<DME, String> fieldValues = new HashMap<>();

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }

      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      if (isVA || isIHSCenter) {
        continue;
      }

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long carrClmId = BB2RIFExporter.carrClmCntlNum.getAndDecrement();

      double latestHemoglobin = 0;
      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, DME.class, person);

      // complex fields that could not easily be set using cms_field_values.tsv
      fieldValues.put(DME.CLM_ID, "" + claimId);
      fieldValues.put(DME.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(DME.CARR_CLM_CNTL_NUM, "" + carrClmId);
      fieldValues.put(DME.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(DME.LINE_HCT_HGB_RSLT_NUM, "" + latestHemoglobin);
      fieldValues.put(DME.CARR_NUM,
              getCarrier(encounter.provider.state, CARRIER.CARR_NUM));
      fieldValues.put(DME.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(DME.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(DME.PRVDR_NPI, encounter.provider.npi);
      fieldValues.put(DME.RFR_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(DME.PRVDR_SPCLTY,
          ClinicianSpecialty.getCMSProviderSpecialtyCode(
              (String) encounter.clinician.attributes.get(Clinician.SPECIALTY)));
      fieldValues.put(DME.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(DME.TAX_NUM,
              bb2TaxId((String)encounter.clinician.attributes.get(Person.IDENTIFIER_SSN)));
      fieldValues.put(DME.DMERC_LINE_PRCNG_STATE_CD,
              locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
      fieldValues.put(DME.LINE_1ST_EXPNS_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(DME.LINE_LAST_EXPNS_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(DME.LINE_SRVC_CNT, "" + encounter.claim.items.size());
      fieldValues.put(DME.LINE_PLACE_OF_SRVC_CD, getPlaceOfService(encounter));

      // OPTIONAL
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(DME.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(DME.LINE_ICD_DGNS_CD, icdCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(), BB2RIFStructure.dmeDxFields.length);
      for (int i = 0; i < smallest; i++) {
        DME[] dxField = BB2RIFStructure.dmeDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(DME.PRNCPAL_DGNS_CD)) {
        fieldValues.put(DME.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        fieldValues.put(DME.LINE_ICD_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      // preprocess some subtotals...
      ClaimEntry subTotals = (encounter.claim).new ClaimEntry(null);
      for (ClaimEntry lineItem : encounter.claim.items) {
        if (lineItem.entry instanceof Device || lineItem.entry instanceof Supply) {
          subTotals.addCosts(lineItem);
        }
      }
      fieldValues.put(DME.CARR_CLM_CASH_DDCTBL_APLD_AMT,
          String.format("%.2f", subTotals.deductible));
      fieldValues.put(DME.CARR_CLM_PRMRY_PYR_PD_AMT,
          String.format("%.2f", subTotals.coinsurance + subTotals.payer));
      fieldValues.put(DME.NCH_CARR_CLM_ALOWD_AMT,
          String.format("%.2f", subTotals.cost - subTotals.adjustment));
      fieldValues.put(DME.NCH_CARR_CLM_SBMTD_CHRG_AMT,
          String.format("%.2f", subTotals.cost));
      fieldValues.put(DME.NCH_CLM_PRVDR_PMT_AMT,
          String.format("%.2f", subTotals.coinsurance + subTotals.payer));
      fieldValues.put(DME.CLM_PMT_AMT,
          String.format("%.2f", subTotals.coinsurance + subTotals.payer));

      synchronized (rifWriters.getOrCreateWriter(DME.class)) {
        int lineNum = 1;
        // Now generate the line items...
        for (ClaimEntry lineItem : encounter.claim.items) {
          if (!(lineItem.entry instanceof Device || lineItem.entry instanceof Supply)) {
            continue;
          }
          if (lineItem.entry instanceof Supply) {
            Supply supply = (Supply) lineItem.entry;
            fieldValues.put(DME.DMERC_LINE_MTUS_CNT, "" + supply.quantity);
          } else {
            fieldValues.put(DME.DMERC_LINE_MTUS_CNT, "");
          }
          if (!dmeCodeMapper.canMap(lineItem.entry.codes.get(0).code)) {
            System.err.println(" *** Possibly Missing DME Code: "
                + lineItem.entry.codes.get(0).code
                + " " + lineItem.entry.codes.get(0).display);
            continue;
          }
          fieldValues.put(DME.CLM_FROM_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(DME.CLM_THRU_DT, bb2DateFromTimestamp(lineItem.entry.start));
          String hcpcsCode = dmeCodeMapper.map(lineItem.entry.codes.get(0).code, person);
          fieldValues.put(DME.HCPCS_CD, hcpcsCode);
          if (betosCodeMapper.canMap(hcpcsCode)) {
            fieldValues.put(DME.BETOS_CD, betosCodeMapper.map(hcpcsCode, person));
          } else {
            fieldValues.put(DME.BETOS_CD, "");
          }
          fieldValues.put(DME.LINE_CMS_TYPE_SRVC_CD,
                  dmeCodeMapper.map(lineItem.entry.codes.get(0).code,
                          DME.LINE_CMS_TYPE_SRVC_CD.toString().toLowerCase(),
                          person));
          fieldValues.put(DME.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", lineItem.deductible));
          fieldValues.put(DME.LINE_COINSRNC_AMT,
                  String.format("%.2f", lineItem.getCoinsurancePaid()));
          fieldValues.put(DME.LINE_BENE_PMT_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          fieldValues.put(DME.LINE_PRVDR_PMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(DME.LINE_SBMTD_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(DME.LINE_ALOWD_CHRG_AMT,
              String.format("%.2f", lineItem.cost - lineItem.adjustment));
          fieldValues.put(DME.LINE_PRMRY_ALOWD_CHRG_AMT,
              String.format("%.2f", lineItem.cost - lineItem.adjustment));
          fieldValues.put(DME.LINE_NCH_PMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));

          // set the line number and write out field values
          fieldValues.put(DME.LINE_NUM, Integer.toString(lineNum++));
          rifWriters.writeValues(DME.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export Home Health Agency visits for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportHome(Person person, long startTime, long stopTime) throws IOException {
    HashMap<HHA, String> fieldValues = new HashMap<>();
    int homeVisits = 0;
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }

      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      boolean isHome = encounter.type.equals(EncounterType.HOME.toString());
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;

      if (isVA || isIHSCenter || !isHome) {
        continue;
      }

      homeVisits += 1;
      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long fiDocId = BB2RIFExporter.fiDocCntlNum.getAndDecrement();

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, HHA.class, person);

      // The REQUIRED fields
      fieldValues.put(HHA.BENE_ID,  (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(HHA.CLM_ID, "" + claimId);
      fieldValues.put(HHA.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(HHA.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(HHA.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HHA.CLM_ADMSN_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HHA.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(HHA.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(HHA.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(HHA.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(HHA.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(HHA.RNDRNG_PHYSN_NPI, encounter.clinician.npi);

      fieldValues.put(HHA.CLM_PMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer(HealthInsuranceModule.MEDICARE)) {
        fieldValues.put(HHA.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(HHA.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(HHA.PRVDR_STATE_CD,
          locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(HHA.CLM_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(HHA.CLM_HHA_TOT_VISIT_CNT, "" + homeVisits);
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(HHA.REV_CNTR_UNIT_CNT, "" + days);
      fieldValues.put(HHA.REV_CNTR_RATE_AMT,
          String.format("%.2f", (encounter.claim.getTotalClaimCost() / days)));
      fieldValues.put(HHA.REV_CNTR_PMT_AMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(HHA.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(HHA.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(HHA.PRNCPAL_DGNS_CD, icdCode);
        }
      }

      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String dischargeStatus = null;
      if (encounter.ended) {
        dischargeStatus = "1"; // TODO 2=transfer if the next encounter is also inpatient
      } else {
        dischargeStatus = "30"; // the patient is still here
      }
      if (!person.alive(encounter.stop)) {
        dischargeStatus = "20"; // the patient died before the encounter ended
      }
      fieldValues.put(HHA.PTNT_DSCHRG_STUS_CD, dischargeStatus);

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(),
              BB2RIFStructure.homeDxFields.length);
      for (int i = 0; i < smallest; i++) {
        HHA[] dxField = BB2RIFStructure.homeDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(HHA.PRNCPAL_DGNS_CD)) {
        fieldValues.put(HHA.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      // Check for external code...
      setExternalCode(person, fieldValues,
          HHA.PRNCPAL_DGNS_CD, HHA.ICD_DGNS_E_CD1, HHA.ICD_DGNS_E_VRSN_CD1);
      setExternalCode(person, fieldValues,
          HHA.PRNCPAL_DGNS_CD, HHA.FST_DGNS_E_CD, HHA.FST_DGNS_E_VRSN_CD);

      String revCenter = fieldValues.get(HHA.REV_CNTR);

      synchronized (rifWriters.getOrCreateWriter(HHA.class)) {
        int claimLine = 1;
        for (ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (hcpcsCodeMapper.canMap(code.code)) {
                hcpcsCode = hcpcsCodeMapper.map(code.code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
            fieldValues.put(HHA.REV_CNTR, revCenter);
            fieldValues.remove(HHA.REV_CNTR_NDC_QTY);
            fieldValues.remove(HHA.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(HHA.REV_CNTR, "0250"); // Pharmacy-general classification
              fieldValues.put(HHA.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(HHA.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(HHA.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(HHA.REV_CNTR_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(HHA.HCPCS_CD, hcpcsCode);
          fieldValues.put(HHA.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(HHA.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(HHA.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(HHA.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          rifWriters.writeValues(HHA.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(HHA.CLM_LINE_NUM, Integer.toString(claimLine));
          fieldValues.put(HHA.REV_CNTR_DT, bb2DateFromTimestamp(encounter.start));
          fieldValues.put(HHA.HCPCS_CD, "T1021"); // home health visit
          rifWriters.writeValues(HHA.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export Home Health Agency visits for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportHospice(Person person, long startTime, long stopTime) throws IOException {
    HashMap<HOSPICE, String> fieldValues = new HashMap<>();
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }

      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      boolean isHospice = encounter.type.equals(EncounterType.HOSPICE.toString());
      if (isVA || isIHSCenter || !isHospice) {
        continue;
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long fiDocId = BB2RIFExporter.fiDocCntlNum.getAndDecrement();
      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, HOSPICE.class, person);

      fieldValues.put(HOSPICE.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(HOSPICE.CLM_ID, "" + claimId);
      fieldValues.put(HOSPICE.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(HOSPICE.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(HOSPICE.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HOSPICE.CLM_HOSPC_START_DT_ID, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HOSPICE.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(HOSPICE.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(HOSPICE.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(HOSPICE.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(HOSPICE.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(HOSPICE.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(HOSPICE.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer(HealthInsuranceModule.MEDICARE)) {
        fieldValues.put(HOSPICE.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(HOSPICE.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(HOSPICE.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      // NCH_PTNT_STUS_IND_CD: A = Discharged, B = Died, C = Still a patient
      String dischargeStatus = null;
      String patientStatus = null;
      String dischargeDate = null;
      if (encounter.ended) {
        dischargeStatus = "1"; // TODO 2=transfer if the next encounter is also inpatient
        patientStatus = "A"; // discharged
        dischargeDate = bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
      } else {
        dischargeStatus = "30"; // the patient is still here
        patientStatus = "C"; // still a patient
      }
      if (!person.alive(encounter.stop)) {
        dischargeStatus = "20"; // the patient died before the encounter ended
        patientStatus = "B"; // died
        dischargeDate = bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
      }
      fieldValues.put(HOSPICE.PTNT_DSCHRG_STUS_CD, dischargeStatus);
      fieldValues.put(HOSPICE.NCH_PTNT_STATUS_IND_CD, patientStatus);
      if (dischargeDate != null) {
        fieldValues.put(HOSPICE.NCH_BENE_DSCHRG_DT, dischargeDate);
      }
      fieldValues.put(HOSPICE.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(HOSPICE.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(HOSPICE.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(HOSPICE.REV_CNTR_PMT_AMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(HOSPICE.REV_CNTR_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));

      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(HOSPICE.PRNCPAL_DGNS_CD, icdCode);
        }
      }

      int smallest = Math.min(mappedDiagnosisCodes.size(),
              BB2RIFStructure.hospiceDxFields.length);
      for (int i = 0; i < smallest; i++) {
        HOSPICE[] dxField = BB2RIFStructure.hospiceDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(HOSPICE.PRNCPAL_DGNS_CD)) {
        fieldValues.put(HOSPICE.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      // Check for external code...
      setExternalCode(person, fieldValues,
          HOSPICE.PRNCPAL_DGNS_CD, HOSPICE.ICD_DGNS_E_CD1, HOSPICE.ICD_DGNS_E_VRSN_CD1);
      setExternalCode(person, fieldValues,
          HOSPICE.PRNCPAL_DGNS_CD, HOSPICE.FST_DGNS_E_CD, HOSPICE.FST_DGNS_E_VRSN_CD);

      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(HOSPICE.CLM_UTLZTN_DAY_CNT, "" + days);
      int coinDays = days -  21; // first 21 days no coinsurance
      if (coinDays < 0) {
        coinDays = 0;
      }
      fieldValues.put(HOSPICE.REV_CNTR_UNIT_CNT, "" + days);
      fieldValues.put(HOSPICE.REV_CNTR_RATE_AMT,
          String.format("%.2f", (encounter.claim.getTotalClaimCost() / days)));
      String revCenter = fieldValues.get(HOSPICE.REV_CNTR);

      synchronized (rifWriters.getOrCreateWriter(HOSPICE.class)) {
        int claimLine = 1;
        for (ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (hcpcsCodeMapper.canMap(code.code)) {
                hcpcsCode = hcpcsCodeMapper.map(code.code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
            fieldValues.put(HOSPICE.REV_CNTR, revCenter);
            fieldValues.remove(HOSPICE.REV_CNTR_NDC_QTY);
            fieldValues.remove(HOSPICE.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(HOSPICE.REV_CNTR, "0250"); // Pharmacy-general classification
              fieldValues.put(HOSPICE.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(HOSPICE.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(HOSPICE.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(HOSPICE.REV_CNTR_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(HOSPICE.HCPCS_CD, hcpcsCode);
          fieldValues.put(HOSPICE.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(HOSPICE.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(HOSPICE.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(HOSPICE.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          rifWriters.writeValues(HOSPICE.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(HOSPICE.CLM_LINE_NUM, Integer.toString(claimLine));
          fieldValues.put(HOSPICE.REV_CNTR_DT, bb2DateFromTimestamp(encounter.start));
          fieldValues.put(HOSPICE.HCPCS_CD, "S9126"); // hospice per diem
          rifWriters.writeValues(HOSPICE.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export Home Health Agency visits for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportSNF(Person person, long startTime, long stopTime) throws IOException {
    HashMap<SNF, String> fieldValues = new HashMap<>();
    boolean previousEmergency;
    boolean previousUrgent;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < claimCutoff) {
        continue;
      }

      boolean isVA = (ProviderType.VETERAN == encounter.provider.type);
      // IHS facilities have valid 6 digit id, IHS centers don't
      boolean isIHSCenter = (ProviderType.IHS == encounter.provider.type)
              && encounter.provider.id.length() != 6;
      boolean isSNF = encounter.type.equals(EncounterType.SNF.toString());
      if (isVA || isIHSCenter || !isSNF) {
        continue;
      }

      previousEmergency = encounter.type.equals(EncounterType.EMERGENCY.toString());
      previousUrgent = encounter.type.equals(EncounterType.URGENTCARE.toString());

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      long fiDocId = BB2RIFExporter.fiDocCntlNum.getAndDecrement();

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, SNF.class, person);

      // The REQUIRED Fields
      fieldValues.put(SNF.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(SNF.CLM_ID, "" + claimId);
      fieldValues.put(SNF.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(SNF.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(SNF.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(SNF.CLM_ADMSN_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(SNF.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(SNF.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(SNF.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(SNF.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(SNF.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(SNF.OP_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(SNF.RNDRNG_PHYSN_NPI, encounter.clinician.npi);

      fieldValues.put(SNF.CLM_PMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer(HealthInsuranceModule.MEDICARE)) {
        fieldValues.put(SNF.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(SNF.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(SNF.PRVDR_STATE_CD,
          locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(SNF.CLM_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (previousEmergency) {
        fieldValues.put(SNF.CLM_IP_ADMSN_TYPE_CD, "1");
      } else if (previousUrgent) {
        fieldValues.put(SNF.CLM_IP_ADMSN_TYPE_CD, "2");
      } else {
        fieldValues.put(SNF.CLM_IP_ADMSN_TYPE_CD, "3");
      }
      fieldValues.put(SNF.NCH_BENE_IP_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(SNF.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(SNF.NCH_IP_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(SNF.NCH_IP_TOT_DDCTN_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()
              + encounter.claim.getCoinsurancePaid()));
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(SNF.CLM_UTLZTN_DAY_CNT, "" + days);
      int coinDays = days -  21; // first 21 days no coinsurance
      if (coinDays < 0) {
        coinDays = 0;
      }
      fieldValues.put(SNF.BENE_TOT_COINSRNC_DAYS_CNT, "" + coinDays);
      fieldValues.put(SNF.REV_CNTR_UNIT_CNT, "" + days);
      fieldValues.put(SNF.REV_CNTR_RATE_AMT,
          String.format("%.2f", (encounter.claim.getTotalClaimCost() / days)));
      fieldValues.put(SNF.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(SNF.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      // OPTIONAL CODES
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(SNF.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(SNF.ADMTG_DGNS_CD, icdCode);
          if (drgCodeMapper.canMap(icdCode)) {
            fieldValues.put(SNF.CLM_DRG_CD, drgCodeMapper.map(icdCode, person));
          }
        }
      }

      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      // NCH_PTNT_STUS_IND_CD: A = Discharged, B = Died, C = Still a patient
      String dischargeStatus = null;
      String patientStatus = null;
      String dischargeDate = null;
      if (encounter.ended) {
        dischargeStatus = "1"; // TODO 2=transfer if the next encounter is also inpatient
        patientStatus = "A"; // discharged
        dischargeDate = bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
      } else {
        dischargeStatus = "30"; // the patient is still here
        patientStatus = "C"; // still a patient
      }
      if (!person.alive(encounter.stop)) {
        dischargeStatus = "20"; // the patient died before the encounter ended
        patientStatus = "B"; // died
        dischargeDate = bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
      }
      fieldValues.put(SNF.PTNT_DSCHRG_STUS_CD, dischargeStatus);
      fieldValues.put(SNF.NCH_PTNT_STATUS_IND_CD, patientStatus);
      if (dischargeDate != null) {
        fieldValues.put(SNF.NCH_BENE_DSCHRG_DT, dischargeDate);
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(),
                BB2RIFStructure.snfDxFields.length);
        for (int i = 0; i < smallest; i++) {
          SNF[] dxField = BB2RIFStructure.snfDxFields[i];
          fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
          fieldValues.put(dxField[1], "0"); // 0=ICD10
        }
        if (!fieldValues.containsKey(SNF.PRNCPAL_DGNS_CD)) {
          fieldValues.put(SNF.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
          fieldValues.put(SNF.ADMTG_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
      }

      // Check for external code...
      setExternalCode(person, fieldValues,
          SNF.PRNCPAL_DGNS_CD, SNF.ICD_DGNS_E_CD1, SNF.ICD_DGNS_E_VRSN_CD1);
      setExternalCode(person, fieldValues,
          SNF.PRNCPAL_DGNS_CD, SNF.FST_DGNS_E_CD, SNF.FST_DGNS_E_VRSN_CD);

      // Use the procedures in this encounter to enter mapped values
      boolean noProcedures = false;
      if (!encounter.procedures.isEmpty()) {
        List<HealthRecord.Procedure> mappableProcedures = new ArrayList<>();
        List<String> mappedProcedureCodes = new ArrayList<>();
        for (HealthRecord.Procedure procedure : encounter.procedures) {
          for (HealthRecord.Code code : procedure.codes) {
            if (conditionCodeMapper.canMap(code.code)) {
              mappableProcedures.add(procedure);
              mappedProcedureCodes.add(conditionCodeMapper.map(code.code, person, true));
              break; // take the first mappable code for each procedure
            }
          }
        }
        if (!mappableProcedures.isEmpty()) {
          int smallest = Math.min(mappableProcedures.size(),
                  BB2RIFStructure.snfPxFields.length);
          for (int i = 0; i < smallest; i++) {
            SNF[] pxField = BB2RIFStructure.snfPxFields[i];
            fieldValues.put(pxField[0], mappedProcedureCodes.get(i));
            fieldValues.put(pxField[1], "0"); // 0=ICD10
            fieldValues.put(pxField[2], bb2DateFromTimestamp(mappableProcedures.get(i).start));
          }
        } else {
          noProcedures = true;
        }
      }

      if (noDiagnoses && noProcedures) {
        continue; // skip this encounter
      }
      String revCenter = fieldValues.get(SNF.REV_CNTR);

      synchronized (rifWriters.getOrCreateWriter(SNF.class)) {
        int claimLine = 1;
        for (ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (hcpcsCodeMapper.canMap(code.code)) {
                hcpcsCode = hcpcsCodeMapper.map(code.code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
            fieldValues.put(SNF.REV_CNTR, revCenter);
            fieldValues.remove(SNF.REV_CNTR_NDC_QTY);
            fieldValues.remove(SNF.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(SNF.REV_CNTR, "0250"); // Pharmacy-general classification
              fieldValues.put(SNF.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(SNF.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(SNF.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(SNF.HCPCS_CD, hcpcsCode);
          fieldValues.put(SNF.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(SNF.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(SNF.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          rifWriters.writeValues(SNF.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(SNF.CLM_LINE_NUM, Integer.toString(claimLine));
          // G0299: "direct skilled nursing services of a registered nurse (RN) in the home health
          // or hospice setting"
          fieldValues.put(SNF.HCPCS_CD, "G0299");
          rifWriters.writeValues(SNF.class, fieldValues);
        }
      }
    }
  }

  /**
   * Get the BB2 race code. BB2 uses a single code to represent race and ethnicity, we assume
   * ethnicity gets priority here.
   * @param ethnicity the Synthea ethnicity
   * @param race the Synthea race
   * @return the BB2 race code
   */
  public static String bb2RaceCode(String ethnicity, String race) {
    if ("hispanic".equals(ethnicity)) {
      return "5";
    } else {
      String bbRaceCode; // unknown
      switch (race) {
        case "white":
          bbRaceCode = "1";
          break;
        case "black":
          bbRaceCode = "2";
          break;
        case "asian":
          bbRaceCode = "4";
          break;
        case "native":
          bbRaceCode = "6";
          break;
        case "other":
        default:
          bbRaceCode = "3";
          break;
      }
      return bbRaceCode;
    }
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final BB2RIFExporter instance = new BB2RIFExporter();
  }

  /**
   * Get the current instance of the BBExporter.
   *
   * @return the current instance of the BBExporter.
   */
  public static BB2RIFExporter getInstance() {
    return SingletonHolder.instance;
  }

  /**
   * Utility class for dealing with code mapping configuration writers.
   */
  static class CodeMapper {
    private static boolean requireCodeMaps = Config.getAsBoolean(
            "exporter.bfd.require_code_maps", true);
    private HashMap<String, List<Map<String, String>>> map;
    private boolean mapImported = false;

    /**
     * Create a new CodeMapper for the supplied JSON string.
     * @param jsonMap a stringified JSON mapping writer. Expects the following format:
     * <pre>
     * {
     *   "synthea_code": [ # each synthea code will be mapped to one of the codes in this array
     *     {
     *       "code": "BFD_code",
     *       "description": "Description of code", # optional
     *       "other field": "value of other field" # optional additional fields
     *     }
     *   ]
     * }
     * </pre>
     */
    public CodeMapper(String jsonMap) {
      try {
        String json = Utilities.readResource(jsonMap);
        Gson g = new Gson();
        Type type = new TypeToken<HashMap<String,List<Map<String, String>>>>(){}.getType();
        map = g.fromJson(json, type);
        mapImported = true;
      } catch (JsonSyntaxException | IOException | IllegalArgumentException e) {
        if (requireCodeMaps) {
          throw new MissingResourceException("Unable to read code map file: " + jsonMap,
                  "CodeMapper", jsonMap);
        } else {
          // For testing, the mapping writer is not present.
          System.out.println("BB2Exporter is running without " + jsonMap);
        }
      }
    }

    /**
     * Determines whether this mapper has an entry for the supplied code.
     * @param codeToMap the Synthea code to look for
     * @return true if the Synthea code can be mapped to BFD, false if not
     */
    public boolean canMap(String codeToMap) {
      if (map == null) {
        return false;
      }
      return map.containsKey(codeToMap);
    }

    /**
     * Determines whether this mapper was successfully configured with a code map.
     * @return true if configured, false otherwise.
     */
    public boolean hasMap() {
      return mapImported;
    }

    /**
     * Get one of the BFD codes for the supplied Synthea code. Equivalent to
     * {@code map(codeToMap, "code", rand)}.
     * @param codeToMap the Synthea code to look for
     * @param rand a source of random numbers used to pick one of the list of BFD codes
     * @return the BFD code or null if the code can't be mapped
     */
    public String map(String codeToMap, RandomNumberGenerator rand) {
      return map(codeToMap, "code", rand);
    }

    /**
     * Get one of the BFD codes for the supplied Synthea code. Equivalent to
     * {@code map(codeToMap, "code", rand)}.
     * @param codeToMap the Synthea code to look for
     * @param rand a source of random numbers used to pick one of the list of BFD codes
     * @param stripDots whether to remove dots in codes (e.g. J39.45 -> J3945)
     * @return the BFD code or null if the code can't be mapped
     */
    public String map(String codeToMap, RandomNumberGenerator rand, boolean stripDots) {
      return map(codeToMap, "code", rand, stripDots);
    }

    /**
     * Get one of the BFD codes for the supplied Synthea code.
     * @param codeToMap the Synthea code to look for
     * @param bfdCodeType the type of BFD code to map to
     * @param rand a source of random numbers used to pick one of the list of BFD codes
     * @return the BFD code or null if the code can't be mapped
     */
    public String map(String codeToMap, String bfdCodeType, RandomNumberGenerator rand) {
      return map(codeToMap, bfdCodeType, rand, false);
    }

    /**
     * Get one of the BFD codes for the supplied Synthea code.
     * @param codeToMap the Synthea code to look for
     * @param bfdCodeType the type of BFD code to map to
     * @param rand a source of random numbers used to pick one of the list of BFD codes
     * @param stripDots whether to remove dots in codes (e.g. J39.45 -> J3945)
     * @return the BFD code or null if the code can't be mapped
     */
    public String map(String codeToMap, String bfdCodeType, RandomNumberGenerator rand,
            boolean stripDots) {
      if (!canMap(codeToMap)) {
        return null;
      }
      List<Map<String, String>> options = map.get(codeToMap);
      int choice = rand.randInt(options.size());
      String code = options.get(choice).get(bfdCodeType);
      if (stripDots) {
        return code.replaceAll("\\.", "");
      } else {
        return code;
      }
    }
  }

  /**
   * Utility class for writing to BB2 writers.
   */
  private static class SynchronizedBBLineWriter<E extends Enum<E>> {

    private String bbFieldSeparator = "|";
    private final Path path;
    private final Class<E> clazz;

    /**
     * Construct a new instance. Fields will be separated using the default '|' character.
     * @param path the file path to write to
     * @throws IOException if something goes wrong
     */
    public SynchronizedBBLineWriter(Class<E> clazz, Path path) {
      this.path = path;
      this.clazz = clazz;
      writeHeaderIfNeeded();
    }

    /**
     * Construct a new instance.
     * @param path the file path to write to
     * @param separator overrides the default '|' field separator
     * @throws IOException if something goes wrong
     */
    public SynchronizedBBLineWriter(Class<E> clazz, Path path, String separator) {
      this.path = path;
      this.clazz = clazz;
      this.bbFieldSeparator = separator;
      writeHeaderIfNeeded();
    }

    /**
     * Write a BB2 header if the file is not present or is empty.
     * @throws IOException if something goes wrong
     */
    private void writeHeaderIfNeeded() {
      if (getFile().length() == 0) {
        String[] fields = Arrays.stream(clazz.getEnumConstants()).map(Enum::name)
                .toArray(String[]::new);
        writeLine(fields);
      }
    }

    /**
     * Write a line of output consisting of one or more fields separated by '|' and terminated with
     * a system new line.
     * @param fields the fields that will be concatenated into the line
     * @throws IOException if something goes wrong
     */
    private void writeLine(String... fields) {
      String line = String.join(bbFieldSeparator, fields);
      Exporter.appendToFile(path, line);
    }

    /**
     * Write a BB2 writer line.
     * @param fieldValues a sparse map of column names to values, missing values will result in
     *     empty values in the corresponding column
     * @throws IOException if something goes wrong
     */
    public void writeValues(Map<E, String> fieldValues)
            throws IOException {
      String[] fields = Arrays.stream(clazz.getEnumConstants())
              .map((e) -> fieldValues.getOrDefault(e, "")).toArray(String[]::new);
      writeLine(fields);
    }

    /**
     * Get the file that this writer writes to.
     * @return the file
     */
    public File getFile() {
      return path.toFile();
    }
  }

  /**
   * Class to manage mapping values in the static BFD TSV writer to the exported writers.
   */
  public static class StaticFieldConfig {
    List<LinkedHashMap<String, String>> config;
    Map<String, LinkedHashMap<String, String>> configMap;

    /**
     * Default constructor that parses the TSV config writer.
     * @throws IOException if the writer can't be read.
     */
    public StaticFieldConfig() throws IOException {
      String tsv = Utilities.readResource("export/bfd_field_values.tsv");
      config = SimpleCSV.parse(tsv, '\t');
      configMap = new HashMap<>();
      for (LinkedHashMap<String, String> row: config) {
        configMap.put(row.get("Field"), row);
      }
    }

    /**
     * Only used for unit tests.
     * @param <E> the type parameter
     * @param field the name of a value in the supplied enum class (e.g. DML_IND).
     * @param tableEnum one of the exporter enums (e.g. InpatientFields or OutpatientFields).
     * @return the cell value in the TSV where field identifies the row and tableEnum is the column.
     */
    <E extends Enum<E>> String getValue(String field, Class<E> tableEnum) {
      return configMap.get(field).get(tableEnum.getSimpleName());
    }

    Set<String> validateTSV() {
      LinkedHashSet tsvIssues = new LinkedHashSet<>();
      for (Class tableEnum: BB2RIFStructure.RIF_FILES) {
        String columnName = tableEnum.getSimpleName();
        Method valueOf;
        try {
          valueOf = tableEnum.getMethod("valueOf", String.class);
        } catch (NoSuchMethodException | SecurityException ex) {
          // this should never happen since tableEnum has to be an enum which will have a valueOf
          // method but the compiler isn't clever enought to figure that out
          throw new IllegalArgumentException(ex);
        }
        for (LinkedHashMap<String, String> row: config) {
          String cellContents = stripComments(row.get(columnName));
          if (cellContents.equalsIgnoreCase("N/A")
                  || cellContents.equalsIgnoreCase("Coded")
                  || cellContents.equalsIgnoreCase("[Blank]")) {
            continue; // Skip fields that aren't used are required to be blank or are hand-coded
          } else if (isMacro(cellContents)) {
            tsvIssues.add(String.format(
                    "Skipping macro in TSV line %s [%s] for %s",
                    row.get("Line"), row.get("Field"), columnName));
            continue; // Skip unsupported macro's in the TSV
          } else if (cellContents.isEmpty()) {
            // tsvIssues.add(String.format(
            //        "Empty cell in TSV line %s [%s] for %s",
            //        row.get("Line"), row.get("Field"), columnName));
            continue; // Skip empty cells
          }
          try {
            Enum enumVal = (Enum)valueOf.invoke(null, row.get("Field"));
          } catch (IllegalAccessException | IllegalArgumentException
                  | InvocationTargetException ex) {
            // This should only happen if the TSV contains a value for a field when the
            // columnName enum does not contain that field value.
            tsvIssues.add(String.format(
                    "Error in TSV line %s [%s] for %s (field not in enum): %s",
                    row.get("Line"), row.get("Field"), columnName, ex.toString()));
          }
        }
      }
      return tsvIssues;
    }

    /**
     * Set the configured values from the BFD TSV into the supplied map.
     * @param <E> the type parameter.
     * @param values the map that will receive the TSV-configured values.
     * @param tableEnum the enum class for the BFD table (e.g. InpatientFields or OutpatientFields).
     * @param rand source of randomness
     */
    public <E extends Enum<E>> void setValues(HashMap<E, String> values, Class<E> tableEnum,
            RandomNumberGenerator rand) {
      // Get the name of the columnName to populate. This must match a column name in the
      // config TSV.
      String columnName = tableEnum.getSimpleName();

      // Get the valueOf method for the supplied enum using reflection.
      // We'll use this to convert the string field name to the corresponding enum value
      Method valueOf;
      try {
        valueOf = tableEnum.getMethod("valueOf", String.class);
      } catch (NoSuchMethodException | SecurityException ex) {
        // this should never happen since tableEnum has to be an enum which will have a valueOf
        // method but the compiler isn't clever enought to figure that out
        throw new IllegalArgumentException(ex);
      }

      // Iterate over all of the rows in the TSV
      for (LinkedHashMap<String, String> row: config) {
        String cellContents = stripComments(row.get(columnName));
        String value = null;
        if (cellContents.equalsIgnoreCase("N/A")
            || cellContents.equalsIgnoreCase("Coded")) {
          continue; // Skip fields that aren't used or are hand-coded
        } else if (cellContents.equalsIgnoreCase("[Blank]")) {
          value = " "; // Literally blank
        } else if (isMacro(cellContents)) {
          continue; // Skip unsupported macro's in the TSV
        } else if (cellContents.isEmpty()) {
          continue; // Skip empty cells
        } else {
          value = processCell(cellContents, rand);
        }
        try {
          E enumVal = (E)valueOf.invoke(null, row.get("Field"));
          values.put(enumVal, value);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
          // This should only happen if the TSV contains a value for a field when the
          // columnName enum does not contain that field value.
        }
      }
    }

    /**
     * Remove comments (that consist of text in braces like this) and white space. Note that
     * this is greedy so "(foo) bar (baz)" would yield "".
     * @param str the string to strip
     * @return str with comments and white space removed.
     */
    private static String stripComments(String str) {
      str = str.replaceAll("\\(.*\\)", "");
      return str.trim();
    }

    private static boolean isMacro(String cellContents) {
      return cellContents.startsWith("[");
    }

    /**
     * Process a TSV cell. Content should be either a single value or a comma separated list of
     * value. Single values will be returned unchanged, if a list of values is supplied then one
     * is chosen at random and returned with any leading or trailing white space removed.
     * @param cellContents TSV cell contents.
     * @param rand a source of randomness.
     * @return the selected value.
     */
    static String processCell(String cellContents, RandomNumberGenerator rand) {
      String retval = cellContents;
      if (cellContents.contains(",")) {
        List<String> values = Arrays.asList(retval.split(","));
        int index = rand.randInt(values.size());
        retval = values.get(index).trim();
      }
      return retval;
    }
  }

  /**
   * Utility class for working with CLIA Lab Numbers.
   *
   * @see <a href="https://www.cms.gov/apps/clia/clia_start.asp">
   * https://www.cms.gov/apps/clia/clia_start.asp</a>
   */
  static class CLIA extends FixedLengthIdentifier {

    private static final char[][] CLIA_FORMAT = {NUMERIC, NUMERIC, ALPHA,
        NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC, NUMERIC};
    static final long MIN_CLIA = 0;
    static final long MAX_CLIA = maxValue(CLIA_FORMAT);

    public CLIA(long value) {
      super(value, CLIA_FORMAT);
    }

    static CLIA parse(String str) {
      return new CLIA(parse(str, CLIA_FORMAT));
    }

    public CLIA next() {
      return new CLIA(value + 1);
    }
  }

  /**
   * Utility class for working with CMS MBIs.
   * Note that this class fixes the value of character position 2 to be 'S' and will fail to
   * parse MBIs that do not conform to this restriction.
   *
   * @see <a href="https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf">
   * https://www.cms.gov/Medicare/New-Medicare-Card/Understanding-the-MBI-with-Format.pdf</a>
   */
  static class MBI extends FixedLengthIdentifier {

    private static final char[] FIXED = {'S'};
    private static final char[][] MBI_FORMAT = {NON_ZERO_NUMERIC, FIXED, ALPHA_NUMERIC, NUMERIC,
      NON_NUMERIC_LIKE_ALPHA, ALPHA_NUMERIC, NUMERIC, NON_NUMERIC_LIKE_ALPHA,
      NON_NUMERIC_LIKE_ALPHA, NUMERIC, NUMERIC};
    static final long MIN_MBI = 0;
    static final long MAX_MBI = maxValue(MBI_FORMAT);

    public MBI(long value) {
      super(value, MBI_FORMAT);
    }

    static MBI parse(String str) {
      return new MBI(parse(str, MBI_FORMAT));
    }

    public MBI next() {
      return new MBI(value + 1);
    }
  }

  /**
   * Utility class for working with CMS Part D Contract IDs.
   */
  static class PartDContractID extends FixedLengthIdentifier {

    private static final char[][] PARTD_CONTRACT_FORMAT = {
      ALPHA, NUMERIC, NUMERIC, NUMERIC, NUMERIC};
    static final long MIN_PARTD_CONTRACT_ID = 0;
    static final long MAX_PARTD_CONTRACT_ID = maxValue(PARTD_CONTRACT_FORMAT);

    public PartDContractID(long value) {
      super(value, PARTD_CONTRACT_FORMAT);
    }

    static PartDContractID parse(String str) {
      return new PartDContractID(parse(str, PARTD_CONTRACT_FORMAT));
    }

    public PartDContractID next() {
      return new PartDContractID(value + 1);
    }
  }

  /**
   * Utility class for working with CMS Part C Contract IDs.
   */
  static class PartCContractID extends FixedLengthIdentifier {

    private static final char[][] PARTC_CONTRACT_FORMAT = {
      ALPHA, NUMERIC, NUMERIC, NUMERIC, NUMERIC};
    static final long MIN_PARTC_CONTRACT_ID = 0;
    static final long MAX_PARTC_CONTRACT_ID = maxValue(PARTC_CONTRACT_FORMAT);

    public PartCContractID(long value) {
      super(value, PARTC_CONTRACT_FORMAT);
    }

    static PartCContractID parse(String str) {
      return new PartCContractID(parse(str, PARTC_CONTRACT_FORMAT));
    }

    public PartCContractID next() {
      return new PartCContractID(value + 1);
    }
  }

  /**
   * Utility class for working with CMS Plan Benefit Package IDs.
   */
  static class PlanBenefitPackageID extends FixedLengthIdentifier {

    private static final char[][] PBP_CONTRACT_FORMAT = {
      NUMERIC, NUMERIC, NUMERIC};
    static final long MIN_PARTC_CONTRACT_ID = 0;
    static final long MAX_PARTC_CONTRACT_ID = maxValue(PBP_CONTRACT_FORMAT);

    public PlanBenefitPackageID(long value) {
      super(value, PBP_CONTRACT_FORMAT);
    }

    static PlanBenefitPackageID parse(String str) {
      return new PlanBenefitPackageID(parse(str, PBP_CONTRACT_FORMAT));
    }

    public PlanBenefitPackageID next() {
      return new PlanBenefitPackageID(value + 1);
    }
  }

  /**
   * Utility class for working with CMS HICNs.
   * Note that this class fixes the value of character position 1 to be 'T' and character position
   * 10 to be 'A' - it will fail to parse HICNs that do not conform to this restriction.
   *
   * @see <a href="https://github.com/CMSgov/beneficiary-fhir-data/blob/master/apps/bfd-model/bfd-model-rif-samples/dev/design-sample-data-sets.md">
   * https://github.com/CMSgov/beneficiary-fhir-data/blob/master/apps/bfd-model/bfd-model-rif-samples/dev/design-sample-data-sets.md</a>
   */
  static class HICN extends FixedLengthIdentifier {

    private static final char[] START = {'T'};
    private static final char[] END = {'A'};
    private static final char[][] HICN_FORMAT = {START, NUMERIC, NUMERIC, NUMERIC, NUMERIC,
      NUMERIC, NUMERIC, NUMERIC, NUMERIC, END};
    static final long MIN_HICN = 0;
    static final long MAX_HICN = maxValue(HICN_FORMAT);

    public HICN(long value) {
      super(value, HICN_FORMAT);
    }

    static HICN parse(String str) {
      return new HICN(parse(str, HICN_FORMAT));
    }

    public HICN next() {
      return new HICN(value + 1);
    }
  }

  private static class FixedLengthIdentifier {

    static final char[] NUMERIC = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    static final char[] NON_ZERO_NUMERIC = {'1', '2', '3', '4', '5', '6', '7', '8', '9'};
    static final char[] ALPHA = {
      'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
      'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    };
    static final char[] NON_NUMERIC_LIKE_ALPHA = {
      'A', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'T', 'U', 'V',
      'W', 'X', 'Y'
    };
    static final char[] ALPHA_NUMERIC = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'C', 'D', 'E', 'F', 'G', 'H', 'J',
      'K', 'M', 'N', 'P', 'Q', 'R', 'T', 'U', 'V', 'W', 'X', 'Y'
    };

    private final char[][] format;
    long value;

    public FixedLengthIdentifier(long value, char[][] format) {
      this.format = format;
      if (value < 0 || value > maxValue(format)) {
        throw new IllegalArgumentException(String.format("Value (%d) out of range (%d - %d)",
                value, 0, maxValue(format)));
      }
      this.value = value;
    }

    protected static long parse(String str, char[][] format) {
      str = str.replaceAll("-", "").toUpperCase();
      if (str.length() != format.length) {
        throw new IllegalArgumentException(String.format(
                "Invalid format (%s), must be %d characters", str, format.length));
      }
      long v = 0;
      for (int i = 0; i < format.length; i++) {
        int multiplier = format[i].length;
        v = v * multiplier;
        char c = str.charAt(i);
        char[] range = format[i];
        int index = indexOf(range, c);
        if (index == -1) {
          throw new IllegalArgumentException(String.format(
                  "Unexpected character (%c) at position %d in %s", c, i, str));
        }
        v += index;
      }
      return v;
    }

    protected static long maxValue(char[][] format) {
      long max = 1;
      for (char[] range : format) {
        max = max * range.length;
      }
      return max - 1;
    }

    private static int indexOf(char[] arr, char v) {
      for (int i = 0; i < arr.length; i++) {
        if (arr[i] == v) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      long v = this.value;
      for (int i = 0; i < format.length; i++) {
        char[] range = format[format.length - i - 1];
        long p = v % range.length;
        sb.insert(0, range[(int)p]);
        v = v / range.length;
      }
      return sb.toString();
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 53 * hash + (int) (this.value ^ (this.value >>> 32));
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final FixedLengthIdentifier other = (FixedLengthIdentifier) obj;
      return this.value == other.value;
    }
  }

  private enum RifEntryStatus {
    INITIAL,
    INTERIM,
    FINAL
  }

  private static class RifWriters {
    private final Map<RifEntryStatus, Map<Class, SynchronizedBBLineWriter>> allWriters;
    private final Path outputDir;

    public RifWriters(Path outputDir) {
      this.outputDir = outputDir;
      allWriters = Collections.synchronizedMap(new HashMap<>());
      for (RifEntryStatus status: RifEntryStatus.values()) {
        allWriters.put(status, Collections.synchronizedMap(new HashMap<>()));
      }
    }

    public <E extends Enum<E>> SynchronizedBBLineWriter<E> getWriter(Class<E> rifEnum,
            RifEntryStatus status) {
      return allWriters.get(status).get(rifEnum);
    }

    private <E extends Enum<E>> Path getFilePath(Class<E> enumClass, RifEntryStatus status) {
      return getFilePath(enumClass, status, "csv");
    }

    private <E extends Enum<E>> Path getFilePath(Class<E> enumClass, RifEntryStatus status,
            String ext) {
      String prefix = enumClass.getSimpleName().toLowerCase();
      String suffix = status == RifEntryStatus.INITIAL ? "" : "_" + status.toString().toLowerCase();
      String fileName = String.format("%s%s.%s", prefix, suffix, ext);
      return outputDir.resolve(fileName);
    }

    public synchronized <E extends Enum<E>> SynchronizedBBLineWriter<E> getOrCreateWriter(
            Class<E> enumClass) {
      return getOrCreateWriter(enumClass, RifEntryStatus.INITIAL);
    }

    public synchronized <E extends Enum<E>> SynchronizedBBLineWriter<E> getOrCreateWriter(
            Class<E> enumClass, RifEntryStatus status) {
      return getOrCreateWriter(enumClass, status, "csv", "|");
    }

    public synchronized <E extends Enum<E>> SynchronizedBBLineWriter<E> getOrCreateWriter(
            Class<E> enumClass, RifEntryStatus status, String ext, String separator) {
      SynchronizedBBLineWriter<E> writer = getWriter(enumClass, status);
      if (writer == null) {
        Path filePath = getFilePath(enumClass, status, ext);
        writer = new SynchronizedBBLineWriter<>(
                enumClass, filePath, separator);
        allWriters.get(status).put(enumClass, writer);
      }
      return writer;
    }

    public <E extends Enum<E>> void writeValues(Class<E> enumClass, Map<E, String> fieldValues)
            throws IOException {
      writeValues(enumClass, fieldValues, RifEntryStatus.INITIAL);
    }

    public <E extends Enum<E>> void writeValues(Class<E> enumClass, Map<E, String> fieldValues,
            RifEntryStatus status) throws IOException {
      getOrCreateWriter(enumClass, status).writeValues(fieldValues);
    }
  }
}
