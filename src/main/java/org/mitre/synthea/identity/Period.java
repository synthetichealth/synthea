package org.mitre.synthea.identity;

import java.time.LocalDate;
import java.time.ZoneOffset;

public class Period {
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
        && (this.end.isAfter(date) || this.end.isEqual(date)));
  }

  public boolean contains(long timestamp) {
    return (this.start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() <= timestamp)
        && (this.end.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() <= timestamp);
  }
}
