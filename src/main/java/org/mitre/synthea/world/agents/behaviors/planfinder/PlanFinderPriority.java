package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

public class PlanFinderPriority implements IPlanFinder {

  @Override
  public InsurancePlan find(List<InsurancePlan> plans, Person person,
      EncounterType service, long time) {

    List<InsurancePlan> eligiblePlans = (plans.stream().filter(plan -> (plan.isGovernmentPlan()
        || (IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time)))
        && plan.accepts(person, time)).collect(Collectors.toList()));

    if (eligiblePlans.size() > 1) {
      // If there are more than 1 affordable/eligible plans, filter to the highest priority ones.
      int highestEligibltPriority = eligiblePlans.stream()
          .min(Comparator.comparing(plan -> plan.getPriority())).get().getPriority();
      eligiblePlans = eligiblePlans.stream().filter(
          (plan) -> plan.getPriority() == highestEligibltPriority).collect(Collectors.toList());
    }

    return chooseRandomPlan(eligiblePlans, person);
  }
}