package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Returns the requested Payer eligibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static Map<String, IPlanEligibility> planEligibilities;

  private static final String ELIGIBILITY_NAME = "Name";
  public static final String GENERIC = "GENERIC";
  public static final IPlanEligibility DEFAULT = new DefaultPlanEligibility();

  /**
   * Returns the correct eligibility algorithm based on the given string.
   * @param eligibility The name of the eligibility type.
   * @return  The requested payer eligibility algorithm.
   */
  public static IPlanEligibility getEligibilityAlgorithm(String eligibility) {
    String cleanedEligibility = eligibility.replaceAll("\\s", "").toUpperCase();
    if (cleanedEligibility.equals(GENERIC)) {
      return DEFAULT;
    } else if (planEligibilities.containsKey(cleanedEligibility)) {
      return planEligibilities.get(cleanedEligibility);
    }
    throw new RuntimeException("Plan eligibility " + eligibility + " does not exist.");
  }

  /**
   * Builds the plan eligibilities for the given state and CSV input file.
   * @param state The state.
   */
  public static void buildPlanEligibilities(String state, String fileName) {
    planEligibilities = new HashMap<>();
    // Build the CSV input eligibility algorithms.
    CSVEligibility.buildEligibilityOptions(state);
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResource(fileName, true, true);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = removeBlankMapStringValues(csv.next());
      row.keySet().forEach(key -> row.put(key, row.get(key).trim()));
      String eligblilityName = row.remove(ELIGIBILITY_NAME).replaceAll("\\s", "").toUpperCase();
      if (planEligibilities.containsKey(eligblilityName)) {
        throw new IllegalArgumentException("Plan eligibility name '"
            + eligblilityName + "'' is reserved or already in use.");
      }
      planEligibilities.put(eligblilityName, new CSVEligibility(row));
    }
  }

  private static <T> Map<T, String> removeBlankMapStringValues(Map<T, String> map) {
    Map<T, String> mapValuesToKeep = map.entrySet().stream()
        .filter(entry -> !StringUtils.isBlank(entry.getValue())).collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return mapValuesToKeep;
  }

}
