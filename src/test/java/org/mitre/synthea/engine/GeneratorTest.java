package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;

public class GeneratorTest {
  @Test
  public void testGeneratorCreatesPeople() throws Exception {
    int numberOfPeople = 1;
    TestHelper.exportOff();
    Generator generator = new Generator(numberOfPeople);
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
    // there may be more than the requested number of people, because we re-generate on death

    numberOfPeople = 3;
    Config.set("generate.default_population", Integer.toString(numberOfPeople));
    generator = new Generator();
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }

  @Test
  public void testGenerateWithDatabase() throws Exception {
    TestHelper.exportOff();
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
    TestHelper.exportOff();
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
    TestHelper.exportOff();
    int numberOfPeople = 1;
    Config.set("generate.log_patients.detail", "detailed");
    Generator generator = new Generator(numberOfPeople, 0L);
    Config.set("generate.log_patients.detail", "simple");
    generator.run();
    assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
  }
}
