package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.concepts.healthinsurance.PlanRecord;

/**
 * Represents a claim for healthcare services, including costs and payer details.
 */
public class Claim implements Serializable {
  /**
   * Serial version UID for serialization.
   */
  private static final long serialVersionUID = -3565704321813987656L;

  /**
   * Constant representing zero cost in cents.
   */
  public static final BigDecimal ZERO_CENTS = BigDecimal.ZERO.setScale(2);

  /**
   * Represents the cost details of a claim.
   */
  public static class ClaimCost implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Total cost of the entry. */
    public BigDecimal cost = ZERO_CENTS;
    /** Copay paid by the patient. */
    public BigDecimal copayPaidByPatient = ZERO_CENTS;
    /** Deductible paid by the patient. */
    public BigDecimal deductiblePaidByPatient = ZERO_CENTS;
    /** Amount the charge was decreased by payer adjustment. */
    public BigDecimal adjustment = ZERO_CENTS;
    /** Coinsurance paid by the payer. */
    public BigDecimal coinsurancePaidByPayer = ZERO_CENTS;
    /** Amount otherwise paid by the primary payer. */
    public BigDecimal paidByPayer = ZERO_CENTS;
    /** Amount otherwise paid by the secondary payer. */
    public BigDecimal paidBySecondaryPayer = ZERO_CENTS;
    /** Amount otherwise paid by the patient out of pocket. */
    public BigDecimal patientOutOfPocket = ZERO_CENTS;

    /**
     * Create a new instance with all costs set to zero.
     */
    public ClaimCost() {
    }

    /**
     * Reset all claim costs to zero.
     */
    public void reset() {
      cost = ZERO_CENTS;
      copayPaidByPatient = ZERO_CENTS;
      deductiblePaidByPatient = ZERO_CENTS;
      adjustment = ZERO_CENTS;
      coinsurancePaidByPayer = ZERO_CENTS;
      paidByPayer = ZERO_CENTS;
      paidBySecondaryPayer = ZERO_CENTS;
      patientOutOfPocket = ZERO_CENTS;
    }

    /**
     * Create a new instance with the same cost values as the supplied instance.
     * @param other the instance to copy costs from
     */
    public ClaimCost(ClaimCost other) {
      this.cost = other.cost;
      this.copayPaidByPatient = other.copayPaidByPatient;
      this.deductiblePaidByPatient = other.deductiblePaidByPatient;
      this.adjustment = other.adjustment;
      this.coinsurancePaidByPayer = other.coinsurancePaidByPayer;
      this.paidByPayer = other.paidByPayer;
      this.paidBySecondaryPayer = other.paidBySecondaryPayer;
      this.patientOutOfPocket = other.patientOutOfPocket;
    }

    /**
     * Add the costs from the other entry to this one.
     * @param other the other claim entry.
     */
    public void addCosts(ClaimCost other) {
      this.cost = this.cost.add(other.cost);
      this.copayPaidByPatient = this.copayPaidByPatient.add(other.copayPaidByPatient);
      this.deductiblePaidByPatient
          = this.deductiblePaidByPatient.add(other.deductiblePaidByPatient);
      this.adjustment = this.adjustment.add(other.adjustment);
      this.coinsurancePaidByPayer = this.coinsurancePaidByPayer.add(other.coinsurancePaidByPayer);
      this.paidByPayer = this.paidByPayer.add(other.paidByPayer);
      this.paidBySecondaryPayer = this.paidBySecondaryPayer.add(other.paidBySecondaryPayer);
      this.patientOutOfPocket = this.patientOutOfPocket.add(other.patientOutOfPocket);
    }

    /**
     * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
     * of pocket.
     * @return the amount of coinsurance paid
     */
    public BigDecimal getCoinsurancePaid() {
      if (this.paidBySecondaryPayer.compareTo(Claim.ZERO_CENTS) > 0) {
        return this.paidBySecondaryPayer;
      } else if (this.coinsurancePaidByPayer.compareTo(Claim.ZERO_CENTS) > 0) {
        return this.patientOutOfPocket;
      }
      return Claim.ZERO_CENTS;
    }

    /**
     * Returns the total cost of the Claim, including immunizations/procedures tied to the
     * encounter.
     * @return the total claim cost
     */
    public BigDecimal getTotalClaimCost() {
      return cost;
    }

    /**
     * Returns the total cost covered by the payer, which includes coinsurance and
     * any amounts paid by the secondary payer.
     * @return the cost covered by the payer.
     */
    public BigDecimal getCoveredCost() {
      return coinsurancePaidByPayer.add(paidByPayer);
    }

    /**
     * Returns the total deductible paid by the patient.
     * @return the deductible paid by the patient.
     */
    public BigDecimal getDeductiblePaid() {
      return deductiblePaidByPatient;
    }

    /**
     * Returns the total copay paid by the patient.
     * @return the copay paid by the patient.
     */
    public BigDecimal getCopayPaid() {
      return copayPaidByPatient;
    }

    /**
     * Returns the total cost paid by the patient, which includes
     * out-of-pocket expenses, copay, and deductible.
     * @return the total cost paid by the patient.
     */
    public BigDecimal getPatientCost() {
      return patientOutOfPocket.add(copayPaidByPatient).add(deductiblePaidByPatient);
    }
  }

  /**
   * Represents a specific entry in a claim, such as an encounter or medication.
   */
  public class ClaimEntry extends ClaimCost implements Serializable {
    /**
     * Serial version UID for serialization.
     */
    private static final long serialVersionUID = 1871121895630816723L;

    /** The health record entry associated with this claim entry. */
    @JSONSkip
    public Entry entry;

    /**
     * Create a new ClaimEntry for the given health record entry.
     * @param entry the health record entry
     */
    public ClaimEntry(Entry entry) {
      this.entry = entry;
    }

    /**
     * Assign costs for this ClaimEntry.
     * @param planRecord the plan record to check patient costs from
     */
    private void assignCosts(PlanRecord planRecord) {
      reset();
      this.cost = this.entry.getCost();
      BigDecimal remainingBalance = this.cost;

      InsurancePlan plan = planRecord.getPlan();

      if (!plan.coversService(this.entry)) {
        plan.incrementUncoveredEntries(this.entry);
        // Payer does not cover care
        this.patientOutOfPocket = remainingBalance;
        return;
      }

      plan.incrementCoveredEntries(this.entry);

      if (planRecord.getOutOfPocketExpenses().compareTo(plan.getMaxOop()) > 0) {
        // TODO - This will only trigger after a person has already paid more than their Max OOP.
        // An accurate implementation would require a Max OOP check for every time a patient is
        // assigned costs in this method.
        // The person has already paid their maximum out-of-pocket costs.
        this.paidByPayer = remainingBalance;
        remainingBalance = ZERO_CENTS;
      }

      // Apply copay to Encounters and Medication claims only
      if ((this.entry instanceof HealthRecord.Encounter)
          || (this.entry instanceof HealthRecord.Medication)) {
        this.copayPaidByPatient = plan.determineCopay(this.entry.type, this.entry.start);
        if (this.copayPaidByPatient.compareTo(remainingBalance) > 0) {
          this.copayPaidByPatient = remainingBalance;
        }
        remainingBalance = remainingBalance.subtract(this.copayPaidByPatient);
      }
      // Check if the patient has remaining deductible
      if (remainingBalance.compareTo(Claim.ZERO_CENTS) > 0 && planRecord.remainingDeductible
              .compareTo(Claim.ZERO_CENTS) > 0) {
        if (planRecord.remainingDeductible.compareTo(remainingBalance) >= 0) {
          this.deductiblePaidByPatient = remainingBalance;
        } else {
          this.deductiblePaidByPatient = planRecord.remainingDeductible;
        }
        remainingBalance = remainingBalance.subtract(this.deductiblePaidByPatient);
        planRecord.remainingDeductible = planRecord.remainingDeductible
            .subtract(this.deductiblePaidByPatient);
      }
      if (remainingBalance.compareTo(Claim.ZERO_CENTS) > 0) {
        // Check if the payer has an adjustment
        BigDecimal adjustment = plan.adjustClaim(this, person);
        remainingBalance = remainingBalance.subtract(adjustment);
      }
      if (remainingBalance.compareTo(Claim.ZERO_CENTS) > 0) {
        // Check if the patient has coinsurance
        BigDecimal patientCoinsurance = plan.getPatientCoinsurance();
        if (patientCoinsurance.compareTo(Claim.ZERO_CENTS) > 0) {
          BigDecimal payerCoinsurance = BigDecimal.ONE.subtract(plan.getPatientCoinsurance());
          // Payer covers some
          this.coinsurancePaidByPayer = payerCoinsurance.multiply(remainingBalance)
                  .setScale(2, RoundingMode.HALF_EVEN);
          remainingBalance = remainingBalance.subtract(this.coinsurancePaidByPayer);
        } else {
          // Payer covers all
          this.paidByPayer = remainingBalance;
          remainingBalance = ZERO_CENTS;
        }
      }
      if (remainingBalance.compareTo(Claim.ZERO_CENTS) > 0) {
        // If secondary insurance, payer covers remainder, not patient.
        if (!planRecord.getSecondaryPlan().isNoInsurance()) {
          this.paidBySecondaryPayer = remainingBalance;
          remainingBalance = remainingBalance.subtract(this.paidBySecondaryPayer);
        }
      }
      if (remainingBalance.compareTo(Claim.ZERO_CENTS) > 0) {
        // Patient amount
        this.patientOutOfPocket = remainingBalance;
        remainingBalance = remainingBalance.subtract(this.patientOutOfPocket);
      }
    }
  }

  /** The person associated with this claim. */
  @JSONSkip
  public final Person person;
  /** The main entry of the claim. */
  public final ClaimEntry mainEntry;
  /** Additional items in the claim. */
  public final List<ClaimEntry> items;
  /** Totals for the claim. */
  public ClaimEntry totals;
  /** Unique identifier for the claim. */
  public final UUID uuid;
  /** The plan record associated with the claim. */
  private final PlanRecord planRecord;

  /**
   * Constructor of a Claim for an Entry.
   * @param entry the health record entry
   * @param person the person associated with the claim
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
    // Set the plan record.
    if (person.alive(entry.start)) {
      this.planRecord = this.person.coverage.getPlanRecordAtTime(entry.start);
    } else {
      // Rarely, an encounter (such as death certification), after a person is dead can
      // result in a missing plan record. Account for this by using the last plan record.
      this.planRecord = this.person.coverage.getLastPlanRecord();
    }
    this.items = new ArrayList<ClaimEntry>();
    this.totals = new ClaimEntry(entry);
    this.uuid = this.person.randUUID();
  }

  /**
   * Adds non-explicit costs to the Claim (e.g., procedures, immunizations).
   * @param entry the health record entry to add as a line item
   */
  public void addLineItem(Entry entry) {
    ClaimEntry claimEntry = new ClaimEntry(entry);
    this.items.add(claimEntry);
  }

  /**
   * Assign costs between the payer and patient for all ClaimEntries in this claim.
   */
  public void assignCosts() {
    mainEntry.assignCosts(planRecord);
    totals = new ClaimEntry(mainEntry.entry);
    totals.addCosts(mainEntry);
    for (ClaimEntry item : items) {
      item.assignCosts(planRecord);
      totals.addCosts(item);
    }

    planRecord.incrementOutOfPocketExpenses(getTotalPatientCost());
    planRecord.incrementPrimaryCoverage(getTotalCoveredCost());
    planRecord.incrementSecondaryCoverage(getTotalPaidBySecondaryPayer());
  }

  /**
   * Returns the unique identifier for this claim.
   * @return the total cost of the claim, including all associated items
   */
  public BigDecimal getTotalClaimCost() {
    return this.totals.getTotalClaimCost();
  }

  /**
   * Returns the total cost covered by the payer for this claim.
   * @return the total cost covered by the payer for this claim
   */
  public BigDecimal getTotalCoveredCost() {
    return this.totals.getCoveredCost();
  }

  /**
   * Returns the total cost of the claim, including all associated items.
   * @return the total deductible paid by the patient
   */
  public BigDecimal getTotalDeductiblePaid() {
    return this.totals.getDeductiblePaid();
  }

  /**
   * Returns the total copay paid by the patient for this claim.
   * @return the total copay paid by the patient
   */
  public BigDecimal getTotalCopayPaid() {
    return this.totals.getCopayPaid();
  }

  /**
   * Returns the total amount paid by the patient, which includes
   * @return the total amount paid by the secondary payer
   */
  public BigDecimal getTotalPaidBySecondaryPayer() {
    return this.totals.paidBySecondaryPayer;
  }

  /**
   * Returns the total adjustment amount for this claim, which is the amount
   * @return the total adjustment amount
   */
  public BigDecimal getTotalAdjustment() {
    return this.totals.adjustment;
  }

  /**
   * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
   * of pocket.
   * @return the amount of coinsurance paid
   */
  public BigDecimal getTotalCoinsurancePaid() {
    return this.totals.getCoinsurancePaid();
  }

  /**
   * Returns the total cost to the patient, which includes copay, coinsurance, and deductible.
   * @return the total cost to the patient, including copay, coinsurance, and deductible
   */
  public BigDecimal getTotalPatientCost() {
    return this.totals.getPatientCost();
  }

  /**
   * Checks if this claim was covered by Medicare as the primary payer.
   * @return whether this Claim was covered by Medicare as the primary payer
   */
  public boolean coveredByMedicare() {
    String payerName = this.getPayer().getName();
    return payerName.equals(PayerManager.MEDICARE)
       || payerName.equals(PayerManager.DUAL_ELIGIBLE);
  }

  /**
   * Returns the member ID of the plan record associated with this claim.
   * @return the member ID.
   */
  public String getPlanRecordMemberId() {
    return this.planRecord.id;
  }

  /**
   * Returns the plan record associated with this claim.
   * @return the primary payer of this claim
   */
  public Payer getPayer() {
    return this.planRecord.getPlan().getPayer();
  }

  /**
   * Returns the secondary payer of this claim, if applicable.
   * @return the secondary payer of this claim
   */
  public Payer getSecondaryPayer() {
    return this.planRecord.getSecondaryPlan().getPayer();
  }
}