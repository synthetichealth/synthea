package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.util.Set;

import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planeligibility.IPlanEligibility;
import org.mitre.synthea.world.agents.behaviors.planeligibility.PlanEligibilityFinder;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.healthinsurance.Claim.ClaimEntry;

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
  private final boolean medicareSupplement;
  // Plan Eligibilty strategy.
  private transient IPlanEligibility planEligibility;

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
      double defaultCoinsurance, double defaultCopay, double monthlyPremium, boolean medicareSupplement) {
    this.payer = payer;
    this.deductible = deductible;
    this.defaultCoinsurance = defaultCoinsurance;
    this.defaultCopay = defaultCopay;
    this.monthlyPremium = monthlyPremium;
    this.servicesCovered = servicesCovered;
    this.medicareSupplement = medicareSupplement;
    // Set the payer's eligibility criteria.
    this.planEligibility = PlanEligibilityFinder.getPlanEligibilityAlgorithm(this.payer.getEligibilityName());
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
    return this.payer.equals(PayerManager.getGovernmentPayer(PayerManager.MEDICARE));
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
   * Increments the number of covered entries.
   * @param entry  The covered entry.
   */
  public void incrementCoveredEntries(Entry entry) {
    this.payer.incrementCoveredEntries(entry);
  }

  /**
   * Increments the number of uncovered entries.
   * @param entry  The uncovered entry.
   */
  public void incrementUncoveredEntries(Entry entry) {
    this.payer.incrementUncoveredEntries(entry);
  }

  /**
   * Determines whether this plan covere the request service type.
   * @param entry The entry to cover.
   * @return Whether this plan covere the request service type.
   */
  public boolean coversService(Entry entry) {
    if (entry == null) {
      return true;
    }
    String service = entry.type;
    return (service == null
        || this.servicesCovered.contains(service)
        || this.servicesCovered.contains("*"))
        && this.payer.isInNetwork(null);
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

  /**
   * Determines whether or not this payer will adjust this claim, and by how
   * much. This determination is based on the claim adjustment strategy configuration,
   * which defaults to none.
   * @param claimEntry The claim entry to be adjusted.
   * @param person The person making the claim.
   * @return The dollar amount the claim entry was adjusted.
   */
  public double adjustClaim(ClaimEntry claimEntry, Person person) {
    return this.payer.adjustClaim(claimEntry, person);
  }

  /**
   * Returns the yearly cost of this plan.
   * @return
   */
  public double getYearlyCost() {
    double yearlyPremiumTotal = this.monthlyPremium * 12;
    double yearlyDeductible = this.deductible;
    return yearlyPremiumTotal + yearlyDeductible;
  }

  /**
   * Returns whether a plan will accept the given patient at this
   * time using the plan eligibility criteria.
   * @param person Person to consider
   * @param time   Time the person seeks care
   * @return whether or not the payer will accept this patient as a customer
   */
  public boolean accepts(Person person, long time) {
    return this.planEligibility.isPersonEligible(person, time);
  }

  /**
   * Returns whether this plan is based on the no insurance payer.
   * @return
   */
  public boolean isNoInsurance() {
    return this.payer.isNoInsurance();
  }

  public boolean isGovernmentPlan() {
    return this.payer.isGovernmentPayer();
  }

  /**
   * Returns whether this plans is a medicare supplement plan.
   */
  public boolean isMedicareSupplementPlan() {
    return this.medicareSupplement;
  }

}
