package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.math.NumberUtils;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * An eligibility criteria based on whether a person has the given attributes.
 */
public class QualifyingAttributesEligibility implements IPlanEligibility {

  private final List<AttributeQualifier> qualifyingAttributes;

  /**
   * Constructor.
   * @param attributes  The "|" delimited string or file of qualifying attributes.
   */
  public QualifyingAttributesEligibility(String attributeInput) {
    if (attributeInput.contains("/")) {
      // The input is a file, so we have a file that defines the eligible conditions.
      qualifyingAttributes = buildQualifyingAttributesFile(attributeInput);
    } else {
      // The input is a set of attributes.
      qualifyingAttributes = new ArrayList<>();
      for(String attributeExpression : Arrays.asList(attributeInput.split("\\|"))){
        qualifyingAttributes.add(covertAttributeExpressionToLogic(attributeExpression));
      }
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    return qualifyingAttributes.stream().anyMatch(attributeLogic -> 
      attributeLogic.checkLogic(person)
    );
  }

  /**
   * Builds a list of attributes that would qualify a person for this eligibility type.
   * @return  A list of qualifying attributes.
   */
  private static List<AttributeQualifier> buildQualifyingAttributesFile(String fileName) {
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResource(fileName);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }

    List<AttributeQualifier> attributeEligibilities = new ArrayList<AttributeQualifier>();
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String attributeRow = row.get("attributes");
      String[] attributes = attributeRow.split("\\|");
      for(String attributeExpression : attributes){
        AttributeQualifier attributeLogic = covertAttributeExpressionToLogic(attributeExpression);
        attributeEligibilities.add(attributeLogic);
      }
    }
    return attributeEligibilities;
  }

  private static AttributeQualifier covertAttributeExpressionToLogic(String attributeExpression) {
    String regex = " ";
    String[] splitExpression = attributeExpression.split(regex);
    if(splitExpression.length != 3){
      throw new RuntimeException("Invalid attribute expression '" + attributeExpression + "'.");
    }
    String attribute = splitExpression[0];
    String operator = splitExpression[1];
    String value = splitExpression[2];
    return new AttributeQualifier(attribute, value, operator);
  }

  private static class AttributeQualifier {

    @FunctionalInterface
    private interface AttributeLogic {
      boolean checkAttributeLogic(Person person);
    }

    private final AttributeLogic logic;

    AttributeQualifier (String attribute, Object initialValue, String intialOperator) {
      if (intialOperator.equals("=")) {
        intialOperator = "==";
      }
      final String operator = intialOperator;
      // Non-numeric logic can only use equality logic.
      String[] validOperators = {"!=", "=="};
      if (NumberUtils.isCreatable((String) initialValue)) {
        // If the value is a number, treat it and the attribute as numeric.
        initialValue = Double.parseDouble((String) initialValue);
        validOperators = new String[]{">=", ">", "<", "<=", "==", "!="};
      }
      final Object value = initialValue;
      if (Arrays.asList(validOperators).contains(operator)) {
        logic = (Person person) -> {
          Object attributeResult = person.attributes.get(attribute);
          if (attributeResult == null) {
            return false;
          }
          if (!(attributeResult instanceof Number) && !(attributeResult instanceof String) && !(attributeResult instanceof Boolean)) {
            throw new RuntimeException("Attribute must be of Number, String, or Boolean type. Recieved type '" + attributeResult.getClass() + "'.");
          }
          if (attributeResult instanceof Boolean) {
            attributeResult = String.valueOf((Boolean) attributeResult);
          }
          return Utilities.compare(attributeResult, value, operator);
        };
      } else {
        throw new RuntimeException("Operator '" + operator + "' is invalid for attribute '" + attribute + "' with value '" + value + "'.");
      }
      return;
    }

    boolean checkLogic(Person person) {
      return this.logic.checkAttributeLogic(person);
    }

  }
}
