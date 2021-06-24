package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

public class PerformCABG extends Module {

  public PerformCABG() {
    this.name = "PerformCABG";
    this.submodule = true; // make sure this doesn't run except when called
  }
  
  public Module clone() {
    return this;
  }
  
  private static final Code CABG =
      new Code("SNOMED-CT", "232717009", "Coronary artery bypass grafting (procedure)");
  private static final Code EMERGENCY_CABG =
      new Code("SNOMED-CT", "414088005", "Emergency coronary artery bypass graft (procedure)");
  
  private static List<Clinician> cabgSurgeons = loadCabgSurgeons();
  
  private static List<Clinician> loadCabgSurgeons() {
    try {
      String cabgSurgeonsCsv = Utilities.readResource("cabg_surgeons.csv");
      List<LinkedHashMap<String,String>> surgeons = SimpleCSV.parse(cabgSurgeonsCsv);

      // only keep "CABG" code lines
      surgeons.removeIf(s -> !s.get("surgery_group_label").equals("CABG"));

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

  private static Distribution buildDistribution(double mean, double std) {
    Distribution d = new Distribution();
    d.kind = Kind.GAUSSIAN;
    d.parameters = new HashMap<>();
    d.parameters.put("standardDeviation", std);
    d.parameters.put("mean", mean);
    return d;
  }
  
  @Override
  public boolean process(Person person, long time) {
    long stopTime;
    
    if (person.attributes.containsKey("cabg_stop_time")) {
      stopTime = (long) person.attributes.get("cabg_stop_time");
    } else {
      Clinician surgeon = cabgSurgeons.get(person.randInt(cabgSurgeons.size()));

      boolean emergency = false;
      String operativeStatus = (String) person.attributes.get("operative_status");
      if ("emergent".equals(operativeStatus) || "emergent_salvage".equals(operativeStatus)) {
        emergency = true;
      }

      stopTime = time + getCabgDuration(person, surgeon, operativeStatus, time);
      person.attributes.put("cabg_stop_time", stopTime);

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
    // if we return false, the submodule did not complete (like with a Delay)
    // and will re-process the next timestep.
    if (time >= stopTime) {
      // remove the stop time so that a second processing can go through correctly
      person.attributes.remove("cabg_stop_time");
      
      // HACK for ensuring rewind time works. it will get overwritten later
      person.history.get(0).exited = stopTime;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Get the duration of a CABG operation.
   * @param person The person undergoing surgery.
   * @param surgeon The surgeon conducting the operation.
   * @param operativeStatus The operative status of the patient.
   * @param time The time the surgery is scheduled to occur.
   * @return The length of the surgery in milliseconds.
   */
  public static long getCabgDuration(
      Person person, Clinician surgeon, String operativeStatus, long time) {

    double duration;
    double min;
    double max;
    double mean;
    double std;

    if ("elective".equals(operativeStatus)) {
      min = 40;
      max = 439;
      mean = 112.617647;
      std = 52.817343;
    } else if ("emergent".equals(operativeStatus)) {
      min = 37;
      max = 252;
      mean = 112.38;
      std = 48.0743;
    } else if ("emergent_salvage".equals(operativeStatus)) {
      min = 78;
      max = 266;
      mean = 140.285714;
      std = 62.246056;
    } else if ("urgent".equals(operativeStatus)) {
      min = 43;
      max = 354;
      mean = 110.297872;
      std = 45.136946;
    } else {
      min = 37;
      max = 439;
      mean = 111.88;
      std = 48.819034;
    }

    Distribution distribution = buildDistribution(mean, std);
    duration = distribution.generate(person);
    
    long durationInMs = Utilities.convertTime("minutes", duration);
    long minInMs = Utilities.convertTime("minutes", min);
    long maxInMs = Utilities.convertTime("minutes", max);

    return bound(durationInMs, minInMs, maxInMs);
  }
  
  private static long bound(long value, long min, long max) {
    return Math.min(Math.max(value, min), max);
  }
}
