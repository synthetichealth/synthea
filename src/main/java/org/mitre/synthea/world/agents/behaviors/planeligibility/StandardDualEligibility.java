package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard dual elgibilty criteria.
 */
public class StandardDualEligibility implements IPlanEligibility {

  @Override
  public boolean isPersonEligible(Person person, long time) {
    // This format could allow for a swap out of the medicare eligblity by altering the
    // PlanEligibilityFinder to have different Medicare/Medicaid criteria.
    String medicareEligibility = PayerManager.getGovernmentPayer(PayerManager.MEDICARE).getEligibilityName();
    String medicaidEligbility = PayerManager.getGovernmentPayer(PayerManager.MEDICARE).getEligibilityName();
    return (
      PlanEligibilityFinder
        .getPlanEligibilityAlgorithm(medicareEligibility).isPersonEligible(person, time)
        && PlanEligibilityFinder
        .getPlanEligibilityAlgorithm(medicaidEligbility).isPersonEligible(person, time));
  }
}
