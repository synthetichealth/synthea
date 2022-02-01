package org.mitre.synthea.helpers;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.helpers.ChartRenderer.MultiTableChartConfig;
import org.mitre.synthea.helpers.ChartRenderer.MultiTableSeriesConfig;
import org.mitre.synthea.helpers.ChartRenderer.PersonChartConfig;
import org.mitre.synthea.helpers.ChartRenderer.PersonSeriesConfig;
import org.mitre.synthea.world.agents.Person;
import org.simulator.math.odes.MultiTable;

public class ChartRendererTest {

  public static final TemporaryFolder tmpFolder = new TemporaryFolder();

  /**
   * Returns a mock MultiTable to test ChartRenderer methods.
   * @return mock MultiTable instance
   */
  public static MultiTable getMockTable() {
    double[] timePoints = {0.0,1.0,2.0,3.0,4.0};
    String[] identifiers = {"test1", "test2"};
    double[][] data = {{1.0,1.0},{1.0,2.0},{1.0,3.0},{1.0,4.0},{1.0,5.0}};

    return new MultiTable(timePoints, data, identifiers);
  }

  @Test
  public void testRenderFileFromMultiTable() throws Exception {
    tmpFolder.create();

    Path tmpFilePath = Paths.get(tmpFolder.getRoot().getAbsolutePath(), "tmp.png");

    MultiTableChartConfig chartCfg = new MultiTableChartConfig();
    chartCfg.setAxisLabelX("Time");
    chartCfg.setAxisLabelY("Test1");
    chartCfg.setAxisParamX("time");
    chartCfg.setFilename(tmpFilePath.toString());
    chartCfg.setTitle("Test Image");
    chartCfg.setType("line");

    MultiTableSeriesConfig seriesCfg = new MultiTableSeriesConfig();
    seriesCfg.setParam("test1");
    ArrayList<MultiTableSeriesConfig> seriesList = new ArrayList<MultiTableSeriesConfig>();
    seriesList.add(seriesCfg);

    chartCfg.setSeries(seriesList);

    MultiTable table = getMockTable();

    ChartRenderer.drawChartAsFile(table, chartCfg);

    // Verify that the image file was created
    File imgFile = tmpFilePath.toFile();

    assertTrue("image file created", imgFile.exists());
    assertTrue("image file not empty", imgFile.length() > 0);
  }

  @Test
  public void testRenderBase64FromMultiTable() throws Exception {

    MultiTableChartConfig chartCfg = new MultiTableChartConfig();
    chartCfg.setAxisParamX("test2");
    chartCfg.setAxisHiddenX(true);
    chartCfg.setAxisHiddenY(true);
    chartCfg.setType("scatter");

    MultiTableSeriesConfig seriesCfg = new MultiTableSeriesConfig();
    seriesCfg.setParam("test1");
    seriesCfg.setLabel("Test 1");
    MultiTableSeriesConfig seriesCfg2 = new MultiTableSeriesConfig();
    seriesCfg2.setParam("test2");
    seriesCfg2.setLabel("Test 2");

    ArrayList<MultiTableSeriesConfig> seriesList = new ArrayList<MultiTableSeriesConfig>();
    seriesList.add(seriesCfg);
    seriesList.add(seriesCfg2);

    chartCfg.setSeries(seriesList);

    MultiTable table = getMockTable();

    String b64 = ChartRenderer.drawChartAsBase64(table, chartCfg).getEncodedBytes();

    assertTrue(Base64.isBase64(b64));
  }

  @Test
  public void testRenderFileFromPerson() throws Exception {
    tmpFolder.create();

    Path tmpFilePath = Paths.get(tmpFolder.getRoot().getAbsolutePath(), "tmp.png");

    Person person = new Person(0L);
    TimeSeriesData data1 = new TimeSeriesData(1.0);
    data1.addValue(3.5);
    data1.addValue(3.9);
    data1.addValue(4.3);
    data1.addValue(4.8);
    person.attributes.put("attr1", data1);

    PersonChartConfig chartCfg = new PersonChartConfig();
    chartCfg.setAxisLabelX("Time");
    chartCfg.setAxisAttributeX("time");
    chartCfg.setFilename(tmpFilePath.toString());
    chartCfg.setType("scatter");

    PersonSeriesConfig seriesCfg = new PersonSeriesConfig();
    seriesCfg.setAttribute("attr1");

    ArrayList<PersonSeriesConfig> seriesList = new ArrayList<PersonSeriesConfig>();
    seriesList.add(seriesCfg);

    chartCfg.setSeries(seriesList);

    ChartRenderer.drawChartAsFile(person, chartCfg);

    // Verify that the image file was created
    File imgFile = tmpFilePath.toFile();

    assertTrue("image file created", imgFile.exists());
    assertTrue("image file not empty", imgFile.length() > 0);
  }

  @Test
  public void testRenderBase64FromPerson() throws Exception {
    ArrayList<Double> data1 = new ArrayList<Double>();
    data1.add(1.0);
    data1.add(2.0);
    data1.add(3.0);
    data1.add(4.0);

    ArrayList<Double> data2 = new ArrayList<Double>();
    data2.add(1.2);
    data2.add(5.7);
    data2.add(14.3);
    data2.add(40.3);

    ArrayList<Double> data3 = new ArrayList<Double>();
    data3.add(3.5);
    data3.add(2.6);
    data3.add(2.1);
    data3.add(1.7);

    Person person = new Person(0L);

    person.attributes.put("attr1", data1);
    person.attributes.put("attr2", data2);
    person.attributes.put("attr3", data3);

    PersonChartConfig chartCfg = new PersonChartConfig();
    chartCfg.setAxisAttributeX("attr1");
    chartCfg.setType("line");

    PersonSeriesConfig seriesCfg1 = new PersonSeriesConfig();
    seriesCfg1.setAttribute("attr2");

    PersonSeriesConfig seriesCfg2 = new PersonSeriesConfig();
    seriesCfg2.setAttribute("attr3");

    ArrayList<PersonSeriesConfig> seriesList = new ArrayList<PersonSeriesConfig>();
    seriesList.add(seriesCfg1);
    seriesList.add(seriesCfg2);

    chartCfg.setSeries(seriesList);

    String b64 = ChartRenderer.drawChartAsBase64(person, chartCfg).getEncodedBytes();

    assertTrue(Base64.isBase64(b64));
  }


  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartConfigStart() throws URISyntaxException, IOException {
    MultiTableChartConfig config = new MultiTableChartConfig();

    config.setStartTime(-1);

    ChartRenderer.drawChartAsBase64(getMockTable(), config);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartConfigEnd() throws URISyntaxException, IOException {
    MultiTableChartConfig config = new MultiTableChartConfig();

    // Mock only goes up to 5.0, so end is invalid
    config.setEndTime(10.0);

    ChartRenderer.drawChartAsBase64(getMockTable(), config);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartTimeSpan() throws URISyntaxException, IOException {
    MultiTableChartConfig config = new MultiTableChartConfig();

    config.setEndTime(1.0);
    config.setStartTime(2.0);

    ChartRenderer.drawChartAsBase64(getMockTable(), config);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartAxis() throws URISyntaxException, IOException {
    MultiTableChartConfig config = new MultiTableChartConfig();

    config.setStartTime(0.0);
    config.setEndTime(1.0);
    config.setAxisParamX("oops");

    ChartRenderer.drawChartAsBase64(getMockTable(), config);
  }
}
