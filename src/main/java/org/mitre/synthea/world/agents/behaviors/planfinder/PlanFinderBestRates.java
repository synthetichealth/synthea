package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.List;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerController;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

/**
 * Find a particular provider by service.
 */
public class PlanFinderBestRates implements IPlanFinder {
  /**
   * Find a provider with a specific service for the person.
   *
   * @param payers  The list of eligible payers.
   * @param person  The patient who requires the service.
   * @param service The service required.
   * @param time    The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  @Override
  public InsurancePlan find(List<Payer> payers, Person person, EncounterType service, long time) {
    int numberOfExpectedEncounters = 0;
    if (person.hasMultipleRecords) {
      for (HealthRecord record : person.records.values()) {
        numberOfExpectedEncounters += twelveMonthEncounterCount(record, time);
      }
    } else {
      numberOfExpectedEncounters = twelveMonthEncounterCount(person.defaultRecord, time);
    }

    HealthRecord.Encounter dummy
        = person.record.new Encounter(time, EncounterType.AMBULATORY.toString());

    InsurancePlan bestRatePlan = PayerController.getNoInsurancePlan();
    double bestExpectedRate = Double.MAX_VALUE;

    for (Payer payer : payers) {
      for (InsurancePlan plan : payer.getPlans()) {
        if (IPlanFinder.meetsBasicRequirements(plan, person, service, time)) {
          // First, calculate the annual premium.
          double expectedRate = (plan.getMonthlyPremium() * 12.0);
          // Second, calculate expected copays based on last years visits.
          expectedRate += (plan.determineCopay(dummy) * numberOfExpectedEncounters);
          // TODO consider deductibles, coinsurance, covered services, etc.
          if (expectedRate < bestExpectedRate) {
            bestExpectedRate = expectedRate;
            bestRatePlan = plan;
          }
        }
      }
    }
    return bestRatePlan;
  }

  /**
   * Calculates the number of encounters during the last 12 months.
   * @param record The health record being examined.
   * @param time   The date/time within the simulated world, in milliseconds.
   * @return The number of encounters during the last 12 months.
   */
  protected int twelveMonthEncounterCount(HealthRecord record, long time) {
    double oneYearAgo = time - Utilities.convertTime("years", 1);
    int count = 0;
    for (int i = record.encounters.size() - 1; i >= 0; i--) {
      if (record.encounters.get(i).start >= oneYearAgo) {
        count++;
      } else {
        break;
      }
    }
    return count;
  }
}