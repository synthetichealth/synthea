package org.mitre.synthea.modules;

import java.util.HashMap;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

public class OphthalmicNoteModule extends Module {

  public OphthalmicNoteModule() {
    this.name = "OphthalmicNote";
    this.submoduleName = "OphthalmicNote";
    this.submodule = true; // make sure this doesn't run except when called
  }

  public Module clone() {
    return this;
  }
  
  private static final String[] STAGES = {
      /* 0 */ "no diabetic retinopathy", 
      /* 1 */ "background diabetic retinopathy",
      /* 2 */ "moderate nonproliferative diabetic retinopathy",
      /* 3 */ "severe nonproliferative diabetic retinopathy",
      /* 4 */ "high-risk proliferative diabetic retinopathy",
  };
  
  @Override
  public boolean process(Person person, long time) {
    Encounter currEncounter = person.record.currentEncounter(time);
    Encounter prevEncounter = (Encounter) person.attributes.get("previous_ophthalmic_encounter");
    int drStage = (Integer) person.attributes.get("diabetic_retinopathy_stage");

    int firstObservedDrStage = -1;

    StringBuilder encounterNote = new StringBuilder();
    if (prevEncounter == null) {
      encounterNote.append("Initial examination of ")
          .append(person.attributes.get(Person.NAME))
          .append(" on ")
          .append(ExportHelper.dateFromTimestamp(time))
          .append('\n');
      firstObservedDrStage = drStage;
      person.attributes.put("first_observed_dr_stage", firstObservedDrStage);
    } else {
      encounterNote.append("Followup exam with ")
        .append(person.attributes.get(Person.NAME))
        .append('\n');
      
      firstObservedDrStage = (Integer) person.attributes.get("first_observed_dr_stage");
    }

    HashMap<Integer,Long> drStageFirstObservedDate = (HashMap<Integer,Long>) person.attributes.get("dr_stage_to_first_observed_date");
    if (drStageFirstObservedDate == null) {
      drStageFirstObservedDate = new HashMap<>();
    }

    if (!drStageFirstObservedDate.containsKey(drStage)) {
      drStageFirstObservedDate.put(drStage, currEncounter.start);
    }
    
    if (drStage == 0) {
      encounterNote.append("No current signs of diabetic retinopathy, both eyes\n");
    } else if (firstObservedDrStage > 0) {
      encounterNote.append(STAGES[firstObservedDrStage]).append(" OU\n");
    }
    
    if (drStage > firstObservedDrStage) {
//      encounterNote.append("Progressed to " )
    }

    Observation hba1c = person.record.getLatestObservation("4548-4");
    
    if (((Double)hba1c.value) > 6.5 && person.rand() > .7) {
      if (drStage == 0) {
        encounterNote.append("Discussed the nature of DM and its potential effects on the eye in detail with the patient. Discussed the importance of maintaining strict glycemic control to avoid developing retinopathy.");
      } else {
        encounterNote.append("I discussed the effects of elevated glucose on the eye, and the importance of strict control in preventing progression of retinopathy.");
      }
    }

    currEncounter.note = encounterNote.toString();
    
    person.attributes.put("previous_ophthalmic_encounter", currEncounter);
    
    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the next state
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    return true;
  }
}
