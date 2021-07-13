package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.mitre.synthea.engine.Components.Range;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.Module.ModuleSupplier;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;

public class HypertensionTrial {

  public static void registerModules(Map<String, ModuleSupplier> modules) {
   new WrappedFunctionModule("ChooseNextTherapy", HypertensionTrial::chooseNextTherapy).register(modules);
   new WrappedFunctionModule("VisitCheck", HypertensionTrial::visitCheck).register(modules);
   new WrappedFunctionModule("StepDownTherapy", HypertensionTrial::stepDownTherapy).register(modules);
  }
  
  public static class WrappedFunctionModule extends Module {
    private static final long serialVersionUID = -5448385841737112663L;
    private BiConsumer<Person, Long> processFunction;
    public WrappedFunctionModule(String name, BiConsumer<Person,Long> processFunction) {
      this.name = name;
      this.processFunction = processFunction;
      this.submodule = true;
    }
    
    public Module clone() {
      return this;
    }
    
    @Override
    public boolean process(Person person, long time) {
      this.processFunction.accept(person, time);
      return true;
    }
    
    public void register(Map<String, ModuleSupplier> modules) {
      modules.put(this.name, new ModuleSupplier(this));
    }
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

      // ensure nothing from this class is picked
      if (drugClass.stream().anyMatch(drug -> person.record.medicationActive(drug.code.code))) {
        continue;
      }
      
      List<Drug> drugsInClass = new ArrayList<>(drugClass);
      Collections.shuffle(drugsInClass);
      
      for (Drug drug : drugsInClass) {
        // be super sure
        if (person.record.medicationActive(drug.code.code)) {
          nextDrug = null; 
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
    
    boolean hypertension = person.getBoolean("hypertension", false);
    
    if (hypertension) {
      System.err.println("patient with hypertension has low BP but not on any meds?");
    }
    return null;
  }
  
  public static Drug findTitratableDrug(Person person, TitrationDirection direction) {
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
  public static final String SKIP = "skip";
  

  public static final double TITRATION_RATIO = 1; // always titrate every time rather than adding new class
    
  public static void chooseNextTherapy(Person person, long time) {      
    // output: 
    // htn_trial_next_action = "titrate" or "add_therapy"
    // htn_trial_next_action_code = code for titrating or adding
    
  	
    // note: some repetition of logic here from what's in the module
    // there may be opportunities to de-dup, but I like having the detail in the module
    
    String trialArm = person.getString("trial_arm");
    boolean milepost = person.getBoolean("milepost_visit", false);
    
    double sbp = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
    double dbp = person.getVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, time);
   
    boolean dbpGte90 = person.getBoolean("dbp_gte_90", false);
    boolean sbpGte140 = person.getBoolean("sbp_gte_140", false);
    boolean sbpLt135 = person.getBoolean("sbp_lt_135", false);
    boolean sbpGt120 = person.getBoolean("sbp_gt_120", false);
    
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
        
        if (sbp <= 125 && !sbpGt120) {
          nextAction = SKIP;
        } else if (milepost) {
          // Add therapy not in use
          // see participant monthly, handled in module
          nextAction = ADD_THERAPY;
          person.attributes.put("sbp_gt_120", true);
        } else {
          // Titrate or add therapy not in use
          // see participant monthly, handled in module
          
          if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
            nextAction = ADD_THERAPY;
          } else {
            nextAction = TITRATE;
          }
          person.attributes.put("sbp_gt_120", true);

        }
      } else if (dbp >= 100 || (dbp >= 90 && dbpGte90)) {
        // titrate or add therapy not in use
        
        if (drugCount == titrationCounter.get() || person.rand() > TITRATION_RATIO) {
          nextAction = ADD_THERAPY;
        } else {
          nextAction = TITRATE;
        }
        person.attributes.put("sbp_gt_120", false);

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
      nextActionCode = findTitratableDrug(person, TitrationDirection.UP);
      markTitrated(nextActionCode, person, TitrationDirection.UP);
      titrationCounter.incrementAndGet();

    } else if (nextAction != SKIP) {
      throw new IllegalStateException("nextAction set to something unexpected: " + nextAction);
    }
    
    person.attributes.put("htn_trial_next_action", nextAction);
    if (nextAction != SKIP) {
      person.attributes.put("htn_trial_next_action_code", nextActionCode.code);
    }
  }
   
  public static void stepDownTherapy(Person person, long time) {
    double sbp = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, time);
    String trialArm = person.getString("trial_arm");
    
    if (trialArm.equals("intensive")) {
      if (sbp < 114 || person.rand() < 0.03) {
        // titrate down if BP is low, or randomly
        AtomicInteger titrationCounter = (AtomicInteger)person.attributes.get("titration_counter");
  
        Drug drugToToTitrateDown = findTitratableDrug(person, TitrationDirection.DOWN);
        if (drugToToTitrateDown == null) {
          person.attributes.put("htn_trial_next_action", SKIP);
          person.attributes.remove("htn_trial_next_action_code");
        } else {
          if (isTitrated(drugToToTitrateDown, person, TitrationDirection.UP)) {
            titrationCounter.decrementAndGet();
          }
          
          markTitrated(drugToToTitrateDown, person, TitrationDirection.DOWN);
          person.attributes.put("htn_trial_next_action", TITRATE);
          person.attributes.put("htn_trial_next_action_code", drugToToTitrateDown.code);
        }
      }
    } else {
      boolean sbpLt135 = person.getBoolean("sbp_lt_135", false);
      
      if (sbp < 130 || (sbp < 135 && sbpLt135)) {
        AtomicInteger titrationCounter = (AtomicInteger)person.attributes.get("titration_counter");

        Drug drugToToTitrateDown = findTitratableDrug(person, TitrationDirection.DOWN);
        if (drugToToTitrateDown == null) {
          Drug drugToEnd = findTherapyToEnd(person);
          
          if (drugToEnd == null) {
            person.attributes.remove("htn_trial_next_action");
            person.attributes.remove("htn_trial_next_action_code");
            return;
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
      } else {
        throw new IllegalStateException("stepdown called for patient that doesn't need it");
      }
    }
  }
  
 
  private static final Set<Integer> VISIT_SCHEDULE = new HashSet<>( Arrays.asList(1,2,3, 6,9,12, 15,18,21,24, 27,30,33,36, 39,42,45,48) ); 

  public static void visitCheck(Person person, long time) {
    int monthCount = (int)person.attributes.getOrDefault("htn_trial_month_count", 0);
    monthCount++;
    
    if (monthCount > 48) {
      person.attributes.put("trial_complete", true);
      return;
    }
    
    boolean milepost = (monthCount % 6) == 0; // every 6 months is a milepost visit
    boolean seeParticipantMonthly = person.getBoolean("see_participant_monthly", false);

    boolean should_have_encounter = seeParticipantMonthly || VISIT_SCHEDULE.contains(monthCount);
    
    person.attributes.put("htn_trial_month_count", monthCount);
    person.attributes.put("milepost_visit", milepost);
    person.attributes.put("htn_trial_should_start_encounter", should_have_encounter);
  }
}
