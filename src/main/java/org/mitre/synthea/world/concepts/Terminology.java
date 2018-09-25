package org.mitre.synthea.world.concepts;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.hl7.fhir.dstu3.model.ValueSet;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


/**
 * Finds a specified value set, extracts the codes and systems
 * and selects one code/system at random.
 */
public class Terminology {
  private static final String VS_FOLDER = "valuesets";
  private static final String VSAC_USER = Config.get("terminology.username");
  private static final String VSAC_PASS = Config.get("terminology.password");

  private static final String AUTH_URL = "http://vsac.nlm.nih.gov/vsac/ws/Ticket";
  private static final String SERVICE_URL = "http://umlsks.nlm.nih.gov";
  private static final String VSAC_VALUE_SET_RETRIEVAL_URL = "vsac.nlm.nih.gov";

  private static final FhirContext ctx = FhirContext.forDstu3();
  private static final Map<String, ValueSet> valueSets = loadValueSets();

  private static final Logger logger = LoggerFactory.getLogger(Terminology.class);

  // We only really need one client
  private static final OkHttpClient client = buildClient();

  // Can be reused for multiple requests
  // Not final in case we want to get a new token
  private static String authToken = Terminology.requestAuthToken();

  /**
   * Retrieves a value set from file, and if it can't find one, looks on VSAC.
   * Returns null if it can't find anything.
   * @param url the uri, oid, or url of the value set
   * @return the value set that was requested, either from file or VSAC
   */
  static ValueSet getValueSet(String url) {
    // Call to get valueSet from a Map
    ValueSet vs = valueSets.get(url);
    if (vs == null && authToken != null) {
      String ticket = getServiceTicket(authToken);
      Response response = Terminology.requestValueSet(url,ticket);
      if (response.code() == 200) {
        try {
          assert response.body() != null;
          vs = parseResponse(response.body().string());
          valueSets.put(url,vs);
          response.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        logger.warn("Invalid OID or URL provided");
        return null;
      }
    } else if (vs == null) {
      return null;
    }
    return vs;
  }

  private static Response requestValueSet(String oid, String ticket) {
    Response response = null;
    Map<String,String> parameterMap = new LinkedHashMap<>();
    parameterMap.put("id",oid);
    parameterMap.put("ticket",ticket);
    HttpUrl requestUrl = makeGetUrl(parameterMap);
    Request request = buildGetRequest(requestUrl.toString());
    try {
      response = client.newCall(request).execute();
    } catch (IOException | NullPointerException e) {
      e.printStackTrace();
    }

    return response;
  }

  /**
   * Gets a random code from a value set.  First gets a value set, then gets its codes, then
   * chooses one of those codes.  If the value set can't be found, it just returns
   * the provided defaults.
   * @param url The canonical URL of the ValueSet or a VSAC oid
   * @return A random code from the specified ValueSet as a Pair of form (system:code)
   */
  public static Code getRandomCode(String url,
      String defaultSystem, String defaultCode, String defaultDisplay) {
    ValueSet vs = getValueSet(url);
    if (vs == null) {
      return new Code(defaultSystem,defaultCode,defaultDisplay);
    }
    return chooseCode(Terminology.getCodes(vs));
  }

  /**
   * Gets all the codes from a value set after retrieving it.
   * @param url the url or oid of the value set to be retrieved
   * @return an array of every code, unsorted
   */
  public static List<Code> getAllCodes(String url) {
    // Mainly for use by Concepts.java, doesn't default
    ValueSet vs = getValueSet(url);
    if (vs == null) {
      return null;
    }
    Multimap<String, Code> codes = getCodes(vs);
    return new ArrayList<>(codes.values());
  }

  /**
   * Parses the XML response from VSAC, returns a value set, and saves the value set
   * as JSON locally for easy use later.
   * @param xml String representation of XML response from VSAC
   * @return ValueSet containing systems and their codes
   * @throws Exception just in case
   */
  static ValueSet parseResponse(String xml) throws Exception {
    // Parse the string to get XML encoding.
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

    // Regex replaces all punctuation with an underscore
    displayName = displayName.replaceAll("[, ';\"/:]","_");
    String oid = valueSetAttr.getNamedItem("ID").getTextContent();
    vs.setUrl(oid);
    NodeList concepts = responseXml.getElementsByTagNameNS(namespaceURI,"Concept");
    for (int i = 0; i < concepts.getLength(); i++) {
      Node currentNode = concepts.item(i);
      String system = currentNode.getAttributes().getNamedItem("codeSystem").getTextContent();
      String code = currentNode.getAttributes().getNamedItem("code").getTextContent();
      String display = currentNode.getAttributes().getNamedItem("displayName").getTextContent();

      // the toggle checks to see if we already have the system present
      // in the ValueSet so we don't have duplicates with the codes
      // spread out between them.
      boolean toggle = false;

      for (ValueSet.ConceptSetComponent c : composeComponent.getInclude()) {
        if (c.getSystem().equals(system)) {
          c.addConcept().setCode(code).setDisplay(display);
          toggle = true;
        }
      }
      if (!toggle) {
        composeComponent.addInclude()
            .setSystem(system)
            .addConcept()
            .setCode(code)
            .setDisplay(display);
      }
    }
    vs.setCompose(composeComponent);

    // Writes the value set to the valueset folder with VSAC appended.
    FileWriter w = new FileWriter(
        Paths.get(Objects.requireNonNull(ClassLoader.getSystemClassLoader()
            .getResource(VS_FOLDER)).toURI()) + "/" + displayName + "VSAC.json");

    // System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(vs));
    ctx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(vs,w);
    return vs;
  }

  static <E> Optional<E> getRandom(Collection<E> e) {
    // Gets a random element from a collection
    return e.stream()
              .skip((int) (e.size() * Math.random()))
              .findFirst();
  }

  static Code chooseCode(Multimap<String, Code> codes) {
    // Chooses a random code by picking a random system then picking a random code from
    // that system.

    List<String> keys = new ArrayList<>(codes.keySet());
    Random r = new Random();
    String system = keys.get(r.nextInt(keys.size()));
    return getRandom(codes.get(system)).get();
  }

  static String getAuthToken() {
    return authToken;
  }

  static OkHttpClient getClient() {
    return client;
  }

  static void setAuthToken(String token) {
    authToken = token;
  }

  static Map<String, ValueSet> loadValueSets() {
    // Loads valueSets from file and puts them in a map of {url : ValueSet}
    Map<String, ValueSet> retVal = new ConcurrentHashMap<>();
    URL valuesetsFolder = ClassLoader.getSystemClassLoader().getResource(VS_FOLDER);

    try {
      assert valuesetsFolder != null;
      Path path = Paths.get(valuesetsFolder.toURI());
      Files.walk(path, Integer.MAX_VALUE).filter(Files::isReadable).filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".json")).forEach(t -> {
            try {
              ValueSet vs = loadValueSetFile(t);
              String url = vs.getUrl();
              retVal.put(url,vs);
            } catch (Exception e) {
              e.printStackTrace();
              throw new RuntimeException(e);
            }
          });
    } catch (Exception e) {
      e.printStackTrace();
    }

    return retVal;
  }

  static ValueSet loadValueSetFile(Path path) throws Exception {
    // Loads JSON files as ValueSets.
    String jsonContent = loadJsonFile(path).toString();
    ValueSet valueset;
    try {
      valueset = ctx.newJsonParser().parseResource(ValueSet.class, jsonContent);
    } catch (Exception e) {
      logger.error("File " + path.toString() + " is not the correct format for a ValueSet");
      valueset = null;
    }
    return valueset;
  }

  static JsonObject loadJsonFile(Path path) {
    // Loads JSON files as ValueSets.
    FileReader fileReader = null;
    try {
      fileReader = new FileReader(path.toString());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    JsonReader reader = new JsonReader(fileReader);
    JsonParser parser = new JsonParser();
    JsonObject retVal = parser.parse(reader).getAsJsonObject();
    try {
      fileReader.close();
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return retVal;
  }

  /**
   * Gets codes from the provided value set and sorts them by system.
   * Sorting by system makes each system have the same "weight" when being chosen
   * e.g. SNOMED may have 100 codes to ICD-10's 3 in a value set but the chance that
   * either one gets picked is the same.
   */
  static Multimap<String, Code> getCodes(ValueSet vs) {
    Multimap<String,Code> retVal = ArrayListMultimap.create();
    String system;
    List<ValueSet.ConceptSetComponent> concepts = vs.getCompose().getInclude();
    for (ValueSet.ConceptSetComponent concept : concepts) {
      system = concept.getSystem();
      for (ValueSet.ConceptReferenceComponent conceptRef : concept.getConcept()) {
        if (conceptRef.getDisplay() == null) {
          retVal.put(system,new Code(system,conceptRef.getCode(),system));
        } else {
          retVal.put(system,new Code(system,conceptRef.getCode(),conceptRef.getDisplay()));
        }
      }
    }
    return retVal;
  }

  public static OkHttpClient buildClient() {
    return new OkHttpClient.Builder().build();
  }

  static String requestAuthToken() {
    String token = null;
    if (VSAC_PASS == null || VSAC_USER == null) {
      return null;
    } else {
      //Order matters for request body, requiring an ordered map.
      Map<String, String> requestForm = new LinkedHashMap<>();
      requestForm.put("username", VSAC_USER);
      requestForm.put("password", VSAC_PASS);
      // Make request body.
      RequestBody requestBody = makeRequestBody(requestForm);
      //Create Request
      Request request = buildPostRequest(AUTH_URL, requestBody);
      try {
        Response response = client.newCall(request).execute();
        if (response.code() == 200) {
          assert response.body() != null;
          token = response.body().string();
        } else {
          token = null;
        }
        response.close();
      } catch (IOException | NullPointerException e) {
        // Could just be a logger warn statement to notify user they're not connected to
        // VSAC instead of a full blown stack trace.
        e.printStackTrace();
        token = null;
        // Check to make sure that params are correctly entered
        // eg. 'username' not 'user', 'password' not 'pass'.
      }
    }

    return token;
  }

  /**
   * Service tickets are a one-time use auth code for accessing value sets from VSAC.
   * A regular auth token can be used to acquire multiple service tickets.
   * @param authToken the auth token needed to make a VSAC request
   * @return a string representing a service ticket, which is a one use code to get a value set
   */
  static String getServiceTicket(String authToken) {
    String token;
    Map<String,String> requestForm = new LinkedHashMap<>();
    requestForm.put("service",SERVICE_URL);
    RequestBody requestBody = makeRequestBody(requestForm);
    Request request = buildPostRequest(AUTH_URL + "/" + authToken,requestBody);
    try {
      Response response = client.newCall(request).execute();
      assert response.body() != null;
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

  static Request buildGetRequest(String url) {
    //Makes requests of specified type, overloaded to switch between GET and POST.
    return new Request.Builder()
                        .url(url)
                        .get()
                        .build();
  }

  static Request buildPostRequest(String url, RequestBody body) {
    return new Request.Builder()
            .url(url)
            .post(body)
            .build();
  }

  static RequestBody makeRequestBody(Map<String, String> valuePairs) {
    // Builds generic request bodies from maps
    FormBody.Builder requestBuilder = new FormBody.Builder();
    for (String s : valuePairs.keySet()) {
      requestBuilder.add(s,valuePairs.get(s));
    }
    return requestBuilder.build();
  }

  private static HttpUrl makeGetUrl(Map<String, String> valuePairs) {
    // Constructs the url that is queried in the Get call.
    HttpUrl.Builder urlBuilder = new HttpUrl.Builder();

    // URL needs to be HTTPS or the request will be redirected, defaulting request type
    String urlScheme = "https";
    urlBuilder.scheme(urlScheme)
        .host(Terminology.VSAC_VALUE_SET_RETRIEVAL_URL)
        .addPathSegments("vsac/svs/RetrieveValueSet");
    for (String s : valuePairs.keySet()) {
      urlBuilder.setQueryParameter(s,valuePairs.get(s));
    }
    return urlBuilder.build();
  }

  static Map<String, ValueSet> getValueSets() {
    return valueSets;
  }
}
