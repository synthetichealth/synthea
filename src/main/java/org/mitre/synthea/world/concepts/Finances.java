package org.mitre.synthea.world.concepts;

import org.mitre.synthea.modules.HealthInsuranceModule;

/**
 * This class manages all financial information related to a Person.
 */
public class Finances {

  // TODO - manage the peron's total expenses, don't just subtract from their income.
  // Right now, expenses are managed in the CoverageRecord.

  public static final String FINANCES = "FINANCES";
  private String socioeconmicCategory = "";
  private int income;
  private int lastMonthPaid = 0;
  private double incomeLevel;
  private double occupationLevel;
  private double povertyRatio;
  private double sesScore;

  public Finances(int income, double incomeLevel, double povertyRatio, double occupation, double sesScore) {
    this.income = income;
    this.incomeLevel = incomeLevel;
    this.povertyRatio = povertyRatio;
    this.occupationLevel = occupation;
    this.sesScore = sesScore;
  }


  /**
   * Returns whether it is time for these finances to pay a new monthly premium.
   * @param month
   * @return
   */
  public boolean timeToPayMonthlyPremium(int month) {
    return month > lastMonthPaid || (month == 1 && lastMonthPaid == 12);
  }


  public void updateLastMonthPaid(int month) {
    this.lastMonthPaid = month;
  }


  public int getIncome() {
    return this.income;
  }

  public Object getSocioeconmicCategory() {
    return this.socioeconmicCategory;
  }


  public double getIncomeLevel() {
    return this.incomeLevel;
  }

  public boolean occupationMeetsInsuranceMandate() {
    return this.occupationLevel >= HealthInsuranceModule.mandateOccupation;
  }
  
}
