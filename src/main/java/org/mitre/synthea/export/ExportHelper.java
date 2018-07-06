package org.mitre.synthea.export;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.hl7.fhir.dstu3.model.Condition;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

/**
 * Helper class for common logic that is used by more than one otherwise unrelated exporter.
 */
public abstract class ExportHelper {

  /**
   * Helper to get a readable string representation of an Observation's value.
   * Units are not included.
   * 
   * @param observation The observation to get the value from
   * @return A human-readable string representation of observation.value
   */
  public static String getObservationValue(Observation observation) {
    String value = null;
    
    if (observation.value instanceof Condition) {
      Code conditionCode = ((HealthRecord.Entry)observation.value).codes.get(0); 
      value = conditionCode.display;
    } else if (observation.value instanceof Code) {
      value = ((Code)observation.value).display;
    } else if (observation.value instanceof String) {
      value = (String)observation.value;
    } else if (observation.value instanceof Double) {
      // round to 1 decimal place for display
      value = String.format("%.1f", observation.value);
    } else if (observation.value != null) {
      value = observation.value.toString();
    }
    
    return value;
  }

  /**
   * Helper to get a readable string representation of an Observation's type.
   * 
   * @param observation The observation to get the type from
   * @return A human-readable string representation of the type of observation.value
   */
  public static String getObservationType(Observation observation) {
    String type = null;
    
    if (observation.value instanceof Condition) {
      type = "text";
    } else if (observation.value instanceof Code) {
      type = "text";
    } else if (observation.value instanceof String) {
      type = "text";
    } else if (observation.value instanceof Double) {
      type = "numeric";
    } else if (observation.value != null) {
      type = "text";
    }
    
    return type;
  }

  /**
   * Year-Month-Day date format.
   */
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("YYY-MM-dd");

  /**
   * Iso8601 date time format.
   */
  private static final SimpleDateFormat ISO_DATE_FORMAT = iso();

  /**
   * Create a SimpleDateFormat for iso8601.
   * @return Iso8601 date time format.
   */
  private static final SimpleDateFormat iso() {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    f.setTimeZone(TimeZone.getTimeZone("UTC"));
    return f;
  }

  /**
   * Get a date string in the format YYYY-MM-DD from the given time stamp.
   */
  public static String dateFromTimestamp(long time) {
    synchronized (DATE_FORMAT) {
      // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6231579
      return DATE_FORMAT.format(new Date(time));
    }
  }

  /**
   * Get an iso8601 string for the given time stamp.
   */
  public static String iso8601Timestamp(long time) {
    synchronized (ISO_DATE_FORMAT) {
      // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6231579
      return ISO_DATE_FORMAT.format(new Date(time));
    }
  }
}
