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
public class QualifyingConditionsEligibility implements IPlanEligibility {

  public static final String SOCIAL_SECURITY = "SOCIAL_SECURITY";

  // A list that maintains the codes that qualify a person for Social Security Disability.
  // Source: https://www.ssa.gov/disability/professionals/bluebook/AdultListings.htm
  // Note that the this list is incomplete, some condtions are not currently simulated in Synthea.
  // It is by no means an exhaustive list, it probably has ~50% of the disability eligibilities.
  private final List<String> ssDisabilities;

  public QualifyingConditionsEligibility(String input) {
    if (input.contains("/")) {
      // The input is a file, so we have a file that defines the eligible conditions.
      ssDisabilities = buildQualifyingConditionsEligibility(input);
    } else {
      // The input is a set of codes.
      ssDisabilities = Arrays.asList(input.split("|"));
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean ssDisabilityEligble = ssDisabilities.stream().anyMatch(code -> person.record.conditionActive(code));
    return ssDisabilityEligble;
  }

  /**
   * Builds a list of codes that would qualify a person for Social Security Disability.
   * @return
   */
  private static List<String> buildQualifyingConditionsEligibility(String fileName) {
    String resource = null;
    try {
      resource = Utilities.readResource(fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }

    List<String> ssdEligibleCodes = new ArrayList<String>();

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
        ssdEligibleCodes.addAll(Arrays.asList(codes));
    }
    return ssdEligibleCodes;
  }
}
