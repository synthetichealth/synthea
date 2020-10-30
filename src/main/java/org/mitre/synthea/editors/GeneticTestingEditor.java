package org.mitre.synthea.editors;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  private static final double GENETIC_TESTING_THRESHOLD = 0.50;
  static final Map<String, List<MedicalCategory>> TRIGGER_CONDITIONS = init();
  
  static Map<String, List<MedicalCategory>> init() {
    return loadConfig("editors/genetics/TriggerToCategory.json",
            new TypeToken<Map<String, List<MedicalCategory>>>() {}.getType());
  }
  
  static <T> T loadConfig(String resourcePath, Type typeOfT) {
    InputStream in = GeneticTestingEditor.class.getClassLoader().getResourceAsStream(
            resourcePath);
    Gson gson = new Gson();
    return gson.fromJson(new InputStreamReader(in), typeOfT);
  }
  
  @Override
  public boolean shouldRun(Person person, HealthRecord record, long time) {
    // Not all patients will get genetic testing
    return shouldRun(person, record) && person.rand() >= GENETIC_TESTING_THRESHOLD;
  }
  
  protected boolean shouldRun(Person person, HealthRecord record) {
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
  
  private static MedicalCategory[] getGeneticCategories(Person person) {
    HashSet<MedicalCategory> categories = new HashSet<>();
    categories.addAll(getGeneticCategories(person.defaultRecord));
    if (person.hasMultipleRecords) {
      for (HealthRecord record: person.records.values()) {
        categories.addAll(getGeneticCategories(record));
      }
    }
    
    System.out.printf("%s: performing DNA synthesis for: %s\n",
            person.attributes.get(Person.NAME), categories.toString());

    MedicalCategory[] array = categories.stream()
        .toArray(n -> new MedicalCategory[n]);
    return array;
  }
  
  private static DnaSynthesisConfig.Population getPopulation(Person person) {
    DnaSynthesisConfig.Population population;
    String race = (String) person.attributes.get(Person.RACE);
    switch (race) {
      case "white":
        population = DnaSynthesisConfig.Population.EUR;
        break;
      case "black":
        population = DnaSynthesisConfig.Population.AFR;
        break;
      case "asian":
        population = DnaSynthesisConfig.Population.EAS;
        break;
      case "native":
        population = DnaSynthesisConfig.Population.AMR;
        break;
      default:
        population = null; // randomly selected
    }
    return population;
  }
  
  @Override
  public void process(Person person, List<HealthRecord.Encounter> encounters, 
      long time) {
    if (encounters.isEmpty()) {
      return;
    }
    
    MedicalCategory[] categories = getGeneticCategories(person);
    DnaSynthesisConfig.Population population = getPopulation(person);
    DnaSynthesisConfig cfg = new DnaSynthesisConfig(
        population, categories);
                      
    DnaSynthesisWrapper invoker = new DnaSynthesisWrapper(cfg);
    File outputFile;
    try {
      outputFile = invoker.invoke();
      List<GeneticMarker> markers = 
              GeneticTestingEditor.DnaSynthesisWrapper.loadOutputFile(outputFile);
      List<HealthRecord.Observation> observations = new ArrayList<>(10);
      HealthRecord.Encounter encounter = encounters.get(0);

      int variants = 0;
      for (GeneticMarker marker: markers) {
        if (marker.isVariant()) {
          variants++;
          HealthRecord.Observation observation = person.record.new Observation(time, 
              marker.toString(), null);
          observation.codes.add(marker.getVariantCode());
          encounter.observations.add(observation);
          observations.add(observation);
        }
      }
      System.out.printf("%s: %d genetic variants\n", person.attributes.get(Person.NAME),
              variants);

      HealthRecord.Report geneticTestingReport = person.record.new Report(time, 
          GENETIC_TESTING_REPORT_TYPE, observations);
      geneticTestingReport.codes.add(new Code("LOINC", "55232-3", 
          GENETIC_TESTING_REPORT_TYPE));
      encounter.reports.add(geneticTestingReport);
      Map<String, Object> context = this.getOrInitContextFor(person);
      context.put(PRIOR_GENETIC_TESTING, time);
    } catch (IOException | InterruptedException ex) {
      System.out.println("Unable to invoke DNA synthesis script");
      ex.printStackTrace();
    }
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
      @SerializedName("Aneurysm")
      ANEURYSM("Aneurysm"),
      @SerializedName("Aortic Aneurysm")
      AORTIC_ANEURYSM("Aortic Aneurysm"),
      @SerializedName("Arrhythmia")
      ARRHYTHMIA("Arrhythmia"),
      @SerializedName("Arrhythmogenic Right Ventricular Dysplasia")
      ARRHYTHMOGENIC_RIGHT_VENTRICULAR_DYSPLASIA("Arrhythmogenic Right Ventricular Dysplasia"),
      @SerializedName("Arterial Occlusive Disease")
      ARTERIAL_OCCLUSIVE_DISEASE("Arterial Occlusive Disease"),
      @SerializedName("Brugada Syndrome")
      BRUGADA_SYNDROME("Brugada Syndrome"),
      @SerializedName("Cardiac Amyloidosis")
      CARDIAC_AMYLOIDOSIS("Cardiac Amyloidosis"),
      @SerializedName("Cardiac Arrest")
      CARDIAC_ARREST("Cardiac Arrest"),
      @SerializedName("Cardiomyopathy")
      CARDIOMYOPATHY("Cardiomyopathy"),
      @SerializedName("Cardiovascular")
      CARDIOVASCULAR("Cardiovascular Issues"),
      @SerializedName("Dilated Cardiomyopathy")
      DILATED_CARDIOMYOPATHY("Dilated Cardiomyopathy"),
      @SerializedName("Dsyslipidemia")
      DSYSLIPIDEMIA("Dsyslipidemia"),
      @SerializedName("Elevated Triglycerides")
      ELEVATED_TRIGLYCERIDES("Elevated Triglycerides"),
      @SerializedName("Familial Dilated Cardiomyopathy")
      FAMILIAL_DILATED_CARDIOMYOPATHY("Familial Dilated Cardiomyopathy"),
      @SerializedName("Familial Hypercholesterolemia")
      FAMILIAL_HYPERCHOLESTEROLEMIA("Familial Hypercholesterolemia"),
      @SerializedName("HDL")
      HDL("Low HDL"),
      @SerializedName("Heart Disease")
      HEART_DISEASE("Heart Disease"),
      @SerializedName("Heart Valve Disease")
      HEART_VALVE_DISEASE("Heart Valve Disease"),
      @SerializedName("Hemorrhagic Stroke")
      HEMORRHAGIC_STROKE("Hemorrhagic Stroke"),
      @SerializedName("High Blood Pressure")
      HIGH_BLOOD_PRESSURE("High Blood Pressure"),
      @SerializedName("Hypercholesterolemia")
      HYPERCHOLESTEROLEMIA("Hypercholesterolemia"),
      @SerializedName("Hypertension")
      HYPERTENSION("Hypertension"),
      @SerializedName("Insulin Resistance")
      INSULIN_RESISTANCE("Insulin Resistance"),
      @SerializedName("Ischemic Stroke")
      ISCHEMIC_STROKE("Ischemic Stroke"),
      @SerializedName("LDL")
      LDL("High LDL"),
      @SerializedName("Long QT Syndrome")
      LONG_QT_SYNDROME("Long QT Syndrome"),
      @SerializedName("Marfan Syndrome")
      MARFAN_SYNDROME("Marfan Syndrome"),
      @SerializedName("Mitral")
      MITRAL("Mitral valve issues"),
      @SerializedName("Mitral Valve prolapse")
      MITRAL_VALVE_PROLAPSE("Mitral Valve prolapse"),
      @SerializedName("Noncompaction Cardiomyopathy")
      NONCOMPACTION_CARDIOMYOPATHY("Noncompaction Cardiomyopathy"),
      @SerializedName("Obesity")
      OBESITY("Obesity"),
      @SerializedName("Prolapse")
      PROLAPSE("Prolapse"),
      @SerializedName("Pulmonary Hypertension")
      PULMONARY_HYPERTENSION("Pulmonary Hypertension"),
      @SerializedName("Restrictive Cardiomyopathy")
      RESTRICTIVE_CARDIOMYOPATHY("Restrictive Cardiomyopathy"),
      @SerializedName("Short QT Syndrome")
      SHORT_QT_SYNDROME("Short QT Syndrome"),
      @SerializedName("Stroke")
      STROKE("Stroke"),
      @SerializedName("Thrombosis")
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
        initializeCategoryMap();
    private static final Map<String, String> LOINC_MAP = initializeLoincMap();

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
                getAssociatedMedicalCategories();
        if (associatedConditions != null) {
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
        } else {
          sb.append("unknown conditions");
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

    private static Map<String, List<MedicalCategory>> initializeCategoryMap() {
      // mapping extracted from Python dna_synthesis config at
      // dna-synthesis/populations_and_individuals/Data/cvd_families_byVar.tsv
      return GeneticTestingEditor.loadConfig("editors/genetics/GeneIndexToCategory.json",
              new TypeToken<Map<String, List<MedicalCategory>>>() {}.getType());
    }
    
    private static Map<String, String> initializeLoincMap() {
      return GeneticTestingEditor.loadConfig("editors/genetics/GeneticVariationLOINC.json",
              new TypeToken<Map<String, String>>() {}.getType());
    }

    Code getVariantCode() {
      String loincCode = LOINC_MAP.getOrDefault(this.gene, "69548-6");
      return new Code("LOINC", loincCode, toString());
    }
  }

}
