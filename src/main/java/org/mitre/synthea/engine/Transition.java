package org.mitre.synthea.engine;

import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
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
public abstract class Transition {

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
  private abstract static class TransitionOption {
    protected String transition;
  }

  /**
   * A DistributedTransitionOption represents a single destination state, with a
   * given distribution percentage, or a named distribution representing an
   * attribute containing the distribution percentage.
   */
  public static final class DistributedTransitionOption extends TransitionOption {
    private Object distribution;
    public Double numericDistribution;
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
    public String lookupTableName;
    public double defaultProbability;
  }

  /**
   * LookupTable transitions will transition to one of several possible states
   * based on probabilities for each state extacted from a table. A
   * LookUpTableTransition will have one field denoting the lookuptable to use. A
   * table may have any set of attributes to determine the probabilities of any
   * set of states to transition to based on a variety of attributes which will be
   * compared to the attributes of the current person. If a person does not
   * correspond to any of the sets of attributes, the state will transition to
   * "Terminal"
   */
  public static class LookupTableTransition extends Transition {

    // Map of lookupTables
    private static HashMap<String, HashMap<LookupTableKey, ArrayList<DistributedTransitionOption>>>
        lookupTables = new HashMap<String, HashMap<LookupTableKey,
        ArrayList<DistributedTransitionOption>>>();
    private List<LookupTableTransitionOption> transitions;
    private ArrayList<String> attributes;
    private ArrayList<String> stateNames;
    private ArrayList<DistributedTransitionOption> defaultTransitions;
    private String lookupTableName;

    /**
     * Constructor for LookupTableTransition.
     * @param lookupTableTransitions transitions parsed from JSON
     */
    public LookupTableTransition(List<LookupTableTransitionOption> lookupTableTransitions) {

      this.transitions = lookupTableTransitions;
      this.attributes = new ArrayList<String>();
      // Create Default Transition
      this.defaultTransitions = new ArrayList<DistributedTransitionOption>();
      for (LookupTableTransitionOption transitionOption : this.transitions) {
        DistributedTransitionOption distributedTransitionOption = new DistributedTransitionOption();
        distributedTransitionOption.transition = transitionOption.transition;
        distributedTransitionOption.numericDistribution = transitionOption.defaultProbability;
        defaultTransitions.add(distributedTransitionOption);
      }

      this.lookupTableName = lookupTableTransitions.get(0).lookupTableName;
      if (lookupTableName == null) {
        throw new RuntimeException("LOOKUP TABLE JSON ERROR: Table name cannot be null.");
      }
      if (!lookupTables.containsKey(lookupTableName)) {
        System.out.println("Loading Lookup Table: " + lookupTableName);
        // Hashmap for the new table
        HashMap<LookupTableKey, ArrayList<DistributedTransitionOption>> newTable
            = new HashMap<LookupTableKey, ArrayList<DistributedTransitionOption>>();
        // Load in the respective CSV file
        String fileName = null;
        fileName = Config.get("generate.lookuptables") + "/" + lookupTableName;
        String csv;
        List<? extends Map<String, String>> lookupTable = null;
        try {
          csv = Utilities.readResource(fileName);
          if (csv.startsWith("\uFEFF")) {
            csv = csv.substring(1); // Removes BOM
          }
          lookupTable = SimpleCSV.parse(csv);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        // Parse List of Attributes
        this.attributes = new ArrayList<String>(lookupTable.get(0).keySet());
        this.attributes.subList((this.attributes.size() - lookupTableTransitions.size()),
            this.attributes.size()).clear();
        // Parse list of State Transitions
        this.stateNames = new ArrayList<String>(lookupTable.get(0).keySet());
        this.stateNames.subList(0, this.attributes.size()).clear();
        // Insert each row of CSV into Hashmap
        for (Map<String, String> currentRow : lookupTable) {
          ArrayList<String> currentAttributes = new ArrayList<String>(currentRow.values());
          currentAttributes.subList(this.attributes.size(), currentAttributes.size()).clear();
          LookupTableKey attributeRecordsLookupKey = new LookupTableKey(currentAttributes,
              this.attributes.indexOf("age"), -1);
          ArrayList<DistributedTransitionOption> transitionProbabilities
              = new ArrayList<DistributedTransitionOption>();
          transitionProbabilities = createDistributedTransitionOptions(currentRow);
          newTable.put(attributeRecordsLookupKey, transitionProbabilities);
        }
        // Put new table into Hasmap of all tables
        lookupTables.put(lookupTableName, newTable);
      }
    }

    // Creates Distributed Transition Options based on CSV probabilities and JSON
    // States
    private ArrayList<DistributedTransitionOption>
        createDistributedTransitionOptions(Map<String, String> currentRow) {

      ArrayList<DistributedTransitionOption> transitionProbabilities
          = new ArrayList<DistributedTransitionOption>();
      for (String transitionName : this.stateNames) {
        if (currentRow.containsKey(transitionName) && checkTransitionMatch(transitionName)) {
          DistributedTransitionOption currentOption = new DistributedTransitionOption();
          currentOption.numericDistribution = Double.parseDouble(currentRow.get(transitionName));
          currentOption.transition = transitionName;
          transitionProbabilities.add(currentOption);
        } else {
          throw new RuntimeException("LOOKUP TABLE CSV/JSON ERROR: CSV column state name '"
              + transitionName + "' does not match a JSON state to transition to in CSV table '"
              + lookupTableName + "'");
        }
      }
      return transitionProbabilities;
    }

    private boolean checkTransitionMatch(String transitionName) {
      for (LookupTableTransitionOption option : this.transitions) {
        if (option.transition.equals(transitionName)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public String follow(Person person, long time) {

      // Extract Person's list of relevant attributes
      String personAge = "-1";
      ArrayList<String> personsAttributes = new ArrayList<String>();
      for (String attributeToAdd : this.attributes) {
        attributeToAdd.toLowerCase();
        if (attributeToAdd.equals("age")) {
          personAge = Integer.toString(person.ageInYears(time));
        } else {
          String currentAttributeToCheck = (String) person.attributes.get(attributeToAdd);
          if (currentAttributeToCheck == null) {
            throw new RuntimeException("LOOKUP TABLE CSV ATTRIBUTE ERROR: Attribute '"
                + attributeToAdd + "' in table '" + this.lookupTableName
                + "' does not exist as one of this person's attributes.");
          }
          personsAttributes.add(currentAttributeToCheck);
        }
      }
      // Create Key to get distributions
      LookupTableKey personsAttributesLookupKey = new LookupTableKey(
          personsAttributes, this.attributes.indexOf("age"), Integer.parseInt(personAge));
      if (lookupTables.get(lookupTableName).containsKey(personsAttributesLookupKey)) {
        // Person matches, use their attribute's list of distributedtransitionoptions
        return pickDistributedTransition(
          lookupTables.get(lookupTableName).get(personsAttributesLookupKey), person);
      } else {
        // No attribute match, use default value
        return pickDistributedTransition(this.defaultTransitions, person);
      }
    }

    private final class LookupTableKey {

      private final ArrayList<String> recordAttributes;
      private final int ageIndex;
      private final int personAge;
      private final Range<Integer> ageRange;

      LookupTableKey(ArrayList<String> currentAttributes, int ageIndex, int personAge) {
        this.personAge = personAge;
        this.recordAttributes = currentAttributes;
        this.ageIndex = ageIndex;
        if (ageIndex > -1 && personAge < 0) {
          String ageRange = currentAttributes.get(ageIndex);
          if (ageRange.indexOf("-") == -1 || ageRange.substring(0,
              ageRange.indexOf("-")).length() < 1
              || ageRange.substring(ageRange.indexOf("-") + 1).length() < 1) {
            throw new RuntimeException(
                "LOOKUP TABLE CSV AGE ERROR: Age Range '" + ageRange
                + "' must be in the form: 'ageLow-ageHigh'");
          }
          this.ageRange = Range.between(
              Integer.parseInt(ageRange.substring(0, ageRange.indexOf("-"))),
              Integer.parseInt(ageRange.substring(ageRange.indexOf("-") + 1)));
          currentAttributes.remove(ageIndex);
        } else {
          this.ageRange = Range.between(-1, -1);
        }
      }

      @Override
      public int hashCode() {
        return this.recordAttributes.hashCode();
      }

      @Override
      public boolean equals(Object obj) {

        if (obj == null || this.getClass() != obj.getClass()) {
          return false;
        }
        LookupTableKey lookupTableKey = (LookupTableKey) obj;
        ArrayList<String> personAttributes = lookupTableKey.recordAttributes;
        // If There is an age column (at ageIndex)
        if (this.ageIndex > -1) {
          // If this is a person
          if (personAge > -1) {
            return lookupTableKey.ageRange.contains(personAge);
          } else {
            return attributes.equals(personAttributes);
          }
        } else {
          // No age column. Return standard ArrayList.equals();
          return this.recordAttributes.equals(personAttributes);
        }
      }
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
      LinkedTreeMap<String, Object> map = (LinkedTreeMap<String, Object>) option.distribution;
      option.namedDistribution = new NamedDistribution(map);
    }
  }

  /**
   * Helper class for distributions, which may either be a double, or a
   * NamedDistribution with an attribute to fetch the desired probability from and
   * a default.
   */
  public static class NamedDistribution {
    public String attribute;
    public double defaultDistribution;

    public NamedDistribution(JsonObject definition) {
      this.attribute = definition.get("attribute").getAsString();
      this.defaultDistribution = definition.get("default").getAsDouble();
    }

    public NamedDistribution(LinkedTreeMap<String, ?> definition) {
      this.attribute = (String) definition.get("attribute");
      this.defaultDistribution = (Double) definition.get("default");
    }
  }
}