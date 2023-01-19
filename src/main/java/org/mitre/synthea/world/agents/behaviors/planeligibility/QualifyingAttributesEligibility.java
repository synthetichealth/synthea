package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * An eligibility criteria based on whether a person has any of the given attributes.
 * Attributes can be checked with logical operators. Multiple logic checks can be
 * input by splitting attribute logic expressions with "|".
 */
public class QualifyingAttributesEligibility implements IPlanEligibility {

  private final List<AttributeQualifier> qualifyingAttributes;

  /**
   * Constructor.
   * @param attributeInput  The "|" delimited string or file of attribute logics.
   */
  public QualifyingAttributesEligibility(String attributeInput) {
    if (attributeInput.endsWith(".csv")) {
      // The input is a csv file, convert it to a list of attribute expressions.
      qualifyingAttributes = buildQualifyingAttributesFile(attributeInput);
    } else {
      // The input is a set of attributes.
      qualifyingAttributes = convertAttributeExpressionSet(attributeInput);
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    return qualifyingAttributes.stream().anyMatch(attributeLogic ->
      attributeLogic.checkLogic(person));
  }

  private static List<AttributeQualifier> convertAttributeExpressionSet(String attributeInput) {
    List<AttributeQualifier> qualifyingAttributes = new ArrayList<>();
    for (String attributeExpression : Arrays.asList(attributeInput.split("\\|"))) {
      qualifyingAttributes.add(convertAttributeExpressionToLogic(attributeExpression));
    }
    return qualifyingAttributes;
  }

  private static AttributeQualifier convertAttributeExpressionToLogic(String attributeExpression) {
    attributeExpression = attributeExpression.replaceAll("\\s", "");
    // We will specifically iterate over the possible operators in this order.
    String[] operatorRegexes = {"<=", ">=", "!=", "==", "<", ">", "="};
    for (String regex : operatorRegexes) {
      String[] splitExpression = attributeExpression.split(regex);
      if (splitExpression.length == 2) {
        String attribute = splitExpression[0];
        String operator = regex;
        String value = splitExpression[1];
        return new AttributeQualifier(attribute, value, operator);
      }
    }
    throw new RuntimeException("Invalid attribute logic expression '" + attributeExpression + "'.");
  }

  /**
   * Builds a list of attributes that would qualify a person for this eligibility type.
   * @return  A list of qualifying attributes.
   */
  private static List<AttributeQualifier> buildQualifyingAttributesFile(String fileName) {
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResource(fileName, true, true);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      throw new RuntimeException("There was an issue reading the file '"
          + fileName + "'. This issue was caused by " + e.getMessage());
    }

    List<AttributeQualifier> attributeEligibilities = new ArrayList<AttributeQualifier>();
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String attributeRow = row.get("attributes");
      attributeEligibilities.addAll(convertAttributeExpressionSet(attributeRow));
    }
    return attributeEligibilities;
  }

  /**
   * An inner class to deal with parsing and converting an
   * attribute logical expression to a logical function.
   */
  private static class AttributeQualifier implements Serializable {

    /**
     * An interface that describes the logic for checking a person's attributes.
     */
    @FunctionalInterface
    private interface AttributeLogic extends Serializable {
      boolean checkAttributeLogic(Person person);
    }

    private final AttributeLogic logic;

    /**
     * Constructor that builds AttributeQualifier logic.
     * @param attribute The attribute name to check.
     * @param value  The value to compare to.
     * @param operator  The logical operator to use.
     */
    AttributeQualifier(String attribute, Object value, String operator) {
      if (operator.equals("=")) {
        operator = "==";
      }
      final String trueOperator = operator;
      // Non-numeric logic can only use equality logic.
      String[] validOperators = {"!=", "=="};
      if (NumberUtils.isCreatable((String) value)) {
        // If the value is a number, treat it and the attribute as numeric.
        value = Double.parseDouble((String) value);
        validOperators = new String[]{">=", ">", "<", "<=", "==", "!="};
      }
      final Object trueValue = value;
      if (Arrays.asList(validOperators).contains(trueOperator)) {
        logic = (Person person) -> {
          Object attributeResult = person.attributes.get(attribute);
          if (attributeResult == null) {
            return false;
          }
          if (!(attributeResult instanceof Number)
              && !(attributeResult instanceof String)
              && !(attributeResult instanceof Boolean)) {
            throw new RuntimeException("Attribute must be of Number, String, or Boolean "
                + "type. Recieved type '" + attributeResult.getClass() + "'.");
          }
          if (attributeResult instanceof Boolean) {
            attributeResult = String.valueOf((Boolean) attributeResult);
          }
          return Utilities.compare(attributeResult, trueValue, trueOperator);
        };
      } else {
        throw new RuntimeException("Operator '" + trueOperator + "' is invalid for attribute '"
            + attribute + "' with value '" + trueValue + "'.");
      }
      return;
    }

    /**
     * Checks whether the person satisfies this attribute qualifing logic.
     * @param person  The person to check.
     * @return  Whether the person's attributes satisfy the logic requirement.
     */
    boolean checkLogic(Person person) {
      return this.logic.checkAttributeLogic(person);
    }
  }
}
