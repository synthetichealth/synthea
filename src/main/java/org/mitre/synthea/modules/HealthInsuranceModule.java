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

  public static long mandateTime;
  public static double mandateOccupation;
  public static int privateIncomeThreshold;
  public static double povertyLevel;
  public static double medicaidLevel;

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
  @Override
  public boolean process(Person person, long time) {
    
    // If the payerHistory at the given age is null, they must get insurance for the new year.
    // Note: This means the person will check to change insurance yearly, just after their
    // birthday.
    if (person.getPayerAtTime(time) == null) {
      Payer newPayer = determineInsurance(person, time);
      // Set the new payer at the current time
      person.setPayerAtTime(time, newPayer);
      // Update the Payer statistics
      person.getPayerAtTime(time).incrementCustomers(person);
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
  private Payer determineInsurance(Person person, long time) {

    double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);
    int income = (Integer) person.attributes.get(Person.INCOME);

    // If Medicare/Medicaid will accept this person, then it takes priority.
    if (Payer.getGovernmentPayer("Medicare").accepts(person, time)
        && Payer.getGovernmentPayer("Medicaid").accepts(person, time)) {
      return Payer.getGovernmentPayer("Dual Eligible");
    } else if (Payer.getGovernmentPayer("Medicare").accepts(person, time)) {
      return Payer.getGovernmentPayer("Medicare");
    } else if (Payer.getGovernmentPayer("Medicaid").accepts(person, time)) {
      return Payer.getGovernmentPayer("Medicaid");
    } else {
      // If this person can afford private insurance, they will recieve it.
      if ((time >= mandateTime && occupation >= mandateOccupation)
          || (income >= privateIncomeThreshold)) {
        // If this person had private insurance the previous year, the will keep it.
        if(person.ageInYears(time) > 0 && person.getPayerAtAge(person.ageInYears(time) - 1).getOwnership().equalsIgnoreCase("Private")){
          return person.getPayerAtAge(person.ageInYears(time) - 1);
        }
        // TODO - If this person can no longer afford this Payer, the will try to get a new one.
        // Randomly choose one of the remaining private insurances
        Payer newPayer = Payer.getPayerFinder().find(Payer.getAllPayers(), person, null, time);
        if (newPayer != null) {
          return newPayer;
        }
      }
    }
    // There is no insurance available to this person.
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
