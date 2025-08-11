package org.mitre.synthea.world.geography;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Utility class for converting between state names and abbreviations and CMS provider state codes.
 * Note that this class is specific to the Centers for Medicare and Medicaid in the USA.
 */
public class CMSStateCodeMapper {

  private final Map<String, String> providerStateCodes;
  private Map<String, String> stateToAbbrev = this.buildStateAbbrevTable();
  private final Map<String, String> abbrevToState;
  private final Map<String, String> ssaTable;
  private final Map<String, Map<String, String>> ssaStateCountyNameCountyCode;
  private final Map<String, String> fipsTable;

  /**
   * Create a new instance.
   */
  public CMSStateCodeMapper() {
    this.providerStateCodes = this.buildProviderStateTable();
    this.stateToAbbrev = this.buildStateAbbrevTable();
    // support two-way conversion between state name and abbreviations
    this.abbrevToState = new HashMap<>();
    for (Map.Entry<String, String> entry : stateToAbbrev.entrySet()) {
      this.abbrevToState.put(entry.getValue(), entry.getKey());
    }
    this.ssaTable = buildSSATable();
    this.ssaStateCountyNameCountyCode = buildCountyNameLookup();
    this.fipsTable = buildFipsTable();
  }

  // support two-way conversion between state name and abbreviations

  /**
   * Return state code for a given state.
   * @param state (either state name or abbreviation)
   * @return 2-digit state code
   */
  public String getStateCode(String state) {
    if (state.length() == 2) {
      state = this.changeStateFormat(state);
    } else {
      state = this.capitalizeWords(state);
    }
    String res = this.providerStateCodes.getOrDefault(state, "NONE");
    return res;
  }

  /**
   * Switch between state name and abbreviation. If state is abbreviation, will return name,
   * and vice versa
   * @param state abbreviation or name of state
   * @return
   */
  private String changeStateFormat(String state) {
    if (state.length() == 2) {
      return this.abbrevToState.getOrDefault(state.toUpperCase(), null);
    } else {
      String stateClean = this.capitalizeWords(state.toLowerCase());
      return this.stateToAbbrev.getOrDefault(stateClean, null);
    }
  }

  private Map<String, String> buildStateAbbrevTable() {
    Map<String, String> states = new HashMap<String, String>();
    states.put("Alabama", "AL");
    states.put("Alaska", "AK");
    states.put("Alberta", "AB");
    states.put("American Samoa", "AS");
    states.put("Arizona", "AZ");
    states.put("Arkansas", "AR");
    states.put("Armed Forces (AE)", "AE");
    states.put("Armed Forces Americas", "AA");
    states.put("Armed Forces Pacific", "AP");
    states.put("British Columbia", "BC");
    states.put("California", "CA");
    states.put("Colorado", "CO");
    states.put("Connecticut", "CT");
    states.put("Delaware", "DE");
    states.put("District of Columbia", "DC");
    states.put("Florida", "FL");
    states.put("Georgia", "GA");
    states.put("Guam", "GU");
    states.put("Hawaii", "HI");
    states.put("Idaho", "ID");
    states.put("Illinois", "IL");
    states.put("Indiana", "IN");
    states.put("Iowa", "IA");
    states.put("Kansas", "KS");
    states.put("Kentucky", "KY");
    states.put("Louisiana", "LA");
    states.put("Maine", "ME");
    states.put("Manitoba", "MB");
    states.put("Maryland", "MD");
    states.put("Massachusetts", "MA");
    states.put("Michigan", "MI");
    states.put("Minnesota", "MN");
    states.put("Mississippi", "MS");
    states.put("Missouri", "MO");
    states.put("Montana", "MT");
    states.put("Nebraska", "NE");
    states.put("Nevada", "NV");
    states.put("New Brunswick", "NB");
    states.put("New Hampshire", "NH");
    states.put("New Jersey", "NJ");
    states.put("New Mexico", "NM");
    states.put("New York", "NY");
    states.put("Newfoundland", "NF");
    states.put("North Carolina", "NC");
    states.put("North Dakota", "ND");
    states.put("Northwest Territories", "NT");
    states.put("Nova Scotia", "NS");
    states.put("Nunavut", "NU");
    states.put("Ohio", "OH");
    states.put("Oklahoma", "OK");
    states.put("Ontario", "ON");
    states.put("Oregon", "OR");
    states.put("Pennsylvania", "PA");
    states.put("Prince Edward Island", "PE");
    states.put("Puerto Rico", "PR");
    states.put("Quebec", "QC");
    states.put("Rhode Island", "RI");
    states.put("Saskatchewan", "SK");
    states.put("South Carolina", "SC");
    states.put("South Dakota", "SD");
    states.put("Tennessee", "TN");
    states.put("Texas", "TX");
    states.put("Utah", "UT");
    states.put("Vermont", "VT");
    states.put("Virgin Islands", "VI");
    states.put("Virginia", "VA");
    states.put("Washington", "WA");
    states.put("West Virginia", "WV");
    states.put("Wisconsin", "WI");
    states.put("Wyoming", "WY");
    states.put("Yukon Territory", "YT");
    return states;
  }

  private HashMap<String, String> buildProviderStateTable() {
    HashMap<String, String> providerStateCode = new HashMap<String, String>();
    providerStateCode.put("Alabama", "01");
    providerStateCode.put("Alaska", "02");
    providerStateCode.put("Arizona", "03");
    providerStateCode.put("Arkansas", "04");
    providerStateCode.put("California", "05");
    providerStateCode.put("Colorado", "06");
    providerStateCode.put("Connecticut", "07");
    providerStateCode.put("Delaware", "08");
    providerStateCode.put("District of Columbia", "09");
    providerStateCode.put("Florida", "10");
    providerStateCode.put("Georgia", "11");
    providerStateCode.put("Hawaii", "12");
    providerStateCode.put("Idaho", "13");
    providerStateCode.put("Illinois", "14");
    providerStateCode.put("Indiana", "15");
    providerStateCode.put("Iowa", "16");
    providerStateCode.put("Kansas", "17");
    providerStateCode.put("Kentucky", "18");
    providerStateCode.put("Louisiana", "19");
    providerStateCode.put("Maine", "20");
    providerStateCode.put("Maryland", "21");
    providerStateCode.put("Massachusetts", "22");
    providerStateCode.put("Michigan", "23");
    providerStateCode.put("Minnesota", "24");
    providerStateCode.put("Mississippi", "25");
    providerStateCode.put("Missouri", "26");
    providerStateCode.put("Montana", "27");
    providerStateCode.put("Nebraska", "28");
    providerStateCode.put("Nevada", "29");
    providerStateCode.put("New Hampshire", "30");
    providerStateCode.put("New Jersey", "31");
    providerStateCode.put("New Mexico", "32");
    providerStateCode.put("New York", "33");
    providerStateCode.put("North Carolina", "34");
    providerStateCode.put("North Dakota", "35");
    providerStateCode.put("Ohio", "36");
    providerStateCode.put("Oklahoma", "37");
    providerStateCode.put("Oregon", "38");
    providerStateCode.put("Pennsylvania", "39");
    providerStateCode.put("Puerto Rico", "40");
    providerStateCode.put("Rhode Island", "41");
    providerStateCode.put("South Carolina", "42");
    providerStateCode.put("South Dakota", "43");
    providerStateCode.put("Tennessee", "44");
    providerStateCode.put("Texas", "45");
    providerStateCode.put("Utah", "46");
    providerStateCode.put("Vermont", "47");
    providerStateCode.put("Virgin Islands", "48");
    providerStateCode.put("Virginia", "49");
    providerStateCode.put("Washington", "50");
    providerStateCode.put("West Virginia", "51");
    providerStateCode.put("Wisconsin", "52");
    providerStateCode.put("Wyoming", "53");
    providerStateCode.put("Africa", "54");
    providerStateCode.put("Asia", "55");
    providerStateCode.put("Canada & Islands", "56");
    providerStateCode.put("Central America and West Indies", "57");
    providerStateCode.put("Europe", "58");
    providerStateCode.put("Mexico", "59");
    providerStateCode.put("Oceania", "60");
    providerStateCode.put("Philippines", "61");
    providerStateCode.put("South America", "62");
    providerStateCode.put("U.S. Possessions", "63");
    providerStateCode.put("American Samoa", "64");
    providerStateCode.put("Guam", "65");
    providerStateCode.put("Commonwealth of the Northern Marianas Islands", "66");
    return providerStateCode;
  }

  /**
   * Get the SSA county code for a given zipcode.
   * @param zipcode the ZIP
   * @return The SSA county code.
   */
  public String zipToCountyCode(String zipcode) {
    return ssaTable.get(zipcode);
  }

  /**
   * Get the SSA county code for a given state and county.
   * @param state The abbreviation of the state.
   * @param countyName The name of the county.
   * @param rand Random number generator, used if county name is not found.
   * @return The SSA county code.
   */
  public String stateCountyNameToCountyCode(String state, String countyName,
      RandomNumberGenerator rand) {
    String ssaCounty = null;
    String abbrv = stateToAbbrev.get(state);
    Map<String, String> stateData = ssaStateCountyNameCountyCode.get(abbrv);
    String key = countyName.toUpperCase().replace(" COUNTY", "");
    if (stateData != null) {
      ssaCounty = stateData.get(key);
      if (ssaCounty == null) {
        // TODO ideally, we'd search by Lat/Lon and pick the closest county
        // instead, we pick a random county within the state
        int index = rand.randInt(stateData.keySet().size());
        key = (String) stateData.keySet().toArray()[index];
        ssaCounty = stateData.get(key);
      }
    }
    return ssaCounty;
  }

  private HashMap<String, String> buildSSATable() {
    HashMap<String, String> ssaTable = new HashMap<String, String>();
    List<LinkedHashMap<String, String>> csvData;
    try {
      String csv = Utilities.readResourceAndStripBOM("geography/fipscodes.csv");
      csvData = SimpleCSV.parse(csv);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (LinkedHashMap<String, String> row : csvData) {
      String zipcode = row.get("zip");
      if (zipcode.length() == 4) {
        zipcode = "0" + zipcode;
      }
      String ssaCode = row.get("ssacounty");
      ssaTable.put(zipcode, ssaCode);
    }
    return ssaTable;
  }


  private Map<String, Map<String, String>> buildCountyNameLookup() {
    Map<String, Map<String, String>> results = new HashMap<String, Map<String, String>>();
    List<LinkedHashMap<String, String>> csvData;
    try {
      String csv = Utilities.readResourceAndStripBOM("geography/fipscodes.csv");
      csvData = SimpleCSV.parse(csv);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (LinkedHashMap<String, String> row : csvData) {
      String state = row.get("state");
      if (!results.containsKey(state)) {
        results.put(state, new HashMap<String, String>());
      }
      Map<String, String> stateData = results.get(state);
      String county = row.get("county");
      if (!stateData.containsKey(county)) {
        String ssaCode = row.get("ssacounty");
        stateData.put(county, ssaCode);
      }
    }
    return results;
  }

  /**
   * Get the FIPS county code for a given zipcode.
   * @param zipcode the ZIP
   * @return fips county code
   */
  public String zipToFipsCountyCode(String zipcode) {
    // FIPS county codes are optional, so return blank if a match isn't found.
    return fipsTable.getOrDefault(zipcode, "");
  }

  private HashMap<String, String> buildFipsTable() {
    HashMap<String, String> fipsTable = new HashMap<String, String>();
    List<LinkedHashMap<String, String>> csvData;
    try {
      String csv = Utilities.readResourceAndStripBOM("geography/fipscodes.csv");
      csvData = SimpleCSV.parse(csv);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (LinkedHashMap<String, String> row : csvData) {
      String zipcode = row.get("zip");
      if (zipcode.length() == 4) {
        zipcode = "0" + zipcode;
      }
      String fipsCode = row.get("fipscounty");
      fipsTable.put(zipcode, fipsCode);
    }
    return fipsTable;
  }

  private String capitalizeWords(String str) {
    String[] words = str.split("\\s");
    String capitalizeWords = "";
    for (String w : words) {
      if (w.equalsIgnoreCase("of") || w.equalsIgnoreCase("and") || w.equalsIgnoreCase("the")) {
        capitalizeWords += w.toLowerCase() + " ";
      } else {
        String first = w.substring(0, 1);
        String afterFirst = w.substring(1);
        capitalizeWords += first.toUpperCase() + afterFirst + " ";
      }
    }
    return capitalizeWords.trim();
  }

}