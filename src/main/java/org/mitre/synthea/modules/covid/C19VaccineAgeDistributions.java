package org.mitre.synthea.modules.covid;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * This class parses the CSV file that is created by calling the CDC API for COVID-19 vaccination
 * information. The API can be found at:
 * <p>
 * https://data.cdc.gov/resource/km4m-vcsb
 * </p>
 * <p>
 * The number of doses given in a day is used to select a date for when an individual in the
 * simulation will get their vaccine. EnumeratedDistributions are created for each age group, with
 * the days weighted by the number of doses administered on that day.
 * </p>
 */
public class C19VaccineAgeDistributions {
  public static final String DOSE_RATES_FILE = "covid_dose_rates.csv";
  public static final String FIRST_SHOT_PROBS_FILE = "covid_first_shot_percentage_by_age.json";
  public static final String DATE_COLUMN_HEADER = "date";
  public static final LocalDate EXPAND_AGE_TO_TWELVE = LocalDate.of(2021, 5, 10);
  public static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");
  public static final HashMap<AgeRange,
      List<Pair<String, Integer>>> rawDistributions = new HashMap<>();
  public static final HashMap<AgeRange,
      EnumeratedDistribution<String>> distributions = new HashMap<>();
  public static final HashMap<AgeRange, Double> firstShotProbByAge = new HashMap<>();

  /**
   * Representation of an age range in years with logic for parsing the format used by the CDC API.
   */
  public static class AgeRange {
    public int min;
    public int max;
    public String display;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AgeRange ageRange = (AgeRange) o;
      return min == ageRange.min && max == ageRange.max;
    }

    @Override
    public int hashCode() {
      return Objects.hash(min, max);
    }

    /**
     * Creates an AgeRange by parsing the display string.
     * @param display something like Ages_75+_yrs or Ages_40-49_yrs
     */
    public AgeRange(String display) {
      this.display = display;
      switch (display.length()) {
        case 12:
          // Example: Ages_75+_yrs
          this.min = Integer.parseInt(display.substring(5, 7));
          this.max = Integer.MAX_VALUE;
          break;
        case 14:
          // Example: Ages_40-49_yrs
          this.min = Integer.parseInt(display.substring(5, 7));
          this.max = Integer.parseInt(display.substring(8, 10));
          break;
        default:
          throw new IllegalArgumentException("Display should be 12 or 14 characters long.");
      }
    }

    /**
     * Determines whether the specified age falls within this range.
     * @param age Age in years
     * @return true if the age is contained in the range
     */
    public boolean in(int age) {
      return age >= min && age <= max;
    }
  }

  /**
   * Run all methods needed to interact with the methods to select shot time or shot chance.
   */
  public static void initialize() {
    loadRawDistribution();
    populateDistributions();
    loadShotProbabilitiesByAge();
  }

  /**
   * Reads in the CSV file. Creating a List for each AgeRange, where the items are Pairs of the
   * String representation of the date and an Integer counting the number of doses administered.
   */
  public static void loadRawDistribution() {
    String fileName = Config.get("generate.lookup_tables") + DOSE_RATES_FILE;
    List<? extends Map<String, String>> rawRates = null;
    try {
      String csv = Utilities.readResource(fileName);
      if (csv.startsWith("\uFEFF")) {
        csv = csv.substring(1); // Removes BOM.
      }
      rawRates = SimpleCSV.parse(csv);
    } catch (IOException e) {
      e.printStackTrace();
    }
    rawRates.forEach((rowMap) -> {
      String date = rowMap.get(DATE_COLUMN_HEADER);
      rowMap.forEach((name, value) -> {
        if (! name.equals(DATE_COLUMN_HEADER)) {
          AgeRange r = new AgeRange(name);
          List distroForRange = rawDistributions.get(r);
          if (distroForRange == null) {
            distroForRange = new ArrayList(200);
            rawDistributions.put(r, distroForRange);
          }
          distroForRange.add(new Pair(date, Integer.parseInt(value)));
        }
      });
    });
    // Special case for 12-15. While there were individuals under 16 who received vaccinations
    // prior to May 10, 2021, the numbers are pretty small. This strips out that population for
    // the sake of simplicity in the simulation
    AgeRange specialCaseRange = new AgeRange("Ages_12-15_yrs");
    rawDistributions.put(specialCaseRange, rawDistributions.get(specialCaseRange).stream()
        .filter(pair -> {
          String dateString = pair.getFirst();
          LocalDate doseCountDate = CSV_DATE_FORMAT.parse(dateString, LocalDate::from);
          return doseCountDate.isAfter(EXPAND_AGE_TO_TWELVE);
        })
        .collect(Collectors.toList()));
  }

  /**
   * Processes the raw distribution information into the EnumeratedDistributions that will be used
   * to select the date of vaccination administration. Must be called after loadRawDistribution().
   */
  public static void populateDistributions() {
    rawDistributions.forEach((ageRange, dayInfoList) -> {
      double totalDosesForRange = dayInfoList.stream()
          .map(pair -> pair.getSecond()).collect(Collectors.summingInt(Integer::intValue));
      List<Pair<String, Double>> pmf = dayInfoList.stream().map(dayInfo -> {
        double dosesForDay = dayInfo.getSecond();
        double weight = dosesForDay / totalDosesForRange;
        return new Pair<String, Double>(dayInfo.getFirst(), weight);
      }).collect(Collectors.toList());
      distributions.put(ageRange, new EnumeratedDistribution(pmf));
    });
  }

  /**
   * Load the JSON file that has a map of age ranges to percentage of that age range that has been
   * vaccinated.
   */
  public static void loadShotProbabilitiesByAge() {
    String fileName = "covid19/" + FIRST_SHOT_PROBS_FILE;
    LinkedTreeMap<String, Object> rawShotProbs = null;
    try {
      String rawJson = Utilities.readResource(fileName);
      Gson gson = new Gson();
      rawShotProbs = gson.fromJson(rawJson, LinkedTreeMap.class);
    } catch (IOException e) {
      throw new RuntimeException("Couldn't load the shot probabilities file", e);
    }
    rawShotProbs.entrySet().forEach(stringObjectEntry -> {
      AgeRange ar = new AgeRange(stringObjectEntry.getKey());
      Double probability = Double.parseDouble((String) stringObjectEntry.getValue());
      probability = probability / 100;
      firstShotProbByAge.put(ar, probability);
    });
  }

  /**
   * Selects a time for an individual to get the COVID-19 vaccine based on their age and historical
   * distributions of vaccine administration based on age.
   * @param person The person to use
   * @param time Time in the simulation used for age calculation
   * @return A time in the simulation when the person should get their first (only?) shot
   */
  public static long selectShotTime(Person person, long time) {
    int age = person.ageInYears(time);
    AgeRange r = distributions.keySet()
        .stream()
        .filter(ageRange -> ageRange.in(age))
        .findFirst()
        .get();
    EnumeratedDistribution<String> distro = distributions.get(r);
    distro.reseedRandomGenerator(person.randLong());
    LocalDate shotDate = CSV_DATE_FORMAT.parse(distro.sample(), LocalDate::from);
    return LocalDateTime.of(shotDate, LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  /**
   * Determines the likelihood that an individual will get a COVID-19 vaccine based on their age.
   * @param person The person to use
   * @param time Time in the simulation used for age calculation
   * @return a value between 0 - 1 representing the chance the person will get vaccinated
   */
  public static double chanceOfGettingShot(Person person, long time) {
    int age = person.ageInYears(time);
    AgeRange r = distributions.keySet()
        .stream()
        .filter(ageRange -> ageRange.in(age))
        .findFirst()
        .get();
    return firstShotProbByAge.get(r);
  }
}
