package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

/**
 * Find a particular provider by service.
 */
public class PlanFinderRandom implements IPlanFinder {
  /**
   * Find a provider with a specific service for the person.
   *
   * @param plans The list of eligible plans.
   * @param person The patient who requires the service.
   * @param service The service required.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return A plan or null if none is available.
   */
  @Override
  public InsurancePlan find(List<InsurancePlan> plans,
      Person person, EncounterType service, long time) {
    List<InsurancePlan> eligiblePlans = new ArrayList<InsurancePlan>();

    for (InsurancePlan plan : plans) {
      if ((plan.isGovernmentPlan()
          || IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time))
          && plan.accepts(person, time)) {
        eligiblePlans.add(plan);
      }
    }
    // Choose a random payer from the list of options.
    return chooseRandomPlan(eligiblePlans, person);
  }
}