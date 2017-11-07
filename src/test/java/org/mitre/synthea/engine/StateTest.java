package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.world.agents.Hospital;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mockito.Mockito;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

public class StateTest {

	private Person person;
	private long time;

	@Before
	public void setup() throws IOException {
		person = new Person(0L);
		
		person.history = new LinkedList<>();
		person.setAmbulatoryProvider( Mockito.mock(Hospital.class) );
		person.setEmergencyProvider( Mockito.mock(Hospital.class) );
		person.setInpatientProvider( Mockito.mock(Hospital.class) );
		
		long birthTime = time - Utilities.convertTime("years", 35);
		person.attributes.put(Person.BIRTHDATE, birthTime);
		person.events.create(birthTime, Event.BIRTH, "Generator.run", true);
		
		time = System.currentTimeMillis();
	}
	
	private static Module getModule(String name)
	{
		try 
		{
			Path modulesFolder = Paths.get("src/test/resources/generic");
			Path logicFile = modulesFolder.resolve(name);
			JsonReader reader = new JsonReader(new FileReader(logicFile.toString()));
			JsonObject jsonModule = new JsonParser().parse(reader).getAsJsonObject();
			reader.close();
		
			return new Module(jsonModule, false);
		} catch (Exception e)
		{
			// if anything breaks, we can't fix it. throw a RuntimeException for simplicity
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private void simulateWellnessEncounter(Module module)
	{
		person.attributes.put(EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + module.name, true);
	}
	
	
	  @Test public void initial_always_passes()
	  {
	    Module module = getModule("initial_to_terminal.json");
	    State initial = module.getState("Initial");
	    assertTrue(initial.process(person, time));
	  }
	
	  @Test public void terminal_never_passes()
	  {
	    Module module = getModule("initial_to_terminal.json");
	    State terminal =  module.getState("Terminal");
	    assertFalse(terminal.process(person, time));
	    assertFalse(terminal.process(person, time + TimeUnit.DAYS.toMillis(7)));
	  }
	  
	  
	  @Test public void guard_passes_when_condition_is_met()
	  {
	    Module module = getModule("guard.json");
	    State guard = module.getState("Gender_Guard");
	    person.attributes.put(Person.GENDER, "F");
	    assertTrue(guard.process(person, time));
	  }

	  @Test public void guard_blocks_when_condition_isnt_met()
	  {
	    Module module = getModule("guard.json");
	    State guard = module.getState("Gender_Guard");
	    person.attributes.put(Person.GENDER, "M");
	    assertFalse(guard.process(person, time));
	  }
	  
	  @Test public void counter()
	  {
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
	  
	  @Test public void condition_onset()
	  {
	    // Setup a mock to track calls to the patient record
	    // In this case, the record shouldn't be called at all
		person.record = Mockito.mock(HealthRecord.class);

	    Module module = getModule("condition_onset.json");
	    State condition = module.getState("Diabetes");
	    // Should pass through this state immediately without calling the record
	    assertTrue(condition.process(person, time));
	    
	    verifyZeroInteractions(person.record);
	  }

	  @Test public void condition_onset_during_encounter()
	  {
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

	  @Test public void allergy_onset()
	  {
	    // Setup a mock to track calls to the patient record
	    // In this case, the record shouldn't be called at all
		person.record = Mockito.mock(HealthRecord.class);

	    Module module = getModule("allergies.json");
	    State allergy = module.getState("Allergy_to_Eggs");
	    // Should pass through this state immediately without calling the record
	    assertTrue(allergy.process(person, time));
	    
	    verifyZeroInteractions(person.record);
	  }
	  
	  @Test public void delay_passes_after_exact_time()
	  {
	    Module module = getModule("delay.json");

	    // Seconds
	    State delay = module.getState("2_Second_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000));
	    assertTrue(delay.process(person, time + 2L*1000));
	    assertTrue(delay.process(person, time + 3L*1000));
	    

	    // Minutes
	    delay = module.getState("2_Minute_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60));
	    assertTrue(delay.process(person, time + 2L*1000*60));
	    assertTrue(delay.process(person, time + 3L*1000*60));

	    // Hours
	    delay = module.getState("2_Hour_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60));
	    assertTrue(delay.process(person, time + 2L*1000*60*60));
	    assertTrue(delay.process(person, time + 3L*1000*60*60));

	    // Days
	    delay = module.getState("2_Day_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24));
	    assertTrue(delay.process(person, time + 2L*1000*60*60*24));
	    assertTrue(delay.process(person, time + 3L*1000*60*60*24));

	    // Weeks
	    delay = module.getState("2_Week_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24*7));
	    assertTrue(delay.process(person, time + 2L*1000*60*60*24*7));
	    assertTrue(delay.process(person, time + 3L*1000*60*60*24*7));

	    // Months
	    // NOTE: months + years are not "well-defined" like the smaller units of time
	    // so these may be flaky around things like leap years & DST changes
	    delay = module.getState("2_Month_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24*30));
	    assertTrue(delay.process(person, time + 2L*1000*60*60*24*30));
	    assertTrue(delay.process(person, time + 3L*1000*60*60*24*30));

	    // Years
	    delay = module.getState("2_Year_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24*365));
	    assertTrue(delay.process(person, time + 2L*1000*60*60*24*365));
	    assertTrue(delay.process(person, time + 3L*1000*60*60*24*365));
	  }
	  

	  @Test public void delay_passes_after_time_range()
	  {
	    Module module = getModule("delay.json");


	    // Seconds
	    State delay = module.getState("2_To_10_Second_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000));
	    assertFalse(delay.process(person, time + 2L*1000));
	    assertTrue(delay.process(person, time + 10L*1000));

	    // Minutes
	    delay = module.getState("2_To_10_Minute_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60));
	    assertFalse(delay.process(person, time + 2L*1000*60));
	    assertTrue(delay.process(person, time + 10L*1000*60));

	    // Hours
	    delay = module.getState("2_To_10_Hour_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60));
	    assertFalse(delay.process(person, time + 2L*1000*60*60));
	    assertTrue(delay.process(person, time + 10L*1000*60*60));

	    // Days
	    delay = module.getState("2_To_10_Day_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24));
	    assertFalse(delay.process(person, time + 2L*1000*60*60*24));
	    assertTrue(delay.process(person, time + 10L*1000*60*60*24));


	    // Weeks
	    delay = module.getState("2_To_10_Week_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24*7));
	    assertFalse(delay.process(person, time + 2L*1000*60*60*24*7));
	    assertTrue(delay.process(person, time + 10L*1000*60*60*24*7));

	    // Months
	    delay = module.getState("2_To_10_Month_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24*30));
	    assertFalse(delay.process(person, time + 2L*1000*60*60*24*30));
	    assertTrue(delay.process(person, time + 10L*1000*60*60*24*30));

	    // Years
	    delay = module.getState("2_To_10_Year_Delay");
	    delay.entered = time;
	    assertFalse(delay.process(person, time));
	    assertFalse(delay.process(person, time + 1L*1000*60*60*24*365));
	    assertFalse(delay.process(person, time + 2L*1000*60*60*24*365));
	    assertTrue(delay.process(person, time + 10L*1000*60*60*24*365));
	  }

	  @Test public void vitalsign()
	  {
	    // Setup a mock to track calls to the patient record
		// In this case, the record shouldn't be called at all
		person.record = Mockito.mock(HealthRecord.class);

	    Module module = getModule("vitalsign_observation.json");

	    State vitalsign = module.getState("VitalSign");
	    assertTrue(vitalsign.process(person, time));

	    assertEquals(120.0, person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE), 0.0);

	    verifyZeroInteractions(person.record);
	  }
	  
	  @Test public void symptoms()
	  {
	    Module module = getModule("symptom.json");

	    State symptom1 = module.getState("SymptomOnset");
	    assertTrue(symptom1.process(person, time));
	    int symptomValue = person.getSymptom("Chest Pain");
	    assertTrue(1 <= symptomValue && symptomValue <= 10);

	    State symptom2 = module.getState("SymptomWorsen");
	    assertTrue(symptom2.process(person, time));
	    assertEquals(96, person.getSymptom("Chest Pain"));
	  }
	  
	  @Test public void setAttribute_with_value()
	  {
	    Module module = getModule("set_attribute.json");

	    person.attributes.remove("Current Opioid Prescription");
	    State set1 = module.getState("Set_Attribute_1");
	    assertTrue(set1.process(person, time));

	    assertEquals("Vicodin", person.attributes.get("Current Opioid Prescription"));
	  }

	  @Test public void setAttribute_without_value()
	  {
	    Module module = getModule("set_attribute.json");

	    person.attributes.put("Current Opioid Prescription", "Vicodin");
	    State set2 = module.getState("Set_Attribute_2");
	    assertTrue(set2.process(person, time));

	    assertNull( person.attributes.get("Current Opioid Prescription") );
	  }
	  
	  @Test public void procedure_assigns_entity_attribute()
	  {
	    person.attributes.remove("Most Recent Surgery");
	    Module module = getModule("procedure.json");
	    State encounter = module.getState("Inpatient_Encounter");
	    assertTrue(encounter.process(person, time));
	    person.history.add(encounter);

	    State appendectomy = module.getState("Appendectomy");
	    appendectomy.process(person, time);

	    HealthRecord.Procedure procedure = (HealthRecord.Procedure)person.attributes.get("Most Recent Surgery");
	    
	    assertEquals(time, procedure.start);
	    
	    Code code = procedure.codes.get(0);
	    
	    assertEquals("6025007", code.code);
	    assertEquals("Laparoscopic appendectomy", code.display);
	  }
	  
	  @Test public void procedure_during_encounter()
	  {
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
	  
	  @Test public void observation()
	  {
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
	  
	  

	  @Test public void wellness_encounter()
	  {
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

	  @Test public void wellness_encounter_diagnoses_condition()
	  {
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

	  @Test public void ed_visit_encounter()
	  {
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
	    assertEquals(0L, enc.stop);
	    
	    Code code = enc.codes.get(0);
	    assertEquals("50849002", code.code);
	    assertEquals("Emergency Room Admission", code.display);

	  }

	  @Test public void encounter_with_attribute_reason()
	  {
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
	    assertEquals(0L, enc.stop);
	    assertEquals("73211009", enc.reason.code);
	    assertEquals("Diabetes Mellitus", enc.reason.display);
	    
	    Code code = enc.codes.get(0);
	    assertEquals("50849002", code.code);
	    assertEquals("Emergency Room Admission", code.display);
	  }
	  
	  @Test public void allergy_onset_during_encounter()
	  {
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

	  @Test public void allergy_end_by_state_name()
	  {
	    Module module = getModule("allergies.json");
	    State allergyState = module.getState("Allergy_to_Eggs");
	    // Should pass through this state immediately without calling the record
	    assertTrue(allergyState.process(person, time));
	    person.history.add(allergyState);

	    State encounter = module.getState("Dr_Visit");
	    assertTrue(encounter.process(person, time));

	    // Now process the end of the prescription
	    State med_end = module.getState("Allergy_Ends");
	    assertTrue(med_end.process(person, time));

	    HealthRecord.Entry allergy = person.record.encounters.get(0).allergies.get(0);
	    assertEquals(time, allergy.start);
	    assertEquals(time, allergy.stop);
	    
	    Code code = allergy.codes.get(0);
	    assertEquals("91930004", code.code);
	    assertEquals("Allergy to eggs", code.display);
	  }
	  

	  @Test public void condition_end_by_entity_attribute()
	  {
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

	    HealthRecord.Entry attributeEntry = (HealthRecord.Entry)person.attributes.get("Drug Use Behavior");

	    // Now process the end of the condition
	    State con_end = module.getState("Condition1_End");
	    assertTrue(con_end.process(person, time));
	    
	    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
	    assertEquals(time, condition.start);
	    assertEquals(time, condition.stop);
	    assertEquals(attributeEntry, condition);
	    
	    Code code = condition.codes.get(0);
	    assertEquals("228380004", code.code);
	    assertEquals("Chases the dragon (finding)", code.display);
	  }

	  @Test public void condition_end_by_condition_onset()
	  {
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
	    State con_end = module.getState("Condition2_End");
	    assertTrue(con_end.process(person, time));

	    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
	    assertEquals(time, condition.start);
	    assertEquals(time, condition.stop);
	    
	    Code code = condition.codes.get(0);
	    assertEquals("6142004", code.code);
	    assertEquals("Influenza", code.display);
	  }

	  @Test public void condition_end_by_code()
	  {
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
	    State con_end = module.getState("Condition3_End");
	    assertTrue(con_end.process(person, time));

	    HealthRecord.Entry condition = person.record.encounters.get(0).conditions.get(0);
	    assertEquals(time, condition.start);
	    assertEquals(time, condition.stop);
	    
	    Code code = condition.codes.get(0);
	    assertEquals("73211009", code.code);
	    assertEquals("Diabetes mellitus", code.display);
	  }
	  

	  @Test public void medication_order_during_wellness_encounter()
	  {
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

	  @Test public void medication_order_with_dosage()
	  {
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

	  @Test public void medication_order_as_needed()
	  {
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

	  @Test public void medication_order_assigns_entity_attribute()
	  {
	    person.attributes.remove("Diabetes Medication");
	    Module module = getModule("medication_order.json");
	    State encounter = module.getState("Wellness_Encounter");
	    simulateWellnessEncounter(module);
	    assertTrue(encounter.process(person, time));
	    person.history.add(encounter);

	    State med = module.getState("Metformin");
	    assertTrue(med.process(person, time));

	    HealthRecord.Medication medication = (HealthRecord.Medication)person.attributes.get("Diabetes Medication");
	    assertEquals(time, medication.start);
	    
	    Code code = medication.codes.get(0);
	    assertEquals("860975", code.code);
	    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);
	  }

	  @Test public void medication_end_by_entity_attribute()
	  {
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
	    State med_end = module.getState("Insulin_End");
	    assertTrue(med_end.process(person, time));

	    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
	    assertEquals(time, medication.start);
	    assertEquals(time, medication.stop);
	    
	    Code code = medication.codes.get(0);
	    assertEquals("575679", code.code);
	    assertEquals("Insulin, Aspart, Human 100 UNT/ML [NovoLOG]", code.display);
	  }

	  @Test public void medication_end_by_medication_order()
	  {
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
	    State med_end = module.getState("Bromocriptine_End");
	    assertTrue(med_end.process(person, time));

	    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
	    assertEquals(time, medication.start);
	    assertEquals(time, medication.stop);
	    
	    Code code = medication.codes.get(0);
	    assertEquals("563894", code.code);
	    assertEquals("Bromocriptine 5 MG [Parlodel]", code.display);
	  }

	  @Test public void medication_end_by_code()
	  {
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
	    State med_end = module.getState("Metformin_End");
	    assertTrue(med_end.process(person, time));

	    HealthRecord.Medication medication = person.record.encounters.get(0).medications.get(0);
	    assertEquals(time, medication.start);
	    assertEquals(time, medication.stop);
	    
	    Code code = medication.codes.get(0);
	    assertEquals("860975", code.code);
	    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);
	  }
	  
	  @Test public void careplan_start()
	  {
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

	  @Test public void careplan_assigns_entity_attribute()
	  {
	      person.attributes.remove("Diabetes_CarePlan");
	      Module module = getModule("careplan_start.json");
	      State encounter = module.getState("Wellness_Encounter");
	      simulateWellnessEncounter(module);
		  assertTrue(encounter.process(person, time));
	      person.history.add(encounter);

	      State plan = module.getState("Diabetes_Self_Management");
	      assertTrue(plan.process(person, time));

	      HealthRecord.CarePlan cp = (HealthRecord.CarePlan)person.attributes.get("Diabetes_CarePlan");
	      assertEquals(time, cp.start);
	      assertEquals(0L, cp.stop);
	    
	      Code code = cp.codes.get(0);
	      assertEquals("698360004", code.code);
	      assertEquals("Diabetes self management plan", code.display);
	  }

	  @Test public void careplan_end_by_entity_attribute()
	  {
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
	    assertTrue(plan.process(person, time)); // have to use run not process here because the entity attribute stuff happens in run
	    person.history.add(plan);

	    HealthRecord.CarePlan entityAttribute = (HealthRecord.CarePlan) person.attributes.get("Diabetes_CarePlan");
	    

	    // Now process the end of the careplan
	    State plan_end = module.getState("CarePlan1_End");
	    assertTrue(plan_end.process(person, time));
	    person.history.add(plan_end);

	    HealthRecord.CarePlan cp = person.record.encounters.get(0).careplans.get(0);
	    assertEquals(time, cp.start);
	    assertEquals(time, cp.stop);
	    assertEquals(cp,  entityAttribute);
	      
	    Code code = cp.codes.get(0);
	    assertEquals("698360004", code.code);
	    assertEquals("Diabetes self management plan", code.display);
	    
	    assertEquals(1, cp.activities.size());
	    Code activity = cp.activities.iterator().next();
	    assertEquals("160670007", activity.code);
	    assertEquals("Diabetic diet", activity.display);
	  }

	  @Test public void careplan_end_by_code()
	  {
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
	    State plan_end = module.getState("CarePlan2_End");
	    assertTrue(plan_end.process(person, time));
	    person.history.add(plan_end);

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

	  @Test public void careplan_end_by_careplan()
	  {
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
	    State plan_end = module.getState("CarePlan3_End");
	    assertTrue(plan_end.process(person, time));
	    person.history.add(plan_end);

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
	  

	  @Test public void death()
	  {
	    Module module = getModule("death.json");
	    State death = module.getState("Death");
	    assertTrue(person.alive(time));
	    assertTrue(death.process(person, time));

	    // Patient shouldn't be alive anymore
	    assertFalse(person.alive(time));

	    // Verify that death was added to the record
	    assertEquals(time, (long)person.record.death);
	  }

	  @Test public void future_death()
	  {
	    Module module = getModule("death_life_expectancy.json");
	    module.process(person, time);
	    module.process(person, time + Utilities.convertTime("days", 7));

	    assertTrue(person.alive(time + Utilities.convertTime("days", 7)));
	    assertTrue((boolean)person.attributes.get("processing"));
	    assertNull(person.attributes.get("still_processing"));

	    module.process(person, time + Utilities.convertTime("days", 14));
	    assertTrue((boolean)person.attributes.get("still_processing"));

	    module.process(person, time + Utilities.convertTime("months", 6));
	    assertFalse(person.alive(time + Utilities.convertTime("months", 6)));
	  }

	  @Test public void cause_of_death_code()
	  {
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

	  @Test public void cause_of_death_conditionOnset()
	  {
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

	  // TODO uncomment this once attributes for undiagnosed conditions work
	  @Test public void cause_of_death_attribute()
	  {
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
}
