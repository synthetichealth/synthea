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

  // Income limits.
  private static double povertyAge1;  // Poverty percentile ages 0-1.
  private static double povertyAge5;  // Poverty percentile ages 2-5.
  private static double povertyAge18;  // Poverty percentile ages 6-18.
  private static double povertyPregnant;  // Poverty percentile for pregnant women.
  private static double povertyParent;  // Poverty percentile for parents.
  private static double povertyAdult;  // Poverty percentile for ages 19+.
  // Medicaid Medically Needy Income Limits (MNIL)
  private static boolean mnilAvailable;  // Whether Medicaid Medically Needy Income Limits are available.
  private static boolean mnilDisabilityLimited;  // Whether MNIL is limited to aged/disabled/blind patients.
  private static int mnilYearlySpenddown;  // The income that a person must "spend down" to to be eligible based on MNIL.

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean blind = (person.attributes.containsKey("blindness")
        && (boolean) person.attributes.get("blindness"));
    int income = (Integer) person.attributes.get(Person.INCOME);

    double povertyPercentile = determinePovertyPercentile(person, time);
    double medicaidIncomeLevel = povertyPercentile * povertyLevel;
    boolean medicaidIncomeEligible = (income <= medicaidIncomeLevel);

    boolean medicaidEligible =  blind || medicaidIncomeEligible;

    if (!medicaidEligible && mnilAvailable && !mnilDisabilityLimited) {
      // If the person is not medicaid eligble, check if they're MNIL eligble.
      // For now, we'll only calculate MNIL for those states without an age/disability requirement.
      int incomeRemaining = person.incomeRemaining(time);
      medicaidEligible = incomeRemaining <= mnilYearlySpenddown;
    }

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
      // By-age and by-state Medicaid Income Limits data from: 
      // MNIL (Medically Needy Income Limit) data from: https://www.medicaidplanningassistance.org/medically-needy-pathway/
      // MNIL allows people who don't qualify for income-based Medicare to qualify if their expenses bring them down to a certain income bracket.
      Map<String, String> row = csv.next();
      if(row.get("state").equals(state)){
        povertyAge1 = Double.parseDouble(row.get("0-1"));
        povertyAge5= Double.parseDouble(row.get("2-5"));
        povertyAge18 = Double.parseDouble(row.get("6-18"));
        povertyPregnant = Double.parseDouble(row.get("pregnant"));
        povertyParent = Double.parseDouble(row.get("parent"));
        povertyAdult = Double.parseDouble(row.get("adult"));
        mnilAvailable = Boolean.parseBoolean(row.get("mnil-available"));
        if (mnilAvailable) {
          mnilDisabilityLimited = Boolean.parseBoolean(row.get("age-blind-disabled-limit"));
          mnilYearlySpenddown = Integer.parseInt(row.get("monthly-spenddown-req")) * 12;
        } else {
          mnilDisabilityLimited = false;
          mnilYearlySpenddown = -1;
        }
        return;
      }
    }
    throw new RuntimeException("Invalid state " + state + " used.");
  }
}
