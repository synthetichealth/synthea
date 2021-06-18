package org.mitre.synthea.world.concepts;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RaceAndEthnicity {
  public static final Map<String, String> LOOK_UP_CDC_RACE = createCDCRaceLookup();
  public static final Map<String, String> LOOK_UP_CDC_ETHNICITY_CODE = createCDCEthnicityLookup();
  public static final Map<String, String> LOOK_UP_CDC_ETHNICITY_DISPLAY = 
      createCDCEthnicityDisplayLookup();

  private static Map<String, String> createCDCRaceLookup() {
    Map<String, String> result = new HashMap<String, String>();
    result.put("white", "2106-3"); // white
    result.put("hispanic", "2106-3"); // hispanic
    result.put("black", "2054-5"); // black
    result.put("asian", "2028-9"); // asian
    result.put("native", "1002-5"); // american indian or alaska native
    result.put("hawaiian", "2076-8"); // native hawaiian or pacific islander
    result.put("other", "2131-1"); // other
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, String> createCDCEthnicityLookup() {
    Map<String, String> result = new HashMap<String, String>();
    result.put("white", "2186-5"); // non-hispanic
    result.put("hispanic", "2135-2"); // hispanic
    result.put("black", "2186-5"); // non-hispanic
    result.put("asian", "2186-5"); // non-hispanic
    result.put("native", "2186-5"); // non-hispanic
    result.put("hawaiian", "2186-5"); // non-hispanic
    result.put("other", "2186-5"); // non-hispanic
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, String> createCDCEthnicityDisplayLookup() {
    Map<String, String> result = new HashMap<String, String>();
    result.put("white", "non-hispanic");
    result.put("hispanic", "hispanic");
    result.put("black", "non-hispanic");
    result.put("asian", "non-hispanic");
    result.put("native", "non-hispanic");
    result.put("hawaiian", "non-hispanic");
    result.put("other", "non-hispanic");
    return Collections.unmodifiableMap(result);
  }
}
