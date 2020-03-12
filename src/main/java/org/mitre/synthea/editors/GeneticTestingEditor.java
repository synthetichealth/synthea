package org.mitre.synthea.editors;

import static org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.MedicalCategory.*;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.MedicalCategory;
import org.mitre.synthea.engine.StatefulHealthRecordEditor;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

/**
 * Post-processor for health records to add genetic testing results for patients with
 * cardiovascular health conditions that match a set of genetic testing triggers.
 * @author mhadley
 */
public class GeneticTestingEditor extends StatefulHealthRecordEditor {
  
  protected static final String PRIOR_GENETIC_TESTING = "PRIOR_GENETIC_TESTING";
  static final String GENETIC_TESTING_REPORT_TYPE = "Genetic analysis summary panel";
  private static final double GENETIC_TESTING_THRESHOLD = 0.80;
  static final Map<String, List<MedicalCategory>> TRIGGER_CONDITIONS = init();
  
  static Map<String, List<MedicalCategory>> init() {
    Map<String, List<MedicalCategory>> triggerConditions = new HashMap<>();
    triggerConditions.put("stroke", Arrays.asList(STROKE, HEMORRHAGIC_STROKE,
        ISCHEMIC_STROKE, ANEURYSM, THROMBOSIS));
    triggerConditions.put("coronary_heart_disease", Arrays.asList(
        ARTERIAL_OCCLUSIVE_DISEASE, CARDIOMYOPATHY, CARDIOVASCULAR, 
        DILATED_CARDIOMYOPATHY, FAMILIAL_DILATED_CARDIOMYOPATHY, HEART_DISEASE,
        HEART_VALVE_DISEASE, NONCOMPACTION_CARDIOMYOPATHY,
        RESTRICTIVE_CARDIOMYOPATHY));
    triggerConditions.put("myocardial_infarction", Arrays.asList(CARDIAC_ARREST));
    triggerConditions.put("cardiac_arrest", Arrays.asList(CARDIAC_ARREST));
    triggerConditions.put("atrial_fibrillation", Arrays.asList(ARRHYTHMIA,
        LONG_QT_SYNDROME, SHORT_QT_SYNDROME));
    triggerConditions.put("cardiovascular_disease", Arrays.asList(
        ARTERIAL_OCCLUSIVE_DISEASE, CARDIOMYOPATHY, CARDIOVASCULAR, 
        DILATED_CARDIOMYOPATHY, FAMILIAL_DILATED_CARDIOMYOPATHY, HEART_DISEASE,
        HEART_VALVE_DISEASE, NONCOMPACTION_CARDIOMYOPATHY,
        RESTRICTIVE_CARDIOMYOPATHY));
    return triggerConditions;
  };

  @Override
  public boolean shouldRun(Person person, HealthRecord record, long time) {
    // Not all patients will get genetic testing
    return shouldRun(person, record) && person.rand() >= GENETIC_TESTING_THRESHOLD;
  }
  
  boolean shouldRun(Person person, HealthRecord record) {
    Map<String, Object> context = this.getOrInitContextFor(person);

    // Don't do genetic testing if it has already been done
    if (context.get(PRIOR_GENETIC_TESTING) != null) {
      return false;
    }

    // Check for trigger conditions
    boolean hasActiveTriggerCondition = false;
    for (String triggerCondition: TRIGGER_CONDITIONS.keySet()) {
      if (record.conditionActive(triggerCondition)) {
        hasActiveTriggerCondition = true;
        break;
      }
    }
    return hasActiveTriggerCondition;
  }
  
  static Set<MedicalCategory> getGeneticCategories(HealthRecord record) {
    Set<MedicalCategory> categories = new HashSet<>();
    for (String triggerCondition: TRIGGER_CONDITIONS.keySet()) {
      if (record.conditionActive(triggerCondition)) {
        categories.addAll(TRIGGER_CONDITIONS.get(triggerCondition));
      }
    }
    return categories;
  }
  
  static MedicalCategory[] getGeneticCategories(Person person) {
    HashSet<MedicalCategory> categories = new HashSet<>();
    categories.addAll(getGeneticCategories(person.defaultRecord));
    if (person.hasMultipleRecords) {
      for (HealthRecord record: person.records.values()) {
        categories.addAll(getGeneticCategories(record));
      }
    }
    
    MedicalCategory[] array = categories.stream()
        .toArray(n -> new MedicalCategory[n]);
    return array;
  }

  @Override
  public void process(Person person, List<HealthRecord.Encounter> encounters, 
      long time, Random random) {
    if (encounters.isEmpty()) {
      return;
    }
    
    MedicalCategory[] categories = getGeneticCategories(person);
    DnaSynthesisConfig cfg = new DnaSynthesisConfig(
        DnaSynthesisConfig.Population.AFR, categories);
                      
    List<HealthRecord.Observation> observations = new ArrayList<>(10);
    // TODO create list of observations by invoking dna_synthesis application
    //    HealthRecord.Observation observation = person.record.new Observation(time, 
    //        GENETIC_TESTING_REPORT_TYPE, null);
    //    observation.codes.add(new Code("LOINC", "55232-3", 
    //        GENETIC_TESTING_REPORT_TYPE));
    //    observations.add(observation);
    HealthRecord.Report geneticTestingReport = person.record.new Report(time, 
        GENETIC_TESTING_REPORT_TYPE, observations);
    geneticTestingReport.codes.add(new Code("LOINC", "55232-3", 
        GENETIC_TESTING_REPORT_TYPE));
    HealthRecord.Encounter encounter = encounters.get(0);
    encounter.reports.add(geneticTestingReport);
    Map<String, Object> context = this.getOrInitContextFor(person);
    context.put(PRIOR_GENETIC_TESTING, time);
  }
  
  /**
   * Wrapper class for invoking the dna_synthesis Python script and extracting
   * the resulting genetic markers.
   * @author mhadley
   */
  static class DnaSynthesisWrapper {
    private final DnaSynthesisConfig config;
    private final File script;
    public static String DNA_SYNTHESIS_SCRIPT = "genetictesting.script";

    public DnaSynthesisWrapper(DnaSynthesisConfig config) {
      this.config = config;
      this.script = new File(Config.get(DNA_SYNTHESIS_SCRIPT));
    }

    public List<GeneticMarker> execute() throws IOException, InterruptedException {
      File outputFile = invoke();
      return loadOutputFile(outputFile);
    }

    static List<GeneticMarker> loadOutputFile(File outputFile) throws FileNotFoundException {
      TsvParserSettings settings = new TsvParserSettings();
      settings.getFormat().setLineSeparator("\n");
      TsvParser parser = new TsvParser(settings);
      List<String[]> allRows = parser.parseAll(new FileReader(outputFile));
      int numColumns = allRows.get(0).length;

      ArrayList<GeneticMarker> markers = new ArrayList<>();
      for (int i = 1; i < numColumns; i++) {
        GeneticMarker m = new GeneticMarker(
            allRows.get(0)[i], // index
            allRows.get(1)[i], // chromosome
            allRows.get(2)[i], // location
            allRows.get(3)[i], // strand
            allRows.get(4)[i], // ancestral allele
            allRows.get(5)[i], // variant allele list
            allRows.get(6)[i], // gene
            allRows.get(13)[i], // clinical significance
            allRows.get(14)[i]  // allele
        );
        markers.add(m);
      }
      return markers;
    }

    synchronized File invoke() throws IOException, InterruptedException {
      // Create a new file to hold the script output and create the config
      // file required by the script
      File outputFile = File.createTempFile("dna_", ".tsv");
      config.individualFilename = outputFile.getAbsolutePath();
      File cfgFile = createConfigFile();

      // Run the Python wrapper shell script
      ProcessBuilder pb = new ProcessBuilder();
      pb.command("sh", script.getAbsolutePath(), cfgFile.getAbsolutePath(), 
              outputFile.getAbsolutePath());
      Process p = pb.start();

      // Capture the STDOUT of the script in case there's an error
      StringBuilder output = new StringBuilder();
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(p.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line + "\n");
      }

      // Capture the STDERR of the script in case there's an error
      reader = new BufferedReader(
          new InputStreamReader(p.getErrorStream()));
      while ((line = reader.readLine()) != null) {
        output.append(line + "\n");
      }

      // wait for the script to finish and check for succesful exit code
      int exitCode = p.waitFor();
      if (exitCode != 0) {
        throw new IOException(output.toString());
      }

      return outputFile;
    }

    private File createConfigFile() throws IOException {
      GsonBuilder builder = new GsonBuilder(); 
      builder.setPrettyPrinting();
      builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
      Gson gson = builder.create();

      File cfgFile = File.createTempFile("dna_synth_cfg_", ".json");
      BufferedWriter bw = new BufferedWriter(new FileWriter(cfgFile));
      bw.write(gson.toJson(config));
      bw.close();

      return cfgFile;
    }
  }
  
  /**
   * Simple bean for creating the config file needed by the Python dna-synthesis code.
   * @author mhadley
   */
  static class DnaSynthesisConfig {
    public String individualName = "John Doe3";
    public boolean randomPopulation = false;
    public Population population = null;
    public boolean useDefined = false;
    public String[] definedMarkers = null;
    public MedicalCategory[] medicalCategories = null;
    public String individualFilename = null;

    public enum Population { AFR, AMR, EAS, EUR, SAS }

    public enum MedicalCategory {
      ANEURYSM("Aneurysm"),
      AORTIC_ANEURYSM("Aortic Aneurysm"),
      ARRHYTHMIA("Arrhythmia"),
      ARRHYTHMOGENIC_RIGHT_VENTRICULAR_DYSPLASIA("Arrhythmogenic Right Ventricular Dysplasia"),
      ARTERIAL_OCCLUSIVE_DISEASE("Arterial Occlusive Disease"),
      BRUGADA_SYNDROME("Brugada Syndrome"),
      CARDIAC_AMYLOIDOSIS("Cardiac Amyloidosis"),
      CARDIAC_ARREST("Cardiac Arrest"),
      CARDIOMYOPATHY("Cardiomyopathy"),
      CARDIOVASCULAR("Cardiovascular"),
      DILATED_CARDIOMYOPATHY("Dilated Cardiomyopathy"),
      DSYSLIPIDEMIA("Dsyslipidemia"),
      ELEVATED_TRIGLYCERIDES("Elevated Triglycerides"),
      FAMILIAL_DILATED_CARDIOMYOPATHY("Familial Dilated Cardiomyopathy"),
      FAMILIAL_HYPERCHOLESTEROLEMIA("Familial Hypercholesterolemia"),
      HDL("HDL"),
      HEART_DISEASE("Heart Disease"),
      HEART_VALVE_DISEASE("Heart Valve Disease"),
      HEMORRHAGIC_STROKE("Hemorrhagic Stroke"),
      HIGH_BLOOD_PRESSURE("High Blood Pressure"),
      HYPERCHOLESTEROLEMIA("Hypercholesterolemia"),
      HYPERTENSION("Hypertension"),
      INSULIN_RESISTANCE("Insulin Resistance"),
      ISCHEMIC_STROKE("Ischemic Stroke"),
      LDL("LDL"),
      LONG_QT_SYNDROME("Long QT Syndrome"),
      MARFAN_SYNDROME("Marfan Syndrome"),
      MITRAL("Mitral"),
      MITRAL_VALVE_PROLAPSE("Mitral Valve prolapse"),
      NONCOMPACTION_CARDIOMYOPATHY("Noncompaction Cardiomyopathy"),
      OBESITY("Obesity"),
      PROLAPSE("Prolapse"),
      PULMONARY_HYPERTENSION("Pulmonary Hypertension"),
      RESTRICTIVE_CARDIOMYOPATHY("Restrictive Cardiomyopathy"),
      SHORT_QT_SYNDROME("Short QT Syndrome"),
      STROKE("Stroke"),
      THROMBOSIS("Thrombosis");

      private String description;

      MedicalCategory(String description) {
        this.description = description;
      }

      public String getDescription() {
        return description;
      }

      @Override
      public String toString() {
        return description;
      }
    }

    public DnaSynthesisConfig(Population population, MedicalCategory[] medicalCategories) {
      this.population = population;
      this.medicalCategories = medicalCategories;
    }
  }
  
  /**
   * Bean class for processing genetic markers.
   * @author mhadley
   */
  static class GeneticMarker {
    private static final Map<String, List<DnaSynthesisConfig.MedicalCategory>> INDEX_MAP = 
        initialize();

    public String index;
    public String indexPrefix;
    public String chromosome;
    public String location;
    public String strand;
    public String ancestralAllele;
    public String variantAlleleList;
    public String gene;
    public String clinicalSignificance;
    public String allele;
    private boolean variant;

    public GeneticMarker(String index, String chromosome, String location, String strand, 
            String ancestralAllele, String variantAlleleList, String gene, 
            String clinicalSignificance, String allele) {
      this.index = index.trim();
      this.indexPrefix = index.split("_")[0];
      this.chromosome = chromosome.trim();
      this.location = location.trim();
      this.strand = strand.trim();
      this.ancestralAllele = ancestralAllele.trim();
      this.variantAlleleList = variantAlleleList.trim();
      this.gene = gene.trim();
      this.clinicalSignificance = clinicalSignificance.trim();
      this.allele = allele.trim();
      this.variant = this.allele.contains(">");
    }

    static final String PATHOGENIC_CLINICAL_SIGNIFICANCE = "Pathogenic";
    static final String LIKELY_PATHOGENIC_CLINICAL_SIGNIFICANCE = "Likely Pathogenic";
    static final String UNCERTAIN_CLINICAL_SIGNIFICANCE = "Uncertain";
    static final String RISK_FACTOR_CLINICAL_SIGNIFICANCE = "Risk Factor";
    static final String ASSOCIATION_CLINICAL_SIGNIFICANCE = "Association";
    static final String BENIGN_CLINICAL_SIGNIFICANCE = "Benign";
    static final String LIKELY_BENIGN_CLINICAL_SIGNIFICANCE = "Likely Benign";
    static final String DRUG_RESPONSE_CLINICAL_SIGNIFICANCE = "Drug Response";

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("The ");
      sb.append(gene);
      sb.append(" gene ");
      if (isVariant()) {
        sb.append("exhibits a variation of '");
        sb.append(clinicalSignificance);
        sb.append("' clinical significance. The variation at index ");
        sb.append(index);
        sb.append(" is associated with an increased risk of: ");
        List<DnaSynthesisConfig.MedicalCategory> associatedConditions = 
                GeneticMarker.INDEX_MAP.get(index);
        for (int i = 0; i < associatedConditions.size(); i++) {
          if (i > 0) {
            if (i < associatedConditions.size() - 1) {
              sb.append(", ");
            } else {
              sb.append(" and ");
            }
          }
          sb.append(associatedConditions.get(i));
        }
        sb.append(".");
      } else {
        sb.append("does not exhibit any variation.");
      }
      return sb.toString();
    }
    
    boolean isVariant() {
      return variant;
    }

    boolean isPathogenicVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(PATHOGENIC_CLINICAL_SIGNIFICANCE);
    }

    boolean isLikelyPathogenicVariant() {
      return variant 
              && clinicalSignificance.equalsIgnoreCase(LIKELY_PATHOGENIC_CLINICAL_SIGNIFICANCE);
    }

    boolean isUncertainVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(UNCERTAIN_CLINICAL_SIGNIFICANCE);
    }

    boolean isRiskFactorVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(RISK_FACTOR_CLINICAL_SIGNIFICANCE);
    }

    boolean isAssociationVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(ASSOCIATION_CLINICAL_SIGNIFICANCE);
    }

    boolean isBenignVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(BENIGN_CLINICAL_SIGNIFICANCE);
    }

    boolean isLikelyBenignVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(LIKELY_BENIGN_CLINICAL_SIGNIFICANCE);
    }

    boolean isDrugResponseVariant() {
      return variant && clinicalSignificance.equalsIgnoreCase(DRUG_RESPONSE_CLINICAL_SIGNIFICANCE);
    }

    boolean isAssociatedWith(DnaSynthesisConfig.MedicalCategory category) {
      List<DnaSynthesisConfig.MedicalCategory> associatedCategories = 
              getAssociatedMedicalCategories();
      if (associatedCategories != null) {
        return associatedCategories.contains(category);
      } else {
        return false;
      }
    }

    boolean hasAssociatedMedicalCatgory() {
      List<DnaSynthesisConfig.MedicalCategory> associatedCategories = 
              getAssociatedMedicalCategories();
      return !(associatedCategories == null || associatedCategories.isEmpty());
    }

    List<DnaSynthesisConfig.MedicalCategory> getAssociatedMedicalCategories() {
      return INDEX_MAP.get(indexPrefix);
    }

    private static Map<String, List<MedicalCategory>> initialize() {
      // extracted from Python dna_synthesis config at
      // dna-synthesis/populations_and_individuals/Data/cvd_families_byVar.tsv
      Map<String, List<DnaSynthesisConfig.MedicalCategory>> indexMap = new HashMap<>();
      indexMap.put("rs10757272", Arrays.asList(ANEURYSM));
      indexMap.put("rs10958409", Arrays.asList(ANEURYSM));
      indexMap.put("rs700651", Arrays.asList(ANEURYSM));
      indexMap.put("rs9298506", Arrays.asList(ANEURYSM));
      indexMap.put("rs9315204", Arrays.asList(ANEURYSM));
      indexMap.put("rs111671429", Arrays.asList(AORTIC_ANEURYSM, MARFAN_SYNDROME));
      indexMap.put("rs1421085", Arrays.asList(AORTIC_ANEURYSM, INSULIN_RESISTANCE));
      indexMap.put("rs2118181", Arrays.asList(AORTIC_ANEURYSM));
      indexMap.put("rs7025486", Arrays.asList(AORTIC_ANEURYSM, ANEURYSM, PULMONARY_HYPERTENSION,
              ISCHEMIC_STROKE));
      indexMap.put("rs16847548", Arrays.asList(ARRHYTHMIA));
      indexMap.put("rs2076295", Arrays.asList(ARRHYTHMOGENIC_RIGHT_VENTRICULAR_DYSPLASIA));
      indexMap.put("rs172149856", Arrays.asList(BRUGADA_SYNDROME));
      indexMap.put("rs201907325", Arrays.asList(BRUGADA_SYNDROME));
      indexMap.put("rs104894368", Arrays.asList(CARDIOMYOPATHY));
      indexMap.put("rs190228518", Arrays.asList(CARDIOMYOPATHY));
      indexMap.put("rs376923877", Arrays.asList(CARDIOMYOPATHY));
      indexMap.put("rs10306114", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs1041740", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs10507391", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs10757274", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE, HEART_DISEASE,
              ANEURYSM, AORTIC_ANEURYSM));
      indexMap.put("rs10757278", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE, AORTIC_ANEURYSM,
              ISCHEMIC_STROKE));
      indexMap.put("rs1137617", Arrays.asList(CARDIOVASCULAR, LONG_QT_SYNDROME, ARRHYTHMIA));
      indexMap.put("rs11570112", Arrays.asList(CARDIOVASCULAR, FAMILIAL_DILATED_CARDIOMYOPATHY,
              NONCOMPACTION_CARDIOMYOPATHY, CARDIOMYOPATHY, DILATED_CARDIOMYOPATHY));
      indexMap.put("rs11591147", Arrays.asList(CARDIOVASCULAR, LDL, FAMILIAL_HYPERCHOLESTEROLEMIA,
              HDL));
      indexMap.put("rs1333048", Arrays.asList(CARDIOVASCULAR, AORTIC_ANEURYSM, ISCHEMIC_STROKE,
              HEART_DISEASE));
      indexMap.put("rs1333049", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs13333226", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs137853964", Arrays.asList(CARDIOVASCULAR, FAMILIAL_HYPERCHOLESTEROLEMIA,
              LDL));
      indexMap.put("rs139794067", Arrays.asList(CARDIOVASCULAR, CARDIOMYOPATHY));
      indexMap.put("rs17228212", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE, HDL));
      indexMap.put("rs1746048", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs17465637", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE));
      indexMap.put("rs17482753", Arrays.asList(CARDIOVASCULAR, DSYSLIPIDEMIA, HDL,
              ELEVATED_TRIGLYCERIDES));
      indexMap.put("rs1799945", Arrays.asList(CARDIOVASCULAR, THROMBOSIS, INSULIN_RESISTANCE,
              ISCHEMIC_STROKE, PULMONARY_HYPERTENSION, HEMORRHAGIC_STROKE));
      indexMap.put("rs1800437", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs1800497", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs1800629", Arrays.asList(CARDIOVASCULAR, FAMILIAL_DILATED_CARDIOMYOPATHY,
              DILATED_CARDIOMYOPATHY));
      indexMap.put("rs1800796", Arrays.asList(CARDIOVASCULAR, AORTIC_ANEURYSM, THROMBOSIS,
              ISCHEMIC_STROKE));
      indexMap.put("rs1801020", Arrays.asList(CARDIOVASCULAR, INSULIN_RESISTANCE,
              DILATED_CARDIOMYOPATHY, CARDIOMYOPATHY, SHORT_QT_SYNDROME));
      indexMap.put("rs1801282", Arrays.asList(CARDIOVASCULAR, LDL));
      indexMap.put("rs1805127", Arrays.asList(CARDIOVASCULAR, ARRHYTHMIA,
              LONG_QT_SYNDROME));
      indexMap.put("rs2229169", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs2231137", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs2383206", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE, ISCHEMIC_STROKE));
      indexMap.put("rs2383207", Arrays.asList(CARDIOVASCULAR, ANEURYSM, AORTIC_ANEURYSM,
              ISCHEMIC_STROKE));
      indexMap.put("rs2943634", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE, HDL,
              INSULIN_RESISTANCE, ISCHEMIC_STROKE));
      indexMap.put("rs3093059", Arrays.asList(CARDIOVASCULAR, HEMORRHAGIC_STROKE, ISCHEMIC_STROKE));
      indexMap.put("rs3798220", Arrays.asList(CARDIOVASCULAR, DSYSLIPIDEMIA, LDL, HDL,
              HEART_VALVE_DISEASE));
      indexMap.put("rs383830", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE, THROMBOSIS));
      indexMap.put("rs3842787", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs3900940", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs41261344", Arrays.asList(CARDIOVASCULAR, BRUGADA_SYNDROME, CARDIOMYOPATHY));
      indexMap.put("rs429358", Arrays.asList(CARDIOVASCULAR, ARTERIAL_OCCLUSIVE_DISEASE,
              DSYSLIPIDEMIA, LDL, AORTIC_ANEURYSM));
      indexMap.put("rs4420638", Arrays.asList(CARDIOVASCULAR, AORTIC_ANEURYSM, DSYSLIPIDEMIA,
              LDL, HDL));
      indexMap.put("rs45620037", Arrays.asList(CARDIOVASCULAR, FAMILIAL_DILATED_CARDIOMYOPATHY,
              BRUGADA_SYNDROME, DILATED_CARDIOMYOPATHY, CARDIOMYOPATHY));
      indexMap.put("rs4961", Arrays.asList(CARDIOVASCULAR, LDL, HEMORRHAGIC_STROKE, HYPERTENSION,
              HIGH_BLOOD_PRESSURE));
      indexMap.put("rs4977574", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs4986790", Arrays.asList(CARDIOVASCULAR, INSULIN_RESISTANCE, HDL,
              ISCHEMIC_STROKE, DILATED_CARDIOMYOPATHY, PULMONARY_HYPERTENSION, ISCHEMIC_STROKE));
      indexMap.put("rs4994", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs501120", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE, HEART_DISEASE));
      indexMap.put("rs599839", Arrays.asList(CARDIOVASCULAR, DSYSLIPIDEMIA, HEART_DISEASE, LDL,
              AORTIC_ANEURYSM));
      indexMap.put("rs6025", Arrays.asList(CARDIOVASCULAR, ARTERIAL_OCCLUSIVE_DISEASE, THROMBOSIS,
              PULMONARY_HYPERTENSION, ISCHEMIC_STROKE, STROKE));
      indexMap.put("rs646776", Arrays.asList(CARDIOVASCULAR, DSYSLIPIDEMIA, LDL));
      indexMap.put("rs662", Arrays.asList(CARDIOVASCULAR, MITRAL, DSYSLIPIDEMIA,
              INSULIN_RESISTANCE, HDL, ISCHEMIC_STROKE));
      indexMap.put("rs662799", Arrays.asList(CARDIOVASCULAR, DSYSLIPIDEMIA, HDL,
              ISCHEMIC_STROKE, OBESITY, HEART_DISEASE));
      indexMap.put("rs6749447", Arrays.asList(CARDIOVASCULAR, HIGH_BLOOD_PRESSURE,
              HYPERTENSION));
      indexMap.put("rs6922269", Arrays.asList(CARDIOVASCULAR, HEART_DISEASE));
      indexMap.put("rs7439293", Arrays.asList(CARDIOVASCULAR, ISCHEMIC_STROKE));
      indexMap.put("rs76992529", Arrays.asList(CARDIOVASCULAR, CARDIOMYOPATHY,
              CARDIAC_AMYLOIDOSIS));
      indexMap.put("rs77615401", Arrays.asList(CARDIOVASCULAR, FAMILIAL_DILATED_CARDIOMYOPATHY,
              RESTRICTIVE_CARDIOMYOPATHY));
      indexMap.put("rs8192678", Arrays.asList(CARDIOVASCULAR, INSULIN_RESISTANCE, CARDIOMYOPATHY));
      indexMap.put("rs854560", Arrays.asList(CARDIOVASCULAR, INSULIN_RESISTANCE, HDL,
              ISCHEMIC_STROKE, HYPERCHOLESTEROLEMIA));
      indexMap.put("rs9934438", Arrays.asList(CARDIOVASCULAR));
      indexMap.put("rs121917809", Arrays.asList(DILATED_CARDIOMYOPATHY, CARDIOMYOPATHY));
      indexMap.put("rs45487699", Arrays.asList(DILATED_CARDIOMYOPATHY,
              FAMILIAL_DILATED_CARDIOMYOPATHY, CARDIOMYOPATHY, NONCOMPACTION_CARDIOMYOPATHY));
      indexMap.put("rs63750197", Arrays.asList(DILATED_CARDIOMYOPATHY, CARDIOMYOPATHY));
      indexMap.put("rs74315379", Arrays.asList(DILATED_CARDIOMYOPATHY,
              FAMILIAL_DILATED_CARDIOMYOPATHY, CARDIOMYOPATHY, NONCOMPACTION_CARDIOMYOPATHY));
      indexMap.put("rs12713559", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs13306512", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs13306515", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs139361635", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs144467873", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs146651743", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs146675823", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs150673992", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs185098634", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs200533979", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs201016593", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs201102461", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs201102492", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs2228671", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA, LDL,
              DSYSLIPIDEMIA, HDL));
      indexMap.put("rs369943481", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs376207800", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs540073140", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs544453230", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA, CARDIOMYOPATHY));
      indexMap.put("rs551747280", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs552422789", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs563382937", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs563390335", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs570942190", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs577934998", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs5933", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA, CARDIOMYOPATHY));
      indexMap.put("rs72658860", Arrays.asList(FAMILIAL_HYPERCHOLESTEROLEMIA));
      indexMap.put("rs10413089", Arrays.asList(HDL));
      indexMap.put("rs183130", Arrays.asList(HDL));
      indexMap.put("rs326", Arrays.asList(HDL, DSYSLIPIDEMIA));
      indexMap.put("rs3843763", Arrays.asList(HDL));
      indexMap.put("rs5370", Arrays.asList(HDL));
      indexMap.put("rs17672135", Arrays.asList(HEART_DISEASE));
      indexMap.put("rs4665058", Arrays.asList(HEART_DISEASE));
      indexMap.put("rs688034", Arrays.asList(HEART_DISEASE));
      indexMap.put("rs7250581", Arrays.asList(HEART_DISEASE));
      indexMap.put("rs8055236", Arrays.asList(HEART_DISEASE));
      indexMap.put("rs1126742", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs2820037", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs3754777", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE, CARDIOVASCULAR));
      indexMap.put("rs3755351", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs3781719", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs3794260", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs6997709", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs7961152", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs9739493", Arrays.asList(HYPERTENSION, HIGH_BLOOD_PRESSURE));
      indexMap.put("rs17300539", Arrays.asList(INSULIN_RESISTANCE));
      indexMap.put("rs17817449", Arrays.asList(INSULIN_RESISTANCE));
      indexMap.put("rs1799999", Arrays.asList(INSULIN_RESISTANCE));
      indexMap.put("rs4969168", Arrays.asList(INSULIN_RESISTANCE));
      indexMap.put("rs10033464", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs1024611", Arrays.asList(ISCHEMIC_STROKE, PULMONARY_HYPERTENSION));
      indexMap.put("rs1041981", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs10455872", Arrays.asList(ISCHEMIC_STROKE, CARDIOVASCULAR,
              HEART_VALVE_DISEASE, MITRAL, LDL));
      indexMap.put("rs1048990", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs1333040", Arrays.asList(ISCHEMIC_STROKE, ANEURYSM));
      indexMap.put("rs1800888", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs187238", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs2200733", Arrays.asList(ISCHEMIC_STROKE, STROKE));
      indexMap.put("rs268", Arrays.asList(ISCHEMIC_STROKE, HDL));
      indexMap.put("rs3217992", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs34210653", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs5186", Arrays.asList(ISCHEMIC_STROKE, CARDIOMYOPATHY, HYPERTENSION,
              HIGH_BLOOD_PRESSURE, CARDIAC_ARREST));
      indexMap.put("rs5361", Arrays.asList(ISCHEMIC_STROKE, ARTERIAL_OCCLUSIVE_DISEASE,
              THROMBOSIS));
      indexMap.put("rs5918", Arrays.asList(ISCHEMIC_STROKE, ARTERIAL_OCCLUSIVE_DISEASE,
              THROMBOSIS));
      indexMap.put("rs6797312", Arrays.asList(ISCHEMIC_STROKE, STROKE, HDL));
      indexMap.put("rs699", Arrays.asList(ISCHEMIC_STROKE, PULMONARY_HYPERTENSION,
              CARDIOMYOPATHY));
      indexMap.put("rs841", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs964184", Arrays.asList(ISCHEMIC_STROKE, HEMORRHAGIC_STROKE,
              DSYSLIPIDEMIA, HDL));
      indexMap.put("rs966221", Arrays.asList(ISCHEMIC_STROKE));
      indexMap.put("rs5174", Arrays.asList(LDL));
      indexMap.put("rs1010", Arrays.asList(LDL, PULMONARY_HYPERTENSION, HDL));
      indexMap.put("rs55883237", Arrays.asList(NONCOMPACTION_CARDIOMYOPATHY, CARDIOMYOPATHY));
      indexMap.put("rs6971091", Arrays.asList(OBESITY));
      indexMap.put("rs201457110", Arrays.asList(PROLAPSE, MITRAL_VALVE_PROLAPSE, MITRAL));
      indexMap.put("rs137852752", Arrays.asList(PULMONARY_HYPERTENSION));
      indexMap.put("rs1799895", Arrays.asList(PULMONARY_HYPERTENSION));
      indexMap.put("rs1799963", Arrays.asList(PULMONARY_HYPERTENSION, CARDIOVASCULAR,
              ISCHEMIC_STROKE, THROMBOSIS, ARTERIAL_OCCLUSIVE_DISEASE));
      indexMap.put("rs200948870", Arrays.asList(PULMONARY_HYPERTENSION));
      indexMap.put("rs3903239", Arrays.asList(PULMONARY_HYPERTENSION));
      indexMap.put("rs5335", Arrays.asList(PULMONARY_HYPERTENSION));
      indexMap.put("rs557172581", Arrays.asList(PULMONARY_HYPERTENSION));
      indexMap.put("rs3783799", Arrays.asList(STROKE));
      indexMap.put("rs4244285", Arrays.asList(THROMBOSIS, CARDIOVASCULAR));
      indexMap.put("rs7080536", Arrays.asList(THROMBOSIS));
      return indexMap;
    }
  }

}
