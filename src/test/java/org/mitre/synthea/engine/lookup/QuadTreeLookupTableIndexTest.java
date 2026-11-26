package org.mitre.synthea.engine.lookup;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.Range;
import org.junit.Test;
import org.mitre.synthea.engine.lookup.LookupTableIndex.Implementation;
import org.mitre.synthea.engine.lookup.LookupTableIndex.Row;

/** Tests rectangle matching through the shared lookup interface. */
public class QuadTreeLookupTableIndexTest {
  /** A query collects parent-spanning and child-local matches in original row order. */
  @Test
  public void collectsStraddlingAndLocalRulesWithoutDuplicates() {
    List<Row<String>> rows = new ArrayList<>();
    rows.add(row(0, 100, 0, 100, "all"));
    for (int coordinate = 0; coordinate < 40; coordinate++) {
      rows.add(row(coordinate, coordinate, coordinate, coordinate, "point" + coordinate));
    }
    rows.add(row(10, 30, 10, 30, "overlap"));
    LookupTableIndex<String> index = LookupTableIndex.create(Implementation.QUADTREE, rows);
    assertEquals(Arrays.asList("all", "point20", "overlap"),
        index.findAll(Collections.emptyList(), 20, 20L));
    assertEquals(Collections.singletonList("all"),
        index.findAll(Collections.emptyList(), 80, 80L));
    assertEquals(Collections.emptyList(), index.findAll(Collections.emptyList(), 101, 50L));
  }

  /** Age-only rules can descend without a finite time dimension. */
  @Test
  public void ageOnlyRulesAndUnconstrainedParentMatchWithoutTimeBounds() {
    List<Row<String>> rows = new ArrayList<>();
    rows.add(new Row<>(Collections.emptyList(), null, null, "all"));
    for (int coordinate = 0; coordinate < 40; coordinate++) {
      rows.add(new Row<>(Collections.emptyList(), Range.between(coordinate, coordinate),
          null, "age" + coordinate));
    }
    LookupTableIndex<String> index = LookupTableIndex.create(Implementation.QUADTREE, rows);
    assertEquals(Arrays.asList("all", "age20"),
        index.findAll(Collections.emptyList(), 20, Long.MIN_VALUE));
    assertEquals(Collections.singletonList("all"),
        index.findAll(Collections.emptyList(), null, Long.MAX_VALUE));
  }

  /** Time-only rectangles keep their exact millisecond endpoints and accept unknown age. */
  @Test
  public void timeOnlyRulesPreserveInclusiveEndpointsAtExtremeTimestamps() {
    List<Row<String>> rows = new ArrayList<>();
    rows.add(new Row<>(Collections.emptyList(), null, null, "all"));
    rows.add(new Row<>(Collections.emptyList(), null,
        Range.between(Long.MIN_VALUE, Long.MIN_VALUE), "minimum"));
    rows.add(new Row<>(Collections.emptyList(), null,
        Range.between(Long.MAX_VALUE, Long.MAX_VALUE), "maximum"));
    for (long coordinate = -20; coordinate < 20; coordinate++) {
      rows.add(new Row<>(Collections.emptyList(), null, Range.between(coordinate, coordinate),
          "time" + coordinate));
    }
    rows.add(new Row<>(Collections.emptyList(), null, Range.between(-1L, 0L), "split"));
    LookupTableIndex<String> index = LookupTableIndex.create(Implementation.QUADTREE, rows);
    assertEquals(Arrays.asList("all", "minimum"),
        index.findAll(Collections.emptyList(), null, Long.MIN_VALUE));
    assertEquals(Arrays.asList("all", "maximum"),
        index.findAll(Collections.emptyList(), null, Long.MAX_VALUE));
    assertEquals(Arrays.asList("all", "time-1", "split"),
        index.findAll(Collections.emptyList(), null, -1L));
    assertEquals(Arrays.asList("all", "time0", "split"),
        index.findAll(Collections.emptyList(), null, 0L));
    assertEquals(Collections.singletonList("all"),
        index.findAll(Collections.emptyList(), null, Long.MIN_VALUE + 1));
  }

  /** Identical rectangles are distinct rows and cannot cause endless subdivision. */
  @Test(timeout = 5000)
  public void identicalRectanglesTerminateAndRetainEveryRow() {
    List<Row<String>> rows = new ArrayList<>();
    for (int duplicate = 0; duplicate < 1000; duplicate++) {
      rows.add(row(10, 20, 30, 40, "same"));
    }
    LookupTableIndex<String> index = LookupTableIndex.create(Implementation.QUADTREE, rows);
    assertEquals(Collections.nCopies(1000, "same"),
        index.findAll(Collections.emptyList(), 10, 30L));
    assertEquals(Collections.nCopies(1000, "same"),
        index.findAll(Collections.emptyList(), 20, 40L));
    assertEquals(Collections.emptyList(), index.findAll(Collections.emptyList(), 21, 40L));
  }

  /** Both coordinate types retain their limits while unconstrained age remains distinct. */
  @Test
  public void mixedDimensionsHandleIntegerLimitsAndUnknownAge() {
    List<Row<String>> rows = new ArrayList<>();
    rows.add(new Row<>(Collections.emptyList(), null, null, "all"));
    rows.add(row(Integer.MIN_VALUE, Integer.MIN_VALUE,
        Long.MIN_VALUE, Long.MIN_VALUE, "minimum"));
    rows.add(row(Integer.MAX_VALUE, Integer.MAX_VALUE,
        Long.MAX_VALUE, Long.MAX_VALUE, "maximum"));
    for (int coordinate = 0; coordinate < 40; coordinate++) {
      rows.add(row(coordinate, coordinate, coordinate, coordinate, "point" + coordinate));
    }
    rows.add(new Row<>(Collections.emptyList(), null, Range.between(0L, 0L), "time-zero"));
    LookupTableIndex<String> index = LookupTableIndex.create(Implementation.QUADTREE, rows);
    assertEquals(Arrays.asList("all", "minimum"),
        index.findAll(Collections.emptyList(), Integer.MIN_VALUE, Long.MIN_VALUE));
    assertEquals(Arrays.asList("all", "maximum"),
        index.findAll(Collections.emptyList(), Integer.MAX_VALUE, Long.MAX_VALUE));
    assertEquals(Arrays.asList("all", "time-zero"),
        index.findAll(Collections.emptyList(), null, 0L));
    assertEquals(Arrays.asList("all", "point0", "time-zero"),
        index.findAll(Collections.emptyList(), 0, 0L));
  }

  private static Row<String> row(int lowAge, int highAge, long lowTime, long highTime,
      String value) {
    return new Row<>(Collections.emptyList(), Range.between(lowAge, highAge),
        Range.between(lowTime, highTime), value);
  }
}
