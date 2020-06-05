package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mitre.synthea.TestHelper.LOINC_OID;
import static org.mitre.synthea.TestHelper.LOINC_URI;
import static org.mitre.synthea.TestHelper.SNOMED_URI;
import static org.mitre.synthea.TestHelper.getDstu2FhirContext;
import static org.mitre.synthea.TestHelper.getR4FhirContext;
import static org.mitre.synthea.TestHelper.getStu3FhirContext;
import static org.mitre.synthea.TestHelper.getTxRecordingSource;
import static org.mitre.synthea.TestHelper.isHttpRecordingEnabled;
import static org.mitre.synthea.TestHelper.wiremockOptions;
import static org.mitre.synthea.TestHelper.years;

import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.helpers.TerminologyClient;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CodeResolveAndExportTest {

  private static final String EXPECTED_REASON_CODE = "242332002";
  private static final String EXPECTED_REASON_DISPLAY =
      "Accidental ingestion of matrimony vine berries";
  private static final String OBSERVATION_CODE = "11376-1";
  private static final String OBSERVATION_DISPLAY = "Injury location";
  private static final String EXPECTED_VALUE_CODE = "LA14090-7";
  private static final String EXPECTED_VALUE_DISPLAY = "Trade or service area";
  private Person person;
  private long time;
  private Path stu3OutputPath;
  private Path r4OutputPath;
  private Path dstu2OutputPath;
  private Path ccdaOutputPath;

  @Rule
  public WireMockRule mockTerminologyService = new WireMockRule(wiremockOptions()
      .usingFilesUnderDirectory("src/test/resources/wiremock/CodeResolveAndExportTest"));

  /**
   * Prepare for each test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    TerminologyClient terminologyClient = getR4FhirContext()
        .newRestfulClient(TerminologyClient.class, mockTerminologyService.baseUrl() + "/fhir");
    RandomCodeGenerator.initialize(terminologyClient);
    if (isHttpRecordingEnabled()) {
      WireMock.startRecording(getTxRecordingSource());
    }

    TestHelper.exportOff();
    Config.set("exporter.ccda.export", "true");
    Config.set("exporter.fhir.export", "true");
    Config.set("exporter.fhir_stu3.export", "true");
    Config.set("exporter.fhir_dstu2.export", "true");
    Config.set("generate.terminology_service_url", mockTerminologyService.baseUrl() + "/fhir");

    person = new Person(12345L);
    time = new SimpleDateFormat("yyyy-MM-dd").parse("2013-06-10").getTime();
    person.attributes.put(Person.ID, "12345");
    person.attributes.put(Person.NAME, "Foo Bar");
    person.attributes.put(Person.BIRTHDATE, time - years(30));
    person.attributes.put(Person.ADDRESS, "12 Palm Ave");
    person.attributes.put(Person.CITY, "Bandligar");
    person.attributes.put(Person.STATE, "Gromnigar");
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.FIRST_LANGUAGE, "hindi");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "other");
    person.attributes.put(Person.ETHNICITY, "other");
    person.attributes.put(Person.SEXUAL_ORIENTATION, "bisexual");
    person.attributes.put(Person.SOCIOECONOMIC_CATEGORY, "Middle");
    person.attributes.put(Person.EDUCATION, "Middle");

    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Location location = new Location(Generator.DEFAULT_STATE, null);
    location.assignPoint(person, location.randomCityName(person.random));
    Provider.loadProviders(location, 1L);

    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Payer.loadPayers(new Location(Generator.DEFAULT_STATE, null));

    File stu3OutputDirectory = Exporter.getOutputFolder("fhir_stu3", person);
    stu3OutputPath = stu3OutputDirectory.toPath().resolve(Exporter.filename(person, "", "json"));
    File r4OutputDirectory = Exporter.getOutputFolder("fhir", person);
    r4OutputPath = r4OutputDirectory.toPath().resolve(Exporter.filename(person, "", "json"));
    File dstu2OutputDirectory = Exporter.getOutputFolder("fhir_dstu2", person);
    dstu2OutputPath = dstu2OutputDirectory.toPath().resolve(Exporter.filename(person, "", "json"));
    File ccdaOutputDirectory = Exporter.getOutputFolder("ccda", person);
    ccdaOutputPath = ccdaOutputDirectory.toPath().resolve(Exporter.filename(person, "", "xml"));
  }

  @Test
  public void resolveAndExportEncounterCodes()
      throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
    Encounter encounter = person.encounterStart(time, EncounterType.EMERGENCY);
    String reasonCode = "417981005";
    String reasonDisplay = "Exposure to blood and/or body fluid";
    encounter.reason = new Code(SNOMED_URI, reasonCode, reasonDisplay);
    encounter.reason.valueSet = SNOMED_URI + "?fhir_vs=ecl/<418307001";
    encounter.codes.add(encounter.reason);

    String observationDisplay = OBSERVATION_DISPLAY;
    Code observationType = new Code(LOINC_URI, OBSERVATION_CODE, observationDisplay);
    String observationValueCode = "LA14088-1";
    String observationValueDisplay = "Sports or recreational area";
    Code observationValue = new Code(LOINC_URI, observationValueCode, observationValueDisplay);
    observationValue.valueSet = "http://loinc.org/vs/LL1051-3";
    encounter.addObservation(time, observationType.code, observationValue, observationDisplay);

    Exporter.export(person, time);

    verifyEncounterCodeStu3();
    verifyEncounterCodeR4();
    verifyEncounterCodeDstu2();
    verifyEncounterCodeCcda();
  }

  private void verifyEncounterCodeStu3() throws FileNotFoundException {
    InputStream inputStream = new FileInputStream(stu3OutputPath.toFile().getAbsolutePath());
    Bundle bundle = (Bundle) getStu3FhirContext().newJsonParser().parseResource(inputStream);

    // Find encounter reason code.
    Optional<BundleEntryComponent> maybeEncounterEntry = bundle.getEntry().stream()
        .filter(entry -> entry.getResource().getResourceType().equals(ResourceType.Encounter))
        .findFirst();
    assertTrue(maybeEncounterEntry.isPresent());

    org.hl7.fhir.dstu3.model.Encounter encounterResource =
        (org.hl7.fhir.dstu3.model.Encounter) maybeEncounterEntry.get().getResource();
    assertEquals(encounterResource.getReason().size(), 1);
    CodeableConcept encounterReason = encounterResource.getReason().get(0);
    assertEquals(encounterReason.getCoding().size(), 1);
    Coding reasonCoding = encounterReason.getCoding().get(0);

    // Check encounter reason code.
    assertEquals(SNOMED_URI, reasonCoding.getSystem());
    assertEquals(EXPECTED_REASON_CODE, reasonCoding.getCode());
    assertEquals(EXPECTED_REASON_DISPLAY, reasonCoding.getDisplay());

    Optional<BundleEntryComponent> maybeObservationEntry = bundle.getEntry().stream()
        .filter(entry -> entry.getResource().getResourceType().equals(ResourceType.Observation))
        .findFirst();
    assertTrue(maybeObservationEntry.isPresent());

    // Find observation type code.
    org.hl7.fhir.dstu3.model.Observation observationResource =
        (org.hl7.fhir.dstu3.model.Observation) maybeObservationEntry.get().getResource();
    CodeableConcept observationType = observationResource.getCode();
    assertNotNull(observationType);
    assertEquals(observationType.getCoding().size(), 1);
    Coding observationTypeCoding = observationType.getCoding().get(0);

    // Check observation type code.
    assertEquals(LOINC_URI, observationTypeCoding.getSystem());
    assertEquals(OBSERVATION_CODE, observationTypeCoding.getCode());
    assertEquals(OBSERVATION_DISPLAY, observationTypeCoding.getDisplay());

    // Find observation value code.
    CodeableConcept observationValue = observationResource.getValueCodeableConcept();
    assertNotNull(observationValue);
    assertEquals(observationValue.getCoding().size(), 1);
    Coding observationValueCoding = observationValue.getCoding().get(0);

    // Check observation value code.
    assertEquals(LOINC_URI, observationValueCoding.getSystem());
    assertEquals(EXPECTED_VALUE_CODE, observationValueCoding.getCode());
    assertEquals(EXPECTED_VALUE_DISPLAY, observationValueCoding.getDisplay());
  }

  private void verifyEncounterCodeR4() throws FileNotFoundException {
    InputStream inputStream = new FileInputStream(r4OutputPath.toFile().getAbsolutePath());
    org.hl7.fhir.r4.model.Bundle bundle = (org.hl7.fhir.r4.model.Bundle) getR4FhirContext()
        .newJsonParser().parseResource(inputStream);

    // Find encounter reason code.
    Optional<org.hl7.fhir.r4.model.Bundle.BundleEntryComponent> maybeEncounterEntry = bundle
        .getEntry().stream()
        .filter(entry -> entry.getResource().getResourceType().equals(
            org.hl7.fhir.r4.model.ResourceType.Encounter))
        .findFirst();
    assertTrue(maybeEncounterEntry.isPresent());

    org.hl7.fhir.r4.model.Encounter encounterResource = 
        (org.hl7.fhir.r4.model.Encounter) maybeEncounterEntry.get().getResource();
    assertEquals(encounterResource.getReasonCode().size(), 1);
    org.hl7.fhir.r4.model.CodeableConcept encounterReason = encounterResource.getReasonCode()
        .get(0);
    assertEquals(encounterReason.getCoding().size(), 1);
    org.hl7.fhir.r4.model.Coding reasonCoding = encounterReason.getCoding().get(0);

    // Check encounter reason code.
    assertEquals(SNOMED_URI, reasonCoding.getSystem());
    assertEquals(EXPECTED_REASON_CODE, reasonCoding.getCode());
    assertEquals(EXPECTED_REASON_DISPLAY, reasonCoding.getDisplay());

    Optional<org.hl7.fhir.r4.model.Bundle.BundleEntryComponent> maybeObservationEntry = 
        bundle.getEntry().stream()
        .filter(entry -> entry.getResource().getResourceType().equals(
            org.hl7.fhir.r4.model.ResourceType.Observation))
        .findFirst();
    assertTrue(maybeObservationEntry.isPresent());

    // Find observation type code.
    org.hl7.fhir.r4.model.Observation observationResource =
        (org.hl7.fhir.r4.model.Observation) maybeObservationEntry.get().getResource();
    org.hl7.fhir.r4.model.CodeableConcept observationType = observationResource.getCode();
    assertNotNull(observationType);
    assertEquals(observationType.getCoding().size(), 1);
    org.hl7.fhir.r4.model.Coding observationTypeCoding = observationType.getCoding().get(0);

    // Check observation type code.
    assertEquals(LOINC_URI, observationTypeCoding.getSystem());
    assertEquals(OBSERVATION_CODE, observationTypeCoding.getCode());
    assertEquals(OBSERVATION_DISPLAY, observationTypeCoding.getDisplay());

    // Find observation value code.
    org.hl7.fhir.r4.model.CodeableConcept observationValue = 
        observationResource.getValueCodeableConcept();
    assertNotNull(observationValue);
    assertEquals(observationValue.getCoding().size(), 1);
    org.hl7.fhir.r4.model.Coding observationValueCoding = observationValue.getCoding().get(0);

    // Check observation value code.
    assertEquals(LOINC_URI, observationValueCoding.getSystem());
    assertEquals(EXPECTED_VALUE_CODE, observationValueCoding.getCode());
    assertEquals(EXPECTED_VALUE_DISPLAY, observationValueCoding.getDisplay());
  }

  private void verifyEncounterCodeDstu2() throws FileNotFoundException {
    InputStream inputStream = new FileInputStream(dstu2OutputPath.toFile().getAbsolutePath());
    ca.uhn.fhir.model.dstu2.resource.Bundle bundle = 
        (ca.uhn.fhir.model.dstu2.resource.Bundle) getDstu2FhirContext().newJsonParser()
            .parseResource(inputStream);

    // Find encounter reason code.
    Optional<Entry> maybeEncounterEntry = bundle.getEntry().stream()
        .filter(entry -> entry.getResource().getResourceName().equals(
            org.hl7.fhir.dstu2.model.ResourceType.Encounter.name()))
        .findFirst();
    assertTrue(maybeEncounterEntry.isPresent());

    ca.uhn.fhir.model.dstu2.resource.Encounter encounterResource = 
        (ca.uhn.fhir.model.dstu2.resource.Encounter) maybeEncounterEntry.get().getResource();
    assertEquals(encounterResource.getReason().size(), 1);
    CodeableConceptDt encounterReason = encounterResource.getReason().get(0);
    assertEquals(encounterReason.getCoding().size(), 1);
    CodingDt reasonCoding = encounterReason.getCoding().get(0);

    // Check encounter reason code.
    assertEquals(SNOMED_URI, reasonCoding.getSystem());
    assertEquals(EXPECTED_REASON_CODE, reasonCoding.getCode());
    assertEquals(EXPECTED_REASON_DISPLAY, reasonCoding.getDisplay());

    Optional<Entry> maybeObservationEntry = bundle.getEntry().stream()
        .filter(entry -> entry.getResource().getResourceName().equals(
            org.hl7.fhir.dstu2.model.ResourceType.Observation.name()))
        .findFirst();
    assertTrue(maybeObservationEntry.isPresent());

    // Find observation type code.
    ca.uhn.fhir.model.dstu2.resource.Observation observationResource =
        (ca.uhn.fhir.model.dstu2.resource.Observation) maybeObservationEntry.get().getResource();
    CodeableConceptDt observationType = observationResource.getCode();
    assertNotNull(observationType);
    assertEquals(observationType.getCoding().size(), 1);
    CodingDt observationTypeCoding = observationType.getCoding().get(0);

    // Check observation type code.
    assertEquals(LOINC_URI, observationTypeCoding.getSystem());
    assertEquals(OBSERVATION_CODE, observationTypeCoding.getCode());
    assertEquals(OBSERVATION_DISPLAY, observationTypeCoding.getDisplay());

    // Find observation value code.
    CodeableConceptDt observationValue = (CodeableConceptDt) observationResource.getValue();
    assertNotNull(observationValue);
    assertEquals(observationValue.getCoding().size(), 1);
    CodingDt observationValueCoding = observationValue.getCoding().get(0);

    // Check observation value code.
    assertEquals(LOINC_URI, observationValueCoding.getSystem());
    assertEquals(EXPECTED_VALUE_CODE, observationValueCoding.getCode());
    assertEquals(EXPECTED_VALUE_DISPLAY, observationValueCoding.getDisplay());
  }

  private void verifyEncounterCodeCcda()
      throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
    InputStream inputStream = new FileInputStream(ccdaOutputPath.toFile().getAbsolutePath());

    // Find the encounter reason code.
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(inputStream);
    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();
    XPathExpression expr = xpath.compile("/ClinicalDocument/component/structuredBody"
        + "/component/section/entry/encounter/code");

    NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());
    Node coding = nodeList.item(0);
    String system = coding.getAttributes().getNamedItem("codeSystem").getNodeValue();
    String code = coding.getAttributes().getNamedItem("code").getNodeValue();
    String display = coding.getAttributes().getNamedItem("displayName").getNodeValue();

    // Check the encounter reason code.
    assertEquals("2.16.840.1.113883.6.96", system);
    assertEquals(EXPECTED_REASON_CODE, code);
    assertEquals(EXPECTED_REASON_DISPLAY, display);

    // Find the observation type code.
    expr = xpath.compile("/ClinicalDocument/component/structuredBody/component/section" 
        + "/entry/organizer/component/observation/code");

    nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());
    coding = nodeList.item(0);
    system = coding.getAttributes().getNamedItem("codeSystem").getNodeValue();
    code = coding.getAttributes().getNamedItem("code").getNodeValue();
    display = coding.getAttributes().getNamedItem("displayName").getNodeValue();

    // Check the observation type code.
    assertEquals(LOINC_OID, system);
    assertEquals(OBSERVATION_CODE, code);
    assertEquals(OBSERVATION_DISPLAY, display);

    // Check that there are no translations for the observation type code.
    expr = xpath.compile("/ClinicalDocument/component/structuredBody/component/section"
        + "/entry/organizer/component/observation/code/translation");
    nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    assertEquals(0, nodeList.getLength());

    // Find the observation value code.
    expr = xpath.compile("/ClinicalDocument/component/structuredBody/component/section"
        + "/entry/organizer/component/observation/value");

    nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());
    coding = nodeList.item(0);
    String type = coding.getAttributes().getNamedItem("xsi:type").getNodeValue();
    system = coding.getAttributes().getNamedItem("codeSystem").getNodeValue();
    code = coding.getAttributes().getNamedItem("code").getNodeValue();
    display = coding.getAttributes().getNamedItem("displayName").getNodeValue();
    assertEquals(0, coding.getChildNodes().getLength());

    // Check the observation value code.
    assertEquals("CD", type);
    assertEquals(LOINC_OID, system);
    assertEquals(EXPECTED_VALUE_CODE, code);
    assertEquals(EXPECTED_VALUE_DISPLAY, display);
  }

  /**
   * Clean up after each test.
   */
  @After
  public void tearDown() {
    if (isHttpRecordingEnabled()) {
      WireMock.stopRecording();
    }
    
    List<Path> pathsToDelete =
        Arrays.asList(stu3OutputPath, r4OutputPath, dstu2OutputPath, ccdaOutputPath);
    for (Path outputPath : pathsToDelete) {
      File outputFile = outputPath.toFile();
      boolean delete = outputFile.delete();
      assertTrue(delete);
    }
  }
}