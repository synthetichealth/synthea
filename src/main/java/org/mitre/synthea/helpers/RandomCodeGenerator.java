package org.mitre.synthea.helpers;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
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
 *
 * <p>The URL for the terminology service is configured using the
 * <code>generate.terminology_service_url</code> property.
 */
public abstract class RandomCodeGenerator {

  public static final int EXPAND_PAGE_SIZE = 1000;
  private static final int RESPONSE_CACHE_SIZE = 100;

  private static final Logger logger = LoggerFactory.getLogger(RandomCodeGenerator.class);
  private static TerminologyClient terminologyClient = null;
  private static LoadingCache<ExpandInput, ValueSet> responseCache;

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
      initializeCache();
    }
  }

  /**
   * Initialize the RandomCodeGenerator with the supplied TerminologyClient.
   *
   * @param terminologyClient the TerminologyClient to be used
   */
  public static void initialize(TerminologyClient terminologyClient) {
    RandomCodeGenerator.terminologyClient = terminologyClient;
    initializeCache();
  }

  private static void initializeCache() {
    responseCache = CacheBuilder.newBuilder()
        .maximumSize(RESPONSE_CACHE_SIZE)
        .build(
            new CacheLoader<ExpandInput, ValueSet>() {
              @Override
              public ValueSet load(@Nonnull ExpandInput key) {
                return terminologyClient
                    .expand(new UriType(key.getValueSetUri()), new IntegerType(EXPAND_PAGE_SIZE),
                        new IntegerType(key.getOffset()));
              }
            }
        );
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
    int total;
    int offset = 0;
    int count = 0;

    do {
      offset += count;
      logger.info("Sending ValueSet expand request to terminology service (" + terminologyClient
          .getServerBase() + "): url=" + valueSetUri + ", count=" + EXPAND_PAGE_SIZE + ", offset="
          + offset);
      try {
        response = responseCache.get(new ExpandInput(valueSetUri, offset));
      } catch (ExecutionException e) {
        throw new RuntimeException("Error expanding ValueSet", e);
      }
      validateExpansion(response.getExpansion());
      total = response.getExpansion().getTotal();
      count = response.getExpansion().getContains().size();
      result.getContains().addAll(response.getExpansion().getContains());
    } while ((offset + count) < total);
    result.setTotal(total);

    return result;
  }

  private static void validateExpansion(@Nonnull ValueSetExpansionComponent expansion) {
    if (expansion.getContains().isEmpty()) {
      throw new RuntimeException("ValueSet expansion does not contain any codes");
    } else if (expansion.getTotalElement().isEmpty()) {
      throw new RuntimeException("No total element in ValueSet expand result");
    }
  }

  private static void validateContains(ValueSetExpansionContainsComponent contains) {
    if (contains.getSystem() == null || contains.getCode() == null
        || contains.getDisplay() == null) {
      throw new RuntimeException(
          "ValueSet contains element does not contain system, code and display");
    }
  }

  private static class ExpandInput {

    @Nonnull
    private final String valueSetUri;
    private final int offset;

    public ExpandInput(@Nonnull String valueSetUri, int offset) {
      this.valueSetUri = valueSetUri;
      this.offset = offset;
    }

    @Nonnull
    public String getValueSetUri() {
      return valueSetUri;
    }

    public int getOffset() {
      return offset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ExpandInput that = (ExpandInput) o;
      return offset == that.offset &&
          valueSetUri.equals(that.valueSetUri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(valueSetUri, offset);
    }

  }
}
