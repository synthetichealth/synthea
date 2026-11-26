package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.FailedExportHelper;
import org.mitre.synthea.ParallelTestingService;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.VitalSign;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Uses HAPI FHIR project to validate FHIR export. http://hapifhir.io/doc_validation.html
 */
public class FHIRR5ExporterTest {
  private static boolean physStateEnabled;

  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Setup state for exporter test.
   */
  @BeforeClass
  public static void setup() {
    // Ensure Physiology state is enabled
    physStateEnabled = State.ENABLE_PHYSIOLOGY_STATE;
    State.ENABLE_PHYSIOLOGY_STATE = true;
    PayerManager.clear();
    PayerManager.loadNoInsurance();
  }

  /**
   * Reset state after exporter test.
   */
  @AfterClass
  public static void tearDown() {
    State.ENABLE_PHYSIOLOGY_STATE = physStateEnabled;
  }

  @Test
  public void testDecimalRounding() {
    Integer i = 123456;
    Object v = FhirR5.mapValueToFHIRType(i,"fake");
    assertTrue(v instanceof Quantity);
    Quantity q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(123460)) == 0);

    Double d = 0.000123456;
    v = FhirR5.mapValueToFHIRType(d, "fake");
    assertTrue(v instanceof Quantity);
    q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(0.00012346)) == 0);

    d = 0.00012345678901234;
    v = FhirR5.mapValueToFHIRType(d, "fake");
    assertTrue(v instanceof Quantity);
    q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(0.00012346)) == 0);
  }

  private void setupTestFhirExport() throws Exception {
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());

    // setting these static fields randomly per patient creates nondeterministic effects
    // since the exporters run in parallel below.
    // instead, set them once and run a few patients for each of the relevant combinations
    TestHelper.exportOff();
    FhirR5.reloadIncludeExclude();
    FhirR5.TRANSACTION_BUNDLE = true;
    FhirR5.USE_US_CORE_IG = false;
    FhirR5.US_CORE_VERSION = "4";
  }

  @Test
  public void testFHIRR5Export() throws Exception {
    setupTestFhirExport();

    ValidationResources baseValidator = ValidationResources.forR5(null);
    FhirR5.USE_US_CORE_IG = false;
    baseTestFHIRR5Export(baseValidator);
  }

  @Test
  public void testFHIRR5ExportUsCore3() throws Exception {
    setupTestFhirExport();

    FhirR5.USE_US_CORE_IG = true;
    FhirR5.US_CORE_VERSION = "3";
    FhirR5.useUSCore3();

    ValidationResources uscore3Validator = ValidationResources.forR5(FhirR5.USCoreVersion.v311);
    baseTestFHIRR5Export(uscore3Validator);
  }

  @Test
  public void testFHIRR5ExportUsCore4() throws Exception {
    setupTestFhirExport();

    FhirR5.USE_US_CORE_IG = true;
    FhirR5.US_CORE_VERSION = "4";
    FhirR5.useUSCore4();

    ValidationResources uscore4Validator = ValidationResources.forR5(FhirR5.USCoreVersion.v400);
    baseTestFHIRR5Export(uscore4Validator);
  }

  @Test
  public void testFHIRR5ExportUsCore5() throws Exception {
    setupTestFhirExport();

    FhirR5.USE_US_CORE_IG = true;
    FhirR5.US_CORE_VERSION = "5";
    FhirR5.useUSCore5();

    ValidationResources uscore5Validator = ValidationResources.forR5(FhirR5.USCoreVersion.v501);
    baseTestFHIRR5Export(uscore5Validator);
  }

  @Test
  public void testFHIRR5ExportUsCore6() throws Exception {
    setupTestFhirExport();

    FhirR5.USE_US_CORE_IG = true;
    FhirR5.US_CORE_VERSION = "6";
    FhirR5.useUSCore6();

    ValidationResources uscore6Validator = ValidationResources.forR5(FhirR5.USCoreVersion.v610);
    baseTestFHIRR5Export(uscore6Validator);
  }

  @Test
  public void testFHIRR5ExportUsCore7() throws Exception {
    setupTestFhirExport();

    FhirR5.USE_US_CORE_IG = true;
    FhirR5.US_CORE_VERSION = "7.0.0";
    FhirR5.useUSCore7();

    ValidationResources uscore7Validator = ValidationResources.forR5(FhirR5.USCoreVersion.v700);
    baseTestFHIRR5Export(uscore7Validator);
  }

  /**
   * Common test steps for testing FHIR R5 exporter. Assumes that various settings
   * have been previously set, and the given validator is in alignment with those settings.
   * @param validator ValidationResources configured for specific FHIR settings
   */
  public void baseTestFHIRR5Export(ValidationResources validator) throws Exception {
    FhirContext ctx = FhirR5.getContext();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    List<String> errors = ParallelTestingService.runInParallel(4, (person) -> {
      List<String> validationErrors = new ArrayList<String>();

      String fhirJson = FhirR5.convertToFHIRJson(person, System.currentTimeMillis());

      // Check that the fhirJSON doesn't contain unresolved SNOMED-CT strings
      // (these should have been converted into URIs)
      if (fhirJson.contains("SNOMED-CT")) {
        validationErrors.add(
            "JSON contains unconverted references to 'SNOMED-CT' (should be URIs)");
      }

      // Let's crack open the Bundle and validate
      // each individual entry.resource to get context-sensitive error
      // messages...
      // IMPORTANT: this approach significantly reduces memory usage when compared to
      // validating the entire bundle at a time, but means that validating references
      // is impossible.
      // As of 2021-01-05, validating the bundle didn't validate references anyway,
      // but at some point we may want to do that.

      Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
      for (BundleEntryComponent entry : bundle.getEntry()) {
        ValidationResult eresult = validator.validateR5(entry.getResource());
        if (!eresult.isSuccessful()) {
          for (SingleValidationMessage emessage : eresult.getMessages()) {
            boolean valid = false;
            if (emessage.getSeverity() == ResultSeverityEnum.INFORMATION
                || emessage.getSeverity() == ResultSeverityEnum.WARNING) {
              /*
               * Ignore warnings.
               */
              valid = true;
            }
            if (!valid) {
              System.out.println(parser.encodeResourceToString(entry.getResource()));
              System.out.println("ERROR: " + emessage.getMessage());
              validationErrors.add(emessage.getMessage());
            }
          }
        }
      }

      // make sure all entries have a unique fullURL
      List<String> allFullUrls = bundle.getEntry().stream()
          .map(e -> e.getFullUrl()).collect(Collectors.toList());
      Set<String> uniqueFullUrls = new HashSet<>(allFullUrls);

      if (allFullUrls.size() != uniqueFullUrls.size()) {
        Map<String, List<BundleEntryComponent>> entriesByUrl = bundle.getEntry().stream()
            .collect(Collectors.groupingBy(BundleEntryComponent::getFullUrl));

        entriesByUrl.values().forEach(entryList -> {
          if (entryList.size() > 1) {
            String fullUrl = entryList.get(0).getFullUrl();
            String resourceTypes = entryList.stream()
                .map(e -> e.getResource().getResourceType().toString())
                .collect(Collectors.joining(" , "));
            validationErrors.add("Found bundle entries with duplicate fullURL: " + fullUrl
                + " - Types: " + resourceTypes);
          }
        });
      }

      if (! validationErrors.isEmpty()) {
        FailedExportHelper.dumpInfo("FHIRR5", fhirJson, validationErrors, person);
      }
      return validationErrors;
    });
    assertTrue("Validation of exported FHIR bundle failed: "
        + String.join("|", errors), errors.size() == 0);
  }

  @Test
  public void testSampledDataExport() throws Exception {

    Person person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.FIRST_LANGUAGE, "spanish");
    person.attributes.put(Person.RACE, "other");
    person.attributes.put(Person.ETHNICITY, "hispanic");
    person.attributes.put(Person.INCOME, Integer.parseInt(Config
        .get("generate.demographics.socioeconomic.income.poverty")) * 2);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.CONTACT_EMAIL, "test@test.test");
    person.attributes.put(Person.CONTACT_GIVEN_NAME, "John");
    person.attributes.put(Person.CONTACT_FAMILY_NAME, "Appleseed");
    person.history = new LinkedList<>();

    Provider stub = new Provider();
    stub.name = "Fake Provider";
    stub.npi = "0";
    Clinician doc = new Clinician(0, person, 0, stub);
    doc.attributes.put(Person.GENDER, "F");
    ArrayList<Clinician> docs = new ArrayList<Clinician>();
    docs.add(doc);
    stub.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, docs);
    person.setProvider(EncounterType.AMBULATORY, stub);
    person.setProvider(EncounterType.WELLNESS, stub);
    person.setProvider(EncounterType.EMERGENCY, stub);
    person.setProvider(EncounterType.INPATIENT, stub);

    Long time = System.currentTimeMillis();
    int age = 35;
    long birthTime = time - Utilities.convertTime("years", age);
    person.attributes.put(Person.BIRTHDATE, birthTime);

    PayerManager.loadNoInsurance();
    person.coverage.setPlanToNoInsurance((long) person.attributes.get(Person.BIRTHDATE));
    person.coverage.setPlanToNoInsurance(time + 1);

    Module module = TestHelper.getFixture("observation.json");

    State encounter = module.getState("SomeEncounter");
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    State physiology = module.getState("Simulate_CVS");
    assertTrue(physiology.process(person, time));
    person.history.add(physiology);

    State sampleObs = module.getState("SampledDataObservation");
    assertTrue(sampleObs.process(person, time));
    person.history.add(sampleObs);

    FhirContext ctx = FhirR5.getContext();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);
    String fhirJson = FhirR5.convertToFHIRJson(person, time);
    Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Observation) {
        Observation obs = (Observation) entry.getResource();
        assertTrue(obs.getValue() instanceof SampledData);
        SampledData data = (SampledData) obs.getValue();
        assertEquals(10, data.getInterval().doubleValue(), 0.001); // 0.01s == 10ms
        assertEquals(3, (int) data.getDimensions());
      }
    }
  }

  @Test
  public void testObservationAttachment() throws Exception {

    Person person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.FIRST_LANGUAGE, "spanish");
    person.attributes.put(Person.RACE, "other");
    person.attributes.put(Person.ETHNICITY, "hispanic");
    person.attributes.put(Person.INCOME, Integer.parseInt(Config
        .get("generate.demographics.socioeconomic.income.poverty")) * 2);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put("Pulmonary Resistance", 0.1552);
    person.attributes.put("BMI Multiplier", 0.055);
    person.setVitalSign(VitalSign.BMI, 21.0);
    person.history = new LinkedList<>();

    Provider stub = new Provider();
    stub.name = "Fake Provider";
    stub.npi = "0";
    Clinician doc = new Clinician(0, person, 0, stub);
    doc.attributes.put(Person.GENDER, "F");
    ArrayList<Clinician> docs = new ArrayList<Clinician>();
    docs.add(doc);
    stub.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, docs);
    person.setProvider(EncounterType.AMBULATORY, stub);
    person.setProvider(EncounterType.WELLNESS, stub);
    person.setProvider(EncounterType.EMERGENCY, stub);
    person.setProvider(EncounterType.INPATIENT, stub);

    Long time = System.currentTimeMillis();
    int age = 35;
    long birthTime = time - Utilities.convertTime("years", age);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.coverage.setPlanToNoInsurance((long) person.attributes.get(Person.BIRTHDATE));
    person.coverage.setPlanToNoInsurance(time + 1);

    Module module = TestHelper.getFixture("observation.json");

    State physiology = module.getState("Simulate_CVS");
    assertTrue(physiology.process(person, time));
    person.history.add(physiology);

    State encounter = module.getState("SomeEncounter");
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    State chartState = module.getState("ChartObservation");
    assertTrue(chartState.process(person, time));
    person.history.add(chartState);

    State urlState = module.getState("UrlObservation");
    assertTrue(urlState.process(person, time));
    person.history.add(urlState);

    FhirContext ctx = FhirR5.getContext();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);
    String fhirJson = FhirR5.convertToFHIRJson(person, time);
    Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof DocumentReference) {
        DocumentReference documentReference = (DocumentReference) entry.getResource();
        if ("Invasive arterial pressure".equals(documentReference.getDescription())) {
          assertNotNull(documentReference.getContentFirstRep().getAttachment());
          assertTrue(Base64.isBase64(documentReference.getContentFirstRep().getAttachment()
              .getDataElement().getValueAsString()));
        } else if (documentReference.hasContent()) {
          DocumentReference.DocumentReferenceContentComponent content =
              documentReference.getContentFirstRep();
          if (content.hasAttachment() && content.getAttachment().hasData()) {
            assertTrue(Base64.isBase64(content.getAttachment().getDataElement()
                .getValueAsString()));
          } else if (content.hasAttachment() && content.getAttachment().hasUrl()) {
            assertEquals("https://example.com/image/12498596132",
                content.getAttachment().getUrl());
            assertEquals("en-US", content.getAttachment().getLanguage());
            assertTrue(content.getAttachment().getSize() > 0);
          } else {
            fail("Invalid DocumentReference element in output JSON");
          }
        } else {
          fail("Invalid DocumentReference element in output JSON");
        }
      }
    }
  }

  @Test
  public void testShouldExport() {
    // nothing set for either == allow everything
    Config.set("exporter.fhir.included_resources", "");
    Config.set("exporter.fhir.excluded_resources", "");
    FhirR5.reloadIncludeExclude();

    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.MedicationRequest.class));
    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.Procedure.class));
    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.Condition.class));

    // a few items included, all others excluded
    Config.set("exporter.fhir.included_resources", "MedicationRequest, Procedure");
    Config.set("exporter.fhir.excluded_resources", "");
    FhirR5.reloadIncludeExclude();

    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.MedicationRequest.class));
    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.Procedure.class));

    assertFalse(FhirR5.shouldExport(org.hl7.fhir.r5.model.Condition.class));


    // a few items excluded, all others included
    Config.set("exporter.fhir.included_resources", "");
    Config.set("exporter.fhir.excluded_resources", "MedicationRequest,Procedure");
    FhirR5.reloadIncludeExclude();

    assertFalse(FhirR5.shouldExport(org.hl7.fhir.r5.model.MedicationRequest.class));
    assertFalse(FhirR5.shouldExport(org.hl7.fhir.r5.model.Procedure.class));

    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.Condition.class));


    // both set in config == both disabled == allow everything
    Config.set("exporter.fhir.included_resources", "Condition,Observation");
    Config.set("exporter.fhir.excluded_resources", "MedicationRequest ,Procedure");
    FhirR5.reloadIncludeExclude();

    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.MedicationRequest.class));
    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.Procedure.class));
    assertTrue(FhirR5.shouldExport(org.hl7.fhir.r5.model.Condition.class));
  }

  @Test
  public void testFHIRExportIncludes() throws Exception {
    Config.set("exporter.fhir.included_resources", "MedicationRequest  ,Procedure");
    Config.set("exporter.fhir.excluded_resources", "");
    FhirR5.reloadIncludeExclude();

    Person p = new Person(0L);
    p.attributes.put(Person.RACE, "dummy value to prevent NPE");
    p.attributes.put(Person.ETHNICITY, "dummy value to prevent NPE");
    p.attributes.put(Person.FIRST_LANGUAGE, "english");
    p.attributes.put(Person.BIRTHDATE, 0L);
    p.attributes.put(Person.GENDER, "F");
    p.coverage.setPlanToNoInsurance(0L);

    Provider stub = new Provider();
    stub.name = "Fake Provider";
    stub.npi = "0";
    Clinician doc = new Clinician(0, p, 0, stub);
    ArrayList<Clinician> docs = new ArrayList<Clinician>();
    docs.add(doc);
    stub.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, docs);
    p.setProvider(EncounterType.AMBULATORY, stub);
    p.setProvider(EncounterType.WELLNESS, stub);
    p.setProvider(EncounterType.EMERGENCY, stub);
    p.setProvider(EncounterType.INPATIENT, stub);
    p.record.provider = stub;

    HealthRecord.Encounter e = p.record.encounterStart(0, EncounterType.WELLNESS);
    e.provider = p.record.provider;

    Code dummyCode = new Code("","","");

    p.record.conditionStart(0, "Terminal Examplitis :(").codes.add(dummyCode);
    p.record.procedure(0, "Examplotomy").codes.add(dummyCode);
    p.record.medicationStart(0, "Examplitol", true).codes.add(dummyCode);

    Bundle bundle = FhirR5.convertToFHIR(p, 0);


    boolean foundMedications = false;
    boolean foundProcedures = false;
    boolean foundConditions = false;

    for (BundleEntryComponent entry : bundle.getEntry()) {
      switch (entry.getResource().getResourceType().toString()) {
        case "Condition":
          foundConditions = true;
          break;
        case "MedicationRequest":
          foundMedications = true;
          break;
        case "Procedure":
          foundProcedures = true;
          break;
        default:
          // do nothing
      }
    }

    assertTrue("MedicationRequest missing but should have been included", foundMedications);
    assertTrue("Procedure resource missing but should have been included", foundProcedures);
    assertFalse("Condition resource found but should not have been included", foundConditions);
  }

  @Test
  public void testFHIRExportExcludes() throws Exception {
    Config.set("exporter.fhir.included_resources", "");
    Config.set("exporter.fhir.excluded_resources", "MedicationRequest,Procedure,Observation");
    FhirR5.reloadIncludeExclude();

    Person p = new Person(0L);
    p.attributes.put(Person.RACE, "dummy value to prevent NPE");
    p.attributes.put(Person.ETHNICITY, "dummy value to prevent NPE");
    p.attributes.put(Person.FIRST_LANGUAGE, "english");
    p.attributes.put(Person.BIRTHDATE, 0L);
    p.attributes.put(Person.GENDER, "F");
    p.coverage.setPlanToNoInsurance(0L);

    Provider stub = new Provider();
    stub.name = "Fake Provider";
    stub.npi = "0";
    Clinician doc = new Clinician(0, p, 0, stub);
    ArrayList<Clinician> docs = new ArrayList<Clinician>();
    docs.add(doc);
    stub.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, docs);
    p.setProvider(EncounterType.AMBULATORY, stub);
    p.setProvider(EncounterType.WELLNESS, stub);
    p.setProvider(EncounterType.EMERGENCY, stub);
    p.setProvider(EncounterType.INPATIENT, stub);
    p.record.provider = stub;

    HealthRecord.Encounter e = p.record.encounterStart(0, EncounterType.WELLNESS);
    e.provider = p.record.provider;

    Code dummyCode = new Code("","","");

    p.record.conditionStart(0, "Terminal Examplitis :(").codes.add(dummyCode);
    p.record.procedure(0, "Examplotomy").codes.add(dummyCode);
    p.record.medicationStart(0, "Examplitol", true).codes.add(dummyCode);

    Bundle bundle = FhirR5.convertToFHIR(p, 0);


    boolean foundMedications = false;
    boolean foundProcedures = false;
    boolean foundConditions = false;

    for (BundleEntryComponent entry : bundle.getEntry()) {
      switch (entry.getResource().getResourceType().toString()) {
        case "Condition":
          foundConditions = true;
          break;
        case "MedicationRequest":
          foundMedications = true;
          break;
        case "Procedure":
          foundProcedures = true;
          break;
        default:
          // do nothing
      }
    }

    assertFalse("MedicationRequest found but should not have been included", foundMedications);
    assertFalse("Procedure resource found but should not have been included", foundProcedures);
    assertTrue("Condition resource missing but should have been included", foundConditions);
  }
}
