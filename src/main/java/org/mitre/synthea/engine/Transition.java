package org.mitre.synthea.engine;

import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;

/**
 * Transition represents all the transition types within the generic module framework. This class is
 * stateless, and calling 'follow' on an instance must not modify state as instances of Transition
 * within States and Modules are shared across the population.
 */
public abstract class Transition implements Validation {

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
  
  public abstract Set<String> getAllTransitions();
  
  
  public static class DirectTransition extends Transition {
    private String transition;
    
    public DirectTransition(String transition) {
      this.transition = transition;
    }

    @Override
    public Set<String> getAllTransitions() {
      return Collections.singleton(transition);
    }

    @Override
    public String follow(Person person, long time) {
      return transition;
    }
  }
  
  private abstract static class TransitionOption implements Validation {
    protected String transition;
  }
  
  public static final class DistributedTransitionOption extends TransitionOption {
    private Object distribution;
    private Double numericDistribution;
    private NamedDistribution namedDistribution;
  }
  
  public static final class DistributedTransition extends Transition {
    private List<DistributedTransitionOption> transitions;
    
    public DistributedTransition(List<DistributedTransitionOption> transitions) {
      this.transitions = transitions;
    }
    
    @Override
    public Set<String> getAllTransitions() {
      return transitions.stream().map(dto -> dto.transition).collect(Collectors.toSet());
    }

    @Override
    public String follow(Person person, long time) {
      return pickDistributedTransition(transitions, person);
    }
  }
  
  public static final class ConditionalTransitionOption extends TransitionOption {
    private Logic condition;
  }
  
  public static class ConditionalTransition extends Transition {
    private List<ConditionalTransitionOption> transitions;
    
    public ConditionalTransition(List<ConditionalTransitionOption> transitions) {
      this.transitions = transitions;
    }
    
    @Override
    public Set<String> getAllTransitions() {
      return transitions.stream().map(cto -> cto.transition).collect(Collectors.toSet());
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
  
  public static final class ComplexTransitionOption extends TransitionOption {
    private Logic condition;
    private List<DistributedTransitionOption> distributions;
    
  }
  
  public static class ComplexTransition extends Transition {
    private List<ComplexTransitionOption> transitions;
    
    public ComplexTransition(List<ComplexTransitionOption> transitions) {
      this.transitions = transitions;
    }
    
    @Override
    public Set<String> getAllTransitions() {
      Set<String> allTransitions = new HashSet<String>();
      
      for (ComplexTransitionOption cto : transitions) {
        if (cto.transition != null) {
          allTransitions.add(cto.transition);
        } else if (cto.distributions != null) {
          
          Set<String> subDists = cto.distributions
              .stream()
              .map(dto -> dto.transition)
              .collect(Collectors.toSet());
          allTransitions.addAll(subDists);
        }
      }
      
      return allTransitions;
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
      this.defaultDistribution = (double) definition.get("default");
    }
  }
}
