package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.CoverageRecord.PlanRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

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
    public BigDecimal paidTowardDeductible = ZERO_CENTS;
    /** amount paid by deductible. */
    public BigDecimal paidByDeductible = ZERO_CENTS;
    /** amount the charge was decreased by payer adjustment. */
    public BigDecimal adjustment = ZERO_CENTS;
    /** coinsurance paid by payer. */
    public BigDecimal coinsurancePaidByPayer = ZERO_CENTS;
    /** coinsurance paid by payer. */
    public BigDecimal coinsurancePaidByPatient = ZERO_CENTS;
    /** otherwise paid by payer. */
    public BigDecimal paidByPayer = ZERO_CENTS;
    /** otherwise paid by secondary payer. */
    public BigDecimal paidBySecondaryPayer = ZERO_CENTS;
    /** otherwise paid by patient out of pocket. */
    public BigDecimal paidByPatient = ZERO_CENTS;

    public ClaimEntry(Entry entry) {
      this.entry = entry;
    }

    void assignCosts(PlanRecord planRecord) {
      this.cost = this.entry.getCost();

      if (!plan.coversService(this.entry) || plan.isNoInsurance()) {
        plan.incrementUncoveredEntries(this.entry);
        // Plan does not cover care
        this.paidByPatient = this.cost;
        return;
      }

      plan.incrementCoveredEntries(this.entry);
      // Check if the plan has a cost adjustment
      BigDecimal adjustment = plan.adjustClaim(this, person);
      final BigDecimal adjustedCost = cost.subtract(adjustment);
      // Check if the patient has remaining deductible (if the plan is deductible-based)
      if (planRecord.remainingDeductible.compareTo(BigDecimal.ONE) == -1
          && planRecord.isDedctiblePlan()) {
        this.paidByDeductible = adjustedCost;
        this.paidByPatient = ZERO_CENTS;
        this.paidByPayer = this.paidByDeductible;
        return;
      }
      // Apply copay to Encounters and Medication claims only
      if (plan.isCopayBased() && ((this.entry instanceof HealthRecord.Encounter)
          || (this.entry instanceof HealthRecord.Medication))) {
        BigDecimal copay = plan.determineCopay(this.entry);
        this.copay = copay;
        this.paidByPatient = paidByPatient.add(copay);
        this.paidByPayer = paidByPayer.add(adjustedCost.subtract(copay));
        planRecord.remainingDeductible = planRecord.remainingDeductible.subtract(copay);
        if (planRecord.isDedctiblePlan()) {
          this.paidTowardDeductible = this.paidTowardDeductible.add(this.paidByPatient);
        }
        return;
      }
      // Check if the patient has coinsurance to pay.
      BigDecimal patientCoinsurance = plan.getPatientCoinsurance();
      if (patientCoinsurance.compareTo(BigDecimal.ZERO) == 1) {
        BigDecimal payerCoinsurance = plan.getPayerCoinsurance();
        BigDecimal coinsurancePatientToPay = adjustedCost.multiply(patientCoinsurance);
        // If the person has secondary insurance, they cover the coinusurance.
        if (!planRecord.secondaryPlan.getPayer().isNoInsurance()) {
          this.paidBySecondaryPayer = coinsurancePatientToPay;
          coinsurancePatientToPay = BigDecimal.ZERO;
        }
        paidByPatient = paidByPatient.add(coinsurancePatientToPay);
        coinsurancePaidByPatient = coinsurancePaidByPatient.add(coinsurancePatientToPay);
        BigDecimal coinsurancePayerToPay = adjustedCost.multiply(payerCoinsurance);
        paidByPayer = paidByPayer.add(coinsurancePayerToPay);
        this.coinsurancePaidByPayer = coinsurancePayerToPay;
        planRecord.remainingDeductible
            = planRecord.remainingDeductible.subtract(coinsurancePatientToPay);
        if (planRecord.isDedctiblePlan()) {
          this.paidTowardDeductible = this.paidTowardDeductible.add(this.paidByPatient);
        }
        return;
      }
      // Since the patient has not been covered up to this point, they incur the total cost.
      this.paidByPatient = this.paidByPatient.add(adjustedCost);
      planRecord.remainingDeductible = planRecord.remainingDeductible.subtract(this.paidByPatient);
    }

    /**
     * Add the costs from the other entry to this one.
     * @param other the other claim entry.
     */
    public void addCosts(ClaimEntry other) {
      this.cost = this.cost.add(other.cost);
      this.copay = this.copay.add(other.copay);
      this.paidByDeductible = this.paidByDeductible.add(other.paidByDeductible);
      this.adjustment = this.adjustment.add(other.adjustment);
      this.coinsurancePaidByPayer = this.coinsurancePaidByPayer.add(other.coinsurancePaidByPayer);
      this.paidByPayer = this.paidByPayer.add(other.paidByPayer);
      this.paidBySecondaryPayer = this.paidBySecondaryPayer.add(other.paidBySecondaryPayer);
      this.paidByPatient = this.paidByPatient.add(other.paidByPatient);
    }

    /**
     * Returns the amount of coinsurance paid by the patient, either via secondary insurance or out
     * of pocket.
     * @return the amount of coinsurance paid
     */
    public BigDecimal getCoinsurancePaid() {
      if (this.paidBySecondaryPayer.compareTo(Claim.ZERO_CENTS) == 1) {
        return this.paidBySecondaryPayer;
      }
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

    planRecord.incrementHealthcareExpenses(totals.paidByPatient);
    planRecord.incrementCoverage(totals.paidByPayer);
    planRecord.incrementCoverage(totals.paidBySecondaryPayer);
    planRecord.plan.addCoveredCost(totals.paidByPayer);
    planRecord.plan.addUncoveredCost(totals.paidByPatient);
    planRecord.secondaryPlan.addCoveredCost(totals.paidBySecondaryPayer);
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
    return this.totals.paidByPayer;
  }

  public BigDecimal getDeductiblePaid() {
    return this.totals.paidTowardDeductible;
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
    BigDecimal totalCoinsurancePaid = mainEntry.getCoinsurancePaid();
    for (ClaimEntry entry : this.items) {
      totalCoinsurancePaid = totalCoinsurancePaid.add(entry.getCoinsurancePaid());
    }
    return totalCoinsurancePaid;
  }

  /**
   * Returns the total cost to the patient, including copay, coinsurance, and deductible.
   */
  public BigDecimal getPatientCost() {
    return this.totals.paidByPatient;
  }
}