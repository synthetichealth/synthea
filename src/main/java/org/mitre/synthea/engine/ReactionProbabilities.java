package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * ReactionProbabilities is a structure to capture the kind and severity of reaction for a
 * given AllergyIntolerance.
 */
public class ReactionProbabilities implements Serializable {
  /** Severe allergic reaction */
  private static final String SEVERE = "severe";
  /** Moderate allergic reaction */
  private static final String MODERATE = "moderate";
  /** Mild allergic reaction */
  private static final String MILD = "mild";
  /** Used in the case that the reaction does not happen for the individual. */
  private static final String NONE = "none";

  /** Code representing the type of reaction. */
  private HealthRecord.Code reaction;
  /** List of possible severities and their probabilities for the reaction. */
  private List<SeverityProbability> possibleSeverities;
  /** Distribution used to sample reaction severities based on probabilities. */
  private EnumeratedDistribution<String> severityDistribution;

  /**
   * Gets the reaction code.
   * @return The reaction code.
   */
  public HealthRecord.Code getReaction() {
    return reaction;
  }

  /**
   * Sets the reaction code.
   * @param reaction The reaction code.
   */
  public void setReaction(HealthRecord.Code reaction) {
    this.reaction = reaction;
  }

  /**
   * Constructs a ReactionProbabilities object with a reaction code and possible severities.
   *
   * @param code The reaction code.
   * @param possibleReactions The list of possible severities and their probabilities.
   */
  public ReactionProbabilities(HealthRecord.Code code,
                               List<SeverityProbability> possibleReactions) {
    this.reaction = code;
    this.possibleSeverities = possibleReactions;
  }

  /**
   * Checks if the ReactionProbabilities object has any possible severities defined.
   *
   * @return True if there are possible severities, false otherwise.
   */
  public boolean isPopulated() {
    return !this.possibleSeverities.isEmpty();
  }

  /**
   * Represents the severity level and its associated probability for a reaction.
   */
  public static class SeverityProbability implements Serializable {
    /** Severity level of the reaction (e.g., mild, moderate, severe). */
    private String level;
    /** Probability of the severity level occurring. */
    private float value;

    /**
     * Constructor for SeverityProbability.
     * @param level The severity level.
     * @param value The probability of the severity level.
     */
    public SeverityProbability(String level, float value) {
      this.level = level;
      this.value = value;
    }

    /**
     * Gets the severity level.
     * @return The severity level.
     */
    public String getLevel() {
      return level;
    }

    /**
     * Sets the severity level.
     * @param level The severity level.
     */
    public void setLevel(String level) {
      this.level = level;
    }

    /**
     * Gets the probability of the severity level.
     * @return The probability value.
     */
    public float getValue() {
      return value;
    }

    /**
     * Sets the probability of the severity level.
     * @param value The probability value.
     */
    public void setValue(float value) {
      this.value = value;
    }
  }

  /**
   * This will validate the reaction probabilities and generate the underlying objects used
   * to get weighted samples of reaction severities.
   */
  public void buildReactionDistributions() {
    if (!this.validate()) {
      throw new IllegalStateException("Invalid distribution values specified");
    }
    List<Pair<String, Double>> probPairs = this.possibleSeverities.stream()
          .map(sp -> new Pair<>(sp.getLevel(), (double) sp.getValue()))
          .collect(Collectors.toList());
    this.severityDistribution = new EnumeratedDistribution<String>(probPairs);
  }

  /**
   * Validates the reaction probabilities to ensure that the sum of all
   * probabilities is less than or equal to 1.0.
   * @return true if the reaction probabilities are valid, false otherwise
   */
  public boolean validate() {
    // less than 1.001 to deal with floating point rounding issues
    return possibleSeverities.stream().mapToDouble(sp -> sp.getValue()).sum() <= 1.001;
  }

  /**
   * Generate a severity for the reaction of an allergy or intolerance. This uses the passed in
   * person for the random seed. There is a possibility that the reaction will not be selected
   * for an individual. In that case, the method returns null.
   * @param person used for random seed
   * @return a ReactionSeverity value to indicate severity or null
   */
  public HealthRecord.ReactionSeverity generateSeverity(Person person) {
    if (this.isPopulated() && this.severityDistribution == null) {
      this.buildReactionDistributions();
    }
    this.severityDistribution.reseedRandomGenerator(person.randLong());
    String severity = this.severityDistribution.sample();
    switch (severity) {
      case SEVERE:
        return HealthRecord.ReactionSeverity.SEVERE;
      case MODERATE:
        return HealthRecord.ReactionSeverity.MODERATE;
      case MILD:
        return HealthRecord.ReactionSeverity.MILD;
      case NONE:
        // do nothing
        return null;
      default:
        // should never get here
    }
    //should never get here
    return null;
  }
}
