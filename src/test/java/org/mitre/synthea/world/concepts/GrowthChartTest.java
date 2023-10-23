package org.mitre.synthea.world.concepts;

import java.util.Map;

import junit.framework.TestCase;

public class GrowthChartTest extends TestCase {

  /**
   * Test the look up of BMI values at the high end of the range.
   */
  public void testLookUp() {
    Map<GrowthChart.ChartType, GrowthChart> growthChart =
            GrowthChart.loadCharts();
    double bmi = growthChart.get(GrowthChart.ChartType.BMI).lookUp(240, "F", 0.997394309960757);
    assertFalse(Double.isNaN(bmi));
    bmi = growthChart.get(GrowthChart.ChartType.BMI).lookUp(240, "F", 0.5);
    assertEquals(21.71699934, bmi, 0.01);
  }
}