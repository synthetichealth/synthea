package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard medicare elgibilty criteria.
 */
public class StandardMedicareEligibility implements IPlanEligibility {

  private final IPlanEligibility ageEligibility;
  private final IPlanEligibility ssdEligibility;

  public StandardMedicareEligibility() {
    String fileName = Config.get("generate.payers.insurance_plans.ssd_eligibility");
    ssdEligibility = new SocialSecurityEligibilty(fileName);
    ageEligibility = new AgeThresholdEligibility(65);
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean ssdEligible = ssdEligibility.isPersonEligible(person, time);
    boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
        && (boolean) person.attributes.get("end_stage_renal_disease"));
    boolean ageEligible = ageEligibility.isPersonEligible(person, time);
    return ssdEligible || ageEligible || esrd;
  }
}
