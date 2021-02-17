package org.mitre.synthea.world.geography;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class LocationTest {

  private static String testState = null;
  private static String testTown = null;
  private static Location location = null;

  /**
   * Setup the unit tests with a single location... no need to reload this
   * in every unit test.
   * @throws Exception on configuration loading error.
   */
  @BeforeClass
  public static void createLocation() throws Exception {
    TestHelper.loadTestProperties();
    testState = Config.get("test_state.default", "Massachusetts");
    testTown = Config.get("test_town.default", "Bedford");
    location = new Location(testState, null);
  }

  @Test
  public void testAbbreviations() {
    Assert.assertNotNull(Location.getAbbreviation(testState));
    Assert.assertFalse(Location.getAbbreviation(testState).equals(testState));
  }

  @Test
  public void testAbbreviationsReverse() {
    String abbreviation = Location.getAbbreviation(testState);
    Assert.assertTrue(Location.getStateName(abbreviation).equals(testState));
  }

  @Test
  public void testLocation() {
    Assert.assertTrue(location.getPopulation(testTown) > 0);
    List<String> zipcodes = location.getZipCodes(testTown);
    String zipcode = location.getZipCode(testTown, new Person(1));
    Assert.assertTrue(zipcodes.contains(zipcode));
  }

  @Test
  public void testTimezone() {
    String tz = Location.getTimezoneByState(testState);
    Assert.assertNotNull(tz);
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
    Set<String> zipLocations = new HashSet<>();
    for (Map<String,String> line : zips) {
      String city = line.get("NAME");
      String state = line.get("USPS");
      String key = (city + ", " + state).toUpperCase();
      zipLocations.add(key);
    }

    // parse all the locations from demographics and put them in a a set.
    Set<String> demoLocations = new HashSet<>();
    for (LinkedHashMap<String,String> line : demographics) {
      String city = line.get("NAME");
      String state = line.get("STNAME");
      
      String key = (city + ", " + state).toUpperCase();
      demoLocations.add(key);
    }

    Set<String> demosWithoutZip = Sets.difference(demoLocations, zipLocations);
    Set<String> zipsWithoutDemo = Sets.difference(zipLocations, demoLocations);

    String message = "Locations without zip: " + demosWithoutZip.toString();
    Assert.assertEquals(message, 0, demosWithoutZip.size());

    message = "Zips without demographics: " + zipsWithoutDemo.toString();
    Assert.assertEquals(message, 0, zipsWithoutDemo.size());
  }

  @Test
  public void testAssignPointInMultiZipCodeCity() {
    Person p = new Person(1);
    String zipcode = location.getZipCode(testTown, p);
    p.attributes.put(Person.ZIP, zipcode);
    location.assignPoint(p, testTown);
    Point2D.Double coord = (Point2D.Double) p.attributes.get(Person.COORDINATE);
    Assert.assertNotNull(coord);
    Assert.assertNotNull(coord.x);
    Assert.assertNotNull(coord.y);
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
        Location.loadCitiesByLanguage("geography/malformed_foreign_birthplace.json.txt");
    Assert.assertNotNull(map);
    Assert.assertEquals("Expected map to be empty", 0, map.size());
  }

  @Test
  public void testGetForeignPlaceOfBirth_HappyPath() {
    Person person = new Person(4L);
    String[] placeOfBirth = location.randomBirthplaceByLanguage(person, "german");
    for (String part : placeOfBirth) {
      Assert.assertNotNull(part);
      Assert.assertTrue(placeOfBirth[placeOfBirth.length - 1].contains(part));
    }
  }

  @Test
  public void testGetForeignPlaceOfBirth_ValidStringInvalidFormat_1() {
    Person person = new Person(0L);
    String[] placeOfBirth = location.randomBirthplaceByLanguage(person, "too_many_elements");
    for (String part : placeOfBirth) {
      Assert.assertNotNull(part);
      Assert.assertTrue(placeOfBirth[placeOfBirth.length - 1].contains(part));
    }
  }

  @Test
  public void testGetForeignPlaceOfBirth_ValidStringInvalidFormat_2() {
    Person person = new Person(0L);
    String[] placeOfBirth = location.randomBirthplaceByLanguage(person, "not_enough_elements");
    for (String part : placeOfBirth) {
      Assert.assertNotNull(part);
      Assert.assertTrue(placeOfBirth[placeOfBirth.length - 1].contains(part));
    }
  }

  @Test
  public void testGetForeignPlaceOfBirth_MissingValue() {
    Person person = new Person(0L);
    String[] placeOfBirth = location.randomBirthplaceByLanguage(person, "unknown_ethnicity");
    for (String part : placeOfBirth) {
      Assert.assertNotNull(part);
      Assert.assertTrue(placeOfBirth[placeOfBirth.length - 1].contains(part));
    }
  }

  @Test
  public void testGetForeignPlaceOfBirth_EmptyValue() {
    Person person = new Person(0L);
    String[] placeOfBirth = location.randomBirthplaceByLanguage(person, "empty_ethnicity");
    for (String part : placeOfBirth) {
      Assert.assertNotNull(part);
      Assert.assertTrue(placeOfBirth[placeOfBirth.length - 1].contains(part));
    }
  }
}
