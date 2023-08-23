package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * A class that defines the elgibility logic for Income medical cost spenddowns
 * (MNIL, in the case of Medicaid).
 * MNIL allows people who don't qualify for income-based Medicaid to qualify if their expenses
 * bring them down to a certain income bracket.
 * By-age and by-state Standard Medicaid MNIL data from:
 * https://www.medicaidplanningassistance.org/medically-needy-pathway/
 */
public class IncomeSpenddownEligibility implements IPlanEligibility {

  // Whether Medical spenddowns are available.
  private static boolean spenddownAvailable;
  // Whether spenddowns are limited to aged/disabled/blind patients.
  private static boolean spenddownDisabilityLimited;
  // The income that a person must "spend down" to to be eligible.
  private static int yearlySpenddown;

  private static final String SPENDDOWN_AVAILABLE = "spenddown-available";
  private static final String DISABILITY_LIMITED = "age-blind-disabled-limit";
  private static final String MONTHLY_SPENDDOWN_REQ = "monthly-spenddown-req";

  /**
   * Constructor.
   * @param state The state.
   * @param fileName  The file to create the spenddowns from.
   */
  public IncomeSpenddownEligibility(String state, String fileName) {
    String resource = null;
    Iterator<? extends Map<String, String>> csv = null;
    try {
      resource = Utilities.readResource(fileName, true, true);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      throw new RuntimeException("There was an issue reading the file '"
          + fileName + "'. This issue was caused by " + e.getMessage());
    }
    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      if (row.get("state").equals(state)) {
        spenddownAvailable = Boolean.parseBoolean(row.get(SPENDDOWN_AVAILABLE));
        if (spenddownAvailable) {
          spenddownDisabilityLimited = Boolean.parseBoolean(row.get(DISABILITY_LIMITED));
          yearlySpenddown = Integer.parseInt(row.get(MONTHLY_SPENDDOWN_REQ)) * 12;
        } else {
          spenddownDisabilityLimited = false;
          yearlySpenddown = -1;
        }
        return;
      }
    }
    throw new RuntimeException("Invalid state " + state + " used.");
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    // For now, we'll skip spenddowns for those states without an age/disability requirement.
    if (!spenddownAvailable || spenddownDisabilityLimited) {
      return false;
    }
    // Check for the previous year to see if they were spenddown eligible.
    int incomeRemaining
        = person.coverage.incomeRemaining(time - Config.getAsLong("generate.timestep"));
    boolean spenddownEligible = incomeRemaining <= yearlySpenddown;
    return spenddownEligible;
  }

}
