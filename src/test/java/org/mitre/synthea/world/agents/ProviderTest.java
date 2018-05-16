package org.mitre.synthea.world.agents;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.world.geography.Location;

public class ProviderTest {

  @Test
  public void testLoadProvidersByAbbreviation() {
    Provider.getProviderList().clear();
    Provider.loadProviders("MA");
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
  }

  @Test
  public void testLoadProvidersByStateName() {
    Provider.getProviderList().clear();
    Provider.loadProviders("Massachusetts");
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
  }

  @Test
  public void testNearestInpatientInState() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", null);
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.INPATIENT, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestAmbulatoryInState() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", null);
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.AMBULATORY, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestEmergencyInState() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", null);
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.EMERGENCY, 0);
    Assert.assertNotNull(provider);
  }
  
  @Test
  public void testNearestInpatientInCity() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", "Bedford");
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.INPATIENT, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestAmbulatoryInCity() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", "Bedford");
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.AMBULATORY, 0);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestEmergencyInCity() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", "Bedford");
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.EMERGENCY, 0);
    Assert.assertNotNull(provider);
  }
  
  @Test
  public void testVaFacilityOnlyAcceptsVeteran() {
    Provider.loadProviders("Massachusetts");

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
    Path path = Paths.get(providersFolder.toURI());
    Files.walk(path)
         .filter(Files::isReadable)
         .filter(Files::isRegularFile)
         .filter(p -> p.toString().endsWith(".csv"))
         .forEach(t -> {
           try {
             Provider.loadProviders("Massachusetts", "MA", "providers/" + t.getFileName());
           } catch (Exception e) {
             throw new RuntimeException("Failed to load provider file " + t, e);
           }
         });
  }
}
