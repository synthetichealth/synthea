package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class DistributionTest {
  @Test
  public void testValidate() {
    Distribution d = new Distribution();
    d.kind = Distribution.Kind.UNIFORM;
    assertFalse(d.validate());
    HashMap<String, Double> parameters = new HashMap();
    parameters.put("low", 1d);
    parameters.put("high", 10d);
    d.parameters = parameters;
    assertTrue(d.validate());
  }

  @Test
  public void testRounding() {
    Distribution d = new Distribution();
    d.kind = Distribution.Kind.UNIFORM;
    d.round = true;
    HashMap<String, Double> parameters = new HashMap();
    parameters.put("low", 1d);
    parameters.put("high", 10d);
    d.parameters = parameters;
    assertTrue(d.validate());
    Person person = new Person(0L);
    for (int i = 0; i < 10; i++) {
      double value = d.generate(person);
      assertTrue(Math.round(value) == value);
    }
  }

}