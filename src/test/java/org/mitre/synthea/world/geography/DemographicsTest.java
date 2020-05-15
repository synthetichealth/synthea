package org.mitre.synthea.world.geography;

import static org.junit.Assert.assertTrue;

import com.google.common.collect.Table;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;

public class DemographicsTest {
  public static String demographicsFile;
  public static Demographics philly;
  public static Random random;

  /**
   * Set up the demographics to use for testing.
   */
  @BeforeClass
  @SuppressWarnings("rawtypes")
  public static void setUp() throws IOException {
    demographicsFile = Config.get("generate.demographics.default_file");
    Config.set("generate.demographics.default_file", "geography/test_demographics.csv");
    Table pa = Demographics.load("Pennsylvania");
    philly = (Demographics) pa.get("Pennsylvania", "27237");
    random = new Random();
  }

  @AfterClass
  public static void cleanUp() {
    Config.set("generate.demographics.default_file", demographicsFile);
  }

  @Test
  public void pickRace() {
    HashMap<String, Integer> raceMap = new HashMap<String, Integer>();
    for (int i = 0; i < 10000; i++) {
      String race = philly.pickRace(random);
      if (raceMap.containsKey(race)) {
        raceMap.put(race, raceMap.get(race) + 1);
      } else {
        raceMap.put(race, 1);
      }
    }
    assertTrue(raceMap.get("white") > 4300 && raceMap.get("white") < 4800);
    assertTrue(raceMap.get("black") > 4100 && raceMap.get("black") < 4700);
    assertTrue(raceMap.get("asian") > 500 && raceMap.get("asian") < 900);
  }

  @Test
  public void pickEthnicity() {
    int hispanic = 0;
    for (int i = 0; i < 10000; i++) {
      String ethnicity = philly.pickEthnicity(random);
      if (ethnicity.equals("hispanic")) {
        hispanic++;
      }
    }
    assertTrue(hispanic > 1000 && hispanic < 1600);
  }

  @Test
  public void languageFromRaceAndEthnicity() {
    HashMap<String, Integer> languageMap = new HashMap<String, Integer>();
    for (int i = 0; i < 10000; i++) {
      String language = philly.languageFromRaceAndEthnicity("white", "hispanic", random);
      if (languageMap.containsKey(language)) {
        languageMap.put(language, languageMap.get(language) + 1);
      } else {
        languageMap.put(language, 1);
      }
    }
    assertTrue(languageMap.get("english") > 4600 && languageMap.get("english") < 5000);
    assertTrue(languageMap.get("spanish") > 4900 && languageMap.get("spanish") < 5300);

    languageMap = new HashMap<String, Integer>();
    for (int i = 0; i < 10000; i++) {
      String language = philly.languageFromRaceAndEthnicity("white", "nonhispanic", random);
      if (languageMap.containsKey(language)) {
        languageMap.put(language, languageMap.get(language) + 1);
      } else {
        languageMap.put(language, 1);
      }
    }
    assertTrue(languageMap.get("english") > 9500);

    languageMap = new HashMap<String, Integer>();
    for (int i = 0; i < 10000; i++) {
      String language = philly.languageFromRaceAndEthnicity("asian", "nonhispanic", random);
      if (languageMap.containsKey(language)) {
        languageMap.put(language, languageMap.get(language) + 1);
      } else {
        languageMap.put(language, 1);
      }
    }
    assertTrue(languageMap.get("english") > 6400 && languageMap.get("english") < 7000);
    assertTrue(languageMap.get("chinese") > 900 && languageMap.get("chinese") < 1900);
  }
}