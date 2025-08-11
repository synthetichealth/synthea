package org.mitre.synthea.editors;

import com.google.gson.Gson;

import java.util.List;
import java.util.stream.Collectors;

import org.mitre.synthea.engine.HealthRecordEditor;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * This editor simulates errors that occur in growth data as it would be collected in a clinical
 * setting. This editor is an implementation of the protocol for simulating growth data errors as
 * specified in Supplemental File 5 in the paper "Automated identification of implausible values
 * in growth data from pediatric electronic health records":
 * https://academic.oup.com/jamia/article/24/6/1080/3767271
 * <p>
 * If a height or weight is changed, if the Encounter has an associated BMI, it will be recomputed
 * based on the new, error values.
 * </p>
 * <p>
 * This module will only operate on Observations that take place while the patient is less than
 * MAX_AGE.
 * </p>
 */
public class GrowthDataErrorsEditor implements HealthRecordEditor {

  /** LOINC code for height observations. */
  public static final String HEIGHT_LOINC_CODE = "8302-2";

  /** LOINC code for weight observations. */
  public static final String WEIGHT_LOINC_CODE = "29463-7";

  /** LOINC code for BMI observations. */
  public static final String BMI_LOINC_CODE = "39156-5";

  /** Empty constructor */
  public GrowthDataErrorsEditor() { }

  /** The maximum age (in years) for which this editor will operate. */
  public static int MAX_AGE = 65;

  /** Conversion factor from kilograms to pounds. */
  public static double POUNDS_PER_KG = 2.205;

  /** Conversion factor from centimeters to inches. */
  public static double INCHES_PER_CM = 0.394;

  /** Configuration for error rates in growth data. */
  private static final Config config = loadConfig();

  /** Configuration class for growth data error rates. */

  /** Probability rate of introducing unit errors in weight measurements. */
  public double weightUnitErrorRate;

  /** Probability rate of introducing digit transposition errors in weight measurements. */
  public double weightTransposeErrorRate;

  /** Probability rate of switching weight values between different records. */
  public double weightSwitchErrorRate;

  /** Probability rate of introducing extreme (outlier) errors in weight measurements. */
  public double weightExtremeErrorRate;

  /** Probability rate of duplicating weight measurements. */
  public double weightDuplicateErrorRate;

  /** Probability rate of carrying forward previous weight measurements erroneously. */
  public double weightCarriedForwardErrorRate;

  /** Probability rate of introducing unit errors in height measurements. */
  public double heightUnitErrorRate;

  /** Probability rate of introducing digit transposition errors in height measurements. */
  public double heightTransposeErrorRate;

  /** Probability rate of switching height values between different records. */
  public double heightSwitchErrorRate;

  /** Probability rate of introducing extreme (outlier) errors in height measurements. */
  public double heightExtremeErrorRate;

  /** Probability rate of introducing absolute errors in height measurements. */
  public double heightAbsoluteErrorRate;

  /** Probability rate of duplicating height measurements. */
  public double heightDuplicateErrorRate;

  /** Probability rate of carrying forward previous height measurements erroneously. */
  public double heightCarriedForwardErrorRate;
  /** Configuration class for specifying error rates in growth data editing. */

  public class Config {
    /** Probability of introducing a unit error in weight measurements. */
    public double weightUnitErrorRate;
    /** Probability of transposing digits in weight measurements. */
    public double weightTransposeErrorRate;
    /** Probability of switching weight values between records. */
    public double weightSwitchErrorRate;
    /** Probability of introducing an extreme error in weight measurements. */
    public double weightExtremeErrorRate;
    /** Probability of duplicating weight measurements. */
    public double weightDuplicateErrorRate;
    /** Probability of carrying forward previous weight measurements. */
    public double weightCarriedForwardErrorRate;
    /** Probability of introducing a unit error in height measurements. */
    public double heightUnitErrorRate;
    /** Probability of transposing digits in height measurements. */
    public double heightTransposeErrorRate;
    /** Probability of switching height values between records. */
    public double heightSwitchErrorRate;
    /** Probability of introducing an extreme error in height measurements. */
    public double heightExtremeErrorRate;
    /** Probability of introducing an absolute error in height measurements. */
    public double heightAbsoluteErrorRate;
    /** Probability of duplicating height measurements. */
    public double heightDuplicateErrorRate;
    /** Probability of carrying forward previous height measurements. */
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

  /**
   * Checks to see if the person is under MAX_AGE.
   *
   * @param person The Synthea person to check on whether the module should be run.
   * @param record The person's HealthRecord.
   * @param time The current time in the simulation.
   * @return True if the person is under MAX_AGE.
   */
  @Override
  public boolean shouldRun(Person person, HealthRecord record, long time) {
    return person.ageInYears(time) <= MAX_AGE;
  }

  /**
   * Potentially mess up heights and weights in the encounters.
   *
   * @param person The Synthea person to check on whether the module should be run.
   * @param encounters The encounters that took place during the last time step of the simulation.
   * @param time The current time in the simulation.
   */
  public void process(Person person, List<HealthRecord.Encounter> encounters, long time) {
    List<HealthRecord.Encounter> encountersWithWeights =
        encountersWithObservationsOfCode(encounters, WEIGHT_LOINC_CODE);
    encountersWithWeights.forEach(e -> {
      if (person.rand() <= config.weightUnitErrorRate) {
        introduceWeightUnitError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightTransposeErrorRate) {
        introduceTransposeError(e, "weight");
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightSwitchErrorRate) {
        introduceWeightSwitchError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightExtremeErrorRate) {
        introduceWeightExtremeError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightDuplicateErrorRate) {
        introduceWeightDuplicateError(e, person);
        recalculateBMI(e);
      }
      if (person.rand() <= config.weightCarriedForwardErrorRate) {
        introduceWeightCarriedForwardError(e);
        recalculateBMI(e);
      }
    });

    List<HealthRecord.Encounter> encountersWithHeights =
        encountersWithObservationsOfCode(encounters, HEIGHT_LOINC_CODE);
    encountersWithHeights.forEach(e -> {
      if (person.rand() <= config.heightUnitErrorRate) {
        introduceHeightUnitError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.heightTransposeErrorRate) {
        introduceTransposeError(e, "height");
        recalculateBMI(e);
      }
      if (person.rand() <= config.heightSwitchErrorRate) {
        introduceHeightSwitchError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.heightExtremeErrorRate) {
        introduceHeightExtremeError(e);
        recalculateBMI(e);
      }
      if (person.rand() <= config.heightAbsoluteErrorRate) {
        introduceHeightAbsoluteError(e, person);
        recalculateBMI(e);
      }
      if (person.rand() <= config.heightDuplicateErrorRate) {
        introduceHeightDuplicateError(e, person);
        recalculateBMI(e);
      }
      if (person.rand() <= config.heightCarriedForwardErrorRate) {
        introduceHeightCarriedForwardError(e);
        recalculateBMI(e);
      }
    });
  }

  /**
   * Convert the weight observation value from kg to lbs. The unit is not updated because we are
   * intentionally messing things up.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceWeightUnitError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation obs = weightObservation(encounter);
    double originalWeight = (Double) obs.value;
    obs.value = originalWeight * POUNDS_PER_KG;
  }

  /**
   * Convert the height observation from cm to in. Again, we're intentionally messing things up
   * here.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceHeightUnitError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation obs = heightObservation(encounter);
    double originalHeight = (Double) obs.value;
    obs.value = originalHeight * INCHES_PER_CM;
  }

  /**
   * Flip the tens and ones place in the desired observation value.
   *
   * @param encounter The encounter that contains the observation.
   * @param obsType "height" or "weight".
   */
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
      obs.value = Double.parseDouble(new String(originalChars));
    } else {
      // for those with a single digit, just shift the ones to the tens
      double ones = Math.floor(original);
      double newTens = ones * 10;
      obs.value = original - ones + newTens;
    }
  }

  /**
   * Swap weight and height. This will work even if height is null. It will set weight to null
   * and height to the weight value.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceWeightSwitchError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    HealthRecord.Observation htObs = heightObservation(encounter);
    if (htObs == null) {
      // If there is no existing height observation, change the weight observation into a height
      // one
      wtObs.unit = "cm";
      wtObs.codes.get(0).code = HEIGHT_LOINC_CODE;
    } else {
      Object wtValue = wtObs.value;
      Object htValue = htObs.value;
      wtObs.value = htValue;
      htObs.value = wtValue;
    }
  }

  /**
   * Swap height and weight. This will work even if weight is null. It will set height to null
   * and weight to height.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceHeightSwitchError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    HealthRecord.Observation htObs = heightObservation(encounter);
    if (wtObs == null) {
      // If there is no existing weight observation, change the height observation into a weight
      // one
      htObs.unit = "kg";
      htObs.codes.get(0).code = WEIGHT_LOINC_CODE;
    } else {
      Object wtValue = wtObs.value;
      Object htValue = htObs.value;
      wtObs.value = htValue;
      htObs.value = wtValue;
    }
  }

  /**
   * Shift the decimal place for the weight observation one place to the right.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceWeightExtremeError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    double weightValue = (Double) wtObs.value;
    wtObs.value = weightValue * 10;
  }

  /**
   * Shift the decimal place for the height observation one place to the right.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceHeightExtremeError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation htObs = heightObservation(encounter);
    double heightValue = (Double) htObs.value;
    htObs.value = heightValue * 10;
  }

  /**
   * Reduce the height observation by 3 to 6 cm. It looks like someone forgot to take off their
   * shoes before getting measured.
   *
   * @param encounter The encounter that contains the observation.
   * @param random The random number generator to use.
   */
  public static void introduceHeightAbsoluteError(HealthRecord.Encounter encounter,
                                                  RandomNumberGenerator random) {
    HealthRecord.Observation htObs = heightObservation(encounter);
    double heightValue = (Double) htObs.value;
    double additionalAbsolute = random.rand() * 3;
    htObs.value = heightValue - (3 + additionalAbsolute);
  }

  /**
   * Create a duplicate weight observation in the encounter that is off slightly.
   *
   * @param encounter The encounter that contains the observation.
   * @param random The random number generator to use.
   */
  public static void introduceWeightDuplicateError(HealthRecord.Encounter encounter,
                                                   RandomNumberGenerator random) {
    HealthRecord.Observation wtObs = weightObservation(encounter);
    double weightValue = (Double) wtObs.value;
    double jitter = random.rand() - 0.5;
    HealthRecord.Observation newObs =
        encounter.addObservation(wtObs.start, wtObs.type, weightValue + jitter, "Body Weight");
    newObs.category = "vital-signs";
    newObs.unit = "kg";
  }

  /**
   * Create a duplicate height observation in the encounter that is off slightly.
   *
   * @param encounter The encounter that contains the observation.
   * @param random The random number generator to use.
   */
  public static void introduceHeightDuplicateError(HealthRecord.Encounter encounter,
                                                   RandomNumberGenerator random) {
    HealthRecord.Observation htObs = heightObservation(encounter);
    double heightValue = (Double) htObs.value;
    double jitter = random.rand() - 0.5;
    HealthRecord.Observation newObs = encounter.addObservation(htObs.start, htObs.type,
        heightValue + jitter, "Body Height");
    newObs.category = "vital-signs";
    newObs.unit = "cm";
  }

  /**
   * Replace the weight observation in this encounter with the value from the person's last
   * encounter.
   *
   * @param encounter The encounter that contains the observation.
   */
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

  /**
   * Replace the height observation in this encounter with the value from the person's last
   * encounter.
   *
   * @param encounter The encounter that contains the observation.
   */
  public static void introduceHeightCarriedForwardError(HealthRecord.Encounter encounter) {
    HealthRecord.Observation htObs = heightObservation(encounter);
    HealthRecord.Encounter previousEncounter = encounter.previousEncounter();
    if (previousEncounter != null) {
      HealthRecord.Observation previousHt = heightObservation(previousEncounter);
      if (previousHt != null) {
        htObs.value = previousHt.value;
      }
    }
  }

  private static HealthRecord.Observation weightObservation(HealthRecord.Encounter encounter) {
    return encounter.findObservation(WEIGHT_LOINC_CODE);
  }

  private static HealthRecord.Observation heightObservation(HealthRecord.Encounter encounter) {
    return encounter.findObservation(HEIGHT_LOINC_CODE);
  }

  private static HealthRecord.Observation bmiObservation(HealthRecord.Encounter encounter) {
    return encounter.findObservation(BMI_LOINC_CODE);
  }

  /**
   * Recalculate the BMI based on the existing height and weight observations in the encounter.
   * Only recreates a BMI if one already exists. Otherwise, it does nothing.
   *
   * @param encounter The encounter to recalculate BMI on.
   */
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

  /**
   * Filter a list of encounters to find all that have an observation with a particular code.
   *
   * @param encounters The list to filter.
   * @param code The code to look for.
   * @return The filtered list. If there are no matching encounters, then an empty list.
   */
  public List<HealthRecord.Encounter> encountersWithObservationsOfCode(
      List<HealthRecord.Encounter> encounters,
      String code) {
    return encounters.stream().filter(e -> e.findObservation(code) != null)
        .collect(Collectors.toList());
  }

}
