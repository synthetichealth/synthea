package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

public class StateTest {

  private Person person;
  private long time;

  /**
   * Setup State tests.
   * @throws IOException On File IO errors.
   */
  @Before
  public void setup() throws IOException {
    person = new Person(0L);

    person.history = new LinkedList<>();
    person.setAmbulatoryProvider(Mockito.mock(Provider.class));
    person.setEmergencyProvider(Mockito.mock(Provider.class));
    person.setInpatientProvider(Mockito.mock(Provider.class));

    long birthTime = time - Utilities.convertTime("years", 35);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.events.create(birthTime, Event.BIRTH, "Generator.run", true);

    time = System.currentTimeMillis();
  }

  private static Module getModule(String name) {
    try {
      Path modulesFolder = Paths.get("src/test/resources/generic");
      Path logicFile = modulesFolder.resolve(name);
      JsonReader reader = new JsonReader(new FileReader(logicFile.toString()));
      JsonObject jsonModule = new JsonParser().parse(reader).getAsJsonObject();
      reader.close();

      return new Module(jsonModule, false);
    } catch (Exception e) {
      // if anything breaks, we can't fix it. throw a RuntimeException for simplicity
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private void simulateWellnessEncounter(Module module) {
    person.attributes.put(EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + module.name, true);
  }

  @Test
  public void initial_always_passes() {
    Module module = getModule("initial_to_terminal.json");
    State initial = module.getState("Initial");
    assertTrue(initial.process(person, time));
  }

  @Test
  public void terminal_never_passes() {
    Module module = getModule("initial_to_terminal.json");
    State terminal = module.getState("Terminal");
    assertFalse(terminal.process(person, time));
    assertFalse(terminal.process(person, time + TimeUnit.DAYS.toMillis(7)));
  }

  @Test(expected = RuntimeException.class)
  public void stateMustHaveTransition() {
    getModule("state_without_transition.json");
  }

  @Test
  public void guard_passes_when_condition_is_met() {
    Module module = getModule("guard.json");
    State guard = module.getState("Gender_Guard");
    person.attributes.put(Person.GENDER, "F");
    assertTrue(guard.process(person, time));
  }

  @Test
  public void guard_blocks_when_condition_isnt_met() {
    Module module = getModule("guard.json");
    State guard = module.getState("Gender_Guard");
    person.attributes.put(Person.GENDER, "M");
    assertFalse(guard.process(person, time));
  }

  @Test
  public void counter() {
    Module module = getModule("counter.json");

    assertTrue(person.attributes.get("loop_index") == null);

    State counter = module.getState("Counter");
    assertTrue(counter.process(person, time));
    assertEquals(1, person.attributes.get("loop_index"));

    assertTrue(counter.process(person, time));
    assertEquals(2, person.attributes.get("loop_index"));

    assertTrue(counter.process(person, time));
    assertEquals(3, person.attributes.get("loop_index"));

    State decrement = module.getState("Counter_Decrement");
    assertTrue(decrement.process(person, time));
    assertEquals(2, person.attributes.get("loop_index"));

    assertTrue(decrement.process(person, time));
    assertEquals(1, person.attributes.get("loop_index"));
  }

  @Test
  public void condition_onset() {
    // Setup a mock to track calls to the patient record
    // In this case, the record shouldn't be called at all
    person.record = Mockito.mock(HealthRecord.class);

    Module module = getModule("condition_onset.json");
    State condition = module.getState("Diabetes");
    // Should pass through this state immediately without calling the record
    assertTrue(condition.process(person, time));

    verifyZeroInteractions(person.record);
  }

  @Test
  public void condition_onset_diagnosed_by_target_encounter() { 
    Module module = getModule("condition_onset.json");
    
    State condition = module.getState("Diabetes");
    // Should pass through this state immediately without calling the record
    assertTrue(condition.process(person, time));
    person.history.add(0, condition);
    
    // The encounter comes next (and add it to history);
    State encounter = module.getState("ED_Visit");

    assertTrue(encounter.process(person, time));
    person.history.add(0, encounter);

    assertEquals(1, person.record.encounters.size());
    Encounter enc = person.record.encounters.get(0);
    Code code = enc.codes.get(0);
    assertEquals("50849002", code.code);
    assertEquals("Emergency Room Admission", code.display);
    assertEquals(1, enc.conditions.size());
    code = enc.conditions.get(0).codes.get(0);
    assertEquals("73211009", code.code);
    assertEquals("Diabetes mellitus", code.display);
  }

  @Test
  public void condition_onset_during_encounter() {
    Module module = getModule("condition_onset.json");
    // The encounter comes first (and add it to history);
    State encounter = module.getState("ED_Visit");

    assertTrue(encounter.process(person, time));
    person.history.add(0, encounter);

    // Then appendicitis is diagnosed
    State appendicitis = module.getState("Appendicitis");
    assertTrue(appendicitis.process(person, time));

    assertEquals(1, person.record.encounters.size());
    Encounter enc = person.record.encounters.get(0);
    Code code = enc.codes.get(0);
    assertEquals("50849002", code.code);
    assertEquals("Emergency Room Admission", code.display);
    assertEquals(1, enc.conditions.size());
    code = enc.conditions.get(0).codes.get(0);
    assertEquals("47693006", code.code);
    assertEquals("Rupture of appendix", code.display);

  }

  @Test
  public void allergy_onset() {
    // Setup a mock to track calls to the patient record
    // In this case, the record shouldn't be called at all
    person.record = Mockito.mock(HealthRecord.class);

    Module module = getModule("allergies.json");
    State allergy = module.getState("Allergy_to_Eggs");
    // Should pass through this state immediately without calling the record
    assertTrue(allergy.process(person, time));

    verifyZeroInteractions(person.record);
  }

  @Test
  public void delay_passes_after_exact_time() {
    Module module = getModule("delay.json");

    // Seconds
    State delay = module.getState("2_Second_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000));
    assertTrue(delay.process(person, time + 2L * 1000));
    assertTrue(delay.process(person, time + 3L * 1000));

    // Minutes
    delay = module.getState("2_Minute_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60));
    assertTrue(delay.process(person, time + 2L * 1000 * 60));
    assertTrue(delay.process(person, time + 3L * 1000 * 60));

    // Hours
    delay = module.getState("2_Hour_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60));
    assertTrue(delay.process(person, time + 2L * 1000 * 60 * 60));
    assertTrue(delay.process(person, time + 3L * 1000 * 60 * 60));

    // Days
    delay = module.getState("2_Day_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24));
    assertTrue(delay.process(person, time + 2L * 1000 * 60 * 60 * 24));
    assertTrue(delay.process(person, time + 3L * 1000 * 60 * 60 * 24));

    // Weeks
    delay = module.getState("2_Week_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24 * 7));
    assertTrue(delay.process(person, time + 2L * 1000 * 60 * 60 * 24 * 7));
    assertTrue(delay.process(person, time + 3L * 1000 * 60 * 60 * 24 * 7));

    // Months
    // NOTE: months + years are not "well-defined" like the smaller units of time
    // so these may be flaky around things like leap years & DST changes
    delay = module.getState("2_Month_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24 * 30));
    assertTrue(delay.process(person, time + 2L * 1000 * 60 * 60 * 24 * 30));
    assertTrue(delay.process(person, time + 3L * 1000 * 60 * 60 * 24 * 30));

    // Years
    delay = module.getState("2_Year_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24 * 365));
    assertTrue(delay.process(person, time + 2L * 1000 * 60 * 60 * 24 * 365));
    assertTrue(delay.process(person, time + 3L * 1000 * 60 * 60 * 24 * 365));
  }

  @Test
  public void delay_passes_after_time_range() {
    Module module = getModule("delay.json");

    // Seconds
    State delay = module.getState("2_To_10_Second_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000));
    assertFalse(delay.process(person, time + 2L * 1000));
    assertTrue(delay.process(person, time + 10L * 1000));

    // Minutes
    delay = module.getState("2_To_10_Minute_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60));
    assertFalse(delay.process(person, time + 2L * 1000 * 60));
    assertTrue(delay.process(person, time + 10L * 1000 * 60));

    // Hours
    delay = module.getState("2_To_10_Hour_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60));
    assertFalse(delay.process(person, time + 2L * 1000 * 60 * 60));
    assertTrue(delay.process(person, time + 10L * 1000 * 60 * 60));

    // Days
    delay = module.getState("2_To_10_Day_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24));
    assertFalse(delay.process(person, time + 2L * 1000 * 60 * 60 * 24));
    assertTrue(delay.process(person, time + 10L * 1000 * 60 * 60 * 24));

    // Weeks
    delay = module.getState("2_To_10_Week_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24 * 7));
    assertFalse(delay.process(person, time + 2L * 1000 * 60 * 60 * 24 * 7));
    assertTrue(delay.process(person, time + 10L * 1000 * 60 * 60 * 24 * 7));

    // Months
    delay = module.getState("2_To_10_Month_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24 * 30));
    assertFalse(delay.process(person, time + 2L * 1000 * 60 * 60 * 24 * 30));
    assertTrue(delay.process(person, time + 10L * 1000 * 60 * 60 * 24 * 30));

    // Years
    delay = module.getState("2_To_10_Year_Delay");
    delay.entered = time;
    assertFalse(delay.process(person, time));
    assertFalse(delay.process(person, time + 1L * 1000 * 60 * 60 * 24 * 365));
    assertFalse(delay.process(person, time + 2L * 1000 * 60 * 60 * 24 * 365));
    assertTrue(delay.process(person, time + 10L * 1000 * 60 * 60 * 24 * 365));
  }

  @Test
  public void vitalsign() {
    // Setup a mock to track calls to the patient record
    // In this case, the record shouldn't be called at all
    person.record = Mockito.mock(HealthRecord.class);

    Module module = getModule("vitalsign_observation.json");

    State vitalsign = module.getState("VitalSign").clone();
    assertTrue(vitalsign.process(person, time));

    assertEquals(120.0, person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE), 0.0);

    verifyZeroInteractions(person.record);
  }

  @Test
  public void symptoms() {
    Module module = getModule("symptom.json");

    State symptom1 = module.getState("SymptomOnset");
    assertTrue(symptom1.process(person, time));
    int symptomValue = person.getSymptom("Chest Pain");
    assertTrue(1 <= symptomValue && symptomValue <= 10);

    State symptom2 = module.getState("SymptomWorsen");
    assertTrue(symptom2.process(person, time));
    assertEquals(96, person.getSymptom("Chest Pain"));
  }

  @Test
  public void setAttribute_with_value() {
    Module module = getModule("set_attribute.json");

    person.attributes.remove("Current Opioid Prescription");
    State set1 = module.getState("Set_Attribute_1");
    assertTrue(set1.process(person, time));

    assertEquals("Vicodin", person.attributes.get("Current Opioid Prescription"));
  }

  @Test
  public void setAttribute_without_value() {
    Module module = getModule("set_attribute.json");

    person.attributes.put("Current Opioid Prescription", "Vicodin");
    State set2 = module.getState("Set_Attribute_2");
    assertTrue(set2.process(person, time));

    assertNull(person.attributes.get("Current Opioid Prescription"));
  }

  @Test
  public void procedure_assigns_entity_attribute() {
    person.attributes.remove("Most Recent Surgery");
    Module module = getModule("procedure.json");
    State encounter = module.getState("Inpatient_Encounter");
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    State appendectomy = module.getState("Appendectomy");
    appendectomy.process(person, time);

    HealthRecord.Procedure procedure = (HealthRecord.Procedure) person.attributes
        .get("Most Recent Surgery");

    assertEquals(time, procedure.start);

    Code code = procedure.codes.get(0);

    assertEquals("6025007", code.code);
    assertEquals("Laparoscopic appendectomy", code.display);
  }

  @Test
  public void procedure_during_encounter() {
    Module module = getModule("procedure.json");

    // The encounter comes first (and add it to history);
    State encounter = module.getState("Inpatient_Encounter");

    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Then have the appendectomy
    State appendectomy = module.getState("Appendectomy");
    appendectomy.entered = time;
    assertTrue(appendectomy.process(person, time));

    HealthRecord.Procedure proc = person.record.encounters.get(0).procedures.get(0);
    Code code = proc.codes.get(0);

    assertEquals("6025007", code.code);
    assertEquals("Laparoscopic appendectomy", code.display);
    assertEquals(time, proc.start);
    assertEquals(time + Utilities.convertTime("minutes", 45), proc.stop);
  }

  @Test
  public void observation() {
    Module module = getModule("vitalsign_observation.json");

    State vitalsign = module.getState("VitalSign");
    assertTrue(vitalsign.process(person, time));
    person.history.add(vitalsign);

    State encounter = module.getState("SomeEncounter");
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    State obs = module.getState("SomeObservation");
    assertTrue(obs.process(person, time));

    HealthRecord.Observation observation = person.record.encounters.get(0).observations.get(0);
    assertEquals(120.0, observation.value);
    assertEquals("vital-signs", observation.category);
    assertEquals("mmHg", observation.unit);

    Code code = observation.codes.get(0);
    assertEquals("8480-6", code.code);
    assertEquals("Systolic Blood Pressure", code.display);
  }

  @Test
  public void imaging_study_during_encounter() {
    Module module = getModule("imaging_study.json");

    // First, onset the injury
    State kneeInjury = module.getState("Knee_Injury");
    assertTrue(kneeInjury.process(person, time));
    person.history.add(kneeInjury);

    // An ImagingStudy must occur during an Encounter
    State encounterState = module.getState("ED_Visit");
    assertTrue(encounterState.process(person, time));
    person.history.add(encounterState);

    // Run the imaging study
    State mri = module.getState("Knee_MRI");
    assertTrue(mri.process(person, time));

    // Verify that the ImagingStudy was added to the record
    HealthRecord.Encounter encounter = person.record.encounters.get(0);

    HealthRecord.ImagingStudy study = encounter.imagingStudies.get(0);
    assertEquals(time, study.start);
    assertEquals(1, study.series.size());

    HealthRecord.ImagingStudy.Series series = study.series.get(0);
    assertEquals(1, series.instances.size());

    Code bodySite = series.bodySite;
    assertEquals("SNOMED-CT", bodySite.system);
    assertEquals("6757004", bodySite.code);
    assertEquals("Right knee", bodySite.display);

    Code modality = series.modality;
    assertEquals("DICOM-DCM", modality.system);
    assertEquals("MR", modality.code);
    assertEquals("Magnetic Resonance", modality.display);

    HealthRecord.ImagingStudy.Instance instance = series.instances.get(0);
    assertEquals("Image of right knee", instance.title);

    Code sopClass = instance.sopClass;
    assertEquals("DICOM-SOP", sopClass.system);
    assertEquals("1.2.840.10008.5.1.4.1.1.4", sopClass.code);
    assertEquals("MR Image Storage", sopClass.display);

    // Verify that the equivalent Procedure was also added to the patient's record
    HealthRecord.Procedure procedure = encounter.procedures.get(0);
    assertEquals(time, procedure.start);

    Code procCode = procedure.codes.get(0);
    assertEquals("2491000087104", procCode.code);
    assertEquals("Magnetic resonance imaging of right knee", procCode.display);
    assertEquals("SNOMED-CT", procCode.system);
  }

  @Test
  public void wellness_encounter() {
    Module module = getModule("encounter.json");
    State encounter = module.getState("Annual_Physical");

    // shouldn't pass through this state until a wellness encounter happens externally
    assertFalse(encounter.process(person, time));

    time = time + Utilities.convertTime("months", 6);
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("weeks", 2);

    simulateWellnessEncounter(module);

    // Now we should pass through
    assertTrue(encounter.process(person, time));
  }

  @Test
  public void wellness_encounter_diagnoses_condition() {
    Module module = getModule("encounter.json");
    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Now process the encounter, waiting until it actually happens
    State encounter = module.getState("Annual_Physical_2");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);

    simulateWellnessEncounter(module);

    assertTrue(encounter.process(person, time));

    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
    assertEquals(time, condition.start);
    assertEquals(0L, condition.stop);

    Code code = condition.codes.get(0);
    assertEquals("73211009", code.code);
    assertEquals("Diabetes Mellitus", code.display);
  }

  @Test
  public void ed_visit_encounter() {
    Module module = getModule("encounter.json");
    // Non-wellness encounters happen immediately

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    State encounter = module.getState("ED_Visit");
    assertTrue(encounter.process(person, time));
    // Verify that the Encounter was added to the record
    HealthRecord.Encounter enc = person.record.encounters.get(0);
    assertEquals(time, enc.start);
    assertEquals(time + TimeUnit.MINUTES.toMillis(60), enc.stop);

    Code code = enc.codes.get(0);
    assertEquals("50849002", code.code);
    assertEquals("Emergency Room Admission", code.display);

  }

  @Test
  public void encounter_with_attribute_reason() {
    Module module = getModule("encounter.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Non-wellness encounters happen immediately
    State encounter = module.getState("ED_Visit_AttributeReason");
    assertTrue(encounter.process(person, time));
    // Verify that the Encounter was added to the record
    HealthRecord.Encounter enc = person.record.encounters.get(0);
    assertEquals(time, enc.start);
    assertEquals(time + TimeUnit.MINUTES.toMillis(60), enc.stop);
    assertEquals("73211009", enc.reason.code);
    assertEquals("Diabetes Mellitus", enc.reason.display);

    Code code = enc.codes.get(0);
    assertEquals("50849002", code.code);
    assertEquals("Emergency Room Admission", code.display);
  }

  @Test
  public void allergy_onset_during_encounter() {
    Module module = getModule("allergies.json");
    State allergyState = module.getState("Allergy_to_Eggs");
    // Should pass through this state immediately without calling the record
    assertTrue(allergyState.process(person, time));
    person.history.add(allergyState);

    State encounter = module.getState("Dr_Visit");
    assertTrue(encounter.process(person, time));

    HealthRecord.Entry allergy = person.record.encounters.get(0).allergies.get(0);
    assertEquals(time, allergy.start);
    assertEquals(0L, allergy.stop);

    Code code = allergy.codes.get(0);
    assertEquals("91930004", code.code);
    assertEquals("Allergy to eggs", code.display);
  }

  @Test
  public void allergy_end_by_state_name() {
    Module module = getModule("allergies.json");
    State allergyState = module.getState("Allergy_to_Eggs").clone();
    // Should pass through this state immediately without calling the record
    assertTrue(allergyState.process(person, time));
    person.history.add(allergyState);

    State encounter = module.getState("Dr_Visit").clone();
    assertTrue(encounter.process(person, time));

    // Now process the end of the prescription
    State medEnd = module.getState("Allergy_Ends").clone();
    assertTrue(medEnd.process(person, time));

    HealthRecord.Entry allergy = person.record.encounters.get(0).allergies.get(0);
    assertEquals(time, allergy.start);
    assertEquals(time, allergy.stop);

    Code code = allergy.codes.get(0);
    assertEquals("91930004", code.code);
    assertEquals("Allergy to eggs", code.display);
  }

  @Test
  public void condition_end_by_entity_attribute() {
    Module module = getModule("condition_end.json");

    // First, onset the condition
    State condition1 = module.getState("Condition1_Start");
    assertTrue(condition1.process(person, time));
    person.history.add(condition1);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("DiagnosisEncounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    HealthRecord.Entry attributeEntry = (HealthRecord.Entry) person.attributes
        .get("Drug Use Behavior");

    // Now process the end of the condition
    State conEnd = module.getState("Condition1_End");
    assertTrue(conEnd.process(person, time));

    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
    assertEquals(time, condition.start);
    assertEquals(time, condition.stop);
    assertEquals(attributeEntry, condition);

    Code code = condition.codes.get(0);
    assertEquals("228380004", code.code);
    assertEquals("Chases the dragon (finding)", code.display);
  }

  @Test
  public void condition_end_by_condition_onset() {
    Module module = getModule("condition_end.json");

    // First, onset the condition
    State condition2 = module.getState("Condition2_Start");
    assertTrue(condition2.process(person, time));
    person.history.add(condition2);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("DiagnosisEncounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    // Simulate the wellness encounter by calling perform_encounter

    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the end of the condition
    State conEnd = module.getState("Condition2_End");
    assertTrue(conEnd.process(person, time));

    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
    assertEquals(time, condition.start);
    assertEquals(time, condition.stop);

    Code code = condition.codes.get(0);
    assertEquals("6142004", code.code);
    assertEquals("Influenza", code.display);
  }

  @Test
  public void condition_end_by_code() {
    Module module = getModule("condition_end.json");

    // First, onset the Diabetes!
    State condition3 = module.getState("Condition3_Start");
    assertTrue(condition3.process(person, time));
    person.history.add(condition3);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("DiagnosisEncounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    // Simulate the wellness encounter by calling perform_encounter
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the end of the condition
    State conEnd = module.getState("Condition3_End");
    assertTrue(conEnd.process(person, time));

    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
    assertEquals(time, condition.start);
    assertEquals(time, condition.stop);

    Code code = condition.codes.get(0);
    assertEquals("73211009", code.code);
    assertEquals("Diabetes mellitus", code.display);
  }

  @Test
  public void medication_order_during_wellness_encounter() {
    Module module = getModule("medication_order.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the prescription
    State med = module.getState("Metformin");
    assertTrue(med.process(person, time));

    // Verify that Metformin was added to the record
    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);
    assertEquals(0L, medication.stop);

    Code code = medication.codes.get(0);
    assertEquals("860975", code.code);
    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);
  }

  @Test
  public void medication_order_with_dosage() {
    Module module = getModule("medication_order.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the prescription
    State med = module.getState("Metformin_With_Dosage");
    assertTrue(med.process(person, time));

    // Verify that Metformin was added to the record, including dosage information
    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);
    assertEquals(0L, medication.stop);
    // TODO: verify details. ideally these should not just be a jsonobject
    Code code = medication.codes.get(0);
    assertEquals("860975", code.code);
    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);
  }

  @Test
  public void medication_order_as_needed() {
    Module module = getModule("medication_order.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);

    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the prescription
    State med = module.getState("Tylenol_As_Needed");
    assertTrue(med.process(person, time));

    // Verify that tylenol was added to the record
    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);
    assertEquals(0L, medication.stop);
    // TODO: verify details. ideally these should not just be a jsonobject

    Code code = medication.codes.get(0);
    assertEquals("123456", code.code);
    assertEquals("Acetaminophen 325mg [Tylenol]", code.display);
  }

  @Test
  public void medication_order_assigns_entity_attribute() {
    person.attributes.remove("Diabetes Medication");
    Module module = getModule("medication_order.json");
    State encounter = module.getState("Wellness_Encounter");
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    State med = module.getState("Metformin");
    assertTrue(med.process(person, time));

    HealthRecord.Medication medication = (HealthRecord.Medication) person.attributes
        .get("Diabetes Medication");
    assertEquals(time, medication.start);

    Code code = medication.codes.get(0);
    assertEquals("860975", code.code);
    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);
  }

  @Test
  public void medication_end_by_entity_attribute() {
    Module module = getModule("medication_end.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);

    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the prescription
    State med = module.getState("Insulin_Start");
    assertTrue(med.process(person, time));

    person.history.add(med);

    // Now process the end of the prescription
    State medEnd = module.getState("Insulin_End");
    assertTrue(medEnd.process(person, time));

    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);
    assertEquals(time, medication.stop);

    Code code = medication.codes.get(0);
    assertEquals("575679", code.code);
    assertEquals("Insulin, Aspart, Human 100 UNT/ML [NovoLOG]", code.display);
  }

  @Test
  public void medication_end_by_medication_order() {
    Module module = getModule("medication_end.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the prescription
    State med = module.getState("Bromocriptine_Start");
    assertTrue(med.process(person, time));

    person.history.add(med);

    // Now process the end of the prescription
    State medEnd = module.getState("Bromocriptine_End");
    assertTrue(medEnd.process(person, time));

    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);
    assertEquals(time, medication.stop);

    Code code = medication.codes.get(0);
    assertEquals("563894", code.code);
    assertEquals("Bromocriptine 5 MG [Parlodel]", code.display);
  }

  @Test
  public void medication_end_by_code() {
    Module module = getModule("medication_end.json");

    // First, onset the Diabetes!
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the prescription
    State med = module.getState("Metformin_Start");
    assertTrue(med.process(person, time));

    person.history.add(med);

    // Now process the end of the prescription
    State medEnd = module.getState("Metformin_End");
    assertTrue(medEnd.process(person, time));

    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);
    assertEquals(time, medication.stop);

    Code code = medication.codes.get(0);
    assertEquals("860975", code.code);
    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);
  }

  @Test
  public void careplan_start() {
    Module module = getModule("careplan_start.json");

    // First onset diabetes
    State diabetes = module.getState("Diabetes");
    assertTrue(diabetes.process(person, time));
    person.history.add(diabetes);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the careplan
    State plan = module.getState("Diabetes_Self_Management");
    assertTrue(plan.process(person, time));
    person.history.add(plan);

    // Verify that the careplan was added to the record
    HealthRecord.CarePlan cp = person.record.encounters.get(0).careplans.get(0);
    assertEquals(time, cp.start);
    assertEquals(0L, cp.stop);

    Code code = cp.codes.get(0);
    assertEquals("698360004", code.code);
    assertEquals("Diabetes self management plan", code.display);

    assertEquals(1, cp.activities.size());
    Code activity = cp.activities.iterator().next();
    assertEquals("160670007", activity.code);
    assertEquals("Diabetic diet", activity.display);
  }

  @Test
  public void careplan_assigns_entity_attribute() {
    person.attributes.remove("Diabetes_CarePlan");
    Module module = getModule("careplan_start.json");
    State encounter = module.getState("Wellness_Encounter");
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    State plan = module.getState("Diabetes_Self_Management");
    assertTrue(plan.process(person, time));

    HealthRecord.CarePlan cp = (HealthRecord.CarePlan) person.attributes.get("Diabetes_CarePlan");
    assertEquals(time, cp.start);
    assertEquals(0L, cp.stop);

    Code code = cp.codes.get(0);
    assertEquals("698360004", code.code);
    assertEquals("Diabetes self management plan", code.display);
  }

  @Test
  public void careplan_end_by_entity_attribute() {
    Module module = getModule("careplan_end.json");

    // First, onset the condition
    State condition = module.getState("The_Condition");
    assertTrue(condition.process(person, time));
    person.history.add(condition);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the careplan
    State plan = module.getState("CarePlan1_Start");
    // have to use `run` not `process` here because the entity
    // attribute stuff happens in `run`
    assertTrue(plan.process(person, time));
    person.history.add(plan);

    HealthRecord.CarePlan entityAttribute = (HealthRecord.CarePlan) person.attributes
        .get("Diabetes_CarePlan");

    // Now process the end of the careplan
    State planEnd = module.getState("CarePlan1_End");
    assertTrue(planEnd.process(person, time));
    person.history.add(planEnd);

    HealthRecord.CarePlan cp = person.record.encounters.get(0).careplans.get(0);
    assertEquals(time, cp.start);
    assertEquals(time, cp.stop);
    assertEquals(cp, entityAttribute);

    Code code = cp.codes.get(0);
    assertEquals("698360004", code.code);
    assertEquals("Diabetes self management plan", code.display);

    assertEquals(1, cp.activities.size());
    Code activity = cp.activities.iterator().next();
    assertEquals("160670007", activity.code);
    assertEquals("Diabetic diet", activity.display);
  }

  @Test
  public void careplan_end_by_code() {
    Module module = getModule("careplan_end.json");

    // First, onset the condition
    State condition = module.getState("The_Condition");
    assertTrue(condition.process(person, time));
    person.history.add(condition);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    person.history.add(encounter);

    // Now process the careplan
    State plan = module.getState("CarePlan2_Start");
    assertTrue(plan.process(person, time));
    person.history.add(plan);

    // Now process the end of the careplan
    State planEnd = module.getState("CarePlan2_End");
    assertTrue(planEnd.process(person, time));
    person.history.add(planEnd);

    HealthRecord.CarePlan cp = person.record.encounters.get(0).careplans.get(0);
    assertEquals(time, cp.start);
    assertEquals(time, cp.stop);

    Code code = cp.codes.get(0);
    assertEquals("698358001", code.code);
    assertEquals("Angina self management plan", code.display);

    assertEquals(2, cp.activities.size());
    Iterator<Code> itr = cp.activities.iterator();
    // note that activities is a LinkedHashSet so should preserve insertion order
    Code activity = itr.next();
    assertEquals("229065009", activity.code);
    assertEquals("Exercise therapy", activity.display);

    activity = itr.next();
    assertEquals("226234005", activity.code);
    assertEquals("Healthy diet", activity.display);
  }

  @Test
  public void careplan_end_by_careplan() {
    Module module = getModule("careplan_end.json");

    // First, onset the condition
    State condition = module.getState("The_Condition");
    assertTrue(condition.process(person, time));
    person.history.add(condition);

    // Process the wellness encounter state, which will wait for a wellness encounter
    State encounter = module.getState("Wellness_Encounter");
    assertFalse(encounter.process(person, time));
    time = time + Utilities.convertTime("months", 6);
    simulateWellnessEncounter(module);
    assertTrue(encounter.process(person, time));
    person.history.add(encounter);

    // Now process the careplan
    State plan = module.getState("CarePlan3_Start");
    assertTrue(plan.process(person, time));
    person.history.add(plan);

    // Now process the end of the careplan
    State planEnd = module.getState("CarePlan3_End");
    assertTrue(planEnd.process(person, time));
    person.history.add(planEnd);

    HealthRecord.CarePlan cp = person.record.encounters.get(0).careplans.get(0);
    assertEquals(time, cp.start);
    assertEquals(time, cp.stop);

    Code code = cp.codes.get(0);
    assertEquals("408907000", code.code);
    assertEquals("Immunological care management", code.display);

    assertEquals(1, cp.activities.size());
    Code activity = cp.activities.iterator().next();
    assertEquals("764101000000108", activity.code);
    assertEquals("Allergen immunotherapy drugs band 1", activity.display);
  }

  @Test
  public void death() {
    Module module = getModule("death.json");
    State death = module.getState("Death");
    assertTrue(person.alive(time));
    assertTrue(death.process(person, time));

    // Patient shouldn't be alive anymore
    assertFalse(person.alive(time));

    // Verify that death was added to the record
    assertEquals(time, (long) person.record.death);
  }

  @Test
  public void future_death() {
    Module module = getModule("death_life_expectancy.json");
    module.process(person, time);
    module.process(person, time + Utilities.convertTime("days", 7));

    assertTrue(person.alive(time + Utilities.convertTime("days", 7)));
    assertTrue((boolean) person.attributes.get("processing"));
    assertNull(person.attributes.get("still_processing"));

    module.process(person, time + Utilities.convertTime("days", 14));
    assertTrue((boolean) person.attributes.get("still_processing"));

    module.process(person, time + Utilities.convertTime("months", 6));
    assertFalse(person.alive(time + Utilities.convertTime("months", 6)));
  }

  @Test
  public void cause_of_death_code() {
    Module module = getModule("death_reason.json");

    // First, onset the Diabetes!
    State condition = module.getState("OnsetDiabetes");
    assertTrue(condition.process(person, time));
    person.history.add(condition);

    // Now process the end of the condition
    State death = module.getState("Death_by_Code");
    assertTrue(death.process(person, time));

    assertFalse(person.alive(time));
  }

  @Test
  public void cause_of_death_conditionOnset() {
    Module module = getModule("death_reason.json");

    // First, onset the Diabetes!
    State condition = module.getState("OnsetDiabetes");
    assertTrue(condition.process(person, time));
    person.history.add(condition);

    // Now process the end of the condition
    State death = module.getState("Death_by_ConditionOnset");
    assertTrue(death.process(person, time));

    assertFalse(person.alive(time));
  }

  @Test
  public void cause_of_death_attribute() {
    Module module = getModule("death_reason.json");

    // First, onset the Diabetes!
    State condition = module.getState("OnsetDiabetes");
    assertTrue(condition.process(person, time));
    person.history.add(condition);

    // Now process the end of the condition
    State death = module.getState("Death_by_Attribute");
    assertTrue(death.process(person, time));

    assertFalse(person.alive(time));
  }

  @Test
  public void testDelayRewindTime() {
    // Synthea is currently run in 7-day increments. If a delay falls between increments, then the
    // delay and subsequent states must be run at the delay expiration time -- not at the current
    // cycle time.

    // Setup the context
    Module module = getModule("delay_time_travel.json");

    // Run number one should stop at the delay
    module.process(person, time);
    assertEquals("2_Day_Delay", person.history.get(0).name);

    // Run number two should go all the way to Terminal, but should process Encounter and Death
    // along the way
    // Run number 2: 7 days after run number 1
    module.process(person, time + days(7));


    assertEquals(6, person.history.size());
    assertEquals("Initial", person.history.get(5).name);
    assertEquals(time, (long)person.history.get(5).entered);
    assertEquals(time, (long) person.history.get(5).exited);

    assertEquals("2_Day_Delay", person.history.get(4).name);
    assertEquals(time, (long) person.history.get(4).entered);
    assertEquals(time + days(2), (long) person.history.get(4).exited);

    assertEquals("ED_Visit", person.history.get(3).name);
    assertEquals(time + days(2), (long) person.history.get(3).entered);
    assertEquals(time + days(2), (long) person.history.get(3).exited);

    assertEquals("3_Day_Delay", person.history.get(2).name);
    assertEquals(time + days(2), (long) person.history.get(2).entered);
    assertEquals(time + days(5), (long) person.history.get(2).exited);

    assertEquals("Death", person.history.get(1).name);
    assertEquals(time + days(5), (long) person.history.get(1).entered);
    assertEquals(time + days(5), (long) person.history.get(1).exited);

    assertEquals("Terminal", person.history.get(0).name);
    assertEquals(time + days(5), (long) person.history.get(0).entered);
    assertEquals(null, person.history.get(0).exited);
  }

  /**
   * Readability helper for the above test case. Turn days into time.
   * @param numDays Number of days
   * @return Amount to add to timestamp
   */
  private static long days(long numDays) {
    return Utilities.convertTime("days", numDays);
  }

  @Test
  public void testSubmoduleHistory() {
    Map<String, Module> modules =
        Whitebox.<Map<String, Module>>getInternalState(Module.class, "modules");
    // hack to load these test modules so they can be called by the CallSubmodule state
    Module subModule1 = getModule("submodules/encounter_submodule.json");
    Module subModule2 = getModule("submodules/medication_submodule.json");
    modules.put("submodules/encounter_submodule", subModule1);
    modules.put("submodules/medication_submodule", subModule2);

    try {
      Module module = getModule("recursively_calls_submodules.json");
      while (!module.process(person, time)) {
        time += Utilities.convertTime("years", 1);
      }

      // main module has 5 states, with the callsubmodule counted 2x
      // encounter_submodule has 6 states, with the callsubmodule counted 2x
      // medication_submodule has 5 states
      // total = 18
      System.out.println(person.history);
      assertEquals(18, person.history.size());

      assertEquals("Initial", person.history.get(17).name);
      assertEquals("Recursive Calls Submodules Module", person.history.get(17).module.name);

      assertEquals("Example_Condition", person.history.get(16).name);
      assertEquals("Recursive Calls Submodules Module", person.history.get(16).module.name);

      assertEquals("Call_Encounter_Submodule", person.history.get(15).name);
      assertEquals("Recursive Calls Submodules Module", person.history.get(15).module.name);


      assertEquals("Initial", person.history.get(14).name);
      assertEquals("Encounter Submodule Module", person.history.get(14).module.name);

      assertEquals("Delay", person.history.get(13).name);
      assertEquals("Encounter Submodule Module", person.history.get(13).module.name);

      assertEquals("Encounter_In_Submodule", person.history.get(12).name);
      assertEquals("Encounter Submodule Module", person.history.get(12).module.name);

      assertEquals("Call_MedicationOrder_Submodule", person.history.get(11).name);
      assertEquals("Encounter Submodule Module", person.history.get(11).module.name);


      assertEquals("Initial", person.history.get(10).name);
      assertEquals("Medication Submodule Module", person.history.get(10).module.name);

      assertEquals("Examplitis_Medication", person.history.get(9).name);
      assertEquals("Medication Submodule Module", person.history.get(9).module.name);

      assertEquals("Delay_Yet_Again", person.history.get(8).name);
      assertEquals("Medication Submodule Module", person.history.get(8).module.name);

      assertEquals("End_Medication", person.history.get(7).name);
      assertEquals("Medication Submodule Module", person.history.get(7).module.name);

      assertEquals("Med_Terminal", person.history.get(6).name);
      assertEquals("Medication Submodule Module", person.history.get(6).module.name);


      assertEquals("Call_MedicationOrder_Submodule", person.history.get(5).name);
      assertEquals("Encounter Submodule Module", person.history.get(5).module.name);

      assertEquals("Delay_Some_More", person.history.get(4).name);
      assertEquals("Encounter Submodule Module", person.history.get(4).module.name);

      assertEquals("Encounter_Terminal", person.history.get(3).name);
      assertEquals("Encounter Submodule Module", person.history.get(3).module.name);


      assertEquals("Call_Encounter_Submodule", person.history.get(2).name);
      assertEquals("Recursive Calls Submodules Module", person.history.get(2).module.name);

      assertEquals("End_Condition", person.history.get(1).name);
      assertEquals("Recursive Calls Submodules Module", person.history.get(1).module.name);

      assertEquals("Terminal", person.history.get(0).name);
      assertEquals("Recursive Calls Submodules Module", person.history.get(0).module.name);
    } finally {
      // always clean these up, to ensure they don't get seen by any other tests
      modules.remove("submodules/encounter_submodule");
      modules.remove("submodules/medication_submodule");
    }
  }
  
  @Test
  public void testDiagnosticReport() {
    Module module = getModule("observation_groups.json");

    State condition = module.getState("Record_MetabolicPanel");
    assertTrue(condition.process(person, time));

    // for a DiagnosticReport, we expect the report as well as the individual observations
    // to be added to the record
    Encounter e = person.record.encounters.get(0);
    
    assertEquals(1, e.reports.size());
    HealthRecord.Report report = e.reports.get(0);
    assertEquals(8, report.observations.size());
    assertEquals(8, e.observations.size());
    
    String[] codes =
        {"2339-0", "6299-2", "38483-4", "49765-1", "2947-0", "6298-4", "2069-3", "20565-8"};
    // Glucose, Urea Nitrogen, Creatinine, Calcium, Sodium, Potassium, Chloride, Carbon Dioxide
    
    for (int i = 0; i < 8; i++) {
      HealthRecord.Observation o = e.observations.get(i);
      
      assertEquals(codes[i], o.codes.get(0).code);
      assertEquals(report, o.report);
    }
  }
  
  @Test
  public void testMultiObservation() {
    Module module = getModule("observation_groups.json");

    State condition = module.getState("Record_BP");
    assertTrue(condition.process(person, time));

    // for a MultiObservation, we expect only the MultiObs to be added to the record,
    // not the child observations, which get added as components of the parent observation
    Encounter e = person.record.encounters.get(0);
    assertEquals(1, e.observations.size());
    
    HealthRecord.Observation o = e.observations.get(0);
    assertEquals("55284-4", o.codes.get(0).code);
    assertEquals(2, o.observations.size());
    assertEquals("8462-4", o.observations.get(0).codes.get(0).code); // diastolic
    assertEquals("8480-6", o.observations.get(1).codes.get(0).code); // systolic
  }
}
