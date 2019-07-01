package org.mitre.synthea.modules;

import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;

public class HealthInsuranceModule extends Module {
  public static final String INSURANCE = "insurance";

  public long mandateTime;
  public double mandateOccupation;
  public int privateIncomeThreshold;
  public double povertyLevel;
  public double medicaidLevel;

  /**
   * HealthInsuranceModule constructor.
   */
  public HealthInsuranceModule() {
    int mandateYear = Integer.parseInt(Config.get("generate.insurance.mandate.year", "2006"));
    mandateTime = Utilities.convertCalendarYearsToTime(mandateYear);
    mandateOccupation = Double
        .parseDouble(Config.get("generate.insurance.mandate.occupation", "0.2"));
    privateIncomeThreshold = Integer
        .parseInt(Config.get("generate.insurance.private.minimum_income", "24000"));
    povertyLevel = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.income.poverty", "11000"));
    medicaidLevel = 1.33 * povertyLevel;
  }

  /**
   * Process this HealthInsuranceModule with the given Person at the specified
   * time within the simulation.
   * 
   * @param person the person being simulated
   * @param time   the date within the simulated world
   * @return completed : whether or not this Module completed.
   */
  @SuppressWarnings("unchecked")
  @Override
  public boolean process(Person person, long time) {

    // Checks if person has paid their premium this month. If not, they pay it.
    person.checkToPayMonthlyPremium(time);
    
    // If the payerHistory at the given age is null, they must get insurance for the new year.
    // Note: This means the person will check to change insurance yearly, just after their
    // birthday.
    if (person.getPayerAtTime(time) == null) {
      Payer newPayer = determineInsurance(person, time);
      // Set the new payer at the current time
      person.setPayerAtTime(time, newPayer);
      // Update the Payer statistics
      newPayer.incrementCustomers(person);
    }

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
  private Payer determineInsurance(Person person, long time) {
    int age = person.ageInYears(time);
    boolean female = (person.attributes.get(Person.GENDER).equals("F"));
    boolean pregnant = (person.attributes.containsKey("pregnant")
        && (boolean) person.attributes.get("pregnant"));
    boolean blind = (person.attributes.containsKey("blindness")
        && (boolean) person.attributes.get("blindness"));
    boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
        && (boolean) person.attributes.get("end_stage_renal_disease"));
    boolean sixtyFive = (age >= 65);
    double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);
    int income = (Integer) person.attributes.get(Person.INCOME);
    boolean medicaidIncomeEligible = (income <= medicaidLevel);

    boolean medicare = false;
    boolean medicaid = false;

    // Possibly redundant because of the Payer.accepts method?
    // Perhaps makes more sense to have Medicare/Medicaid accept the patient,
    // not the other way around.
    if (sixtyFive || esrd) {
      medicare = true;
    }
    if ((female && pregnant) || blind || medicaidIncomeEligible) {
      medicaid = true;
    }

    // Currently assumes that medicare/medicaid/dualeligible are always the first
    // three entries of the list. Perhaps need to make a list of government payers?
    if (medicare && medicaid) {
      return Payer.getPayerList().get(2);
    } else if (medicare) {
      return Payer.getPayerList().get(0);
    } else if (medicaid) {
      return Payer.getPayerList().get(1);
    } else {
      if (time >= mandateTime && occupation >= mandateOccupation) {
        // Randomly choose one of the remaining private insurances
        Payer newPayer = Payer.getPayerFinder().find(Payer.getPayerList(), person, null, time);
        if(newPayer != null){
          // If Payer is null, then there is no insurance available to them. Return No_Insurance.
          return newPayer;
        }
      }
      if (income >= privateIncomeThreshold) {
        // Randomly choose one of the remaining private insurances
        Payer newPayer = Payer.getPayerFinder().find(Payer.getPayerList(), person, null, time);
        if(newPayer != null){
          // If Payer is null, then there is no insurance available to them. Return No_Insurance.
          return newPayer;
        }
      }
    }

    // No insurance. Return a fake payer with 0.0 for every field (coverage/copay/premium)
    return Payer.noInsurance;
  }

  /**
   * Populate the given attribute map with the list of attributes that this module
   * reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Inventory> attributes) {
    String m = HealthInsuranceModule.class.getSimpleName();
    Attributes.inventory(attributes, m, INSURANCE, true, true, "List<String>");
    Attributes.inventory(attributes, m, "pregnant", true, false, "Boolean");
    Attributes.inventory(attributes, m, "blindness", true, false, "Boolean");
    Attributes.inventory(attributes, m, "end_stage_renal_disease", true, false, "Boolean");
    Attributes.inventory(attributes, m, Person.GENDER, true, false, "F");
    Attributes.inventory(attributes, m, Person.OCCUPATION_LEVEL, true, false, "Low");
    Attributes.inventory(attributes, m, Person.INCOME, true, false, "1.0");
  }
}
