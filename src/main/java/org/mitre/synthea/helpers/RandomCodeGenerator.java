package org.mitre.synthea.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates random codes based upon ValueSet URIs, with the help of a FHIR
 * terminology service API.
 *
 *
 * <p>The URL for the terminology service is configured using the
 * <code>generate.terminology_service_url</code> property.
 */
public abstract class RandomCodeGenerator {

  public static String expandBaseUrl = Config.get("generate.terminology_service_url")
      + "/ValueSet/$expand?url=";
  private static final Logger logger = LoggerFactory.getLogger(RandomCodeGenerator.class);
  public static Map<String, List<Object>> codeListCache = new HashMap<>();
  public static List<Code> selectedCodes = new ArrayList<>();
  private static UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_2_SLASHES);
  private static OkHttpClient client = new OkHttpClient();

  /**
   * Gets a random code from the expansion of a ValueSet.
   *
   * @param valueSetUri
   *          the URI of the ValueSet
   * @param seed
   *          a random seed to ensure reproducibility of this result
   * @return the randomly selected Code
   */
  public static Code getCode(String valueSetUri, long seed, Code code) {
    if (urlValidator.isValid(valueSetUri)) {
      Map<String, String> codeMap = getCodeAsMap(valueSetUri, seed);
      validateCode(codeMap);
      Code newCode = new Code(codeMap.get("system"), codeMap.get("code"), codeMap.get("display"));
      selectedCodes.add(newCode);
      return newCode;
    }
    return code;
  }

  /**
   * Gets a random code from the expansion of a ValueSet, represented as a Map.
   *
   * @param valueSetUri
   *          the URI of the ValueSet
   * @param seed
   *          a random seed to ensure reproducibility of this result
   * @return the randomly selected code as a Map of Strings
   */
  @SuppressWarnings("unchecked")
  public static Map<String, String> getCodeAsMap(String valueSetUri, long seed) {
    if (urlValidator.isValid(valueSetUri)) {
      expandValueSet(valueSetUri);
      List<Object> codes = codeListCache.get(valueSetUri);
      int randomIndex = new Random(seed).nextInt(codes.size());
      Map<String, String> codeMap = (Map<String, String>) codes.get(randomIndex);
      validateCode(codeMap);
      return codeMap;
    }
    return null;
  }

  /**
   * Check whether the given code is in the given ValueSet.
   *
   * @param code Code to check
   * @param valueSetUri URI of the ValueSet to check the code for
   * @return true if the code is in the given valueset
   */
  @SuppressWarnings("unchecked")
  // TODO: this does not belong here, but this class is where the code cache is
  public static boolean codeInValueSet(Code code, String valueSetUri) {
    if (urlValidator.isValid(valueSetUri)) {
      expandValueSet(valueSetUri);

      // TODO: there has to be a better way to do this
      Map<String,String> codeAsMap = new HashMap<>();
      codeAsMap.put("system", code.system);
      codeAsMap.put("code", code.code);
      codeAsMap.put("display", code.display);

      List<Object> cachedCodeList = codeListCache.get(valueSetUri);

      // this will only return true if everything is exactly identical
      // ie, it will not match if display is different
      if (cachedCodeList.contains(codeAsMap)) {
        return true;
      }

      // iterate through all the codes to see if it contains the system/code combo
      // TODO: pick better data structures that support this

      for (Object cachedCodeObj : cachedCodeList) {
        Map<String,String> cachedCode = (Map<String,String>)cachedCodeObj;

        if (cachedCode.get("system").equals(code.system)
            && cachedCode.get("code").equals(code.code)) {
          return true;
        }
      }
    }
    // TODO??
    return false;
  }


  private static synchronized void expandValueSet(String valueSetUri) {
    if (!codeListCache.containsKey(valueSetUri)) {
      Request request = new Request.Builder()
              .url(expandBaseUrl + valueSetUri)
              .header("Content-Type", "application/json")
              .build();
      Map<String, Object> valueSet = null;
      try {
        Response response = client.newCall(request).execute();
        ObjectMapper objectMapper = new ObjectMapper();
        ResponseBody body = response.body();
        if (body != null) {
          valueSet = objectMapper.readValue(body.byteStream(),
                  new TypeReference<Map<String, Object>>() {
            });
        } else {
          throw new RuntimeException("Value Set Expansion contained no body");
        }
      } catch (JsonProcessingException e) {
        throw new RuntimeException("JsonProcessingException while parsing valueSet response");
      } catch (IOException e) {
        throw new RuntimeException("Issue when expanding the value set", e);
      }

      loadValueSet(valueSetUri, valueSet);
    }
  }

  /**
   * Load the given value set into our cache.
   * @param valueSetUri URI to reference this value set
   * @param valueSet Parsed JSON representation of FHIR valueset
   */
  @SuppressWarnings("unchecked")
  public static void loadValueSet(String valueSetUri, Map<String, Object> valueSet) {
    if (valueSetUri == null) {
      valueSetUri = (String)valueSet.get("url");
    }

    if (valueSetUri != null && !codeListCache.containsKey(valueSetUri)) {
      Map<String, Object> expansion = (Map<String, Object>) valueSet.get("expansion");
      if (expansion != null) {
        validateExpansion(expansion);
        codeListCache.put(valueSetUri, (List<Object>) expansion.get("contains"));

      } else {
        Map<String, Object> compose  = (Map<String, Object>) valueSet.get("compose");

        if (compose == null) {
          throw new RuntimeException("ValueSet does not contain compose or expansion");
        }

        // TODO: why is this List<Object> instead of something more specific?
        // we know the contents are Map<String,String>
        List<Object> codes = new ArrayList<>();

        List<Map<String, Object>> includeList = (List<Map<String, Object>>) compose.get("include");

        for (Map<String, Object> include : includeList) {
          String system = (String)include.get("system");

          List<Map<String, Object>> conceptList =
              (List<Map<String, Object>>) include.get("concept");

          for (Map<String, Object> concept : conceptList) {
            Map<String,String> codeAsMap = new HashMap<>();
            codeAsMap.put("system", system);
            codeAsMap.put("code", (String)concept.get("code"));
            codeAsMap.put("display", (String)concept.get("display"));

            codes.add(codeAsMap);
          }


        }

        if (codes.isEmpty()) {
          throw new RuntimeException("ValueSet does not contain any codes defined within compose");
        }

        codeListCache.put(valueSetUri, codes);


      }
      System.out.println("Loaded " + valueSetUri);
    }
  }

  private static void validateExpansion(@Nonnull Map<String, Object> expansion) {
    if (!expansion.containsKey("contains")
        || ((Collection) expansion.get("contains")).isEmpty()) {
      throw new RuntimeException("ValueSet expansion does not contain any codes");
    } else if (!expansion.containsKey("total")) {
      throw new RuntimeException("No total element in ValueSet expand result");
    }
  }

  private static void validateCode(Map<String, String> code) {
    if (StringUtils.isAnyEmpty(code.get("system"), code.get("code"), code.get("display"))) {
      throw new RuntimeException(
          "ValueSet contains element does not contain system, code and display");
    }
  }

  public static void setBaseUrl(String url) {
    expandBaseUrl = url + "/ValueSet/$expand?url=";
  }
}
