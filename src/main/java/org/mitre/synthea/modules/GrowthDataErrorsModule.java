package org.mitre.synthea.modules;

import com.google.gson.Gson;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This module simulates errors that occur in growth data as it would be collected in a clinical
 * setting. This module is an implementation of the protocol for simulating growth data errors as
 * specified in Supplemental File 5 in the paper "Automated identification of implausible values
 * in growth data from pediatric electronic health records"
 *
 * https://academic.oup.com/jamia/article/24/6/1080/3767271
 *
 * The following module is intended to run after the simulation for the person is complete. At that
 * point the module will potentially introduce errors into the height and weight Observations for
 * the person.
 *
 * If a height or weight is changed, if the Encounter has an associated BMI, it will be recomputed
 * based on the new, error values.
 *
 * This module will only operate on Observations that take place while the patient is less than
 * MAX_AGE.
 */
public class GrowthDataErrorsModule extends Module {
  public GrowthDataErrorsModule() { this.name = "Growth Data Errors";}

  public static int MAX_AGE = 20;
  public static double POUNDS_PER_KG = 2.205;

  private static final Config config = loadConfig();

  public class Config {
    public double weightUnitErrorRate;
    public double weightTransposeErrorRate;
    public double weightSwitchErrorRate;
    public double weightExtremeErrorRate;
    public double weightDuplicateErrorRate;
    public double weightCarriedForwardErrorRate;
    public double heightUnitErrorRate;
    public double heightTransposeErrorRate;
    public double heightSwitchErrorRate;
    public double heightExtremeErrorRate;
    public double heightAbsoluteErrorRate;
    public double heightDuplicateErrorRate;
    public double heightCarriedForwardErrorRate;
  }

  private static Config loadConfig() {
    String filename = "growth_data_error_rates.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      return g.fromJson(json, Config.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public boolean process(Person person, long time) {
    List<HealthRecord.Encounter> encountersWithWeights = person.record.encountersWithObservationsOfCode("29463-7",
        "http://loinc.org");
    List<HealthRecord.Encounter> weightEncountersInRange = encountersInAgeRange(person, encountersWithWeights);
    weightEncountersInRange.forEach(e -> {
      if (person.rand() <= config.weightUnitErrorRate) {
        introduceWeightUnitError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightTransposeErrorRate) {
        introduceWeightTransposeError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightSwitchErrorRate) {
        introduceWeightSwitchError(e);
        recalculateBMI(e);
      }

    });

    return config.heightAbsoluteErrorRate < 1;
  }

  public static void introduceWeightUnitError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation obs = weightObservation(encounter);
    double originalWeight = (Double) obs.value;
    obs.value = originalWeight * POUNDS_PER_KG;
  }

  public static void introduceWeightTransposeError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation obs = weightObservation(encounter);
    Double originalWeight = (Double) obs.value;
    if (originalWeight >= 10) {
      String weightString = originalWeight.toString();
      int decimalPointPosition = weightString.indexOf('.');
      char[] weightChars = weightString.toCharArray();
      char tens = weightChars[decimalPointPosition - 2];
      char ones = weightChars[decimalPointPosition - 1];
      weightChars[decimalPointPosition - 2] = ones;
      weightChars[decimalPointPosition - 1] = tens;
      obs.value = Double.parseDouble(weightChars.toString());
    } else {
      // for those with a single digit weight, just shift the ones to the tens
      double ones = Math.floor(originalWeight);
      double newTens = ones * 10;
      obs.value = originalWeight - ones + newTens;
    }

    obs.value = originalWeight * POUNDS_PER_KG;
  }

  public static void introduceWeightSwitchError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    HealthRecord.Observation htObs = heightObservation(encounter);
    if (htObs == null) {
      // If there is no existing height observation, change the weight observation into a height
      // one
      wtObs.unit = "cm";
      wtObs.codes.get(0).code = "8302-2";
    } else {
      Object wtValue = wtObs.value;
      Object htValue = htObs.value;
      wtObs.value = htValue;
      htObs.value = wtValue;
    }
  }

  private static HealthRecord.Observation weightObservation(HealthRecord.Encounter encounter) {
    return findObservation(encounter, "29463-7");
  }

  private static HealthRecord.Observation heightObservation(HealthRecord.Encounter encounter) {
    return findObservation(encounter, "8302-2");
  }

  private static HealthRecord.Observation bmiObservation(HealthRecord.Encounter encounter) {
    return findObservation(encounter, "39156-5");
  }

  private static HealthRecord.Observation findObservation(HealthRecord.Encounter encounter, String code) {
    return encounter.observations
        .stream()
        .filter(o -> o.containsCode(code, "http://loinc.org"))
        .findFirst()
        .orElse(null);
  }

  public static void recalculateBMI(HealthRecord.Encounter encounter) {
    HealthRecord.Observation bmi = bmiObservation(encounter);
    if (bmi != null) {
      HealthRecord.Observation weight = weightObservation(encounter);
      HealthRecord.Observation height = heightObservation(encounter);
      if (weight != null && height != null) {
        double wt = (Double) weight.value;
        double ht = (Double) height.value;
        bmi.value = wt / ((ht / 100) * (ht / 100));
      }
    }
  }

  private static List<HealthRecord.Encounter> encountersInAgeRange(Person person, List<HealthRecord.Encounter> encounters) {
    return encounters
        .stream()
        .filter(e -> person.ageInYears(e.start) < MAX_AGE)
        .collect(Collectors.toList());
  }

}
