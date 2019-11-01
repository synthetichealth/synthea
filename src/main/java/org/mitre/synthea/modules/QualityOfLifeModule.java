package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class QualityOfLifeModule extends Module {

  /**
   * Disability Weight Lookup Table.
   * <br/>
   * Key: Disease Code (e.g. use "44054006" and not "Diabetes")
   * <br/>
   * Value: DisabilityWeight object (inner class)
   */
  private static Map<String, DisabilityWeight> disabilityWeights = loadDisabilityWeights();

  public static final String QALY = "QALY";
  public static final String DALY = "DALY";
  public static final String QOLS = "QOLS";

  public QualityOfLifeModule() {
    this.name = "Quality of Life";
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean process(Person person, long time) {
    if (!person.attributes.containsKey(QALY)) {
      person.attributes.put(QALY, new LinkedHashMap<Integer, Double>());
      person.attributes.put(DALY, new LinkedHashMap<Integer, Double>());
      person.attributes.put(QOLS, new LinkedHashMap<Integer, Double>());
      // linked hashmaps to preserve insertion order, and then we can iterate by year
    }

    Map<Integer, Double> qalys = (Map<Integer, Double>) person.attributes.get(QALY);
    Map<Integer, Double> dalys = (Map<Integer, Double>) person.attributes.get(DALY);
    Map<Integer, Double> qols = (Map<Integer, Double>) person.attributes.get(QOLS);

    int year = Utilities.getYear(time);

    if (!qalys.containsKey(year)) {

      double[] values = calculate(person, time);

      dalys.put(year, values[0]);
      qalys.put(year, values[1]);
      qols.put(year, values[2]);
      person.attributes.put("most-recent-daly", values[0]);
      person.attributes.put("most-recent-qaly", values[1]);

    }
    // java modules will never "finish"
    return false;
  }

  /**
   * Load the disability weights from the gbd_disability_weights.csv file.
   * @return Map of clinical terminology codes (e.g. "44054006") to DisabilityWeight objects.
   */
  protected static Map<String, DisabilityWeight> loadDisabilityWeights() {
    String filename = "gbd_disability_weights.csv";
    try {
      String data = Utilities.readResource(filename);
      Iterator<? extends Map<String,String>> csv = SimpleCSV.parseLineByLine(data);
      Map<String, DisabilityWeight> map = new HashMap<String, DisabilityWeight>();
      while (csv.hasNext()) {
        Map<String,String> row = csv.next();
        map.put(row.get("CODE"), new DisabilityWeight(row));
      }
      return map;
    } catch (Exception e) {
      System.err.println("ERROR: unable to load csv: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Calculate the HALYs for this person, at the given time. HALYs include QALY
   * and DALY.
   * 
   * @param person Person to calculate
   * @param stop   current timestamp
   * @return array of [daly (cumulative), qaly (cumulative), current disability
   *         weight]
   */
  public static double[] calculate(Person person, long stop) {
    // Disability-Adjusted Life Year = DALY = YLL + YLD
    // Years of Life Lost = YLL = (1) * (standard life expectancy at age of death in
    // years)
    // Years Lost due to Disability = YLD = (disability weight) * (average duration
    // of case)
    // from http://www.who.int/healthinfo/global_burden_disease/metrics_daly/en/
    double yll = 0.0;
    double yld = 0.0;

    int age = person.ageInYears(stop);
    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);

    if (!person.alive(stop)) {
      // life expectancy equation derived from IHME GBD 2015 Reference Life Table
      // 6E-5x^3 - 0.0054x^2 - 0.8502x + 86.16
      // R^2 = 0.99978
      double l = ((0.00006 * Math.pow(age, 3))
          - (0.0054 * Math.pow(age, 2)) - (0.8502 * age) + 86.16);
      yll = l;

      // Need to give the yll to the payer here.
      // person.payer.addQALY(personAge/QALY) or .add(DALY)
      // At the end, payer averages this data out

      // TODO - It seems as if this does not get reached as often as it should.
    }

    // Get counts of covered healthcare.
    List<Entry> allCoveredConditions = new ArrayList<Entry>();
    int coveredMedicationCount = 0;
    int coveredProcedureCount = 0;
    int coveredImmunizationCount = 0;
    int coveredEncounterCount = 0;
    for (Encounter encounter : person.coveredHealthRecord.encounters) {
      for (Entry condition : encounter.conditions) {
        allCoveredConditions.add(condition);
      }
      coveredMedicationCount += encounter.medications.size();
      coveredProcedureCount += encounter.procedures.size();
      coveredImmunizationCount += encounter.immunizations.size();
      coveredEncounterCount++;
    }

    // Get counts of uncovered healthcare.
    List<Entry> allLossOfCareConditions = new ArrayList<Entry>();
    int uncoveredMedicationCount = 0;
    int uncoveredProcedureCount = 0;
    int uncoveredImmunizationCount = 0;
    int uncoveredEncounterCount = 0;
    for (Encounter encounter : person.lossOfCareHealthRecord.encounters) {
      for (Entry condition : encounter.conditions) {
        allLossOfCareConditions.add(condition);
      }
      uncoveredMedicationCount += encounter.medications.size();
      uncoveredProcedureCount += encounter.procedures.size();
      uncoveredImmunizationCount += encounter.immunizations.size();
      uncoveredEncounterCount++;
    }

    // Determine the percentage of covered care.
    // NOTE: This percentageOfCoveredCare is based on entire life, not just current year.
    int coveredEntries = coveredEncounterCount + coveredMedicationCount
        + coveredProcedureCount + coveredImmunizationCount;
    int uncoveredEntries = uncoveredEncounterCount + uncoveredMedicationCount
        + uncoveredProcedureCount + uncoveredImmunizationCount;
    if (coveredEntries < 1) {
      coveredEntries = 1;
    }
    double percentageOfCoveredCare = coveredEntries / (coveredEntries + uncoveredEntries);

    // Create a list of all conditions to be used in calculating disability weight.
    allCoveredConditions.addAll(allLossOfCareConditions);
    List<Entry> allConditions = allCoveredConditions;

    double disabilityWeight = 0.0;
    // calculate yld with yearly timestep
    for (int i = 0; i < age + 1; i++) {
      long yearStart = birthdate + TimeUnit.DAYS.toMillis((long) (365.25 * i));
      long yearEnd = birthdate + (TimeUnit.DAYS.toMillis((long) (365.25 * (i + 1) - 1)));
      List<Entry> conditionsInYear = conditionsInYear(allConditions, yearStart, yearEnd);

      disabilityWeight = 0.0;

      for (Entry condition : conditionsInYear) {
        // Get the disability weight for this condition based on the percentageOfCoveredCare.
        disabilityWeight +=
            (double) disabilityWeights.get(condition.codes.get(0).code)
            .getWeight(percentageOfCoveredCare);
      }

      disabilityWeight = Math.min(1.0, weight(disabilityWeight, i + 1));
      yld += disabilityWeight;
    }

    double daly = yll + yld;
    double qaly = age - yld;

    return new double[] { daly, qaly, 1 - disabilityWeight };
  }

  /**
   * Given a list of conditions, return a subset that was active during a given time period
   * indicated by stop and stop.
   * @param conditions The given list of conditions.
   * @param start The start of the time period the condition must be active.
   * @param stop The stop or end of the time period the condition must be active.
   * @return Subset of input conditions that were active given the specified time period.
   */
  protected static List<Entry> conditionsInYear(List<Entry> conditions, long start, long stop) {
    List<Entry> conditionsInYear = new ArrayList<Entry>();
    for (Entry condition : conditions) {
      if (disabilityWeights.containsKey(condition.codes.get(0).code)) {
        // condition.stop == 0 for conditions that have not yet ended
        if (start >= condition.start && condition.start <= stop
            && (condition.stop > start || condition.stop == 0)) {
          conditionsInYear.add(condition);
        }
      }
    }
    return conditionsInYear;
  }

  /**
   * Calculates the age-adjusted disability weight for a single year.
   * @param disabilityWeight The unadjusted disability weight.
   * @param age The age of the person during the year being adjusted.
   * @return The age-adjusted disability weight during a specified age/year.
   */
  protected static double weight(double disabilityWeight, int age) {
    // age_weight = 0.1658 * age * e^(-0.04 * age)
    // from http://www.who.int/quantifying_ehimpacts/publications/9241546204/en/
    // weight = age_weight * disability_weight
    double ageWeight = 0.1658 * age * Math.exp(-0.04 * age);
    double weight = ageWeight * disabilityWeight;
    return weight;
  }

  /**
   * Populate the given attribute map with the list of attributes that this module
   * reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Inventory> attributes) {
    String m = QualityOfLifeModule.class.getSimpleName();
    Attributes.inventory(attributes, m, QALY, true, true, "LinkedHashMap<Integer, Double>");
    Attributes.inventory(attributes, m, DALY, true, true, "LinkedHashMap<Integer, Double>");
    Attributes.inventory(attributes, m, QOLS, true, true, "LinkedHashMap<Integer, Double>");
    Attributes.inventory(attributes, m, Person.BIRTHDATE, true, false, null);
    Attributes.inventory(attributes, m, "most-recent-daly", false, true, "Numeric");
    Attributes.inventory(attributes, m, "most-recent-qaly", false, true, "Numeric");
  }

  private static class DisabilityWeight {
    public double low;
    public double medium;
    public double high;

    public DisabilityWeight(Map<String, String> values) {
      this.low = parseDouble(values.getOrDefault("LOW", "0.0"));
      this.medium = parseDouble(values.getOrDefault("MED", "0.0"));
      this.high = parseDouble(values.getOrDefault("HIGH", "0.0"));
    }

    /**
     * Get the weight of this disability weight based on the percentage of covered care.
     * Uses a triangular distribution where perecentageOfCoveredCare = 1.0 means all care
     * was covered and 0 means no care was covered.
     * 
     * @param percentageOfCoveredCare the percentage of this person's care that was covered.
     * @return
     */
    public double getWeight(double percentageOfCoveredCare) {
      return triangularDistribution(1 - percentageOfCoveredCare);
    }

    /**
     * Returns a value based on a triangular distribution with high, medium, and low points.
     * 
     * @param position the position along the triangular distribution to return.
     * @return
     */
    public double triangularDistribution(double position) {
      double f = (medium - low) / (high - low);
      if (position < f) {
        return low + Math.sqrt(position * (high - low) * (medium - low));
      } else {
        return high - Math.sqrt((1 - position) * (high - low) * (high - medium));
      }
    }

    private double parseDouble(String value) {
      if (value == null || value.isEmpty()) {
        return 0.0;
      } else {
        return Double.parseDouble(value);
      }
    }
  }
}