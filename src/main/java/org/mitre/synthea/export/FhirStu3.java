package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.AllergyIntolerance;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCategory;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceClinicalStatus;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceCriticality;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceType;
import org.hl7.fhir.dstu3.model.AllergyIntolerance.AllergyIntoleranceVerificationStatus;
import org.hl7.fhir.dstu3.model.Basic;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Bundle.HTTPVerb;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanActivityComponent;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanActivityDetailComponent;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanActivityStatus;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanIntent;
import org.hl7.fhir.dstu3.model.CarePlan.CarePlanStatus;
import org.hl7.fhir.dstu3.model.Claim.ClaimStatus;
import org.hl7.fhir.dstu3.model.Claim.ItemComponent;
import org.hl7.fhir.dstu3.model.Claim.ProcedureComponent;
import org.hl7.fhir.dstu3.model.Claim.SpecialConditionComponent;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus;
import org.hl7.fhir.dstu3.model.Condition.ConditionVerificationStatus;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.dstu3.model.ContactPoint.ContactPointUse;
import org.hl7.fhir.dstu3.model.Coverage;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.DateType;
import org.hl7.fhir.dstu3.model.DecimalType;
import org.hl7.fhir.dstu3.model.Device;
import org.hl7.fhir.dstu3.model.Device.FHIRDeviceStatus;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.DiagnosticReport.DiagnosticReportStatus;
import org.hl7.fhir.dstu3.model.Dosage;
import org.hl7.fhir.dstu3.model.Encounter.EncounterHospitalizationComponent;
import org.hl7.fhir.dstu3.model.Encounter.EncounterStatus;
import org.hl7.fhir.dstu3.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Goal.GoalStatus;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.ImagingStudy.ImagingStudySeriesComponent;
import org.hl7.fhir.dstu3.model.ImagingStudy.ImagingStudySeriesInstanceComponent;
import org.hl7.fhir.dstu3.model.ImagingStudy.InstanceAvailability;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Immunization.ImmunizationStatus;
import org.hl7.fhir.dstu3.model.IntegerType;
import org.hl7.fhir.dstu3.model.Media.DigitalMediaType;
import org.hl7.fhir.dstu3.model.MedicationAdministration;
import org.hl7.fhir.dstu3.model.MedicationAdministration.MedicationAdministrationDosageComponent;
import org.hl7.fhir.dstu3.model.MedicationAdministration.MedicationAdministrationStatus;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestRequesterComponent;
import org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Money;
import org.hl7.fhir.dstu3.model.Narrative;
import org.hl7.fhir.dstu3.model.Narrative.NarrativeStatus;
import org.hl7.fhir.dstu3.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Patient.ContactComponent;
import org.hl7.fhir.dstu3.model.Patient.PatientCommunicationComponent;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.PositiveIntType;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.Procedure.ProcedureStatus;
import org.hl7.fhir.dstu3.model.Property;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ReferralRequest;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.SimpleQuantity;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.SupplyDelivery;
import org.hl7.fhir.dstu3.model.SupplyDelivery.SupplyDeliveryStatus;
import org.hl7.fhir.dstu3.model.SupplyDelivery.SupplyDeliverySuppliedItemComponent;
import org.hl7.fhir.dstu3.model.Timing;
import org.hl7.fhir.dstu3.model.Timing.TimingRepeatComponent;
import org.hl7.fhir.dstu3.model.Timing.UnitsOfTime;
import org.hl7.fhir.dstu3.model.Type;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.mitre.synthea.engine.Components;
import org.mitre.synthea.engine.Components.Attachment;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public class FhirStu3 {
  // HAPI FHIR warns that the context creation is expensive, and should be performed
  // per-application, not per-record
  private static final FhirContext FHIR_CTX = FhirContext.forDstu3();

  private static final String SNOMED_URI = "http://snomed.info/sct";
  private static final String LOINC_URI = "http://loinc.org";
  private static final String RXNORM_URI = "http://www.nlm.nih.gov/research/umls/rxnorm";
  private static final String CVX_URI = "http://hl7.org/fhir/sid/cvx";
  private static final String DISCHARGE_URI = "http://www.nubc.org/patient-discharge";
  private static final String SHR_EXT = "http://standardhealthrecord.org/fhir/StructureDefinition/";
  private static final String SYNTHEA_EXT = "http://synthetichealth.github.io/synthea/";
  private static final String UNITSOFMEASURE_URI = "http://unitsofmeasure.org";
  private static final String DICOM_DCM_URI = "http://dicom.nema.org/resources/ontology/DCM";

  @SuppressWarnings("rawtypes")
  private static final Map raceEthnicityCodes = loadRaceEthnicityCodes();
  @SuppressWarnings("rawtypes")
  private static final Map languageLookup = loadLanguageLookup();

  private static final boolean USE_SHR_EXTENSIONS =
      Config.getAsBoolean("exporter.fhir.use_shr_extensions");
  protected static boolean TRANSACTION_BUNDLE =
      Config.getAsBoolean("exporter.fhir.transaction_bundle");

  private static final String COUNTRY_CODE = Config.get("generate.geography.country_code");

  private static final Table<String,String,String> SHR_MAPPING = loadSHRMapping();

  @SuppressWarnings("rawtypes")
  private static Map loadRaceEthnicityCodes() {
    String filename = "race_ethnicity_codes.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      return g.fromJson(json, HashMap.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  @SuppressWarnings("rawtypes")
  private static Map loadLanguageLookup() {
    String filename = "language_lookup.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      return g.fromJson(json, HashMap.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }


  private static Table<String, String, String> loadSHRMapping() {
    if (!USE_SHR_EXTENSIONS) {
      // don't bother creating the table unless we need it
      return null;
    }
    Table<String,String,String> mappingTable = HashBasedTable.create();

    List<LinkedHashMap<String,String>> csvData;
    try {
      csvData = SimpleCSV.parse(Utilities.readResource("shr_mapping.csv"));
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }

    for (LinkedHashMap<String,String> line : csvData) {
      String system = line.get("SYSTEM");
      String code = line.get("CODE");
      String url = line.get("URL");

      mappingTable.put(system, code, url);
    }

    return mappingTable;
  }
  
  public static FhirContext getContext() {
    return FHIR_CTX;
  }

  /**
   * Convert the given Person into a FHIR Bundle, containing the Patient and the
   * associated entries from their health record.
   *
   * @param person Person to generate the FHIR from
   * @param stopTime Time the simulation ended
   * @return FHIR Bundle containing the Person's health record.
   */
  public static Bundle convertToFHIR(Person person, long stopTime) {
    Bundle bundle = new Bundle();
    if (TRANSACTION_BUNDLE) {
      bundle.setType(BundleType.TRANSACTION);
    } else {
      bundle.setType(BundleType.COLLECTION);
    }

    BundleEntryComponent personEntry = basicInfo(person, bundle, stopTime);

    for (Encounter encounter : person.record.encounters) {
      BundleEntryComponent encounterEntry = encounter(person, personEntry, bundle, encounter);

      for (HealthRecord.Entry condition : encounter.conditions) {
        condition(person, personEntry, bundle, encounterEntry, condition);
      }

      for (HealthRecord.Entry allergy : encounter.allergies) {
        allergy(person, personEntry, bundle, encounterEntry, allergy);
      }

      for (Observation observation : encounter.observations) {
        // If the Observation contains an attachment, use a Media resource, since
        // Observation resources in stu3 don't support Attachments
        if (observation.value instanceof Attachment) {
          media(person, personEntry, bundle, encounterEntry, observation);
        } else {
          observation(person, personEntry, bundle, encounterEntry, observation);
        }
      }

      for (Procedure procedure : encounter.procedures) {
        procedure(person, personEntry, bundle, encounterEntry, procedure);
      }

      for (Medication medication : encounter.medications) {
        medication(person, personEntry, bundle, encounterEntry, medication);
      }

      for (HealthRecord.Entry immunization : encounter.immunizations) {
        immunization(person, personEntry, bundle, encounterEntry, immunization);
      }

      for (Report report : encounter.reports) {
        report(person, personEntry, bundle, encounterEntry, report);
      }

      for (CarePlan careplan : encounter.careplans) {
        careplan(person, personEntry, bundle, encounterEntry, careplan);
      }

      for (ImagingStudy imagingStudy : encounter.imagingStudies) {
        imagingStudy(person, personEntry, bundle, encounterEntry, imagingStudy);
      }

      for (HealthRecord.Device device : encounter.devices) {
        device(person, personEntry, bundle, device);
      }
      
      for (HealthRecord.Supply supply : encounter.supplies) {
        supplyDelivery(person, personEntry, bundle, supply, encounter);
      }
      
      // one claim per encounter
      BundleEntryComponent encounterClaim = encounterClaim(person, personEntry, bundle,
          encounterEntry, encounter.claim);

      explanationOfBenefit(personEntry,bundle,encounterEntry,person,
          encounterClaim, encounter);
    }
    return bundle;
  }

  /**
   * Convert the given Person into a JSON String, containing a FHIR Bundle of the Person and the
   * associated entries from their health record.
   *
   * @param person Person to generate the FHIR JSON for
   * @param stopTime Time the simulation ended
   * @return String containing a JSON representation of a FHIR Bundle containing the Person's 
   *     health record.
   */
  public static String convertToFHIRJson(Person person, long stopTime) {
    Bundle bundle = convertToFHIR(person, stopTime);
    String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true)
        .encodeResourceToString(bundle);
    return bundleJson;
  }

  /**
   * Map the given Person to a FHIR Patient resource, and add it to the given Bundle.
   *
   * @param person The Person
   * @param bundle The Bundle to add to
   * @param stopTime Time the simulation ended
   * @return The created Entry
   */
  @SuppressWarnings("rawtypes")
  private static BundleEntryComponent basicInfo(Person person, Bundle bundle, long stopTime) {
    Patient patientResource = new Patient();

    patientResource.addIdentifier().setSystem("https://github.com/synthetichealth/synthea")
        .setValue((String) person.attributes.get(Person.ID));

    Code mrnCode = new Code("http://hl7.org/fhir/v2/0203", "MR", "Medical Record Number");
    patientResource.addIdentifier()
        .setType(mapCodeToCodeableConcept(mrnCode, "http://hl7.org/fhir/v2/0203"))
        .setSystem("http://hospital.smarthealthit.org")
        .setValue((String) person.attributes.get(Person.ID));

    Code ssnCode = new Code("http://hl7.org/fhir/identifier-type", "SB", "Social Security Number");
    patientResource.addIdentifier()
        .setType(mapCodeToCodeableConcept(ssnCode, "http://hl7.org/fhir/identifier-type"))
        .setSystem("http://hl7.org/fhir/sid/us-ssn")
        .setValue((String) person.attributes.get(Person.IDENTIFIER_SSN));

    if (person.attributes.get(Person.IDENTIFIER_DRIVERS) != null) {
      Code driversCode = new Code("http://hl7.org/fhir/v2/0203", "DL", "Driver's License");
      patientResource.addIdentifier()
          .setType(mapCodeToCodeableConcept(driversCode, "http://hl7.org/fhir/v2/0203"))
          .setSystem("urn:oid:2.16.840.1.113883.4.3.25")
          .setValue((String) person.attributes.get(Person.IDENTIFIER_DRIVERS));
    }

    if (person.attributes.get(Person.IDENTIFIER_PASSPORT) != null) {
      Code passportCode = new Code("http://hl7.org/fhir/v2/0203", "PPN", "Passport Number");
      patientResource.addIdentifier()
          .setType(mapCodeToCodeableConcept(passportCode, "http://hl7.org/fhir/v2/0203"))
          .setSystem(SHR_EXT + "passportNumber")
          .setValue((String) person.attributes.get(Person.IDENTIFIER_PASSPORT));
    }

    if (person.attributes.get(Person.CONTACT_EMAIL) != null) {
      ContactComponent contact = new ContactComponent();
      HumanName contactName = new HumanName();
      contactName.setUse(HumanName.NameUse.OFFICIAL);
      contactName.addGiven((String) person.attributes.get(Person.CONTACT_GIVEN_NAME));
      contactName.setFamily((String) person.attributes.get(Person.CONTACT_FAMILY_NAME));
      contact.setName(contactName);
      contact.addTelecom().setSystem(ContactPointSystem.EMAIL)
          .setUse(ContactPointUse.HOME)
          .setValue((String) person.attributes.get(Person.CONTACT_EMAIL));
      patientResource.addContact(contact);
    }

    // We do not yet account for mixed race
    Extension raceExtension = new Extension(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race");
    String race = (String) person.attributes.get(Person.RACE);

    String raceDisplay;
    switch (race) {
      case "white":
        raceDisplay = "White";
        break;
      case "black":
        raceDisplay = "Black or African American";
        break;
      case "asian":
        raceDisplay = "Asian";
        break;
      case "native":
        raceDisplay = "American Indian or Alaska Native";
        break;
      case "hawaiian":
        raceDisplay = "Native Hawaiian or Other Pacific Islander";
        break;
      default:
        raceDisplay = "Other";
        break;
    }

    String raceNum = (String) raceEthnicityCodes.get(race);

    Extension raceCodingExtension = new Extension("ombCategory");
    Coding raceCoding = new Coding();
    if (raceDisplay.equals("Other")) {
      raceCoding.setSystem("http://hl7.org/fhir/v3/NullFlavor");
      raceCoding.setCode("UNK");
      raceCoding.setDisplay("Unknown");
    } else {
      raceCoding.setSystem("urn:oid:2.16.840.1.113883.6.238");
      raceCoding.setCode(raceNum);
      raceCoding.setDisplay(raceDisplay);
    }
    raceCodingExtension.setValue(raceCoding);
    raceExtension.addExtension(raceCodingExtension);

    Extension raceTextExtension = new Extension("text");
    raceTextExtension.setValue(new StringType(raceDisplay));

    raceExtension.addExtension(raceTextExtension);

    patientResource.addExtension(raceExtension);

    // We do not yet account for mixed ethnicity
    Extension ethnicityExtension = new Extension(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity");
    String ethnicity = (String) person.attributes.get(Person.ETHNICITY);

    String ethnicityDisplay;
    if (ethnicity.equals("hispanic")) {
      ethnicity = "hispanic";
      ethnicityDisplay = "Hispanic or Latino";
    } else {
      ethnicity = "nonhispanic";
      ethnicityDisplay = "Not Hispanic or Latino";
    }

    String ethnicityNum = (String) raceEthnicityCodes.get(ethnicity);

    Extension ethnicityCodingExtension = new Extension("ombCategory");
    Coding ethnicityCoding = new Coding();
    ethnicityCoding.setSystem("urn:oid:2.16.840.1.113883.6.238");
    ethnicityCoding.setCode(ethnicityNum);
    ethnicityCoding.setDisplay(ethnicityDisplay);
    ethnicityCodingExtension.setValue(ethnicityCoding);

    ethnicityExtension.addExtension(ethnicityCodingExtension);

    Extension ethnicityTextExtension = new Extension("text");
    ethnicityTextExtension.setValue(new StringType(ethnicityDisplay));

    ethnicityExtension.addExtension(ethnicityTextExtension);

    patientResource.addExtension(ethnicityExtension);

    String firstLanguage = (String) person.attributes.get(Person.FIRST_LANGUAGE);
    Map languageMap = (Map) languageLookup.get(firstLanguage);
    Code languageCode = new Code((String) languageMap.get("system"),
        (String) languageMap.get("code"), (String) languageMap.get("display"));
    List<PatientCommunicationComponent> communication =
        new ArrayList<PatientCommunicationComponent>();
    communication.add(new PatientCommunicationComponent(
        mapCodeToCodeableConcept(languageCode, (String) languageMap.get("system"))));
    patientResource.setCommunication(communication);

    HumanName name = patientResource.addName();
    name.setUse(HumanName.NameUse.OFFICIAL);
    name.addGiven((String) person.attributes.get(Person.FIRST_NAME));
    name.setFamily((String) person.attributes.get(Person.LAST_NAME));
    if (person.attributes.get(Person.NAME_PREFIX) != null) {
      name.addPrefix((String) person.attributes.get(Person.NAME_PREFIX));
    }
    if (person.attributes.get(Person.NAME_SUFFIX) != null) {
      name.addSuffix((String) person.attributes.get(Person.NAME_SUFFIX));
    }
    if (person.attributes.get(Person.MAIDEN_NAME) != null) {
      HumanName maidenName = patientResource.addName();
      maidenName.setUse(HumanName.NameUse.MAIDEN);
      maidenName.addGiven((String) person.attributes.get(Person.FIRST_NAME));
      maidenName.setFamily((String) person.attributes.get(Person.MAIDEN_NAME));
      if (person.attributes.get(Person.NAME_PREFIX) != null) {
        maidenName.addPrefix((String) person.attributes.get(Person.NAME_PREFIX));
      }
      if (person.attributes.get(Person.NAME_SUFFIX) != null) {
        maidenName.addSuffix((String) person.attributes.get(Person.NAME_SUFFIX));
      }
    }

    Extension mothersMaidenNameExtension = new Extension(
        "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName");
    String mothersMaidenName = (String) person.attributes.get(Person.NAME_MOTHER);
    mothersMaidenNameExtension.setValue(new StringType(mothersMaidenName));
    patientResource.addExtension(mothersMaidenNameExtension);

    long birthdate = (long) person.attributes.get(Person.BIRTHDATE);
    patientResource.setBirthDate(new Date(birthdate));

    Extension birthSexExtension = new Extension(
        "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex");
    if (person.attributes.get(Person.GENDER).equals("M")) {
      patientResource.setGender(AdministrativeGender.MALE);
      birthSexExtension.setValue(new CodeType("M"));
    } else if (person.attributes.get(Person.GENDER).equals("F")) {
      patientResource.setGender(AdministrativeGender.FEMALE);
      birthSexExtension.setValue(new CodeType("F"));
    }
    patientResource.addExtension(birthSexExtension);

    String state = (String) person.attributes.get(Person.STATE);

    Address addrResource = patientResource.addAddress();
    addrResource.addLine((String) person.attributes.get(Person.ADDRESS))
        .setCity((String) person.attributes.get(Person.CITY))
        .setPostalCode((String) person.attributes.get(Person.ZIP))
        .setState(state);
    if (COUNTRY_CODE != null) {
      addrResource.setCountry(COUNTRY_CODE);
    }

    Address birthplace = new Address();
    birthplace.setCity((String) person.attributes.get(Person.BIRTH_CITY))
            .setState((String) person.attributes.get(Person.BIRTH_STATE))
            .setCountry((String) person.attributes.get(Person.BIRTH_COUNTRY));

    Extension birthplaceExtension = new Extension(
        "http://hl7.org/fhir/StructureDefinition/birthPlace");
    birthplaceExtension.setValue(birthplace);
    patientResource.addExtension(birthplaceExtension);

    if (person.attributes.get(Person.MULTIPLE_BIRTH_STATUS) != null) {
      patientResource.setMultipleBirth(
          new IntegerType((int) person.attributes.get(Person.MULTIPLE_BIRTH_STATUS)));
    } else {
      patientResource.setMultipleBirth(new BooleanType(false));
    }

    patientResource.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE)
        .setUse(ContactPoint.ContactPointUse.HOME)
        .setValue((String) person.attributes.get(Person.TELECOM));

    String maritalStatus = ((String) person.attributes.get(Person.MARITAL_STATUS));
    if (maritalStatus != null) {
      Code maritalStatusCode = new Code("http://hl7.org/fhir/v3/MaritalStatus", maritalStatus,
          maritalStatus);
      patientResource.setMaritalStatus(
          mapCodeToCodeableConcept(maritalStatusCode, "http://hl7.org/fhir/v3/MaritalStatus"));
    } else {
      Code maritalStatusCode = new Code("http://hl7.org/fhir/v3/MaritalStatus", "S",
          "Never Married");
      patientResource.setMaritalStatus(
          mapCodeToCodeableConcept(maritalStatusCode, "http://hl7.org/fhir/v3/MaritalStatus"));
    }

    Point2D.Double coord = person.getLonLat();
    if (coord != null) {
      Extension geolocation = addrResource.addExtension();
      geolocation.setUrl("http://hl7.org/fhir/StructureDefinition/geolocation");
      geolocation.addExtension("latitude", new DecimalType(coord.getY()));
      geolocation.addExtension("longitude", new DecimalType(coord.getX()));
    }

    if (!person.alive(stopTime)) {
      patientResource.setDeceased(
          convertFhirDateTime((Long) person.attributes.get(Person.DEATHDATE), true));
    }

    String generatedBySynthea = "Generated by <a href=\"https://github.com/synthetichealth/synthea\">Synthea</a>."
        + "Version identifier: " + Utilities.SYNTHEA_VERSION + " . "
        + "  Person seed: " + person.seed
        + "  Population seed: " + person.populationSeed;

    patientResource.setText(new Narrative().setStatus(NarrativeStatus.GENERATED)
        .setDiv(new XhtmlNode(NodeType.Element).setValue(generatedBySynthea)));

    if (USE_SHR_EXTENSIONS) {

      patientResource.setMeta(new Meta().addProfile(SHR_EXT + "shr-entity-Patient"));

      // Patient profile requires race, ethnicity, birthsex,
      // MothersMaidenName, FathersName, Person-extension

      patientResource.addExtension()
        .setUrl(SHR_EXT + "shr-actor-FictionalPerson-extension")
        .setValue(new BooleanType(true));

      String fathersName = (String) person.attributes.get(Person.NAME_FATHER);
      Extension fathersNameExtension = new Extension(
          SHR_EXT + "shr-entity-FathersName-extension", new HumanName().setText(fathersName));
      patientResource.addExtension(fathersNameExtension);

      String ssn = (String) person.attributes.get(Person.IDENTIFIER_SSN);
      Extension ssnExtension = new Extension(
          SHR_EXT + "shr-demographics-SocialSecurityNumber-extension",
          new StringType(ssn));
      patientResource.addExtension(ssnExtension);

      Basic personResource = new Basic();
      // the only required field on this patient resource is code

      Coding fixedCode = new Coding(
          "http://standardhealthrecord.org/fhir/basic-resource-type",
          "shr-entity-Person", "shr-entity-Person");
      personResource.setCode(new CodeableConcept().addCoding(fixedCode));

      Meta personMeta = new Meta();
      personMeta.addProfile(SHR_EXT + "shr-entity-Person");
      personResource.setMeta(personMeta);

      BundleEntryComponent personEntry = newEntry(person, bundle, personResource);
      patientResource.addExtension()
          .setUrl(SHR_EXT + "shr-entity-Person-extension")
          .setValue(new Reference(personEntry.getFullUrl()));
    }

    // DALY and QALY values
    // we only write the last(current) one to the patient record
    Double dalyValue = (Double) person.attributes.get("most-recent-daly");
    Double qalyValue = (Double) person.attributes.get("most-recent-qaly");
    if (dalyValue != null) {
      Extension dalyExtension = new Extension(SYNTHEA_EXT + "disability-adjusted-life-years");
      DecimalType daly = new DecimalType(dalyValue);
      dalyExtension.setValue(daly);
      patientResource.addExtension(dalyExtension);

      Extension qalyExtension = new Extension(SYNTHEA_EXT + "quality-adjusted-life-years");
      DecimalType qaly = new DecimalType(qalyValue);
      qalyExtension.setValue(qaly);
      patientResource.addExtension(qalyExtension);
    }

    return newEntry(bundle, patientResource, (String) person.attributes.get(Person.ID));
  }

  /**
   * Map the given Encounter into a FHIR Encounter resource, and add it to the given Bundle.
   *
   * @param personEntry Entry for the Person
   * @param bundle The Bundle to add to
   * @param encounter The current Encounter
   * @return The added Entry
   */
  private static BundleEntryComponent encounter(Person person, BundleEntryComponent personEntry,
      Bundle bundle, Encounter encounter) {

    org.hl7.fhir.dstu3.model.Encounter encounterResource = new org.hl7.fhir.dstu3.model.Encounter();

    encounterResource.setSubject(new Reference(personEntry.getFullUrl()));
    encounterResource.setStatus(EncounterStatus.FINISHED);
    if (encounter.codes.isEmpty()) {
      // wellness encounter
      encounterResource.addType().addCoding().setCode("185349003")
          .setDisplay("Encounter for check up").setSystem(SNOMED_URI);

    } else {
      Code code = encounter.codes.get(0);
      encounterResource.addType(mapCodeToCodeableConcept(code, SNOMED_URI));
    }

    Coding classCode = new Coding();
    classCode.setCode(EncounterType.fromString(encounter.type).code());
    classCode.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
    encounterResource.setClass_(classCode);
    encounterResource
        .setPeriod(new Period()
            .setStart(new Date(encounter.start))
            .setEnd(new Date(encounter.stop)));

    if (encounter.reason != null) {
      encounterResource.addReason().addCoding().setCode(encounter.reason.code)
          .setDisplay(encounter.reason.display).setSystem(SNOMED_URI);
    }

    Provider provider = encounter.provider;
    if (provider == null) {
      // no associated provider, patient goes to wellness provider
      provider = person.getProvider(EncounterType.WELLNESS, encounter.start);
    }
    
    if (TRANSACTION_BUNDLE) {
      encounterResource.setServiceProvider(new Reference(
              ExportHelper.buildFhirSearchUrl("Organization", provider.getResourceID())));
    } else {
      String providerFullUrl = findProviderUrl(provider, bundle);
      if (providerFullUrl != null) {
        encounterResource.setServiceProvider(new Reference(providerFullUrl));
      } else {
        BundleEntryComponent providerOrganization = provider(bundle, provider);
        encounterResource.setServiceProvider(new Reference(providerOrganization.getFullUrl()));
      }
    }
    encounterResource.getServiceProvider().setDisplay(provider.name);

    if (encounter.clinician != null) {
      if (TRANSACTION_BUNDLE) {
        encounterResource.addParticipant().setIndividual(new Reference(
                ExportHelper.buildFhirNpiSearchUrl(encounter.clinician)));
      } else {
        String practitionerFullUrl = findPractitioner(encounter.clinician, bundle);
        if (practitionerFullUrl != null) {
          encounterResource.addParticipant().setIndividual(new Reference(practitionerFullUrl));
        } else {
          BundleEntryComponent practitioner = practitioner(bundle, encounter.clinician);
          encounterResource.addParticipant()
                  .setIndividual(new Reference(practitioner.getFullUrl()));
        }
      }
      encounterResource.getParticipantFirstRep().getIndividual()
              .setDisplay(encounter.clinician.getFullname());
    }

    if (encounter.discharge != null) {
      EncounterHospitalizationComponent hospitalization = new EncounterHospitalizationComponent();
      Code dischargeDisposition = new Code(DISCHARGE_URI, encounter.discharge.code,
          encounter.discharge.display);
      hospitalization
          .setDischargeDisposition(mapCodeToCodeableConcept(dischargeDisposition, DISCHARGE_URI));
      encounterResource.setHospitalization(hospitalization);
    }

    if (USE_SHR_EXTENSIONS) {
      encounterResource.setMeta(
          new Meta().addProfile(SHR_EXT + "shr-encounter-EncounterPerformed"));
      // required fields for this profile are status & action-PerformedContext-extension

      Extension performedContext = new Extension();
      performedContext.setUrl(SHR_EXT + "shr-action-PerformedContext-extension");
      performedContext.addExtension(
          SHR_EXT + "shr-action-Status-extension",
          new CodeType("finished"));

      encounterResource.addExtension(performedContext);
    }

    return newEntry(person, bundle, encounterResource);
  }

  /**
   * Find the provider entry in this bundle, and return the associated "fullUrl" attribute.
   * @param provider A given provider.
   * @param bundle The current bundle being generated.
   * @return Provider.fullUrl if found, otherwise null.
   */
  private static String findProviderUrl(Provider provider, Bundle bundle) {
    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource().fhirType().equals("Organization")) {
        Organization org = (Organization) entry.getResource();
        if (org.getIdentifierFirstRep().getValue().equals(provider.getResourceID())) {
          return entry.getFullUrl();
        }
      }
    }
    return null;
  }

  /**
   * Find the Practitioner entry in this bundle, and return the associated "fullUrl"
   * attribute.
   * @param clinician A given clinician.
   * @param bundle The current bundle being generated.
   * @return Practitioner.fullUrl if found, otherwise null.
   */
  private static String findPractitioner(Clinician clinician, Bundle bundle) {
    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource().fhirType().equals("Practitioner")) {
        Practitioner doc = (Practitioner) entry.getResource();
        if (doc.getIdentifierFirstRep().getValue().equals("" + clinician.identifier)) {
          return entry.getFullUrl();
        }
      }
    }
    return null;
  }

  /**
   * Create an entry for the given Claim, which references a Medication.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry Entry for the person
   * @param bundle The Bundle to add to
   * @param encounterEntry The current Encounter
   * @param claim the Claim object
   * @param medicationEntry The Entry for the Medication object, previously created
   * @return the added Entry
   */
  private static BundleEntryComponent medicationClaim(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          Claim claim, BundleEntryComponent medicationEntry) {
    org.hl7.fhir.dstu3.model.Claim claimResource = new org.hl7.fhir.dstu3.model.Claim();
    org.hl7.fhir.dstu3.model.Encounter encounterResource =
        (org.hl7.fhir.dstu3.model.Encounter) encounterEntry.getResource();

    claimResource.setStatus(ClaimStatus.ACTIVE);
    claimResource.setUse(org.hl7.fhir.dstu3.model.Claim.Use.COMPLETE);

    // duration of encounter
    claimResource.setBillablePeriod(encounterResource.getPeriod());

    claimResource.setPatient(new Reference(personEntry.getFullUrl()));
    claimResource.setOrganization(encounterResource.getServiceProvider());

    // add item for encounter
    claimResource.addItem(new org.hl7.fhir.dstu3.model.Claim.ItemComponent(new PositiveIntType(1))
        .addEncounter(new Reference(encounterEntry.getFullUrl())));

    // add prescription.
    claimResource.setPrescription(new Reference(medicationEntry.getFullUrl()));

    Money moneyResource = new Money();
    moneyResource.setValue(claim.getTotalClaimCost());
    moneyResource.setCode("USD");
    moneyResource.setSystem("urn:iso:std:iso:4217");
    claimResource.setTotal(moneyResource);

    return newEntry(rand, bundle, claimResource);
  }

  /**
   * Create an entry for the given Claim, associated to an Encounter.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry Entry for the person
   * @param bundle The Bundle to add to
   * @param encounterEntry The current Encounter
   * @param claim the Claim object
   * @return the added Entry
   */
  private static BundleEntryComponent encounterClaim(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          Claim claim) {
    org.hl7.fhir.dstu3.model.Claim claimResource = new org.hl7.fhir.dstu3.model.Claim();
    org.hl7.fhir.dstu3.model.Encounter encounterResource =
        (org.hl7.fhir.dstu3.model.Encounter) encounterEntry.getResource();
    claimResource.setStatus(ClaimStatus.ACTIVE);
    claimResource.setUse(org.hl7.fhir.dstu3.model.Claim.Use.COMPLETE);

    // duration of encounter
    claimResource.setBillablePeriod(encounterResource.getPeriod());

    claimResource.setPatient(new Reference(personEntry.getFullUrl()));
    claimResource.setOrganization(encounterResource.getServiceProvider());

    // add item for encounter
    claimResource.addItem(new ItemComponent(new PositiveIntType(1))
        .addEncounter(new Reference(encounterEntry.getFullUrl())));

    int itemSequence = 2;
    int conditionSequence = 1;
    int procedureSequence = 1;
    int informationSequence = 1;

    for (HealthRecord.Entry item : claim.items) {
      if (Costs.hasCost(item)) {
        // update claimItems list
        ItemComponent claimItem = new ItemComponent(new PositiveIntType(itemSequence));
        Code primaryCode = item.codes.get(0);
        String system = ExportHelper.getSystemURI(primaryCode.system);
        CodeableConcept serviceProvided = new CodeableConcept()
            .addCoding(new Coding()
                .setCode(primaryCode.code)
                .setVersion("v1")
                .setSystem(system));
        claimItem.setService(serviceProvided);
        // calculate the cost of the procedure
        Money moneyResource = new Money();
        moneyResource.setCode("USD");
        moneyResource.setSystem("urn:iso:std:iso:4217");
        moneyResource.setValue(item.getCost());
        claimItem.setNet(moneyResource);

        if (item instanceof HealthRecord.Procedure) {
          Type procedureReference = new Reference(item.fullUrl);
          ProcedureComponent claimProcedure = new ProcedureComponent(
              new PositiveIntType(procedureSequence), procedureReference);
          claimResource.addProcedure(claimProcedure);
          claimItem.addProcedureLinkId(procedureSequence);

          procedureSequence++;
        } else {
          Reference informationReference = new Reference(item.fullUrl);
          SpecialConditionComponent informationComponent = new SpecialConditionComponent();
          informationComponent.setSequence(informationSequence);
          informationComponent.setValue(informationReference);
          CodeableConcept category = new CodeableConcept();
          category.getCodingFirstRep()
            .setSystem("http://hl7.org/fhir/claiminformationcategory")
            .setCode("info");
          informationComponent.setCategory(category);
          claimResource.addInformation(informationComponent);
          claimItem.addInformationLinkId(informationSequence);
          claimItem.setService(claimResource.getType());

          informationSequence++;
        }
        claimResource.addItem(claimItem);
      } else {
        // assume it's a Condition, we don't have a Condition class specifically
        // add diagnosisComponent to claim
        Reference diagnosisReference = new Reference(item.fullUrl);
        org.hl7.fhir.dstu3.model.Claim.DiagnosisComponent diagnosisComponent =
            new org.hl7.fhir.dstu3.model.Claim.DiagnosisComponent(
                new PositiveIntType(conditionSequence), diagnosisReference);
        claimResource.addDiagnosis(diagnosisComponent);

        // update claimItems with diagnosis
        ItemComponent diagnosisItem = new ItemComponent(new PositiveIntType(itemSequence));
        diagnosisItem.addDiagnosisLinkId(conditionSequence);
        claimResource.addItem(diagnosisItem);

        conditionSequence++;
      }
      itemSequence++;
    }

    Money moneyResource = new Money();
    moneyResource.setCode("USD");
    moneyResource.setSystem("urn:iso:std:iso:4217");
    moneyResource.setValue(claim.getTotalClaimCost());
    claimResource.setTotal(moneyResource);

    return newEntry(rand, bundle, claimResource);
  }

  /**
   * Create an extension in with a valueMoney in USD.
   * @param url The url of the extension.
   * @param value The value in USD.
   * @return the Extension
   */
  private static Extension createMoneyExtension(String url, double value) {
    Money money = new Money();
    money.setValue(value);
    money.setSystem("urn:iso:std:iso:4217");
    money.setCode("USD");

    Extension extension = new Extension();
    extension.setUrl(url);
    extension.setValue(money);

    return extension;
  }

  /**
   * Create an explanation of benefit resource for each claim, detailing insurance
   * information.
   *
   * @param personEntry Entry for the person
   * @param bundle The Bundle to add to
   * @param encounterEntry The current Encounter
   * @param claimEntry the Claim object
   * @param person the person the health record belongs to
   * @param encounter the current Encounter as an object
   * @return the added entry
   */
  private static BundleEntryComponent explanationOfBenefit(BundleEntryComponent personEntry,
                                           Bundle bundle, BundleEntryComponent encounterEntry,
                                           Person person, BundleEntryComponent claimEntry,
                                           Encounter encounter) {
    boolean inpatient = false;
    boolean outpatient = false;
    EncounterType type = EncounterType.fromString(encounter.type);
    if (type == EncounterType.INPATIENT) {
      inpatient = true;
      // Provider enum doesn't include outpatient, but it can still be
      // an encounter type.
    } else if (type == EncounterType.AMBULATORY || type == EncounterType.WELLNESS) {
      outpatient = true;
    }
    ExplanationOfBenefit eob = new ExplanationOfBenefit();
    org.hl7.fhir.dstu3.model.Encounter encounterResource =
        (org.hl7.fhir.dstu3.model.Encounter) encounterEntry.getResource();

    // First add the extensions
    // will have to deal with different claim types (e.g. inpatient vs outpatient)
    if (inpatient) {
      //https://www.cms.gov/Medicare/Medicare-Fee-for-Service-Payment/AcuteInpatientPPS/Indirect-Medical-Education-IME
      // Extra cost for educational hospitals
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-ime-op-clm-val-amt-extension",
          400));

      // DSH payment-- Massachusetts does not make DSH payments at all, so set to 0 for now
      // https://www.cms.gov/Medicare/Medicare-Fee-for-Service-Payment/AcuteInpatientPPS/dsh
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-dsh-op-clm-val-amt-extension",
          0));

      // The pass through per diem rate
      // not really defined by CMS
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pass-thru-per-diem-amt-extension",
          0));

      // Professional charge
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-profnl-cmpnt-chrg-amt-extension",
          0));

      // total claim PPS charge
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-tot-pps-cptl-amt-extension",
          0));

      // Deductible Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-bene-ip-ddctbl-amt-extension",
          0));

      // Coinsurance Liability
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-bene-pta-coinsrnc-lblty-amt-extension",
          0));

      // Non-covered Charge Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-ip-ncvrd-chrg-amt-extension",
          0));

      // Total Deductible/Coinsurance Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-ip-tot-ddctn-amt-extension",
          0));

      // PPS Capital DSH Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pps-cptl-dsprprtnt-shr-amt-extension",
          0));

      // PPS Capital Exception Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pps-cptl-excptn-amt-extension",
          0));

      // PPS FSP
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pps-cptl-fsp-amt-extension",
          0));

      // PPS IME
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pps-cptl-ime-amt-extension",
          400));

      // PPS Capital Outlier Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pps-cptl-outlier-amt-extension",
          0));

      // Old capital hold harmless amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-pps-old-cptl-hld-hrmls-amt-extension",
          0));

      // NCH DRG Outlier Approved Payment Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-drg-outlier-aprvd-pmt-amt-extension",
          0));

      // NCH Beneficiary Blood Deductible Liability Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-nch-bene-blood-ddctbl-lblty-am-extension",
          0));

      // Non-payment reason
      eob.addExtension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-mdcr-non-pmt-rsn-cd-extension")
          .setValue(new Coding()
              .setSystem("https://bluebutton.cms.gov/assets/ig/CodeSystem-clm-mdcr-non-pmt-rsn-cd")
              .setDisplay("All other reasons for non-payment")
              .setCode("N"));

      // Prepayment
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-prpayamt-extension",
          0));

      // FI or MAC number
      eob.addExtension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-fi-num-extension")
          .setValue(new Identifier()
              .setValue("002000")
              // No system page exists yet
              .setSystem("https://bluebutton.cms.gov/assets/ig/CodeSystem-fi-num"));
    } else if (outpatient) {
      // Professional component charge amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-nch-profnl-cmpnt-chrg-amt-extension",
          0));

      // Deductible amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-nch-bene-ptb-ddctbl-amt-extension",
          0));

      // Coinsurance amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-nch-bene-ptb-coinsrnc-amt-extension",
          0));

      // Provider Payment
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-clm-op-prvdr-pmt-amt-extension",
          0));

      // Beneficiary payment
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-clm-op-bene-pmt-amt-extension",
          0));

      // Beneficiary Blood Deductible Liability Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-nch-bene-blood-ddctbl-lblty-am-extension",
          0));

      // Claim Medicare Non Payment Reason Code
      eob.addExtension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-clm-mdcr-non-pmt-rsn-cd-extension")
          .setValue(new Coding()
              .setDisplay("All other reasons for non-payment")
              .setSystem("https://bluebutton.cms.gov/assets/ig/CodeSystem-clm-mdcr-non-pmt-rsn-cd")
              .setCode("N"));

      // NCH Primary Payer Claim Paid Amount
      eob.addExtension(createMoneyExtension(
          "https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-prpayamt-extension",
          0));

      // FI or MAC number
      eob.addExtension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-fi-num-extension")
          .setValue(new Identifier()
              .setValue("002000")
              // No system page exists yet
              .setSystem("https://bluebutton.cms.gov/assets/ig/CodeSystem-fi-num"));
    }

    // according to CMS guidelines claims have 12 months to be
    // billed, so we set the billable period to 1 year after
    // services have ended (the encounter ends).
    Calendar cal = Calendar.getInstance();
    cal.setTime(encounterResource.getPeriod().getEnd());
    cal.add(Calendar.YEAR,1);

    Period billablePeriod = new Period()
        .setStart(encounterResource
            .getPeriod()
            .getEnd())
        .setEnd(cal.getTime());
    if (inpatient) {
      billablePeriod.addExtension(new Extension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-claim-query-cd-extension")
          .setValue(new Coding()
              .setCode("3")
              .setSystem("https://bluebutton.cms.gov/assets/ig/ValueSet-claim-query-cd")
              .setDisplay("Final Bill")));
    } else if (outpatient) {
      billablePeriod.addExtension(new Extension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-claim-query-cd-extension")
          .setValue(new Coding()
              .setCode("3")
              .setSystem("https://bluebutton.cms.gov/assets/ig/ValueSet-claim-query-cd")
              .setDisplay("Final Bill")));
    }

    eob.setBillablePeriod(billablePeriod);

    // cost is hardcoded to be USD in claim so this should be fine as well
    Money totalCost = new Money();
    totalCost.setSystem("urn:iso:std:iso:4217");
    totalCost.setCode("USD");
    totalCost.setValue(encounter.claim.getTotalClaimCost());
    eob.setTotalCost(totalCost);

    // Set References
    eob.setPatient(new Reference(personEntry.getFullUrl()));
    if (encounter.provider != null) {
      // This is what should happen if BlueButton 2.0 wasn't needlessly restrictive
      // String providerUrl = findProviderUrl(encounter.provider, bundle);
      // eob.setOrganization(new Reference().setReference(providerUrl));
      // Instead, we'll create the BlueButton 2.0 reference via identifier...
      Identifier identifier = new Identifier();
      identifier.setValue(encounter.provider.getResourceID());
      eob.setOrganization(new Reference().setIdentifier(identifier));
    }

    // Get the insurance info at the time that the encounter happened.
    Payer payer = encounter.claim.payer;

    Coverage coverage = new Coverage();
    coverage.setId("coverage");
    coverage.setType(new CodeableConcept().setText(payer.getName()));
    eob.addContained(coverage);
    ExplanationOfBenefit.InsuranceComponent insuranceComponent =
        new ExplanationOfBenefit.InsuranceComponent();
    insuranceComponent.setCoverage(new Reference("#coverage"));
    eob.setInsurance(insuranceComponent);

    org.hl7.fhir.dstu3.model.Claim claim =
        (org.hl7.fhir.dstu3.model.Claim) claimEntry.getResource();
    eob.addIdentifier()
        .setSystem("https://bluebutton.cms.gov/resources/variables/clm_id")
        .setValue(claim.getId());
    // Hardcoded group id
    eob.addIdentifier()
        .setSystem("https://bluebutton.cms.gov/resources/identifier/claim-group")
        .setValue("99999999999");

    eob.setStatus(org.hl7.fhir.dstu3.model.ExplanationOfBenefit.ExplanationOfBenefitStatus.ACTIVE);
    if (!inpatient && !outpatient) {
      eob.setClaim(new Reference()
          .setReference(claimEntry.getFullUrl()));
      eob.setReferral(new Reference("#1"));
      eob.setCreated(encounterResource.getPeriod().getEnd());
    }
    eob.setType(claim.getType());

    List<ExplanationOfBenefit.DiagnosisComponent> eobDiag = new ArrayList<>();
    for (org.hl7.fhir.dstu3.model.Claim.DiagnosisComponent claimDiagnosis : claim.getDiagnosis()) {
      ExplanationOfBenefit.DiagnosisComponent diagnosisComponent =
          new ExplanationOfBenefit.DiagnosisComponent();
      diagnosisComponent.setDiagnosis(claimDiagnosis.getDiagnosis());
      diagnosisComponent.getType().add(new CodeableConcept()
          .addCoding(new Coding()
              .setCode("principal")
              .setSystem("https://bluebutton.cms.gov/resources/codesystem/diagnosis-type")));
      diagnosisComponent.setSequence(claimDiagnosis.getSequence());
      diagnosisComponent.setPackageCode(claimDiagnosis.getPackageCode());
      diagnosisComponent.addExtension()
          .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-clm-poa-ind-sw1-extension")
          .setValue(new Coding()
              .setCode("Y")
              .setSystem("https://bluebutton.cms.gov/assets/ig/CodeSystem-clm-poa-ind-sw1")
              .setDisplay("Diagnosis present at time of admission"));
      eobDiag.add(diagnosisComponent);
    }
    eob.setDiagnosis(eobDiag);

    List<ExplanationOfBenefit.ProcedureComponent> eobProc = new ArrayList<>();
    for (ProcedureComponent proc : claim.getProcedure()) {
      ExplanationOfBenefit.ProcedureComponent p = new ExplanationOfBenefit.ProcedureComponent();
      p.setDate(proc.getDate());
      p.setSequence(proc.getSequence());
      p.setProcedure(proc.getProcedure());
    }
    eob.setProcedure(eobProc);

    List<ExplanationOfBenefit.ItemComponent> eobItem = new ArrayList<>();
    double totalPayment = 0;
    // Get all the items info from the claim

    for (ItemComponent item : claim.getItem()) {

      ExplanationOfBenefit.ItemComponent itemComponent = new ExplanationOfBenefit.ItemComponent();

      itemComponent.setSequence(item.getSequence());
      itemComponent.setQuantity(item.getQuantity());
      itemComponent.setUnitPrice(item.getUnitPrice());
      itemComponent.setCareTeamLinkId(item.getCareTeamLinkId());

      if (item.hasService()) {
        itemComponent
            .setService(item
                .getService());
      }
      if (!inpatient && !outpatient) {
        itemComponent.setDiagnosisLinkId(item.getDiagnosisLinkId());
        itemComponent.setInformationLinkId(item.getInformationLinkId());
        itemComponent.setNet(item.getNet());
        itemComponent.setEncounter(item.getEncounter());
        itemComponent.setServiced(encounterResource.getPeriod());
        itemComponent.setCategory(new CodeableConcept().addCoding(new Coding()
            .setSystem("https://bluebutton.cms.gov/resources/variables/line_cms_type_srvc_cd")
            .setCode("1")
            .setDisplay("Medical care")));
      }
      if (inpatient) {
        itemComponent.addExtension(new Extension()
            .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-inpatient-rev-cntr-ndc-qty-extension")
            .setValue(new Quantity().setValue(0)));
      } else if (outpatient) {
        itemComponent.addExtension(new Extension()
            .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-rev-cntr-ndc-qty-extension")
            .setValue(new Quantity().setValue(0)));
        if (itemComponent.hasService()) {
          itemComponent.getService().addExtension(new Extension()
              .setUrl("https://bluebutton.cms.gov/assets/ig/StructureDefinition-bluebutton-outpatient-rev-cntr-ide-ndc-upc-num-extension")
              .setValue(new Coding()
                  .setSystem("https://www.accessdata.fda.gov/scripts/cder/ndc")
                  .setDisplay("Dummy")
                  .setCode("0624")));
        }
      }

      // Location of service, can use switch statement based on
      // encounter type
      String code;
      String display;
      CodeableConcept location = new CodeableConcept();
      EncounterType encounterType = EncounterType.fromString(encounter.type);
      switch (encounterType) {
        case AMBULATORY:
          code = "21";
          display = "Inpatient Hospital";
          break;
        case EMERGENCY:
          code = "23";
          display = "Emergency Room";
          break;
        case INPATIENT:
          code = "21";
          display = "Inpatient Hospital";
          break;
        case URGENTCARE:
          code = "20";
          display = "Urgent Care Facility";
          break;
        case WELLNESS:
          code = "22";
          display = "Outpatient Hospital";
          break;
        default:
          code = "21";
          display = "Inpatient Hospital";
      }
      location.addCoding()
          .setCode(code)
          //.setSystem("http://hl7.org/fhir/ValueSet/service-place") > if we wanted hl7
          .setSystem("https://bluebutton.cms.gov/resources/variables/line_place_of_srvc_cd")
          .setDisplay(display);
      itemComponent.setLocation(location);

      // Adjudication
      if (item.hasNet()) {

        // Assume that the patient has already paid deductible and
        // has 20/80 coinsurance
        ExplanationOfBenefit.AdjudicationComponent coinsuranceAmount =
            new ExplanationOfBenefit.AdjudicationComponent();
        coinsuranceAmount.getCategory()
            .getCoding()
            .add(new Coding()
                .setCode("https://bluebutton.cms.gov/resources/variables/line_coinsrnc_amt")
                .setSystem("https://bluebutton.cms.gov/resources/codesystem/adjudication")
                .setDisplay("Line Beneficiary Coinsurance Amount"));
        coinsuranceAmount.getAmount()
            .setValue(0.2 * item.getNet().getValue().doubleValue()) //20% coinsurance
            .setSystem("urn:iso:std:iso:4217") //USD
            .setCode("USD");

        ExplanationOfBenefit.AdjudicationComponent lineProviderAmount =
            new ExplanationOfBenefit.AdjudicationComponent();
        lineProviderAmount.getCategory()
            .getCoding()
            .add(new Coding()
                .setCode("https://bluebutton.cms.gov/resources/variables/line_prvdr_pmt_amt")
                .setSystem("https://bluebutton.cms.gov/resources/codesystem/adjudication")
                .setDisplay("Line Provider Payment Amount"));
        lineProviderAmount.getAmount()
            .setValue(0.8 * item.getNet().getValue().doubleValue())
            .setSystem("urn:iso:std:iso:4217")
            .setCode("USD");

        // assume the allowed and submitted amounts are the same for now
        ExplanationOfBenefit.AdjudicationComponent submittedAmount =
            new ExplanationOfBenefit.AdjudicationComponent();
        submittedAmount.getCategory()
            .getCoding()
            .add(new Coding()
                .setCode("https://bluebutton.cms.gov/resources/variables/line_sbmtd_chrg_amt")
                .setSystem("https://bluebutton.cms.gov/resources/codesystem/adjudication")
                .setDisplay("Line Submitted Charge Amount"));
        submittedAmount.getAmount()
            .setValue(item.getNet().getValue())
            .setSystem("urn:iso:std:iso:4217")
            .setCode("USD");

        ExplanationOfBenefit.AdjudicationComponent allowedAmount =
            new ExplanationOfBenefit.AdjudicationComponent();
        allowedAmount.getCategory()
            .getCoding()
            .add(new Coding()
                .setCode("https://bluebutton.cms.gov/resources/variables/line_alowd_chrg_amt")
                .setSystem("https://bluebutton.cms.gov/resources/codesystem/adjudication")
                .setDisplay("Line Allowed Charge Amount"));
        allowedAmount.getAmount()
            .setValue(item.getNet().getValue())
            .setSystem("urn:iso:std:iso:4217")
            .setCode("USD");

        ExplanationOfBenefit.AdjudicationComponent indicatorCode =
            new ExplanationOfBenefit.AdjudicationComponent();
        indicatorCode.getCategory()
            .getCoding()
            .add(new Coding()
                .setCode("https://bluebutton.cms.gov/resources/variables/line_prcsg_ind_cd")
                .setSystem("https://bluebutton.cms.gov/resources/codesystem/adjudication")
                .setDisplay("Line Processing Indicator Code"));

        if (!inpatient && !outpatient) {
          indicatorCode.getReason()
              .addCoding()
              .setCode("A")
              .setSystem("https://bluebutton.cms.gov/resources/variables/line_prcsg_ind_cd");
          indicatorCode
              .getReason()
              .getCodingFirstRep()
              .setDisplay("Allowed");
        }

        // assume deductible is 0
        ExplanationOfBenefit.AdjudicationComponent deductibleAmount =
            new ExplanationOfBenefit.AdjudicationComponent();
        deductibleAmount.getCategory()
            .getCoding()
            .add(new Coding()
                .setCode("https://bluebutton.cms.gov/resources/variables/line_bene_ptb_ddctbl_amt")
                .setSystem("https://bluebutton.cms.gov/resources/codesystem/adjudication")
                .setDisplay("Line Beneficiary Part B Deductible Amount"));
        deductibleAmount.getAmount()
            .setValue(0)
            .setSystem("urn:iso:std:iso:4217")
            .setCode("USD");

        List<ExplanationOfBenefit.AdjudicationComponent> adjudicationComponents = new ArrayList<>();
        adjudicationComponents.add(coinsuranceAmount);
        adjudicationComponents.add(lineProviderAmount);
        adjudicationComponents.add(submittedAmount);
        adjudicationComponents.add(allowedAmount);
        adjudicationComponents.add(deductibleAmount);
        adjudicationComponents.add(indicatorCode);

        itemComponent.setAdjudication(adjudicationComponents);
        // the total payment is what the insurance ends up paying
        totalPayment += 0.8 * item.getNet().getValue().doubleValue();
      }
      eobItem.add(itemComponent);
    }

    eob.setItem(eobItem);

    // This will throw a validation error no matter what.  The
    // payment section is required, and it requires a value.
    // The validator will complain that if there is a value, the payment
    // needs a code, but it will also complain if there is a code.
    // There is no way to resolve this error.
    Money payment = new Money();
    payment.setValue(totalPayment)
        .setSystem("urn:iso:std:iso:4217")
        .setCode("USD");
    eob.setPayment(new ExplanationOfBenefit.PaymentComponent()
        .setAmount(payment));

    // Hardcoded
    List<Reference> recipientList = new ArrayList<>();
    recipientList.add(new Reference()
        .setIdentifier(new Identifier()
        .setSystem("http://hl7.org/fhir/sid/us-npi")
        .setValue("99999999")));
    eob.addContained(new ReferralRequest()
        .setStatus(ReferralRequest.ReferralRequestStatus.COMPLETED)
        .setIntent(ReferralRequest.ReferralCategory.ORDER)
        .setSubject(new Reference(personEntry.getFullUrl()))
        .setRequester(new ReferralRequest.ReferralRequestRequesterComponent()
            .setAgent(new Reference()
                .setIdentifier(new Identifier()
                    .setSystem("http://hl7.org/fhir/sid/us-npi")
                    .setValue("99999999"))))
        .setRecipient(recipientList)
        .setId("1"));

    if (encounter.clinician != null) {
      // This is what should happen if BlueButton 2.0 wasn't needlessly restrictive
      // String practitionerFullUrl = findPractitioner(encounter.clinician, bundle);
      // eob.setProvider(new Reference().setReference(practitionerFullUrl));
      // Instead, we'll create the BlueButton 2.0 reference via identifier...
      Identifier identifier = new Identifier();
      identifier.setValue(encounter.clinician.getResourceID());
      eob.setProvider(new Reference().setIdentifier(identifier));
    } else {
      Identifier identifier = new Identifier();
      identifier.setValue("Unknown");
      eob.setProvider(new Reference().setIdentifier(identifier));
    }

    eob.addCareTeam(new ExplanationOfBenefit.CareTeamComponent()
        .setSequence(1)
        .setProvider(new Reference()
            // .setReference(findProviderUrl(provider, bundle))
            .setIdentifier(new Identifier()
                .setSystem("http://hl7.org/fhir/sid/us-npi")
                // providers don't have an npi
                .setValue("99999999")))
        .setRole(new CodeableConcept().addCoding(new Coding()
            .setCode("primary")
            .setSystem("http://hl7.org/fhir/claimcareteamrole")
            .setDisplay("Primary Care Practitioner"))));

    eob.setType(new CodeableConcept()
        .addCoding(new Coding()
            .setSystem("https://bluebutton.cms.gov/resources/variables/nch_clm_type_cd")
            // The code should be chosen from the
            // claim type, which is different from
            // the encounter type apparently.
            .setCode("71")
            .setDisplay("Local carrier non-durable medical equipment, prosthetics, orthotics, "
                + "and supplies (DMEPOS) claim"))
        .addCoding(new Coding()
            .setSystem("https://bluebutton.cms.gov/resources/codesystem/eob-type")
            // the code is chosen directly as
            // a result of the nch_clm_type_cd.
            .setCode("CARRIER")
            .setDisplay("EOB Type"))
        .addCoding(new Coding()
            .setSystem("http://hl7.org/fhir/ex-claimtype")
            // the ex-claimtype is also directly dependent on
            // the eob-type, making the clm_type the only real
            // category that needs to be dynamically chosen
            .setCode("professional")
            .setDisplay("Claim Type"))
        .addCoding(new Coding()
            .setSystem("https://bluebutton.cms.gov/resources/variables/nch_near_line_rec_ident_cd")
            // also dependent on clm-type
            .setCode("O")
            .setDisplay("Part B physician/supplier claim record (processed by local "
                      + "carriers; can include DMEPOS services)")));

    return newEntry(person, bundle,eob);
  }

  /**
   * Map the Condition into a FHIR Condition resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Entry for the Person
   * @param bundle The Bundle to add to
   * @param encounterEntry The current Encounter entry
   * @param condition The Condition
   * @return The added Entry
   */
  private static BundleEntryComponent condition(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          HealthRecord.Entry condition) {
    Condition conditionResource = new Condition();

    conditionResource.setSubject(new Reference(personEntry.getFullUrl()));
    conditionResource.setContext(new Reference(encounterEntry.getFullUrl()));

    Code code = condition.codes.get(0);
    conditionResource.setCode(mapCodeToCodeableConcept(code, SNOMED_URI));

    conditionResource.setVerificationStatus(ConditionVerificationStatus.CONFIRMED);
    conditionResource.setClinicalStatus(ConditionClinicalStatus.ACTIVE);

    conditionResource.setOnset(convertFhirDateTime(condition.start, true));
    conditionResource.setAssertedDate(new Date(condition.start));

    if (condition.stop != 0) {
      conditionResource.setAbatement(convertFhirDateTime(condition.stop, true));
      conditionResource.setClinicalStatus(ConditionClinicalStatus.RESOLVED);
    }

    if (USE_SHR_EXTENSIONS) {
      // TODO: use different categories. would need to add a "category" to GMF Condition state
      // also potentially use Injury profile here,
      // once different codes map to different categories

      conditionResource.addCategory(new CodeableConcept().addCoding(new Coding(
          "http://standardhealthrecord.org/shr/condition/vs/ConditionCategoryVS", "disease",
          "Disease")));
      conditionResource.setMeta(new Meta().addProfile(SHR_EXT + "shr-condition-Condition"));
      // required fields for this profile are clinicalStatus, assertedDate, category
    }

    BundleEntryComponent conditionEntry = newEntry(rand, bundle, conditionResource);

    condition.fullUrl = conditionEntry.getFullUrl();

    return conditionEntry;
  }

  /**
   * Map the Condition into a FHIR AllergyIntolerance resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Entry for the Person
   * @param bundle The Bundle to add to
   * @param encounterEntry The current Encounter entry
   * @param allergy The Allergy Entry
   * @return The added Entry
   */
  private static BundleEntryComponent allergy(RandomNumberGenerator rand,
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          HealthRecord.Entry allergy) {

    AllergyIntolerance allergyResource = new AllergyIntolerance();

    allergyResource.setAssertedDate(new Date(allergy.start));

    if (allergy.stop == 0) {
      allergyResource.setClinicalStatus(AllergyIntoleranceClinicalStatus.ACTIVE);
    } else {
      allergyResource.setClinicalStatus(AllergyIntoleranceClinicalStatus.INACTIVE);
    }

    allergyResource.setType(AllergyIntoleranceType.ALLERGY);
    AllergyIntoleranceCategory category = AllergyIntoleranceCategory.FOOD;
    allergyResource.addCategory(category); // TODO: allergy categories in GMF
    allergyResource.setCriticality(AllergyIntoleranceCriticality.LOW);
    allergyResource.setVerificationStatus(AllergyIntoleranceVerificationStatus.CONFIRMED);
    allergyResource.setPatient(new Reference(personEntry.getFullUrl()));
    Code code = allergy.codes.get(0);
    allergyResource.setCode(mapCodeToCodeableConcept(code, SNOMED_URI));

    if (USE_SHR_EXTENSIONS) {
      Meta meta = new Meta();
      meta.addProfile(SHR_EXT + "shr-allergy-AllergyIntolerance");
      // required fields for AllergyIntolerance profile are:
      // verificationStatus, code, patient, assertedDate
      allergyResource.setMeta(meta);
    }
    BundleEntryComponent allergyEntry = newEntry(rand, bundle, allergyResource);
    allergy.fullUrl = allergyEntry.getFullUrl();
    return allergyEntry;
  }


  /**
   * Map the given Observation into a FHIR Observation resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Person Entry
   * @param bundle The Bundle to add to
   * @param encounterEntry The current Encounter entry
   * @param observation The Observation
   * @return The added Entry
   */
  private static BundleEntryComponent observation(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          Observation observation) {
    org.hl7.fhir.dstu3.model.Observation observationResource =
        new org.hl7.fhir.dstu3.model.Observation();

    observationResource.setSubject(new Reference(personEntry.getFullUrl()));
    observationResource.setContext(new Reference(encounterEntry.getFullUrl()));

    observationResource.setStatus(ObservationStatus.FINAL);

    Code code = observation.codes.get(0);
    observationResource.setCode(mapCodeToCodeableConcept(code, LOINC_URI));

    observationResource.addCategory().addCoding().setCode(observation.category)
        .setSystem("http://hl7.org/fhir/observation-category").setDisplay(observation.category);

    if (observation.value != null) {
      Type value = mapValueToFHIRType(observation.value, observation.unit);
      observationResource.setValue(value);
    } else if (observation.observations != null && !observation.observations.isEmpty()) {
      // multi-observation (ex blood pressure)
      for (Observation subObs : observation.observations) {
        ObservationComponentComponent comp = new ObservationComponentComponent();
        comp.setCode(mapCodeToCodeableConcept(subObs.codes.get(0), LOINC_URI));
        Type value = mapValueToFHIRType(subObs.value, subObs.unit);
        comp.setValue(value);
        observationResource.addComponent(comp);
      }
    }

    observationResource.setEffective(convertFhirDateTime(observation.start, true));
    observationResource.setIssued(new Date(observation.start));

    if (USE_SHR_EXTENSIONS) {
      Meta meta = new Meta();
      meta.addProfile(SHR_EXT + "shr-finding-Observation"); // all Observations are Observations
      if ("vital-signs".equals(observation.category)) {
        meta.addProfile(SHR_EXT + "shr-vital-VitalSign");
      }
      // add the specific profile based on code
      String codeMappingUri = SHR_MAPPING.get(LOINC_URI, code.code);
      if (codeMappingUri != null) {
        meta.addProfile(codeMappingUri);
      }
      observationResource.setMeta(meta);
    }

    BundleEntryComponent entry = newEntry(rand, bundle, observationResource);
    observation.fullUrl = entry.getFullUrl();
    return entry;
  }

  static Type mapValueToFHIRType(Object value, String unit) {
    if (value == null) {
      return null;

    } else if (value instanceof Condition) {
      Code conditionCode = ((HealthRecord.Entry) value).codes.get(0);
      return mapCodeToCodeableConcept(conditionCode, SNOMED_URI);

    } else if (value instanceof Code) {
      return mapCodeToCodeableConcept((Code) value, SNOMED_URI);

    } else if (value instanceof String) {
      return new StringType((String) value);

    } else if (value instanceof Number) {
      double dblVal = ((Number) value).doubleValue();
      PlainBigDecimal bigVal = new PlainBigDecimal(dblVal);
      return new Quantity().setValue(bigVal)
          .setCode(unit).setSystem(UNITSOFMEASURE_URI)
          .setUnit(unit);
    } else if (value instanceof Components.SampledData) {
      return mapValueToSampledData((Components.SampledData) value, unit);
    } else {
      throw new IllegalArgumentException("unexpected observation value class: "
          + value.getClass().toString() + "; " + value);
    }
  }
  
  /**
   * Maps a Synthea internal SampledData object to the FHIR standard SampledData
   * representation.
   * 
   * @param value Synthea internal SampledData instance
   * @param unit Observation unit value
   * @return
   */
  static org.hl7.fhir.dstu3.model.SampledData mapValueToSampledData(
      Components.SampledData value, String unit) {
    
    org.hl7.fhir.dstu3.model.SampledData recordData = new org.hl7.fhir.dstu3.model.SampledData();
    
    SimpleQuantity origin = new SimpleQuantity();
    origin.setValue(new BigDecimal(value.originValue))
      .setCode(unit).setSystem(UNITSOFMEASURE_URI)
      .setUnit(unit);
    
    recordData.setOrigin(origin);
    
    // Use the period from the first series. They should all be the same.
    // FHIR output is milliseconds so we need to convert from TimeSeriesData seconds.
    recordData.setPeriod(value.series.get(0).getPeriod() * 1000);
    
    // Set optional fields if they were provided
    if (value.factor != null) {
      recordData.setFactor(value.factor);
    }
    if (value.lowerLimit != null) {
      recordData.setLowerLimit(value.lowerLimit);
    }
    if (value.upperLimit != null) {
      recordData.setUpperLimit(value.upperLimit);
    }
    
    recordData.setDimensions(value.series.size());
    
    recordData.setData(ExportHelper.sampledDataToValueString(value));
    
    return recordData;
  }

  /**
   * Map the given Procedure into a FHIR Procedure resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Person entry
   * @param bundle Bundle to add to
   * @param encounterEntry The current Encounter entry
   * @param procedure  The Procedure
   * @return The added Entry
   */
  private static BundleEntryComponent procedure(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          Procedure procedure) {
    org.hl7.fhir.dstu3.model.Procedure procedureResource = new org.hl7.fhir.dstu3.model.Procedure();

    procedureResource.setStatus(ProcedureStatus.COMPLETED);
    procedureResource.setSubject(new Reference(personEntry.getFullUrl()));
    procedureResource.setContext(new Reference(encounterEntry.getFullUrl()));

    Code code = procedure.codes.get(0);
    CodeableConcept procCode = mapCodeToCodeableConcept(code, SNOMED_URI);
    procedureResource.setCode(procCode);

    if (procedure.stop != 0L) {
      Date startDate = new Date(procedure.start);
      Date endDate = new Date(procedure.stop);
      procedureResource.setPerformed(new Period().setStart(startDate).setEnd(endDate));
    } else {
      procedureResource.setPerformed(convertFhirDateTime(procedure.start, true));
    }

    if (!procedure.reasons.isEmpty()) {
      Code reason = procedure.reasons.get(0); // Only one element in list
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.getResource().fhirType().equals("Condition")) {
          Condition condition = (Condition) entry.getResource();
          Coding coding = condition.getCode().getCoding().get(0); // Only one element in list
          if (reason.code.equals(coding.getCode())) {
            procedureResource.addReasonReference().setReference(entry.getFullUrl())
                .setDisplay(reason.display);
          }
        }
      }
    }

    if (USE_SHR_EXTENSIONS) {
      procedureResource.setMeta(
          new Meta().addProfile(SHR_EXT + "shr-procedure-ProcedurePerformed"));
      // required fields for this profile are action-PerformedContext-extension,
      // status, code, subject, performed[x]

      Extension performedContext = new Extension();
      performedContext.setUrl(SHR_EXT + "shr-action-PerformedContext-extension");
      performedContext.addExtension(
          SHR_EXT + "shr-action-Status-extension",
          new CodeType("completed"));

      procedureResource.addExtension(performedContext);
    }

    BundleEntryComponent procedureEntry = newEntry(rand, bundle, procedureResource);
    procedure.fullUrl = procedureEntry.getFullUrl();

    return procedureEntry;
  }

  private static BundleEntryComponent immunization(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          HealthRecord.Entry immunization) {
    Immunization immResource = new Immunization();
    immResource.setStatus(ImmunizationStatus.COMPLETED);
    immResource.setDate(new Date(immunization.start));
    immResource.setVaccineCode(mapCodeToCodeableConcept(immunization.codes.get(0), CVX_URI));
    immResource.setNotGiven(false);
    immResource.setPrimarySource(true);
    immResource.setPatient(new Reference(personEntry.getFullUrl()));
    immResource.setEncounter(new Reference(encounterEntry.getFullUrl()));

    if (USE_SHR_EXTENSIONS) {
      immResource.setMeta(new Meta().addProfile(SHR_EXT + "shr-immunization-ImmunizationGiven"));
      // profile requires action-PerformedContext-extension, status, notGiven, vaccineCode, patient,
      // date, primarySource

      Extension performedContext = new Extension();
      performedContext.setUrl(SHR_EXT + "shr-action-PerformedContext-extension");
      performedContext.addExtension(
          SHR_EXT + "shr-action-Status-extension",
          new CodeType("completed"));

      immResource.addExtension(performedContext);
    }

    BundleEntryComponent immunizationEntry = newEntry(rand, bundle, immResource);
    immunization.fullUrl = immunizationEntry.getFullUrl();

    return immunizationEntry;
  }

  /**
   * Map the given Medication to a FHIR MedicationRequest resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Entry for the Person
   * @param bundle Bundle to add the Medication to
   * @param encounterEntry Current Encounter entry
   * @param medication The Medication
   * @return The added Entry
   */
  private static BundleEntryComponent medication(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          Medication medication) {
    MedicationRequest medicationResource = new MedicationRequest();

    medicationResource.setSubject(new Reference(personEntry.getFullUrl()));
    medicationResource.setContext(new Reference(encounterEntry.getFullUrl()));

    Code code = medication.codes.get(0);
    String system = code.system.equals("SNOMED-CT")
        ? SNOMED_URI
        : RXNORM_URI;
    medicationResource.setMedication(mapCodeToCodeableConcept(code, system));

    medicationResource.setAuthoredOn(new Date(medication.start));
    medicationResource.setIntent(MedicationRequestIntent.ORDER);
    org.hl7.fhir.dstu3.model.Encounter encounter =
        (org.hl7.fhir.dstu3.model.Encounter) encounterEntry.getResource();
    MedicationRequestRequesterComponent requester = new MedicationRequestRequesterComponent();
    requester.setAgent(encounter.getParticipantFirstRep().getIndividual());
    requester.setOnBehalfOf(encounter.getServiceProvider());
    medicationResource.setRequester(requester);

    if (medication.stop != 0L) {
      medicationResource.setStatus(MedicationRequestStatus.STOPPED);
    } else {
      medicationResource.setStatus(MedicationRequestStatus.ACTIVE);
    }

    if (!medication.reasons.isEmpty()) {
      // Only one element in list
      Code reason = medication.reasons.get(0);
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.getResource().fhirType().equals("Condition")) {
          Condition condition = (Condition) entry.getResource();
          // Only one element in list
          Coding coding = condition.getCode().getCoding().get(0);
          if (reason.code.equals(coding.getCode())) {
            medicationResource.addReasonReference()
                .setReference(entry.getFullUrl());
          }
        }
      }
    }

    if (medication.prescriptionDetails != null) {
      JsonObject rxInfo = medication.prescriptionDetails;
      Dosage dosage = new Dosage();

      dosage.setSequence(1);
      // as_needed is true if present
      dosage.setAsNeeded(new BooleanType(rxInfo.has("as_needed")));

      // as_needed is true if present
      if ((rxInfo.has("dosage")) && (!rxInfo.has("as_needed"))) {
        Timing timing = new Timing();
        TimingRepeatComponent timingRepeatComponent = new TimingRepeatComponent();
        timingRepeatComponent.setFrequency(
            rxInfo.get("dosage").getAsJsonObject().get("frequency").getAsInt());
        timingRepeatComponent.setPeriod(
            rxInfo.get("dosage").getAsJsonObject().get("period").getAsDouble());
        timingRepeatComponent.setPeriodUnit(
            convertUcumCode(rxInfo.get("dosage").getAsJsonObject().get("unit").getAsString()));
        timing.setRepeat(timingRepeatComponent);
        dosage.setTiming(timing);

        Quantity dose = new SimpleQuantity().setValue(
            rxInfo.get("dosage").getAsJsonObject().get("amount").getAsDouble());
        dosage.setDose(dose);

        if (rxInfo.has("instructions")) {
          for (JsonElement instructionElement : rxInfo.get("instructions").getAsJsonArray()) {
            JsonObject instruction = instructionElement.getAsJsonObject();
            Code instructionCode = new Code(
                SNOMED_URI,
                instruction.get("code").getAsString(),
                instruction.get("display").getAsString()
            );

            dosage.addAdditionalInstruction(mapCodeToCodeableConcept(instructionCode, SNOMED_URI));
          }
        }
      }

      List<Dosage> dosageInstruction = new ArrayList<Dosage>();
      dosageInstruction.add(dosage);
      medicationResource.setDosageInstruction(dosageInstruction);
    }

    if (USE_SHR_EXTENSIONS) {

      medicationResource.addExtension()
        .setUrl(SHR_EXT + "shr-base-ActionCode-extension")
        .setValue(PRESCRIPTION_OF_DRUG_CC);

      medicationResource.setMeta(new Meta()
          .addProfile(SHR_EXT + "shr-medication-MedicationRequested"));
      // required fields for this profile are status, action-RequestedContext-extension,
      // medication[x]subject, authoredOn, requester

      Extension requestedContext = new Extension();
      requestedContext.setUrl(SHR_EXT + "shr-action-RequestedContext-extension");
      requestedContext.addExtension(
          SHR_EXT + "shr-action-Status-extension",
          new CodeType("completed"));
      requestedContext.addExtension(
          SHR_EXT + "shr-action-RequestIntent-extension",
          new CodeType("original-order"));

      medicationResource.addExtension(requestedContext);
    }

    BundleEntryComponent medicationEntry = newEntry(rand, bundle, medicationResource);
    // create new claim for medication
    medicationClaim(rand, personEntry, bundle, encounterEntry, medication.claim, medicationEntry);

    // Create new administration for medication, if needed
    if (medication.administration) {
      medicationAdministration(rand, personEntry, bundle, encounterEntry, medication,
              medicationResource);
    }

    return medicationEntry;
  }
  
  /**
   * Add a MedicationAdministration if needed for the given medication.
   * 
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry       The Entry for the Person
   * @param bundle            Bundle to add the MedicationAdministration to
   * @param encounterEntry    Current Encounter entry
   * @param medication        The Medication
   * @param medicationRequest The related medicationRequest
   * @return The added Entry
   */
  private static BundleEntryComponent medicationAdministration(
          RandomNumberGenerator rand, BundleEntryComponent personEntry, Bundle bundle,
          BundleEntryComponent encounterEntry, Medication medication,
          MedicationRequest medicationRequest) {

    MedicationAdministration medicationResource = new MedicationAdministration();

    medicationResource.setSubject(new Reference(personEntry.getFullUrl()));
    medicationResource.setContext(new Reference(encounterEntry.getFullUrl()));

    Code code = medication.codes.get(0);
    String system = code.system.equals("SNOMED-CT") ? SNOMED_URI : RXNORM_URI;

    medicationResource.setMedication(mapCodeToCodeableConcept(code, system));
    medicationResource.setEffective(new DateTimeType(new Date(medication.start)));

    medicationResource.setStatus(MedicationAdministrationStatus.fromCode("completed"));

    if (medication.prescriptionDetails != null) {
      JsonObject rxInfo = medication.prescriptionDetails;
      MedicationAdministrationDosageComponent dosage =
          new MedicationAdministrationDosageComponent();

      // as_needed is true if present
      if ((rxInfo.has("dosage")) && (!rxInfo.has("as_needed"))) {
        Quantity dose = new SimpleQuantity().setValue(
            rxInfo.get("dosage").getAsJsonObject().get("amount").getAsDouble());
        dosage.setDose((SimpleQuantity) dose);

        if (rxInfo.has("instructions")) {
          for (JsonElement instructionElement : rxInfo.get("instructions").getAsJsonArray()) {
            JsonObject instruction = instructionElement.getAsJsonObject();

            dosage.setText(instruction.get("display").getAsString());
          }
        }
      }
      medicationResource.setDosage(dosage);
    }

    if (!medication.reasons.isEmpty()) {
      // Only one element in list
      Code reason = medication.reasons.get(0);
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.getResource().fhirType().equals("Condition")) {
          Condition condition = (Condition) entry.getResource();
          // Only one element in list
          Coding coding = condition.getCode().getCoding().get(0);
          if (reason.code.equals(coding.getCode())) {
            medicationResource.addReasonReference().setReference(entry.getFullUrl());
          }
        }
      }
    }

    BundleEntryComponent medicationAdminEntry = newEntry(rand, bundle, medicationResource);
    return medicationAdminEntry;
  }

  private static final Code PRESCRIPTION_OF_DRUG_CODE =
      new Code("SNOMED-CT","33633005","Prescription of drug (procedure)");
  private static final CodeableConcept PRESCRIPTION_OF_DRUG_CC =
      mapCodeToCodeableConcept(PRESCRIPTION_OF_DRUG_CODE, SNOMED_URI);


  /**
   * Map the given Report to a FHIR DiagnosticReport resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Entry for the Person
   * @param bundle Bundle to add the Report to
   * @param encounterEntry Current Encounter entry
   * @param report The Report
   * @return The added Entry
   */
  private static BundleEntryComponent report(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle,
          BundleEntryComponent encounterEntry, Report report) {
    DiagnosticReport reportResource = new DiagnosticReport();
    reportResource.setStatus(DiagnosticReportStatus.FINAL);
    reportResource.setCode(mapCodeToCodeableConcept(report.codes.get(0), LOINC_URI));
    reportResource.setSubject(new Reference(personEntry.getFullUrl()));
    reportResource.setContext(new Reference(encounterEntry.getFullUrl()));
    reportResource.setEffective(convertFhirDateTime(report.start, true));
    reportResource.setIssued(new Date(report.start));
    for (Observation observation : report.observations) {
      Reference reference = new Reference(observation.fullUrl);
      reference.setDisplay(observation.codes.get(0).display);
      reportResource.addResult(reference);
    }

    // no SHR profile for DiagnosticReport

    return newEntry(rand, bundle, reportResource);
  }

  /**
   * Map the given CarePlan to a FHIR CarePlan resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Entry for the Person
   * @param bundle Bundle to add the CarePlan to
   * @param encounterEntry Current Encounter entry
   * @param carePlan The CarePlan to map to FHIR and add to the bundle
   * @return The added Entry
   */
  private static BundleEntryComponent careplan(RandomNumberGenerator rand,
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          CarePlan carePlan) {
    org.hl7.fhir.dstu3.model.CarePlan careplanResource = new org.hl7.fhir.dstu3.model.CarePlan();
    careplanResource.setIntent(CarePlanIntent.ORDER);
    careplanResource.setSubject(new Reference(personEntry.getFullUrl()));
    careplanResource.setContext(new Reference(encounterEntry.getFullUrl()));

    Code code = carePlan.codes.get(0);
    careplanResource.addCategory(mapCodeToCodeableConcept(code, SNOMED_URI));

    CarePlanActivityStatus activityStatus;
    GoalStatus goalStatus;

    Period period = new Period().setStart(new Date(carePlan.start));
    careplanResource.setPeriod(period);
    if (carePlan.stop != 0L) {
      period.setEnd(new Date(carePlan.stop));
      careplanResource.setStatus(CarePlanStatus.COMPLETED);
      activityStatus = CarePlanActivityStatus.COMPLETED;
      goalStatus = GoalStatus.ACHIEVED;
    } else {
      careplanResource.setStatus(CarePlanStatus.ACTIVE);
      activityStatus = CarePlanActivityStatus.INPROGRESS;
      goalStatus = GoalStatus.INPROGRESS;
    }

    if (!carePlan.activities.isEmpty()) {
      for (Code activity : carePlan.activities) {
        CarePlanActivityComponent activityComponent = new CarePlanActivityComponent();
        CarePlanActivityDetailComponent activityDetailComponent =
            new CarePlanActivityDetailComponent();

        activityDetailComponent.setStatus(activityStatus);

        activityDetailComponent.setCode(mapCodeToCodeableConcept(activity, SNOMED_URI));
        activityComponent.setDetail(activityDetailComponent);

        careplanResource.addActivity(activityComponent);
      }
    }

    if (!carePlan.reasons.isEmpty()) {
      // Only one element in list
      Code reason = carePlan.reasons.get(0);
      for (BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.getResource().fhirType().equals("Condition")) {
          Condition condition = (Condition) entry.getResource();
          // Only one element in list
          Coding coding = condition.getCode().getCoding().get(0);
          if (reason.code.equals(coding.getCode())) {
            careplanResource.addAddresses().setReference(entry.getFullUrl());
          }
        }
      }
    }

    for (JsonObject goal : carePlan.goals) {
      BundleEntryComponent goalEntry = caregoal(rand, bundle, goalStatus, goal);
      careplanResource.addGoal().setReference(goalEntry.getFullUrl());
    }

    return newEntry(rand, bundle, careplanResource);
  }

  /**
   * Map the given ImagingStudy to a FHIR ImagingStudy resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry The Entry for the Person
   * @param bundle Bundle to add the ImagingStudy to
   * @param encounterEntry Current Encounter entry
   * @param imagingStudy The ImagingStudy to map to FHIR and add to the bundle
   * @return The added Entry
   */
  private static BundleEntryComponent imagingStudy(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, BundleEntryComponent encounterEntry,
          ImagingStudy imagingStudy) {
    org.hl7.fhir.dstu3.model.ImagingStudy imagingStudyResource =
        new org.hl7.fhir.dstu3.model.ImagingStudy();

    imagingStudyResource.setUid("urn:oid:" + imagingStudy.dicomUid);
    imagingStudyResource.setPatient(new Reference(personEntry.getFullUrl()));
    imagingStudyResource.setContext(new Reference(encounterEntry.getFullUrl()));

    if (! imagingStudy.codes.isEmpty()) {
      imagingStudyResource.addProcedureCode(
              mapCodeToCodeableConcept(imagingStudy.codes.get(0), SNOMED_URI));
    }

    Date startDate = new Date(imagingStudy.start);
    imagingStudyResource.setStarted(startDate);

    // Convert the series into their FHIR equivalents
    int numberOfSeries = imagingStudy.series.size();
    imagingStudyResource.setNumberOfSeries(numberOfSeries);

    List<ImagingStudySeriesComponent> seriesResourceList =
        new ArrayList<ImagingStudySeriesComponent>();

    int totalNumberOfInstances = 0;
    int seriesNo = 1;

    for (ImagingStudy.Series series : imagingStudy.series) {
      ImagingStudySeriesComponent seriesResource = new ImagingStudySeriesComponent();
      seriesResource.setUid("urn:oid:" + series.dicomUid);
      seriesResource.setNumber(seriesNo);
      seriesResource.setStarted(startDate);
      seriesResource.setAvailability(InstanceAvailability.UNAVAILABLE);

      CodeableConcept modalityConcept = mapCodeToCodeableConcept(series.modality, DICOM_DCM_URI);
      seriesResource.setModality(modalityConcept.getCoding().get(0));

      CodeableConcept bodySiteConcept = mapCodeToCodeableConcept(series.bodySite, SNOMED_URI);
      seriesResource.setBodySite(bodySiteConcept.getCoding().get(0));

      // Convert the images in each series into their FHIR equivalents
      int numberOfInstances = series.instances.size();
      seriesResource.setNumberOfInstances(numberOfInstances);
      totalNumberOfInstances += numberOfInstances;

      List<ImagingStudySeriesInstanceComponent> instanceResourceList =
          new ArrayList<ImagingStudySeriesInstanceComponent>();

      int instanceNo = 1;

      for (ImagingStudy.Instance instance : series.instances) {
        ImagingStudySeriesInstanceComponent instanceResource =
            new ImagingStudySeriesInstanceComponent();
        instanceResource.setUid("urn:oid:" + instance.dicomUid);
        instanceResource.setTitle(instance.title);
        instanceResource.setSopClass("urn:oid:" + instance.sopClass.code);
        instanceResource.setNumber(instanceNo);

        instanceResourceList.add(instanceResource);
        instanceNo += 1;
      }

      seriesResource.setInstance(instanceResourceList);
      seriesResourceList.add(seriesResource);
      seriesNo += 1;
    }

    imagingStudyResource.setSeries(seriesResourceList);
    imagingStudyResource.setNumberOfInstances(totalNumberOfInstances);
    return newEntry(rand, bundle, imagingStudyResource);
  }
  
  /**
   * Map the given Media element to a FHIR Media resource, and add it to the given Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry    The Entry for the Person
   * @param bundle         Bundle to add the Media to
   * @param encounterEntry Current Encounter entry
   * @param obs   The Observation to map to FHIR and add to the bundle
   * @return The added Entry
   */
  private static BundleEntryComponent media(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle,
          BundleEntryComponent encounterEntry, Observation obs) {
    org.hl7.fhir.dstu3.model.Media mediaResource =
        new org.hl7.fhir.dstu3.model.Media();

    if (obs.codes != null && obs.codes.size() > 0) {
      List<CodeableConcept> reasonList = obs.codes.stream()
          .map(code -> mapCodeToCodeableConcept(code, SNOMED_URI)).collect(Collectors.toList());
      mediaResource.setReasonCode(reasonList);
    }
    
    // Hard code as an image
    mediaResource.setType(DigitalMediaType.PHOTO);
    mediaResource.setSubject(new Reference(personEntry.getFullUrl()));

    Attachment content = (Attachment) obs.value;
    org.hl7.fhir.dstu3.model.Attachment contentResource = new org.hl7.fhir.dstu3.model.Attachment();
    
    contentResource.setContentType(content.contentType);
    contentResource.setLanguage(content.language);
    if (content.data != null) {
      contentResource.setDataElement(new org.hl7.fhir.dstu3.model.Base64BinaryType(content.data));
    }
    contentResource.setUrl(content.url);
    contentResource.setSize(content.size);
    contentResource.setTitle(content.title);
    if (content.hash != null) {
      contentResource.setHashElement(new org.hl7.fhir.dstu3.model.Base64BinaryType(content.hash));
    }
    
    mediaResource.setWidth(content.width);
    mediaResource.setHeight(content.height);
    
    mediaResource.setContent(contentResource);

    return newEntry(rand, bundle, mediaResource);
  }

  /**
   * Map the HealthRecord.Device into a FHIR Device and add it to the Bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param personEntry    The Person entry.
   * @param bundle         Bundle to add to.
   * @param device         The device to add.
   * @return The added Entry.
   */
  private static BundleEntryComponent device(RandomNumberGenerator rand, 
          BundleEntryComponent personEntry, Bundle bundle, HealthRecord.Device device) {
    Device deviceResource = new Device();
    Device.DeviceUdiComponent udi = new Device.DeviceUdiComponent()
        .setDeviceIdentifier(device.deviceIdentifier)
        .setCarrierHRF(device.udi);
    deviceResource.setUdi(udi);
    deviceResource.setStatus(FHIRDeviceStatus.ACTIVE);
    if (device.manufacturer != null) {
      deviceResource.setManufacturer(device.manufacturer);
    }
    if (device.model != null) {
      deviceResource.setModel(device.model);
    }
    deviceResource.setManufactureDate(new Date(device.manufactureTime));
    deviceResource.setExpirationDate(new Date(device.expirationTime));
    deviceResource.setLotNumber(device.lotNumber);
    deviceResource.setType(mapCodeToCodeableConcept(device.codes.get(0), SNOMED_URI));
    deviceResource.setPatient(new Reference(personEntry.getFullUrl()));
    return newEntry(rand, bundle, deviceResource);
  }
  
  /**
   * Map the JsonObject for a Supply into a FHIR SupplyDelivery and add it to the Bundle.
   *
   * @param rand           Source of randomness to use when generating ids etc
   * @param personEntry    The Person entry.
   * @param bundle         Bundle to add to.
   * @param supply         The supplied object to add.
   * @param encounter      The encounter during which the supplies were delivered
   * @return The added Entry.
   */
  private static BundleEntryComponent supplyDelivery(RandomNumberGenerator rand,
          BundleEntryComponent personEntry, Bundle bundle, HealthRecord.Supply supply,
          Encounter encounter) {
   
    SupplyDelivery supplyResource = new SupplyDelivery();
    supplyResource.setStatus(SupplyDeliveryStatus.COMPLETED);
    supplyResource.setPatient(new Reference(personEntry.getFullUrl()));
    
    CodeableConcept type = new CodeableConcept();
    type.addCoding()
      .setCode("device")
      .setDisplay("Device")
      .setSystem("http://hl7.org/fhir/supply-item-type");
    supplyResource.setType(type);
    
    SupplyDeliverySuppliedItemComponent suppliedItem = new SupplyDeliverySuppliedItemComponent();
    suppliedItem.setItem(mapCodeToCodeableConcept(supply.codes.get(0), SNOMED_URI));
    
    SimpleQuantity quantity = new SimpleQuantity();
    quantity.setValue(supply.quantity);
    suppliedItem.setQuantity(quantity);
    
    supplyResource.setSuppliedItem(suppliedItem);
    
    supplyResource.setOccurrence(convertFhirDateTime(supply.start, true));
    
    return newEntry(rand, bundle, supplyResource);
  }
  
  /**
   * Map the Provider into a FHIR Organization resource, and add it to the given Bundle.
   * @param bundle The Bundle to add to
   * @param provider The Provider
   * @return The added Entry
   */
  protected static BundleEntryComponent provider(Bundle bundle, Provider provider) {
    org.hl7.fhir.dstu3.model.Organization organizationResource =
        new org.hl7.fhir.dstu3.model.Organization();

    List<CodeableConcept> organizationType = new ArrayList<CodeableConcept>();
    organizationType.add(
        mapCodeToCodeableConcept(
            new Code(
                "http://hl7.org/fhir/organization-type",
                "prov",
                "Healthcare Provider"),
            "http://hl7.org/fhir/organization-type"));

    organizationResource.addIdentifier().setSystem("https://github.com/synthetichealth/synthea")
    .setValue((String) provider.getResourceID());

    organizationResource.setId(provider.getResourceID());
    organizationResource.setName(provider.name);
    organizationResource.setType(organizationType);

    Address address = new Address()
        .addLine(provider.address)
        .setCity(provider.city)
        .setPostalCode(provider.zip)
        .setState(provider.state);
    if (COUNTRY_CODE != null) {
      address.setCountry(COUNTRY_CODE);
    }
    organizationResource.addAddress(address);

    Point2D.Double coord = provider.getLonLat();
    if (coord != null) {
      Extension geolocation = address.addExtension();
      geolocation.setUrl("http://hl7.org/fhir/StructureDefinition/geolocation");
      geolocation.addExtension("latitude", new DecimalType(coord.getY()));
      geolocation.addExtension("longitude", new DecimalType(coord.getX()));
    }
    
    if (provider.phone != null && !provider.phone.isEmpty()) {
      ContactPoint contactPoint = new ContactPoint()
          .setSystem(ContactPointSystem.PHONE)
          .setValue(provider.phone);
      organizationResource.addTelecom(contactPoint);
    }

    if (USE_SHR_EXTENSIONS) {
      organizationResource.setMeta(new Meta().addProfile(SHR_EXT + "shr-entity-Organization"));
      // required fields for this profile are identifier, type, address, and contact

      organizationResource.addIdentifier()
          .setSystem("urn:ietf:rfc:3986")
          .setValue("urn:uuid:" + provider.getResourceID());
      organizationResource.addContact().setName(new HumanName().setText("Synthetic Provider"));
    }

    return newEntry(bundle, organizationResource, provider.getResourceID());
  }

  /**
   * Map the clinician into a FHIR Practitioner resource, and add it to the given Bundle.
   * @param bundle The Bundle to add to
   * @param clinician The clinician
   * @return The added Entry
   */
  protected static BundleEntryComponent practitioner(Bundle bundle, Clinician clinician) {
    Practitioner practitionerResource = new Practitioner();

    practitionerResource.addIdentifier()
            .setSystem("http://hl7.org/fhir/sid/us-npi")
            .setValue("" + (9_999_999_999L - clinician.identifier));
    practitionerResource.setActive(true);
    practitionerResource.addName().setFamily(
        (String) clinician.attributes.get(Clinician.LAST_NAME))
      .addGiven((String) clinician.attributes.get(Clinician.FIRST_NAME))
      .addPrefix((String) clinician.attributes.get(Clinician.NAME_PREFIX));

    Address address = new Address()
        .addLine((String) clinician.attributes.get(Clinician.ADDRESS))
        .setCity((String) clinician.attributes.get(Clinician.CITY))
        .setPostalCode((String) clinician.attributes.get(Clinician.ZIP))
        .setState((String) clinician.attributes.get(Clinician.STATE));
    if (COUNTRY_CODE != null) {
      address.setCountry(COUNTRY_CODE);
    }
    practitionerResource.addAddress(address);

    if (clinician.attributes.get(Person.GENDER).equals("M")) {
      practitionerResource.setGender(AdministrativeGender.MALE);
    } else if (clinician.attributes.get(Person.GENDER).equals("F")) {
      practitionerResource.setGender(AdministrativeGender.FEMALE);
    }

    return newEntry(bundle, practitionerResource, clinician.getResourceID());
  }

  /**
   * Map the JsonObject into a FHIR Goal resource, and add it to the given Bundle.
   * @param rand Source of randomness to use when generating ids etc
   * @param bundle The Bundle to add to
   * @param goalStatus The GoalStatus
   * @param goal The JsonObject
   * @return The added Entry
   */
  private static BundleEntryComponent caregoal(
      RandomNumberGenerator rand, Bundle bundle, GoalStatus goalStatus, JsonObject goal) {
    String resourceID = rand.randUUID().toString();

    org.hl7.fhir.dstu3.model.Goal goalResource =
        new org.hl7.fhir.dstu3.model.Goal();
    goalResource.setStatus(goalStatus);
    goalResource.setId(resourceID);

    if (goal.has("text")) {
      CodeableConcept descriptionCodeableConcept = new CodeableConcept();

      descriptionCodeableConcept.setText(goal.get("text").getAsString());
      goalResource.setDescription(descriptionCodeableConcept);
    } else if (goal.has("codes")) {
      CodeableConcept descriptionCodeableConcept = new CodeableConcept();

      JsonObject code =
          goal.get("codes").getAsJsonArray().get(0).getAsJsonObject();
      descriptionCodeableConcept.addCoding()
        .setSystem(LOINC_URI)
        .setCode(code.get("code").getAsString())
        .setDisplay(code.get("display").getAsString());

      descriptionCodeableConcept.setText(code.get("display").getAsString());
      goalResource.setDescription(descriptionCodeableConcept);
    } else if (goal.has("observation")) {
      CodeableConcept descriptionCodeableConcept = new CodeableConcept();

      // build up our own text from the observation condition, similar to the graphviz logic
      JsonObject logic = goal.get("observation").getAsJsonObject();

      String[] text = {
        logic.get("codes").getAsJsonArray().get(0)
            .getAsJsonObject().get("display").getAsString(),
        logic.get("operator").getAsString(),
        logic.get("value").getAsString()
      };

      descriptionCodeableConcept.setText(String.join(" ", text));
      goalResource.setDescription(descriptionCodeableConcept);
    }

    if (goal.has("addresses")) {
      for (JsonElement reasonElement : goal.get("addresses").getAsJsonArray()) {
        if (reasonElement instanceof JsonObject) {
          JsonObject reasonObject = reasonElement.getAsJsonObject();
          String reasonCode =
              reasonObject.get("codes")
                  .getAsJsonObject()
                  .get("SNOMED-CT")
                  .getAsJsonArray()
                  .get(0)
                  .getAsString();

          for (BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource().fhirType().equals("Condition")) {
              Condition condition = (Condition) entry.getResource();
              // Only one element in list
              Coding coding = condition.getCode().getCoding().get(0);
              if (reasonCode.equals(coding.getCode())) {
                goalResource.addAddresses()
                    .setReference(entry.getFullUrl());
              }
            }
          }
        }
      }
    }

    return newEntry(rand, bundle, goalResource);
  }

  /**
   * Convert the unit into a UnitsOfTime.
   *
   * @param unit unit String
   * @return a UnitsOfTime representing the given unit
   */
  private static UnitsOfTime convertUcumCode(String unit) {
    // From: http://hl7.org/fhir/ValueSet/units-of-time
    switch (unit) {
      case "seconds":
        return UnitsOfTime.S;
      case "minutes":
        return UnitsOfTime.MIN;
      case "hours":
        return UnitsOfTime.H;
      case "days":
        return UnitsOfTime.D;
      case "weeks":
        return UnitsOfTime.WK;
      case "months":
        return UnitsOfTime.MO;
      case "years":
        return UnitsOfTime.A;
      default:
        return null;
    }
  }

  /**
   * Convert the timestamp into a FHIR DateType or DateTimeType.
   *
   * @param datetime Timestamp
   * @param time If true, return a DateTime; if false, return a Date.
   * @return a DateType or DateTimeType representing the given timestamp
   */
  private static Type convertFhirDateTime(long datetime, boolean time) {
    Date date = new Date(datetime);

    if (time) {
      return new DateTimeType(date);
    } else {
      return new DateType(date);
    }
  }

  /**
   * Helper function to convert a Code into a CodeableConcept. Takes an optional system, which
   * replaces the Code.system in the resulting CodeableConcept if not null.
   *
   * @param from The Code to create a CodeableConcept from.
   * @param system The system identifier, such as a URI. Optional; may be null.
   * @return The converted CodeableConcept
   */
  private static CodeableConcept mapCodeToCodeableConcept(Code from, String system) {
    CodeableConcept to = new CodeableConcept();
    system = system == null ? null : ExportHelper.getSystemURI(system);
    from.system = ExportHelper.getSystemURI(from.system);

    if (from.display != null) {
      to.setText(from.display);
    }

    Coding coding = new Coding();
    coding.setCode(from.code);
    coding.setDisplay(from.display);
    if (from.system == null) {
      coding.setSystem(system);
    } else {
      coding.setSystem(from.system);
    }

    to.addCoding(coding);

    return to;
  }

  /**
   * Helper function to create an Entry for the given Resource within the given Bundle. Sets the
   * resourceID to a random UUID, sets the entry's fullURL to that resourceID, and adds the entry to
   * the bundle.
   *
   * @param rand Source of randomness to use when generating ids etc
   * @param bundle The Bundle to add the Entry to
   * @param resource Resource the new Entry should contain
   * @return the created Entry
   */
  private static BundleEntryComponent newEntry(RandomNumberGenerator rand, Bundle bundle, 
          Resource resource) {
    String resourceID = rand.randUUID().toString();
    return newEntry(bundle, resource, resourceID);
  }

  /**
   * Helper function to create an Entry for the given Resource within the given Bundle.
   * Sets the entry's fullURL to resourceID, and adds the entry to the bundle.
   *
   * @param bundle The Bundle to add the Entry to
   * @param resource Resource the new Entry should contain
   * @param resourceID The Resource ID to assign
   * @return the created Entry
   */
  private static BundleEntryComponent newEntry(Bundle bundle, Resource resource,
      String resourceID) {
    BundleEntryComponent entry = bundle.addEntry();

    resource.setId(resourceID);
    if (Config.getAsBoolean("exporter.fhir.bulk_data")) {
      entry.setFullUrl(resource.fhirType() + "/" + resourceID);
    } else {
      entry.setFullUrl("urn:uuid:" + resourceID);
    }
    entry.setResource(resource);

    if (TRANSACTION_BUNDLE) {
      BundleEntryRequestComponent request = entry.getRequest();
      request.setMethod(HTTPVerb.POST);
      String resourceType = resource.getResourceType().name();
      request.setUrl(resourceType);
      if (ExportHelper.UNDUPLICATED_FHIR_RESOURCES.contains(resourceType)) {
        Property prop = entry.getResource().getNamedProperty("identifier");
        if (prop != null && prop.getValues().size() > 0) {
          Identifier identifier = (Identifier)prop.getValues().get(0);
          request.setIfNoneExist(
              "identifier=" + identifier.getSystem() + "|" + identifier.getValue());
        }
      }
      entry.setRequest(request);
    }

    return entry;
  }
}
