package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.List;

import org.mitre.synthea.world.agents.PayerManager;
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
   * @param plans  The list of eligible plans.
   * @param person  The patient who requires the service.
   * @param service The service required.
   * @param time    The date/time within the simulated world, in milliseconds.
   * @return A plan or null if none is available.
   */
  @Override
  public InsurancePlan find(List<InsurancePlan> plans, Person person,
      EncounterType service, long time) {
    int numberOfExpectedEncounters = 0;
    if (person.hasMultipleRecords) {
      for (HealthRecord record : person.records.values()) {
        numberOfExpectedEncounters += twelveMonthEncounterCount(record, time);
      }
    } else {
      numberOfExpectedEncounters = twelveMonthEncounterCount(person.defaultRecord, time);
    }

    InsurancePlan bestRatePlan = PayerManager.getNoInsurancePlan();
    BigDecimal bestExpectedRate = BigDecimal.valueOf(Double.MAX_VALUE);

    for (InsurancePlan plan : plans) {
      if ((plan.isGovernmentPlan()
          || IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time))
          && plan.accepts(person, time)) {
        // First, calculate the annual premium.
        BigDecimal expectedRate = plan.getMonthlyPremium(
            (int) person.attributes.get(Person.INCOME)).multiply(BigDecimal.valueOf(12))
            .setScale(2, RoundingMode.HALF_EVEN);
        // Second, calculate expected copays based on last years visits.
        expectedRate = expectedRate.add(
            plan.determineCopay(EncounterType.AMBULATORY.toString(), time).multiply(
                BigDecimal.valueOf(numberOfExpectedEncounters)))
                .setScale(2, RoundingMode.HALF_EVEN);
        // TODO consider deductibles, coinsurance, covered services, etc.
        if (expectedRate.compareTo(bestExpectedRate) < 0) {
          bestExpectedRate = expectedRate;
          bestRatePlan = plan;
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
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(time);
    c.add(Calendar.YEAR, -1);
    long oneYearAgo = c.getTimeInMillis();
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