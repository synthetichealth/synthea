package org.mitre.synthea.helpers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.UriParam;
import java.util.Random;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

/**
 * Generates random codes based upon ValueSet URIs, with the help of a FHIR terminology service
 * API.
 * <p>
 * The URL for the terminology service is configured using the <pre>generate.terminology_service_url</pre>
 * property.
 */
public abstract class RandomCodeGenerator {

  private static TerminologyClient terminologyClient = null;

  static {
    String terminologyServiceUrl = Config.get("generate.terminology_service_url");
    if (terminologyServiceUrl != null && !terminologyServiceUrl.isEmpty()) {
      terminologyClient = FhirContext.forR4()
          .newRestfulClient(TerminologyClient.class, terminologyServiceUrl);
    }
  }

  /**
   * Gets a random code from the expansion of a ValueSet.
   *
   * @param valueSetUri the URI of the ValueSet
   * @param seed a random seed to ensure reproducibility of this result
   * @return the randomly selected Code
   */
  public static Code getCode(String valueSetUri, long seed) {
    if (terminologyClient == null) {
      throw new RuntimeException(
          "Unable to generate code from ValueSet URI: terminology service not configured");
    }
    ValueSet result = terminologyClient.expand(new UriParam(valueSetUri));

    ValueSetExpansionComponent expansion = result.getExpansion();
    validateExpansion(expansion);

    Random random = new Random(seed);
    int randomIndex = random.nextInt(expansion.getTotal());

    ValueSetExpansionContainsComponent contains = expansion.getContains().get(randomIndex);
    validateContains(contains);

    return new Code(contains.getSystem(), contains.getCode(), contains.getDisplay());
  }

  private static void validateExpansion(ValueSetExpansionComponent expansion) {
    if (expansion == null) {
      throw new RuntimeException("No expansion present in ValueSet expand result");
    } else if (expansion.getTotalElement() == null) {
      throw new RuntimeException("No total element in ValueSet expand result");
    } else if (expansion.getContains().isEmpty()) {
      throw new RuntimeException("ValueSet expansion is empty");
    }
  }

  private static void validateContains(ValueSetExpansionContainsComponent contains) {
    if (contains.getSystem() == null || contains.getCode() == null
        || contains.getDisplay() == null) {
      throw new RuntimeException(
          "ValueSet contains element does not contain system, code and display");
    }
  }

}
