package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;

/**
 * A class that manages a history of coverage.
 */
public class CoverageRecord implements Serializable {
  private static final long serialVersionUID = 771457063723016307L;

  @JSONSkip
  private Person person;
  private Set<PlanRecord> planHistory;
  private Long nextEnrollmentPeriod = null;

  /**
   * Create a new CoverageRecord for the given Person.
   * @param person The person.
   */
  public CoverageRecord(Person person) {
    this.person = person;
    this.planHistory = new HashSet<PlanRecord>();
  }

  /**
   * Get the plan history associated with this CoverageRecord.
   * @return the play history.
   */
  public Set<PlanRecord> getPlanHistory() {
    return planHistory;
  }

  /**
   * Sets the person's payer history at the given time to the given payers.
   * @param time the current simulation time.
   * @param newPlan the primary InsurancePlan.
   * @param secondaryPlan the secondary InsurancePlan (i.e. Medicare Supplemental Insurance).
   */
  public void setPlanAtTime(long time, InsurancePlan newPlan, InsurancePlan secondaryPlan) {
    if (this.planHistory.isEmpty()) {
      if (person.ageInDecimalYears(time) >= 1.0) {
        throw new RuntimeException("Person was greater than the age"
            + " of 1 when recieving their initial insurance plan.");
      }
      // If this is the person's first plan, set the start date to their birthdate.
      time = (long) person.attributes.get(Person.BIRTHDATE);
      this.newEnrollmentPeriod(time);
    } else {
      // Set the new stop date of the last insurance plan to prevent any gaps.
      PlanRecord planRecord = this.getLastPlanRecord();
      planRecord.updateStopTime(time);
    }

    PlanRecord planRecord = new PlanRecord(time, newPlan, this.nextEnrollmentPeriod);
    planRecord.setSecondaryPlan(secondaryPlan);
    PlanRecord prevPlanRecord = null;
    if (!this.planHistory.isEmpty()) {
      prevPlanRecord = this.getLastPlanRecord();
    }
    planRecord.determinePlanOwnership(time, person, prevPlanRecord);
    this.planHistory.add(planRecord);
    // Set the person's insurance status.
    person.attributes.put(Person.INSURANCE_STATUS, newPlan.getInsuranceStatus());
  }

  /**
   * Sets the person's payer history at the given time to become no insurance.
   * @param time the current simulation time.
   */
  public void setPlanToNoInsurance(long time) {
    this.setPlanAtTime(time, PayerManager.getNoInsurancePlan(), PayerManager.getNoInsurancePlan());
  }

  /**
   * Get the Plan active at a given time.
   * @param time the time.
   * @return the active plan.
   */
  public PlanRecord getPlanRecordAtTime(long time) {
    for (PlanRecord planRecord : this.planHistory) {
      if (planRecord.getStartTime() <= time && time < planRecord.getStopTime()) {
        return planRecord;
      }
    }
    throw new RuntimeException("Person does not have insurance at time " + time + ".");
  }

  /**
   * Returns this coverage record history's record of the plan at the given time.
   * @param time  The time to get the person's plan at.
   * @return  The InsurancePlan at the given time.
   */
  public InsurancePlan getPlanAtTime(long time) {
    return getPlanRecordAtTime(time).getPlan();
  }

  /**
   * Get the last plan record.
   * @return the last plan.
   */
  public PlanRecord getLastPlanRecord() {
    if (this.planHistory.isEmpty()) {
      throw new RuntimeException("Invalid attempt to get previous insurance plan for a"
          + " patient with no insurance history.");
    }
    return this.planHistory.stream()
        .max((plan1, plan2) -> Long.compare(plan1.getStopTime(), plan2.getStopTime())).get();
  }

  /**
   * Determines whether the person should enter an enrollment period and search for a new insurance
   * plan. If so, the next enrollment period will be accordingly updated.
   */
  public boolean newEnrollmentPeriod(long time) {
    if (this.nextEnrollmentPeriod == null) {
      // Initialize enrollment.
      this.nextEnrollmentPeriod = (long) person.attributes.get(Person.BIRTHDATE);
    }
    if (time >= nextEnrollmentPeriod) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(nextEnrollmentPeriod);
      c.add(Calendar.YEAR, 1);
      nextEnrollmentPeriod = c.getTimeInMillis();
      return true;
    }
    return false;
  }

  /**
   * Returns the total healthcare expenses for this person.
   * Does not include premium costs.
   * @return The healthcare expenses.
   */
  public BigDecimal getTotalOutOfPocketExpenses() {
    BigDecimal total = BigDecimal.ZERO;
    for (PlanRecord planRecord : planHistory) {
      total = total.add(planRecord.getOutOfPocketExpenses());
    }
    return total;
  }

  /**
   * Returns the total premium expenses.
   * Does not include healthcare expenses.
   * @return  The premium expenses.
   */
  public BigDecimal getTotalPremiumExpenses() {
    BigDecimal total = Claim.ZERO_CENTS;
    for (PlanRecord plan : planHistory) {
      total = total.add(plan.getInsuranceExpenses());
    }
    return total;
  }

  /**
   * Returns the total healthcare coverage for this person.
   * @return  The healthcare coverage.
   */
  public BigDecimal getTotalCoverage() {
    BigDecimal total = Claim.ZERO_CENTS;
    for (PlanRecord planRecord : planHistory) {
      total = total.add(planRecord.getCoveredExpenses());
    }
    return total;
  }

  /**
   * Returns the amount of income the person has remaining at the given time.
   * @param time  The time to check for.
  * @return  The amount of income the person has remaining.
   */
  public int incomeRemaining(long time) {
    int income = (int) person.attributes.get(Person.INCOME);
    long timestep = Config.getAsLong("generate.timestep");
    long birthDate = (long) this.person.attributes.get(Person.BIRTHDATE);
    if ((this.planHistory.isEmpty() && time <= (birthDate + timestep))
        || !this.person.alive(time)) {
      // Too young to have incurred any expenses yet or they are dead.
      return income;
    }
    PlanRecord currentPlanRecord = this.getPlanRecordAtTime(time);
    BigDecimal currentYearlyExpenses = BigDecimal.ZERO;
    currentYearlyExpenses = currentYearlyExpenses.add(currentPlanRecord.getOutOfPocketExpenses());
    currentYearlyExpenses = currentYearlyExpenses.add(currentPlanRecord.getInsuranceExpenses());

    return (BigDecimal.valueOf(income).subtract(currentYearlyExpenses)).intValue();
  }

  /**
   * Pay monthly premiums to the payers at this time.
   * @param time  The time to pay the premiums.
   */
  public void payMonthlyPremiumsAtTime(long time, double employerLevel, int income) {
    this.getPlanRecordAtTime(time).payMonthlyPremiums(employerLevel, income);
  }

  /**
   * Update the quality of life score for the most recent plan.
   */
  public void updateLastPayerQols(double qolsForYear) {
    if (!this.planHistory.isEmpty()) {
      this.getLastPlanRecord().getPlan().getPayer().addQols(qolsForYear);
    }
  }
}
