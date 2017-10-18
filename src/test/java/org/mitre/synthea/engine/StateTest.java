package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
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
	  
}
