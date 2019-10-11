package org.mitre.synthea.modules;

import org.junit.Test;
import org.mitre.synthea.world.concepts.HealthRecord;

import static org.junit.Assert.*;

public class GrowthDataErrorsModuleTest {

  @Test
  public void process() {
    GrowthDataErrorsModule m = new GrowthDataErrorsModule();
    assertTrue(m.process(null, 1));
  }

}