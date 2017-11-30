package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.mitre.synthea.helpers.Utilities;

public class BillingConcept {
  // HashMap of all codes in synthea
  private static HashMap<String, BillingConcept> conceptHash = 
      new HashMap<String, BillingConcept>();

  private String type;
  private String hcpc;
  private String description;
  private String icd;
  private String cost;

  public BillingConcept(String type, Map<String, ?> p) {
    this.type = type;
    hcpc = (String) p.get("hcpc");
    description = (String) p.get("description");
    icd = (String) p.get("icd-10");
    cost = (String) p.get("cost");
  }

  @SuppressWarnings("unchecked")
  public static void loadConceptMappings() {

    String filename = "concept_mappings.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      HashMap<String, Map<String, ?>> gson = g.fromJson(json, HashMap.class);
      for (Entry<String, ?> entry : gson.entrySet()) {
        String type = entry.getKey();
        Map<String, ?> value = (Map<String, ?>) entry.getValue();
        for (Map.Entry<String, ?> o : value.entrySet()) {
          String syntheaCode = o.getKey();
          Map<String, ?> conceptInfo = (Map<String, ?>) o.getValue();
          BillingConcept cncpt = new BillingConcept(type, conceptInfo);
          conceptHash.put(syntheaCode, cncpt);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  public static BillingConcept getConcept(String code) {
    return conceptHash.get(code);
  }

  public String getType() {
    return type;
  }
  
  public String getHcpcCode() {
    return hcpc;
  }

  public String getDescription() {
    return description;
  }

  public String getICDCode() {
    return icd;
  }

  public String getCost() {
    return cost;
  }
}
