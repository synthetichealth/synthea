package org.mitre.synthea.world.concepts;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import com.google.gson.stream.JsonWriter;
import okhttp3.*;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.mitre.synthea.helpers.Config;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




/**
 * Finds a specified value set, extracts the codes and systems and selects one code/system at random.
 */

public class Terminology {

    private static String VS_FOLDER = "valuesets";
    private static String URL_SCHEME = "https";

    private static String PROXY_URL = Config.get("terminology.PROXY_HOSTNAME");

    private static String PROXY_PORT = Config.get("terminology.PORT");

    private static final String VSAC_USER = Config.get("terminology.username");
    private static final String VSAC_PASS = Config.get("terminology.password");

    // URL needs to be HTTPS or the request will be redirected, defaulting request type
    private static final String AUTH_URL = "https://vsac.nlm.nih.gov/vsac/ws/Ticket";
    private static final String SERVICE_URL = "http://umlsks.nlm.nih.gov";
    private static final String VSAC_VALUE_SET_RETRIEVAL_URL = "vsac.nlm.nih.gov";

    private static final FhirContext ctx = FhirContext.forDstu3();
    private static final Map<String, ValueSet> valueSets = loadValueSets();



    final static Logger logger = LoggerFactory.getLogger(Terminology.class);

    public static Session sess = new Session();


    public static class Session{
        private OkHttpClient client;


        // Can be reused for multiple requests
        private String authToken;

        public Session(){

            if(PROXY_URL!=null & PROXY_PORT!=null){
                client = getClient(PROXY_URL,Integer.parseInt(PROXY_PORT));
            }else{
                client = getClient();

            }
            if(VSAC_PASS == null | VSAC_USER==null){
                authToken=null;
            }else{
                authToken = Terminology.getAuthToken(client);
            }
        }
        public ValueSet getValueSet(String url){
            // Call to get valueSet from a Map
            ValueSet vs = valueSets.get(url);
            if(vs==null & authToken!=null){
                String ticket = getServiceTicket(authToken,client);


                Response response = Terminology.getValueSet(client,url,ticket);
                if(response.code()==200){
                    try{
                        vs = parseResponse(response.body().string());
                        valueSets.put(url,vs);
                        response.close();

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }else{
                    logger.warn("Invalid OID or URL provided");
                    return null;
                }

            }else if(vs==null){
                return null;
            }

            return vs;
        }


        /**
         *
         * @param url The canonical URL of the ValueSet or a VSAC oid
         * @return A random code from the specified ValueSet as a Pair of form <system:code>
         */
        public Code getRandomCode(String url, String defaultSystem, String defaultCode, String defaultDisplay){

            ValueSet vs = getValueSet(url);
            if(vs==null){
                return new Code(defaultSystem,defaultCode,defaultDisplay);
            }
            return chooseCode(Terminology.getCodes(vs));

        }

        public List<Code> getAllCodes(String url){
            // Mainly for use by Concepts.java, doesn't default
            ValueSet vs = getValueSet(url);
            if(vs==null){
                return null;
            }
            Multimap<String, Code> codes = getCodes(vs);
            return new ArrayList<>(codes.values());
        }

        /**
         *
         * @param xml String representation of XML response from VSAC
         * @return ValueSet containing systems and their codes
         * @throws Exception
         */
        public ValueSet parseResponse(String xml) throws Exception
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

            NamedNodeMap valueSetAttr = responseXml.getElementsByTagNameNS(namespaceURI,"ValueSet")
                    .item(0)
                    .getAttributes();

            String displayName = valueSetAttr.getNamedItem("displayName").getTextContent();

            // Regex replaces all puncuation with an underscore
            displayName = displayName.replaceAll("[, ';\"/:]","_");
            String oid = valueSetAttr.getNamedItem("ID").getTextContent();


            vs.setUrl(oid);
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
                    composeComponent.addInclude().setSystem(system).addConcept().setCode(code).setDisplay(display);
                }

            }
            vs.setCompose(composeComponent);

            // Writes the value set to the valueset folder with VSAC appended
            FileWriter w = new FileWriter(
                    Paths.get(ClassLoader.getSystemClassLoader().getResource(VS_FOLDER).toURI()) + "/" + displayName+"VSAC.json");

//            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(vs));
            ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(vs,w);

            return vs;


        }

        public <E> Optional<E> getRandom (Collection<E> e) {
            //Gets random element from java collection

            return e.stream()
                    .skip((int) (e.size() * Math.random()))
                    .findFirst();
        }

        public Code chooseCode(Multimap<String,Code> codes){
            // Chooses a random code by picking a system then picking a code from
            // that system

            List<String> keys = new ArrayList<>(codes.keySet());
            Random r = new Random();
            String system = keys.get(r.nextInt(keys.size()));

            return getRandom(codes.get(system)).get();
        }

        public String getAuthToken(){
            return this.authToken;
        }
        public OkHttpClient getSessionClient(){
            return this.client;
        }
        public void setAuthToken(String token){
            this.authToken=token;
        }





    }

    public static Map<String, ValueSet> loadValueSets(){
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

    public static ValueSet loadFile(Path path) throws Exception{

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


    public static Multimap<String, Code> getCodes(ValueSet vs){
        /* Gets codes from the provided value set and sorts them by
          system.

          Sorting by system makes each system have the same "weight" when being chosen
          e.g. SNOMED may have 100 codes to ICD-10's 3 in a value set but the chance that
          either one gets picked is the same.
         */

        Multimap<String,Code> retVal = ArrayListMultimap.create();
        String system;
        String code;
        List<ValueSet.ConceptSetComponent> concepts = vs.getCompose().getInclude();
        for(ValueSet.ConceptSetComponent concept : concepts){
            system = concept.getSystem();
            for(ValueSet.ConceptReferenceComponent conceptRef : concept.getConcept()){
                if(conceptRef.getDisplay()==null){
                    retVal.put(system,new Code(system,conceptRef.getCode(),system));
                }else{
                    retVal.put(system,new Code(system,conceptRef.getCode(),conceptRef.getDisplay()));
                }

            }
        }

        return retVal;

    }




    public static OkHttpClient getClient(String host, int port){
        // If you want to route through a proxy

        return new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(host, port)))
                .build();
    }

    //Overloaded for use without a proxy
    public static OkHttpClient getClient() {
        return new OkHttpClient.Builder().build();
    }

    public static String getAuthToken(OkHttpClient client){
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
            if(response.code()==200){
                token = response.body().string();
            }else{
                token=null;
            }
            response.close();
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
    public static String getServiceTicket(String auth_token, OkHttpClient client){
        String token;

        Map<String,String> requestForm = new LinkedHashMap<>();
        requestForm.put("service",SERVICE_URL);
        RequestBody requestBody = makeRequestBody(requestForm);
        Request request = makeRequest(AUTH_URL + "/" + auth_token,requestBody);

        try {
            Response response = client.newCall(request).execute();
            token = response.body().string();
            response.close();

        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            token = null;
            // Check to make sure that params are correctly entered
            // eg. 'username' not 'user', 'password' not 'pass'.
        }

        return token;


    }

    public static String makeGenericPost(String url, Map<String,String> requestForm, OkHttpClient client){
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

    public static String makeGenericGet(String url, Map<String,String> requestForm, OkHttpClient client){
        String response_body;
        HttpUrl requestUrl = makeGetUrl(url, requestForm);

        Request request = makeRequest(requestUrl.toString());
        try {
            Response response = client.newCall(request).execute();
            response_body = response.body().string();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            response_body = null;
        }

        return response_body;

    }

    private static Response getValueSet(OkHttpClient client, String oid, String ticket){
        Response response=null;
        Map<String,String> parameterMap = new LinkedHashMap<>();
        parameterMap.put("id",oid);
        parameterMap.put("ticket",ticket);
        HttpUrl requestUrl = makeGetUrl(VSAC_VALUE_SET_RETRIEVAL_URL,parameterMap);
        Request request = makeRequest(requestUrl.toString());
        try {
            response = client.newCall(request).execute();

        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }

        return response;
    }


    public static Request makeRequest(String url){
         //Makes requests of specified type, overloaded to switch between GET and POST

        Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();

        return request;

    }
    public static Request makeRequest(String url, RequestBody body){
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        return request;
    }

    public static RequestBody makeRequestBody(Map<String,String> valuePairs){
        // Builds generic request bodies from maps

        FormBody.Builder requestBuilder = new FormBody.Builder();
        for (String s : valuePairs.keySet()) {

            requestBuilder.add(s,valuePairs.get(s));
        }

        return requestBuilder.build();
    }

    public static HttpUrl makeGetUrl(String url, Map<String,String> valuePairs){
        // Constructs the url that is queried in the Get call

        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.scheme(URL_SCHEME).host(url).addPathSegments("vsac/svs/RetrieveValueSet");
        for (String s : valuePairs.keySet()) {

            urlBuilder.setQueryParameter(s,valuePairs.get(s));
        }

        return urlBuilder.build();
    }


    public static Map<String, ValueSet> getValueSets(){
        return valueSets;
    }


}
