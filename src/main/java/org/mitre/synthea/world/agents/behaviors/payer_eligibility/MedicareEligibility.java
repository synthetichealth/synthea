package org.mitre.synthea.world.agents.behaviors.payer_eligibility;

import org.mitre.synthea.world.agents.Person;

public class MedicareEligibility implements IPayerEligibility {

    @Override
    public boolean isPersonEligible(Person person, long time) {
        boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
                && (boolean) person.attributes.get("end_stage_renal_disease"));
        boolean sixtyFive = (person.ageInYears(time) >= 65);
        boolean medicareEligible = sixtyFive || esrd;
        return medicareEligible;
    }
}
