package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mitre.synthea.engine.Transition.DistributedTransition;
import org.mitre.synthea.engine.Transition.DistributedTransitionOption;
import org.mitre.synthea.engine.Transition.LookupTableTransition;
import org.mitre.synthea.engine.Transition.LookupTableTransitionOption;
import org.mitre.synthea.engine.lookup.LookupTable;
import org.mitre.synthea.engine.lookup.LookupTableIndex.Implementation;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/** Exercises real CSV loading and probability selection through Transition.follow(). */
@RunWith(Parameterized.class)
public class IndexedLookupTableTransitionTest {
  private static final long TIME = Instant.parse("2020-06-01T00:00:00Z").toEpochMilli();
  private final Map<String, String> originalConfig = new HashMap<>();
  private final Implementation implementation;

  /**
   * Exercise each configurable index through the real transition interface.
   * @return supported matching algorithms
   */
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> implementations() {
    return Arrays.asList(new Object[][] {
        {Implementation.SCAN}, {Implementation.AGE_BUCKETS}, {Implementation.QUADTREE}
    });
  }

  /**
   * Select the matching algorithm for this test run.
   * @param implementation index to configure
   */
  public IndexedLookupTableTransitionTest(Implementation implementation) {
    this.implementation = implementation;
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  /** Isolate table paths and implementation selection from other tests. */
  @Before
  public void configure() {
    for (String key : Arrays.asList("generate.lookup_tables",
        "generate.lookup_tables.implementation")) {
      originalConfig.put(key, Config.get(key));
    }
    Config.set("generate.lookup_tables", temporaryFolder.getRoot().getAbsolutePath() + "/");
    Config.set("generate.lookup_tables.implementation",
        implementation.name().toLowerCase(Locale.ROOT));
  }

  /** Restore process-wide configuration. */
  @After
  public void restore() {
    originalConfig.forEach((key, value) -> {
      if (value == null) {
        Config.remove(key);
      } else {
        Config.set(key, value);
      }
    });
  }

  /** A cached table must retain its schema for every transition using it. */
  @Test
  public void reusedTableRetainsItsAttributes() throws Exception {
    Path csv = temporaryFolder.newFile("shared.csv").toPath();
    Files.writeString(csv, "gender,age,A,B\nF,30-40,1,0\n");
    LookupTableTransition first = transition("shared.csv");
    LookupTableTransition second = transition("shared.csv");
    Person person = person(42);
    assertEquals("A", first.follow(person, TIME));
    assertEquals("A", second.follow(person, TIME));
  }

  /** Age/time column order and case do not change matching or date endpoint semantics. */
  @Test
  public void parsesAgeBeforeTimeAndIncludesTheWholeFinalDate() throws Exception {
    write("dates.csv", "AGE,TIME,gender,A,B\n"
        + "30-40,2020-06-01-2020-06-01,F,1,0\n");
    LookupTableTransition transition = transition("dates.csv");
    assertEquals("A", transition.follow(person(1), TIME));
    assertEquals("A", transition.follow(person(1), TIME + 86400000L - 1));
    assertEquals("B", transition.follow(person(1), TIME - 1));
    assertEquals("B", transition.follow(person(1), TIME + 86400000L));
  }

  /** Original weights, truncation and last-state fallback use the existing sampler unchanged. */
  @Test
  public void sampledDestinationsAndRandomDrawsMatchExistingDistributionRules() throws Exception {
    write("weights.csv", "gender,A,B\nF,0.2,0.3\nM,1.2,0.4\n");
    LookupTableTransition transition = transition("weights.csv");
    for (String gender : List.of("F", "M", "other")) {
      double first = gender.equals("F") ? 0.2 : gender.equals("M") ? 1.2 : 0.0;
      double second = gender.equals("F") ? 0.3 : gender.equals("M") ? 0.4 : 1.0;
      DistributedTransition reference = new DistributedTransition(Arrays.asList(
          new DistributedTransitionOption("A", first),
          new DistributedTransitionOption("B", second)));
      Person actual = person(4242);
      Person expected = person(4242);
      actual.attributes.put("gender", gender);
      for (int draw = 0; draw < 100; draw++) {
        assertEquals(reference.follow(expected, TIME), transition.follow(actual, TIME));
      }
      assertEquals(100L, actual.getCount());
      assertEquals(expected.getCount(), actual.getCount());
    }
  }

  /** Matching remains deterministic and leaves overlapping probability vectors separate. */
  @Test
  public void overlappingRowsUseTheFirstCsvVectorWithoutMergingProbabilities() throws Exception {
    write("overlap.csv", "gender,age,A,B\nF,0-150,1,0\nF,20-40,0,1\n");
    LookupTableTransition transition = transition("overlap.csv");
    Person person = person(42);
    for (int draw = 0; draw < 100; draw++) {
      assertEquals("A", transition.follow(person, TIME));
    }
    assertEquals(100L, person.getCount());
  }

  /** Condition modules receive one distribution of destination states, not nested candidates. */
  @Test
  public void resolvesOneStateTransitionDistributionFromMatchingRules() throws Exception {
    write("distributions.csv", "gender,age,A,B\nF,0-150,0.25,0.75\nF,20-40,1,0\n");
    LookupTable table = LookupTable.load(Config.get("generate.lookup_tables")
        + "distributions.csv", List.of("A", "B"), implementation);
    List<DistributedTransitionOption> distribution = table.findDistribution(person(42), TIME);
    assertEquals(2, distribution.size());
    DistributedTransition resolved = new DistributedTransition(distribution);
    DistributedTransition reference = new DistributedTransition(List.of(
        new DistributedTransitionOption("A", 0.25),
        new DistributedTransitionOption("B", 0.75)));
    Person actual = person(42);
    Person expected = person(42);
    for (int draw = 0; draw < 100; draw++) {
      assertEquals(reference.follow(expected, TIME), resolved.follow(actual, TIME));
    }
    Person unmatched = person(42);
    unmatched.attributes.put("gender", "M");
    assertTrue(table.findDistribution(unmatched, TIME).isEmpty());
    assertEquals(0L, unmatched.getCount());
  }

  /** A missing exact attribute remains a module error instead of silently using defaults. */
  @Test
  public void reportsMissingPersonAttributes() throws Exception {
    write("missing.csv", "gender,A,B\nF,1,0\n");
    LookupTableTransition transition = transition("missing.csv");
    Person person = person(1);
    person.attributes.remove("gender");
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> transition.follow(person, TIME));
    assertTrue(error.getMessage(), error.getMessage().contains("gender"));
    assertTrue(error.getMessage(), error.getMessage().contains("missing.csv"));
  }

  /** A same-named table in a different configured directory must not reuse stale data. */
  @Test
  public void cacheIncludesTheConfiguredDirectory() throws Exception {
    write("same.csv", "gender,A,B\nF,1,0\n");
    LookupTableTransition first = transition("same.csv");
    Path other = temporaryFolder.newFolder("other").toPath();
    Files.writeString(other.resolve("same.csv"), "gender,A,B\nF,0,1\n");
    Config.set("generate.lookup_tables", other.toString() + "/");
    LookupTableTransition second = transition("same.csv");
    assertEquals("A", first.follow(person(1), TIME));
    assertEquals("B", second.follow(person(1), TIME));
  }

  /** Cached tables must still reject a different transition schema. */
  @Test
  public void cacheDoesNotBypassTransitionColumnValidation() throws Exception {
    write("schema.csv", "gender,A,B\nF,1,0\n");
    transition("schema.csv");
    assertThrows(IllegalArgumentException.class, () -> transition("schema.csv", "A", "C"));
  }

  /** Selecting another implementation for an already-used filename remains supported. */
  @Test
  public void switchesImplementationsAndRejectsUnknownNames() throws Exception {
    write("switch.csv", "gender,age,A,B\nF,30-40,0.25,0.75\n");
    for (Implementation selected : Implementation.values()) {
      Config.set("generate.lookup_tables.implementation", selected.name());
      LookupTableTransition transition = transition("switch.csv");
      Person actual = person(4242);
      Person expected = person(4242);
      DistributedTransition reference = new DistributedTransition(Arrays.asList(
          new DistributedTransitionOption("A", 0.25),
          new DistributedTransitionOption("B", 0.75)));
      for (int draw = 0; draw < 20; draw++) {
        assertEquals(reference.follow(expected, TIME), transition.follow(actual, TIME));
      }
      assertEquals(expected.getCount(), actual.getCount());
    }
    Config.set("generate.lookup_tables.implementation", "unknown");
    assertThrows(IllegalArgumentException.class, () -> transition("switch.csv"));
  }

  /** Confusing the algorithm selector with the data directory gives an actionable correction. */
  @Test
  public void reportsImplementationPassedAsLookupDirectory() {
    String selected = implementation.name().toLowerCase(Locale.ROOT);
    Config.set("generate.lookup_tables", selected);
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> transition("covid-19-severity-outcomes.csv"));
    assertTrue(error.getMessage(), error.getMessage().contains("lookup data directory"));
    assertTrue(error.getMessage(), error.getMessage()
        .contains("--generate.lookup_tables.implementation=" + selected));
  }

  /** Serialized module transitions preserve their schema, index and fallback behavior. */
  @Test
  public void serializationRetainsMatchingAndDefaultTransitions() throws Exception {
    write("serialized.csv", "gender,age,A,B\nF,30-40,1,0\n");
    LookupTableTransition transition = transition("serialized.csv");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(transition);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      Transition restored = (Transition) in.readObject();
      assertEquals("A", restored.follow(person(1), TIME));
      Person unmatched = person(1);
      unmatched.attributes.put("gender", "M");
      assertEquals("B", restored.follow(unmatched, TIME));
    }
  }

  /** A table without range or exact-attribute columns applies to everyone. */
  @Test
  public void matchesTablesWithOnlyProbabilityColumns() throws Exception {
    write("unconstrained.csv", "A,B\n1,0\n");
    assertEquals("A", transition("unconstrained.csv").follow(person(1), TIME));
  }

  private void write(String name, String csv) throws Exception {
    Files.writeString(temporaryFolder.newFile(name).toPath(), csv);
  }

  private LookupTableTransition transition(String name) {
    return transition(name, "A", "B");
  }

  private LookupTableTransition transition(String name, String first, String second) {
    String json = "[{\"transition\":\"" + first + "\",\"default_probability\":0,"
        + "\"lookup_table_name\":\"" + name + "\"},"
        + "{\"transition\":\"" + second + "\",\"default_probability\":1,"
        + "\"lookup_table_name\":\"" + name + "\"}]";
    return new LookupTableTransition(Arrays.asList(Utilities.getGson().fromJson(json,
        LookupTableTransitionOption[].class)));
  }

  private Person person(long seed) {
    Person person = new Person(seed);
    person.attributes.put(Person.BIRTHDATE,
        Instant.parse("1990-01-01T00:00:00Z").toEpochMilli());
    person.attributes.put("gender", "F");
    return person;
  }
}
