package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * This class provides growth trajectories for individuals between 2 and 20.
 * For a person, starting at age 2, the trajectory will set an age + 1 year BMI. The BMI will be
 * selected by computing the person's current extended BMI Z score. The + 1 year extended BMI Z
 * score will be selected from a normal distribution with a mean that is based on the previous
 * score, correlation between extended BMI Z scores from year to year and difference in mean
 * extended BMI Z scores from year to year.
 * <p>
 * This method uses extended BMI Z Scores, which is a concept under development at CDC. The current
 * growth charts, were not intended to be used beyond the 97th percentile. See:
 * https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4528342/
 * </p>
 * <p>
 * Further, CDC growth chart data is from 1960-1990. CDC defines obesity in children as having a BMI
 * at or above the 95th percentile for sex and age in months:
 * https://www.cdc.gov/obesity/childhood/defining.html
 * However, recent data shows that at least 18.5% of children have obesity:
 * https://www.cdc.gov/obesity/data/childhood.html
 * This oddly means that more than 18.5% of the population have a BMI greater than the 95th
 * percentile.
 * </p>
 * <p>
 * To account for these issues, this class uses correlations and means gathered from more recent
 * NHANES data: https://wwwn.cdc.gov/nchs/nhanes/Default.aspx
 * </p>
 * <p>
 * The class also uses extended BMI Z scores. For children with a BMI value less than the 95th
 * percentile, regular Z scores are used. At or above the 95th percentile models BMI values as
 * a half normal distribution with a parameter sigma that is age based. The value for sigma is
 * calculated using a quadratic function with weights obtained by examining NHANES data
 * ADD CITATION ONCE PUBLISHED BY CDC
 * </p>
 */
public class PediatricGrowthTrajectory {
  // Sigma is approximated using a quadratic formula, with different weights based on sex
  // The following constants are for those weights assuming a quadratic formula of:
  // ax^2 + bx +c
  public static double SIGMA_MALE_A = 0.0091;
  public static double SIGMA_MALE_B = 0.5196;
  public static double SIGMA_MALE_C = 0.3728;

  public static double SIGMA_FEMALE_A = 0.0011;
  public static double SIGMA_FEMALE_B = 0.3712;
  public static double SIGMA_FEMALE_C = 0.8334;

  private static final Map<GrowthChart.ChartType, GrowthChart> growthChart =
      GrowthChart.loadCharts();

  private static Map<String, YearInformation> yearCorrelations = loadCorrelations();
  private static NormalDistribution normalDistribution = new NormalDistribution();

  /**
   * Container for data on changes between years for BMI information
   */
  public static class YearInformation {
    // The correlation of extended BMI Z Score between the current year and the next year.
    public double correlation;
    // The difference in mean BMI between the current year and the next year.
    public double diff;
  }

  /**
   * Generates a BMI for the person one year later. BMIs are generated based on correlations
   * measured between years of age and differences in mean BMI. This takes into special
   * consideration people at or above the 95th percentile, as the growth charts start to break down.
   * @param person to generate the new BMI for
   * @param time current time
   * @param randomGenerator Apache Commons Math random thingy needed to sample a value
   * @return what the person's BMI should be next year
   */
  public static double generateNextYearBMI(Person person, long time,
                                          JDKRandomGenerator randomGenerator) {
    double age = person.ageInDecimalYears(time);
    double nextAgeYear = age + 1;
    String sex = (String) person.attributes.get(Person.GENDER);
    int nextRoundedYear = (int) Math.floor(nextAgeYear);
    YearInformation yi = yearCorrelations.get(Integer.toString(nextRoundedYear));
    double sigma = sigma(sex, age);
    double currentBMI = person.getVitalSign(VitalSign.BMI, time);
    double ezscore = extendedZScore(currentBMI, person.ageInMonths(time), sex, sigma);
    double mean = yi.correlation * ezscore + yi.diff;
    double sd = Math.sqrt(1 - Math.pow(yi.correlation, 2));
    NormalDistribution nextZDistro = new NormalDistribution(randomGenerator, mean, sd);
    double nextYearZscore = nextZDistro.sample();
    double nextYearPercentile = GrowthChart.zscoreToPercentile(nextYearZscore);
    return percentileToBMI(nextYearPercentile, person.ageInMonths(time) + 12,
        sex, sigma(sex, nextAgeYear));
  }

  /**
   * Uses extended percentiles when calculating a BMI greater than or equal to the 95th
   * percentile.
   * @param percentile BMI percentile {0 - 1}
   * @param ageInMonths of the person
   * @param sex of the person
   * @param sigma age based value to model the extended percentiles
   * @return The BMI, offering a different value than the growth charts when above the 95th
   */
  public static double percentileToBMI(double percentile, int ageInMonths, String sex,
                                       double sigma) {
    if (percentile < 0.95) {
     return growthChart.get(GrowthChart.ChartType.BMI)
         .lookUp(ageInMonths, sex, percentile);
    } else {
      double ninetyFifth = growthChart.get(GrowthChart.ChartType.BMI)
          .lookUp(ageInMonths, sex, 0.95);
      return ninetyFifth +
          normalDistribution.inverseCumulativeProbability((percentile - 0.9) * 10) * sigma;
    }
  }

  /**
   * Calculates the extended BMI Z Score. This is the regular Z Score when below the 95th
   * percentile. Above that, a different, half normal distribution is used to model the values.
   * @param bmi you want the Z Score for
   * @param ageInMonths of the person
   * @param sex of the person
   * @param sigma age based value to model the extended percentiles
   * @return the extended Z Score
   */
  public static double extendedZScore(double bmi, int ageInMonths, String sex, double sigma) {
    double currentPercentile = growthChart.get(GrowthChart.ChartType.BMI)
        .percentileFor(ageInMonths, sex, bmi);
    if (currentPercentile < 0.95) {
      return GrowthChart.calculateZScore(currentPercentile);
    } else {
      double ninetyFifth = growthChart.get(GrowthChart.ChartType.BMI)
          .lookUp(ageInMonths, sex, 0.95);
      double ebmiPercentile = 90 + 10 *
          normalDistribution.cumulativeProbability((bmi - ninetyFifth) / sigma);
      return normalDistribution.inverseCumulativeProbability(ebmiPercentile / 100);
    }
  }

  /**
   * Calculate the paramater needed to model the half normal distribution for BMI values at or above
   * the 95th percentile
   *
   * @param sex of the person
   * @param age of the person, precision matters! You probably don't want to pass an integer like
   *            value
   * @return sigma to use in other calculations
   */
  public static double sigma(String sex, double age) {
    if (sex.equals("M")) {
      return SIGMA_MALE_C + SIGMA_MALE_B * age - SIGMA_MALE_A * Math.pow(age, 2);
    } else {
      return SIGMA_FEMALE_C + SIGMA_FEMALE_B * age - SIGMA_FEMALE_A * Math.pow(age, 2);
    }
  }

  public static Map<String, YearInformation> loadCorrelations() {
    String filename = "bmi_correlations.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      Type hashType = new TypeToken<Map<String, YearInformation>>() {}.getType();
      return g.fromJson(json, hashType);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }
}
