package org.mitre.synthea.world.concepts;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class RelativeValueUnit {

  private static HashMap<String, RelativeValueUnit> rvuHash = 
      new HashMap<String, RelativeValueUnit>();

  private String hcpcCode;
  private String mod;
  private String status;
  private String description;
  private float workRvu;
  private float nonfacilityPeRvu;
  private float facilityPeRvu;
  private float malpracticeRvu;
  private float totalNfRvu;
  private float totalFRvu;
  private String global;

  public RelativeValueUnit(LinkedTreeMap m) {
    hcpcCode = (String) m.get("cpt1hcpcs");
    mod = (String) m.get("mod");
    status = (String) m.get("status");
    description = (String) m.get("description");
    workRvu = Float.parseFloat((String) m.get("work_rvus"));
    malpracticeRvu = Float.parseFloat((String) m.get("malpractice_rvus"));
    global = (String) m.get("global");

    // for facility NA values
    try {
      facilityPeRvu = Float.parseFloat((String) m.get("facility_pe_rvus"));
      totalFRvu = Float.parseFloat((String) m.get("total_facility_rvus"));
    } catch (Exception e) {
      facilityPeRvu = 0;
      totalFRvu = 0;
    }
    // for non facility NA values
    try {
      nonfacilityPeRvu = Float.parseFloat((String) m.get("nonfacility_pe_rvus"));
      totalNfRvu = Float.parseFloat((String) m.get("total_nonfacility_rvus"));
    } catch (Exception e) {
      nonfacilityPeRvu = 0;
      totalNfRvu = 0;
    }
  }

  public static void clear() {
    rvuHash.clear();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void loadRVUs() {

    String filename = "/relative_value_units.json";
    try {
      InputStream stream = RelativeValueUnit.class.getResourceAsStream(filename);
      // read all text into a string
      String json = new BufferedReader(new InputStreamReader(stream)).lines().parallel()
          .collect(Collectors.joining("\n"));
      Gson g = new Gson();
      LinkedTreeMap<String, LinkedTreeMap> gson = g.fromJson(json, LinkedTreeMap.class);
      for (Entry<String, LinkedTreeMap> entry : gson.entrySet()) {
        String hcpc = entry.getKey();
        LinkedTreeMap value = entry.getValue();
        RelativeValueUnit rvu = new RelativeValueUnit(value);
        rvuHash.put(hcpc, rvu);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  public static RelativeValueUnit getRvu(String hcpcCode) {
    return rvuHash.get(hcpcCode);
  }

  public double getFacilityPeRvu() {
    return facilityPeRvu;
  }

  public void setFacilityPeRvu(float facilityPeRvu) {
    this.facilityPeRvu = facilityPeRvu;
  }

  public static HashMap<String, RelativeValueUnit> getRvuHash() {
    return rvuHash;
  }

  public String getHcpcCode() {
    return hcpcCode;
  }

  public String getMod() {
    return mod;
  }

  public String getStatus() {
    return status;
  }

  public String getDescription() {
    return description;
  }

  public double getWorkRvu() {
    return workRvu;
  }

  public double getNonfacilityPeRvu() {
    return nonfacilityPeRvu;
  }

  public double getMalpracticeRvu() {
    return malpracticeRvu;
  }

  public double getTotalNfRvu() {
    return totalNfRvu;
  }

  public double getTotalFRvu() {
    return totalFRvu;
  }

  public String getGlobal() {
    return global;
  }
}
