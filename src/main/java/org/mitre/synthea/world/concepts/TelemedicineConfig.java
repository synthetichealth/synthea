package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;
import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Utilities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelemedicineConfig {
  public static final String AMBULATORY = "ambulatory";
  public static final String EMERGENCY = "emergency";
  public static final String TELEMEDICINE = "telemedicine";

  private long telemedicineStartTime;
  private List<String> highEmergencyUseInsuranceNames;

  private EnumeratedDistribution<String> preTelemedHighEmergency;
  private EnumeratedDistribution<String> preTelemedTypicalEmergency;
  private EnumeratedDistribution<String> telemedHighEmergency;
  private EnumeratedDistribution<String> telemedTypicalEmergency;

  public static class TelemedicineProbabilities {
    public double ambulatory;
    public double emergency;
    public double telemedicine;

    public TelemedicineProbabilities(Map<String, Double> json) {
      ambulatory = json.get(AMBULATORY);
      emergency = json.get(EMERGENCY);
      if (json.containsKey(TELEMEDICINE)) {
        telemedicine = json.get(TELEMEDICINE);
      }
    }

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

  public long getTelemedicineStartTime() {
    return telemedicineStartTime;
  }

  public List<String> getHighEmergencyUseInsuranceNames() {
    return highEmergencyUseInsuranceNames;
  }

  public EnumeratedDistribution<String> getPreTelemedHighEmergency() {
    return preTelemedHighEmergency;
  }

  public EnumeratedDistribution<String> getPreTelemedTypicalEmergency() {
    return preTelemedTypicalEmergency;
  }

  public EnumeratedDistribution<String> getTelemedHighEmergency() {
    return telemedHighEmergency;
  }

  public EnumeratedDistribution<String> getTelemedTypicalEmergency() {
    return telemedTypicalEmergency;
  }

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
