package org.mitre.synthea.helpers;

import ca.uhn.fhir.parser.IParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

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
  public static Map<String, List<Code>> codeListCache = new HashMap<>();
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
      Code newCode = getCode(valueSetUri, seed);
      if (newCode == null) {
        return code;
      }
      validateCode(newCode);
      selectedCodes.add(newCode);
      return newCode;
    }
    return code;
  }

  /**
   * Gets a random code from the expansion of a ValueSet.
   *
   * @param valueSetUri
   *          the URI of the ValueSet
   * @param seed
   *          a random seed to ensure reproducibility of this result
   * @return the randomly selected code
   */
  public static Code getCode(String valueSetUri, long seed) {
    if (urlValidator.isValid(valueSetUri)) {
      expandValueSet(valueSetUri);
      List<Code> codes = codeListCache.get(valueSetUri);
      int randomIndex = new Random(seed).nextInt(codes.size());
      Code code = codes.get(randomIndex);
      validateCode(code);
      return code;
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
  // TODO: this does not belong here, but this class is where the code cache is
  public static boolean codeInValueSet(Code code, String valueSetUri) {
    if (urlValidator.isValid(valueSetUri)) {
      expandValueSet(valueSetUri);
      List<Code> cachedCodeList = codeListCache.get(valueSetUri);
      return cachedCodeList.contains(code);
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
      try {
        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();
        if (body != null) {
          IParser parser = FhirR4.getContext().newJsonParser();
          ValueSet valueSet = (ValueSet) parser.parseResource(body.charStream());
          loadValueSet(valueSetUri, valueSet);
        } else {
          throw new RuntimeException("Value Set Expansion contained no body");
        }
      } catch (IOException e) {
        throw new RuntimeException("Issue when expanding the value set", e);
      }
    }
  }

  /**
   * Load the given value set into our cache.
   * @param valueSetUri URI to reference this value set
   * @param valueSet Parsed JSON representation of FHIR valueset
   */
  public static void loadValueSet(String valueSetUri, ValueSet valueSet) {
    if (valueSetUri == null) {
      valueSetUri = valueSet.getUrl();
    }

    if (valueSetUri != null && !codeListCache.containsKey(valueSetUri)) {
      if (valueSet.hasExpansion()) {
        ValueSetExpansionComponent expansion = valueSet.getExpansion();

        validateExpansion(expansion);
        List<ValueSetExpansionContainsComponent> contains = expansion.getContains();
        List<Code> containsCodes = contains.stream()
            .map(c -> new Code(c.getSystem(), c.getCode(), c.getDisplay()))
            .collect(Collectors.toList());
        codeListCache.put(valueSetUri, containsCodes);

      } else if (valueSet.hasCompose()) {
        List<Code> codes = new ArrayList<>();
        ValueSetComposeComponent compose = valueSet.getCompose();
        List<ConceptSetComponent> includeList = compose.getInclude();

        for (ConceptSetComponent include : includeList) {
          String system = include.getSystem();

          List<ConceptReferenceComponent> conceptList = include.getConcept();

          for (ConceptReferenceComponent concept : conceptList) {
            codes.add(new Code(system, concept.getCode(), concept.getDisplay()));
          }
        }

        if (codes.isEmpty()) {
          throw new RuntimeException("ValueSet does not contain any codes defined within compose");
        }

        codeListCache.put(valueSetUri, codes);
      } else {
        throw new RuntimeException("ValueSet does not contain compose or expansion");
      }
      System.out.println("Loaded " + valueSetUri);
    }
  }

  private static void validateExpansion(@Nonnull ValueSetExpansionComponent expansion) {
    if (expansion.getContains() == null
        || expansion.getContains().isEmpty()) {
      throw new RuntimeException("ValueSet expansion does not contain any codes");
    } else if (expansion.getTotal() == 0) {
      throw new RuntimeException("No total element in ValueSet expand result");
    }
  }

  private static void validateCode(Code code) {
    if (StringUtils.isAnyEmpty(code.system, code.code, code.display)) {
      throw new RuntimeException(
          "ValueSet contains element does not contain system, code and display");
    }
  }

  public static void setBaseUrl(String url) {
    expandBaseUrl = url + "/ValueSet/$expand?url=";
  }
}
