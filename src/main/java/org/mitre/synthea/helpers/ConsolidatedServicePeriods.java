package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.List;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Utility class to group encounters separated by less than a configurable amount of time.
 */
public class ConsolidatedServicePeriods {
  public static class ConsolidatedServicePeriod {
    private long start;
    private long stop;
    private final List<HealthRecord.Encounter> encounters;
    private final Claim.ClaimCost totalCost;

    /**
     * Create a new instance initialized with the time bounds and costs of the supplied encounter.
     * @param encounter used to initialize the service period
     */
    public ConsolidatedServicePeriod(HealthRecord.Encounter encounter) {
      start = encounter.start;
      stop = encounter.stop;
      encounters = new ArrayList<>();
      encounters.add(encounter);
      totalCost = new Claim.ClaimCost(encounter.claim.totals);
    }

    /**
     * Determines whether the supplied encounter is contiguous (plus or minus the supplied
     * interval) with this period.
     * @param encounter the encounter
     * @param maxSeparationTime maxx allowed separation in milliseconds
     * @return
     */
    public boolean isContiguous(HealthRecord.Encounter encounter, long maxSeparationTime) {
      return (encounter.start >= start && encounter.start <= stop + maxSeparationTime)
              || (encounter.stop <= stop && encounter.stop >= start - maxSeparationTime)
              || (encounter.start <= start && encounter.stop >= stop);
    }

    /**
     * Extend the current period to encapsulate the time bounds and costs of the supplied encounter.
     * @param encounter the encounter
     */
    public void addEncounter(HealthRecord.Encounter encounter) {
      // TODO: separate encounters by provider, clinician
      encounters.add(encounter);
      start = Math.min(start, encounter.start);
      stop = Math.max(stop, encounter.stop);
      totalCost.addCosts(encounter.claim.totals);
    }

    public List<HealthRecord.Encounter> getEncounters() {
      return encounters;
    }

    public long getStart() {
      return start;
    }

    public long getStop() {
      return stop;
    }

    public Claim.ClaimCost getTotalCost() {
      return totalCost;
    }
  }

  private long maxSeparationTime;
  private List<HealthRecord.Encounter> encounters;

  public ConsolidatedServicePeriods(long maxSeparationTime) {
    this.maxSeparationTime = maxSeparationTime;
    encounters = new ArrayList<>();
  }

  public void addEncounter(HealthRecord.Encounter encounter) {
    encounters.add(encounter);
  }

  private static void consolidate(HealthRecord.Encounter encounter,
          List<ConsolidatedServicePeriod> servicePeriods, long maxSeparationTime) {
    for (ConsolidatedServicePeriod currentPeriod: servicePeriods) {
      if (currentPeriod.isContiguous(encounter, maxSeparationTime)) {
        currentPeriod.addEncounter(encounter);
        return;
      }
    }
    ConsolidatedServicePeriod period = new ConsolidatedServicePeriod(encounter);
    servicePeriods.add(period);
  }

  /**
   * Create a list of consolidated service periods from the encounters previously added.
   * @return the list
   */
  public List<ConsolidatedServicePeriod> getPeriods() {
    List<ConsolidatedServicePeriod> servicePeriods = new ArrayList<>();
    encounters.sort((e1, e2) -> {
      return (int)(e1.start - e2.start);
    });
    for (HealthRecord.Encounter encounter: encounters) {
      consolidate(encounter, servicePeriods, maxSeparationTime);
    }
    return servicePeriods;
  }
}
