package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

/**
 * Uses HAPI FHIR project to validate FHIR export.
 * http://hapifhir.io/doc_validation.html
 */
public class FHIRExporterTest {
	@Test public void testFHIRExport() throws Exception
	{
		FhirContext ctx = FhirContext.forDstu3();
		IParser parser = ctx.newJsonParser().setPrettyPrint(true);

		FhirValidator validator = ctx.newValidator();
		validator.setValidateAgainstStandardSchema(true);
		validator.setValidateAgainstStandardSchematron(true);

		List<String> validationErrors = new ArrayList<String>();

		int numberOfPeople = 10;
		Generator generator = new Generator(numberOfPeople);
		for(int i=0; i < numberOfPeople; i++)
		{
			int x = validationErrors.size();
			TestHelper.exportOff();
			Person person = generator.generatePerson(i);
			Config.set("exporter.fhir.export", "true");
			String fhirJson = FhirStu3.convertToFHIR(person, System.currentTimeMillis());
			IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
			ValidationResult result = validator.validateWithResult(resource);
			if(result.isSuccessful() == false) {
				// If the validation failed, let's crack open the Bundle and validate
				// each individual entry.resource to try to context-sensitive error
				// messages...
				Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
				for(BundleEntryComponent entry : bundle.getEntry()) {
					ValidationResult eresult = validator.validateWithResult(entry.getResource());
					if(eresult.isSuccessful() == false) {
						for(SingleValidationMessage emessage : eresult.getMessages()) {
							if(emessage.getSeverity() == ResultSeverityEnum.ERROR || emessage.getSeverity() == ResultSeverityEnum.FATAL)
							{
								boolean valid = false;
								if(emessage.getMessage().contains("Message=obs-7")) {
									valid = validateObs7((Observation) entry.getResource());
								} else if(emessage.getMessage().contains("Message=con-4")) {
									valid = validateCon4((Condition) entry.getResource());
								}
								if(!valid) {
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
			if(x != y) {
				Exporter.export(person, System.currentTimeMillis());
			}
		}

		assertEquals(0, validationErrors.size());
	}

	public static boolean validateObs7(Observation obs) {
		return (obs.hasComponent() && !obs.hasValue()) || (obs.hasValue() && !obs.hasComponent());
	}

	public static boolean validateCon4(Condition con) {
		return (con.hasAbatement() && con.getClinicalStatus()!=ConditionClinicalStatus.ACTIVE) || !con.hasAbatement();
	}
}
