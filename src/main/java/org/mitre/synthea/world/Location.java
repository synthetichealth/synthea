package org.mitre.synthea.world;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.mitre.synthea.modules.CommunityHealthWorker;
import org.mitre.synthea.modules.Person;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.GeoJSONFactory;
import org.wololo.jts2geojson.GeoJSONReader;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;


public class Location {

	private static final FeatureCollection cities;
	private static final long totalPopulation;
	
	// cache the population by city name for performance
	private static final Map<String,Long> populationByCity;
	private static final Map<String,Feature> featuresByName;
	
	static {
		// load the GeoJSON once so we can use it for all patients
		String filename = "/geography/ma_geo.json";
		
		long runningPopulation = 0;
		populationByCity = new LinkedHashMap<>(); // linked to ensure consistent iteration order
		featuresByName = new HashMap<>(); 
		
		try 
		{
			InputStream stream = Location.class.getResourceAsStream(filename);
			// read all text into a string
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					   .parallel().collect(Collectors.joining("\n"));

		    cities = (FeatureCollection) GeoJSONFactory.create(json);

			for (Feature f : cities.getFeatures())
			{
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
	}
	
	public static String getZipCode(String cityName)
	{
		return "00000"; // TODO
	}
	
	public static long getPopulation(String cityName)
	{
		return populationByCity.getOrDefault(cityName, 0L);
	}
	
	public static String randomCityName(Random random)
	{
		long targetPop = (long) (random.nextDouble() * totalPopulation);
		
		for (Map.Entry<String, Long> city : populationByCity.entrySet())
		{
			targetPop -= city.getValue();
			
			if (targetPop < 0)
			{
				return city.getKey();
			}
		}

		// should never happen
		throw new RuntimeException("Unable to select a random city name.");
	}
	
	/**
	 * Assign a geographic location to the given Person. Location includes City, State, Zip, and Coordinate.
	 * If cityName is given, then Zip and Coordinate are restricted to valid values for that city.
	 * If cityName is not given, then picks a random city from the list of all cities.
	 * 
	 * @param person Person to assign location information
	 * @param cityName Name of the city, or null to choose one randomly
	 */
	public static void assignPoint(Person person, String cityName)
	{
		Feature cityFeature = null;
		
		// randomly select a city if not provided
		if (cityName == null)
		{
			long targetPop = (long) (person.rand() * totalPopulation);

			for (Map.Entry<String, Long> city : populationByCity.entrySet())
			{
				targetPop -= city.getValue();
				
				if (targetPop < 0)
				{
					cityName =  city.getKey();
					cityFeature = featuresByName.get(cityName);
					break;
				}
			}
		} else
		{
			cityFeature = featuresByName.get(cityName);
			
			if (cityFeature == null)
			{
				cityFeature = featuresByName.get(cityName + " Town");
			}
		}
		
		GeoJSONReader reader = new GeoJSONReader();
		MultiPolygon geom = (MultiPolygon) reader.read(cityFeature.getGeometry());

		Polygon boundingBox = (Polygon) geom.getEnvelope();
		/*
		 * If this Geometry is: 
				empty, returns an empty Point. 
				a point, returns a Point. 
				a line parallel to an axis, a two-vertex LineString 
				otherwise, returns a Polygon whose vertices are (minx miny, maxx miny, maxx maxy, minx maxy, minx miny). 
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
			selectedPoint = new GeometryFactory().createPoint(new Coordinate(x,y));
		} while (!geom.contains(selectedPoint));
		
		person.attributes.put(Person.CITY, cityName);
		person.attributes.put(Person.STATE, "MA");
		person.attributes.put(Person.ZIP, getZipCode(cityName));
		person.attributes.put(Person.COORDINATE, selectedPoint);
	}
	
	public static void assignCity(CommunityHealthWorker chw){

		Random random = new Random();
		String city = Location.randomCityName(random);
		chw.attributes.put(CommunityHealthWorker.CITY, city);
	}
}
