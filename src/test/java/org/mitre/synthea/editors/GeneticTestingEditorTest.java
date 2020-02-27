package org.mitre.synthea.editors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

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
    record.conditionStart(100, GeneticTestingEditor.TRIGGER_CONDITIONS[0]);
    GeneticTestingEditorSub editor = new GeneticTestingEditorSub();
    editor.setPriorGeneticTest(person, true);
    boolean shouldRun = editor.shouldRun(person, record);
    assertFalse(shouldRun);
  }

  @Test
  public void shouldRunWhenCardiovascularConditions() {
    record.conditionStart(100, GeneticTestingEditor.TRIGGER_CONDITIONS[0]);
    GeneticTestingEditor editor = new GeneticTestingEditor();
    boolean shouldRun = editor.shouldRun(person, record);
    assertTrue(shouldRun);
  }
  
  @Test
  public void shouldAddGeneticTestingPanel() {
    HealthRecord.Encounter e = record.encounterStart(1000, HealthRecord.EncounterType.OUTPATIENT);
    GeneticTestingEditor editor = new GeneticTestingEditor();
    editor.process(person, Arrays.asList(e), 0, person.random);
    assertEquals(1, e.reports.size());
    assertEquals(1, e.reports.get(0).codes.size());
    assertEquals(GeneticTestingEditor.GENETIC_TESTING_REPORT_TYPE,
        e.reports.get(0).codes.get(0).display);
  }
}
