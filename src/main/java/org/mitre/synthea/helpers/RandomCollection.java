package org.mitre.synthea.helpers;

import java.io.Serializable;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

import org.mitre.synthea.world.agents.Person;

/**
 * Random collection of objects, with weightings. Intended to be an equivalent to the ruby Pickup
 * gem. Adapted from https://stackoverflow.com/a/6409791/630384
 */
public class RandomCollection<E> implements Serializable {
  private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
  private double total = 0;

  /**
   * Add an object (result) to the collection with a given weight.
   * If the weight is negative, the object (result) is not added to the collection.
   * Objects are retrieved by weight using the next method.
   * @param weight - Positive weight of the result.
   * @param result - the object to add to the collection.
   */
  public void add(double weight, E result) {
    if (weight <= 0) {
      return;
    }
    total += weight;
    map.put(total, result);
  }

  /**
   * Select an item from the collection at random by the weight of the items.
   * Selecting an item from one draw, does not remove the item from the collection
   * for subsequent draws. In other words, an item can be selected repeatedly if
   * the weights are severely imbalanced.
   * @param random - Random object.
   * @return a random item from the collection weighted by the item weights.
   */
  public E next(Random random) {
    return next(random.nextDouble() * total);
  }

  /**
   * Select an item from the collection at random by the weight of the items.
   * Selecting an item from one draw, does not remove the item from the collection
   * for subsequent draws. In other words, an item can be selected repeatedly if
   * the weights are severely imbalanced.
   * @param person - person object, and the source of the random number generator.
   * @return a random item from the collection weighted by the item weights.
   */
  public E next(Person person) {
    return next(person.rand() * total);
  }

  private E next(double value) {
    Entry<Double, E> entry = map.higherEntry(value);
    if (entry == null) {
      entry = map.lastEntry();
    }
    return entry.getValue();
  }
}
