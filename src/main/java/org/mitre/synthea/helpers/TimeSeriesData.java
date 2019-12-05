package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.Collection;

public class TimeSeriesData {
  /** Provides a collection of time series values as well as sampling metadata
   *  concerning those values.
   */
  private Collection<Double> values;
  private double period; // number of seconds between samples
  
  public TimeSeriesData(double period) {
    this.setValues(new ArrayList<Double>());
    this.setPeriod(period);
  }
  
  public TimeSeriesData(Collection<Double> values, double period) {
    this.setValues(values);
    this.setPeriod(period);
  }
  
  public TimeSeriesData(int initialCapacity, double period) {
    this.setValues(new ArrayList<Double>(initialCapacity));
    this.setPeriod(period);
  }
  
  public Collection<Double> getValues() {
    return values;
  }

  public void setValues(Collection<Double> values) {
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
