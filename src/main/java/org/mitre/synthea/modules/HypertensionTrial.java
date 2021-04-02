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

    @Override
    public boolean process(Person person, long time) {      
      // output: htn_trial_next_action = "titrate" or class to add
      
      
      AtomicInteger titrationCounter = (AtomicInteger)person.attributes.get("titration_counter");
      if (titrationCounter == null) {
        titrationCounter = new AtomicInteger(0);
        person.attributes.put("titration_counter", titrationCounter);
      }
      
      
      String nextAction = "titrate";
      
      person.attributes.put("htn_trial_next_action", nextAction);
      
      
      return true; // submodule is complete
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
