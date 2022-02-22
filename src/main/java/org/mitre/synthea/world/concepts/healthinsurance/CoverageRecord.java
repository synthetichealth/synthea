package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerController;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

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
    private Double totalExpenses;
    private Double totalCoverage;
    public Double remainingDeductible;

    /**
     * Create a new Plan with the given Payer.
     * @param time The time the plan starts.
     * @param plan The plan associated with the PlanRecord.
     */
    public PlanRecord(long time, InsurancePlan plan) {
      this.start = time;
      this.stop = time + Utilities.convertTime("years", 1);
      this.plan = plan;
      this.totalExpenses = 0.0;
      this.totalCoverage = 0.0;
      this.remainingDeductible = plan.getDeductible();
    }

    /**
     * Pay monthly premiums associated with this plan.
     * @return  Cost of the premiums.
     */
    public double payMonthlyPremiums() {
      double premiumPrice = (this.plan.payMonthlyPremium())
          + (this.secondaryPlan.payMonthlyPremium());
      this.totalExpenses += premiumPrice;
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
      this.totalExpenses += expenses;
    }

    public void incrementCoverage(double coverage) {
      this.totalCoverage += coverage;
    }
  }

  @JSONSkip
  private Person person;
  private List<PlanRecord> planHistory;

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
  }

  /**
   * Sets the person's payer history at the given time to the given payer.
   * @param time the current simulation time.
   * @param newPlan the primary plan.
   */
  public void setPlanAtTime(long time, InsurancePlan newPlan) {
    this.setPlanAtTime(time, newPlan, PayerController.getNoInsurancePlan());
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
   * Returns the owner of the person's payer at the given time.
   */
  public String getPlanOwner(long time) {
    String owner = null;
    PlanRecord plan = getPlanRecordAtTime(time);
    if (plan != null) {
      owner = plan.owner;
    }
    return owner;
  }

  /**
   * Returns the total healthcare expenses for this person.
   */
  public double getTotalExpenses() {
    double total = 0;
    for (PlanRecord plan : planHistory) {
      total += plan.totalExpenses;
    }
    return total;
  }

  /**
   * Returns the total healthcare coverage for this person.
   */
  public double getTotalCoverage() {
    double total = 0;
    for (PlanRecord plan : planHistory) {
      total += plan.totalCoverage;
    }
    return total;
  }

  /**
   * Determines and returns what the ownership of the person's insurance at this age.
   */
  private String[] determinePlanOwnership(long time, InsurancePlan plan) {

    Payer payer = plan.getPayer();

    String[] results = new String[3];
    // Keep previous year's ownership if payer is unchanged and person has not just turned 18.
    int age = this.person.ageInYears(time);
    PlanRecord lastPlan = this.getLastPlanRecord();
    if (lastPlan != null
        && lastPlan.plan != null
        && lastPlan.plan.equals(plan)
        && age != 18) {
      results[0] = lastPlan.owner;
      results[1] = lastPlan.ownerName;
      results[2] = lastPlan.id;
    } else if (payer.equals(PayerController.noInsurance)) {
      // No owner for no insurance.
      results[0] = null;
      results[1] = null;
    } else if (age < 18 && !payer.getName().equals("Medicaid")) {
      // If a person is a minor, their Guardian owns their health plan unless it is Medicaid.
      results[0] = "Guardian";
      if (person.randBoolean()) {
        results[1] = (String) person.attributes.get(Person.NAME_MOTHER);
      } else {
        results[1] = (String) person.attributes.get(Person.NAME_FATHER);
      }
    } else if (age < 18 && payer.getName().equals("Medicaid")) {
      // If a person is a minor and is on Medicaid.
      results[0] = "Self";
      results[1] = (String) person.attributes.get(Person.NAME);
    } else if ((person.attributes.containsKey(Person.MARITAL_STATUS))
        && person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
      // If a person is married, there is a 50% chance their spouse owns their insurance.
      if (person.randBoolean()) {
        results[0] = "Spouse";
        if ("homosexual".equals(person.attributes.get(Person.SEXUAL_ORIENTATION))) {
          if ("M".equals(person.attributes.get(Person.GENDER))) {
            results[1] = "Mr. ";
          } else {
            results[1] = "Mrs. ";
          }
        } else {
          if ("M".equals(person.attributes.get(Person.GENDER))) {
            results[1] = "Mrs. ";
          } else {
            results[1] = "Mr. ";
          }
        }
        results[1] += (String) person.attributes.get(Person.LAST_NAME);
      } else {
        results[0] = "Self";
        results[1] = (String) person.attributes.get(Person.NAME);
      }
    } else {
      // If a person is unmarried and over 18, they own their insurance.
      results[0] = "Self";
      results[1] = (String) person.attributes.get(Person.NAME);
    }
    return results;
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
      currentYearlyExpenses = planRecord.totalExpenses;
    } else {
      currentYearlyExpenses = 0.0;
    }
    return (yearlyIncome - currentYearlyExpenses) > 0;
  }
}
