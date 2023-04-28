package org.mitre.synthea.world.agents.behaviors.planfinder;

import java.util.List;

import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

/**
 * Find a particular provider by service.
 */
public interface IPlanFinder {

  /**
   * Find a payer that meets the person's and simulation's requirements.
   *
   * @param plans The list of plans (as determined by state).
   * @param person The patient who requires a payer.
   * @param service The service required.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  public InsurancePlan
      find(List<InsurancePlan> plans, Person person, EncounterType service, long time);

  /**
   * Determine whether or not the given payer meets the person's basic requirements.
   *
   * @param plan The plan to check.
   * @param person The patient who requires a payer.
   * @param service The service required.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return if the payer meets the basic requirements.
   */
  public static boolean meetsAffordabilityRequirements(
      InsurancePlan plan, Person person, EncounterType service, long time) {

    // Occupation determines whether their employer will pay for insurance after the mandate.
    double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);

    return (person.canAffordPlan(plan) || (time >= HealthInsuranceModule.mandateTime
        && occupation >= HealthInsuranceModule.mandateOccupation))
        && (plan.coversService(null)); // For a null service, Plan.coversService returns true.
  }

  /**
   * Choose a random payer from a list of payers.
   *
   * @param options the list of acceptable payer options that the person can recieve.
   * @return a random payer from the given list of options.
   */
  public default InsurancePlan chooseRandomPlan(List<InsurancePlan> options,
      RandomNumberGenerator rand) {
    if (options.isEmpty()) {
      return PayerManager.getNoInsurancePlan();
    }
    return options.get(rand.randInt(options.size()));
  }
}