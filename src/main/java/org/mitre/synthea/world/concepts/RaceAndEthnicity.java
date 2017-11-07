package org.mitre.synthea.world.concepts;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RaceAndEthnicity {
	public static final Map<String, String> LOOK_UP = createLookup();
	public static final Map<String, String> LOOK_UP_CDC_RACE = createCDCRaceLookup();
	public static final Map<String, String> LOOK_UP_CDC_ETHNICITY_CODE = createCDCEthnicityLookup();
	public static final Map<String, String> LOOK_UP_CDC_ETHNICITY_DISPLAY = createCDCEthnicityDisplayLookup();
	
    private static Map<String, String> createLookup() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("white", "2106-3");
        result.put("hispanic", "2135-2");
        result.put("black", "2054-5");
        result.put("asian", "2028-9");
        result.put("native", "1002-5");
        result.put("other", "2131-1");
        result.put("irish", "2113-9");
        result.put("italian", "2114-7");
        result.put("english", "2110-5");
        result.put("french", "2111-3");
        result.put("german", "2112-1");
        result.put("polish", "2115-4");
        result.put("portuguese", "2131-1");
        result.put("american", "2131-1");
        result.put("french_canadian", "2131-1");
        result.put("scottish", "2116-2");
        result.put("russian", "2131-1");
        result.put("swedish", "2131-1");
        result.put("greek", "2131-1");
        result.put("puerto_rican", "2180-8");
        result.put("mexican", "2148-5");
        result.put("central_american", "2155-0");
        result.put("south_american", "2165-9");
        result.put("african", "2058-6");
        result.put("dominican", "2069-3");
        result.put("chinese", "2034-7");
        result.put("west_indian", "2075-0");
        result.put("asian_indian", "2029-7");
        result.put("american_indian", "1004-1");
        result.put("arab", "2129-5");
        result.put("nonhispanic", "2186-5");
        return Collections.unmodifiableMap(result);
    }
    
    private static Map<String, String> createCDCRaceLookup() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("white", "2106-3");		// white
        result.put("hispanic", "2106-3");	// white
        result.put("black", "2054-5");		// black
        result.put("asian", "2028-9");		// asian
        result.put("native", "1002-5");		// american indian or alaska native
        result.put("other", "2076-8");		// native hawaiian or pacific islander
        return Collections.unmodifiableMap(result);
    }
    
    private static Map<String, String> createCDCEthnicityLookup() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("white", "2186-5");		// non-hispanic
        result.put("hispanic", "2135-2");	// hispanic
        result.put("black", "2186-5");		// non-hispanic
        result.put("asian", "2186-5");		// non-hispanic
        result.put("native", "2186-5");		// non-hispanic
        result.put("other", "2186-5");		// non-hispanic
        return Collections.unmodifiableMap(result);
    }
    
    private static Map<String, String> createCDCEthnicityDisplayLookup() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("white", "non-hispanic");
        result.put("hispanic", "hispanic");
        result.put("black", "non-hispanic");
        result.put("asian", "non-hispanic");
        result.put("native", "non-hispanic");
        result.put("other", "non-hispanic");
        return Collections.unmodifiableMap(result);
    }
}
