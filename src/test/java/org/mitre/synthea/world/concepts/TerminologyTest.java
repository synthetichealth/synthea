package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Multimap;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.junit.Test;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.concepts.HealthRecord.Code;




public class TerminologyTest {
  @Test
  public void testTerminology() {
    List<String> resultsList = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      Code retVal = Terminology.getRandomCode("https://www.hl7.org/fhir/synthea/diabetes",
          "websiteName.org", "8675309", "diabetes mellitus");
      String code = retVal.code;
      resultsList.add(code);
      assertTrue(code.equals("427089005") || code.equals("E08") || code.equals("250.00"));
    }
    boolean allEqual = resultsList.isEmpty() || resultsList
        .stream().allMatch(resultsList.get(0)::equals);
    assertFalse(allEqual);

    resultsList.clear();

    if (Terminology.getAuthToken() != null) {
      for (int i = 0; i < 8; i++) {
        Code retVal = Terminology.getRandomCode("2.16.840.1.113883.3.464.1003.198.12.1071", "ICD-9",
            "42", "display text");
        String code = retVal.code;
        resultsList.add(code);
        assertTrue(code.equals("248802009") || code.equals("V45.71") || code.equals("Z90.10"));
      }
      allEqual = resultsList.isEmpty() || resultsList.stream().allMatch(resultsList.get(0)::equals);
      assertFalse(allEqual);
      ValueSet va = Terminology.getValueSets().get("2.16.840.1.113883.3.464.1003.198.12.1071");
      assertNotNull(va);
      String placeholder = Terminology.getAuthToken();
      Terminology.setAuthToken(null);
      Code cachedResult = Terminology
          .getRandomCode("2.16.840.1.113883.3.464.1003.198.12.1071", "Ow",
          "wO", "Wo");
      assertNotEquals(cachedResult.system, "Ow");
      assertNotEquals(cachedResult.code, "wO");

      resultsList.clear();
      Terminology.setAuthToken(placeholder);

      for (int i = 0; i < 8; i++) {
        Code retVal = Terminology.getRandomCode("2.16.840.1.113883.3.464.1003.103.12.1001",
            "Snomed", "340", "foobar");
        String code = retVal.code;
        resultsList.add(code);

      }

      // This test is probabilistically capable of failing with
      // a 0.000000001% chance of failure.
      boolean enoughUniqueCodes = resultsList.stream().distinct().count() > 3;
      assertTrue(enoughUniqueCodes);
    } else {
      Code retVal = Terminology.getRandomCode("2.16.840.1.113883.3.526.3.1489",
          "bip", "bop", "boop");
      assertEquals(retVal.system, "bip");
      assertEquals(retVal.code, "bop");
    }
    Code retVal = Terminology.getRandomCode("1.11.111.1.111111.11.1.111.111.11.111",
        "wishy", "washy", "wooshy");
    assertEquals(retVal.system, "wishy");
    assertEquals(retVal.code, "washy");


  }

  @Test
  public void loadValueSets() {
    Map<String, ValueSet> valueSetMap = Terminology.loadValueSets();
    ValueSet va = valueSetMap.get("https://www.hl7.org/fhir/synthea/diabetes");
    assertTrue(va.hasCompose());
    assertTrue(va.getCompose().hasInclude());

  }

  @Test
  public void loadFile() {
    Path path = Paths.get("src/main/resources/valuesets/diabetes.json");
    ValueSet va;
    try {
      va = Terminology.loadValueSetFile(path);
    } catch (Exception e) {
      e.printStackTrace();

      va = null;
    }
    assertTrue(Objects.requireNonNull(va).hasCompose());
    assertTrue(va.getCompose().hasInclude());
    path = Paths.get("src/main/resources/valuesets/neverMakeThisFile.json");
    try {
      va = Terminology.loadValueSetFile(path);
    } catch (Exception e) {
      va = null;
    }
    assertNull(va);
  }

  @Test
  public void getValueSet() {
    ValueSet va = Terminology.getValueSets().get("https://www.hl7.org/fhir/synthea/diabetes");
    assertNotNull(va);
    assertTrue(va.hasCompose());
    assertTrue(va.getCompose().getInclude().get(0).hasConcept());

  }

  @Test
  public void getCodes() {
    ValueSet va = new ValueSet();
    va.getCompose().addInclude();
    ValueSet.ConceptSetComponent b1 = new ValueSet.ConceptSetComponent();
    ValueSet.ConceptSetComponent b2 = new ValueSet.ConceptSetComponent();
    b1.setSystem("foobar");
    b2.setSystem("foo");

    ValueSet.ConceptReferenceComponent c1 = new ValueSet.ConceptReferenceComponent();
    ValueSet.ConceptReferenceComponent c2 = new ValueSet.ConceptReferenceComponent();
    ValueSet.ConceptReferenceComponent c3 = new ValueSet.ConceptReferenceComponent();
    ValueSet.ConceptReferenceComponent c4 = new ValueSet.ConceptReferenceComponent();
    ValueSet.ConceptReferenceComponent c5 = new ValueSet.ConceptReferenceComponent();
    ValueSet.ConceptReferenceComponent c6 = new ValueSet.ConceptReferenceComponent();
    ValueSet.ConceptReferenceComponent c7 = new ValueSet.ConceptReferenceComponent();

    c1.setCode("1");
    c2.setCode("2");
    c3.setCode("3");
    c4.setCode("4");
    c5.setCode("5");
    c6.setCode("6");
    c7.setCode("7");
    b1.addConcept(c1);
    b1.addConcept(c2);
    b1.addConcept(c3);
    b2.addConcept(c4);
    b2.addConcept(c5);
    b2.addConcept(c6);
    b2.addConcept(c7);
    va.getCompose().addInclude(b1);
    va.getCompose().addInclude(b2);
    Multimap<String, Code> codeMap = Terminology.getCodes(va);
    assertTrue(codeMap.containsKey("foo"));
    assertTrue(codeMap.get("foo").stream().anyMatch(code -> code.code.equals("4")));
    assertTrue(codeMap.get("foo").stream().anyMatch(code -> code.code.equals("5")));
    assertTrue(codeMap.get("foo").stream().anyMatch(code -> code.code.equals("6")));
    assertTrue(codeMap.get("foo").stream().anyMatch(code -> code.code.equals("7")));
    assertFalse(codeMap.get("foo").stream().anyMatch(code -> code.code.equals("3")));

    assertTrue(codeMap.get("foobar").stream().anyMatch(code -> code.code.equals("2")));
    assertFalse(codeMap.get("foobar").stream().anyMatch(code -> code.code.equals("5")));
  }

  @Test
  public void getClient() {
    OkHttpClient hello = new OkHttpClient.Builder().build();
    OkHttpClient goodbye = Terminology.getClient();
    assertEquals(hello.getClass(), goodbye.getClass());
  }


  @Test
  public void getAuthToken() {
    if (Config.get("VSAC_USER") != null & Config.get("VSAC_PASS") != null) {

      String authToken = Terminology.getAuthToken();
      assertTrue(authToken.length() > 10);
      assertNotNull(authToken);
    }


  }

  @Test
  public void getServiceTicket() {
    if (Terminology.getAuthToken() != null) {
      String token = Terminology.getServiceTicket(Terminology.getAuthToken());
      assertTrue(token.length() > 10);
      assertNotNull(token);
    }
  }

  @Test
  public void makeRequest() {
    String url = "http://mock.com/";
    Request request = Terminology.buildRequest(url);
    assertEquals(request.url().toString(), url);
  }

  @Test
  public void makeRequest1() throws IOException {

    FormBody formBody = new FormBody.Builder().add("json", "{1:2}").build();

    // buildRequest doesn't actually make the request, it makes the request object
    // which can be used to make the request.  Potentially needs to be renamed.
    Request request = Terminology.buildRequest("http://real.website.com/", formBody);
    Buffer buffer = new Buffer();
    assertNotNull(request.body());
    request.body().writeTo(buffer);
    assertEquals(buffer.readUtf8(), "json=%7B1%3A2%7D");
  }

  @Test
  public void makeRequestBody() throws IOException {
    // Just need to make sure the request body is put together properly, not that it
    // yields a response from an arbitrary server.
    Map<String, String> alpha = new HashMap<>();
    alpha.put("yes", "no");
    alpha.put("thirst", "quenched");

    RequestBody requestBody = Terminology.makeRequestBody(alpha);
    Buffer buffer = new Buffer();
    requestBody.writeTo(buffer);

    assertEquals(buffer.readUtf8(), "thirst=quenched&yes=no");

  }

}