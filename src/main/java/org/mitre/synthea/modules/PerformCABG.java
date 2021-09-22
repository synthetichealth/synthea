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
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
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
  
  private static final Code ON_PUMP_CABG =
      new Code("SNOMED-CT", "232717009", "Coronary artery bypass grafting (procedure)");
  private static final Code OFF_PUMP_CABG =
      new Code("SNOMED-CT", "418824004", "Off-pump coronary artery bypass (procedure)");
  private static final Code EMERGENCY_CABG =
      new Code("SNOMED-CT", "414088005", "Emergency coronary artery bypass graft (procedure)");
  
  private static Map<String,Clinician> surgeons = new HashMap<String,Clinician>();
  private static RandomCollection<Clinician> onPumpCabgSurgeons =
      new RandomCollection<Clinician>();
  private static RandomCollection<Clinician> offPumpCabgSurgeons =
      new RandomCollection<Clinician>();
  
  static {
    try {
      // Load the Surgeon File
      String cabgSurgeonsCsv = Utilities.readResource("surgeon_stats.csv");
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

        surgeons.put(surgeonId, clin);
      }

      provider.clinicianMap.put(ClinicianSpecialty.CARDIAC_SURGERY, new ArrayList<Clinician>(surgeons.values()));
      
      // Finally, go back through the surgeon file data and create distributions
      // for each surgery...
      for (LinkedHashMap<String,String> row : surgeonsFile) {
        String identifier = row.get("RandomID");
        Clinician clin = surgeons.get(identifier);

        String operation = row.get("Surgery_cat_2");
        Double weight = Double.parseDouble(row.get("n_surgeries"));
        Double mean = Double.parseDouble(row.get("mean"));
        Double std = Double.parseDouble(row.get("std"));
        Double min = Double.parseDouble(row.get("min"));
        Double max = Double.parseDouble(row.get("max"));

        Distribution distribution = buildDistribution(mean, std, min, max);
        clin.attributes.put(operation, distribution);
        clin.attributes.put("mean_surgeon_time", mean);
        if (operation.equals("OnPumpCABG")) {
          onPumpCabgSurgeons.add(weight, clin);
        } else if (operation.equals("OffPumpCABG")) {
          offPumpCabgSurgeons.add(weight, clin);
        }
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
    
    if (person.attributes.containsKey("cabg_stop_time")) {
      stopTime = (long) person.attributes.get("cabg_stop_time");
    } else {
      Clinician surgeon = null;

      boolean onPump = (Boolean) person.attributes.getOrDefault("cabg_pump", true);
      if (onPump) {
        surgeon = onPumpCabgSurgeons.next(person);
      } else {
        surgeon = offPumpCabgSurgeons.next(person);
      }

      double durationInMinutes = getProcedureDuration(person, surgeon, time);
      long durationInMs = Utilities.convertTime("minutes", durationInMinutes);
      stopTime = time + durationInMs;
      person.attributes.put("cabg_stop_time", stopTime);

      boolean emergency = false;
      String operativeStatus = (String) person.attributes.get("operative_status");
      if ("emergent".equals(operativeStatus) || "emergent_salvage".equals(operativeStatus)) {
        emergency = true;
      }
      Code code = onPump ? ON_PUMP_CABG : OFF_PUMP_CABG;
      String primaryCode = code.code;
      Procedure cabg = person.record.procedure(time, primaryCode);
      cabg.name = this.name;
      cabg.codes.add(code);
      if (emergency) {
        cabg.codes.add(EMERGENCY_CABG);
      }
      cabg.stop = stopTime;
      cabg.clinician = surgeon;

      surgeon.incrementEncounters();
      surgeon.getOrganization().incrementEncounters(EncounterType.INPATIENT, Utilities.getYear(time));

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
  
  public static final double getProcedureDuration(Person person, Clinician surgeon, long time) {
    boolean onPump = (Boolean) person.attributes.getOrDefault("cabg_pump", true);

    int numberGrafts = (int) person.attributes.get("cabg_number_of_grafts");

    int ckdStage = (Integer) person.attributes.getOrDefault("ckd", 0);
    boolean hasDialysis = ckdStage >= 4; // dialysis module kicks off at ckd stage >= 4

    double totalNoDistAnastArtCond = (int) person.attributes.get("cabg_arterial_conduits");

    HealthRecord.Procedure operativeApproach = (HealthRecord.Procedure) person.attributes.get("cabg_operative_approach");
    boolean sternotomy = operativeApproach.containsCode("359672006", "SNOMED-CT");

    boolean redo = person.record.conditionActive("399261000"); // history of CABG code, see heart/cabg/operation.json

    boolean unstableAngina = person.record.conditionActive("4557003"); // "preinfarction syndrome", see heart/nsteacs_pathway.json

    double calculatedBMI = person.getVitalSign(VitalSign.BMI, time);

    double meanSurgeonTime = (double) surgeon.attributes.get("mean_surgeon_time");

    double gaussianNoise = person.randGaussian();
    
    return getProcedureDuration(onPump, numberGrafts, hasDialysis, totalNoDistAnastArtCond, sternotomy, redo,
        unstableAngina, calculatedBMI, meanSurgeonTime, gaussianNoise);
  }

  // extracted to a constant so we can disable it in the unit test
  public static int GAUSSIAN_NOISE_FACTOR = 28;
  
  public static final double getProcedureDuration(boolean onPump, int numberGrafts, boolean hasDialysis,
      double totalNoDistAnastArtCond, boolean sternotomy, boolean redo, boolean unstableAngina, double calculatedBMI,
      double meanSurgeonTime, double gaussianNoise) {

    double duration = 19.7; // constant value

    if (onPump) duration += 5.92;

    if (numberGrafts >= 5) duration += 52.22;
    else if (numberGrafts == 4) duration += 21.09;
    else if (numberGrafts == 3) duration += 4.76;
    else if (numberGrafts == 2) duration += -14.34;
    else if (numberGrafts == 1) duration += -64.03;

    if (hasDialysis) duration += 30.22;

    duration += totalNoDistAnastArtCond * 9.27;

    if (sternotomy) duration += -34.37;

    if (redo) duration += 45.7;

    if (unstableAngina) duration += 8.22;

    duration += calculatedBMI * 0.7471;

    duration += meanSurgeonTime * 0.843;
    
    duration += gaussianNoise * GAUSSIAN_NOISE_FACTOR; // mean of zero and std of 28

    // bound to the 0.5% and 99.5% quantile of surgery times
    duration = bound(duration, 90, 382);

    return duration;
  }

  private static double bound(double value, double min, double max) {
    return Math.min(Math.max(value, min), max);
  }

}
