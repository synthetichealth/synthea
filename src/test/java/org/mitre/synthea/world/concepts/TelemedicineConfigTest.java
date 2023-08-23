package org.mitre.synthea.world.concepts;

import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.math3.util.Pair;

public class TelemedicineConfigTest extends TestCase {

  /**
   * Test reading in a config file from JSON.
   */
  public void testFromJSON() {
    double expectedPreTypicalEmergencyValue = 0.25;
    TelemedicineConfig config = TelemedicineConfig.fromJSON();
    List<Pair<String, Double>> pmf = config.getPreTelemedTypicalEmergency().getPmf();
    double actualValue = 0;
    for (int i = 0; i < pmf.size(); i++) {
      Pair<String, Double> pair = pmf.get(i);
      if (pair.getFirst().equals(TelemedicineConfig.EMERGENCY)) {
        actualValue = pair.getSecond();
      }
    }
    assertEquals(expectedPreTypicalEmergencyValue, actualValue);
  }
}