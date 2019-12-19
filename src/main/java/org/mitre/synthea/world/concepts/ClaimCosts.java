package org.mitre.synthea.world.concepts;

import org.mitre.synthea.world.agents.Payer;

public class ClaimCosts {

  private double deductiblePaid;
  private double copayPaid;
  private double totalPatientPaid;
  private double patientCoinsurance;
  private double payerCoinsurance;
  private double overallCost;
  private double memberReimbursementAmount;
  private double claimDiscountAmount;
  private double claimPrimaryPayerPaid;
  private double memberLiability;
  private double patientPointOfServicePaid;
  private double coveredCost;
  private double uncoveredCost;


  /**
   * Default constructor.  Initialize amounts to zero.
   */
  ClaimCosts() {
    this.deductiblePaid = 0.0;
    this.copayPaid = 0.0;
    this.totalPatientPaid = 0.0;
    this.patientCoinsurance = 0.0;
    this.payerCoinsurance = 0.0;
    this.overallCost = 0.0;
    this.memberLiability = 0.0;
    this.patientPointOfServicePaid = 0.0;
    this.coveredCost = 0.0;
    this.uncoveredCost = 0.0;

    // not implemented. will always be zero.
    this.memberReimbursementAmount = 0.0;

    this.claimDiscountAmount = 0.0;
    this.claimPrimaryPayerPaid = 0.0;
  }

  /**
   * Calculates and assigns encounter claim costs.  This is the first implementation
   * of deductible in cost calculations. Total amounts for the claim
   * and claim line items are used to determine coinsurance, copay, and deductible.
   * Before method return, determineClaimLineCosts(Claim claim) is called to
   * break costs down to claim line items for each item. Copay applies toward deductible
   * amounts. Coinsurance only applies after deductible requirement has been met.
   *
   * @param claim claim for which to calculate total and line costs
   * @return the total cost of the claim for the patient
   */
  double determineClaimCosts(Claim claim) {
    double patientCopay = claim.payer.determineCopay(claim.getMainEntry());
    double totalCost = claim.getTotalClaimCost();

    // used to determine if cost applies to fields based on insurance status
    boolean hasNoInsurance = checkIfNoInsurance(claim);

    // patient deductible sometimes is present if the person has a payer that does not
    // cover an encounter
    double patientDeductible = (hasNoInsurance ? 0.0 : claim.person.getDeductible());

    double costToPatient;

    /* Determine if claim cost is higher or lower than deductible and copay.
     We always want to pay deductible first instead of copay since it applies to
     deductible, but if copay is higher than deductible, pay that.
     Do not pay more than totalCost.  If claim total cost is lower than
     both deductible and copay, the patient will pay that amount.
     costToPatient is used to track how much the patient is paying during calculations.
     */
    costToPatient =  Math.max(
        Math.min(patientCopay, totalCost),
        Math.min(patientDeductible, totalCost));

    // if copay is zero, pay none; if copay exists pay no more than total cost if it
    // is greater; otherwise pay copay
    claim.claimCosts.setCopayPaid(Math.min(patientCopay, totalCost));

    // pay the same amount as copay at point of service
    claim.claimCosts.setPatientPointOfServicePaid(claim.claimCosts.getCopayPaid());

    claim.claimCosts.setDeductiblePaid(Math.min(patientDeductible, totalCost));

    // update person's remainder deductible in their attributes
    claim.person.setDeductible((patientDeductible - Math.min(patientDeductible, totalCost)));

    double patientCoinsuranceLiability = calculatePatientCoinsurance(claim.payer.getCoinsurance(),
        (totalCost - costToPatient));

    // set patient coinsurance to 0.0 if no insurance
    claim.claimCosts.setPatientCoinsurance((hasNoInsurance ? 0.0 : patientCoinsuranceLiability));

    costToPatient += patientCoinsuranceLiability;

    // payer coinsurance is remainder of total cost after patient cost
    claim.claimCosts.setPayerCoinsurance((totalCost - costToPatient));

    claim.claimCosts.setOverallCost(totalCost);
    claim.claimCosts.setTotalPatientPaid(costToPatient);

    // if no insurance, put total cost, otherwise use patient coinsurance
    claim.claimCosts.setMemberLiability((hasNoInsurance
        ? this.totalPatientPaid
        : this.patientCoinsurance));

    claim.claimCosts.setUncoveredCost(costToPatient);
    claim.claimCosts.setCoveredCost(claim.claimCosts.getPayerCoinsurance());

    // start itemizing costs for each entry
    determineClaimLineCosts(claim);

    return costToPatient;
  }

  /**
   * Compares payer ownership to determine if it is no insurance.
   * @param claim the claim being tested for no insurance case
   * @return boolean true if claim has no insurance
   */
  private boolean checkIfNoInsurance(Claim claim) {
    return claim.payer.getOwnership().equals(Payer.noInsurance.getOwnership());
  }

  /**
   * Called before return of determineClaimCosts(Claim claim).  This method
   * calculates coinsurance, copay, deductible, and total costs associated
   * with each line item.  If a copay or deductible costs more than the cost
   * of the first claim item (encounter), subsequent claim items will continue to
   * take these into account before coinsurance rates are calculated.  Copay counts
   * toward yearly deductible costs.
   *
   * @param claim claim for which to calculate line item costs
   */
  private void determineClaimLineCosts(Claim claim) {
    double patientCopay = claim.payer.determineCopay(claim.getMainEntry());
    HealthRecord.Entry encounter = claim.getMainEntry();

    // grab deductible paid for this claim so we know how
    // much to disburse to claim line costs
    double patientDeductible = claim.claimCosts.getDeductiblePaid();

    // encounter entry cost to process first (claim line sequence 1)
    double encounterCost = claim.getMainEntry().getCost().doubleValue();

    encounter.claimLineCosts.setOverallCost(encounterCost);

    double payerCoinsuranceRate = claim.payer.getCoinsurance();
    boolean hasNoInsurance = checkIfNoInsurance(claim);

    /* Determine if encounter cost is higher or lower than deductible and copay.
     We always want to pay deductible first instead of copay since it applies to
     deductible, but if copay is higher than deductible, pay that.
      Do not pay more than encounterCost.  If encounter cost is lower than
     both deductible and copay, the patient will pay that amount, deducting
     that amount from copay and deductible. Copay
     and deductible remainder will be moved forward to be paid by claim items.
     patientTotal is used to track how much the patient is paying during calculations.
     */
    double patientTotal = (Math.max(
        Math.min(encounterCost, patientDeductible),
        Math.min(patientCopay, encounterCost)));

    // Calculate remainder copay to bring forward to claim items.
    double encounterCopayRemainder = (patientCopay >= encounterCost
        ? (patientCopay - encounterCost)
        : 0.0);

    // Start processing the encounter entry claim.  Use math min to not over-pay encounter cost
    encounter.claimLineCosts.setDeductiblePaid((Math.min(patientDeductible, encounterCost)));

    // copay will either be copay amount or encounter cost, whichever is less
    encounter.claimLineCosts.setCopayPaid(Math.min(encounterCost, patientCopay));

    // only pay copay at point of service for now
    encounter.claimLineCosts.setPatientPointOfServicePaid(encounter.claimLineCosts.getCopayPaid());

    // payer coinsurance will be zero if no coverage or patient costs cover all
    encounter.claimLineCosts.setPayerCoinsurance(
        calculatePayerCoinsurance(
            payerCoinsuranceRate, (encounterCost - patientTotal)));

    // if no insurance, have patient coinsurance be 0
    encounter.claimLineCosts.setPatientCoinsurance((hasNoInsurance
        ? 0.0
        : calculatePatientCoinsurance(
        (payerCoinsuranceRate), (encounterCost - patientTotal))));

    // add patient coinsurance to cost total. If no insurance, remainder added.
    patientTotal += (calculatePatientCoinsurance(
        (payerCoinsuranceRate), (encounterCost - patientTotal)));

    encounter.claimLineCosts.setTotalPatientPaid(patientTotal);

    // start tracking remainder deductible if it is greater than encounter cost.
    // remainder cost will be disbursed amongst claim items.
    patientDeductible -= Math.min(patientDeductible, encounterCost);

    // put total amount if no insurance, otherwise patient copay
    encounter.claimLineCosts.setMemberLiability((hasNoInsurance
        ? encounterCost
        : encounter.claimLineCosts.getPatientCoinsurance()));

    encounter.claimLineCosts.setUncoveredCost(patientTotal);
    encounter.claimLineCosts.setCoveredCost(encounter.claimLineCosts.getPayerCoinsurance());

    // start calculating amounts for claim items
    // follows the same process as encounter line costs
    for (HealthRecord.Entry entry : claim.items) {
      double claimItemCost = entry.getCost().doubleValue();

      entry.claimLineCosts.setOverallCost(claimItemCost);
      double patientSubTotal = (Math.min(claimItemCost, encounterCopayRemainder)
          + Math.min(patientDeductible, claimItemCost));
      double remainderItemCost = claimItemCost - patientSubTotal;

      entry.claimLineCosts.setCopayPaid(Math.min(claimItemCost, encounterCopayRemainder));
      entry.claimLineCosts.setPatientPointOfServicePaid(entry.claimLineCosts.getCopayPaid());
      entry.claimLineCosts.setDeductiblePaid(Math.min(patientDeductible, claimItemCost));
      entry.claimLineCosts.setPatientCoinsurance((hasNoInsurance
          ? 0.0
          : calculatePatientCoinsurance(payerCoinsuranceRate, remainderItemCost)));
      entry.claimLineCosts.setPayerCoinsurance(
          calculatePayerCoinsurance(payerCoinsuranceRate, remainderItemCost));
      entry.claimLineCosts.setTotalPatientPaid((patientSubTotal + calculatePatientCoinsurance(
          payerCoinsuranceRate, remainderItemCost)));
      entry.claimLineCosts.setMemberLiability((hasNoInsurance
          ? claimItemCost
          : entry.claimLineCosts.getPatientCoinsurance()));
      entry.claimLineCosts.setCoveredCost(entry.claimLineCosts.getPayerCoinsurance());
      entry.claimLineCosts.setUncoveredCost(entry.claimLineCosts.getTotalPatientPaid());

      // keep track of deductible and copay remainders for next claim item
      encounterCopayRemainder = (claimItemCost >= encounterCopayRemainder
          ? 0.0
          : (encounterCopayRemainder - claimItemCost));

      patientDeductible -= Math.min(patientDeductible, claimItemCost);
    }
  }

  /**
   * Calculates payer liability of a claim. Multiplies a decimal percentage for the rate.
   *
   * @param coinsuranceRate percentage rate that a payer pays of a claim cost
   * @param costOfItem cost of a claim or claim item
   * @return payer liability of a claim
   */
  private double calculatePayerCoinsurance(double coinsuranceRate, double costOfItem) {
    return (coinsuranceRate * costOfItem);
  }

  /**
   * Calculates patient liability of a claim. Subtract payer rate from 1 to get
   * patient rate.
   *
   * @param coinsuranceRate percentage rate that a payer pays of a claim cost
   * @param costOfItem cost of a claim item
   * @return amount of cost of claim item that a patient is liable for
   */
  private double calculatePatientCoinsurance(double coinsuranceRate, double costOfItem) {
    return ((1.0 - coinsuranceRate) * costOfItem);
  }

  void setOverallCost(double overallCost) {
    this.overallCost = overallCost;
  }

  public double getDeductiblePaid() {
    return deductiblePaid;
  }

  public double getCoveredCost() {
    return coveredCost;
  }

  private void setCoveredCost(double coveredCost) {
    this.coveredCost = coveredCost;
  }

  public double getUncoveredCost() {
    return uncoveredCost;
  }

  private void setUncoveredCost(double uncoveredCost) {
    this.uncoveredCost = uncoveredCost;
  }

  public double getMemberLiability() {
    return memberLiability;
  }

  private void setMemberLiability(double memberLiability) {
    this.memberLiability = memberLiability;
  }

  public double getCopayPaid() {
    return copayPaid;
  }

  public double getTotalPatientPaid() {
    return totalPatientPaid;
  }

  public double getMemberReimbursementAmount() {
    return memberReimbursementAmount;
  }

  public double getPatientCoinsurance() {
    return patientCoinsurance;
  }

  private void setPatientPointOfServicePaid(double patientPointOfServicePaid) {
    this.patientPointOfServicePaid = patientPointOfServicePaid;
  }

  public double getPatientPointOfServicePaid() {
    return patientPointOfServicePaid;
  }

  public double getClaimDiscountAmount() {
    return claimDiscountAmount;
  }

  public double getClaimPrimaryPayerPaid() {
    return claimPrimaryPayerPaid;
  }

  public double getPayerCoinsurance() {
    return payerCoinsurance;
  }

  private void setDeductiblePaid(double deductiblePaid) {
    this.deductiblePaid = deductiblePaid;
  }

  private void setCopayPaid(double copayPaid) {
    this.copayPaid = copayPaid;
  }

  private void setTotalPatientPaid(double totalPatientPaid) {
    this.totalPatientPaid = totalPatientPaid;
  }

  private void setPatientCoinsurance(double patientCoinsurance) {
    this.patientCoinsurance = patientCoinsurance;
  }

  private void setPayerCoinsurance(double payerCoinsurance) {
    this.payerCoinsurance = payerCoinsurance;
  }

  public double getOverallCost() {
    return overallCost;
  }

}
