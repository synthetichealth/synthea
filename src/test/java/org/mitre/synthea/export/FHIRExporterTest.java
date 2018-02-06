package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
public class FHIRExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();
  
  @Test
  public void testFHIRExport() throws Exception {
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    
    FhirContext ctx = FhirContext.forDstu3();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    FhirValidator validator = ctx.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);

    List<String> validationErrors = new ArrayList<String>();

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    for (int i = 0; i < numberOfPeople; i++) {
      int x = validationErrors.size();
      TestHelper.exportOff();
      Person person = generator.generatePerson(i);
      Config.set("exporter.fhir.export", "true");
      String fhirJson = FhirStu3.convertToFHIR(person, System.currentTimeMillis());
      IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
      ValidationResult result = validator.validateWithResult(resource);
      if (result.isSuccessful() == false) {
        // If the validation failed, let's crack open the Bundle and validate
        // each individual entry.resource to get context-sensitive error
        // messages...
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
        for (BundleEntryComponent entry : bundle.getEntry()) {
          ValidationResult eresult = validator.validateWithResult(entry.getResource());
          if (eresult.isSuccessful() == false) {
            for (SingleValidationMessage emessage : eresult.getMessages()) {
              if (emessage.getSeverity() == ResultSeverityEnum.ERROR
                  || emessage.getSeverity() == ResultSeverityEnum.FATAL) {
                boolean valid = false;
                /*
                 * There are a few bugs in the FHIR schematron files that are distributed with HAPI
                 * 3.0.0 (these are fixed in the latest `master` branch), specifically with XPath
                 * expressions.
                 *
                 * Two of these bugs are related to the FHIR Invariant rules obs-7 and con-4, which
                 * have XPath expressions that incorrectly raise errors on validation.
                 */
                if (emessage.getMessage().contains("Message=obs-7")) {
                  /*
                   * The obs-7 invariant basically says that Observations should have values, unless
                   * they are made of components. This test replaces an invalid XPath expression
                   * that was causing correct instances to fail validation.
                   */
                  valid = validateObs7((Observation) entry.getResource());
                } else if (emessage.getMessage().contains("Message=con-4")) {
                  /*
                   * The con-4 invariant says "If condition is abated, then clinicalStatus must be
                   * either inactive, resolved, or remission" which is very clear and sensical.
                   * However, the XPath expression does not evaluate correctly for valid instances,
                   * so we must manually validate.
                   */
                  valid = validateCon4((Condition) entry.getResource());
                }
                if (!valid) {
                  System.out.println(parser.encodeResourceToString(entry.getResource()));
                  System.out.println("ERROR: " + emessage.getMessage());
                  validationErrors.add(emessage.getMessage());
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

    assertEquals(0, validationErrors.size());
  }

  /**
   * The obs-7 invariant basically says that Observations should have values, unless they are made
   * of components. This test replaces an invalid XPath expression that was causing correct
   * instances to fail validation.
   * 
   * @param obs The observation.
   * @return true on valid, otherwise false.
   */
  public static boolean validateObs7(Observation obs) {
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
  public static boolean validateCon4(Condition con) {
    return (con.hasAbatement() && con.getClinicalStatus() != ConditionClinicalStatus.ACTIVE)
        || !con.hasAbatement();
  }
}
