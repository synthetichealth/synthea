package org.mitre.synthea.world.geography;

import com.google.common.collect.Table;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSONFactory;
import org.wololo.jts2geojson.GeoJSONReader;

public class Location {
  private FeatureCollection cities;
  private long totalPopulation;

  // cache the population by city name for performance
  private Map<String, Long> populationByCity;
  private Map<String, Feature> featuresByName;
  private Map<String, List<String>> zipCodes;
  
  private String city;
  private Map<String, Demographics> demographics;

  public Location(String state, String city) {
    try {
      this.city = city;
      
      Table<String,String,Demographics> allDemographics = Demographics.load(state);
      
      // this still works even if only 1 city given,
      // because allDemographics will only contain that 1 city
      this.demographics = allDemographics.row(state);

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
    
    // load the GeoJSON once so we can use it for all patients
    String filename = Config.get("generate.geography.borders.default_file");
    featuresByName = new HashMap<>();

    try {
      String json = Utilities.readResource(filename);
      cities = (FeatureCollection) GeoJSONFactory.create(json);

      for (Feature f : cities.getFeatures()) {
        String cityName = (String) f.getProperties().get("cs_name");
        featuresByName.put(cityName, f);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load geojson: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }

    try {
      filename = Config.get("generate.geography.zipcodes.default_file");
      String csv = Utilities.readResource(filename);
      List<? extends Map<String,String>> ziplist = SimpleCSV.parse(csv);

      zipCodes = new HashMap<>();
      for (Map<String,String> line : ziplist) {
        String lineState = line.get("USPS");
        
        if (!lineState.equals(state)) {
          continue;
        }
        
        String lineCity = line.get("NAME");
        String zip = line.get("ZCTA5");
        
        List<String> zipsForCity = zipCodes.get(lineCity);
        if (zipsForCity == null) {
          zipsForCity = new ArrayList<>();
          zipCodes.put(lineCity, zipsForCity);
        }
        
        zipsForCity.add(zip);
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
   * @param random Source of randomness
   * @return a zip code for the given city
   */
  public String getZipCode(String cityName, Random random) {
    List<String> zipsForCity = zipCodes.get(cityName);
    
    if (zipsForCity == null) {
      zipsForCity = zipCodes.get(cityName + " Town");
    }
    
    if (zipsForCity == null || zipsForCity.isEmpty()) {
      return "00000"; // if we don't have the city, just use a dummy
    } else if (zipsForCity.size() == 1) {
      return zipsForCity.get(0);
    } else {
      // pick a random one
      return zipsForCity.get(random.nextInt(zipsForCity.size()));
    }
  }

  public long getPopulation(String cityName) {
    return populationByCity.getOrDefault(cityName, 0L);
  }

  /**
   * Pick the name of a random city
   * @param random Source of randomness
   * @param overrideSingleCity If we only have one city being generated,
   * do we want to select from other cities 
   * @return
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
    Feature cityFeature = null;

    cityFeature = featuresByName.get(cityName);

    if (cityFeature == null) {
      cityFeature = featuresByName.get(cityName + " Town");
    }
    
    if (cityFeature == null) {
      // TODO - warning? 
      return;
    }
    
    GeoJSONReader reader = new GeoJSONReader();
    MultiPolygon geom = (MultiPolygon) reader.read(cityFeature.getGeometry());

    Polygon boundingBox = (Polygon) geom.getEnvelope();
    /*
     * If this Geometry is: empty, returns an empty Point. a point, returns a Point. a line parallel
     * to an axis, a two-vertex LineString otherwise, returns a Polygon whose vertices are (minx
     * miny, maxx miny, maxx maxy, minx maxy, minx miny).
     */
    Coordinate[] coords = boundingBox.getCoordinates();
    double minX = coords[0].x;
    double minY = coords[0].y;
    double maxX = coords[2].x;
    double maxY = coords[2].y;

    Point selectedPoint = null;

    do {
      double x = person.rand(minX, maxX);
      double y = person.rand(minY, maxY);
      selectedPoint = new GeometryFactory().createPoint(new Coordinate(x, y));
    } while (!geom.contains(selectedPoint));

    person.attributes.put(Person.COORDINATE, selectedPoint);
  }
}
