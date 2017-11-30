package org.mitre.synthea.world.geography;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.CommunityHealthWorker;
import org.mitre.synthea.world.agents.Person;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSONFactory;
import org.wololo.jts2geojson.GeoJSONReader;

public class Location {

  private static final FeatureCollection cities;
  private static final long totalPopulation;

  // cache the population by city name for performance
  private static final Map<String, Long> populationByCity;
  private static final Map<String, Feature> featuresByName;
  private static final Map<String, List<String>> zipCodes;

  static {
    // load the GeoJSON once so we can use it for all patients
    String filename = "geography/ma_geo.json";

    long runningPopulation = 0;
    populationByCity = new LinkedHashMap<>(); // linked to ensure consistent iteration order
    featuresByName = new HashMap<>();

    try {
      String json = Utilities.readResource(filename);
      cities = (FeatureCollection) GeoJSONFactory.create(json);

      for (Feature f : cities.getFeatures()) {
        long pop = ((Double) f.getProperties().get("pop")).longValue();
        runningPopulation += pop;

        String cityName = (String) f.getProperties().get("cs_name");
        populationByCity.put(cityName, pop);
        featuresByName.put(cityName, f);
      }

      totalPopulation = runningPopulation;

    } catch (Exception e) {
      System.err.println("ERROR: unable to load geojson: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }

    try {
      filename = "geography/ma_zip.json";
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      zipCodes = g.fromJson(json, LinkedTreeMap.class);
      
    } catch (Exception e) {
      System.err.println("ERROR: unable to load zips json: " + filename);
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
  public static String getZipCode(String cityName, Random random) {
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

  public static long getPopulation(String cityName) {
    return populationByCity.getOrDefault(cityName, 0L);
  }

  /**
   * Pick a random city name, weighted by population.
   * @param random Source of randomness
   * @return a city name
   */
  public static String randomCityName(Random random) {
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
  public static void assignPoint(Person person, String cityName) {
    Feature cityFeature = null;

    // randomly select a city if not provided
    if (cityName == null) {
      long targetPop = (long) (person.rand() * totalPopulation);

      for (Map.Entry<String, Long> city : populationByCity.entrySet()) {
        targetPop -= city.getValue();

        if (targetPop < 0) {
          cityName = city.getKey();
          cityFeature = featuresByName.get(cityName);
          break;
        }
      }
    } else {
      cityFeature = featuresByName.get(cityName);

      if (cityFeature == null) {
        cityFeature = featuresByName.get(cityName + " Town");
      }
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

    person.attributes.put(Person.CITY, cityName);
    person.attributes.put(Person.STATE, "MA");
    person.attributes.put(Person.ZIP, getZipCode(cityName, person.random));
    person.attributes.put(Person.COORDINATE, selectedPoint);
  }

  public static void assignCity(CommunityHealthWorker chw) {

    Random random = new Random();
    String city = Location.randomCityName(random);
    chw.attributes.put(CommunityHealthWorker.CITY, city);
  }
}
