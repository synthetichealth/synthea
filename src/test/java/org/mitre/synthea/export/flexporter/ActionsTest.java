package org.mitre.synthea.export.flexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.PractitionerRole.DaysOfWeek;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.Type;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.world.agents.Person;

public class ActionsTest {

  private static Map<String, String> buildApplyProfileAction(String profile, String applicability) {
    Map<String, String> map = new HashMap<>();
    map.put("profile", profile);
    map.put("applicability", applicability);
    return map;
  }

  private static Mapping testMapping;

  /**
   * Class setup - load the mapping file containing all tests.
   */
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
    IParser parser = FhirR4.getContext().newJsonParser();
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

    Actions.applyAction(b, action, null, null);

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

    Actions.applyAction(b, action, null, null);

    assertEquals("2022-02-22", i.getRecordedElement().getValueAsString());
  }

  @Test
  public void testSetValues_getField_diff_applicability() {
    Procedure p1 = new Procedure();
    Date startDate = new Date(0);
    Date endDate = new Date(1000000);
    p1.setPerformed(new Period().setStart(startDate).setEnd(endDate));

    Procedure p2 = new Procedure();
    p2.setPerformed(new DateTimeType(new Date(0)));

    Bundle b = new Bundle();
    b.addEntry().setResource(p1);
    b.addEntry().setResource(p2);

    Map<String, Object> action = getActionByName("testSetValues_getField_diff_applicability");

    Actions.applyAction(b, action, null, null);

    Extension e1 = p1.getExtensionByUrl("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-recorded");
    assertNotNull(e1);
    String d1 = ((DateTimeType) e1.getValue()).getValueAsString();
    assertEquals(0, ZonedDateTime.parse(d1).toInstant().getEpochSecond());

    Extension e2 = p2.getExtensionByUrl("http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-recorded");
    assertNotNull(e2);
    String d2 = ((DateTimeType) e2.getValue()).getValueAsString();
    assertEquals(0, ZonedDateTime.parse(d2).toInstant().getEpochSecond());
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

    Actions.applyAction(b, action, null, null);

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

    Actions.applyAction(b, action, null, null);

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

    Actions.applyAction(b, action, null, null);

    /*

 - name: testSetValues_object
   set_values:
     - applicability: Patient
       fields:
         - location: Patient.maritalStatus.coding
           value:
               system: http://snomedct.io
               code: "36629006"
               display: "Legally married (finding)"

     */

    Coding c = p.getMaritalStatus().getCodingFirstRep();

    assertEquals("http://snomedct.io", c.getSystem());
    assertEquals("36629006", c.getCode());
    assertEquals("Legally married (finding)", c.getDisplay());
  }

  @Test
  public void testSetValues_where() {
    testSetValues_encounter_common("testSetValues_where");
  }

  @Test
  public void testSetValues_indexed() {
    testSetValues_encounter_common("testSetValues_indexed");
  }

  @Test
  public void testSetValues_list() {
    testSetValues_encounter_common("testSetValues_list");
  }

  void testSetValues_encounter_common(String actionName) {
    Map<String, Object> action = getActionByName(actionName);

    Encounter e = new Encounter();
    Bundle b = new Bundle();
    b.addEntry().setResource(e);

    Actions.applyAction(b, action, null, null);

    assertNotNull(e.getServiceType());

    CodeableConcept serviceType = e.getServiceType();
    List<Coding> codings = serviceType.getCoding();
    assertNotNull(codings);
    assertEquals(2, codings.size());

    boolean seen229 = false;
    boolean seen355 = false;
    for (Coding coding : codings) {
      assertEquals("http://terminology.hl7.org/CodeSystem/service-type", coding.getSystem());
      String code = coding.getCode();
      if (code.equals("229")) {
        if (seen229) {
          fail("Code 229 seen multiple times in " + actionName);
        }
        seen229 = true;
      } else if (code.equals("355")) {
        if (seen355) {
          fail("Code 355 seen multiple times in " + actionName);
        }
        seen355 = true;
      } else {
        fail("Unexpected code found in " + actionName + ": " + code);
      }
    }
    assertTrue(seen229);
    assertTrue(seen355);
  }

  @Test
  public void testSetValues_listOfPrimitives() {
    Map<String, Object> action = getActionByName("testSetValues_listOfPrimitives");

    PractitionerRole pr = new PractitionerRole();
    Claim c = new Claim();
    Bundle b = new Bundle();
    b.addEntry().setResource(pr);
    b.addEntry().setResource(c);

    Actions.applyAction(b, action, null, null);

    assertEquals(1, pr.getAvailableTime().size());

    List<Enumeration<DaysOfWeek>> daysOfWeek = pr.getAvailableTimeFirstRep().getDaysOfWeek();
    assertEquals(3, daysOfWeek.size());

    assertEquals(DaysOfWeek.WED, daysOfWeek.get(0).getValue());
    assertEquals(DaysOfWeek.THU, daysOfWeek.get(1).getValue());
    assertEquals(DaysOfWeek.FRI, daysOfWeek.get(2).getValue());

    assertEquals(1, c.getItem().size());
    Claim.ItemComponent item = c.getItemFirstRep();

    List<PositiveIntType> diagnosisSequence = item.getDiagnosisSequence();
    assertEquals(6, diagnosisSequence.size());
    assertEquals(1, diagnosisSequence.get(0).getValue().intValue());
    assertEquals(1, diagnosisSequence.get(1).getValue().intValue());
    assertEquals(2, diagnosisSequence.get(2).getValue().intValue());
    assertEquals(3, diagnosisSequence.get(3).getValue().intValue());
    assertEquals(5, diagnosisSequence.get(4).getValue().intValue());
    assertEquals(8, diagnosisSequence.get(5).getValue().intValue());
  }

  @Test
  public void testSetValues_deeplyNested() {
    Map<String, Object> action = getActionByName("testSetValues_deeplyNested");

    Observation o = new Observation();
    Bundle b = new Bundle();
    b.addEntry().setResource(o);

    Actions.applyAction(b, action, null, null);

    CodeableConcept code = o.getCode();
    assertNotNull(code);
    assertEquals(1, code.getCoding().size());
    Coding coding = code.getCodingFirstRep();

    assertEquals("http://loinc.org", coding.getSystem());
    assertEquals("85354-9", coding.getCode());
    assertEquals("Blood pressure panel with all children optional", coding.getDisplay());

    assertEquals("Blood pressure systolic & diastolic", code.getText());

    assertEquals(2, o.getComponent().size());

    Observation.ObservationComponentComponent systolic = o.getComponent().get(0);

    code = systolic.getCode();
    assertNotNull(code);
    assertEquals(3, code.getCoding().size());

    coding = code.getCoding().get(0);

    assertEquals("http://loinc.org", coding.getSystem());
    assertEquals("8480-6", coding.getCode());
    assertEquals("Systolic blood pressure", coding.getDisplay());

    coding = code.getCoding().get(1);

    assertEquals("http://snomed.info/sct", coding.getSystem());
    assertEquals("271649006", coding.getCode());
    assertEquals("Systolic blood pressure", coding.getDisplay());

    coding = code.getCoding().get(2);

    assertEquals("http://acme.org/devices/clinical-codes", coding.getSystem());
    assertEquals("bp-s", coding.getCode());
    assertEquals("Systolic Blood pressure", coding.getDisplay());


    Quantity q = systolic.getValueQuantity();

    assertEquals(107, q.getValue().intValue());
    assertEquals("mmHg", q.getUnit());
    assertEquals("http://unitsofmeasure.org", q.getSystem());
    assertEquals("mm[Hg]", q.getCode());

    Observation.ObservationComponentComponent diastolic = o.getComponent().get(1);
    code = diastolic.getCode();
    assertNotNull(code);
    assertEquals(1, code.getCoding().size());

    coding = code.getCoding().get(0);

    assertEquals("http://loinc.org", coding.getSystem());
    assertEquals("8462-4", coding.getCode());
    assertEquals("Diastolic blood pressure", coding.getDisplay());

    q = diastolic.getValueQuantity();

    assertEquals(60, q.getValue().intValue());
    assertEquals("mmHg", q.getUnit());
    assertEquals("http://unitsofmeasure.org", q.getSystem());
    assertEquals("mm[Hg]", q.getCode());
  }

  @Test
  public void testKeepResources() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");


    Map<String, Object> action = getActionByName("testKeepResources");

    Actions.applyAction(b, action, null, null);

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

    Actions.applyAction(b, action, null, null);

    countProvenance = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.Provenance).count();

    assertEquals(0, countProvenance);
  }
  
  @Test
  public void testDeleteResourcesByFhirPath() throws Exception {
	    Bundle b = new Bundle();
	    
	    MedicationRequest m = new MedicationRequest();
	    CodeableConcept cc = new CodeableConcept();
	    cc.addCoding().setCode("1191013");
	    m.setMedication(cc);
	    b.addEntry().setResource(m);
	    Map<String, Object> action = getActionByName("testDeleteResourcesByFhirPath");

	    Actions.applyAction(b, action, null, null);

	    long countProvenance = b.getEntry().stream()
	        .filter(bec -> bec.getResource().getResourceType() == ResourceType.MedicationRequest).count();

	    assertEquals(0, countProvenance);
	  }

  @Test
  public void testDeleteResourcesCascade() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");

    System.out.println(b.getEntry().size());

    Map<String, Object> action = getActionByName("testDeleteResourcesCascade");

    // action deletes Patient resource, everything should reference back to Patient

    Actions.applyAction(b, action, null, null);

    System.out.println(b.getEntry().size());
    b.getEntry().forEach(e -> System.out.println(e.getResource().getResourceType()));
  }

  @Test
  public void testDateFilter() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");

    System.out.println(b.getEntry().size());
    Map<String, Object> action = getActionByName("testDateFilter");

    Actions.applyAction(b, action, null, null);

    System.out.println(b.getEntry().size());
  }

  @Test
  public void testCreateResources_createSingle() throws Exception {
    Bundle b = loadFixtureBundle("sample_complete_patient.json");

    long countSR = b.getEntry().stream()
        .filter(bec -> bec.getResource().getResourceType() == ResourceType.ServiceRequest).count();
    assertEquals(0, countSR);


    Map<String, Object> action = getActionByName("testCreateResources_createSingle");

    Actions.applyAction(b, action, null, null);

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
  public void testShiftDates() throws Exception {
    Patient p = new Patient();
    p.addName().addGiven("Terry").setFamily("Teal");
    DateType date = new DateType();
    date.fromStringValue("1999-09-29");
    p.setBirthDateElement(date);

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    Observation o = new Observation();
    Period period = new Period();
    period.setStartElement(new DateTimeType("1999-10-05T10:24:01-05:00"));
    period.setEndElement(new DateTimeType("1999-10-06T10:24:01-05:00"));
    o.setEffective(period);
    o.setIssuedElement(new InstantType("1999-10-06T10:24:01-05:00"));
    o.setValue(new TimeType("10:24:01"));

    b.addEntry().setResource(o);

    Map<String, Object> action = getActionByName("testShiftDates");
    Actions.applyAction(b, action, null, null);

    assertEquals("1998-09-29", p.getBirthDateElement().getValueAsString());
    assertEquals("1998-10-05T10:24:01-05:00", period.getStartElement().getValueAsString());
    assertEquals("1998-10-06T10:24:01-05:00", period.getEndElement().getValueAsString());
  }

  @Test
  public void testExec() throws Exception {
    Map<String, Object> action = getActionByName("testExecuteScript");

    Patient p = new Patient();
    p.addName().addGiven("Cristina").setFamily("Crimson");
    DateType date = new DateType();
    date.fromStringValue("1999-09-29");
    p.setBirthDateElement(date);

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    Bundle updatedBundle = Actions.applyAction(b, action, null, new FlexporterJavascriptContext());

    /*
          function apply(bundle) {
            bundle['entry'][0]['resource']['meta'] = {profile: ['http://example.com/dummy-profile']}
          }

          function apply2(resource, bundle) {
           if (resource.resourceType == 'Patient') {
             resource.birthDate = '2022-02-22';
           }
         }

     */

    Patient outPatient = (Patient) updatedBundle.getEntryFirstRep().getResource();

    assertEquals("http://example.com/dummy-profile", outPatient.getMeta().getProfile().get(0).getValueAsString());

    assertEquals("2022-02-22", outPatient.getBirthDateElement().getValueAsString());
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

    Actions.applyAction(b, action, null, null);

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
      assertTrue(proc.getCode().equalsDeep(sr.getCode()));
    }

    // we removed each SR as we checked it, to ensure there are none left
    assertEquals(0, serviceRequests.size());

  }

  @Test
  public void testCreateResources_createBasedOnState() throws Exception {
    Patient p = new Patient();
    p.setId("mypatient");

    Bundle b = new Bundle();
    b.setType(BundleType.COLLECTION);
    b.addEntry().setResource(p);

    Person person = new Person(0L);
    person.history = new LinkedList<State>();
    Module m = Module.getModuleByPath("sinusitis");

    // this is cheating but the alternative is to pick a seed that goes through this path
    // which is flaky
    for (String stateName : List.of("Initial", "Potential_Onset", "Bacterial_Infection_Starts",
        "Doctor_Visit", "Penicillin_Allergy_Check", "Prescribe_Alternative_Antibiotic")) {
      State s = m.getState(stateName).clone();

      s.entered = 120_000L;
      s.exited = 120_000L;

      person.history.add(0, s);
    }
    person.attributes.put(m.name, person.history);


    Map<String, Object> action = getActionByName("testCreateResources_createBasedOnState");

    Actions.applyAction(b, action, person, null);



    List<BundleEntryComponent> entries = b.getEntry();
    assertEquals(2, entries.size());
    /*
 - name: testCreateResources_createBasedOnState
   create_resource:
     - resourceType: MedicationRequest
       based_on:
         module: Sinusitis
         state: Prescribe_Alternative_Antibiotic
       profiles:
         - http://hl7.org/fhir/us/qicore/StructureDefinition/qicore-medicationnotrequested
       fields:
         - location: MedicationRequest.doNotPerform
           value: "true"
         - location: MedicationRequest.status
           value: completed
         - location: MedicationRequest.intent
           value: order
         # - location: MedicationRequest.encounter.reference
         #   value: $getField([Procedure.encounter.reference])
         - location: MedicationRequest.subject.reference
           value: $findRef([Patient])
         # - location: MedicationRequest.medicationCodeableConcept
         #   value: $getField([Procedure.code])
         - location: MedicationRequest.authoredOn
           value: $getField([State.entered])
     */

    Resource newResource = entries.get(1).getResource();

    assertEquals("MedicationRequest", newResource.getResourceType().toString());

    MedicationRequest mr = (MedicationRequest) newResource;

    assertEquals(true, mr.getDoNotPerform());
    assertEquals("COMPLETED", mr.getStatus().toString());
    assertEquals("ORDER", mr.getIntent().toString());
    assertEquals("Patient/mypatient", mr.getSubject().getReference());

    // note: the date conversion loses the millis part
    // so this will only work if the value % 1000 == 0
    assertEquals(120_000L, mr.getAuthoredOn().toInstant().toEpochMilli());
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

    Actions.applyAction(b, action, p, null);

    Patient patient = (Patient) b.getEntryFirstRep().getResource();
    HumanName name = patient.getNameFirstRep();

    assertEquals("Robert", name.getGivenAsSingleString());
    assertEquals("Rainbow", name.getFamily());
    assertEquals("Robert Rainbow", name.getText());
  }

}
