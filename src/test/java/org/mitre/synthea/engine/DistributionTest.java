package org.mitre.synthea.engine;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

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

}