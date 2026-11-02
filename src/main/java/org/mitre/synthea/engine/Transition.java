package org.mitre.synthea.engine;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.Range;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.mitre.synthea.engine.lookup.LookupTable;
import org.mitre.synthea.engine.lookup.LookupTableIndex;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.TelemedicineConfig;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

/**
 * Transition represents all the transition types within the generic module
 * framework. This class is stateless, and calling 'follow' on an instance must
 * not modify state as instances of Transition within States and Modules are
 * shared across the population.
 */
public abstract class Transition implements Serializable {

  /** Remarks associated with the transition. */
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
    /** The name of the state to transition to. */
    private String transition;

    /**
     * Represents a transition with a direct state name.
     * @param transition The name of the state to transition to.
     */
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
    /** The name of the destination state */
    protected String transition;
  }

  /**
   * A DistributedTransitionOption represents a single destination state, with a
   * given distribution percentage, or a named distribution representing an
   * attribute containing the distribution percentage.
   */
  public static final class DistributedTransitionOption extends TransitionOption {
    /**
     * The distribution for this transition. This may be a double, or a
     * NamedDistribution.
    */
    private Object distribution;
    /** The numeric distribution value, if distribution is a double. */
    private Double numericDistribution;
    /** The named distribution, if distribution is named */
    private NamedDistribution namedDistribution;

    /**
     * Represents a distributed transition option with a state and probability.
     */
    public DistributedTransitionOption() {
    }

    /**
     * Represents a distributed transition option with a state and probability.
     *
     * @param transition The name of the state to transition to.
     * @param probability The probability of transitioning to the state.
     */
    public DistributedTransitionOption(String transition, double probability) {
      this.transition = transition;
      this.numericDistribution = probability;
    }
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
  public static class DistributedTransition extends Transition {
    /**
     * List of transitions to choose from, each with a distribution.
     * The distribution values should sum up to 1.0.
     */
    private List<DistributedTransitionOption> transitions;

    /**
     * Represents a distributed transition with multiple options.
     *
     * @param transitions The list of transition options.
     */
    public DistributedTransition(List<DistributedTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      return pickDistributedTransition(transitions, person);
    }
  }

  /**
   * A transition that is based on the type of care that will follow. Transitions may be made to
   * three states, "ambulatory", "emergency" and "emergency". The probability that a person will
   * follow a particular transition path is based on the time in the simulation (telemedicine is
   * more likely during and after the COVID-19 pandemic) and the type of insurance the person has.
   */
  public static class TypeOfCareTransition extends Transition {
    /** Ambulatory care */
    private String ambulatory;
    /** Telemedicine care */
    private String telemedicine;
    /** Emergency care */
    private String emergency;
    /** The telemedicine configuration */
    private TelemedicineConfig config;

    /**
     * Creates a new telemedicine config essentially from the JSON in GMF. This also reads in the
     * telemedicine_config.json files which contains the different distributions to be used for
     * transitions.
     * @param options The states to transition to
     */
    public TypeOfCareTransition(TypeOfCareTransitionOptions options) {
      this.ambulatory = options.ambulatory;
      this.emergency = options.emergency;
      this.telemedicine = options.telemedicine;
      this.config = TelemedicineConfig.fromJSON();
    }

    @Override
    public String follow(Person person, long time) {
      String selectedTransition;
      InsurancePlan current = person.coverage.getPlanAtTime(time);
      String insuranceName;
      if (current != null) {
        insuranceName = current.getPayer().getName();
      } else {
        insuranceName = PayerManager.NO_INSURANCE;
      }
      if (time < config.getTelemedicineStartTime()) {
        if (config.getHighEmergencyUseInsuranceNames().contains(insuranceName)) {
          EnumeratedDistribution<String> preHigh = config.getPreTelemedHighEmergency();
          synchronized (preHigh) {
            preHigh.reseedRandomGenerator(person.randLong());
            selectedTransition = preHigh.sample();
          }
        } else {
          EnumeratedDistribution<String> preTypical = config.getPreTelemedTypicalEmergency();
          synchronized (preTypical) {
            preTypical.reseedRandomGenerator(person.randLong());
            selectedTransition = preTypical.sample();
          }
        }
      } else {
        if (config.getHighEmergencyUseInsuranceNames().contains(insuranceName)) {
          EnumeratedDistribution<String> high = config.getTelemedHighEmergency();
          synchronized (high) {
            high.reseedRandomGenerator(person.randLong());
            selectedTransition = high.sample();
          }
        } else {
          EnumeratedDistribution<String> typical = config.getTelemedTypicalEmergency();
          synchronized (typical) {
            typical.reseedRandomGenerator(person.randLong());
            selectedTransition = typical.sample();
          }
        }
      }
      switch (selectedTransition) {
        case TelemedicineConfig.AMBULATORY:
          return this.ambulatory;
        case TelemedicineConfig.EMERGENCY:
          return this.emergency;
        case TelemedicineConfig.TELEMEDICINE:
          return this.telemedicine;
        default:
          throw new IllegalStateException("Selected transition is not ambulatory, emergency or"
                  + "telemedicine.");
      }
    }
  }

  /**
   * Represents options for transitioning based on the type of care.
   */
  public static final class TypeOfCareTransitionOptions implements Serializable {
    /** Ambulatory care */
    private String ambulatory;
    /** Telemedicine care */
    private String telemedicine;
    /** Emergency care */
    private String emergency;
  }

  /**
   * A LookupTableTransitionOption represents a destination state which will be
   * compared with a table lookup to find its probability and attributes.
   */
  public static final class LookupTableTransitionOption extends TransitionOption {
    /** The name of the lookup table to use for this transition */
    private String lookupTableName;
    /** The default probability for this transition if no attributes match */
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

    private final List<DistributedTransitionOption> defaultTransitions;
    private final LookupTable lookupTable;

    /**
     * Constructor for LookupTableTransition.
     * @param lookupTableTransitions transitions parsed from JSON
     */
    public LookupTableTransition(List<LookupTableTransitionOption> lookupTableTransitions) {
      if (lookupTableTransitions.isEmpty()) {
        throw new IllegalArgumentException("LOOKUP TABLE JSON ERROR: No transitions.");
      }
      String name = lookupTableTransitions.get(0).lookupTableName;
      if (name == null) {
        throw new IllegalArgumentException("LOOKUP TABLE JSON ERROR: Table name cannot be null.");
      }
      defaultTransitions = new ArrayList<>();
      List<String> states = new ArrayList<>();
      for (LookupTableTransitionOption option : lookupTableTransitions) {
        states.add(option.transition);
        defaultTransitions.add(new DistributedTransitionOption(option.transition,
            option.defaultProbability));
      }
      String configured = Config.get("generate.lookup_tables.implementation", "quadtree");
      LookupTableIndex.Implementation implementation;
      try {
        implementation = LookupTableIndex.Implementation.valueOf(
            configured.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown lookup table implementation '" + configured
            + "'. Expected scan, age_buckets or quadtree.", e);
      }
      String directory = Config.get("generate.lookup_tables");
      for (LookupTableIndex.Implementation candidate : LookupTableIndex.Implementation.values()) {
        if (candidate.name().equalsIgnoreCase(directory)) {
          throw new IllegalArgumentException("generate.lookup_tables specifies the lookup data "
              + "directory. To select a matching implementation, use "
              + "--generate.lookup_tables.implementation=" + directory.toLowerCase(Locale.ROOT));
        }
      }
      lookupTable = LookupTable.load(directory + name, states, implementation);
    }

    @Override
    public String follow(Person person, long time) {
      List<DistributedTransitionOption> distribution = lookupTable.findDistribution(person, time);
      return pickDistributedTransition(distribution.isEmpty() ? defaultTransitions : distribution,
          person);
    }
  }

  /**
   * Represents a key for lookup tables, containing attributes, age, and time information.
   */
  public final class LookupTableKey implements Serializable {
    /** Attributes for this key. */
    private final List<String> attributes;
    /** Age for this patient. May be null if lookup table does not use age. */
    private final Integer age;
    /** Age range for this row. Null if this is a person. */
    private final Range<Integer> ageRange;
    /** Time in the simulation */
    private final Long time;
    /** Time range for this row. */
    private final Range<Long> timeRange;

    /**
     * Create a symbolic lookup key for a given row that contains actual patient values.
     *
     * @param attributes Patient attribute values.
     * @param age Patient age.
     * @param time Patient time.
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
     *
     * @param attributes Table attribute values.
     * @param range If the table contains an age column, range contains the age range
     *     information for this key.
     * @param timeRange If the table contains a time column, timeRange contains the time range
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
    /**
     * Conditional logic gating transition.
     */
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
    /**
     * List of transitions to choose from, each with a condition.
     * The last transition may not have a condition, and will be used as a fallback.
     */
    private List<ConditionalTransitionOption> transitions;

    /**
     * Represents a conditional transition with a list of options.
     * @param transitions List of transitions to choose from, each with a condition.
     */
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
    /** Conditional logic gating transition */
    private Logic condition;
    /** List of transition options weighted by a percentage chance */
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
    /**
     * List of transitions to choose from, each with a condition or
     * distributions.
     */
    private List<ComplexTransitionOption> transitions;

    /**
     * Represents a complex transition with a list of options.
     * @param transitions List of transitions to choose from, each with a condition or
     *        distributions.
     */
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
    /** The attribute to fetch the distribution from, if applicable. */
    public String attribute;
    /** The default distribution value to use if the attribute is not present. */
    public double defaultDistribution;

    /**
     * Constructor for NamedDistribution.
     * @param definition the JSON definition of the named distribution
     */
    public NamedDistribution(JsonObject definition) {
      this.attribute = definition.get("attribute").getAsString();
      this.defaultDistribution = definition.get("default").getAsDouble();
    }

    /**
     * Constructor for NamedDistribution.
     * @param definition the map definition of the named distribution
     */
    public NamedDistribution(Map<String, ?> definition) {
      this.attribute = (String) definition.get("attribute");
      this.defaultDistribution = (Double) definition.get("default");
    }
  }
}