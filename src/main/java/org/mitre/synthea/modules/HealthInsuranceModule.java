package org.mitre.synthea.modules;

import java.util.List;
import java.util.Random;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class HealthInsuranceModule extends Module {
  // public static final String INSURANCE = "insurance";

  // public static final String NO_INSURANCE = "no_insurance";
  // public static final String PRIVATE = "private";
  // public static final String MEDICAID = "medicaid";
  // public static final String MEDICARE = "medicare";
  // public static final String DUAL_ELIGIBLE = "dual_eligible";

  public long mandateTime;
  public double mandateOccupation;
  public int privateIncomeThreshold;
  public double povertyLevel;
  public double medicaidLevel;

  public HealthInsuranceModule() {
    int mandateYear = Integer.parseInt(Config.get("generate.insurance.mandate.year", "2006"));
    mandateTime = Utilities.convertCalendarYearsToTime(mandateYear);
    mandateOccupation = Double.parseDouble(Config.get("generate.insurance.mandate.occupation", "0.2"));
    privateIncomeThreshold = Integer.parseInt(Config.get("generate.insurance.private.minimum_income", "24000"));
    povertyLevel = Double.parseDouble(Config.get("generate.demographics.socioeconomic.income.poverty", "11000"));
    medicaidLevel = 1.33 * povertyLevel;
  }

  @SuppressWarnings("unchecked")
  public boolean process(Person person, long time) {

    //List<Payer> payerHistory = (List<Payer>) person.getPayerHistory();

    int age = person.ageInYears(time);

    // payerhistory must be populated first...
    // Payer currentPayer = payerHistory.get(age);
    Payer currentPayer = determineInsurance(person, age, time);
    System.out.println(person.attributes.get(Person.NAME) + " Got: " + currentPayer.name);

    // Is "Dead Code"
    // if (currentPayer == null) {
    //   Payer previousPayer = null;
    //   if (age >= 1) {
    //     previousPayer = payerHistory.get(age - 1);
    //   }
    //   // Payer currentInsurance = determineInsurance(person, age, time);
    //   if (currentPayer != previousPayer && Provider.PROVIDER_SELECTION_BEHAVIOR.equals(Provider.NETWORK)) {
    //     // update providers if we are considering insurance networks
    //     for (EncounterType type : EncounterType.values()) {
    //       person.setProvider(type, time);
    //     }
    //   }
    // }
    person.payerHistory.set(age, currentPayer);

    // java modules will never "finish"
    return false;
  }

  private Payer determineInsurance(Person person, int age, long time) {
    boolean female = (person.attributes.get(Person.GENDER).equals("F"));
    boolean pregnant = (person.attributes.containsKey("pregnant") && (boolean) person.attributes.get("pregnant"));
    boolean blind = (person.attributes.containsKey("blindness") && (boolean) person.attributes.get("blindness"));
    boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
        && (boolean) person.attributes.get("end_stage_renal_disease"));
    boolean sixtyFive = (age >= 65);
    double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);
    int income = (Integer) person.attributes.get(Person.INCOME);
    boolean medicaidIncomeEligible = (income <= medicaidLevel);

    boolean medicare = false;
    boolean medicaid = false;

    if (sixtyFive || esrd) {
      medicare = true;
    }

    if ((female && pregnant) || blind || medicaidIncomeEligible) {
      medicaid = true;
    }

    // CURRENTLY ASSUMES: that medicare/medicaid/dualeligible are always the first
    // three entries of the list
    if (medicare && medicaid) {
      return Payer.getPayerList().get(2);
    } else if (medicare) {
      return Payer.getPayerList().get(0);
    } else if (medicaid) {
      return Payer.getPayerList().get(1);
    } else {
      if (time >= mandateTime && occupation >= mandateOccupation) {
        // Randomly choose one of the remaining private insurances
        return Payer.getPayerFinder().find(Payer.getPayerList(), person, null, time);
      }
      if (income >= privateIncomeThreshold) {
        // Randomly choose one of the remaining private insurances
        return Payer.getPayerFinder().find(Payer.getPayerList(), person, null, time);
      }
    }

    // No Insurance... What to return here?
    // Temportatily just a fake payer with 0.0 for every field (coverage/copay/premium)
    return Payer.noInsurance;
  }

  /**
   * Get the insurance recorded for a person at a given time. The time must not be
   * in the future or beyond the latest simulated date.
   * 
   * @param person The person under question.
   * @return A string categorization of insurance.
   */
  @SuppressWarnings("unchecked")
  public static Payer getCurrentInsurance(Person person, long time) {
    return person.getInsurance(time);
  }

  // Is the following necessary? Not sure if blindness is already an attribute before this.

  /**
   * Populate the given attribute map with the list of attributes that this module
   * reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  // public static void inventoryAttributes(Map<String,Inventory> attributes) {
  // String m = HealthInsuranceModule.class.getSimpleName();
  // Attributes.inventory(attributes, m, INSURANCE, true, true, "List<String>");
  // Attributes.inventory(attributes, m, "pregnant", true, false, "Boolean");
  // Attributes.inventory(attributes, m, "blindness", true, false, "Boolean");
  // Attributes.inventory(attributes, m, "end_stage_renal_disease", true, false,
  // "Boolean");
  // Attributes.inventory(attributes, m, Person.GENDER, true, false, "F");
  // Attributes.inventory(attributes, m, Person.OCCUPATION_LEVEL, true, false,
  // "Low");
  // Attributes.inventory(attributes, m, Person.INCOME, true, false, "1.0");
  // }
}
