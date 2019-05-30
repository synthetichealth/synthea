package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
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
  private ActiveCondition lymeCondition;
  private ActiveCondition superLymeCondition;

  /**
   * Setup State tests.
   * 
   * @throws IOException On File IO errors.
   */
  @Before
  public void setup() throws IOException {
    standardGeneratorOptions = new GeneratorOptions();
    this.population = 100;
    standardGeneratorOptions.population = this.population;
    // Create Lyme Condition
    lymeCondition = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code> lymeDiseaseCode = new ArrayList<Code>();
    lymeDiseaseCode.add(new Code("SNOMED-CT", "23502006", "Onset_Of_Lyme"));
    lymeCondition.codes = lymeDiseaseCode;
    // Create SuperLyme Condition
    superLymeCondition = new ActiveCondition();
    List<org.mitre.synthea.world.concepts.HealthRecord.Code> superLymeDiseaseCode = new ArrayList<Code>();
    superLymeDiseaseCode.add(new Code("SNOMED-CT", "23502007", "Onset_Of_SuperLyme"));
    superLymeCondition.codes = superLymeDiseaseCode;

  }

  // ISSUE: The less than should be <= 50 not <= 51
  // The reason it's like this is because converting to year truncates months/days, making a 50 y/o appear to be 51.

  @Test
  public void lookUpTableTestMassachusetts() {

    standardGeneratorOptions.state = "Massachusetts";
    Generator generator = new Generator(standardGeneratorOptions);

    for (int i = 0; i < this.population; i++) {
      // Generate People
      Person person = generator.generatePerson(i);

      if (person.attributes.get(Person.GENDER).equals("M")) {
        // Person is MALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")){
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(lymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("irish")){
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition <= 51);
            assertFalse(lymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("italian")){
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(lymeCondition.test(person, time));
          }
        }
        else{
          assertFalse(lymeCondition.test(person, time));
          assertFalse(superLymeCondition.test(person, time));
        }
      }
      else {
        // Person is FEMALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("irish")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("italian")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else{
          assertFalse(lymeCondition.test(person, time));
          assertFalse(superLymeCondition.test(person, time));
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
        if (person.attributes.get(Person.ETHNICITY).equals("english")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("irish")){
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(lymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("italian")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition <= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else{
          assertFalse(lymeCondition.test(person, time));
          assertFalse(superLymeCondition.test(person, time));
        }
      }
      else {
        // Person is FEMALE
        if (person.attributes.get(Person.ETHNICITY).equals("english")){
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition <= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition >= 51);
            assertFalse(lymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("irish")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition <= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else if (person.attributes.get(Person.ETHNICITY).equals("italian")){
          if (superLymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(superLymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition, personAgeOfCondition >= 51);
            assertFalse(lymeCondition.test(person, time));
          }
          if (lymeCondition.test(person, time)) {
            int startYear = Utilities.getYear(person.record.present.get(lymeCondition.codes.get(0).code).start);
            int birthYear = Utilities.getYear((long) person.attributes.get(Person.BIRTHDATE));
            int personAgeOfCondition = startYear - birthYear;
            assertTrue("Age of Condition: " + personAgeOfCondition ,personAgeOfCondition <= 51);
            assertFalse(superLymeCondition.test(person, time));
          }
        }
        else{
          assertFalse(lymeCondition.test(person, time));
          assertFalse(superLymeCondition.test(person, time));
        }
      }
    }
  }
}