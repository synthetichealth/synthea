package org.mitre.synthea.export;

import java.sql.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus;
import org.hl7.fhir.dstu3.model.Condition.ConditionVerificationStatus;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DateType;
import org.hl7.fhir.dstu3.model.DecimalType;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Encounter.EncounterStatus;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Procedure;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.Type;
import org.mitre.synthea.modules.HealthRecord;
import org.mitre.synthea.modules.HealthRecord.CarePlan;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Medication;
import org.mitre.synthea.modules.HealthRecord.Observation;
import org.mitre.synthea.modules.HealthRecord.Report;
import org.mitre.synthea.modules.Person;

import ca.uhn.fhir.context.FhirContext;

public class FhirStu3 
{
	// HAPI FHIR warns that the context creation is expensive, and should be performed
	// per-application, not per-record
	private static final FhirContext FHIR_CTX = FhirContext.forDstu3();
	
	private static final String SNOMED_URI = "http://snomed.info/sct";
	private static final String LOINC_URI = "http://loinc.org";
	
	public static String convertToFHIR(Person person)
	{
		Bundle bundle = new Bundle();
		
		BundleEntryComponent personEntry = basicInfo(person, bundle);
		
		for (Encounter encounter : person.record.encounters)
		{
			BundleEntryComponent encounterEntry = encounter(personEntry, bundle, encounter);
			
			for (HealthRecord.Entry condition : encounter.conditions)
			{
				condition(personEntry, bundle, encounterEntry, condition);
			}
			
			for (Observation observation : encounter.observations)
			{
				observation(personEntry, bundle, encounterEntry, observation);
			}
			
			for (HealthRecord.Entry procedure : encounter.procedures)
			{
				procedure(personEntry, bundle, encounterEntry, procedure);
			}
			
			for (Medication medication : encounter.medications)
			{
				medication(personEntry, bundle, encounterEntry, medication);
			}
			
			for (HealthRecord.Entry immunization : encounter.immunizations)
			{
				immunization(personEntry, bundle, encounterEntry, immunization);
			}
			
			for (Report report : encounter.reports)
			{
				report(personEntry, bundle, encounterEntry, report);
			}
			
			for (CarePlan careplan : encounter.careplans)
			{
				careplan(personEntry, bundle, encounterEntry, careplan);
			}
		}
		
		String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		return bundleJson;
	}
	


	private static BundleEntryComponent basicInfo(Person person, Bundle bundle)
	{		
		Patient patientResource = new Patient();
		
		patientResource.addIdentifier()
			.setSystem("https://github.com/synthetichealth/synthea")
			.setValue((String)person.attributes.get(Person.ID));
		
		patientResource.addName().addGiven((String)person.attributes.get(Person.NAME));
		
		if (person.attributes.get(Person.GENDER).equals("M"))
		{
			patientResource.setGender(AdministrativeGender.MALE);
		} else if (person.attributes.get(Person.GENDER).equals("F"))
		{
			patientResource.setGender(AdministrativeGender.FEMALE);
		}
		
		// DALY and QALY values
		Extension dalyExtension = new Extension(SNOMED_URI + "/disability-adjusted-life-years");
		DecimalType daly = new DecimalType((double) person.attributes.get("DALY"));
		dalyExtension.setValue(daly);
		patientResource.addExtension(dalyExtension);
		
		Extension qalyExtension = new Extension(SNOMED_URI + "/quality-adjusted-life-years");
		DecimalType qaly = new DecimalType((double) person.attributes.get("QALY"));
		qalyExtension.setValue(qaly);
		patientResource.addExtension(qalyExtension);
		
		return newEntry(bundle, patientResource);
	}
	
	
	private static BundleEntryComponent encounter(BundleEntryComponent personEntry, Bundle bundle,
			Encounter encounter) 
	{
		org.hl7.fhir.dstu3.model.Encounter encounterResource = new org.hl7.fhir.dstu3.model.Encounter();
	
		encounterResource.setSubject(new Reference(personEntry.getFullUrl()));
		encounterResource.setStatus(EncounterStatus.FINISHED);
		if (encounter.codes.isEmpty())
		{
			// wellness encounter
		} else
		{
			Code code = encounter.codes.get(0); 
			encounterResource.addType( mapCodeToCodeableConcept(code, SNOMED_URI) );
		}
		
		long encounter_end = encounter.stop > 0 ? encounter.stop : encounter.stop + TimeUnit.MINUTES.toMillis(15);
		
		encounterResource.setPeriod( new Period().setStart(new Date(encounter.start)).setEnd( new Date(encounter_end)) );
		
		return newEntry(bundle, encounterResource);
	}
	
	private static BundleEntryComponent condition(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, HealthRecord.Entry condition)
	{
		Condition conditionResource = new Condition();
		
		conditionResource.setSubject(new Reference(personEntry.getFullUrl()));
		conditionResource.setContext(new Reference(encounterEntry.getFullUrl()));
		
		Code code = condition.codes.get(0); 
		conditionResource.setCode( mapCodeToCodeableConcept(code, SNOMED_URI) );
		
		conditionResource.setVerificationStatus(ConditionVerificationStatus.CONFIRMED);
		conditionResource.setClinicalStatus(ConditionClinicalStatus.ACTIVE);
		
		conditionResource.setOnset( convertFhirDateTime(condition.start, true) );
		conditionResource.setAssertedDate(new Date(condition.start));
		
		if (condition.stop > 0)
		{
			conditionResource.setAbatement(convertFhirDateTime(condition.stop, true));
		}
		
		return newEntry(bundle, conditionResource);
	}
	
	private static BundleEntryComponent observation(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Observation observation)
	{
		org.hl7.fhir.dstu3.model.Observation observationResource = new org.hl7.fhir.dstu3.model.Observation();
		
		observationResource.setSubject(new Reference(personEntry.getFullUrl()));
		observationResource.setContext(new Reference(encounterEntry.getFullUrl()));
		
		Code code = observation.codes.get(0); 
		observationResource.setCode( mapCodeToCodeableConcept(code, LOINC_URI) );
		
		return newEntry(bundle, observationResource);
	}
	
	private static BundleEntryComponent procedure(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, HealthRecord.Entry procedure)
	{
		Procedure procedureResource = new Procedure();
		
		procedureResource.setSubject(new Reference(personEntry.getFullUrl()));
		procedureResource.setContext(new Reference(encounterEntry.getFullUrl()));
		
		Code code = procedure.codes.get(0); 
		procedureResource.setCode( mapCodeToCodeableConcept(code, SNOMED_URI) );
		
		return newEntry(bundle, procedureResource);
	}
	
	// TODO - no immunizations in GMF yet
	private static BundleEntryComponent immunization(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, HealthRecord.Entry immunization)
	{
		Immunization immResource = new Immunization();
		
		immResource.setPatient(new Reference(personEntry.getFullUrl()));
		immResource.setEncounter(new Reference(encounterEntry.getFullUrl()));
		
		return newEntry(bundle, immResource);
	}

	private static BundleEntryComponent medication(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Medication medication)
	{
		MedicationRequest medicationResource = new MedicationRequest();
		
		medicationResource.setSubject(new Reference(personEntry.getFullUrl()));
		medicationResource.setContext(new Reference(encounterEntry.getFullUrl()));
		
		return newEntry(bundle, medicationResource);
	}

	private static BundleEntryComponent report(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Report report)
	{
		DiagnosticReport reportResource = new DiagnosticReport();
		
		reportResource.setSubject(new Reference(personEntry.getFullUrl()));
		reportResource.setContext(new Reference(encounterEntry.getFullUrl()));
		
		return newEntry(bundle, reportResource);
	}

	private static BundleEntryComponent careplan(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, CarePlan carePlan)
	{
		org.hl7.fhir.dstu3.model.CarePlan careplanResource = new org.hl7.fhir.dstu3.model.CarePlan();
		
		careplanResource.setSubject(new Reference(personEntry.getFullUrl()));
		careplanResource.setContext(new Reference(encounterEntry.getFullUrl()));
		
		Code code = carePlan.codes.get(0); 
		careplanResource.addCategory( mapCodeToCodeableConcept(code, SNOMED_URI) );
		
		return newEntry(bundle, careplanResource);
	}
	
	private static Type convertFhirDateTime(long datetime, boolean time)
	{
		Date date = new Date(datetime);
		
		if (time)
		{
			return new DateTimeType(date);
		} else
		{
			return new DateType(date);
		}
	}


//	private static CodeableConcept mapCodeToCodeableConcept(Code from)
//	{
//		return mapCodeToCodeableConcept(from, null);
//	}

	private static CodeableConcept mapCodeToCodeableConcept(Code from, String system)
	{
		CodeableConcept to = new CodeableConcept();
		
		if (from.display != null)
		{
			to.setText(from.display);
		}
		
		Coding coding = new Coding();
		coding.setCode(from.code);
		coding.setDisplay(from.display);
		if (system == null)
		{
			coding.setSystem(from.system);
		} else
		{
			coding.setSystem(system);
		}
		
		to.addCoding(coding);
		
		return to;
	}

	private static BundleEntryComponent newEntry(Bundle bundle, Resource resource)
	{
		BundleEntryComponent entry = bundle.addEntry();
		
		String resourceID = UUID.randomUUID().toString();
		resource.setId(resourceID);
		entry.setFullUrl("urn:uuid:" + resourceID);
		
		entry.setResource(resource);
		
		return entry;
	}
}
