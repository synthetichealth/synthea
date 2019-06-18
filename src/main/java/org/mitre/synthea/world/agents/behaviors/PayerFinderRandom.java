package org.mitre.synthea.world.agents.behaviors;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Find a particular provider by service.
 */
public class PayerFinderRandom implements IPayerFinder {
  /**
   * Find a provider with a specific service for the person.
   * @param payers The list of eligible payers.
   * @param person The patient who requires the service.
   * @param service The service required. Determines if the payer covers that service (TODO)
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  @Override
  public Payer find(List<Payer> payers, Person person, EncounterType service, long time) {
    List<Payer> options = new ArrayList<Payer>();

    // TODO: Must be within provider network

    for (Payer payer : payers) {
      if (payer.accepts(person, time)
          && (payer.coversService(service) || service == null)) {
        options.add(payer);
      }
    }

    if (options.isEmpty()) {
      return null;
    } else if (options.size() == 1) {
      return options.get(0);
    } else {
      // there are a few equally good options, pick one randomly.
      return options.get(person.randInt(options.size()));
    }
  }
}
