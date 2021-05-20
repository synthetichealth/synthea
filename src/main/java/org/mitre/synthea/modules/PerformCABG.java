package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.engine.Distribution;
import org.mitre.synthea.engine.Distribution.Kind;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.VitalSign;

public class PerformCABG extends Module {

  public PerformCABG() {
    this.name = "PerformCABG";
    this.submodule = true; // make sure this doesn't run except when called
  }
  
  public Module clone() {
    return this;
  }
  
  private static final Code CABG = new Code("SNOMED-CT", "232717009", "Coronary artery bypass grafting (procedure)");
  private static final Code EMERGENCY_CABG = new Code("SNOMED-CT", "414088005", "Emergency coronary artery bypass graft (procedure)");
  
  private static List<Clinician> cabgSurgeons = loadCabgSurgeons();
  
  private static List<Clinician> loadCabgSurgeons() {
    try {
    String cabgSurgeonsCsv = Utilities.readResource("cabg_surgeons.csv");
    List<LinkedHashMap<String,String>> surgeons = SimpleCSV.parse(cabgSurgeonsCsv);
    
    // only keep "CABG" code lines
    surgeons.removeIf(s -> !s.get("surgery_group_label").equals("CABG"));
    
    
    Provider provider = Provider.getProviderList().get(0);
    
    Random clinicianRand = new Random(-1);

    ArrayList<Clinician> clinicianList = new ArrayList<>();
    
    int id = 0;
    for (LinkedHashMap<String,String> surgeon : surgeons) {
      Clinician clin = new Clinician(-1, clinicianRand, id++, provider);
      clin.attributes.putAll(surgeon);
      
      clin.attributes.put(Clinician.SPECIALTY, "CABG");

      
      String surgeonCode = (String)surgeon.get("surgeon_code_final");

      clin.attributes.put(Clinician.FIRST_NAME, surgeonCode);
      clin.attributes.put(Clinician.LAST_NAME, surgeonCode);
      clin.attributes.put(Clinician.NAME, surgeonCode);
      clin.attributes.put(Clinician.NAME_PREFIX, "Dr.");
      
      clin.attributes.put(Clinician.GENDER, clinicianRand.nextBoolean() ? "F" : "M");
      
      clin.attributes.put(Person.ADDRESS, provider.address);
      clin.attributes.put(Person.CITY, provider.city);
      clin.attributes.put(Person.STATE, provider.state);
      clin.attributes.put(Person.ZIP, provider.zip);
      clin.attributes.put(Person.COORDINATE, provider.getLonLat());
      
      clinicianList.add(clin);
    }
    
    provider.clinicianMap.put("CABG", clinicianList);
    
    return clinicianList;
    
    } catch (Exception e) {
      throw new Error(e);
    }
  }
  
  private static Distribution NOISE = buildNoise();
  
  private static Distribution buildNoise() {
    Distribution d = new Distribution();
    
    d.kind = Kind.GAUSSIAN;
    
    d.parameters = new HashMap<>();
    d.parameters.put("standardDeviation", 35.0);
    d.parameters.put("mean", -10.0);
    
    return d;
  }
  
  @Override
  public boolean process(Person person, long time) {
    long stopTime;
    
    if (person.attributes.containsKey("cabg_stop_time") ) {
      stopTime = (long) person.attributes.get("cabg_stop_time");
    } else {
      Clinician surgeon = cabgSurgeons.get(person.randInt(cabgSurgeons.size()));
      
      stopTime = time + getCabgDuration(person, surgeon, time);
      
      person.attributes.put("cabg_stop_time", stopTime);
      
      boolean emergency = (boolean)person.attributes.get("care_score_e");
      
      Code code = emergency ? EMERGENCY_CABG : CABG;
      
      String primaryCode = code.code;
      Procedure cabg = person.record.procedure(time, primaryCode);
      cabg.name = this.name;
      cabg.codes.add(code);
      
      cabg.stop = stopTime;
      cabg.clinician = surgeon;
      
      surgeon.incrementEncounters();
      
      // hack this clinician back onto the record?
      person.record.currentEncounter(stopTime).clinician = surgeon;
      
      String reason = "cardiac_surgery_reason";
      
      // below copied from Procedure State to make this easier
      if (person.attributes.containsKey(reason)) {
        Entry condition = (Entry) person.attributes.get(reason);
        cabg.reasons.addAll(condition.codes);
      } else if (person.hadPriorState(reason)) {
        // loop through the present conditions, the condition "name" will match
        // the name of the ConditionOnset state (aka "reason")
        for (Entry entry : person.record.present.values()) {
          if (reason.equals(entry.name)) {
            cabg.reasons.addAll(entry.codes);
          }
        }
      }
    }
    
    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the next state
    // if we return false, the submodule did not complete (like with a Delay) and will re-process the next timestep.
    if (time >= stopTime) {
      // remove the stop time so that a second processing can go through correctly
      person.attributes.remove("cabg_stop_time");
      
      person.history.get(0).exited = stopTime; // HACK for ensuring rewind time works. it will get overwritten later
      return true;
    } else {
      return false;
    }
  }

  public static final long MAX_DURATION = Utilities.convertTime("minutes", 926);
  public static final long MIN_DURATION = Utilities.convertTime("minutes", 45);

  // commented out for now - probably easier to just manually code these than make it generic
//  private static final Map<String,Double> COEFFICIENTS;
//  private static final Table<String,String,Double> VALUE_COEFFICIENTS;
//  
//  static {
//    Map<String, Double> coefficients = new HashMap<>();
//    coefficients.put("age", 14.0);
//    COEFFICIENTS = coefficients;
//    
//    Table<String,String,Double> valueCoefficients = HashBasedTable.create();
//    valueCoefficients.put(Person.GENDER, "F", 0.3);
//    valueCoefficients.put(Person.GENDER, "M", 0.3);
//    VALUE_COEFFICIENTS = valueCoefficients;
//  }
  
  
  /*
Key,Coefficient,Type
constant,79.23,number
Age,-0.63,number
bsa,12.39,number
surgeon_mean_time,0.93,number
n_surgeries,-0.03,number
GENDER.M,4.16,category
GENDER.F,-3.91,category
care_score_e.1,-10.99,category
care_score_e.2,-6.85,category
care_score_e.3,0.03,category
care_score_e.3E,-5.61,category
care_score_e.4,6.54,category
care_score_e.4E,3.18,category
care_score_e.5,-19.50,category
care_score_e.5E,-23.10,category
Cardiac_Redo.False,-25.37,category
Cardiac_Redo.True,97.48,category
Operative_priority.0,-46.84,category
Operative_priority.1,-15.70,category
Operative_priority.2,-32.01,category
Operative_priority.3,-15.82,category
Operative_priority.4,-60.55,category
   */
  
  private static final double AGE_COEFFICIENT = -0.63;
  private static final double BSA_COEFFICIENT = 12.39;
  
  private static final double surgeon_mean_time_COEFFICIENT = 0.93;
  private static final double n_surgeries_COEFFICIENT = -0.03;
  
  private static final double M_COEFFICIENT = 4.16;
  private static final double F_COEFFICIENT = -3.91;
  
  private static final double REDO_TRUE_COEFFICIENT = 97.48;
  private static final double REDO_FALSE_COEFFICIENT = -25.37;
  
  // care score
  private static final Map<String, Double> CARE_SCORE_COEFFICIENTS = createCareScoreCoefficients();

  private static Map<String, Double> createCareScoreCoefficients() {
    Map<String, Double> map = new HashMap<>();
    map.put("1",  -10.99);
    map.put("2",   -6.85);
    map.put("3",    0.03);
    map.put("3E",  -5.61);
    map.put("4",    6.54);
    map.put("4E",   3.18);
    map.put("5",  -19.50);
    map.put("5E", -23.10);
    return map;
  }
  
  // operative priority
  private static final double[] OPER_PRIORITY_COEFFICIENTS = { 
      /* 0 */ -46.84, /* 1 */ -15.70, /* 2 */ -32.01, /* 3 */ -15.82, /* 4 */ -60.55 };  
  
  public static long getCabgDuration(Person person, Clinician surgeon, long time) {
   
    double duration = 79.23; // baseline, minutes
  
    duration += (AGE_COEFFICIENT * person.ageInDecimalYears(time));
    duration += (BSA_COEFFICIENT * getBodySurfaceArea(person, time));
    
    if ("F".equals(person.attributes.get(Person.GENDER))) {
      duration += F_COEFFICIENT;
    } else {
      duration += M_COEFFICIENT;
    }
    
    boolean cardiacRedo = person.record.present.containsKey(EMERGENCY_CABG.code) || person.record.present.containsKey(CABG.code);
    if (cardiacRedo) {
      duration += REDO_TRUE_COEFFICIENT;
    } else {
      duration += REDO_FALSE_COEFFICIENT;
    }
    
    Integer careScore = (Integer)person.attributes.get("care_score");
    
    String careScoreString = careScore.toString();
    
    if (careScore > 2 && (boolean)(person.attributes.get("care_score_e"))) {
      careScoreString += "E";
    }
    
    if (!CARE_SCORE_COEFFICIENTS.containsKey(careScoreString)) {
      throw new IllegalStateException("Failed to find " + careScoreString);
    }
    
    duration += CARE_SCORE_COEFFICIENTS.get(careScoreString);
    
    duration += OPER_PRIORITY_COEFFICIENTS[(int)person.attributes.get("care_priority_level")];
    
    
    // these are ints but all have a trailing .0 in the CSV
    int surgeon_n_surgeries = (int)Double.parseDouble((String)surgeon.attributes.get("n_surgeries"));
    double surgeon_mean_time = Double.parseDouble((String)surgeon.attributes.get("mean"));
    
    duration += (surgeon_mean_time_COEFFICIENT * surgeon_mean_time);
    duration += (n_surgeries_COEFFICIENT * surgeon_n_surgeries);
   
    duration += NOISE.generate(person);
    
    long durationInMs = Utilities.convertTime("minutes", duration);
    
    return bound(durationInMs, MIN_DURATION, MAX_DURATION);
  }
  
  private static double getBodySurfaceArea(Person person, long time) {
    double h = person.getVitalSign(VitalSign.HEIGHT, time);
    double w = person.getVitalSign(VitalSign.WEIGHT, time);
    
    return Math.sqrt(h * w / 3600);
  }
  
  private static long bound(long value, long min, long max) {
    return Math.min(Math.max(value, min), max);
  }
}
