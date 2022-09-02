package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static Map<String, IPlanEligibility> planEligibilities;

  private static final String ELIGIBILITY_NAME = "Name";
  public static final String GENERIC = "GENERIC";

  /**
   * Returns the correct elgibility algorithm based on the given string.
   * @param eligibility The name of the eligibility type.
   * @return  The requested payer eligibility algorithm.
   */
  public static IPlanEligibility getEligibilityAlgorithm(String eligibility) {
    String cleanedEigibility = eligibility.replaceAll("\\s", "").toUpperCase();
    if (planEligibilities.containsKey(cleanedEigibility)) {
      return planEligibilities.get(cleanedEigibility);
    }
    throw new RuntimeException("Plan eligiblity " + eligibility + " does not exist.");
  }

  /**
   * Builds the plan eligiblities for the given state and CSV input file.
   * @param state The state.
   */
  public static void buildPlanEligibilities(String state, String fileName) {
    planEligibilities = new HashMap<>();
    // Generic eliigblities always return true.
    planEligibilities.put(PlanEligibilityFinder.GENERIC, new IPlanEligibility() {
      @Override
      public boolean isPersonEligible(Person person, long time) {
        return true;
      }
    });
    // Build the CSV input eligbility algorithms.
    CSVEligibility.buildEligibilityOptions(state);
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResourceAndStripBOM(fileName);
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
