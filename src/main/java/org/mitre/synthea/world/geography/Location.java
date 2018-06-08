package org.mitre.synthea.world.geography;

import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class Location {
  private static Map<String, String> stateAbbreviations = loadAbbreviations();

  private long totalPopulation;

  // cache the population by city name for performance
  private Map<String, Long> populationByCity;
  private Map<String, List<Place>> zipCodes;

  private String city;
  private String state;
  private Map<String, Demographics> demographics;
  
  private static final Map<String,String> STATE_MUNICIPALITY_LABEL = getStateSpecificLabels();

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
  
  private static Map<String,String> getStateSpecificLabels() {
    // set of labels that are often appended to municipality names
    // in each state
    
    Map<String,String> labels = new HashMap<>();
    labels.put("Massachusetts", "Town");
    // TODO: there are some Villages too in MD, TX, 
    
    List<String> statesWithVillages = Arrays.asList("Florida", "Illinois", "Louisiana",
        "Maryland", "Michigan", "Mississippi", "Missouri", "Nebraska", "New Mexico",
        "New York", "North Carolina", "Ohio", "Texas", "Vermont", "West Virginia", "Wisconsin");
    statesWithVillages.forEach(s -> labels.put(s, "village"));
    
    List<String> statesWithBoroughs = Arrays.asList("Connecticut", "New Jersey", "Pennsylvania");
    statesWithBoroughs.forEach(s -> labels.put(s, "borough"));
    
    return labels;
  }
  
  /**
   * Get the zip code for the given city name. 
   * If a city has more than one zip code, this picks a random one.
   * 
   * @param cityName Name of the city
   * @return a zip code for the given city
   */
  public String getZipCode(String cityName) {
    List<Place> zipsForCity = getZipsForCity(cityName);
    
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
   * Assign a geographic location to the given Person. Location includes City, State, Zip, and
   * Coordinate. If cityName is given, then Zip and Coordinate are restricted to valid values for
   * that city. If cityName is not given, then picks a random city from the list of all cities.
   * 
   * @param person
   *          Person to assign location information
   * @param cityName
   *          Name of the city, or null to choose one randomly
   */
  public void assignPoint(Person person, String cityName) {
    if (cityName == null) {
      int size = zipCodes.keySet().size();
      cityName = (String) zipCodes.keySet().toArray()[person.randInt(size)];
    }

    List<Place> zipsForCity = getZipsForCity(cityName);
    if (zipsForCity == null) {
      throw new RuntimeException("Unable to find zipcodes entry for " 
          + cityName + ", " + state);
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
  
  private List<Place> getZipsForCity(String cityName) {
    if (zipCodes.containsKey(cityName)) {
      return zipCodes.get(cityName);
    }
    
    // try adding/removing an extra label, ex Town, village, borough, etc
    String extraLabel = STATE_MUNICIPALITY_LABEL.get(state);
    
    if (cityName.endsWith(extraLabel)) {
      cityName = cityName.substring(0, cityName.length() - extraLabel.length() - 1);
    } else {
      cityName = cityName + " " + extraLabel;
    }
    
    return zipCodes.get(cityName);
  }

  private static Map<String, String> loadAbbreviations() {
    Map<String, String> abbreviations = new HashMap<String, String>();
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
}
