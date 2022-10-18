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
  public static class ConsolidatedServicePeriod {
    private long start;
    private long stop;
    private final List<HealthRecord.Encounter> encounters;
    private final Claim.ClaimCost totalCost;
    private final Provider provider;

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
      provider = encounter.provider;
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

    public Provider getProvider() {
      return provider;
    }
  }

  private long maxSeparationTime;
  private Map<String, List<HealthRecord.Encounter>> providerEncounters;

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
   * Create a list of consolidated service periods from the encounters previously added. Each
   * member of the list is a consolidated service period for a particular provider.
   * @return the list, sorted by period start time
   */
  public List<ConsolidatedServicePeriod> getPeriods() {
    List<ConsolidatedServicePeriod> allServicePeriods = new ArrayList<>();
    for (List<HealthRecord.Encounter> encounters: providerEncounters.values()) {
      List<ConsolidatedServicePeriod> providerServicePeriods = new ArrayList<>();
      encounters.sort((e1, e2) -> {
        return (int)(e1.start - e2.start);
      });
      for (HealthRecord.Encounter encounter: encounters) {
        consolidate(encounter, providerServicePeriods, maxSeparationTime);
      }
      allServicePeriods.addAll(providerServicePeriods);
    }
    allServicePeriods.sort((e1, e2) -> {
      return (int)(e1.getStart() - e2.getStart());
    });
    return allServicePeriods;
  }
}
