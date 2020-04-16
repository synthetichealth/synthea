package org.mitre.synthea.helpers;

import static org.mitre.synthea.TestHelper.getResourceAsStream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import junit.framework.TestCase;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class RandomCodeGeneratorTest {

  private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
  private static final String SNOMED_URI = "http://snomed.info/sct";
  private static final int SEED = 1234;
  private TerminologyClient terminologyClient;
  private static final String VALUE_SET_URI = "http://snomed.info/sct?fhir_vs=ecl/<<131148009";

  @Before
  public void setUp() {
    terminologyClient = mock(TerminologyClient.class);
    RandomCodeGenerator.initialize(terminologyClient);
  }

  @Test
  public void getCode() {
    ValueSet expansionPage1 = (ValueSet) FHIR_CONTEXT.newJsonParser().parseResource(
        getResourceAsStream("txResponses/expansionPage1.ValueSet.json"));
    ValueSet expansionPage2 = (ValueSet) FHIR_CONTEXT.newJsonParser().parseResource(
        getResourceAsStream("txResponses/expansionPage2.ValueSet.json"));
    
    when(terminologyClient.expand(argThat(uriType -> uriType.equals(VALUE_SET_URI)),
        any(IntegerType.class), any(IntegerType.class))).thenReturn(expansionPage1, expansionPage2);
    
    Code code = RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
    
    Assert.assertEquals(code.system, SNOMED_URI);
    Assert.assertEquals(code.code, "403393000");
    Assert.assertEquals(code.display, "Stellate pseudoscar in senile purpura");
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenNotConfigured() {
    RandomCodeGenerator.reset();
    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenNoExpansion() {
    ValueSet noExpansion = (ValueSet) FHIR_CONTEXT.newJsonParser()
        .parseResource(getResourceAsStream("txResponses/noExpansion.ValueSet.json"));

    when(terminologyClient.expand(argThat(uriType -> uriType.equals(VALUE_SET_URI)),
        any(IntegerType.class), any(IntegerType.class))).thenReturn(noExpansion);

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenNoTotal() {
    ValueSet noTotal = (ValueSet) FHIR_CONTEXT.newJsonParser()
        .parseResource(getResourceAsStream("txResponses/noTotal.ValueSet.json"));

    when(terminologyClient.expand(argThat(uriType -> uriType.equals(VALUE_SET_URI)),
        any(IntegerType.class), any(IntegerType.class))).thenReturn(noTotal);

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenNoContains() {
    ValueSet noContains = (ValueSet) FHIR_CONTEXT.newJsonParser()
        .parseResource(getResourceAsStream("txResponses/noContains.ValueSet.json"));

    when(terminologyClient.expand(argThat(uriType -> uriType.equals(VALUE_SET_URI)),
        any(IntegerType.class), any(IntegerType.class))).thenReturn(noContains);

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenMissingCodeElements() {
    ValueSet missingCodeElements = (ValueSet) FHIR_CONTEXT.newJsonParser()
        .parseResource(getResourceAsStream("txResponses/missingCodeElements.ValueSet.json"));

    when(terminologyClient.expand(argThat(uriType -> uriType.equals(VALUE_SET_URI)),
        any(IntegerType.class), any(IntegerType.class))).thenReturn(missingCodeElements);

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }
}