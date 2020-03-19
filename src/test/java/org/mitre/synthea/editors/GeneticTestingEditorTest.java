package org.mitre.synthea.editors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.MedicalCategory.HDL;
import static org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.MedicalCategory.LDL;
import static org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.MedicalCategory.OBESITY;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig;
import org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.MedicalCategory;
import org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisConfig.Population;
import org.mitre.synthea.editors.GeneticTestingEditor.DnaSynthesisWrapper;
import org.mitre.synthea.editors.GeneticTestingEditor.GeneticMarker;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

public class GeneticTestingEditorTest {
  private Person person;
  private HealthRecord record;
  
  private static class GeneticTestingEditorSub extends GeneticTestingEditor {
    void setPriorGeneticTest(Person person, boolean tested) {
      if (tested) {
        this.getOrInitContextFor(person).put(PRIOR_GENETIC_TESTING, true);
      } else {
        this.getOrInitContextFor(person).put(PRIOR_GENETIC_TESTING, null);
      }
    }
  }
  
  @Before
  public void setup() {
    person = new Person(1);
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.NAME, "YYZ FOO");
    record = new HealthRecord(person);
  }
  
  @Test
  public void shouldNotRunWhenNoConditions() {
    GeneticTestingEditor editor = new GeneticTestingEditor();
    boolean shouldRun = editor.shouldRun(person, record, 0);
    assertFalse(shouldRun);
  }

  @Test
  public void shouldNotRunWhenNoCardiovascularConditions() {
    record.conditionStart(100, "FooBarBaz");
    GeneticTestingEditor editor = new GeneticTestingEditor();
    boolean shouldRun = editor.shouldRun(person, record);
    assertFalse(shouldRun);
  }

  @Test
  public void shouldNotRunWhenPriorGeneticTest() {
    record.conditionStart(100, (String) GeneticTestingEditor.TRIGGER_CONDITIONS
        .keySet().toArray()[0]);
    GeneticTestingEditorSub editor = new GeneticTestingEditorSub();
    editor.setPriorGeneticTest(person, true);
    boolean shouldRun = editor.shouldRun(person, record);
    assertFalse(shouldRun);
  }

  @Test
  public void shouldRunWhenCardiovascularConditions() {
    record.conditionStart(100, (String) GeneticTestingEditor.TRIGGER_CONDITIONS
        .keySet().toArray()[0]);
    GeneticTestingEditor editor = new GeneticTestingEditor();
    boolean shouldRun = editor.shouldRun(person, record);
    assertTrue(shouldRun);
  }
  
  @Test
  public void shouldAddGeneticTestingPanel() {
    HealthRecord.Encounter e = record.encounterStart(1000, 
        HealthRecord.EncounterType.OUTPATIENT);
    GeneticTestingEditor editor = new GeneticTestingEditor();
    editor.process(person, Arrays.asList(e), 0, person.random);
    assertEquals(1, e.reports.size());
    assertEquals(1, e.reports.get(0).codes.size());
    assertEquals(GeneticTestingEditor.GENETIC_TESTING_REPORT_TYPE,
        e.reports.get(0).codes.get(0).display);
  }
  
  @Test
  public void loadResultFile() throws FileNotFoundException {
    URL testUrl = getClass().getClassLoader().getResource(
            "editors/genetic.testing/test_result.tsv");
    File testFile = new File(testUrl.getFile());
    List<GeneticMarker> output = 
            GeneticTestingEditor.DnaSynthesisWrapper.loadOutputFile(testFile);
    int numVariants = 0;
    Map<String, Integer> variantTypeCount = new HashMap<>();
    Map<String, Set<MedicalCategory>> variantMedicalCategories = new HashMap<>();
    for (GeneticMarker m: output) {
      if (m.isVariant()) {
        numVariants++;
        if (!variantTypeCount.containsKey(m.clinicalSignificance)) {
          variantTypeCount.put(m.clinicalSignificance, 0);
        }
        variantTypeCount.put(m.clinicalSignificance, variantTypeCount
                .get(m.clinicalSignificance) + 1);
        if (!variantMedicalCategories.containsKey(m.clinicalSignificance)) {
          variantMedicalCategories.put(m.clinicalSignificance, new HashSet<>());
        }
        variantMedicalCategories.get(m.clinicalSignificance)
                .addAll(m.getAssociatedMedicalCategories());
      }
    }
    assertEquals(12, numVariants);
    assertEquals(5, variantTypeCount.get(
            GeneticMarker.UNCERTAIN_CLINICAL_SIGNIFICANCE).intValue());
    assertEquals(4, variantTypeCount.get(
            GeneticMarker.ASSOCIATION_CLINICAL_SIGNIFICANCE).intValue());
    assertEquals(2, variantTypeCount.get(
            GeneticMarker.DRUG_RESPONSE_CLINICAL_SIGNIFICANCE).intValue());
    assertEquals(1, variantTypeCount.get(
            GeneticMarker.RISK_FACTOR_CLINICAL_SIGNIFICANCE).intValue());
  }
  
  @Test
  public void invokeScript() throws IOException, InterruptedException {
    URL scriptURL = getClass().getClassLoader().getResource(
        "editors/genetic.testing/dummy.sh");
    File scriptFile = new File(scriptURL.getFile());
    DnaSynthesisConfig cfg = new DnaSynthesisConfig(Population.AFR,
            new MedicalCategory[] {LDL, HDL, OBESITY});
    Config.set(DnaSynthesisWrapper.DNA_SYNTHESIS_SCRIPT,
        scriptFile.getAbsolutePath());
    DnaSynthesisWrapper invoker = new DnaSynthesisWrapper(cfg);
    File outputFile = invoker.invoke();
    List<GeneticMarker> output = 
            GeneticTestingEditor.DnaSynthesisWrapper.loadOutputFile(outputFile);
    int numVariants = 0;
    Map<String, Integer> variantTypeCount = new HashMap<>();
    Map<String, Set<MedicalCategory>> variantMedicalCategories = new HashMap<>();
    for (GeneticMarker m: output) {
      if (m.isVariant()) {
        numVariants++;
        if (!variantTypeCount.containsKey(m.clinicalSignificance)) {
          variantTypeCount.put(m.clinicalSignificance, 0);
        }
        variantTypeCount.put(m.clinicalSignificance,
                variantTypeCount.get(m.clinicalSignificance) + 1);
        if (!variantMedicalCategories.containsKey(m.clinicalSignificance)) {
          variantMedicalCategories.put(m.clinicalSignificance, new HashSet<>());
        }
        variantMedicalCategories.get(
                m.clinicalSignificance).addAll(m.getAssociatedMedicalCategories());
      }
    }
    assertEquals(12, numVariants);
    assertEquals(5, variantTypeCount.get(
            GeneticMarker.UNCERTAIN_CLINICAL_SIGNIFICANCE).intValue());
    assertEquals(4, variantTypeCount.get(
            GeneticMarker.ASSOCIATION_CLINICAL_SIGNIFICANCE).intValue());
    assertEquals(2, variantTypeCount.get(
            GeneticMarker.DRUG_RESPONSE_CLINICAL_SIGNIFICANCE).intValue());
    assertEquals(1, variantTypeCount.get(
            GeneticMarker.RISK_FACTOR_CLINICAL_SIGNIFICANCE).intValue());
  }
  
  @Test
  public void testMarkerToString() {
    GeneticMarker marker = new GeneticMarker("rs7025486", "chromosome", "location", "strand", 
            "ancestralAllele", "variantAlleleList", "XYZZY1", 
            GeneticMarker.UNCERTAIN_CLINICAL_SIGNIFICANCE, "C>T");
    String expected = "The XYZZY1 gene exhibits a variation of " +
            "'Uncertain' clinical significance. " +
            "The variation at index rs7025486 is associated with an increased risk of: " +
            "Aortic Aneurysm, Aneurysm, Pulmonary Hypertension and Ischemic Stroke.";
    assertEquals(expected, marker.toString());
    marker = new GeneticMarker("rs111671429", "chromosome", "location", "strand", 
            "ancestralAllele", "variantAlleleList", "XYZZY1", 
            GeneticMarker.RISK_FACTOR_CLINICAL_SIGNIFICANCE, "C>T");
    expected = "The XYZZY1 gene exhibits a variation of 'Risk Factor' clinical " +
            "significance. The variation at index rs111671429 is associated with an " +
            "increased risk of: Aortic Aneurysm and Marfan Syndrome.";
    assertEquals(expected, marker.toString());
    marker = new GeneticMarker("rs10757272", "chromosome", "location", "strand", 
            "ancestralAllele", "variantAlleleList", "XYZZY1", 
            GeneticMarker.DRUG_RESPONSE_CLINICAL_SIGNIFICANCE, "C>T");
    expected = "The XYZZY1 gene exhibits a variation of 'Drug Response' clinical " +
            "significance. The variation at index rs10757272 is associated with an " +
            "increased risk of: Aneurysm.";
    assertEquals(expected, marker.toString());
    marker = new GeneticMarker("rs10757272", "chromosome", "location", "strand", 
            "ancestralAllele", "variantAlleleList", "XYZZY1", 
            GeneticMarker.DRUG_RESPONSE_CLINICAL_SIGNIFICANCE, "C");
    expected = "The XYZZY1 gene does not exhibit any variation.";
    assertEquals(expected, marker.toString());
  }
}
