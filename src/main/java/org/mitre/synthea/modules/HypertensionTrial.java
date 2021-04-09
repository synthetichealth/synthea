package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.Module.ModuleSupplier;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;

public class HypertensionTrial {

  public static void registerModules(Map<String, ModuleSupplier> modules) {

    modules.put("ChooseNextTherapy", new ModuleSupplier(new ChooseNextTherapy()));
    
    modules.put("CreateVisitSchedule", new ModuleSupplier(new CreateVisitSchedule()));
    modules.put("DelayUntilNextVisit", new ModuleSupplier(new DelayUntilNextVisit()));

  }

  public static class ChooseNextTherapy extends Module {
    public ChooseNextTherapy() {
      this.name = "ChooseNextTherapy";
      this.submodule = true;
    }

    public Module clone() {
      return this;
    }

    public static final String ADD_THERAPY = "add_therapy";
    public static final String TITRATE = "titrate";
    
    public static final double TITRATION_RATIO = 0.7; // 70% of the time, titrate rather than adding new class
    
    @Override
    public boolean process(Person person, long time) {      
      // output: 
      // htn_trial_next_action = "titrate" or "add_therapy"
      // htn_trial_next_action_code = code for titrating or adding
      
    	
      // note: some repetition of logic here from what's in the module
      // there may be opportunities to de-dup, but I like having the detail in the module
      
      String trialArm = (String)person.attributes.get("trial_arm");
      boolean milepost = (boolean) person.attributes.getOrDefault("milestone_visit", false);
      
      double sbp = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
      double dbp = person.getVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, time);
     
      boolean dbpGte90 = (boolean)person.attributes.getOrDefault("dbp_gte_90", false);
      boolean sbpGte140 = (boolean)person.attributes.getOrDefault("sbp_gte_140", false);
      boolean sbpLt135 = (boolean)person.attributes.getOrDefault("sbp_lt_135", false);
      
      AtomicInteger titrationCounter = (AtomicInteger)person.attributes.get("titration_counter");
      if (titrationCounter == null) {
        titrationCounter = new AtomicInteger(0);
        person.attributes.put("titration_counter", titrationCounter);
      }
      
      int drugCount = countDrugs(person);
      
      String nextAction;
      String nextActionCode = null;
      
      if (trialArm.equals("intensive")) {
        if (sbp >= 120) {
          if (milepost) {
            // Add therapy not in use
            // see participant monthly, handled in module
            nextAction = ADD_THERAPY;
            nextActionCode = findTherapyNotInUse(person);
            
          } else {
            // Titrate or add therapy not in use
            // see participant monthly, handled in module
            
            if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
              nextAction = ADD_THERAPY;
              nextActionCode = findTherapyNotInUse(person);
            } else {
              nextAction = TITRATE;
              nextActionCode = "TODO";
            }

          }
        } else if (dbp >= 100 || (dbp >= 90 && dbpGte90)) {
          // titrate or add therapy not in use
          
          if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
            nextAction = ADD_THERAPY;
            nextActionCode = findTherapyNotInUse(person);
          } else {
            nextAction = TITRATE;
            nextActionCode = "TODO";
          }
          
        } else {
          // why did the module get called?
          throw new IllegalStateException("ChooseNextTherapy module called for patient that doesn't need it!");
        }
      } else {
        if (sbp >= 160 || (sbp >= 140 && sbpGte140)) {
          // titrate or add therapy not in use
          // schedule 1 month visit, not currently implemented
          
          if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
            nextAction = ADD_THERAPY;
            nextActionCode = findTherapyNotInUse(person);
          } else {
            nextAction = TITRATE;
            nextActionCode = "TODO";
          }
          
        } else if (dbp >= 100 || (dbp >= 90 && dbpGte90)) {
          // titrate or add therapy not in use
          
          if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
            nextAction = ADD_THERAPY;
            nextActionCode = findTherapyNotInUse(person);
          } else {
            nextAction = TITRATE;
            nextActionCode = "TODO";
          }
          
        } else if (sbp < 130 || (sbp < 135 && sbpLt135)) {
          // step down
          throw new IllegalStateException("ChooseNextTherapy module called for patient that needs StepDown");
        } else {
          // why did the module get called at all?
          throw new IllegalStateException("ChooseNextTherapy module called for patient that doesn't need it!");
        }
      }
      
      person.attributes.put("htn_trial_next_action", nextAction);
      person.attributes.put("htn_trial_next_action_code", nextActionCode);
      
      return true; // submodule is complete
    }
    
    private static final String[][] DRUG_HIERARCHY = {
        { "chlorthalidone", "furosemide", "spironolactone", "triamterene/hctz", "amiloride" }, // diuretics
        { "lisinopril" }, // ace inhibitor
        {}, // angiotensin receptor blocker
        {}, // calcium channel blocker
        {}, // beta blocker
        {}, // vasodilators
        {}, // alpha 2 agonist
        {}, // alpha blocker
        {}, // potassium supplement
    };
    
    public static int countDrugs(Person person) {
      int count = 0;
      for (String[] drugClass : DRUG_HIERARCHY) {
        for (String drug : drugClass) {
          if (person.record.medicationActive(drug)) {
            count++;
          }
        }
      }
      
      return count;
    }
    
    public static String findTherapyNotInUse(Person person) {
      String nextDrug = null;
      for (String[] drugClass : DRUG_HIERARCHY) {
        // TODO: shuffle the class
        for (String drug : drugClass) {
          if (person.record.medicationActive(drug)) {
            break; // break out of drug class, the person has one in this class already
          }
          nextDrug = drug;
          break;
        }
        if (nextDrug != null) break;
      }
      
      return nextDrug;
    }
    

  }
  
  
  public static class CreateVisitSchedule extends Module {
    public CreateVisitSchedule() {
      this.name = "CreateVisitSchedule";
      this.submodule = true;
    }

    public Module clone() {
      return this;
    }

    @Override
    public boolean process(Person person, long time) {
      List<Long> visitSchedule = new ArrayList<>();
      
      visitSchedule.add(time + Utilities.convertTime("months", 1)); // TODO, add the rest
      visitSchedule.add(time + Utilities.convertTime("months", 2));
      visitSchedule.add(time + Utilities.convertTime("months", 3));
      
      
      visitSchedule.add(time + Utilities.convertTime("months", 6));
      visitSchedule.add(time + Utilities.convertTime("months", 9));
      visitSchedule.add(time + Utilities.convertTime("months", 12));
      
      // yr 2
      visitSchedule.add(time + Utilities.convertTime("months", 15));
      visitSchedule.add(time + Utilities.convertTime("months", 18));
      visitSchedule.add(time + Utilities.convertTime("months", 21));
      visitSchedule.add(time + Utilities.convertTime("months", 24));
      
      // yr 3
      visitSchedule.add(time + Utilities.convertTime("months", 27));
      visitSchedule.add(time + Utilities.convertTime("months", 30));
      visitSchedule.add(time + Utilities.convertTime("months", 33));
      visitSchedule.add(time + Utilities.convertTime("months", 36));
      
      // yr 4
      visitSchedule.add(time + Utilities.convertTime("months", 39));
      visitSchedule.add(time + Utilities.convertTime("months", 42));
      visitSchedule.add(time + Utilities.convertTime("months", 45));
      visitSchedule.add(time + Utilities.convertTime("months", 48));
      
      person.attributes.put("htn_trial_visit_schedule", visitSchedule);
      
      return true;
    }
  }

  public static class DelayUntilNextVisit extends Module {
    public DelayUntilNextVisit() {
      this.name = "DelayUntilNextVisit";
      this.submodule = true;
    }

    public Module clone() {
      return this;
    }

    @Override
    public boolean process(Person person, long time) {
      
      // attributes we may care about
      // trial_arm (String, "standard"/"intensive") is input
      // see_participant_monthly (boolean) is input
      // milestone_visit (boolean) should be set on milestone visits
      // milestone visit schedule needs to be clarified
      
      boolean seeParticipantMonthly = (boolean) person.attributes.getOrDefault("see_participant_monthly", false);
      
      
      List<Long> visitSchedule = (ArrayList<Long>)person.attributes.get("htn_trial_visit_schedule");
      
      visitSchedule.removeIf(visit -> visit < time); // remove all visits already completed
      
      long nextEncounter;
      
      if (seeParticipantMonthly) {
        HealthRecord.Encounter previousEncounter = null;
        
        for (HealthRecord.Encounter e : person.record.encounters) {
          if (e.codes.get(0).code.equals("1234")) {
            // TODO: pick the right code
            
            // assumes that encounters are always added sequentially, which they should be
            previousEncounter = e;
          }
        }

        // delay 1 month since last encounter
        nextEncounter = previousEncounter.start + Utilities.convertTime("months", 1);
        boolean milestone = isMilestoneVisit(time, visitSchedule);
        person.attributes.put("milestone_visit", milestone);
      } else {
        // wait until the next milestone

        // if there is no next milestone, flag that and exit immediately
        
        if (visitSchedule.isEmpty()) {
          person.attributes.put("trial_complete", true);
          return true;
        }
        
        
        person.attributes.put("milestone_visit", true);
        nextEncounter = visitSchedule.get(0);
      }

      return (time <= nextEncounter);
      // note return options here, see State$CallSubmodule
      // if we return true, the submodule completed and processing continues to the next state
      // if we return false, the submodule did not complete (like with a Delay) and will re-process the next timestep.
      // so we should not always return false like in other java modules
    }
    
    
    public static boolean isMilestoneVisit(long time, List<Long> visitSchedule) {
      long oneWeek = Utilities.convertTime("weeks", 1);
      
      for (long visit : visitSchedule) {
        if (Math.abs(time - visit) < oneWeek) {
          return true;
        }
      }
      
      return false;
    }
  }
}
