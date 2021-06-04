package org.mitre.synthea.world.agents.behaviors;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;

/**
 * Default payer adjustment strategy -- no adjustment.
 */
public class PayerAdjustmentNone implements IPayerAdjustment {

  @Override
  public double adjustClaim(ClaimEntry claimEntry, Person person) {
    return 0;
  }
}
