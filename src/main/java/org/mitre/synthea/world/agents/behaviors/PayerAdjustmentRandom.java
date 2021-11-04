package org.mitre.synthea.world.agents.behaviors;

import java.io.Serializable;

import org.mitre.synthea.world.agents.Person;
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
  public double adjustClaim(ClaimEntry claimEntry, Person person) {
    if (person.randBoolean()) {
      double currentRate = person.rand(0.0, rate);
      claimEntry.adjustment = currentRate * claimEntry.cost;
      return claimEntry.adjustment;      
    } else {
      return 0;
    }
  }
}
