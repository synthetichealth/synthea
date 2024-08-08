package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
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
    
    long currentStageFirstObserved = drStageFirstObservedDate.get(drStage);
    
    
    if (drStage > 0) {
      encounterNote.append("\nDiagnosis History:");
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
    	encounterNote.append('\n');
    }
    
    Procedure panRetinalLaser = (Procedure) person.attributes.get("panretinal_laser");
    List<Procedure> gridLaserHistory = (List<Procedure>)person.attributes.get("grid_laser_history");
    Procedure firstAntiVEGF = (Procedure)person.attributes.get("first_anti_vegf");

    if (gridLaserHistory == null) {
    	gridLaserHistory = new ArrayList<>();
    	person.attributes.put("grid_laser_history", gridLaserHistory);
    }
    
    if (panRetinalLaser != null || !gridLaserHistory.isEmpty() || firstAntiVEGF != null) {
    	encounterNote.append(person.rand("\nProcedure History:\n", "\nPast Surgical History:\n"));
    	if (panRetinalLaser != null) {
    		encounterNote.append("Panretinal laser ").append(ExportHelper.dateFromTimestamp(panRetinalLaser.start)).append('\n');
    	} 
    	if (!gridLaserHistory.isEmpty()) {
    		encounterNote.append("Grid laser");
    		for (Procedure g : gridLaserHistory) {
    			encounterNote.append(", ").append(ExportHelper.dateFromTimestamp(g.start));
    		}
    		encounterNote.append('\n');
    	}
    	if (firstAntiVEGF != null) {
    	  encounterNote.append("Anti-VEGF first performed ").append(ExportHelper.dateFromTimestamp(firstAntiVEGF.start)).append('\n');
    	}
    }
    encounterNote.append('\n');
    
    recordVA(person, encounterNote);
    recordIOP(person, encounterNote);
    encounterNote.append('\n');
    
    encounterNote.append("Pupils: Equal, round, reactive to light\n");
    encounterNote.append("Extraocular Movements: Full and smooth\n");
    encounterNote.append("Confrontation Visual Fields: Full to finger count OU\n");
    encounterNote.append("Anterior Segment: Normal lids, lashes, and conjunctiva OU.\n  Cornea clear OU. Anterior chamber deep and quiet OU. Iris normal OU. ");
    int age = person.ageInYears(time);
    if (age > 70) {
      encounterNote.append("Moderate nuclear sclerosis observed OU.");
    } else if (age > 50) {
      encounterNote.append("Early nuclear sclerosis bilaterally.");
    } else {
      encounterNote.append("Lens clear OU.");
    }
    encounterNote.append('\n');
    
    encounterNote.append(person.rand("Dilated Fundus Examination:\n", "Posterior Segment:\n"));
    
    Map<String, String> drSymptoms = (Map<String, String>) person.attributes.get("diabetic_retinopathy_symptoms");
    if (drSymptoms == null) {
      drSymptoms = new HashMap<>();
    }
    
    recordOpticDisc(person, encounterNote, drStage, stable, drSymptoms);
    recordMacula(person, encounterNote, drStage, stable, drSymptoms, gridLaserHistory, currentStageFirstObserved);
    recordVessels(person, encounterNote, drStage, stable, drSymptoms);
    recordPeriphery(person, encounterNote, drStage, stable, drSymptoms, panRetinalLaser, currentStageFirstObserved);
    
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

    encounterNote.append("\nAssessment: \n");
    if (currEncounter.conditions != null && !currEncounter.conditions.isEmpty()) {
      for (HealthRecord.Entry c : currEncounter.conditions) {
        encounterNote.append(" - ").append(c.codes.get(0).display).append('\n');
      }
    } else if (drStage == 0) {
      encounterNote.append("No current signs of diabetic retinopathy, both eyes\n");
    } else {
      encounterNote.append(" - ").append(STAGES[drStage]);
    }

    
    Observation hba1c = person.record.getLatestObservation("4548-4");
    
    if (((Double)hba1c.value) > 6.5 && person.rand() > .7) {
      if (drStage == 0) {
        encounterNote.append("\nDiscussed the nature of DM and its potential effects on the eye in detail with the patient. \nDiscussed the importance of maintaining strict glycemic control to avoid developing retinopathy.\n");
      } else {
        encounterNote.append("\nI discussed the effects of elevated glucose on the eye, \nand the importance of strict control in preventing progression of retinopathy.\n");
      }
    }
    
    if (prevEncounter == null) {
    	encounterNote.append("\nDiscussed the signs of vision changes that require immediate medical attention.\n");
    }
    
    // TODO: line about follow-up

    currEncounter.note = encounterNote.toString();
    
    person.attributes.put("previous_ophthalmic_encounter", currEncounter);
    
    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the next state
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    return true;
  }
  
  private static void recordVA(Person person, StringBuilder encounterNote) {
    double va = (double) person.attributes.get("visual_acuity_logmar");
    // logmar to 20/x = 20*10^(logmar)
    long denom = Math.round(20 * Math.pow(10, va));
    encounterNote.append("Visual Acuity: 20/").append(denom).append(" OD, 20/").append(denom).append(" OS\n");
  }
  
  private static void recordIOP(Person person, StringBuilder encounterNote) {
    int iop = (int) person.attributes.get("intraocular_pressure");
    encounterNote.append("Intraocular Pressure (IOP): ").append(iop).append(" mmHg OD, ").append(iop).append(" mmHg OS\n");
  }
  
  
  
  private static void recordOpticDisc(Person person, StringBuilder encounterNote, int drStage, boolean stable, Map<String, String> drSymptoms) {
    
    String opticDiscSymptom = drSymptoms.get("optic_disc");
    
    if (opticDiscSymptom == null || !stable) {
      switch(drStage) {
      case 0:
        opticDiscSymptom = "Normal, no edema or pallor";
        break;
      case 1:
        opticDiscSymptom = person.rand("Pink, well-defined margins, cup-to-disc ratio 0.3 OU", "Normal, no edema or pallor");
        break;
      case 2:
      case 3:
        opticDiscSymptom = "Normal in color and contour, no signs of neovascularization or optic disc edema.";
        break;
      case 4:
        opticDiscSymptom = "NVD extending from the disc margins into the adjacent retina";
        break;
        
      }
      drSymptoms.put("optic_disc", opticDiscSymptom);
    }
    
    encounterNote.append("Optic Disc: ").append(opticDiscSymptom).append('\n');

  }
  

  private static void recordMacula(Person person, StringBuilder encounterNote, int drStage, boolean stable,
      Map<String, String> drSymptoms, List<Procedure> gridLaserHistory, long currentStageFirstObserved) {
    boolean edema = (boolean) person.attributes.getOrDefault("macular_edema", false);
    
    String maculaSymptom = drSymptoms.get("macula");
    
    if (stable && !gridLaserHistory.isEmpty()
        && gridLaserHistory.get(gridLaserHistory.size() - 1).start > currentStageFirstObserved) {
      maculaSymptom = "Evidence of grid laser scars, no active edema, no exudates";
    } else if (maculaSymptom == null || !stable) {

      if (edema) {
        switch(drStage) {
        // 0 and 1 can't get edema in this model
        case 2:
          maculaSymptom = "Mild edema noted OU.";
          break;
        case 3:
        case 4:
          maculaSymptom = "Severe edema noted OU, with hard exudates present.";
        }
       
      } else {
        switch(drStage) {
        case 0:
        case 1:
        case 2:
          maculaSymptom = person.rand("Flat, no edema or exudates OU.", "No edema or exudates OU.", "Clear, no edema or exudates OU");
          break;
        case 3:
        case 4:
          maculaSymptom = "No CSME, but presence of microaneurysms near the macula.";
          break;
        }
      }
      
      if (!gridLaserHistory.isEmpty()) {
        maculaSymptom += " Evidence of prior grid laser scars.";
      }
      
      
      drSymptoms.put("macula", maculaSymptom);
    }
    
    encounterNote.append("Macula: ").append(maculaSymptom).append('\n');
  }
  
  private static void recordVessels(Person person, StringBuilder encounterNote, int drStage, boolean stable, Map<String, String> drSymptoms) {
    String vesselsSymptom = drSymptoms.get("vessels");
    
    if (vesselsSymptom == null || !stable) {
      switch(drStage) {
      case 0:
        vesselsSymptom = "Normal, no microaneurysms, hemorrhages, or venous beading observed.";
        break;
      case 1:
        vesselsSymptom = person.rand("Mild microaneurysms noted in both eyes.", "Microaneurysms OU.") + " No neovascularization noted.";
        break;
      case 2:
        vesselsSymptom = "Presence of microaneurysms and mild venous beading.";
        break;
      case 3:
        vesselsSymptom = "Presence of microaneurysms, intraretinal hemorrhages, IRMA, and mild venous beading.";
        break;
      case 4:
        vesselsSymptom = "Numerous microaneurysms, intraretinal hemorrhages, IRMA, and venous beading OU";
        break;
      }
      drSymptoms.put("vessels", vesselsSymptom);
    }
    
    encounterNote.append("Vessels: ").append(vesselsSymptom).append('\n');
  }
  
  private static void recordPeriphery(Person person, StringBuilder encounterNote, int drStage, boolean stable, Map<String, String> drSymptoms, Procedure panRetinalLaser, long currentStageFirstObserved) {
    String peripherySymptom = drSymptoms.get("periphery");
    
    if (stable && panRetinalLaser != null && panRetinalLaser.start > currentStageFirstObserved) {
      peripherySymptom = "Evidence of scattered laser scars from previous PRP, no new hemorrhages or microaneurysms, no neovascularization";
    } else if (peripherySymptom == null || !stable) {
      switch(drStage) {
      case 0:
      case 1:
        peripherySymptom = person.rand("No signs of retinal neovascularization, microaneurysms, or areas of non-perfusion.", "No tears, holes, or detachments OU");
        break;
      case 2:
      case 3:
        peripherySymptom = "Scattered microaneurysms and dot-blot hemorrhages, no signs of neovascularization.";
        break;
      case 4:
        peripherySymptom = "Neovascularization OU, no retinal detachment.";
        break;
      }
      
      if (panRetinalLaser != null) {
        peripherySymptom += " Evidence of scattered laser scars from prior PRP.";
      }
      
      
      drSymptoms.put("periphery", peripherySymptom);
    }
    
    
    
    encounterNote.append("Periphery: ").append(peripherySymptom).append('\n');
  }
}
