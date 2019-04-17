package org.mitre.synthea.world.agents.behaviors;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class ProviderFinderRandom implements IProviderFinder {

  @Override
  public Provider find(List<Provider> providers, Person person, EncounterType service, long time) {
    List<Provider> options = new ArrayList<Provider>();

    for (Provider provider : providers) {
      if (provider.accepts(person, time)
          && (provider.hasService(service) || service == null)) {
        options.add(provider);
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
