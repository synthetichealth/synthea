package org.mitre.synthea.modules;

import org.junit.Before;
import org.junit.Test;

import org.mitre.synthea.engine.Event;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class EncounterModuleTest {

    private Person person;
    private long time;

    @Before
    public void setup() throws IOException {
        person = new Person(0L);

        person.history = new LinkedList<>();

        long birthTime = time - Utilities.convertTime("years", 35);
        person.attributes.put(Person.BIRTHDATE, birthTime);
        person.events.create(birthTime, Event.BIRTH, "Generator.run", true);

        time = 1503832821000L; // Is a Sunday
    }

    @Test
    public void testEncounterWellness() {
        Encounter encounter = person.record.encounterStart(time,
                HealthRecord.EncounterType.WELLNESS.toString());
        assertEquals("WELLNESS", encounter.type);
        long timeArray[] = new long[]{ encounter.start, encounter.stop };
        for(long l: timeArray){
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(l);
            // Ensure start/stop are a weekday
            assertEquals(c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY, true);
            assertEquals(c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY, true);
            // Ensure start/stop are during business hours
            assertEquals(c.get(Calendar.HOUR)>=8 && c.get(Calendar.HOUR)<=16, true);
        }
    }
}
