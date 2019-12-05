package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.PhysiologyValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

public class LifecycleModuleTest {
  public static boolean deathByNaturalCauses;
  
  @BeforeClass
  public static void before() {
    deathByNaturalCauses = LifecycleModule.ENABLE_DEATH_BY_NATURAL_CAUSES;
  }

  @AfterClass
  public static void after() {
    LifecycleModule.ENABLE_DEATH_BY_NATURAL_CAUSES = deathByNaturalCauses;
  }

  @Test
  public void testDeathByNaturalCauses() {
    LifecycleModule.ENABLE_DEATH_BY_NATURAL_CAUSES = true;
    Person person = new Person(0L);
    long time = System.currentTimeMillis();
    long birth = time - Utilities.convertTime("years", 100);
    person.attributes.put(Person.BIRTHDATE, birth);
    for (int i = 0; i < 100000; i++) {
      LifecycleModule.death(person, time);
    }
    assertEquals(false, person.alive(time));
  }

  @Test
  public void testLikelihoodOfDeathInputs() {
    // should handle zero to very old
    for (int age = 0; age < 100; age++) {
      double likelihood = LifecycleModule.likelihoodOfDeath(age);
      Assert.assertTrue(likelihood >= 0);
    }
  }

  @Test
  public void testAdherenceFade() {
    Person person = new Person(0L);
    long time = System.currentTimeMillis();
    person.attributes.put(Person.ADHERENCE, true);
    person.attributes.put(LifecycleModule.ADHERENCE_PROBABILITY, 1.0);
    for (int i = 0; i < 5; i++) {
      double before = (Double) person.attributes.get(LifecycleModule.ADHERENCE_PROBABILITY);
      LifecycleModule.adherence(person, time);
      double after = (Double) person.attributes.get(LifecycleModule.ADHERENCE_PROBABILITY);
      Assert.assertTrue(after < before);
    }
  }

  @Test
  public void testPercentileForBMI() {
    double percentile = LifecycleModule.percentileForBMI(18.37736191, "M", 26);
    Assert.assertEquals(0.9, percentile, 0.01);
  }

  @Test
  public void lookupGrowthChart() {
//    Uncomment to check performance.
//    long start = System.currentTimeMillis();
//    for (int i = 0; i < 1000000; i++) {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 24, 0.5);
    Assert.assertEquals(86.86160934, height, 0.01);
//    }
//    long end = System.currentTimeMillis();
//    System.out.println("Time to complete: " + (end - start));
  }

  @Test
  public void lookupHeadCircumference() {
    double head = LifecycleModule.lookupGrowthChart("head", "F", 36, 0.9);
    Assert.assertEquals(50.57, head, 0.01);
  }

  @Test
  public void testPhysiologyEnabled() {
    boolean enablePhysiology = LifecycleModule.ENABLE_PHYSIOLOGY_GENERATORS;
    LifecycleModule.ENABLE_PHYSIOLOGY_GENERATORS = true;
    Person person = new Person(0L);
    
    // Need to set some attributes for birth to work properly
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "english");
    
    LifecycleModule.birth(person, 0);
    
    // Person should have some PhysiologyValueGenerators
    Assert.assertEquals(person.vitalSigns.get(VitalSign.SYSTOLIC_BLOOD_PRESSURE).getClass(),
        PhysiologyValueGenerator.class);
    Assert.assertEquals(person.vitalSigns.get(VitalSign.DIASTOLIC_BLOOD_PRESSURE).getClass(),
        PhysiologyValueGenerator.class);
    
    LifecycleModule.ENABLE_PHYSIOLOGY_GENERATORS = enablePhysiology;
  }
}
