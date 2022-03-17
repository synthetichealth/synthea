package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard medicaid elgibilty criteria.
 */
public class StandardMedicaidEligibility implements IPlanEligibility {

  private static final double defaultPoverty = 1.33;
  private static final double povertyLevel = Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);

  private static double povertyAge1;  // Poverty percentile ages 0-1.
  private static double povertyAge5;  // Poverty percentile ages 2-5.
  private static double povertyAge18;  // Poverty percentile ages 6-18.
  private static double povertyPregnant;  // Poverty percentile for pregnant women.
  private static double povertyParent;  // Poverty percentile for parents.
  private static double povertyAdult;  // Poverty percentile for ages 19+.

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean blind = (person.attributes.containsKey("blindness")
        && (boolean) person.attributes.get("blindness"));
    int income = (Integer) person.attributes.get(Person.INCOME);

    double povertyPercentile = determinePovertyPercentile(person, time);
    double medicaidIncomeLevel = povertyPercentile * povertyLevel;
    boolean medicaidIncomeEligible = (income <= medicaidIncomeLevel);

    boolean medicaidEligible =  blind || medicaidIncomeEligible;
    return medicaidEligible;
  }

  /**
   * Determines the poverty percentile this person qualifies for at this time.
   * @param person
   * @param time
   * @return
   */
  private double determinePovertyPercentile(Person person, long time) {
    boolean female = (person.attributes.get(Person.GENDER).equals("F"));
    boolean pregnant = (person.attributes.containsKey("pregnant")
        && (boolean) person.attributes.get("pregnant"));
    int age = person.ageInYears(time);
    if(age <= 1) {
      return povertyAge1;
    } else if(age <= 5) {
      return povertyAge5;
    } else if(age <= 18) {
      return povertyAge18;
    } else if(female && pregnant) {
      return povertyPregnant;
    } else {
      return povertyAdult;
    }
  }

  /**
   * Builds the Medicaid income eligibility for the given state based on the input file specified in synthea.properties.
   * @param state
   */
  public static void buildMedicaidEligibility(String state) {
    String fileName = Config.get("generate.payers.insurance_companies.medicaid_eligibility");
    String resource = null;
    try {
      resource = Utilities.readResource(fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }
    Iterator<? extends Map<String, String>> csv = null;
    try {
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      if(row.get("state").equals(state)){
        povertyAge1 = Double.parseDouble(row.get("0-1"));
        povertyAge5= Double.parseDouble(row.get("2-5"));
        povertyAge18 = Double.parseDouble(row.get("6-18"));
        povertyPregnant = Double.parseDouble(row.get("pregnant"));
        povertyParent = Double.parseDouble(row.get("parent"));
        povertyAdult = Double.parseDouble(row.get("adult"));
        return;
      }
    }
    throw new RuntimeException("Invalid state " + state + " used.");
  }
}
