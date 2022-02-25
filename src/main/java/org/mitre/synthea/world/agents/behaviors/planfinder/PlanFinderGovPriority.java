package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

public class PlanFinderGovPriority implements IPlanFinder {

  @Override
  public InsurancePlan find(List<Payer> payers, Person person, EncounterType service, long time) {
    List<InsurancePlan> eligiblePlans = new ArrayList<InsurancePlan>();

    for (Payer payer : payers) {
      for (InsurancePlan plan : payer.getPlans()) {
        if (plan.isGovernmentPlan() && plan.accepts(person, time)) {
          // Government plan selection does not consider affordability.
          eligiblePlans.add(plan);
        } else if (IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time)) {
          // Private plans require that a person meets basic afforrdabilty/occupation requirements.
          eligiblePlans.add(plan);
        }
      }
    }

    if (eligiblePlans.stream().anyMatch(plan
        -> plan.getPayer().getName().equals(PayerManager.DUAL_ELIGIBLE))) {
      // Dual Eligble plans take priority.
      eligiblePlans = eligiblePlans.stream().filter(plan -> plan.getPayer().getName()
          .equals(PayerManager.DUAL_ELIGIBLE)).collect(Collectors.toList());
    } else if (eligiblePlans.stream().anyMatch(plan -> plan.isGovernmentPlan())) {
      // Government plans (that are not dual eligble) take secondary priority.
      eligiblePlans = eligiblePlans.stream().filter(plan -> plan.isGovernmentPlan())
          .collect(Collectors.toList());
    }
    return chooseRandomPlan(eligiblePlans, person);
  }
}