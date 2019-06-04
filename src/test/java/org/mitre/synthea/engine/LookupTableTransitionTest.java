package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.engine.Logic.ActiveCondition;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class LookupTableTransitionTest {

  private long time;

  private GeneratorOptions standardGeneratorOptions;
  private int population;
  private ActiveCondition mildLookuptablitis;
  private ActiveCondition moderateLookuptablitis;
  private ActiveCondition extremeLookuptablitis;

  /**
   * Setup State tests.
   * 
   * @throws IOException On File IO errors.
   */
  @Before
  public void setup() {
    standardGeneratorOptions = new GeneratorOptions();
    this.population = 200;
    standardGeneratorOptions.population = this.population;
    // Create Mild Lookuptablitis Condition
    mildLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code> mildLookuptablitisCode = new ArrayList<Code>();
    mildLookuptablitisCode.add(new Code("SNOMED-CT", "23502007", "Mild_Lookuptablitis"));
    mildLookuptablitis.codes = mildLookuptablitisCode;
    // Create Moderate Lookuptablitis Condition
    moderateLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code> moderateLookuptablitisCode = new ArrayList<Code>();
    moderateLookuptablitisCode.add(new Code("SNOMED-CT", "23502008", "Moderate_Lookuptablitis"));
    moderateLookuptablitis.codes = moderateLookuptablitisCode;
    // Create Extreme Lookuptablitis Condition
    extremeLookuptablitis = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code> extremeLookuptablitisCode = new ArrayList<Code>();
    extremeLookuptablitisCode.add(new Code("SNOMED-CT", "23502009", "Extreme_Lookuptablitis"));
    extremeLookuptablitis.codes = extremeLookuptablitisCode;
  }

  // NOTE: The less than should be <= 50 not <= 51
  // The reason it's like this is because JUNIT converting to year truncates
  // months/days, sometimes making a 50 y/o appear to be 51.

  @Test
  public void lookUpTableTestMassachusetts() {

    standardGeneratorOptions.state = "Massachusetts";
    Generator generator = new Generator(standardGeneratorOptions);

    for (int i = 0; i < this.population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          }
          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else {
            if (person.ageInYears(time) > 50) {
              assertTrue(false);
            }
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(extremeLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else {
            if (person.ageInYears(time) > 50) {
              assertTrue(false);
            }
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
          if (moderateLookuptablitis.test(person, time)) {
            int startYear = Utilities
                .getYear(person.record.present.get(moderateLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(extremeLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else {
            if (person.ageInYears(time) > 50) {
              assertTrue(false);
            }
          }
        } else {
          if (person.ageInYears(time) > 50) {
            assertTrue("Person age: " + person.ageInYears(time), extremeLookuptablitis.test(person, time));
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
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else {
            if (person.ageInYears(time) > 50) {
              assertTrue(false);
            }
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
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else {
            if (person.ageInYears(time) > 50) {
              assertTrue(false);
            }
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else {
            if (person.ageInYears(time) > 50) {
              assertTrue(false);
            }
          }
        } else {
          if (person.ageInYears(time) > 50) {
            assertTrue("Person age: " + person.ageInYears(time), extremeLookuptablitis.test(person, time));
          }
        }
      }
    }
  }

  @Test
  public void lookUpTableTestArizona() {

    standardGeneratorOptions.state = "Arizona";
    Generator generator = new Generator(standardGeneratorOptions);

    for (int i = 0; i < this.population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")) {

          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          } else if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else {
            // assertTrue(false);
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("irish")) {
          if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
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
          } else {
            // assertTrue(false);
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {

          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
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
          } else {
            // assertTrue(false);
          }
        } else {
          if (person.ageInYears(time) > 50) {
            assertTrue("Person age: " + person.ageInYears(time), extremeLookuptablitis.test(person, time));
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
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else {
            // assertTrue(false);
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
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else {
            // assertTrue(false);
          }
        } else if (person.attributes.get(Person.ETHNICITY).equals("italian")) {
          if (mildLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(mildLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(extremeLookuptablitis.test(person, time));
          } else if (extremeLookuptablitis.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(extremeLookuptablitis.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(moderateLookuptablitis.test(person, time));
            assertFalse(mildLookuptablitis.test(person, time));
          }
        } else {
          if (person.ageInYears(time) > 50) {
            assertTrue("Person age: " + person.ageInYears(time), extremeLookuptablitis.test(person, time));
          }
        }
      }
    }
  }
}