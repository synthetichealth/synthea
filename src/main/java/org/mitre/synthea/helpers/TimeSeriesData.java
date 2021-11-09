package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.List;

public class TimeSeriesData {
  /** Provides a collection of time series values as well as sampling metadata
   *  concerning those values.
   */
  private List<Double> values;
  private double period; // number of seconds between samples

  public TimeSeriesData(double period) {
    this.setValues(new ArrayList<Double>());
    this.setPeriod(period);
  }

  public TimeSeriesData(List<Double> values, double period) {
    this.setValues(values);
    this.setPeriod(period);
  }

  public TimeSeriesData(int initialCapacity, double period) {
    this.setValues(new ArrayList<Double>(initialCapacity));
    this.setPeriod(period);
  }

  public List<Double> getValues() {
    return values;
  }

  public void setValues(List<Double> values) {
    this.values = values;
  }

  public double getPeriod() {
    return period;
  }

  public void setPeriod(double period) {
    this.period = period;
  }

  public boolean addValue(double value) {
    return this.values.add(value);
  }

}
