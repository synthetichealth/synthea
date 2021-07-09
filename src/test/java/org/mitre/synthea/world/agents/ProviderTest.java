package org.mitre.synthea.world.agents;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Provider.ProviderType;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;

public class ProviderTest {

  private static Location location;
  private static Location city;

  /**
   * Setup test suite.
   * @throws Exception on configuration loading error.
   */
  @BeforeClass
  public static void setup() throws Exception {
    TestHelper.loadTestProperties();
    String testState = Config.get("test_state.default", "Massachusetts");
    String testTown = Config.get("test_town.default", "Bedford");
    city = new Location(testState, testTown);

    testState = Config.get("test_state.alternative", "Utah");
    location = new Location(testState, null);
  }

  @Before
  public void clearProviders() {
    Provider.clear();
  }

  @Test
  public void testLoadProvidersByAbbreviation() {
    Provider.loadProviders(location, 1L);
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
  }

  @Test
  public void testLoadProvidersByStateName() {
    Provider.loadProviders(location, 1L);
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
  }

  @Test
  public void testGenerateClinicianByAbbreviation() {
    Provider.loadProviders(location, 1L);
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
    Provider provider = Provider.getProviderList().get(0);
    Assert.assertNotNull(provider.clinicianMap);
    Map<String, ArrayList<Clinician>> clinicianMap = provider.clinicianMap;
    Assert.assertNotNull(clinicianMap.get("GENERAL PRACTICE"));
  }
  
  @Test
  public void testGenerateClinicianByState() {
    Provider.loadProviders(location, 1L);
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
    Provider provider = Provider.getProviderList().get(0);
    Assert.assertNotNull(provider.clinicianMap);
    Map<String, ArrayList<Clinician>> clinicianMap = provider.clinicianMap;
    Assert.assertNotNull(clinicianMap.get("GENERAL PRACTICE"));
  }
  
  @Test
  public void testAllFacilitiesHaveAnId() {
    Provider.loadProviders(location, 1L);
    for (Provider p : Provider.getProviderList()) {
      Assert.assertNotNull(p.name + " has a null ID.", p.id);
    }
  }

  @Test
  public void testNearestInpatientInState() {
    Provider.loadProviders(location, 1L);
    Person person = new Person(0L);
    location.assignPoint(person, location.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.INPATIENT, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestAmbulatoryInState() {
    Provider.loadProviders(location, 1L);
    Person person = new Person(0L);
    location.assignPoint(person, location.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.AMBULATORY, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestWellnessInState() {
    Provider.loadProviders(location, 1L);
    Person person = new Person(0L);
    location.assignPoint(person, location.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.WELLNESS, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestEmergencyInState() {
    Provider.loadProviders(location, 1L);
    Person person = new Person(0L);
    location.assignPoint(person, location.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.EMERGENCY, 0);
    Assert.assertNotNull(provider);
  }

  @Ignore("Test requires US data, and fails on international configurations.")
  @Test
  public void testNearestEmergencyInDC() {
    // DC is a good test because it has one city, Washington, with a single
    // coordinate. People in the same city have more or less the same
    // coordinate as emergency hospitals.
    Location capital = new Location("District of Columbia", null);
    Provider.loadProviders(capital, 1L);
    Person person = new Person(0L);
    capital.assignPoint(person, capital.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.EMERGENCY, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestUrgentCareInState() {
    Provider.loadProviders(location, 1L);
    Person person = new Person(0L);
    location.assignPoint(person, location.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.URGENTCARE, 0);
    Assert.assertNotNull(provider); 
  }
  
  @Test
  public void testNearestInpatientInCity() {
    Provider.loadProviders(city, 1L);
    Person person = new Person(0L);
    city.assignPoint(person, city.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.INPATIENT, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestAmbulatoryInCity() {
    Provider.loadProviders(city, 1L);
    Person person = new Person(0L);
    city.assignPoint(person, city.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.AMBULATORY, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestWellnessInCity() {
    Provider.loadProviders(city, 1L);
    Person person = new Person(0L);
    city.assignPoint(person, city.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.WELLNESS, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestEmergencyInCity() {
    Provider.loadProviders(city, 1L);
    Person person = new Person(0L);
    city.assignPoint(person, city.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.EMERGENCY, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestUrgentCareInCity() {
    Provider.loadProviders(city, 1L);
    Person person = new Person(0L);
    city.assignPoint(person, city.randomCityName(person));
    Provider provider = Provider.findService(person, EncounterType.URGENTCARE, 0);
    Assert.assertNotNull(provider);
  }

  @Ignore("VA Facilities are not guaranteed to exist with international configurations.")
  @Test
  public void testVaFacilityOnlyAcceptsVeteran() {
    Provider.loadProviders(location, 1L);
    Provider vaProvider = Provider.getProviderList()
                                  .stream()
                                  .filter(p -> "VA Facility".equals(p.type))
                                  .findFirst().get();
    Person veteran = new Person(0L);
    veteran.attributes.put("veteran", "vietnam");
    Person nonVet = new Person(1L);

    Assert.assertTrue(vaProvider.accepts(veteran, System.currentTimeMillis()));
    Assert.assertFalse(vaProvider.accepts(nonVet, System.currentTimeMillis()));
  }

  @Test
  public void testAllFiles() throws Exception {
    // just load all files and make sure they don't crash
    URL providersFolder = ClassLoader.getSystemClassLoader().getResource("providers");
    Set<EncounterType> providerServices = new HashSet<EncounterType>();
    providerServices.add(EncounterType.WELLNESS);
    Path path = Paths.get(providersFolder.toURI());
    Files.walk(path)
         .filter(Files::isReadable)
         .filter(Files::isRegularFile)
         .filter(p -> p.toString().endsWith(".csv"))
         .forEach(t -> {
           try {
             Provider.clear();
             Provider.loadProviders(location, "providers/" + t.getFileName(),
                 ProviderType.HOSPITAL, providerServices, 1L);
           } catch (Exception e) {
             throw new RuntimeException("Failed to load provider file " + t, e);
           }
         });
  }
  
  @Test
  public void testNPICreation() {
    Assert.assertEquals("1234567893", Provider.toNPI(123_456_789L));
  }
}
