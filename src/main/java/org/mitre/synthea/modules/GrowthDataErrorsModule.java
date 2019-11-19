package org.mitre.synthea.modules;

import com.google.gson.Gson;
import org.mitre.synthea.engine.HealthRecordModule;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * This module simulates errors that occur in growth data as it would be collected in a clinical
 * setting. This module is an implementation of the protocol for simulating growth data errors as
 * specified in Supplemental File 5 in the paper "Automated identification of implausible values
 * in growth data from pediatric electronic health records"
 *
 * https://academic.oup.com/jamia/article/24/6/1080/3767271
 *
 * If a height or weight is changed, if the Encounter has an associated BMI, it will be recomputed
 * based on the new, error values.
 *
 * This module will only operate on Observations that take place while the patient is less than
 * MAX_AGE.
 */
public class GrowthDataErrorsModule implements HealthRecordModule {
  public GrowthDataErrorsModule() { }

  public static int MAX_AGE = 20;
  public static double POUNDS_PER_KG = 2.205;
  public static double INCHES_PER_CM = 0.394;

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
  public boolean shouldRun(Person person, HealthRecord record, long time) {
    return person.ageInYears(time) <= MAX_AGE;
  }

  public void process(Person person, List<HealthRecord.Encounter> encounters, long time, Random random) {
    List<HealthRecord.Encounter> encountersWithWeights = encountersWithObservationsOfCode(encounters, "29463-7",
        "http://loinc.org");
    encountersWithWeights.forEach(e -> {
      if (random.nextDouble() <= config.weightUnitErrorRate) {
        introduceWeightUnitError(e);
        recalculateBMI(e);
      }
      if (random.nextDouble() <= config.weightTransposeErrorRate) {
        introduceTransposeError(e, "weight");
        recalculateBMI(e);
      }
      if (random.nextDouble() <= config.weightSwitchErrorRate) {
        introduceWeightSwitchError(e);
        recalculateBMI(e);
      }
      if (random.nextDouble() <= config.weightExtremeErrorRate) {
        introduceWeightExtremeError(e);
        recalculateBMI(e);
      }
      if (random.nextDouble() <= config.weightDuplicateErrorRate) {
        introduceWeightDuplicateError(e, random);
        recalculateBMI(e);
      }
      if (random.nextDouble() <= config.weightCarriedForwardErrorRate) {
        introduceWeightCarriedForwardError(e);
        recalculateBMI(e);
      }
    });

    List<HealthRecord.Encounter> encountersWithHeights = encountersWithObservationsOfCode(encounters, "8302-2",
        "http://loinc.org");
    encountersWithHeights.forEach(e -> {
      if (random.nextDouble() <= config.heightUnitErrorRate) {
        introduceHeightUnitError(e);
        recalculateBMI(e);
      }
      if (random.nextDouble() <= config.heightTransposeErrorRate) {
        introduceTransposeError(e, "height");
        recalculateBMI(e);
      }
    });
  }

  public static void introduceWeightUnitError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation obs = weightObservation(encounter);
    double originalWeight = (Double) obs.value;
    obs.value = originalWeight * POUNDS_PER_KG;
  }

  public static void introduceHeightUnitError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation obs = heightObservation(encounter);
    double originalHeight = (Double) obs.value;
    obs.value = originalHeight * INCHES_PER_CM;
  }

  public static void introduceTransposeError(HealthRecord.Encounter encounter, String obsType) {
    HealthRecord.Observation obs;
    if (obsType.equals("weight")) {
      obs = weightObservation(encounter);
    } else {
      obs = heightObservation(encounter);
    }

    Double original = (Double) obs.value;
    if (original >= 10) {
      String originalString = original.toString();
      int decimalPointPosition = originalString.indexOf('.');
      char[] originalChars = originalString.toCharArray();
      char tens = originalChars[decimalPointPosition - 2];
      char ones = originalChars[decimalPointPosition - 1];
      originalChars[decimalPointPosition - 2] = ones;
      originalChars[decimalPointPosition - 1] = tens;
      obs.value = Double.parseDouble(originalChars.toString());
    } else {
      // for those with a single digit, just shift the ones to the tens
      double ones = Math.floor(original);
      double newTens = ones * 10;
      obs.value = original - ones + newTens;
    }
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

  public static void introduceWeightExtremeError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    double weightValue = (Double) wtObs.value;
    wtObs.value = weightValue * 10;
  }

  public static void introduceWeightDuplicateError(HealthRecord.Encounter encounter, Random random) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    double weightValue = (Double) wtObs.value;
    double jitter = random.nextDouble() - 0.5;
    encounter.addObservation(wtObs.start, wtObs.type, weightValue + jitter);
  }

  public static void introduceWeightCarriedForwardError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    HealthRecord.Encounter previousEncounter = encounter.previousEncounter();
    if (previousEncounter != null) {
      HealthRecord.Observation previousWt = weightObservation(previousEncounter);
      if (previousWt != null) {
        wtObs.value = previousWt.value;
      }
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

  public List<HealthRecord.Encounter> encountersWithObservationsOfCode(List<HealthRecord.Encounter> encounters,
                                                                       String code, String system) {
    return encounters.stream().filter(e ->
        e.observations.stream().anyMatch(o ->
            o.codes.stream().anyMatch(c ->
                c.code.equals(code) && c.system.equals(system))))
        .collect(Collectors.toList());
  }

}
