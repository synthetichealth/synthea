package org.mitre.synthea.world.agents.behaviors.planeligibility;

import java.io.Serializable;

import org.mitre.synthea.world.agents.Person;

@FunctionalInterface
public interface IPlanEligibility extends Serializable {

  /**
   * Returns whether the given person meets the eligibility criteria of this
   * algorithm at the given time.
   * @param person The person to check against.
   * @param time   The time.
   * @return Whether the person is eligible.
   */
  public boolean isPersonEligible(Person person, long time);

}
