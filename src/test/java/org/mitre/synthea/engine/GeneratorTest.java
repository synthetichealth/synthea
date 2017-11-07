package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;

public class GeneratorTest {
    @Test public void testGeneratorCreatesPeople() throws Exception {
    	int numberOfPeople = 1;
    	TestHelper.exportOff();
        Generator generator = new Generator(numberOfPeople);
        generator.run();
        assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
        // there may be more than the requested number of people, because we re-generate on death
        
        
        numberOfPeople = 3;
    	Config.set("generate.default_population", Integer.toString(numberOfPeople));
        generator = new Generator();
        generator.run();
        assertEquals(numberOfPeople, generator.stats.get("alive").longValue());
    }
}
