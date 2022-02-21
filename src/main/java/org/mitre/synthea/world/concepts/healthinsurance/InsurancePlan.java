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

  /**
   * Constructor for an InsurancePlan.
   * @param payer The plan's payer.
   * @param servicesCovered The services covered.
   * @param deductible  The deductible.
   * @param defaultCoinsurance  The default coinsuranc.
   * @param defaultCopay  The default copay.
   * @param monthlyPremium  The montly premium.
   */
  public InsurancePlan(Payer payer, Set<String> servicesCovered, double deductible,
      double defaultCoinsurance, double defaultCopay, double monthlyPremium) {
    this.payer = payer;
    this.deductible = deductible;
    this.defaultCoinsurance = defaultCoinsurance;
    this.defaultCopay = defaultCopay;
    this.monthlyPremium = monthlyPremium;
    this.servicesCovered = servicesCovered;
  }

  /**
   * Determines the copay owed by this InsurancePlan on the given entry.
   * @param recordEntry The entry to check against.
   * @return  The copay.
   */
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
    // This will need to be updated to pull the correct monthly premium from the
    // correct plan for a given person.
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
   * @return  Whether this is a medicar provided plan.
   */
  public boolean isMedicarePlan() {
    return this.payer.equals(PayerController.getGovernmentPayer(HealthInsuranceModule.MEDICARE));
  }

  /**
   * Returns the insurance status dictated by this plan's payer.
   * @return  The insurance status.
   */
  public String getAssociatedInsuranceStatus() {
    return this.payer.getAssociatedInsuranceStatus();
  }

  /**
   * Increments the number of customers that have used this plan.
   * @param person  The person for whom to increment.
   */
  public void incrementCustomers(Person person) {
    this.payer.incrementCustomers(person);
  }

  /**
   * Determines whether this plan covere the request service type.
   * @param service The service type.
   * @return Whether this plan covere the request service type.
   */
  public boolean coversService(String service) {
    return service == null
        || this.servicesCovered.contains(service)
        || this.servicesCovered.contains("*");
  }

  /**
   * Adds a covered cost to this plan.
   * @param coveredCosts  The cost covered.
   */
  public void addCoveredCost(double coveredCosts) {
    this.payer.addCoveredCost(coveredCosts);
  }

  /**
   * Adds an uncovered cost to this plan.
   * @param uncoveredCosts  The cost covered.
   */
  public void addUncoveredCost(double uncoveredCosts) {
    this.payer.addUncoveredCost(uncoveredCosts);
  }
}
