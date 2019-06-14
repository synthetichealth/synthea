package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.engine.Logic.ActiveCondition;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.powermock.reflect.Whitebox;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LookupTableTransitionTest {

  protected static Module getModule(String name) {
    try {
      Path modulesFolder = Paths.get("src/test/resources/generic");
      Path logicFile = modulesFolder.resolve(name);
      JsonReader reader = new JsonReader(new FileReader(logicFile.toString()));
      JsonObject jsonModule = new JsonParser().parse(reader).getAsJsonObject();
      reader.close();
      return new Module(jsonModule, false);
    } catch (Exception e) {
      // if anything breaks, we can't fix it. throw a RuntimeException for simplicity
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Test
  public void lookUpTableTestMassachusetts() {

    int population = 30;
    GeneratorOptions standardGO = new GeneratorOptions();
    standardGO.population = population;

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
  public void lookUpTableTestArizona() {

    // Hack in the lookuptable_test.json module
    Map<String, Module.ModuleSupplier> modules =
        Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_test.json");
    modules.put("lookuptable_test", new Module.ModuleSupplier(lookuptabletestModule));

    int population = 30;
    GeneratorOptions standardGO = new GeneratorOptions();
    standardGO.population = population;

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

  @Test(expected = RuntimeException.class)
  public void zinvalidAgeRangeTest() {

    Map<String, Module.ModuleSupplier> modules =
            Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_agerangetest.json");
    modules.put("lookuptable_agerangetest", new Module.ModuleSupplier(lookuptabletestModule));

    GeneratorOptions onePersonGeneratorOption = new GeneratorOptions();
    onePersonGeneratorOption.population = 5;
    Generator generator = new Generator(onePersonGeneratorOption);

    generator.generatePerson(2);

    modules.remove("lookuptable_agerangetest");
  }

  @Test(expected = RuntimeException.class)
  public void anoTransitionMatchTest() {

    Map<String, Module.ModuleSupplier> modules =
            Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_nomatchcolumn.json");
    modules.put("lookuptable_nomatchcolumn", new Module.ModuleSupplier(lookuptabletestModule));

    GeneratorOptions onePersonGeneratorOption = new GeneratorOptions();
    onePersonGeneratorOption.population = 5;
    Generator generator = new Generator(onePersonGeneratorOption);

    generator.generatePerson(2);

    modules.remove("lookuptable_nomatchcolumn");
  }
}

