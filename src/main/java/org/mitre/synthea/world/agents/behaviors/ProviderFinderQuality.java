package org.mitre.synthea.world.agents.behaviors;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class ProviderFinderQuality implements IProviderFinder {

  private IProviderFinder nearest = new ProviderFinderNearest();

  @Override
  public Provider find(List<Provider> providers, Person person, EncounterType service, long time) {
    List<Provider> options = new ArrayList<>();
    double bestQuality = Double.NEGATIVE_INFINITY;

    for (Provider provider : providers) {
      if (provider.accepts(person, time)
          && (provider.hasService(service) || service == null)) {
        if (provider.quality > bestQuality) {
          options.clear();
          options.add(provider);
          bestQuality = provider.quality;
        } else if (provider.quality == bestQuality) {
          options.add(provider);
        }
      }
    }

    if (options.isEmpty()) {
      return null;
    } else if (options.size() == 1) {
      return options.get(0);
    } else {
      return nearest.find(options, person, service, time);
    }
  }
}
