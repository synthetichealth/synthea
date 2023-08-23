package org.mitre.synthea.export.rif.enrollment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import org.junit.Test;
import org.mitre.synthea.export.rif.identifiers.PlanBenefitPackageID;
import org.mitre.synthea.world.agents.Person;

public class ContractPeriodTest {

  @Test
  public void testPartDContractPeriod() {
    Person rand = new Person(System.currentTimeMillis());
    rand.attributes.put(Person.INCOME_LEVEL, "1.5");
    LocalDate start = LocalDate.of(2020, Month.MARCH, 15);
    LocalDate end = LocalDate.of(2021, Month.JUNE, 15);
    PartDContractHistory history = new PartDContractHistory(rand, Instant.now().toEpochMilli(), 10);
    PartDContractHistory.ContractPeriod period = history.new ContractPeriod(
            start, end, null, (PlanBenefitPackageID) null);
    assertNull(period.getContractID());
    assertFalse(period.coversYear(2019));
    assertEquals(0, period.getCoveredMonths(2019).size());
    assertTrue(period.coversYear(2020));
    List<Integer> twentyTwentyMonths = period.getCoveredMonths(2020);
    assertEquals(10, twentyTwentyMonths.size());
    assertFalse(twentyTwentyMonths.contains(1));
    assertFalse(twentyTwentyMonths.contains(2));
    assertTrue(twentyTwentyMonths.contains(3));
    assertTrue(twentyTwentyMonths.contains(4));
    assertTrue(twentyTwentyMonths.contains(5));
    assertTrue(twentyTwentyMonths.contains(6));
    assertTrue(twentyTwentyMonths.contains(7));
    assertTrue(twentyTwentyMonths.contains(8));
    assertTrue(twentyTwentyMonths.contains(9));
    assertTrue(twentyTwentyMonths.contains(10));
    assertTrue(twentyTwentyMonths.contains(11));
    assertTrue(twentyTwentyMonths.contains(12));
    assertTrue(period.coversYear(2021));
    List<Integer> twentyTwentyOneMonths = period.getCoveredMonths(2021);
    assertEquals(6, twentyTwentyOneMonths.size());
    assertTrue(twentyTwentyOneMonths.contains(1));
    assertTrue(twentyTwentyOneMonths.contains(2));
    assertTrue(twentyTwentyOneMonths.contains(3));
    assertTrue(twentyTwentyOneMonths.contains(4));
    assertTrue(twentyTwentyOneMonths.contains(5));
    assertTrue(twentyTwentyOneMonths.contains(6));
    assertFalse(twentyTwentyOneMonths.contains(7));
    assertFalse(twentyTwentyOneMonths.contains(8));
    assertFalse(twentyTwentyOneMonths.contains(9));
    assertFalse(twentyTwentyOneMonths.contains(10));
    assertFalse(twentyTwentyOneMonths.contains(11));
    assertFalse(twentyTwentyOneMonths.contains(12));
    assertFalse(period.coversYear(2022));
    assertEquals(0, period.getCoveredMonths(2022).size());
    LocalDate pointInTime = start;
    Instant instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    long timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    pointInTime = start.minusDays(1); // previous day (start is middle of month)
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    pointInTime = start.minusDays(15); // previous month
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertFalse(period.covers(timeInMillis));
    pointInTime = end;
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    pointInTime = end.plusDays(1); // next day (end is middle of month)
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    pointInTime = end.plusDays(16); // next month (end is middle of month)
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertFalse(period.covers(timeInMillis));
  }

}
