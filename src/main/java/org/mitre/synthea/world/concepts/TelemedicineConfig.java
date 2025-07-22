package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Utilities;

/**
 * Configuration that drives TypeOfCare transitions in Synthea. The concept is that an individual
 * will seek a particular type of care (telemedicine vs. something in person) based on the time
 * in the simulation and their insurance. Some insurance types, or lack thereof will have higher
 * utilization of the emergency department.
 */
public class TelemedicineConfig implements Serializable {
  /** Ambulatory type of care */
  public static final String AMBULATORY = "ambulatory";
  /** Emergency type of care */
  public static final String EMERGENCY = "emergency";
  /** Telemedicine type of care */
  public static final String TELEMEDICINE = "telemedicine";

  /** The time in the simulation that transitions to telemedicine should start. */
  private long telemedicineStartTime;

  /** List of insurance names with high emergency department utilization. */
  private List<String> highEmergencyUseInsuranceNames;

  /** Distribution of emergency care types before telemedicine for high emergency use insurance. */
  private EnumeratedDistribution<String> preTelemedHighEmergency;

  /** Distribution of emergency care types before telemedicine
   * for typical emergency use insurance. */
  private EnumeratedDistribution<String> preTelemedTypicalEmergency;

  /** Distribution of emergency care types during telemedicine
   * for high emergency use insurance. */
  private EnumeratedDistribution<String> telemedHighEmergency;

  /** Distribution of emergency care types during telemedicine
   * for typical emergency use insurance. */
  private EnumeratedDistribution<String> telemedTypicalEmergency;

  /**
   * A class to hold the transition probabilities of a given scenario.
   * A scenario could be a person with a high ED utilization insurance plan
   * and in the telemedicine era.
   */
  public static class TelemedicineProbabilities {
    /** Ambulatory care transition probability */
    public double ambulatory;
    /** Emergency care transition probability */
    public double emergency;
    /** Telemedicine care transition probability */
    public double telemedicine;

    /**
     * Create a new object based on the Map that is pulled out of the config JSON.
     * @param json A fragment of the config file
     */
    public TelemedicineProbabilities(Map<String, Double> json) {
      ambulatory = json.get(AMBULATORY);
      emergency = json.get(EMERGENCY);
      if (json.containsKey(TELEMEDICINE)) {
        telemedicine = json.get(TELEMEDICINE);
      }
    }

    /**
     * Turn the configuration information into an actual EnumeratedDistribution which can be used
     * to select transitions.
     * @return A fully populated EnumeratedDistribution
     */
    public EnumeratedDistribution<String> toEnumeratedDistribution() {
      List<Pair<String, Double>> pmf = new ArrayList<>();
      pmf.add(new Pair(AMBULATORY, ambulatory));
      pmf.add(new Pair(EMERGENCY, emergency));
      if (telemedicine != 0) {
        pmf.add(new Pair(TELEMEDICINE, telemedicine));
      }
      return new EnumeratedDistribution<>(pmf);
    }
  }

  /**
   * Returns the time in the simulation that transitions to telemedicine should start.
   * @return The telemedicine start time.
   */
  public long getTelemedicineStartTime() {
    return telemedicineStartTime;
  }

  /**
   * Returns the list of insurance names with high emergency department utilization.
   * @return List of insurance names.
   */
  public List<String> getHighEmergencyUseInsuranceNames() {
    return highEmergencyUseInsuranceNames;
  }

  /**
   * Returns the distribution of emergency care types before
   * telemedicine for high emergency use insurance.
   * @return Distribution of emergency care types.
   */
  public EnumeratedDistribution<String> getPreTelemedHighEmergency() {
    return preTelemedHighEmergency;
  }

  /**
   * Returns the distribution of emergency care types before telemedicine for
   * typical emergency use insurance.
   * @return Distribution of emergency care types.
   */
  public EnumeratedDistribution<String> getPreTelemedTypicalEmergency() {
    return preTelemedTypicalEmergency;
  }

  /**
   * Returns the distribution of emergency care types during
   * telemedicine for high emergency use insurance.
   * @return Distribution of emergency care types.
   */
  public EnumeratedDistribution<String> getTelemedHighEmergency() {
    return telemedHighEmergency;
  }

  /**
   * Returns the distribution of emergency care types during
   * telemedicine for typical emergency use insurance.
   * @return Distribution of emergency care types.
   */
  public EnumeratedDistribution<String> getTelemedTypicalEmergency() {
    return telemedTypicalEmergency;
  }

  /**
   * Create an instance of TelemedicineConfig by reading it in from
   * the JSON file in resources.
   * @return A fully populated TelemedicineConfig object.
   */
  public static TelemedicineConfig fromJSON() {
    TelemedicineConfig config = new TelemedicineConfig();

    String filename = "telemedicine_config.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      HashMap configHash = g.fromJson(json, HashMap.class);
      int startYear = ((Double) configHash.get("start_year")).intValue();
      LocalDate telemedicineStartDate = LocalDate.of(startYear, 1, 1);
      config.telemedicineStartTime = Utilities.localDateToTimestamp(telemedicineStartDate);
      config.highEmergencyUseInsuranceNames =
              (List<String>) configHash.get("high_emergency_use_insurance_names");
      // woof
      Map<String, Map<String, Double>> pre =
              (Map<String, Map<String, Double>>) configHash.get("pre_telemedicine");
      TelemedicineProbabilities preHigh =
              new TelemedicineProbabilities(pre.get("high_emergency_distribution"));
      config.preTelemedHighEmergency = preHigh.toEnumeratedDistribution();
      TelemedicineProbabilities preTypical =
              new TelemedicineProbabilities(pre.get("typical_emergency_distribution"));
      config.preTelemedTypicalEmergency = preTypical.toEnumeratedDistribution();

      Map<String, Map<String, Double>> telemedicine =
              (Map<String, Map<String, Double>>) configHash.get("during_telemedicine");
      TelemedicineProbabilities telemedicineHigh =
              new TelemedicineProbabilities(telemedicine.get("high_emergency_distribution"));
      config.telemedHighEmergency = telemedicineHigh.toEnumeratedDistribution();
      TelemedicineProbabilities telemedicineTypical =
              new TelemedicineProbabilities(telemedicine.get("typical_emergency_distribution"));
      config.telemedTypicalEmergency = telemedicineTypical.toEnumeratedDistribution();

    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
    return config;
  }
}
