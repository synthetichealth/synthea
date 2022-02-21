package org.mitre.synthea.modules;

import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerController;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planfinder.IPlanFinder;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

public class HealthInsuranceModule extends Module {

  // Load properties insurance numbers.
  public static long mandateTime
      = Utilities.convertCalendarYearsToTime(Integer.parseInt(Config
      .get("generate.insurance.mandate.year", "2006")));
  public static double mandateOccupation =
      Config.getAsDouble("generate.insurance.mandate.occupation", 0.2);
  public static double medicaidLevel = 1.33
          * Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);
  public static final String MEDICARE =
      Config.get("generate.payers.insurance_companies.medicare", "Medicare");
  public static final String MEDICAID =
      Config.get("generate.payers.insurance_companies.medicaid", "Medicaid");
  public static final String DUAL_ELIGIBLE =
      Config.get("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");
  public static final String INSURANCE_STATUS = "insurance_status";

  /**
   * HealthInsuranceModule constructor.
   */
  public HealthInsuranceModule() {}

  // TODO - this should clone the module not return the original.
  @Override
  public Module clone() {
    return this;
  }

  /**
   * Process this HealthInsuranceModule with the given Person at the specified
   * time within the simulation.
   *
   * @param person the person being simulated
   * @param time   the date within the simulated world
   * @return completed : whether or not this Module completed.
   */
  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }

    // If the payerHistory at the current age is null, they must get insurance for the new year.
    // Note: This means the person will check to change insurance yearly, just after their
    // birthday.
    InsurancePlan planAtTime = person.coverage.getPlanAtTime(time);
    if (planAtTime == null) {
      // Update their last plan's payer with person's QOLS for that year.
      Payer lastPayer = person.coverage.getLastPayer();
      if (lastPayer != null) {
        lastPayer.addQols(person.getQolsForYear(Utilities.getYear(time) - 1));
      }
      // Determine the insurance for this person at this time.
      InsurancePlan newPlan = determineInsurance(person, time);
      InsurancePlan secondaryPlan = null;

      // If the payer is Medicare, they may buy supplemental insurance.
      if (newPlan.isMedicarePlan() && (person.rand() <= 0.8)) {
        // Buy supplemental insurance if it is affordable
        secondaryPlan = PayerController.findPlan(person, null, time);
      } else {
        // This patient will not purchase secondary insurance.
        secondaryPlan = PayerController.noInsurance.getNoInsurancePlan();
      }

      // Set this new payer at the current time for the person.
      person.coverage.setPlanAtTime(time, newPlan, secondaryPlan);

      // Update the new Payer's customer statistics.
      newPlan.incrementCustomers(person);
      if (PayerController.noInsurance.getNoInsurancePlan() != secondaryPlan) {
        secondaryPlan.incrementCustomers(person);
      }

      // Set insurance attribute for module access
      String insuranceStatus = newPlan.getAssociatedInsuranceStatus();
      person.attributes.put(INSURANCE_STATUS, insuranceStatus);
    }

    // Checks if person has paid their premium this month. If not, they pay it.
    person.checkToPayMonthlyPremium(time);

    // java modules will never "finish"
    return false;
  }

  /**
   * Determine what insurance a person will get based on their attributes.
   *
   * @param person the person to cover
   * @param time   the current time to consider
   * @return the insurance that this person gets
   */
  private InsurancePlan determineInsurance(Person person, long time) {
    // Government payers
    Payer medicare = PayerController.getGovernmentPayer(MEDICARE);
    Payer medicaid = PayerController.getGovernmentPayer(MEDICAID);
    Payer dualPayer = PayerController.getGovernmentPayer(DUAL_ELIGIBLE);

    boolean medicareEligible = medicare != null && medicare.accepts(person, time);
    boolean medicaidEligible = medicaid != null && medicaid.accepts(person, time);

    // If Medicare/Medicaid will accept this person, then it takes priority.
    if (medicareEligible && medicaidEligible) {
      return dualPayer.getGovernmentPayerPlan();
    } else if (medicareEligible) {
      return medicare.getGovernmentPayerPlan();
    } else if (medicaidEligible) {
      return medicaid.getGovernmentPayerPlan();
    }
    InsurancePlan planAtTime = person.coverage.getPlanAtTime(time);
    if (planAtTime != null
        && IPlanFinder.meetsBasicRequirements(planAtTime.getPayer(), person, null, time)) {
      // People will keep their previous year's insurance if they can.
      return planAtTime;
    }
    // Randomly choose one of the remaining private payer's plans.
    // Returns the no insurance plan if a person cannot afford any of them.
    return PayerController.findPlan(person, null, time);
  }

  /**
   * Populate the given attribute map with the list of attributes that this module
   * reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Inventory> attributes) {
    String m = HealthInsuranceModule.class.getSimpleName();
    Attributes.inventory(attributes, m, "pregnant", true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.BLINDNESS, true, false, "Boolean");
    Attributes.inventory(attributes, m, "end_stage_renal_disease", true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "F");
    Attributes.inventory(attributes, m, Person.OCCUPATION_LEVEL, true, false, "Low");
    Attributes.inventory(attributes, m, Person.INCOME, true, false, "1.0");
  }
}