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
    
    if (entry instanceof HealthRecord.Procedure) {
      return 500.0; // TODO: completely invented
    } else if (entry instanceof HealthRecord.Medication) {
      return 255.0;
    } else if (entry instanceof HealthRecord.Encounter) {
      
      // Encounters billed using avg prices from https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3096340/
      // Adjustments for initial or subsequent hospital visit and level/complexity/time of encounter
      // not included. Assume initial, low complexity encounter (Tables 4 & 6)
      
      String code = entry.codes.get(0).code;
      if (code.equals("183452005")) {
        // Encounter for 'checkup', Encounter for symptom, Encounter for problem, etc
        return 75.0;
      } else {
        return 125.0;
      }
    } else {
      // Immunizations, Conditions, and Allergies are all just Entries,
      // but this should only be called for Immunizations
      
      // https://www.nytimes.com/2014/07/03/health/Vaccine-Costs-Soaring-Paying-Till-It-Hurts.html
      // currently all vaccines cost $136.
      return 136.0;
    }
  }
}
