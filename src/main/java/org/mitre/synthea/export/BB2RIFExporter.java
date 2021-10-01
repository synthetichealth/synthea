package org.mitre.synthea.export;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
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
  
  private BeneficiaryWriters beneficiaryWriters;
  private SynchronizedBBLineWriter beneficiaryHistory;
  private SynchronizedBBLineWriter outpatient;
  private SynchronizedBBLineWriter inpatient;
  private SynchronizedBBLineWriter carrier;
  private SynchronizedBBLineWriter prescription;
  private SynchronizedBBLineWriter dme;
  private SynchronizedBBLineWriter home;
  private SynchronizedBBLineWriter hospice;
  private SynchronizedBBLineWriter snf;
  private SynchronizedBBLineWriter npi;

  private static AtomicLong beneId =
      new AtomicLong(Config.getAsLong("exporter.bfd.bene_id_start", -1));
  private static AtomicLong claimId =
      new AtomicLong(Config.getAsLong("exporter.bfd.clm_id_start", -1));
  private static AtomicInteger claimGroupId =
      new AtomicInteger(Config.getAsInteger("exporter.bfd.clm_grp_id_start", -1));
  private static AtomicLong pdeId =
      new AtomicLong(Config.getAsLong("exporter.bfd.pde_id_start", -1));
  private static AtomicReference<MBI> mbi =
      new AtomicReference<>(MBI.parse(Config.get("exporter.bfd.mbi_start", "1S00-A00-AA00")));
  private static AtomicReference<HICN> hicn =
      new AtomicReference<>(HICN.parse(Config.get("exporter.bfd.hicn_start", "T00000000A")));
  private final PartDContractID[] partDContractIDs;
  private final CLIA[] cliaLabNumbers;

  private List<LinkedHashMap<String, String>> carrierLookup;
  private CodeMapper conditionCodeMapper;
  private CodeMapper medicationCodeMapper;
  private CodeMapper drgCodeMapper;
  private CodeMapper dmeCodeMapper;
  private CodeMapper hcpcsCodeMapper;
  
  private StateCodeMapper locationMapper;
  private StaticFieldConfig staticFieldConfig;

  private static final String BB2_BENE_ID = "BB2_BENE_ID";
  private static final String BB2_PARTD_CONTRACTS = "BB2_PARTD_CONTRACTS";
  private static final String BB2_HIC_ID = "BB2_HIC_ID";
  private static final String BB2_MBI = "BB2_MBI";
  
  /**
   * Day-Month-Year date format.
   */
  private static final SimpleDateFormat BB2_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

  /**
   * Get a date string in the format DD-MMM-YY from the given time stamp.
   */
  private static String bb2DateFromTimestamp(long time) {
    synchronized (BB2_DATE_FORMAT) {
      // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6231579
      return BB2_DATE_FORMAT.format(new Date(time));
    }
  }
  
  /**
   * Create the output folder and files. Write headers to each file.
   */
  private BB2RIFExporter() {
    partDContractIDs = initPartDContractIDs();
    cliaLabNumbers = initCliaLabNumbers();
    conditionCodeMapper = new CodeMapper("export/condition_code_map.json");
    medicationCodeMapper = new CodeMapper("export/medication_code_map.json");
    drgCodeMapper = new CodeMapper("export/drg_code_map.json");
    dmeCodeMapper = new CodeMapper("export/dme_code_map.json");
    hcpcsCodeMapper = new CodeMapper("export/hcpcs_code_map.json");
    locationMapper = new StateCodeMapper();
    try {
      String csv = Utilities.readResourceAndStripBOM("payers/carriers.csv");
      carrierLookup = SimpleCSV.parse(csv);
      staticFieldConfig = new StaticFieldConfig();
      prepareOutputFiles();
      for (String tsvIssue: staticFieldConfig.validateTSV()) {
        System.out.println(tsvIssue);
      }
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }
  
  private static PartDContractID[] initPartDContractIDs() {
    int numContracts = Config.getAsInteger("exporter.bfd.partd_contract_count",1);
    PartDContractID[] partDContractIDs = new PartDContractID[numContracts];
    PartDContractID contractID = PartDContractID.parse(
            Config.get("exporter.bfd.partd_contract_start", "Z0001"));
    for (int i = 0; i < numContracts; i++) {
      partDContractIDs[i] = contractID;
      contractID = contractID.next();
    }
    return partDContractIDs;
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
   * Create the output folder and files. Write headers to each file.
   */
  final void prepareOutputFiles() throws IOException {
    // Initialize output writers
    File output = Exporter.getOutputFolder("bfd", null);
    output.mkdirs();
    Path outputDirectory = output.toPath();
    
    beneficiaryWriters = new BeneficiaryWriters(outputDirectory);

    Path beneficiaryHistoryFile = outputDirectory.resolve("beneficiary_history.csv");
    beneficiaryHistory = new SynchronizedBBLineWriter(beneficiaryHistoryFile);
    beneficiaryHistory.writeHeader(BeneficiaryHistoryFields.class);

    Path outpatientFile = outputDirectory.resolve("outpatient.csv");
    outpatient = new SynchronizedBBLineWriter(outpatientFile);
    outpatient.writeHeader(OutpatientFields.class);

    Path inpatientFile = outputDirectory.resolve("inpatient.csv");
    inpatient = new SynchronizedBBLineWriter(inpatientFile);
    inpatient.writeHeader(InpatientFields.class);

    Path carrierFile = outputDirectory.resolve("carrier.csv");
    carrier = new SynchronizedBBLineWriter(carrierFile);
    carrier.writeHeader(CarrierFields.class);

    Path prescriptionFile = outputDirectory.resolve("prescription.csv");
    prescription = new SynchronizedBBLineWriter(prescriptionFile);
    prescription.writeHeader(PrescriptionFields.class);

    Path dmeFile = outputDirectory.resolve("dme.csv");
    dme = new SynchronizedBBLineWriter(dmeFile);
    dme.writeHeader(DMEFields.class);

    Path homeFile = outputDirectory.resolve("home.csv");
    home = new SynchronizedBBLineWriter(homeFile);
    home.writeHeader(HHAFields.class);

    Path hospiceFile = outputDirectory.resolve("hospice.csv");
    hospice = new SynchronizedBBLineWriter(hospiceFile);
    hospice.writeHeader(HospiceFields.class);

    Path snfFile = outputDirectory.resolve("snf.csv");
    snf = new SynchronizedBBLineWriter(snfFile);
    snf.writeHeader(SNFFields.class);

    Path npiFile = outputDirectory.resolve("npi.tsv");
    npi = new SynchronizedBBLineWriter(npiFile, "\t");
    npi.writeHeader(NPIFields.class);
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
    for (File file: beneficiaryWriters.getFiles()) {
      manifest.write(String.format("  <entry name=\"%s\" type=\"BENEFICIARY\"/>\n",
              file.getName()));
    }
    manifest.write(String.format("  <entry name=\"%s\" type=\"INPATIENT\"/>\n",
            inpatient.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"OUTPATIENT\"/>\n",
            outpatient.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"PDE\"/>\n",
            prescription.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"CARRIER\"/>\n",
            carrier.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"DME\"/>\n",
            dme.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"HHA\"/>\n",
            home.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"HOSPICE\"/>\n",
            hospice.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"SNF\"/>\n",
            snf.getFile().getName()));
    manifest.write(String.format("  <entry name=\"%s\" type=\"BENEFICIARY_HISTORY\"/>\n",
            beneficiaryHistory.getFile().getName()));
    manifest.write("</dataSetManifest>");
    Path manifestPath = output.toPath().resolve("manifest.xml");
    Exporter.appendToFile(manifestPath, manifest.toString());
  }

  /**
   * Export NPI writer with synthetic providers.
   * @throws IOException if something goes horribly wrong.
   */
  public void exportNPIs() throws IOException {
    HashMap<NPIFields, String> fieldValues = new HashMap<>();

    for (Provider h : Provider.getProviderList()) {

      // filter - exports only those organizations in use
      Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
      int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
          .mapToInt(ai -> ai.get()).sum();

      if (totalEncounters > 0) {
        // export organization
        fieldValues.clear();
        fieldValues.put(NPIFields.NPI, h.npi);
        fieldValues.put(NPIFields.ENTITY_TYPE_CODE, "2");
        fieldValues.put(NPIFields.EIN, "<UNAVAIL>");
        fieldValues.put(NPIFields.ORG_NAME, h.name);
        npi.writeValues(NPIFields.class, fieldValues);

        Map<String, ArrayList<Clinician>> clinicians = h.clinicianMap;
        for (String specialty : clinicians.keySet()) {
          ArrayList<Clinician> docs = clinicians.get(specialty);
          for (Clinician doc : docs) {
            if (doc.getEncounterCount() > 0) {
              // export each doc
              Map<String,Object> attributes = doc.getAttributes();
              fieldValues.clear();
              fieldValues.put(NPIFields.NPI, doc.npi);
              fieldValues.put(NPIFields.ENTITY_TYPE_CODE, "1");
              fieldValues.put(NPIFields.LAST_NAME,
                  attributes.get(Clinician.LAST_NAME).toString());
              fieldValues.put(NPIFields.FIRST_NAME,
                  attributes.get(Clinician.FIRST_NAME).toString());
              fieldValues.put(NPIFields.PREFIX,
                  attributes.get(Clinician.NAME_PREFIX).toString());
              fieldValues.put(NPIFields.CREDENTIALS, "M.D.");
              npi.writeValues(NPIFields.class, fieldValues);
            }
          }
        }
      }
    }
  }

  /**
   * Export a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  public void export(Person person, long stopTime) throws IOException {
    exportBeneficiary(person, stopTime);
    exportBeneficiaryHistory(person, stopTime);
    exportInpatient(person, stopTime);
    exportOutpatient(person, stopTime);
    exportCarrier(person, stopTime);
    exportPrescription(person, stopTime);
    exportDME(person, stopTime);
    exportHome(person, stopTime);
    exportHospice(person, stopTime);
    exportSNF(person, stopTime);
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
    long deathDate = person.attributes.get(Person.DEATHDATE) == null ? -1
            : (long) person.attributes.get(Person.DEATHDATE);

    // One entry for each year of history or until patient dies
    int yearsOfHistory = Config.getAsInteger("exporter.years_of_history");
    int endYear = Utilities.getYear(stopTime);
    if (deathDate != -1 && Utilities.getYear(deathDate) < endYear) {
      endYear = Utilities.getYear(deathDate); // stop after year in which patient dies
    }
    Map<Integer, PartDContractID> partDContracts = new HashMap<>();
    for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
      if (person.randInt(10) > 2) {
        // 30% chance of not enrolling in Part D
        // see https://www.kff.org/medicare/issue-brief/10-things-to-know-about-medicare-part-d-coverage-and-costs-in-2019/
        PartDContractID partDContractID = partDContractIDs[person.randInt(partDContractIDs.length)];
        partDContracts.put(year, partDContractID);
      }
    }
    person.attributes.put(BB2_PARTD_CONTRACTS, partDContracts);
    
    for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
      HashMap<BeneficiaryFields, String> fieldValues = new HashMap<>();
      staticFieldConfig.setValues(fieldValues, BeneficiaryFields.class, person);
      if (year > endYear - yearsOfHistory) {
        // The first year output is set via staticFieldConfig to "INSERT", subsequent years
        // need to be "UPDATE"
        fieldValues.put(BeneficiaryFields.DML_IND, "UPDATE");
      }

      fieldValues.put(BeneficiaryFields.RFRNC_YR, String.valueOf(year));
      int monthCount = year == endYear ? Utilities.getMonth(stopTime) : 12;
      String monthCountStr = String.valueOf(monthCount);
      fieldValues.put(BeneficiaryFields.A_MO_CNT, monthCountStr);
      fieldValues.put(BeneficiaryFields.B_MO_CNT, monthCountStr);
      fieldValues.put(BeneficiaryFields.BUYIN_MO_CNT, monthCountStr);
      fieldValues.put(BeneficiaryFields.RDS_MO_CNT, monthCountStr);
      fieldValues.put(BeneficiaryFields.PLAN_CVRG_MO_CNT, monthCountStr);
      fieldValues.put(BeneficiaryFields.BENE_ID, beneIdStr);
      fieldValues.put(BeneficiaryFields.BENE_CRNT_HIC_NUM, hicId);
      fieldValues.put(BeneficiaryFields.MBI_NUM, mbiStr);
      fieldValues.put(BeneficiaryFields.BENE_SEX_IDENT_CD,
              getBB2SexCode((String)person.attributes.get(Person.GENDER)));
      String zipCode = (String)person.attributes.get(Person.ZIP);
      fieldValues.put(BeneficiaryFields.BENE_COUNTY_CD,
              locationMapper.zipToCountyCode(zipCode));
      fieldValues.put(BeneficiaryFields.STATE_CODE,
              locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
      fieldValues.put(BeneficiaryFields.BENE_ZIP_CD,
              (String)person.attributes.get(Person.ZIP));
      fieldValues.put(BeneficiaryFields.BENE_RACE_CD,
              bb2RaceCode(
                      (String)person.attributes.get(Person.ETHNICITY),
                      (String)person.attributes.get(Person.RACE)));
      fieldValues.put(BeneficiaryFields.BENE_SRNM_NAME, 
              (String)person.attributes.get(Person.LAST_NAME));
      String givenName = (String)person.attributes.get(Person.FIRST_NAME);
      fieldValues.put(BeneficiaryFields.BENE_GVN_NAME, StringUtils.truncate(givenName, 15));
      long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
      fieldValues.put(BeneficiaryFields.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
      fieldValues.put(BeneficiaryFields.AGE, String.valueOf(ageAtEndOfYear(birthdate, year)));
      fieldValues.put(BeneficiaryFields.BENE_PTA_TRMNTN_CD, "0");
      fieldValues.put(BeneficiaryFields.BENE_PTB_TRMNTN_CD, "0");
      if (deathDate != -1) {
        // only add death date for years when it was (presumably) known. E.g. If we are outputting
        // record for 2005 and patient died in 2007 we don't include the death date.
        if (Utilities.getYear(deathDate) <= year) {
          fieldValues.put(BeneficiaryFields.DEATH_DT, bb2DateFromTimestamp(deathDate));
          fieldValues.put(BeneficiaryFields.BENE_PTA_TRMNTN_CD, "1");
          fieldValues.put(BeneficiaryFields.BENE_PTB_TRMNTN_CD, "1");
        }
      }
      PartDContractID partDContractID = partDContracts.get(year);
      if (partDContractID != null) {
        for (int i = 0; i < monthCount; i++) {
          fieldValues.put(beneficiaryPartDContractFields[i], partDContractID.toString());
        }
      }
      
      beneficiaryWriters.getWriter(year).writeValues(BeneficiaryFields.class, fieldValues);
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
   * Export a beneficiary history for single person. Assumes exportBeneficiary
   * was called first to set up various ID on person
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportBeneficiaryHistory(Person person, 
        long stopTime) throws IOException {
    HashMap<BeneficiaryHistoryFields, String> fieldValues = new HashMap<>();

    staticFieldConfig.setValues(fieldValues, BeneficiaryHistoryFields.class, person);

    String beneIdStr = (String)person.attributes.get(BB2_BENE_ID);
    fieldValues.put(BeneficiaryHistoryFields.BENE_ID, beneIdStr);
    String hicId = (String)person.attributes.get(BB2_HIC_ID);
    fieldValues.put(BeneficiaryHistoryFields.BENE_CRNT_HIC_NUM, hicId);
    String mbiStr = (String)person.attributes.get(BB2_MBI);
    fieldValues.put(BeneficiaryHistoryFields.MBI_NUM, mbiStr);
    fieldValues.put(BeneficiaryHistoryFields.BENE_SEX_IDENT_CD,
            getBB2SexCode((String)person.attributes.get(Person.GENDER)));
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    fieldValues.put(BeneficiaryHistoryFields.BENE_BIRTH_DT, bb2DateFromTimestamp(birthdate));
    String zipCode = (String)person.attributes.get(Person.ZIP);
    fieldValues.put(BeneficiaryHistoryFields.BENE_COUNTY_CD,
            locationMapper.zipToCountyCode(zipCode));
    fieldValues.put(BeneficiaryHistoryFields.STATE_CODE,
            locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
    fieldValues.put(BeneficiaryHistoryFields.BENE_ZIP_CD,
            (String)person.attributes.get(Person.ZIP));
    fieldValues.put(BeneficiaryHistoryFields.BENE_RACE_CD,
            bb2RaceCode(
                    (String)person.attributes.get(Person.ETHNICITY),
                    (String)person.attributes.get(Person.RACE)));
    fieldValues.put(BeneficiaryHistoryFields.BENE_SRNM_NAME, 
            (String)person.attributes.get(Person.LAST_NAME));
    fieldValues.put(BeneficiaryHistoryFields.BENE_GVN_NAME,
            (String)person.attributes.get(Person.FIRST_NAME));
    String terminationCode = (person.attributes.get(Person.DEATHDATE) == null) ? "0" : "1";
    fieldValues.put(BeneficiaryHistoryFields.BENE_PTA_TRMNTN_CD, terminationCode);
    fieldValues.put(BeneficiaryHistoryFields.BENE_PTB_TRMNTN_CD, terminationCode);
    beneficiaryHistory.writeValues(BeneficiaryHistoryFields.class, fieldValues);
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
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportOutpatient(Person person, long stopTime) 
        throws IOException {
    HashMap<OutpatientFields, String> fieldValues = new HashMap<>();

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      boolean isAmbulatory = encounter.type.equals(EncounterType.AMBULATORY.toString());
      boolean isOutpatient = encounter.type.equals(EncounterType.OUTPATIENT.toString());
      boolean isUrgent = encounter.type.equals(EncounterType.URGENTCARE.toString());
      boolean isWellness = encounter.type.equals(EncounterType.WELLNESS.toString());
      boolean isPrimary = (ProviderType.PRIMARY == encounter.provider.type);
      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

      if (isPrimary || !(isAmbulatory || isOutpatient || isUrgent || isWellness)) {
        continue;
      }
      
      staticFieldConfig.setValues(fieldValues, OutpatientFields.class, person);
      
      // The REQUIRED fields
      fieldValues.put(OutpatientFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(OutpatientFields.CLM_ID, "" + claimId);
      fieldValues.put(OutpatientFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(OutpatientFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(OutpatientFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(OutpatientFields.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(OutpatientFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(OutpatientFields.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(OutpatientFields.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(OutpatientFields.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(OutpatientFields.OP_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(OutpatientFields.CLM_PMT_AMT, String.format("%.2f",
              encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(OutpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(OutpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(OutpatientFields.PRVDR_STATE_CD,
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
      fieldValues.put(OutpatientFields.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(OutpatientFields.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OutpatientFields.CLM_OP_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OutpatientFields.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(OutpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(OutpatientFields.NCH_BENE_PTB_DDCTBL_AMT,
              String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(OutpatientFields.REV_CNTR_CASH_DDCTBL_AMT,
              String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(OutpatientFields.REV_CNTR_COINSRNC_WGE_ADJSTD_C,
              String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(OutpatientFields.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OutpatientFields.REV_CNTR_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(OutpatientFields.REV_CNTR_PTNT_RSPNSBLTY_PMT,
              String.format("%.2f",
                      encounter.claim.getDeductiblePaid() + encounter.claim.getCoinsurancePaid()));
      fieldValues.put(OutpatientFields.REV_CNTR_RDCD_COINSRNC_AMT,
              String.format("%.2f", encounter.claim.getCoinsurancePaid()));

      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(OutpatientFields.PRNCPAL_DGNS_CD, icdCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(), outpatientDxFields.length);
        for (int i = 0; i < smallest; i++) {
          OutpatientFields[] dxField = outpatientDxFields[i];
          fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
          fieldValues.put(dxField[1], "0"); // 0=ICD10
        }
        if (!fieldValues.containsKey(OutpatientFields.PRNCPAL_DGNS_CD)) {
          fieldValues.put(OutpatientFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
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
          int smallest = Math.min(mappableProcedures.size(), outpatientPxFields.length);
          for (int i = 0; i < smallest; i++) {
            OutpatientFields[] pxField = outpatientPxFields[i];
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

      synchronized (outpatient) {
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
            fieldValues.remove(OutpatientFields.REV_CNTR_NDC_QTY);
            fieldValues.remove(OutpatientFields.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(OutpatientFields.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(OutpatientFields.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(OutpatientFields.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(OutpatientFields.REV_CNTR_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(OutpatientFields.HCPCS_CD, hcpcsCode);
          fieldValues.put(OutpatientFields.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost)));
          fieldValues.put(OutpatientFields.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(OutpatientFields.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(OutpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          outpatient.writeValues(OutpatientFields.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(OutpatientFields.CLM_LINE_NUM, Integer.toString(claimLine));
          fieldValues.put(OutpatientFields.REV_CNTR_DT, bb2DateFromTimestamp(encounter.start));
          // 99241: "Office consultation for a new or established patient" 
          fieldValues.put(OutpatientFields.HCPCS_CD, "99241");
          outpatient.writeValues(OutpatientFields.class, fieldValues);
        }
      }
    }
  }
  
  /**
   * Export inpatient claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportInpatient(Person person, long stopTime) 
        throws IOException {
    HashMap<InpatientFields, String> fieldValues = new HashMap<>();

    boolean previousEmergency = false;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      boolean isInpatient = encounter.type.equals(EncounterType.INPATIENT.toString());
      boolean isEmergency = encounter.type.equals(EncounterType.EMERGENCY.toString());
      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

      if (!(isInpatient || isEmergency)) {
        previousEmergency = false;
        continue;
      }

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, InpatientFields.class, person);
      
      // The REQUIRED fields
      fieldValues.put(InpatientFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(InpatientFields.CLM_ID, "" + claimId);
      fieldValues.put(InpatientFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(InpatientFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(InpatientFields.CLM_ADMSN_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(InpatientFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(InpatientFields.NCH_BENE_DSCHRG_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(InpatientFields.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(InpatientFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(InpatientFields.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(InpatientFields.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(InpatientFields.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(InpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(InpatientFields.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(InpatientFields.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      if (encounter.ended) {
        field = "1"; // TODO 2=transfer if the next encounter is also inpatient
      } else {
        field = "30"; // the patient is still here
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
      }
      fieldValues.put(InpatientFields.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(InpatientFields.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (isEmergency) {
        field = "1"; // emergency
      } else if (previousEmergency) {
        field = "2"; // urgent
      } else {
        field = "3"; // elective
      }
      fieldValues.put(InpatientFields.CLM_IP_ADMSN_TYPE_CD, field);
      fieldValues.put(InpatientFields.NCH_BENE_IP_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(InpatientFields.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(InpatientFields.NCH_IP_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(InpatientFields.NCH_IP_TOT_DDCTN_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      fieldValues.put(InpatientFields.CLM_UTLZTN_DAY_CNT, "" + days);
      if (days > 60) {
        field = "" + (days - 60);
      } else {
        field = "0";
      }
      fieldValues.put(InpatientFields.BENE_TOT_COINSRNC_DAYS_CNT, field);
      if (days > 60) {
        field = "1"; // days outlier
      } else if (encounter.claim.getTotalClaimCost() > 100_000) {
        field = "2"; // cost outlier
      } else {
        field = "0"; // no outlier
      }
      fieldValues.put(InpatientFields.CLM_DRG_OUTLIER_STAY_CD, field);
      fieldValues.put(InpatientFields.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(InpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      // OPTIONAL FIELDS
      fieldValues.put(InpatientFields.RNDRNG_PHYSN_NPI, encounter.clinician.npi);

      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(InpatientFields.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(InpatientFields.ADMTG_DGNS_CD, icdCode);
          if (drgCodeMapper.canMap(icdCode)) {
            fieldValues.put(InpatientFields.CLM_DRG_CD, drgCodeMapper.map(icdCode, person));
          }
        }
      }
      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> presentOnAdmission = getDiagnosesCodes(person, encounter.start);
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(), inpatientDxFields.length);
        for (int i = 0; i < smallest; i++) {
          InpatientFields[] dxField = inpatientDxFields[i];
          String dxCode = mappedDiagnosisCodes.get(i);
          fieldValues.put(dxField[0], dxCode);
          fieldValues.put(dxField[1], "0"); // 0=ICD10
          String present = presentOnAdmission.contains(dxCode) ? "Y" : "N";
          fieldValues.put(dxField[2], present);
        }
        if (!fieldValues.containsKey(InpatientFields.PRNCPAL_DGNS_CD)) {
          fieldValues.put(InpatientFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
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
          int smallest = Math.min(mappableProcedures.size(), inpatientPxFields.length);
          for (int i = 0; i < smallest; i++) {
            InpatientFields[] pxField = inpatientPxFields[i];
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
      previousEmergency = isEmergency;

      synchronized (inpatient) {
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
            fieldValues.remove(InpatientFields.REV_CNTR_NDC_QTY);
            fieldValues.remove(InpatientFields.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(InpatientFields.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(InpatientFields.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(InpatientFields.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(InpatientFields.HCPCS_CD, hcpcsCode);
          fieldValues.put(InpatientFields.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(InpatientFields.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(InpatientFields.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(InpatientFields.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(InpatientFields.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(InpatientFields.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(InpatientFields.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          inpatient.writeValues(InpatientFields.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(InpatientFields.CLM_LINE_NUM, Integer.toString(claimLine));
          // HCPCS 99221: "Inpatient hospital visits: Initial and subsequent"
          fieldValues.put(InpatientFields.HCPCS_CD, "99221");
          inpatient.writeValues(InpatientFields.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export carrier claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportCarrier(Person person, long stopTime) throws IOException {
    HashMap<CarrierFields, String> fieldValues = new HashMap<>();

    double latestHemoglobin = 0;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (ProviderType.PRIMARY != encounter.provider.type) {
        continue;
      }

      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      staticFieldConfig.setValues(fieldValues, CarrierFields.class, person);
      fieldValues.put(CarrierFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));

      // The REQUIRED fields
      fieldValues.put(CarrierFields.CLM_ID, "" + claimId);
      fieldValues.put(CarrierFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(CarrierFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(CarrierFields.LINE_1ST_EXPNS_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(CarrierFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(CarrierFields.LINE_LAST_EXPNS_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(CarrierFields.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(CarrierFields.CARR_NUM,
              getCarrier(encounter.provider.state, CarrierFields.CARR_NUM));
      fieldValues.put(CarrierFields.CLM_PMT_AMT, 
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(CarrierFields.CARR_CLM_PRMRY_PYR_PD_AMT, "0");
      } else {
        fieldValues.put(CarrierFields.CARR_CLM_PRMRY_PYR_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(CarrierFields.NCH_CLM_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CarrierFields.NCH_CARR_CLM_SBMTD_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(CarrierFields.NCH_CARR_CLM_ALOWD_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(CarrierFields.CARR_CLM_CASH_DDCTBL_APLD_AMT,
              String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(CarrierFields.CARR_CLM_RFRNG_PIN_NUM, encounter.provider.id);
      fieldValues.put(CarrierFields.CARR_PRFRNG_PIN_NUM, encounter.provider.id);
      fieldValues.put(CarrierFields.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(CarrierFields.PRF_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(CarrierFields.RFR_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(CarrierFields.PRVDR_SPCLTY,
          ClinicianSpecialty.getCMSProviderSpecialtyCode(
              (String) encounter.clinician.attributes.get(Clinician.SPECIALTY)));
      fieldValues.put(CarrierFields.TAX_NUM,
              bb2TaxId((String)encounter.clinician.attributes.get(Person.IDENTIFIER_SSN)));
      fieldValues.put(CarrierFields.LINE_SRVC_CNT, "" + encounter.claim.items.size());
      fieldValues.put(CarrierFields.CARR_LINE_PRCNG_LCLTY_CD,
              getCarrier(encounter.provider.state, CarrierFields.CARR_LINE_PRCNG_LCLTY_CD));
      fieldValues.put(CarrierFields.LINE_NCH_PMT_AMT,
              String.format("%.2f", encounter.claim.getCoveredCost()));
      // length of encounter in minutes
      fieldValues.put(CarrierFields.CARR_LINE_MTUS_CNT,
              "" + ((encounter.stop - encounter.start) / (1000 * 60)));

      fieldValues.put(CarrierFields.LINE_HCT_HGB_RSLT_NUM,
              "" + latestHemoglobin);

      // OPTIONAL
      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          icdReasonCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(CarrierFields.PRNCPAL_DGNS_CD, icdReasonCode);
          fieldValues.put(CarrierFields.LINE_ICD_DGNS_CD, icdReasonCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty() && icdReasonCode == null) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(), carrierDxFields.length);
      for (int i = 0; i < smallest; i++) {
        CarrierFields[] dxField = carrierDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(CarrierFields.PRNCPAL_DGNS_CD)) {
        fieldValues.put(CarrierFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }
      
      synchronized (carrier) {
        int lineNum = 1;
        CLIA cliaLab = cliaLabNumbers[person.randInt(cliaLabNumbers.length)];
        for (ClaimEntry lineItem : encounter.claim.items) {
          fieldValues.put(CarrierFields.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", lineItem.deductible));
          fieldValues.put(CarrierFields.LINE_COINSRNC_AMT,
                  String.format("%.2f", lineItem.coinsurance));
          fieldValues.put(CarrierFields.LINE_BENE_PMT_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          fieldValues.put(CarrierFields.LINE_PRVDR_PMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(CarrierFields.LINE_SBMTD_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(CarrierFields.LINE_ALOWD_CHRG_AMT,
              String.format("%.2f", lineItem.cost - lineItem.adjustment));

          // If this item is a lab report, add the number of the clinical lab...
          if  (lineItem.entry instanceof HealthRecord.Report) {
            fieldValues.put(CarrierFields.CARR_LINE_CLIA_LAB_NUM, cliaLab.toString());
          }

          // set the line number and write out field values
          fieldValues.put(CarrierFields.LINE_NUM, Integer.toString(lineNum++));
          carrier.writeValues(CarrierFields.class, fieldValues);
        }

        if (lineNum == 1) {
          // If lineNum still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(CarrierFields.LINE_NUM, Integer.toString(lineNum));
          fieldValues.put(CarrierFields.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", encounter.claim.getDeductiblePaid()));
          fieldValues.put(CarrierFields.LINE_COINSRNC_AMT,
                  String.format("%.2f", encounter.claim.getCoinsurancePaid()));
          fieldValues.put(CarrierFields.LINE_SBMTD_CHRG_AMT,
                  String.format("%.2f", encounter.claim.getTotalClaimCost()));
          fieldValues.put(CarrierFields.LINE_ALOWD_CHRG_AMT,
                  String.format("%.2f", encounter.claim.getCoveredCost()));
          fieldValues.put(CarrierFields.LINE_PRVDR_PMT_AMT,
                  String.format("%.2f", encounter.claim.getCoveredCost()));
          fieldValues.put(CarrierFields.LINE_BENE_PMT_AMT,
                  String.format("%.2f", encounter.claim.getPatientCost()));
          carrier.writeValues(CarrierFields.class, fieldValues);
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
  
  private String getCarrier(String state, CarrierFields column) {
    for (LinkedHashMap<String, String> row : carrierLookup) {
      if (row.get("STATE").equals(state) || row.get("STATE_CODE").equals(state)) {
        return row.get(column.toString());
      }
    }
    return "0";
  }

  /**
   * Export prescription claims details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportPrescription(Person person, long stopTime) 
        throws IOException {
    Map<Integer, PartDContractID> partDContracts = 
            (Map<Integer, PartDContractID>) person.attributes.get(BB2_PARTD_CONTRACTS);
    HashMap<PrescriptionFields, String> fieldValues = new HashMap<>();
    HashMap<String, Integer> fillNum = new HashMap<>();
    double costs = 0;
    int costYear = 0;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      PartDContractID partDContractID = partDContracts.get(Utilities.getYear(encounter.start));
      if (partDContractID == null) {
        continue; // skip medications if patient isn't enrolled in Part D
      }
      for (Medication medication : encounter.medications) {
        if (!medicationCodeMapper.canMap(medication.codes.get(0).code)) {
          continue; // skip codes that can't be mapped to NDC
        }

        long pdeId = BB2RIFExporter.pdeId.getAndDecrement();
        int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

        fieldValues.clear();
        staticFieldConfig.setValues(fieldValues, PrescriptionFields.class, person);

        // The REQUIRED fields
        fieldValues.put(PrescriptionFields.PDE_ID, "" + pdeId);
        fieldValues.put(PrescriptionFields.CLM_GRP_ID, "" + claimGroupId);
        fieldValues.put(PrescriptionFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
        fieldValues.put(PrescriptionFields.SRVC_DT, bb2DateFromTimestamp(encounter.start));
        fieldValues.put(PrescriptionFields.SRVC_PRVDR_ID, encounter.provider.id);
        fieldValues.put(PrescriptionFields.PRSCRBR_ID,
            "" + (9_999_999_999L - encounter.clinician.identifier));
        fieldValues.put(PrescriptionFields.RX_SRVC_RFRNC_NUM, "" + pdeId);
        fieldValues.put(PrescriptionFields.PROD_SRVC_ID, 
                medicationCodeMapper.map(medication.codes.get(0).code, person));
        // The following field was replaced by the PartD contract ID, leaving this here for now
        // until this is validated
        // H=hmo, R=ppo, S=stand-alone, E=employer direct, X=limited income
        // fieldValues.put(PrescriptionFields.PLAN_CNTRCT_REC_ID,
        //     ("R" + Math.abs(
        //         UUID.fromString(medication.claim.payer.uuid)
        //         .getMostSignificantBits())).substring(0, 5));
        fieldValues.put(PrescriptionFields.PLAN_CNTRCT_REC_ID, partDContractID.toString());
        fieldValues.put(PrescriptionFields.DAW_PROD_SLCTN_CD, "" + (int) person.rand(0, 9));
        fieldValues.put(PrescriptionFields.QTY_DSPNSD_NUM, "" + getQuantity(medication, stopTime));
        fieldValues.put(PrescriptionFields.DAYS_SUPLY_NUM, "" + getDays(medication, stopTime));
        Integer fill = 1;
        if (fillNum.containsKey(medication.codes.get(0).code)) {
          fill = 1 + fillNum.get(medication.codes.get(0).code);
        }
        fillNum.put(medication.codes.get(0).code, fill);
        fieldValues.put(PrescriptionFields.FILL_NUM, "" + fill);
        int year = Utilities.getYear(medication.start);
        if (year != costYear) {
          costYear = year;
          costs = 0;
        }
        costs += medication.claim.getPatientCost();
        if (costs <= 4550.00) {
          fieldValues.put(PrescriptionFields.GDC_BLW_OOPT_AMT, String.format("%.2f", costs));
          fieldValues.put(PrescriptionFields.GDC_ABV_OOPT_AMT, "0");
        } else {
          fieldValues.put(PrescriptionFields.GDC_BLW_OOPT_AMT, "4550.00");
          fieldValues.put(PrescriptionFields.GDC_ABV_OOPT_AMT,
                  String.format("%.2f", (costs - 4550)));
        }
        fieldValues.put(PrescriptionFields.PTNT_PAY_AMT, 
                String.format("%.2f", medication.claim.getPatientCost()));
        fieldValues.put(PrescriptionFields.CVRD_D_PLAN_PD_AMT,
            String.format("%.2f", medication.claim.getCoveredCost()));
        fieldValues.put(PrescriptionFields.NCVRD_PLAN_PD_AMT,
            String.format("%.2f", medication.claim.getPatientCost()));
        fieldValues.put(PrescriptionFields.TOT_RX_CST_AMT,
            String.format("%.2f", medication.claim.getTotalClaimCost()));
        fieldValues.put(PrescriptionFields.PHRMCY_SRVC_TYPE_CD, "0" + (int) person.rand(1, 8));
        fieldValues.put(PrescriptionFields.PD_DT, bb2DateFromTimestamp(encounter.start));
        // 00=not specified, 01=home, 02=SNF, 03=long-term, 11=hospice, 14=homeless
        if (person.attributes.containsKey("homeless")
            && ((Boolean) person.attributes.get("homeless") == true)) {
          fieldValues.put(PrescriptionFields.PTNT_RSDNC_CD, "14");
        } else {
          fieldValues.put(PrescriptionFields.PTNT_RSDNC_CD, "01");
        }

        prescription.writeValues(PrescriptionFields.class, fieldValues);
      }
    }
  }

  /**
   * Export DME details for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportDME(Person person, long stopTime) 
        throws IOException {
    HashMap<DMEFields, String> fieldValues = new HashMap<>();

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();
      double latestHemoglobin = 0;
      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, DMEFields.class, person);

      // complex fields that could not easily be set using cms_field_values.tsv
      fieldValues.put(DMEFields.CLM_ID, "" + claimId);
      fieldValues.put(DMEFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(DMEFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(DMEFields.LINE_HCT_HGB_RSLT_NUM, "" + latestHemoglobin);
      fieldValues.put(DMEFields.CARR_NUM,
              getCarrier(encounter.provider.state, CarrierFields.CARR_NUM));
      fieldValues.put(DMEFields.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(DMEFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(DMEFields.PRVDR_NPI, encounter.provider.npi);
      fieldValues.put(DMEFields.PRVDR_SPCLTY,
          ClinicianSpecialty.getCMSProviderSpecialtyCode(
              (String) encounter.clinician.attributes.get(Clinician.SPECIALTY)));
      fieldValues.put(DMEFields.PRVDR_STATE_CD,
              locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(DMEFields.TAX_NUM,
              bb2TaxId((String)encounter.clinician.attributes.get(Person.IDENTIFIER_SSN)));
      fieldValues.put(DMEFields.DMERC_LINE_PRCNG_STATE_CD,
              locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
      fieldValues.put(DMEFields.LINE_1ST_EXPNS_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(DMEFields.LINE_LAST_EXPNS_DT, bb2DateFromTimestamp(encounter.stop));

      // OPTIONAL
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(DMEFields.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(DMEFields.LINE_ICD_DGNS_CD, icdCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(), dmeDxFields.length);
      for (int i = 0; i < smallest; i++) {
        DMEFields[] dxField = dmeDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(DMEFields.PRNCPAL_DGNS_CD)) {
        fieldValues.put(DMEFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        fieldValues.put(DMEFields.LINE_ICD_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      synchronized (dme) {
        int lineNum = 1;
        for (ClaimEntry lineItem : encounter.claim.items) {
          if (!(lineItem.entry instanceof Device)) {
            continue;
          }
          Device device = (Device)lineItem.entry;
          if (!dmeCodeMapper.canMap(device.codes.get(0).code)) {
            System.err.println(" *** Possibly Missing DME Code: " + device.codes.get(0).code
                    + " " + device.codes.get(0).display);
            continue;
          }

          fieldValues.put(DMEFields.CLM_FROM_DT, bb2DateFromTimestamp(device.start));
          fieldValues.put(DMEFields.CLM_THRU_DT, bb2DateFromTimestamp(device.start));
          fieldValues.put(DMEFields.HCPCS_CD,
                  dmeCodeMapper.map(device.codes.get(0).code, person));
          fieldValues.put(DMEFields.LINE_CMS_TYPE_SRVC_CD,
                  dmeCodeMapper.map(device.codes.get(0).code,
                          DMEFields.LINE_CMS_TYPE_SRVC_CD.toString().toLowerCase(),
                          person));
          fieldValues.put(DMEFields.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", lineItem.deductible));
          fieldValues.put(DMEFields.LINE_COINSRNC_AMT,
                  String.format("%.2f", lineItem.coinsurance));
          fieldValues.put(DMEFields.LINE_BENE_PMT_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          fieldValues.put(DMEFields.LINE_PRVDR_PMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(DMEFields.LINE_SBMTD_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(DMEFields.LINE_ALOWD_CHRG_AMT,
              String.format("%.2f", lineItem.cost - lineItem.adjustment));


          // set the line number and write out field values
          fieldValues.put(DMEFields.LINE_NUM, Integer.toString(lineNum++));
          dme.writeValues(DMEFields.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export Home Health Agency visits for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportHome(Person person, long stopTime) throws IOException {
    HashMap<HHAFields, String> fieldValues = new HashMap<>();
    int homeVisits = 0;
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (!encounter.type.equals(EncounterType.HOME.toString())) {
        continue;
      }

      homeVisits += 1;
      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, HHAFields.class, person);

      // The REQUIRED fields
      fieldValues.put(HHAFields.BENE_ID,  (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(HHAFields.CLM_ID, "" + claimId);
      fieldValues.put(HHAFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(HHAFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HHAFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(HHAFields.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(HHAFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(HHAFields.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(HHAFields.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(HHAFields.RNDRNG_PHYSN_NPI, encounter.clinician.npi);

      fieldValues.put(HHAFields.CLM_PMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(HHAFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(HHAFields.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(HHAFields.PRVDR_STATE_CD,
          locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(HHAFields.CLM_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(HHAFields.CLM_HHA_TOT_VISIT_CNT, "" + homeVisits);
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(HHAFields.REV_CNTR_UNIT_CNT, "" + days);
      fieldValues.put(HHAFields.REV_CNTR_RATE_AMT,
          String.format("%.2f", (encounter.claim.getTotalClaimCost() / days)));
      fieldValues.put(HHAFields.REV_CNTR_PMT_AMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(HHAFields.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(HHAFields.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(HHAFields.PRNCPAL_DGNS_CD, icdCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(), homeDxFields.length);
      for (int i = 0; i < smallest; i++) {
        HHAFields[] dxField = homeDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(HHAFields.PRNCPAL_DGNS_CD)) {
        fieldValues.put(HHAFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      synchronized (home) {
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
            fieldValues.remove(HHAFields.REV_CNTR_NDC_QTY);
            fieldValues.remove(HHAFields.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(HHAFields.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(HHAFields.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(HHAFields.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(HHAFields.REV_CNTR_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(HHAFields.HCPCS_CD, hcpcsCode);
          fieldValues.put(HHAFields.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(HHAFields.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(HHAFields.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(HHAFields.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(HHAFields.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(HHAFields.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(HHAFields.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(HHAFields.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          home.writeValues(HHAFields.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(HHAFields.CLM_LINE_NUM, Integer.toString(claimLine));
          fieldValues.put(HHAFields.REV_CNTR_DT, bb2DateFromTimestamp(encounter.start));
          fieldValues.put(HHAFields.HCPCS_CD, "T1021"); // home health visit
          home.writeValues(HHAFields.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export Home Health Agency visits for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportHospice(Person person, long stopTime) throws IOException {
    HashMap<HospiceFields, String> fieldValues = new HashMap<>();
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (!encounter.type.equals(EncounterType.HOSPICE.toString())) {
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
      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, HospiceFields.class, person);

      fieldValues.put(HospiceFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(HospiceFields.CLM_ID, "" + claimId);
      fieldValues.put(HospiceFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(HospiceFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HospiceFields.CLM_HOSPC_START_DT_ID, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(HospiceFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(HospiceFields.NCH_WKLY_PROC_DT,
              bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(HospiceFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(HospiceFields.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(HospiceFields.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(HospiceFields.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(HospiceFields.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(HospiceFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(HospiceFields.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(HospiceFields.PRVDR_STATE_CD,
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
      fieldValues.put(HospiceFields.PTNT_DSCHRG_STUS_CD, dischargeStatus);
      fieldValues.put(HospiceFields.NCH_PTNT_STATUS_IND_CD, patientStatus);
      if (dischargeDate != null) {
        fieldValues.put(HospiceFields.NCH_BENE_DSCHRG_DT, dischargeDate);
      }
      fieldValues.put(HospiceFields.CLM_TOT_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(HospiceFields.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      fieldValues.put(HospiceFields.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(HospiceFields.REV_CNTR_PMT_AMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));

      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(HospiceFields.PRNCPAL_DGNS_CD, icdCode);
        }
      }

      int smallest = Math.min(mappedDiagnosisCodes.size(), hospiceDxFields.length);
      for (int i = 0; i < smallest; i++) {
        HospiceFields[] dxField = hospiceDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(HospiceFields.PRNCPAL_DGNS_CD)) {
        fieldValues.put(HospiceFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(HospiceFields.CLM_UTLZTN_DAY_CNT, "" + days);
      int coinDays = days -  21; // first 21 days no coinsurance
      if (coinDays < 0) {
        coinDays = 0;
      }
      fieldValues.put(HospiceFields.REV_CNTR_UNIT_CNT, "" + days);
      fieldValues.put(HospiceFields.REV_CNTR_RATE_AMT,
          String.format("%.2f", (encounter.claim.getTotalClaimCost() / days)));

      synchronized (hospice) {
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
            fieldValues.remove(HospiceFields.REV_CNTR_NDC_QTY);
            fieldValues.remove(HospiceFields.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(HospiceFields.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(HospiceFields.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(HospiceFields.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(HospiceFields.REV_CNTR_DT, bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(HospiceFields.HCPCS_CD, hcpcsCode);
          fieldValues.put(HospiceFields.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(HospiceFields.REV_CNTR_PMT_AMT_AMT,
              String.format("%.2f", lineItem.coinsurance + lineItem.payer));
          fieldValues.put(HospiceFields.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(HospiceFields.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(HospiceFields.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(HospiceFields.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(HospiceFields.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(HospiceFields.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          hospice.writeValues(HospiceFields.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(HospiceFields.CLM_LINE_NUM, Integer.toString(claimLine));
          fieldValues.put(HospiceFields.REV_CNTR_DT, bb2DateFromTimestamp(encounter.start));
          fieldValues.put(HospiceFields.HCPCS_CD, "S9126"); // hospice per diem
          hospice.writeValues(HospiceFields.class, fieldValues);
        }
      }
    }
  }

  /**
   * Export Home Health Agency visits for a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @throws IOException if something goes wrong
   */
  private void exportSNF(Person person, long stopTime) throws IOException {
    HashMap<SNFFields, String> fieldValues = new HashMap<>();
    boolean previousEmergency;
    boolean previousUrgent;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      previousEmergency = encounter.type.equals(EncounterType.EMERGENCY.toString());
      previousUrgent = encounter.type.equals(EncounterType.URGENTCARE.toString());

      if (!encounter.type.equals(EncounterType.SNF.toString())) {
        continue;
      }
      long claimId = BB2RIFExporter.claimId.getAndDecrement();
      int claimGroupId = BB2RIFExporter.claimGroupId.getAndDecrement();

      fieldValues.clear();
      staticFieldConfig.setValues(fieldValues, SNFFields.class, person);

      // The REQUIRED Fields
      fieldValues.put(SNFFields.BENE_ID, (String) person.attributes.get(BB2_BENE_ID));
      fieldValues.put(SNFFields.CLM_ID, "" + claimId);
      fieldValues.put(SNFFields.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(SNFFields.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(SNFFields.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(SNFFields.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(SNFFields.PRVDR_NUM, encounter.provider.id);
      fieldValues.put(SNFFields.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(SNFFields.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(SNFFields.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
      
      fieldValues.put(SNFFields.CLM_PMT_AMT,
          String.format("%.2f", encounter.claim.getCoveredCost()));
      if (encounter.claim.payer == Payer.getGovernmentPayer("Medicare")) {
        fieldValues.put(SNFFields.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(SNFFields.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getCoveredCost()));
      }
      fieldValues.put(SNFFields.PRVDR_STATE_CD,
          locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(SNFFields.CLM_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (previousEmergency) {
        fieldValues.put(SNFFields.CLM_IP_ADMSN_TYPE_CD, "1");
      } else if (previousUrgent) {
        fieldValues.put(SNFFields.CLM_IP_ADMSN_TYPE_CD, "2");
      } else {
        fieldValues.put(SNFFields.CLM_IP_ADMSN_TYPE_CD, "3");
      }
      fieldValues.put(SNFFields.NCH_BENE_IP_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()));
      fieldValues.put(SNFFields.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          String.format("%.2f", encounter.claim.getCoinsurancePaid()));
      fieldValues.put(SNFFields.NCH_IP_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));
      fieldValues.put(SNFFields.NCH_IP_TOT_DDCTN_AMT,
          String.format("%.2f", encounter.claim.getDeductiblePaid()
              + encounter.claim.getCoinsurancePaid()));
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(SNFFields.CLM_UTLZTN_DAY_CNT, "" + days);
      int coinDays = days -  21; // first 21 days no coinsurance
      if (coinDays < 0) {
        coinDays = 0;
      }
      fieldValues.put(SNFFields.BENE_TOT_COINSRNC_DAYS_CNT, "" + coinDays);
      fieldValues.put(SNFFields.REV_CNTR_UNIT_CNT, "" + days);
      fieldValues.put(SNFFields.REV_CNTR_RATE_AMT,
          String.format("%.2f", (encounter.claim.getTotalClaimCost() / days)));
      fieldValues.put(SNFFields.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(SNFFields.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getPatientCost()));

      // OPTIONAL CODES
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(SNFFields.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(SNFFields.ADMTG_DGNS_CD, icdCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(), snfDxFields.length);
        for (int i = 0; i < smallest; i++) {
          SNFFields[] dxField = snfDxFields[i];
          fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
          fieldValues.put(dxField[1], "0"); // 0=ICD10
        }
        if (!fieldValues.containsKey(SNFFields.PRNCPAL_DGNS_CD)) {
          fieldValues.put(SNFFields.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
          fieldValues.put(SNFFields.ADMTG_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
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
          int smallest = Math.min(mappableProcedures.size(), snfPxFields.length);
          for (int i = 0; i < smallest; i++) {
            SNFFields[] pxField = snfPxFields[i];
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

      synchronized (snf) {
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
            fieldValues.remove(SNFFields.REV_CNTR_NDC_QTY);
            fieldValues.remove(SNFFields.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              fieldValues.put(SNFFields.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(SNFFields.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }
          if (hcpcsCode == null) {
            continue;
          }

          fieldValues.put(SNFFields.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(SNFFields.HCPCS_CD, hcpcsCode);
          fieldValues.put(SNFFields.REV_CNTR_RATE_AMT,
              String.format("%.2f", (lineItem.cost / Integer.max(1, days))));
          fieldValues.put(SNFFields.REV_CNTR_TOT_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(SNFFields.REV_CNTR_NCVRD_CHRG_AMT,
              String.format("%.2f", lineItem.copay + lineItem.deductible + lineItem.pocket));
          if (lineItem.pocket == 0 && lineItem.deductible == 0) {
            // Not subject to deductible or coinsurance
            fieldValues.put(SNFFields.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
          } else if (lineItem.pocket > 0 && lineItem.deductible > 0) {
            // Subject to deductible and coinsurance
            fieldValues.put(SNFFields.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
          } else if (lineItem.pocket == 0) {
            // Not subject to deductible
            fieldValues.put(SNFFields.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
          } else {
            // Not subject to coinsurance
            fieldValues.put(SNFFields.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
          }
          snf.writeValues(SNFFields.class, fieldValues);
        }

        if (claimLine == 1) {
          // If claimLine still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(SNFFields.CLM_LINE_NUM, Integer.toString(claimLine));
          // G0299: “direct skilled nursing services of a registered nurse (RN) in the home health
          // or hospice setting”
          fieldValues.put(SNFFields.HCPCS_CD, "G0299");
          snf.writeValues(SNFFields.class, fieldValues);
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

  private int getQuantity(Medication medication, long stopTime) {
    double amountPerDay = 1;
    double days = getDays(medication, stopTime);

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

    return (int) (amountPerDay * days);
  }

  private int getDays(Medication medication, long stopTime) {
    double days = 1;
    long stop = medication.stop;
    if (stop == 0L) {
      stop = stopTime;
    }
    long medDuration = stop - medication.start;
    days = (double) (medDuration / (1000 * 60 * 60 * 24));

    if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("duration")) {
      JsonObject duration = medication.prescriptionDetails.getAsJsonObject("duration");
      long quantity = duration.get("quantity").getAsLong();
      String unit = duration.get("unit").getAsString();
      long durationTime = Utilities.convertTime(unit, quantity);
      double durationTimeInDays = (double) (durationTime / (1000 * 60 * 60 * 24));
      if (durationTimeInDays > days) {
        days = durationTimeInDays;
      }
    }
    return (int) days;
  }

  /**
   * Utility class for converting state names and abbreviations to provider state codes.
   */
  class StateCodeMapper {
    private final HashMap<String, String> providerStateCodes;
    private Map<String, String> stateToAbbrev = this.buildStateAbbrevTable();
    private final Map<String, String> abbrevToState;
    private final HashMap<String, String> ssaTable;

    public StateCodeMapper() {
      this.providerStateCodes = this.buildProviderStateTable();
      this.stateToAbbrev = this.buildStateAbbrevTable();
      // support two-way conversion between state name and abbreviations
      this.abbrevToState = new HashMap<>();
      for (Map.Entry<String, String> entry : stateToAbbrev.entrySet()) {
        this.abbrevToState.put(entry.getValue(), entry.getKey());
      }
      this.ssaTable = buildSSATable();
    }

    /**
     * Return state code for a given state.
     * @param state (either state name or abbreviation)
     * @return 2-digit state code
     */
    String getStateCode(String state) {
      if (state.length() == 2) {
        state = this.changeStateFormat(state);
      } else {
        state = this.capitalizeWords(state);
      }
      String res = this.providerStateCodes.getOrDefault(state, "NONE");
      return res;
    }
    
    /**
     * Switch between state name and abbreviation. If state is abbreviation, will return name,
     * and vice versa
     * @param state abbreviation or name of state
     * @return
     */
    private String changeStateFormat(String state) {
      if (state.length() == 2) {
        return this.abbrevToState.getOrDefault(state.toUpperCase(), null);
      } else {
        String stateClean = this.capitalizeWords(state.toLowerCase());
        return this.stateToAbbrev.getOrDefault(stateClean, null);
      }
    }

    private Map<String, String> buildStateAbbrevTable() {
      Map<String, String> states = new HashMap<String, String>();
      states.put("Alabama","AL");
      states.put("Alaska","AK");
      states.put("Alberta","AB");
      states.put("American Samoa","AS");
      states.put("Arizona","AZ");
      states.put("Arkansas","AR");
      states.put("Armed Forces (AE)","AE");
      states.put("Armed Forces Americas","AA");
      states.put("Armed Forces Pacific","AP");
      states.put("British Columbia","BC");
      states.put("California","CA");
      states.put("Colorado","CO");
      states.put("Connecticut","CT");
      states.put("Delaware","DE");
      states.put("District Of Columbia","DC");
      states.put("Florida","FL");
      states.put("Georgia","GA");
      states.put("Guam","GU");
      states.put("Hawaii","HI");
      states.put("Idaho","ID");
      states.put("Illinois","IL");
      states.put("Indiana","IN");
      states.put("Iowa","IA");
      states.put("Kansas","KS");
      states.put("Kentucky","KY");
      states.put("Louisiana","LA");
      states.put("Maine","ME");
      states.put("Manitoba","MB");
      states.put("Maryland","MD");
      states.put("Massachusetts","MA");
      states.put("Michigan","MI");
      states.put("Minnesota","MN");
      states.put("Mississippi","MS");
      states.put("Missouri","MO");
      states.put("Montana","MT");
      states.put("Nebraska","NE");
      states.put("Nevada","NV");
      states.put("New Brunswick","NB");
      states.put("New Hampshire","NH");
      states.put("New Jersey","NJ");
      states.put("New Mexico","NM");
      states.put("New York","NY");
      states.put("Newfoundland","NF");
      states.put("North Carolina","NC");
      states.put("North Dakota","ND");
      states.put("Northwest Territories","NT");
      states.put("Nova Scotia","NS");
      states.put("Nunavut","NU");
      states.put("Ohio","OH");
      states.put("Oklahoma","OK");
      states.put("Ontario","ON");
      states.put("Oregon","OR");
      states.put("Pennsylvania","PA");
      states.put("Prince Edward Island","PE");
      states.put("Puerto Rico","PR");
      states.put("Quebec","QC");
      states.put("Rhode Island","RI");
      states.put("Saskatchewan","SK");
      states.put("South Carolina","SC");
      states.put("South Dakota","SD");
      states.put("Tennessee","TN");
      states.put("Texas","TX");
      states.put("Utah","UT");
      states.put("Vermont","VT");
      states.put("Virgin Islands","VI");
      states.put("Virginia","VA");
      states.put("Washington","WA");
      states.put("West Virginia","WV");
      states.put("Wisconsin","WI");
      states.put("Wyoming","WY");
      states.put("Yukon Territory","YT");
      return states;
    }
    
    private  HashMap<String, String> buildProviderStateTable() {
      HashMap<String, String> providerStateCode = new HashMap<String, String>();
      providerStateCode.put("Alabama", "01");
      providerStateCode.put("Alaska", "02");
      providerStateCode.put("Arizona", "03");
      providerStateCode.put("Arkansas", "04");
      providerStateCode.put("California", "05");
      providerStateCode.put("Colorado", "06");
      providerStateCode.put("Connecticut", "07");
      providerStateCode.put("Delaware", "08");
      providerStateCode.put("District of Columbia", "09");
      providerStateCode.put("Florida", "10");
      providerStateCode.put("Georgia", "11");
      providerStateCode.put("Hawaii", "12");
      providerStateCode.put("Idaho", "13");
      providerStateCode.put("Illinois", "14");
      providerStateCode.put("Indiana", "15");
      providerStateCode.put("Iowa", "16");
      providerStateCode.put("Kansas", "17");
      providerStateCode.put("Kentucky", "18");
      providerStateCode.put("Louisiana", "19");
      providerStateCode.put("Maine", "20");
      providerStateCode.put("Maryland", "21");
      providerStateCode.put("Massachusetts", "22");
      providerStateCode.put("Michigan", "23");
      providerStateCode.put("Minnesota", "24");
      providerStateCode.put("Mississippi", "25");
      providerStateCode.put("Missouri", "26");
      providerStateCode.put("Montana", "27");
      providerStateCode.put("Nebraska", "28");
      providerStateCode.put("Nevada", "29");
      providerStateCode.put("New Hampshire", "30");
      providerStateCode.put("New Jersey", "31");
      providerStateCode.put("New Mexico", "32");
      providerStateCode.put("New York", "33");
      providerStateCode.put("North Carolina", "34");
      providerStateCode.put("North Dakota", "35");
      providerStateCode.put("Ohio", "36");
      providerStateCode.put("Oklahoma", "37");
      providerStateCode.put("Oregon", "38");
      providerStateCode.put("Pennsylvania", "39");
      providerStateCode.put("Puerto Rico", "40");
      providerStateCode.put("Rhode Island", "41");
      providerStateCode.put("South Carolina", "42");
      providerStateCode.put("South Dakota", "43");
      providerStateCode.put("Tennessee", "44");
      providerStateCode.put("Texas", "45");
      providerStateCode.put("Utah", "46");
      providerStateCode.put("Vermont", "47");
      providerStateCode.put("Virgin Islands", "48");
      providerStateCode.put("Virginia", "49");
      providerStateCode.put("Washington", "50");
      providerStateCode.put("West Virginia", "51");
      providerStateCode.put("Wisconsin", "52");
      providerStateCode.put("Wyoming", "53");
      providerStateCode.put("Africa", "54");
      providerStateCode.put("California", "55");
      providerStateCode.put("Canada & Islands", "56");
      providerStateCode.put("Central America and West Indies", "57");
      providerStateCode.put("Europe", "58");
      providerStateCode.put("Mexico", "59");
      providerStateCode.put("Oceania", "60");
      providerStateCode.put("Philippines", "61");
      providerStateCode.put("South America", "62");
      providerStateCode.put("U.S. Possessions", "63");
      providerStateCode.put("American Samoa", "64");
      providerStateCode.put("Guam", "65");
      providerStateCode.put("Commonwealth of the Northern Marianas Islands", "66");
      return providerStateCode;
    }

    /**
     * Get the SSA county code for a given zipcode. Will eventually use countyname, but wanted
     * to use a unique key
     * @param zipcode the ZIP
     * @return
     */
    private String zipToCountyCode(String zipcode) {
      // TODO: fix this. Currently hard-coding default because required field, but will
      // eventually add name-based matching as fallback
      return ssaTable.getOrDefault(zipcode, "22090");
    }

    private HashMap<String, String> buildSSATable() {
      HashMap<String, String> ssaTable = new HashMap<String, String>();
      List<LinkedHashMap<String, String>> csvData;

      try {
        String csv = Utilities.readResourceAndStripBOM("geography/fipscodes.csv");
        csvData = SimpleCSV.parse(csv);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      for (LinkedHashMap<String, String> row : csvData) {
        String zipcode = row.get("zip");

        if (zipcode.length() > 3) {
          if (zipcode.length() == 4) {
            zipcode = "0" + zipcode;
          }
        }

        String ssaCode = row.get("ssacounty");
        ssaTable.put(zipcode, ssaCode);
      }
      return ssaTable;
    }
    
    private String capitalizeWords(String str) {
      String[] words = str.split("\\s");
      String capitalizeWords = "";
      for (String w: words) {
        String first = w.substring(0,1);
        String afterFirst = w.substring(1);
        capitalizeWords += first.toUpperCase() + afterFirst + " ";
      }
      return capitalizeWords.trim();
    }
  }

  /**
   * Defines the fields used in the beneficiary file. Note that order is significant, columns will
   * be written in the order specified.
   * Note also that it is package accessible, since it is used in BFDExportBuilder
   */
  enum BeneficiaryFields {
    DML_IND,
    BENE_ID,
    STATE_CODE,
    BENE_COUNTY_CD,
    BENE_ZIP_CD,
    BENE_BIRTH_DT,
    BENE_SEX_IDENT_CD,
    BENE_RACE_CD,
    BENE_ENTLMT_RSN_ORIG,
    BENE_ENTLMT_RSN_CURR,
    BENE_ESRD_IND,
    BENE_MDCR_STATUS_CD,
    BENE_PTA_TRMNTN_CD,
    BENE_PTB_TRMNTN_CD,
    // BENE_PTD_TRMNTN_CD, // The spreadsheet has a gap for this column, examples do not include it
    BENE_CRNT_HIC_NUM,
    BENE_SRNM_NAME,
    BENE_GVN_NAME,
    BENE_MDL_NAME,
    MBI_NUM,
    DEATH_DT,
    RFRNC_YR,
    A_MO_CNT,
    B_MO_CNT,
    BUYIN_MO_CNT,
    HMO_MO_CNT,
    RDS_MO_CNT,
    ENRL_SRC,
    SAMPLE_GROUP,
    EFIVEPCT,
    CRNT_BIC,
    AGE,
    COVSTART,
    DUAL_MO_CNT,
    FIPS_STATE_CNTY_JAN_CD,
    FIPS_STATE_CNTY_FEB_CD,
    FIPS_STATE_CNTY_MAR_CD,
    FIPS_STATE_CNTY_APR_CD,
    FIPS_STATE_CNTY_MAY_CD,
    FIPS_STATE_CNTY_JUN_CD,
    FIPS_STATE_CNTY_JUL_CD,
    FIPS_STATE_CNTY_AUG_CD,
    FIPS_STATE_CNTY_SEPT_CD,
    FIPS_STATE_CNTY_OCT_CD,
    FIPS_STATE_CNTY_NOV_CD,
    FIPS_STATE_CNTY_DEC_CD,
    V_DOD_SW,
    RTI_RACE_CD,
    MDCR_STUS_JAN_CD,
    MDCR_STUS_FEB_CD,
    MDCR_STUS_MAR_CD,
    MDCR_STUS_APR_CD,
    MDCR_STUS_MAY_CD,
    MDCR_STUS_JUN_CD,
    MDCR_STUS_JUL_CD,
    MDCR_STUS_AUG_CD,
    MDCR_STUS_SEPT_CD,
    MDCR_STUS_OCT_CD,
    MDCR_STUS_NOV_CD,
    MDCR_STUS_DEC_CD,
    PLAN_CVRG_MO_CNT,
    MDCR_ENTLMT_BUYIN_1_IND,
    MDCR_ENTLMT_BUYIN_2_IND,
    MDCR_ENTLMT_BUYIN_3_IND,
    MDCR_ENTLMT_BUYIN_4_IND,
    MDCR_ENTLMT_BUYIN_5_IND,
    MDCR_ENTLMT_BUYIN_6_IND,
    MDCR_ENTLMT_BUYIN_7_IND,
    MDCR_ENTLMT_BUYIN_8_IND,
    MDCR_ENTLMT_BUYIN_9_IND,
    MDCR_ENTLMT_BUYIN_10_IND,
    MDCR_ENTLMT_BUYIN_11_IND,
    MDCR_ENTLMT_BUYIN_12_IND,
    HMO_1_IND,
    HMO_2_IND,
    HMO_3_IND,
    HMO_4_IND,
    HMO_5_IND,
    HMO_6_IND,
    HMO_7_IND,
    HMO_8_IND,
    HMO_9_IND,
    HMO_10_IND,
    HMO_11_IND,
    HMO_12_IND,
    PTC_CNTRCT_JAN_ID,
    PTC_CNTRCT_FEB_ID,
    PTC_CNTRCT_MAR_ID,
    PTC_CNTRCT_APR_ID,
    PTC_CNTRCT_MAY_ID,
    PTC_CNTRCT_JUN_ID,
    PTC_CNTRCT_JUL_ID,
    PTC_CNTRCT_AUG_ID,
    PTC_CNTRCT_SEPT_ID,
    PTC_CNTRCT_OCT_ID,
    PTC_CNTRCT_NOV_ID,
    PTC_CNTRCT_DEC_ID,
    PTC_PBP_JAN_ID,
    PTC_PBP_FEB_ID,
    PTC_PBP_MAR_ID,
    PTC_PBP_APR_ID,
    PTC_PBP_MAY_ID,
    PTC_PBP_JUN_ID,
    PTC_PBP_JUL_ID,
    PTC_PBP_AUG_ID,
    PTC_PBP_SEPT_ID,
    PTC_PBP_OCT_ID,
    PTC_PBP_NOV_ID,
    PTC_PBP_DEC_ID,
    PTC_PLAN_TYPE_JAN_CD,
    PTC_PLAN_TYPE_FEB_CD,
    PTC_PLAN_TYPE_MAR_CD,
    PTC_PLAN_TYPE_APR_CD,
    PTC_PLAN_TYPE_MAY_CD,
    PTC_PLAN_TYPE_JUN_CD,
    PTC_PLAN_TYPE_JUL_CD,
    PTC_PLAN_TYPE_AUG_CD,
    PTC_PLAN_TYPE_SEPT_CD,
    PTC_PLAN_TYPE_OCT_CD,
    PTC_PLAN_TYPE_NOV_CD,
    PTC_PLAN_TYPE_DEC_CD,
    PTD_CNTRCT_JAN_ID,
    PTD_CNTRCT_FEB_ID,
    PTD_CNTRCT_MAR_ID,
    PTD_CNTRCT_APR_ID,
    PTD_CNTRCT_MAY_ID,
    PTD_CNTRCT_JUN_ID,
    PTD_CNTRCT_JUL_ID,
    PTD_CNTRCT_AUG_ID,
    PTD_CNTRCT_SEPT_ID,
    PTD_CNTRCT_OCT_ID,
    PTD_CNTRCT_NOV_ID,
    PTD_CNTRCT_DEC_ID,
    PTD_PBP_JAN_ID,
    PTD_PBP_FEB_ID,
    PTD_PBP_MAR_ID,
    PTD_PBP_APR_ID,
    PTD_PBP_MAY_ID,
    PTD_PBP_JUN_ID,
    PTD_PBP_JUL_ID,
    PTD_PBP_AUG_ID,
    PTD_PBP_SEPT_ID,
    PTD_PBP_OCT_ID,
    PTD_PBP_NOV_ID,
    PTD_PBP_DEC_ID,
    PTD_SGMT_JAN_ID,
    PTD_SGMT_FEB_ID,
    PTD_SGMT_MAR_ID,
    PTD_SGMT_APR_ID,
    PTD_SGMT_MAY_ID,
    PTD_SGMT_JUN_ID,
    PTD_SGMT_JUL_ID,
    PTD_SGMT_AUG_ID,
    PTD_SGMT_SEPT_ID,
    PTD_SGMT_OCT_ID,
    PTD_SGMT_NOV_ID,
    PTD_SGMT_DEC_ID,
    RDS_JAN_IND,
    RDS_FEB_IND,
    RDS_MAR_IND,
    RDS_APR_IND,
    RDS_MAY_IND,
    RDS_JUN_IND,
    RDS_JUL_IND,
    RDS_AUG_IND,
    RDS_SEPT_IND,
    RDS_OCT_IND,
    RDS_NOV_IND,
    RDS_DEC_IND,
    META_DUAL_ELGBL_STUS_JAN_CD,
    META_DUAL_ELGBL_STUS_FEB_CD,
    META_DUAL_ELGBL_STUS_MAR_CD,
    META_DUAL_ELGBL_STUS_APR_CD,
    META_DUAL_ELGBL_STUS_MAY_CD,
    META_DUAL_ELGBL_STUS_JUN_CD,
    META_DUAL_ELGBL_STUS_JUL_CD,
    META_DUAL_ELGBL_STUS_AUG_CD,
    META_DUAL_ELGBL_STUS_SEPT_CD,
    META_DUAL_ELGBL_STUS_OCT_CD,
    META_DUAL_ELGBL_STUS_NOV_CD,
    META_DUAL_ELGBL_STUS_DEC_CD,
    CST_SHR_GRP_JAN_CD,
    CST_SHR_GRP_FEB_CD,
    CST_SHR_GRP_MAR_CD,
    CST_SHR_GRP_APR_CD,
    CST_SHR_GRP_MAY_CD,
    CST_SHR_GRP_JUN_CD,
    CST_SHR_GRP_JUL_CD,
    CST_SHR_GRP_AUG_CD,
    CST_SHR_GRP_SEPT_CD,
    CST_SHR_GRP_OCT_CD,
    CST_SHR_GRP_NOV_CD,
    CST_SHR_GRP_DEC_CD,
    DRVD_LINE_1_ADR,
    DRVD_LINE_2_ADR,
    DRVD_LINE_3_ADR,
    DRVD_LINE_4_ADR,
    DRVD_LINE_5_ADR,
    DRVD_LINE_6_ADR,
    CITY_NAME,
    STATE_CD,
    STATE_CNTY_ZIP_CD,
    EFCTV_BGN_DT,
    EFCTV_END_DT,
    BENE_LINK_KEY
  }
  
  private final BeneficiaryFields[] beneficiaryPartDContractFields = {
    BeneficiaryFields.PTD_CNTRCT_JAN_ID,
    BeneficiaryFields.PTD_CNTRCT_FEB_ID,
    BeneficiaryFields.PTD_CNTRCT_MAR_ID,
    BeneficiaryFields.PTD_CNTRCT_APR_ID,
    BeneficiaryFields.PTD_CNTRCT_MAY_ID,
    BeneficiaryFields.PTD_CNTRCT_JUN_ID,
    BeneficiaryFields.PTD_CNTRCT_JUL_ID,
    BeneficiaryFields.PTD_CNTRCT_AUG_ID,
    BeneficiaryFields.PTD_CNTRCT_SEPT_ID,
    BeneficiaryFields.PTD_CNTRCT_OCT_ID,
    BeneficiaryFields.PTD_CNTRCT_NOV_ID,
    BeneficiaryFields.PTD_CNTRCT_DEC_ID
  };

  /* package access */
  enum BeneficiaryHistoryFields {
    DML_IND,
    BENE_ID,
    STATE_CODE,
    BENE_COUNTY_CD,
    BENE_ZIP_CD,
    BENE_BIRTH_DT,
    BENE_SEX_IDENT_CD,
    BENE_RACE_CD,
    BENE_ENTLMT_RSN_ORIG,
    BENE_ENTLMT_RSN_CURR,
    BENE_ESRD_IND,
    BENE_MDCR_STATUS_CD,
    BENE_PTA_TRMNTN_CD,
    BENE_PTB_TRMNTN_CD,
    BENE_CRNT_HIC_NUM,
    BENE_SRNM_NAME,
    BENE_GVN_NAME,
    BENE_MDL_NAME,
    MBI_NUM,
    EFCTV_BGN_DT,
    EFCTV_END_DT
  }
  
  enum OutpatientFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    CLAIM_QUERY_CODE,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    OP_PHYSN_UPIN,
    OP_PHYSN_NPI,
    OT_PHYSN_UPIN,
    OT_PHYSN_NPI,
    CLM_MCO_PD_SW,
    PTNT_DSCHRG_STUS_CD,
    CLM_TOT_CHRG_AMT,
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    NCH_PROFNL_CMPNT_CHRG_AMT,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    ICD_PRCDR_CD1,
    ICD_PRCDR_VRSN_CD1,
    PRCDR_DT1,
    ICD_PRCDR_CD2,
    ICD_PRCDR_VRSN_CD2,
    PRCDR_DT2,
    ICD_PRCDR_CD3,
    ICD_PRCDR_VRSN_CD3,
    PRCDR_DT3,
    ICD_PRCDR_CD4,
    ICD_PRCDR_VRSN_CD4,
    PRCDR_DT4,
    ICD_PRCDR_CD5,
    ICD_PRCDR_VRSN_CD5,
    PRCDR_DT5,
    ICD_PRCDR_CD6,
    ICD_PRCDR_VRSN_CD6,
    PRCDR_DT6,
    ICD_PRCDR_CD7,
    ICD_PRCDR_VRSN_CD7,
    PRCDR_DT7,
    ICD_PRCDR_CD8,
    ICD_PRCDR_VRSN_CD8,
    PRCDR_DT8,
    ICD_PRCDR_CD9,
    ICD_PRCDR_VRSN_CD9,
    PRCDR_DT9,
    ICD_PRCDR_CD10,
    ICD_PRCDR_VRSN_CD10,
    PRCDR_DT10,
    ICD_PRCDR_CD11,
    ICD_PRCDR_VRSN_CD11,
    PRCDR_DT11,
    ICD_PRCDR_CD12,
    ICD_PRCDR_VRSN_CD12,
    PRCDR_DT12,
    ICD_PRCDR_CD13,
    ICD_PRCDR_VRSN_CD13,
    PRCDR_DT13,
    ICD_PRCDR_CD14,
    ICD_PRCDR_VRSN_CD14,
    PRCDR_DT14,
    ICD_PRCDR_CD15,
    ICD_PRCDR_VRSN_CD15,
    PRCDR_DT15,
    ICD_PRCDR_CD16,
    ICD_PRCDR_VRSN_CD16,
    PRCDR_DT16,
    ICD_PRCDR_CD17,
    ICD_PRCDR_VRSN_CD17,
    PRCDR_DT17,
    ICD_PRCDR_CD18,
    ICD_PRCDR_VRSN_CD18,
    PRCDR_DT18,
    ICD_PRCDR_CD19,
    ICD_PRCDR_VRSN_CD19,
    PRCDR_DT19,
    ICD_PRCDR_CD20,
    ICD_PRCDR_VRSN_CD20,
    PRCDR_DT20,
    ICD_PRCDR_CD21,
    ICD_PRCDR_VRSN_CD21,
    PRCDR_DT21,
    ICD_PRCDR_CD22,
    ICD_PRCDR_VRSN_CD22,
    PRCDR_DT22,
    ICD_PRCDR_CD23,
    ICD_PRCDR_VRSN_CD23,
    PRCDR_DT23,
    ICD_PRCDR_CD24,
    ICD_PRCDR_VRSN_CD24,
    PRCDR_DT24,
    ICD_PRCDR_CD25,
    ICD_PRCDR_VRSN_CD25,
    PRCDR_DT25,
    RSN_VISIT_CD1,
    RSN_VISIT_VRSN_CD1,
    RSN_VISIT_CD2,
    RSN_VISIT_VRSN_CD2,
    RSN_VISIT_CD3,
    RSN_VISIT_VRSN_CD3,
    NCH_BENE_PTB_DDCTBL_AMT,
    NCH_BENE_PTB_COINSRNC_AMT,
    CLM_OP_PRVDR_PMT_AMT,
    CLM_OP_BENE_PMT_AMT,
    FI_DOC_CLM_CNTL_NUM,
    FI_ORIG_CLM_CNTL_NUM,
    CLM_LINE_NUM,
    REV_CNTR,
    REV_CNTR_DT,
    REV_CNTR_1ST_ANSI_CD,
    REV_CNTR_2ND_ANSI_CD,
    REV_CNTR_3RD_ANSI_CD,
    REV_CNTR_4TH_ANSI_CD,
    REV_CNTR_APC_HIPPS_CD,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    REV_CNTR_PMT_MTHD_IND_CD,
    REV_CNTR_DSCNT_IND_CD,
    REV_CNTR_PACKG_IND_CD,
    REV_CNTR_OTAF_PMT_CD,
    REV_CNTR_IDE_NDC_UPC_NUM,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_BLOOD_DDCTBL_AMT,
    REV_CNTR_CASH_DDCTBL_AMT,
    REV_CNTR_COINSRNC_WGE_ADJSTD_C,
    REV_CNTR_RDCD_COINSRNC_AMT,
    REV_CNTR_1ST_MSP_PD_AMT,
    REV_CNTR_2ND_MSP_PD_AMT,
    REV_CNTR_PRVDR_PMT_AMT,
    REV_CNTR_BENE_PMT_AMT,
    REV_CNTR_PTNT_RSPNSBLTY_PMT,
    REV_CNTR_PMT_AMT_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_STUS_IND_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }

  /* package access */
  enum InpatientFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    CLAIM_QUERY_CODE,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    FI_CLM_ACTN_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    OP_PHYSN_UPIN,
    OP_PHYSN_NPI,
    OT_PHYSN_UPIN,
    OT_PHYSN_NPI,
    CLM_MCO_PD_SW,
    PTNT_DSCHRG_STUS_CD,
    CLM_PPS_IND_CD,
    CLM_TOT_CHRG_AMT,
    CLM_ADMSN_DT,
    CLM_IP_ADMSN_TYPE_CD,
    CLM_SRC_IP_ADMSN_CD,
    NCH_PTNT_STATUS_IND_CD,
    CLM_PASS_THRU_PER_DIEM_AMT,
    NCH_BENE_IP_DDCTBL_AMT,
    NCH_BENE_PTA_COINSRNC_LBLTY_AM,
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    NCH_PROFNL_CMPNT_CHRG_AMT,
    NCH_IP_NCVRD_CHRG_AMT,
    NCH_IP_TOT_DDCTN_AMT,
    CLM_TOT_PPS_CPTL_AMT,
    CLM_PPS_CPTL_FSP_AMT,
    CLM_PPS_CPTL_OUTLIER_AMT,
    CLM_PPS_CPTL_DSPRPRTNT_SHR_AMT,
    CLM_PPS_CPTL_IME_AMT,
    CLM_PPS_CPTL_EXCPTN_AMT,
    CLM_PPS_OLD_CPTL_HLD_HRMLS_AMT,
    CLM_PPS_CPTL_DRG_WT_NUM,
    CLM_UTLZTN_DAY_CNT,
    BENE_TOT_COINSRNC_DAYS_CNT,
    BENE_LRD_USED_CNT,
    CLM_NON_UTLZTN_DAYS_CNT,
    NCH_BLOOD_PNTS_FRNSHD_QTY,
    NCH_VRFD_NCVRD_STAY_FROM_DT,
    NCH_VRFD_NCVRD_STAY_THRU_DT,
    NCH_ACTV_OR_CVRD_LVL_CARE_THRU,
    NCH_BENE_MDCR_BNFTS_EXHTD_DT_I,
    NCH_BENE_DSCHRG_DT,
    CLM_DRG_CD,
    CLM_DRG_OUTLIER_STAY_CD,
    NCH_DRG_OUTLIER_APRVD_PMT_AMT,
    ADMTG_DGNS_CD,
    ADMTG_DGNS_VRSN_CD,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    CLM_POA_IND_SW1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    CLM_POA_IND_SW2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    CLM_POA_IND_SW3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    CLM_POA_IND_SW4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    CLM_POA_IND_SW5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    CLM_POA_IND_SW6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    CLM_POA_IND_SW7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    CLM_POA_IND_SW8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    CLM_POA_IND_SW9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    CLM_POA_IND_SW10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    CLM_POA_IND_SW11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    CLM_POA_IND_SW12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    CLM_POA_IND_SW13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    CLM_POA_IND_SW14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    CLM_POA_IND_SW15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    CLM_POA_IND_SW16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    CLM_POA_IND_SW17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    CLM_POA_IND_SW18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    CLM_POA_IND_SW19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    CLM_POA_IND_SW20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    CLM_POA_IND_SW21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    CLM_POA_IND_SW22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    CLM_POA_IND_SW23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    CLM_POA_IND_SW24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    CLM_POA_IND_SW25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    CLM_E_POA_IND_SW1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    CLM_E_POA_IND_SW2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    CLM_E_POA_IND_SW3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    CLM_E_POA_IND_SW4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    CLM_E_POA_IND_SW5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    CLM_E_POA_IND_SW6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    CLM_E_POA_IND_SW7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    CLM_E_POA_IND_SW8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    CLM_E_POA_IND_SW9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    CLM_E_POA_IND_SW10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    CLM_E_POA_IND_SW11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    CLM_E_POA_IND_SW12,
    ICD_PRCDR_CD1,
    ICD_PRCDR_VRSN_CD1,
    PRCDR_DT1,
    ICD_PRCDR_CD2,
    ICD_PRCDR_VRSN_CD2,
    PRCDR_DT2,
    ICD_PRCDR_CD3,
    ICD_PRCDR_VRSN_CD3,
    PRCDR_DT3,
    ICD_PRCDR_CD4,
    ICD_PRCDR_VRSN_CD4,
    PRCDR_DT4,
    ICD_PRCDR_CD5,
    ICD_PRCDR_VRSN_CD5,
    PRCDR_DT5,
    ICD_PRCDR_CD6,
    ICD_PRCDR_VRSN_CD6,
    PRCDR_DT6,
    ICD_PRCDR_CD7,
    ICD_PRCDR_VRSN_CD7,
    PRCDR_DT7,
    ICD_PRCDR_CD8,
    ICD_PRCDR_VRSN_CD8,
    PRCDR_DT8,
    ICD_PRCDR_CD9,
    ICD_PRCDR_VRSN_CD9,
    PRCDR_DT9,
    ICD_PRCDR_CD10,
    ICD_PRCDR_VRSN_CD10,
    PRCDR_DT10,
    ICD_PRCDR_CD11,
    ICD_PRCDR_VRSN_CD11,
    PRCDR_DT11,
    ICD_PRCDR_CD12,
    ICD_PRCDR_VRSN_CD12,
    PRCDR_DT12,
    ICD_PRCDR_CD13,
    ICD_PRCDR_VRSN_CD13,
    PRCDR_DT13,
    ICD_PRCDR_CD14,
    ICD_PRCDR_VRSN_CD14,
    PRCDR_DT14,
    ICD_PRCDR_CD15,
    ICD_PRCDR_VRSN_CD15,
    PRCDR_DT15,
    ICD_PRCDR_CD16,
    ICD_PRCDR_VRSN_CD16,
    PRCDR_DT16,
    ICD_PRCDR_CD17,
    ICD_PRCDR_VRSN_CD17,
    PRCDR_DT17,
    ICD_PRCDR_CD18,
    ICD_PRCDR_VRSN_CD18,
    PRCDR_DT18,
    ICD_PRCDR_CD19,
    ICD_PRCDR_VRSN_CD19,
    PRCDR_DT19,
    ICD_PRCDR_CD20,
    ICD_PRCDR_VRSN_CD20,
    PRCDR_DT20,
    ICD_PRCDR_CD21,
    ICD_PRCDR_VRSN_CD21,
    PRCDR_DT21,
    ICD_PRCDR_CD22,
    ICD_PRCDR_VRSN_CD22,
    PRCDR_DT22,
    ICD_PRCDR_CD23,
    ICD_PRCDR_VRSN_CD23,
    PRCDR_DT23,
    ICD_PRCDR_CD24,
    ICD_PRCDR_VRSN_CD24,
    PRCDR_DT24,
    ICD_PRCDR_CD25,
    ICD_PRCDR_VRSN_CD25,
    PRCDR_DT25,
    IME_OP_CLM_VAL_AMT,
    DSH_OP_CLM_VAL_AMT,
    CLM_UNCOMPD_CARE_PMT_AMT,
    FI_DOC_CLM_CNTL_NUM,
    FI_ORIG_CLM_CNTL_NUM,
    CLM_LINE_NUM,
    REV_CNTR,
    HCPCS_CD,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_DDCTBL_COINSRNC_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }

  private InpatientFields[][] inpatientDxFields = {
    { InpatientFields.ICD_DGNS_CD1, InpatientFields.ICD_DGNS_VRSN_CD1,
      InpatientFields.CLM_POA_IND_SW1 },
    { InpatientFields.ICD_DGNS_CD2, InpatientFields.ICD_DGNS_VRSN_CD2,
      InpatientFields.CLM_POA_IND_SW2 },
    { InpatientFields.ICD_DGNS_CD3, InpatientFields.ICD_DGNS_VRSN_CD3,
      InpatientFields.CLM_POA_IND_SW3 },
    { InpatientFields.ICD_DGNS_CD4, InpatientFields.ICD_DGNS_VRSN_CD4,
      InpatientFields.CLM_POA_IND_SW4 },
    { InpatientFields.ICD_DGNS_CD5, InpatientFields.ICD_DGNS_VRSN_CD5,
      InpatientFields.CLM_POA_IND_SW5 },
    { InpatientFields.ICD_DGNS_CD6, InpatientFields.ICD_DGNS_VRSN_CD6,
      InpatientFields.CLM_POA_IND_SW6 },
    { InpatientFields.ICD_DGNS_CD7, InpatientFields.ICD_DGNS_VRSN_CD7,
      InpatientFields.CLM_POA_IND_SW7 },
    { InpatientFields.ICD_DGNS_CD8, InpatientFields.ICD_DGNS_VRSN_CD8,
      InpatientFields.CLM_POA_IND_SW8 },
    { InpatientFields.ICD_DGNS_CD9, InpatientFields.ICD_DGNS_VRSN_CD9,
      InpatientFields.CLM_POA_IND_SW9 },
    { InpatientFields.ICD_DGNS_CD10, InpatientFields.ICD_DGNS_VRSN_CD10,
      InpatientFields.CLM_POA_IND_SW10 },
    { InpatientFields.ICD_DGNS_CD11, InpatientFields.ICD_DGNS_VRSN_CD11,
      InpatientFields.CLM_POA_IND_SW11 },
    { InpatientFields.ICD_DGNS_CD12, InpatientFields.ICD_DGNS_VRSN_CD12,
      InpatientFields.CLM_POA_IND_SW12 },
    { InpatientFields.ICD_DGNS_CD13, InpatientFields.ICD_DGNS_VRSN_CD13,
      InpatientFields.CLM_POA_IND_SW13 },
    { InpatientFields.ICD_DGNS_CD14, InpatientFields.ICD_DGNS_VRSN_CD14,
      InpatientFields.CLM_POA_IND_SW14 },
    { InpatientFields.ICD_DGNS_CD15, InpatientFields.ICD_DGNS_VRSN_CD15,
      InpatientFields.CLM_POA_IND_SW15 },
    { InpatientFields.ICD_DGNS_CD16, InpatientFields.ICD_DGNS_VRSN_CD16,
      InpatientFields.CLM_POA_IND_SW16 },
    { InpatientFields.ICD_DGNS_CD17, InpatientFields.ICD_DGNS_VRSN_CD17,
      InpatientFields.CLM_POA_IND_SW17 },
    { InpatientFields.ICD_DGNS_CD18, InpatientFields.ICD_DGNS_VRSN_CD18,
      InpatientFields.CLM_POA_IND_SW18 },
    { InpatientFields.ICD_DGNS_CD19, InpatientFields.ICD_DGNS_VRSN_CD19,
      InpatientFields.CLM_POA_IND_SW19 },
    { InpatientFields.ICD_DGNS_CD20, InpatientFields.ICD_DGNS_VRSN_CD20,
      InpatientFields.CLM_POA_IND_SW20 },
    { InpatientFields.ICD_DGNS_CD21, InpatientFields.ICD_DGNS_VRSN_CD21,
      InpatientFields.CLM_POA_IND_SW21 },
    { InpatientFields.ICD_DGNS_CD22, InpatientFields.ICD_DGNS_VRSN_CD22,
      InpatientFields.CLM_POA_IND_SW22 },
    { InpatientFields.ICD_DGNS_CD23, InpatientFields.ICD_DGNS_VRSN_CD23,
      InpatientFields.CLM_POA_IND_SW23 },
    { InpatientFields.ICD_DGNS_CD24, InpatientFields.ICD_DGNS_VRSN_CD24,
      InpatientFields.CLM_POA_IND_SW24 },
    { InpatientFields.ICD_DGNS_CD25, InpatientFields.ICD_DGNS_VRSN_CD25,
      InpatientFields.CLM_POA_IND_SW25 }
  };

  private InpatientFields[][] inpatientPxFields = {
    { InpatientFields.ICD_PRCDR_CD1, InpatientFields.ICD_PRCDR_VRSN_CD1,
      InpatientFields.PRCDR_DT1 },
    { InpatientFields.ICD_PRCDR_CD2, InpatientFields.ICD_PRCDR_VRSN_CD2,
      InpatientFields.PRCDR_DT2 },
    { InpatientFields.ICD_PRCDR_CD3, InpatientFields.ICD_PRCDR_VRSN_CD3,
      InpatientFields.PRCDR_DT3 },
    { InpatientFields.ICD_PRCDR_CD4, InpatientFields.ICD_PRCDR_VRSN_CD4,
      InpatientFields.PRCDR_DT4 },
    { InpatientFields.ICD_PRCDR_CD5, InpatientFields.ICD_PRCDR_VRSN_CD5,
      InpatientFields.PRCDR_DT5 },
    { InpatientFields.ICD_PRCDR_CD6, InpatientFields.ICD_PRCDR_VRSN_CD6,
      InpatientFields.PRCDR_DT6 },
    { InpatientFields.ICD_PRCDR_CD7, InpatientFields.ICD_PRCDR_VRSN_CD7,
      InpatientFields.PRCDR_DT7 },
    { InpatientFields.ICD_PRCDR_CD8, InpatientFields.ICD_PRCDR_VRSN_CD8,
      InpatientFields.PRCDR_DT8 },
    { InpatientFields.ICD_PRCDR_CD9, InpatientFields.ICD_PRCDR_VRSN_CD9,
      InpatientFields.PRCDR_DT9 },
    { InpatientFields.ICD_PRCDR_CD10, InpatientFields.ICD_PRCDR_VRSN_CD10,
      InpatientFields.PRCDR_DT10 },
    { InpatientFields.ICD_PRCDR_CD11, InpatientFields.ICD_PRCDR_VRSN_CD11,
      InpatientFields.PRCDR_DT11 },
    { InpatientFields.ICD_PRCDR_CD12, InpatientFields.ICD_PRCDR_VRSN_CD12,
      InpatientFields.PRCDR_DT12 },
    { InpatientFields.ICD_PRCDR_CD13, InpatientFields.ICD_PRCDR_VRSN_CD13,
      InpatientFields.PRCDR_DT13 },
    { InpatientFields.ICD_PRCDR_CD14, InpatientFields.ICD_PRCDR_VRSN_CD14,
      InpatientFields.PRCDR_DT14 },
    { InpatientFields.ICD_PRCDR_CD15, InpatientFields.ICD_PRCDR_VRSN_CD15,
      InpatientFields.PRCDR_DT15 },
    { InpatientFields.ICD_PRCDR_CD16, InpatientFields.ICD_PRCDR_VRSN_CD16,
      InpatientFields.PRCDR_DT16 },
    { InpatientFields.ICD_PRCDR_CD17, InpatientFields.ICD_PRCDR_VRSN_CD17,
      InpatientFields.PRCDR_DT17 },
    { InpatientFields.ICD_PRCDR_CD18, InpatientFields.ICD_PRCDR_VRSN_CD18,
      InpatientFields.PRCDR_DT18 },
    { InpatientFields.ICD_PRCDR_CD19, InpatientFields.ICD_PRCDR_VRSN_CD19,
      InpatientFields.PRCDR_DT19 },
    { InpatientFields.ICD_PRCDR_CD20, InpatientFields.ICD_PRCDR_VRSN_CD20,
      InpatientFields.PRCDR_DT20 },
    { InpatientFields.ICD_PRCDR_CD21, InpatientFields.ICD_PRCDR_VRSN_CD21,
      InpatientFields.PRCDR_DT21 },
    { InpatientFields.ICD_PRCDR_CD22, InpatientFields.ICD_PRCDR_VRSN_CD22,
      InpatientFields.PRCDR_DT22 },
    { InpatientFields.ICD_PRCDR_CD23, InpatientFields.ICD_PRCDR_VRSN_CD23,
      InpatientFields.PRCDR_DT23 },
    { InpatientFields.ICD_PRCDR_CD24, InpatientFields.ICD_PRCDR_VRSN_CD24,
      InpatientFields.PRCDR_DT24 },
    { InpatientFields.ICD_PRCDR_CD25, InpatientFields.ICD_PRCDR_VRSN_CD25,
      InpatientFields.PRCDR_DT25 }
  };

  private OutpatientFields[][] outpatientDxFields = {
    { OutpatientFields.ICD_DGNS_CD1, OutpatientFields.ICD_DGNS_VRSN_CD1 },
    { OutpatientFields.ICD_DGNS_CD2, OutpatientFields.ICD_DGNS_VRSN_CD2 },
    { OutpatientFields.ICD_DGNS_CD3, OutpatientFields.ICD_DGNS_VRSN_CD3 },
    { OutpatientFields.ICD_DGNS_CD4, OutpatientFields.ICD_DGNS_VRSN_CD4 },
    { OutpatientFields.ICD_DGNS_CD5, OutpatientFields.ICD_DGNS_VRSN_CD5 },
    { OutpatientFields.ICD_DGNS_CD6, OutpatientFields.ICD_DGNS_VRSN_CD6 },
    { OutpatientFields.ICD_DGNS_CD7, OutpatientFields.ICD_DGNS_VRSN_CD7 },
    { OutpatientFields.ICD_DGNS_CD8, OutpatientFields.ICD_DGNS_VRSN_CD8 },
    { OutpatientFields.ICD_DGNS_CD9, OutpatientFields.ICD_DGNS_VRSN_CD9 },
    { OutpatientFields.ICD_DGNS_CD10, OutpatientFields.ICD_DGNS_VRSN_CD10 },
    { OutpatientFields.ICD_DGNS_CD11, OutpatientFields.ICD_DGNS_VRSN_CD11 },
    { OutpatientFields.ICD_DGNS_CD12, OutpatientFields.ICD_DGNS_VRSN_CD12 },
    { OutpatientFields.ICD_DGNS_CD13, OutpatientFields.ICD_DGNS_VRSN_CD13 },
    { OutpatientFields.ICD_DGNS_CD14, OutpatientFields.ICD_DGNS_VRSN_CD14 },
    { OutpatientFields.ICD_DGNS_CD15, OutpatientFields.ICD_DGNS_VRSN_CD15 },
    { OutpatientFields.ICD_DGNS_CD16, OutpatientFields.ICD_DGNS_VRSN_CD16 },
    { OutpatientFields.ICD_DGNS_CD17, OutpatientFields.ICD_DGNS_VRSN_CD17 },
    { OutpatientFields.ICD_DGNS_CD18, OutpatientFields.ICD_DGNS_VRSN_CD18 },
    { OutpatientFields.ICD_DGNS_CD19, OutpatientFields.ICD_DGNS_VRSN_CD19 },
    { OutpatientFields.ICD_DGNS_CD20, OutpatientFields.ICD_DGNS_VRSN_CD20 },
    { OutpatientFields.ICD_DGNS_CD21, OutpatientFields.ICD_DGNS_VRSN_CD21 },
    { OutpatientFields.ICD_DGNS_CD22, OutpatientFields.ICD_DGNS_VRSN_CD22 },
    { OutpatientFields.ICD_DGNS_CD23, OutpatientFields.ICD_DGNS_VRSN_CD23 },
    { OutpatientFields.ICD_DGNS_CD24, OutpatientFields.ICD_DGNS_VRSN_CD24 },
    { OutpatientFields.ICD_DGNS_CD25, OutpatientFields.ICD_DGNS_VRSN_CD25 }
  };

  private OutpatientFields[][] outpatientPxFields = {
    { OutpatientFields.ICD_PRCDR_CD1, OutpatientFields.ICD_PRCDR_VRSN_CD1,
      OutpatientFields.PRCDR_DT1 },
    { OutpatientFields.ICD_PRCDR_CD2, OutpatientFields.ICD_PRCDR_VRSN_CD2,
      OutpatientFields.PRCDR_DT2 },
    { OutpatientFields.ICD_PRCDR_CD3, OutpatientFields.ICD_PRCDR_VRSN_CD3,
      OutpatientFields.PRCDR_DT3 },
    { OutpatientFields.ICD_PRCDR_CD4, OutpatientFields.ICD_PRCDR_VRSN_CD4,
      OutpatientFields.PRCDR_DT4 },
    { OutpatientFields.ICD_PRCDR_CD5, OutpatientFields.ICD_PRCDR_VRSN_CD5,
      OutpatientFields.PRCDR_DT5 },
    { OutpatientFields.ICD_PRCDR_CD6, OutpatientFields.ICD_PRCDR_VRSN_CD6,
      OutpatientFields.PRCDR_DT6 },
    { OutpatientFields.ICD_PRCDR_CD7, OutpatientFields.ICD_PRCDR_VRSN_CD7,
      OutpatientFields.PRCDR_DT7 },
    { OutpatientFields.ICD_PRCDR_CD8, OutpatientFields.ICD_PRCDR_VRSN_CD8,
      OutpatientFields.PRCDR_DT8 },
    { OutpatientFields.ICD_PRCDR_CD9, OutpatientFields.ICD_PRCDR_VRSN_CD9,
      OutpatientFields.PRCDR_DT9 },
    { OutpatientFields.ICD_PRCDR_CD10, OutpatientFields.ICD_PRCDR_VRSN_CD10,
      OutpatientFields.PRCDR_DT10 },
    { OutpatientFields.ICD_PRCDR_CD11, OutpatientFields.ICD_PRCDR_VRSN_CD11,
      OutpatientFields.PRCDR_DT11 },
    { OutpatientFields.ICD_PRCDR_CD12, OutpatientFields.ICD_PRCDR_VRSN_CD12,
      OutpatientFields.PRCDR_DT12 },
    { OutpatientFields.ICD_PRCDR_CD13, OutpatientFields.ICD_PRCDR_VRSN_CD13,
      OutpatientFields.PRCDR_DT13 },
    { OutpatientFields.ICD_PRCDR_CD14, OutpatientFields.ICD_PRCDR_VRSN_CD14,
      OutpatientFields.PRCDR_DT14 },
    { OutpatientFields.ICD_PRCDR_CD15, OutpatientFields.ICD_PRCDR_VRSN_CD15,
      OutpatientFields.PRCDR_DT15 },
    { OutpatientFields.ICD_PRCDR_CD16, OutpatientFields.ICD_PRCDR_VRSN_CD16,
      OutpatientFields.PRCDR_DT16 },
    { OutpatientFields.ICD_PRCDR_CD17, OutpatientFields.ICD_PRCDR_VRSN_CD17,
      OutpatientFields.PRCDR_DT17 },
    { OutpatientFields.ICD_PRCDR_CD18, OutpatientFields.ICD_PRCDR_VRSN_CD18,
      OutpatientFields.PRCDR_DT18 },
    { OutpatientFields.ICD_PRCDR_CD19, OutpatientFields.ICD_PRCDR_VRSN_CD19,
      OutpatientFields.PRCDR_DT19 },
    { OutpatientFields.ICD_PRCDR_CD20, OutpatientFields.ICD_PRCDR_VRSN_CD20,
      OutpatientFields.PRCDR_DT20 },
    { OutpatientFields.ICD_PRCDR_CD21, OutpatientFields.ICD_PRCDR_VRSN_CD21,
      OutpatientFields.PRCDR_DT21 },
    { OutpatientFields.ICD_PRCDR_CD22, OutpatientFields.ICD_PRCDR_VRSN_CD22,
      OutpatientFields.PRCDR_DT22 },
    { OutpatientFields.ICD_PRCDR_CD23, OutpatientFields.ICD_PRCDR_VRSN_CD23,
      OutpatientFields.PRCDR_DT23 },
    { OutpatientFields.ICD_PRCDR_CD24, OutpatientFields.ICD_PRCDR_VRSN_CD24,
      OutpatientFields.PRCDR_DT24 },
    { OutpatientFields.ICD_PRCDR_CD25, OutpatientFields.ICD_PRCDR_VRSN_CD25,
      OutpatientFields.PRCDR_DT25 }
  };

  /* package access */
  enum CarrierFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    CARR_CLM_ENTRY_CD,
    CLM_DISP_CD,
    CARR_NUM,
    CARR_CLM_PMT_DNL_CD,
    CLM_PMT_AMT,
    CARR_CLM_PRMRY_PYR_PD_AMT,
    RFR_PHYSN_UPIN,
    RFR_PHYSN_NPI,
    CARR_CLM_PRVDR_ASGNMT_IND_SW,
    NCH_CLM_PRVDR_PMT_AMT,
    NCH_CLM_BENE_PMT_AMT,
    NCH_CARR_CLM_SBMTD_CHRG_AMT,
    NCH_CARR_CLM_ALOWD_AMT,
    CARR_CLM_CASH_DDCTBL_APLD_AMT,
    CARR_CLM_HCPCS_YR_CD,
    CARR_CLM_RFRNG_PIN_NUM,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    CLM_CLNCL_TRIL_NUM,
    CARR_CLM_CNTL_NUM,
    LINE_NUM,
    CARR_PRFRNG_PIN_NUM,
    PRF_PHYSN_UPIN,
    PRF_PHYSN_NPI,
    ORG_NPI_NUM,
    CARR_LINE_PRVDR_TYPE_CD,
    TAX_NUM,
    PRVDR_STATE_CD,
    PRVDR_ZIP,
    PRVDR_SPCLTY,
    PRTCPTNG_IND_CD,
    CARR_LINE_RDCD_PMT_PHYS_ASTN_C,
    LINE_SRVC_CNT,
    LINE_CMS_TYPE_SRVC_CD,
    LINE_PLACE_OF_SRVC_CD,
    CARR_LINE_PRCNG_LCLTY_CD,
    LINE_1ST_EXPNS_DT,
    LINE_LAST_EXPNS_DT,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    BETOS_CD,
    LINE_NCH_PMT_AMT,
    LINE_BENE_PMT_AMT,
    LINE_PRVDR_PMT_AMT,
    LINE_BENE_PTB_DDCTBL_AMT,
    LINE_BENE_PRMRY_PYR_CD,
    LINE_BENE_PRMRY_PYR_PD_AMT,
    LINE_COINSRNC_AMT,
    LINE_SBMTD_CHRG_AMT,
    LINE_ALOWD_CHRG_AMT,
    LINE_PRCSG_IND_CD,
    LINE_PMT_80_100_CD,
    LINE_SERVICE_DEDUCTIBLE,
    CARR_LINE_MTUS_CNT,
    CARR_LINE_MTUS_CD,
    LINE_ICD_DGNS_CD,
    LINE_ICD_DGNS_VRSN_CD,
    HPSA_SCRCTY_IND_CD,
    CARR_LINE_RX_NUM,
    LINE_HCT_HGB_RSLT_NUM,
    LINE_HCT_HGB_TYPE_CD,
    LINE_NDC_CD,
    CARR_LINE_CLIA_LAB_NUM,
    CARR_LINE_ANSTHSA_UNIT_CNT
  }

  private CarrierFields[][] carrierDxFields = {
    { CarrierFields.ICD_DGNS_CD1, CarrierFields.ICD_DGNS_VRSN_CD1 },
    { CarrierFields.ICD_DGNS_CD2, CarrierFields.ICD_DGNS_VRSN_CD2 },
    { CarrierFields.ICD_DGNS_CD3, CarrierFields.ICD_DGNS_VRSN_CD3 },
    { CarrierFields.ICD_DGNS_CD4, CarrierFields.ICD_DGNS_VRSN_CD4 },
    { CarrierFields.ICD_DGNS_CD5, CarrierFields.ICD_DGNS_VRSN_CD5 },
    { CarrierFields.ICD_DGNS_CD6, CarrierFields.ICD_DGNS_VRSN_CD6 },
    { CarrierFields.ICD_DGNS_CD7, CarrierFields.ICD_DGNS_VRSN_CD7 },
    { CarrierFields.ICD_DGNS_CD8, CarrierFields.ICD_DGNS_VRSN_CD8 },
    { CarrierFields.ICD_DGNS_CD9, CarrierFields.ICD_DGNS_VRSN_CD9 },
    { CarrierFields.ICD_DGNS_CD10, CarrierFields.ICD_DGNS_VRSN_CD10 },
    { CarrierFields.ICD_DGNS_CD11, CarrierFields.ICD_DGNS_VRSN_CD11 },
    { CarrierFields.ICD_DGNS_CD12, CarrierFields.ICD_DGNS_VRSN_CD12 }
  };

  public enum PrescriptionFields {
    DML_IND,
    PDE_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    BENE_ID,
    SRVC_DT,
    PD_DT,
    SRVC_PRVDR_ID_QLFYR_CD,
    SRVC_PRVDR_ID,
    PRSCRBR_ID_QLFYR_CD,
    PRSCRBR_ID,
    RX_SRVC_RFRNC_NUM,
    PROD_SRVC_ID,
    PLAN_CNTRCT_REC_ID,
    PLAN_PBP_REC_NUM,
    CMPND_CD,
    DAW_PROD_SLCTN_CD,
    QTY_DSPNSD_NUM,
    DAYS_SUPLY_NUM,
    FILL_NUM,
    DSPNSNG_STUS_CD,
    DRUG_CVRG_STUS_CD,
    ADJSTMT_DLTN_CD,
    NSTD_FRMT_CD,
    PRCNG_EXCPTN_CD,
    CTSTRPHC_CVRG_CD,
    GDC_BLW_OOPT_AMT,
    GDC_ABV_OOPT_AMT,
    PTNT_PAY_AMT,
    OTHR_TROOP_AMT,
    LICS_AMT,
    PLRO_AMT,
    CVRD_D_PLAN_PD_AMT,
    NCVRD_PLAN_PD_AMT,
    TOT_RX_CST_AMT,
    RX_ORGN_CD,
    RPTD_GAP_DSCNT_NUM,
    BRND_GNRC_CD,
    PHRMCY_SRVC_TYPE_CD,
    PTNT_RSDNC_CD,
    SUBMSN_CLR_CD
  }

  public enum DMEFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    CARR_CLM_ENTRY_CD,
    CLM_DISP_CD,
    CARR_NUM,
    CARR_CLM_PMT_DNL_CD,
    CLM_PMT_AMT,
    CARR_CLM_PRMRY_PYR_PD_AMT,
    CARR_CLM_PRVDR_ASGNMT_IND_SW,
    NCH_CLM_PRVDR_PMT_AMT,
    NCH_CLM_BENE_PMT_AMT,
    NCH_CARR_CLM_SBMTD_CHRG_AMT,
    NCH_CARR_CLM_ALOWD_AMT,
    CARR_CLM_CASH_DDCTBL_APLD_AMT,
    CARR_CLM_HCPCS_YR_CD,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    RFR_PHYSN_UPIN,
    RFR_PHYSN_NPI,
    CLM_CLNCL_TRIL_NUM,
    CARR_CLM_CNTL_NUM,
    LINE_NUM,
    TAX_NUM,
    PRVDR_SPCLTY,
    PRTCPTNG_IND_CD,
    LINE_SRVC_CNT,
    LINE_CMS_TYPE_SRVC_CD,
    LINE_PLACE_OF_SRVC_CD,
    LINE_1ST_EXPNS_DT,
    LINE_LAST_EXPNS_DT,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    BETOS_CD,
    LINE_NCH_PMT_AMT,
    LINE_BENE_PMT_AMT,
    LINE_PRVDR_PMT_AMT,
    LINE_BENE_PTB_DDCTBL_AMT,
    LINE_BENE_PRMRY_PYR_CD,
    LINE_BENE_PRMRY_PYR_PD_AMT,
    LINE_COINSRNC_AMT,
    LINE_PRMRY_ALOWD_CHRG_AMT,
    LINE_SBMTD_CHRG_AMT,
    LINE_ALOWD_CHRG_AMT,
    LINE_PRCSG_IND_CD,
    LINE_PMT_80_100_CD,
    LINE_SERVICE_DEDUCTIBLE,
    LINE_ICD_DGNS_CD,
    LINE_ICD_DGNS_VRSN_CD,
    LINE_DME_PRCHS_PRICE_AMT,
    PRVDR_NUM,
    PRVDR_NPI,
    DMERC_LINE_PRCNG_STATE_CD,
    PRVDR_STATE_CD,
    DMERC_LINE_SUPPLR_TYPE_CD,
    HCPCS_3RD_MDFR_CD,
    HCPCS_4TH_MDFR_CD,
    DMERC_LINE_SCRN_SVGS_AMT,
    DMERC_LINE_MTUS_CNT,
    DMERC_LINE_MTUS_CD,
    LINE_HCT_HGB_RSLT_NUM,
    LINE_HCT_HGB_TYPE_CD,
    LINE_NDC_CD
  }

  private DMEFields[][] dmeDxFields = {
    { DMEFields.ICD_DGNS_CD1, DMEFields.ICD_DGNS_VRSN_CD1 },
    { DMEFields.ICD_DGNS_CD2, DMEFields.ICD_DGNS_VRSN_CD2 },
    { DMEFields.ICD_DGNS_CD3, DMEFields.ICD_DGNS_VRSN_CD3 },
    { DMEFields.ICD_DGNS_CD4, DMEFields.ICD_DGNS_VRSN_CD4 },
    { DMEFields.ICD_DGNS_CD5, DMEFields.ICD_DGNS_VRSN_CD5 },
    { DMEFields.ICD_DGNS_CD6, DMEFields.ICD_DGNS_VRSN_CD6 },
    { DMEFields.ICD_DGNS_CD7, DMEFields.ICD_DGNS_VRSN_CD7 },
    { DMEFields.ICD_DGNS_CD8, DMEFields.ICD_DGNS_VRSN_CD8 },
    { DMEFields.ICD_DGNS_CD9, DMEFields.ICD_DGNS_VRSN_CD9 },
    { DMEFields.ICD_DGNS_CD10, DMEFields.ICD_DGNS_VRSN_CD10 },
    { DMEFields.ICD_DGNS_CD11, DMEFields.ICD_DGNS_VRSN_CD11 },
    { DMEFields.ICD_DGNS_CD12, DMEFields.ICD_DGNS_VRSN_CD12 }
  };

  public enum NPIFields {
    NPI,
    ENTITY_TYPE_CODE,
    REPLACEMENT_NPI,
    EIN,
    ORG_NAME,
    LAST_NAME,
    FIRST_NAME,
    MIDDLE_NAME,
    PREFIX,
    SUFFIX,
    CREDENTIALS
  }
  
  public enum HHAFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    PTNT_DSCHRG_STUS_CD,
    CLM_PPS_IND_CD,
    CLM_TOT_CHRG_AMT,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    CLM_HHA_LUPA_IND_CD,
    CLM_HHA_RFRL_CD,
    CLM_HHA_TOT_VISIT_CNT,
    CLM_ADMSN_DT,
    FI_DOC_CLM_CNTL_NUM,
    FI_ORIG_CLM_CNTL_NUM,
    CLM_LINE_NUM,
    REV_CNTR,
    REV_CNTR_DT,
    REV_CNTR_1ST_ANSI_CD,
    REV_CNTR_APC_HIPPS_CD,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    REV_CNTR_PMT_MTHD_IND_CD,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_PMT_AMT_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_DDCTBL_COINSRNC_CD,
    REV_CNTR_STUS_IND_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }

  private HHAFields[][] homeDxFields = {
    { HHAFields.ICD_DGNS_CD1, HHAFields.ICD_DGNS_VRSN_CD1 },
    { HHAFields.ICD_DGNS_CD2, HHAFields.ICD_DGNS_VRSN_CD2 },
    { HHAFields.ICD_DGNS_CD3, HHAFields.ICD_DGNS_VRSN_CD3 },
    { HHAFields.ICD_DGNS_CD4, HHAFields.ICD_DGNS_VRSN_CD4 },
    { HHAFields.ICD_DGNS_CD5, HHAFields.ICD_DGNS_VRSN_CD5 },
    { HHAFields.ICD_DGNS_CD6, HHAFields.ICD_DGNS_VRSN_CD6 },
    { HHAFields.ICD_DGNS_CD7, HHAFields.ICD_DGNS_VRSN_CD7 },
    { HHAFields.ICD_DGNS_CD8, HHAFields.ICD_DGNS_VRSN_CD8 },
    { HHAFields.ICD_DGNS_CD9, HHAFields.ICD_DGNS_VRSN_CD9 },
    { HHAFields.ICD_DGNS_CD10, HHAFields.ICD_DGNS_VRSN_CD10 },
    { HHAFields.ICD_DGNS_CD11, HHAFields.ICD_DGNS_VRSN_CD11 },
    { HHAFields.ICD_DGNS_CD12, HHAFields.ICD_DGNS_VRSN_CD12 },
    { HHAFields.ICD_DGNS_CD13, HHAFields.ICD_DGNS_VRSN_CD13 },
    { HHAFields.ICD_DGNS_CD14, HHAFields.ICD_DGNS_VRSN_CD14 },
    { HHAFields.ICD_DGNS_CD15, HHAFields.ICD_DGNS_VRSN_CD15 },
    { HHAFields.ICD_DGNS_CD16, HHAFields.ICD_DGNS_VRSN_CD16 },
    { HHAFields.ICD_DGNS_CD17, HHAFields.ICD_DGNS_VRSN_CD17 },
    { HHAFields.ICD_DGNS_CD18, HHAFields.ICD_DGNS_VRSN_CD18 },
    { HHAFields.ICD_DGNS_CD19, HHAFields.ICD_DGNS_VRSN_CD19 },
    { HHAFields.ICD_DGNS_CD20, HHAFields.ICD_DGNS_VRSN_CD20 },
    { HHAFields.ICD_DGNS_CD21, HHAFields.ICD_DGNS_VRSN_CD21 },
    { HHAFields.ICD_DGNS_CD22, HHAFields.ICD_DGNS_VRSN_CD22 },
    { HHAFields.ICD_DGNS_CD23, HHAFields.ICD_DGNS_VRSN_CD23 },
    { HHAFields.ICD_DGNS_CD24, HHAFields.ICD_DGNS_VRSN_CD24 },
    { HHAFields.ICD_DGNS_CD25, HHAFields.ICD_DGNS_VRSN_CD25 }
  };

  public enum HospiceFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    PTNT_DSCHRG_STUS_CD,
    CLM_TOT_CHRG_AMT,
    NCH_PTNT_STATUS_IND_CD,
    CLM_UTLZTN_DAY_CNT,
    NCH_BENE_DSCHRG_DT,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    CLM_HOSPC_START_DT_ID,
    BENE_HOSPC_PRD_CNT,
    FI_DOC_CLM_CNTL_NUM,
    FI_ORIG_CLM_CNTL_NUM,
    CLM_LINE_NUM,
    REV_CNTR,
    REV_CNTR_DT,
    HCPCS_CD,
    HCPCS_1ST_MDFR_CD,
    HCPCS_2ND_MDFR_CD,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_PRVDR_PMT_AMT,
    REV_CNTR_BENE_PMT_AMT,
    REV_CNTR_PMT_AMT_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_DDCTBL_COINSRNC_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }
  
  private HospiceFields[][] hospiceDxFields = {
    { HospiceFields.ICD_DGNS_CD1, HospiceFields.ICD_DGNS_VRSN_CD1 },
    { HospiceFields.ICD_DGNS_CD2, HospiceFields.ICD_DGNS_VRSN_CD2 },
    { HospiceFields.ICD_DGNS_CD3, HospiceFields.ICD_DGNS_VRSN_CD3 },
    { HospiceFields.ICD_DGNS_CD4, HospiceFields.ICD_DGNS_VRSN_CD4 },
    { HospiceFields.ICD_DGNS_CD5, HospiceFields.ICD_DGNS_VRSN_CD5 },
    { HospiceFields.ICD_DGNS_CD6, HospiceFields.ICD_DGNS_VRSN_CD6 },
    { HospiceFields.ICD_DGNS_CD7, HospiceFields.ICD_DGNS_VRSN_CD7 },
    { HospiceFields.ICD_DGNS_CD8, HospiceFields.ICD_DGNS_VRSN_CD8 },
    { HospiceFields.ICD_DGNS_CD9, HospiceFields.ICD_DGNS_VRSN_CD9 },
    { HospiceFields.ICD_DGNS_CD10, HospiceFields.ICD_DGNS_VRSN_CD10 },
    { HospiceFields.ICD_DGNS_CD11, HospiceFields.ICD_DGNS_VRSN_CD11 },
    { HospiceFields.ICD_DGNS_CD12, HospiceFields.ICD_DGNS_VRSN_CD12 },
    { HospiceFields.ICD_DGNS_CD13, HospiceFields.ICD_DGNS_VRSN_CD13 },
    { HospiceFields.ICD_DGNS_CD14, HospiceFields.ICD_DGNS_VRSN_CD14 },
    { HospiceFields.ICD_DGNS_CD15, HospiceFields.ICD_DGNS_VRSN_CD15 },
    { HospiceFields.ICD_DGNS_CD16, HospiceFields.ICD_DGNS_VRSN_CD16 },
    { HospiceFields.ICD_DGNS_CD17, HospiceFields.ICD_DGNS_VRSN_CD17 },
    { HospiceFields.ICD_DGNS_CD18, HospiceFields.ICD_DGNS_VRSN_CD18 },
    { HospiceFields.ICD_DGNS_CD19, HospiceFields.ICD_DGNS_VRSN_CD19 },
    { HospiceFields.ICD_DGNS_CD20, HospiceFields.ICD_DGNS_VRSN_CD20 },
    { HospiceFields.ICD_DGNS_CD21, HospiceFields.ICD_DGNS_VRSN_CD21 },
    { HospiceFields.ICD_DGNS_CD22, HospiceFields.ICD_DGNS_VRSN_CD22 },
    { HospiceFields.ICD_DGNS_CD23, HospiceFields.ICD_DGNS_VRSN_CD23 },
    { HospiceFields.ICD_DGNS_CD24, HospiceFields.ICD_DGNS_VRSN_CD24 },
    { HospiceFields.ICD_DGNS_CD25, HospiceFields.ICD_DGNS_VRSN_CD25 }
  };

  public enum SNFFields {
    DML_IND,
    BENE_ID,
    CLM_ID,
    CLM_GRP_ID,
    FINAL_ACTION,
    NCH_NEAR_LINE_REC_IDENT_CD,
    NCH_CLM_TYPE_CD,
    CLM_FROM_DT,
    CLM_THRU_DT,
    NCH_WKLY_PROC_DT,
    FI_CLM_PROC_DT,
    CLAIM_QUERY_CODE,
    PRVDR_NUM,
    CLM_FAC_TYPE_CD,
    CLM_SRVC_CLSFCTN_TYPE_CD,
    CLM_FREQ_CD,
    FI_NUM,
    CLM_MDCR_NON_PMT_RSN_CD,
    CLM_PMT_AMT,
    NCH_PRMRY_PYR_CLM_PD_AMT,
    NCH_PRMRY_PYR_CD,
    FI_CLM_ACTN_CD,
    PRVDR_STATE_CD,
    ORG_NPI_NUM,
    AT_PHYSN_UPIN,
    AT_PHYSN_NPI,
    OP_PHYSN_UPIN,
    OP_PHYSN_NPI,
    OT_PHYSN_UPIN,
    OT_PHYSN_NPI,
    CLM_MCO_PD_SW,
    PTNT_DSCHRG_STUS_CD,
    CLM_PPS_IND_CD,
    CLM_TOT_CHRG_AMT,
    CLM_ADMSN_DT,
    CLM_IP_ADMSN_TYPE_CD,
    CLM_SRC_IP_ADMSN_CD,
    NCH_PTNT_STATUS_IND_CD,
    NCH_BENE_IP_DDCTBL_AMT,
    NCH_BENE_PTA_COINSRNC_LBLTY_AM,
    NCH_BENE_BLOOD_DDCTBL_LBLTY_AM,
    NCH_IP_NCVRD_CHRG_AMT,
    NCH_IP_TOT_DDCTN_AMT,
    CLM_PPS_CPTL_FSP_AMT,
    CLM_PPS_CPTL_OUTLIER_AMT,
    CLM_PPS_CPTL_DSPRPRTNT_SHR_AMT,
    CLM_PPS_CPTL_IME_AMT,
    CLM_PPS_CPTL_EXCPTN_AMT,
    CLM_PPS_OLD_CPTL_HLD_HRMLS_AMT,
    CLM_UTLZTN_DAY_CNT,
    BENE_TOT_COINSRNC_DAYS_CNT,
    CLM_NON_UTLZTN_DAYS_CNT,
    NCH_BLOOD_PNTS_FRNSHD_QTY,
    NCH_QLFYD_STAY_FROM_DT,
    NCH_QLFYD_STAY_THRU_DT,
    NCH_VRFD_NCVRD_STAY_FROM_DT,
    NCH_VRFD_NCVRD_STAY_THRU_DT,
    NCH_ACTV_OR_CVRD_LVL_CARE_THRU,
    NCH_BENE_MDCR_BNFTS_EXHTD_DT_I,
    NCH_BENE_DSCHRG_DT,
    CLM_DRG_CD,
    ADMTG_DGNS_CD,
    ADMTG_DGNS_VRSN_CD,
    PRNCPAL_DGNS_CD,
    PRNCPAL_DGNS_VRSN_CD,
    ICD_DGNS_CD1,
    ICD_DGNS_VRSN_CD1,
    ICD_DGNS_CD2,
    ICD_DGNS_VRSN_CD2,
    ICD_DGNS_CD3,
    ICD_DGNS_VRSN_CD3,
    ICD_DGNS_CD4,
    ICD_DGNS_VRSN_CD4,
    ICD_DGNS_CD5,
    ICD_DGNS_VRSN_CD5,
    ICD_DGNS_CD6,
    ICD_DGNS_VRSN_CD6,
    ICD_DGNS_CD7,
    ICD_DGNS_VRSN_CD7,
    ICD_DGNS_CD8,
    ICD_DGNS_VRSN_CD8,
    ICD_DGNS_CD9,
    ICD_DGNS_VRSN_CD9,
    ICD_DGNS_CD10,
    ICD_DGNS_VRSN_CD10,
    ICD_DGNS_CD11,
    ICD_DGNS_VRSN_CD11,
    ICD_DGNS_CD12,
    ICD_DGNS_VRSN_CD12,
    ICD_DGNS_CD13,
    ICD_DGNS_VRSN_CD13,
    ICD_DGNS_CD14,
    ICD_DGNS_VRSN_CD14,
    ICD_DGNS_CD15,
    ICD_DGNS_VRSN_CD15,
    ICD_DGNS_CD16,
    ICD_DGNS_VRSN_CD16,
    ICD_DGNS_CD17,
    ICD_DGNS_VRSN_CD17,
    ICD_DGNS_CD18,
    ICD_DGNS_VRSN_CD18,
    ICD_DGNS_CD19,
    ICD_DGNS_VRSN_CD19,
    ICD_DGNS_CD20,
    ICD_DGNS_VRSN_CD20,
    ICD_DGNS_CD21,
    ICD_DGNS_VRSN_CD21,
    ICD_DGNS_CD22,
    ICD_DGNS_VRSN_CD22,
    ICD_DGNS_CD23,
    ICD_DGNS_VRSN_CD23,
    ICD_DGNS_CD24,
    ICD_DGNS_VRSN_CD24,
    ICD_DGNS_CD25,
    ICD_DGNS_VRSN_CD25,
    FST_DGNS_E_CD,
    FST_DGNS_E_VRSN_CD,
    ICD_DGNS_E_CD1,
    ICD_DGNS_E_VRSN_CD1,
    ICD_DGNS_E_CD2,
    ICD_DGNS_E_VRSN_CD2,
    ICD_DGNS_E_CD3,
    ICD_DGNS_E_VRSN_CD3,
    ICD_DGNS_E_CD4,
    ICD_DGNS_E_VRSN_CD4,
    ICD_DGNS_E_CD5,
    ICD_DGNS_E_VRSN_CD5,
    ICD_DGNS_E_CD6,
    ICD_DGNS_E_VRSN_CD6,
    ICD_DGNS_E_CD7,
    ICD_DGNS_E_VRSN_CD7,
    ICD_DGNS_E_CD8,
    ICD_DGNS_E_VRSN_CD8,
    ICD_DGNS_E_CD9,
    ICD_DGNS_E_VRSN_CD9,
    ICD_DGNS_E_CD10,
    ICD_DGNS_E_VRSN_CD10,
    ICD_DGNS_E_CD11,
    ICD_DGNS_E_VRSN_CD11,
    ICD_DGNS_E_CD12,
    ICD_DGNS_E_VRSN_CD12,
    ICD_PRCDR_CD1,
    ICD_PRCDR_VRSN_CD1,
    PRCDR_DT1,
    ICD_PRCDR_CD2,
    ICD_PRCDR_VRSN_CD2,
    PRCDR_DT2,
    ICD_PRCDR_CD3,
    ICD_PRCDR_VRSN_CD3,
    PRCDR_DT3,
    ICD_PRCDR_CD4,
    ICD_PRCDR_VRSN_CD4,
    PRCDR_DT4,
    ICD_PRCDR_CD5,
    ICD_PRCDR_VRSN_CD5,
    PRCDR_DT5,
    ICD_PRCDR_CD6,
    ICD_PRCDR_VRSN_CD6,
    PRCDR_DT6,
    ICD_PRCDR_CD7,
    ICD_PRCDR_VRSN_CD7,
    PRCDR_DT7,
    ICD_PRCDR_CD8,
    ICD_PRCDR_VRSN_CD8,
    PRCDR_DT8,
    ICD_PRCDR_CD9,
    ICD_PRCDR_VRSN_CD9,
    PRCDR_DT9,
    ICD_PRCDR_CD10,
    ICD_PRCDR_VRSN_CD10,
    PRCDR_DT10,
    ICD_PRCDR_CD11,
    ICD_PRCDR_VRSN_CD11,
    PRCDR_DT11,
    ICD_PRCDR_CD12,
    ICD_PRCDR_VRSN_CD12,
    PRCDR_DT12,
    ICD_PRCDR_CD13,
    ICD_PRCDR_VRSN_CD13,
    PRCDR_DT13,
    ICD_PRCDR_CD14,
    ICD_PRCDR_VRSN_CD14,
    PRCDR_DT14,
    ICD_PRCDR_CD15,
    ICD_PRCDR_VRSN_CD15,
    PRCDR_DT15,
    ICD_PRCDR_CD16,
    ICD_PRCDR_VRSN_CD16,
    PRCDR_DT16,
    ICD_PRCDR_CD17,
    ICD_PRCDR_VRSN_CD17,
    PRCDR_DT17,
    ICD_PRCDR_CD18,
    ICD_PRCDR_VRSN_CD18,
    PRCDR_DT18,
    ICD_PRCDR_CD19,
    ICD_PRCDR_VRSN_CD19,
    PRCDR_DT19,
    ICD_PRCDR_CD20,
    ICD_PRCDR_VRSN_CD20,
    PRCDR_DT20,
    ICD_PRCDR_CD21,
    ICD_PRCDR_VRSN_CD21,
    PRCDR_DT21,
    ICD_PRCDR_CD22,
    ICD_PRCDR_VRSN_CD22,
    PRCDR_DT22,
    ICD_PRCDR_CD23,
    ICD_PRCDR_VRSN_CD23,
    PRCDR_DT23,
    ICD_PRCDR_CD24,
    ICD_PRCDR_VRSN_CD24,
    PRCDR_DT24,
    ICD_PRCDR_CD25,
    ICD_PRCDR_VRSN_CD25,
    PRCDR_DT25,
    FI_DOC_CLM_CNTL_NUM,
    FI_ORIG_CLM_CNTL_NUM,
    CLM_LINE_NUM,
    REV_CNTR,
    HCPCS_CD,
    REV_CNTR_UNIT_CNT,
    REV_CNTR_RATE_AMT,
    REV_CNTR_TOT_CHRG_AMT,
    REV_CNTR_NCVRD_CHRG_AMT,
    REV_CNTR_DDCTBL_COINSRNC_CD,
    REV_CNTR_NDC_QTY,
    REV_CNTR_NDC_QTY_QLFR_CD,
    RNDRNG_PHYSN_UPIN,
    RNDRNG_PHYSN_NPI
  }

  private SNFFields[][] snfDxFields = {
    { SNFFields.ICD_DGNS_CD1, SNFFields.ICD_DGNS_VRSN_CD1 },
    { SNFFields.ICD_DGNS_CD2, SNFFields.ICD_DGNS_VRSN_CD2 },
    { SNFFields.ICD_DGNS_CD3, SNFFields.ICD_DGNS_VRSN_CD3 },
    { SNFFields.ICD_DGNS_CD4, SNFFields.ICD_DGNS_VRSN_CD4 },
    { SNFFields.ICD_DGNS_CD5, SNFFields.ICD_DGNS_VRSN_CD5 },
    { SNFFields.ICD_DGNS_CD6, SNFFields.ICD_DGNS_VRSN_CD6 },
    { SNFFields.ICD_DGNS_CD7, SNFFields.ICD_DGNS_VRSN_CD7 },
    { SNFFields.ICD_DGNS_CD8, SNFFields.ICD_DGNS_VRSN_CD8 },
    { SNFFields.ICD_DGNS_CD9, SNFFields.ICD_DGNS_VRSN_CD9 },
    { SNFFields.ICD_DGNS_CD10, SNFFields.ICD_DGNS_VRSN_CD10 },
    { SNFFields.ICD_DGNS_CD11, SNFFields.ICD_DGNS_VRSN_CD11 },
    { SNFFields.ICD_DGNS_CD12, SNFFields.ICD_DGNS_VRSN_CD12 },
    { SNFFields.ICD_DGNS_CD13, SNFFields.ICD_DGNS_VRSN_CD13 },
    { SNFFields.ICD_DGNS_CD14, SNFFields.ICD_DGNS_VRSN_CD14 },
    { SNFFields.ICD_DGNS_CD15, SNFFields.ICD_DGNS_VRSN_CD15 },
    { SNFFields.ICD_DGNS_CD16, SNFFields.ICD_DGNS_VRSN_CD16 },
    { SNFFields.ICD_DGNS_CD17, SNFFields.ICD_DGNS_VRSN_CD17 },
    { SNFFields.ICD_DGNS_CD18, SNFFields.ICD_DGNS_VRSN_CD18 },
    { SNFFields.ICD_DGNS_CD19, SNFFields.ICD_DGNS_VRSN_CD19 },
    { SNFFields.ICD_DGNS_CD20, SNFFields.ICD_DGNS_VRSN_CD20 },
    { SNFFields.ICD_DGNS_CD21, SNFFields.ICD_DGNS_VRSN_CD21 },
    { SNFFields.ICD_DGNS_CD22, SNFFields.ICD_DGNS_VRSN_CD22 },
    { SNFFields.ICD_DGNS_CD23, SNFFields.ICD_DGNS_VRSN_CD23 },
    { SNFFields.ICD_DGNS_CD24, SNFFields.ICD_DGNS_VRSN_CD24 },
    { SNFFields.ICD_DGNS_CD25, SNFFields.ICD_DGNS_VRSN_CD25 }
  };

  private SNFFields[][] snfPxFields = {
    { SNFFields.ICD_PRCDR_CD1, SNFFields.ICD_PRCDR_VRSN_CD1,  SNFFields.PRCDR_DT1 },
    { SNFFields.ICD_PRCDR_CD2, SNFFields.ICD_PRCDR_VRSN_CD2, SNFFields.PRCDR_DT2 },
    { SNFFields.ICD_PRCDR_CD3, SNFFields.ICD_PRCDR_VRSN_CD3, SNFFields.PRCDR_DT3 },
    { SNFFields.ICD_PRCDR_CD4, SNFFields.ICD_PRCDR_VRSN_CD4, SNFFields.PRCDR_DT4 },
    { SNFFields.ICD_PRCDR_CD5, SNFFields.ICD_PRCDR_VRSN_CD5, SNFFields.PRCDR_DT5 },
    { SNFFields.ICD_PRCDR_CD6, SNFFields.ICD_PRCDR_VRSN_CD6, SNFFields.PRCDR_DT6 },
    { SNFFields.ICD_PRCDR_CD7, SNFFields.ICD_PRCDR_VRSN_CD7, SNFFields.PRCDR_DT7 },
    { SNFFields.ICD_PRCDR_CD8, SNFFields.ICD_PRCDR_VRSN_CD8, SNFFields.PRCDR_DT8 },
    { SNFFields.ICD_PRCDR_CD9, SNFFields.ICD_PRCDR_VRSN_CD9, SNFFields.PRCDR_DT9 },
    { SNFFields.ICD_PRCDR_CD10, SNFFields.ICD_PRCDR_VRSN_CD10, SNFFields.PRCDR_DT10 },
    { SNFFields.ICD_PRCDR_CD11, SNFFields.ICD_PRCDR_VRSN_CD11, SNFFields.PRCDR_DT11 },
    { SNFFields.ICD_PRCDR_CD12, SNFFields.ICD_PRCDR_VRSN_CD12, SNFFields.PRCDR_DT12 },
    { SNFFields.ICD_PRCDR_CD13, SNFFields.ICD_PRCDR_VRSN_CD13, SNFFields.PRCDR_DT13 },
    { SNFFields.ICD_PRCDR_CD14, SNFFields.ICD_PRCDR_VRSN_CD14, SNFFields.PRCDR_DT14 },
    { SNFFields.ICD_PRCDR_CD15, SNFFields.ICD_PRCDR_VRSN_CD15, SNFFields.PRCDR_DT15 },
    { SNFFields.ICD_PRCDR_CD16, SNFFields.ICD_PRCDR_VRSN_CD16, SNFFields.PRCDR_DT16 },
    { SNFFields.ICD_PRCDR_CD17, SNFFields.ICD_PRCDR_VRSN_CD17, SNFFields.PRCDR_DT17 },
    { SNFFields.ICD_PRCDR_CD18, SNFFields.ICD_PRCDR_VRSN_CD18, SNFFields.PRCDR_DT18 },
    { SNFFields.ICD_PRCDR_CD19, SNFFields.ICD_PRCDR_VRSN_CD19, SNFFields.PRCDR_DT19 },
    { SNFFields.ICD_PRCDR_CD20, SNFFields.ICD_PRCDR_VRSN_CD20, SNFFields.PRCDR_DT20 },
    { SNFFields.ICD_PRCDR_CD21, SNFFields.ICD_PRCDR_VRSN_CD21, SNFFields.PRCDR_DT21 },
    { SNFFields.ICD_PRCDR_CD22, SNFFields.ICD_PRCDR_VRSN_CD22, SNFFields.PRCDR_DT22 },
    { SNFFields.ICD_PRCDR_CD23, SNFFields.ICD_PRCDR_VRSN_CD23, SNFFields.PRCDR_DT23 },
    { SNFFields.ICD_PRCDR_CD24, SNFFields.ICD_PRCDR_VRSN_CD24, SNFFields.PRCDR_DT24 },
    { SNFFields.ICD_PRCDR_CD25, SNFFields.ICD_PRCDR_VRSN_CD25, SNFFields.PRCDR_DT25 }
  };

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
  private static class SynchronizedBBLineWriter {
    
    private String bbFieldSeparator = "|";
    private Path path;
    
    /**
     * Construct a new instance. Fields will be separated using the default '|' character.
     * @param path the file path to write to
     * @throws IOException if something goes wrong
     */
    public SynchronizedBBLineWriter(Path path) {
      this.path = path;
    }

    /**
     * Construct a new instance.
     * @param path the file path to write to
     * @param separator overrides the default '|' field separator
     * @throws IOException if something goes wrong
     */
    public SynchronizedBBLineWriter(Path path, String separator) {
      this.bbFieldSeparator = separator;
      this.path = path;
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
     * Write a BB2 writer header.
     * @param enumClass the enumeration class whose members define the column names
     * @throws IOException if something goes wrong
     */
    public <E extends Enum<E>> void writeHeader(Class<E> enumClass) throws IOException {
      String[] fields = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name)
              .toArray(String[]::new);
      writeLine(fields);
    }

    /**
     * Write a BB2 writer line.
     * @param enumClass the enumeration class whose members define the column names
     * @param fieldValues a sparse map of column names to values, missing values will result in
     *     empty values in the corresponding column
     * @throws IOException if something goes wrong 
     */
    public <E extends Enum<E>> void writeValues(Class<E> enumClass, Map<E, String> fieldValues)
            throws IOException {
      String[] fields = Arrays.stream(enumClass.getEnumConstants())
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
      Class<?>[] tableEnums = {
        BeneficiaryFields.class,
        BeneficiaryHistoryFields.class,
        InpatientFields.class,
        OutpatientFields.class,
        CarrierFields.class,
        PrescriptionFields.class,
        DMEFields.class
      };
      for (Class tableEnum: tableEnums) {
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
            tsvIssues.add(String.format(
                    "Empty cell in TSV line %s [%s] for %s",
                    row.get("Line"), row.get("Field"), columnName));
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
      if (this.value != other.value) {
        return false;
      }
      return true;
    }
  }
  
  private static class BeneficiaryWriters {
    private final TreeMap<Integer, SynchronizedBBLineWriter> writers;
    private final Path dir;
    
    public BeneficiaryWriters(Path dir) {
      this.dir = dir;
      this.writers = new TreeMap<>();
    }
    
    public synchronized SynchronizedBBLineWriter getWriter(int year) throws IOException {
      SynchronizedBBLineWriter writer = writers.get(year);
      if (writer == null) {
        Path beneficiaryFile = dir.resolve(String.format("beneficiary_%d.csv", year));
        writer = new SynchronizedBBLineWriter(beneficiaryFile);
        writers.put(year, writer);
        writer.writeHeader(BeneficiaryFields.class);        
      }
      return writer;
    }
    
    public synchronized List<File> getFiles() {
      ArrayList<File> list = new ArrayList<>(writers.size());
      writers.values().forEach(writer -> {
        list.add(writer.getFile());
      });
      return list;
    }
  }
}
