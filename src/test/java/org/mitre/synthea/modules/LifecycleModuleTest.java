package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.DefaultRandomNumberGenerator;
import org.mitre.synthea.helpers.PhysiologyValueGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
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
    // Uncomment to check performance.
    // long start = System.currentTimeMillis();
    // for (int i = 0; i < 1000000; i++) {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 24, 0.5);
    Assert.assertEquals(86.86160934, height, 0.01);
    // }
    // long end = System.currentTimeMillis();
    // System.out.println("Time to complete: " + (end - start));
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

  @Test
  public void testPassportFormatIsAlwaysEightDigits() throws Exception {
    Pattern eightDigitPassport = Pattern.compile("^X\\d{8}X$");
    int sampleSize = 2000;
    int issued = 0;

    java.lang.reflect.Method ageMethod = LifecycleModule.class.getDeclaredMethod(
        "age", Person.class, long.class);
    ageMethod.setAccessible(true);

    for (int i = 0; i < sampleSize; i++) {
      Person person = new Person(i);
      person.attributes.put(Person.GENDER, "F");
      person.attributes.put(Person.RACE, "white");
      person.attributes.put(Person.ETHNICITY, "english");
      long birth = 0L;
      LifecycleModule.birth(person, birth);

      long candidateTime = birth + Utilities.convertTime("years", 20);
      while (person.ageInYears(candidateTime) < 20) {
        candidateTime += Utilities.convertTime("days", 1);
      }

      ageMethod.invoke(null, person, candidateTime);

      String passport = (String) person.attributes.get(Person.IDENTIFIER_PASSPORT);
      if (passport != null) {
        issued++;
        Assert.assertTrue("Passport not 8 digits: " + passport,
            eightDigitPassport.matcher(passport).matches());
      }
    }
    Assert.assertTrue("Expected at least some passports to be issued", issued > 0);
  }

  @Test
  public void testHbA1cMedicationImpactsDoNotGoBelowPhysiologicalFloor() throws Exception {
    // regression test for https://github.com/synthetichealth/synthea/issues/1693
    // stacked diabetes drug impacts drove HbA1c to impossible (even negative) values
    PayerManager.clear();
    PayerManager.loadNoInsurance();

    Person person = new Person(0L);
    long time = System.currentTimeMillis();
    long birth = time - Utilities.convertTime("years", 55);
    person.attributes.put(Person.BIRTHDATE, birth);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put("diabetes", true);
    person.coverage.setPlanToNoInsurance(birth);
    // extend the single no-insurance record so medication claims can be created
    person.coverage.getLastPlanRecord().updateStopTime(Long.MAX_VALUE);
    // medicationStart needs a provider for the current encounter
    Provider provider = new Provider();
    for (EncounterType type : EncounterType.values()) {
      provider.servicesProvided.add(type);
    }
    RandomNumberGenerator rng = new DefaultRandomNumberGenerator(0L);
    Clinician doc = new Clinician(0L, rng, 0L, provider);
    ArrayList<Clinician> clinicians = new ArrayList<Clinician>();
    clinicians.add(doc);
    provider.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, clinicians);
    for (EncounterType type : EncounterType.values()) {
      person.setProvider(type, provider);
    }
    person.setVitalSign(VitalSign.BMI, 26.0);

    // activate every drug in DIABETES_DRUG_HBA1C_IMPACTS
    long medStart = time - Utilities.convertTime("years", 1);
    String[] drugCodes = {"860975", "897122", "1373463", "106892", "865098"};
    for (String code : drugCodes) {
      person.record.medicationStart(medStart, code, true);
    }

    java.lang.reflect.Method vitalSignsMethod = LifecycleModule.class.getDeclaredMethod(
        "calculateVitalSigns", Person.class, long.class);
    vitalSignsMethod.setAccessible(true);
    vitalSignsMethod.invoke(null, person, time);

    double hbA1c = person.getVitalSign(VitalSign.BLOOD_GLUCOSE, time);
    // baseline is 6.6 for BMI 26, impacts total -11.5, so unclamped = -4.9
    Assert.assertEquals("HbA1c must be clamped to the physiological floor",
        4.0, hbA1c, 0.0001);
    Assert.assertTrue("HbA1c must never be below the physiological floor",
        hbA1c >= 4.0);
  }
}
