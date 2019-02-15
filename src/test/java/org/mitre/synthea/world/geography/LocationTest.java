package org.mitre.synthea.world.geography;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
  public void testTimezone() {
    String tz = Location.getTimezoneByState("Massachusetts");
    Assert.assertNotNull(tz);
    Assert.assertTrue(tz.equals("Eastern Standard Time"));
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

  @Test
  public void testForeignPlaceOfBirthFileLoad() {
    Map<String, List<String>> map =
        Location.loadCitiesByLanguage("geography/foreign_birthplace_simple.json");
    Set<String> expectedCities = ImmutableSet.of("german", "west_indian");

    Assert.assertNotNull("Expected Non-Null map", map);
    Assert.assertEquals(
        "Expected foreign_birthplace resource to contain 2 ethnicities", 2, map.size());
    for (String key : map.keySet()) {
      Assert.assertTrue("Unexpected key found in map " + key, expectedCities.contains(key));
      Assert.assertEquals("Expected size to be 1 for key: " + key, 1, map.get(key).size());
    }
  }

  @Test
  public void testInvalidForeignPlaceOfBirthFileLoad() {
    Map<String, List<String>> map =
        Location.loadCitiesByLanguage("geography/this_isnt_a_file.json");
    Assert.assertNotNull(map);
    Assert.assertEquals("Expected map to be empty", 0, map.size());
  }

  @Test
  public void testMalformedForeignPlaceOfBirthFileLoad() {
    Map<String, List<String>> map =
        Location.loadCitiesByLanguage("geography/malformed_foreign_birthplace.json");
    Assert.assertNotNull(map);
    Assert.assertEquals("Expected map to be empty", 0, map.size());
  }

  @Test
  public void testGetForeignPlaceOfBirth_HappyPath() {
    Location location = new Location("Massachusetts", null);
    Random random = new Random(0);
    String[] placeOfBirth = location.randomBirthplaceByEthnicity(random, "german");
    Assert.assertEquals("Expected to receive 'Munich'", "Munich", placeOfBirth[0]);
    Assert.assertEquals("Expected to receive 'Bavaria'", "Bavaria", placeOfBirth[1]);
    Assert.assertEquals("Expected to receive 'DE'", "DE", placeOfBirth[2]);
    Assert.assertEquals("Expected to receive 'Munich, Bavaria, DE'",
        "Munich, Bavaria, DE", placeOfBirth[3]);
  }

  @Test
  public void testGetForeignPlaceOfBirth_ValidStringInvalidFormat_1() {
    Location location = new Location("Massachusetts", null);
    Random random = new Random(0);
    String[] placeOfBirth = location.randomBirthplaceByEthnicity(random, "too_many_elements");
    Assert.assertEquals("Expected to receive 'Stoughton'", "Stoughton", placeOfBirth[0]);
    Assert.assertEquals("Expected to receive 'Massachusetts'", "Massachusetts", placeOfBirth[1]);
    Assert.assertEquals("Expected to receive 'US'", "US", placeOfBirth[2]);
    Assert.assertEquals("Expected to recieve 'Stoughton, Massachusetts, US'",
        "Stoughton, Massachusetts, US", placeOfBirth[3]);
  }

  @Test
  public void testGetForeignPlaceOfBirth_ValidStringInvalidFormat_2() {
    Location location = new Location("Massachusetts", null);
    Random random = new Random(0);
    String[] placeOfBirth = location.randomBirthplaceByEthnicity(random, "not_enough_elements");
    Assert.assertEquals("Expected to receive 'Stoughton'", "Stoughton", placeOfBirth[0]);
    Assert.assertEquals("Expected to receive 'Massachusetts'", "Massachusetts", placeOfBirth[1]);
    Assert.assertEquals("Expected to receive 'US'", "US", placeOfBirth[2]);
    Assert.assertEquals("Expected to recieve 'Stoughton, Massachusetts, US'",
        "Stoughton, Massachusetts, US", placeOfBirth[3]);
  }

  @Test
  public void testGetForeignPlaceOfBirth_MissingValue() {
    Location location = new Location("Massachusetts", null);
    Random random = new Random(0);
    String[] placeOfBirth = location.randomBirthplaceByEthnicity(random, "unknown_ethnicity");
    Assert.assertEquals("Expected to receive 'Rehoboth'", "Rehoboth", placeOfBirth[0]);
    Assert.assertEquals("Expected to receive 'Massachusetts'", "Massachusetts", placeOfBirth[1]);
    Assert.assertEquals("Expected to receive 'US'", "US", placeOfBirth[2]);
    Assert.assertEquals("Expected to recieve 'Rehoboth, Massachusetts, US'",
        "Rehoboth, Massachusetts, US", placeOfBirth[3]);
  }

  @Test
  public void testGetForeignPlaceOfBirth_EmptyValue() {
    Location location = new Location("Massachusetts", null);
    Random random = new Random(0);
    String[] placeOfBirth = location.randomBirthplaceByEthnicity(random, "empty_ethnicity");
    Assert.assertEquals("Expected to receive 'Rehoboth'", "Rehoboth", placeOfBirth[0]);
    Assert.assertEquals("Expected to receive 'Massachusetts'", "Massachusetts", placeOfBirth[1]);
    Assert.assertEquals("Expected to receive 'US'", "US", placeOfBirth[2]);
    Assert.assertEquals("Expected to recieve 'Rehoboth, Massachusetts, US'",
        "Rehoboth, Massachusetts, US", placeOfBirth[3]);
  }
}
