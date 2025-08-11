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

import org.apache.commons.lang3.ArrayUtils;
import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Employment;

/**
 * Represents a geographical location with associated data such as population.
 */
public class Location implements Serializable {
  private static final long serialVersionUID = 1L;
  private static LinkedHashMap<String, String> stateAbbreviations = loadAbbreviations();
  private static Map<String, String> timezones = loadTimezones();
  private static Map<String, List<String>> foreignPlacesOfBirth = loadCitiesByLanguage();
  private static final String COUNTRY_CODE = Config.get("generate.geography.country_code");
  private static CMSStateCodeMapper cmsStateCodeMapper = new CMSStateCodeMapper();

  /** Total population of the location. */
  private long totalPopulation;

  /** Cache the population by city name for performance */
  @JSONSkip
  private Map<String, Long> populationByCity;

  /** Cache of population by city ID for performance. */
  @JSONSkip
  private Map<String, Long> populationByCityId;

  /** Cache of zip codes by city name. */
  @JSONSkip
  private Map<String, List<Place>> zipCodes;

  /** The name of the city. */
  public final String city;

  /** Demographics for the city represented by the above variable. */
  private Demographics fixedCity;

  /** The name of the state. */
  public final String state;

  /** Map of CityId to Demographics. */
  @JSONSkip
  private Map<String, Demographics> demographics;

  /** Map of County Name to attributes and probabilities. */
  @JSONSkip
  private Map<String, Map<String, Double>> socialDeterminantsOfHealth;

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
        throw new Exception("The city " + city
            + " was not found in the demographics file for state " + state + ".");
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
      String csv = Utilities.readResource(filename, true, true);
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

    socialDeterminantsOfHealth = new HashMap<String, Map<String, Double>>();
    try {
      filename = Config.get("generate.geography.sdoh.default_file",
        "geography/sdoh.csv");
      String csv = Utilities.readResource(filename, true, true);
      List<? extends Map<String,String>> sdohList = SimpleCSV.parse(csv);

      for (Map<String,String> line : sdohList) {
        String lineState = line.remove("STATE");
        if (!lineState.equalsIgnoreCase(state)) {
          continue;
        }
        line.remove("FIPS_CODE");
        line.remove("COUNTY_CODE");
        String county = line.remove("COUNTY");
        line.remove("ST");

        Map<String, Double> sdoh = new HashMap<String, Double>();
        for (String attribute : line.keySet()) {
          Double probability = Double.parseDouble(line.get(attribute));
          sdoh.put(attribute.toLowerCase(), probability);
        }

        socialDeterminantsOfHealth.put(county, sdoh);
      }
    } catch (Exception e) {
      System.err.println("WARNING: unable to load SDoH csv: " + filename);
      e.printStackTrace();
    }

    if (!socialDeterminantsOfHealth.isEmpty()) {
      Map<String, Double> averages = new HashMap<String, Double>();
      for (String county : socialDeterminantsOfHealth.keySet()) {
        Map<String, Double> determinants = socialDeterminantsOfHealth.get(county);
        for (String determinant : determinants.keySet()) {
          Double probability = determinants.get(determinant);
          Double sum = averages.getOrDefault(determinant, 0.0);
          averages.put(determinant, probability + sum);
        }
      }
      for (String determinant : averages.keySet()) {
        Double probability = averages.get(determinant);
        averages.put(determinant, (probability / socialDeterminantsOfHealth.keySet().size()));
      }
      socialDeterminantsOfHealth.put("AVERAGE", averages);
    } else {
      // An SDoH file was not provided, and a non-fatal exception was caught above.
      // This can occur with older synthea-international configurations.
      String[] determinants = { Person.FOOD_INSECURITY, Person.SEVERE_HOUSING_COST_BURDEN,
          Person.UNEMPLOYED, Person.NO_VEHICLE_ACCESS, Person.UNINSURED };
      Map<String, Double> averages = new HashMap<String, Double>();
      for (String determinant : determinants) {
        averages.put(determinant, 0.5);
      }
      socialDeterminantsOfHealth.put("AVERAGE", averages);
    }
  }


  /**
   * Get the zip code for the given city name.
   * If a city has more than one zip code, this picks a random one.
   *
   * @param cityName Name of the city
   * @param random Used for a source of repeatable randomness when selecting
   *               a zipcode when multiple exist for a location
   * @return a zip code for the given city
   */
  public String getZipCode(String cityName, RandomNumberGenerator random) {
    List<String> zipsForCity = getZipCodes(cityName);
    if (zipsForCity.size() > 1) {
      int randomChoice = random.randInt(zipsForCity.size());
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

    if (zipsForCity != null && zipsForCity.size() >= 1) {
      for (Place place : zipsForCity) {
        if (place.postalCode != null && !place.postalCode.isEmpty()) {
          results.add(place.postalCode);
        }
      }
    }

    if (results.isEmpty()) {
      results.add("00000"); // if we don't have the city, just use a dummy
    }

    return results;
  }

  /**
   * Gets the population of a specific city.
   * @param cityName The name of the city.
   * @return The population of the city.
   */
  public long getPopulation(String cityName) {
    return populationByCity.getOrDefault(cityName, 0L);
  }

  /**
   * Pick the name of a random city from the current "world".
   * If only one city was selected, this will return that one city.
   *
   * @param random The source of randomness.
   * @return Demographics of a random city.
   */
  public Demographics randomCity(RandomNumberGenerator random) {
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
   * @param random the source of randomness
   * @return a city name
   */
  public String randomCityName(RandomNumberGenerator random) {
    String cityId = randomCityId(random);
    return demographics.get(cityId).city;
  }

  /**
   * Pick a random city id, weighted by population.
   * @param random the source of randomness
   * @return a city id
   */
  private String randomCityId(RandomNumberGenerator random) {
    long targetPop = (long) (random.rand() * totalPopulation);

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
   * @param random the source of randomness
   * @return Array of Strings: [city, state, country, "city, state, country"]
   */
  public String[] randomBirthPlace(RandomNumberGenerator random) {
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
   * @param random the source of randomness
   * @param language the language to look for cities in
   * @return A String representing the place of birth
   */
  public String[] randomBirthplaceByLanguage(RandomNumberGenerator random, String language) {
    String[] birthPlace;

    List<String> cities = foreignPlacesOfBirth.get(language.toLowerCase());
    if (cities != null && cities.size() > 0) {
      int upperBound = cities.size();
      String randomBirthPlace = cities.get(random.randInt(upperBound));
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
    if (zipsForCity != null && zipsForCity.size() == 1) {
      place = zipsForCity.get(0);
    } else if (zipsForCity != null) {
      String personZip = (String) person.attributes.get(Person.ZIP);
      if (personZip == null) {
        place = zipsForCity.get(person.randInt(zipsForCity.size()));
      } else {
        place = zipsForCity.stream()
            .filter(c -> personZip.equals(c.postalCode))
            .findFirst()
            .orElse(zipsForCity.get(person.randInt(zipsForCity.size())));
      }
    } else {
      // The place doesn't exist for some reason, pick a random location...
      String key = (String) zipCodes.keySet().toArray()[person.randInt(zipCodes.keySet().size())];
      place = zipCodes.get(key).get(person.randInt(zipCodes.get(key).size()));
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
   * Set social determinants of health attributes on the patient, as defined
   * by the optional social determinants of health county-level file.
   * @param person The person to assign attributes.
   */
  public void setSocialDeterminants(Person person) {
    String county = (String) person.attributes.get(Person.COUNTY);
    if (county == null || !socialDeterminantsOfHealth.containsKey(county)) {
      county = "AVERAGE";
    }
    Map<String, Double> sdoh = socialDeterminantsOfHealth.get(county);
    if (sdoh != null) {
      for (String determinant : sdoh.keySet()) {
        Double probability = sdoh.get(determinant);
        if (determinant.equals(Person.UNEMPLOYED)) {
          if (probability == null) {
            throw new IllegalStateException("Unable to determine unemployment probability");
          }
          person.attributes.put(Person.UNEMPLOYED, false);
          person.attributes.put(Person.EMPLOYMENT_MODEL, new Employment(probability));
        }
        person.attributes.put(determinant, (person.rand() <= probability));
      }
    }
  }

  private static LinkedHashMap<String, String> loadAbbreviations() {
    LinkedHashMap<String, String> abbreviations = new LinkedHashMap<String, String>();
    String filename = null;
    try {
      filename = Config.get("generate.geography.zipcodes.default_file");
      String csv = Utilities.readResource(filename, true, true);
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
      String csv = Utilities.readResource(filename, true, true);
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
   * <p>
   * {"ethnicity":["city1,state1,country1", "city2,state2,country2"..., "cityN,stateN,countryN"]}
   * </p>
   * see src/main/resources/foreign_birthplace.json for a working example
   * package protected for testing
   * @param resource A json file listing foreign places of birth by ethnicity.
   * @return Map of ethnicity to Lists of Strings "city,state,country"
   */
  @SuppressWarnings("unchecked")
  protected static Map<String, List<String>> loadCitiesByLanguage(String resource) {
    Map<String, List<String>> foreignPlacesOfBirth = new HashMap<>();
    try {
      String json = Utilities.readResourceOrPath(resource);
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

  /**
   * Get the FIPS code, if it exists, for a given zip code.
   * @param zipCode The zip code of the location.
   * @return The FIPS county code of the location.
   */
  public static String getFipsCodeByZipCode(String zipCode) {
    return Location.cmsStateCodeMapper.zipToFipsCountyCode(zipCode);
  }
}