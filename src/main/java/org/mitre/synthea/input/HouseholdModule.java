package org.mitre.synthea.input;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Person;

public class HouseholdModule extends Module {

  /**
   * HouseholdModule constructor.
   */
  public HouseholdModule() {}

  public Module clone() {
    return this;
  }

  /**
   * Process this HouseholdModule with the given Person at the specified
   * time within the simulation.
   * 
   * @param person the person being simulated
   * @param time   the date within the simulated world
   * @return completed : whether or not this Module completed.
   */
  @Override
  public boolean process(Person person, long time) {

    // Check the healthrecord if a person should become pregnant. Should be nine months before the birthdate of a dependent.
    Household household = (Household) person.attributes.get(Person.HOUSEHOLD);

    


    return true;
  }
    
}
