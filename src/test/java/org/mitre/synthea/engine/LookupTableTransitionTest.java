package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.engine.Logic.ActiveCondition;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.powermock.reflect.Whitebox;

public class LookupTableTransitionTest {

  private long time;
  private int population;
  private ActiveCondition mildLookuptablitis;
  private ActiveCondition moderateLookuptablitis;
  private ActiveCondition extremeLookuptablitis;

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

  /**
   * Setup Conditions
   * @throws IOException On File IO errors.
   */
  @Before
  public void setup() throws IOException {

    // // Hack in the lookuptable_test.json module
    // Map<String, Module.ModuleSupplier> modules =
    //         Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // // hack to load these test modules so they can be called by the CallSubmodule state
    // Module lookuptabletestModule = getModule("lookuptable_test.json");
    // modules.put("lookuptable_test", new Module.ModuleSupplier(lookuptabletestModule));

    // standardGeneratorOptions = new GeneratorOptions();
    // this.population = 50;
    // standardGeneratorOptions.population = this.population;
    
    this.population = 100; 
    // Create Mild Lookuptablitis Condition
    mildLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        mildLookuptablitisCode = new ArrayList<Code>();
    mildLookuptablitisCode.add(new Code("SNOMED-CT", "23502007", "Mild_Lookuptablitis"));
    mildLookuptablitis.codes = mildLookuptablitisCode;
    // Create Moderate Lookuptablitis Condition
    moderateLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        moderateLookuptablitisCode = new ArrayList<Code>();
    moderateLookuptablitisCode.add(new Code("SNOMED-CT", "23502008", "Moderate_Lookuptablitis"));
    moderateLookuptablitis.codes = moderateLookuptablitisCode;
    // Create Extreme Lookuptablitis Condition
    extremeLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code>
        extremeLookuptablitisCode = new ArrayList<Code>();
    extremeLookuptablitisCode.add(new Code("SNOMED-CT", "23502009", "Extreme_Lookuptablitis"));
    extremeLookuptablitis.codes = extremeLookuptablitisCode;
  }

  @Test
  public void lookUpTableTestMassachusetts() {

    // Hack in the lookuptable_test.json module
    Map<String, Module.ModuleSupplier> modules =
            Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_test.json");
    modules.put("lookuptable_test", new Module.ModuleSupplier(lookuptabletestModule));

    GeneratorOptions massGeneratorOptions = new GeneratorOptions();
    massGeneratorOptions.population = this.population;
    massGeneratorOptions.state = "Massachusetts";
    Generator generator = new Generator(massGeneratorOptions);

    for (int i = 0; i < this.population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {
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
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
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
    modules.remove("lookuptable_test");
  }

  @Test
  public void lookUpTableTestArizona() {

    // Hack in the lookuptable_test.json module
    Map<String, Module.ModuleSupplier> modules =
            Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_test.json");
    modules.put("lookuptable_test", new Module.ModuleSupplier(lookuptabletestModule));

    GeneratorOptions arizGeneratorOptions = new GeneratorOptions();
    arizGeneratorOptions.population = this.population;
    arizGeneratorOptions.state = "Arizona";
    Generator generator = new Generator(arizGeneratorOptions);

    for (int i = 0; i < this.population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {

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
  public void invalidAgeRangeTest() {

    Map<String, Module.ModuleSupplier> modules =
            Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_agerangetest.json");
    modules.put("lookuptable_agerangetest", new Module.ModuleSupplier(lookuptabletestModule));

    GeneratorOptions onePersonGeneratorOption = new GeneratorOptions();
    onePersonGeneratorOption.population = 5;
    Generator generator = new Generator(onePersonGeneratorOption);

    generator.generatePerson(4);

    modules.remove("lookuptable_agerangetest");
  }

  @Test(expected = RuntimeException.class)
  public void noTransitionMatchTest() {

    Map<String, Module.ModuleSupplier> modules =
            Whitebox.<Map<String, Module.ModuleSupplier>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module lookuptabletestModule = getModule("lookuptable_nomatchcolumn.json");
    modules.put("lookuptable_nomatchcolumn", new Module.ModuleSupplier(lookuptabletestModule));

    GeneratorOptions onePersonGeneratorOption = new GeneratorOptions();
    onePersonGeneratorOption.population = 10;
    Generator generator = new Generator(onePersonGeneratorOption);

    generator.generatePerson(6);

    modules.remove("lookuptable_nomatchcolumn");
  }
}