package org.mitre.synthea.engine.lookup;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.Range;
import org.mitre.synthea.engine.Transition.DistributedTransitionOption;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * Eligibility rules and state-transition probability distributions for clinical condition modules.
 * The rules are loaded from a CSV representation and cached together with their matching index.
 */
public final class LookupTable implements Serializable {
  private static final ConcurrentMap<List<String>, LookupTable> TABLES = new ConcurrentHashMap<>();

  private final String fileName;
  private final List<String> attributes;
  private final LookupTableIndex<List<DistributedTransitionOption>> index;

  private LookupTable(String fileName, List<String> transitions,
      LookupTableIndex.Implementation implementation) {
    this.fileName = fileName;
    List<? extends Map<String, String>> rows;
    try {
      rows = SimpleCSV.parse(Utilities.readResource(fileName, true, true));
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load " + fileName, e);
    }
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("LOOKUP TABLE '" + fileName + "' ERROR: No rows.");
    }
    List<String> headers = new ArrayList<>(rows.get(0).keySet());
    int attributeCount = headers.size() - transitions.size();
    if (transitions.isEmpty() || attributeCount < 0) {
      throw new IllegalArgumentException("LOOKUP TABLE '" + fileName
          + "' ERROR: Invalid transition columns.");
    }
    attributes = List.copyOf(headers.subList(0, attributeCount));
    List<String> states = headers.subList(attributeCount, headers.size());
    if (!new HashSet<>(states).equals(new HashSet<>(transitions))
        || new HashSet<>(transitions).size() != transitions.size()) {
      throw new IllegalArgumentException("LOOKUP TABLE '" + fileName
          + "' ERROR: CSV column state name does not match a JSON state to transition to "
          + "in CSV table.");
    }

    List<LookupTableIndex.Row<List<DistributedTransitionOption>>> rules = new ArrayList<>();
    for (Map<String, String> row : rows) {
      List<String> exact = new ArrayList<>();
      Range<Integer> ageRange = null;
      Range<Long> timeRange = null;
      for (String attribute : attributes) {
        String value = row.get(attribute);
        if (attribute.equalsIgnoreCase("age")) {
          ageRange = parseAgeRange(value);
        } else if (attribute.equalsIgnoreCase("time")) {
          timeRange = Utilities.parseDateRange(value);
        } else {
          exact.add(value);
        }
      }
      List<DistributedTransitionOption> probabilities = new ArrayList<>();
      for (String state : states) {
        probabilities.add(new DistributedTransitionOption(state,
            Double.parseDouble(row.get(state))));
      }
      rules.add(new LookupTableIndex.Row<>(exact, ageRange, timeRange, List.copyOf(probabilities)));
    }
    index = LookupTableIndex.create(implementation, rules);
  }

  /**
   * Load transition rules, sharing their attribute schema and index with equivalent transitions.
   * @param fileName resource name or filesystem path including the configured directory
   * @param transitions destination state names within the clinical condition module
   * @param implementation matching algorithm
   * @return cached table, keyed by path, transition schema and matching algorithm
   */
  public static LookupTable load(String fileName, List<String> transitions,
      LookupTableIndex.Implementation implementation) {
    List<String> schema = List.copyOf(transitions);
    List<String> key = new ArrayList<>();
    key.add(fileName);
    key.add(implementation.name());
    key.addAll(schema);
    return TABLES.computeIfAbsent(List.copyOf(key),
        ignored -> new LookupTable(fileName, schema, implementation));
  }

  private Range<Integer> parseAgeRange(String value) {
    int hyphen = value.indexOf('-');
    if (hyphen <= 0 || hyphen == value.length() - 1) {
      throw new IllegalArgumentException("LOOKUP TABLE '" + fileName
          + "' ERROR: Age Range must be in the form: 'ageLow-ageHigh'. Found '" + value + "'");
    }
    return Range.between(Integer.parseInt(value.substring(0, hyphen)),
        Integer.parseInt(value.substring(hyphen + 1)));
  }

  /**
   * Resolve the probability distribution for transitions between states in a condition module.
   * When eligibility rules overlap, the first declared rule takes precedence. The selected
   * distribution is returned unchanged; matching neither samples nor combines distributions.
   * @param person person whose exact attributes and integer age are queried
   * @param time simulation timestamp in milliseconds
   * @return destination states and their probabilities, or an empty list if no rule matches
   */
  public List<DistributedTransitionOption> findDistribution(Person person, long time) {
    List<String> exact = new ArrayList<>();
    Integer age = null;
    for (String attribute : attributes) {
      if (attribute.equalsIgnoreCase("age")) {
        age = person.ageInYears(time);
      } else if (!attribute.equalsIgnoreCase("time")) {
        Object value = person.attributes.get(attribute);
        if (value == null) {
          throw new IllegalArgumentException("LOOKUP TABLE ERROR: Attribute '" + attribute
              + "' in CSV table '" + fileName
              + "' does not exist as one of this person's attributes.");
        }
        exact.add(value.toString());
      }
    }
    List<List<DistributedTransitionOption>> distributions = index.findAll(exact, age, time);
    return distributions.isEmpty() ? List.of() : distributions.get(0);
  }
}
