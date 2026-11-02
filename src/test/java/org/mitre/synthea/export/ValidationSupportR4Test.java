package org.mitre.synthea.export;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport.CodeValidationResult;
import ca.uhn.fhir.context.support.ValidationSupportContext;

import org.junit.Test;

/** Tests the local terminology used by the R4 exporter validation tests. */
public class ValidationSupportR4Test {
  @Test
  public void testDocumentCodeSystemIsAvailableForEveryUsCoreVersion() {
    for (FhirR4.USCoreVersion version : FhirR4.USCoreVersion.values()) {
      ValidationSupportR4 support = new ValidationSupportR4(FhirR4.getContext(), version);
      assertKnownCode(support, "http://loinc.org", "34117-2");
      assertKnownCode(support, "urn:oid:2.16.840.1.113883.6.238", "2106-3");
      assertFalse("Unknown LOINC codes must not be accepted",
          support.lookupCode(new ValidationSupportContext(support),
              "http://loinc.org", "not-a-loinc-code", null).isFound());
    }
  }

  private void assertKnownCode(ValidationSupportR4 support, String system, String code) {
    assertTrue("Missing code system: " + system,
        support.isCodeSystemSupported(new ValidationSupportContext(support), system));
    CodeValidationResult result = support.validateCode(new ValidationSupportContext(support),
        new ConceptValidationOptions(), system, code, null, null);
    assertNotNull("Missing code: " + system + "#" + code, result);
    assertTrue(result.isOk());
  }
}
