package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Range;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Logic.ActiveCondition;
import org.mitre.synthea.engine.Transition.DirectTransition;
import org.mitre.synthea.engine.Transition.LookupTableKey;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.powermock.reflect.Whitebox;

public class LookupTableTransitionTest {

  // Lookuptablitis Conditions
  private static ActiveCondition mildLookuptablitis;
  private static ActiveCondition moderateLookuptablitis;
  private static ActiveCondition extremeLookuptablitis;
  // Modules (including lookuptablitis_test module)
  private static Map<String, Module.ModuleSupplier> modules;
 
  /**
   * Initalizes the lookuptablitis module and conditions.
   */
  @BeforeClass
  public static void setup() throws Exception {
    // Set the lookuptable CSV location to the test directory.
    Config.set("generate.lookup_tables", "generic/lookup_tables/");

    // Hack in the lookuptable_test.json module
    modules =
        Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = TestHelper.getFixture("lookuptable_test.json");
    modules.put("lookuptable_test", new Module.ModuleSupplier(lookuptabletestModule));

    /* Create Mild Lookuptablitis Condition */
    mildLookuptablitis = new ActiveCondition();
    List<Code> mildLookuptablitisCode = new ArrayList<Code>();
    mildLookuptablitisCode.add(new Code("SNOMED-CT", "23502007", "Mild_Lookuptablitis"));
    mildLookuptablitis.codes = mildLookuptablitisCode;
    /* Create Moderate Lookuptablitis Condition */
    moderateLookuptablitis = new ActiveCondition();
    List<Code> moderateLookuptablitisCode = new ArrayList<Code>();
    moderateLookuptablitisCode.add(new Code("SNOMED-CT", "23502008", "Moderate_Lookuptablitis"));
    moderateLookuptablitis.codes = moderateLookuptablitisCode;
    /* Create Extreme Lookuptablitis Condition */
    extremeLookuptablitis = new ActiveCondition();
    List<Code> extremeLookuptablitisCode = new ArrayList<Code>();
    extremeLookuptablitisCode.add(new Code("SNOMED-CT", "23502009", "Extreme_Lookuptablitis"));
    extremeLookuptablitis.codes = extremeLookuptablitisCode;
  }

  /**
   * Reset the modules and lookup tables.
   */
  @AfterClass
  public static void reset() throws Exception {
    // Set the lookuptable CSV location to the standard directory.
    Config.set("generic.lookuptables", "modules/lookup_tables");
    // Remove the lookuptable_test.json module
    modules.remove("lookuptable_test");
  }

  @Test
  public void keyTestWithAgesMatch() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = 20;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("bar");
    Range<Integer> range = Range.between(0, 30);
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertEquals(silver, gold);
    Assert.assertEquals(gold, silver);

    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertTrue(set.contains(silver));
  }

  @Test
  public void keyTestWithAgesMatchHigh() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = 30;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("bar");
    Range<Integer> range = Range.between(0, 30);
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertEquals(silver, gold);
    Assert.assertEquals(gold, silver);

    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertTrue(set.contains(silver));
  }

  @Test
  public void keyTestWithAgesMatchLow() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = 0;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("bar");
    Range<Integer> range = Range.between(0, 30);
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertEquals(silver, gold);
    Assert.assertEquals(gold, silver);

    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertTrue(set.contains(silver));
  }

  @Test
  public void keyTestCorrectMatch() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = 20;
    LookupTableKey yellow = test.new LookupTableKey(attributes, age);
    age = 50;
    LookupTableKey grey = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("bar");
    Range<Integer> range = Range.between(0, 30);
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Range<Integer> anotherRange = Range.between(31, 60);
    LookupTableKey platinum = test.new LookupTableKey(others, anotherRange);

    Assert.assertEquals(yellow, gold);
    Assert.assertEquals(gold, yellow);
    Assert.assertEquals(grey, platinum);
    Assert.assertEquals(platinum, grey);

    Assert.assertNotEquals(grey, gold);
    Assert.assertNotEquals(yellow, platinum);
    Assert.assertNotEquals(gold, platinum);
    Assert.assertNotEquals(yellow, grey);

    Map<LookupTableKey, String> map = new HashMap<LookupTableKey, String>();
    map.put(gold, "gold");
    map.put(platinum, "platinum");
    Assert.assertTrue(map.containsKey(yellow));
    Assert.assertEquals(map.get(yellow), "gold");
    Assert.assertTrue(map.containsKey(grey));
    Assert.assertEquals(map.get(grey), "platinum");
  }

  @Test
  public void keyTestWithAgesNoMatchAge() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = 40;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("bar");
    Range<Integer> range = Range.between(0, 30);
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertNotEquals(silver, gold);
    Assert.assertNotEquals(gold, silver);

    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertFalse(set.contains(silver));
  }

  @Test
  public void keyTestWithAgesNoMatchOther() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = 20;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("baz");
    Range<Integer> range = Range.between(0, 30);
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertNotEquals(silver, gold);
    Assert.assertNotEquals(gold, silver);
 
    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertFalse(set.contains(silver));
  }

  @Test
  public void keyTestWithoutAgesMatch() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = null;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("bar");
    Range<Integer> range = null;
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertEquals(silver, gold);
    Assert.assertEquals(gold, silver);

    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertTrue(set.contains(silver));
  }

  @Test
  public void keyTestWithoutAgesNoMatch() {
    DirectTransition test = new DirectTransition("test");

    List<String> attributes = new ArrayList<String>();
    attributes.add("foo");
    attributes.add("bar");
    Integer age = null;
    LookupTableKey silver = test.new LookupTableKey(attributes, age);

    List<String> others = new ArrayList<String>();
    others.add("foo");
    others.add("baz");
    Range<Integer> range = null;
    LookupTableKey gold = test.new LookupTableKey(others, range);

    Assert.assertNotEquals(silver, gold);
    Assert.assertNotEquals(gold, silver);

    Set<LookupTableKey> set = new HashSet<LookupTableKey>();
    set.add(gold);
    Assert.assertFalse(set.contains(silver));
  }

  @Test
  public void englishFemaleMassachusettsUnderFifty() {

    long birthTime = 0L;
    // Under Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 45);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "english");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.STATE, "Massachusetts");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertTrue(moderateLookuptablitis.test(person, conditionTime + 100));
    assertFalse(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void englishFemaleMassachusettsOverFifty() {

    long birthTime = 0L;
    // Over Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 55);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "english");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.STATE, "Massachusetts");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertTrue(mildLookuptablitis.test(person, conditionTime + 100));
    assertFalse(moderateLookuptablitis.test(person, conditionTime + 100));
    assertFalse(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void englishMaleMassachusettsOverFifty() {

    long birthTime = 0L;
    // Over Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 55);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "english");
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.STATE, "Massachusetts");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertFalse(moderateLookuptablitis.test(person, conditionTime + 100));
    assertTrue(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void irishMaleMassachusettsUnderFifty() {

    long birthTime = 0L;
    // Under Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 1);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "irish");
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.STATE, "Massachusetts");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertTrue(moderateLookuptablitis.test(person, conditionTime + 100));
    assertFalse(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void irishFemaleMassachusettsOverFifty() {

    long birthTime = 0L;
    // Over Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 53);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "irish");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.STATE, "Massachusetts");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertTrue(moderateLookuptablitis.test(person, conditionTime + 100));
    assertFalse(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void italianMaleArizonaOverFifty() {

    long birthTime = 0L;
    // Over Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 65);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "italian");
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.STATE, "Arizona");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertTrue(moderateLookuptablitis.test(person, conditionTime + 100));
    assertFalse(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void italianFemaleArizonaUnderFifty() {

    long birthTime = 0L;
    // Under Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 15);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "italian");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.STATE, "Arizona");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertFalse(moderateLookuptablitis.test(person, conditionTime + 100));
    assertTrue(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void doesNotMatchAnyEthnicityAttributes() {

    // If a person does not match the table's attributes, they default to Extremelookuptablitis.

    long birthTime = 0L;
    // Under Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 15);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    // 'spanish' is not accounted for in lookuptabltitis.csv.
    person.attributes.put(Person.ETHNICITY, "spanish");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.STATE, "Arizona");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertFalse(moderateLookuptablitis.test(person, conditionTime + 100));
    assertTrue(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void doesNotMatchAnyStateAttributes() {

    // If a person does not match the table's attributes, they default to Extremelookuptablitis.

    long birthTime = 0L;
    // Under Fifty
    long conditionTime = birthTime + Utilities.convertTime("years", 15);

    // Create the person with the preset attributes.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ETHNICITY, "english");
    person.attributes.put(Person.GENDER, "F");
    // 'Alaska' is not accounted for in lookuptabltitis.csv.
    person.attributes.put(Person.STATE, "Alaska");

    // Process the lookuptable_test Module.
    Module lookuptableTestModule = modules.get("lookuptable_test").get();
    lookuptableTestModule.process(person, conditionTime);

    // Make sure this person has the correct lookuptablitis.
    assertFalse(mildLookuptablitis.test(person, conditionTime + 100));
    assertFalse(moderateLookuptablitis.test(person, conditionTime + 100));
    assertTrue(extremeLookuptablitis.test(person, conditionTime + 100));
  }

  @Test
  public void invalidCsvAgeRange() {
    try {
      TestHelper.getFixture("lookuptable_agerangetest.json");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Age Range must be in the form: 'ageLow-ageHigh'"));
    }
  }

  @Test
  public void jsonToCsvNoTransitionMatch() {
    try {
      TestHelper.getFixture("lookuptable_nomatchcolumn.json");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("CSV column state name"));
      assertTrue(
          e.getMessage().contains("does not match a JSON state to transition to in CSV table"));
    }
  }
}