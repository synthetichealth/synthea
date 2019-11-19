package org.mitre.synthea.modules;

import org.junit.Test;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class GrowthDataErrorsModuleTest {

  @Test
  public void process() {
    GrowthDataErrorsModule m = new GrowthDataErrorsModule();
    m.process(null, new ArrayList<>(), 1, null);
  }

}