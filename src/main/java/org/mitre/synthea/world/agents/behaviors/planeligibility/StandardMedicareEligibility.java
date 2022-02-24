package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard medicare elgibilty criteria.
 */
public class StandardMedicareEligibility implements IPlanEligibility {

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
        && (boolean) person.attributes.get("end_stage_renal_disease"));
    int personAge = person.ageInYears(time);
    boolean sixtyFive = personAge >= 65.0;
    return sixtyFive || esrd;
  }
}
