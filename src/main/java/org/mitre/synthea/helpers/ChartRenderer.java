package org.mitre.synthea.helpers;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.mitre.synthea.world.agents.Person;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;

/**
 * This class provides methods to create and render charts using JFreeChart.
 */
public class ChartRenderer {

  /**
   * Enumeration of supported chart types.
   */
  public enum ChartType {
    /** Scatter plot chart type. */
    SCATTER,
    /** Line chart type. */
    LINE
  }

  /**
   * POJO configuration for a chart.
   */
  public abstract static class ChartConfig implements Serializable {
    /** Name of the image file to export. */
    private String filename;
    /** User input for the type of chart to render. */
    private String type;
    /** Chart title. */
    private String title;
    /** X axis label. */
    private String axisLabelX;
    /** Y axis label. */
    private String axisLabelY;
    /** Chart width in pixels. */
    private int width = 600;
    /** Chart height in pixels. */
    private int height = 300;
    /** Whether the X Axis is visible. */
    private boolean axisHiddenX = false;
    /** Whether the Y Axis is visible. */
    private boolean axisHiddenY = false;

    /**
     * Gets the filename of the chart image.
     * @return the filename of the chart image
     */
    public String getFilename() {
      return filename;
    }

    /**
     * Sets the filename of the chart image.
     * @param filename the filename to set for the chart image
     */
    public void setFilename(String filename) {
      this.filename = filename;
    }

    /**
     * Gets the type of the chart.
     * @return the type of the chart
     */
    public String getType() {
      return type;
    }

    /**
     * Sets the type of the chart.
     * @param type the type to set for the chart
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Gets the title of the chart.
     * @return the title of the chart
     */
    public String getTitle() {
      return title;
    }

    /**
     * Sets the title of the chart.
     * @param title the title to set for the chart
     */
    public void setTitle(String title) {
      this.title = title;
    }

    /**
     * Gets the label of the X axis.
     * @return the label of the X axis
     */
    public String getAxisLabelX() {
      return axisLabelX;
    }

    /**
     * Sets the label of the X axis.
     * @param axisLabelX the label to set for the X axis
     */
    public void setAxisLabelX(String axisLabelX) {
      this.axisLabelX = axisLabelX;
    }

    /**
     * Gets the label of the Y axis.
     * @return the label of the Y axis
     */
    public String getAxisLabelY() {
      return axisLabelY;
    }

    /**
     * Sets the label of the Y axis.
     * @param axisLabelY the label to set for the Y axis
     */
    public void setAxisLabelY(String axisLabelY) {
      this.axisLabelY = axisLabelY;
    }

    /**
     * Gets the width of the chart in pixels.
     * @return the width of the chart in pixels
     */
    public int getWidth() {
      return width;
    }

    /**
     * Sets the width of the chart in pixels.
     * @param width the width to set for the chart in pixels
     */
    public void setWidth(int width) {
      this.width = width;
    }

    /**
     * Gets the height of the chart in pixels.
     * @return the height of the chart in pixels
     */
    public int getHeight() {
      return height;
    }

    /**
     * Sets the height of the chart in pixels.
     * @param height the height to set for the chart in pixels
     */
    public void setHeight(int height) {
      this.height = height;
    }

    /**
     * Checks if the X axis is hidden.
     * @return true if the X axis is hidden, false otherwise
     */
    public boolean isAxisHiddenX() {
      return axisHiddenX;
    }

    /**
     * Sets whether the X axis is hidden.
     * @param axisHiddenX true to hide the X axis, false to show it
     */
    public void setAxisHiddenX(boolean axisHiddenX) {
      this.axisHiddenX = axisHiddenX;
    }

    /**
     * Checks if the Y axis is hidden.
     * @return true if the Y axis is hidden, false otherwise
     */
    public boolean isAxisHiddenY() {
      return axisHiddenY;
    }

    /**
     * Sets whether the Y axis is hidden.
     * @param axisHiddenY true to hide the Y axis, false to show it
     */
    public void setAxisHiddenY(boolean axisHiddenY) {
      this.axisHiddenY = axisHiddenY;
    }
  }

  /**
   * POJO configuration for a chart with data from a MultiTable.
   */
  public static class MultiTableChartConfig extends ChartConfig {
    /** Parameter to render on the x axis. */
    private String axisParamX;
    /** Simulation time in seconds to start charting points. */
    private double startTime;
    /** Simulation time in seconds to end charting points. */
    private double endTime;
    /** List of series configurations for this chart. */
    private List<MultiTableSeriesConfig> series;

    /**
     * Gets the parameter to render on the x-axis.
     * @return the parameter to render on the x-axis.
     */
    public String getAxisParamX() {
      return axisParamX;
    }

    /**
     * Sets the parameter for the x-axis.
     * @param axisParamX the parameter to set for the x-axis.
     */
    public void setAxisParamX(String axisParamX) {
      this.axisParamX = axisParamX;
    }

    /**
     * Gets the simulation time in seconds to start charting points.
     * @return the simulation time in seconds to start charting points.
     */
    public double getStartTime() {
      return startTime;
    }

    /**
     * Sets the simulation time to start charting points.
     * @param startTime the simulation time to set for starting charting points.
     */
    public void setStartTime(double startTime) {
      this.startTime = startTime;
    }

    /**
     * Gets the simulation time in seconds to end charting points.
     * @return the simulation time in seconds to end charting points.
     */
    public double getEndTime() {
      return endTime;
    }

    /**
     * Sets the simulation time to end charting points.
     * @param endTime the simulation time to set for ending charting points.
     */
    public void setEndTime(double endTime) {
      this.endTime = endTime;
    }

    /**
     * Gets the list of series configurations for this chart.
     * @return the list of series configurations for this chart.
     */
    public List<MultiTableSeriesConfig> getSeries() {
      return series;
    }

    /**
     * Sets the list of series configurations for this chart.
     * @param series the list of series configurations to set for this chart.
     */
    public void setSeries(List<MultiTableSeriesConfig> series) {
      this.series = series;
    }

  }

  /**
   * POJO configuration for a chart with data from a Person object.
   */
  public static class PersonChartConfig extends ChartConfig {
    /** Person attribute to render on the x axis. */
    private String axisAttributeX;
    /** List of series configurations for this chart. */
    private List<PersonSeriesConfig> series;

    /**
     * Gets the attribute to render on the x-axis.
     * @return the attribute to render on the x-axis.
     */
    public String getAxisAttributeX() {
      return axisAttributeX;
    }

    /**
     * Sets the attribute for the x-axis.
     * @param axisAttributeX the attribute to set for the x-axis.
     */
    public void setAxisAttributeX(String axisAttributeX) {
      this.axisAttributeX = axisAttributeX;
    }

    /**
     * Gets the list of series configurations for this chart.
     * @return the list of series configurations for this chart.
     */
    public List<PersonSeriesConfig> getSeries() {
      return series;
    }

    /**
     * Sets the list of series configurations for this chart.
     * @param series the list of series configurations to set for this chart.
     */
    public void setSeries(List<PersonSeriesConfig> series) {
      this.series = series;
    }

  }

  /**
   * POJO configuration for a chart series.
   */
  private abstract static class SeriesConfig implements Serializable {
    /** Series label in the legend. */
    private String label;

    /**
     * Gets the label of the series.
     * @return the label of the series.
     */
    public String getLabel() {
      return label;
    }

    /**
     * Sets the label for the series.
     * @param label the label to set for the series.
     */
    public void setLabel(String label) {
      this.label = label;
    }

  }

  /**
   * POJO configuration for a chart series with data from a MultiTable.
   */
  public static class MultiTableSeriesConfig extends SeriesConfig {
    /** Which parameter to plot on this series. (if providing a MultiTable) */
    private String param;

    /**
     * Gets the parameter plotted on this series.
     * @return the parameter plotted on this series.
     */
    public String getParam() {
      return param;
    }

    /**
     * Sets the parameter for this series.
     * @param param the parameter to set for this series.
     */
    public void setParam(String param) {
      this.param = param;
    }

  }

  /**
   * POJO configuration for a chart series with data from a Person object.
   */
  public static class PersonSeriesConfig extends SeriesConfig {
    /** Which attribute to plot on this series. (if providing a Person) */
    private String attribute;

    /**
     * Gets the attribute plotted on this series.
     * @return the attribute plotted on this series.
     */
    public String getAttribute() {
      return attribute;
    }

    /**
     * Sets the attribute for this series.
     * @param attr the attribute to set for this series.
     */
    public void setAttribute(String attr) {
      this.attribute = attr;
    }

  }

  /**
   * Create a JFreeChart object based on values from a MultiTable.
   *
   * @param table the MultiTable to retrieve values from.
   * @param config the chart configuration options.
   * @return the created JFreeChart object.
   */
  public static JFreeChart createChart(MultiTable table, MultiTableChartConfig config) {

    double lastTimePoint = table.getTimePoint(table.getRowCount() - 1);

    // If no chart type was provided, throw an exception
    if (config.getType() == null) {
      throw new IllegalArgumentException("Chart type must be provided");
    }

    // Set the chart end time if not specified
    if (config.getEndTime() == 0) {
      config.setEndTime(lastTimePoint);
    }

    // Check that the start time is valid
    if (config.getStartTime() < 0) {
      throw new IllegalArgumentException("Chart start time must not be negative");
    }

    // Check the chart end time is valid
    if (config.getEndTime() > lastTimePoint) {
      throw new IllegalArgumentException("Invalid chart end time: " + config.getEndTime()
          + " is greater than final time point " + lastTimePoint);
    }

    // Check the time range is valid
    if (config.getStartTime() > config.getEndTime()) {
      throw new IllegalArgumentException("Invalid chart range: " + config.getStartTime()
          + " to " + config.getEndTime());
    }

    // Get the list of x values. Time is treated specially since it doesn't have a param identifier
    boolean axisXIsTime = "time".equalsIgnoreCase(config.getAxisParamX());
    List<Double> valuesX = new ArrayList<Double>(table.getRowCount());
    double[] timePoints = table.getTimePoints();
    Column colX = table.getColumn(config.getAxisParamX());

    // Check that the x axis identifier is valid
    if (!axisXIsTime && colX == null) {
      throw new IllegalArgumentException("Invalid X axis identifier: " + config.getAxisParamX());
    }

    int startIndex = Arrays.binarySearch(timePoints, config.getStartTime());
    int endIndex = Arrays.binarySearch(timePoints, config.getEndTime());

    // Add the table values to the list of x axis values within the provided time range
    for (int i = startIndex; i < endIndex; i++) {
      if (axisXIsTime) {
        valuesX.add(timePoints[i]);
      } else {
        valuesX.add(colX.getValue(i));
      }
    }

    XYSeriesCollection dataset = new XYSeriesCollection();

    // Add each series to the dataset
    for (MultiTableSeriesConfig seriesConfig : config.getSeries()) {
      // If a label is not provided, use the parameter as the label
      String seriesLabel = seriesConfig.getLabel();
      if (seriesLabel == null) {
        seriesLabel = seriesConfig.getParam();
      }

      // don't auto-sort the series
      XYSeries series = new XYSeries(seriesLabel, false);

      Column col = table.getColumn(seriesConfig.getParam());

      // Check that the series identifier is valid
      if (col == null) {
        throw new IllegalArgumentException("Invalid series identifier: " + seriesConfig.getParam());
      }

      int indexX = 0;
      for (int i = startIndex; i < endIndex; i++) {
        series.add((double) valuesX.get(indexX++), col.getValue(i));
      }

      dataset.addSeries(series);
    }

    // Instantiate our renderer to draw the chart
    XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
    JFreeChart chart;

    // Determine the appropriate Chart from the configuration options
    switch (ChartType.valueOf(config.getType().toUpperCase())) {
      default:
      case LINE:
        chart = ChartFactory.createXYLineChart(
            config.getTitle(),
            config.getAxisLabelX(),
            config.getAxisLabelY(),
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
          renderer.setSeriesShapesVisible(i, false);
        }
        break;
      case SCATTER:
        chart = ChartFactory.createScatterPlot(
            config.getTitle(),
            config.getAxisLabelX(),
            config.getAxisLabelY(),
            dataset
        );
        break;
    }

    // If there's only one series, and there's a chart title, the legend is unnecessary
    if (config.getTitle() != null && !config.getTitle().isEmpty()
        && config.getSeries().size() == 1) {
      chart.removeLegend();
    } else {
      chart.getLegend().setFrame(BlockBorder.NONE);
    }

    // Instantiate the plot and set some reasonable styles
    // TODO eventually we can make these more configurable if desired
    XYPlot plot = chart.getXYPlot();

    if (config.isAxisHiddenX()) {
      plot.getDomainAxis().setVisible(false);
    }

    if (config.isAxisHiddenY()) {
      plot.getRangeAxis().setVisible(false);
    }

    plot.setRenderer(renderer);
    plot.setBackgroundPaint(Color.white);
    plot.setRangeGridlinesVisible(true);
    plot.setDomainGridlinesVisible(true);

    return chart;
  }

  /**
   * Create a JFreeChart object based on values from a Person object.
   *
   * @param person the Person instance to retrieve values from.
   * @param config the chart configuration options.
   * @return the created JFreeChart object.
   */
  @SuppressWarnings("unchecked")
  public static JFreeChart createChart(Person person, PersonChartConfig config) {

    // Get the list of x values.
    Object attrValueX = person.attributes.get(config.getAxisAttributeX());

    boolean axisIsTimeX = false;
    List<Double> valuesX = null;

    if (config.getAxisAttributeX().equalsIgnoreCase("time")) {
      axisIsTimeX = true;
    } else if (attrValueX instanceof TimeSeriesData) {
      valuesX = ((TimeSeriesData) attrValueX).getValues();
    } else if (attrValueX instanceof List && ((List<?>) attrValueX).get(0) instanceof Double) {
      valuesX = (List<Double>) attrValueX;
    } else {
      throw new RuntimeException("Invalid Person attribute \""
          + config.getAxisAttributeX() + "\" provided for chart X Axis: "
          + attrValueX + ". Attribute must either be \"time\" or refer to a TimeSeriesData or"
          + "List<Double> Object.");
    }

    XYSeriesCollection dataset = new XYSeriesCollection();

    // Add each series to the dataset
    for (PersonSeriesConfig seriesConfig : config.getSeries()) {
      // If a label is not provided, use the parameter as the label
      String seriesLabel = seriesConfig.getLabel();
      if (seriesLabel == null) {
        seriesLabel = seriesConfig.getAttribute();
      }

      // don't auto-sort the series
      XYSeries series = new XYSeries(seriesLabel, false);

      Object seriesObject = person.attributes.get(seriesConfig.getAttribute());

      List<Double> seriesValues;

      if (seriesObject instanceof TimeSeriesData) {
        TimeSeriesData timeSeries = (TimeSeriesData) seriesObject;
        seriesValues = timeSeries.getValues();

        // If time is defined for the X axis, and it hasn't yet been created,
        // create the time axis values now
        if (valuesX == null && axisIsTimeX) {
          valuesX = new ArrayList<Double>(timeSeries.getValues().size());

          for (int i = 0; i < timeSeries.getValues().size(); i++) {
            valuesX.add(timeSeries.getPeriod() * i);
          }
        }
      } else if (seriesObject instanceof List
          && ((List<?>) seriesObject).get(0) instanceof Double) {
        seriesValues = (List<Double>) seriesObject;
      } else {
        throw new RuntimeException("Invalid Person attribute \""
            + seriesConfig.getAttribute() + "\" provided for chart series: "
            + seriesObject + ". Attribute value must be a TimeSeriesData or List<Double> Object.");
      }

      if (valuesX == null) {
        throw new RuntimeException("When the special attribute \"time\" is provided for the X axis,"
            + " the first series attribute MUST point to a valid TimeSeriesData object.");
      }

      Iterator<Double> iterX = valuesX.iterator();
      Iterator<Double> seriesIter = seriesValues.iterator();

      while (iterX.hasNext()) {
        if (!seriesIter.hasNext()) {
          throw new RuntimeException("List for attribute \"" + seriesConfig.getAttribute()
              + "\" does not have the same length as the x axis values \""
              + config.getAxisAttributeX() + "\"");
        }
        series.add(iterX.next(), seriesIter.next());
      }

      dataset.addSeries(series);
    }

    // Instantiate our renderer to draw the chart
    XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
    JFreeChart chart;

    // Determine the appropriate Chart from the configuration options
    switch (ChartType.valueOf(config.getType().toUpperCase())) {
      default:
      case LINE:
        chart = ChartFactory.createXYLineChart(
            config.getTitle(),
            config.getAxisLabelX(),
            config.getAxisLabelY(),
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
          renderer.setSeriesShapesVisible(i, false);
        }
        break;
      case SCATTER:
        chart = ChartFactory.createScatterPlot(
            config.getTitle(),
            config.getAxisLabelX(),
            config.getAxisLabelY(),
            dataset
        );
        break;
    }

    // If there's only one series, and there's a chart title, the legend is unnecessary
    if (config.getTitle() != null && !config.getTitle().isEmpty()
        && config.getSeries().size() == 1) {
      chart.removeLegend();
    } else {
      chart.getLegend().setFrame(BlockBorder.NONE);
    }

    // Instantiate the plot and set some reasonable styles
    // TODO eventually we can make these more configurable if desired
    XYPlot plot = chart.getXYPlot();

    if (config.isAxisHiddenX()) {
      plot.getDomainAxis().setVisible(false);
    }

    if (config.isAxisHiddenY()) {
      plot.getRangeAxis().setVisible(false);
    }

    plot.setRenderer(renderer);
    plot.setBackgroundPaint(Color.white);
    plot.setRangeGridlinesVisible(true);
    plot.setDomainGridlinesVisible(true);

    return chart;
  }

  /**
   * Draw a JFreeChart to an image file based on values from a MultiTable.
   *
   * @param table the MultiTable to retrieve values from.
   * @param config the chart configuration options.
   */
  public static void drawChartAsFile(MultiTable table, MultiTableChartConfig config) {

    JFreeChart chart = createChart(table, config);

    // Save the chart as a PNG image to the file system
    try {
      ChartUtils.saveChartAsPNG(new File(config.getFilename()), chart, config.getWidth(),
          config.getHeight());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Draw a JFreeChart to an image file based on values from a Person object.
   *
   * @param person the Person to retrieve attributes from.
   * @param config the chart configuration options.
   */
  public static void drawChartAsFile(Person person, PersonChartConfig config) {

    JFreeChart chart = createChart(person, config);

    // Save the chart as a PNG image to the file system
    try {
      ChartUtils.saveChartAsPNG(new File(config.getFilename()), chart, config.getWidth(),
          config.getHeight());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Represents a base64 encoded chart image.
   */
  public static class Base64EncodedChart {
    private final String encodedBytes;
    private final int unencodedLength;

    /**
     * Constructor for Base64EncodedChart.
     *
     * @param bytes The raw image bytes
     */
    public Base64EncodedChart(byte[] bytes) {
      this.encodedBytes = new String(Base64.getEncoder().encode(bytes));
      this.unencodedLength = bytes.length;
    }

    /**
     * Get the base64 encoded bytes.
     *
     * @return The encoded bytes as a string
     */
    public String getEncodedBytes() {
      return encodedBytes;
    }

    /**
     * Get the unencoded length of the image.
     *
     * @return The unencoded length
     */
    public int getUnencodedLength() {
      return unencodedLength;
    }
  }

  /**
   * Draw a JFreeChart to a base64 encoded image based on values from a MultiTable.
   *
   * @param table the MultiTable to retrieve values from.
   * @param config the chart configuration options.
   * @return the base64 encoded chart image.
   * @throws IOException if an error occurs during encoding.
   */
  public static Base64EncodedChart drawChartAsBase64(MultiTable table, MultiTableChartConfig config)
      throws IOException {

    JFreeChart chart = createChart(table, config);

    byte[] imgBytes = ChartUtils.encodeAsPNG(chart.createBufferedImage(config.getWidth(),
        config.getHeight()));
    return new Base64EncodedChart(imgBytes);
  }

  /**
   * Draw a JFreeChart to a base64 encoded image based on values from a Person object.
   *
   * @param person the Person to retrieve attribute values from.
   * @param config the chart configuration options.
   * @return the base64 encoded chart image.
   * @throws IOException if an error occurs during encoding.
   */
  public static Base64EncodedChart drawChartAsBase64(Person person, PersonChartConfig config)
      throws IOException {

    JFreeChart chart = createChart(person, config);

    byte[] imgBytes = ChartUtils.encodeAsPNG(chart.createBufferedImage(config.getWidth(),
        config.getHeight()));
    return new Base64EncodedChart(imgBytes);
  }
}
