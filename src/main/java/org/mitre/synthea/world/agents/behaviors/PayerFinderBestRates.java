package org.mitre.synthea.world.agents.behaviors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Find a particular provider by service.
 */
public class PayerFinderBestRates implements IPayerFinder {
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
  public Payer find(List<Payer> payers, Person person, EncounterType service, long time) {
    int numberOfExpectedEncounters = 0;
    if (person.hasMultipleRecords) {
      for (HealthRecord record : person.records.values()) {
        numberOfExpectedEncounters += numberOfEncounterDuringLastTwelveMonths(record, time);
      }
    } else {
      numberOfExpectedEncounters =
          numberOfEncounterDuringLastTwelveMonths(person.defaultRecord, time);
    }

    HealthRecord.Encounter dummy =
        person.record.new Encounter(time, EncounterType.AMBULATORY.toString());

    Payer bestRatePayer = Payer.noInsurance;
    BigDecimal bestExpectedRate = BigDecimal.valueOf(Double.MAX_VALUE);

    for (Payer payer : payers) {
      if (IPayerFinder.meetsBasicRequirements(payer, person, service, time)) {
        // First, calculate the annual premium.
        BigDecimal expectedRate = payer.getMonthlyPremium().multiply(BigDecimal.valueOf(12))
                .setScale(2, RoundingMode.HALF_EVEN);
        // Second, calculate expected copays based on last years visits.
        expectedRate = expectedRate.add(
                payer.determineCopay(dummy).multiply(
                        BigDecimal.valueOf(numberOfExpectedEncounters)))
                .setScale(2, RoundingMode.HALF_EVEN);
        // TODO consider deductibles, coinsurance, covered services, etc.
        if (expectedRate.compareTo(bestExpectedRate) < 0) {
          bestExpectedRate = expectedRate;
          bestRatePayer = payer;
        }
      }
    }
    return bestRatePayer;
  }

  /**
   * Calculates the number of encounters during the last 12 months.
   * @param record The health record being examined.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return The number of encounters during the last 12 months.
   */
  protected int numberOfEncounterDuringLastTwelveMonths(HealthRecord record, long time) {
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