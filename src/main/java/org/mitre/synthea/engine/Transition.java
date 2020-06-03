package org.mitre.synthea.engine;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.Range;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * Transition represents all the transition types within the generic module
 * framework. This class is stateless, and calling 'follow' on an instance must
 * not modify state as instances of Transition within States and Modules are
 * shared across the population.
 */
public abstract class Transition implements Serializable {

  protected List<String> remarks;

  /**
   * Get the name of the next state.
   * 
   * @param person : person being processed
   * @param time   : time of this transition
   * @return name : name of the next state
   */
  public abstract String follow(Person person, long time);

  /**
   * Direct transitions are the simplest of transitions. They transition directly
   * to the indicated state. The value of a direct_transition is simply the name
   * of the state to transition to.
   */
  public static class DirectTransition extends Transition {
    private String transition;

    public DirectTransition(String transition) {
      this.transition = transition;
    }

    @Override
    public String follow(Person person, long time) {
      return transition;
    }
  }

  /**
   * A TransitionOption represents a single destination state that may be
   * transitioned to.
   */
  private abstract static class TransitionOption implements Serializable {
    protected String transition;
  }

  /**
   * A DistributedTransitionOption represents a single destination state, with a
   * given distribution percentage, or a named distribution representing an
   * attribute containing the distribution percentage.
   */
  public static final class DistributedTransitionOption extends TransitionOption {
    private Object distribution;
    private Double numericDistribution;
    private NamedDistribution namedDistribution;
  }

  /**
   * Distributed transitions will transition to one of several possible states
   * based on the configured distribution. Distribution values are from 0.0 to
   * 1.0, such that a value of 0.55 would indicate a 55% chance of transitioning
   * to the corresponding state. A distributed_transition consists of an array of
   * distribution/transition pairs for which the distribution values should sum up
   * to 1.0. If the distribution values do not sum up to 1.0, the remaining
   * distribution will transition to the last defined transition state. For
   * example, given distributions of 0.3 and 0.6, the effective distribution of
   * the last transition will actually be 0.7. If the distribution values sum up
   * to more than 1.0, the remaining distributions are ignored (for example, if
   * distribution values are 0.75, 0.5, and 0.3, then the second transition will
   * have an effective distribution of 0.25, and the last transition will have an
   * effective distribution of 0.0).
   */
  public static final class DistributedTransition extends Transition {
    private List<DistributedTransitionOption> transitions;

    public DistributedTransition(List<DistributedTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      return pickDistributedTransition(transitions, person);
    }
  }

  /**
   * A LookupTableTransitionOption represents a destination state which will be
   * compared with a table lookup to find its probability and attributes.
   */
  public static final class LookupTableTransitionOption extends TransitionOption {
    private String lookupTableName;
    private double defaultProbability;
  }

  /**
   * LookupTable transitions will transition to one of any number of possible states
   * based on probabilities for each state extacted from a table. A
   * LookUpTableTransition has a field denoting the lookup table csv file to use. A
   * table may have any set of attributes to determine the probabilities of any
   * set of states to transition to which are compared to the attributes of a person.
   * If a person does not correspond to any of the sets of attributes, the transition
   * will use the default probabilities defined in the JSON file.
   */
  public static class LookupTableTransition extends Transition {

    // Map of lookupTables
    private static HashMap<String, HashMap<LookupTableKey, List<DistributedTransitionOption>>>
        lookupTables = new HashMap<String, HashMap<LookupTableKey,
        List<DistributedTransitionOption>>>();
    private final List<LookupTableTransitionOption> transitions;
    private List<String> attributes;
    private List<DistributedTransitionOption> defaultTransitions;
    private String lookupTableName;

    /**
     * Constructor for LookupTableTransition.
     * @param lookupTableTransitions transitions parsed from JSON
     */
    public LookupTableTransition(List<LookupTableTransitionOption> lookupTableTransitions) {

      this.transitions = lookupTableTransitions;
      this.defaultTransitions = loadDefaultTransitions();
      this.lookupTableName = lookupTableTransitions.get(0).lookupTableName;
      if (lookupTableName == null) {
        throw new RuntimeException(
          "LOOKUP TABLE JSON ERROR: Table name cannot be null.");
      }
      if (!lookupTables.containsKey(lookupTableName)) {
        loadLookupTable();
      }
    }

    /**
     * Loads the default transitions for this transition.
     */
    private List<DistributedTransitionOption> loadDefaultTransitions() {
      List<DistributedTransitionOption> defaultTransitions
          = new ArrayList<DistributedTransitionOption>();
      for (LookupTableTransitionOption transitionOption : this.transitions) {
        DistributedTransitionOption distributedTransitionOption = new DistributedTransitionOption();
        distributedTransitionOption.transition = transitionOption.transition;
        distributedTransitionOption.numericDistribution = transitionOption.defaultProbability;
        defaultTransitions.add(distributedTransitionOption);
      }
      return defaultTransitions;
    }

    /**
     * Loads the current lookuptable.
     */
    private void loadLookupTable() {

      System.out.println("Loading Lookup Table: " + lookupTableName);
      // Hashmap for the new lookup table.
      HashMap<LookupTableKey, List<DistributedTransitionOption>> newTable
          = new HashMap<LookupTableKey, List<DistributedTransitionOption>>();
      
      // Load in this transitions's CSV file.
      String fileName = Config.get("generate.lookup_tables") + lookupTableName;
      List<? extends Map<String, String>> lookupTable = null;
      try {
        String csv = Utilities.readResource(fileName);
        if (csv.startsWith("\uFEFF")) {
          csv = csv.substring(1); // Removes BOM.
        }
        lookupTable = SimpleCSV.parse(csv);
      } catch (IOException e) {
        e.printStackTrace();
      }

      // Retrieve CSV column headers.
      List<String> columnHeaders = new ArrayList<String>(lookupTable.get(0).keySet());
      // Parse the list of attributes.
      this.attributes = new ArrayList<String>(columnHeaders.subList(0,
          columnHeaders.size() - this.transitions.size()));
      // Parse the list of states to transition to.
      List<String> transitionStates = columnHeaders.subList((columnHeaders.size()
          - this.transitions.size()), columnHeaders.size());

      // Create keys and insert each row of CSV into lookup table map.
      for (Map<String, String> currentRow : lookupTable) {
        // Extract attributes from current CSV row.
        List<String> rowAttributes = new ArrayList<String>(currentRow.values());
        rowAttributes = rowAttributes.subList(0, this.attributes.size());
        // Create age range for lookup table key if age is an attribute.
        Range<Integer> ageRange = null;
        Range<Long> timeRange = null;
        if (this.attributes.contains("age")) {
          Integer ageIndex = this.attributes.indexOf("age");
          // Remove and parse the age range.
          String value = rowAttributes.remove(ageIndex.intValue());
          if (!value.contains("-")
              || value.substring(0, value.indexOf("-")).length() < 1
              || value.substring(value.indexOf("-") + 1).length() < 1) {
            throw new RuntimeException(
                "LOOKUP TABLE '" + fileName
                + "' ERROR: Age Range must be in the form: 'ageLow-ageHigh'. Found '"
                + value + "'");
          }
          ageRange = Range.between(
              Integer.parseInt(value.substring(0, value.indexOf("-"))),
              Integer.parseInt(value.substring(value.indexOf("-") + 1)));
        }
        if (this.attributes.contains("time")) {
          Integer timeIndex = this.attributes.indexOf("time");
          // Remove and parse the age range.
          String value = rowAttributes.remove(timeIndex.intValue());
          if (!value.contains("-")
              || value.substring(0, value.indexOf("-")).length() < 1
              || value.substring(value.indexOf("-") + 1).length() < 1) {
            throw new RuntimeException(
                "LOOKUP TABLE '" + fileName
                    + "' ERROR: Time Range must be in the form: 'timeLow-timeHigh'. Found '"
                    + value + "'");
          }
          timeRange = Range.between(
              Long.parseLong(value.substring(0, value.indexOf("-"))),
              Long.parseLong(value.substring(value.indexOf("-") + 1)));
        }
        // Attributes key to inert into lookup table.
        LookupTableKey attributesLookupKey =
            new LookupTableKey(rowAttributes, ageRange, timeRange);
        // Transition probabilities to insert into lookup table.
        List<DistributedTransitionOption> transitionProbabilities
            = createDistributedTransitionOptions(currentRow, transitionStates);
        // Insert the parsed attributes and transition probabilities into lookup table.
        newTable.put(attributesLookupKey, transitionProbabilities);
      }

      // Put new table into Hash map of all lookup tables.
      lookupTables.put(lookupTableName, newTable);
    }

    /**
     *  Creates Distributed Transition Options based on CSV row probabilities.
     */
    private ArrayList<DistributedTransitionOption> createDistributedTransitionOptions(
        Map<String, String> currentRow, List<String> transitionStates) {

      ArrayList<DistributedTransitionOption> transitionProbabilities
          = new ArrayList<DistributedTransitionOption>();
      
      for (String transitionName : transitionStates) {
        if (currentRow.containsKey(transitionName)
            && transitions.stream().anyMatch(t -> t.transition.equals(transitionName))) {
          DistributedTransitionOption currentOption = new DistributedTransitionOption();
          currentOption.numericDistribution = Double.parseDouble(currentRow.get(transitionName));
          currentOption.transition = transitionName;
          transitionProbabilities.add(currentOption);
        } else {
          throw new RuntimeException("LOOKUP TABLE ERROR: CSV column state name '"
              + transitionName + "' does not match a JSON state to transition to in CSV '"
              + lookupTableName + "'");
        }
      }
      return transitionProbabilities;
    }

    @Override
    public String follow(Person person, long time) {
      Integer age = null;
      // Extract Person's list of relevant attributes.
      ArrayList<String> personsAttributes = new ArrayList<String>();
      for (String currentAttribute : this.attributes) {
        if (currentAttribute.equalsIgnoreCase("age")) {
          age = person.ageInYears(time);
        } else if (currentAttribute.equalsIgnoreCase("time")) {
          // do nothing, we already have it
        } else {
          String personsAttribute
              = (String) person.attributes.get(currentAttribute.toLowerCase());
          if (personsAttribute == null) {
            throw new RuntimeException("LOOKUP TABLE ERROR: Attribute '"
                + currentAttribute + "' in CSV table '" + this.lookupTableName
                + "' does not exist as one of this person's attributes.");
          }
          personsAttributes.add(personsAttribute);
        }
      }
      // Create key from person's attributes to get distributions
      LookupTableKey personsAttributesLookupKey = new LookupTableKey(personsAttributes, age, time);
      if (lookupTables.get(lookupTableName).containsKey(personsAttributesLookupKey)) {
        // Person matches, use their attribute's list of distributedtransitionoptions
        return pickDistributedTransition(
          lookupTables.get(lookupTableName).get(personsAttributesLookupKey), person);
      } else {
        // No attribute match, use default transition.
        return pickDistributedTransition(this.defaultTransitions, person);
      }
    }
  }

  public final class LookupTableKey implements Serializable {
    private final List<String> attributes;
    /** Age for this patient. May be null if lookup table does not use age. */
    private final Integer age;
    /** Age range for this row. Null if this is a person. */
    private final Range<Integer> ageRange;

    private final Long time;
    private final Range<Long> timeRange;

    /**
     * Create a symbolic lookup key for a given row that contains actual patient values.
     * @param attributes Patient attribute values.
     * @param age Patient age.
     */
    public LookupTableKey(List<String> attributes, Integer age, Long time) {
      this.attributes = attributes;
      this.age = age;
      this.ageRange = null;
      this.time = time;
      this.timeRange = null;
    }

    /**
     * Create a symbolic lookup key for a given row that contains lookup values.
     * @param attributes Table attribute values.
     * @param range If the table contains an age column, range contains the age range
     *     information for this key.
     */
    public LookupTableKey(List<String> attributes, Range<Integer> range, Range<Long> timeRange) {
      this.attributes = attributes;
      this.age = null;
      this.ageRange = range;
      this.time = null;
      this.timeRange = timeRange;
    }

    /**
     * Overrides the hashcode method. Returns the hash of the List of attributes,
     * forcing age range to be a hash collision.
     */
    @Override
    public int hashCode() {
      return this.attributes.hashCode();
    }

    /**
     * Overides the equals method. If there is no age in this tranistion, then it just
     * returns the default List.equals(). If there is an age range, then the age
     * must fit in the range for this to return true.
     * 
     * @param obj the object to check that this equals.
     */
    @Override
    public boolean equals(Object obj) {
      if (obj == null || this.getClass() != obj.getClass()) {
        return false;
      }
      LookupTableKey that = (LookupTableKey) obj;

      boolean agesMatch = true;
      boolean timesMatch = true;

      if (this.age != null) {
        if (that.age != null) {
          agesMatch = (this.age == that.age);
        } else if (that.ageRange != null) {
          agesMatch = (that.ageRange.contains(this.age));
        } else {
          // that.age == null && that.ageRange == null
          agesMatch = false;
        }
      } else if (that.age != null) {
        if (this.ageRange != null) {
          agesMatch = (this.ageRange.contains(that.age));
        } else {
          // this.age == null && this.ageRange == null
          agesMatch = false;
        }
      } else if (this.ageRange != null) {
        // this.age == null && that.age == null
        if (that.ageRange != null) {
          agesMatch = this.ageRange.containsRange(that.ageRange);
        } else {
          agesMatch = false;
        }
      } else if (that.ageRange != null) {
        agesMatch = false;
      }

      if (this.time != null) {
        if (that.time != null) {
          timesMatch = (this.time == that.time);
        } else if (that.timeRange != null) {
          timesMatch = (that.timeRange.contains(this.time));
        } else {
          // that.age == null && that.ageRange == null
          // do nothing. Time will always be populated in one of them
        }
      } else if (that.time != null) {
        if (this.timeRange != null) {
          timesMatch = (this.timeRange.contains(that.time));
        } else {
          // this.age == null && this.ageRange == null
          timesMatch = false;
        }
      } else if (this.timeRange != null) {
        // this.age == null && that.age == null
        if (that.timeRange != null) {
          timesMatch = this.timeRange.containsRange(that.timeRange);
        } else {
          timesMatch = false;
        }
      } else if (that.timeRange != null) {
        timesMatch = false;
      }

      return agesMatch && timesMatch && this.attributes.equals(that.attributes);
    }

    /**
     * Overrides the toString method for LookupTableKey.
     */
    @Override
    public String toString() {
      String ending = "";
      if (this.age != null || this.ageRange != null) {
        ending = (this.age == null ? ageRange.toString() : this.age.toString());
      }
      if (this.time != null || this.timeRange != null) {
        ending = (this.time == null ? timeRange.toString() : this.time.toString());
      }
      return attributes.toString() + " : " + ending;
    }
  }


  /**
   * A ConditionalTransitionOption represents a single destination state, with a
   * given logical condition that must be true in order for the state to be
   * transitioned to.
   */
  public static final class ConditionalTransitionOption extends TransitionOption {
    private Logic condition;
  }

  /**
   * Conditional transitions will transition to one of several possible states
   * based on conditional logic. A conditional_transition consists of an array of
   * condition/transition pairs which are tested in the order they are defined.
   * The first condition that evaluates to true will result in a transition to its
   * corresponding transition state. The last element in the condition_transition
   * array may contain only a transition (with no condition) to indicate a
   * "fallback transition" when all other conditions are false. If none of the
   * conditions evaluated to true, and no fallback transition was specified, the
   * module will transition to the last transition defined.
   */
  public static class ConditionalTransition extends Transition {
    private List<ConditionalTransitionOption> transitions;

    public ConditionalTransition(List<ConditionalTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      for (ConditionalTransitionOption option : transitions) {
        if (option.condition == null || option.condition.test(person, time)) {
          return option.transition;
        }
      }
      // fallback, just return the last transition
      TransitionOption last = transitions.get(transitions.size() - 1);
      return last.transition;
    }
  }

  /**
   * A DistributedTransitionOption represents a transition option with any of: - a
   * single state to transition to, - a condition that must be true, or - a set of
   * distributions and transitions.
   */
  public static final class ComplexTransitionOption extends TransitionOption {
    private Logic condition;
    private List<DistributedTransitionOption> distributions;
  }

  /**
   * Complex transitions are a combination of direct, distributed, and conditional
   * transitions. A complex_transition consists of an array of
   * condition/transition pairs which are tested in the order they are defined.
   * The first condition that evaluates to true will result in a transition based
   * on its corresponding transition or distributions. If the module defines a
   * transition, it will transition directly to that named state. If the module
   * defines distributions, it will then transition to one of these according to
   * the same rules as the distributed_transition. See Distributed for more
   * detail. The last element in the complex_transition array may omit the
   * condition to indicate a fallback transition when all other conditions are
   * false. If none of the conditions evaluated to true, and no fallback
   * transition was specified, the module will transition to the last transition
   * defined.
   */
  public static class ComplexTransition extends Transition {
    private List<ComplexTransitionOption> transitions;

    public ComplexTransition(List<ComplexTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      for (ComplexTransitionOption option : transitions) {
        if (option.condition == null || option.condition.test(person, time)) {
          return follow(option, person);
        }
      }

      // fallback, just return the last transition
      ComplexTransitionOption last = transitions.get(transitions.size() - 1);
      return follow(last, person);
    }

    private String follow(ComplexTransitionOption option, Person person) {
      if (option.transition != null) {
        return option.transition;
      } else if (option.distributions != null) {
        return pickDistributedTransition(option.distributions, person);
      }
      throw new IllegalArgumentException(
          "Complex Transition must have either transition or distributions");
    }
  }

  private static String pickDistributedTransition(
      List<DistributedTransitionOption> transitions, Person person) {
    double p = person.rand();
    double high = 0.0;
    for (DistributedTransitionOption option : transitions) {
      processDistributedTransition(option);
      if (option.numericDistribution != null) {
        high += option.numericDistribution;
      } else {
        NamedDistribution nd = option.namedDistribution;
        double dist = nd.defaultDistribution;
        if (person.attributes.containsKey(nd.attribute)) {
          dist = (Double) person.attributes.get(nd.attribute);
        }
        high += dist;
      }
      if (p < high) {
        return option.transition;
      }
    }
    // fallback, just return the last transition
    TransitionOption last = transitions.get(transitions.size() - 1);
    return last.transition;
  }

  private static void processDistributedTransition(DistributedTransitionOption option) {
    if (option.numericDistribution != null || option.namedDistribution != null) {
      return;
    }

    if (option.distribution instanceof Double) {
      option.numericDistribution = (Double) option.distribution;
    } else {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) option.distribution;
      option.namedDistribution = new NamedDistribution(map);
    }
  }

  /**
   * Helper class for distributions, which may either be a double, or a
   * NamedDistribution with an attribute to fetch the desired probability from and
   * a default.
   */
  public static class NamedDistribution implements Serializable {
    public String attribute;
    public double defaultDistribution;

    public NamedDistribution(JsonObject definition) {
      this.attribute = definition.get("attribute").getAsString();
      this.defaultDistribution = definition.get("default").getAsDouble();
    }

    public NamedDistribution(Map<String, ?> definition) {
      this.attribute = (String) definition.get("attribute");
      this.defaultDistribution = (Double) definition.get("default");
    }
  }
}