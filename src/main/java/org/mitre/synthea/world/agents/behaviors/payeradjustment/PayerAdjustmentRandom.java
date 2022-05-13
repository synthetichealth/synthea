package org.mitre.synthea.world.agents.behaviors.payeradjustment;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;

/**
 * Randomized payment adjustment strategy.
 */
public class PayerAdjustmentRandom implements IPayerAdjustment, Serializable {

  private static final long serialVersionUID = -5292509643177122361L;

  /** Maximum adjustment rate. */
  private double rate;

  /**
   * Create a new random payer adjustment.
   * @param rate The maximum adjustment rate.
   */
  public PayerAdjustmentRandom(double rate) {
    this.rate = rate;
    if (this.rate < 0.0) {
      this.rate = 0.0;
    } else if (this.rate > 1.0) {
      this.rate = 1.0;
    }
  }

  @Override
  public BigDecimal adjustClaim(ClaimEntry claimEntry, Person person) {
    if (person.randBoolean()) {
      double currentRate = person.rand(0.0, rate);
      claimEntry.adjustment = BigDecimal.valueOf(currentRate).multiply(claimEntry.cost)
              .setScale(2, RoundingMode.HALF_EVEN);
      return claimEntry.adjustment;
    } else {
      return Claim.ZERO_CENTS;
    }
  }
}
