package org.mitre.synthea.world.concepts;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mitre.synthea.TestHelper.SNOMED_URI;
import static org.mitre.synthea.TestHelper.getR4FhirContext;
import static org.mitre.synthea.TestHelper.getTxRecordingSource;
import static org.mitre.synthea.TestHelper.isHttpRecordingEnabled;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.helpers.TerminologyClient;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class CodeTest {

  private static final int SEED = 1234;
  private static final String VALUE_SET_URI = SNOMED_URI + "?fhir_vs=ecl/<<254632001";

  @Rule
  public WireMockRule mockTerminologyService = new WireMockRule(options()
      .usingFilesUnderDirectory("src/test/resources/wiremock/CodeTest"));

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
  public void materialize() {
    Code code = valueSetCode();
    Code materializedCode = code.materialize(SEED);

    assertEquals(SNOMED_URI, materializedCode.system);
    assertEquals("254633006", materializedCode.code);
    assertEquals("Oat cell carcinoma of lung", materializedCode.display);
    assertNull(materializedCode.valueSet);
  }

  @Test
  public void doNothingIfAlreadyMaterialized() {
    Code code = materializedCode();
    code.materialize(SEED);
    verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  public void toStringWorksIfMaterialized() {
    Code code = materializedCode();
    assertEquals("http://snomed.info/sct 254641006 Histiocytoma of lung", code.toString());
  }

  @Test
  public void toStringWorksIfNotMaterialized() {
    Code code = valueSetCode();
    assertEquals(VALUE_SET_URI, code.toString());
  }

  private static Code valueSetCode() {
    String json = "{ \"value_set\": \"" + VALUE_SET_URI + "\" }";
    return Utilities.getGson().fromJson(json, Code.class);
  }

  private static Code materializedCode() {
    String json = "{ \"system\": \"" + SNOMED_URI
        + "\", \"code\": \"254641006\", \"display\": \"Histiocytoma of lung\" }";
    return Utilities.getGson().fromJson(json, Code.class);
  }

  @After
  public void tearDown() {
    if (isHttpRecordingEnabled()) {
      WireMock.stopRecording();
    }
  }

}