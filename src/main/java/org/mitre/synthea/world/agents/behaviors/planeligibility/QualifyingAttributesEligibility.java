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
 * An algorithm that defines the logic for eligibility for social security based on disability. This is expected to be used in conjuntion with Medicare, when someone is Social Security eligible, they also qualify for Medicare.
 */
public class QualifyingAttributesEligibility implements IPlanEligibility {

  private final List<String> qualifyingAttributes;

  public QualifyingAttributesEligibility(String input) {
    if (input.contains("/")) {
      // The input is a file, so we have a file that defines the eligible conditions.
      qualifyingAttributes = buildQualifyingAttributesFile(input);
    } else {
      // The input is a set of attributes.
      qualifyingAttributes = Arrays.asList(input.split("\\|"));
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean attributeEligible = qualifyingAttributes.stream().anyMatch(attribute -> {
      Object attributeResult = person.attributes.get(attribute);
      if (attributeResult == null) {
        return false;
      }
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
