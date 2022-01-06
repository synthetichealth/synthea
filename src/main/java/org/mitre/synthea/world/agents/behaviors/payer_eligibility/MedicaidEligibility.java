package org.mitre.synthea.world.agents.behaviors.payer_eligibility;

import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Person;

public class MedicaidEligibility implements IPayerEligibility {

    @Override
    public boolean isPersonEligible(Person person, long time) {
        boolean female = (person.attributes.get(Person.GENDER).equals("F"));
        boolean pregnant = (person.attributes.containsKey("pregnant")
                && (boolean) person.attributes.get("pregnant"));
        boolean blind = (person.attributes.containsKey("blindness")
                && (boolean) person.attributes.get("blindness"));
        int income = (Integer) person.attributes.get(Person.INCOME);
        // TODO - why is medicaid level in the HealthInsuranceModule?
        boolean medicaidIncomeEligible = (income <= HealthInsuranceModule.medicaidLevel);

        boolean medicaidEligible = (female && pregnant) || blind || medicaidIncomeEligible;
        return medicaidEligible;
    }
}
