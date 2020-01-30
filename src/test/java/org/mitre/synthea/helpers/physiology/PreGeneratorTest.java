package org.mitre.synthea.helpers.physiology;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.helpers.physiology.PreGenerator.PreGeneratorArg;
import org.mitre.synthea.world.agents.Person;

public class PreGeneratorTest {
  @Test
  public void testPreGenerator() {
    PreGenerator gen = new PreGenerator();
    gen.setClassName("org.mitre.synthea.helpers.ConstantValueGenerator");
    
    // Set the second argument for the ConstantValueGenerator constructor
    PreGeneratorArg arg1 = new PreGeneratorArg();
    arg1.setType("java.lang.Double");
    arg1.setPrimitive(true);
    arg1.setValue("1.0");
    
    // Put it in a List
    List<PreGeneratorArg> args = new ArrayList<PreGeneratorArg>();
    args.add(arg1);
    
    // Add them to the PreGenerator
    gen.setArgs(args);
    
    // Construct the generator
    ValueGenerator generator = gen.getGenerator(new Person(0));
    
    // Test that we get the expected value
    assertEquals(1.0, generator.getValue(0), 0.0);
  }
}
