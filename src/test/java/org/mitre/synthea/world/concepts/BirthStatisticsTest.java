package org.mitre.synthea.world.concepts;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class BirthStatisticsTest {

  @Test
  public void testBirthStatistics() {
    Person mother = new Person(0L);
    mother.attributes.put(Person.GENDER, "F");
    mother.attributes.put("pregnant", true);
    mother.attributes.put(Person.RACE, "other");
    mother.attributes.put(Person.ETHNICITY, "hispanic");

    BirthStatistics.setBirthStatistics(mother, 0L);
    assertNotNull(mother.attributes.get(BirthStatistics.BIRTH_DATE));
    assertNotNull(mother.attributes.get(BirthStatistics.BIRTH_WEEK));
    assertNotNull(mother.attributes.get(BirthStatistics.BIRTH_SEX));
    assertNotNull(mother.attributes.get(BirthStatistics.BIRTH_HEIGHT));
    assertNotNull(mother.attributes.get(BirthStatistics.BIRTH_WEIGHT));
  }

  @Test
  public void testNoFathersAreMothers() {
    Person father = new Person(0L);
    father.attributes.put(Person.GENDER, "M");
    BirthStatistics.setBirthStatistics(father, 0L);
    assertNull(father.attributes.get(BirthStatistics.BIRTH_DATE));
    assertNull(father.attributes.get(BirthStatistics.BIRTH_WEEK));
    assertNull(father.attributes.get(BirthStatistics.BIRTH_SEX));
    assertNull(father.attributes.get(BirthStatistics.BIRTH_HEIGHT));
    assertNull(father.attributes.get(BirthStatistics.BIRTH_WEIGHT));
  }

  @Test
  public void testNoStatisticsForWomenWhoAreNotPregnant() {
    Person woman = new Person(0L);
    woman.attributes.put(Person.GENDER, "F");
    woman.attributes.put("pregnant", false);
    BirthStatistics.setBirthStatistics(woman, 0L);
    assertNull(woman.attributes.get(BirthStatistics.BIRTH_DATE));
    assertNull(woman.attributes.get(BirthStatistics.BIRTH_WEEK));
    assertNull(woman.attributes.get(BirthStatistics.BIRTH_SEX));
    assertNull(woman.attributes.get(BirthStatistics.BIRTH_HEIGHT));
    assertNull(woman.attributes.get(BirthStatistics.BIRTH_WEIGHT));
  }
}
