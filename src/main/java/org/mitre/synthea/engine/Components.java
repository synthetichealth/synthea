package org.mitre.synthea.engine;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.ChartRenderer;
import org.mitre.synthea.helpers.ChartRenderer.PersonChartConfig;
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
  public static class Range<R extends Number> implements Serializable {
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
  public static class RangeWithUnit<R extends Number> extends Range<R> implements Serializable {
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
  public static class Exact<T> implements Serializable {
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
  public static class ExactWithUnit<T> extends Exact<T> implements Serializable {
    /**
     * Unit for the quantity. Ex, "days" if the quantity represents an amount of time.
     */
    public String unit;
  }
  
  public static class DateInput implements Serializable {
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;
    public int millisecond;
  }
  
  public static class SampledData implements Serializable {
    public double originValue; // Zero value
    public Double factor; // Multiply data by this before adding to origin
    public Double lowerLimit; // Lower limit of detection
    public Double upperLimit; // Upper limit of detection
    public List<String> attributes; // Person attributes containing TimeSeriesData objects
    public transient List<TimeSeriesData> series; // List of actual series data collections
    
    // Format for the output decimal numbers
    // See https://docs.oracle.com/javase/8/docs/api/java/text/DecimalFormat.html
    public String decimalFormat;

    public SampledData() {}
  
    /**
     * Copy constructor for SampledData.
     * @param other data to copy
     */
    public SampledData(SampledData other) {
      originValue = other.originValue;
      factor = other.factor;
      lowerLimit = other.lowerLimit;
      upperLimit = other.upperLimit;
      attributes = other.attributes;
      series = other.series;
    }
    
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

  /**
   * Attachment class to support inline image file generation,
   * raw base64 data, or URLs of image attachments for Observations.
   */
  public static class Attachment implements Serializable {
    public String contentType; // Code for the MIME type of the content, with charset etc.
    public String language; // Human language of the content (BCP-47)
    public String data; // Data inline, base64ed
    public String url; // URI where the data can be found
    public int size; // Number of bytes of content (if url provided)
    public String hash; // Hash of the data (sha1, base64ed)
    public String title; // Label to display in place of the data
    public String creation; // Date attachment was first created
    public int height; // Height of the image in pixels (photo/video)
    public int width; // Width of the image in pixesl (photo/video)
    public int frames; // Number of frames if > 1 (photo)
    public double duration; // Length in seconds (audio / video)
    public int pages; // Number of printed pages
    public PersonChartConfig chart; // Configuration to generate a chart image
    public Boolean validated; // Whether this Attachment has been validated
  
    public Attachment() {
      validated = false;
    }
    
    /**
     * Copy constructor for Attachments.
     * @param other Attachment to copy.
     */
    public Attachment(Attachment other) {
      contentType = other.contentType;
      language = other.language;
      data = other.data;
      url = other.url;
      size = other.size;
      hash = other.hash;
      title = other.title;
      creation = other.creation;
      height = other.height;
      width = other.width;
      frames = other.frames;
      duration = other.duration;
      pages = other.pages;
      chart = other.chart;
      validated = other.validated;
    }

  
    /**
     * Processes the attachment for the given person. Generates a chart image if
     * the chart configuration is provided. Populates the hash field based on
     * inline data if provided/generated.
     * @param person Person to generate the chart for.
     */
    public void process(Person person) {
      // Check if chart configuration is provided to generate an image based on data
      // stored in the patient's attributes
      ChartRenderer.Base64EncodedChart renderedChart = null;
      if (chart != null) {
        try {
          renderedChart = ChartRenderer.drawChartAsBase64(person, chart);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }

        height = chart.getHeight();
        width = chart.getWidth();
        title = chart.getTitle();
        contentType = "image/png";
      }
      
      if (renderedChart != null) {
        data = renderedChart.getEncodedBytes();
        size = renderedChart.getUnencodedLength();
        
        // Generate the SHA-1 hash if it hasn't been provided
        if (hash == null) {
          MessageDigest md;
          try {
            md = MessageDigest.getInstance("SHA-1");
          } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
          }
          byte[] tmpdata = Base64.decodeBase64(data);
          hash = Base64.encodeBase64String(md.digest(tmpdata));
        }
      }
    }
  
    /**
     * Validates that attachment parameters are valid.
     */
    public void validate() {
      if (validated) {
        // Nothing to do if it has already been validated
        return;
      }
      // Module should define one, and only one, of "chart", "url", or "data"
      if (chart == null && url == null && data == null) {
        throw new RuntimeException("Attachments must provide one of:\n"
            + "1. \"chart\": a chart rendering configuration\n"
            + "2. \"url\": media location URL\n"
            + "3. \"data\": base64 encoded binary data");
      }
      
      if (chart != null && (url != null || data != null)) {
        throw new RuntimeException("Only 1 of \"chart\", \"url\", or \"data\" must be defined.");
      } else if (url != null && data != null) {
        throw new RuntimeException("Only 1 of \"chart\", \"url\", or \"data\" must be defined.");
      }
      
      if (data != null && !Base64.isBase64(data)) {
        throw new RuntimeException("Invalid Attachment data \"" + data
            + "\". If provided, this must be a Base64 encoded string.");
      }
    }
  }

}
