package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;

/**
 * A class that manages a history of coverage.
 */
public class CoverageRecord implements Serializable {
  private static final long serialVersionUID = 771457063723016307L;

  public static class PlanRecord implements Serializable {
    private static final long serialVersionUID = -547445624583743525L;

    public String id;
    public long start;
    public long stop;
    public InsurancePlan plan;
    public InsurancePlan secondaryPlan;
    public String owner;
    public String ownerName;
    private double healthcareExpenses;
    private double coveredExpenses;
    private double insuranceCosts;
    public double remainingDeductible;

    /**
     * Create a new Plan with the given Payer.
     * @param time The time the plan starts.
     * @param plan The plan associated with the PlanRecord.
     */
    public PlanRecord(long time, InsurancePlan plan) {
      this.start = time;
      this.stop = time + Utilities.convertTime("years", 1);
      this.plan = plan;
      this.healthcareExpenses = 0.0;
      this.coveredExpenses = 0.0;
      this.insuranceCosts = 0.0;
      this.remainingDeductible = plan.getDeductible();
    }

    /**
     * Pay monthly premiums associated with this plan.
     * @return  Cost of the premiums.
     */
    public double payMonthlyPremiums() {
      double premiumPrice = (this.plan.payMonthlyPremium())
          + (this.secondaryPlan.payMonthlyPremium());
      this.insuranceCosts += premiumPrice;
      return premiumPrice;
    }

    public void updateStopTime(long updatedStopTime) {
      this.stop = updatedStopTime;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("[PlanRecord:");
      sb.append(" Start: " + start);
      sb.append(" Stop: " + stop + "]");
      return sb.toString();
    }

    public void incrementExpenses(double expenses) {
      this.healthcareExpenses += expenses;
    }

    public void incrementCoverage(double coverage) {
      this.coveredExpenses += coverage;
    }

    public double getHealthcareExpenses() {
      return this.healthcareExpenses;
    }

    public double getCoveredExpenses() {
      return this.coveredExpenses;
    }

    public double getInsuranceCosts() {
      return this.insuranceCosts;
    }
  }

  @JSONSkip
  private Person person;
  private List<PlanRecord> planHistory;
  private String insuranceStatus;

  /**
   * Create a new CoverageRecord for the given Person.
   * @param person The person.
   */
  public CoverageRecord(Person person) {
    this.person = person;
    this.planHistory = new ArrayList<PlanRecord>();
  }

  /**
   * Get the plan history associated with this CoverageRecord.
   * @return the play history.
   */
  public List<PlanRecord> getPlanHistory() {
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
      if (person.age(time).getYears() > Utilities.convertTime("years", 1)) {
        throw new RuntimeException("Person was greater than the age"
            + " of 1 when recieving their initial insurance plan.");
      }
      // If this is the person's first plan, set the start date to their birthdate.
      time = (long) person.attributes.get(Person.BIRTHDATE);
    } else {
      // Set the new stop date of the last insurance plan to prevent any gaps.
      PlanRecord planRecord = this.getLastPlanRecord();
      planRecord.updateStopTime(time);
    }

    PlanRecord planRecord = new PlanRecord(time, newPlan);
    planRecord.secondaryPlan = secondaryPlan;
    String[] ownership = determinePlanOwnership(time, newPlan);
    planRecord.owner = ownership[0];
    planRecord.ownerName = ownership[1];
    if (ownership[0] == null) {
      planRecord.id = null; // no insurance, no id.
    } else if (ownership[2] != null) {
      planRecord.id = ownership[2]; // use previous id.
    } else {
      planRecord.id = person.randUUID().toString(); // new id required.
    }
    this.planHistory.add(planRecord);
    // Set the person's insurance status.
    this.insuranceStatus = newPlan.getAssociatedInsuranceStatus();
  }

  /**
   * Sets the person's payer history at the given time to the given payer.
   * @param time the current simulation time.
   * @param newPlan the primary plan.
   */
  public void setPlanAtTime(long time, InsurancePlan newPlan) {
    this.setPlanAtTime(time, newPlan, PayerManager.getNoInsurancePlan());
  }

  /**
   * Get the Plan active at a given time.
   * @param time the time.
   * @return the active plan.
   */
  public PlanRecord getPlanRecordAtTime(long time) {
    for (PlanRecord planRecord : this.planHistory) {
      if (planRecord.start <= time && time < planRecord.stop) {
        return planRecord;
      }
    }
    return null;
  }

  /**
   * Returns this coverage record history's record of the plan at the given time.
   * @param time  The time to get the person's plan at.
   * @return  The InsurancePlan at the given time.
   */
  public InsurancePlan getPlanAtTime(long time) {
    PlanRecord planRecord = getPlanRecordAtTime(time);
    if (planRecord != null) {
      return planRecord.plan;
    }
    return null;
  }

  /**
   * Get the last plan record.
   * @return the last plan.
   */
  public PlanRecord getLastPlanRecord() {
    if (!this.planHistory.isEmpty()) {
      return this.planHistory.get(this.planHistory.size() - 1);
    }
    return null;
  }

  /**
   * Get the last insurance plan.
   * @return the last plan.
   */
  public InsurancePlan getLastInsurancePlan() {
    PlanRecord planRecord = this.getLastPlanRecord();
    if (planRecord != null) {
      return planRecord.plan;
    }
    return null;
  }

  /**
   * Get the payer associated with the last plan.
   * @return the payer associated with the last plan.
   */
  public Payer getLastPayer() {
    Payer payer = null;
    InsurancePlan plan = getLastInsurancePlan();
    if (plan != null) {
      payer = plan.getPayer();
    }
    return payer;
  }

  /**
   * Returns the total healthcare expenses for this person.
   */
  public double getTotalHealthcareExpenses() {
    double total = 0;
    for (PlanRecord plan : planHistory) {
      total += plan.healthcareExpenses;
    }
    return total;
  }

  public double getTotalPremiumExpenses() {
    double total = 0;
    for (PlanRecord plan : planHistory) {
      total += plan.insuranceCosts;
    }
    return total;
  }

  /**
   * Returns the total healthcare coverage for this person.
   */
  public double getTotalCoverage() {
    double total = 0;
    for (PlanRecord plan : planHistory) {
      total += plan.coveredExpenses;
    }
    return total;
  }

  /**
   * Determines and returns what the ownership of the person's insurance at this age.
   */
  private String[] determinePlanOwnership(long time, InsurancePlan newPlan) {
    // TODO - Refactor this logic using Payer inheritance.
    Payer payer = newPlan.getPayer();

    String[] ownerships = new String[3];
    // Keep previous year's ownership if payer is unchanged and person has not just turned 18.
    int age = this.person.ageInYears(time);
    PlanRecord currentPlan = this.getPlanRecordAtTime(time);
    if (currentPlan == null) {
      currentPlan = this.getLastPlanRecord();
    }
    if (currentPlan != null
        && currentPlan.plan != null
        && currentPlan.plan.equals(newPlan)
        && age != 18) {
      ownerships[0] = currentPlan.owner;
      ownerships[1] = currentPlan.ownerName;
      ownerships[2] = currentPlan.id;
    } else if (payer.equals(PayerManager.noInsurance)) {
      // No owner for no insurance.
      ownerships[0] = null;
      ownerships[1] = null;
    } else if (age < 18 && payer.getName().equals(PayerManager.MEDICAID)) {
      // If a person is a minor and is on Medicaid, they own their own insurance.
      ownerships[0] = "Self";
      ownerships[1] = (String) person.attributes.get(Person.NAME);
    } else if (age < 18) {
      // If a person is a minor, their Guardian owns their health plan unless it is Medicaid.
      ownerships[0] = "Guardian";
      if (person.randBoolean()) {
        ownerships[1] = (String) person.attributes.get(Person.NAME_MOTHER);
      } else {
        ownerships[1] = (String) person.attributes.get(Person.NAME_FATHER);
      }
    } else if ((person.attributes.containsKey(Person.MARITAL_STATUS))
        && person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
      // If a person is married, there is a 50% chance their spouse owns their insurance.
      if (person.randBoolean()) {
        ownerships[0] = "Spouse";
        if ("homosexual".equals(person.attributes.get(Person.SEXUAL_ORIENTATION))) {
          if ("M".equals(person.attributes.get(Person.GENDER))) {
            ownerships[1] = "Mr. ";
          } else {
            ownerships[1] = "Mrs. ";
          }
        } else {
          if ("M".equals(person.attributes.get(Person.GENDER))) {
            ownerships[1] = "Mrs. ";
          } else {
            ownerships[1] = "Mr. ";
          }
        }
        ownerships[1] += (String) person.attributes.get(Person.LAST_NAME);
      } else {
        ownerships[0] = "Self";
        ownerships[1] = (String) person.attributes.get(Person.NAME);
      }
    } else {
      // If a person is unmarried and over 18, they own their insurance.
      ownerships[0] = "Self";
      ownerships[1] = (String) person.attributes.get(Person.NAME);
    }
    return ownerships;
  }

  /**
   * Determines whether the given income can afford the coverage based on its expenses.
   * @param yearlyIncome  The yearly income.
   * @param time  The time to check for.
   * @return
   */
  public boolean canIncomeAffordExpenses(int yearlyIncome, long time) {
    CoverageRecord.PlanRecord planRecord = this.getPlanRecordAtTime(time);
    double currentYearlyExpenses;
    if (planRecord != null) {
      currentYearlyExpenses = planRecord.healthcareExpenses;
    } else {
      currentYearlyExpenses = 0.0;
    }
    return (yearlyIncome - currentYearlyExpenses) > 0;
  }
}
