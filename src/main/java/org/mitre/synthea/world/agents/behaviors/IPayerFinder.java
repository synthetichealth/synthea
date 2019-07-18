package org.mitre.synthea.world.agents.behaviors;

import java.util.List;
import java.util.Random;

import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * Find a particular provider by service.
 */
public interface IPayerFinder {

  /**
   * Find a payer that meets the person's and simulation's requirements.
   * 
   * @param payers The list of eligible payers (determined by state currently).
   * @param person The patient who requires a payer.
   * @param service The service required.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  public Payer find(List<Payer> payers, Person person, EncounterType service, long time);

  /**
   * Determine whether or not the given payer meets the person's basic requirements.
   * 
   * @param payer The payer to check.
   * @param person The patient who requires a payer.
   * @param service The service required.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return if the payer meets the basic requirements.
   */
  public default boolean meetsBasicRequirements(
      Payer payer, Person person, EncounterType service, long time) {

    // occupation determines whether their employer will pay for insurance after the mandate.
    double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);

    return payer.accepts(person, time)
        && (person.canAfford(payer) || (time >= HealthInsuranceModule.mandateTime
        && occupation >= HealthInsuranceModule.mandateOccupation))
        && payer.isInNetwork(null)
        && (payer.coversService(service)); // For a null service, Payer.coversService returns true.
  }

  /**
   * Choose a random payer from a list of payers.
   * 
   * @param options the list of acceptable payer options that the person can recieve.
   * @return a random payer from the given list of options.
   */
  public default Payer chooseRandomlyFromList(List<Payer> options) {
    if (options.isEmpty()) {
      return Payer.noInsurance;
    } else if (options.size() == 1) {
      return options.get(0);
    } else {
      // There are a few equally good options, pick one randomly.
      Random r = new Random();
      return options.get(r.nextInt(options.size()));
    }
  }
}