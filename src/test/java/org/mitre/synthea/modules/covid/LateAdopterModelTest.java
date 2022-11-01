package org.mitre.synthea.modules.covid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.Person;

public class LateAdopterModelTest {

  @Test
  public void willGetShot() {
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    LateAdopterModel lam = new LateAdopterModel(person, decemberFifteenth);
    assertEquals(0.1, lam.getChanceOfGettingShot(), 0.001);
    long januaryOne = TestHelper.timestamp(2021, 1, 1, 0, 0, 0);
    boolean gettingShot = lam.willGetShot(person, januaryOne);
    if (!gettingShot) {
      assertEquals(0.05, lam.getChanceOfGettingShot(), 0.001);
    }

  }

  @Test
  public void isNotGettingShot() {
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    LateAdopterModel lam = new LateAdopterModel(person, decemberFifteenth);
    lam.setChanceOfGettingShot(0.0001);
    assertTrue(lam.isNotGettingShot());
  }
}