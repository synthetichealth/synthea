package org.mitre.synthea.world.agents.behaviors;

import java.io.Serializable;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;

/**
 * Default payer adjustment strategy -- no adjustment.
 */
public class PayerAdjustmentNone implements IPayerAdjustment, Serializable {

  private static final long serialVersionUID = 3288715364746907944L;

  @Override
  public double adjustClaim(ClaimEntry claimEntry, Person person) {
    return 0;
  }
}
