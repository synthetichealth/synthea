package org.mitre.synthea.world.concepts;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class LostCareHealthRecord extends HealthRecord {

    public static boolean lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);

    public LostCareHealthRecord(Person person) {
        super(person);
    }
}
