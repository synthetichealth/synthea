package org.mitre.synthea.world.concepts;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.geography.Location;

public class Costs {
  // all of these are CSVs with these columns:
  // code, min cost in $, mode cost in $, max cost in $, comments
  private static final Map<String, CostData> PROCEDURE_COSTS =
      parseCsvToMap("costs/procedures.csv");
  private static final Map<String, CostData> MEDICATION_COSTS =
      parseCsvToMap("costs/medications.csv");
  private static final Map<String, CostData> ENCOUNTER_COSTS =
      parseCsvToMap("costs/encounters.csv");
  private static final Map<String, CostData> IMMUNIZATION_COSTS =
      parseCsvToMap("costs/immunizations.csv");

  private static final double DEFAULT_PROCEDURE_COST = Double
      .parseDouble(Config.get("generate.costs.default_procedure_cost"));
  private static final double DEFAULT_MEDICATION_COST = Double
      .parseDouble(Config.get("generate.costs.default_medication_cost"));
  private static final double DEFAULT_ENCOUNTER_COST = Double
      .parseDouble(Config.get("generate.costs.default_encounter_cost"));
  private static final double DEFAULT_IMMUNIZATION_COST = Double
      .parseDouble(Config.get("generate.costs.default_immunization_cost"));

  private static final Map<String, Double> LOCATION_ADJUSTMENT_FACTORS = parseAdjustmentFactors();

  /**
   * Return the cost of the given entry (Encounter/Procedure/Immunization/Medication).
   * 
   * @param entry the entry to calculate the cost for.
   * @param person the person associated with the entry.
   * @return the total cost of the entry.
   */
  public static double determineCostOfEntry(Entry entry, Person person) {

    double defaultCost = 0.0;
    Map<String, CostData> costs = null;

    if (entry instanceof HealthRecord.Procedure) {
      costs = PROCEDURE_COSTS;
      defaultCost = DEFAULT_PROCEDURE_COST;
    } else if (entry instanceof HealthRecord.Medication) {
      costs = MEDICATION_COSTS;
      defaultCost = DEFAULT_MEDICATION_COST;
    } else if (entry instanceof HealthRecord.Encounter) {
      costs = ENCOUNTER_COSTS;
      defaultCost = DEFAULT_ENCOUNTER_COST;
    } else if (entry instanceof HealthRecord.Immunization) {
      costs = IMMUNIZATION_COSTS;
      defaultCost = DEFAULT_IMMUNIZATION_COST;
    } else {
      // Not an entry type that has an associated cost.
      return 0.0;
    }

    String code = entry.codes.get(0).code;
    // Retrieve the base cost based on the code.
    double baseCost;
    if (costs != null && costs.containsKey(code)) {
      baseCost = costs.get(code).chooseCost(person);
    } else {
      baseCost = defaultCost;
    }

    // Retrieve the location adjustment factor.
    double locationAdjustment = 1.0;
    if (person != null && person.attributes.containsKey(Person.STATE)) {
      String state = (String) person.attributes.get(Person.STATE);
      state = Location.getAbbreviation(state);
      if (LOCATION_ADJUSTMENT_FACTORS.containsKey(state)) {
        locationAdjustment = (double) LOCATION_ADJUSTMENT_FACTORS.get(state);
      }
    }
    // Return the total cost of the given entry.
    return (baseCost * locationAdjustment);
  }

  /**
   * Load all cost data needed by the system.
   */
  public static void loadCostData() {
    // intentionally do nothing
    // this method is only called to ensure the static data is loaded at a predictable time
  }

  /**
   * Parse the given CSV into the costMap.
   */
  private static Map<String, CostData> parseCsvToMap(String filename) {
    try {
      String rawData = Utilities.readResource(filename);
      List<LinkedHashMap<String, String>> lines = SimpleCSV.parse(rawData);

      Map<String, CostData> costMap = new HashMap<>();
      for (Map<String, String> line : lines) {
        String code = line.get("CODE");
        String minStr = line.get("MIN");
        String modeStr = line.get("MODE");
        String maxStr = line.get("MAX");

        try {
          double min = Double.parseDouble(minStr);
          double mode = Double.parseDouble(modeStr);
          double max = Double.parseDouble(maxStr);
          costMap.put(code, new CostData(min, mode, max));
        } catch (NumberFormatException nfe) {
          System.err.println(filename + ": Invalid cost for code: '"
              + code + "' -- costs should be numeric but were "
              + "'" + minStr + "', '" + modeStr + "', '" + maxStr + "'");
          System.err.println("Code '" + code + "' will use the default cost");
          nfe.printStackTrace();
        }
      }

      return costMap;
    } catch (IOException e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError("Unable to read required file: " + filename);
    }
  }

  private static Map<String, Double> parseAdjustmentFactors() {
    try {
      String rawData = Utilities.readResource("costs/adjustmentFactors.csv");
      List<LinkedHashMap<String, String>> lines = SimpleCSV.parse(rawData);

      Map<String, Double> costMap = new HashMap<>();
      for (Map<String, String> line : lines) {
        String state = line.get("STATE");
        String factorStr = line.get("ADJ_FACTOR");
        try {
          Double factor = Double.valueOf(factorStr);
          costMap.put(state, factor);
        } catch (NumberFormatException nfe) {
          throw new RuntimeException("Invalid cost adjustment factor: " + factorStr, nfe);
        }
      }
      return costMap;
    } catch (IOException e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(
          "Unable to read required file: costs/adjustmentFactors.csv");
    }
  }

  /**
   * Whether or not this HealthRecord.Entry has an associated cost on a claim.
   * Billing cost is not necessarily reimbursed cost or paid cost.
   * 
   * @param entry HealthRecord.Entry
   * @return true if the entry has a cost; false otherwise
   */
  public static boolean hasCost(Entry entry) {
    return (entry instanceof HealthRecord.Procedure) 
        || (entry instanceof HealthRecord.Medication)
        || (entry instanceof HealthRecord.Encounter)
        || (entry instanceof HealthRecord.Immunization);
  }

  /**
   * Helper class to store a grouping of cost data for a single concept. Currently
   * cost data includes a minimum, maximum, and mode (most common value).
   * Selection of individual prices based on this cost data should be done using
   * the chooseCost(Random) method.
   */
  private static class CostData {
    private double min;
    private double mode;
    private double max;

    private CostData(double min, double mode, double max) {
      this.min = min;
      this.mode = mode;
      this.max = max;
    }

    /**
     * Select an individual cost based on this cost data. Uses a triangular
     * distribution to pick a randomized value.
     * @param random Source of randomness
     * @return Single cost within the range this set of cost data represents
     */
    private double chooseCost(Person person) {
      return triangularDistribution(min, max, mode, person.rand());
    }

    /**
     * Pick a single value based on a triangular distribution. See:
     * https://en.wikipedia.org/wiki/Triangular_distribution
     * @param min  Lower limit of the distribution
     * @param max  Upper limit of the distribution
     * @param mode Most common value
     * @param rand A random value between 0-1
     * @return a single value from the distribution
     */
    public static double triangularDistribution(double min, double max, double mode, double rand) {
      double f = (mode - min) / (max - min);
      if (rand < f) {
        return min + Math.sqrt(rand * (max - min) * (mode - min));
      } else {
        return max - Math.sqrt((1 - rand) * (max - min) * (max - mode));
      }
    }
  }
}