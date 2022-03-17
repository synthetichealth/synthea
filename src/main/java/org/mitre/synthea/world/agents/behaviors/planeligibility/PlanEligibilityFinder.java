package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.util.HashMap;
import java.util.Map;

import org.mitre.synthea.world.agents.PayerManager;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PlanEligibilityFinder {

  private static final Map<String, IPlanEligibility> payerEligibilties = buildPayerEligibilities();

  private static final String GENERIC = "GENERIC";

  /**
   * Returns the correct elgibility algorithm based on the payer's name. It uses
   * names of either Medicare or Medicaid.
   * @param eligibility The name of the eligibility type.
   * @return  The requested payer eligibilty algorithm.
   */
  public static IPlanEligibility getPayerEligibilityAlgorithm(String eligibility) {
    if (payerEligibilties.containsKey(eligibility)) {
      return payerEligibilties.get(eligibility);
    }
    return payerEligibilties.get(GENERIC);
  }

  private static Map<String, IPlanEligibility> buildPayerEligibilities(){
    Map<String, IPlanEligibility> payerEligibilties = new HashMap<>();
    payerEligibilties.put(PayerManager.MEDICAID, new StandardMedicaidEligibility());
    payerEligibilties.put(PayerManager.MEDICARE, new StandardMedicareEligibility());
    payerEligibilties.put(PayerManager.DUAL_ELIGIBLE, new StandardDualEligibility());
    payerEligibilties.put(PlanEligibilityFinder.GENERIC, new GenericPayerEligibilty());
    payerEligibilties.put(SocialSecurityEligibilty.SOCIAL_SECURITY, new SocialSecurityEligibilty());

    // TODO - HERE IS WHERE CSV INPUT ELIGIBILITIES WOULD BE BUILT

    return payerEligibilties;
  }
}
