package org.mitre.synthea.export.rif;

import com.google.common.collect.Table;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.export.rif.BB2RIFStructure.EXPORT_SUMMARY;
import org.mitre.synthea.export.rif.BB2RIFStructure.NPI;
import org.mitre.synthea.export.rif.tools.StaticFieldConfig;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
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

  final RifWriters rifWriters;
  final StaticFieldConfig staticFieldConfig;

  final CodeMapper conditionCodeMapper;
  final CodeMapper medicationCodeMapper;
  final CodeMapper drgCodeMapper;
  CodeMapper dmeCodeMapper; // not final so we can mock it for tests
  final CodeMapper hcpcsCodeMapper;
  final CodeMapper betosCodeMapper;
  final CodeMapper snfPPSMapper;
  final CodeMapper snfPDPMMapper;
  final CodeMapper snfRevCntrMapper;
  final CodeMapper hhaRevCntrMapper;
  final CodeMapper hospiceRevCntrMapper;
  final CodeMapper inpatientRevCntrMapper;
  final CodeMapper outpatientRevCntrMapper;
  final Map<String, RandomCollection<String>> externalCodes;
  final RandomCollection<String> hhaCaseMixCodes;
  final RandomCollection<String> hhaPDGMCodes;
  final CMSStateCodeMapper locationMapper;
  final BeneficiaryExporter beneExp;
  final InpatientExporter inpatientExp;
  final OutpatientExporter outpatientExp;
  final CarrierExporter carrierExp;
  final PDEExporter pdeExp;
  final DMEExporter dmeExp;
  final HHAExporter hhaExp;
  final HospiceExporter hospiceExp;
  final SNFExporter snfExp;


  /**
   * Create the output folder and files. Write headers to each file.
   */
  private BB2RIFExporter() {
    conditionCodeMapper = new CodeMapper("export/condition_code_map.json");
    medicationCodeMapper = new CodeMapper("export/medication_code_map.json");
    drgCodeMapper = new CodeMapper("export/drg_code_map.json");
    dmeCodeMapper = new CodeMapper("export/dme_code_map.json");
    hcpcsCodeMapper = new CodeMapper("export/hcpcs_code_map.json");
    betosCodeMapper = new CodeMapper("export/betos_code_map.json");
    snfPPSMapper = new CodeMapper("export/snf_pps_code_map.json");
    snfPDPMMapper = new CodeMapper("export/snf_pdpm_code_map.json");
    snfRevCntrMapper = new CodeMapper("export/snf_rev_cntr_code_map.json");
    hhaRevCntrMapper = new CodeMapper("export/hha_rev_cntr_code_map.json");
    hospiceRevCntrMapper = new CodeMapper("export/hospice_rev_cntr_code_map.json");
    inpatientRevCntrMapper = new CodeMapper("export/inpatient_rev_cntr_code_map.json");
    outpatientRevCntrMapper = new CodeMapper("export/outpatient_rev_cntr_code_map.json");
    locationMapper = new CMSStateCodeMapper();
    externalCodes = loadExternalCodes();
    hhaCaseMixCodes = loadPPSCodes("export/hha_pps_case_mix_codes.csv");
    hhaPDGMCodes = loadPPSCodes("export/hha_pps_pdgm_codes.csv");
    try {
      staticFieldConfig = new StaticFieldConfig();
      rifWriters = prepareOutputFiles();
      for (String tsvIssue: staticFieldConfig.validateTSV()) {
        System.out.println(tsvIssue);
      }
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
    beneExp = new BeneficiaryExporter(this);
    inpatientExp = new InpatientExporter(this);
    outpatientExp = new OutpatientExporter(this);
    carrierExp = new CarrierExporter(this);
    pdeExp = new PDEExporter(this);
    dmeExp = new DMEExporter(this);
    hhaExp = new HHAExporter(this);
    hospiceExp = new HospiceExporter(this);
    snfExp = new SNFExporter(this);
  }

  private static Map<String, RandomCollection<String>> loadExternalCodes() {
    Map<String, RandomCollection<String>> data = new HashMap<>();
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

  private static RandomCollection<String> loadPPSCodes(String resourcePath) {
    RandomCollection<String> codes = new RandomCollection<>();
    try {
      String fileData = Utilities.readResourceAndStripBOM(resourcePath);
      List<LinkedHashMap<String, String>> csv = SimpleCSV.parse(fileData);
      for (LinkedHashMap<String, String> row : csv) {
        String code = row.get("code");
        long count = Long.parseLong(row.get("count"));
        codes.add((double) count, code);
      }
    } catch (Exception e) {
      if (Config.getAsBoolean("exporter.bfd.require_code_maps", true)) {
        throw new MissingResourceException(
            "Unable to read PPS code file",
            "BB2RIFExporter", resourcePath);
      } else {
        // For testing, the external codes are not present.
        System.out.printf("BB2RIFExporter is running without '%s'\n", resourcePath);
      }
      return null;
    }
    return codes;
  }

  <E extends Enum<E>> void setExternalCode(Person person,
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

  <E extends Enum<E>> boolean setExternalCode(Person person,
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


  /**
   * Create the output folder. Files will be added as needed.
   */
  final RifWriters prepareOutputFiles() throws IOException {
    // Initialize output writers
    File output = Exporter.getOutputFolder("bfd", null);
    output.mkdirs();
    Path outputDirectory = output.toPath();

    return new RifWriters(outputDirectory);
  }

  private static DateTimeFormatter MANIFEST_TIMESTAMP_FORMAT
          = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

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
    manifest.write("<dataSetManifest xmlns=\"http://cms.hhs.gov/bluebutton/api/schema/ccw-rif/v10\"");
    manifest.write(String.format(" timestamp=\"%s\" ",
             java.time.Instant.now()
                     .atZone(ZoneOffset.UTC)
                     .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                     .format(MANIFEST_TIMESTAMP_FORMAT)));
    manifest.write("sequenceId=\"0\" syntheticData=\"true\">\n");
    for (Class<?> rifFile: BB2RIFStructure.RIF_FILES) {
      for (int year: rifWriters.getYears()) {
        // generics and arrays are weird so need to cast below rather than declare on the array
        Class<? extends Enum> rifEnum = (Class<? extends Enum>) rifFile;
        SynchronizedBBLineWriter writer = rifWriters.getWriter(rifEnum, year);
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
            -1, "tsv", "\t");

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
        for (List<Clinician> docs: clinicians.values()) {
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
    endState.setProperty("exporter.bfd.bene_id_start", BeneficiaryExporter.nextBeneId.toString());
    endState.setProperty("exporter.bfd.clm_id_start", RIFExporter.nextClaimId.toString());
    endState.setProperty("exporter.bfd.clm_grp_id_start", RIFExporter.nextClaimGroupId.toString());
    endState.setProperty("exporter.bfd.pde_id_start", PDEExporter.nextPdeId.toString());
    endState.setProperty("exporter.bfd.mbi_start", BeneficiaryExporter.nextMbi.toString());
    endState.setProperty("exporter.bfd.hicn_start", BeneficiaryExporter.nextHicn.toString());
    endState.setProperty("exporter.bfd.fi_doc_cntl_num_start",
            RIFExporter.nextFiDocCntlNum.toString());
    endState.setProperty("exporter.bfd.carr_clm_cntl_num_start",
            CarrierExporter.nextCarrClmCntlNum.toString());
    File outputDir = Exporter.getOutputFolder("bfd", null);
    FileOutputStream f = new FileOutputStream(new File(outputDir, "end_state.properties"));
    endState.store(f, "BFD Properties End State");
    f.close();
  }

  /**
   * Export codes that were not mappable during export.
   * These missing codes might be accidental, they may be intentional.
   */
  public void exportMissingCodes() throws IOException {
    if (Config.getAsBoolean("exporter.bfd.export_missing_codes", true)) {
      List<Map<String, String>> allMissingCodes = new LinkedList<>();
      allMissingCodes.addAll(conditionCodeMapper.getMissingCodes());
      allMissingCodes.addAll(medicationCodeMapper.getMissingCodes());
      allMissingCodes.addAll(drgCodeMapper.getMissingCodes());
      allMissingCodes.addAll(dmeCodeMapper.getMissingCodes());
      allMissingCodes.addAll(hcpcsCodeMapper.getMissingCodes());
      allMissingCodes.addAll(betosCodeMapper.getMissingCodes());
      allMissingCodes.addAll(snfPPSMapper.getMissingCodes());
      allMissingCodes.addAll(snfPDPMMapper.getMissingCodes());
      allMissingCodes.addAll(snfRevCntrMapper.getMissingCodes());
      allMissingCodes.addAll(hhaRevCntrMapper.getMissingCodes());
      allMissingCodes.addAll(hospiceRevCntrMapper.getMissingCodes());

      File outputDir = Exporter.getOutputFolder("bfd", null);
      if (!allMissingCodes.isEmpty()) {
        Files.write(outputDir.toPath().resolve("missing_codes.csv"),
                SimpleCSV.unparse(allMissingCodes).getBytes());
      }
    }
  }

  /**
   * Export a single person.
   * @param person the person to export
   * @param stopTime end time of simulation
   * @param yearsOfHistory number of years of claims to export
   * @throws IOException if something goes wrong
   */
  public boolean export(Person person, long stopTime, int yearsOfHistory) throws IOException {
    Map<EXPORT_SUMMARY, String> exportCounts = new HashMap<>();
    long startTime = stopTime - Utilities.convertTime("years", yearsOfHistory);
    if (yearsOfHistory == 0) {
      startTime = (long) person.attributes.get(Person.BIRTHDATE);
    }
    String beneId = beneExp.export(person, startTime, stopTime);
    if (beneId == null) {
      // was not a medicare beneficiary
      return false;
    }
    exportCounts.put(EXPORT_SUMMARY.BENE_ID, beneId);
    exportCounts.put(EXPORT_SUMMARY.INPATIENT_CLAIMS,
            Long.toString(inpatientExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.OUTPATIENT_CLAIMS,
            Long.toString(outpatientExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.CARRIER_CLAIMS,
            Long.toString(carrierExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.PDE_CLAIMS,
            Long.toString(pdeExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.DME_CLAIMS,
            Long.toString(dmeExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.HHA_CLAIMS,
            Long.toString(hhaExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.HOSPICE_CLAIMS,
            Long.toString(hospiceExp.export(person, startTime, stopTime)));
    exportCounts.put(EXPORT_SUMMARY.SNF_CLAIMS,
            Long.toString(snfExp.export(person, startTime, stopTime)));
    rifWriters.getOrCreateWriter(EXPORT_SUMMARY.class, -1, "csv", ",").writeValues(exportCounts);
    return true;
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the BB2RIFExporter.
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
}