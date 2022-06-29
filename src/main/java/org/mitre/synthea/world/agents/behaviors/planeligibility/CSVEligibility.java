package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;

public class CSVEligibility implements IPlanEligibility {

  // The possible columns of the CSV input file.
  private static final String POVERTY_MULTIPLIER = "Poverty Multiplier";
  private static final String INCOME_THRESHOLD = "Income Threshold";
  private static final String AGE_THRESHOLD = "Age Threshold";
  private static final String QUALIFYING_CONDITIONS = "Qualifying Codes";
  private static final String QUALIFYING_ATTRIBUTES = "Qualifying Attributes";
  private static final String ACCEPTANCE_LIKELIHOOD = "Acceptance Likelihood";
  private static final String POVERTY_MULTIPLIER_FILE = "Poverty Multiplier File";
  private static final String SPENDDOWN_FILE = "Spenddown File";
  private static final String LOGICAL_OPERATOR = "Logical Operator";
  private static final Object SUB_ELIGIBILITIES = "Sub-Eligibilities";

  // A map that maps a column to the type of eligibilty it should create.
  private static Map<String, Function<String, IPlanEligibility>> eligbilityOptions;

  private final List<IPlanEligibility> eligibilityCriteria;
  private String logicalOperator;

  /**
   * Constructor.
   * @param inputEligibilities The row of eligiblity inputs.
   */
  public CSVEligibility(Map<String, String> inputEligibilities) throws IllegalArgumentException {
    this.eligibilityCriteria = new ArrayList<>();
    for (String key : inputEligibilities.keySet()) {
      if (key.equals(LOGICAL_OPERATOR)) {
        this.logicalOperator = convertToLogicalOperator(inputEligibilities.get(key));
        continue;
      }
      if (key.equals(SUB_ELIGIBILITIES)) {
        List<String> subEligibilities = Arrays.asList(inputEligibilities.get(key).split("\\|"));
        subEligibilities.forEach(subEligibility -> this.eligibilityCriteria.add(
            PlanEligibilityFinder.getEligibilityAlgorithm(subEligibility)));
        continue;
      }
      if (!eligbilityOptions.containsKey(key)) {
        throw new IllegalArgumentException("Invalid CSV eligibility input column: " + key + ".");
      }
      String input = inputEligibilities.get(key);
      IPlanEligibility newEligbility = eligbilityOptions.get(key).apply(input);
      this.eligibilityCriteria.add(newEligbility);
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    if (logicalOperator.equalsIgnoreCase("AND")) {
      return eligibilityCriteria.stream().allMatch(eligibility
          -> eligibility.isPersonEligible(person, time));
    }
    if (logicalOperator.equalsIgnoreCase("OR")) {
      return eligibilityCriteria.stream().anyMatch(eligibility
          -> eligibility.isPersonEligible(person, time));
    }
    throw new RuntimeException("Error: invalid logical operator '"
        + logicalOperator + "' for input eligibility.");
  }

  /**
   * Builds the eligibility options and their mappings for all CSV input eligibilties.
   */
  static void buildEligibilityOptions(String state) {
    eligbilityOptions = new HashMap<>();
    eligbilityOptions.put(POVERTY_MULTIPLIER, (input)
        -> new PovertyMultiplierEligibility(Double.parseDouble(input)));
    eligbilityOptions.put(INCOME_THRESHOLD, (input)
        -> new IncomeThresholdEligibility(Double.parseDouble(input)));
    eligbilityOptions.put(AGE_THRESHOLD, (input)
        -> new AgeThresholdEligibility(Integer.parseInt(input)));
    eligbilityOptions.put(QUALIFYING_CONDITIONS, (input)
        -> new QualifyingConditionCodesEligibility(input));
    eligbilityOptions.put(QUALIFYING_ATTRIBUTES, (input)
        -> new QualifyingAttributesEligibility(input));
    eligbilityOptions.put(ACCEPTANCE_LIKELIHOOD, (input)
        -> new AcceptanceLikelihoodEligibility(Double.parseDouble(input)));
    eligbilityOptions.put(POVERTY_MULTIPLIER_FILE, (input)
        -> new PovertyMultiplierFileEligibility(state, input));
    eligbilityOptions.put(SPENDDOWN_FILE, (input)
        -> new IncomeSpenddownEligibility(state, input));
  }

  /**
   * Converts the given string to a logic operator.
   * @param logicalOperator The input string.
   * @return  The converted logical operator.
   */
  private static String convertToLogicalOperator(String logicalOperator) {
    logicalOperator = logicalOperator.replaceAll("\\s", "");
    if (logicalOperator.equalsIgnoreCase("and")) {
      return "AND";
    }
    if (logicalOperator.equalsIgnoreCase("or") || logicalOperator.isEmpty()) {
      return "OR";
    }
    throw new IllegalArgumentException("Invalid logical operator '"
        + logicalOperator + "' for input eligibilities table.");
  }

  @Override
  public String toString() {
    return "{CSVEligiblity: " + this.eligibilityCriteria.stream().map(eligibility
        -> eligibility.toString()).collect(Collectors.toList()).toString()
        + " Logical operator: " + logicalOperator + "}";
  }
}