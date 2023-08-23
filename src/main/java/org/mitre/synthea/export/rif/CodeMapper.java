package org.mitre.synthea.export.rif;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.apache.commons.io.FilenameUtils;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCollection;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

/**
 * Utility class for dealing with code mapping configuration writers.
 */
class CodeMapper {

  private final boolean requireCodeMaps; // initialize in ctor to simplify unit testing
  private static final String WEIGHT_KEY = "weight";
  private HashMap<String, RandomCollection<Map<String, String>>> map;
  private boolean mapImported = false;
  private ConcurrentHashMap<Code, LongAdder> missingCodes;
  private String mapName;

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
    missingCodes = new ConcurrentHashMap<>();
    mapName = FilenameUtils.getBaseName(jsonMapResource);
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
    return canMap(codeToMap, true);
  }

  /**
   * Determines whether this mapper has an entry for the supplied code.
   * @param codeToMap the Synthea code to look for
   * @return true if the Synthea code can be mapped to BFD, false if not
   */
  public boolean canMap(Code codeToMap) {
    boolean mappable = canMap(codeToMap.code, false);
    if (!mappable) {
      missingCodes.compute(codeToMap, (k, v) -> {
        if (v == null) {
          v = new LongAdder();
        }
        v.increment();
        return v;
      });
    }
    return mappable;
  }

  /**
   * Determines whether this mapper has an entry for the supplied code.
   * @param codeToMap the Synthea code to look for
   * @param logMissing whether to log missing codes or not
   * @return true if the Synthea code can be mapped to BFD, false if not
   */
  private boolean canMap(String codeToMap, boolean logMissing) {
    if (map == null) {
      return false;
    }
    boolean mappable = map.containsKey(codeToMap);
    if (!mappable && logMissing) {
      missingCodes.compute(new Code(null, codeToMap, null), (k, v) -> {
        if (v == null) {
          v = new LongAdder();
        }
        v.increment();
        return v;
      });
    }
    return mappable;
  }

  /**
   * Determines whether this mapper was successfully configured with a code map. Currently used
   * in unit tests which may be run both with and without mapping files present.
   * @return true if configured, false otherwise.
   */
  boolean hasMap() {
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
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(Code codeToMap, RandomNumberGenerator rand) {
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
   * Get one of the BFD codes for the supplied Synthea code. Equivalent to
   * {@code map(codeToMap, "code", rand)}.
   * @param codeToMap the Synthea code to look for
   * @param rand a source of random numbers used to pick one of the list of BFD codes
   * @param stripDots whether to remove dots in codes (e.g. J39.45 -> J3945)
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(Code codeToMap, RandomNumberGenerator rand, boolean stripDots) {
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
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(Code codeToMap, String bfdCodeType, RandomNumberGenerator rand) {
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

  /**
   * Get one of the BFD codes for the supplied Synthea code.
   * @param codeToMap the Synthea code to look for
   * @param bfdCodeType the type of BFD code to map to
   * @param rand a source of random numbers used to pick one of the list of BFD codes
   * @param stripDots whether to remove dots in codes (e.g. J39.45 -> J3945)
   * @return the BFD code or null if the code can't be mapped
   */
  public String map(Code codeToMap, String bfdCodeType, RandomNumberGenerator rand,
          boolean stripDots) {
    return map(codeToMap.code, bfdCodeType, rand, stripDots);
  }

  /**
   * Get the missing code as a list of maps, where each map includes the mapper name, a missing
   * code, a description, and the count of times the code was requested.
   */
  public List<? extends Map<String, String>> getMissingCodes() {
    List<Map<String, String>> missingCodeList = new ArrayList<>(missingCodes.size());
    missingCodes.forEach((code, count) -> {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("map", mapName);
      row.put("code", code.code);
      row.put("description", code.display);
      row.put("count", count.toString());
      missingCodeList.add(row);
    });
    // sort in decending order by count
    Collections.sort(missingCodeList, (o1, o2) -> {
      return (int)(Long.parseLong(o2.get("count")) - Long.parseLong(o1.get("count")));
    });
    return missingCodeList;
  }
}
