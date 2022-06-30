package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static Map<String, IPlanEligibility> planEligibilities;

  private static final String ELIGIBILITY_NAME = "name";
  public static final String GENERIC = "generic";

  /**
   * Returns the correct elgibility algorithm based on the given string.
   * @param eligibility The name of the eligibility type.
   * @return  The requested payer eligibilty algorithm.
   */
  public static IPlanEligibility getEligibilityAlgorithm(String eligibility) {
    if (planEligibilities.containsKey(eligibility)) {
      return planEligibilities.get(eligibility);
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
      resource = Utilities.readResource(fileName);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      removeEmptyMapValues(row);
      String eligblilityName = row.remove(ELIGIBILITY_NAME);
      if (planEligibilities.containsKey(eligblilityName)) {
        throw new IllegalArgumentException("Plan eligibility name '"
            + eligblilityName + "'' is reserved or already in use.");
      }
      planEligibilities.put(eligblilityName, new CSVEligibility(row));
    }
  }

  private static void removeEmptyMapValues(Map<String, String> map) {
    Set<String> keysToRemove = map.keySet().stream()
        .filter(key -> map.get(key).isEmpty()).collect(Collectors.toSet());
    map.keySet().removeAll(keysToRemove);
  }

}
