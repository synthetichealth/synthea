package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.healthinsurance.CoverageRecord.PlanRecord;

public class Claim implements Serializable {
  private static final long serialVersionUID = -3565704321813987656L;

  public class ClaimEntry implements Serializable {
    private static final long serialVersionUID = 1871121895630816723L;
    @JSONSkip
    public Entry entry;
    /** total cost of the entry. */
    public double cost;
    /** copay paid by patient. */
    public double copay;
    /** deductible paid by patient. */
    public double paidByDeductible;
    /** amount the charge was decreased by payer adjustment. */
    public double adjustment;
    /** coinsurance paid by payer. */
    public double coinsurancePaidByPayer;
    /** coinsurance paid by payer. */
    public double coinsurancePaidByPatient;
    /** otherwise paid by payer. */
    public double paidByPayer;
    /** otherwise paid by secondary payer. */
    public double paidBySecondaryPayer;
    /** otherwise paid by patient out of pocket. */
    public double paidByPatient;

    public ClaimEntry(Entry entry) {
      this.entry = entry;
    }

    void assignCosts(PlanRecord planRecord) {
      this.cost = this.entry.getCost().doubleValue();

      if (!plan.coversService(this.entry)) {
        plan.incrementUncoveredEntries(this.entry);
        // Plan does not cover care
        this.paidByPatient = this.cost;
        return;
      }

      plan.incrementCoveredEntries(this.entry);
      // Check if the plan has a cost adjustment
      double adjustment = plan.adjustClaim(this, person);
      final double adjustedCost = cost - adjustment;
      // Check if the patient has remaining deductible (if the plan is deductible-based)
      if (planRecord.remainingDeductible < 1 && planRecord.plan.getDeductible() > 0) {
        this.paidByDeductible = adjustedCost;
        return;
      }
      // Apply copay to Encounters and Medication claims only
      if (plan.isCopayBased() && ((this.entry instanceof HealthRecord.Encounter)
          || (this.entry instanceof HealthRecord.Medication))) {
        double copay = plan.determineCopay(this.entry);
        this.copay = copay;
        this.paidByPatient += copay;
        this.paidByPayer += (adjustedCost - copay);
        planRecord.remainingDeductible -= copay;
        return;
      }
      // Check if the patient has coinsurance to pay.
      double patientCoinsurance = plan.getPatientCoinsurance();
      if (patientCoinsurance > 0.0) {
        double payerCoinsurance = plan.getPayerCoinsurance();
        double coinsurancePatientToPay = (adjustedCost * patientCoinsurance);
        // If the person has secondary insurance, they cover the coinusurance.
        if (!planRecord.secondaryPlan.getPayer().isNoInsurance()) {
          this.paidBySecondaryPayer = coinsurancePatientToPay;
          coinsurancePatientToPay = 0;
        }
        this.paidByPatient += coinsurancePatientToPay;
        this.coinsurancePaidByPatient += coinsurancePatientToPay;
        double coinsurancePayerToPay = (adjustedCost * payerCoinsurance);
        this.paidByPayer += coinsurancePayerToPay;
        this.coinsurancePaidByPayer = coinsurancePayerToPay;
        planRecord.remainingDeductible -= coinsurancePatientToPay;
        return;
      }
      // Since the patient has not been covered up to this point, they incur the total cost.
      this.paidByPatient += adjustedCost;
      planRecord.remainingDeductible -= this.paidByPatient;
    }

    /**
     * Add the costs from the other entry to this one.
     * @param other the other claim entry.
     */
    public void addCosts(ClaimEntry other) {
      this.cost += other.cost;
      this.copay += other.copay;
      this.paidByDeductible += other.paidByDeductible;
      this.adjustment += other.adjustment;
      this.coinsurancePaidByPayer += other.coinsurancePaidByPayer;
      this.paidByPayer += other.paidByPayer;
      this.paidBySecondaryPayer += other.paidBySecondaryPayer;
      this.paidByPatient += other.paidByPatient;
    }

    /**
     * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
     * of pocket.
     * @return the amount of coinsurance paid
     */
    public double getCoinsurancePaid() {
      return this.coinsurancePaidByPatient;
    }
  }

  public InsurancePlan plan;
  public InsurancePlan secondaryPlan;
  @JSONSkip
  public Person person;
  public ClaimEntry mainEntry;
  public List<ClaimEntry> items;
  public ClaimEntry totals;

  /**
   * Constructor of a Claim for an Entry.
   */
  public Claim(Entry entry, Person person) {
    // Set the Entry.
    if ((entry instanceof Encounter) || (entry instanceof Medication)) {
      this.mainEntry = new ClaimEntry(entry);
    } else {
      throw new RuntimeException(
          "A Claim can only be made with entry types Encounter or Medication.");
    }
    // Set the Person.
    this.person = person;
    // Set the Payer(s)
    PlanRecord planRecord = this.person.coverage.getPlanRecordAtTime(entry.start);
    if (planRecord != null) {
      this.plan = planRecord.plan;
      this.secondaryPlan = planRecord.secondaryPlan;
    }
    if (this.plan == null) {
      // This can rarely occur when an death certification encounter
      // occurs on the birthday or immediately afterwards before a new
      // insurance plan is selected.
      this.plan = this.person.coverage.getLastInsurancePlan();
      if (this.plan == null) {
        this.plan = PayerManager.getNoInsurancePlan();
      }
    }
    this.items = new ArrayList<ClaimEntry>();
    this.totals = new ClaimEntry(entry);
  }

  /**
   * Adds non-explicit costs to the Claim. (Procedures/Immunizations/etc).
   */
  public void addLineItem(Entry entry) {
    ClaimEntry claimEntry = new ClaimEntry(entry);
    this.items.add(claimEntry);
  }

  /**
   * Assign costs between the payer and patient.
   */
  public void assignCosts() {
    PlanRecord planRecord = person.coverage.getPlanRecordAtTime(mainEntry.entry.start);
    if (planRecord == null) {
      planRecord = person.coverage.getLastPlanRecord();
      if (planRecord == null) {
        person.coverage.setPlanAtTime(mainEntry.entry.start, PayerManager.getNoInsurancePlan());
        planRecord = person.coverage.getLastPlanRecord();
      }
    }
    mainEntry.assignCosts(planRecord);
    totals = new ClaimEntry(mainEntry.entry);
    totals.addCosts(mainEntry);
    for (ClaimEntry item : items) {
      item.assignCosts(planRecord);
      totals.addCosts(item);
    }

    double uncoveredCosts = totals.paidByPatient;
    planRecord.incrementExpenses(uncoveredCosts);
    planRecord.incrementCoverage(totals.paidByPayer + totals.paidBySecondaryPayer);
    planRecord.plan.addCoveredCost(totals.paidByPayer);
    planRecord.plan.addUncoveredCost(uncoveredCosts);
    planRecord.secondaryPlan.addCoveredCost(totals.paidBySecondaryPayer);
  }

  /**
   * Returns the total cost of the Claim, including immunizations/procedures tied to the encounter.
   */
  public double getTotalClaimCost() {
    return this.totals.cost;
  }

  /**
   * Returns the total cost that the Payer covered for this claim.
   */
  public double getCoveredCost() {
    return (this.totals.coinsurancePaidByPayer + this.totals.paidByPayer);
  }

  public double getDeductiblePaid() {
    return this.totals.paidByDeductible;
  }

  public double getCopayPaid() {
    return this.totals.copay;
  }

  /**
   * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
   * of pocket.
   * @return the amount of coinsurance paid
   */
  public double getCoinsurancePaid() {
    double paid = this.mainEntry.getCoinsurancePaid();
    paid += this.items.stream().mapToDouble(item -> item.getCoinsurancePaid()).sum();
    return paid;
  }

  /**
   * Returns the total cost to the patient, including copay, coinsurance, and deductible.
   */
  public double getPatientCost() {
    return this.totals.paidByPatient;
  }
}