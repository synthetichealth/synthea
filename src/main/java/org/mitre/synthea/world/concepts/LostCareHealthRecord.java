package org.mitre.synthea.world.concepts;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class LostCareHealthRecord extends HealthRecord {

    /**
     * Experimental feature flag. When "lossOfCareEnabled" is true, patients can miss
     * care due to cost or lack of health insurance coverage.
     */
    public static boolean lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);

    public LostCareHealthRecord(Person person) {
        super(person);
    }
}