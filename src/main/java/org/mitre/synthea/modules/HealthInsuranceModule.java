package org.mitre.synthea.modules;

import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;

public class HealthInsuranceModule extends Module {
  public static final String INSURANCE = "insurance";

  /**
   * HealthInsuranceModule constructor.
   */
  public HealthInsuranceModule() {}

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
    
    // If the payerHistory at the current age is null, they must get insurance for the new year.
    // Note: This means the person will check to change insurance yearly, just after their
    // birthday.
    if (person.getPayerAtTime(time) == null) {
      // Update their last payer with person's QOLS for that year.
      if(person.ageInYears(time) > 0){
        person.getPayerAtAge(person.ageInYears(time) - 1).addQOLS(person.getQolsForYear(Utilities.getYear(time) - 1));
      }
      // Determine the insurance for this person at this time.
      Payer newPayer = determineInsurance(person, time);
      // Set this new payer at the current time for the person.
      person.setPayerAtTime(time, newPayer);
      // Update the new Payer's customer statistics.
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

    // If Medicare/Medicaid will accept this person, then it takes priority.
    if (Payer.getGovernmentPayer("Medicare").accepts(person, time)
        && Payer.getGovernmentPayer("Medicaid").accepts(person, time)) {
      return Payer.getGovernmentPayer("Dual Eligible");
    } else if (Payer.getGovernmentPayer("Medicare").accepts(person, time)) {
      return Payer.getGovernmentPayer("Medicare");
    } else if (Payer.getGovernmentPayer("Medicaid").accepts(person, time)) {
      return Payer.getGovernmentPayer("Medicaid");
    } else {
      // Randomly choose one of the remaining private insurances. Returns no_insurance if a person cannot afford any of them.
      return Payer.getPayerFinder().find(Payer.getPrivatePayers(), person, null, time);
    }
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
