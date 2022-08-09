package org.mitre.synthea.world.concepts;

import junit.framework.TestCase;

public class TelemedicineConfigTest extends TestCase {

  public void testFromJSON() {
    TelemedicineConfig config = TelemedicineConfig.fromJSON();
    System.out.println(config.getTelemedicineStartTime());
  }
}