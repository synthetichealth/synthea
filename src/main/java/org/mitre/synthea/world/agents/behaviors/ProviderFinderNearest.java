package org.mitre.synthea.world.agents.behaviors;

import java.util.List;
import java.util.stream.Stream;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class ProviderFinderNearest implements IProviderFinder {

  @Override
  public Provider find(List<Provider> providers, Person person, EncounterType service, long time) {
    Stream<Provider> options = providers.stream()
        // Find providers that accept the person
        .filter(p -> p.accepts(person, time));

    // Find providers with the requested service, if one is given
    if (service != null) {
      options = options.filter(p -> p.hasService(service));
    }

    // If it's not an emergency
    if (service == null
        || !(service.equals(EncounterType.URGENTCARE) || service.equals(EncounterType.EMERGENCY))) {
      // Filter to only VA Facilities if the person is a veteran
      if (person.attributes.containsKey("veteran")) {
        options = options.filter(p -> "VA Facility".equals(p.type));
      }

      // Filter out IHS facilities if someone is not Native American
      if (! "NATIVE".equals(person.attributes.get(Person.RACE))) {
        options = options.filter(p -> ! "IHS Facility".equals(p.type));
      }
    }
    // Sort by distance
    options = options.sorted((a, b) -> {
      double distanceToA = a.getLonLat().distance(person.getLonLat());
      double distanceToB = b.getLonLat().distance(person.getLonLat());
      return Double.compare(distanceToA, distanceToB);
    });

    return options.findFirst().orElse(null);
  }
}
