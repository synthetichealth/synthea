package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.synthea.TestHelper.LOINC_URI;
import static org.mitre.synthea.TestHelper.SNOMED_URI;
import static org.mitre.synthea.TestHelper.getTxRecordingSource;
import static org.mitre.synthea.TestHelper.isHttpRecordingEnabled;
import static org.mitre.synthea.TestHelper.wiremockOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy.Series;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Report;
import org.mitre.synthea.world.geography.Location;
import org.springframework.web.client.RestTemplate;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

public class ValueSetCodeResolverTest {

  private Person person;
  private long time;
  private Encounter encounter;

  @Rule
  public WireMockRule mockTerminologyService = new WireMockRule(wiremockOptions()
      .usingFilesUnderDirectory("src/test/resources/wiremock/ValueSetCodeResolverTest"));

  /**
   * Prepare for each test.
   * 
   * @throws Exception
   *           on failure
   */
  @Before
  public void setUp() throws Exception {
    if (isHttpRecordingEnabled()) {
      WireMock.startRecording(getTxRecordingSource());
    }
    RandomCodeGenerator.setBaseUrl(mockTerminologyService.baseUrl() + "/fhir");
    RandomCodeGenerator.restTemplate = new RestTemplate();

    person = new Person(12345L);
    time = new SimpleDateFormat("yyyy-MM-dd").parse("2014-09-25").getTime();

    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Location location = new Location(Generator.DEFAULT_STATE, null);
    location.assignPoint(person, location.randomCityName(person));
    Provider.loadProviders(location, 1L);

    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Payer.loadPayers(new Location(Generator.DEFAULT_STATE, null));

    encounter = person.encounterStart(time, EncounterType.WELLNESS);
    String reasonCode = "275926002";
    String reasonDisplay = "Screening - health check";
    encounter.reason = new Code(SNOMED_URI, reasonCode, reasonDisplay);
    encounter.codes.add(encounter.reason);
  }

  @Test
  public void resolveReportValue() {
    Code observationType = new Code(LOINC_URI, "73985-4", "Exercise activity");
    Code observationValue = new Code(LOINC_URI, "LA11837-4", "Bicycling");
    observationValue.valueSet = "http://loinc.org/vs/LL734-5";
    encounter.addObservation(time, observationType.code, observationValue, observationType.display);

    Code reportType = new Code(SNOMED_URI, "371543004", "Comprehensive history and physical report");
    reportType.valueSet = SNOMED_URI + "?fhir_vs=<<371531000";
    person.record.report(time, reportType.code, 1);

    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    Person resolvedPerson = valueSetCodeResolver.resolve();

    assertEquals(1, resolvedPerson.record.encounters.size());
    Encounter resolvedEncounter = resolvedPerson.record.encounters.get(0);
    assertEquals(1, resolvedEncounter.reports.size());
    Report resolvedReport = resolvedEncounter.reports.get(0);
    assertEquals(1, resolvedReport.observations.size());
    Observation observation = resolvedReport.observations.get(0);
    Code actualObservationValue = (Code) observation.value;
    assertEquals(LOINC_URI, actualObservationValue.system);
    assertEquals("LA11834-1", actualObservationValue.code);
    assertEquals("Walking", actualObservationValue.display);
  }

  @Test
  public void resolveProcedureReason() {
    Code procedureType = new Code(SNOMED_URI, "236172004", "Nephroscopic lithotripsy of ureteric calculus");
    Code procedureReason = new Code(SNOMED_URI, "95570007", "Renal calculus");
    procedureReason.valueSet = SNOMED_URI + "?fhir_vs=ecl/<" + procedureReason.code;
    Procedure procedure = person.record.procedure(time, procedureType.display);
    procedure.reasons.add(procedureReason);

    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    Person resolvedPerson = valueSetCodeResolver.resolve();

    assertEquals(1, resolvedPerson.record.encounters.size());
    Encounter resolvedEncounter = resolvedPerson.record.encounters.get(0);
    assertEquals(1, resolvedEncounter.procedures.size());
    Procedure resolvedProcedure = resolvedEncounter.procedures.get(0);
    assertEquals(1, resolvedProcedure.reasons.size());
    Code actualProcedureReason = resolvedProcedure.reasons.get(0);
    assertEquals(SNOMED_URI, actualProcedureReason.system);
    assertEquals("699322002", actualProcedureReason.code);
    assertEquals("Matrix stone of kidney", actualProcedureReason.display);
  }

  @Test
  public void resolveMedicationCodes() {
    Code medicationCode = new Code(SNOMED_URI, "372756006", "Warfarin");
    Code reasonCode = new Code(SNOMED_URI, "128053003", "Deep venuous thrombosis");
    reasonCode.valueSet = SNOMED_URI + "?fhir_vs=ecl/<" + reasonCode.code;
    Code stopReason = new Code(SNOMED_URI, "401207004", "Medicine side effects present");
    stopReason.valueSet = SNOMED_URI + "?fhir_vs=ecl/<309298003";
    Medication medication = person.record.medicationStart(time, medicationCode.display, false);
    medication.codes.add(medicationCode);
    medication.reasons.add(reasonCode);
    person.record.medicationEnd(time, medicationCode.display, stopReason);

    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    Person resolvedPerson = valueSetCodeResolver.resolve();

    assertEquals(1, resolvedPerson.record.encounters.size());
    Encounter resolvedEncounter = resolvedPerson.record.encounters.get(0);
    assertEquals(1, resolvedEncounter.medications.size());
    Medication resolvedMedication = resolvedEncounter.medications.get(0);

    assertEquals(1, resolvedMedication.reasons.size());
    Code actualMedicationReason = resolvedMedication.reasons.get(0);
    assertEquals(SNOMED_URI, actualMedicationReason.system);
    assertEquals("709687000", actualMedicationReason.code);
    assertEquals("Chronic deep vein thrombosis of pelvic vein", actualMedicationReason.display);

    Code actualStopReason = resolvedMedication.stopReason;
    assertEquals(SNOMED_URI, actualStopReason.system);
    assertEquals("408343002", actualStopReason.code);
    assertEquals("Indication for each drug checked", actualStopReason.display);
  }

  @Test
  public void resolveCodesInCarePlan() {
    Code carePlanCode = new Code(SNOMED_URI, "734163000", "Care plan");
    Code reasonCode = new Code(SNOMED_URI, "90935002", "Haemophilia");
    reasonCode.valueSet = SNOMED_URI + "?fhir_vs=ecl/<64779008";
    Code stopReason = new Code(SNOMED_URI, "301857004", "Finding of body region");
    stopReason.valueSet = SNOMED_URI + "?fhir_vs=ecl/<" + stopReason.code;
    CarePlan carePlan = person.record.careplanStart(time, carePlanCode.display);
    carePlan.reasons.add(reasonCode);
    person.record.careplanEnd(time, carePlanCode.display, stopReason);

    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    Person resolvedPerson = valueSetCodeResolver.resolve();

    assertEquals(1, resolvedPerson.record.encounters.size());
    Encounter resolvedEncounter = resolvedPerson.record.encounters.get(0);
    assertEquals(1, resolvedEncounter.careplans.size());
    CarePlan resolvedCarePlan = resolvedEncounter.careplans.get(0);

    assertEquals(1, resolvedCarePlan.reasons.size());
    Code actualCarePlanReason = resolvedCarePlan.reasons.get(0);
    assertEquals(SNOMED_URI, actualCarePlanReason.system);
    assertEquals("773422002", actualCarePlanReason.code);
    assertEquals("East Texas bleeding disorder", actualCarePlanReason.display);

    Code actualStopReason = resolvedCarePlan.stopReason;
    assertEquals(SNOMED_URI, actualStopReason.system);
    assertEquals("246995007", actualStopReason.code);
    assertEquals("Pseudo-hypopyon", actualStopReason.display);
  }

  @Test
  public void resolveCodesInImagingStudy() throws Exception {
    // We load the imaging study from a module fixture, as there doesn't seem to be
    // a way to
    // instantiate it programmatically.
    Module module = TestHelper.getFixture("imaging_study_with_valueset.json");
    person.history = new ArrayList<>();
    State encounterState = module.getState("ED_Visit");
    assertTrue(encounterState.process(person, time));
    person.history.add(encounterState);
    State mri = module.getState("Knee_MRI");
    assertTrue(mri.process(person, time));

    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    Person resolvedPerson = valueSetCodeResolver.resolve();

    assertEquals(2, resolvedPerson.record.encounters.size());
    Encounter resolvedEncounter = resolvedPerson.record.encounters.get(1);
    assertEquals(1, resolvedEncounter.imagingStudies.size());
    ImagingStudy resolvedImagingStudy = resolvedEncounter.imagingStudies.get(0);

    assertEquals(1, resolvedImagingStudy.series.size());
    Series series = resolvedImagingStudy.series.get(0);
    assertEquals(SNOMED_URI, series.bodySite.system);
    assertEquals("762879008", series.bodySite.code);
    assertEquals("Structure of right common peroneal nerve in popliteal region",
        series.bodySite.display);

    // Modality and SOP class are not really good candidates for ValueSet-based
    // selection, so we do
    // not currently have a sensible test case for these.
  }

  @Test
  public void handlesNullHealthRecord() {
    person.record = null;
    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();
  }

  @Test
  public void handlesNullEncounter() {
    person.record.encounters.set(0, null);
    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();
  }

  @Test
  public void handlesNullCodeInObservation() {
    encounter.addObservation(time, "foo", null, "bar");
    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();
  }

  @Test
  public void handlesNullCodeInCarePlan() {
    CarePlan carePlan = person.record.careplanStart(time, "foo");
    carePlan.reasons = null;
    carePlan.activities = null;
    person.record.careplanEnd(time, "foo", null);

    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();
  }

  @Test
  public void handlesNullCodesInImagingStudy() throws Exception {
    // We load the imaging study from a module fixture, as there doesn't seem to be
    // a way to
    // instantiate it programmatically.
    Module module = TestHelper.getFixture("imaging_study_with_valueset.json");
    person.history = new ArrayList<>();
    State encounterState = module.getState("ED_Visit");
    assertTrue(encounterState.process(person, time));
    person.history.add(encounterState);
    State mri = module.getState("Knee_MRI");
    assertTrue(mri.process(person, time));

    Encounter imagingEncounter = person.record.encounters.get(1);
    imagingEncounter.imagingStudies.get(0).series.get(0).instances.set(0, null);
    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();

    imagingEncounter.imagingStudies.get(0).series.set(0, null);
    valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();
  }

  @Test
  public void handlesNullAttribute() {
    person.attributes = null;
    ValueSetCodeResolver valueSetCodeResolver = new ValueSetCodeResolver(person);
    valueSetCodeResolver.resolve();
  }

  /**
   * Clean up after each test.
   */
  @After
  public void tearDown() {
    if (isHttpRecordingEnabled()) {
      WireMock.stopRecording();
    }
    RandomCodeGenerator.codeListCache.clear();
  }
}