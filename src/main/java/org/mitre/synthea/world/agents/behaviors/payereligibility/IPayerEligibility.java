package org.mitre.synthea.world.agents.behaviors.payereligibility;

import org.mitre.synthea.world.agents.Person;

public interface IPayerEligibility {

  /**
   * Returns whether the given person meets the eligibilty criteria of this
   * algorithm at the given time.
   * @param person The person to check against.
   * @param time   The time.
   * @return Whether the person is elgible.
   */
  public boolean isPersonEligible(Person person, long time);

}
