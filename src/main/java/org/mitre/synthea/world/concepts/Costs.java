package org.mitre.synthea.world.concepts;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
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
   * @param isFacility Whether to use facility-based cost factors.
   * @return Cost, in USD.
   */
  public static double calculateCost(Entry entry, boolean isFacility) {
    String code = entry.codes.get(0).code;
    
    if (entry instanceof HealthRecord.Procedure) {
      return PROCEDURE_COSTS.getOrDefault(code, DEFAULT_PROCEDURE_COST);
    } else if (entry instanceof HealthRecord.Medication) {
      return MEDICATION_COSTS.getOrDefault(code, DEFAULT_MEDICATION_COST);
    } else if (entry instanceof HealthRecord.Encounter) {
      return ENCOUNTER_COSTS.getOrDefault(code, DEFAULT_ENCOUNTER_COST);
    } else if (entry instanceof HealthRecord.Immunization) {
      return IMMUNIZATION_COSTS.getOrDefault(code, DEFAULT_IMMUNIZATION_COST);
    } else {
      return 0;
    }
  }
}
