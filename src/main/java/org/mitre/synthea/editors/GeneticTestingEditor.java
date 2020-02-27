package org.mitre.synthea.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.mitre.synthea.engine.StatefulHealthRecordEditor;
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
  static final String[] TRIGGER_CONDITIONS = {
    "stroke",
    "coronary_heart_disease",
    "myocardial_infarction",
    "cardiac_arrest",
    "atrial_fibrillation",
    "cardiovascular_disease"
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
    for (String triggerCondition: TRIGGER_CONDITIONS) {
      if (record.conditionActive(triggerCondition)) {
        hasActiveTriggerCondition = true;
        break;
      }
    }
    if (!hasActiveTriggerCondition) {
      return false;
    }
    return true;
  }

  @Override
  public void process(Person person, List<HealthRecord.Encounter> encounters, 
      long time, Random random) {
    if (encounters.isEmpty()) {
      return;
    }
    HealthRecord.Encounter encounter = encounters.get(0);
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
    encounter.reports.add(geneticTestingReport);
    Map<String, Object> context = this.getOrInitContextFor(person);
    context.put(PRIOR_GENETIC_TESTING, time);
  }
  
}
