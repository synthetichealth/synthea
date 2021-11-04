package org.mitre.synthea.world.agents.behaviors;

import java.io.Serializable;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;

/**
 * Fixed payment adjustment strategy.
 */
public class PayerAdjustmentFixed implements IPayerAdjustment, Serializable {

  private static final long serialVersionUID = -1515831606680338099L;

  /** Fixed adjustment rate. */
  private double rate;

  /**
   * Create a new fixed payer adjustment.
   * @param rate The fixed adjustment rate.
   */
  public PayerAdjustmentFixed(double rate) {
    this.rate = rate;
    if (this.rate < 0.0) {
      this.rate = 0.0;
    } else if (this.rate > 1.0) {
      this.rate = 1.0;
    }
  }

  @Override
  public double adjustClaim(ClaimEntry claimEntry, Person person) {
    claimEntry.adjustment = this.rate * claimEntry.cost;
    return claimEntry.adjustment;
  }
}
