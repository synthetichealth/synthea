package org.mitre.synthea.modules;

import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

public class HealthInsuranceModule extends Module {

  // Load properties insurance numbers.
  public static long mandateTime
      = Utilities.convertCalendarYearsToTime(Integer.parseInt(Config
      .get("generate.insurance.mandate.year", "2006")));
  public static double mandateOccupation =
      Config.getAsDouble("generate.insurance.mandate.occupation", 0.2);
  public static double povertyLevel =
          Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 17550);

  /**
   * HealthInsuranceModule constructor.
   */
  public HealthInsuranceModule() {}

  @Override
  public Module clone() {
    return this;
  }

  /**
   * Process this HealthInsuranceModule with the given Person at the specified
   * time within the simulation.
   *
   * @param person the person being simulated
   * @param time   the date within the simulated world
   * @return completed : whether or not this Module completed.
   */
  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }

    // Enroll patients in new insurance for the year if a new enrollment period is reached.
    // Note: This means the person will check to change insurance yearly, within one timestep
    // after their birthday.
    if (person.coverage.newEnrollmentPeriod(time)) {
      // Update their last plan's payer with person's QOLS for that year.
      person.coverage.updateLastPayerQols(person.getQolsForYear(Utilities.getYear(time) - 1));
      // Update the insurance for this person at this time.
      this.updateInsurance(person, time);
    }

    // Checks if person has paid their premium this month. If not, they pay it.
    person.checkToPayMonthlyPremium(time);

    // java modules will never "finish"
    return false;
  }

  /**
   * Determine what insurance a person will get based on their attributes.
   *
   * @param person the person to cover
   * @param time   the current time to consider
   * @return the insurance that this person gets
   */
  private void updateInsurance(Person person, long time) {
    InsurancePlan newPlan = PayerManager.findPlan(person, null, time);
    InsurancePlan secondaryPlan = PayerManager.getNoInsurancePlan();
    // If the payer is Medicare, they may buy supplemental insurance.
    if (newPlan.mayPurchaseSupplement() && (person.rand() <= 0.9)) {
      // 9 out of 10 Medicare patients have supplemental insurance, buy some if affordable.
      // https://www.kff.org/medicare/issue-brief/a-snapshot-of-sources-of-coverage-among-medicare-beneficiaries-in-2018/
      secondaryPlan = PayerManager.findMedicareSupplement(person, null, time);
    }
    // Set the person's new plan(s).
    person.coverage.setPlanAtTime(time, newPlan, secondaryPlan);
    // Update the new Payer's customer statistics.
    String personId = (String) person.attributes.get(Person.ID);
    newPlan.incrementCustomers(personId);
    if (!secondaryPlan.isNoInsurance()) {
      secondaryPlan.incrementCustomers(personId);
    }
  }

  /**
   * Populate the given attribute map with the list of attributes that this module
   * reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Inventory> attributes) {
    String m = HealthInsuranceModule.class.getSimpleName();
    Attributes.inventory(attributes, m, "pregnant", true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.BLINDNESS, true, false, "Boolean");
    Attributes.inventory(attributes, m, "end_stage_renal_disease", true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "F");
    Attributes.inventory(attributes, m, Person.OCCUPATION_LEVEL, true, false, "Low");
    Attributes.inventory(attributes, m, Person.INCOME, true, false, "1.0");
  }
}