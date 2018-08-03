package org.mitre.synthea.world.geography;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

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
  public void testAllDemographicsHaveLocations() throws Exception {
    String demoFileContents =
        Utilities.readResource(Config.get("generate.demographics.default_file"));
    List<LinkedHashMap<String, String>> demographics = SimpleCSV.parse(demoFileContents);
    
    String zipFileContents =
        Utilities.readResource(Config.get("generate.geography.zipcodes.default_file"));
    List<LinkedHashMap<String, String>> zips = SimpleCSV.parse(zipFileContents);
    
    // parse all the locations from the zip codes and put them in a a set.
    Set<String> availableLocations = new HashSet<>();
    for (Map<String,String> line : zips) {
      String city = line.get("NAME");
      String state = line.get("USPS");
      String key = (city + ", " + state).toUpperCase();
      availableLocations.add(key);
    }

    // iterate over all the locations in the demographics file, 
    // and check them all against the locations set from above
    List<String> mismatches = new ArrayList<>();
    for (LinkedHashMap<String,String> line : demographics) {
      String city = line.get("NAME");
      String state = line.get("STNAME");
      
      String original = (city + ", " + state);
      String key = original.toUpperCase();
      if (!availableLocations.contains(key)) {
        mismatches.add(original);
      }
    }
    String message = mismatches.toString();
    Assert.assertEquals(message, 0, mismatches.size());
  }
}
