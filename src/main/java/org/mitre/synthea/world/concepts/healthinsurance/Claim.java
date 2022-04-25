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
    public double deductible;
    /** amount the charge was decreased by payer adjustment. */
    public double adjustment;
    /** coinsurance paid by payer. */
    public double coinsurancePaidByPayer;
    /** otherwise paid by payer. */
    public double paidByPayer;
    /** otherwise paid by secondary payer. */
    public double paidBySecondaryPayer;
    /** otherwise paid by patient out of pocket. */
    public double paidByPatient;

    public ClaimEntry(Entry entry) {
      this.entry = entry;
    }

    /**
     * Add the costs from the other entry to this one.
     * @param other the other claim entry.
     */
    public void addCosts(ClaimEntry other) {
      this.cost += other.cost;
      this.copay += other.copay;
      this.deductible += other.deductible;
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
      if (this.paidBySecondaryPayer > 0) {
        return this.paidBySecondaryPayer;
      } else if (this.coinsurancePaidByPayer > 0) {
        return this.paidByPatient;
      }
      return 0;
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
    assignCosts(mainEntry, planRecord);
    totals = new ClaimEntry(mainEntry.entry);
    totals.addCosts(mainEntry);
    for (ClaimEntry item : items) {
      assignCosts(item, planRecord);
      totals.addCosts(item);
    }

    double uncoveredCosts = totals.copay + totals.deductible + totals.paidByPatient;
    planRecord.incrementExpenses(uncoveredCosts);
    planRecord.incrementCoverage(totals.coinsurancePaidByPayer
        + totals.paidByPayer + totals.paidBySecondaryPayer);
    double coveredCosts = totals.coinsurancePaidByPayer + totals.paidByPayer;
    planRecord.plan.addCoveredCost(coveredCosts);
    planRecord.plan.addUncoveredCost(uncoveredCosts);
    planRecord.secondaryPlan.addCoveredCost(totals.paidBySecondaryPayer);
  }

  private void assignCosts(ClaimEntry claimEntry, PlanRecord planRecord) {
    claimEntry.cost = claimEntry.entry.getCost().doubleValue();
    double remainingUnpaid = claimEntry.cost;

    if (!plan.coversService(claimEntry.entry)) {
      plan.incrementUncoveredEntries(claimEntry.entry);
      // Payer does not cover care
      claimEntry.paidByPatient = remainingUnpaid;
      return;
    }

    plan.incrementCoveredEntries(claimEntry.entry);
    // Apply copay to Encounters and Medication claims only
    if ((claimEntry.entry instanceof HealthRecord.Encounter)
        || (claimEntry.entry instanceof HealthRecord.Medication)) {
      claimEntry.copay = plan.determineCopay(claimEntry.entry);
      remainingUnpaid -= claimEntry.copay;
    }
    // Check if the patient has remaining deductible
    if (remainingUnpaid > 0 && planRecord.remainingDeductible > 0) {
      if (planRecord.remainingDeductible >= remainingUnpaid) {
        claimEntry.deductible = remainingUnpaid;
      } else {
        claimEntry.deductible = planRecord.remainingDeductible;
      }
      remainingUnpaid -= claimEntry.deductible;
      planRecord.remainingDeductible -= claimEntry.deductible;
    }
    if (remainingUnpaid > 0) {
      // Check if the payer has an adjustment
      double adjustment = plan.adjustClaim(claimEntry, person);
      remainingUnpaid -= adjustment;
    }
    if (remainingUnpaid > 0) {
      // Check if the patient has coinsurance
      double coinsurance = plan.getCoinsurance();
      if (coinsurance > 0) {
        // Payer covers some
        claimEntry.coinsurancePaidByPayer = (coinsurance * remainingUnpaid);
        remainingUnpaid -= claimEntry.coinsurancePaidByPayer;
      } else {
        // Payer covers all
        claimEntry.paidByPayer = remainingUnpaid;
        remainingUnpaid -= claimEntry.paidByPayer;
      }
    }
    if (remainingUnpaid > 0) {
      // If secondary insurance, payer covers remainder, not patient.
      if (!planRecord.secondaryPlan.getPayer().isNoInsurance()) {
        claimEntry.paidBySecondaryPayer = remainingUnpaid;
        remainingUnpaid -= claimEntry.paidBySecondaryPayer;
      }
    }
    if (remainingUnpaid > 0) {
      // Patient amount
      claimEntry.paidByPatient = remainingUnpaid;
      remainingUnpaid -= claimEntry.paidByPatient;
    }
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
    return this.totals.deductible;
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
    if (this.totals.paidBySecondaryPayer > 0) {
      return this.totals.paidBySecondaryPayer;
    } else if (this.totals.coinsurancePaidByPayer > 0) {
      return this.totals.paidByPatient;
    }
    return 0;
  }

  /**
   * Returns the total cost to the patient, including copay, coinsurance, and deductible.
   */
  public double getPatientCost() {
    return this.totals.paidByPatient + this.totals.copay + this.totals.deductible;
  }
}