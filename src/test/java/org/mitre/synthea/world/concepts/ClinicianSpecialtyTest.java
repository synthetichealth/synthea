package org.mitre.synthea.world.concepts;

import org.junit.Assert;
import org.junit.Test;

public class ClinicianSpecialtyTest {

  @Test
  public void testSpecialtiesMap() {
    String[] specialties = ClinicianSpecialty.getSpecialties();
    Assert.assertNotNull("Clinician Specialties should not be null.", specialties);
    Assert.assertTrue("Clinician Specialities should not be empty.", specialties.length > 0);

    for (String specialty : specialties) {
      String code = ClinicianSpecialty.getCMSProviderSpecialtyCode(specialty);
      Assert.assertNotNull("CMS Specialty code should not be null.", code);
      String msg = specialty + " => " + code;
      Assert.assertTrue("CMS Speciality code should be length 2: " + msg, code.length() == 2);
    }
  }
}
