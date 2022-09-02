package org.mitre.synthea.world.concepts.healthinsurance;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import org.apache.commons.lang3.Range;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planeligibility.IPlanEligibility;
import org.mitre.synthea.world.agents.behaviors.planeligibility.PlanEligibilityFinder;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

/**
 * A class that defines an insurance plan.
 */
public class InsurancePlan implements Serializable {

  private final Payer payer;
  private final BigDecimal deductible;
  private final BigDecimal defaultCopay;
  private final BigDecimal defaultCoinsurance;
  private final BigDecimal monthlyPremium;
  private final Set<String> servicesCovered;
  private final boolean medicareSupplement;
  private final String insuranceStatus;
  // Plan Eligibility.
  private final IPlanEligibility planEligibility;
  // Start/end date of plan availablity.
  private final Range<Long> activeTimeRange;

  /**
   * Constructor for an InsurancePlan.
   * @param payer The plan's payer.
   * @param servicesCovered The services covered.
   * @param deductible  The deductible.
   * @param defaultCoinsurance  The default coinsurance.
   * @param defaultCopay  The default copay.
   * @param monthlyPremium  The monthly premium.
   * @param eligibilityName The eligibility algorithm to use.
   */
  public InsurancePlan(Payer payer, Set<String> servicesCovered, BigDecimal deductible,
      BigDecimal defaultCoinsurance, BigDecimal defaultCopay, BigDecimal monthlyPremium,
      boolean medicareSupplement, int activeYearStart, int activeYearEnd, String eligibilityName) {
    this.payer = payer;
    this.deductible = deductible;
    this.defaultCoinsurance = defaultCoinsurance;
    this.defaultCopay = defaultCopay;
    this.monthlyPremium = monthlyPremium;
    this.servicesCovered = servicesCovered;
    this.medicareSupplement = medicareSupplement;
    if (activeYearStart >= activeYearEnd) {
      throw new RuntimeException("Plan start year cannot be after its end year."
      + "Was given start year: " + activeYearStart + " and end year " + activeYearEnd + ".");
    }
    long activeTimeStart = Utilities.convertCalendarYearsToTime(activeYearStart);
    long activeTimeEnd = Utilities.convertCalendarYearsToTime(activeYearEnd);
    this.activeTimeRange = Range.between(activeTimeStart, activeTimeEnd);
    // Set the payer's eligibility criteria.
    this.planEligibility = PlanEligibilityFinder.getEligibilityAlgorithm(eligibilityName);
    this.insuranceStatus = this.determineInsuranceStatus();
  }

  /**
   * Returns whether this plan is active at the given time.
   * @param time The time to check if the plan is active at.
   * @return whether this plan is active at the given time.
   */
  public boolean isActive(long time) {
    return this.activeTimeRange.contains(time);
  }

  /**
   * Determines the copay owed for this Payer based on the type of entry.
   * For now, this returns a default copay. But in the future there will be different
   * copays depending on the encounter type covered. If the entry is a wellness visit
   * and the time is after the mandate year, then the copay is $0.00.
   *
   * @param entry the entry to calculate the copay for.
   */
  public BigDecimal determineCopay(Entry entry) {
    BigDecimal copay = this.defaultCopay;
    if (entry.type.equalsIgnoreCase(EncounterType.WELLNESS.toString())
        && entry.start > HealthInsuranceModule.mandateTime) {
      copay = Claim.ZERO_CENTS;
    }
    return copay;
  }

  public BigDecimal getMonthlyPremium() {
    return this.monthlyPremium;
  }

  public BigDecimal getDeductible() {
    return this.deductible;
  }

  public BigDecimal getPayerCoinsurance() {
    return this.defaultCoinsurance;
  }

  public BigDecimal getPatientCoinsurance() {
    BigDecimal coinsurance = BigDecimal.ONE.subtract(this.defaultCoinsurance);
    return coinsurance.compareTo(BigDecimal.ONE) == -1 ? coinsurance : BigDecimal.ZERO;
  }

  /**
   * Pays the plan's premium to the Payer, increasing their revenue.
   *
   * @return the monthly premium amount.
   */
  public BigDecimal payMonthlyPremium() {
    BigDecimal premiumPaid = this.getMonthlyPremium();
    this.payer.addRevenue(premiumPaid);
    return premiumPaid;
  }

  public Payer getPayer() {
    return this.payer;
  }


  /**
   * Determines and returns the insurance status that this plan's payer would have
   * for its customers.
   * @return  The insurance status.
   */
  private String determineInsuranceStatus() {
    if (this.isNoInsurance()) {
      return "none";
    }
    if (this.payer.isGovernmentPayer()) {
      return this.payer.getName().toLowerCase();
    }
    return "private";
  }

  /**
   * Returns the insurance status type of this plan.
   * @return  The insurance status.
   */
  public String getInsuranceStatus() {
    return this.insuranceStatus;
  }

  /**
   * Increments the number of customers that have used this plan's payers.
   * @param personId  The person Id for whom to increment.
   */
  public void incrementCustomers(String personId) {
    this.payer.incrementCustomers(personId);
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
   * Determines whether this plan covers the given service type.
   * @param entry The entry to cover.
   * @return Whether this plan covers the given service type.
   */
  public boolean coversService(Entry entry) {
    if (entry == null) {
      return true;
    }
    String service = entry.type;
    return (service == null
        || this.servicesCovered.contains(service)
        || this.servicesCovered.contains("*"));
  }

  /**
   * Adds a covered cost to this plan.
   * @param coveredCosts  The cost covered.
   */
  protected void addCoveredCost(BigDecimal coveredCosts) {
    this.payer.addCoveredCost(coveredCosts);
  }

  /**
   * Adds an uncovered cost to this plan.
   * @param uncoveredCosts  The cost covered.
   */
  protected void addUncoveredCost(BigDecimal uncoveredCosts) {
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
  public BigDecimal adjustClaim(ClaimEntry claimEntry, Person person) {
    return this.payer.adjustClaim(claimEntry, person);
  }

  /**
   * Returns the yearly cost of this plan.
   * @return the yearly cost.
   */
  public BigDecimal getYearlyCost() {
    BigDecimal yearlyPremiumTotal = this.getMonthlyPremium()
            .multiply(BigDecimal.valueOf(12))
            .setScale(2, RoundingMode.HALF_EVEN);
    return yearlyPremiumTotal;
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

  /**
   * Returns whether this is a government plan.
   * @return whether this is a government plan.
   */
  public boolean isGovernmentPlan() {
    return this.payer.isGovernmentPayer();
  }

  /**
   * Returns whether a customer may buy a supplement to this plan.
   * @return  Whether a person may buy a supplement plan.
   */
  public boolean mayPurchaseSupplement() {
    return this.getPayer().getName().equals("Medicare");
  }

  /**
   * Returns whether this plans is a medicare supplement plan.
   */
  public boolean isMedicareSupplementPlan() {
    return this.medicareSupplement;
  }

  /**
   * Returns whether this plan is copay-based.
   * @return whether this is a copay-based plan.
   */
  public boolean isCopayBased() {
    return this.defaultCopay.compareTo(BigDecimal.ZERO) > 0;
  }

}
