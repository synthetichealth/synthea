package org.mitre.synthea.export.flexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Ignore;
import org.junit.Test;
import org.mitre.synthea.export.FhirR4;


public class CustomFHIRPathResourceGeneratorR4Test {

  @SuppressWarnings("unused")
  private static void logPatientJson(Patient p) {
    String patientJson = FhirR4.getContext().newJsonParser().setPrettyPrint(true)
        .encodeResourceToString(p);

    System.out.println(patientJson);
  }

  private Patient createPatient(Map<String, Object> fhirPathMapping) {
    CustomFHIRPathResourceGeneratorR4<Patient> fhirPathGenerator =
        new CustomFHIRPathResourceGeneratorR4<>();
    fhirPathGenerator.setMapping(fhirPathMapping);
    return fhirPathGenerator.generateResource(Patient.class);
  }

  private void updatePatient(Patient p, Map<String, Object> fhirPathMapping) {
    CustomFHIRPathResourceGeneratorR4<Patient> fhirPathGenerator =
        new CustomFHIRPathResourceGeneratorR4<>();
    fhirPathGenerator.setResource(p);
    fhirPathGenerator.setMapping(fhirPathMapping);
    fhirPathGenerator.generateResource(Patient.class);
  }

  @Test
  public void testSimpleField() {
    Map<String, Object> fhirPathMapping = new HashMap<>();
    fhirPathMapping.put("Patient.deceasedBoolean", "false");
    Patient patient = createPatient(fhirPathMapping);

    assertFalse(patient.getDeceasedBooleanType().booleanValue());
  }

  @Test
  public void testArray1() {
    Map<String, Object> fhirPathMapping = new HashMap<>();

    fhirPathMapping.put("Patient.name.given[0]", "Billy");
    fhirPathMapping.put("Patient.name.given[1]", "Bob");

    Patient patient = createPatient(fhirPathMapping);

    List<StringType> given = patient.getNameFirstRep().getGiven();

    assertEquals(2, given.size());
    assertEquals("Billy", given.get(0).getValueAsString());
    assertEquals("Bob", given.get(1).getValueAsString());
  }


  @Ignore
  @Test
  public void testArray2() {
    Map<String, Object> fhirPathMapping = new HashMap<>();

    fhirPathMapping.put("Patient.name[0].given", "Billy");
    fhirPathMapping.put("Patient.name[1].given", "Bob");

    // TODO: for some reason Patient.name.given[0] and given[1] work as expected (2 name strings in
    // the 'given' array)
    // but Patient.name[0].given and .name[1].given do not (same result, expected was 2 HumanName
    // objects in the name array)

    Patient patient = createPatient(fhirPathMapping);

    List<HumanName> name = patient.getName();
    assertEquals(2, name.size());

    assertEquals("Billy", name.get(0).getGivenAsSingleString());
    assertEquals("Bob", name.get(1).getGivenAsSingleString());
  }

  @Test
  public void testArray3() {
    Map<String, Object> fhirPathMapping = new HashMap<>();

    fhirPathMapping.put("Patient.name.where(use='official').given", "Billy");
    fhirPathMapping.put("Patient.name.where(use='usual').given", "Bob");

    Patient patient = createPatient(fhirPathMapping);
    List<HumanName> name = patient.getName();
    assertEquals(2, name.size());

    // we don't necessarily know which order these will be in
    HumanName first = name.get(0);
    HumanName second = name.get(1);

    HumanName official;
    HumanName usual;

    if (first.getUse() == NameUse.OFFICIAL) {
      official = first;
      usual = second;
    } else {
      usual = first;
      official = second;
    }

    assertEquals("Billy", official.getGivenAsSingleString());
    assertEquals("Bob", usual.getGivenAsSingleString());
  }

  @Test
  public void testExtension() {
    Map<String, Object> fhirPathMapping = new HashMap<>();
    fhirPathMapping.put(
        "Patient.extension.where(url='http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName').valueString",
        "Donita707 Langosh790");

    fhirPathMapping.put(
        "Patient.extension.where(url='http://hl7.org/fhir/StructureDefinition/patient-birthPlace').valueAddress.city",
        "Watertown");
    fhirPathMapping.put(
        "Patient.extension.where(url='http://hl7.org/fhir/StructureDefinition/patient-birthPlace').valueAddress.state",
        "Massachusetts");
    fhirPathMapping.put(
        "Patient.extension.where(url='http://hl7.org/fhir/StructureDefinition/patient-birthPlace').valueAddress.country",
        "US");

    Patient patient = createPatient(fhirPathMapping);

    assertEquals(2, patient.getExtension().size());

    Extension mothersMaidenName = patient
        .getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName");
    assertEquals("Donita707 Langosh790",
        mothersMaidenName.getValueAsPrimitive().getValueAsString());


    Extension birthPlace =
        patient.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/patient-birthPlace");

    Address address = (Address) birthPlace.getValue();

    assertEquals("Watertown", address.getCity());
    assertEquals("Massachusetts", address.getState());
    assertEquals("US", address.getCountry());
  }

  @Test
  public void testExtensionOnPrimitive() {
    Map<String, Object> fhirPathMapping = new HashMap<>();

    // fhirPathMapping.put("Patient.birthDate", "2021-12-15");
    fhirPathMapping.put(
        "Patient.birthDate.extension.where(url='http://hl7.org/fhir/StructureDefinition/patient-birthTime').valueDateTime",
        "2021-12-15T17:57:28-05:00");

    Patient patient = new Patient();
    patient.setBirthDate(new Date(123456789L)); // translates into 1970-01-02

    updatePatient(patient, fhirPathMapping);

    assertEquals(0, patient.getExtension().size());

    DateType birthdate = patient.getBirthDateElement();
    assertEquals("1970-01-02", birthdate.getValueAsString());

    Extension birthTime =
        birthdate.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/patient-birthTime");
    assertEquals("2021-12-15T17:57:28-05:00", birthTime.getValueAsPrimitive().getValueAsString());
  }

  @Test
  public void testSingleFieldSetter() {
    Patient p = new Patient();
    CustomFHIRPathResourceGeneratorR4.setField(p, "name.given", "TestName");
    assertEquals("TestName", p.getNameFirstRep().getGivenAsSingleString());
  }
}
