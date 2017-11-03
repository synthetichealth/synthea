package org.mitre.synthea.world.geography;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.world.agents.Person;

/**
 * Demographics class holds the information from the towns.json and associated county config files.
 * This data is used to build up a synthetic population matching these real-world statistics. A
 * single instance of Demographics represents a single city or town. The Ages, Gender, Race, Income,
 * and Education properties are maps of frequency information. TODO: add ways to better wrap these
 * maps so they are more accessible and useful. TODO: merge this with Location somehow. they
 * probably don't need to be separate classes
 */
public class Demographics {
  public long population;
  public String state;
  public String county;
  public Map<String, Double> ages;
  private RandomCollection<String> ageDistribution;
  public Map<String, Double> gender;
  private RandomCollection<String> genderDistribution;
  public Map<String, Double> race;
  private RandomCollection<String> raceDistribution;
  public Map<String, Double> income;
  private RandomCollection<String> incomeDistribution;
  public Map<String, Double> education;
  private RandomCollection<String> educationDistribution;

  public int pickAge(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (ageDistribution == null) {
      ageDistribution = buildRandomCollectionFromMap(ages);
    }
    /*
     * Sample Age frequency: "ages": { "0..4": 0.03810425832699584, "5..9": 0.04199539968180355,
     * [truncated] "75..79": 0.04838265689371212, "80..84": 0.037026496153182195, "85..110":
     * 0.040978290790498896 }
     */

    String pickedRange = ageDistribution.next(random);

    String[] range = pickedRange.split("\\.\\.");
    // TODO this seems like it would benefit from better caching
    int low = Integer.parseInt(range[0]);
    int high = Integer.parseInt(range[1]);

    // nextInt is normally exclusive of the top value,
    // so add 1 to make it inclusive
    return random.nextInt((high - low) + 1) + low;
  }

  public String pickGender(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (genderDistribution == null) {
      genderDistribution = buildRandomCollectionFromMap(gender);
    }

    /*
     * Sample Gender frequency: "gender": { "male": 0.47638487773697935, "female":
     * 0.5236151222630206 },
     */
    return genderDistribution.next(random);
  }

  public String pickRace(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (raceDistribution == null) {
      raceDistribution = buildRandomCollectionFromMap(race);
    }

    /*
     * Sample Race frequency: "race": { "white": 0.932754172245991, "hispanic":
     * 0.028409064399789113, "black": 0.026762094497814148, "asian": 0.014094889727666761, "native":
     * 0.008015564565419232, "other": 0.001 },
     */

    return raceDistribution.next(random);
  }

  public int pickIncome(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (incomeDistribution == null) {
      Map<String, Double> tempIncome = new HashMap<>(income);
      tempIncome.remove("mean");
      tempIncome.remove("median");
      incomeDistribution = buildRandomCollectionFromMap(tempIncome);
    }

    /*
     * Sample Income frequency: "income": { "mean": 81908, "median": 58933, "00..10":
     * 0.07200000000000001, "10..15": 0.055, "15..25": 0.099, "25..35": 0.079, "35..50": 0.115,
     * "50..75": 0.205, "75..100": 0.115, "100..150": 0.155, "150..200": 0.052000000000000005,
     * "200..999": 0.054000000000000006 },
     */

    String pickedRange = incomeDistribution.next(random);

    String[] range = pickedRange.split("\\.\\.");
    // TODO this seems like it would benefit from better caching
    int low = Integer.parseInt(range[0]) * 1000;
    int high = Integer.parseInt(range[1]) * 1000;

    // nextInt is normally exclusive of the top value,
    // so add 1 to make it inclusive
    return random.nextInt((high - low) + 1) + low;
  }

  public double incomeLevel(int income) {
    // simple linear formula just maps federal poverty level to 0.0 and 75,000 to 1.0
    // 75,000 chosen based on
    // https://www.princeton.edu/~deaton/downloads/deaton_kahneman_high_income_improves_evaluation_August2010.pdf
    double poverty = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.income.poverty", "11000"));
    double high = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.income.high", "75000"));

    if (income >= high) {
      return 1.0;
    } else if (income <= poverty) {
      return 0.0;
    } else {
      return ((double) income - poverty) / (high - poverty);
    }
  }

  public String pickEducation(Random random) {
    // lazy-load in case this randomcollection isn't necessary
    if (educationDistribution == null) {
      educationDistribution = buildRandomCollectionFromMap(education);
    }

    return educationDistribution.next(random);
  }

  public double educationLevel(String level, Person person) {
    switch (level) {
      case "less_than_hs":
        return person.rand(0.0, 0.5);
      case "hs_degree":
        return person.rand(0.1, 0.75);
      case "some_college":
        return person.rand(0.3, 0.85);
      case "bs_degree":
        return person.rand(0.5, 1.0);
      default:
        return 0.0;
    }
  }

  public double socioeconomicScore(double income, double education, double occupation) {
    double incomeWeight = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.weights.income"));
    double educationWeight = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.weights.education"));
    double occupationWeight = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.weights.occupation"));

    return (income * incomeWeight) + (education * educationWeight)
        + (occupation * occupationWeight);
  }

  public String socioeconomicCategory(double score) {
    double highScore = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.score.high"));
    double middleScore = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.score.middle"));

    if (score >= highScore) {
      return "High";
    } else if (score >= middleScore) {
      return "Middle";
    } else {
      return "Low";
    }
  }

  /**
   * Load a map of Demographics from the JSON file at the given location.
   * 
   * @param filename
   *          location of a file containing demographic info.
   * @return Map of City Name -> Demographics
   * @throws IOException
   *           if the file could not be found or read
   */
  public static Map<String, Demographics> loadByName(String filename) throws IOException {
    InputStream stream = Location.class.getResourceAsStream(filename);
    // read all text into a string
    String json = new BufferedReader(new InputStreamReader(stream)).lines()
        .collect(Collectors.joining("\n"));
    return loadByContent(json);
  }

  /**
   * Load a map of Demographics from the given JSON string.
   * 
   * @param json
   *          String containing JSON content.
   * @return Map of City Name -> Demographics
   */
  public static Map<String, Demographics> loadByContent(String json) {
    // wrap the json in a "demographicsFile" property so gson can parse it
    json = "{ \"demographicsFile\" : " + json + " }";
    Gson gson = new Gson();

    DemographicsFile parsed = gson.fromJson(json, DemographicsFile.class);

    return parsed.demographicsFile;
  }

  /**
   * Helper function to convert a map of frequencies into a RandomCollection.
   */
  private static RandomCollection<String> buildRandomCollectionFromMap(Map<String, Double> map) {
    RandomCollection<String> distribution = new RandomCollection<>();
    for (Map.Entry<String, Double> e : map.entrySet()) {
      distribution.add(e.getValue(), e.getKey());
    }
    return distribution;
  }

  /**
   * Helper class only used to make it easier to parse the towns.json and county .json files via
   * Gson.
   */
  private static class DemographicsFile {
    private Map<String, Demographics> demographicsFile;
  }
}
