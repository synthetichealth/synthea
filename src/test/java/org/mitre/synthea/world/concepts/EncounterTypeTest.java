package org.mitre.synthea.world.concepts;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;;

public class EncounterTypeTest {
  
  @Test public void testEncounterTypeStringEquality() {
    Assert.assertTrue(EncounterType.WELLNESS.equals("wellness"));
  }

  @Test public void testEncounterTypeStringNonEquality() {
    Assert.assertFalse(EncounterType.WELLNESS.equals("emergency"));
  }

  @Test public void testEncounterTypeEquality() {
    EncounterType foo = EncounterType.WELLNESS;
    Assert.assertTrue(foo.equals(EncounterType.WELLNESS));
    Assert.assertTrue(EncounterType.WELLNESS.equals(foo));    
  }

  @Test public void testEncounterTypeNonEquality() {
    EncounterType foo = EncounterType.EMERGENCY;
    Assert.assertFalse(foo.equals(EncounterType.WELLNESS));
    Assert.assertFalse(EncounterType.WELLNESS.equals(foo));    
  }  

}
