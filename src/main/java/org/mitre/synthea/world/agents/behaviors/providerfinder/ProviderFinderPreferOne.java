package org.mitre.synthea.world.agents.behaviors.providerfinder;

import java.util.List;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import java.util.Map;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Finder that prioritizes a single provider based on NPI specified in configuration.
 * Also provides a static method to override initial person demographics based on this preference.
 * If the preferred provider is not available or suitable, it falls back to the nearest provider.
 */
public class ProviderFinderPreferOne implements IProviderFinder {

  private static final String PREFER_ONE_NPI = "generate.providers.prefer_one.npi";
  private static final String PREFER_ONE_IGNORE_SUITABLE = "generate.providers.prefer_one.ignore_suitable";
  // Fallback finder if the preferred one isn't suitable during encounter finding
  private final IProviderFinder fallbackFinder = new ProviderFinderNearest();

  public static boolean isUsingPreferredProvider() {
    return Config.get("generate.providers.selection_behavior", "nearest").equals(Provider.PREFER_ONE);
  }

  public static boolean isIgnoringSuitable() {
    return Boolean.valueOf(Config.get(PREFER_ONE_IGNORE_SUITABLE, "false"));
  }

  public static String getPreferredNPI() {
    return Config.get(PREFER_ONE_NPI, null);
  }

  public static Provider getPreferredProvider() {

    if (!isUsingPreferredProvider()) return null;


    String preferredNpi = getPreferredNPI();
    if (preferredNpi == null || preferredNpi.isEmpty()) {
      System.err.println("WARNING: generate.providers.selection_behavior=PreferOne but " + PREFER_ONE_NPI + " is not set. Using demographic location.");
      return null; // NPI not configured, do nothing.
    }

    Provider preferredProvider = null;
    // Find the preferred provider by NPI from the loaded list
    // Note: This assumes the provider is within the initially loaded set.
    for (Provider p : Provider.getProviderList()) {
      if (preferredNpi.equals(p.npi)) {
        preferredProvider = p;
        break;
      }
    }
    return preferredProvider;
  }

  /**
   * Checks configuration for the PreferOne provider setting. If enabled and the
   * preferred provider is found, overrides the City, State, and Coordinate
   * entries in the provided demographics map with the provider's location.
   * Logs warnings if the provider or its location data is not found.
   *
   * @param demoAttributes The map of demographic attributes to potentially modify.
   */
  public static void overrideDemographicsIfPreferredProvider(Map<String, Object> demoAttributes) {

    Provider preferredProvider = getPreferredProvider();

    if (preferredProvider != null) {
      // Override demographics with preferred provider's location data
      boolean cityOverridden = false;
      boolean stateOverridden = false;
      boolean coordsOverridden = false;

      if (preferredProvider.city != null && !preferredProvider.city.isEmpty()) {
        demoAttributes.put(Person.CITY, preferredProvider.city);
        cityOverridden = true;
      }
      if (preferredProvider.state != null && !preferredProvider.state.isEmpty()) {
        demoAttributes.put(Person.STATE, preferredProvider.state);
        stateOverridden = true;
      }
      // IMPORTANT: Update coordinates as well for provider finding logic
      java.awt.geom.Point2D.Double providerCoords = preferredProvider.getLonLat();
      if (providerCoords != null) {
        // Create a new Point2D object to avoid modifying the provider's instance
        demoAttributes.put(Person.COORDINATE,
            new java.awt.geom.Point2D.Double(providerCoords.getX(), providerCoords.getY()));
        coordsOverridden = true;
      }

      if (!cityOverridden || !stateOverridden || !coordsOverridden) {
        System.err.println("WARNING: Preferred provider NPI '" + preferredProvider.npi
            + "' found, but missing location data (City: " + preferredProvider.city
            + ", State: " + preferredProvider.state + ", Coords: " + providerCoords
            + "). Not all location attributes were overridden.");
      }
      // TODO: Consider updating Person.COUNTY if available? Provider doesn't store it.

    } else {
      // Log a warning if the provider wasn't found
      System.err.println("WARNING: Preferred provider NPI '" + getPreferredNPI() + "' configured but provider not found in loaded list. Using demographic location.");
    }
  }


  @Override
  public Provider find(List<Provider> providers, Person person, EncounterType service, long time) {

    String preferredNpi = Config.get(PREFER_ONE_NPI, null);

    System.out.println("PreferredNpi: " + preferredNpi);

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
    for (Provider provider : providers) { // Iterate the passed-in list
        // Check if this provider matches the preferred NPI
        if (preferredNpi.equals(provider.npi)) {
            System.out.println("FOUND PROVIDER: " + provider.npi + " -- " + preferredNpi);

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
    return null;
}
  
}
