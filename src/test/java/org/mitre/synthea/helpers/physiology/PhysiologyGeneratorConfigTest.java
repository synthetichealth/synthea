package org.mitre.synthea.helpers.physiology;

import org.junit.Before;
import org.junit.Test;

public class PhysiologyGeneratorConfigTest {
  
  PhysiologyGeneratorConfig config;
  
  @Before
  public void setup() {
    config = new PhysiologyGeneratorConfig();
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testValidate() {
    config.setLeadTime(4);
    config.setSimDuration(2);
    config.validate();
  }
}
