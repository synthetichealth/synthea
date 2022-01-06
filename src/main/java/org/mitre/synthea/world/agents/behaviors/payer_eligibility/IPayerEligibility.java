package org.mitre.synthea.world.agents.behaviors.payer_eligibility;

import org.mitre.synthea.world.agents.Person;

public interface IPayerEligibility {

    /**
     * Returns whether the given person meets the eligibilty criteria of this
     * algorithm at the given time.
     * 
     * @param person
     * @param time
     * @return
     */
    public boolean isPersonEligible(Person person, long time);

}
