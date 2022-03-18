package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.util.HashMap;
import java.util.Map;

import org.mitre.synthea.world.agents.PayerManager;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static Map<String, IPlanEligibility> planEligibilities;

  private static final String GENERIC = "GENERIC";

  /**
   * Returns the correct elgibility algorithm based on the given string.
   * @param eligibility The name of the eligibility type.
   * @return  The requested payer eligibilty algorithm.
   */
  public static IPlanEligibility getPayerEligibilityAlgorithm(String eligibility) {
    if (planEligibilities.containsKey(eligibility)) {
      return planEligibilities.get(eligibility);
    }
    return planEligibilities.get(GENERIC);
  }

  public static void buildPayerEligibilities(String state){
    Map<String, IPlanEligibility> payerEligibilties = new HashMap<>();
    payerEligibilties.put(PayerManager.MEDICAID, new StandardMedicaidEligibility(state));
    payerEligibilties.put(PayerManager.MEDICARE, new StandardMedicareEligibility());
    payerEligibilties.put(PayerManager.DUAL_ELIGIBLE, new StandardDualEligibility());
    payerEligibilties.put(PlanEligibilityFinder.GENERIC, new GenericPayerEligibilty());

    // TODO - HERE IS WHERE CSV INPUT ELIGIBILITIES WOULD BE BUILT

    PlanEligibilityFinder.planEligibilities = payerEligibilties;
  }
}
