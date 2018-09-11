package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.dstu3.model.Bundle;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class ExplanationOfBenefitsTest {

  @Test
  public void testEobConformance() throws Exception {
    FhirContext ctx = FhirContext.forDstu3();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);

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
        if (bec.getResource().fhirType() == "ExplanationOfBenefit") {
          ValidationResult result = validator.validate(bec.getResource());
          System.out.println(result);

        }
      }



    }
  }
}
