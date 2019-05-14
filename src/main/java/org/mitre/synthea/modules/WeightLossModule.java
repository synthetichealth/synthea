package org.mitre.synthea.modules;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

import static org.mitre.synthea.modules.LifecycleModule.bmi;
import static org.mitre.synthea.modules.LifecycleModule.lookupGrowthChart;

public final class WeightLossModule extends Module {

  public static final String ACTIVE_WEIGHT_MANAGEMENT = "active_weight_management";
  public static final String PRE_MANAGEMENT_WEIGHT = "pre_management_weight";
  public static final String WEIGHT_MANAGEMENT_START = "weight_management_start";
  public static final String WEIGHT_LOSS_PERCENTAGE = "weight_loss_percentage";
  public static final String LONG_TERM_WEIGHT_LOSS = "long_term_weight_loss";
  public static final String WEIGHT_LOSS_ADHERENCE = "weight_loss_adherence";

  public static final int managementStartAge =
      Integer.parseInt(Config.get("lifecycle.weight_loss.min_age", "5"));
  public static final double startWeightManagementProb =
      Double.parseDouble(Config.get("lifecycle.weight_loss.start_prob", "0.493"));
  public static final double adherence =
      Double.parseDouble(Config.get("lifecycle.weight_loss.adherence", "0.605"));
  public static final double startBMI =
      Double.parseDouble(Config.get("lifecycle.weight_loss.start_bmi", "30"));
  public static final double minLoss =
      Double.parseDouble(Config.get("lifecycle.weight_loss.min_management_loss", "0.07"));
  public static final double maxLoss =
      Double.parseDouble(Config.get("lifecycle.weight_loss.max_management_loss", "0.1"));
  public static final double maintenance =
      Double.parseDouble(Config.get("lifecycle.weight_loss.maintenance", "0.2"));
  public static final double minWeightPercentile =
      Double.parseDouble(Config.get("lifecycle.weight_loss.best_pediatric_percentile", "0.6"));


  @Override
  public boolean process(Person person, long time) {
    boolean activeWeightManagement = (boolean) person.attributes.get(ACTIVE_WEIGHT_MANAGEMENT);
    if (activeWeightManagement) {
      boolean followsPlan = (boolean) person.attributes.get(WEIGHT_LOSS_ADHERENCE);
      boolean longTermSuccess = (boolean) person.attributes.get(LONG_TERM_WEIGHT_LOSS);
      if (firstYearOfManagement(person, time)) {
        if (followsPlan) {
          int age = person.ageInYears(time);
          double weight;
          if (age < 20) {
            weight = pediatricWeightLoss(person, time);
          } else {
            weight = adultWeightLoss(person, time);
          }
          double height = person.getVitalSign(VitalSign.HEIGHT, time);
          person.setVitalSign(VitalSign.WEIGHT, weight);
          person.setVitalSign(VitalSign.BMI, bmi(height, weight));
        }
      } else if (firstFiveYearsOfManagement(person, time)){
        if (followsPlan) {
          if (! longTermSuccess) {
            int age = person.ageInYears(time);
            double weight;
            if (age < 20) {
              weight = pediatricRegression(person, time);
            } else {
              long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
              if (person.ageInYears(start) < 20) {
                weight = transitionRegression(person, time);
              } else {
                weight = adultRegression(person, time);
              }
            }
            double height = person.getVitalSign(VitalSign.HEIGHT, time);
            person.setVitalSign(VitalSign.WEIGHT, weight);
            person.setVitalSign(VitalSign.BMI, bmi(height, weight));
          }
        }
      } else {
        // five years after the start
        if (! longTermSuccess) {
          stopWeightManagement(person);
        }
      }
    } else {
      boolean willStart = willStartWeightManagement(person, time);
      if (willStart) {
        startWeightManagement(person, time);
      }
    }
    return false;
  }

  public void stopWeightManagement(Person person) {
    person.attributes.remove(WEIGHT_MANAGEMENT_START);
    person.attributes.remove(WEIGHT_LOSS_PERCENTAGE);
    person.attributes.remove(WEIGHT_LOSS_ADHERENCE);
    person.attributes.remove(PRE_MANAGEMENT_WEIGHT);
    person.attributes.remove(ACTIVE_WEIGHT_MANAGEMENT);
    person.attributes.remove(LONG_TERM_WEIGHT_LOSS);
  }

  public boolean firstYearOfManagement(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    return start >= time - Utilities.convertTime("years", 1);
  }

  public boolean firstFiveYearsOfManagement(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    return start <= time - Utilities.convertTime("years", 5);
  }

  /*
    Weight loss is linear from the person's start weight to their target weight (start - percentage loss) over the
    first year of active weight management. Returns the new weight for the person.
   */
  public double adultWeightLoss(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    double percentOfYearElapsed = (time - start) / Utilities.convertTime("years", 1);
    double startWeight = (double) person.attributes.get(PRE_MANAGEMENT_WEIGHT);
    double lossPercent = (double) person.attributes.get(WEIGHT_LOSS_PERCENTAGE);
    return startWeight - (startWeight * lossPercent * percentOfYearElapsed);
  }

  /*
    Weight regression is linear from a person's current weight to their original weight over the
    second through fifth year of active weight management. Returns the new weight for the person.
 */
  public double adultRegression(Person person, long time) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    double percentOfTimeElapsed = (time - start - Utilities.convertTime("years", 1)) /
        Utilities.convertTime("years", 4);
    double startWeight = (double) person.attributes.get(PRE_MANAGEMENT_WEIGHT);
    double lossPercent = (double) person.attributes.get(WEIGHT_LOSS_PERCENTAGE);
    double minWeight = startWeight - (startWeight * lossPercent);
    return startWeight - ((startWeight - minWeight) * (1 - percentOfTimeElapsed));
  }

  /*
    This will regress a pediatric patient back to their weight percentile. Weight gain will not necessarily be linear.
    It will approach the weight based on percentile at age as a function of time in the regression period.
   */
  public double pediatricRegression(Person person, long time) {
    int ageInMonths = person.ageInMonths(time);
    return percentileRegression(person, time, ageInMonths);
  }

  /*
    Revert the person to their 240 month weight percentile following the same procedure as pediatric regression
   */
  public double transitionRegression(Person person, long time) {
    int maxAgeInMonths = 240;
    return percentileRegression(person, time, maxAgeInMonths);
  }

  private double percentileRegression(Person person, long time, int ageInMonths) {
    long start = (long) person.attributes.get(WEIGHT_MANAGEMENT_START);
    String gender = (String) person.attributes.get(Person.GENDER);
    double regressionWeight = lookupGrowthChart("weight", gender, ageInMonths,
        person.getVitalSign(VitalSign.WEIGHT_PERCENTILE, time));
    double lossPercent = (double) person.attributes.get(WEIGHT_LOSS_PERCENTAGE);
    double percentOfTimeElapsed = (time - start - Utilities.convertTime("years", 1)) /
        Utilities.convertTime("years", 4);
    return regressionWeight - (regressionWeight * lossPercent * (1 - percentOfTimeElapsed));
  }

  /*
    Uses the same method as adult weight loss, but sets a threshold for how low the weight can go. This is to
    handle the fact that children will grow, so their healthy weight will increase.
   */
  public double pediatricWeightLoss(Person person, long time) {
    double weight = adultWeightLoss(person, time);
    String gender = (String) person.attributes.get(Person.GENDER);
    int ageInMonths = person.ageInMonths(time);
    double minWeight = lookupGrowthChart("weight", gender, ageInMonths, minWeightPercentile);
    if (minWeight > weight) {
      weight = minWeight;
    }
    return weight;
  }

  public void startWeightManagement(Person person, long time) {
    double startWeight = person.getVitalSign(VitalSign.WEIGHT, time);
    person.attributes.put(ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(PRE_MANAGEMENT_WEIGHT, startWeight);
    person.attributes.put(WEIGHT_MANAGEMENT_START, time);
    boolean stickToPlan = person.rand() <= adherence;
    person.attributes.put(WEIGHT_LOSS_ADHERENCE, stickToPlan);
    if (stickToPlan) {
      double percentWeightLoss = person.rand(minLoss, maxLoss);
      person.attributes.put(WEIGHT_LOSS_PERCENTAGE, percentWeightLoss);
      boolean longTermSuccess = person.rand() <= maintenance;
      person.attributes.put(LONG_TERM_WEIGHT_LOSS, longTermSuccess);
    }
  }

  public boolean willStartWeightManagement(Person person, long time) {
    int age = person.ageInYears(time);
    double bmi = person.getVitalSign(VitalSign.BMI, time);
    if (age >= managementStartAge && bmi >= startBMI) {
      return person.rand() <= startWeightManagementProb;
    }
    return false;
  }
}
