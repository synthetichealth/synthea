package org.mitre.synthea.world.agents.behaviors.payeradjustment;

import java.io.Serializable;
import java.math.BigDecimal;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;

/**
 * Default payer adjustment strategy -- no adjustment.
 */
public class PayerAdjustmentNone implements IPayerAdjustment, Serializable {

  private static final long serialVersionUID = 3288715364746907944L;

  @Override
  public BigDecimal adjustClaim(ClaimEntry claimEntry, Person person) {
    return Claim.ZERO_CENTS;
  }
}
