package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

public class AgeThresholdEligibility implements IPlanEligibility {

  private final double ageThreshold;

  public AgeThresholdEligibility(double ageThreshold) {
    this.ageThreshold = ageThreshold;
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    int age = (int) person.ageInYears(time);
    return age >= ageThreshold;
  }

}
