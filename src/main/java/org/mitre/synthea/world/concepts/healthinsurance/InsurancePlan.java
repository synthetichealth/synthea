package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.util.Set;

import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerController;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/**
 * A class that defines an insurance plan.
 */
public class InsurancePlan implements Serializable {

    private final Payer payer;
    private final double deductible;
    private final double defaultCopay;
    private final double defaultCoinsurance;
    private final double monthlyPremium;
    private final Set<String> servicesCovered;

    public InsurancePlan(Payer payer, Set<String> servicesCovered, double deductible, double defaultCoinsurance, double defaultCopay, double monthlyPremium) {
        this.payer = payer;
        this.deductible = deductible;
        this.defaultCoinsurance = defaultCoinsurance;
        this.defaultCopay = defaultCopay;
        this.monthlyPremium = monthlyPremium;
        this.servicesCovered = servicesCovered;
    }

    public double determineCopay(HealthRecord.Entry recordEntry) {
        double copay = this.defaultCopay;
        if (recordEntry.type.equalsIgnoreCase(EncounterType.WELLNESS.toString())
            && recordEntry.start > HealthInsuranceModule.mandateTime) {
          copay = 0.0;
        }
        return copay;
    }

    public double getMonthlyPremium() {
        return this.monthlyPremium;
    }

    public double getDeductible() {
        return this.deductible;
    }

    public double getCoinsurance() {
        return this.defaultCoinsurance;
    }

    /**
   * Pays the plan's to the Payer, increasing their revenue.
   *
   * @return the monthly premium amount.
   */
  public double payMonthlyPremium() {
    // This will need to be updated to pull the correct monthly premium from the correct plan for a given person.
    // Will need to take a person's ID?
    double premiumPaid = this.getMonthlyPremium();
    this.payer.addRevenue(premiumPaid);
    return premiumPaid;
  }

    public Payer getPayer() {
        return this.payer;
    }

    /**
     * Returns whether this plan is a Medicare provided plan.
     * @return
     */
    public boolean isMedicarePlan() {
        return this.payer.equals(PayerController.getGovernmentPayer(HealthInsuranceModule.MEDICARE));
    }

    public String getAssociatedInsuranceStatus() {
        return this.payer.getAssociatedInsuranceStatus();
    }

    public void incrementCustomers(Person person) {
        this.payer.incrementCustomers(person);
    }

    public boolean coversService(String service) {
        return service == null
            || this.servicesCovered.contains(service)
            || this.servicesCovered.contains("*");
    }

    public void addCoveredCost(double coveredCosts) {
        this.payer.addCoveredCost(coveredCosts);
    }

    public void addUncoveredCost(double uncoveredCosts) {
        this.payer.addUncoveredCost(uncoveredCosts);
    }
}
