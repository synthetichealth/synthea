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
    Provider.loadProviders("MA");
    Assert.assertNotNull(Provider.getProviderList());
    Assert.assertFalse(Provider.getProviderList().isEmpty());
  }

  @Test
  public void testLoadProvidersByStateName() {
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
