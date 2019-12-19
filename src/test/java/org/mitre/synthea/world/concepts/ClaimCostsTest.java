package org.mitre.synthea.world.concepts;

import org.junit.Test;
import org.mitre.synthea.TestHelper;

import static org.junit.Assert.assertEquals;


public class ClaimCostsTest {

  private static final double CLAIM_AMOUNT = 576.67;
  private static final double ENCOUNTER_LINE_COST = 115.33;
  private static final double CLAIM_ITEM_COST = 461.34;
  private static final double PATIENT_TOTAL_PAY_INSURANCE = 520.0;
  private static final double PATIENT_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE = 544.0;
  private static final double PAYER_TOTAL_PAY_INSURANCE = 56.67;
  private static final double PAYER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE = 32.67;
  private static final double PATIENT_LIABILITY_INSURANCE = 510.0;
  private static final double PATIENT_LIABILITY_INSURANCE_WITH_DEDUCTIBLE = 294.0;
  private static final double COPAY_INSURANCE = 10.0;
  private static final double ZERO_DOLLARS = 0.0;
  private static final double PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE = 104.8;
  private static final double PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE = 115.33;
  private static final double PATIENT_ENCOUNTER_LIABILITY_INSURANCE = 94.8;
  private static final double PAYER_ENCOUNTER_PAY_INSURANCE = 10.53;
  private static final double PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE = 415.21;
  private static final double PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE = 428.67;
  private static final double PATIENT_CLAIM_ITEM_COINSURANCE_INSURANCE_WITH_DEDUCTIBLE = 294.0;
  private static final double PATIENT_CLAIM_ITEM_DEDUCTIBLE_INSURANCE_WITH_DEDUCTIBLE = 134.67;
  private static final double PAYER_CLAIM_ITEM_PAY_INSURANCE = 46.13;
  private static final double PAYER_CLAIM_ITEM_PAY_INSURANCE_WITH_DEDUCTIBLE = 32.67;
  private static final double DEDUCTIBLE_AMOUNT = 250.0;

  @Test
  public void assignCosts_noInsurance() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(
        TestHelper.getNoInsurancePayer());
    encounter.claim.assignCosts();
    assertEquals(0.0, encounter.claim.claimCosts.getCopayPaid(), 0.0);
    assertEquals(0.0, encounter.claim.getCoveredCost(), 0.0);
    assertEquals(576.67, encounter.claim.getUncoveredCost(), 0.0);
    assertEquals(0.0, encounter.claim.claimCosts.getDeductiblePaid(), 0.0);
    assertEquals(576.67, encounter.claim.claimCosts.getTotalPatientPaid(), 0.0);
    assertEquals(576.67, encounter.claim.getTotalClaimCost(), 0.0);
    assertEquals(0.0, encounter.claim.claimCosts.getPatientCoinsurance(), 0.0);
    assertEquals(0.0, encounter.claim.claimCosts.getPayerCoinsurance(), 0.0);
  }

  @Test
  public void assignCosts_withInsurance() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(TestHelper.getMockPayer());
    encounter.claim.assignCosts();
    assertEquals(10.0, encounter.claim.claimCosts.getCopayPaid(), 0.0);
    assertEquals(56.67, encounter.claim.getCoveredCost(), 0.01);
    assertEquals(520.0, encounter.claim.getUncoveredCost(), 0.01);
    assertEquals(0.0, encounter.claim.claimCosts.getDeductiblePaid(), 0.0);
    assertEquals(520.0, encounter.claim.claimCosts.getTotalPatientPaid(), 0.01);
    assertEquals(576.67, encounter.claim.getTotalClaimCost(), 0.0);
    assertEquals(510.0, encounter.claim.claimCosts.getPatientCoinsurance(), 0.01);
    assertEquals(56.67, encounter.claim.claimCosts.getPayerCoinsurance(), 0.01);
  }

  @Test
  public void determineClaimCosts_noInsurance() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(
        TestHelper.getNoInsurancePayer());
    double returnedPatientPaid = encounter.claim.claimCosts.determineClaimCosts(encounter.claim);
    ClaimCosts claimTotalCosts = encounter.claim.getClaimCosts();

    // overall claim totals
    assertEquals(CLAIM_AMOUNT, returnedPatientPaid, 0.0);
    assertEquals(encounter.claim.getTotalClaimCost(), claimTotalCosts.getOverallCost(), 0.0);
    assertEquals(CLAIM_AMOUNT, claimTotalCosts.getUncoveredCost(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getCoveredCost(), 0.0);
    assertEquals(CLAIM_AMOUNT, claimTotalCosts.getMemberLiability(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getPatientCoinsurance(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getPayerCoinsurance(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getCopayPaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getDeductiblePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(CLAIM_AMOUNT, claimTotalCosts.getTotalPatientPaid(), 0.0);

    // encounter line item
    assertEquals(encounter.getCost().doubleValue(), encounter.claimLineCosts.getOverallCost(), 0.0);
    assertEquals(ENCOUNTER_LINE_COST, encounter.claimLineCosts.getTotalPatientPaid(), 0.0);
    assertEquals(ENCOUNTER_LINE_COST, encounter.claimLineCosts.getMemberLiability(), 0.0);
    assertEquals(ENCOUNTER_LINE_COST, encounter.claimLineCosts.getUncoveredCost(), 0.0);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getDeductiblePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getCopayPaid(), 0.0);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getPayerCoinsurance(), 0.0);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getPatientCoinsurance(), 0.0);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getCoveredCost(), 0.0);

    // first claim entry item
    HealthRecord.Entry claimItemEntry = encounter.claim.items.get(0);
    assertEquals(claimItemEntry.getCost().doubleValue(),
        claimItemEntry.claimLineCosts.getOverallCost(), 0.0);
    assertEquals(CLAIM_ITEM_COST, claimItemEntry.claimLineCosts.getTotalPatientPaid(), 0.0);
    assertEquals(CLAIM_ITEM_COST, claimItemEntry.claimLineCosts.getMemberLiability(), 0.0);
    assertEquals(CLAIM_ITEM_COST, claimItemEntry.claimLineCosts.getUncoveredCost(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getDeductiblePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getCopayPaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getPayerCoinsurance(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getPatientCoinsurance(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getCoveredCost(), 0.0);
  }

  @Test
  public void determineClaimCosts_withInsurance_noDeductible() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(TestHelper.getMockPayer());
    double returnedPatientPaid = encounter.claim.claimCosts.determineClaimCosts(encounter.claim);
    ClaimCosts claimTotalCosts = encounter.claim.getClaimCosts();

    // overall claim totals
    assertEquals(encounter.claim.getTotalClaimCost(), claimTotalCosts.getOverallCost(), 0.0);
    assertEquals(PATIENT_TOTAL_PAY_INSURANCE, returnedPatientPaid, 0.003);
    assertEquals(PATIENT_TOTAL_PAY_INSURANCE, claimTotalCosts.getUncoveredCost(), 0.003);
    assertEquals(PAYER_TOTAL_PAY_INSURANCE, claimTotalCosts.getCoveredCost(), 0.01);
    assertEquals(PATIENT_LIABILITY_INSURANCE, claimTotalCosts.getMemberLiability(), 0.003);
    assertEquals(PATIENT_LIABILITY_INSURANCE, claimTotalCosts.getPatientCoinsurance(), 0.003);
    assertEquals(PAYER_TOTAL_PAY_INSURANCE, claimTotalCosts.getPayerCoinsurance(), 0.01);
    assertEquals(COPAY_INSURANCE, claimTotalCosts.getCopayPaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimTotalCosts.getDeductiblePaid(), 0.0);
    assertEquals(COPAY_INSURANCE, claimTotalCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(PATIENT_TOTAL_PAY_INSURANCE, claimTotalCosts.getTotalPatientPaid(), 0.003);

    // encounter line item
    assertEquals(encounter.getCost().doubleValue(), encounter.claimLineCosts.getOverallCost(), 0.0);
    assertEquals(PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE,
        encounter.claimLineCosts.getTotalPatientPaid(), 0.01);
    assertEquals(PATIENT_ENCOUNTER_LIABILITY_INSURANCE,
        encounter.claimLineCosts.getMemberLiability(), 0.01);
    assertEquals(PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE,
        encounter.claimLineCosts.getUncoveredCost(), 0.01);
    assertEquals(ZERO_DOLLARS, encounter.claimLineCosts.getDeductiblePaid(), 0.0);
    assertEquals(COPAY_INSURANCE, encounter.claimLineCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(COPAY_INSURANCE, encounter.claimLineCosts.getCopayPaid(), 0.0);
    assertEquals(PAYER_ENCOUNTER_PAY_INSURANCE,
        encounter.claimLineCosts.getPayerCoinsurance(), 0.007);
    assertEquals(PATIENT_ENCOUNTER_LIABILITY_INSURANCE,
        encounter.claimLineCosts.getPatientCoinsurance(), 0.01);
    assertEquals(PAYER_ENCOUNTER_PAY_INSURANCE,
        encounter.claimLineCosts.getCoveredCost(), 0.007);

    // first claim entry item
    HealthRecord.Entry claimItemEntry = encounter.claim.items.get(0);
    assertEquals(claimItemEntry.getCost().doubleValue(),
        claimItemEntry.claimLineCosts.getOverallCost(), 0.0);
    assertEquals(PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE,
        claimItemEntry.claimLineCosts.getTotalPatientPaid(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE,
        claimItemEntry.claimLineCosts.getMemberLiability(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE,
        claimItemEntry.claimLineCosts.getUncoveredCost(), 0.01);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getDeductiblePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getCopayPaid(), 0.0);
    assertEquals(PAYER_CLAIM_ITEM_PAY_INSURANCE,
        claimItemEntry.claimLineCosts.getPayerCoinsurance(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE,
        claimItemEntry.claimLineCosts.getPatientCoinsurance(), 0.01);
    assertEquals(PAYER_CLAIM_ITEM_PAY_INSURANCE,
        claimItemEntry.claimLineCosts.getCoveredCost(), 0.01);
  }

  @Test
  public void determineClaimCosts_withInsurance_withDeductible() {
    HealthRecord.Encounter encounter = TestHelper.generateMockEncounter(TestHelper.getMockPayer());
    encounter.claim.person.setDeductible(DEDUCTIBLE_AMOUNT);
    double returnedPatientPaid = encounter.claim.claimCosts.determineClaimCosts(encounter.claim);
    ClaimCosts claimTotalCosts = encounter.claim.getClaimCosts();

    // overall claim totals
    assertEquals(encounter.claim.getTotalClaimCost(), claimTotalCosts.getOverallCost(), 0.0);
    assertEquals(PATIENT_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE, returnedPatientPaid, 0.003);
    assertEquals(PATIENT_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimTotalCosts.getUncoveredCost(), 0.003);
    assertEquals(PAYER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimTotalCosts.getCoveredCost(), 0.01);
    assertEquals(PATIENT_LIABILITY_INSURANCE_WITH_DEDUCTIBLE,
        claimTotalCosts.getMemberLiability(), 0.003);
    assertEquals(PATIENT_LIABILITY_INSURANCE_WITH_DEDUCTIBLE,
        claimTotalCosts.getPatientCoinsurance(), 0.003);
    assertEquals(PAYER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimTotalCosts.getPayerCoinsurance(), 0.01);
    assertEquals(COPAY_INSURANCE, claimTotalCosts.getCopayPaid(), 0.0);
    assertEquals(DEDUCTIBLE_AMOUNT, claimTotalCosts.getDeductiblePaid(), 0.0);
    assertEquals(COPAY_INSURANCE, claimTotalCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(PATIENT_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimTotalCosts.getTotalPatientPaid(), 0.003);

    // encounter line item
    assertEquals(encounter.getCost().doubleValue(), encounter.claimLineCosts.getOverallCost(), 0.0);
    assertEquals(PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        encounter.claimLineCosts.getTotalPatientPaid(), 0.0);
    assertEquals(ZERO_DOLLARS,
        encounter.claimLineCosts.getMemberLiability(), 0.0);
    assertEquals(PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        encounter.claimLineCosts.getUncoveredCost(), 0.01);
    assertEquals(PATIENT_ENCOUNTER_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        encounter.claimLineCosts.getDeductiblePaid(), 0.0);
    assertEquals(COPAY_INSURANCE, encounter.claimLineCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(COPAY_INSURANCE, encounter.claimLineCosts.getCopayPaid(), 0.0);
    assertEquals(ZERO_DOLLARS,
        encounter.claimLineCosts.getPayerCoinsurance(), 0.007);
    assertEquals(ZERO_DOLLARS,
        encounter.claimLineCosts.getPatientCoinsurance(), 0.01);
    assertEquals(ZERO_DOLLARS,
        encounter.claimLineCosts.getCoveredCost(), 0.007);

    // first claim entry item
    HealthRecord.Entry claimItemEntry = encounter.claim.items.get(0);
    assertEquals(claimItemEntry.getCost().doubleValue(),
        claimItemEntry.claimLineCosts.getOverallCost(), 0.0);
    assertEquals(PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getTotalPatientPaid(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_COINSURANCE_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getMemberLiability(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_TOTAL_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getUncoveredCost(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_DEDUCTIBLE_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getDeductiblePaid(), 0.001);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getPatientPointOfServicePaid(), 0.0);
    assertEquals(ZERO_DOLLARS, claimItemEntry.claimLineCosts.getCopayPaid(), 0.0);
    assertEquals(PAYER_CLAIM_ITEM_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getPayerCoinsurance(), 0.01);
    assertEquals(PATIENT_CLAIM_ITEM_COINSURANCE_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getPatientCoinsurance(), 0.01);
    assertEquals(PAYER_CLAIM_ITEM_PAY_INSURANCE_WITH_DEDUCTIBLE,
        claimItemEntry.claimLineCosts.getCoveredCost(), 0.01);
  }
}