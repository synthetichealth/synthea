package org.mitre.synthea.export;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanActivityStatus;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanStatus;
import org.hl7.fhir.dstu3.model.Claim.ClaimStatus;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus;
import org.hl7.fhir.dstu3.model.Condition.ConditionVerificationStatus;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DateType;
import org.hl7.fhir.dstu3.model.DecimalType;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.DiagnosticReport.DiagnosticReportStatus;
import org.hl7.fhir.dstu3.model.Encounter.EncounterStatus;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Goal.GoalStatus;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.HumanName.NameUse;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Immunization.ImmunizationStatus;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.dstu3.model.Money;
import org.hl7.fhir.dstu3.model.Narrative;
import org.hl7.fhir.dstu3.model.Narrative.NarrativeStatus;
import org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.PositiveIntType;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.Type;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.mitre.synthea.modules.HealthRecord;
import org.mitre.synthea.modules.HealthRecord.CarePlan;
import org.mitre.synthea.modules.HealthRecord.Claim;
import org.mitre.synthea.modules.HealthRecord.ClaimItem;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Medication;
import org.mitre.synthea.modules.HealthRecord.Observation;
import org.mitre.synthea.modules.HealthRecord.Procedure;
import org.mitre.synthea.modules.HealthRecord.Report;
import org.mitre.synthea.modules.Person;

import ca.uhn.fhir.context.FhirContext;

import com.vividsolutions.jts.geom.Point;


public class FhirStu3
{
	// HAPI FHIR warns that the context creation is expensive, and should be performed
	// per-application, not per-record
	private static final FhirContext FHIR_CTX = FhirContext.forDstu3();

	private static final String SNOMED_URI = "http://snomed.info/sct";
	private static final String LOINC_URI = "http://loinc.org";
	private static final String RXNORM_URI = "http://www.nlm.nih.gov/research/umls/rxnorm";
	private static final String CVX_URI = "http://hl7.org/fhir/sid/cvx";
	private static final String SHR_EXT = "http://standardhealthrecord.org/fhir/StructureDefinition/";

	/**
	 * Convert the given Person into a JSON String,
	 * containing a FHIR Bundle of the Person and the associated entries from their health record.
	 * @param person Person to generate the FHIR JSON for
	 * @param stopTime Time the simulation ended
	 * @return String containing a JSON representation of a FHIR Bundle containing the Person's health record
	 */
	public static String convertToFHIR(Person person, long stopTime)
	{
		Bundle bundle = new Bundle();
		bundle.setType(BundleType.COLLECTION);

		BundleEntryComponent personEntry = basicInfo(person, bundle, stopTime);

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

			for (Procedure procedure : encounter.procedures)
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

			// one claim per encounter
			encounterClaim(personEntry, bundle, encounterEntry, encounter.claim);
		}

		String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

		return bundleJson;
	}

	/**
	 * Map the given Person to a FHIR Patient resource, and add it to the given Bundle.
	 * @param person The Person
	 * @param bundle The Bundle to add to
	 * @param stopTime Time the simulation ended
	 * @return The created Entry
	 */
	private static BundleEntryComponent basicInfo(Person person, Bundle bundle, long stopTime)
	{
		Patient patientResource = new Patient();

		patientResource.addIdentifier()
			.setSystem("https://github.com/synthetichealth/synthea")
			.setValue((String)person.attributes.get(Person.ID));

		Code mrnCode = new Code("http://hl7.org/fhir/v2/0203", "MR", "Medical Record Number");
		patientResource.addIdentifier()
			.setType(mapCodeToCodeableConcept(mrnCode, "http://hl7.org/fhir/v2/0203"))
			.setSystem("http://hospital.smarthealthit.org")
			.setValue((String)person.attributes.get(Person.ID));

		Code ssnCode = new Code("http://hl7.org/fhir/identifier-type", "SB", "Social Security Number");
		patientResource.addIdentifier()
			.setType(mapCodeToCodeableConcept(ssnCode, "http://hl7.org/fhir/identifier-type"))
			.setSystem("http://hl7.org/fhir/sid/us-ssn")
			.setValue((String)person.attributes.get(Person.IDENTIFIER_SSN));

		if (person.attributes.get(Person.IDENTIFIER_DRIVERS) != null) {
			Code driversCode = new Code("http://hl7.org/fhir/v2/0203", "DL", "Driver's License");
			patientResource.addIdentifier()
				.setType(mapCodeToCodeableConcept(driversCode, "http://hl7.org/fhir/v2/0203"))
				.setSystem("urn:oid:2.16.840.1.113883.4.3.25")
				.setValue((String)person.attributes.get(Person.IDENTIFIER_DRIVERS));
		}

		if (person.attributes.get(Person.IDENTIFIER_PASSPORT) != null) {
			Code passportCode = new Code("http://hl7.org/fhir/v2/0203", "PPN", "Passport Number");
			patientResource.addIdentifier()
				.setType(mapCodeToCodeableConcept(passportCode, "http://hl7.org/fhir/v2/0203"))
				.setSystem(SHR_EXT + "passportNumber")
				.setValue((String)person.attributes.get(Person.IDENTIFIER_PASSPORT));
		}

		HumanName name = patientResource.addName();
		name.setUse(HumanName.NameUse.OFFICIAL);
		name.addGiven((String)person.attributes.get(Person.FIRST_NAME));
		name.setFamily((String)person.attributes.get(Person.LAST_NAME));
		if (person.attributes.get(Person.NAME_PREFIX) != null) {
			name.addPrefix((String)person.attributes.get(Person.NAME_PREFIX));
		}
		if (person.attributes.get(Person.NAME_SUFFIX) != null) {
			name.addSuffix((String)person.attributes.get(Person.NAME_SUFFIX));
		}
		if (person.attributes.get(Person.MAIDEN_NAME) != null) {
			HumanName maidenName = patientResource.addName();
			maidenName.setUse(HumanName.NameUse.MAIDEN);
			maidenName.addGiven((String)person.attributes.get(Person.FIRST_NAME));
			maidenName.setFamily((String)person.attributes.get(Person.MAIDEN_NAME));
			if (person.attributes.get(Person.NAME_PREFIX) != null) {
				maidenName.addPrefix((String)person.attributes.get(Person.NAME_PREFIX));
			}
			if (person.attributes.get(Person.NAME_SUFFIX) != null) {
				maidenName.addSuffix((String)person.attributes.get(Person.NAME_SUFFIX));
			}
		}

		Extension birthSexExtension = new Extension("http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex");
		if (person.attributes.get(Person.GENDER).equals("M"))
		{
			patientResource.setGender(AdministrativeGender.MALE);
			birthSexExtension.setValue(new CodeType("M"));
		} else if (person.attributes.get(Person.GENDER).equals("F"))
		{
			patientResource.setGender(AdministrativeGender.FEMALE);
			birthSexExtension.setValue(new CodeType("F"));
		}
		patientResource.addExtension(birthSexExtension);

		Extension mothersMaidenNameExtension = new Extension("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName");
		String mothersMaidenName = (String)person.attributes.get(Person.NAME_MOTHER);
		mothersMaidenNameExtension.setValue(new StringType(mothersMaidenName));
		patientResource.addExtension(mothersMaidenNameExtension);

		long birthdate = (long)person.attributes.get(Person.BIRTHDATE);
		patientResource.setBirthDate(new Date(birthdate));

		Point coord = (Point)person.attributes.get(Person.COORDINATE);

		Address addrResource = patientResource.addAddress();
		addrResource.addLine((String)person.attributes.get(Person.ADDRESS))
			.setCity((String)person.attributes.get(Person.CITY))
			.setPostalCode((String)person.attributes.get(Person.ZIP))
			.setState((String)person.attributes.get(Person.STATE))
			.setCountry("US");

		patientResource.addTelecom()
			.setSystem(ContactPoint.ContactPointSystem.PHONE)
			.setUse(ContactPoint.ContactPointUse.HOME)
			.setValue((String)person.attributes.get(Person.TELECOM));

		String maritalStatus = ((String)person.attributes.get(Person.MARITAL_STATUS));
		if (maritalStatus != null) {
			Code maritalStatusCode = new Code("http://hl7.org/fhir/v3/MaritalStatus", maritalStatus, maritalStatus);
			patientResource.setMaritalStatus(mapCodeToCodeableConcept(maritalStatusCode, "http://hl7.org/fhir/v3/MaritalStatus"));
		} else {
			Code maritalStatusCode = new Code("http://hl7.org/fhir/v3/MaritalStatus", "S", "Never Married");
			patientResource.setMaritalStatus(mapCodeToCodeableConcept(maritalStatusCode, "http://hl7.org/fhir/v3/MaritalStatus"));
		}

		Extension geolocation = addrResource.addExtension();
		geolocation.setUrl("http://hl7.org/fhir/StructureDefinition/geolocation");
		geolocation.addExtension("latitude", new DecimalType(coord.getY()));
		geolocation.addExtension("longitude", new DecimalType(coord.getX()));

		if (!person.alive(stopTime))
		{
			patientResource.setDeceased( convertFhirDateTime(person.record.death, true) );
		}

		// TODO: ruby version also has:
		//  language, race, ethnicity, place of birth,
		// suffix, multiple birth status, fingerprint

		String generatedBySynthea =
				"Generated by <a href=\"https://github.com/synthetichealth/synthea\">Synthea</a>. "
				+ "Version identifier: JAVA-0.0.0 . "
				+ "Person seed: " + person.seed + "</div>"; // TODO

		patientResource.setText(new Narrative().setStatus(NarrativeStatus.GENERATED).setDiv(new XhtmlNode(NodeType.Element).setValue(generatedBySynthea)));

		// DALY and QALY values
		// we only write the last(current) one to the patient record
		Double dalyValue = (Double) person.attributes.get("most-recent-daly");
		Double qalyValue = (Double) person.attributes.get("most-recent-qaly");
		if (dalyValue != null)
		{
			Extension dalyExtension = new Extension(SNOMED_URI + "/disability-adjusted-life-years");
			DecimalType daly = new DecimalType(dalyValue);
			dalyExtension.setValue(daly);
			patientResource.addExtension(dalyExtension);

			Extension qalyExtension = new Extension(SNOMED_URI + "/quality-adjusted-life-years");
			DecimalType qaly = new DecimalType(qalyValue);
			qalyExtension.setValue(qaly);
			patientResource.addExtension(qalyExtension);
		}

		return newEntry(bundle, patientResource);
	}

	/**
	 * Map the given Encounter into a FHIR Encounter resource, and add it to the given Bundle.
	 * @param personEntry Entry for the Person
	 * @param bundle The Bundle to add to
	 * @param encounter The current Encounter
	 * @return The added Entry
	 */
	private static BundleEntryComponent encounter(BundleEntryComponent personEntry, Bundle bundle,
			Encounter encounter)
	{
		org.hl7.fhir.dstu3.model.Encounter encounterResource = new org.hl7.fhir.dstu3.model.Encounter();

		encounterResource.setSubject(new Reference(personEntry.getFullUrl()));
		encounterResource.setStatus(EncounterStatus.FINISHED);
		if (encounter.codes.isEmpty())
		{
			// wellness encounter
			encounterResource.addType().addCoding()
				.setCode("185349003")
				.setDisplay("Encounter for check up")
				.setSystem(SNOMED_URI);

		} else
		{
			Code code = encounter.codes.get(0);
			encounterResource.addType( mapCodeToCodeableConcept(code, SNOMED_URI) );
		}

		encounterResource.setClass_(new Coding().setCode(encounter.type));

		long encounter_end = encounter.stop > 0 ? encounter.stop : encounter.stop + TimeUnit.MINUTES.toMillis(15);

		encounterResource.setPeriod( new Period().setStart(new Date(encounter.start)).setEnd( new Date(encounter_end)) );

		// TODO: provider, reason, discharge

		return newEntry(bundle, encounterResource);
	}

	/**
	 * Create an entry for the given Claim, which references a Medication.
	 * @param personEntry Entry for the person
	 * @param bundle The Bundle to add to
	 * @param encounterEntry The current Encounter
	 * @param claim the Claim object
	 * @param medicationEntry The Entry for the Medication object, previously created
	 * @return the added Entry
	 */
	private static BundleEntryComponent medicationClaim(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Claim claim, BundleEntryComponent medicationEntry)
	{
		org.hl7.fhir.dstu3.model.Claim claimResource = new org.hl7.fhir.dstu3.model.Claim();
		org.hl7.fhir.dstu3.model.Encounter encounterResource = (org.hl7.fhir.dstu3.model.Encounter) encounterEntry.getResource();

		claimResource.setStatus(ClaimStatus.ACTIVE);
		claimResource.setUse(org.hl7.fhir.dstu3.model.Claim.Use.COMPLETE);

		//duration of encounter
		claimResource.setBillablePeriod(encounterResource.getPeriod());

		claimResource.setPatient(new Reference(personEntry.getFullUrl()));
		claimResource.setOrganization(encounterResource.getServiceProvider());

		//add item for encounter
		claimResource.addItem(new org.hl7.fhir.dstu3.model.Claim.ItemComponent().addEncounter(new Reference(encounterEntry.getFullUrl())));

		//add prescription.
		claimResource.setPrescription(new Reference(medicationEntry.getFullUrl()));

		Money moneyResource = new Money();
		moneyResource.setValue(claim.total());
		claimResource.setTotal(moneyResource);

		return newEntry(bundle, claimResource);
	}

	/**
	 * Create an entry for the given Claim, associated to an Encounter
	 * @param personEntry Entry for the person
	 * @param bundle The Bundle to add to
	 * @param encounterEntry The current Encounter
	 * @param claim the Claim object
	 * @return the added Entry
	 */
	private static BundleEntryComponent encounterClaim(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry,
			Claim claim)
	{
		org.hl7.fhir.dstu3.model.Claim claimResource = new org.hl7.fhir.dstu3.model.Claim();
		org.hl7.fhir.dstu3.model.Encounter encounterResource = (org.hl7.fhir.dstu3.model.Encounter) encounterEntry.getResource();
		claimResource.setStatus(ClaimStatus.ACTIVE);
		claimResource.setUse(org.hl7.fhir.dstu3.model.Claim.Use.COMPLETE);

		//duration of encounter
		claimResource.setBillablePeriod(encounterResource.getPeriod());

		claimResource.setPatient(new Reference(personEntry.getFullUrl()));
		claimResource.setOrganization(encounterResource.getServiceProvider());

		//add item for encounter
		claimResource.addItem(new org.hl7.fhir.dstu3.model.Claim.ItemComponent().addEncounter(new Reference(encounterEntry.getFullUrl())));

		int conditionSequence = 1;
		int procedureSequence = 1;
		for (ClaimItem item : claim.items)
		{
			if (item.entry instanceof Procedure)
			{
				Type procedureReference= new Reference(item.entry.fullUrl);
				org.hl7.fhir.dstu3.model.Claim.ProcedureComponent claimProcedure =
						new org.hl7.fhir.dstu3.model.Claim.ProcedureComponent(new PositiveIntType(procedureSequence), procedureReference);
				claimResource.addProcedure(claimProcedure);

				//update claimItems list
				org.hl7.fhir.dstu3.model.Claim.ItemComponent procedureItem = new org.hl7.fhir.dstu3.model.Claim.ItemComponent();
				procedureItem.addProcedureLinkId(procedureSequence); // TODO ??? this field needs more description

				//calculate cost of procedure based on rvu values for a facility
				Money moneyResource = new Money();

				moneyResource.setValue(item.cost());
				procedureItem.setNet(moneyResource);
				claimResource.addItem(procedureItem);

				procedureSequence++;
			}
			else
			{
				// assume it's a Condition, we don't have a Condition class specifically
				//add diagnosisComponent to claim
				Reference diagnosisReference = new Reference(item.entry.fullUrl);
				org.hl7.fhir.dstu3.model.Claim.DiagnosisComponent diagnosisComponent =
						new org.hl7.fhir.dstu3.model.Claim.DiagnosisComponent(new PositiveIntType(conditionSequence),diagnosisReference);
				claimResource.addDiagnosis(diagnosisComponent);

				//update claimItems with diagnosis
				org.hl7.fhir.dstu3.model.Claim.ItemComponent diagnosisItem = new org.hl7.fhir.dstu3.model.Claim.ItemComponent();
				diagnosisItem.addDiagnosisLinkId(conditionSequence);
				claimResource.addItem(diagnosisItem);

				conditionSequence++;
			}
		}

		Money moneyResource = new Money();
		moneyResource.setValue(claim.total());
		claimResource.setTotal(moneyResource);

		return newEntry(bundle, claimResource);
	}

	/**
	 * Map the Condition into a FHIR Condition resource, and add it to the given Bundle.
	 * @param personEntry The Entry for the Person
	 * @param bundle The Bundle to add to
	 * @param encounterEntry The current Encounter entry
	 * @param condition The Condition
	 * @return The added Entry
	 */
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

		BundleEntryComponent conditionEntry = newEntry(bundle, conditionResource);

		condition.fullUrl = conditionEntry.getFullUrl();

		return conditionEntry;
	}

	/**
	 * Map the given Observation into a FHIR Observation resource, and add it to the given Bundle.
	 * @param personEntry The Person Entry
	 * @param bundle The Bundle to add to
	 * @param encounterEntry The current Encounter entry
	 * @param observation The Observation
	 * @return The added Entry
	 */
	private static BundleEntryComponent observation(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Observation observation)
	{
		org.hl7.fhir.dstu3.model.Observation observationResource = new org.hl7.fhir.dstu3.model.Observation();

		observationResource.setSubject(new Reference(personEntry.getFullUrl()));
		observationResource.setContext(new Reference(encounterEntry.getFullUrl()));

		observationResource.setStatus(ObservationStatus.FINAL);

		Code code = observation.codes.get(0);
		observationResource.setCode( mapCodeToCodeableConcept(code, LOINC_URI) );

		observationResource.addCategory().addCoding()
				.setCode(observation.category)
				.setSystem("http://hl7.org/fhir/observation-category")
				.setDisplay(observation.category);

		Type value = null;
		if (observation.value instanceof Condition)
		{
			Code conditionCode = ((HealthRecord.Entry)observation.value).codes.get(0);
			value = mapCodeToCodeableConcept(conditionCode, SNOMED_URI);
		} else if (observation.value instanceof String)
		{
			value = new StringType((String)observation.value);
		} else if (observation.value instanceof Number)
		{
			value = new Quantity()
				.setValue(((Number)observation.value).doubleValue())
				.setCode(observation.unit)
				.setSystem("http://unitsofmeasure.org/")
				.setUnit(observation.unit);
		} else if (observation.value != null)
		{
			throw new IllegalArgumentException("unexpected observation value class: " + observation.value.getClass().toString() + "; " + observation.value);
		}

		if (value != null)
		{
			observationResource.setValue(value);
		}

		observationResource.setEffective(convertFhirDateTime(observation.start, true));
		observationResource.setIssued(new Date(observation.start));

		BundleEntryComponent entry = newEntry(bundle, observationResource);
		observation.fullUrl = entry.getFullUrl();
		return entry;
	}

	/**
	 * Map the given Procedure into a FHIR Procedure resource, and add it to the given Bundle.
	 * @param personEntry The Person entry
	 * @param bundle Bundle to add to
	 * @param encounterEntry The current Encounter entry
	 * @param procedure The Procedure
	 * @return The added Entry
	 */
	private static BundleEntryComponent procedure(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Procedure procedure)
	{
		org.hl7.fhir.dstu3.model.Procedure procedureResource = new org.hl7.fhir.dstu3.model.Procedure();

		procedureResource.setSubject(new Reference(personEntry.getFullUrl()));
		procedureResource.setContext(new Reference(encounterEntry.getFullUrl()));

		Code code = procedure.codes.get(0);
		procedureResource.setCode( mapCodeToCodeableConcept(code, SNOMED_URI) );

		if (procedure.stop > 0L)
		{
			Date startDate = new Date(procedure.start);
			Date endDate = new Date(procedure.stop);
			procedureResource.setPerformed(new Period().setStart(startDate).setEnd(endDate));
		} else
		{
			procedureResource.setPerformed(convertFhirDateTime(procedure.start, true));
		}

		BundleEntryComponent procedureEntry = newEntry(bundle, procedureResource);
		// TODO - reason

		procedure.fullUrl = procedureEntry.getFullUrl();

		return procedureEntry;
	}

	private static BundleEntryComponent immunization(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, HealthRecord.Entry immunization)
	{
		Immunization immResource = new Immunization();
		immResource.setStatus(ImmunizationStatus.COMPLETED);
		immResource.setDate(new Date(immunization.start));
		immResource.setVaccineCode(mapCodeToCodeableConcept(immunization.codes.get(0), CVX_URI));
		immResource.setNotGiven(false);
		immResource.setPrimarySource(true);
		immResource.setPatient(new Reference(personEntry.getFullUrl()));
		immResource.setEncounter(new Reference(encounterEntry.getFullUrl()));
		return newEntry(bundle, immResource);
	}

	/**
	 * Map the given Medication to a FHIR MedicationRequest resource, and add it to the given Bundle.
	 * @param personEntry The Entry for the Person
	 * @param bundle Bundle to add the Medication to
	 * @param encounterEntry Current Encounter entry
	 * @param medication The Medication
	 * @return The added Entry
	 */
	private static BundleEntryComponent medication(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Medication medication)
	{
		MedicationRequest medicationResource = new MedicationRequest();

		medicationResource.setSubject(new Reference(personEntry.getFullUrl()));
		medicationResource.setContext(new Reference(encounterEntry.getFullUrl()));

		medicationResource.setMedication(mapCodeToCodeableConcept(medication.codes.get(0), RXNORM_URI));

		medicationResource.setAuthoredOn(new Date(medication.start));
		medicationResource.setIntent(MedicationRequestIntent.ORDER);

		if (medication.stop > 0L)
		{
			medicationResource.setStatus(MedicationRequestStatus.STOPPED);
		} else
		{
			medicationResource.setStatus(MedicationRequestStatus.ACTIVE);
		}

		// TODO - prescription details & reason

		BundleEntryComponent medicationEntry = newEntry(bundle, medicationResource);
		//create new claim for medication
		medicationClaim(personEntry, bundle, encounterEntry, medication.claim, medicationEntry);

		return medicationEntry;
	}

	/**
	 * Map the given Report to a FHIR DiagnosticReport resource, and add it to the given Bundle.
	 * @param personEntry The Entry for the Person
	 * @param bundle Bundle to add the Report to
	 * @param encounterEntry Current Encounter entry
	 * @param report The Report
	 * @return The added Entry
	 */
	private static BundleEntryComponent report(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, Report report)
	{
		DiagnosticReport reportResource = new DiagnosticReport();
		reportResource.setStatus(DiagnosticReportStatus.FINAL);
		reportResource.setCode(mapCodeToCodeableConcept(report.codes.get(0), LOINC_URI));
		reportResource.setSubject(new Reference(personEntry.getFullUrl()));
		reportResource.setContext(new Reference(encounterEntry.getFullUrl()));
		reportResource.setEffective(convertFhirDateTime(report.start, true));
		reportResource.setIssued(new Date(report.start));
		for(Observation observation : report.observations) {
			Reference reference = new Reference(observation.fullUrl);
			reference.setDisplay(observation.codes.get(0).display);
			reportResource.addResult(reference);
		}

		return newEntry(bundle, reportResource);
	}

	/**
	 * Map the given CarePlan to a FHIR CarePlan resource, and add it to the given Bundle.
	 * @param personEntry The Entry for the Person
	 * @param bundle Bundle to add the CarePlan to
	 * @param encounterEntry Current Encounter entry
	 * @param carePlan The CarePlan to map to FHIR and add to the bundle
	 * @return The added Entry
	 */
	private static BundleEntryComponent careplan(BundleEntryComponent personEntry, Bundle bundle,
			BundleEntryComponent encounterEntry, CarePlan carePlan)
	{
		org.hl7.fhir.dstu3.model.CarePlan careplanResource = new org.hl7.fhir.dstu3.model.CarePlan();

		careplanResource.setSubject(new Reference(personEntry.getFullUrl()));
		careplanResource.setContext(new Reference(encounterEntry.getFullUrl()));

		Code code = carePlan.codes.get(0);
		careplanResource.addCategory( mapCodeToCodeableConcept(code, SNOMED_URI) );

		CarePlanActivityStatus activityStatus;
		GoalStatus goalStatus;

		Period period = new Period().setStart(new Date(carePlan.start));
		careplanResource.setPeriod(period);
		if (carePlan.stop > 0L)
		{
			period.setEnd(new Date(carePlan.stop));
			careplanResource.setStatus(CarePlanStatus.COMPLETED);
			activityStatus = CarePlanActivityStatus.COMPLETED;
			goalStatus = GoalStatus.ACHIEVED;
		} else
		{
			careplanResource.setStatus(CarePlanStatus.ACTIVE);
			activityStatus = CarePlanActivityStatus.INPROGRESS;
			goalStatus = GoalStatus.INPROGRESS;
		}

		// TODO - goals, activities, reasons

		return newEntry(bundle, careplanResource);
	}

	/**
	 * Convert the timestamp into a FHIR DateType or DateTimeType.
	 *
	 * @param datetime Timestamp
	 * @param time If true, return a DateTime; if false, return a Date.
	 * @return a DateType or DateTimeType representing the given timestamp
	 */
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

	/**
	 * Helper function to convert a Code into a CodeableConcept.
	 * Takes an optional system, which replaces the Code.system in the resulting CodeableConcept if not null.
	 *
	 * @param from The Code to create a CodeableConcept from.
	 * @param system The system identifier, such as a URI. Optional; may be null.
	 * @return The converted CodeableConcept
	 */
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

	/**
	 * Helper function to create an Entry for the given Resource within the given Bundle.
	 * Sets the resourceID to a random UUID, sets the entry's fullURL to that resourceID,
	 * and adds the entry to the bundle.
	 *
	 * @param bundle The Bundle to add the Entry to
	 * @param resource Resource the new Entry should contain
	 * @return the created Entry
	 */
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
