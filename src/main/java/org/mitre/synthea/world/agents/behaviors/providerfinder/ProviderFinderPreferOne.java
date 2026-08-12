package org.mitre.synthea.world.agents.behaviors.providerfinder;

import java.util.List;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Finder that prioritizes a single provider based on NPI specified in configuration.
 * If the preferred provider is not available or suitable, it falls back to the nearest provider.
 */
public class ProviderFinderPreferOne implements IProviderFinder {

  private static final String PREFER_ONE_NPI = "generate.providers.prefer_one.npi";
  private static final String PREFER_ONE_IGNORE_SUITABLE = "generate.providers.prefer_one.ignore_suitable";
  // Fallback finder if the preferred one isn't suitable during encounter finding
  private final IProviderFinder fallbackFinder = new ProviderFinderNearest();
  // Cache for the preferred provider to avoid repeated lookups
  private static Provider cachedPreferredProvider = null;
  // Track the NPI that was used to find the cached provider
  private static String cachedNpi = null;

  public static boolean isUsingPreferredProvider() {
    return Config.get("generate.providers.selection_behavior", "nearest").equals(Provider.PREFER_ONE);
  }

  public static boolean isIgnoringSuitable() {
    return Boolean.valueOf(Config.get(PREFER_ONE_IGNORE_SUITABLE, "false"));
  }

  public static String getPreferredNPI() {
    return isUsingPreferredProvider() ? Config.get(PREFER_ONE_NPI, null) : null;
  }

  
  
  @Override
  public Provider find(List<Provider> providers, Person person, EncounterType service, long time) {

    String preferredNpi = getPreferredNPI();

    if (preferredNpi != null && !preferredNpi.isEmpty()) {
      // first check the list passed in (if the states line up with the detault state, e.g., MA, then it may be found in the list)
      // if it's not found in the list then look across all providers. The state will be updated on the patient and generator.
      Provider provider = findPreferredProvider(providers, preferredNpi, person, service, time);
      if (provider == null) provider = findPreferredProvider(Provider.getProviderList(), preferredNpi, person, service, time);
      if (provider != null) return provider;
    }

    // If preferred NPI not set, not found in the list, or not suitable, use the fallback finder.
    return fallbackFinder.find(providers, person, service, time);
  }

  private Provider findPreferredProvider(List<Provider> providers, String preferredNpi, Person person, EncounterType service, long time) {

    if (!isUsingPreferredProvider()) return null;

    if (preferredNpi == null || preferredNpi.isEmpty()) {
      throw new ExceptionInInitializerError("ERROR: generate.providers.selection_behavior=PreferOne but " + PREFER_ONE_NPI + " is not set. Using demographic location.");
    }
  
    // Check if we already have a cached provider with the correct NPI
    if (cachedPreferredProvider != null && preferredNpi.equals(cachedNpi)) {
      return cachedPreferredProvider;
    }
  
    for (Provider provider : providers) {

        // Check if this provider matches the preferred NPI
        if (preferredNpi.equals(provider.npi)) {

            cachedPreferredProvider = provider;
            cachedNpi = provider.npi;
          
            // Check if the preferred provider offers the service and accepts the patient
            if (provider.hasService(service) && provider.accepts(person, time)) {
                return provider;
            } else {
                if (isIgnoringSuitable()) return provider;
                // Preferred provider found but is not suitable (doesn't offer service or accept patient)
                // Break the loop and proceed to fallback.
                break;
            }
        }
    }
    cachedPreferredProvider = null;
    cachedNpi = null;
    return null;
  }

}
