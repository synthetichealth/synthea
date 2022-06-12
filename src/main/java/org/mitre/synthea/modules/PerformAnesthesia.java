package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.mitre.synthea.engine.Distribution;
import org.mitre.synthea.engine.Distribution.Kind;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.VitalSign;

// TODO: extract common logic to a PerformProcedure submodule?
public class PerformAnesthesia extends Module {

  public PerformAnesthesia() {
    this.name = "PerformAnesthesia";
    this.submodule = true; // make sure this doesn't run except when called
  }
  
  public Module clone() {
    return this;
  }
  
  private static final Code ANESTHESIA =
      new Code("SNOMED-CT", "410770002", "Administration of anesthesia for procedure (procedure)");
  private static final Code ANESTHESIA_OR_SEDATION =
      new Code("SNOMED-CT", "410011004", "Administration of anesthesia AND/OR sedation (procedure)");

  
  private static Map<String,Clinician> anesthetists = new HashMap<String,Clinician>();
  private static Map<String, RandomCollection<Clinician>> anesthetistsBySurgeryType =
      new HashMap<>();
  
  static {
    try {
      // Load the Anesthetist File
      String anesthetistsCsv = Utilities.readResource("anesthetist_stats.csv");
      List<LinkedHashMap<String,String>> anesthetistsFile = SimpleCSV.parse(anesthetistsCsv);

      // First, extract the unique list of surgeon identifiers
      Set<String> identifierSet = new HashSet<String>();
      for (LinkedHashMap<String,String> row : anesthetistsFile) {
        identifierSet.add(row.get("anes_id"));
      }

      if (Provider.getProviderList().isEmpty()) {
        // awful hack to prevent a crash is the test suite,
        // if this module gets instantiated before providers load.
        // this should never happen when creating a real population
        Provider dummyProvider = new Provider();
        dummyProvider.address = "101 Fake St";
        dummyProvider.city = "Boston";
        dummyProvider.state = "MA";
        dummyProvider.zip = "02110";
        dummyProvider.getLonLat().setLocation(42, -71);
        Provider.getProviderList().add(dummyProvider);
      }

      // Now create a Clinician representing each anesthetist...
      Provider provider = Provider.getProviderList().get(0);
      Random clinicianRand = new Random(-9);
      int id = 0;
      for (String anesthetistId : identifierSet) {
        Clinician clin = new Clinician(-1, clinicianRand, id++, provider);

        clin.attributes.put(Clinician.SPECIALTY, "Anesthesiology");
        clin.attributes.put(Clinician.FIRST_NAME, anesthetistId);
        clin.attributes.put(Clinician.LAST_NAME, anesthetistId);
        clin.attributes.put(Clinician.NAME, anesthetistId);
        clin.attributes.put(Clinician.NAME_PREFIX, "Dr.");

        clin.attributes.put(Clinician.GENDER, clinicianRand.nextBoolean() ? "F" : "M");

        clin.attributes.put(Person.ADDRESS, provider.address);
        clin.attributes.put(Person.CITY, provider.city);
        clin.attributes.put(Person.STATE, provider.state);
        clin.attributes.put(Person.ZIP, provider.zip);
        clin.attributes.put(Person.COORDINATE, provider.getLonLat());

        anesthetists.put(anesthetistId, clin);
      }

      provider.clinicianMap.put(ClinicianSpecialty.ANESTHESIOLOGY, new ArrayList<Clinician>(anesthetists.values()));
      
      // Finally, go back through the anesthetist file data and create distributions
      // for each surgery...
      for (LinkedHashMap<String,String> row : anesthetistsFile) {
        String operation = row.get("new_surgery_col");
        
        String identifier = row.get("anes_id");
        Clinician clin = anesthetists.get(identifier);

        Double weight = Double.parseDouble(row.get("n_surgeries"));
        Double mean = Double.parseDouble(row.get("mean"));
        Double std = Double.parseDouble(row.get("std"));
        Double min = Double.parseDouble(row.get("min"));
        Double max = Double.parseDouble(row.get("max"));

        Distribution distribution = buildDistribution(mean, std, min, max);
        clin.attributes.put(operation, distribution);
        // note that surgeons have different mean times for on/off pump
        

        clin.attributes.put("mean_anesthesia_time", mean);
        
        RandomCollection<Clinician> clins = anesthetistsBySurgeryType.get(operation);
        
        if (clins == null) {
          clins = new RandomCollection<>();
          anesthetistsBySurgeryType.put(operation, clins);
        }
        
        clins.add(weight, clin);

      }
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  private static Distribution buildDistribution(double mean, double std, double min, double max) {
    Distribution d = new Distribution();
    d.kind = Kind.GAUSSIAN;
    d.parameters = new HashMap<>();
    d.parameters.put("standardDeviation", std);
    d.parameters.put("mean", mean);
    d.parameters.put("min", min);
    d.parameters.put("max", max);
    return d;
  }
  
  @Override
  public boolean process(Person person, long time) {
    long stopTime;
    
    if (person.attributes.containsKey("anesthesia_stop_time")) {
      stopTime = (long) person.attributes.get("anesthesia_stop_time");
    } else {
      Clinician anesthetist = null;

      String cardiacSurgery = (String) person.attributes.get("cardiac_surgery");
      // options: cabg, savreplace, savrepair, tavr
      // or null if this was called from general_anesthesia on a non-cardiac surgery
      // (ie. gallstones)
      
      if (cardiacSurgery == null) {
        // true means "done" so it won't process again next timestep
        return true;
      }
      
      Code code = null;
      
      switch (cardiacSurgery) {
      case "cabg":
        code = ANESTHESIA;
        anesthetist = anesthetistsBySurgeryType.get("CABG").next(person);
        break;
      case "savreplace":
        code = ANESTHESIA;
        anesthetist = anesthetistsBySurgeryType.get("AV_Replace").next(person);
        break;
      case "savrepair":
        code = ANESTHESIA;
        // TODO: what to use here?
        anesthetist = anesthetistsBySurgeryType.get("AV_Replace").next(person);
        break;
      case "tavr":
        code = ANESTHESIA_OR_SEDATION;
        anesthetist = anesthetistsBySurgeryType.get("TAVR").next(person);
        break;
      }


      double durationInMinutes = getProcedureDuration(person, cardiacSurgery, anesthetist, time);
      long durationInMs = Utilities.convertTime("minutes", durationInMinutes);
      stopTime = time + durationInMs;
      person.attributes.put("anesthesia_stop_time", stopTime);


      String primaryCode = code.code;
      Procedure anesthesiaProc = person.record.procedure(time, primaryCode);
      anesthesiaProc.name = this.name;
      anesthesiaProc.codes.add(code);
      anesthesiaProc.stop = stopTime;
      anesthesiaProc.clinician = anesthetist;

      anesthetist.incrementEncounters();
      anesthetist.incrementProcedures();
      anesthetist.getOrganization().incrementEncounters(EncounterType.INPATIENT, Utilities.getYear(time));

      // hack this clinician back onto the record?
      // only do this for the surgeon
      // person.record.currentEncounter(time).clinician = anesthetist;

      String reason = "cardiac_surgery_reason";

      // below copied from Procedure State to make this easier
      if (person.attributes.containsKey(reason)) {
        Entry condition = (Entry) person.attributes.get(reason);
        anesthesiaProc.reasons.addAll(condition.codes);
      } else if (person.hadPriorState(reason)) {
        // loop through the present conditions, the condition "name" will match
        // the name of the ConditionOnset state (aka "reason")
        for (Entry entry : person.record.present.values()) {
          if (reason.equals(entry.name)) {
            anesthesiaProc.reasons.addAll(entry.codes);
          }
        }
      }
    }

    // note return options here, see State$CallSubmodule
    // if we return true, the submodule completed and processing continues to the next state
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    if (time >= stopTime) {
      // remove the stop time so that a second processing can go through correctly
      person.attributes.remove("anesthesia_stop_time");
      
      // HACK for ensuring rewind time works. it will get overwritten later
      person.history.get(0).exited = stopTime;
      return true;
    } else {
      return false;
    }
  }
  
  public static final double getProcedureDuration(Person person, String cardiacSurgery, Clinician clin, long time) {
    double calculatedBMI = person.getVitalSign(VitalSign.BMI, time);

    double meanSurgeonTime = (double) clin.attributes.get("mean_anesthesia_time");
    
    double gaussianNoise = person.randGaussian();
    
    if (cardiacSurgery.equals("savreplace")) {
      boolean historyOfStroke = person.attributes.get("stroke_history") != null;
      return getProcedureDurationAVReplace(calculatedBMI, meanSurgeonTime, historyOfStroke, gaussianNoise);
    } else {
      int age = person.ageInYears(time);
      
      return getProcedureDuration(cardiacSurgery, age, calculatedBMI, meanSurgeonTime, gaussianNoise);
    }
  }


  /*
   * gaussian noise factors
     {'TAVR': {'mean': -11, 'std': 15},
      'CABG': {'mean': 4, 'std': 13},
      'AV_Replace': {'mean': 0, 'std': 8}}
   */
  
  
  public static final double getProcedureDuration(String cardiacSurgery, int age, double calculatedBMI, double meanAnesthetistTime, double gaussianNoise) {

    /* 
   
﻿Key,Coefficient,Type
mean_anesthetist_time,0.7938,continuous
Age,-0.16,continuous
CalculatedBMI,0.4162,continuous
const,10.191,continuous
     */
    
    double duration = 10.191; // constant value
    
    duration += meanAnesthetistTime * 0.7938;
    
    duration += age * -0.16;
    
    duration += calculatedBMI * 0.4162;
    
    if (cardiacSurgery.equals("cabg")) {
      duration += gaussianNoise * 13 + 4;
    } else if (cardiacSurgery.equals("tavr")) {
      duration += gaussianNoise * 15 - 11;
    }
    
    return duration;
  }
  
  
  public static final double getProcedureDurationAVReplace(double calculatedBMI, double meanAnesthetistTime, boolean historyOfStroke, double gaussianNoise) {    
    /*

CalculatedBMI,0.1395,Continuous,
mean_anesthetist_time,0.793,Continuous,mean anesthesia time for particular anesthetist
CVA_No,0.293,Binary,No prior history of stroke
CVA_Yes,4.27,Binary,Prior history of stroke
constant,4.568,continuous,

     */
 
    double duration = 4.568; // constant value
    
    duration += meanAnesthetistTime * 0.793;
    
    duration += calculatedBMI * 0.1395;
    
    duration += historyOfStroke ? 4.27 : 0.293;
    
    duration += gaussianNoise * 8;
    
    return duration;
  }
  
}
