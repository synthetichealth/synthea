package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.PayerManager;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static IPlanEligibility medicareEligibilty = new StandardMedicareEligibility();
  private static IPlanEligibility medicaidEligibility = new StandardMedicaidEligibility();
  private static IPlanEligibility duaEligibility = new StandardDualEligibility();
  private static IPlanEligibility genericEligibility = new GenericPayerEligibilty();

  /**
   * Returns the correct elgibility algorithm based on the payer's name. It uses
   * names of either Medicare or Medicaid.
   * @param payerName The name of the payer.
   * @return  The requested payer eligibilty algorithm.
   */
  public static IPlanEligibility getPayerEligibilityAlgorithm(String payerName) {
    if (payerName.equalsIgnoreCase(PayerManager.MEDICAID)) {
      return PlanEligibilityFinder.medicaidEligibility;
    } else if (payerName.equalsIgnoreCase(PayerManager.MEDICARE)) {
      return PlanEligibilityFinder.medicareEligibilty;
    } else if (payerName.equalsIgnoreCase(PayerManager.DUAL_ELIGIBLE)) {
      return PlanEligibilityFinder.duaEligibility;
    }
    return PlanEligibilityFinder.genericEligibility;
  }
}
