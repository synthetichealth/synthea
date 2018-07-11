package org.mitre.synthea.helpers;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;


import javafx.util.Pair;

import okhttp3.*;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;





/**
 * Finds a specified value set, extracts the codes and systems and selects one code/system at random.
 */

public class Terminology {

    private static String VS_FOLDER = "valuesets";
    private static String URL_SCHEME = "https";

    private static String PROXY_URL = "gatekeeper-w.mitre.org";
    private static int PROXY_PORT = 80;

    private static final String VSAC_USER = Config.get("terminology.username");
    private static final String VSAC_PASS = Config.get("terminology.password");

    // URL needs to be HTTPS or the request will be redirected, defaulting request type
    private static final String AUTH_URL = "https://vsac.nlm.nih.gov/vsac/ws/Ticket";
    private static final String SERVICE_URL = "http://umlsks.nlm.nih.gov";
    private static final String VSAC_VALUE_SET_RETRIEVAL_URL = "vsac.nlm.nih.gov";

    private static final FhirContext ctx = FhirContext.forDstu3();
    private static final Map<String, ValueSet> valueSets = loadValueSets();



    public static class Session{
        private OkHttpClient client;


        // Can be reused for multiple requests
        private String authToken;

        Session(){
            if(PROXY_URL.length()>0){
                client = getClient(PROXY_URL,PROXY_PORT);
            }else{
                client = getClient();

            }
            authToken = getAuthToken(client);

        }


        /**
         *
         * @param url The canonical URL of the ValueSet or a VSAC oid
         * @return A random code from the specified ValueSet as a Pair of form <system:code>
         */
        protected Pair<String,String> getRandomCode(String url){

            ValueSet vs = Terminology.getValueSet(url);
            if(vs==null){
                String ticket = getServiceTicket(authToken);
                String response = Terminology.getValueSet(client,url,ticket);
                try{
                    vs = parseResponse(response);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            return chooseCode(Terminology.getCodes(vs));

        }

        /**
         *
         * @param xml String representation of XML response from VSAC
         * @return ValueSet containing systems and their codes
         * @throws Exception
         */
        protected ValueSet parseResponse(String xml) throws Exception
        {
            // Parse the string to get XML encoding
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(xml));

            // Build a ValueSet
            ValueSet vs = new ValueSet();

            /*A value set should look like
             Compose: {
                  Include: [
                      {
                      system,
                      concept: [
                              code
                              code
                              ...
                              code
                               ]
                      }
                      {
                      system,
                      concept: [
                          ...
                              ]
                       }
                       ]}
            */
            ValueSet.ValueSetComposeComponent composeComponent = new ValueSet.ValueSetComposeComponent();
            Document responseXml = builder.parse(is);
            Element root = responseXml.getDocumentElement();
            String namespaceURI = root.getNamespaceURI();
            NodeList concepts =responseXml.getElementsByTagNameNS(namespaceURI,"Concept");
            for(int i = 0 ; i<concepts.getLength();i++){

                Node currentNode = concepts.item(i);
                String system = currentNode.getAttributes().getNamedItem("codeSystem").getTextContent();
                String code = currentNode.getAttributes().getNamedItem("code").getTextContent();
                String display = currentNode.getAttributes().getNamedItem("displayName").getTextContent();

                // the toggle checks to see if we already have the system present
                // in the ValueSet so we don't have duplicates with the codes
                // spread out between them.
                boolean toggle = false;
                for (ValueSet.ConceptSetComponent c : composeComponent.getInclude()) {
                    if(c.getSystem().equals(system)){
                        c.addConcept().setCode(code).setDisplay(display);
                        toggle=true;

                    }

                }
                if(!toggle){
                    composeComponent.addInclude().setSystem(system).addConcept().setCode(code);
                }

            }
            vs.setCompose(composeComponent);
//            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(vs));

            return vs;


        }

        private <E> Optional<E> getRandom (Collection<E> e) {
            //Gets random element from java collection

            return e.stream()
                    .skip((int) (e.size() * Math.random()))
                    .findFirst();
        }

        private Pair<String,String> chooseCode(Multimap<String,String> codes){
            // Chooses a random code by picking a system then picking a code from
            // that system

            List<String> keys = new ArrayList<>(codes.keySet());
            Random r = new Random();
            String system = keys.get(r.nextInt(keys.size()));

            String code = getRandom(codes.get(system)).get();
            System.out.println(code);

            return new Pair<>(system,code);
        }





    }

    private static Map<String, ValueSet> loadValueSets(){
        // Loads valueSets from file and puts them in a map of <url : ValueSet>

        Map<String, ValueSet> retVal = new ConcurrentHashMap<String, ValueSet>();
        URL valuesetsFolder = ClassLoader.getSystemClassLoader().getResource(VS_FOLDER);
        try{
            Path path = Paths.get(valuesetsFolder.toURI());

            Files.walk(path, Integer.MAX_VALUE).filter(Files::isReadable).filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json")).forEach(t -> {
                try {
                    ValueSet vs = loadFile(t);
                    String url = vs.getUrl();

                    retVal.put(url,vs);


                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            });

        }catch(Exception e){
            e.printStackTrace();
        }


        return retVal;

    }

    private static ValueSet loadFile(Path path) throws Exception{

        // Loads JSON files as ValueSets

        FileReader fileReader = new FileReader(path.toString());
        JsonReader reader = new JsonReader(fileReader);
        JsonParser parser = new JsonParser();
        String object = parser.parse(reader).toString();
        ValueSet valueset;

        try{
            valueset = ctx.newJsonParser().parseResource(ValueSet.class, object);
        }catch(Exception e){
            System.out.println("File " + path.toString() + " is not the correct format for a ValueSet");
            valueset = null;
        }
        fileReader.close();
        reader.close();
        return valueset;
    }

    private static ValueSet getValueSet(String url){
        // Temporary call to get valueSet from a Map
        return valueSets.get(url);
    }


    private static Multimap<String, String> getCodes(ValueSet vs){
        /* Gets codes from the provided value set and sorts them by
          system.

          Sorting by system makes each system have the same "weight" when being chosen
          e.g. SNOMED may have 100 codes to ICD-10's 3 in a value set but the chance that
          either one gets picked is the same.
         */

        Multimap<String,String> retVal = ArrayListMultimap.create();
        String system;
        String code;
        List<ValueSet.ConceptSetComponent> concepts = vs.getCompose().getInclude();
        for(ValueSet.ConceptSetComponent concept : concepts){
            system = concept.getSystem();
            for(ValueSet.ConceptReferenceComponent conceptRef : concept.getConcept()){
                retVal.put(system,conceptRef.getCode());
            }

        }

        return retVal;

    }




    private static OkHttpClient getClient(String host, int port){
        // If you want to route through a proxy

        return new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(host, port)))
                .build();
    }

    //Overloaded for use without a proxy
    private static OkHttpClient getClient() {
        return new OkHttpClient.Builder().build();
    }

    private static String getAuthToken(OkHttpClient client){
        String token;

        //Order matters for request body, requiring an ordered map
        Map<String,String> requestForm = new LinkedHashMap<>();
        requestForm.put("username",VSAC_USER);
        requestForm.put("password",VSAC_PASS);

        // Make request body
        RequestBody requestBody = makeRequestBody(requestForm);

        //Create Request
        Request request = makeRequest(AUTH_URL,requestBody);

        try {
            Response response = client.newCall(request).execute();
            token = response.body().string();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            token = null;
            // Check to make sure that params are correctly entered
            // eg. 'username' not 'user', 'password' not 'pass'.
        }
        return token;



    }

    /**
     * Service tickets are a one-time use auth code for accessing value sets from VSAC
     * A regular auth token can be used to acquire multiple service tickets
     */
    private static String getServiceTicket(String auth_token){
        String token;
        OkHttpClient client = getClient(PROXY_URL,PROXY_PORT);

        Map<String,String> requestForm = new LinkedHashMap<>();
        requestForm.put("service",SERVICE_URL);
        RequestBody requestBody = makeRequestBody(requestForm);
        Request request = makeRequest(AUTH_URL + "/" + auth_token,requestBody);

        try {
            Response response = client.newCall(request).execute();
            token = response.body().string();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            token = null;
            // Check to make sure that params are correctly entered
            // eg. 'username' not 'user', 'password' not 'pass'.
        }

        return token;


    }

    private static String makeGenericPost(String url, Map<String,String> requestForm, OkHttpClient client){
        String response_body;
        RequestBody requestBody = makeRequestBody(requestForm);

        Request request = makeRequest(url,requestBody);
        try {
            Response response = client.newCall(request).execute();
            response_body = response.body().string();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            response_body = null;
        }

        return response_body;

    }

    private static String makeGenericGet(String url, Map<String,String> requestForm, OkHttpClient client){
        String response_body;
        HttpUrl requestUrl = makeGetUrl(url, requestForm);

        Request request = makeRequest(requestUrl);
        try {
            Response response = client.newCall(request).execute();
            response_body = response.body().string();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            response_body = null;
        }

        return response_body;

    }

    private static String getValueSet(OkHttpClient client, String oid, String ticket){
        String responseBody;
        Map<String,String> parameterMap = new LinkedHashMap<>();
        parameterMap.put("id",oid);
        parameterMap.put("ticket",ticket);
        HttpUrl requestUrl = makeGetUrl(VSAC_VALUE_SET_RETRIEVAL_URL,parameterMap);
        Request request = makeRequest(requestUrl);
        try {
            Response response = client.newCall(request).execute();
            responseBody = response.body().string();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            responseBody = null;
        }

        return responseBody;



    }


    private static Request makeRequest(HttpUrl url){
         //Makes requests of specified type, overloaded to switch between GET and POST

        Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

        return request;

    }
    private static Request makeRequest(String url, RequestBody body){
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        return request;
    }

    private static RequestBody makeRequestBody(Map<String,String> valuePairs){
        // Builds generic request bodies from maps

        FormBody.Builder requestBuilder = new FormBody.Builder();
        for (String s : valuePairs.keySet()) {

            requestBuilder.add(s,valuePairs.get(s));
        }

        return requestBuilder.build();
    }

    private static HttpUrl makeGetUrl(String url, Map<String,String> valuePairs){
        // Constructs the url that is queried in the Get call

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.scheme(URL_SCHEME).host(VSAC_VALUE_SET_RETRIEVAL_URL).addPathSegments("vsac/svs/RetrieveValueSet");
        for (String s : valuePairs.keySet()) {

            urlBuilder.setQueryParameter(s,valuePairs.get(s));
        }

        return urlBuilder.build();
    }



}
