package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
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
   * @param payers The list of eligible payers.
   * @param person The patient who requires the service.
   * @param service The service required.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  @Override
  public InsurancePlan find(List<Payer> payers, Person person, EncounterType service, long time) {
    List<InsurancePlan> eligiblePlans = new ArrayList<InsurancePlan>();

    for (Payer payer : payers) {
      for (InsurancePlan plan : payer.getPlans()) {
        if (IPlanFinder.meetsAffordabilityRequirements(plan, person, service, time)) {
          eligiblePlans.add(plan);
        }
      }
    }
    // Choose a random payer from the list of options.
    return chooseRandomPlan(eligiblePlans, person);
  }
}