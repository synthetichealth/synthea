package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

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
  public static double SIGMA_FEMALE_A = 0.0011;
  public static double SIGMA_FEMALE_B = 0.3712;
  public static double SIGMA_FEMALE_C = 0.8334;

  public static double SIGMA_MALE_A = 0.0091;
  public static double SIGMA_MALE_B = 0.5196;
  public static double SIGMA_MALE_C = 0.3728;

  public static long ONE_YEAR = Utilities.convertTime("years", 1);
  public static int NINETEEN_YEARS_IN_MONTHS = 228;

  private static final Map<GrowthChart.ChartType, GrowthChart> growthChart =
      GrowthChart.loadCharts();

  private static Map<String, YearInformation> yearCorrelations = loadCorrelations();
  private static NormalDistribution normalDistribution = new NormalDistribution();
  private static EnumeratedDistribution<NHANESSample> nhanesSamples =
      NHANESSample.loadDistribution();

  /**
   * Container for data on changes between years for BMI information.
   */
  public static class YearInformation {
    // The correlation of extended BMI Z Score between the current year and the next year.
    public double correlation;
    // The difference in mean BMI between the current year and the next year.
    public double diff;
  }

  /**
   * A representation of a point in the growth trajectory.
   */
  public class Point {
    public int ageInMonths;
    public long timeInSimulation;
    public double bmi;
  }

  private NHANESSample initialSample;
  private List<Point> trajectory;


  /**
   * Starts a new pediatric growth trajectory. Selects a start BMI between 2 and 3 years old by
   * pulling a weighted sample from NHANES.
   *
   * @param personSeed The person's seed to allow for repeatable randomization
   * @param birthTime The time the individual was born in the simulation
   */
  public PediatricGrowthTrajectory(long personSeed, long birthTime) {
    // TODO: Make the selection sex specific
    nhanesSamples.reseedRandomGenerator(personSeed);
    this.initialSample = nhanesSamples.sample();
    this.trajectory = new LinkedList();
    Point p = new Point();
    p.ageInMonths = this.initialSample.agem;
    p.bmi = this.initialSample.bmi;
    p.timeInSimulation = birthTime + Utilities.convertTime("months", this.initialSample.agem);
    this.trajectory.add(p);
  }

  /**
   * Given the sex, BMI and height percentile at age 2, calculate the correct weight percentile.
   * @param sex of the person to get the weight percentile for
   * @param heightPercentile or the person
   * @return the weight percentile the person would have to be in, given their height percentile
   *     and BMI
   */
  public double reverseWeightPercentile(String sex, double heightPercentile) {
    double height = growthChart.get(GrowthChart.ChartType.HEIGHT).lookUp(this.initialSample.agem,
        sex, heightPercentile);
    double weight = BMI.weightForHeightAndBMI(height, this.initialSample.bmi);
    return growthChart.get(GrowthChart.ChartType.WEIGHT).percentileFor(this.initialSample.agem, sex,
        weight);
  }

  /**
   * Generates a BMI for the person one year later. BMIs are generated based on correlations
   * measured between years of age and differences in mean BMI. This takes into special
   * consideration people at or above the 95th percentile, as the growth charts start to break down.
   * @param person to generate the new BMI for
   * @param time current time
   * @param randomGenerator Apache Commons Math random thingy needed to sample a value
   */
  public void generateNextYearBMI(Person person, long time,
                                    JDKRandomGenerator randomGenerator) {
    double age = person.ageInDecimalYears(time);
    double nextAgeYear = age + 1;
    String sex = (String) person.attributes.get(Person.GENDER);
    int nextRoundedYear = (int) Math.floor(nextAgeYear);
    YearInformation yi = yearCorrelations.get(Integer.toString(nextRoundedYear));
    double sigma = sigma(sex, age);
    Point lastPoint = this.tail();
    double currentBMI = lastPoint.bmi;
    double ezscore = extendedZScore(currentBMI, lastPoint.ageInMonths, sex, sigma);
    double mean = yi.correlation * ezscore + yi.diff;
    double sd = Math.sqrt(1 - Math.pow(yi.correlation, 2));
    NormalDistribution nextZDistro = new NormalDistribution(randomGenerator, mean, sd);
    double nextYearZscore = nextZDistro.sample();
    double nextYearPercentile = GrowthChart.zscoreToPercentile(nextYearZscore);
    double nextPointBMI = percentileToBMI(nextYearPercentile, lastPoint.ageInMonths + 12,
        sex, sigma(sex, nextAgeYear));
    Point nextPoint = new Point();
    nextPoint.timeInSimulation = lastPoint.timeInSimulation + ONE_YEAR;
    nextPoint.ageInMonths = lastPoint.ageInMonths + 12;
    nextPoint.bmi = nextPointBMI;
    this.trajectory.add(nextPoint);
  }

  /**
   * Finds the last point of the growth trajectory.
   * @return the point
   */
  public Point tail() {
    return this.trajectory.get(this.trajectory.size() - 1);
  }

  /**
   * Finds the closest point occurring before the given time.
   * @param time time of interest
   * @return Point just before it or null if it doesn't exist
   */
  public Point justBefore(long time) {
    Point p = null;
    for (int i = 0; i < this.trajectory.size(); i++) {
      if (this.trajectory.get(i).timeInSimulation < time) {
        p = this.trajectory.get(i);
      } else {
        return p;
      }
    }
    return p;
  }

  /**
   * Finds the closest point occurring after the given time.
   * @param time time of interest
   * @return Point just after it or null if it doesn't exist
   */
  public Point justAfter(long time) {
    for (int i = 0; i < this.trajectory.size(); i++) {
      if (this.trajectory.get(i).timeInSimulation > time) {
        return this.trajectory.get(i);
      }
    }
    return null;
  }

  /**
   * Determines whether the given time is before or after the NHANES sample selected as the seed
   * for this growth trajectory.
   * @param time The time to check
   * @return true if the time is before the sample
   */
  public boolean beforeInitialSample(long time) {
    return this.trajectory.get(0).timeInSimulation > time;
  }

  /**
   * Adds a point to the end of the trajectory. It must be at the end of the trajectory, otherwise,
   * it will throw an IllegalArgumentException.
   * @param ageInMonths for the person at the point
   * @param timeInSimulation at the time of the point
   * @param bmi what the body mass index should be at that point in time
   */
  public void addPoint(int ageInMonths, long timeInSimulation, double bmi) {
    Point p = new Point();
    p.ageInMonths = ageInMonths;
    p.timeInSimulation = timeInSimulation;
    p.bmi = bmi;
    if (tail().timeInSimulation > timeInSimulation) {
      throw new IllegalArgumentException("Must extend beyond end of current trajectory");
    }
    trajectory.add(p);
  }

  /**
   * Provides the BMI for the individual at the supplied time. If the time provided is beyond the
   * current length of the trajectory, it will generate a new point in the trajectory, if that point
   * will happen before the person is 20 years old.
   * @param person to get the BMI for
   * @param time the time at which you want the BMI
   * @param randomGenerator Apache Commons Math random thingy needed to sample a value
   * @return a BMI value
   */
  public double currentBMI(Person person, long time, JDKRandomGenerator randomGenerator) {
    Point lastPoint = tail();
    if (lastPoint.timeInSimulation < time) {
      if (lastPoint.ageInMonths > NINETEEN_YEARS_IN_MONTHS) {
        return lastPoint.bmi;
      }
      generateNextYearBMI(person, time, randomGenerator);
    }
    Point previous = justBefore(time);
    Point next = justAfter(time);
    double percentOfTimeBetweenPointsElapsed = ((double) time - previous.timeInSimulation)
        / (next.timeInSimulation - previous.timeInSimulation);
    double bmiDifference = next.bmi - previous.bmi;

    return previous.bmi + (bmiDifference * percentOfTimeBetweenPointsElapsed);
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
      return ninetyFifth
          + normalDistribution.inverseCumulativeProbability((percentile - 0.9) * 10) * sigma;
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
      double ebmiPercentile = 90 + 10
          * normalDistribution.cumulativeProbability((bmi - ninetyFifth) / sigma);
      return normalDistribution.inverseCumulativeProbability(ebmiPercentile / 100);
    }
  }

  /**
   * Calculate the parameter needed to model the half normal distribution for BMI values at or above
   * the 95th percentile.
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

  /**
   * Load correlations of extended BMI Z Scores from age year to age year.
   * @return The correlations keyed by age year
   */
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
