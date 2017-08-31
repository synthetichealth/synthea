package org.mitre.synthea.modules;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratorTest {
    @Test public void testGeneratorCreatesPeople() throws Exception {
    	int numberOfPeople = 1;
        Generator generator = new Generator(numberOfPeople);
        generator.run();
        assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
        // there may be more than the requested number of people, because we re-generate on death
//        assertTrue(numberOfPeople <= generator.people.size());
    }
}
