package org.mitre.synthea.export.flexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Test;


public class FhirPathUtilsTest {

  @Test
  public void testEvaluateResource() {
    Patient p = new Patient();
    p.addName().addGiven("John").setFamily("Smith");

    List<Base> result = FhirPathUtils.evaluateResource(p, "Patient.name.given");
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof StringType);
    assertEquals("John", ((StringType) result.get(0)).getValue());

    result = FhirPathUtils.evaluateResource(p, "Patient.name.suffix");
    assertEquals(0, result.size());
  }

  @Test
  public void testEvaluateBundle() {
    Patient p = new Patient();
    p.addName().addGiven("Jessica").setFamily("Jones");

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    List<Base> result = FhirPathUtils.evaluateBundle(b, "Patient.name.given", false);
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof StringType);
    assertEquals("Jessica", ((StringType) result.get(0)).getValue());

    // now do the same thing with returnResource = true,
    // so we expect the resource that this was true for
    result = FhirPathUtils.evaluateBundle(b, "Patient.name.given", true);
    assertEquals(1, result.size());
    assertEquals(p, result.get(0));

    // now try some bundle-specific fhirpath
    result = FhirPathUtils.evaluateBundle(b,
        "Bundle.entry.resource.ofType(Patient).first().name.given = 'Jessica'", false);
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof BooleanType);
    assertTrue(((BooleanType) result.get(0)).booleanValue());

    result = FhirPathUtils.evaluateBundle(b,
        "Bundle.entry.resource.ofType(Patient).first().name.given = 'Julianna'", false);
    assertEquals(1, result.size());
    assertTrue(result.get(0) instanceof BooleanType);
    assertFalse(((BooleanType) result.get(0)).booleanValue());
  }

  @Test
  public void testAppliesToResource() {
    Patient p = new Patient();
    p.addName().addGiven("Jane").setFamily("Shepard");

    assertTrue(FhirPathUtils.appliesToResource(p, "Patient.name.given"));
    assertTrue(FhirPathUtils.appliesToResource(p, "Patient.name.given = 'Jane'"));
    assertFalse(FhirPathUtils.appliesToResource(p, "Patient.name.given = 'John'"));
    assertFalse(FhirPathUtils.appliesToResource(p, "Patient.name.suffix"));
  }

  @Test
  public void testAppliesToBundle() {
    Patient p = new Patient();
    p.addName().addGiven("Jack").setFamily("Black");

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    // fhirpath not starting with "Bundle" implies
    //   "is there any resource in the bundle that this applies to"
    assertTrue(FhirPathUtils.appliesToBundle(b, "Patient.name.given"));
    assertTrue(FhirPathUtils.appliesToBundle(b, "Patient.name.given = 'Jack'"));
    assertFalse(FhirPathUtils.appliesToBundle(b, "Patient.name.given = 'Jerametrius'"));
    assertFalse(FhirPathUtils.appliesToBundle(b, "Patient.name.suffix"));

    // fhirpath starting with "Bundle" implies
    //    "is this truthy in the context of the bundle resource itself"
    assertTrue(FhirPathUtils.appliesToBundle(b,
        "Bundle.entry.resource.ofType(Patient).first().name.given"));
    assertTrue(FhirPathUtils.appliesToBundle(b,
        "Bundle.entry.resource.ofType(Patient).first().name.given = 'Jack'"));
    assertFalse(FhirPathUtils.appliesToBundle(b,
        "Bundle.entry.resource.ofType(Patient).first().name.given = 'Jerametrius'"));
    assertFalse(
        FhirPathUtils.appliesToBundle(b, "Bundle.entry.resource.ofType(Patient).name.suffix"));
  }

  @Test
  public void testVariables() {
    Patient p1 = new Patient();
    p1.addName().addGiven("Jeff").setFamily("Goldblum");
    Bundle b1 = new Bundle();
    b1.addEntry().setResource(p1);

    Patient p2 = new Patient();
    p2.addName().addGiven("Jamie Lee").setFamily("Curtis");
    Bundle b2 = new Bundle();
    b2.addEntry().setResource(p2);

    String fhirpath = "Patient.name.family in %jurassicNames";

    Map<String,Object> variables = Map.of("jurassicNames", List.of("Neill", "Dern", "Goldblum"));

    assertTrue(FhirPathUtils.appliesToBundle(b1, fhirpath, variables));
    assertFalse(FhirPathUtils.appliesToBundle(b2, fhirpath, variables));
  }
}
