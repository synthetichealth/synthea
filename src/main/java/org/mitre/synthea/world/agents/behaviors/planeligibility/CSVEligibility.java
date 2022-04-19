package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;

public class CSVEligibility implements IPlanEligibility {

  // The possible columns of the CSV input file.
  private static final String POVERTY_MULTIPLIER = "poverty_multiplier";
  private static final String INCOME_THRESHOLD = "income_threshold";
  private static final String AGE_THRESHOLD = "age_threshold";
  private static final String QUALIFYING_CONDITIONS = "qualifying_conditions";
  private static final String ACCEPTANCE_LIKELIHOOD = "acceptance_likelihood";
  private static final String POVERTY_MULTIPLIER_FILE = "poverty_multiplier_file";
  private static final String MNIL_FILE = "mnil_file";
  private static final String LOGICAL_OPERATOR = "logical_operator";
  private static final Object SUB_ELIGIBILITIES = "sub_eligibilities";
  private static final String VETERAN = "veteran_eligiblity";

  // A map that maps a column to the type of eligibilty it should create.
  private static Map<String, Function<String, IPlanEligibility>> eligbilityOptions;

  private final List<IPlanEligibility> eligibilityCriteria;
  private String logicalOperator;

  /**
   * Constructor
   * @param inputEligibilities
   */
  public CSVEligibility(Map<String, String> inputEligibilities) {
    eligibilityCriteria = new ArrayList<>();
    for (String key : inputEligibilities.keySet()) {
      if (key.equals(LOGICAL_OPERATOR)) {
        logicalOperator = convertToLogicalOperator(inputEligibilities.get(key));
        break;
      } else if(key.equals(SUB_ELIGIBILITIES)) {
        for (String subAlgorithm : inputEligibilities.get(key).split("\\|")) {
          eligibilityCriteria.add(PlanEligibilityFinder.getEligibilityAlgorithm(subAlgorithm));
        }
      } else {
        if (!eligbilityOptions.containsKey(key)) {
          throw new RuntimeException("Invalid CSV eligibility input column: " + key + ".");
        }
        String input = inputEligibilities.get(key);
        IPlanEligibility newEligbility = eligbilityOptions.get(key).apply(input);
        eligibilityCriteria.add(newEligbility);
      }
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    if (logicalOperator.equalsIgnoreCase("AND")) {
      return eligibilityCriteria.stream().allMatch(eligibility -> eligibility.isPersonEligible(person, time));
    } else if (logicalOperator.equalsIgnoreCase("OR")) {
      return eligibilityCriteria.stream().anyMatch(eligibility -> eligibility.isPersonEligible(person, time));
    }
    throw new RuntimeException("Erorr with logical operator " + logicalOperator+ " for input csv.");
  }

  /**
   * Builds the eligibility options and their mappings for all CSV input eligibilties.
   * @return
   */
  static void buildEligibilityOptions(String state) {
    eligbilityOptions = new HashMap<>();
    eligbilityOptions.put(POVERTY_MULTIPLIER, (input) -> new PovertyMultiplierEligibility(Double.parseDouble(input)));
    eligbilityOptions.put(INCOME_THRESHOLD, (input) -> new IncomeThresholdEligibility(Double.parseDouble(input)));
    eligbilityOptions.put(AGE_THRESHOLD, (input) -> new AgeThresholdEligibility(Integer.parseInt(input)));
    eligbilityOptions.put(QUALIFYING_CONDITIONS, (input) -> new QualifyingConditionsEligibility(input));
    eligbilityOptions.put(ACCEPTANCE_LIKELIHOOD, (input) -> new AcceptanceLikelihoodEligibility(Double.parseDouble(input)));
    eligbilityOptions.put(POVERTY_MULTIPLIER_FILE, (input) -> new PovertyMultiplierFileEligibility(state, input));
    eligbilityOptions.put(MNIL_FILE, (input) -> new MedicallyNeedyIncomeEligibility(state, input));
    eligbilityOptions.put(VETERAN, (input) -> new VeteranEligiblity());
  }
  
  /**
   * Converts the given string to a logic operator.
   * @param string
   * @return
   */
  private static String convertToLogicalOperator(String string) {
    if(string.toLowerCase().contains("and")){
      return "AND";
    }
    if(string.toLowerCase().contains("or")){
      return "OR";
    }
    throw new RuntimeException("Invalid logical operator " + string + " for input csv.");
  }

  @Override
  public String toString(){
    return "{CSVEligiblity: " + this.eligibilityCriteria.stream().map(eligibility -> eligibility.toString()).collect(Collectors.toList()).toString() + " Logical operator: " + logicalOperator + "}";
  }
  
}
