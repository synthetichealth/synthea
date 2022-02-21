package org.mitre.synthea.world.agents.behaviors.payereligibility;

import org.mitre.synthea.world.agents.Person;

/**
 * A payer eligibilty algorithm that accepts all patients.
 */
public class GenericPayerEligibilty implements IPayerEligibility {

    // Generic Selection algorithm (maybe takes prameters to dictate certain eligibilty)

    @Override
    public boolean isPersonEligible(Person person, long time) {
        // By default, we're currently assuming that generic payer eligiblity will accept any patients.
        return true;
    }
}
