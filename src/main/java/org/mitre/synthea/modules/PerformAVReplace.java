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

public class PerformAVReplace extends Module {

  public PerformAVReplace() {
    this.name = "PerformAVReplace";
    this.submodule = true; // make sure this doesn't run except when called
  }
  
  public Module clone() {
    return this;
  }

  private static final Code AVREPLACE_CODE =
      new Code("SNOMED-CT", "26212005", "Replacement of aortic valve (procedure)");
  
  private static Map<String,Clinician> surgeonsById = new HashMap<String,Clinician>();
  private static RandomCollection<Clinician> surgeons =
      new RandomCollection<Clinician>();

  
  static {
    try {
      // Load the Surgeon File
      String cabgSurgeonsCsv = Utilities.readResource("surgeon_stats_new.csv");
      List<LinkedHashMap<String,String>> surgeonsFile = SimpleCSV.parse(cabgSurgeonsCsv);

      // First, extract the unique list of surgeon identifiers
      Set<String> identifierSet = new HashSet<String>();
      for (LinkedHashMap<String,String> row : surgeonsFile) {
        identifierSet.add(row.get("RandomID"));
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

      // Now create a Clinician representing each surgeon...
      Provider provider = Provider.getProviderList().get(0);
      Random clinicianRand = new Random(-1);
      int id = 0;
      for (String surgeonId : identifierSet) {
        Clinician clin = new Clinician(-1, clinicianRand, id++, provider);

        clin.attributes.put(Clinician.SPECIALTY, "Surgeon");
        clin.attributes.put(Clinician.FIRST_NAME, surgeonId);
        clin.attributes.put(Clinician.LAST_NAME, surgeonId);
        clin.attributes.put(Clinician.NAME, surgeonId);
        clin.attributes.put(Clinician.NAME_PREFIX, "Dr.");

        clin.attributes.put(Clinician.GENDER, clinicianRand.nextBoolean() ? "F" : "M");

        clin.attributes.put(Person.ADDRESS, provider.address);
        clin.attributes.put(Person.CITY, provider.city);
        clin.attributes.put(Person.STATE, provider.state);
        clin.attributes.put(Person.ZIP, provider.zip);
        clin.attributes.put(Person.COORDINATE, provider.getLonLat());

        surgeonsById.put(surgeonId, clin);
      }

      synchronized (provider.clinicianMap) {
        if (!provider.clinicianMap.containsKey(ClinicianSpecialty.CARDIAC_SURGERY)) {
          provider.clinicianMap.put(ClinicianSpecialty.CARDIAC_SURGERY,
              new ArrayList<Clinician>(surgeonsById.values()));
        } else {
          List<Clinician> alreadyExist =
              provider.clinicianMap.get(ClinicianSpecialty.CARDIAC_SURGERY);
          surgeonsById.clear();
          for (Clinician doc : alreadyExist) {
            surgeonsById.put((String) doc.attributes.get(Clinician.NAME), doc);
          }
        }
      }

      // Finally, go back through the surgeon file data and create distributions
      // for each surgery...
      for (LinkedHashMap<String,String> row : surgeonsFile) {
        String operation = row.get("new_surgery_cat");
        if (!operation.equals("AVR_Replace")) {
          // we only care about this operation type
          continue;
        }
        
        String identifier = row.get("RandomID");
        Clinician clin = surgeonsById.get(identifier);

        Double weight = Double.parseDouble(row.get("n_surgeries"));
        Double mean = Double.parseDouble(row.get("mean"));
        Double std = Double.parseDouble(row.get("std"));
        Double min = Double.parseDouble(row.get("min"));
        Double max = Double.parseDouble(row.get("max"));

        Distribution distribution = buildDistribution(mean, std, min, max);
        clin.attributes.put(operation, distribution);
        // note that surgeons have different mean times for on/off pump
        
        clin.attributes.put("mean_surgeon_time_avreplace", mean);
        surgeons.add(weight, clin);
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
    
    if (person.attributes.containsKey("avreplace_stop_time")) {
      stopTime = (long) person.attributes.get("avreplace_stop_time");
    } else {
      Clinician surgeon = surgeons.next(person);

      double durationInMinutes = getProcedureDuration(person, surgeon, time);
      long durationInMs = Utilities.convertTime("minutes", durationInMinutes);
      stopTime = time + durationInMs;
      person.attributes.put("avreplace_stop_time", stopTime);


      Code code = AVREPLACE_CODE;
      String primaryCode = code.code;
      Procedure tavr = person.record.procedure(time, primaryCode);
      tavr.name = this.name;
      tavr.codes.add(code);

      tavr.stop = stopTime;
      tavr.clinician = surgeon;

      surgeon.incrementEncounters();
      surgeon.incrementProcedures();
      surgeon.getOrganization().incrementEncounters(EncounterType.INPATIENT, Utilities.getYear(time));

      // hack this clinician back onto the record?
      person.record.currentEncounter(time).clinician = surgeon;

      String reason = "cardiac_surgery_reason";

      // below copied from Procedure State to make this easier
      if (person.attributes.containsKey(reason)) {
        Entry condition = (Entry) person.attributes.get(reason);
        tavr.reasons.addAll(condition.codes);
      } else if (person.hadPriorState(reason)) {
        // loop through the present conditions, the condition "name" will match
        // the name of the ConditionOnset state (aka "reason")
        for (Entry entry : person.record.present.values()) {
          if (reason.equals(entry.name)) {
            tavr.reasons.addAll(entry.codes);
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
      person.attributes.remove("avreplace_stop_time");
      
      // HACK for ensuring rewind time works. it will get overwritten later
      person.history.get(0).exited = stopTime;
      return true;
    } else {
      return false;
    }
  }
  
  public static final double getProcedureDuration(Person person, Clinician surgeon, long time) {

    String operativeStatus = (String)person.attributes.get("operative_status");
    boolean elective = operativeStatus.equals("elective");
    boolean emergent = operativeStatus.equals("emergent");
    boolean urgent = operativeStatus.equals("urgent");
        
    boolean redo = Boolean.parseBoolean((String)person.attributes.getOrDefault("PriorValveOp", "false"));
    
    double meanSurgeonTime = (double) surgeon.attributes.get("mean_surgeon_time_avreplace");

    int numDiseasedVessels = (int) person.attributes.getOrDefault("avrr_num_diseased_vessels", 0);
    boolean anyDiseasedVessels = numDiseasedVessels > 0;
    
    double gaussianNoise = person.randGaussian();
    
    return getProcedureDuration(elective, emergent, urgent, redo, meanSurgeonTime, anyDiseasedVessels, gaussianNoise);
  }
  
  public static final double getProcedureDuration(boolean elective, boolean emergent, boolean urgent,
      boolean redo, double meanSurgeonTime, boolean anyDiseasedVessels, double gaussianNoise) {

//    Status__Elective,0.6057,Binary,
//    Status__Emergent,36.76,Binary,
//    Status__Urgent,9.743,Binary,
//    Redo,79.32,Binary,
//    mean_surgeon_time,0.666,Continuous,
//    anyDisV,17.143,Binary,Whether patient had any diseased coronary artery vessels prior to surgery
//    constant,47.11,,

    
    double duration = 47.11; // constant value

    if (elective) duration += 0.6057;
    
    if (emergent) duration += 36.76;
    
    if (urgent) duration += 9.743;
        
    if (redo) duration += 79.32;
    
    duration += (0.666 * meanSurgeonTime);
    
    if (anyDiseasedVessels) duration += 17.143;
    
    duration += gaussianNoise * 40;
    
    return duration;
  }

  private static double bound(double value, double min, double max) {
    return Math.min(Math.max(value, min), max);
  }
}
