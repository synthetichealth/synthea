package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Person;

public class PovertyMultiplierEligibility implements IPlanEligibility {

  private final double povertyMultiplier;

  public PovertyMultiplierEligibility(double povertyMultiplier) {
    this.povertyMultiplier = povertyMultiplier;
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    double povertyLevel = HealthInsuranceModule.povertyLevel;
    double incomeThreshold = povertyLevel * povertyMultiplier;
    int income = (int) person.attributes.get(Person.INCOME);
    return income <= incomeThreshold;
  }
}