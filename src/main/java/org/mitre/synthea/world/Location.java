package org.mitre.synthea.world;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.Feature;
import org.opengis.geometry.BoundingBox;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;

public class Location {

	private static final FeatureCollection cities;
	private static final long totalPopulation;
	
	static {
		// load the GeoJSON once so we can use it for all patients
		String filename = "/geography/ma_geo.json";
		
		long runningPopulation = 0;
		
		try {
			FeatureJSON json = new FeatureJSON();
			InputStream stream = Location.class.getResourceAsStream(filename);
			cities = json.readFeatureCollection(stream);
			
			try (FeatureIterator itr = cities.features())
			{
				while (itr.hasNext())
				{
					Feature f = itr.next();
					
					runningPopulation += ((Double)f.getProperty("pop").getValue()).longValue();
				}
			}
			
			totalPopulation = runningPopulation;
			
		} catch (IOException e) {
			System.err.println("ERROR: unable to load geojson: " + filename);
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	public static String getZipCode(String cityName)
	{
		return "00000"; // TODO
	}
	
	public static PointWrapper selectPoint(String cityName)
	{
		Feature cityFeature = null;
		
		// randomly select a city if not provided
		if (cityName == null)
		{
			// TODO - make this population-based
			long targetPop = ThreadLocalRandom.current().nextLong(totalPopulation);
			
			try (FeatureIterator itr = cities.features())
			{
				while (itr.hasNext())
				{
					Feature f = itr.next();
					
					targetPop -= ((Double)f.getProperty("pop").getValue()).longValue();
					
					if (targetPop < 0)
					{
						cityFeature = f;
						cityName = (String)cityFeature.getProperty("cs_name").getValue();
						break;
					}
				}
			}
		} else
		{
			try (FeatureIterator itr = cities.features())
			{
				while (itr.hasNext())
				{
					Feature f = itr.next();
					
					String name = (String) f.getProperty("cs_name").getValue();
					
					if (name.equals(cityName) || name.equals(cityName + " Town"))
					{
						cityFeature = f;
						break;
					}
				}
			}
		}
		
		MultiPolygon geom = (MultiPolygon)cityFeature.getDefaultGeometryProperty().getValue();
		
		BoundingBox bounds = cityFeature.getBounds();
		
		double minX = bounds.getMinX();
		double minY = bounds.getMinY();
		double maxX = bounds.getMaxX();
		double maxY = bounds.getMaxY();
		
		Point selectedPoint = null;
		
		ThreadLocalRandom r = ThreadLocalRandom.current();
		do {
			double x = r.nextDouble(minX, maxX);
			double y = r.nextDouble(minY, maxY);
			selectedPoint = new GeometryFactory().createPoint(new Coordinate(x,y));
		} while (!geom.contains(selectedPoint));
		
		return new PointWrapper(cityName, selectedPoint);
	}
	
	public static class PointWrapper
	{
		public String city;
		public Point point;
		
		public PointWrapper(String city, Point point)
		{
			this.city = city;
			this.point = point;
		}
	}
}
