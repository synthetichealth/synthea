package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

public class IncomeThresholdEligibility implements IPlanEligibility {

  private final double incomeThreshold;

  public IncomeThresholdEligibility(double incomeThreshold) {
    this.incomeThreshold = incomeThreshold;
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    int income = (int) person.attributes.get(Person.INCOME);
    return income <= incomeThreshold;
  }

}
