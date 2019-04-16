package org.mitre.synthea.world.agents.behaviors;

import java.util.ArrayList;
import java.util.List;

import org.apache.sis.geometry.DirectPosition2D;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class ProviderFinderTest {
  
  private List<Provider> providers;
  private Person person;

  @Before
  public void setup() {
    person = new Person(0L);
    DirectPosition2D coordinate = new DirectPosition2D(0, 0);
    person.attributes.put(Person.COORDINATE, coordinate);
    
    providers = new ArrayList<Provider>();
    for (int i = 1; i <= 3; i += 1) {
      Provider provider = new Provider();
      provider.id = i + "";
      provider.getCoordinates().setLocation(i, i);
      provider.quality = i;
      provider.servicesProvided.add(EncounterType.WELLNESS);
      providers.add(provider);
    }
  }

  @Test
  public void testNearest() {
    ProviderFinderNearest finder = new ProviderFinderNearest();
    Provider provider = finder.find(providers, person, EncounterType.WELLNESS, 0L);
    Assert.assertNotNull(provider);
    Assert.assertEquals("1", provider.id);
  }

  @Test
  public void testAnyNearest() {
    ProviderFinderNearest finder = new ProviderFinderNearest();
    Provider provider = finder.find(providers, person, null, 0L);
    Assert.assertNotNull(provider);
    Assert.assertEquals("1", provider.id);
  }

  @Test
  public void testManyNearest() {
    ProviderFinderNearest finder = new ProviderFinderNearest();
    List<Provider> options = new ArrayList<Provider>();
    options.addAll(providers);
    options.addAll(providers);
    Provider provider = finder.find(options, person, EncounterType.WELLNESS, 0L);
    Assert.assertNotNull(provider);
    Assert.assertEquals("1", provider.id);
  }

  @Test
  public void testNoNearest() {
    ProviderFinderNearest finder = new ProviderFinderNearest();
    List<Provider> options = new ArrayList<Provider>();
    Provider provider = finder.find(options, person, EncounterType.WELLNESS, 0L);
    Assert.assertNull(provider);
  }

  @Test
  public void testQuality() {
    ProviderFinderQuality finder = new ProviderFinderQuality();
    Provider provider = finder.find(providers, person, EncounterType.WELLNESS, 0L);
    Assert.assertNotNull(provider);
    Assert.assertEquals("3", provider.id);
  }

  @Test
  public void testAnyQuality() {
    ProviderFinderQuality finder = new ProviderFinderQuality();
    Provider provider = finder.find(providers, person, null, 0L);
    Assert.assertNotNull(provider);
    Assert.assertEquals("3", provider.id);
  }

  @Test
  public void testManyQuality() {
    ProviderFinderQuality finder = new ProviderFinderQuality();
    List<Provider> options = new ArrayList<Provider>();
    options.addAll(providers);
    options.addAll(providers);
    Provider provider = finder.find(options, person, EncounterType.WELLNESS, 0L);
    Assert.assertNotNull(provider);
    Assert.assertEquals("3", provider.id);
  }

  @Test
  public void testNoQuality() {
    ProviderFinderQuality finder = new ProviderFinderQuality();
    List<Provider> options = new ArrayList<Provider>();
    Provider provider = finder.find(options, person, EncounterType.WELLNESS, 0L);
    Assert.assertNull(provider);
  }

  @Test
  public void testRandom() {
    ProviderFinderRandom finder = new ProviderFinderRandom();
    Provider provider = finder.find(providers, person, EncounterType.WELLNESS, 0L);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testAnyRandom() {
    ProviderFinderRandom finder = new ProviderFinderRandom();
    Provider provider = finder.find(providers, person, null, 0L);
    Assert.assertNotNull(provider);
  }

  @Test
  public void testNoRandom() {
    ProviderFinderRandom finder = new ProviderFinderRandom();
    List<Provider> options = new ArrayList<Provider>();
    Provider provider = finder.find(options, person, EncounterType.WELLNESS, 0L);
    Assert.assertNull(provider);
  }
}
