package org.mitre.synthea.helpers;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection of time series values along with sampling metadata.
 * This class provides methods to manage and retrieve time series data.
 */
public class TimeSeriesData implements Serializable {
  /** Provides a collection of time series values as well as sampling metadata
   *  concerning those values.
   */
  private List<Double> values;
  /** number of seconds between samples */
  private double period;

  /**
   * Constructs a TimeSeriesData object with a specified sampling period.
   *
   * @param period The number of seconds between samples.
   */
  public TimeSeriesData(double period) {
    this.setValues(new ArrayList<Double>());
    this.setPeriod(period);
  }

  /**
   * Constructs a TimeSeriesData object with specified values and sampling period.
   *
   * @param values The list of time series values.
   * @param period The number of seconds between samples.
   */
  public TimeSeriesData(List<Double> values, double period) {
    this.setValues(values);
    this.setPeriod(period);
  }

  /**
   * Constructs a TimeSeriesData object with an initial capacity for values and a sampling period.
   *
   * @param initialCapacity The initial capacity of the values list.
   * @param period The number of seconds between samples.
   */
  public TimeSeriesData(int initialCapacity, double period) {
    this.setValues(new ArrayList<Double>(initialCapacity));
    this.setPeriod(period);
  }

  /**
   * Retrieves the list of time series values.
   *
   * @return The list of time series values.
   */
  public List<Double> getValues() {
    return values;
  }

  /**
   * Sets the list of time series values.
   *
   * @param values The list of time series values to set.
   */
  public void setValues(List<Double> values) {
    this.values = values;
  }

  /**
   * Retrieves the sampling period in seconds.
   *
   * @return The sampling period in seconds.
   */
  public double getPeriod() {
    return period;
  }

  /**
   * Sets the sampling period in seconds.
   *
   * @param period The sampling period in seconds to set.
   */
  public void setPeriod(double period) {
    this.period = period;
  }

  /**
   * Adds a value to the time series.
   *
   * @param value The value to add to the time series.
   * @return True if the value was added successfully, false otherwise.
   */
  public boolean addValue(double value) {
    return this.values.add(value);
  }
}
