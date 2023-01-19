package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Person;

/**
 * An eligiblity type based on a file that dictates by-age and by-state poverty multiplier
 * thresholds.
 */
public class PovertyMultiplierFileEligibility implements IPlanEligibility {

  // Income limits.
  private static double povertyAge1;  // Poverty multiplier ages 0-1.
  private static double povertyAge5;  // Poverty multiplier ages 2-5.
  private static double povertyAge18;  // Poverty multiplier ages 6-18.
  private static double povertyPregnant;  // Poverty multiplier for pregnant women.
  private static double povertyAdult;  // Poverty multiplier for ages 19+.

  public PovertyMultiplierFileEligibility(String state, String fileName) {
    this.buildPovertyMultiplierEligibility(state, fileName);
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    int income = (Integer) person.attributes.get(Person.INCOME);
    double povertymultiplier = determinePovertyMultiplier(person, time);
    double incomeThreshold = povertymultiplier * HealthInsuranceModule.povertyLevel;
    return (income <= incomeThreshold);
  }

  /**
   * Determines the poverty multiplier this person qualifies for at this time.
   * @param person  The person to check the poverty multiplier for.
   * @param time  The time to check for.
   * @return  The poverty multuplier for this person at this time.
   */
  private double determinePovertyMultiplier(Person person, long time) {
    int age = person.ageInYears(time);
    if (age <= 1) {
      return povertyAge1;
    }
    if (age <= 5) {
      return povertyAge5;
    }
    if (age <= 18) {
      return povertyAge18;
    }
    boolean female = (person.attributes.get(Person.GENDER).equals("F"));
    boolean pregnant = (person.attributes.containsKey("pregnant")
        && (boolean) person.attributes.get("pregnant"));
    if (female && pregnant) {
      return povertyPregnant;
    }
    return povertyAdult;
  }

  /**
   * Builds the income eligibility the given file and state.
   * @param state The state.
   */
  private void buildPovertyMultiplierEligibility(String state, String fileName) {
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResource(fileName, true, true);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      if (row.get("state").equals(state)) {
        povertyAge1 = Double.parseDouble(row.get("0-1"));
        povertyAge5 = Double.parseDouble(row.get("2-5"));
        povertyAge18 = Double.parseDouble(row.get("6-18"));
        povertyPregnant = Double.parseDouble(row.get("pregnant"));
        povertyAdult = Double.parseDouble(row.get("adult"));
        return;
      }
    }
    throw new RuntimeException("Poverty Eligibility File '" + fileName
        + "' does not contain state '" + state + "'.");
  }

}
