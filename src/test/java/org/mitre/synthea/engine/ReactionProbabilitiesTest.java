package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

public class ReactionProbabilitiesTest {
  private ReactionProbabilities reactionProbabilities;

  /**
   * Set up a test "cough" reaction.
   */
  @Before
  public void setUp() {
    HealthRecord.Code cough = new HealthRecord.Code("http://www.snomed.org/sct",
        "49727002", "Cough");
    ReactionProbabilities.SeverityProbability mild =
        new ReactionProbabilities.SeverityProbability("mild", 0.5f);
    ReactionProbabilities.SeverityProbability none =
        new ReactionProbabilities.SeverityProbability("none", 0.5f);
    List<ReactionProbabilities.SeverityProbability> probs = new ArrayList();
    probs.add(mild);
    probs.add(none);
    this.reactionProbabilities = new ReactionProbabilities(cough, probs);
  }

  @Test
  public void isPopulated() {
    assertTrue(this.reactionProbabilities.isPopulated());
    HealthRecord.Code cough = new HealthRecord.Code("http://www.snomed.org/sct",
        "49727002", "Cough");
    List<ReactionProbabilities.SeverityProbability> probs = new ArrayList();
    assertFalse(new ReactionProbabilities(cough, probs).isPopulated());
  }

  @Test
  public void validate() {
    assertTrue(this.reactionProbabilities.validate());
    HealthRecord.Code cough = new HealthRecord.Code("http://www.snomed.org/sct",
        "49727002", "Cough");
    ReactionProbabilities.SeverityProbability severe =
        new ReactionProbabilities.SeverityProbability("severe", 0.5f);
    ReactionProbabilities.SeverityProbability mild =
        new ReactionProbabilities.SeverityProbability("mild", 0.5f);
    ReactionProbabilities.SeverityProbability none =
        new ReactionProbabilities.SeverityProbability("none", 0.5f);
    List<ReactionProbabilities.SeverityProbability> probs = new ArrayList();
    probs.add(mild);
    probs.add(none);
    probs.add(severe);
    assertFalse(new ReactionProbabilities(cough, probs).validate());
  }

  @Test
  public void generateReactions() {
    Person p = new Person(0);
    HealthRecord.ReactionSeverity severity = this.reactionProbabilities.generateSeverity(p);
    assertTrue(severity == null || severity == HealthRecord.ReactionSeverity.MILD);
  }
}