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
 * An algorithm that defines the logic for eligibility based on a set of qualifying condition
 * codes. The codes can either be input as seperated by "xxx|xxx" or can be input in a file
 * with a heading "codes".
 * This can be used to define Social Security eligibility, as described by:
 * https://www.ssa.gov/disability/professionals/bluebook/AdultListings.htm
 * Note that the SSD file (ssd_dsiabilities.csv) list is incomplete, some
 * condtions are not currently simulated in Synthea.
 */
public class QualifyingConditionCodesEligibility implements IPlanEligibility {

  // A list that maintains the qualifying codes for this instance of an eligibility criteria.
  private final List<String> qualifyingCodes;

  /**
   * Constructor.
   * @param codes  The "|" delimited string or file of qualifying attributes.
   */
  public QualifyingConditionCodesEligibility(String codes) {
    if (codes.endsWith(".csv")) {
      // The input is a csv file, so we have a file that defines the eligible conditions.
      qualifyingCodes = buildQualifyingConditionsFile(codes);
    } else {
      // The input is a string set of codes.
      qualifyingCodes = Arrays.asList(codes.split("\\|"));
    }
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean conditionEligible = qualifyingCodes.stream().anyMatch(code
        -> person.record.conditionActive(code));
    return conditionEligible;
  }

  /**
   * Builds a list of codes that would qualify a person for this eligibility type.
   * @return
   */
  private static List<String> buildQualifyingConditionsFile(String fileName) {
    String resource = null;
    try {
      resource = Utilities.readResource(fileName, true, true);
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

  /**
   * Gets the earliest occurrence of a qualifying condition
   * that is active/present on the person.
   * @param person The person.
   * @return Start time of the earliest qualifying condition, or Long.MAX_VALUE if not present.
   */
  public long getEarliestDiagnosis(Person person) {
    long earliest = Long.MAX_VALUE;
    for (String code : qualifyingCodes) {
      Long onset = person.record.presentOnset(code);
      if (onset != null) {
        earliest = Long.min(earliest, onset);
      }
    }
    return earliest;
  }
}
