package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static Map<String, IPlanEligibility> planEligibilities;

  private static final String ELIGIBILITY_NAME = "name";
  private static final String GENERIC = "generic";

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
   * @param state
   */
  public static void buildPlanEligibilities(String state, String fileName) {    
    planEligibilities = new HashMap<>();
    planEligibilities.put(PlanEligibilityFinder.GENERIC, new GenericPayerEligibilty());
    // Build the CSV input eligbility algorithms.
    CSVEligibility.buildEligibilityOptions(state);
    String resource = null;
    try {
      resource = Utilities.readResource(fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }
    Iterator<? extends Map<String, String>> csv = null;
    try {
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      removeEmptyValues(row);
      String eligblilityName = row.remove(ELIGIBILITY_NAME);
      if (planEligibilities.containsKey(eligblilityName)) {
        throw new RuntimeException("Plan eligibility name " + eligblilityName + " is reserved or already in use.");
      }
      planEligibilities.put(eligblilityName, new CSVEligibility(row));
    }
  }

  private static void removeEmptyValues(Map<String, String> map) {
    List<String> keysToRemove = new ArrayList<>();
    for (String key : map.keySet()) {
      if(map.get(key).isEmpty()){
        keysToRemove.add(key);
      }
    }
    for(String key: keysToRemove){
      map.remove(key);
    }
  }

}
