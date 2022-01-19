package org.mitre.synthea.world.concepts.health_insurance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerController;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.health_insurance.CoverageRecord.PlanRecord;

public class Claim implements Serializable {
  private static final long serialVersionUID = -3565704321813987656L;

  public class ClaimEntry implements Serializable {
    private static final long serialVersionUID = 1871121895630816723L;
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
    public double coinsurance;
    /** otherwise paid by payer. */
    public double paidByPayer;
    /** otherwise paid by secondary payer. */
    public double secondaryPayer;
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
      this.coinsurance += other.coinsurance;
      this.paidByPayer += other.paidByPayer;
      this.secondaryPayer += other.secondaryPayer;
      this.paidByPatient += other.paidByPatient;
    }
  }

  public Payer payer;
  public Payer secondaryPayer;
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
      this.payer = planRecord.plan.getPayer();
      this.secondaryPayer = planRecord.secondaryPlan.getPayer();
    }
    if (this.payer == null) {
      // This can rarely occur when an death certification encounter
      // occurs on the birthday or immediately afterwards before a new
      // insurance plan is selected.
      this.payer = this.person.coverage.getLastPayer();
    }
    if (this.payer == null) {
      this.payer = PayerController.noInsurance;
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
      planRecord = person.coverage.getLastPlan();
    }
    if (planRecord == null) {
      person.coverage.setPlanAtTime(mainEntry.entry.start, PayerController.getNoInsurancePlan());
      planRecord = person.coverage.getLastPlan();
    }
    assignCosts(mainEntry, planRecord);
    totals = new ClaimEntry(mainEntry.entry);
    totals.addCosts(mainEntry);
    for (ClaimEntry item : items) {
      assignCosts(item, planRecord);
      totals.addCosts(item);
    }
    // TODO - This should be refactored to better adhere to OO design principles. Too much getPayer(), redundant calls to the same objects, etc.
    planRecord.incrementExpenses(totals.copay + totals.deductible + totals.paidByPatient);
    planRecord.incrementCoverage(totals.coinsurance + totals.paidByPayer + totals.secondaryPayer);
    double coveredCosts = totals.coinsurance + totals.paidByPayer;
    planRecord.plan.addCoveredCost(coveredCosts);
    double uncoveredCosts = totals.copay + totals.deductible + totals.paidByPatient;
    planRecord.plan.addUncoveredCost(uncoveredCosts);
    planRecord.secondaryPlan.addCoveredCost(totals.secondaryPayer);
  }

  private void assignCosts(ClaimEntry claimEntry, PlanRecord plan) {
    claimEntry.cost = claimEntry.entry.getCost().doubleValue();
    double remainingUnpaid = claimEntry.cost;
    if (payer.coversCare(claimEntry.entry)) {
      payer.incrementCoveredEntries(claimEntry.entry);
      // Apply copay to Encounters and Medication claims only
      if ((claimEntry.entry instanceof HealthRecord.Encounter)
          || (claimEntry.entry instanceof HealthRecord.Medication)) {
        claimEntry.copay = payer.determineCopay(claimEntry.entry);
        remainingUnpaid -= claimEntry.copay;
      }
      // Check if the patient has remaining deductible
      if (remainingUnpaid > 0 && plan.remainingDeductible > 0) {
        if (plan.remainingDeductible >= remainingUnpaid) {
          claimEntry.deductible = remainingUnpaid;
        } else {
          claimEntry.deductible = plan.remainingDeductible;
        }
        remainingUnpaid -= claimEntry.deductible;
        plan.remainingDeductible -= claimEntry.deductible;
      }
      if (remainingUnpaid > 0) {
        // Check if the payer has an adjustment
        double adjustment = payer.adjustClaim(claimEntry, person);
        remainingUnpaid -= adjustment;
      }
      if (remainingUnpaid > 0) {
        // Check if the patient has coinsurance
        double coinsurance = payer.getCoinsurance(person);
        if (coinsurance > 0) {
          // Payer covers some
          claimEntry.coinsurance = (coinsurance * remainingUnpaid);
          remainingUnpaid -= claimEntry.coinsurance;
        } else {
          // Payer covers all
          claimEntry.paidByPayer = remainingUnpaid;
          remainingUnpaid -= claimEntry.paidByPayer;
        }
      }
      if (remainingUnpaid > 0) {
        // If secondary insurance, payer covers remainder, not patient.
        if (PayerController.noInsurance != plan.secondaryPlan.getPayer()) {
          claimEntry.secondaryPayer = remainingUnpaid;
          remainingUnpaid -= claimEntry.secondaryPayer;
        }
      }
      if (remainingUnpaid > 0) {
        // Patient amount
        claimEntry.paidByPatient = remainingUnpaid;
        remainingUnpaid -= claimEntry.paidByPatient;
      }
    } else {
      payer.incrementUncoveredEntries(claimEntry.entry);
      // Payer does not cover care
      claimEntry.paidByPatient = remainingUnpaid;
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
    return (this.totals.coinsurance + this.totals.paidByPayer);
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
    if (this.totals.secondaryPayer > 0) {
      return this.totals.secondaryPayer;
    } else if (this.totals.coinsurance > 0) {
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