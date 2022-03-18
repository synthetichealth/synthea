package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * A class that defines the elgibility logic for Medically Needy Income Limits (MNIL)
 */
public class MedicallyNeedyIncomeEligibility implements IPlanEligibility {

  private static final double povertyLevel = Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);

  // Medicaid Medically Needy Income Limits (MNIL)
  private static boolean mnilAvailable;  // Whether Medicaid Medically Needy Income Limits are available.
  private static boolean mnilDisabilityLimited;  // Whether MNIL is limited to aged/disabled/blind patients.
  private static int mnilYearlySpenddown;  // The income that a person must "spend down" to to be eligible based on MNIL.  

  public MedicallyNeedyIncomeEligibility(String state, String fileName) {
    this.buildMnilEligibility(state, fileName);
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean mnilEligble = false;
    if (mnilAvailable && !mnilDisabilityLimited) {
      // For now, we'll only calculate MNIL for those states without an age/disability requirement.
      int incomeRemaining = person.incomeRemaining(time);
      mnilEligble = incomeRemaining <= mnilYearlySpenddown;
    }
    return mnilEligble;
  }

  /**
   * Builds the income eligibility the given file and state.
   * @param state
   */
  private void buildMnilEligibility(String state, String fileName) {
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
      // MNIL allows people who don't qualify for income-based Medicaid to qualify if their expenses bring them down to a certain income bracket.
      Map<String, String> row = csv.next();
      if(row.get("state").equals(state)){
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
