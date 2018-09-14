package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.After;
import org.junit.Before;
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

  @Before
  public void setUp() {
    TestHelper.exportOff();
    Config.set("exporter.fhir_stu3.export", "true");
  }

  @After
  public void tearDown() {
    Config.remove("exporter.baseDirectory");
    Config.remove("exporter.fhir_stu3.export");
  }

  @Test
  public void testFHIRSTU3Export() throws Exception {
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
      Person person = generator.generatePerson(i);
      FhirStu3.TRANSACTION_BUNDLE = person.random.nextBoolean();
      String fhirJson = FhirStu3.convertToFHIR(person, System.currentTimeMillis());
      IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
      ValidationResult result = validator.validateWithResult(resource);
      if (!result.isSuccessful()) {
        // If the validation failed, let's crack open the Bundle and validate
        // each individual entry.resource to get context-sensitive error
        // messages...
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
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
