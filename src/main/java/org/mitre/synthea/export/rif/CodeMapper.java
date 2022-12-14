package org.mitre.synthea.export.rif;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;

/**
 * Utility class for dealing with code mapping configuration writers.
 */
class CodeMapper {

  private final boolean requireCodeMaps; // initialize in ctor to simplify unit testing
  private static final String WEIGHT_KEY = "weight";
  private HashMap<String, RandomCollection<Map<String, String>>> map;
  private boolean mapImported = false;

  /**
   * Create a new CodeMapper for the supplied JSON string.
   * @param jsonMapResource resource path to a JSON mapping file. Expects the following format:
   * <pre>
   * {
   *   "synthea_code": [ # each synthea code will be mapped to one of the codes in this array
   *     {
   *       "code": "BFD_code",
   *       "description": "Description of code", # optional
   *       "weight": "floating point value", # weighting used when selecting a code randomly
   *       "other field": "value of other field" # optional additional fields
   *     }
   *   ]
   * }
   * </pre>
   */
  public CodeMapper(String jsonMapResource) {
    requireCodeMaps = Config.getAsBoolean("exporter.bfd.require_code_maps", true);
    try {
      // deserialize the JSON code map
      String jsonStr = Utilities.readResource(jsonMapResource);
      Gson g = new Gson();
      Type type = new TypeToken<HashMap<String, List<Map<String, String>>>>() {
      }.getType();
      HashMap<String, List<Map<String, String>>> jsonMap = g.fromJson(jsonStr, type);

      // use the deserialized JSON code map to populate the weighted code map
      map = new HashMap<String, RandomCollection<Map<String, String>>>();
      jsonMap.forEach((syntheaCode, bfdCodeList) -> {
        RandomCollection<Map<String, String>> collection = new RandomCollection<>();
        bfdCodeList.forEach((codeEntry) -> {
          Double weight = 1.0;
          if (codeEntry.containsKey(WEIGHT_KEY)) {
            weight = Double.parseDouble(codeEntry.get(WEIGHT_KEY));
          }
          collection.add(weight, codeEntry);
        });
        map.put(syntheaCode, collection);
      });
      mapImported = true;
    } catch (JsonSyntaxException | IOException | IllegalArgumentException e) {
      if (requireCodeMaps) {
        throw new MissingResourceException("Unable to read code map file: " + jsonMapResource,
                "CodeMapper", jsonMapResource);
      } else {
        // For testing, the mapping writer is not present.
        System.out.println("BB2Exporter is running without " + jsonMapResource);
      }
    }
  }

  /**
   * Determines whether this mapper has an entry for the supplied code.
   * @param codeToMap the Synthea code to look for
   * @return true if the Synthea code can be mapped to BFD, false if not
   */
  public boolean canMap(String codeToMap) {
    if (map == null) {
      return false;
    }
    return map.containsKey(codeToMap);
  }

  /**
   * Determines whether this mapper was successfully configured with a code map.
   * @return true if configured, false otherwise.
   */
  public boolean hasMap() {
    return mapImported;
  }

  /**
   * Get one of the BFD codes for the supplied Synthea code. Equivalent to
   * {@code map(codeToMap, "code", rand)}.
   * @param codeToMap the Synthea code to look for
   * @param rand a source of random numbers used to pick one of the list of BFD codes
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(String codeToMap, RandomNumberGenerator rand) {
    return map(codeToMap, "code", rand);
  }

  /**
   * Get one of the BFD codes for the supplied Synthea code. Equivalent to
   * {@code map(codeToMap, "code", rand)}.
   * @param codeToMap the Synthea code to look for
   * @param rand a source of random numbers used to pick one of the list of BFD codes
   * @param stripDots whether to remove dots in codes (e.g. J39.45 -> J3945)
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(String codeToMap, RandomNumberGenerator rand, boolean stripDots) {
    return map(codeToMap, "code", rand, stripDots);
  }

  /**
   * Get one of the BFD codes for the supplied Synthea code.
   * @param codeToMap the Synthea code to look for
   * @param bfdCodeType the type of BFD code to map to
   * @param rand a source of random numbers used to pick one of the list of BFD codes
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(String codeToMap, String bfdCodeType, RandomNumberGenerator rand) {
    return map(codeToMap, bfdCodeType, rand, false);
  }

  /**
   * Get one of the BFD codes for the supplied Synthea code.
   * @param codeToMap the Synthea code to look for
   * @param bfdCodeType the type of BFD code to map to
   * @param rand a source of random numbers used to pick one of the list of BFD codes
   * @param stripDots whether to remove dots in codes (e.g. J39.45 -> J3945)
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(String codeToMap, String bfdCodeType, RandomNumberGenerator rand,
          boolean stripDots) {
    if (!canMap(codeToMap)) {
      return null;
    }
    RandomCollection<Map<String, String>> options = map.get(codeToMap);
    String code = options.next(rand).get(bfdCodeType);
    if (stripDots) {
      return code.replaceAll("\\.", "");
    } else {
      return code;
    }
  }
}
