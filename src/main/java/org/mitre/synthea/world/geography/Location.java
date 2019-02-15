package org.mitre.synthea.world.geography;

import com.google.common.collect.Table;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.ArrayUtils;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;

public class Location {
  private static LinkedHashMap<String, String> stateAbbreviations = loadAbbreviations();
  private static Map<String, String> timezones = loadTimezones();
  private static Map<String, List<String>> foreignPlacesOfBirth = loadCitiesByLangauge();

  private long totalPopulation;

  // cache the population by city name for performance
  private Map<String, Long> populationByCity;
  private Map<String, List<Place>> zipCodes;

  public final String city;
  public final String state;
  private Map<String, Demographics> demographics;

  /**
   * Location is a set of demographic and place information.
   * @param state The full name of the state.
   *     e.g. "Ohio" and not an abbreviation.
   * @param city The full name of the city.
   *     e.g. "Columbus" or null for an entire state.
   */
  public Location(String state, String city) {
    try {
      this.city = city;
      this.state = state;
      
      Table<String,String,Demographics> allDemographics = Demographics.load(state);
      
      // this still works even if only 1 city given,
      // because allDemographics will only contain that 1 city
      this.demographics = allDemographics.row(state);

      if (city != null && !demographics.containsKey(city)) {
        throw new Exception("The city " + city + " was not found in the demographics file.");
      }

      long runningPopulation = 0;
      populationByCity = new LinkedHashMap<>(); // linked to ensure consistent iteration order
      for (Demographics d : this.demographics.values()) {
        long pop = d.population;
        runningPopulation += pop;
        populationByCity.put(d.city, pop);
      }
      
      totalPopulation = runningPopulation;
      
    } catch (Exception e) {
      System.err.println("ERROR: unable to load demographics");
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }

    String filename = null;
    try {
      filename = Config.get("generate.geography.zipcodes.default_file");
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> ziplist = SimpleCSV.parse(csv);

      zipCodes = new HashMap<>();
      for (Map<String,String> line : ziplist) {
        Place place = new Place(line);
        
        if (!place.sameState(state)) {
          continue;
        }
        
        if (!zipCodes.containsKey(place.name)) {
          zipCodes.put(place.name, new ArrayList<Place>());
        }
        zipCodes.get(place.name).add(place);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load zips csv: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }
  
  
  /**
   * Get the zip code for the given city name. 
   * If a city has more than one zip code, this picks a random one.
   * 
   * @param cityName Name of the city
   * @return a zip code for the given city
   */
  public String getZipCode(String cityName) {
    List<Place> zipsForCity = zipCodes.get(cityName);
    
    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    if (zipsForCity == null || zipsForCity.isEmpty()) {
      return "00000"; // if we don't have the city, just use a dummy
    } else if (zipsForCity.size() >= 1) {
      return zipsForCity.get(0).postalCode;
    }
    return "00000";
  }

  public long getPopulation(String cityName) {
    return populationByCity.getOrDefault(cityName, 0L);
  }

  /**
   * Pick the name of a random city from the current "world".
   * If only one city was selected, this will return that one city.
   * 
   * @param random Source of randomness
   * @return Demographics of a random city.
   */
  public Demographics randomCity(Random random) {
    if (city != null) {
      // if we're only generating one city at a time, just use that one city
      return demographics.get(city);
    }
    return demographics.get(randomCityName(random));
  }
  
  /**
   * Pick a random city name, weighted by population.
   * @param random Source of randomness
   * @return a city name
   */
  public String randomCityName(Random random) {
    long targetPop = (long) (random.nextDouble() * totalPopulation);

    for (Map.Entry<String, Long> city : populationByCity.entrySet()) {
      targetPop -= city.getValue();

      if (targetPop < 0) {
        return city.getKey();
      }
    }

    // should never happen
    throw new RuntimeException("Unable to select a random city name.");
  }

  /**
   * Pick a random birth place, weighted by population.
   * @param random Source of randomness
   * @return Array of Strings: [city, state, country, "city, state, country"]
   */
  public String[] randomBirthPlace(Random random) {
    String[] birthPlace = new String[4];
    birthPlace[0] = randomCityName(random);
    birthPlace[1] = this.state;
    birthPlace[2] = "US";
    birthPlace[3] = birthPlace[0] + ", " + birthPlace[1] + ", " + birthPlace[2];
    return birthPlace;
  }

  /**
   * Method which returns a city from the foreignPlacesOfBirth map if the map contains values
   * for an ethnicity.
   * In the case an ethnicity is not present the method returns the value from a call to
   * randomCityName().
   *
   * @param random the Random to base our city selection on
   * @param ethnicity the ethnicity to look for cities in
   * @return A String representing the place of birth
   */
  public String[] randomBirthplaceByEthnicity(Random random, String ethnicity) {
    String[] birthPlace;

    List<String> cities = foreignPlacesOfBirth.get(ethnicity.toLowerCase());
    if (cities != null && cities.size() > 0) {
      int upperBound = cities.size();
      String randomBirthPlace = cities.get(random.nextInt(upperBound));
      String[] split = randomBirthPlace.split(",");

      // make sure we have exactly 3 elements (city, state, country_abbr)
      // if not fallback to some random US location
      if (split.length != 3) {
        birthPlace = randomBirthPlace(random);
      } else {
        //concatenate all the results together, adding spaces behind commas for readability
        birthPlace = ArrayUtils.addAll(split,
            new String[] {randomBirthPlace.replaceAll(",", ", ")});
      }

    } else {  //if we can't find a foreign city at least return something
      birthPlace = randomBirthPlace(random);
    }

    return birthPlace;
  }

  /**
   * Assign a geographic location to the given Person. Location includes City, State, Zip, and
   * Coordinate. If cityName is given, then Zip and Coordinate are restricted to valid values for
   * that city. If cityName is not given, then picks a random city from the list of all cities.
   * 
   * @param person Person to assign location information
   * @param cityName Name of the city, or null to choose one randomly
   */
  public void assignPoint(Person person, String cityName) {
    List<Place> zipsForCity = null;

    if (cityName == null) {
      int size = zipCodes.keySet().size();
      cityName = (String) zipCodes.keySet().toArray()[person.randInt(size)];
    }
    zipsForCity = zipCodes.get(cityName);

    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    Place place = null;
    if (zipsForCity.size() == 1) {
      place = zipsForCity.get(0);
    } else {
      // pick a random one
      place = zipsForCity.get(person.randInt(zipsForCity.size()));
    }
    
    if (place != null) {
      person.attributes.put(Person.COORDINATE, place.getLatLon());
    }
  }

  /**
   * Assign a geographic location to the given Clinician. Location includes City, State, Zip, and
   * Coordinate. If cityName is given, then Zip and Coordinate are restricted to valid values for
   * that city. If cityName is not given, then picks a random city from the list of all cities.
   * 
   * @param clinician Clinician to assign location information
   * @param cityName Name of the city, or null to choose one randomly
   */
  public void assignPoint(Clinician clinician, String cityName) {
    List<Place> zipsForCity = null;

    if (cityName == null) {
      int size = zipCodes.keySet().size();
      cityName = (String) zipCodes.keySet().toArray()[clinician.randInt(size)];
    }
    zipsForCity = zipCodes.get(cityName);

    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    Place place = null;
    if (zipsForCity.size() == 1) {
      place = zipsForCity.get(0);
    } else {
      // pick a random one
      place = zipsForCity.get(clinician.randInt(zipsForCity.size()));
    }
    
    if (place != null) {
      clinician.attributes.put(Person.COORDINATE, place.getLatLon());
    }
  }
  
  private static LinkedHashMap<String, String> loadAbbreviations() {
    LinkedHashMap<String, String> abbreviations = new LinkedHashMap<String, String>();
    String filename = null;
    try {
      filename = Config.get("generate.geography.zipcodes.default_file");
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> ziplist = SimpleCSV.parse(csv);

      for (Map<String,String> line : ziplist) {
        String state = line.get("USPS");
        String abbreviation = line.get("ST");
        abbreviations.put(state, abbreviation);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load zips csv: " + filename);
      e.printStackTrace();
    }
    return abbreviations;
  }

  /**
   * Get the abbreviation for a state.
   * @param state State name. e.g. "Massachusetts"
   * @return state abbreviation. e.g. "MA"
   */
  public static String getAbbreviation(String state) {
    return stateAbbreviations.get(state);
  }
  
  /**
   * Get the index for a state. This maybe useful for
   * exporters where you want to generate a list of unique
   * identifiers that do not collide across state-boundaries.
   * @param state State name. e.g. "Massachusetts"
   * @return state index. e.g. 1 or 50
   */
  public static int getIndex(String state) {
    int index = 0;
    for (String stateName : stateAbbreviations.keySet()) {
      if (stateName.equals(state)) {
        return index;
      }
      index++;
    }
    return index;
  }

  /**
   * Get the state name from an abbreviation.
   * @param abbreviation State abbreviation. e.g. "MA"
   * @return state name. e.g. "Massachusetts"
   */
  public static String getStateName(String abbreviation) {
    for (String name : stateAbbreviations.keySet()) {
      if (stateAbbreviations.get(name).equalsIgnoreCase(abbreviation)) {
        return name;
      }
    }
    return null;
  }

  private static Map<String, String> loadTimezones() {
    HashMap<String, String> timezones = new HashMap<String, String>();
    String filename = null;
    try {
      filename = Config.get("generate.geography.timezones.default_file");
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> tzlist = SimpleCSV.parse(csv);

      for (Map<String,String> line : tzlist) {
        String state = line.get("STATE");
        String timezone = line.get("TIMEZONE");
        timezones.put(state, timezone);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load timezones csv: " + filename);
      e.printStackTrace();
    }
    return timezones;
  }

  private static Map<String, List<String>> loadCitiesByLangauge() {
    //get the default foreign_birthplace file if we can't get the file listed in the config
    String resource = Config.get("generate.geography.foreign.birthplace.default_file",
            "geography/foreign_birthplace.json");
    return loadCitiesByLanguage(resource);
  }

  /**
   * Load a resource which contains foreign places of birth based on ethnicity in json format:
   * <p/>
   * {"ethnicity":["city1,state1,country1", "city2,state2,country2"..., "cityN,stateN,countryN"]}
   * <p/>
   * see src/main/resources/foreign_birthplace.json for a working example
   * package protected for testing
   * @param resource A json file listing foreign places of birth by ethnicity.
   * @return Map of ethnicity to Lists of Strings "city,state,country"
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, List<String>> loadCitiesByLanguage(String resource) {
    Map<String, List<String>> foreignPlacesOfBirth = new HashMap<>();
    try {
      String json = Utilities.readResource(resource);
      foreignPlacesOfBirth = new Gson().fromJson(json, HashMap.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load foreign places of birth");
      e.printStackTrace();
    }

    return foreignPlacesOfBirth;
  }

  /**
   * Get the full name of the timezone by the full name of the state.
   * Timezones are approximate.
   * @param state The full name of the state (e.g. "Massachusetts")
   * @return The full name of the timezone (e.g. "Eastern Standard Time")
   */
  public static String getTimezoneByState(String state) {
    return timezones.get(state);
  }
}
