package org.mitre.synthea.helpers;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mitre.synthea.TestHelper.SNOMED_URI;
import static org.mitre.synthea.TestHelper.fhirResponse;
import static org.mitre.synthea.TestHelper.getR4FhirContext;
import static org.mitre.synthea.TestHelper.getTxRecordingSource;
import static org.mitre.synthea.TestHelper.isHttpRecordingEnabled;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.io.IOException;
import java.util.UUID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class RandomCodeGeneratorTest {

  private static final int SEED = 1234;
  private static final String EXPAND_STUB_ID = "1b134e58-aabf-4b5c-baf7-cce2c4f593c7";
  private static final String VALUE_SET_URI = SNOMED_URI + "?fhir_vs=ecl/<<131148009";

  @Rule
  public WireMockRule mockTerminologyService = new WireMockRule(options()
      .usingFilesUnderDirectory("src/test/resources/wiremock/RandomCodeGeneratorTest"));

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    TerminologyClient terminologyClient = getR4FhirContext()
        .newRestfulClient(TerminologyClient.class, "http://localhost:8080/fhir");
    RandomCodeGenerator.initialize(terminologyClient);
    if (isHttpRecordingEnabled()) {
      WireMock.startRecording(getTxRecordingSource());
    }
  }

  @Test
  public void getCode() {
    Code code = RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
    
    Assert.assertEquals(SNOMED_URI, code.system);
    Assert.assertEquals("403393000", code.code);
    Assert.assertEquals("Stellate pseudoscar in senile purpura", code.display);
  }

  @Test
  public void throwsWhenNotConfigured() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage(
        "Unable to generate code from ValueSet URI: terminology service not configured");
    
    RandomCodeGenerator.reset();
    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test
  public void throwsWhenNoExpansion() throws IOException {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("No expansion present in ValueSet expand result");
    editStubBody("noExpansion.ValueSet.json");

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test
  public void throwsWhenNoTotal() throws IOException {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("No total element in ValueSet expand result");
    editStubBody("noTotal.ValueSet.json");

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test
  public void throwsWhenNoContains() throws IOException {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("ValueSet expansion does not contain any codes");
    editStubBody("noContains.ValueSet.json");

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @Test
  public void throwsWhenMissingCodeElements() throws IOException {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("ValueSet contains element does not contain system, code and display");
    editStubBody("missingCodeElements.ValueSet.json");

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED);
  }

  @After
  public void tearDown() {
    if (isHttpRecordingEnabled()) {
      WireMock.stopRecording();
    }
  }

  private void editStubBody(String body) {
    StubMapping stub = mockTerminologyService.getSingleStubMapping(UUID.fromString(EXPAND_STUB_ID));
    stub.setResponse(fhirResponse().withBodyFile(body).build());
    stub.setPersistent(false);
    mockTerminologyService.editStubMapping(stub);
  }
  
}
