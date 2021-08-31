package org.mitre.synthea.modules.covid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.Person;

public class C19VaccineAgeDistributionsTest {

  @Test
  public void loadRawDistribution() {
    C19VaccineAgeDistributions.loadRawDistribution();
    assertEquals(8, C19VaccineAgeDistributions.rawDistributions.keySet().size());
  }

  @Test
  public void populateDistributions() {
    C19VaccineAgeDistributions.loadRawDistribution();
    C19VaccineAgeDistributions.populateDistributions();
  }

  @Test
  public void testAgeRange() {
    C19VaccineAgeDistributions.AgeRange ar =
        new C19VaccineAgeDistributions.AgeRange("Ages_75+_yrs");
    assertEquals(75, ar.min);
    ar = new C19VaccineAgeDistributions.AgeRange("Ages_12-15_yrs");
    assertEquals(12, ar.min);
    assertEquals(15, ar.max);
  }

  @Test
  public void loadShotProbabilitiesByAge() {
    C19VaccineAgeDistributions.loadShotProbabilitiesByAge();
    C19VaccineAgeDistributions.AgeRange ar =
        new C19VaccineAgeDistributions.AgeRange("Ages_75+_yrs");
    double prob = C19VaccineAgeDistributions.firstShotProbByAge.get(ar);
    assertTrue(prob > 0.5);
  }

  @Test
  public void selectShotTime() {
    C19VaccineAgeDistributions.loadRawDistribution();
    C19VaccineAgeDistributions.populateDistributions();
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    long shotTime = C19VaccineAgeDistributions.selectShotTime(person, decemberFifteenth);
    assertTrue(shotTime > TestHelper.timestamp(2020, 12, 15, 0, 0, 0));
  }
}