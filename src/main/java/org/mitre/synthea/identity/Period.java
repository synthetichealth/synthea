package org.mitre.synthea.identity;

import static org.mitre.synthea.helpers.Utilities.localDateToTimestamp;

import java.io.Serializable;
import java.time.LocalDate;

public class Period implements Serializable {
  private LocalDate start;
  private LocalDate end;

  public Period() {

  }

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
    return (localDateToTimestamp(this.start) <= timestamp)
        && ((this.end == null)
        || (localDateToTimestamp(this.end) >= timestamp));
  }

  public boolean isBefore(long timestamp) {
    long startStamp = localDateToTimestamp(this.start);
    return timestamp <= startStamp;
  }

  public LocalDate getStart() {
    return start;
  }

  public LocalDate getEnd() {
    return end;
  }
}