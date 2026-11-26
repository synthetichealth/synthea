package org.mitre.synthea.engine.lookup;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.Range;
import org.junit.Test;

/** Matching contract for sparse five-year age buckets. */
public class AgeBucketLookupTableIndexTest {
  /** Buckets narrow candidates without treating a shared bucket as a range match. */
  @Test
  public void returnsAllContainingRowsAndFiltersTheActualAgeAndTime() {
    LookupTableIndex<String> index = LookupTableIndex.create(
        LookupTableIndex.Implementation.AGE_BUCKETS, Arrays.asList(
            row(20, 29, 100L, 200L, "first"),
            row(23, 26, 150L, 250L, "second"),
            row(24, 24, 150L, 250L, "same bucket but wrong age")));

    assertEquals(Arrays.asList("first", "second"), index.findAll(List.of("F"), 25, 175L));
    assertEquals(Collections.singletonList("first"), index.findAll(List.of("F"), 20, 100L));
    assertEquals(Collections.singletonList("second"), index.findAll(List.of("F"), 25, 250L));
    assertEquals(Collections.emptyList(), index.findAll(List.of("F"), 30, 175L));
    assertEquals(Collections.emptyList(), index.findAll(List.of("M"), 25, 175L));
  }

  /** Missing range dimensions match any value, including an unavailable query age. */
  @Test
  public void combinesUnconstrainedDimensionsWithAgeBucketsInInputOrder() {
    LookupTableIndex<String> index = LookupTableIndex.create(
        LookupTableIndex.Implementation.AGE_BUCKETS, Arrays.asList(
            new LookupTableIndex.Row<>(List.of("F"), null, null, "unconstrained"),
            new LookupTableIndex.Row<>(List.of("F"), Range.between(25, 25), null, "age"),
            new LookupTableIndex.Row<>(List.of("F"), null,
                Range.between(100L, 200L), "time")));

    assertEquals(Arrays.asList("unconstrained", "age", "time"),
        index.findAll(List.of("F"), 25, 150L));
    assertEquals(Arrays.asList("unconstrained", "time"),
        index.findAll(List.of("F"), null, 150L));
    assertEquals(Arrays.asList("unconstrained", "age"),
        index.findAll(List.of("F"), 25, 201L));
  }

  /** Very wide integer ranges must not expand into hundreds of millions of buckets. */
  @Test
  public void supportsFullIntegerAgeRangesAndLongTimeEndpoints() {
    LookupTableIndex<String> index = LookupTableIndex.create(
        LookupTableIndex.Implementation.AGE_BUCKETS, Arrays.asList(
            row(Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, "wide"),
            row(Integer.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, "minimum"),
            row(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, "maximum")));

    assertEquals(Arrays.asList("wide", "minimum"),
        index.findAll(List.of("F"), Integer.MIN_VALUE, Long.MIN_VALUE));
    assertEquals(Arrays.asList("wide", "maximum"),
        index.findAll(List.of("F"), Integer.MAX_VALUE, Long.MAX_VALUE));
    assertEquals(Collections.singletonList("wide"), index.findAll(List.of("F"), -1, 0L));
    assertEquals(Collections.emptyList(), index.findAll(List.of("F"), null, 0L));
  }

  /** Negative bucket boundaries and duplicate rows retain inclusive matching and row order. */
  @Test
  public void matchesAcrossZeroAndRetainsDistinctRowsWithTheSamePayload() {
    LookupTableIndex<String> index = LookupTableIndex.create(
        LookupTableIndex.Implementation.AGE_BUCKETS, Arrays.asList(
            row(-6, -1, 0L, 10L, "negative"),
            row(-1, 1, 0L, 10L, "crosses zero"),
            row(-1, 1, 0L, 10L, "crosses zero")));

    assertEquals(Collections.singletonList("negative"), index.findAll(List.of("F"), -6, 5L));
    assertEquals(Arrays.asList("negative", "crosses zero", "crosses zero"),
        index.findAll(List.of("F"), -1, 5L));
    assertEquals(Arrays.asList("crosses zero", "crosses zero"),
        index.findAll(List.of("F"), 0, 5L));
    assertEquals(Collections.emptyList(), index.findAll(List.of("F"), -7, 5L));
    assertEquals(Collections.emptyList(), index.findAll(List.of("F"), 2, 5L));
  }

  private static LookupTableIndex.Row<String> row(int low, int high, long start, long end,
      String value) {
    return new LookupTableIndex.Row<>(List.of("F"), Range.between(low, high),
        Range.between(start, end), value);
  }
}
