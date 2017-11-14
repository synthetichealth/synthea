package org.mitre.synthea.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Person;

/**
 * Transition represents all the transition types within the generic module framework. This class is
 * stateless, and calling 'follow' on an instance must not modify state as instances of Transition
 * within States and Modules are shared across the population.
 */
public class Transition {

  public enum TransitionType {
    DIRECT, DISTRIBUTED, CONDITIONAL, COMPLEX
  }

  public TransitionType type;
  public List<String> transitions;
  public List<Object> distributions;
  public List<JsonObject> conditions;
  public List<Transition> contained;

  public Transition(TransitionType type, JsonElement jsonElement) {
    // TODO - make Transitions OO like States.
    // don't forget about remarks.
    this.type = type;
    this.transitions = new ArrayList<String>();
    switch (type) {
      case DIRECT:
        transitions.add(jsonElement.getAsString());
        break;
      case DISTRIBUTED:
        distributions = new ArrayList<Object>();
        jsonElement.getAsJsonArray().forEach(item -> {
          JsonObject transition = item.getAsJsonObject();
          transitions.add(transition.get("transition").getAsString());

          JsonElement distribution = transition.get("distribution");
          if (distribution.isJsonPrimitive()) {
            distributions.add(distribution.getAsDouble());
          } else {
            distributions.add(new NamedDistribution(distribution.getAsJsonObject()));
          }
        });
        break;
      case CONDITIONAL:
        conditions = new ArrayList<JsonObject>();
        jsonElement.getAsJsonArray().forEach(item -> {
          JsonObject transition = item.getAsJsonObject();
          transitions.add(transition.get("transition").getAsString());
          if (transition.has("condition")) {
            conditions.add(transition.get("condition").getAsJsonObject());
          } else {
            conditions.add(null);
          }
        });
        break;
      case COMPLEX:
        conditions = new ArrayList<JsonObject>();
        contained = new ArrayList<Transition>();
        jsonElement.getAsJsonArray().forEach(item -> {
          JsonObject transition = item.getAsJsonObject();
          if (transition.has("transition")) {
            contained.add(new Transition(TransitionType.DIRECT, transition.get("transition")));
          } else if (transition.has("distributions")) {
            contained
                .add(new Transition(TransitionType.DISTRIBUTED, transition.get("distributions")));
          } else {
            System.err.format("Complex Transition malformed: %s\n", jsonElement.toString());
          }
          if (transition.has("condition")) {
            conditions.add(transition.get("condition").getAsJsonObject());
          }
        });
        break;
      default:
        // not possible
    }
  }

  /**
   * Get the name of the next state.
   * 
   * @param person
   *          : person being processed
   * @param time
   *          : time of this transition
   * @return name : name of the next state
   */
  public String follow(Person person, long time) {
    switch (type) {
      case DIRECT:
        return transitions.get(0);
      case DISTRIBUTED:
        double p = person.rand();
        double high = 0.0;
        for (int i = 0; i < distributions.size(); i++) {
          Object d = distributions.get(i);
          if (d instanceof Double) {
            high += (Double) d;
          } else {
            NamedDistribution nd = (NamedDistribution) d;
            double dist = nd.defaultDistribution;
            if (person.attributes.containsKey(nd.attribute)) {
              dist = (Double) person.attributes.get(nd.attribute);
            }

            high += dist;
          }

          if (p < high) {
            return transitions.get(i);
          }
        }
        return transitions.get(transitions.size() - 1);
      case CONDITIONAL:
        for (int i = 0; i < conditions.size(); i++) {
          JsonObject logicDefinition = conditions.get(i);
          if (logicDefinition == null) {
            return transitions.get(i);
          } else {
            Logic allow = Logic.build(logicDefinition);
            if (allow.test(person, time)) {
              return transitions.get(i);
            }
          }
        }
        return transitions.get(transitions.size() - 1);
      case COMPLEX:
        for (int i = 0; i < conditions.size(); i++) {
          JsonObject logicDefinition = conditions.get(i);
          if (logicDefinition == null) {
            return contained.get(i).follow(person, time);
          } else {
            Logic allow = Logic.build(logicDefinition);
            if (allow.test(person, time)) {
              return contained.get(i).follow(person, time);
            }
          }
        }
        return contained.get(contained.size() - 1).follow(person, time);
      default:
        return transitions.get(0);
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
  }
}
