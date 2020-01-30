package org.mitre.synthea.export;

import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * Uses HAPI FHIR project to validate FHIR export. http://hapifhir.io/doc_validation.html
 */
public class FHIRSTU3ExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testDecimalRounding() {
    Integer i = 123456;
    Object v = FhirStu3.mapValueToFHIRType(i,"fake");
    assertTrue(v instanceof Quantity);
    Quantity q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(123460)) == 0);

    Double d = 0.000123456;
    v = FhirStu3.mapValueToFHIRType(d, "fake");
    assertTrue(v instanceof Quantity);
    q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(0.00012346)) == 0);

    d = 0.00012345678901234;
    v = FhirStu3.mapValueToFHIRType(d, "fake");
    assertTrue(v instanceof Quantity);
    q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(0.00012346)) == 0);
  }

  @Test
  public void testFHIRSTU3Export() throws Exception {
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());

    FhirContext ctx = FhirContext.forDstu3();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    FhirValidator validator = ctx.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);

    ValidationResources validationResources = new ValidationResources();

    List<String> validationErrors = new ArrayList<String>();

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      int x = validationErrors.size();
      TestHelper.exportOff();
      Person person = generator.generatePerson(i);
      Config.set("exporter.fhir_stu3.export", "true");
      Config.set("exporter.fhir.use_shr_extensions", "true");
      FhirStu3.TRANSACTION_BUNDLE = person.random.nextBoolean();
      String fhirJson = FhirStu3.convertToFHIRJson(person, System.currentTimeMillis());
      // Check that the fhirJSON doesn't contain unresolved SNOMED-CT strings
      // (these should have been converted into URIs)
      if (fhirJson.contains("SNOMED-CT")) {
        validationErrors.add(
            "JSON contains unconverted references to 'SNOMED-CT' (should be URIs)");
      }
      // Now validate the resource...
      IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
      ValidationResult result = validator.validateWithResult(resource);
      if (!result.isSuccessful()) {
        // If the validation failed, let's crack open the Bundle and validate
        // each individual entry.resource to get context-sensitive error
        // messages...
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
        for (BundleEntryComponent entry : bundle.getEntry()) {
          ValidationResult eresult = validator.validateWithResult(entry.getResource());
          if (!eresult.isSuccessful()) {
            for (SingleValidationMessage emessage : eresult.getMessages()) {
              boolean valid = false;
              if (emessage.getMessage().contains("@ Observation obs-7")) {
                /*
                 * The obs-7 invariant basically says that Observations should have values, unless
                 * they are made of components. This test replaces an invalid XPath expression
                 * that was causing correct instances to fail validation.
                 */
                valid = validateObs7((Observation) entry.getResource());
              } else if (emessage.getMessage().contains("@ Condition con-4")) {
                /*
                 * The con-4 invariant says "If condition is abated, then clinicalStatus must be
                 * either inactive, resolved, or remission" which is very clear and sensical.
                 * However, the XPath expression does not evaluate correctly for valid instances,
                 * so we must manually validate.
                 */
                valid = validateCon4((Condition) entry.getResource());
              } else if (emessage.getMessage().contains("@ MedicationRequest mps-1")) {
                /*
                 * The mps-1 invariant says MedicationRequest.requester.onBehalfOf can only be
                 * specified if MedicationRequest.requester.agent is practitioner or device.
                 * But the invariant is poorly written and does not correctly handle references
                 * starting with "urn:uuid"
                 */
                valid = true; // ignore this error
              } else if (emessage.getMessage().contains(
                  "per-1: If present, start SHALL have a lower value than end")) {
                /*
                 * The per-1 invariant does not account for daylight savings time... so, if the
                 * daylight savings switch happens between the start and end, the validation
                 * fails, even if it is valid.
                 */
                valid = true; // ignore this error
              }

              if (!valid) {
                System.out.println(parser.encodeResourceToString(entry.getResource()));
                System.out.println("ERROR: " + emessage.getMessage());
                validationErrors.add(emessage.getMessage());
              }
            }
          }
          // Check ExplanationOfBenefit Resources against BlueButton
          if (entry.getResource().fhirType().equals("ExplanationOfBenefit")) {
            ValidationResult bbResult = validationResources.validateSTU3(entry.getResource());

            for (SingleValidationMessage message : bbResult.getMessages()) {
              if (message.getSeverity() == ResultSeverityEnum.ERROR) {
                if (!(message.getMessage().contains(
                    "Element 'ExplanationOfBenefit.id': minimum required = 1, but only found 0")
                    || message.getMessage().contains("Could not verify slice for profile"))) {
                  // For some reason the validator is not detecting the IDs on the resources,
                  // even though they appear to be present while debugging and during normal
                  // operations.
                  System.out.println(message.getSeverity() + ": " + message.getMessage());
                  Assert.fail(message.getSeverity() + ": " + message.getMessage());
                }
              }
            }
          }
        }
      }
      int y = validationErrors.size();
      if (x != y) {
        Exporter.export(person, System.currentTimeMillis());
      }
    }
    assertTrue("Validation of exported FHIR bundle failed: "
        + String.join("|", validationErrors), validationErrors.size() == 0);
  }

  /**
   * The obs-7 invariant basically says that Observations should have values, unless they are made
   * of components. This test replaces an invalid XPath expression that was causing correct
   * instances to fail validation.
   *
   * @param obs The observation.
   * @return true on valid, otherwise false.
   */
  private static boolean validateObs7(Observation obs) {
    return (obs.hasComponent() && !obs.hasValue()) || (obs.hasValue() && !obs.hasComponent());
  }

  /**
   * The con-4 invariant says "If condition is abated, then clinicalStatus must be either inactive,
   * resolved, or remission" which is very clear and sensical. However, the XPath expression does
   * not evaluate correctly for valid instances, so we must manually validate.
   *
   * @param con The condition.
   * @return true on valid, otherwise false.
   */
  private static boolean validateCon4(Condition con) {
    return (con.hasAbatement()
        && con.getClinicalStatus() != Condition.ConditionClinicalStatus.ACTIVE)
        || !con.hasAbatement();
  }

}
