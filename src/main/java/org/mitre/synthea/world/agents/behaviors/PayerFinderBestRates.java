package org.mitre.synthea.world.agents.behaviors;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
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
   * @param service The service required. For example, EncounterType.AMBULATORY.
   *                Determines if the payer covers that service (TODO)
   * @param time    The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  @Override
  public Payer find(List<Payer> payers, Person person, EncounterType service, long time) {
    List<Payer> options = new ArrayList<Payer>();

    // occupation determines whether their employer will pay for insurance after the mandate.
    double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);

    for (Payer payer : payers) {
      if (payer.accepts(person, time)
          && (person.canAfford(payer) || (time >= mandateTime && occupation >= mandateOccupation))
          && payer.isInNetwork(null)
          && (payer.coversService(service))) {

        // If the monthly premium to coverage ratio of this company is better than the
        // current best, choose this company. Also make decision based on whether this
        // person has lots of pre-existing conditions, in which case a higher monthly
        // premium and lower copay would be more cost effective.

        options.add(payer);
      }
    }

    if (options.isEmpty()) {
      return Payer.noInsurance;
    } else if (options.size() == 1) {
      return options.get(0);
    } else {
      // There are a few equally good options, pick one randomly.
      return options.get(person.randInt(options.size()));
    }
  }
}