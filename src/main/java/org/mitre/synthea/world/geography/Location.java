package org.mitre.synthea.world.geography;

import com.google.common.collect.Table;
import com.google.gson.Gson;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
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

public class Location implements Serializable {
  private static final long serialVersionUID = 1L;
  private static LinkedHashMap<String, String> stateAbbreviations = loadAbbreviations();
  private static Map<String, String> timezones = loadTimezones();
  private static Map<String, List<String>> foreignPlacesOfBirth = loadCitiesByLanguage();
  private static final String COUNTRY_CODE = Config.get("generate.geography.country_code");

  private long totalPopulation;

  // cache the population by city name for performance
  private Map<String, Long> populationByCity;
  private Map<String, Long> populationByCityId;
  private Map<String, List<Place>> zipCodes;

  public final String city;
  private Demographics fixedCity;
  public final String state;
  /** Map of CityId to Demographics. */
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
      // we copy the Map returned by the Google Table.row since the implementing
      // class is not serializable
      this.demographics = new HashMap<String, Demographics>(allDemographics.row(state));

      if (city != null 
          && demographics.values().stream().noneMatch(d -> d.city.equalsIgnoreCase(city))) {
        throw new Exception("The city " + city + " was not found in the demographics file.");
      }

      long runningPopulation = 0;
      // linked to ensure consistent iteration order
      populationByCity = new LinkedHashMap<>();
      populationByCityId = new LinkedHashMap<>();
      // sort the demographics to ensure tests pass regardless of implementing class
      // for this.demographics, see comment above on non-serializability of Google Table.row
      ArrayList<Demographics> sortedDemographics =
          new ArrayList<Demographics>(this.demographics.values());
      Collections.sort(sortedDemographics);
      for (Demographics d : sortedDemographics) {
        long pop = d.population;
        runningPopulation += pop;
        if (populationByCity.containsKey(d.city)) {
          populationByCity.put(d.city, pop + populationByCity.get(d.city));
        } else {
          populationByCity.put(d.city, pop);          
        }
        populationByCityId.put(d.id, pop);
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
   * @param person Used for a source of repeatable randomness when selecting a zipcode when multiple
   *               exist for a location
   * @return a zip code for the given city
   */
  public String getZipCode(String cityName, Person person) {
    List<String> zipsForCity = getZipCodes(cityName);
    if (zipsForCity.size() > 1) {
      int randomChoice = person.randInt(zipsForCity.size());
      return zipsForCity.get(randomChoice);
    } else {
      return zipsForCity.get(0);
    }
  }

  /**
   * Get the list of zip codes (or postal codes) by city name.
   * @param cityName Name of the city.
   * @return List of legal zip codes or postal codes.
   */
  public List<String> getZipCodes(String cityName) {
    List<String> results = new ArrayList<String>();
    List<Place> zipsForCity = zipCodes.get(cityName);

    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }

    if (zipsForCity == null || zipsForCity.isEmpty()) {
      results.add("00000"); // if we don't have the city, just use a dummy
    } else if (zipsForCity.size() >= 1) {
      for (Place place : zipsForCity) {
        results.add(place.postalCode);
      }
    }

    return results;
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
      // if we're only generating one city at a time, just use the largest entry for that one city
      if (fixedCity == null) {
        fixedCity = demographics.values().stream()
          .filter(d -> d.city.equalsIgnoreCase(city))
          .sorted().findFirst().get();
      }
      return fixedCity;
    }
    return demographics.get(randomCityId(random));
  }
  
  /**
   * Pick a random city name, weighted by population.
   * @param random Source of randomness
   * @return a city name
   */
  public String randomCityName(Random random) {
    String cityId = randomCityId(random);
    return demographics.get(cityId).city;
  }

  /**
   * Pick a random city id, weighted by population.
   * @param random Source of randomness
   * @return a city id
   */
  private String randomCityId(Random random) {
    long targetPop = (long) (random.nextDouble() * totalPopulation);

    for (Map.Entry<String, Long> city : populationByCityId.entrySet()) {
      targetPop -= city.getValue();

      if (targetPop < 0) {
        return city.getKey();
      }
    }

    // should never happen
    throw new RuntimeException("Unable to select a random city id.");
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
    birthPlace[2] = COUNTRY_CODE;
    birthPlace[3] = birthPlace[0] + ", " + birthPlace[1] + ", " + birthPlace[2];
    return birthPlace;
  }

  /**
   * Method which returns a city from the foreignPlacesOfBirth map if the map contains values
   * for an language.
   * In the case an language is not present the method returns the value from a call to
   * randomCityName().
   *
   * @param random the Random to base our city selection on
   * @param language the language to look for cities in
   * @return A String representing the place of birth
   */
  public String[] randomBirthplaceByLanguage(Random random, String language) {
    String[] birthPlace;

    List<String> cities = foreignPlacesOfBirth.get(language.toLowerCase());
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
    List<Place> zipsForCity;

    if (cityName == null) {
      int size = zipCodes.keySet().size();
      cityName = (String) zipCodes.keySet().toArray()[person.randInt(size)];
    }
    zipsForCity = zipCodes.get(cityName);

    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    Place place;
    if (zipsForCity.size() == 1) {
      place = zipsForCity.get(0);
    } else {
      String personZip = (String) person.attributes.get(Person.ZIP);
      if (personZip == null) {
        place = zipsForCity.get(person.randInt(zipsForCity.size()));
      } else {
        place = zipsForCity.stream()
            .filter(c -> personZip.equals(c.postalCode))
            .findFirst()
            .orElse(zipsForCity.get(person.randInt(zipsForCity.size())));
      }
    }
    
    if (place != null) {
      // Get the coordinate of the city/town
      Point2D.Double coordinate = new Point2D.Double();
      coordinate.setLocation(place.coordinate);
      // And now perturbate it slightly.
      // Precision within 0.001 degree is more or less a neighborhood or street.
      // Precision within 0.01 is a village or town
      // Precision within 0.1 is a large city
      double dx = person.rand(-0.05, 0.05);
      double dy = person.rand(-0.05, 0.05);
      coordinate.setLocation(coordinate.x + dx, coordinate.y + dy);
      person.attributes.put(Person.COORDINATE, coordinate);
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
      // Get the coordinate of the city/town
      Point2D.Double coordinate = new Point2D.Double();
      coordinate.setLocation(place.coordinate);
      // And now perturbate it slightly.
      // Precision within 0.001 degree is more or less a neighborhood or street.
      // Precision within 0.01 is a village or town
      // Precision within 0.1 is a large city
      double dx = (clinician.rand() * 0.1) - 0.05;
      double dy = (clinician.rand() * 0.1) - 0.05;
      coordinate.setLocation(coordinate.x + dx, coordinate.y + dy);
      clinician.attributes.put(Person.COORDINATE, coordinate);
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

  private static Map<String, List<String>> loadCitiesByLanguage() {
    //get the default foreign_birthplace file if we can't get the file listed in the config
    String resource = Config.get("generate.geography.foreign.birthplace.default_file",
            "geography/foreign_birthplace.json");
    return loadCitiesByLanguage(resource);
  }

  /**
   * Load a resource which contains foreign places of birth based on ethnicity in json format:
   * <p></p>
   * {"ethnicity":["city1,state1,country1", "city2,state2,country2"..., "cityN,stateN,countryN"]}
   * <p></p>
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
