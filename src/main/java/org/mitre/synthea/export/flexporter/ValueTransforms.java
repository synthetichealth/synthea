package org.mitre.synthea.export.flexporter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public abstract class ValueTransforms {

  /**
   * Apply the selected Value Transform on the given value.
   *
   * @param originalValue Source value
   * @param transformName Name of the transform to apply
   * @return transformed value
   */
  public static String apply(String originalValue, String transformName) {
    if (transformName == null) {
      return originalValue;
    }

    String newValue;

    switch (transformName) {
      case "toInstant":
        newValue = toInstant(originalValue);
        break;

      case "toDateTime":
        newValue = toDateTime(originalValue);
        break;

      case "toDate":
        newValue = toDate(originalValue);
        break;

      case "toTime":
        newValue = toTime(originalValue);
        break;

      default:
        throw new IllegalArgumentException("Unrecognized transform name: " + transformName);
    }

    return newValue;
  }

  /**
   * Convert the given date/time string into an Instant.
   * @param src Source date/time string
   * @return Input date/time reformatted as an Instant
   */
  public static String toInstant(String src) {
    if (src == null) {
      return null;
    }

    ZonedDateTime dateTime = parse(src);

    return dateTime.toInstant().toString();
  }

  /**
   * Convert the given date/time string into a DateTime.
   * @param src Source date/time string
   * @return Input date/time reformatted as a DateTime
   */
  public static String toDateTime(String src) {
    ZonedDateTime dateTime = parse(src);

    return dateTime.toInstant().toString();
  }

  /**
   * Convert the given date/time string into a Date.
   * @param src Source date/time string
   * @return Input date/time reformatted as a Date
   */
  public static String toDate(String src) {
    if (src == null) {
      return null;
    }

    ZonedDateTime dateTime = parse(src);

    return DateTimeFormatter.ISO_LOCAL_DATE.format(dateTime);
  }

  /**
   * Convert the given date/time string into a Time.
   * @param src Source date/time string
   * @return Input date/time reformatted as a Time
   */
  public static String toTime(String src) {
    if (src == null) {
      return null;
    }

    ZonedDateTime dateTime = parse(src);

    return DateTimeFormatter.ISO_LOCAL_TIME.format(dateTime);
  }

  private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

  private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final ZoneId UTC = ZoneId.of("UTC");

  private static ZonedDateTime parse(String src) {
    // assume src is one of the four formats already

    // instant: YYYY-MM-DDThh:mm:ss.sss+zz:zz
    // date: YYYY, YYYY-MM, or YYYY-MM-DD
    // dateTime: YYYY, YYYY-MM, YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+zz:zz
    // time: hh:mm:ss

    switch (src.length()) {
      case 4:
        // YYYY
        return LocalDate.parse(src, YEAR).atStartOfDay(ZoneId.systemDefault());
      case 7:
        // YYYY-MM
        return LocalDate.parse(src, YEAR_MONTH).atStartOfDay(ZoneId.systemDefault());
      case 8:
        // hh:mm:ss
        return ZonedDateTime.parse(src, DateTimeFormatter.ISO_LOCAL_TIME);
      case 10:
        // YYYY-MM-DD
        return LocalDate.parse(src, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(UTC);
      default:
        // TODO: make sure this actually works -- the docs are weird
        return ZonedDateTime.parse(src, DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
  }

}
