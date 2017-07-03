package org.mitre.synthea.modules;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratorTest {
    @Test public void testGeneratorCreatesPeople() {
    	int numberOfPeople = 1;
        Generator generator = new Generator(numberOfPeople);
        generator.run();
        assertEquals(numberOfPeople, generator.people.size());
    }
}
