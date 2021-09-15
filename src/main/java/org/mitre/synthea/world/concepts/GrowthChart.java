package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Erf;
import org.mitre.synthea.helpers.Utilities;

/**
 * Represents a growth chart for a particular measure.
 */
public class GrowthChart implements Serializable {
  public enum ChartType {
    HEIGHT, WEIGHT, BMI, HEAD
  }

  private ChartType chartType;
  private HashMap<Integer, GrowthChartEntry> maleEntries;
  private HashMap<Integer, GrowthChartEntry> femaleEntries;

  /**
   * Construct a new GrowthChart.
   * @param chartType What kind of chart it is.
   * @param rawChart One of the raw HashMaps pulled from the JSON file.
   */
  public GrowthChart(ChartType chartType, Map<String, Map<String, Map<String, String>>> rawChart) {
    this.chartType = chartType;
    this.maleEntries = new HashMap<Integer, GrowthChartEntry>();
    this.femaleEntries = new HashMap<Integer, GrowthChartEntry>();
    Map<String, Map<String, String>> maleValues = rawChart.get("M");
    maleValues.keySet().forEach(ageMonth -> {
      Map<String, String> percentileInfo = maleValues.get(ageMonth);
      GrowthChartEntry entry = new GrowthChartEntry(Double.parseDouble(percentileInfo.get("l")),
          Double.parseDouble(percentileInfo.get("m")),
          Double.parseDouble(percentileInfo.get("s")));
      maleEntries.put(Integer.parseInt(ageMonth), entry);
    });
    this.chartType = chartType;
    Map<String, Map<String, String>> femaleValues = rawChart.get("F");
    femaleValues.keySet().forEach(ageMonth -> {
      Map<String, String> percentileInfo = femaleValues.get(ageMonth);
      GrowthChartEntry entry = new GrowthChartEntry(Double.parseDouble(percentileInfo.get("l")),
          Double.parseDouble(percentileInfo.get("m")),
          Double.parseDouble(percentileInfo.get("s")));
      femaleEntries.put(Integer.parseInt(ageMonth), entry);
    });
  }

  /**
   * Lookup and calculate values from the CDC growth charts, using the LMS
   * values to calculate the intermediate values.
   * Reference : https://www.cdc.gov/growthcharts/percentile_data_files.htm
   *
   * @param gender "M" | "F"
   * @param ageInMonths 0 - 240
   * @param percentile 0.0 - 1.0
   * @return The height (cm) or weight (kg) or BMI
   */
  public double lookUp(int ageInMonths, String gender, double percentile) {
    GrowthChartEntry entry;
    if (gender.equals("M")) {
      entry = maleEntries.get(ageInMonths);
    } else {
      entry = femaleEntries.get(ageInMonths);
    }
    if (entry == null) {
      throw new RuntimeException(
          "GrowthChart \"" + chartType + "\" does not have data for ageInMonths=" + ageInMonths
          + ", gender=" + gender + ", percentile=" + percentile);
    }
    return entry.lookUp(percentile);
  }

  /**
   * Given a value, find the percentile of the individual based on sex and age in months.
   *
   * @param gender "M" | "F"
   * @param ageInMonths 0 - 240
   * @param value the weight, height or BMI
   * @return 0 - 1.0
   */
  public double percentileFor(int ageInMonths, String gender, double value) {
    GrowthChartEntry entry;
    if (gender.equals("M")) {
      entry = maleEntries.get(ageInMonths);
    } else {
      entry = femaleEntries.get(ageInMonths);
    }
    return entry.percentileForValue(value);
  }

  /**
   * Z is the z-score that corresponds to the percentile.
   * z-scores correspond exactly to percentiles, e.g.,
   * z-scores of:
   * -1.881, // 3rd
   * -1.645, // 5th
   * -1.282, // 10th
   * -0.674, // 25th
   *  0,     // 50th
   *  0.674, // 75th
   *  1.036, // 85th
   *  1.282, // 90th
   *  1.645, // 95th
   *  1.881  // 97th
   * @param percentile 0.0 - 1.0
   * @return z-score that corresponds to the percentile.
   */
  public static double calculateZScore(double percentile) {
    // Set percentile gt0 and lt1, otherwise the error
    // function will return Infinity.
    if (percentile >= 1.0) {
      percentile = 0.999;
    } else if (percentile <= 0.0) {
      percentile = 0.001;
    }
    return -1 * Math.sqrt(2) * Erf.erfcInv(2 * percentile);
  }

  private static final NormalDistribution NORMAL_DISTRIBUTION = new NormalDistribution();

  /**
   * Convert a z-score into a percentile.
   * @param zscore The ZScore to find the percentile for
   * @return percentile - 0.0 - 1.0
   */
  public static double zscoreToPercentile(double zscore) {
    return NORMAL_DISTRIBUTION.cumulativeProbability(zscore);
  }

  /**
   * Load all of the charts in the cdc_growth_charts.json file
   * @return a map of chart type to growth chart
   */
  public static Map<ChartType, GrowthChart> loadCharts() {
    String filename = "cdc_growth_charts.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      HashMap allCharts = g.fromJson(json, HashMap.class);
      HashMap<ChartType, GrowthChart> returnMap = new HashMap<ChartType, GrowthChart>();
      returnMap.put(ChartType.HEIGHT,
          new GrowthChart(ChartType.HEIGHT, (Map) allCharts.get("height")));
      returnMap.put(ChartType.WEIGHT,
          new GrowthChart(ChartType.WEIGHT, (Map) allCharts.get("weight")));
      returnMap.put(ChartType.BMI,
          new GrowthChart(ChartType.BMI, (Map) allCharts.get("bmi")));
      returnMap.put(ChartType.HEAD,
          new GrowthChart(ChartType.HEAD, (Map) allCharts.get("head")));
      return returnMap;
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }
}
