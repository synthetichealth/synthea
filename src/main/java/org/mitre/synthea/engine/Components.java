package org.mitre.synthea.engine;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.TimeSeriesData;
import org.mitre.synthea.world.agents.Person;

/**
 * Various components used in the generic module framework.
 * All components should be defined within this class.
 */
public abstract class Components {

  /**
   * A Range of values, with a low and a high.
   * Values must be numeric. (ex, Integer, Long, Double)
   * 
   * @param <R> Type of range
   */
  public static class Range<R extends Number> {
    /**
     * Minimum value of the range.
     */
    public R low;
    
    /**
     * Maximum value of the range.
     */
    public R high;
    
    /**
     * Decimal places for value within the range.
     */
    public Integer decimals;
  }
  
  /**
   * Variant of the Range class, where a unit is required.
   * Defining this in a separate class makes it easier to define
   * where units are and are not required.
   *
   * @param <R> Type of range
   */
  public static class RangeWithUnit<R extends Number> extends Range<R> {
    /**
     * Unit for the range. Ex, "years" if the range represents an amount of time.
     */
    public String unit;
  }
  
  /**
   * An Exact quantity representing a single fixed value. Note that "quantity" here may be a bit of
   * a misnomer as the value does not have to be numeric. Ex, it may be a String or Code.
   * 
   * @param <T>
   *          Type of quantity
   */
  public static class Exact<T> {
    /**
     * The fixed value.
     */
    public T quantity;
  }
  
  /**
   * Variant of the Exact class, where a unit is required.
   * Defining this in a separate class makes it easier to define
   * where units are and are not required.
   * 
   * @param <T> Type of quantity
   */
  public static class ExactWithUnit<T> extends Exact<T> {
    /**
     * Unit for the quantity. Ex, "days" if the quantity represents an amount of time.
     */
    public String unit;
  }
  
  public static class DateInput {
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;
    public int millisecond;
  }
  
  public static class SampledData {
    public double originValue; // Zero value
    public Double factor; // Multiply data by this before adding to origin
    public Double lowerLimit; // Lower limit of detection
    public Double upperLimit; // Upper limit of detection
    public List<String> attributes; // Person attributes containing TimeSeriesData objects
    public transient List<TimeSeriesData> series; // List of actual series data collections
    
    // Format for the output decimal numbers
    // See https://docs.oracle.com/javase/8/docs/api/java/text/DecimalFormat.html
    public String decimalFormat;
    
    /**
     * Retrieves the actual data lists from the given Person according to
     * the provided timeSeriesAttributes values.
     * @param person Person to get time series data from
     */
    public void setSeriesData(Person person) {
      int dataLen = 0;
      double dataPeriod = 0;
      series = new ArrayList<TimeSeriesData>(attributes.size());
      
      for (String attr : attributes) {
        TimeSeriesData data = (TimeSeriesData) person.attributes.get(attr);
        if (dataLen == 0) {
          dataLen = data.getValues().size();
          dataPeriod = data.getPeriod();
        } else {
          // Verify that each series is consistent in length
          if (data.getValues().size() != dataLen) {
            throw new IllegalArgumentException("Provided series ["
                + StringUtils.join(attributes, ", ")
                + "] have inconsistent lengths!");
          }
          
          // Verify that each series has identical period
          if (data.getPeriod() != dataPeriod) {
            throw new IllegalArgumentException("Provided series ["
                + StringUtils.join(attributes, ", ")
                + "] have inconsistent periods!");
          }
        }
        series.add(data);
      }
    }
  }

}
