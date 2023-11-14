package org.mitre.synthea.helpers;

import static org.junit.Assert.assertThrows;
import static org.mitre.synthea.TestHelper.SNOMED_URI;

import ca.uhn.fhir.parser.DataFormatException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RandomCodeGeneratorTest {

  private static final int SEED = 1234;
  private static final String VALUE_SET_URI = SNOMED_URI + "?fhir_vs=ecl%2F%3C%3C131148009";
  private static final String PATH = "wiremock/RandomCodeGeneratorTest/__files/";
  private final Code code = new Code("SNOMED-CT", "38341003", "Hypertension");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MockWebServer server;

  @Before
  public void setup() {
    server = new MockWebServer();
  }

  @Test
  public void getCode() throws IOException {
    prepareServer("codes.json", false);

    Code code = RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);

    Assert.assertEquals(SNOMED_URI, code.system);
    Assert.assertEquals("312858004", code.code);
    Assert.assertEquals("Neonatal tracheobronchial haemorrhage", code.display);
  }

  @Test
  public void throwsWhenNoExpansion() throws IOException {
    prepareServer("noExpansion.ValueSet.json", false);

    try {
      RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
    } catch (RuntimeException e) {
      Assert.assertEquals("ValueSet does not contain compose or expansion", e.getMessage());
      return;
    }
    Assert.fail("Should have thrown a no expansion exception");
  }

  @Test
  public void throwsWhenNoTotal() throws IOException {
    prepareServer("noTotal.ValueSet.json", false);

    try {
      RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
    } catch (RuntimeException e) {
      Assert.assertEquals("No total element in ValueSet expand result", e.getMessage());
      return;
    }
    Assert.fail("Should have thrown a no total element exception");
  }

  @Test
  public void throwsWhenNoContains() throws IOException {
    prepareServer("noContains.ValueSet.json", false);

    try {
      RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
    } catch (RuntimeException e) {
      Assert.assertEquals("ValueSet expansion does not contain any codes", e.getMessage());
      return;
    }
    Assert.fail("Should have thrown a no codes exception");
  }

  @Test
  public void throwsWhenMissingCodeElements() throws IOException {
    prepareServer("missingCodeElements.ValueSet.json", false);

    try {
      RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
    } catch (RuntimeException e) {
      Assert.assertEquals("ValueSet contains element does not contain system, code and display",
              e.getMessage());
      return;
    }
    Assert.fail("Should have thrown a missing elements exception");
  }

  @Test
  public void throwsWhenInvalidResponse() throws IOException {
    prepareServer("noExpansion.ValueSet.json", true);

    assertThrows(
        DataFormatException.class,
        () -> RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code)
    );
  }


  @Test
  public void filterCodesTest() throws IOException {
    prepareServer("codes.json", false);

    Code code = RandomCodeGenerator.getCode(VALUE_SET_URI + "&filter=tracheobronchial",
        SEED, this.code);

    Assert.assertTrue("Verify filter", code.display.contains("tracheobronchial"));
  }

  @Test
  public void invalidValueSetUrlTest() {
    Code code = RandomCodeGenerator.getCode("", SEED, this.code);

    Assert.assertEquals("SNOMED-CT", code.system);
    Assert.assertEquals("38341003", code.code);
    Assert.assertEquals("Hypertension", code.display);
  }

  @After
  public void cleanup() throws IOException {
    RandomCodeGenerator.codeListCache.clear();
    server.close();
  }

  private String getResponseToStub(String body) {
    ClassLoader classLoader = getClass().getClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(PATH.concat(body))) {
      return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void prepareServer(String fileToLoad, boolean mangle) throws IOException {
    String response = getResponseToStub(fileToLoad);
    if (mangle) {
      response = StringUtils.chop(response);
    }
    server.enqueue(new MockResponse().setBody(response));
    server.start();

    HttpUrl baseUrl = server.url("/ValueSet/$expand");

    RandomCodeGenerator.expandBaseUrl = baseUrl + "?url=";
  }

}
