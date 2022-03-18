package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class PovertyMultiplierFileEligibility implements IPlanEligibility {

  private static final double povertyLevel = Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);

  // Income limits.
  private static double povertyAge1;  // Poverty multiplier ages 0-1.
  private static double povertyAge5;  // Poverty multiplier ages 2-5.
  private static double povertyAge18;  // Poverty multiplier ages 6-18.
  private static double povertyPregnant;  // Poverty multiplier for pregnant women.
  private static double povertyParent;  // Poverty multiplier for parents.
  private static double povertyAdult;  // Poverty multiplier for ages 19+.

  public PovertyMultiplierFileEligibility(String state, String fileName) {
    this.buildPovertyMultiplierEligibility(state, fileName);
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    int income = (Integer) person.attributes.get(Person.INCOME);
    double povertymultiplier = determinePovertyMultiplier(person, time);
    double incomeThreshold = povertymultiplier * povertyLevel;
    return (income <= incomeThreshold);
  }

    /**
   * Determines the poverty multiplier this person qualifies for at this time.
   * @param person
   * @param time
   * @return
   */
  private double determinePovertyMultiplier(Person person, long time) {
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
   * Builds the income eligibility the given file and state.
   * @param state
   */
  private void buildPovertyMultiplierEligibility(String state, String fileName) {
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
        return;
      }
    }
    throw new RuntimeException("Invalid state " + state + " used.");
  }
  
}
