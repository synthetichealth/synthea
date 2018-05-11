package org.mitre.synthea.world.concepts;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class Costs {
  // all of these are CSVs with these columns: code, cost in $, comments
  private static final Map<String, Double> PROCEDURE_COSTS =
      parseCsvToMap("costs/procedures.csv");
  private static final Map<String, Double> MEDICATION_COSTS =
      parseCsvToMap("costs/medications.csv");
  private static final Map<String, Double> ENCOUNTER_COSTS =
      parseCsvToMap("costs/encounters.csv");
  private static final Map<String, Double> IMMUNIZATION_COSTS =
      parseCsvToMap("costs/immunizations.csv");
  
  private static final double DEFAULT_PROCEDURE_COST =
      Double.parseDouble(Config.get("generate.costs.default_procedure_cost"));
  private static final double DEFAULT_MEDICATION_COST =
      Double.parseDouble(Config.get("generate.costs.default_medication_cost"));
  private static final double DEFAULT_ENCOUNTER_COST =
      Double.parseDouble(Config.get("generate.costs.default_encounter_cost"));
  private static final double DEFAULT_IMMUNIZATION_COST =
      Double.parseDouble(Config.get("generate.costs.default_immunization_cost"));
  
  private static final Map<String, Double> LOCATION_ADJUSTMENT_FACTORS = 
      parseCsvToMap("costs/adjustmentFactors.csv"); 
  // Note that this file will have headers CODE and COST for simplicity
  
  /**
   * Load all cost data needed by the system.
   */
  public static void loadCostData() {
    // intentionally do nothing
    // this method is only called to ensure the static data is loaded at a predictable time
  }
  
  private static Map<String, Double> parseCsvToMap(String filename) {
    try {
      String rawData = Utilities.readResource(filename);
      List<LinkedHashMap<String, String>> lines = SimpleCSV.parse(rawData);
      
      Map<String, Double> costMap = new HashMap<>();
      for (Map<String,String> line : lines) {
        String code = line.get("CODE");
        String costString = line.get("COST");
        
        try {
          Double cost = Double.valueOf(costString);
          costMap.put(code, cost);
        } catch (NumberFormatException nfe) {
          System.err.println(filename + ": Invalid cost for code: '" + code
              + "' -- cost should be numeric but was '" + costString + "'");
          System.err.println("Code '" + code + "' will use the default cost");
          nfe.printStackTrace();
        }
      }
      
      return costMap;
    } catch (IOException e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError("Unable to read required file: " + filename);
    }
  }

  /**
   * Whether or not this HealthRecord.Entry has an associated cost on a claim.
   * Billing cost is not necessarily reimbursed cost or paid cost.
   * @param entry HealthRecord.Entry
   * @return true if the entry has a cost; false otherwise
   */
  public static boolean hasCost(Entry entry) {
    return (entry instanceof HealthRecord.Procedure)
        || (entry instanceof HealthRecord.Medication)
        || (entry instanceof HealthRecord.Encounter)
        || (entry instanceof HealthRecord.Immunization);
  }

  /**
   * Calculate the cost of this Procedure, Encounter, Medication, etc.
   * 
   * @param entry Entry to calculate cost of.
   * @param patient Person to whom the entry refers to
   * @param provider Provider that performed the service, if any
   * @param payer Entity paying for the service, if any
   * @return Cost, in USD.
   */
  public static double calculateCost(Entry entry, Person patient, Provider provider, String payer) {
    if (!hasCost(entry)) {
      return 0;
    }
    
    String code = entry.codes.get(0).code;
    
    double baseCost = 0.0;
    
    if (entry instanceof HealthRecord.Procedure) {
      baseCost = PROCEDURE_COSTS.getOrDefault(code, DEFAULT_PROCEDURE_COST);
    } else if (entry instanceof HealthRecord.Medication) {
      baseCost = MEDICATION_COSTS.getOrDefault(code, DEFAULT_MEDICATION_COST);
    } else if (entry instanceof HealthRecord.Encounter) {
      baseCost = ENCOUNTER_COSTS.getOrDefault(code, DEFAULT_ENCOUNTER_COST);
    } else if (entry instanceof HealthRecord.Immunization) {
      baseCost = IMMUNIZATION_COSTS.getOrDefault(code, DEFAULT_IMMUNIZATION_COST);
    }
    
    double locationAdjustment = 1.0;
    if (patient != null && patient.attributes.containsKey(Person.STATE)) {
      String state = (String) patient.attributes.get(Person.STATE);
      
      if (LOCATION_ADJUSTMENT_FACTORS.containsKey(state)) {
        locationAdjustment = (double) LOCATION_ADJUSTMENT_FACTORS.get(state);
      }
    }
    
    return baseCost * locationAdjustment;
  }
}
