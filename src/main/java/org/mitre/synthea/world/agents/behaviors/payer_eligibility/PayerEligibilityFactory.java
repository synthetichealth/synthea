package org.mitre.synthea.world.agents.behaviors.payer_eligibility;

import org.mitre.synthea.modules.HealthInsuranceModule;

/**
 * Returns the requested Payer elgibility algorithm. This prevents redundant
 * recreations of the same objects over and over.
 */
public class PayerEligibilityFactory {

    private static IPayerEligibility medicareEligibilty = new MedicareEligibility();
    private static IPayerEligibility medicaidEligibility = new MedicaidEligibility();
    private static IPayerEligibility genericEligibility = new GenericPayerEligibilty();

    /**
     * Returns the correct elgibility algorithm based on the payer's name. It uses names of either Medicare or Medicaid.
     * @param payerName
     * @return
     */
    public static IPayerEligibility getPayerEligibilityAlgorithm(String payerName) {
        if(payerName.equalsIgnoreCase(HealthInsuranceModule.MEDICAID)){
            return PayerEligibilityFactory.medicaidEligibility;
        } else if(payerName.equalsIgnoreCase(HealthInsuranceModule.MEDICARE)){
            return PayerEligibilityFactory.medicareEligibilty;
        }
        return PayerEligibilityFactory.genericEligibility;
    }

}
