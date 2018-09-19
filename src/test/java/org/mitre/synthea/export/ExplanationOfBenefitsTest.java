package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.dstu3.model.Bundle;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class ExplanationOfBenefitsTest {

  @Test
  public void testEobConformance() {
    FhirContext ctx = FhirContext.forDstu3();

    ValidationResources validator = new ValidationResources();

    int numberOfPeople = 1;
    Generator generator = new Generator(numberOfPeople);
    for (int i = 0; i < numberOfPeople; i++) {

      TestHelper.exportOff();
      Person person = generator.generatePerson(i);
      Config.set("exporter.fhir.export", "true");
      Config.set("exporter.fhir.use_shr_extensions", "true");
      FhirDstu2.TRANSACTION_BUNDLE = person.random.nextBoolean();
      String fhirJson = FhirStu3.convertToFHIR(person, System.currentTimeMillis());
      Bundle resource = (Bundle) ctx.newJsonParser().parseResource(fhirJson);


      for (Bundle.BundleEntryComponent bec : resource.getEntry()) {
        if (bec.getResource().fhirType().equals("ExplanationOfBenefit")) {
          ValidationResult resultNormal = validator.validate(bec.getResource());
          ValidationResult resultBlueButton = validator.validate(bec.getResource());

          for(SingleValidationMessage message : resultNormal.getMessages()) {
            if(message.getSeverity() == ResultSeverityEnum.ERROR) {
              System.out.println(message.getSeverity() + ": " + message.getMessage());

            }
          }

        }
      }
    }
  }
}
