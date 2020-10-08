package org.mitre.synthea.helpers;

import static org.mitre.synthea.TestHelper.SNOMED_URI;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RunWith(MockitoJUnitRunner.class)
public class RandomCodeGeneratorTest {

  private static final int SEED = 1234;
  private static final String VALUE_SET_URI = SNOMED_URI + "?fhir_vs=ecl/<<131148009";
  private static final String PATH = "wiremock/RandomCodeGeneratorTest/__files/";
  private final Code code = new Code("SNOMED-CT", "38341003", "Hypertension");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private RestTemplate restTemplate;

  @Before
  public void setup() {
    RandomCodeGenerator.restTemplate = restTemplate;
  }

  @Test
  public void getCode() {
    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(getResponseToStub("codes.json"), HttpStatus.OK));

    Code code = RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);

    Assert.assertEquals(SNOMED_URI, code.system);
    Assert.assertEquals("312858004", code.code);
    Assert.assertEquals("Neonatal tracheobronchial haemorrhage", code.display);
  }

  @Test
  public void throwsWhenNoExpansion() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("ValueSet does not contain expansion");
    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(getResponseToStub("noExpansion.ValueSet.json"),
            HttpStatus.OK));

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
  }

  @Test
  public void throwsWhenNoTotal() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("No total element in ValueSet expand result");
    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(getResponseToStub("noTotal.ValueSet.json"),
            HttpStatus.OK));

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
  }

  @Test
  public void throwsWhenNoContains() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("ValueSet expansion does not contain any codes");
    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(getResponseToStub("noContains.ValueSet.json"),
            HttpStatus.OK));

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
  }

  @Test
  public void throwsWhenMissingCodeElements() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("ValueSet contains element does not contain system, code and display");

    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(
            getResponseToStub("missingCodeElements.ValueSet.json"), HttpStatus.OK));

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
  }

  @Test
  public void throwsWhenInvalidResponse() {
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("JsonProcessingException while parsing valueSet response");

    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(
            StringUtils.chop(getResponseToStub("noExpansion.ValueSet.json")),
            HttpStatus.OK));

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
  }

  @Test
  public void throwsWhenRestClientFailed() {
    thrown.expect(RestClientException.class);
    thrown.expectMessage("RestClientException while fetching valueSet response");

    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenThrow(new RestClientException(
            "RestClientException while fetching valueSet response"));

    RandomCodeGenerator.getCode(VALUE_SET_URI, SEED, this.code);
  }

  @Test
  public void filterCodesTest() {
    Mockito
        .when(restTemplate.exchange(ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(HttpMethod.GET),
            ArgumentMatchers.<HttpEntity<?>>any(),
            ArgumentMatchers.<Class<String>>any()))
        .thenReturn(new ResponseEntity<String>(getResponseToStub("codes.json"), HttpStatus.OK));

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
  public void cleanup() {
    RandomCodeGenerator.codeListCache.clear();
    RandomCodeGenerator.restTemplate = null;
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

}
