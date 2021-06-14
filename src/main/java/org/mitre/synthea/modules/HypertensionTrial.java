package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.engine.Components.Range;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.Module.ModuleSupplier;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class HypertensionTrial {

  public static void registerModules(Map<String, ModuleSupplier> modules) {

    modules.put("ChooseNextTherapy", new ModuleSupplier(new ChooseNextTherapy()));
    
    modules.put("CreateVisitSchedule", new ModuleSupplier(new CreateVisitSchedule()));
    modules.put("DelayUntilNextVisit", new ModuleSupplier(new DelayUntilNextVisit()));
    
    modules.put("StepDownTherapy", new ModuleSupplier(new StepDownTherapy()));

  }
  
  public static class Drug {
    String klass;
    String name;
    HealthRecord.Code code;
    double impactMin;
    double impactMax;
    HealthRecord.Code ingredient;
    boolean prescribable;
  }
  
  public static final LinkedHashMap<String, Set<Drug>> HTN_TRIAL_FORMULARY;
  
  public static final Map<String, Range<Double>> HTN_DRUG_IMPACTS;
  
  static{
    try {
      LinkedHashMap<String,Set<Drug>> formulary = new LinkedHashMap<>();
      Map<String, Range<Double>> drugImpacts = new HashMap<>();
      
      String csv = Utilities.readResource("htn_trial_drugs.csv");
      
      List<LinkedHashMap<String, String>> table = SimpleCSV.parse(csv);
      
      for (LinkedHashMap<String,String> line : table) {
        String klass = line.get("Class");
        
        if (!formulary.containsKey(klass)) {
          formulary.put(klass, new HashSet<>());
        }
        
        String code = line.get("RxNorm");
        double impactMin = Double.parseDouble(line.get("Impact Minimum"));
        double impactMax = Double.parseDouble(line.get("Impact Maximum"));

        Range<Double> impact = new Range<>();
        impact.low = impactMin;
        impact.high = impactMax;
        
        drugImpacts.put(code, impact);
        
        Set<Drug> drugClass = formulary.get(klass);
        Drug drug = new Drug();
        drug.klass = klass;
        drug.name = line.get("Simple Name");
        drug.code = new HealthRecord.Code("RxNorm", code, line.get("Display"));
        drug.impactMin = impactMin;
        drug.impactMax = impactMax;
        drug.ingredient = null; // TODO maybe
        drug.prescribable = Boolean.parseBoolean(line.get("Prescribable")); // allow medications used elsewhere in synthea to have impacts, without letting them be prescribed here
        
        drugClass.add(drug);
        
      }
      
      HTN_TRIAL_FORMULARY = formulary;
      HTN_DRUG_IMPACTS = drugImpacts;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }
  
  public static int countDrugs(Person person) {
    int count = 0;
    for (Set<Drug> drugClass : HTN_TRIAL_FORMULARY.values()) {
      for (Drug drug : drugClass) {
        if (person.record.medicationActive(drug.code.code)) {
          count++;
        }
      }
    }
    
    return count;
  }
  
  public static Drug findTherapyNotInUse(Person person) {
    Drug nextDrug = null;
    for (Set<Drug> drugClass : HTN_TRIAL_FORMULARY.values()) {
      for (Drug drug : drugClass) {
        if (person.record.medicationActive(drug.code.code)) {
          nextDrug = null; // ensure nothing from this class is picked
          break; // break out of drug class, the person has one in this class already
        }
        
        if (!drug.prescribable) {
          continue;
        }
        
        // note: allergyActive copy-pasted from Andy's WIP branch
        if (drug.ingredient != null && person.record.allergyActive(drug.ingredient.code)) {
          continue;
        }
        
        nextDrug = drug;
        break;
      }
      if (nextDrug != null) break;
    }
    
    if (nextDrug == null) {
      System.err.println("could not find new therapy not in use, note patient is on " + countDrugs(person) + " meds");
    }
    
    return nextDrug;
  }
  
  
  public static Drug findTherapyToEnd(Person person) {
    Drug nonPrescribableTherapyToEnd = null;
    
    List<Set<Drug>> drugClasses = new ArrayList<>(HTN_TRIAL_FORMULARY.values());
    // here we reverse the drug class orders, to try to make sure that
    // we stop drugs in the opposite order they were added (think a LIFO stack)
    Collections.reverse(drugClasses);
    
    for (Set<Drug> drugClass : drugClasses) {
      for (Drug drug : drugClass) {
        
        if (person.record.medicationActive(drug.code.code)) {
          if (!drug.prescribable && nonPrescribableTherapyToEnd == null) {
            nonPrescribableTherapyToEnd = drug;
            continue;
          }
          return drug;
        }
      }
    }
    
    if (nonPrescribableTherapyToEnd != null) {
      return nonPrescribableTherapyToEnd;
    }
    
    boolean hypertension = (boolean) person.attributes.getOrDefault("hypertension", false);
    
    if (hypertension) {
      System.err.println("patient with hypertension has low BP but not on any meds?");
    }
    return null;
  }
  
  public static Drug findNonTitratedDrug(Person person, TitrationDirection direction) {
    Drug nonTitratedDrug = null;
    
    for (Set<Drug> drugClass : HTN_TRIAL_FORMULARY.values()) {
      for (Drug drug : drugClass) {
        if (person.record.medicationActive(drug.code.code) && !isTitrated(drug, person, direction)) {
          nonTitratedDrug = drug;
          break;
        }
        
        if (nonTitratedDrug != null) break;
      }
    }
    
    if (nonTitratedDrug == null && direction == TitrationDirection.UP) {
      
      
      for (Set<Drug> drugClass : HTN_TRIAL_FORMULARY.values()) {
        for (Drug drug : drugClass) {
          System.out.println(person.seed + " - " + drug.code.display + ": active:" + person.record.medicationActive(drug.code.code));
          System.out.println(person.seed + " - " + drug.code.display + ": titrated:" + getTitrated(drug.code.code, person));
            
        }
      }
      
      throw new IllegalStateException("could not find nontitrated drug for "+direction+", note patient is on " + countDrugs(person) + " meds, titration count: " + person.attributes.get("titration_counter"));
    }
    
    return nonTitratedDrug;
  }
  
  public static enum TitrationDirection { UP, DOWN };
  
  public static boolean isTitrated(Drug drug, Person person, TitrationDirection direction) {
    Map<String, TitrationDirection> titratedDrugs = (Map<String, TitrationDirection>)person.attributes.get("titrated_drugs");
    if (titratedDrugs == null || titratedDrugs.isEmpty()) return false;
    
    String key = drug.code.code;
    
    return titratedDrugs.containsKey(key) && titratedDrugs.get(key).equals(direction);
  }
  
  public static TitrationDirection getTitrated(String drug, Person person) {
    Map<String, TitrationDirection> titratedDrugs = (Map<String, TitrationDirection>)person.attributes.get("titrated_drugs");
    if (titratedDrugs == null || titratedDrugs.isEmpty()) return null;
    
    return titratedDrugs.get(drug);
  }
  
  public static void markTitrated(Drug drug, Person person, TitrationDirection direction) {
    Map<String, TitrationDirection> titratedDrugs = (Map<String, TitrationDirection>) person.attributes.get("titrated_drugs");
    if (titratedDrugs == null) {
      titratedDrugs = new HashMap<>();
      person.attributes.put("titrated_drugs", titratedDrugs);
    }
    
    String key = drug.code.code;
    
    if (direction == null) {
      titratedDrugs.remove(key);
    } else {
      titratedDrugs.put(key, direction);
    }
  }

  public static final String ADD_THERAPY = "add_therapy";
  public static final String TITRATE = "titrate";
  
  public static class ChooseNextTherapy extends Module {
    public ChooseNextTherapy() {
      this.name = "ChooseNextTherapy";
      this.submodule = true;
    }

    public Module clone() {
      return this;
    }


    
    public static final double TITRATION_RATIO = 0.7; // 70% of the time, titrate every time rather than adding new class
    
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
      Drug nextActionCode = null;
      
      if (trialArm.equals("intensive")) {
        if (sbp >= 120) {
          if (milepost) {
            // Add therapy not in use
            // see participant monthly, handled in module
            nextAction = ADD_THERAPY;
            
          } else {
            // Titrate or add therapy not in use
            // see participant monthly, handled in module
            
            if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
              nextAction = ADD_THERAPY;
            } else {
              nextAction = TITRATE;
            }

          }
        } else if (dbp >= 100 || (dbp >= 90 && dbpGte90)) {
          // titrate or add therapy not in use
          
          if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
            nextAction = ADD_THERAPY;
          } else {
            nextAction = TITRATE;
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
          } else {
            nextAction = TITRATE;
          }
          
        } else if (dbp >= 100 || (dbp >= 90 && dbpGte90)) {
          // titrate or add therapy not in use
          
          if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
            nextAction = ADD_THERAPY;
          } else {
            nextAction = TITRATE;
          }
          
        } else if (sbp < 130 || (sbp < 135 && sbpLt135)) {
          // step down
          throw new IllegalStateException("ChooseNextTherapy module called for patient that needs StepDown");
        } else {
          // why did the module get called at all?
          throw new IllegalStateException("ChooseNextTherapy module called for patient that doesn't need it!");
        }
      }
      
      if (nextAction == ADD_THERAPY) {
        nextActionCode = findTherapyNotInUse(person);

     // TEMPORARY - add the medication here for quick debugging
        
        HealthRecord.Medication temp = person.record.medicationStart(time, nextActionCode.code.code, false);
        temp.codes.add(nextActionCode.code);
        
        
      } else if (nextAction == TITRATE) {
        nextActionCode = findNonTitratedDrug(person, TitrationDirection.UP);
        markTitrated(nextActionCode, person, TitrationDirection.UP);
        titrationCounter.incrementAndGet();

      } else {
        throw new IllegalStateException("nextAction set to something unexpected");
      }
      
 
      
      person.attributes.put("htn_trial_next_action", nextAction);
      person.attributes.put("htn_trial_next_action_code", nextActionCode.code);
      
      return true; // submodule is complete
    }
    
//    private static final String[][] DRUG_HIERARCHY = {
//        { "chlorthalidone", "furosemide", "spironolactone", "triamterene/hctz", "amiloride" }, // diuretics
//        { "lisinopril" }, // ace inhibitor
//        {"losartan", "azilsartan","azilsartan/chlorthalidone"}, // angiotensin receptor blocker
//        {"diltiazem", "amlodipine"}, // calcium channel blocker
//        {"metoprolol tartrate", "atenolol", "atenolol/chlorthalidone"}, // beta blocker
//        {"hydrazine", "minoxidil"}, // vasodilators
//        {"guanfacine"}, // alpha 2 agonist
//        {"doxazosin"}, // alpha blocker
//        {"kcl tablets", "kcl oral"}, // potassium supplement
//    };
    
    
  }
  
  
  public static class StepDownTherapy extends Module {
    public StepDownTherapy() {
      this.name = "StepDownTherapy";
      this.submodule = true;
    }

    public Module clone() {
      return this;
    }
    
    
    public boolean process(Person person, long time) {
      double sbp = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
     
      boolean sbpLt135 = (boolean)person.attributes.getOrDefault("sbp_lt_135", false);
      
      if (sbp < 130 || (sbp < 135 && sbpLt135)) {
        AtomicInteger titrationCounter = (AtomicInteger)person.attributes.get("titration_counter");

        Drug drugToToTitrateDown = findNonTitratedDrug(person, TitrationDirection.DOWN);
        if (drugToToTitrateDown == null) {
          Drug drugToEnd = findTherapyToEnd(person);
          
          if (drugToEnd == null) {
            person.attributes.remove("htn_trial_next_action");
            person.attributes.remove("htn_trial_next_action_code");
            return true;
          }
          
          if (isTitrated(drugToEnd, person, TitrationDirection.UP)) {
            titrationCounter.decrementAndGet();
          }
          
          HealthRecord.Medication medToEnd = (HealthRecord.Medication) person.record.present.get(drugToEnd.code.code);
          person.attributes.put("htn_trial_next_action", "end drug");
          person.attributes.put("htn_trial_next_action_code", medToEnd);
        } else {
          
          if (isTitrated(drugToToTitrateDown, person, TitrationDirection.UP)) {
            titrationCounter.decrementAndGet();
          }
          
          markTitrated(drugToToTitrateDown, person, TitrationDirection.DOWN);
          person.attributes.put("htn_trial_next_action", TITRATE);
          person.attributes.put("htn_trial_next_action_code", drugToToTitrateDown.code);
        }
        return true;
      } else {
        throw new IllegalStateException("stepdown called for patient that doesn't need it");
      }
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
      
      
      if (visitSchedule.isEmpty()) {
        person.attributes.put("trial_complete", true);
        return true;
      }
      
//      System.out.println(person.seed + " waiting till next visit, curr: " + ExportHelper.iso8601Timestamp(time));
      
//      visitSchedule.removeIf(visit -> visit < time); // remove all visits already completed
      
      long nextEncounter;
      
      if (seeParticipantMonthly) {
        HealthRecord.Encounter previousEncounter = null;
        
        for (HealthRecord.Encounter e : person.record.encounters) {
          if (!e.codes.isEmpty() && e.codes.get(0).code.equals("1234")) {
            // TODO: pick the right code
            
            // assumes that encounters are always added sequentially, which they should be
            previousEncounter = e;
          }
        }

        // delay 1 month since last encounter
        nextEncounter = previousEncounter.start + Utilities.convertTime("months", 1);
        boolean milestone = isMilestoneVisit(time, visitSchedule);
        person.attributes.put("milestone_visit", milestone);
        
//        System.out.println(person.seed + "'s next visit is 1 month: " + ExportHelper.iso8601Timestamp(nextEncounter));

        
        if (milestone && time >= nextEncounter) {
//          System.out.println(person.seed + " removing index 0");

          visitSchedule.remove(0);
          return true;
        }
        
        return (time >= nextEncounter);
        
      } else {
        // wait until the next milestone        

  
        
        person.attributes.put("milestone_visit", true);
        nextEncounter = visitSchedule.get(0);
        
//        System.out.println(person.seed + "'s next visit is mlestone: " + ExportHelper.iso8601Timestamp(nextEncounter));

        
        if (time >= nextEncounter) {
//          System.out.println(person.seed + " removing index 0");

          visitSchedule.remove(0); // remove the current visit once it starts
          return true;
        }
        
        return false;
      }

//      System.out.println("time: " + ExportHelper.iso8601Timestamp(time) + ", nextEncounter: " + ExportHelper.iso8601Timestamp(nextEncounter))
      
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
