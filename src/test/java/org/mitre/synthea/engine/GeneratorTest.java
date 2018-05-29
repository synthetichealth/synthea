package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class GeneratorTest {
  @Before
  public void setup() {
    TestHelper.exportOff();
    Config.set("generate.only_dead_patients", "false");
  }

  @Test
  public void testGeneratorCreatesPeople() throws Exception {
    int numberOfPeople = 1;
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());

    numberOfPeople = 3;
    Config.set("generate.default_population", Integer.toString(numberOfPeople));
    generator = new Generator();
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithDatabase() throws Exception {
    int numberOfPeople = 1;
    Config.set("generate.database_type", "in-memory");
    Generator generator = new Generator(numberOfPeople, 0L);
    Config.set("generate.database_type", "none");
    assertNotNull(generator.database);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithMetrics() throws Exception {
    int numberOfPeople = 1;
    Config.set("generate.track_detailed_transition_metrics", "true");
    Generator generator = new Generator(numberOfPeople, 0L);
    Config.set("generate.track_detailed_transition_metrics", "false");
    assertNotNull(generator.metrics);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithDetailedLogLevel() throws Exception {
    int numberOfPeople = 1;
    Config.set("generate.log_patients.detail", "detailed");
    Generator generator = new Generator(numberOfPeople, 0L);
    Config.set("generate.log_patients.detail", "simple");
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }
  
  @Test
  public void testGenerateOnlyDeadPatients() throws Exception {
    Config.set("generate.only_dead_patients", "true");
    int numberOfPeople = 2;
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals(0, generator.stats.get("alive").longValue());
    assertEquals(numberOfPeople, generator.stats.get("dead").longValue());
  }
  
  @Test
  public void testGeneratePeopleDefaultLocation() throws Exception {
    int numberOfPeople = 2;
    Generator generator = new Generator(); // intentionally no args
    for (int i = 0; i < numberOfPeople; i++) {
      Person p = generator.generatePerson(i);
      assertEquals("Massachusetts", p.attributes.get(Person.STATE));
    }
  }
  
  @Test
  public void testGeneratePeopleByLocation() throws Exception {
    int numberOfPeople = 2;
    String state = "California";
    String city = "South Gate"; // the largest US city with only 1 zip code
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    opts.state = state;
    opts.city = city;
    Generator generator = new Generator(opts);
    for (int i = 0; i < numberOfPeople; i++) {
      Person p = generator.generatePerson(i);
      assertEquals(city, p.attributes.get(Person.CITY));
      assertEquals(state, p.attributes.get(Person.STATE));
      assertEquals("90280", p.attributes.get(Person.ZIP));
    }

    state = "Massachusetts";
    city = "Bedford";
    opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    opts.state = state;
    opts.city = city;
    generator = new Generator(opts);
    for (int i = 0; i < numberOfPeople; i++) {
      Person p = generator.generatePerson(i);
      assertEquals(city, p.attributes.get(Person.CITY));
      assertEquals(state, p.attributes.get(Person.STATE));
      assertEquals("01730", p.attributes.get(Person.ZIP));
    }
  }
  
  @Test
  public void testDemographicsRetry() throws Exception {
    // confirm that the demographic choices will persist if the first generated patients die
    int numberOfPeople = 4;
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = numberOfPeople;
    opts.minAge = 50;
    opts.maxAge = 100;
    Generator generator = new Generator(opts);
    generator.internalStore = new LinkedList<>();
    for (int i = 0; i < numberOfPeople; i++) {
      Person person = generator.generatePerson(i);

      
      for (int j = 0; j < generator.internalStore.size(); j++) {
        Person compare = generator.internalStore.get(j);
        
        TestHelper.assertEqual(person, compare, 
            p -> p.attributes.get(Person.CITY), "Cities are not equal");
        TestHelper.assertEqual(person, compare, 
            p -> p.attributes.get(Person.RACE), "Races are not equal");
        
        if (j < 10) {
          // only the first 10 attempts keep the same birthdate.
          // after that it picks a lower target age
          TestHelper.assertEqual(person, compare,
              p -> p.attributes.get(Person.BIRTHDATE), "Birthdates are not equal");
        }

      }
      
      generator.internalStore.clear();
    }
  }
  
}
