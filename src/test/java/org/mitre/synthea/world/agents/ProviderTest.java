package org.mitre.synthea.world.agents;

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
    Provider provider = Provider.findClosestService(person, Provider.INPATIENT);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestAmbulatoryInState() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", null);
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.AMBULATORY);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestEmergencyInState() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", null);
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.EMERGENCY);
    Assert.assertNotNull(provider);
  }
  
  @Test
  public void testNearestInpatientInCity() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", "Bedford");
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.INPATIENT);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestAmbulatoryInCity() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", "Bedford");
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.AMBULATORY);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNearestEmergencyInCity() {
    Provider.loadProviders("Massachusetts");
    Person person = new Person(0L);
    Location location = new Location("Massachusetts", "Bedford");
    location.assignPoint(person, location.randomCityName(person.random));
    Provider provider = Provider.findClosestService(person, Provider.EMERGENCY);
    Assert.assertNotNull(provider);
  }
}
