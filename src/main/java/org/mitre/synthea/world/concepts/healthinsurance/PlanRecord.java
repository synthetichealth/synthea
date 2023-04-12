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

  public String id;
  private final long start;
  private long stop;
  private InsurancePlan plan;
  private InsurancePlan secondaryPlan;
  public String ownership;
  public String ownerName;
  private BigDecimal coveredExpenses = Claim.ZERO_CENTS;
  public BigDecimal remainingDeductible = Claim.ZERO_CENTS;
  // Any healthcare expenses not covered by insurance and paid, out of pocket, by the patient.
  private BigDecimal outOfPocketExpenses = Claim.ZERO_CENTS;
  // The expenses associated with having an insurance plan: Premiums.
  private BigDecimal insuranceExpenses = Claim.ZERO_CENTS;

  /**
   * Create a new Plan with the given Payer.
   * @param time The time the plan starts.
   * @param plan The plan associated with the PlanRecord.
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
   * @return  Cost of the premiums.
   */
  public BigDecimal payMonthlyPremiums(double employerLevel, int income) {
    BigDecimal premiumPaid = (this.plan.payMonthlyPremium(employerLevel, income))
        .add(this.secondaryPlan.payMonthlyPremium(employerLevel, income));
    this.insuranceExpenses = this.insuranceExpenses.add(premiumPaid);
    return premiumPaid;
  }

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

  public void incrementOutOfPocketExpenses(BigDecimal expenses) {
    this.outOfPocketExpenses = this.outOfPocketExpenses.add(expenses);
    this.plan.addUncoveredCost(expenses);
  }

  public void incrementPrimaryCoverage(BigDecimal coverage) {
    this.coveredExpenses = this.coveredExpenses.add(coverage);
    this.plan.addCoveredCost(coverage);
  }

  public void incrementSecondaryCoverage(BigDecimal coverage) {
    this.coveredExpenses = this.coveredExpenses.add(coverage);
    this.secondaryPlan.addCoveredCost(coverage);
  }

  public BigDecimal getOutOfPocketExpenses() {
    return this.outOfPocketExpenses;
  }

  public BigDecimal getCoveredExpenses() {
    return this.coveredExpenses;
  }

  public BigDecimal getInsuranceExpenses() {
    return this.insuranceExpenses;
  }

  public InsurancePlan getPlan() {
    return this.plan;
  }

  /**
   * Determines and returns what the ownership of the person's insurance at this age.
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

  public long getStartTime() {
    return this.start;
  }

  public long getStopTime() {
    return this.stop;
  }

  public void setSecondaryPlan(InsurancePlan secondaryPlan) {
    this.secondaryPlan = secondaryPlan;
  }

  public InsurancePlan getSecondaryPlan() {
    return this.secondaryPlan;
  }
}