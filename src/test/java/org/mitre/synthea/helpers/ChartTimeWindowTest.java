package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.jfree.data.xy.XYDataset;
import org.junit.Test;
import org.mitre.synthea.helpers.ChartRenderer.MultiTableChartConfig;
import org.mitre.synthea.helpers.ChartRenderer.MultiTableSeriesConfig;
import org.simulator.math.odes.MultiTable;

public class ChartTimeWindowTest {

  private MultiTable table() {
    return new MultiTable(new double[] {0.0, 1.0, 2.0, 3.0},
        new double[][] {{10.0, 100.0}, {11.0, 101.0}, {12.0, 102.0}, {13.0, 103.0}},
        new String[] {"x", "y"});
  }

  private MultiTableChartConfig config(double start, double end) {
    MultiTableChartConfig config = new MultiTableChartConfig();
    config.setType("line");
    config.setAxisParamX("time");
    config.setStartTime(start);
    config.setEndTime(end);
    MultiTableSeriesConfig x = new MultiTableSeriesConfig();
    x.setParam("x");
    MultiTableSeriesConfig y = new MultiTableSeriesConfig();
    y.setParam("y");
    config.setSeries(Arrays.asList(x, y));
    return config;
  }

  private XYDataset dataset(MultiTable table, MultiTableChartConfig config) {
    return ChartRenderer.createChart(table, config).getXYPlot().getDataset();
  }

  private void assertSamples(XYDataset dataset, double[] times, double[] valuesX,
      double[] valuesY) {
    assertEquals(2, dataset.getSeriesCount());
    assertEquals(times.length, dataset.getItemCount(0));
    assertEquals(times.length, dataset.getItemCount(1));
    for (int i = 0; i < times.length; i++) {
      assertEquals(times[i], dataset.getXValue(0, i), 0.0);
      assertEquals(times[i], dataset.getXValue(1, i), 0.0);
      assertEquals(valuesX[i], dataset.getYValue(0, i), 0.0);
      assertEquals(valuesY[i], dataset.getYValue(1, i), 0.0);
    }
  }

  @Test
  public void bothBoundsBetweenSamplesUseOnlySamplesInWindow() {
    assertSamples(dataset(table(), config(0.5, 2.5)),
        new double[] {1.0, 2.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void startBetweenSamplesDoesNotReadNegativeRows() {
    assertSamples(dataset(table(), config(0.5, 3.0)),
        new double[] {1.0, 2.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void endBetweenSamplesRetainsEarlierSamples() {
    assertSamples(dataset(table(), config(1.0, 2.5)),
        new double[] {1.0, 2.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void exactBoundsKeepStartInclusiveAndEndExclusive() {
    assertSamples(dataset(table(), config(1.0, 3.0)),
        new double[] {1.0, 2.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void parameterXAxisUsesTheSameRowsAsEverySeries() {
    MultiTableChartConfig config = config(0.5, 2.5);
    config.setAxisParamX("x");
    assertSamples(dataset(table(), config),
        new double[] {11.0, 12.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void unevenGridUsesActualTimes() {
    MultiTable table = new MultiTable(new double[] {0.0, 0.3, 1.1, 2.7},
        new double[][] {{10.0, 100.0}, {11.0, 101.0}, {12.0, 102.0}, {13.0, 103.0}},
        new String[] {"x", "y"});
    assertSamples(dataset(table, config(0.2, 2.0)),
        new double[] {0.3, 1.1}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void startBeforeFirstSampleUsesFirstAvailableSample() {
    MultiTable table = new MultiTable(new double[] {1.0, 2.0, 3.0},
        new double[][] {{11.0, 101.0}, {12.0, 102.0}, {13.0, 103.0}},
        new String[] {"x", "y"});
    assertSamples(dataset(table, config(0.0, 3.0)),
        new double[] {1.0, 2.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void defaultEndKeepsFinalSampleExcluded() {
    assertSamples(dataset(table(), config(1.0, 0.0)),
        new double[] {1.0, 2.0}, new double[] {11.0, 12.0}, new double[] {101.0, 102.0});
  }

  @Test
  public void windowWithoutSamplesProducesEmptySeries() {
    assertSamples(dataset(table(), config(0.2, 0.8)),
        new double[0], new double[0], new double[0]);
  }

  @Test
  public void startAfterFinalSampleRetainsRangeDiagnostic() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> dataset(table(), config(4.0, 0.0)));
    assertTrue(error.getMessage().contains("Invalid chart range"));
  }

  @Test
  public void endAfterFinalSampleRetainsEndDiagnostic() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> dataset(table(), config(0.5, 4.0)));
    assertTrue(error.getMessage().contains("Invalid chart end time"));
  }

  @Test
  public void emptyResultsHaveClearDiagnostic() {
    MultiTable table = new MultiTable();
    table.setTimePoints(new double[0]);
    table.addBlock(new String[] {"x", "y"});
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> dataset(table, config(0.0, 0.0)));
    assertTrue(error.getMessage().contains("without samples"));
  }
}
