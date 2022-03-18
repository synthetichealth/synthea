package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard medicare elgibilty criteria.
 */
public class StandardMedicareEligibility implements IPlanEligibility {

  private static final int ageRequirement = 65; //65
  private final IPlanEligibility ssdEligibility;

  public StandardMedicareEligibility() {
    String fileName = Config.get("generate.payers.insurance_plans.ssd_eligibility");
    ssdEligibility = new SocialSecurityEligibilty(fileName);
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean ssdEligible = ssdEligibility.isPersonEligible(person, time);
    boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
        && (boolean) person.attributes.get("end_stage_renal_disease"));
    int personAge = person.ageInYears(time);
    boolean ageEligible = personAge >= ageRequirement;
    return ssdEligible || ageEligible || esrd;
  }
}
