package org.mitre.synthea.export.rif;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;

/**
 * Utility class for consolidating multiple claim lines of the same type for the same clinician.
 */
public class ConsolidatedClaimLines extends Claim.ClaimCost {

  public static class ConsolidatedClaimLine extends Claim.ClaimCost {

    private int count;
    private final String code;
    private final String revCntr;
    private final Clinician clinician;
    private final long start;

    /**
     * Construct a new consolidated claim line.
     * @param initial initial costs
     * @param code claim code
     * @param revCntr revenue center code
     * @param encounter the encounter which is used to extract the clinician and start time
     */
    public ConsolidatedClaimLine(Claim.ClaimCost initial, String code, String revCntr,
            Encounter encounter) {
      super(initial);
      this.code = code;
      this.revCntr = revCntr;
      this.clinician = encounter.clinician;
      this.start = encounter.start;
      count = 1;
    }

    @Override
    public void addCosts(Claim.ClaimCost other) {
      super.addCosts(other);
      count++;
    }

    public int getCount() {
      return count;
    }

    public String getCode() {
      return code;
    }

    public String getRevCntr() {
      return revCntr;
    }

    public Clinician getClinician() {
      return clinician;
    }

    public long getStart() {
      return start;
    }
  }

  private Map<String, ConsolidatedClaimLine> uniqueLineItems;

  public ConsolidatedClaimLines() {
    // use a sorted map to ensure we always emit claim lines in the same order
    uniqueLineItems = new TreeMap<>();
  }

  /**
   * Add a claim to the set of consolidated claim lines. Creates a new claim line if the
   * clinician, hcpcsCode or revCntr are different for any existing consolidated claim lines.
   * @param hcpcsCode HCPCS code
   * @param revCntr revenue center
   * @param cost claim costs
   * @param encounter the encounter which is used to obtain the clinician
   */
  public void addClaimLine(String hcpcsCode, String revCntr, Claim.ClaimCost cost,
          Encounter encounter) {
    if (hcpcsCode == null) {
      hcpcsCode = ""; // TreeMap doesn't like null keys unless you provide a custom comparator
    }
    if (revCntr == null) {
      revCntr = "";
    }
    String clinicianId = "";
    if (encounter.clinician != null && encounter.clinician.npi != null) {
      clinicianId = encounter.clinician.npi;
    }
    String key = clinicianId + '-' + hcpcsCode + '-' + revCntr;
    if (uniqueLineItems.containsKey(key)) {
      uniqueLineItems.get(key).addCosts(cost);
    } else {
      uniqueLineItems.put(key, new ConsolidatedClaimLine(cost, hcpcsCode, revCntr, encounter));
    }
    this.addCosts(cost);
  }

  public Collection<ConsolidatedClaimLine> getLines() {
    return uniqueLineItems.values();
  }
}
