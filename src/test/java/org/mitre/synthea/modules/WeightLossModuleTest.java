package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mitre.synthea.world.concepts.BMI.calculate;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.GrowthChart;
import org.mitre.synthea.world.concepts.PediatricGrowthTrajectory;
import org.mitre.synthea.world.concepts.VitalSign;

import java.util.Map;

public class WeightLossModuleTest {
  private WeightLossModule mod;
  private static final Map<GrowthChart.ChartType, GrowthChart> growthChart =
      GrowthChart.loadCharts();

  @Before
  public void before() {
    this.mod = new WeightLossModule();
  }

  @Test
  public void testFirstYearOfManagement() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long nextMonth = TestHelper.timestamp(2000, 2, 1, 0, 0, 0);
    long nextYearMonth = TestHelper.timestamp(2001, 2, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    assertTrue(mod.firstYearOfManagement(person, nextMonth));
    assertFalse(mod.firstYearOfManagement(person, nextYearMonth));
  }

  @Test
  public void testFirstFiveYearsOfManagement() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long nextYearMonth = TestHelper.timestamp(2001, 2, 1, 0, 0, 0);
    long tooLong = TestHelper.timestamp(2005, 2, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    assertTrue(mod.firstFiveYearsOfManagement(person, nextYearMonth));
    assertFalse(mod.firstYearOfManagement(person, tooLong));
  }

  @Test
  public void testAdultWeightLoss() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long sixMonths = TestHelper.timestamp(2000, 7, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 200d);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_PERCENTAGE, 0.1d);
    double weight = mod.adultWeightLoss(person, sixMonths);
    assertEquals(190, weight, 0.1);
  }

  @Test
  public void testAdultRegression() {
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long twoYears = TestHelper.timestamp(2002, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 200d);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_PERCENTAGE, 0.1d);
    double weight = mod.adultRegression(person, twoYears);
    assertEquals(185, weight, 0.1);
  }

  @Test
  public void testPediatricRegression() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long oneMonthAfter = TestHelper.timestamp(2000, 2, 1, 0, 0, 0);
    long oneYearAndTwoMonthsAfter = TestHelper.timestamp(2002, 3, 1, 0, 0, 0);
    long sixMonthsPrior = TestHelper.timestamp(1999, 7, 2, 0, 0, 0);
    Person person = new Person(0L);
    String gender = "M";
    double bmiPercentileChange = 0.01;
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_BMI_PERCENTILE_CHANGE, bmiPercentileChange);
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay);
    double startPercentile = 0.9;
    pgt.addPoint(person.ageInMonths(sixMonthsPrior), sixMonthsPrior,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(sixMonthsPrior), gender, startPercentile));
    pgt.addPoint(person.ageInMonths(oneMonthAfter), oneMonthAfter,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(oneMonthAfter), gender, startPercentile));
    int lowPoint = pgt.tail().ageInMonths + 12;
    double lowestBMI = growthChart.get(GrowthChart.ChartType.BMI).lookUp(lowPoint, gender,
        startPercentile - bmiPercentileChange);
    pgt.addPoint(lowPoint, pgt.tail().timeInSimulation + Utilities.convertTime("years", 1), lowestBMI);
    person.attributes.put(Person.GROWTH_TRAJECTORY, pgt);

    mod.pediatricRegression(person, oneYearAndTwoMonthsAfter);
    assertEquals(lowPoint + 12, pgt.tail().ageInMonths);
    double expectedBMI = growthChart.get(GrowthChart.ChartType.BMI).lookUp(lowPoint + 12, gender,
        startPercentile - (0.8 * bmiPercentileChange));
    assertEquals(expectedBMI, pgt.tail().bmi, 0.1);
  }

  @Test
  public void testMaintainBMIPercentile() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long oneMonthAfter = TestHelper.timestamp(2000, 2, 1, 0, 0, 0);
    Person person = new Person(0L);
    String gender = "M";
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, gender);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay);
    double startPercentile = 0.9;
    pgt.addPoint(person.ageInMonths(start), start,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(start), gender, startPercentile));
    person.attributes.put(Person.GROWTH_TRAJECTORY, pgt);
    int expectedTailAge = pgt.tail().ageInMonths + 12;
    mod.maintainBMIPercentile(person, oneMonthAfter);
    assertEquals(expectedTailAge, pgt.tail().ageInMonths);
    double expectedBMI = growthChart.get(GrowthChart.ChartType.BMI).lookUp(expectedTailAge, gender, startPercentile);
    assertEquals(expectedBMI, pgt.tail().bmi, 0.1);
  }

  @Test
  public void testProcess() {
    long sixMonths = TestHelper.timestamp(2000, 7, 2, 0, 0, 0);
    long oneYear = TestHelper.timestamp(2000, 12, 31, 0, 0, 0);
    long sixYears = TestHelper.timestamp(2006, 1, 1, 0, 0, 0);
    Person person = thirtyYearOld();
    person.attributes.put(WeightLossModule.ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_ADHERENCE, true);
    person.attributes.put(WeightLossModule.LONG_TERM_WEIGHT_LOSS, true);
    mod.process(person, sixMonths);
    double weight = person.getVitalSign(VitalSign.WEIGHT, sixMonths);
    assertEquals(129.2, weight, 0.1);
    mod.process(person, oneYear);
    mod.process(person, sixYears);
    weight = person.getVitalSign(VitalSign.WEIGHT, sixYears);
    assertEquals(122.4, weight,0.1);
    person = thirtyYearOld();
    person.attributes.put(WeightLossModule.ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_ADHERENCE, false);
    person.attributes.put(WeightLossModule.LONG_TERM_WEIGHT_LOSS, true);
    mod.process(person, sixMonths);
    weight = person.getVitalSign(VitalSign.WEIGHT, sixMonths);
    assertEquals(136, weight, 0.1);


    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long oneMonthAfter = TestHelper.timestamp(2000, 2, 1, 0, 0, 0);
    long twoMonthsAfter = TestHelper.timestamp(2000, 3, 1, 0, 0, 0);
    long sixMonthsPrior = TestHelper.timestamp(1999, 7, 2, 0, 0, 0);
    person = new Person(0L);
    String gender = "M";
    double bmiPercentileChange = 0.01;
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, gender);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_BMI_PERCENTILE_CHANGE, bmiPercentileChange);
    person.attributes.put(WeightLossModule.ACTIVE_WEIGHT_MANAGEMENT, true);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_ADHERENCE, true);
    person.attributes.put(WeightLossModule.LONG_TERM_WEIGHT_LOSS, true);
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay);
    double startPercentile = 0.9;
    pgt.addPoint(person.ageInMonths(sixMonthsPrior), sixMonthsPrior,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(sixMonthsPrior), gender, startPercentile));
    pgt.addPoint(person.ageInMonths(oneMonthAfter), oneMonthAfter,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(oneMonthAfter), gender, startPercentile));
    person.attributes.put(Person.GROWTH_TRAJECTORY, pgt);
    int expectedTailAge = pgt.tail().ageInMonths + 12;
    double expectedBMI = growthChart.get(GrowthChart.ChartType.BMI).lookUp(expectedTailAge, gender,
        startPercentile - bmiPercentileChange);
    mod.process(person, twoMonthsAfter);
    assertEquals(expectedBMI, pgt.tail().bmi, 0.001);
    assertEquals(expectedTailAge, pgt.tail().ageInMonths);
  }

  @Test
  public void testMeetsWeightManagementThresholds() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.HEIGHT, 139);
    person.setVitalSign(VitalSign.WEIGHT, 50);
    person.setVitalSign(VitalSign.BMI, calculate(139, 50));
    assertTrue(mod.meetsWeightManagementThresholds(person, start));
    person.setVitalSign(VitalSign.WEIGHT, 35);
    person.setVitalSign(VitalSign.BMI, calculate(139, 35));
    assertFalse(mod.meetsWeightManagementThresholds(person, start));
    assertTrue(mod.meetsWeightManagementThresholds(thirtyYearOld(), start));
    Person lighterThirty = thirtyYearOld();
    lighterThirty.setVitalSign(VitalSign.WEIGHT, 85);
    lighterThirty.setVitalSign(VitalSign.BMI, calculate(175, 85));
    assertFalse(mod.meetsWeightManagementThresholds(lighterThirty, start));
  }

  /*
  30 year old 300 lb man in the year 2000
   */
  private Person thirtyYearOld() {
    long birthDay = TestHelper.timestamp(1970, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, "M");
    person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, 0.9);
    person.setVitalSign(VitalSign.WEIGHT, 136);
    person.setVitalSign(VitalSign.HEIGHT, 175);
    person.setVitalSign(VitalSign.BMI, calculate(175, 136));
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_PERCENTAGE, 0.1d);
    person.attributes.put(WeightLossModule.PRE_MANAGEMENT_WEIGHT, 136d);
    return person;
  }

  @Test
  public void adjustBMIVectorForSuccessfulManagement() {
    long birthDay = TestHelper.timestamp(1990, 1, 1, 0, 0, 0);
    long start = TestHelper.timestamp(2000, 1, 1, 0, 0, 0);
    long oneMonthAfter = TestHelper.timestamp(2000, 2, 1, 0, 0, 0);
    long sixMonthsPrior = TestHelper.timestamp(1999, 7, 2, 0, 0, 0);
    Person person = new Person(0L);
    String gender = "M";
    double bmiPercentileChange = 0.01;
    person.attributes.put(Person.BIRTHDATE, birthDay);
    person.attributes.put(Person.GENDER, gender);
    person.attributes.put(WeightLossModule.WEIGHT_MANAGEMENT_START, start);
    person.attributes.put(WeightLossModule.WEIGHT_LOSS_BMI_PERCENTILE_CHANGE, bmiPercentileChange);
    PediatricGrowthTrajectory pgt = new PediatricGrowthTrajectory(0L, birthDay);
    double startPercentile = 0.9;
    pgt.addPoint(person.ageInMonths(sixMonthsPrior), sixMonthsPrior,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(sixMonthsPrior), gender, startPercentile));
    pgt.addPoint(person.ageInMonths(oneMonthAfter), oneMonthAfter,
        growthChart.get(GrowthChart.ChartType.BMI).lookUp(person.ageInMonths(oneMonthAfter), gender, startPercentile));
    person.attributes.put(Person.GROWTH_TRAJECTORY, pgt);
    int expectedTailAge = pgt.tail().ageInMonths + 12;
    mod.adjustBMIVectorForSuccessfulManagement(person);
    assertEquals(expectedTailAge, pgt.tail().ageInMonths);
    double expectedBMI = growthChart.get(GrowthChart.ChartType.BMI).lookUp(expectedTailAge, gender,
        startPercentile - bmiPercentileChange);
    assertEquals(expectedBMI, pgt.tail().bmi, 0.001);

  }
}
