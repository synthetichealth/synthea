package org.mitre.synthea.world.agents.behaviors;

import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Find a particular provider by service.
 */
public interface IPayerFinder {
  /**
   * Find a provider with a specific service for the person.
   * @param payers The list of eligible payers (determined by state currently).
   * @param person The patient who requires the service.
   * @param service The service required. Determines if the payer covers that service (TODO)
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  public Payer find(List<Payer> payers, Person person, EncounterType service, long time);
}
