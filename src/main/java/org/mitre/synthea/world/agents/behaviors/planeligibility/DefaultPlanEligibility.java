package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

/**
 * DefaultPlanEligibility can be used for the No Insurance plan.
 * Everyone qualifies.
 */
public class DefaultPlanEligibility implements IPlanEligibility {
  @Override
  public boolean isPersonEligible(Person person, long time) {
    return true;
  }
}
