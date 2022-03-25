package org.mitre.synthea.world.agents.behaviors.planeligibility.old;

import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planeligibility.IPlanEligibility;
import org.mitre.synthea.world.agents.behaviors.planeligibility.PlanEligibilityFinder;

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
        .getEligibilityAlgorithm(medicareEligibility).isPersonEligible(person, time)
        && PlanEligibilityFinder
        .getEligibilityAlgorithm(medicaidEligbility).isPersonEligible(person, time));
  }
}
