package org.mitre.synthea.world.concepts;

import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class Costs {

  /**
   * Load all cost data needed by the system.
   */
  public static void loadCostData() {
    // TODO: stub. fill this in once we have meaningful cost data to load
  }

  /**
   * Calculate the cost of this Procedure, Encounter, Medication, etc.
   * 
   * @param entry Entry to calculate cost of.
   * @param isFacility Whether to use facility-based cost factors.
   * @return Cost, in USD.
   */
  public static double calculateCost(Entry entry, boolean isFacility) {
    // TODO: stub. fill this in once we have meaningful cost data
    return 0.0;
  }
}
