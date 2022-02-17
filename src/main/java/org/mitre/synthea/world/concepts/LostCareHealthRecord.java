package org.mitre.synthea.world.concepts;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class LostCareHealthRecord extends HealthRecord {

    private static boolean lossOfCareEnabled = updateLossOfCareFlag();

    public LostCareHealthRecord(Person person) {
        super(person);
    }

    public static boolean updateLossOfCareFlag() {
      lossOfCareEnabled = Config.getAsBoolean("generate.payers.loss_of_care", false);
      return lossOfCareEnabled;
    }

    public static boolean lossOfCareEnabled() {
      return lossOfCareEnabled;
    }
}
