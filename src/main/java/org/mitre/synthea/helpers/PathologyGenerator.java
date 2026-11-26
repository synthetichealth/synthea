package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Specimen;
import org.mitre.synthea.world.concepts.HealthRecord.SpecimenContainer;
import org.mitre.synthea.world.concepts.HealthRecord.SpecimenProcessing;
import org.mitre.synthea.helpers.Utilities;

/**
 * Generator for anatomic pathology specimens, slides, and narrative text.
 */
public final class PathologyGenerator {
  public static final String SYSTEM_PATHOLOGY = "http://synthea.mitre.org/pathology";
  public static final String SYSTEM_SUBSPECIALTY = SYSTEM_PATHOLOGY + "/subspecialty";
  public static final String SYSTEM_STAIN = SYSTEM_PATHOLOGY + "/stain";
  public static final String SYSTEM_CONTAINER = SYSTEM_PATHOLOGY + "/container";
  public static final String SYSTEM_PROCESSING = SYSTEM_PATHOLOGY + "/processing";
  public static final String SYSTEM_SPECIMEN_TYPE = SYSTEM_PATHOLOGY + "/specimen-type";
  public static final String SYSTEM_PART_TYPE = SYSTEM_PATHOLOGY + "/part-type";
  public static final String SYSTEM_BODY_SITE = SYSTEM_PATHOLOGY + "/body-site";
  public static final String PATHOLOGY_ACCESSION_SYSTEM = SYSTEM_PATHOLOGY + "/accession";

  public static final Code GROSS_DESCRIPTION_CODE =
      new Code(SYSTEM_PATHOLOGY, "gross-description", "Gross description");
  public static final Code MICROSCOPIC_DESCRIPTION_CODE =
      new Code(SYSTEM_PATHOLOGY, "microscopic-description", "Microscopic description");
  public static final Code FINAL_DIAGNOSIS_CODE =
      new Code(SYSTEM_PATHOLOGY, "final-diagnosis", "Final diagnosis");

  private static final Code PROCESS_FIXATION =
      new Code(SYSTEM_PROCESSING, "fixation", "Fixation");
  private static final Code PROCESS_EMBEDDING =
      new Code(SYSTEM_PROCESSING, "embedding", "Embedding");
  private static final Code PROCESS_STAINING =
      new Code(SYSTEM_PROCESSING, "staining", "Staining");

  private static final Code CONTAINER_BLOCK =
      new Code(SYSTEM_CONTAINER, "paraffin-block", "Paraffin block");
  private static final Code CONTAINER_SLIDE =
      new Code(SYSTEM_CONTAINER, "glass-slide", "Glass slide");

  private static final Code SPECIMEN_PART =
      new Code(SYSTEM_SPECIMEN_TYPE, "part", "Specimen part");
  private static final Code SPECIMEN_BLOCK =
      new Code(SYSTEM_SPECIMEN_TYPE, "block", "Tissue block");
  private static final Code SPECIMEN_SLIDE =
      new Code(SYSTEM_SPECIMEN_TYPE, "slide", "Histology slide");

  private static final Code STAIN_HE =
      new Code(SYSTEM_STAIN, "H_E", "Hematoxylin and eosin (H&E)");
  private static final Code STAIN_PAS =
      new Code(SYSTEM_STAIN, "PAS", "Periodic acid-Schiff (PAS)");
  private static final Code STAIN_TRICHROME =
      new Code(SYSTEM_STAIN, "TRICHROME", "Trichrome");
  private static final Code STAIN_GMS =
      new Code(SYSTEM_STAIN, "GMS", "Gomori methenamine silver (GMS)");
  private static final Code STAIN_AFB =
      new Code(SYSTEM_STAIN, "AFB", "Acid-fast bacilli (AFB)");
  private static final Code STAIN_ER =
      new Code(SYSTEM_STAIN, "IHC_ER", "Estrogen receptor (IHC)");
  private static final Code STAIN_PR =
      new Code(SYSTEM_STAIN, "IHC_PR", "Progesterone receptor (IHC)");
  private static final Code STAIN_HER2 =
      new Code(SYSTEM_STAIN, "IHC_HER2", "HER2 (IHC)");
  private static final Code STAIN_KI67 =
      new Code(SYSTEM_STAIN, "IHC_KI67", "Ki-67 (IHC)");
  private static final Code STAIN_CKAE1AE3 =
      new Code(SYSTEM_STAIN, "IHC_CKAE1AE3", "Cytokeratin AE1/AE3 (IHC)");

  private static final Map<String, SubspecialtyConfig> CONFIGS = new HashMap<>();

  static {
    registerConfigs();
  }

  private PathologyGenerator() {
  }

  public static class CaseSummary {
    public String accession;
    public String clinicalHistory;
    public String grossDescription;
    public String microscopicDescription;
    public String finalDiagnosis;
    public List<Specimen> specimens = new ArrayList<>();
  }

  private static class SubspecialtyConfig {
    String name;
    IntRange parts;
    IntRange blocksPerPart;
    IntRange slidesPerBlock;
    double wsiProbability;
    List<Code> partTypes;
    List<Code> bodySites;
    List<WeightedCode> stains;
    List<String> benignDx;
    List<String> malignantDx;
  }

  private static class IntRange {
    int min;
    int max;

    IntRange(int min, int max) {
      this.min = min;
      this.max = max;
    }

    int pick(Person person) {
      if (min == max) {
        return min;
      }
      return (int) person.rand(min, max + 1);
    }
  }

  private static class WeightedCode {
    Code code;
    double weight;

    WeightedCode(Code code, double weight) {
      this.code = code;
      this.weight = weight;
    }
  }

  public static CaseSummary generateCase(Person person, long time, String subspecialty) {
    SubspecialtyConfig config = CONFIGS.get(subspecialty);
    if (config == null) {
      config = CONFIGS.get("gi");
    }

    CaseSummary summary = new CaseSummary();
    summary.accession = buildAccession(person, time);
    summary.clinicalHistory = buildClinicalHistory(person);

    int partCount = config.parts.pick(person);
    int totalBlocks = 0;
    int totalSlides = 0;
    Map<String, Integer> stainCounts = new LinkedHashMap<>();

    for (int partIndex = 1; partIndex <= partCount; partIndex++) {
      Specimen part = person.record.specimen(time, SPECIMEN_PART.code);
      part.level = "part";
      part.accession = summary.accession;
      part.identifier = summary.accession + "-P" + partIndex;
      part.specimenType = pickCode(person, config.partTypes);
      part.bodySite = pickCode(person, config.bodySites);
      part.processing.add(makeProcessingStep(PROCESS_FIXATION, null, "Formalin fixed"));
      summary.specimens.add(part);

      int blocks = config.blocksPerPart.pick(person);
      totalBlocks += blocks;
      for (int blockIndex = 1; blockIndex <= blocks; blockIndex++) {
        Specimen block = person.record.specimen(time, SPECIMEN_BLOCK.code);
        block.level = "block";
        block.accession = summary.accession;
        block.identifier = part.identifier + ".B" + blockIndex;
        block.specimenType = SPECIMEN_BLOCK;
        block.parentSpecimen = part.uuid;
        block.container = makeContainer(CONTAINER_BLOCK, block.identifier);
        block.processing.add(makeProcessingStep(PROCESS_EMBEDDING, null, "Paraffin embedded"));
        summary.specimens.add(block);

        int slides = config.slidesPerBlock.pick(person);
        totalSlides += slides;
        for (int slideIndex = 1; slideIndex <= slides; slideIndex++) {
          Specimen slide = person.record.specimen(time, SPECIMEN_SLIDE.code);
          slide.level = "slide";
          slide.accession = summary.accession;
          slide.identifier = block.identifier + ".S" + slideIndex;
          slide.specimenType = SPECIMEN_SLIDE;
          slide.parentSpecimen = block.uuid;
          slide.container = makeContainer(CONTAINER_SLIDE, slide.identifier);

          Code stain = pickWeightedCode(person, config.stains);
          slide.stain = stain;
          slide.processing.add(makeProcessingStep(PROCESS_STAINING, stain,
              "Stained with " + stain.display));

          stainCounts.put(stain.display, stainCounts.getOrDefault(stain.display, 0) + 1);

          if (person.rand() < config.wsiProbability) {
            slide.wsiUrl = buildWsiUrl(summary.accession, slide.identifier);
            slide.wsiMimeType = "image/tiff";
          }

          summary.specimens.add(slide);
        }
      }
    }

    summary.grossDescription = buildGrossDescription(config, partCount, totalBlocks, totalSlides);
    summary.microscopicDescription = buildMicroscopicDescription(stainCounts);
    summary.finalDiagnosis = pickDiagnosis(person, config);

    return summary;
  }

  private static String buildAccession(Person person, long time) {
    int year = Utilities.getYear(time);
    int serial = (int) person.rand(100000, 999999);
    return String.format("S%04d-%06d", year, serial);
  }

  private static String buildClinicalHistory(Person person) {
    List<String> conditions = new ArrayList<>();
    for (int i = person.record.encounters.size() - 1; i >= 0 && conditions.size() < 3; i--) {
      HealthRecord.Encounter encounter = person.record.encounters.get(i);
      for (HealthRecord.Entry condition : encounter.conditions) {
        if (!condition.codes.isEmpty()) {
          String display = condition.codes.get(0).display;
          if (display != null && !conditions.contains(display)) {
            conditions.add(display);
          }
        }
        if (conditions.size() >= 3) {
          break;
        }
      }
    }
    if (conditions.isEmpty()) {
      return "No significant clinical history provided.";
    }
    return "History: " + String.join("; ", conditions) + ".";
  }

  private static String buildGrossDescription(SubspecialtyConfig config, int parts,
      int blocks, int slides) {
    return String.format("Received %d part(s) for %s. Total blocks: %d. Total slides: %d.",
        parts, config.name, blocks, slides);
  }

  private static String buildMicroscopicDescription(Map<String, Integer> stainCounts) {
    if (stainCounts.isEmpty()) {
      return "Microscopic examination performed.";
    }
    List<String> stains = new ArrayList<>();
    stainCounts.forEach((stain, count) -> stains.add(stain + " (" + count + ")"));
    return "Microscopic examination with stains: " + String.join(", ", stains) + ".";
  }

  private static String pickDiagnosis(Person person, SubspecialtyConfig config) {
    boolean malignant = person.rand() < 0.3;
    List<String> choices = malignant ? config.malignantDx : config.benignDx;
    if (choices.isEmpty()) {
      return malignant ? "Malignant neoplasm." : "Benign findings.";
    }
    return choices.get((int) person.rand(0, choices.size()));
  }

  private static SpecimenProcessing makeProcessingStep(Code procedure, Code additive,
      String description) {
    SpecimenProcessing processing = new SpecimenProcessing();
    processing.procedure = procedure;
    processing.additive = additive;
    processing.description = description;
    return processing;
  }

  private static SpecimenContainer makeContainer(Code type, String identifier) {
    SpecimenContainer container = new SpecimenContainer();
    container.type = type;
    container.identifier = identifier;
    return container;
  }

  private static Code pickCode(Person person, List<Code> codes) {
    if (codes == null || codes.isEmpty()) {
      return null;
    }
    if (codes.size() == 1) {
      return codes.get(0);
    }
    return codes.get((int) person.rand(0, codes.size()));
  }

  private static Code pickWeightedCode(Person person, List<WeightedCode> weighted) {
    double total = 0.0;
    for (WeightedCode wc : weighted) {
      total += wc.weight;
    }
    double roll = person.rand() * total;
    double cumulative = 0.0;
    for (WeightedCode wc : weighted) {
      cumulative += wc.weight;
      if (roll <= cumulative) {
        return wc.code;
      }
    }
    return weighted.get(0).code;
  }

  private static String buildWsiUrl(String accession, String slideId) {
    String baseUrl = Config.get("pathology.wsi.base_url", "https://wsi.example.org");
    return baseUrl + "/" + accession + "/" + slideId + ".svs";
  }

  private static List<WeightedCode> defaultStains() {
    List<WeightedCode> stains = new ArrayList<>();
    stains.add(new WeightedCode(STAIN_HE, 0.7));
    stains.add(new WeightedCode(STAIN_PAS, 0.08));
    stains.add(new WeightedCode(STAIN_TRICHROME, 0.08));
    stains.add(new WeightedCode(STAIN_GMS, 0.07));
    stains.add(new WeightedCode(STAIN_AFB, 0.07));
    return stains;
  }

  private static void registerConfigs() {
    CONFIGS.put("breast", config("Breast pathology",
        new IntRange(2, 6), new IntRange(2, 5), new IntRange(2, 8), 0.65,
        partTypes("Breast tissue", "Sentinel lymph node", "Skin margin"),
        bodySites("Breast"),
        stainsForBreast(),
        dxBenign("Fibroadenoma", "Fibrocystic change"),
        dxMalignant("Invasive ductal carcinoma", "Invasive lobular carcinoma")));

    CONFIGS.put("lung", config("Pulmonary pathology",
        new IntRange(1, 4), new IntRange(2, 4), new IntRange(2, 8), 0.6,
        partTypes("Lung biopsy", "Lymph node", "Pleura"),
        bodySites("Lung", "Mediastinum"),
        stainsForLung(),
        dxBenign("Granulomatous inflammation", "Benign lung tissue"),
        dxMalignant("Adenocarcinoma", "Squamous cell carcinoma")));

    CONFIGS.put("gi", config("Gastrointestinal pathology",
        new IntRange(2, 8), new IntRange(1, 3), new IntRange(2, 6), 0.5,
        partTypes("Colon biopsy", "Stomach biopsy", "Esophagus biopsy", "Lymph node"),
        bodySites("Colon", "Stomach", "Esophagus"),
        defaultStains(),
        dxBenign("Tubular adenoma", "Chronic active gastritis"),
        dxMalignant("Adenocarcinoma", "Neuroendocrine tumor")));

    CONFIGS.put("gu", config("Genitourinary pathology",
        new IntRange(2, 6), new IntRange(1, 3), new IntRange(2, 6), 0.55,
        partTypes("Prostate tissue", "Bladder biopsy", "Kidney tissue"),
        bodySites("Prostate", "Bladder", "Kidney"),
        defaultStains(),
        dxBenign("Benign prostatic hyperplasia", "Chronic cystitis"),
        dxMalignant("Prostatic adenocarcinoma", "Urothelial carcinoma")));

    CONFIGS.put("renal", config("Renal pathology",
        new IntRange(1, 2), new IntRange(2, 4), new IntRange(3, 8), 0.6,
        partTypes("Kidney biopsy"),
        bodySites("Kidney"),
        defaultStains(),
        dxBenign("IgA nephropathy", "Diabetic nephropathy"),
        dxMalignant("Renal cell carcinoma")));

    CONFIGS.put("neuro", config("Neuropathology",
        new IntRange(1, 2), new IntRange(2, 6), new IntRange(3, 10), 0.7,
        partTypes("Brain tissue", "Meninges"),
        bodySites("Brain", "Meninges"),
        defaultStains(),
        dxBenign("Reactive gliosis", "Demyelinating lesion"),
        dxMalignant("Glioblastoma", "Oligodendroglioma")));

    CONFIGS.put("heme", config("Hematopathology",
        new IntRange(1, 1), new IntRange(1, 3), new IntRange(4, 10), 0.5,
        partTypes("Bone marrow core", "Bone marrow aspirate"),
        bodySites("Bone marrow"),
        defaultStains(),
        dxBenign("Reactive marrow", "Myeloproliferative neoplasm, chronic"),
        dxMalignant("Acute myeloid leukemia", "Diffuse large B-cell lymphoma")));

    CONFIGS.put("cytology", config("Cytopathology",
        new IntRange(1, 2), new IntRange(1, 2), new IntRange(3, 8), 0.4,
        partTypes("Fine needle aspirate", "Body fluid cytology"),
        bodySites("Lymph node", "Thyroid", "Pleural fluid"),
        defaultStains(),
        dxBenign("Negative for malignant cells", "Atypical cells, favor benign"),
        dxMalignant("Suspicious for malignancy", "Positive for malignant cells")));

    CONFIGS.put("thoracic", config("Thoracic pathology",
        new IntRange(1, 3), new IntRange(2, 4), new IntRange(2, 8), 0.6,
        partTypes("Mediastinal tissue", "Pleura", "Lymph node"),
        bodySites("Mediastinum", "Pleura"),
        stainsForLung(),
        dxBenign("Reactive pleuritis", "Granulomatous inflammation"),
        dxMalignant("Malignant mesothelioma", "Metastatic carcinoma")));

    CONFIGS.put("eye", config("Ophthalmic pathology",
        new IntRange(1, 1), new IntRange(1, 2), new IntRange(2, 6), 0.5,
        partTypes("Corneal tissue", "Enucleation specimen"),
        bodySites("Eye"),
        defaultStains(),
        dxBenign("Corneal scar", "Chronic inflammation"),
        dxMalignant("Uveal melanoma")));

    CONFIGS.put("head_neck", config("Head and neck pathology",
        new IntRange(1, 4), new IntRange(2, 4), new IntRange(2, 8), 0.55,
        partTypes("Larynx biopsy", "Oral cavity biopsy", "Thyroid tissue"),
        bodySites("Larynx", "Oral cavity", "Thyroid"),
        defaultStains(),
        dxBenign("Squamous dysplasia", "Chronic sialadenitis"),
        dxMalignant("Squamous cell carcinoma", "Papillary thyroid carcinoma")));
  }

  private static SubspecialtyConfig config(String name, IntRange parts, IntRange blocks,
      IntRange slides, double wsiProbability, List<Code> partTypes,
      List<Code> bodySites, List<WeightedCode> stains,
      List<String> benignDx, List<String> malignantDx) {
    SubspecialtyConfig config = new SubspecialtyConfig();
    config.name = name;
    config.parts = parts;
    config.blocksPerPart = blocks;
    config.slidesPerBlock = slides;
    config.wsiProbability = wsiProbability;
    config.partTypes = partTypes;
    config.bodySites = bodySites;
    config.stains = stains;
    config.benignDx = benignDx;
    config.malignantDx = malignantDx;
    return config;
  }

  private static List<Code> partTypes(String... names) {
    List<Code> types = new ArrayList<>();
    for (String name : names) {
      types.add(new Code(SYSTEM_PART_TYPE, slug(name), name));
    }
    return types;
  }

  private static List<Code> bodySites(String... names) {
    List<Code> sites = new ArrayList<>();
    for (String name : names) {
      sites.add(new Code(SYSTEM_BODY_SITE, slug(name), name));
    }
    return sites;
  }

  private static List<String> dxBenign(String... values) {
    List<String> list = new ArrayList<>();
    Collections.addAll(list, values);
    return list;
  }

  private static List<String> dxMalignant(String... values) {
    List<String> list = new ArrayList<>();
    Collections.addAll(list, values);
    return list;
  }

  private static String slug(String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  private static List<WeightedCode> stainsForBreast() {
    List<WeightedCode> stains = new ArrayList<>();
    stains.add(new WeightedCode(STAIN_HE, 0.55));
    stains.add(new WeightedCode(STAIN_ER, 0.15));
    stains.add(new WeightedCode(STAIN_PR, 0.12));
    stains.add(new WeightedCode(STAIN_HER2, 0.1));
    stains.add(new WeightedCode(STAIN_KI67, 0.08));
    return stains;
  }

  private static List<WeightedCode> stainsForLung() {
    List<WeightedCode> stains = new ArrayList<>();
    stains.add(new WeightedCode(STAIN_HE, 0.6));
    stains.add(new WeightedCode(STAIN_CKAE1AE3, 0.2));
    stains.add(new WeightedCode(STAIN_GMS, 0.1));
    stains.add(new WeightedCode(STAIN_AFB, 0.1));
    return stains;
  }
}
