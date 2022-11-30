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
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;

/**
 * Utility class for dealing with code mapping configuration writers.
 */
class CodeMapper {

  private static boolean requireCodeMaps = Config.getAsBoolean("exporter.bfd.require_code_maps",
          true);
  private HashMap<String, List<Map<String, String>>> map;
  private boolean mapImported = false;

  /**
   * Create a new CodeMapper for the supplied JSON string.
   * @param jsonMap a stringified JSON mapping writer. Expects the following format:
   * <pre>
   * {
   *   "synthea_code": [ # each synthea code will be mapped to one of the codes in this array
   *     {
   *       "code": "BFD_code",
   *       "description": "Description of code", # optional
   *       "other field": "value of other field" # optional additional fields
   *     }
   *   ]
   * }
   * </pre>
   */
  public CodeMapper(String jsonMap) {
    try {
      String json = Utilities.readResource(jsonMap);
      Gson g = new Gson();
      Type type = new TypeToken<HashMap<String, List<Map<String, String>>>>() {
      }.getType();
      map = g.fromJson(json, type);
      mapImported = true;
    } catch (JsonSyntaxException | IOException | IllegalArgumentException e) {
      if (requireCodeMaps) {
        throw new MissingResourceException("Unable to read code map file: " + jsonMap,
                "CodeMapper", jsonMap);
      } else {
        // For testing, the mapping writer is not present.
        System.out.println("BB2Exporter is running without " + jsonMap);
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
    List<Map<String, String>> options = map.get(codeToMap);
    int choice = rand.randInt(options.size());
    String code = options.get(choice).get(bfdCodeType);
    if (stripDots) {
      return code.replaceAll("\\.", "");
    } else {
      return code;
    }
  }
}
