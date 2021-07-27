package org.mitre.synthea.modules.covid;

import org.junit.Test;

import static org.junit.Assert.*;

public class C19VaccineAgeDistributionsTest {

  @Test
  public void loadRawDistribution() {
    C19VaccineAgeDistributions.loadRawDistribution();
    assertEquals(8, C19VaccineAgeDistributions.rawDistributions.keySet().size());
  }

  @Test
  public void populateDistributions() {
    C19VaccineAgeDistributions.loadRawDistribution();
    C19VaccineAgeDistributions.populateDistributions();
  }

  @Test
  public void testAgeRange() {
    C19VaccineAgeDistributions.AgeRange ar =
        new C19VaccineAgeDistributions.AgeRange("Ages_75+_yrs");
    assertEquals(75, ar.min);
    ar = new C19VaccineAgeDistributions.AgeRange("Ages_12-15_yrs");
    assertEquals(12, ar.min);
    assertEquals(15, ar.max);
  }
}