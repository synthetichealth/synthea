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
 * An algorithm that defines the logic for eligibility based on a set of qualifying condition codes.
 * The codes can either be input as seperated by "xxx|xxx" or can be input in a file with a heading "codes".
 */
public class QualifyingConditionCodesEligibility implements IPlanEligibility {

  // A list that maintains the codes that qualify a person for Social Security Disability.
  // Source: https://www.ssa.gov/disability/professionals/bluebook/AdultListings.htm
  // Note that the this list is incomplete, some condtions are not currently simulated in Synthea.
  // It is by no means an exhaustive list, it probably has ~50% of the disability eligibilities.

  // A list that maintains the qualifying codes for this instance of an eligibility criteria.
  private final List<String> qualifyingCodes;

  public QualifyingConditionCodesEligibility(String input) {
    if (input.contains("/")) {
      // The input is a file, so we have a file that defines the eligible conditions.
      qualifyingCodes = buildQualifyingConditionsFile(input);
    } else {
      // The input is a set of codes.
      qualifyingCodes = Arrays.asList(input.split("\\|"));
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean conditionEligible = qualifyingCodes.stream().anyMatch(code -> person.record.conditionActive(code));
    return conditionEligible;
  }

  /**
   * Builds a list of codes that would qualify a person for this eligibility type.
   * @return
   */
  private static List<String> buildQualifyingConditionsFile(String fileName) {
    String resource = null;
    try {
      resource = Utilities.readResource(fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }

    List<String> eligibleCodes = new ArrayList<String>();

    Iterator<? extends Map<String, String>> csv = null;
    try {
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
        String codeValue = row.get("codes");
        String[] codes = codeValue.split("\\|");
        eligibleCodes.addAll(Arrays.asList(codes));
    }
    return eligibleCodes;
  }
}
