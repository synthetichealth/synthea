package org.mitre.synthea.identity;

import java.time.LocalDate;

import org.mitre.synthea.helpers.Utilities;

/**
 * A simple representation of a time period with a start and end date. End date may be null for
 * usage with Seeds to represent an open-ended range, if it is the last Seed for an Entity.
 */
public class Period {
  private LocalDate start;
  private LocalDate end;

  /**
   * Constructs a Period with a start and end date.
   * @param start the start date of the period
   * @param end the end date of the period, may be null for open-ended periods
   */
  public Period(LocalDate start, LocalDate end) {
    this.start = start;
    this.end = end;
  }

  /**
   * Checks if the given date is within this period.
   * @param date the date to check
   * @return true if the date is within the period, inclusive of start and end
   */
  public boolean contains(LocalDate date) {
    return ((this.start.isBefore(date) || this.start.isEqual(date))
        && ((this.end == null) || (this.end.isAfter(date) || this.end.isEqual(date))));
  }

  /**
   * Determines whether the timestamp is within this period.
   * @param timestamp to check
   * @return true if it is a part of this period inclusive of start and end
   */
  public boolean contains(long timestamp) {
    return contains(Utilities.timestampToLocalDate(timestamp));
  }

  /**
   * Gets the start date of the period.
   * @return the start date
   */
  public LocalDate getStart() {
    return start;
  }

  /**
   * Gets the end date of the period.
   * @return the end date, or null if open-ended
   */
  public LocalDate getEnd() {
    return end;
  }
}