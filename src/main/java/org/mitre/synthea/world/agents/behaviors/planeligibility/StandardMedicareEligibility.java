package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard medicare elgibilty criteria.
 */
public class StandardMedicareEligibility implements IPlanEligibility {

  private static final int ageRequirement = 65; //65

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean ssdEligible = PlanEligibilityFinder.getPayerEligibilityAlgorithm(SocialSecurityEligibilty.SOCIAL_SECURITY).isPersonEligible(person, time);
    boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
        && (boolean) person.attributes.get("end_stage_renal_disease"));
    int personAge = person.ageInYears(time);
    boolean ageEligible = personAge >= ageRequirement;
    return ssdEligible || ageEligible || esrd;
  }
}
