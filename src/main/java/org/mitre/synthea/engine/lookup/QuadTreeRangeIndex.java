package org.mitre.synthea.engine.lookup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mitre.synthea.engine.lookup.LookupTableIndex.Entry;

/**
 * Immutable rectangle quadtree for clinical transition eligibility rules. Rule age/time
 * rectangles crossing a split remain at their parent; each query gathers eligible rules
 * along one descent without replicating stored rules or altering their probability distributions.
 * Unproductive four-way splits fall back to an age or time split.
 *
 * @param <T> rule value, typically a state-transition probability distribution
 */
final class QuadTreeRangeIndex<T> implements RangeIndex<T> {
  private static final int LEAF_SIZE = 16;
  private static final int MAX_DEPTH = 64;

  private final Node<T> root;

  QuadTreeRangeIndex(List<Entry<T>> rows) {
    root = new Node<>(rows, 0);
  }

  @Override
  public void collect(Integer age, long time, List<Entry<T>> results) {
    root.collect(age, time, results);
  }

  private static final class Node<T> implements Serializable {
    private final List<Entry<T>> local;
    private final List<Node<T>> children;
    private final boolean splitAge;
    private final boolean splitTime;
    private final int ageMidpoint;
    private final long timeMidpoint;

    Node(List<Entry<T>> rows, int depth) {
      int lowAge = Integer.MAX_VALUE;
      int highAge = Integer.MIN_VALUE;
      long lowTime = Long.MAX_VALUE;
      long highTime = Long.MIN_VALUE;
      for (Entry<T> entry : rows) {
        if (entry.row.ageRange != null) {
          lowAge = Math.min(lowAge, entry.row.ageRange.getMinimum());
          highAge = Math.max(highAge, entry.row.ageRange.getMaximum());
        }
        if (entry.row.timeRange != null) {
          lowTime = Math.min(lowTime, entry.row.timeRange.getMinimum());
          highTime = Math.max(highTime, entry.row.timeRange.getMaximum());
        }
      }
      // Signed floor averages without addition overflow, including extreme timestamps.
      ageMidpoint = (lowAge & highAge) + ((lowAge ^ highAge) >> 1);
      timeMidpoint = (lowTime & highTime) + ((lowTime ^ highTime) >> 1);
      Partition<T> best = null;
      if (rows.size() > LEAF_SIZE && depth < MAX_DEPTH) {
        if (lowAge < highAge && lowTime < highTime) {
          best = partition(rows, true, true);
        }
        if (lowAge < highAge) {
          best = prefer(best, partition(rows, true, false));
        }
        if (lowTime < highTime) {
          best = prefer(best, partition(rows, false, true));
        }
      }
      if (best == null || best.moved == 0 || best.largestChild == rows.size()) {
        local = List.copyOf(rows);
        children = List.of();
        splitAge = false;
        splitTime = false;
      } else {
        local = List.copyOf(best.local);
        splitAge = best.splitAge;
        splitTime = best.splitTime;
        List<Node<T>> branches = new ArrayList<>(4);
        for (List<Entry<T>> branch : best.branches) {
          branches.add(branch.isEmpty() ? null : new Node<>(branch, depth + 1));
        }
        children = Collections.unmodifiableList(branches);
      }
    }

    private Partition<T> prefer(Partition<T> current, Partition<T> candidate) {
      return current == null || candidate.moved > current.moved ? candidate : current;
    }

    private Partition<T> partition(List<Entry<T>> rows, boolean useAge, boolean useTime) {
      Partition<T> partition = new Partition<>(useAge, useTime);
      for (Entry<T> entry : rows) {
        int ageSide = 0;
        if (useAge) {
          if (entry.row.ageRange == null) {
            ageSide = -1;
          } else if (entry.row.ageRange.getMaximum() <= ageMidpoint) {
            ageSide = 0;
          } else if (entry.row.ageRange.getMinimum() > ageMidpoint) {
            ageSide = 1;
          } else {
            ageSide = -1;
          }
        }
        int timeSide = 0;
        if (useTime) {
          if (entry.row.timeRange == null) {
            timeSide = -1;
          } else if (entry.row.timeRange.getMaximum() <= timeMidpoint) {
            timeSide = 0;
          } else if (entry.row.timeRange.getMinimum() > timeMidpoint) {
            timeSide = 1;
          } else {
            timeSide = -1;
          }
        }
        if (ageSide < 0 || timeSide < 0) {
          partition.local.add(entry);
        } else {
          List<Entry<T>> branch = partition.branches.get(ageSide + 2 * timeSide);
          branch.add(entry);
          partition.moved++;
          partition.largestChild = Math.max(partition.largestChild, branch.size());
        }
      }
      return partition;
    }

    void collect(Integer age, long time, List<Entry<T>> results) {
      for (Entry<T> entry : local) {
        if (entry.matches(age, time)) {
          results.add(entry);
        }
      }
      if (children.isEmpty() || (splitAge && age == null)) {
        return;
      }
      int branch = splitAge && age > ageMidpoint ? 1 : 0;
      if (splitTime && time > timeMidpoint) {
        branch += 2;
      }
      Node<T> child = children.get(branch);
      if (child != null) {
        child.collect(age, time, results);
      }
    }
  }

  private static final class Partition<T> {
    final boolean splitAge;
    final boolean splitTime;
    final List<Entry<T>> local = new ArrayList<>();
    final List<List<Entry<T>>> branches = new ArrayList<>();
    int moved;
    int largestChild;

    Partition(boolean splitAge, boolean splitTime) {
      this.splitAge = splitAge;
      this.splitTime = splitTime;
      for (int branch = 0; branch < 4; branch++) {
        branches.add(new ArrayList<>());
      }
    }
  }
}
