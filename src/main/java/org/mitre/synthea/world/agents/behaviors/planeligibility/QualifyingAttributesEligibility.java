package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * An eligibility criteria based on whether a person has the given attributes.
 */
public class QualifyingAttributesEligibility implements IPlanEligibility {

  private final List<String> qualifyingAttributes;

  /**
   * Constructor.
   * @param attributes  The "|" delimited string or file of qualifying attributes.
   */
  public QualifyingAttributesEligibility(String attributes) {
    if (attributes.contains("/")) {
      // The input is a file, so we have a file that defines the eligible conditions.
      qualifyingAttributes = buildQualifyingAttributesFile(attributes);
    } else {
      // The input is a set of attributes.
      qualifyingAttributes = Arrays.asList(attributes.split("\\|"));
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean attributeEligible = qualifyingAttributes.stream().anyMatch(attribute -> {
      if (!person.attributes.containsKey(attribute)) {
        return false;
      }
      Object attributeResult = person.attributes.get(attribute);
      return ((boolean) attributeResult) == true;
    });
    return attributeEligible;
  }

  /**
   * Builds a list of codes that would qualify a person for Social Security Disability.
   * @return
   */
  private static List<String> buildQualifyingAttributesFile(String fileName) {
    String resource = null;
    try {
      resource = Utilities.readResource(fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }

    List<String> eligibleAttributes = new ArrayList<String>();

    Iterator<? extends Map<String, String>> csv = null;
    try {
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String attribute = row.get("codes");
      String[] codes = attribute.split("\\|");
      eligibleAttributes.addAll(Arrays.asList(codes));
    }
    return eligibleAttributes;
  }
}
