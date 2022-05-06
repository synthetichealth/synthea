package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.CoverageRecord.Plan;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

public class Claim implements Serializable {
  private static final long serialVersionUID = -3565704321813987656L;
  public static final BigDecimal ZERO_CENTS = BigDecimal.ZERO.setScale(2);

  public class ClaimEntry implements Serializable {
    private static final long serialVersionUID = 1871121895630816723L;
    @JSONSkip
    public Entry entry;
    /** total cost of the entry. */
    public BigDecimal cost = ZERO_CENTS;
    /** copay paid by patient. */
    public BigDecimal copay = ZERO_CENTS;
    /** deductible paid by patient. */
    public BigDecimal deductible = ZERO_CENTS;
    /** amount the charge was decreased by payer adjustment. */
    public BigDecimal adjustment = ZERO_CENTS;
    /** coinsurance paid by payer. */
    public BigDecimal coinsurance = ZERO_CENTS;
    /** otherwise paid by payer. */
    public BigDecimal payer = ZERO_CENTS;
    /** otherwise paid by secondary payer. */
    public BigDecimal secondaryPayer = ZERO_CENTS;
    /** otherwise paid by patient out of pocket. */
    public BigDecimal pocket = ZERO_CENTS;

    public ClaimEntry(Entry entry) {
      this.entry = entry;
    }

    /**
     * Add the costs from the other entry to this one.
     * @param other the other claim entry.
     */
    public void addCosts(ClaimEntry other) {
      this.cost = this.cost.add(other.cost);
      this.copay = this.copay.add(other.copay);
      this.deductible = this.deductible.add(other.deductible);
      this.adjustment = this.adjustment.add(other.adjustment);
      this.coinsurance = this.coinsurance.add(other.coinsurance);
      this.payer = this.payer.add(other.payer);
      this.secondaryPayer = this.secondaryPayer.add(other.secondaryPayer);
      this.pocket = this.pocket.add(other.pocket);
    }

    /**
     * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
     * of pocket.
     * @return the amount of coinsurance paid
     */
    public BigDecimal getCoinsurancePaid() {
      if (this.secondaryPayer.compareTo(Claim.ZERO_CENTS) > 0) {
        return this.secondaryPayer;
      } else if (this.coinsurance.compareTo(Claim.ZERO_CENTS) > 0) {
        return this.pocket;
      }
      return Claim.ZERO_CENTS;
    }
  }

  public Payer payer;
  public Payer secondaryPayer;
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
    plan.totalExpenses = plan.totalExpenses.add(totals.copay).add(totals.deductible)
            .add(totals.pocket);
    plan.totalCoverage = plan.totalCoverage.add(totals.coinsurance).add(totals.payer)
            .add(totals.secondaryPayer);
    plan.payer.addCoveredCost(totals.coinsurance);
    plan.payer.addCoveredCost(totals.payer);
    plan.payer.addUncoveredCost(totals.copay);
    plan.payer.addUncoveredCost(totals.deductible);
    plan.payer.addUncoveredCost(totals.pocket);
    plan.secondaryPayer.addCoveredCost(totals.secondaryPayer);
  }

  private void assignCosts(ClaimEntry claimEntry, Plan plan) {
    claimEntry.cost = claimEntry.entry.getCost();
    BigDecimal remaining = claimEntry.cost;
    if (payer.coversCare(claimEntry.entry)) {
      payer.incrementCoveredEntries(claimEntry.entry);
      // Apply copay to Encounters and Medication claims only
      if ((claimEntry.entry instanceof HealthRecord.Encounter)
          || (claimEntry.entry instanceof HealthRecord.Medication)) {
        claimEntry.copay = payer.determineCopay(claimEntry.entry);
        remaining = remaining.subtract(claimEntry.copay);
      }
      // Check if the patient has remaining deductible
      if (remaining.compareTo(Claim.ZERO_CENTS) > 0 && plan.remainingDeductible
              .compareTo(Claim.ZERO_CENTS) > 0) {
        if (plan.remainingDeductible.compareTo(remaining) >= 0) {
          claimEntry.deductible = remaining;
        } else {
          claimEntry.deductible = plan.remainingDeductible;
        }
        remaining = remaining.subtract(claimEntry.deductible);
        plan.remainingDeductible = plan.remainingDeductible.subtract(claimEntry.deductible);
      }
      if (remaining.compareTo(Claim.ZERO_CENTS) > 0) {
        // Check if the payer has an adjustment
        BigDecimal adjustment = payer.adjustClaim(claimEntry, person);
        remaining = remaining.subtract(adjustment);
      }
      if (remaining.compareTo(Claim.ZERO_CENTS) > 0) {
        // Check if the patient has coinsurance
        BigDecimal coinsurance = payer.getCoinsurance();
        if (coinsurance.compareTo(Claim.ZERO_CENTS) > 0) {
          // Payer covers some
          claimEntry.coinsurance = coinsurance.multiply(remaining)
                  .setScale(2, RoundingMode.HALF_EVEN);
          remaining = remaining.subtract(claimEntry.coinsurance);
        } else {
          // Payer covers all
          claimEntry.payer = remaining;
          remaining = remaining.subtract(claimEntry.payer);
        }
      }
      if (remaining.compareTo(Claim.ZERO_CENTS) > 0) {
        // If secondary insurance, payer covers remainder, not patient.
        if (Payer.noInsurance != plan.secondaryPayer) {
          claimEntry.secondaryPayer = remaining;
          remaining = remaining.subtract(claimEntry.secondaryPayer);
        }
      }
      if (remaining.compareTo(Claim.ZERO_CENTS) > 0) {
        // Patient amount
        claimEntry.pocket = remaining;
        remaining = remaining.subtract(claimEntry.pocket);
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
  public BigDecimal getTotalClaimCost() {
    return this.totals.cost;
  }

  /**
   * Returns the total cost that the Payer covered for this claim.
   */
  public BigDecimal getCoveredCost() {
    return this.totals.coinsurance.add(this.totals.payer);
  }

  public BigDecimal getDeductiblePaid() {
    return this.totals.deductible;
  }

  public BigDecimal getCopayPaid() {
    return this.totals.copay;
  }

  /**
   * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
   * of pocket.
   * @return the amount of coinsurance paid
   */
  public BigDecimal getCoinsurancePaid() {
    if (this.totals.secondaryPayer.compareTo(Claim.ZERO_CENTS) > 0) {
      return this.totals.secondaryPayer;
    } else if (this.totals.coinsurance.compareTo(Claim.ZERO_CENTS) > 0) {
      return this.totals.pocket;
    }
    return Claim.ZERO_CENTS;
  }

  /**
   * Returns the total cost to the patient, including copay, coinsurance, and deductible.
   */
  public BigDecimal getPatientCost() {
    return this.totals.pocket.add(this.totals.copay).add(this.totals.deductible);
  }
}