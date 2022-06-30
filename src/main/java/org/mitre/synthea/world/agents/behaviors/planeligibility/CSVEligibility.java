package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.modules.HealthInsuranceModule;
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
  private static final String SUB_ELIGIBILITIES = "Sub-Eligibilities";

  private static final String AND = "and";
  private static final String OR = "or";

  // A map of column names to the type of eligibilty it should create.
  private static Map<String, Function<String, IPlanEligibility>> eligbilityOptions;

  private final List<IPlanEligibility> eligibilityCriteria;
  private final String logicalOperator;

  /**
   * Constructor.
   * @param inputEligibilities The row of eligiblity inputs.
   */
  public CSVEligibility(Map<String, String> inputEligibilities) throws IllegalArgumentException {
    this.eligibilityCriteria = new ArrayList<>();

    String logicalOperatorStr = inputEligibilities.remove(LOGICAL_OPERATOR);
    if (logicalOperatorStr == null) {
      logicalOperatorStr = "";
    }
    this.logicalOperator = convertToLogicalOperator(logicalOperatorStr);
    String subEligibilitiesStr = inputEligibilities.remove(SUB_ELIGIBILITIES);
    if (subEligibilitiesStr != null) {
      List<String> subEligibilities = Arrays.asList(subEligibilitiesStr.split("\\|"));
      subEligibilities.forEach(subEligibility -> this.eligibilityCriteria.add(
          PlanEligibilityFinder.getEligibilityAlgorithm(subEligibility)));
    }

    for (String key : inputEligibilities.keySet()) {
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
    if (logicalOperator.equalsIgnoreCase(AND)) {
      return eligibilityCriteria.stream().allMatch(eligibility
          -> eligibility.isPersonEligible(person, time));
    }
    if (logicalOperator.equalsIgnoreCase(OR)) {
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
    eligbilityOptions.put(INCOME_THRESHOLD, (input) -> new IPlanEligibility() {
          public boolean isPersonEligible(Person person, long time) {
            int income = (int) person.attributes.get(Person.INCOME);
            double incomeThreshold = Double.parseDouble(input);
            return income >= incomeThreshold;
          }
        });
    eligbilityOptions.put(POVERTY_MULTIPLIER, (input) -> new IPlanEligibility() {
          public boolean isPersonEligible(Person person, long time) {
            double povertyMultiplier = Double.parseDouble(input);
            double povertyLevel = HealthInsuranceModule.povertyLevel;
            double incomeThreshold = povertyLevel * povertyMultiplier;
            int income = (int) person.attributes.get(Person.INCOME);
            return income <= incomeThreshold;
          }
        });
    eligbilityOptions.put(AGE_THRESHOLD, (input) -> new IPlanEligibility() {
          public boolean isPersonEligible(Person person, long time) {
            int age = person.ageInYears(time);
            int ageThreshold = Integer.parseInt(input);
            return age >= ageThreshold;
          }
        });
    eligbilityOptions.put(QUALIFYING_CONDITIONS, (input)
        -> new QualifyingConditionCodesEligibility(input));
    eligbilityOptions.put(QUALIFYING_ATTRIBUTES, (input)
        -> new QualifyingAttributesEligibility(input));
    eligbilityOptions.put(ACCEPTANCE_LIKELIHOOD, (input) -> new IPlanEligibility() {
          public boolean isPersonEligible(Person person, long time) {
            double acceptanceLikelihood = Double.parseDouble(input);
            return person.rand() < acceptanceLikelihood;
          }
        });
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
    if (StringUtils.isBlank(logicalOperator) || logicalOperator.equalsIgnoreCase(OR)) {
      return OR;
    }
    if (logicalOperator.equalsIgnoreCase(AND)) {
      return AND;
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