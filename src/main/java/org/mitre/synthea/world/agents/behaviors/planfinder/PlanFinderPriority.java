package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.ArrayList;
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
    List<InsurancePlan> eligiblePlans = new ArrayList<InsurancePlan>();
    for (InsurancePlan plan : plans) {
      if ((plan.isGovernmentPlan() && plan.accepts(person, time))
          || (IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time))) {
        // Government plan selection does not consider affordability.
        // Private plans require that a person meets basic affordabilty/occupation requirements.
        eligiblePlans.add(plan);
      }
    }

    int maxEligiblePriority = Collections.max(eligiblePlans.stream().map(plan -> plan.getPayer().getPriority()).collect(Collectors.toList()));
    eligiblePlans = eligiblePlans.stream().filter(plan -> plan.getPayer().getPriority() == maxEligiblePriority).collect(Collectors.toList());
    
    return chooseRandomPlan(eligiblePlans, person);
  }
}