package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * A class that defines the elgibility logic for Medically Needy Income Limits (MNIL).
 * MNIL allows people who don't qualify for income-based Medicaid to qualify if their expenses
 * bring them down to a certain income bracket.
 * By-age and by-state Standard Medicaid MNIL data from:
 * https://www.medicaidplanningassistance.org/medically-needy-pathway/
 */
public class MedicallyNeedyIncomeEligibility implements IPlanEligibility {

  // Whether Medically Needy Income Limits are available.
  private static boolean mnilAvailable;
  // Whether MNIL is limited to aged/disabled/blind patients.
  private static boolean mnilDisabilityLimited;
  // The income that a person must "spend down" to to be eligible based on MNIL.
  private static int mnilYearlySpenddown;

  /**
   * Constructor.
   * @param state The state.
   * @param fileName  The file to create MNIL from.
   */
  public MedicallyNeedyIncomeEligibility(String state, String fileName) {
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResource(fileName);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      if (row.get("state").equals(state)) {
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

  @Override
  public boolean isPersonEligible(Person person, long time) {
    // For now, we'll skip MNIL for those states without an age/disability requirement.
    if (!mnilAvailable || mnilDisabilityLimited) {
      return false;
    }
    int incomeRemaining = person.incomeRemaining(time);
    boolean mnilEligble = incomeRemaining <= mnilYearlySpenddown;
    return mnilEligble;
  }

}
