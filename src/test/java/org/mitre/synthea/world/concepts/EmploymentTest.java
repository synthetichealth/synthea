package org.mitre.synthea.world.concepts;

import junit.framework.TestCase;
import org.mitre.synthea.world.agents.Person;

public class EmploymentTest extends TestCase {

  /**
   * Test the employment model.
   */
  public void testCheckEmployment() {
    Employment employment = new Employment(0.75);
    Person person = new Person(0);
    person.attributes.put(Person.UNEMPLOYED, false);
    employment.checkEmployment(person, 0);
    // Person becomes instantly unemployed due to the high unemployment rate.
    // Length of unemployment will be 15552000000
    assertTrue((Boolean) person.attributes.get(Person.UNEMPLOYED));
    employment.checkEmployment(person, 1000);
    // Still unemployed
    assertTrue((Boolean) person.attributes.get(Person.UNEMPLOYED));
    employment.checkEmployment(person, 2000);
    // Still unemployed
    assertTrue((Boolean) person.attributes.get(Person.UNEMPLOYED));
    employment.checkEmployment(person, 15552000001L);
    // End of unemployment
    assertFalse((Boolean) person.attributes.get(Person.UNEMPLOYED));
  }
}