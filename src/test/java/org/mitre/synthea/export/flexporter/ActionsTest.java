package org.mitre.synthea.export.flexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Type;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class ActionsTest {

  private static Map<String, String> buildApplyProfileAction(String profile, String applicability) {
    Map<String, String> map = new HashMap<>();
    map.put("profile", profile);
    map.put("applicability", applicability);
    return map;
  }

  private static Mapping testMapping;

  @BeforeClass
  public static void setupClass() throws FileNotFoundException {
    ClassLoader classLoader = ActionsTest.class.getClassLoader();
    File file = new File(classLoader.getResource("flexporter/test_mapping.yaml").getFile());

    testMapping = Mapping.parseMapping(file);
  }

  @AfterClass
  public static void tearDown() {
    testMapping = null;
  }

  private static Map<String, Object> getActionByName(String name) {
    return testMapping.actions.stream().filter(a -> a.get("name").equals(name)).findFirst().get();
  }

  private static Bundle loadFixtureBundle(String filename) throws IOException {
    IParser parser = FhirPathUtils.FHIR_CTX.newJsonParser().setPrettyPrint(true);
    ClassLoader classLoader = ActionsTest.class.getClassLoader();
    File file = new File(classLoader.getResource("flexporter/" + filename).getFile());

    String fhirJson = new String(Files.readAllBytes(file.toPath()));
    return parser.parseResource(Bundle.class, fhirJson);
  }



  @Test
  public void testApplyProfiles() {
    Patient p = new Patient();
    p.addName().addGiven("Bobby").setFamily("Brown");

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    List<Map<String, String>> actions =
        Arrays.asList(buildApplyProfileAction("http://example.com/Patient", "Patient"),
            buildApplyProfileAction("http://example.com/Observation", "Observation"));

    Actions.applyProfiles(b, actions);

    assertNotNull(p.getMeta());
    assertNotNull(p.getMeta().getProfile());
    assertEquals(1, p.getMeta().getProfile().size());
    assertEquals("http://example.com/Patient", p.getMeta().getProfile().get(0).getValueAsString());
  }


  @Test
  public void testApplyProfilesNoMatch() {
    Patient p = new Patient();
    p.addName().addGiven("Willie").setFamily("White");

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    List<Map<String, String>> actions =
        Arrays.asList(buildApplyProfileAction("http://example.com/Observation", "Observation"));

    Actions.applyProfiles(b, actions);

    assertTrue(p.getMeta() == null || p.getMeta().getProfile() == null
        || p.getMeta().getProfile().size() == 0);
  }


  @Test
  public void testApplyProfilesComplexFhirPath() {
    Patient p = new Patient();
    p.addName().addGiven("Barry").setFamily("Black");
    p.setDeceased(new BooleanType(true));

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    List<Map<String, String>> actions =
        Arrays.asList(buildApplyProfileAction("http://example.com/Patient", "Patient"),
            buildApplyProfileAction("http://example.com/DeceasedPatient",
                "(Patient.deceased as boolean) = true"));

    Actions.applyProfiles(b, actions);

    assertNotNull(p.getMeta());
    assertNotNull(p.getMeta().getProfile());
    assertEquals(2, p.getMeta().getProfile().size());
    assertEquals("http://example.com/Patient", p.getMeta().getProfile().get(0).getValueAsString());
    assertEquals("http://example.com/DeceasedPatient",
        p.getMeta().getProfile().get(1).getValueAsString());
  }


  @Test
  public void testSetValues() {
    Patient p = new Patient();
    p.addName().addGiven("Gary").setFamily("Green");

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    Map<String, Object> action = getActionByName("testSetValues");

    Actions.applyAction(b, action, null);

    assertEquals("1987-06-05", p.getBirthDateElement().getValueAsString());
  }


  @Test
  public void testSetValues_getField() {
    Immunization i = new Immunization();

    DateTimeType date = new DateTimeType();
    date.fromStringValue("2022-02-22");
    i.setOccurrence(date);

    Bundle b = new Bundle();
    b.addEntry().setResource(i);

    Map<String, Object> action = getActionByName("testSetValues_getField");

    Actions.applyAction(b, action, null);

    assertEquals("2022-02-22", i.getRecordedElement().getValueAsString());
  }

  @Test
  public void testSetValues_overwrite() {
    Observation o = new Observation();

    DateTimeType date = new DateTimeType();
    date.fromStringValue("2009-10-26T06:44:52-04:00");
    o.setEffective(date);

    Bundle b = new Bundle();
    b.addEntry().setResource(o);

    Map<String, Object> action = getActionByName("testSetValues_overwrite");

    Actions.applyAction(b, action, null);

    Type effective = o.getEffective();

    assertTrue(effective instanceof InstantType);

    InstantType effectiveInstant = (InstantType) effective;

    assertEquals("2009-10-26T06:44:52-04:00", effectiveInstant.getValueAsString());
  }

  @Test
  public void testSetValues_transform() {
    Patient p = new Patient();
    p.addName().addGiven("Cristina").setFamily("Crimson");
    DateType date = new DateType();
    date.fromStringValue("1999-09-29");
    p.setBirthDateElement(date);

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    Map<String, Object> action = getActionByName("testSetValues_transform");

    Actions.applyAction(b, action, null);

    // NOTE: this expected value may change if we ever add randomness to the date -> dateTime
    // transform
    assertEquals("1999-09-29T00:00:00Z",
        ((DateTimeType) date.getExtension().get(0).getValue()).getValueAsString());
  }

  @Test
  public void testSetValues_object() {
    Patient p = new Patient();
    p.addName().addGiven("Mel").setFamily("Maroon");

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    Map<String, Object> action = getActionByName("testSetValues_object");

    Actions.applyAction(b, action, null);
    
    System.out.println(b);
  }
  
  @Test
  public void testKeepResources() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");


    Map<String, Object> action = getActionByName("testKeepResources");

    Actions.applyAction(b, action, null);

    Set<String> expectedResourceTypes =
        new HashSet<>(Arrays.asList("Patient", "Encounter", "Condition"));

    for (BundleEntryComponent bec : b.getEntry()) {
      Resource resource = bec.getResource();
      String resourceType = resource.getResourceType().toString();
      assertTrue(resourceType, expectedResourceTypes.contains(resourceType));
    }
  }

  @Test
  public void testDeleteResources() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");

    long countProvenance = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.Provenance).count();

    // this is just making sure the fixture actually contains the thing we want to delete
    assertEquals(1, countProvenance);
    Map<String, Object> action = getActionByName("testDeleteResources");

    Actions.applyAction(b, action, null);

    countProvenance = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.Provenance).count();

    assertEquals(0, countProvenance);

  }

  @Test
  public void testCreateResources_createSingle() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");

    long countSR = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.ServiceRequest).count();
    assertEquals(0, countSR);


    Map<String, Object> action = getActionByName("testCreateResources_createSingle");

    Actions.applyAction(b, action, null);

    List<Resource> serviceRequests = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.ServiceRequest)
        .map(bec -> bec.getResource()).collect(Collectors.toList());

    assertEquals(1, serviceRequests.size());

    ServiceRequest createdSR = (ServiceRequest) serviceRequests.get(0);

    assertEquals("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-servicerequest",
        createdSR.getMeta().getProfile().get(0).getValue());

    assertEquals("active", createdSR.getStatus().toCode());

    Patient p = (Patient) b.getEntryFirstRep().getResource();
    assertEquals(p.getId(), createdSR.getSubject().getReference());
  }

  @Test
  public void testCreateResources_createBasedOn() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");

    long countSR = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.ServiceRequest)
        .count();
    assertEquals(0, countSR);

    
    List<Procedure> procedures = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.Procedure)
        .map(bec -> (Procedure) bec.getResource())
        .collect(Collectors.toList());
    
    Map<String, Object> action = getActionByName("testCreateResources_createBasedOn");

    Actions.applyAction(b, action, null);

    // there should now be one ServiceRequest per Procedure

    // get a map of id --> servicerequest, for easy lookups 
    Map<String, ServiceRequest> serviceRequests = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.ServiceRequest)
        .map(bec -> (ServiceRequest) bec.getResource())
        .collect(Collectors.toMap(ServiceRequest::getId, Function.identity()));

    assertEquals(procedures.size(), serviceRequests.size());

    String patientId = b.getEntryFirstRep().getResource().getId();
    
    // iterate over the procedures so we can find the servicerequest for each
    for (Procedure proc : procedures) {
      
      // "ServiceRequest/".length == 15
      String basedOn = proc.getBasedOnFirstRep().getReference().substring(15);
      
      ServiceRequest sr = serviceRequests.remove(basedOn);
      assertNotNull(sr);
      assertEquals("plan", sr.getIntent().toCode());
      assertEquals(patientId, sr.getSubject().getReference());
      assertEquals(proc.getEncounter().getReference(), sr.getEncounter().getReference());
    }
    
    // we removed each SR as we checked it, to ensure there are none left
    assertEquals(0, serviceRequests.size());
    
  }

  @Test
  public void testGetAttribute() throws Exception {
    Bundle b = new Bundle();
    b.setType(BundleType.COLLECTION);
    Person p = new Person(0L);

    String firstName = "Robert";
    String lastName = "Rainbow";
    p.attributes.put(Person.FIRST_NAME, firstName);
    p.attributes.put(Person.LAST_NAME, lastName);
    p.attributes.put(Person.NAME, firstName + " " + lastName);
    Map<String, Object> action = getActionByName("testCreateResources_getAttribute");

    Actions.applyAction(b, action, p);

    Patient patient = (Patient) b.getEntryFirstRep().getResource();
    HumanName name = patient.getNameFirstRep();

    assertEquals("Robert", name.getGivenAsSingleString());
    assertEquals("Rainbow", name.getFamily());
    assertEquals("Robert Rainbow", name.getText());
  }

}
