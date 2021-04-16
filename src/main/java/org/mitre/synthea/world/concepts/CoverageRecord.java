package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;

public class CoverageRecord implements Serializable {
  private static final long serialVersionUID = 771457063723016307L;

  public static class Plan implements Serializable {
    private static final long serialVersionUID = -547445624583743525L;

    public long start;
    public long stop;
    public Payer payer;
    public String owner;
    public Double totalExpenses;
    public Double totalCoverage;
    public Double remainingDeductible;

    /**
     * Create a new Plan with the given Payer.
     * @param time The time the plan starts.
     * @param payer The payer associated with the Plan.
     */
    public Plan(long time, Payer payer) {
      this.start = time;
      this.stop = time + Utilities.convertTime("years", 1);
      this.payer = payer;
      this.totalExpenses = 0.0;
      this.totalCoverage = 0.0;
      this.remainingDeductible = payer.getDeductible();
    }
  }

  private Person person;
  private List<Plan> planHistory;

  /**
   * Create a new CoverageRecord for the given Person.
   * @param person The person.
   */
  public CoverageRecord(Person person) {
    this.person = person;
    this.planHistory = new ArrayList<Plan>();
  }

  /**
   * Get the person associated with this CoverageRecord.
   * @return the person.
   */
  public Person getPerson() {
    return person;
  }

  /**
   * Get the plan history associated with this CoverageRecord.
   * @return the play history.
   */
  public List<Plan> getPlanHistory() {
    return planHistory;
  }

  /**
   * Sets the person's payer history at the given time to the given payer.
   */
  public void setPayerAtTime(long time, Payer newPayer) {
    if (!this.planHistory.isEmpty()) {
      this.planHistory.get(this.planHistory.size() - 1).stop = time;
    }
    Plan plan = new Plan(time, newPayer);
    plan.owner = determinePayerOwnership(time, newPayer);
    this.planHistory.add(plan);
  }

  /**
   * Get the Plan active at a given time.
   * @param time the time.
   * @return the active plan.
   */
  public Plan getPlanAtTime(long time) {
    Plan plan = null;
    for (Plan p : this.planHistory) {
      if (p.start <= time && time < p.stop) {
        plan = p;
      }
    }
    return plan;
  }

  /**
   * Returns the person's Payer at the given time.
   */
  public Payer getPayerAtTime(long time) {
    Payer payer = null;
    Plan plan = getPlanAtTime(time);
    if (plan != null) {
      payer = plan.payer;
    }
    return payer;
  }

  /**
   * Get the last plan.
   * @return the last plan.
   */
  public Plan getLastPlan() {
    Plan plan = null;
    if (!this.planHistory.isEmpty()) {
      plan = this.planHistory.get(this.planHistory.size() - 1);
    }
    return plan;
  }

  /**
   * Get the payer associated with the last plan.
   * @return the payer associated with the last plan.
   */
  public Payer getLastPayer() {
    Payer payer = null;
    Plan plan = getLastPlan();
    if (plan != null) {
      payer = plan.payer;
    }
    return payer;
  }

  /**
   * Returns the owner of the person's payer at the given time.
   */
  public String getPlanOwner(long time) {
    String owner = null;
    Plan plan = getPlanAtTime(time);
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
    for (Plan plan : planHistory) {
      total += plan.totalExpenses;
    }
    return total;
  }

  /**
   * Returns the total healthcare coverage for this person.
   */
  public double getTotalCoverage() {
    double total = 0;
    for (Plan plan : planHistory) {
      total += plan.totalCoverage;
    }
    return total;
  }

  /**
   * Determines and returns what the ownership of the person's insurance at this age.
   */
  private String determinePayerOwnership(long time, Payer payer) {
    // Keep previous year's ownership if payer is unchanged and person has not just turned 18.
    int age = this.person.ageInYears(time);
    Plan lastPlan = this.getLastPlan();
    if (lastPlan != null
        && lastPlan.payer != null
        && lastPlan.payer.equals(payer)
        && age != 18) {
      return lastPlan.owner;
    }
    // No owner for no insurance.
    if (payer.equals(Payer.noInsurance)) {
      return "";
    }
    // Standard payer ownership check.
    if (age < 18 && !payer.getName().equals("Medicaid")) {
      // If a person is a minor, their Guardian owns their health plan unless it is Medicaid.
      return "Guardian";
    } else if ((person.attributes.containsKey(Person.MARITAL_STATUS))
        && person.attributes.get(Person.MARITAL_STATUS).equals("M")) {
      // TODO: ownership shouldn't be a coin toss every year
      // If a person is married, there is a 50% chance their spouse owns their insurance.
      if (person.rand(0.0, 1.0) < .5) {
        return "Spouse";
      }
    }
    // If a person is unmarried and over 18, they own their insurance.
    return "Self";
  }
}
