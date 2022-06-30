package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

public class PlanFinderPriority implements IPlanFinder {

  @Override
  public InsurancePlan find(Set<InsurancePlan> plans, Person person,
      EncounterType service, long time) {

    // Government plan selection does not consider affordability.
    // Private plans require that a person meets basic affordabilty/occupation requirements.
    List<InsurancePlan> eligiblePlans = (plans.stream().filter(plan ->
        (plan.isGovernmentPlan() && plan.accepts(person, time))
        || (IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time)))
        .collect(Collectors.toList()));

    if (!eligiblePlans.isEmpty()) {
      // If there are affordable/eligible plans, choose the ones with the highest priority.
      Set<Integer> eligiblePriorities = eligiblePlans.stream().map(plan ->
          plan.getPayer().getPriority()).collect(Collectors.toSet());
      int maxEligiblePriority = Collections.min(eligiblePriorities);
      eligiblePlans = eligiblePlans.stream().filter(plan ->
          plan.getPayer().getPriority() == maxEligiblePriority).collect(Collectors.toList());
    }

    return chooseRandomPlan(eligiblePlans, person);
  }
}