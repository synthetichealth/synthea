package org.mitre.synthea.modules.covid;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

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

public class C19VaccineAgeDistributions {
  public static final String DOSE_RATES_FILE = "covid_dose_rates.csv";
  public static final String DATE_COLUMN_HEADER = "Date";
  public static final LocalDate EXPAND_AGE_TO_TWELVE = LocalDate.of(2021, 5, 10);
  public static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
  public static final HashMap<AgeRange, List<Pair<String, Integer>>> rawDistributions = new HashMap<>();
  public static final HashMap<AgeRange, EnumeratedDistribution<String>> distributions = new HashMap<>();

  public static class AgeRange {
    public int min;
    public int max;
    public String display;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AgeRange ageRange = (AgeRange) o;
      return min == ageRange.min && max == ageRange.max;
    }

    @Override
    public int hashCode() {
      return Objects.hash(min, max);
    }

    public AgeRange(String display) {
      this.display = display;
      switch (display.length()) {
        case 3:
          // Example: 75+
          this.min = Integer.parseInt(display.substring(0 ,2));
          this.max = Integer.MAX_VALUE;
          break;
        case 5:
          // Example: 12-15
          this.min = Integer.parseInt(display.substring(0 ,2));
          this.max = Integer.parseInt(display.substring(3 ,5));
          break;
        default:
          throw new IllegalArgumentException("Display should be 3 or 5 characters long.");
      }
    }

    public boolean in(int age) {
      return age >= min && age <= max;
    }
  }

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
    AgeRange specialCaseRange = new AgeRange("12-15");
    rawDistributions.put(specialCaseRange, rawDistributions.get(specialCaseRange).stream()
        .filter(pair -> {
          String dateString = pair.getFirst();
          LocalDate doseCountDate = CSV_DATE_FORMAT.parse(dateString, LocalDate::from);
          return doseCountDate.isAfter(EXPAND_AGE_TO_TWELVE);
        })
        .collect(Collectors.toList()));
  }

  public static void populateDistributions() {
    rawDistributions.forEach((ageRange, dayInfoList) -> {
      double totalDosesForRange =
          dayInfoList.stream().map(pair -> pair.getSecond()).collect(Collectors.summingInt(Integer::intValue));
      List<Pair<String, Double>> pmf = dayInfoList.stream().map(dayInfo -> {
        double dosesForDay = dayInfo.getSecond();
        double weight = dosesForDay / totalDosesForRange;
        return new Pair<String, Double>(dayInfo.getFirst(), weight);
      }).collect(Collectors.toList());
      distributions.put(ageRange, new EnumeratedDistribution(pmf));
    });
  }

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
}
