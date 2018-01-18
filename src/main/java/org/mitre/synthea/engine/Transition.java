package org.mitre.synthea.engine;

import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;

import java.util.List;

import org.mitre.synthea.world.agents.Person;

/**
 * Transition represents all the transition types within the generic module framework. This class is
 * stateless, and calling 'follow' on an instance must not modify state as instances of Transition
 * within States and Modules are shared across the population.
 */
public abstract class Transition {

  protected List<String> remarks;
  
  /**
   * Get the name of the next state.
   * 
   * @param person
   *          : person being processed
   * @param time
   *          : time of this transition
   * @return name : name of the next state
   */
  public abstract String follow(Person person, long time);
 
  /**
   * Direct transitions are the simplest of transitions. They transition directly to the indicated
   * state. The value of a direct_transition is simply the name of the state to transition to.
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
   * A TransitionOption represents a single destination state that may be transitioned to.
   */
  private abstract static class TransitionOption {
    protected String transition;
  }
  
  /**
   * A DistributedTransitionOption represents a single destination state, with a given distribution
   * percentage, or a named distribution representing an attribute containing the distribution
   * percentage.
   */
  public static final class DistributedTransitionOption extends TransitionOption {
    private Object distribution;
    private Double numericDistribution;
    private NamedDistribution namedDistribution;
  }
  
  /**
   * Distributed transitions will transition to one of several possible states based on the
   * configured distribution. Distribution values are from 0.0 to 1.0, such that a value of 0.55
   * would indicate a 55% chance of transitioning to the corresponding state. A
   * distributed_transition consists of an array of distribution/transition pairs for which the
   * distribution values should sum up to 1.0.
   * If the distribution values do not sum up to 1.0, the remaining distribution will transition to
   * the last defined transition state. For example, given distributions of 0.3 and 0.6, the
   * effective distribution of the last transition will actually be 0.7.
   * If the distribution values sum up to more than 1.0, the remaining distributions are ignored
   * (for example, if distribution values are 0.75, 0.5, and 0.3, then the second transition will
   * have an effective distribution of 0.25, and the last transition will have an effective
   * distribution of 0.0).
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
   * A ConditionalTransitionOption represents a single destination state, with a given logical
   * condition that must be true in order for the state to be transitioned to.
   */
  public static final class ConditionalTransitionOption extends TransitionOption {
    private Logic condition;
  }
  
  /**
   * Conditional transitions will transition to one of several possible states based on conditional
   * logic. A conditional_transition consists of an array of condition/transition pairs which are
   * tested in the order they are defined. The first condition that evaluates to true will result in
   * a transition to its corresponding transition state. The last element in the
   * condition_transition array may contain only a transition (with no condition) to indicate a
   * "fallback transition" when all other conditions are false.
   * If none of the conditions evaluated to true, and no fallback transition was specified, the
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
        if (option.condition == null 
            || option.condition.test(person, time)) {
          return option.transition;
        }
      }
      
      // fallback, just return the last transition
      TransitionOption last = transitions.get(transitions.size() - 1);
      return last.transition;
    }
    
  }
  
  /**
   * A DistributedTransitionOption represents a transition option with any of:
   * - a single state to transition to, 
   * - a condition that must be true, or
   * - a set of distributions and transitions.
   */
  public static final class ComplexTransitionOption extends TransitionOption {
    private Logic condition;
    private List<DistributedTransitionOption> distributions;
    
  }
  
  /**
   * Complex transitions are a combination of direct, distributed, and conditional transitions. A
   * complex_transition consists of an array of condition/transition pairs which are tested in the
   * order they are defined. The first condition that evaluates to true will result in a transition
   * based on its corresponding transition or distributions. If the module defines a transition, it
   * will transition directly to that named state. If the module defines distributions, it will then
   * transition to one of these according to the same rules as the distributed_transition. See
   * Distributed for more detail. The last element in the complex_transition array may omit the
   * condition to indicate a fallback transition when all other conditions are false.
   * If none of the conditions evaluated to true, and no fallback transition was specified, the
   * module will transition to the last transition defined.
   */
  public static class ComplexTransition extends Transition {
    private List<ComplexTransitionOption> transitions;
    
    public ComplexTransition(List<ComplexTransitionOption> transitions) {
      this.transitions = transitions;
    }

    @Override
    public String follow(Person person, long time) {
      for (ComplexTransitionOption option : transitions) {
        if (option.condition == null 
            || option.condition.test(person, time)) {
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
      option.numericDistribution = (Double)option.distribution;
    } else {
      @SuppressWarnings("unchecked")
      LinkedTreeMap<String,Object> map = (LinkedTreeMap<String,Object>)option.distribution;
      option.namedDistribution = new NamedDistribution(map);
    }
  }
  
  /**
   * Helper class for distributions, which may either be a double, or a NamedDistribution with an
   * attribute to fetch the desired probability from and a default.
   */
  public static class NamedDistribution {
    public String attribute;
    public double defaultDistribution;

    public NamedDistribution(JsonObject definition) {
      this.attribute = definition.get("attribute").getAsString();
      this.defaultDistribution = definition.get("default").getAsDouble();
    }
    
    public NamedDistribution(LinkedTreeMap<String,?> definition) {
      this.attribute = (String) definition.get("attribute");
      this.defaultDistribution = (Double) definition.get("default");
    }
  }
}
