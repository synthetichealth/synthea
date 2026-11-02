package org.mitre.synthea.engine.lookup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.Range;

/**
 * Immutable index of eligibility rules for state transitions in clinical condition simulations.
 * A rule associates exact person attributes and age/time ranges with a value, typically a
 * probability distribution over destination states. Queries return all matching rule values
 * in declaration order, preserving duplicate and overlapping rules. Missing range dimensions
 * are unconstrained. Matching does not sample or alter probability distributions.
 *
 * @param <T> rule value, which must be serializable if the index is serialized
 */
public final class LookupTableIndex<T> implements Serializable {
  /** Selectable matching algorithms with the same all-match contract. */
  public enum Implementation {
    SCAN, AGE_BUCKETS, QUADTREE
  }

  /** A transition eligibility rule, its age/time rectangle and its associated value. */
  public static final class Row<T> implements Serializable {
    final List<String> attributes;
    final Range<Integer> ageRange;
    final Range<Long> timeRange;
    final T value;

    /**
     * Create an eligibility rule. Null ranges mean that the dimension is unconstrained.
     * @param attributes exact person attribute values in the declared attribute order
     * @param ageRange inclusive age range, or null
     * @param timeRange inclusive simulation-time range, or null
     * @param value rule value, typically a state-transition probability distribution
     */
    public Row(List<String> attributes, Range<Integer> ageRange, Range<Long> timeRange, T value) {
      this.attributes = List.copyOf(attributes);
      this.ageRange = ageRange;
      this.timeRange = timeRange;
      this.value = Objects.requireNonNull(value);
    }
  }

  static final class Entry<T> implements Serializable {
    final Row<T> row;
    final int order;

    Entry(Row<T> row, int order) {
      this.row = row;
      this.order = order;
    }

    boolean matches(Integer age, long time) {
      return (row.ageRange == null || (row.ageRange.contains(age)))
          && (row.timeRange == null || row.timeRange.contains(time));
    }
  }

  private final Map<List<String>, RangeIndex<T>> groups;

  private LookupTableIndex(Map<List<String>, RangeIndex<T>> groups) {
    this.groups = Map.copyOf(groups);
  }

  /**
   * Build a shared index of transition eligibility rules in declaration order.
   * @param implementation matching algorithm
   * @param rows eligibility rules and their associated values
   * @param <T> rule value type
   * @return immutable index
   */
  public static <T> LookupTableIndex<T> create(Implementation implementation, List<Row<T>> rows) {
    Objects.requireNonNull(implementation);
    Map<List<String>, List<Entry<T>>> grouped = new HashMap<>();
    for (int order = 0; order < rows.size(); order++) {
      Row<T> row = rows.get(order);
      grouped.computeIfAbsent(row.attributes, key -> new ArrayList<>())
          .add(new Entry<>(row, order));
    }
    Map<List<String>, RangeIndex<T>> indexes = new HashMap<>();
    grouped.forEach((attributes, entries) -> {
      switch (implementation) {
        case SCAN:
          indexes.put(attributes, new ScanRangeIndex<>(entries));
          break;
        case AGE_BUCKETS:
          indexes.put(attributes, new AgeBucketRangeIndex<>(entries));
          break;
        case QUADTREE:
          indexes.put(attributes, new QuadTreeRangeIndex<>(entries));
          break;
        default:
          throw new UnsupportedOperationException("Index not implemented: " + implementation);
      }
    });
    return new LookupTableIndex<>(indexes);
  }

  /**
   * Find every matching transition eligibility rule once, in declaration order.
   * @param attributes exact person attribute values in the declared attribute order
   * @param age integer age, or null when age is unavailable
   * @param time simulation timestamp in milliseconds
   * @return immutable list of matching rule values, possibly empty
   */
  public List<T> findAll(List<String> attributes, Integer age, long time) {
    RangeIndex<T> index = groups.get(attributes);
    if (index == null) {
      return List.of();
    }
    List<Entry<T>> matches = new ArrayList<>();
    index.collect(age, time, matches);
    matches.sort(Comparator.comparingInt(entry -> entry.order));
    List<T> values = new ArrayList<>(matches.size());
    for (Entry<T> match : matches) {
      values.add(match.row.value);
    }
    return List.copyOf(values);
  }

  private static final class ScanRangeIndex<T> implements RangeIndex<T> {
    private final List<Entry<T>> entries;

    ScanRangeIndex(List<Entry<T>> entries) {
      this.entries = List.copyOf(entries);
    }

    @Override
    public void collect(Integer age, long time, List<Entry<T>> results) {
      for (Entry<T> entry : entries) {
        if (entry.matches(age, time)) {
          results.add(entry);
        }
      }
    }
  }
}
