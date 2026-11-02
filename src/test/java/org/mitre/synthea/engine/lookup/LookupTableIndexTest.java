package org.mitre.synthea.engine.lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.Range;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Behavioral contract for matching CSV rows independently of transition sampling. */
@RunWith(Parameterized.class)
public class LookupTableIndexTest {
  private final LookupTableIndex.Implementation implementation;

  /**
   * Run the same contract against each implementation.
   * @return matching algorithms
   */
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> implementations() {
    return Arrays.asList(new Object[][] {
        {LookupTableIndex.Implementation.SCAN},
        {LookupTableIndex.Implementation.AGE_BUCKETS},
        {LookupTableIndex.Implementation.QUADTREE}
    });
  }

  /**
   * Select the implementation under test.
   * @param implementation matching algorithm
   */
  public LookupTableIndexTest(LookupTableIndex.Implementation implementation) {
    this.implementation = implementation;
  }

  /** Both dimensions must contain the point, with inclusive endpoints. */
  @Test
  public void matchesExactAttributesAndInclusiveAgeAndTimeRanges() {
    LookupTableIndex<String> index = LookupTableIndex.create(
        implementation, Arrays.asList(
            new LookupTableIndex.Row<>(Arrays.asList("F", "London"),
                Range.between(20, 40), Range.between(100L, 200L), "matched")));

    assertEquals(Collections.singletonList("matched"),
        index.findAll(Arrays.asList("F", "London"), 20, 100L));
    assertEquals(Collections.singletonList("matched"),
        index.findAll(Arrays.asList("F", "London"), 40, 200L));
    assertEquals(Collections.emptyList(),
        index.findAll(Arrays.asList("M", "London"), 30, 150L));
    assertEquals(Collections.emptyList(),
        index.findAll(Arrays.asList("F", "London"), 19, 150L));
    assertEquals(Collections.emptyList(),
        index.findAll(Arrays.asList("F", "London"), 30, 201L));
  }

  /** Overlaps, nested rules and duplicate rows all survive in input order. */
  @Test
  public void returnsEveryMatchingRowOnceInCsvOrder() {
    LookupTableIndex<String> index = overlappingIndex();
    assertEquals(Arrays.asList("all", "overlap", "point", "point"),
        index.findAll(List.of("F"), 30, 150L));
    assertEquals(List.of("all"), index.findAll(List.of("F"), null, 150L));
  }

  /** Exact keys preserve column position and commas within individual values. */
  @Test
  public void keepsExactAttributeColumnsDistinct() {
    LookupTableIndex<String> index = LookupTableIndex.create(implementation, Arrays.asList(
        new LookupTableIndex.Row<>(List.of("A", "B"), null, null, "original"),
        new LookupTableIndex.Row<>(List.of("B", "A"), null, null, "swapped"),
        new LookupTableIndex.Row<>(List.of("a,b", "c"), null, null, "first comma"),
        new LookupTableIndex.Row<>(List.of("a", "b,c"), null, null, "second comma")));
    assertEquals(List.of("original"), index.findAll(List.of("A", "B"), null, 0L));
    assertEquals(List.of("swapped"), index.findAll(List.of("B", "A"), null, 0L));
    assertEquals(List.of("first comma"), index.findAll(List.of("a,b", "c"), null, 0L));
    assertEquals(List.of("second comma"), index.findAll(List.of("a", "b,c"), null, 0L));
  }

  /** Construction isolates caller-owned lists and results cannot corrupt the index. */
  @Test
  public void isolatesStoredRowsAndResultsFromMutation() {
    List<String> attributes = new ArrayList<>(List.of("F"));
    List<LookupTableIndex.Row<String>> rows = new ArrayList<>();
    rows.add(new LookupTableIndex.Row<>(attributes, null, null, "saved"));
    LookupTableIndex<String> index = LookupTableIndex.create(implementation, rows);
    attributes.set(0, "M");
    rows.clear();
    List<String> results = index.findAll(List.of("F"), null, 0L);
    assertEquals(List.of("saved"), results);
    assertThrows(UnsupportedOperationException.class, () -> results.add("corruption"));
    assertEquals(List.of("saved"), index.findAll(List.of("F"), null, 0L));
    assertEquals(List.of(), LookupTableIndex.create(implementation, List.of())
        .findAll(List.of(), null, 0L));
  }

  /** Modules can retain their matching behavior across a serialization round trip. */
  @Test
  public void serializesWithItsMatchingBehavior() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(overlappingIndex());
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      LookupTableIndex<?> restored = (LookupTableIndex<?>) in.readObject();
      assertEquals(Arrays.asList("all", "overlap", "point", "point"),
          restored.findAll(List.of("F"), 30, 150L));
    }
  }

  /** Shared module indexes collect results independently for concurrent people. */
  @Test
  public void concurrentQueriesRetainIndependentResultLists() throws Exception {
    LookupTableIndex<String> index = overlappingIndex();
    ExecutorService workers = Executors.newFixedThreadPool(4);
    try {
      List<Callable<List<String>>> queries = new ArrayList<>();
      for (int query = 0; query < 100; query++) {
        final int age = query % 2 == 0 ? 30 : 19;
        queries.add(() -> index.findAll(List.of("F"), age, 150L));
      }
      List<Future<List<String>>> results = workers.invokeAll(queries);
      for (int query = 0; query < results.size(); query++) {
        assertEquals(query % 2 == 0 ? Arrays.asList("all", "overlap", "point", "point")
            : List.of("all"), results.get(query).get());
      }
    } finally {
      workers.shutdownNow();
    }
  }

  private LookupTableIndex<String> overlappingIndex() {
    return LookupTableIndex.create(implementation, Arrays.asList(
        new LookupTableIndex.Row<>(List.of("F"), null, null, "all"),
        new LookupTableIndex.Row<>(List.of("F"), Range.between(20, 40),
            Range.between(100L, 200L), "overlap"),
        new LookupTableIndex.Row<>(List.of("F"), Range.between(30, 30),
            Range.between(150L, 150L), "point"),
        new LookupTableIndex.Row<>(List.of("F"), Range.between(30, 30),
            Range.between(150L, 150L), "point")));
  }
}
