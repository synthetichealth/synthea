package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

public class VeteranEligiblity implements IPlanEligibility {

  @Override
  public boolean isPersonEligible(Person person, long time) {
    return person.attributes.containsKey(Person.VETERAN);
  }

}
