package org.mitre.synthea.world.concepts;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.StatUtils;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.Person;

import java.util.Map;

import static org.junit.Assert.*;

public class PediatricGrowthTrajectoryTest {

  @Test
  public void generateNextYearBMI() {
    long birthDay = TestHelper.timestamp(2017, 1, 1, 0, 0, 0);
    long now = TestHelper.timestamp(2019, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.BMI,
        GrowthChart.loadCharts().get(GrowthChart.ChartType.BMI).lookUp(24, "M", 0.5));
    JDKRandomGenerator random = new JDKRandomGenerator(6454);
    double percentile = GrowthChart.zscoreToPercentile(PediatricGrowthTrajectory.loadCorrelations().get("3").diff);
    double threeYearBMI =
        GrowthChart.loadCharts().get(GrowthChart.ChartType.BMI).lookUp(36, "M", percentile);
    double generatedBMI[] = new double[1000];
    for (int i = 0; i < 1000; i++) {
      generatedBMI[i] = PediatricGrowthTrajectory.generateNextYearBMI(person, now, random);
    }
    double mean =  StatUtils.mean(generatedBMI);
    // It would be nice to have a smaller delta in the assertion. That said, this test case will
    // have times when the generated BMI ends up above the 95th percentile and extended values
    // are used. That causes the mean to shift slightly above what is expected.
    assertEquals(threeYearBMI, mean, 0.1);
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
    assertEquals(0.768, yi.correlation, 0.001);
    assertEquals(0.08, yi.diff, 0.001);
  }
}