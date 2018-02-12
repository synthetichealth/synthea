package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GrowthChartTest {
  @Test
  public void testZScores() throws Exception {
    /* Z is the z-score that corresponds to the percentile.
    * z-scores correspond exactly to percentiles, e.g.,
    * z-scores of:
    * -1.881, // 3rd
    * -1.645, // 5th
    * -1.282, // 10th
    * -0.674, // 25th
    *  0,     // 50th
    *  0.674, // 75th
    *  1.036, // 85th
    *  1.282, // 90th
    *  1.645, // 95th
    *  1.881  // 97th
    */
    double[] zscores = {-1.881, -1.645, -1.282, -0.674,  0.0, 0.674, 1.036, 1.282, 1.645, 1.881};
    double[] percent = { 0.03, 0.05, 0.10, 0.25, 0.50, 0.75, 0.85, 0.90, 0.95, 0.97};
    for (int i = 0; i < percent.length; i++) {
      double z = LifecycleModule.calculateZScore(percent[i]);
      assertEquals(zscores[i], z, 0.01);
    }
  }

  @Test
  public void testGrowthChartLookupMin() throws Exception {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 0.0);
    assertEquals(74.23114138, height, 0.01);
  }

  @Test
  public void testGrowthChartLookupLow() throws Exception {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 0.03);
    assertEquals(78.06971429, height, 0.01);
  }

  @Test
  public void testGrowthChartLookupMiddle() throws Exception {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 0.5);
    assertEquals(84.24783394, height, 0.01);
  }

  @Test
  public void testGrowthChartLookupHigh() throws Exception {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 0.97);
    assertEquals(90.68153436, height, 0.01);
  }

  @Test
  public void testGrowthChartLookupMax() throws Exception {
    double height = LifecycleModule.lookupGrowthChart("height", "M", 20, 1.0);
    assertEquals(94.95447906, height, 0.01);
  }
}
