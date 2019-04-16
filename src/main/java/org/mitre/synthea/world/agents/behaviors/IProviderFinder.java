package org.mitre.synthea.world.agents.behaviors;

import java.util.List;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Find a particular provider by service.
 */
public interface IProviderFinder {
  /**
   * Find a provider with a specific service for the person.
   * @param providers The list of eligible providers.
   * @param person The patient who requires the service.
   * @param service The service required. For example, EncounterType.AMBULATORY.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  public Provider find(List<Provider> providers, Person person, EncounterType service, long time);
}
