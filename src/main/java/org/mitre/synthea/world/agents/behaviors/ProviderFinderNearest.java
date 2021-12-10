package org.mitre.synthea.world.agents.behaviors;

import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.agents.Provider.ProviderType;
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
      if (person.attributes.containsKey(Person.VETERAN)) {
        options = options.filter(p -> ProviderType.VETERAN.equals(p.type));
      }

      // Filter out IHS facilities if someone is not Native American
      if (! "native".equals(person.attributes.get(Person.RACE))) {
        options = options.filter(p -> ! ProviderType.IHS.equals(p.type));
      }
    }
    // Sort by distance
    Map<Double, List<Provider>> groupedByDistance =
        options.collect(groupingBy(p -> p.getLonLat().distance(person.getLonLat())));
    Optional<Double> minDistance = groupedByDistance.keySet().stream().min(Double::compare);
    if (minDistance.isPresent()) {
      List<Provider> closestProviderGroup = groupedByDistance.get(minDistance.get());
      if (closestProviderGroup.size() > 1) {
        return closestProviderGroup.get(person.randInt(closestProviderGroup.size()));
      } else {
        return closestProviderGroup.get(0);
      }
    } else {
      return null;
    }
  }
}
