package org.mitre.synthea.world.geography;

import org.junit.Assert;
import org.junit.Test;

public class LocationTest {

  @Test
  public void testAbbreviations() {
    Assert.assertTrue(Location.getAbbreviation("Massachusetts").equals("MA"));
  }

  @Test
  public void testAbbreviationsReverse() {
    Assert.assertTrue(Location.getStateName("MA").equals("Massachusetts"));
  }

  @Test
  public void testLocation() {
    Location location = new Location("Massachusetts", null);
    Assert.assertTrue(location.getPopulation("Bedford") > 0);
    Assert.assertTrue(location.getZipCode("Bedford").equals("01730"));
  }

  @Test
  public void testTimezone() {
    String tz = Location.getTimezoneByState("Massachusetts");
    Assert.assertNotNull(tz);
    Assert.assertTrue(tz.equals("Eastern Standard Time"));
  }
}
