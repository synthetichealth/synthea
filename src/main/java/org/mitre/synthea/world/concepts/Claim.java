package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.CoverageRecord.Plan;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

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
    public double payer;
    /** otherwise paid by secondary payer. */
    public double secondaryPayer;
    /** otherwise paid by patient out of pocket. */
    public double pocket;

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
      this.payer += other.payer;
      this.secondaryPayer += other.secondaryPayer;
      this.pocket += other.pocket;
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
    Plan plan = this.person.coverage.getPlanAtTime(entry.start);
    if (plan != null) {
      this.payer = plan.payer;
      this.secondaryPayer = plan.secondaryPayer;
    }
    if (this.payer == null) {
      // This can rarely occur when an death certification encounter
      // occurs on the birthday or immediately afterwards before a new
      // insurance plan is selected.
      this.payer = this.person.coverage.getLastPayer();
    }
    if (this.payer == null) {
      this.payer = Payer.noInsurance;
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
    Plan plan = person.coverage.getPlanAtTime(mainEntry.entry.start);
    if (plan == null) {
      plan = person.coverage.getLastPlan();
    }
    if (plan == null) {
      person.coverage.setPayerAtTime(mainEntry.entry.start,
          Payer.noInsurance);
      plan = person.coverage.getLastPlan();
    }
    assignCosts(mainEntry, plan);
    totals = new ClaimEntry(mainEntry.entry);
    totals.addCosts(mainEntry);
    for (ClaimEntry item : items) {
      assignCosts(item, plan);
      totals.addCosts(item);
    }
    plan.totalExpenses += (totals.copay + totals.deductible + totals.pocket);
    plan.totalCoverage += (totals.coinsurance + totals.payer + totals.secondaryPayer);
    plan.payer.addCoveredCost(totals.coinsurance);
    plan.payer.addCoveredCost(totals.payer);
    plan.payer.addUncoveredCost(totals.copay);
    plan.payer.addUncoveredCost(totals.deductible);
    plan.payer.addUncoveredCost(totals.pocket);
    plan.secondaryPayer.addCoveredCost(totals.secondaryPayer);
  }

  private void assignCosts(ClaimEntry claimEntry, Plan plan) {
    claimEntry.cost = claimEntry.entry.getCost().doubleValue();
    double remaining = claimEntry.cost;
    if (payer.coversCare(claimEntry.entry)) {
      payer.incrementCoveredEntries(claimEntry.entry);
      // Apply copay to Encounters and Medication claims only
      if ((claimEntry.entry instanceof HealthRecord.Encounter)
          || (claimEntry.entry instanceof HealthRecord.Medication)) {
        claimEntry.copay = payer.determineCopay(claimEntry.entry);
        remaining -= claimEntry.copay;
      }
      // Check if the patient has remaining deductible
      if (remaining > 0 && plan.remainingDeductible > 0) {
        if (plan.remainingDeductible >= remaining) {
          claimEntry.deductible = remaining;
        } else {
          claimEntry.deductible = plan.remainingDeductible;
        }
        remaining -= claimEntry.deductible;
        plan.remainingDeductible -= claimEntry.deductible;
      }
      if (remaining > 0) {
        // Check if the payer has an adjustment
        double adjustment = payer.adjustClaim(claimEntry, person);
        remaining -= adjustment;
      }
      if (remaining > 0) {
        // Check if the patient has coinsurance
        double coinsurance = payer.getCoinsurance();
        if (coinsurance > 0) {
          // Payer covers some
          claimEntry.coinsurance = (coinsurance * remaining);
          remaining -= claimEntry.coinsurance;
        } else {
          // Payer covers all
          claimEntry.payer = remaining;
          remaining -= claimEntry.payer;
        }
      }
      if (remaining > 0) {
        // If secondary insurance, payer covers remainder, not patient.
        if (Payer.noInsurance != plan.secondaryPayer) {
          claimEntry.secondaryPayer = remaining;
          remaining -= claimEntry.secondaryPayer;
        }
      }
      if (remaining > 0) {
        // Patient amount
        claimEntry.pocket = remaining;
        remaining -= claimEntry.pocket;
      }
    } else {
      payer.incrementUncoveredEntries(claimEntry.entry);
      // Payer does not cover care
      claimEntry.pocket = remaining;
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
    return (this.totals.coinsurance + this.totals.payer);
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
      return this.totals.pocket; 
    }
    return 0;
  }

  /**
   * Returns the total cost to the patient, including copay, coinsurance, and deductible.
   */
  public double getPatientCost() {
    return this.totals.pocket + this.totals.copay + this.totals.deductible;
  }
}