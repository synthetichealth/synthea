package org.mitre.synthea.world.concepts;

import com.google.common.collect.Multimap;
import javafx.util.Pair;
import okhttp3.*;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

public class TerminologyTest {

    private Terminology.Session session;

    @Before
    public void setUp() {
        session = new Terminology.Session();
    }

    @Test
    public void testSession() {
        List<String> resultsList = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Code retVal = session.getRandomCode("https://www.hl7.org/fhir/synthea/diabetes",
                    "henlo.org","8675309","diabetes mellitus");
            String code = retVal.code;
            resultsList.add(code);
            assertTrue(code.equals("427089005") || code.equals("E08") || code.equals("250.00"));
        }
        boolean allEqual = resultsList.isEmpty() || resultsList.stream().allMatch(resultsList.get(0)::equals);
        assertFalse(allEqual);

        resultsList.clear();

        if(session.getAuthToken()!=null){
            for (int i = 0; i < 8; i++) {
                Code retVal = session.getRandomCode("2.16.840.1.113883.3.464.1003.198.12.1071","CPT",
                        "42","display text");
                String code = retVal.code;
                resultsList.add(code);
                assertTrue(code.equals("248802009") || code.equals("V45.71") || code.equals("Z90.10"));
            }
            allEqual = resultsList.isEmpty() || resultsList.stream().allMatch(resultsList.get(0)::equals);
            assertFalse(allEqual);
            ValueSet va = Terminology.getValueSets().get("2.16.840.1.113883.3.464.1003.198.12.1071");
            assertNotNull(va);
            String placeholder = session.getAuthToken();
            session.setAuthToken(null);
            Code cachedResult = session.getRandomCode("2.16.840.1.113883.3.464.1003.198.12.1071","Ow"
                    ,"wO", "Wo");
            assertNotEquals(cachedResult.system, "Ow");
            assertNotEquals(cachedResult.code, "wO");

            resultsList.clear();
            session.setAuthToken(placeholder);

            for (int i = 0; i < 8; i++) {
                Code retVal = session.getRandomCode("2.16.840.1.113883.3.464.1003.103.12.1001",
                        "Snomed","340", "poquert");
                String code = retVal.code;
                resultsList.add(code);

            }

            boolean enoughUniqueCodes = resultsList.stream().distinct().count() > 3;
            assertTrue(enoughUniqueCodes);
        }else{
            Code retVal = session.getRandomCode("2.16.840.1.113883.3.464.1003.198.12.1071",
                    "bip","bop","boop");
            assertEquals(retVal.system, "bip");
            assertEquals(retVal.code, "bop");
        }
        Code retVal = session.getRandomCode("1.11.111.1.111111.11.1.111.111.11.111",
                "wishy", "washy","wooshy");
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
            va = Terminology.loadFile(path);
        } catch (Exception e) {
            e.printStackTrace();

            va = null;
        }
        assertTrue(Objects.requireNonNull(va).hasCompose());
        assertTrue(va.getCompose().hasInclude());
        path = Paths.get("src/main/resources/valuesets/neverMakeThisFile.json");
        try {
            va = Terminology.loadFile(path);
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

    }

    @Test
    public void getCodes() {
        ValueSet va = new ValueSet();
        va.getCompose().addInclude();
        ValueSet.ConceptSetComponent b1 = new ValueSet.ConceptSetComponent();
        ValueSet.ConceptSetComponent b2 = new ValueSet.ConceptSetComponent();
        b1.setSystem("jimbo");
        b2.setSystem("skadoosh");

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
        assertTrue(codeMap.containsKey("skadoosh"));
        assertTrue(codeMap.get("skadoosh").stream().anyMatch(code -> code.code.equals("4")));
        assertTrue(codeMap.get("skadoosh").stream().anyMatch(code -> code.code.equals("5")));
        assertTrue(codeMap.get("skadoosh").stream().anyMatch(code -> code.code.equals("6")));
        assertTrue(codeMap.get("skadoosh").stream().anyMatch(code -> code.code.equals("7")));
        assertFalse(codeMap.get("skadoosh").stream().anyMatch(code -> code.code.equals("3")));

        assertTrue(codeMap.get("jimbo").stream().anyMatch(code -> code.code.equals("2")));
        assertFalse(codeMap.get("jimbo").stream().anyMatch(code -> code.code.equals("5")));
    }

    @Test
    public void getClient() {
        OkHttpClient hello = new OkHttpClient.Builder().build();
        OkHttpClient goodbye = Terminology.getClient();
        assertEquals(hello.getClass(), goodbye.getClass());
    }

    @Test
    public void getClient1() {
        OkHttpClient client = Terminology.getClient("www.hello.com", 8080);
        assertTrue(client.proxy().address().toString().contains("www.hello.com"));
        assertTrue(client.proxy().address().toString().contains("8080"));
    }

    @Test
    public void getAuthToken() {
        OkHttpClient hello;
        String PORT = Config.get("terminology.PORT");
        String HOSTNAME = Config.get("terminology.PROXY_HOSTNAME");
        if(HOSTNAME!=null & PORT!=null){
            hello = new OkHttpClient.Builder()
                    .proxy(new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress(HOSTNAME, Integer.parseInt(PORT))))
                    .build();
        }else{
            hello = new OkHttpClient.Builder()
                    .build();
        }
        if(Config.get("VSAC_USER")!=null & Config.get("VSAC_PASS")!=null){

            String authToken = Terminology.getAuthToken(hello);
            assertTrue(authToken.length() > 10);
            assertNotNull(authToken);
        }


    }

    @Test
    public void getServiceTicket() {
        if(session.getAuthToken()!=null){
            String token = Terminology.getServiceTicket(session.getAuthToken(), session.getSessionClient());
            assertTrue(token.length() > 10);
            assertNotNull(token);
        }
    }


    // These functions aren't used yet

//    @Test
//    public void makeGenericPost() {
//    }
//
//    @Test
//    public void makeGenericGet() {
//    }

    @Test
    public void makeRequest() {
        String url = "http://echo.jsontest.com/title/ipsum/content/blah";
        Request request = Terminology.makeRequest(url);
        assertEquals(request.url().toString(), url);
        try {
            Response response = session.getSessionClient().newCall(request).execute();

            assertTrue(Objects.requireNonNull(response.body()).string().contains("ipsum"));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Test
    public void makeRequest1() {
        FormBody formBody = new FormBody.Builder().add("json", "{1:2}").build();
        Request request = Terminology.makeRequest("http://validate.jsontest.com/", formBody);
        try {
            Response response = session.getSessionClient().newCall(request).execute();
            String body = Objects.requireNonNull(response.body()).string();
            assertTrue(body.contains("validate"));
            assertTrue(body.contains("true"));
            assertFalse(body.contains("error"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void makeRequestBody() {
        Map<String, String> alpha = new HashMap<>();
        alpha.put("yes", "no");
        alpha.put("thirst", "quenched");
        RequestBody requestBody = Terminology.makeRequestBody(alpha);
        Request request = new Request.Builder().url("http://httpbin.org/post").post(requestBody).build();
        try {
            Response response = session.getSessionClient().newCall(request).execute();
            String body = Objects.requireNonNull(response.body()).string();
            assertTrue(body.contains("thirst"));
            assertTrue(body.contains("quenched"));
            assertFalse(body.contains("error"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}