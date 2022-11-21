package org.mitre.synthea.modules;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.calculators.ASCVD;
import org.mitre.synthea.modules.calculators.Framingham;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public final class CardiovascularDiseaseModule extends Module {
  public CardiovascularDiseaseModule() {
    this.name = "Cardiovascular Disease";
  }

  public Module clone() {
    return this;
  }

  @Override
  public boolean process(Person person, long time) {
    if (!person.alive(time)) {
      return true;
    }
    // Most of this module has been converted into generic modules,
    // with the exception of calculating annual risk numbers.
    if (useFramingham) {
      calculateCardioRisk(person, time);
    } else {
      calculateAscvdRisk(person, time);
    }

    calculateAtrialFibrillationRisk(person, time);
    calculateStrokeRisk(person, time);

    // java modules will never "finish"
    return false;
  }

  /** TODO: make this a config parameter for which risk system we want to use. */
  private static boolean useFramingham = false;
  private static final long tenYearsInMS = TimeUnit.DAYS.toMillis(3650);
  private static final long oneMonthInMS = TimeUnit.DAYS.toMillis(30); // roughly

  /**
   * Calculates the risk of cardiovascular disease using Framingham points
   * and look up tables, putting the current risk in a "cardio_risk" attribute.
   * @param person The patient.
   * @param time The risk is calculated for the given time.
   */
  private static void calculateCardioRisk(Person person, long time) {
    double framinghamRisk = Framingham.chd10Year(person, time, false);
    person.attributes.put("framingham_risk", framinghamRisk);

    double timestepRisk = Utilities.convertRiskToTimestep(framinghamRisk, tenYearsInMS);
    person.attributes.put("cardio_risk", timestepRisk);

    double monthlyRisk =
        Utilities.convertRiskToTimestep(framinghamRisk, tenYearsInMS, oneMonthInMS);
    person.attributes.put("mi_risk", monthlyRisk);
    // drives the myocardial_infarction module

    person.attributes.put("ihd_risk", monthlyRisk * 5);
    // drives the stable_ischemic_heart_disease module
    // multiply by 5 to account for the relative prevalence of the various outcomes
  }

  /**
   * Calculates the 10-year ASCVD Risk Estimates.
   */
  private static void calculateAscvdRisk(Person person, long time) {
    double ascvdRisk = ASCVD.ascvd10Year(person, time, false);
    person.attributes.put("ascvd_risk", ascvdRisk);

    double timestepRisk = Utilities.convertRiskToTimestep(ascvdRisk, tenYearsInMS);
    person.attributes.put("cardio_risk", timestepRisk);

    double monthlyRisk = Utilities.convertRiskToTimestep(ascvdRisk, tenYearsInMS, oneMonthInMS);
    person.attributes.put("mi_risk", monthlyRisk);
    // drives the myocardial_infarction module

    person.attributes.put("ihd_risk", monthlyRisk * 5);
    // drives the stable_ischemic_heart_disease module
    // multiply by 5 to account for the relative prevalence of the various outcomes
  }

  /**
   * Depending on gender, BMI, and blood pressure, there is a small risk of
   * Atrial Fibrillation which is calculated and stored in "atrial_fibrillation_risk".
   * @param person The patient.
   * @param time The time.
   */
  private static void calculateAtrialFibrillationRisk(Person person, long time) {
    double afRisk = Framingham.atrialFibrillation10Year(person, time, false);
    person.attributes.put("atrial_fibrillation_risk",
        Utilities.convertRiskToTimestep(afRisk, tenYearsInMS));
  }

  /**
   * Depending on gender, age, smoking status, and various comorbidities (e.g. diabetes,
   * coronary heart disease, atrial fibrillation), this function calculates the risk
   * of a stroke and stores it in the "stroke_risk" attribute.
   * @param person The patient.
   * @param time The time.
   */
  private static void calculateStrokeRisk(Person person, long time) {
    double tenStrokeRisk = Framingham.stroke10Year(person, time, false);
    // divide 10 year risk by 365 * 10 to get daily risk.
    person.attributes.put("stroke_risk",
        Utilities.convertRiskToTimestep(tenStrokeRisk, tenYearsInMS));
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   *
   * @return Collection of all codes and concepts this module uses
   */
  public static Collection<Code> getAllCodes() {
    return Collections.emptyList();
  }

  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String,Inventory> attributes) {
    String m = CardiovascularDiseaseModule.class.getSimpleName();
    // Write
    Attributes.inventory(attributes, m, "ascvd_risk", false, true, "0.001");
    Attributes.inventory(attributes, m, "atrial_fibrillation_risk", false, true, "0.001");
    Attributes.inventory(attributes, m, "cardio_risk", false, true, "0.001");
    Attributes.inventory(attributes, m, "framingham_risk", false, true, "0.001");
    Attributes.inventory(attributes, m, "ihd_risk", false, true, "0.001");
    Attributes.inventory(attributes, m, "mi_risk", false, true, "0.001");
    Attributes.inventory(attributes, m, "stroke_risk", false, true, "0.001");
  }
}