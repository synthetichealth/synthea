package org.mitre.synthea.world;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.vividsolutions.jts.geom.Point;


public class Hospital extends Provider{
	
	// ArrayList of all hospitals imported
	private static ArrayList<Hospital> hospitalList = new ArrayList<Hospital>();

	public Hospital(LinkedTreeMap p) {
		super(p);
	}
	
	public static void clear(){
		hospitalList.clear();
	}
	
	public static void loadHospitals(){
		String filename = "/geography/healthcare_facilities.json";
		try {
			InputStream stream = Hospital.class.getResourceAsStream(filename);
			// read all text into a string
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					.parallel().collect(Collectors.joining("\n"));
			Gson g = new Gson();
			HashMap<String, LinkedTreeMap> gson = g.fromJson(json, HashMap.class);
			for(Entry<String, LinkedTreeMap> entry : gson.entrySet()) {
				LinkedTreeMap value = entry.getValue();
				String resourceID = UUID.randomUUID().toString();
				value.put("resourceID", resourceID);
				Hospital h = new Hospital(value);
				hospitalList.add(h);
			}
		} catch (Exception e) {
			System.err.println("ERROR: unable to load json: " + filename);
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	// find closest hospital with ambulatory service
	public static Hospital findClosestAmbulatory(Point personLocation){
		Double personLat = personLocation.getY();
		Double personLong = personLocation.getX();
		
		Double closestDistance = 100000000.0;
		Provider closestHospital = null;
		for(Provider p : Provider.getServices().get(Provider.AMBULATORY)){
			Point hospitalLocation = p.getCoordinates();
			Double hospitalLat = hospitalLocation.getY();
			Double hospitalLong = hospitalLocation.getX();
			Double sphericalDistance = haversine(personLat, personLong, hospitalLat, hospitalLong);
			if( sphericalDistance < closestDistance ) {
				closestDistance = sphericalDistance;
				closestHospital = p;
			}
		}
		return (Hospital) closestHospital;
	}
	
	// find closest hospital with inpatient service
	public static Hospital findClosestInpatient(Point personLocation){
		Double personLat = personLocation.getY();
		Double personLong = personLocation.getX();
		
		Double closestDistance = 100000000.0;
		Provider closestHospital = null;
		for(Provider p : Provider.getServices().get(Provider.INPATIENT)){
			Point hospitalLocation = p.getCoordinates();
			Double hospitalLat = hospitalLocation.getY();
			Double hospitalLong = hospitalLocation.getX();
			Double sphericalDistance = haversine(personLat, personLong, hospitalLat, hospitalLong);
			if( sphericalDistance < closestDistance ) {
				closestDistance = sphericalDistance;
				closestHospital = p;
			}
		}
		return (Hospital) closestHospital;
	}
	
	// find closest hospital with emergency service
	public static Hospital findClosestEmergency(Point personLocation){
		Double personLat = personLocation.getY();
		Double personLong = personLocation.getX();
		
		Double closestDistance = 100000000.0;
		Provider closestHospital = null;
		for(Provider p : Provider.getServices().get(Provider.EMERGENCY)){
			Point hospitalLocation = p.getCoordinates();
			Double hospitalLat = hospitalLocation.getY();
			Double hospitalLong = hospitalLocation.getX();
			Double sphericalDistance = haversine(personLat, personLong, hospitalLat, hospitalLong);
			if( sphericalDistance < closestDistance ) {
				closestDistance = sphericalDistance;
				closestHospital = p;
			}
		}
		return (Hospital) closestHospital;
	}
	
	// Haversine Formula 
	// from https://rosettacode.org/wiki/Haversine_formula#Java
    public static final double R = 6372.8; // In kilometers
    
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);
 
        double a = Math.pow(Math.sin(dLat / 2),2) + Math.pow(Math.sin(dLon / 2),2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }
}