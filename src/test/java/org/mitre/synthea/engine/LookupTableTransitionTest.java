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
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.engine.Logic.ActiveCondition;
import org.mitre.synthea.engine.Transition.DirectTransition;
import org.mitre.synthea.engine.Transition.LookupTableKey;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.powermock.reflect.Whitebox;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LookupTableTransitionTest {

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
  public void lookUpTableTestMassachusetts() {

    int population = 10;
    GeneratorOptions standardGO = new GeneratorOptions();
    standardGO.population = population;
    standardGO.overflow = false;

    // Create Mild Lookuptablitis Condition
    ActiveCondition mildLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        mildLookuptablitisCode = new ArrayList<Code>();
    mildLookuptablitisCode.add(new Code("SNOMED-CT", "23502007", "Mild_Lookuptablitis"));
    mildLookuptablitis.codes = mildLookuptablitisCode;
    // Create Moderate Lookuptablitis Condition
    ActiveCondition moderateLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        moderateLookuptablitisCode = new ArrayList<Code>();
    moderateLookuptablitisCode.add(new Code("SNOMED-CT", "23502008", "Moderate_Lookuptablitis"));
    moderateLookuptablitis.codes = moderateLookuptablitisCode;
    // Create Extreme Lookuptablitis Condition
    ActiveCondition extremeLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        extremeLookuptablitisCode = new ArrayList<Code>();
    extremeLookuptablitisCode.add(new Code("SNOMED-CT", "23502009", "Extreme_Lookuptablitis"));
    extremeLookuptablitis.codes = extremeLookuptablitisCode;

    standardGO.state = "Massachusetts";
    Generator generator = new Generator(standardGO);

    for (int i = 0; i < population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {
          long time = System.currentTimeMillis();
          if (mildLookuptablitis.test(person, Utilities.getYear(time))) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
          long time = System.currentTimeMillis();
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(extremeLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          long time = System.currentTimeMillis();
          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(extremeLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        }
      } else {
        // Person is FEMALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {
          long time = System.currentTimeMillis();
          if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(mildLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
          long time = System.currentTimeMillis();
          if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(mildLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          long time = System.currentTimeMillis();
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        }
      }
    }
  }

  @Test
  public void lookUpTableTestArizona() throws Exception {

    // Hack in the lookuptable_test.json module
    Map<String, Module.ModuleSupplier> modules =
        Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = TestHelper.getFixture("lookuptable_test.json");
    modules.put("lookuptable_test", new Module.ModuleSupplier(lookuptabletestModule));

    int population = 10;
    GeneratorOptions standardGO = new GeneratorOptions();
    standardGO.population = population;
    standardGO.overflow = false;

    // Create Mild Lookuptablitis Condition
    ActiveCondition mildLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        mildLookuptablitisCode = new ArrayList<Code>();
    mildLookuptablitisCode.add(new Code("SNOMED-CT", "23502007", "Mild_Lookuptablitis"));
    mildLookuptablitis.codes = mildLookuptablitisCode;
    // Create Moderate Lookuptablitis Condition
    ActiveCondition moderateLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        moderateLookuptablitisCode = new ArrayList<Code>();
    moderateLookuptablitisCode.add(new Code("SNOMED-CT", "23502008", "Moderate_Lookuptablitis"));
    moderateLookuptablitis.codes = moderateLookuptablitisCode;
    // Create Extreme Lookuptablitis Condition
    ActiveCondition extremeLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        extremeLookuptablitisCode = new ArrayList<Code>();
    extremeLookuptablitisCode.add(new Code("SNOMED-CT", "23502009", "Extreme_Lookuptablitis"));
    extremeLookuptablitis.codes = extremeLookuptablitisCode;

    standardGO.state = "Arizona";
    Generator generator = new Generator(standardGO);

    for (int i = 0; i < population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {
          long time = System.currentTimeMillis();
          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
          long time = System.currentTimeMillis();
          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(extremeLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          long time = System.currentTimeMillis();
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(extremeLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        }
      } else {
        // Person is FEMALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {
          long time = System.currentTimeMillis();
          if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(mildLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
          long time = System.currentTimeMillis();
          if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(mildLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          long time = System.currentTimeMillis();
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present
                .get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        }
      }
    }
    modules.remove("lookuptable_test");
  }

  @Test
  public void zzInvalidAgeRangeTest() {
    try {
      TestHelper.getFixture("lookuptable_agerangetest.json");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("Age Range must be in the form: 'ageLow-ageHigh'"));
    }
  }

  @Test
  public void aaNoTransitionMatchTest() {
    try {
      TestHelper.getFixture("lookuptable_nomatchcolumn.json");
    } catch (Exception e) {
      assertTrue(e.getMessage().contains("CSV column state name"));
      assertTrue(
          e.getMessage().contains("does not match a JSON state to transition to in CSV table"));
    }
  }
}

