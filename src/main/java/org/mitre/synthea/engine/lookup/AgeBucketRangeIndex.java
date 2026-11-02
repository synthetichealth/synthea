package org.mitre.synthea.engine.lookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Indexes clinical transition eligibility rules by age bucket, then checks age/time ranges. */
final class AgeBucketRangeIndex<T> implements RangeIndex<T> {
  private static final int BUCKET_WIDTH = 5;
  /** Bound eligibility-rule expansion; broad age ranges stay in a shared fallback list. */
  private static final int MAX_BUCKETS_PER_ROW = 256;

  private final Map<Integer, List<LookupTableIndex.Entry<T>>> buckets;
  private final List<LookupTableIndex.Entry<T>> unbucketed;

  AgeBucketRangeIndex(List<LookupTableIndex.Entry<T>> entries) {
    Map<Integer, List<LookupTableIndex.Entry<T>>> mutableBuckets = new HashMap<>();
    List<LookupTableIndex.Entry<T>> mutableUnbucketed = new ArrayList<>();
    for (LookupTableIndex.Entry<T> entry : entries) {
      if (entry.row.ageRange == null) {
        mutableUnbucketed.add(entry);
        continue;
      }
      int first = Math.floorDiv(entry.row.ageRange.getMinimum(), BUCKET_WIDTH);
      int last = Math.floorDiv(entry.row.ageRange.getMaximum(), BUCKET_WIDTH);
      if ((long) last - first + 1 > MAX_BUCKETS_PER_ROW) {
        mutableUnbucketed.add(entry);
        continue;
      }
      for (int bucket = first; bucket <= last; bucket++) {
        mutableBuckets.computeIfAbsent(bucket, key -> new ArrayList<>()).add(entry);
      }
    }
    Map<Integer, List<LookupTableIndex.Entry<T>>> frozenBuckets = new HashMap<>();
    mutableBuckets.forEach((bucket, values) -> frozenBuckets.put(bucket, List.copyOf(values)));
    buckets = Map.copyOf(frozenBuckets);
    unbucketed = List.copyOf(mutableUnbucketed);
  }

  @Override
  public void collect(Integer age, long time, List<LookupTableIndex.Entry<T>> results) {
    collectMatches(unbucketed, age, time, results);
    if (age == null) {
      return;
    }
    List<LookupTableIndex.Entry<T>> candidates = buckets.get(Math.floorDiv(age, BUCKET_WIDTH));
    if (candidates != null) {
      collectMatches(candidates, age, time, results);
    }
  }

  private void collectMatches(List<LookupTableIndex.Entry<T>> candidates, Integer age, long time,
      List<LookupTableIndex.Entry<T>> results) {
    for (LookupTableIndex.Entry<T> candidate : candidates) {
      if (candidate.matches(age, time)) {
        results.add(candidate);
      }
    }
  }
}
