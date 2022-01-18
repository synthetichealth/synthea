package org.mitre.synthea.world.agents.behaviors.payer_adjustment;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.health_insurance.Claim.ClaimEntry;

/**
 * Interface to abstract various claim adjustment strategies.
 */
public interface IPayerAdjustment {
  /**
   * Adjust the claim entry according to this adjustment strategy.
   * @param claimEntry The claim entry to adjust.
   * @param person The person making the claim.
   * @return The dollar amount that was deducted/adjusted/removed from this claim entry.
   */
  public double adjustClaim(ClaimEntry claimEntry, Person person);
}
