package org.mitre.synthea.world.agents.behaviors.planeligibility;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * An algorithm that dictates the standard medicaid elgibilty criteria.
 */
public class StandardMedicaidEligibility implements IPlanEligibility {

  private static final double defaultPoverty = 1.33;
  private final IPlanEligibility medicaidPovertyEligibility;
  private final IPlanEligibility medicaidMnilEligibility;

  public StandardMedicaidEligibility(String state) {
    String povertyAndMnilFile = Config.get("generate.payers.insurance_companies.medicaid_eligibility");
    this.medicaidPovertyEligibility = new PovertyMultiplierFileEligibility(state, povertyAndMnilFile);
    this.medicaidMnilEligibility = new MedicallyNeedyIncomeEligibility(state, povertyAndMnilFile);
  }

  @Override
  public boolean isPersonEligible(Person person, long time) {
    boolean blind = (person.attributes.containsKey("blindness")
        && (boolean) person.attributes.get("blindness"));

    boolean medicaidIncomeEligible = this.medicaidPovertyEligibility.isPersonEligible(person, time);

    boolean medicaidEligible =  blind || medicaidIncomeEligible;

    if (!medicaidEligible) {
      // If the person is not medicaid eligble, check if they're MNIL eligble.
      medicaidEligible = medicaidMnilEligibility.isPersonEligible(person, time);
    }
    return medicaidEligible;
  }
}
