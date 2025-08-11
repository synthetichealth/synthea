package org.mitre.synthea.world.geography;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Demographics class holds the information from the towns.json and associated county config files.
 * This data is used to build up a synthetic population matching these real-world statistics. A
 * single instance of Demographics represents a single city or town. The Ages, Gender, Race, Income,
 * and Education properties are maps of frequency information. TODO: add ways to better wrap these
 * maps so they are more accessible and useful. TODO: merge this with Location somehow. they
 * probably don't need to be separate classes
 */
public class Demographics implements Comparable<Demographics>, Serializable {
  /** Number of people in the location. */
  public long population;
  /** The unique identifier for the location. */
  public String id;
  /** The name of the city. */
  public String city;
  /** The name of the state where the city is located. */
  public String state;
  /** The name of the county where the city is located. */
  public String county;

  /** A map of age ranges to their frequency in the population. */
  public Map<String, Double> ages;
  /** Age distribution for the population */
  private RandomCollection<String> ageDistribution;

  /** A map of genders to their frequency in the population. */
  public Map<String, Double> gender;
  /** Gender distribution of the population */
  private RandomCollection<String> genderDistribution;

  /** A map of races to their frequency in the population. */
  public Map<String, Double> race;
  /** Race distribution for the population */
  private RandomCollection<String> raceDistribution;

  /** The percentage of the population that is of Hispanic ethnicity. */
  public double ethnicity;
  /** Ethnicity distribution for the population */
  private RandomCollection<String> ethnicityDistribution;

  /** A map of income ranges to their frequency in the population. */
  public Map<String, Double> income;
  /** Income distribution for the population */
  private RandomCollection<String> incomeDistribution;

  /** A map of education levels to their frequency in the population. */
  public Map<String, Double> education;
  /** Eduction distribution for the population */
  private RandomCollection<String> educationDistribution;

  /**
   * Pick an age based on the population distribution for the city.
   *
   * @param random the random number generator to use
   * @return the age in years
   */
  public int pickAge(RandomNumberGenerator random) {
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
    return random.randInt((high - low) + 1) + low;
  }

  /**
   * Pick a gender based on the population distribution for the city.
   *
   * @param random the random number generator to use
   * @return the gender
   */
  public String pickGender(RandomNumberGenerator random) {

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

  /**
   * Pick a race based on the population distribution for the city.
   * Uses the US Census definition for race.
   *
   * @param random the random number generator to use
   * @return the race
   */
  public String pickRace(RandomNumberGenerator random) {
    // lazy-load in case this random collection isn't necessary
    if (raceDistribution == null) {
      raceDistribution = buildRandomCollectionFromMap(race);
    }

    /*
     * Sample Race frequency: "race": { "white": 0.932754172245991,
     * "black": 0.026762094497814148, "asian": 0.014094889727666761, "native":
     * 0.008015564565419232, "other": 0.001 },
     */

    return raceDistribution.next(random);
  }

  /**
   * Pick an ethnicity based on the population distribution for the city.
   * Uses the US Census definition for ethnicity.
   *
   * @param random the random number generator to use
   * @return "hispanic" or "nonhispanic"
   */
  public String pickEthnicity(RandomNumberGenerator random) {
    if (ethnicityDistribution == null) {
      ethnicityDistribution = new RandomCollection();
      ethnicityDistribution.add(ethnicity, "hispanic");
      ethnicityDistribution.add(1 - ethnicity, "nonhispanic");
    }
    return ethnicityDistribution.next(random);
  }

  /**
   * Selects a language based on race and ethnicity.
   * For those of Hispanic ethnicity, language statistics are pulled from the national distribution
   * of spoken languages. For non-Hispanic, national distributions by race are used.
   * @param race US Census race
   * @param ethnicity "hispanic" or "nonhispanic"
   * @param random the random number generator to use
   * @return the language spoken
   */
  public String languageFromRaceAndEthnicity(String race, String ethnicity,
      RandomNumberGenerator random) {
    if (ethnicity.equals("hispanic")) {
      RandomCollection<String> hispanicLanguageUsage = new RandomCollection<>();
      // https://factfinder.census.gov/faces/tableservices/jsf/pages/productview.xhtml?pid=ACS_17_5YR_B16006&prodType=table
      // Of the estimated 51,375,831 people with Hispanic ethnicity in the US:
      // - 13,957,749 speak only English (27.1%)
      // - 27,902,879 speak Spanish and English very well or well (54.3%)
      // - 9,278,993 speak Spanish and English not well or not at all (18%)
      // - 0.4% speak another language, which we will ignore to simplify things
      // 48.85% will speak English (only English + half of bilingual) the rest will speak Spanish
      hispanicLanguageUsage.add(48.85, "english");
      hispanicLanguageUsage.add(51.15, "spanish");
      return hispanicLanguageUsage.next(random);
    } else {
      switch (race) {
        // For the people who are of nonhispanic ethnicity, use the national distribution of
        // languages spoken:
        // http://www2.census.gov/library/data/tables/2008/demo/language-use/2009-2013-acs-lang-tables-nation.xls?#
        //
        // While the census does not provide a breakdown of language usage by
        // race, previously Synthea would associate languages to race through ethnicity. This
        // code "flattens" out that older relationship.
        case "white":
          // Only 1.5% of people who report a race of white alone speak English less than very well.
          // Given the previous categorization of languages by Synthea, the numbers line up closely.
          // https://factfinder.census.gov/faces/tableservices/jsf/pages/productview.xhtml?pid=ACS_17_5YR_B16005H&prodType=table
          RandomCollection<String> whiteLanguageUsage = new RandomCollection();
          whiteLanguageUsage.add(0.002, "italian");
          whiteLanguageUsage.add(0.004, "french");
          whiteLanguageUsage.add(0.003, "german");
          whiteLanguageUsage.add(0.001, "polish");
          whiteLanguageUsage.add(0.002, "portuguese");
          whiteLanguageUsage.add(0.003, "russian");
          whiteLanguageUsage.add(0.001, "greek");
          whiteLanguageUsage.add(0.984, "english");
          return whiteLanguageUsage.next(random);
        case "black":
          // Only 3% of people who report a race of black or African American alone speak English
          // less than very well.
          // https://factfinder.census.gov/faces/tableservices/jsf/pages/productview.xhtml?pid=ACS_17_5YR_B16005B&prodType=table
          RandomCollection<String> blackLanguageUsage = new RandomCollection();
          blackLanguageUsage.add(0.004, "french");
          blackLanguageUsage.add(0.026, "spanish");
          blackLanguageUsage.add(0.97, "english");
          return blackLanguageUsage.next(random);
        case "asian":
          // 33% of people who report a race of Asian alone speak English less than very well
          // https://factfinder.census.gov/faces/tableservices/jsf/pages/productview.xhtml?pid=ACS_17_5YR_B16005D&prodType=table
          // From the national language numbers:
          // - 2,896,766 Chinese speakers
          // - 449,475 Japanese speakers
          // - 1,117,343 Korean speakers
          // - 1,399,936 Vietnamese speakers
          // - 643,337 Hindi speakers
          // So, 44.5% of the selected Asian language speakers use Chinese, which accounts for 14.7%
          // of the overall population of people who report a race of Asian. This is repeated for
          // the rest of the languages.
          RandomCollection<String> asianLanguageUsage = new RandomCollection();
          asianLanguageUsage.add(0.147, "chinese");
          asianLanguageUsage.add(0.022, "japanese");
          asianLanguageUsage.add(0.056, "korean");
          asianLanguageUsage.add(0.07, "vietnamese");
          asianLanguageUsage.add(0.033, "hindi");
          asianLanguageUsage.add(0.67, "english");
          return asianLanguageUsage.next(random);
        case "native":
          // TODO: This is overly simplistic, 7% of people who report a race of American Indian and
          // Alaska Native speak English less than well.
          // https://factfinder.census.gov/faces/tableservices/jsf/pages/productview.xhtml?pid=ACS_17_5YR_B16005C&prodType=table
          return "english";
        case "hawaiian":
          // https://files.hawaii.gov/dbedt/economic/data_reports/Non_English_Speaking_Population_in_Hawaii_April_2016.pdf
          RandomCollection<String> hawaiianLanguageUsage = new RandomCollection();
          hawaiianLanguageUsage.add(0.891, "english");
          hawaiianLanguageUsage.add(0.109, "hawaiian");
          return hawaiianLanguageUsage.next(random);
        case "other":
          // 36% of people who report a race of something else speak English less than well
          // https://factfinder.census.gov/faces/tableservices/jsf/pages/productview.xhtml?pid=ACS_17_5YR_B16005F&prodType=table
          // There are 924,374 Arabic speakers estimated nationally. Since there are 14,270,613
          // people report some other race, we'll give people in this race category a 6.5% chance
          // of speaking Arabic.
          // TODO: Figure out what languages to assign to the missing 30%
          RandomCollection<String> otherLanguageUsage = new RandomCollection();
          otherLanguageUsage.add(0.065, "arabic");
          otherLanguageUsage.add(0.935, "english");
          return otherLanguageUsage.next(random);
        default:
          // Should never happen
          return "english";
      }
    }
  }

  /**
   * Pick an income level based on the population distribution for the city.
   *
   * @param random the random number generator to use
   * @return the income level
   */
  public int pickIncome(RandomNumberGenerator random) {
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
    return random.randInt((high - low) + 1) + low;
  }

  /**
   * Simple linear formula just maps federal poverty level to 0.0 and 75,000 to 1.0.
   * The 75,000 figure was chosen based on
   * https://www.princeton.edu/~deaton/downloads/deaton_kahneman_high_income_improves_evaluation_August2010.pdf
   * @param income Annual income.
   * @return a value between 0.0 and 1.0 representing the income level.
   */
  public double incomeLevel(int income) {
    double poverty =
            Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);
    double high = Config.getAsDouble("generate.demographics.socioeconomic.income.high", 75000);

    if (income >= high) {
      return 1.0;
    } else if (income <= poverty) {
      return 0.0;
    } else {
      return ((double) income - poverty) / (high - poverty);
    }
  }

  /**
   * Return the poverty ratio.
   * @param income Annual income.
   * @return poverty ratio.
   */
  public double povertyRatio(int income) {
    double poverty =
            Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);
    return ((double) income) / poverty;
  }

  /**
   * Return a random education level based on statistics.
   * @param random the random number generator to use
   * @return the randomly selected education level
   */
  public String pickEducation(RandomNumberGenerator random) {
    // lazy-load in case this randomcollection isn't necessary
    if (educationDistribution == null) {
      educationDistribution = buildRandomCollectionFromMap(education);
    }

    return educationDistribution.next(random);
  }

  /**
   * Return a random number between the configured bounds for a specified education level.
   * @param level the education level, one of "less_than_hs", "hs_degree", "some_college",
   *        "bs_degree"
   * @param random the random number generator to use
   * @return a random number between the configured bounds representing the education level
   */
  public double educationLevel(String level, RandomNumberGenerator random) {
    double lessThanHsMin = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.less_than_hs.min", 0.0);
    double lessThanHsMax = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.less_than_hs.max", 0.5);
    double hsDegreeMin = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.hs_degree.min", 0.1);
    double hsDegreeMax = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.hs_degree.max", 0.75);
    double someCollegeMin = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.some_college.min", 0.3);
    double someCollegeMax = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.some_college.max", 0.85);
    double bsDegreeMin = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.bs_degree.min", 0.5);
    double bsDegreeMax = Config.getAsDouble(
            "generate.demographics.socioeconomic.education.bs_degree.max", 1.0);

    switch (level) {
      case "less_than_hs":
        return random.rand(lessThanHsMin, lessThanHsMax);
      case "hs_degree":
        return random.rand(hsDegreeMin, hsDegreeMax);
      case "some_college":
        return random.rand(someCollegeMin, someCollegeMax);
      case "bs_degree":
        return random.rand(bsDegreeMin, bsDegreeMax);
      default:
        return 0.0;
    }
  }


  /**
   * Calculate the socio-economic score for the supplied parameters.
   *
   * @param income the income level
   * @param education the education level
   * @param occupation the occupation level
   * @return the socio-economic score
   */
  public double socioeconomicScore(double income, double education, double occupation) {
    double incomeWeight = Config.getAsDouble("generate.demographics.socioeconomic.weights.income");
    double educationWeight =
            Config.getAsDouble("generate.demographics.socioeconomic.weights.education");
    double occupationWeight =
            Config.getAsDouble("generate.demographics.socioeconomic.weights.occupation");

    return (income * incomeWeight) + (education * educationWeight)
        + (occupation * occupationWeight);
  }

  /**
   * Return a high/middle/low socio economic category based on the supplied score and the
   * configured stratifier values.
   *
   * @param score the socio-economic score
   * @return the socio-economic category, one of "High", "Middle", or "Low"
   */
  public String socioeconomicCategory(double score) {
    double highScore = Config.getAsDouble("generate.demographics.socioeconomic.score.high");
    double middleScore = Config.getAsDouble("generate.demographics.socioeconomic.score.middle");

    if (score >= highScore) {
      return "High";
    } else if (score >= middleScore) {
      return "Middle";
    } else {
      return "Low";
    }
  }

  /**
   * Get a Table of (State, CityId, Demographics), with the given restrictions on state and city.
   *
   * @param state
   *          The state that is desired. Other states will be excluded from the results.
   * @return Table of (State, CityId, Demographics)
   * @throws IOException
   *           if any exception occurs in reading the demographics file
   */
  public static Table<String, String, Demographics> load(String state)
      throws IOException {
    String filename = Config.get("generate.demographics.default_file");
    String csv = Utilities.readResource(filename, true, true);

    List<? extends Map<String,String>> demographicsCsv = SimpleCSV.parse(csv);

    Table<String, String, Demographics> table = HashBasedTable.create();

    for (Map<String,String> demographicsLine : demographicsCsv) {
      String currCityId = demographicsLine.get("ID");
      String currState = demographicsLine.get("STNAME");

      // for now, only allow one state at a time
      if (state != null && state.equalsIgnoreCase(currState)) {
        Demographics parsed = csvLineToDemographics(demographicsLine);

        table.put(currState, currCityId, parsed);
      }
    }

    return table;
  }

  /**
   * The index of the entry in this list + 1 == the column header in the CSV for that age group.
   * For example, age range 0-4 is stored in the CSV with column header "1".
   */
  private static final List<String> CSV_AGE_GROUPS = Arrays.asList(
          "0..4", "5..9", "10..14", "15..19", "20..24", "25..29",
          "30..34", "35..39", "40..44", "45..49", "50..54",
          "55..59", "60..64", "65..69", "70..74", "75..79", "80..84", "85..110");

  private static final List<String> CSV_RACES = Arrays.asList(
      "WHITE", "BLACK", "ASIAN", "NATIVE", "OTHER");

  private static final List<String> CSV_INCOMES = Arrays.asList(
      "00..10", "10..15", "15..25", "25..35", "35..50",
      "50..75", "75..100", "100..150", "150..200", "200..999");

  private static final List<String> CSV_EDUCATIONS = Arrays.asList(
      "LESS_THAN_HS", "HS_DEGREE", "SOME_COLLEGE", "BS_DEGREE");

  private static final String CSV_ETHNICITY = "HISPANIC";

  /**
   * Map a single line of the demographics CSV file into a Demographics object.
   *
   * @param line a line representing one city, parsed via SimpleCSV
   * @return the Demographics object for that city
   */
  private static Demographics csvLineToDemographics(Map<String,String> line) {
    Demographics d = new Demographics();

    d.population = Double.valueOf(line.get("POPESTIMATE2015")).longValue();
    // some .0's seem to sneak in there and break Long.valueOf

    d.id = line.get("ID");
    d.city = line.get("NAME");
    d.state = line.get("STNAME");
    d.county = line.get("CTYNAME");

    d.ages = new HashMap<String, Double>();

    int i = 1;
    for (String ageGroup : CSV_AGE_GROUPS) {
      String csvHeader = Integer.toString(i++);
      double percentage = Double.parseDouble(line.get(csvHeader));
      d.ages.put(ageGroup, percentage);

    }
    nonZeroDefaults(d.ages);

    d.gender = new HashMap<String, Double>();
    d.gender.put("male", Double.parseDouble(line.get("TOT_MALE")));
    d.gender.put("female", Double.parseDouble(line.get("TOT_FEMALE")));
    nonZeroDefaults(d.gender);

    double percentageTotal = 0;
    d.race = new HashMap<String, Double>();
    for (String race : CSV_RACES) {
      double percentage = Double.parseDouble(line.get(race));
      d.race.put(race.toLowerCase(), percentage);
      percentageTotal += percentage;
    }
    if (percentageTotal < 1.0) {
      // Account for Hawaiian and Pacific Islanders
      // and mixed race responses, and responses
      // that chose not to answer the race question.
      double percentageRemainder = (1.0 - percentageTotal);
      double hawaiian = 0.5 * percentageRemainder;
      double other = percentageRemainder - hawaiian;
      d.race.put("hawaiian", hawaiian);
      d.race.put("other", other);
    } else {
      d.race.put("hawaiian", 0.0);
      d.race.put("other", 0.0);
    }
    nonZeroDefaults(d.race);

    d.income = new HashMap<String, Double>();
    for (String income : CSV_INCOMES) {
      String incomeString = line.get(income);
      if (incomeString.isEmpty()) {
        d.income.put(income, 0.01); // dummy value, has to be non-zero
      } else {
        double percentage = Double.parseDouble(incomeString);
        d.income.put(income, percentage);
      }
    }
    nonZeroDefaults(d.income);

    d.education = new HashMap<String, Double>();
    for (String education : CSV_EDUCATIONS) {
      String educationString = line.get(education);
      if (educationString.isEmpty()) {
        d.education.put(education.toLowerCase(), 0.01); // dummy value, has to be non-zero
      } else {
        double percentage = Double.parseDouble(educationString);
        d.education.put(education.toLowerCase(), percentage);
      }
    }
    nonZeroDefaults(d.education);

    d.ethnicity = Double.parseDouble(line.get(CSV_ETHNICITY));

    return d;
  }

  /**
   * A distribution with all zero values will cause runtime issues.
   * If the values are all zero, an equally weighted uniform distribution will be set.
   *
   * @param map the map to check
   */
  private static void nonZeroDefaults(Map<String, Double> map) {
    // Any null or nan values should be zero
    map.replaceAll((key, value) -> (value == null || value.isNaN()) ? 0.0 : value);
    // Now check if all values are zero
    boolean allZero = true;
    for (Double value : map.values()) {
      if (value != 0)  {
        allZero = false;
        break;
      }
    }
    // If all values were zero, apply a uniform distribution.
    if (allZero) {
      Double value = 1.0 / map.size();
      map.replaceAll((key, oldValue) -> value);
    }
  }

  /**
   * Helper function to convert a map of frequencies into a RandomCollection.
   *
   * @param map the map of frequencies
   * @return a RandomCollection based on the map
   */
  private static RandomCollection<String> buildRandomCollectionFromMap(Map<String, Double> map) {
    RandomCollection<String> distribution = new RandomCollection<>();
    for (Map.Entry<String, Double> e : map.entrySet()) {
      distribution.add(e.getValue(), e.getKey());
    }
    return distribution;
  }

  @Override
  public int compareTo(Demographics o) {
    return (int) (this.population - o.population);
  }
}
