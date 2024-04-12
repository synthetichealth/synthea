package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

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
        .append(' ')
        .append(ExportHelper.dateFromTimestamp(time))
        .append('\n');
      
      firstObservedDrStage = (Integer) person.attributes.get("first_observed_dr_stage");
    }

    HashMap<Integer,Long> drStageFirstObservedDate = (HashMap<Integer,Long>) person.attributes.get("dr_stage_to_first_observed_date");
    if (drStageFirstObservedDate == null) {
      drStageFirstObservedDate = new HashMap<>();
      person.attributes.put("dr_stage_to_first_observed_date", drStageFirstObservedDate);
    }

    boolean stable = true;
    if (!drStageFirstObservedDate.containsKey(drStage)) {
      drStageFirstObservedDate.put(drStage, currEncounter.start);
      stable = false;
    }
    
    if (drStage == 0) {
      encounterNote.append("No current signs of diabetic retinopathy, both eyes\n");
    } else {
    	boolean first = true;
    	for (int i = 1 ; i <= 4 ; i++) {
    		Long observed = drStageFirstObservedDate.get(i);
    		if (observed != null) {
    			if (first) {
        			encounterNote.append(STAGES[i]).append(" OU ").append(ExportHelper.dateFromTimestamp(observed)).append('\n');
        			first = false;
    			} else {
        			encounterNote.append("Progressed to " + STAGES[i] + " " + ExportHelper.dateFromTimestamp(observed)).append('\n');
    			}
    		}
    	}
    }
    
    Procedure panRetinalLaser = (Procedure) person.attributes.get("panretinal_laser");
    List<Procedure> gridLaserHistory = (List<Procedure>)person.attributes.get("grid_laser_history");
    if (gridLaserHistory == null) {
    	gridLaserHistory = new ArrayList<>();
    	person.attributes.put("grid_laser_history", gridLaserHistory);
    }
    
    if (panRetinalLaser != null || !gridLaserHistory.isEmpty()) {
    	encounterNote.append("Procedure History:\n");
    	if (panRetinalLaser != null) {
    		encounterNote.append("Panretinal laser ").append(ExportHelper.dateFromTimestamp(panRetinalLaser.start)).append('\n');
    	} else {
    		encounterNote.append("Grid laser");
    		for (Procedure g : gridLaserHistory) {
    			encounterNote.append(", ").append(ExportHelper.dateFromTimestamp(g.start));
    		}
    		encounterNote.append('\n');
    	}
    }
    
    double va = (double) person.attributes.get("visual_acuity_logmar");
    // logmar to 20/x = 20*10^(logmar)
    long denom = Math.round(20 * Math.pow(10, va));
    encounterNote.append("Visual Acuity: 20/").append(denom).append(" OD, 20/").append(denom).append(" OS\n");
    
    int iop = (int) person.attributes.get("intraocular_pressure");
    encounterNote.append("Intraocular Pressure (IOP): ").append(iop).append(" mmHg OD, ").append(iop).append(" mmHg OS\n");
    
    // an example from chatGPT
    encounterNote.append("Pupils: Equal, round, reactive to light and accommodation\n");
    encounterNote.append("Extraocular Movements: Full and smooth\n");
    encounterNote.append("Confrontation Visual Fields: Full to finger count OU\n");
    encounterNote.append("Anterior Segment: Normal lids, lashes, and conjunctiva OU. Cornea clear OU. Anterior chamber deep and quiet OU. Iris normal architecture OU. Lens clear OU.\n");
    encounterNote.append("Dilated Fundus Examination:\n");
    encounterNote.append("Optic Disc: Pink, well-defined margins, cup-to-disc ratio 0.3 OU\n");
    
    boolean edema = (boolean) person.attributes.getOrDefault("macular_edema", false);
    
    if (edema) {
    	// TODO encounterNote.append("Macula: Flat, no edema or exudates OU\n");
    } else {
    	encounterNote.append("Macula: Flat, no edema or exudates OU\n");
    }
    
    encounterNote.append("Vessels: Attenuated arterioles with some copper wiring changes OU. No neovascularization noted.\n");
    encounterNote.append("Periphery: No tears, holes, or detachments OU");
   
    Procedure firstAntiVEGF = (Procedure)person.attributes.get("first_anti_vegf");
    
    for (Procedure p : currEncounter.procedures) {
    	switch (p.type) {
    	// primary code
    	
    	// visual acuity
    	case "16830007":
    		break;
    		
    	// IOP
    	case "252832004":
    		break;
    		
    	// Slit-lamp biomicroscopy
    	case "55468007":
    		break;
    		
    	// Gonioscopy
    	case "389153003":
    		break;
    		
    	// Fundoscopy
    	case "314971001":
    		break;
    		
    	// Examination of the peripheral retina and vitreous
    	case "722161008":
    		break;
    		
    	// OCT
    	case "700070005":
    		break;
    		
    	// Panretinal laser
    	case "413180006":
    		person.attributes.put("panretinal_laser", p);
    		break;
    		
    	// Grid laser
    	case "397539000":
    		gridLaserHistory.add(p);
    		break;
    		
    	// Anti VEGF
    	case "1004045004":
    		if (firstAntiVEGF == null) {

    			firstAntiVEGF = p;
    			person.attributes.put("first_anti_vegf", firstAntiVEGF);
    		}
    		break;
    	
    	}
    }

    Observation hba1c = person.record.getLatestObservation("4548-4");
    
    if (((Double)hba1c.value) > 6.5 && person.rand() > .7) {
      if (drStage == 0) {
        encounterNote.append("Discussed the nature of DM and its potential effects on the eye in detail with the patient. Discussed the importance of maintaining strict glycemic control to avoid developing retinopathy.\n");
      } else {
        encounterNote.append("I discussed the effects of elevated glucose on the eye, and the importance of strict control in preventing progression of retinopathy.\n");
      }
    }
    
    if (prevEncounter == null) {
    	encounterNote.append("Discussed the signs of vision changes that require immediate medical attention.\n");
    }
    
    // TODO: line about follow-up

    currEncounter.note = encounterNote.toString();
    
//    System.out.println(currEncounter.note);
//    System.out.println("\n");
    
    
    person.attributes.put("previous_ophthalmic_encounter", currEncounter);
    
    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the next state
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    return true;
  }
}
