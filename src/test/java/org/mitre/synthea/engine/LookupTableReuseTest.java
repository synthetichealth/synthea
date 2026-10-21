package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class LookupTableReuseTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private String previousDirectory;

  @BeforeClass
  public static void initializeModules() {
    // Core modules load vaccine lookup data; initialize them before changing the directory.
    Module.getModules();
  }

  @Before
  public void setUp() {
    previousDirectory = Config.get("generate.lookup_tables");
    Config.set("generate.lookup_tables", folder.getRoot().getAbsolutePath() + File.separator);
  }

  /** Restore the lookup directory so later tests use their configured resources. */
  @After
  public void tearDown() {
    if (previousDirectory == null) {
      Config.remove("generate.lookup_tables");
    } else {
      Config.set("generate.lookup_tables", previousDirectory);
    }
  }

  @Test
  public void sharedTableCanBeUsedByTwoStates() throws Exception {
    String table = table("gender,Matched,Fallback\nF,1,0\n");
    State first = state(table, 0, 1);
    State second = state(table, 0, 1);
    assertEquals("Matched", first.transition(person(), 500L));
    assertEquals("Matched", second.transition(person(), 500L));
  }

  @Test
  public void sharedTableKeepsEachReferencesDefaults() throws Exception {
    String table = table("gender,Matched,Fallback\nM,1,0\n");
    State first = state(table, 1, 0);
    State second = state(table, 0, 1);
    assertEquals("Matched", first.transition(person(), 500L));
    assertEquals("Fallback", second.transition(person(), 500L));
  }

  @Test
  public void sharedTableAllowsReorderedJsonTransitions() throws Exception {
    String table = table("gender,Matched,Fallback\nF,1,0\n");
    state(table, 0, 1);
    JsonObject definition = definition(table, 0, 1);
    JsonArray options = definition.getAsJsonArray("lookup_table_transition");
    JsonArray reversed = new JsonArray();
    reversed.add(options.get(1));
    reversed.add(options.get(0));
    definition.add("lookup_table_transition", reversed);
    State second = State.build(null, "Lookup", definition);
    assertEquals("Matched", second.transition(person(), 500L));
  }

  @Test
  public void cachedTableRejectsDifferentDestination() throws Exception {
    String table = table("gender,Matched,Fallback\nF,1,0\n");
    state(table, 0, 1);
    JsonObject definition = definition(table, 0, 1);
    definition.getAsJsonArray("lookup_table_transition").get(0).getAsJsonObject()
        .addProperty("transition", "Unrelated");
    RuntimeException error = assertThrows(RuntimeException.class,
        () -> State.build(null, "Lookup", definition));
    assertTrue(error.getMessage().contains("CSV column state name 'Matched'"));
  }

  @Test
  public void cachedTableRejectsDifferentDestinationCount() throws Exception {
    String table = table("gender,Matched,Fallback\nF,1,0\n");
    state(table, 0, 1);
    JsonObject definition = definition(table, 0, 1);
    definition.getAsJsonArray("lookup_table_transition").remove(1);
    RuntimeException error = assertThrows(RuntimeException.class,
        () -> State.build(null, "Lookup", definition));
    assertTrue(error.getMessage().contains("transition columns"));
  }

  @Test
  public void ageThenTimeWithNoOtherAttributes() throws Exception {
    String table = table("age,time,Matched,Fallback\n0-4,100-900,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void ageThenTimeWithGender() throws Exception {
    String table = table("age,time,gender,Matched,Fallback\n0-4,100-900,F,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void genderBetweenAgeAndTime() throws Exception {
    String table = table("age,gender,time,Matched,Fallback\n0-4,F,100-900,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void timeThenAgeKeepsExistingBehavior() throws Exception {
    String table = table("time,age,gender,Matched,Fallback\n100-900,0-4,F,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void ageAndTimeRangeEdgesAreInclusive() throws Exception {
    String table = table("age,time,Matched,Fallback\n0-4,100-900,1,0\n");
    State lookup = state(table, 0, 1);
    assertEquals("Matched", lookup.transition(person(), 100L));
    assertEquals("Matched", lookup.transition(person(), 900L));
    assertEquals("Fallback", lookup.transition(person(), 99L));
    assertEquals("Fallback", lookup.transition(person(), 901L));
    Person older = person();
    older.attributes.put(Person.BIRTHDATE, -315576000000L);
    assertEquals("Fallback", lookup.transition(older, 500L));
  }

  @Test
  public void ageOnlyKeepsExistingBehavior() throws Exception {
    String table = table("age,gender,Matched,Fallback\n0-4,F,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void timeOnlyKeepsExistingBehavior() throws Exception {
    String table = table("time,gender,Matched,Fallback\n100-900,F,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void ordinaryAttributeOnlyKeepsExistingBehavior() throws Exception {
    String table = table("gender,Matched,Fallback\nF,1,0\n");
    assertEquals("Matched", state(table, 0, 1).transition(person(), 500L));
  }

  @Test
  public void reusedTablesSurviveStateSerialization() throws Exception {
    String table = table("age,time,gender,Matched,Fallback\n0-4,100-900,F,1,0\n");
    State[] original = {state(table, 1, 0), state(table, 0, 1)};
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    State[] restored;
    try (ObjectInputStream input = new ObjectInputStream(
        new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (State[]) input.readObject();
    }
    for (State lookup : restored) {
      assertEquals("Matched", lookup.transition(person(), 100L));
      assertEquals("Matched", lookup.transition(person(), 900L));
    }
    assertEquals("Matched", restored[0].transition(person(), 901L));
    assertEquals("Fallback", restored[1].transition(person(), 901L));
  }

  @Test
  public void moduleCanBeLoadedAndProcessedTwiceWithTheSameTable() throws Exception {
    String table = table("gender,Matched,Fallback\nF,1,0\n");
    JsonObject definition = new JsonObject();
    definition.addProperty("name", "Shared lookup");
    JsonObject states = new JsonObject();
    JsonObject initial = definition(table, 0, 1);
    initial.addProperty("type", "Initial");
    states.add("Initial", initial);
    JsonObject terminal = new JsonObject();
    terminal.addProperty("type", "Terminal");
    states.add("Matched", terminal);
    states.add("Fallback", terminal.deepCopy());
    definition.add("states", states);

    Module first = new Module(definition, false);
    Module second = new Module(definition, false);
    Person firstPerson = person();
    Person secondPerson = person();
    assertTrue(first.process(firstPerson, 500L));
    assertTrue(second.process(secondPerson, 500L));
    assertEquals("Matched", firstPerson.history.get(0).name);
    assertEquals("Matched", secondPerson.history.get(0).name);
  }

  private String table(String contents) throws Exception {
    // Unique filenames keep the static cache independent of test execution order.
    File file = folder.newFile();
    Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    return file.getName();
  }

  private State state(String table, double matchedDefault, double fallbackDefault)
      throws Exception {
    return State.build(null, "Lookup", definition(table, matchedDefault, fallbackDefault));
  }

  private JsonObject definition(String table, double matchedDefault, double fallbackDefault) {
    JsonObject definition = new JsonObject();
    definition.addProperty("type", "Simple");
    JsonArray options = new JsonArray();
    options.add(option(table, "Matched", matchedDefault));
    options.add(option(table, "Fallback", fallbackDefault));
    definition.add("lookup_table_transition", options);
    return definition;
  }

  private JsonObject option(String table, String destination, double defaultProbability) {
    JsonObject option = new JsonObject();
    option.addProperty("lookup_table_name", table);
    option.addProperty("transition", destination);
    option.addProperty("default_probability", defaultProbability);
    return option;
  }

  private Person person() {
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    return person;
  }
}
