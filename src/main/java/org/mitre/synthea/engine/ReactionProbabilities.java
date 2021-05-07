package org.mitre.synthea.engine;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ReactionProbabilities {
  private static final String SEVERE = "severe";
  private static final String MODERATE = "moderate";
  private static final String MILD = "mild";
  // Used in the case that the reaction does not happen for the individual
  private static final String NONE = "none";

  private class SeverityProbability {
    private String level;
    private float value;

    public SeverityProbability(String level, float value) {
      this.level = level;
      this.value = value;
    }

    public String getLevel() {
      return level;
    }

    public void setLevel(String level) {
      this.level = level;
    }

    public float getValue() {
      return value;
    }

    public void setValue(float value) {
      this.value = value;
    }
  }

  private HashMap<String, List<SeverityProbability>> possibleReactions;
  private HashMap<String, EnumeratedDistribution<String>> reactionSeverityDistributions;

  public ReactionProbabilities(HashMap<String, List<SeverityProbability>> possibleReactions) {
    this.possibleReactions = possibleReactions;
  }

  public boolean isPopulated() {
    return !this.possibleReactions.isEmpty();
  }

  public void buildReactionDistributions() {
    if (!this.validate()) {
      throw new IllegalStateException("Invalid distribution values specified");
    }
    this.reactionSeverityDistributions = new HashMap();
    this.possibleReactions.forEach((condition, spList) -> {
      List probPairs = spList.stream()
          .map(sp -> new Pair(sp.getLevel(), sp.getValue()))
          .collect(Collectors.toList());
      this.reactionSeverityDistributions.put(condition,
          new EnumeratedDistribution<String>(probPairs));
    });
  }

  public boolean validate() {
    return possibleReactions.values().stream().anyMatch(spList -> {
      return spList.stream()
          .mapToDouble(severityProbability -> severityProbability.getValue()).sum() > 1;
    });
  }

  public HashMap<String, HealthRecord.ReactionSeverity> generateReactions(Person person) {
    HashMap<String, HealthRecord.ReactionSeverity> reactions = new HashMap();
    this.reactionSeverityDistributions.forEach((condition, distribution) -> {
      distribution.reseedRandomGenerator(person.seed);
      String severity = distribution.sample();
      switch (severity) {
        case SEVERE:
          reactions.put(condition, HealthRecord.ReactionSeverity.SEVERE);
          break;
        case MODERATE:
          reactions.put(condition, HealthRecord.ReactionSeverity.MODERATE);
          break;
        case MILD:
          reactions.put(condition, HealthRecord.ReactionSeverity.MILD);
          break;
        case NONE:
          // do nothing
          break;
      }
    });
    return reactions;
  }
}
