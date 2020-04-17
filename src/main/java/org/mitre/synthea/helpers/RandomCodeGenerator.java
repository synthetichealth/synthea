package org.mitre.synthea.helpers;

import ca.uhn.fhir.context.FhirContext;
import java.util.Random;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates random codes based upon ValueSet URIs, with the help of a FHIR terminology service
 * API.
 * <p>
 * The URL for the terminology service is configured using the <code>generate.terminology_service_url</code>
 * property.
 */
public abstract class RandomCodeGenerator {

  public static final int EXPAND_PAGE_SIZE = 1000;
  
  private static final Logger logger = LoggerFactory.getLogger(RandomCodeGenerator.class);
  private static TerminologyClient terminologyClient = null;

  static {
    String terminologyServiceUrl = Config.get("generate.terminology_service_url");
    initialize(terminologyServiceUrl);
  }

  /**
   * Initialize the RandomCodeGenerator with the supplied terminology service URL.
   *
   * @param terminologyServiceUrl the URL of the FHIR terminology service to be used
   */
  public static void initialize(String terminologyServiceUrl) {
    if (terminologyServiceUrl != null && !terminologyServiceUrl.isEmpty()) {
      terminologyClient = FhirContext.forR4()
          .newRestfulClient(TerminologyClient.class, terminologyServiceUrl);
    }
  }

  /**
   * Initialize the RandomCodeGenerator with the supplied TerminologyClient.
   *
   * @param terminologyClient the TerminologyClient to be used
   */
  public static void initialize(TerminologyClient terminologyClient) {
    RandomCodeGenerator.terminologyClient = terminologyClient;
  }

  /**
   * Clear the terminology service configuration.
   */
  public static void reset() {
    RandomCodeGenerator.terminologyClient = null;
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
    ValueSetExpansionComponent expansion = expandValueSet(valueSetUri);

    Random random = new Random(seed);
    int randomIndex = random.nextInt(expansion.getTotal());

    ValueSetExpansionContainsComponent contains = expansion.getContains().get(randomIndex);
    validateContains(contains);

    return new Code(contains.getSystem(), contains.getCode(), contains.getDisplay());
  }

  private static ValueSetExpansionComponent expandValueSet(String valueSetUri) {
    ValueSet response;
    ValueSetExpansionComponent result = new ValueSetExpansionComponent();
    int total, offset = 0, count = 0;

    do {
      offset += count;
      logger.info("Sending ValueSet expand request to terminology service (" + terminologyClient
          .getServerBase() + "): url=" + valueSetUri + ", count=" + EXPAND_PAGE_SIZE + ", offset="
          + offset);
      response = terminologyClient
          .expand(new UriType(valueSetUri), new IntegerType(EXPAND_PAGE_SIZE),
              new IntegerType(offset));
      validateExpansion(response.getExpansion());
      total = response.getExpansion().getTotal();
      count = response.getExpansion().getContains().size();
      result.getContains().addAll(response.getExpansion().getContains());
    } while ((offset + count) < total);
    result.setTotal(total);

    return result;
  }

  private static void validateExpansion(ValueSetExpansionComponent expansion) {
    if (expansion.isEmpty()) {
      throw new RuntimeException("No expansion present in ValueSet expand result");
    } else if (expansion.getTotalElement().isEmpty()) {
      throw new RuntimeException("No total element in ValueSet expand result");
    } else if (expansion.getContains().isEmpty()) {
      throw new RuntimeException("ValueSet expansion does not contain any codes");
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
