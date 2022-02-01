package org.mitre.synthea.identity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.Test;

public class PeriodTest {

  @Test
  public void contains() {
    LocalDate before = LocalDate.parse("1943-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    LocalDate start = LocalDate.parse("1944-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    LocalDate middle = LocalDate.parse("1945-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    LocalDate end = LocalDate.parse("1946-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    LocalDate after = LocalDate.parse("1947-04-17", DateTimeFormatter.ISO_LOCAL_DATE);

    Period p = new Period(start, end);
    assertFalse(p.contains(before));
    assertTrue(p.contains(middle));
    assertFalse(p.contains(after));

    Period openEnded = new Period(start, null);
    assertFalse(openEnded.contains(before));
    assertTrue(openEnded.contains(middle));
    assertTrue(openEnded.contains(after));
  }
}