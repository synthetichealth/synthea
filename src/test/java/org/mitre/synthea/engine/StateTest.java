package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Hospital;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
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

}
