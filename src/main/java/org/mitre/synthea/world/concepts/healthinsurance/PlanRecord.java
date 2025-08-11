package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.math.BigDecimal;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;

/**
 * A class that manages a person's record of plan uses and expenses for an enrollment period.
 */
public class PlanRecord implements Serializable {
  private static final long serialVersionUID = -547445624583743525L;

  /** Unique identifier for the plan record. */
  public String id;
  /** Start time of the plan record. */
  private final long start;
  /** Stop time of the plan record. */
  private long stop;
  /** Primary insurance plan associated with the record. */
  private InsurancePlan plan;
  /** Secondary insurance plan associated with the record. */
  private InsurancePlan secondaryPlan;
  /** Ownership type of the insurance plan. */
  public String ownership;
  /** Name of the owner of the insurance plan. */
  public String ownerName;
  /** Covered expenses for the plan record. */
  private BigDecimal coveredExpenses = Claim.ZERO_CENTS;
  /** Remaining deductible for the plan record. */
  public BigDecimal remainingDeductible = Claim.ZERO_CENTS;
  /** Any healthcare expenses not covered by insurance and paid, out of pocket, by the patient. */
  private BigDecimal outOfPocketExpenses = Claim.ZERO_CENTS;
  /** The expenses associated with having an insurance plan: Premiums. */
  private BigDecimal insuranceExpenses = Claim.ZERO_CENTS;

  /**
   * Create a new Plan with the given Payer.
   * @param time The time the plan starts.
   * @param plan The plan associated with the PlanRecord.
   * @param nextEnrollmentPeriod The time of the next enrollment period.
   */
  public PlanRecord(long time, InsurancePlan plan, long nextEnrollmentPeriod) {
    this.start = time;
    // Calendar c = Calendar.getInstance();
    // c.setTimeInMillis(time);
    // c.add(Calendar.YEAR, 1);
    this.stop = nextEnrollmentPeriod;
    this.plan = plan;
    this.remainingDeductible = plan.getDeductible();
  }

  /**
   * Pay monthly premiums associated with this plan.
   * @param employerLevel The employer contribution level.
   * @param income The income of the person.
   * @return Cost of the premiums.
   */
  public BigDecimal payMonthlyPremiums(double employerLevel, int income) {
    BigDecimal premiumPaid = (this.plan.payMonthlyPremium(employerLevel, income))
        .add(this.secondaryPlan.payMonthlyPremium(employerLevel, income));
    this.insuranceExpenses = this.insuranceExpenses.add(premiumPaid);
    return premiumPaid;
  }

  /**
   * Update the stop time of the plan record.
   * @param updatedStopTime The new stop time.
   */
  public void updateStopTime(long updatedStopTime) {
    this.stop = updatedStopTime;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{PlanRecord:");
    sb.append(" Start: " + start);
    sb.append(" Stop: " + stop);
    sb.append(" Payer: " + this.plan.getPayer().getName() + "}");
    return sb.toString();
  }

  /**
   * Increment out-of-pocket expenses for the plan record.
   * @param expenses The expenses to add.
   */
  public void incrementOutOfPocketExpenses(BigDecimal expenses) {
    this.outOfPocketExpenses = this.outOfPocketExpenses.add(expenses);
    this.plan.addUncoveredCost(expenses);
  }

  /**
   * Increment primary coverage expenses for the plan record.
   * @param coverage The coverage amount to add.
   */
  public void incrementPrimaryCoverage(BigDecimal coverage) {
    this.coveredExpenses = this.coveredExpenses.add(coverage);
    this.plan.addCoveredCost(coverage);
  }

  /**
   * Increment secondary coverage expenses for the plan record.
   * @param coverage The coverage amount to add.
   */
  public void incrementSecondaryCoverage(BigDecimal coverage) {
    this.coveredExpenses = this.coveredExpenses.add(coverage);
    this.secondaryPlan.addCoveredCost(coverage);
  }

  /**
   * Get the out-of-pocket expenses for the plan record.
   * @return The out-of-pocket expenses.
   */
  public BigDecimal getOutOfPocketExpenses() {
    return this.outOfPocketExpenses;
  }

  /**
   * Get the covered expenses for the plan record.
   * @return The covered expenses.
   */
  public BigDecimal getCoveredExpenses() {
    return this.coveredExpenses;
  }

  /**
   * Get the insurance expenses for the plan record.
   * @return The insurance expenses.
   */
  public BigDecimal getInsuranceExpenses() {
    return this.insuranceExpenses;
  }

  /**
   * Get the primary insurance plan associated with the record.
   * @return The primary insurance plan.
   */
  public InsurancePlan getPlan() {
    return this.plan;
  }

  /**
   * Determines and returns what the ownership of the person's insurance at this age.
   * @param time The age at which to determine ownership.
   * @param person The person whose insurance ownership is being determined.
   * @param prevRecord The previous insurance record for comparison.
   */
  public void determinePlanOwnership(long time, Person person, PlanRecord prevRecord) {
    Payer payer = plan.getPayer();

    int age = person.ageInYears(time);
    // Keep previous year's ownership if payer is unchanged and person has not just turned 18.
    if (prevRecord != null
        && this.plan.equals(prevRecord.plan)
        && prevRecord.ownership != null
        && prevRecord.ownership.equals("Guardian")
        && age <= 18) {
      this.ownership = prevRecord.ownership;
      this.ownerName = prevRecord.ownerName;
      this.id = prevRecord.id;
      return;
    }
    if (payer.isNoInsurance()) {
      // No owner for no insurance.
      this.ownership = null;
      this.ownerName = null;
      this.id = null;
      return;
    }
    // New Id is required since this is not the same as the previous insurance.
    this.id = person.randUUID().toString();
    if (age < 18 && payer.isGovernmentPayer()) {
      // If a person is a minor and is on government insurance, they own their own insurance.
      this.ownership = "Self";
      this.ownerName = (String) person.attributes.get(Person.NAME);
      return;
    }
    if (age < 18) {
      // If a person is a minor, their Guardian owns their health plan unless it is government.
      this.ownership = "Guardian";
      if (person.randBoolean()) {
        this.ownerName = (String) person.attributes.get(Person.NAME_MOTHER);
      } else {
        this.ownerName = (String) person.attributes.get(Person.NAME_FATHER);
      }
      return;
    }
    if ((person.attributes.containsKey(Person.MARITAL_STATUS))
        && person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
      // If a person is married, there is a 50% chance their spouse owns their insurance.
      if (person.randBoolean()) {
        this.ownership = "Spouse";
        if (person.attributes.get(Person.SEXUAL_ORIENTATION).equals("homosexual")) {
          if ((person.attributes.get(Person.GENDER).equals("M"))) {
            this.ownerName = "Mr. ";
          } else {
            this.ownerName = "Mrs. ";
          }
        } else {
          if ((person.attributes.get(Person.GENDER).equals("M"))) {
            this.ownerName = "Mrs. ";
          } else {
            this.ownerName = "Mr. ";
          }
        }
        this.ownerName += (String) person.attributes.get(Person.LAST_NAME);
      } else {
        this.ownership = "Self";
        this.ownerName = (String) person.attributes.get(Person.NAME);
      }
      return;
    }
    // If a person is unmarried and over 18, they own their insurance.
    this.ownership = "Self";
    this.ownerName = (String) person.attributes.get(Person.NAME);
  }

  /**
   * Get the start time of the plan record.
   * @return The start time.
   */
  public long getStartTime() {
    return this.start;
  }

  /**
   * Get the stop time of the plan record.
   * @return The stop time.
   */
  public long getStopTime() {
    return this.stop;
  }

  /**
   * Set the secondary insurance plan for the record.
   * @param secondaryPlan The secondary insurance plan to set.
   */
  public void setSecondaryPlan(InsurancePlan secondaryPlan) {
    this.secondaryPlan = secondaryPlan;
  }

  /**
   * Get the secondary insurance plan associated with the record.
   * @return The secondary insurance plan.
   */
  public InsurancePlan getSecondaryPlan() {
    return this.secondaryPlan;
  }
}