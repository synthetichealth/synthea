package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Media;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.SampledData;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mockito.Mockito;

/**
 * Uses HAPI FHIR project to validate FHIR export. http://hapifhir.io/doc_validation.html
 */
public class FHIRSTU3ExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testDecimalRounding() {
    Integer i = 123456;
    Object v = FhirStu3.mapValueToFHIRType(i,"fake");
    assertTrue(v instanceof Quantity);
    Quantity q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(123460)) == 0);

    Double d = 0.000123456;
    v = FhirStu3.mapValueToFHIRType(d, "fake");
    assertTrue(v instanceof Quantity);
    q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(0.00012346)) == 0);

    d = 0.00012345678901234;
    v = FhirStu3.mapValueToFHIRType(d, "fake");
    assertTrue(v instanceof Quantity);
    q = (Quantity)v;
    assertTrue(q.getValue().compareTo(BigDecimal.valueOf(0.00012346)) == 0);
  }

  @Test
  public void testFHIRSTU3Export() throws Exception {
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());

    FhirContext ctx = FhirContext.forDstu3();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);

    FhirValidator validator = ctx.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);

    ValidationResources validationResources = new ValidationResources();

    List<String> validationErrors = new ArrayList<String>();

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      int x = validationErrors.size();
      TestHelper.exportOff();
      Person person = generator.generatePerson(i);
      Config.set("exporter.fhir_stu3.export", "true");
      Config.set("exporter.fhir.use_shr_extensions", "true");
      FhirStu3.TRANSACTION_BUNDLE = person.random.nextBoolean();
      String fhirJson = FhirStu3.convertToFHIRJson(person, System.currentTimeMillis());
      // Check that the fhirJSON doesn't contain unresolved SNOMED-CT strings
      // (these should have been converted into URIs)
      if (fhirJson.contains("SNOMED-CT")) {
        validationErrors.add(
            "JSON contains unconverted references to 'SNOMED-CT' (should be URIs)");
      }
      // Now validate the resource...
      IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
      ValidationResult result = validator.validateWithResult(resource);
      if (!result.isSuccessful()) {
        // If the validation failed, let's crack open the Bundle and validate
        // each individual entry.resource to get context-sensitive error
        // messages...
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
        for (BundleEntryComponent entry : bundle.getEntry()) {
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
              } else if (emessage.getMessage().contains("@ MedicationRequest mps-1")) {
                /*
                 * The mps-1 invariant says MedicationRequest.requester.onBehalfOf can only be
                 * specified if MedicationRequest.requester.agent is practitioner or device.
                 * But the invariant is poorly written and does not correctly handle references
                 * starting with "urn:uuid"
                 */
                valid = true; // ignore this error
              } else if (emessage.getMessage().contains(
                  "per-1: If present, start SHALL have a lower value than end")) {
                /*
                 * The per-1 invariant does not account for daylight savings time... so, if the
                 * daylight savings switch happens between the start and end, the validation
                 * fails, even if it is valid.
                 */
                valid = true; // ignore this error
              }

              if (!valid) {
                System.out.println(parser.encodeResourceToString(entry.getResource()));
                System.out.println("ERROR: " + emessage.getMessage());
                validationErrors.add(emessage.getMessage());
              }
            }
          }
          // Check ExplanationOfBenefit Resources against BlueButton
          if (entry.getResource().fhirType().equals("ExplanationOfBenefit")) {
            ValidationResult bbResult = validationResources.validateSTU3(entry.getResource());

            for (SingleValidationMessage message : bbResult.getMessages()) {
              if (message.getSeverity() == ResultSeverityEnum.ERROR) {
                if (!(message.getMessage().contains(
                    "Element 'ExplanationOfBenefit.id': minimum required = 1, but only found 0")
                    || message.getMessage().contains("Could not verify slice for profile"))) {
                  // For some reason the validator is not detecting the IDs on the resources,
                  // even though they appear to be present while debugging and during normal
                  // operations.
                  System.out.println(message.getSeverity() + ": " + message.getMessage());
                  Assert.fail(message.getSeverity() + ": " + message.getMessage());
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
    assertTrue("Validation of exported FHIR bundle failed: "
        + String.join("|", validationErrors), validationErrors.size() == 0);
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

    person.history = new LinkedList<>();
    Provider mock = Mockito.mock(Provider.class);
    mock.uuid = "Mock-UUID";
    person.setProvider(EncounterType.AMBULATORY, mock);
    person.setProvider(EncounterType.WELLNESS, mock);
    person.setProvider(EncounterType.EMERGENCY, mock);
    person.setProvider(EncounterType.INPATIENT, mock);

    Long time = System.currentTimeMillis();
    long birthTime = time - Utilities.convertTime("years", 35);
    person.attributes.put(Person.BIRTHDATE, birthTime);

    Payer.loadNoInsurance();
    for (int i = 0; i < person.payerHistory.length; i++) {
      person.setPayerAtAge(i, Payer.noInsurance);
    }
    
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
    
    FhirContext ctx = FhirContext.forDstu3();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);
    String fhirJson = FhirStu3.convertToFHIRJson(person, System.currentTimeMillis());
    Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
    
    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Observation) {
        Observation obs = (Observation) entry.getResource();
        assertTrue(obs.getValue() instanceof SampledData);
        SampledData data = (SampledData) obs.getValue();
        assertEquals(3, (int) data.getDimensions());
      }
    }
  }
  
  @Test
  public void testMediaExport() throws Exception {

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
    Provider mock = Mockito.mock(Provider.class);
    mock.uuid = "Mock-UUID";
    person.setProvider(EncounterType.AMBULATORY, mock);
    person.setProvider(EncounterType.WELLNESS, mock);
    person.setProvider(EncounterType.EMERGENCY, mock);
    person.setProvider(EncounterType.INPATIENT, mock);

    Long time = System.currentTimeMillis();
    long birthTime = time - Utilities.convertTime("years", 35);
    person.attributes.put(Person.BIRTHDATE, birthTime);

    Payer.loadNoInsurance();
    for (int i = 0; i < person.payerHistory.length; i++) {
      person.setPayerAtAge(i, Payer.noInsurance);
    }
    
    Module module = TestHelper.getFixture("smith_physiology.json");
    
    State encounter = module.getState("SomeEncounter");
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);
    
    State physiology = module.getState("Simulate_CVS");
    assertTrue(physiology.process(person, time));
    person.history.add(physiology);
    
    State mediaState = module.getState("Media");
    assertTrue(mediaState.process(person, time));
    person.history.add(mediaState);
    
    FhirContext ctx = FhirContext.forDstu3();
    IParser parser = ctx.newJsonParser().setPrettyPrint(true);
    String fhirJson = FhirStu3.convertToFHIRJson(person, System.currentTimeMillis());
    Bundle bundle = parser.parseResource(Bundle.class, fhirJson);
    
    for (BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Media) {
        Media media = (Media) entry.getResource();
        assertEquals(400, media.getWidth());
        assertEquals(200, media.getHeight());
        assertTrue(Base64.isBase64(media.getContent().getDataElement().getValueAsString()));
      }
    }
  }

}
