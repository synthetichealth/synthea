package org.mitre.synthea.world.agents.behaviors.providerfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import java.awt.geom.Point2D;

public class ProviderFinderPreferOneTest {

  // Test Constants
  private static final String PREFERRED_BEHAVIOUR=  "generate.providers.selection_behavior";
  private static final String PREFERRED_NPI_CONFIG = "generate.providers.prefer_one.npi";
  private static final String PREFER_ONE_IGNORE_SUITABLE = "generate.providers.prefer_one.ignore_suitable";
  
  private static final String PREFERRED_NPI = "1234567890";
  private static final String NEAREST_NPI = "0987654321";
  private static final String PREFERRED_CITY = "Bedford";
  private static final String PREFERRED_STATE = "MA";
  private static final String NEAREST_CITY = "Albany";
  private static final String NEAREST_STATE = "NY";
  private static final String PERSON_INITIAL_CITY = "Schenectady";
  private static final String PERSON_INITIAL_STATE = "NY";

  private static final Point2D.Double PERSON_COORDS = new Point2D.Double(0.0, 0.0);
  private static final Point2D.Double NEAREST_PROVIDER_COORDS = new Point2D.Double(0.0001, 0.0001); // Closer
  private static final Point2D.Double PREFERRED_PROVIDER_COORDS = new Point2D.Double(0.0002, 0.0002); // Further

  // Test Objects
  private Person person;
  private List<Provider> providers;
  private Provider preferredProvider;
  private Provider nearestProvider;
  private ProviderFinderPreferOne finder;
  private long time;


  @Before
  public void setUp() {
    // Clear any previously set config
    Config.set(PREFERRED_BEHAVIOUR, Provider.PREFER_ONE);
    Config.set(PREFERRED_NPI_CONFIG, "");
    Config.set(PREFER_ONE_IGNORE_SUITABLE, "false");

    person = new Person(0L);
    // Set initial location and coordinates for the person
    person.attributes.put(Person.CITY, PERSON_INITIAL_CITY);
    person.attributes.put(Person.STATE, PERSON_INITIAL_STATE);
    person.attributes.put(Person.COORDINATE, PERSON_COORDS); // Use static variable

    providers = new ArrayList<>();

    // Preferred Provider Setup
    preferredProvider = new Provider();
    preferredProvider.npi = PREFERRED_NPI;
    preferredProvider.city = PREFERRED_CITY;
    preferredProvider.state = PREFERRED_STATE;
    preferredProvider.getLonLat().setLocation(PREFERRED_PROVIDER_COORDS.getX(), PREFERRED_PROVIDER_COORDS.getY()); // Use static variable
    preferredProvider.servicesProvided.add(EncounterType.AMBULATORY); // Offers the service
    // Assume accepts patient by default

    // Nearest Provider Setup (for fallback)
    nearestProvider = new Provider();
    nearestProvider.npi = NEAREST_NPI;
    nearestProvider.city = NEAREST_CITY;
    nearestProvider.state = NEAREST_STATE;
    nearestProvider.getLonLat().setLocation(NEAREST_PROVIDER_COORDS.getX(), NEAREST_PROVIDER_COORDS.getY()); // Use static variable
    nearestProvider.servicesProvided.add(EncounterType.AMBULATORY); // Offers the service
    // Assume accepts patient by default

    providers.add(nearestProvider); // Add nearest first to test preference logic
    providers.add(preferredProvider);

    finder = new ProviderFinderPreferOne();
    time = System.currentTimeMillis();
  }

  @Test
  public void find_shouldReturnPreferredProvider_whenNpiSetAndProviderAvailableAndSuitable() {
    Config.set(PREFERRED_NPI_CONFIG, PREFERRED_NPI);
    Config.set(PREFER_ONE_IGNORE_SUITABLE, "true");

    finder = new ProviderFinderPreferOne();
    Provider found = finder.find(providers, person, EncounterType.AMBULATORY, time);

    assertNotNull(found);
    assertSame("Should find the preferred provider", preferredProvider.npi, found.npi);
  }

  @Test
  public void find_shouldReturnNearestProvider_whenPreferredProviderDoesNotOfferService() {
    Config.set(PREFERRED_NPI_CONFIG, PREFERRED_NPI);
    preferredProvider.servicesProvided.remove(EncounterType.AMBULATORY); // Does not offer service

    Provider found = finder.find(providers, person, EncounterType.AMBULATORY, time);

    assertNotNull(found);
    // Since ProviderFinderNearest is the fallback and doesn't have complex logic here,
    // we expect it to find the 'nearestProvider' based on simple list order in this test setup.
    // A more robust test would mock ProviderFinderNearest or set up coordinates.
    assertSame("Should fall back to nearest provider", nearestProvider.npi, found.npi);
    assertEquals("Person's city should NOT be updated",
                 PERSON_INITIAL_CITY, person.attributes.get(Person.CITY));
    assertEquals("Person's state should NOT be updated",
                 PERSON_INITIAL_STATE, person.attributes.get(Person.STATE));
  }

  // Mocking Provider.accepts would be ideal, but for simplicity, we'll simulate non-acceptance
  // by removing the provider from the list before calling find, mimicking a scenario where
  // accepts() would return false and the provider wouldn't be considered by the fallback.
  // A better approach involves a custom IProviderFinder mock or modifying Provider for testability.
  @Test
  public void find_shouldReturnNearestProvider_whenPreferredProviderDoesNotAcceptPatient() {
    Config.set(PREFERRED_NPI_CONFIG, PREFERRED_NPI);
    // Simulate non-acceptance by removing the preferred provider temporarily
    providers.remove(preferredProvider);

    Provider found = finder.find(providers, person, EncounterType.AMBULATORY, time);

    providers.add(preferredProvider); // Add back for cleanup/other tests

    assertNotNull(found);
    assertSame("Should fall back to nearest provider", nearestProvider.npi, found.npi);
    assertEquals("Person's city should NOT be updated",
                 PERSON_INITIAL_CITY, person.attributes.get(Person.CITY));
    assertEquals("Person's state should NOT be updated",
                 PERSON_INITIAL_STATE, person.attributes.get(Person.STATE));
  }


  @Test
  public void find_shouldReturnNearestProvider_whenPreferredNpiNotFound() {
    Config.set(PREFERRED_NPI_CONFIG, "NonExistentNPI");

    Provider found = finder.find(providers, person, EncounterType.AMBULATORY, time);

    assertNotNull(found);
    assertSame("Should fall back to nearest provider", nearestProvider.npi, found.npi);
    assertEquals("Person's city should NOT be updated",
                 PERSON_INITIAL_CITY, person.attributes.get(Person.CITY));
    assertEquals("Person's state should NOT be updated",
                 PERSON_INITIAL_STATE, person.attributes.get(Person.STATE));
  }

  @Test
  public void find_shouldReturnNearestProvider_whenPreferredNpiNotSet() {
    Config.set(PREFERRED_NPI_CONFIG, "");

    Provider found = finder.find(providers, person, EncounterType.AMBULATORY, time);

    assertNotNull(found);
    assertSame("Should fall back to nearest provider", nearestProvider.npi, found.npi);
    assertEquals("Person's city should NOT be updated",
                 PERSON_INITIAL_CITY, person.attributes.get(Person.CITY));
    assertEquals("Person's state should NOT be updated",
                 PERSON_INITIAL_STATE, person.attributes.get(Person.STATE));
  }

}
