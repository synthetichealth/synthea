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
   * @param service The service required.
   * @param time    The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  @Override
  public Payer find(List<Payer> payers, Person person, EncounterType service, long time) {
    List<Payer> options = new ArrayList<Payer>();

    for (Payer payer : payers) {
      if (meetsBasicRequirements(payer, person, service, time)) {

        // If the monthly premium to coverage ratio of this company is better than the
        // current best, choose this company. Also make decision based on whether this
        // person has lots of pre-existing conditions, in which case a higher monthly
        // premium and lower copay would be more cost effective.

        options.add(payer);
      }
    }
    // Choose a payer from the list of options.
    return chooseRandomlyFromList(options);
  }
}