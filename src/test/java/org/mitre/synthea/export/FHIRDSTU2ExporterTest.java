package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;

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
public class FHIRDSTU2ExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();
  
  @Test
  public void testFHIRDSTU2Export() throws Exception {
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    
    FhirContext ctx = FhirContext.forDstu2();
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
      Config.set("exporter.fhir_dstu2.export", "true");
      String fhirJson = FhirDstu2.convertToFHIR(person, System.currentTimeMillis());
      IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
      ValidationResult result = validator.validateWithResult(resource);
      if (result.isSuccessful() == false) {
        // If the validation failed, let's crack open the Bundle and validate
        // each individual entry.resource to get context-sensitive error
        // messages...
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
        for (Entry entry : bundle.getEntry()) {
          ValidationResult eresult = validator.validateWithResult(entry.getResource());
          if (eresult.isSuccessful() == false) {
            for (SingleValidationMessage emessage : eresult.getMessages()) {
              System.out.println(parser.encodeResourceToString(entry.getResource()));
              System.out.println("ERROR: " + emessage.getMessage());
              validationErrors.add(emessage.getMessage());
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
}
