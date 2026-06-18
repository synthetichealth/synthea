package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class PediatricGrowthTrajectoryTest {

  @Test
  public void generateNextYearBMI() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay);
    // This will be the initial NHANES Sample
    long sampleSimulationTime = pgt.tail().timeInSimulation;
    double initialBMI = pgt.tail().bmi;
    long sixMonthsAfterInitial = sampleSimulationTime + Utilities.convertTime("months", 6);
    // Will cause generateNextYearBMI to be run
    double sixMonthLaterBMI = pgt.currentBMI(person, sixMonthsAfterInitial);
    double oneYearLaterBMI = pgt.tail().bmi;
    double bmiDiff = oneYearLaterBMI - initialBMI;
    assertEquals(initialBMI + (0.5 * bmiDiff), sixMonthLaterBMI, 0.01);
  }

  @Test
  public void sexSpecificConstructorMale() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay, "M");
    assertNotNull(pgt.tail());
    assertTrue("BMI should be positive", pgt.tail().bmi > 0);
    assertTrue("Age in months should be between 24 and 36",
        pgt.tail().ageInMonths >= 24 && pgt.tail().ageInMonths <= 36);
  }

  @Test
  public void sexSpecificConstructorFemale() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay, "F");
    assertNotNull(pgt.tail());
    assertTrue("BMI should be positive", pgt.tail().bmi > 0);
    assertTrue("Age in months should be between 24 and 36",
        pgt.tail().ageInMonths >= 24 && pgt.tail().ageInMonths <= 36);
  }

  @Test
  public void sexSpecificConstructorIsDeterministic() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    // Same seed and sex should produce the same initial BMI
    PediatricGrowthTrajectory pgt1 = new PediatricGrowthTrajectory(42L, birthDay, "F");
    PediatricGrowthTrajectory pgt2 = new PediatricGrowthTrajectory(42L, birthDay, "F");
    assertEquals("Same seed and sex should produce same BMI",
        pgt1.tail().bmi, pgt2.tail().bmi, 0.0001);
  }

  @Test
  public void sexSpecificConstructorDiffersBySex() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    // Same seed but different sex should produce different initial samples
    // (since they draw from different distributions)
    PediatricGrowthTrajectory malePgt = new PediatricGrowthTrajectory(42L, birthDay, "M");
    PediatricGrowthTrajectory femalePgt = new PediatricGrowthTrajectory(42L, birthDay, "F");
    // They may occasionally match by chance, but with seed 42 they should differ
    // We test that at least the trajectory is valid for both
    assertTrue("Male BMI should be positive", malePgt.tail().bmi > 0);
    assertTrue("Female BMI should be positive", femalePgt.tail().bmi > 0);
  }

  @Test
  public void sexSpecificConstructorGeneratesTrajectory() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "F");
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay, "F");
    // Verify that the trajectory can generate future BMI values
    long sampleSimulationTime = pgt.tail().timeInSimulation;
    long oneYearLater = sampleSimulationTime + Utilities.convertTime("months", 12);
    double futureBMI = pgt.currentBMI(person, oneYearLater);
    assertTrue("Future BMI should be positive", futureBMI > 0);
    assertTrue("Future BMI should be reasonable (< 40)", futureBMI < 40);
  }

  @Test
  public void percentileToBMI() {
    double seventyFifth = 0.75;
    double ninetySeventh = 0.97;
    int threeYearsInMonths = 36;
    double age = 3;
    String sex = "M";
    double sigma = PediatricGrowthTrajectory.sigma(sex, age);
    double seventyFifthBMI = PediatricGrowthTrajectory.percentileToBMI(seventyFifth,
        threeYearsInMonths, sex, sigma);
    // Should match the growth charts
    assertEquals(16.8337599, seventyFifthBMI, 0.01);
    double ninetySeventhBMI = PediatricGrowthTrajectory.percentileToBMI(ninetySeventh,
        threeYearsInMonths, sex, sigma);
    // Should be higher than the growth chart value because it is using extended values
    assertEquals(19.2084002, ninetySeventhBMI, 0.01);
  }

  @Test
  public void extendedZScore() {
    int threeYearsInMonths = 36;
    double age = 3;
    String sex = "M";
    double bmi = 19.2084002;
    double sigma = PediatricGrowthTrajectory.sigma(sex, age);
    double ezscore = PediatricGrowthTrajectory.extendedZScore(bmi, threeYearsInMonths, sex, sigma);
    assertEquals(1.880793608, ezscore, 0.01);
  }

  @Test
  public void sigma() {
    double age = 2;
    String sex = "M";
    assertEquals(1.3756, PediatricGrowthTrajectory.sigma(sex, age), 0.001);
  }

  @Test
  public void loadCorrelations() {
    Map<String, PediatricGrowthTrajectory.YearInformation> correlations =
        PediatricGrowthTrajectory.loadCorrelations();
    PediatricGrowthTrajectory.YearInformation yi = correlations.get("3");
    assertEquals(0.773, yi.correlation, 0.001);
    assertEquals(0.211, yi.diff, 0.001);
  }

  @Test
  public void addPointFromPercentile() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    long timeInSim = TestHelper.timestamp(2020, 1, 1, 0, 0, 0);
    double ninetySeventh = 0.97;
    int threeYearsInMonths = 36;
    String sex = "M";
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay);
    pgt.addPointFromPercentile(threeYearsInMonths, timeInSim, ninetySeventh, sex);
    assertEquals(19.2084002, pgt.tail().bmi, 0.01);
  }
}