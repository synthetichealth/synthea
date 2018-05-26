package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImmunizationsTest {

  @Test
  public void testMaximumDoses() {
    // "52" is the Hep A Adult vaccine. It requires two doses.
    int doses = Immunizations.getMaximumDoses("52");
    assertEquals(2, doses);
  }

  @Test
  public void testMaximumDosesNothing() {
    int doses = Immunizations.getMaximumDoses(null);
    assertEquals(1, doses);
  }
}
