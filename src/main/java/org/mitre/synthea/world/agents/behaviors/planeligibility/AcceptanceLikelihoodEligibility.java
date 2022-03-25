package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

/**
 * A payer eligibilty algorithm that may reject someone based on an acceptance likelihood.
 */
public class AcceptanceLikelihoodEligibility implements IPlanEligibility {
  
  private final double acceptanceLikelihood;

  public AcceptanceLikelihoodEligibility(double acceptanceLikelihood) {
    this.acceptanceLikelihood = acceptanceLikelihood;
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    return person.rand() < this.acceptanceLikelihood;
  }
}
