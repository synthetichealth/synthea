package org.mitre.synthea.helpers;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * Random collection of objects, with weightings. Intended to be an equivalent to the ruby Pickup
 * gem. Adapted from https://stackoverflow.com/a/6409791/630384
 */
public class RandomCollection<E> {
  private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
  private double total = 0;

  public void add(double weight, E result) {
    if (weight <= 0) {
      return;
    }
    total += weight;
    map.put(total, result);
  }

  public E next(Random random) {
    double value = random.nextDouble() * total;
    return map.higherEntry(value).getValue();
  }
}
