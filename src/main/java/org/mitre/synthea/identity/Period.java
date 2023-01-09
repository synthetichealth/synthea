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

  public Period(LocalDate start, LocalDate end) {
    this.start = start;
    this.end = end;
  }

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

  public LocalDate getStart() {
    return start;
  }

  public LocalDate getEnd() {
    return end;
  }
}