package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Utility class to group encounters by provider that are separated by less than a configurable
 * amount of time.
 */
public class ConsolidatedServicePeriods {
  /**
   * Represents a single consolidated service period.
   */
  public static class ConsolidatedServicePeriod {
    /** The start time of the service period in milliseconds. */
    private long start;

    /** The stop time of the service period in milliseconds. */
    private long stop;

    /** The list of encounters included in this service period. */
    private final List<HealthRecord.Encounter> encounters;

    /** The total cost of the service period. */
    private final Claim.ClaimCost totalCost;

    /** The provider associated with this service period. */
    private final Provider provider;

    /**
     * Create a new instance initialized with the time bounds and costs of the supplied encounter.
     *
     * @param encounter the encounter used to initialize the service period
     */
    public ConsolidatedServicePeriod(HealthRecord.Encounter encounter) {
      start = encounter.start;
      stop = encounter.stop;
      encounters = new ArrayList<>();
      encounters.add(encounter);
      totalCost = new Claim.ClaimCost(encounter.claim.totals);
      provider = encounter.provider;
    }

    /**
     * Determines whether the supplied encounter is contiguous (plus or minus the supplied
     * interval) with this period.
     *
     * @param encounter the encounter to check
     * @param maxSeparationTime the maximum allowed separation in milliseconds
     * @return true if the encounter is contiguous with this period, false otherwise
     */
    public boolean isContiguous(HealthRecord.Encounter encounter, long maxSeparationTime) {
      return (encounter.start >= start && encounter.start <= stop + maxSeparationTime)
              || (encounter.stop <= stop && encounter.stop >= start - maxSeparationTime)
              || (encounter.start <= start && encounter.stop >= stop);
    }

    /**
     * Extend the current period to encapsulate the time bounds and costs of the supplied encounter.
     *
     * @param encounter the encounter to add to this service period
     */
    public void addEncounter(HealthRecord.Encounter encounter) {
      // TODO: separate encounters by provider, clinician
      encounters.add(encounter);
      start = Math.min(start, encounter.start);
      stop = Math.max(stop, encounter.stop);
      totalCost.addCosts(encounter.claim.totals);
    }

    /**
     * Get the list of encounters in this service period.
     *
     * @return the list of encounters
     */
    public List<HealthRecord.Encounter> getEncounters() {
      return encounters;
    }

    /**
     * Get the start time of this service period.
     *
     * @return the start time in milliseconds
     */
    public long getStart() {
      return start;
    }

    /**
     * Get the stop time of this service period.
     *
     * @return the stop time in milliseconds
     */
    public long getStop() {
      return stop;
    }

    /**
     * Get the total cost of this service period.
     *
     * @return the total cost
     */
    public Claim.ClaimCost getTotalCost() {
      return totalCost;
    }

    /**
     * Get the provider associated with this service period.
     *
     * @return the provider
     */
    public Provider getProvider() {
      return provider;
    }
  }

  private long maxSeparationTime;
  private Map<String, List<HealthRecord.Encounter>> providerEncounters;

  /**
   * Constructor to initialize the ConsolidatedServicePeriods instance.
   *
   * @param maxSeparationTime the maximum allowed separation time in milliseconds
   */
  public ConsolidatedServicePeriods(long maxSeparationTime) {
    this.maxSeparationTime = maxSeparationTime;
    providerEncounters = new HashMap<>();
  }

  /**
   * Add the supplied encounter to the list managed by this instance.
   * @param encounter the encounter to add
   */
  public void addEncounter(HealthRecord.Encounter encounter) {
    if (!providerEncounters.containsKey(encounter.provider.npi)) {
      providerEncounters.put(encounter.provider.npi, new ArrayList<>());
    }
    providerEncounters.get(encounter.provider.npi).add(encounter);
  }

  /**
   * Add a given encounter to the appropriate consolidated service period.
   * Encounters outside of a consolidated service period will create a new period.
   *
   * @param encounter the encounter to consolidate
   * @param servicePeriods the list of existing service periods
   * @param maxSeparationTime the maximum allowed separation time in milliseconds
   */
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
   * Each member of the list is a consolidated service period for a particular provider.
   *
   * @return the list of consolidated service periods, sorted by period start time
   */
  public List<ConsolidatedServicePeriod> getPeriods() {
    List<ConsolidatedServicePeriod> allServicePeriods = new ArrayList<>();
    for (List<HealthRecord.Encounter> encounters: providerEncounters.values()) {
      List<ConsolidatedServicePeriod> providerServicePeriods = new ArrayList<>();
      encounters.sort((e1, e2) -> {
        return Long.compare(e1.start, e2.start);
      });
      for (HealthRecord.Encounter encounter: encounters) {
        consolidate(encounter, providerServicePeriods, maxSeparationTime);
      }
      allServicePeriods.addAll(providerServicePeriods);
    }
    allServicePeriods.sort((e1, e2) -> {
      return Long.compare(e1.getStart(), e2.getStart());
    });
    return allServicePeriods;
  }
}
