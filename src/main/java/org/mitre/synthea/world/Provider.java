package org.mitre.synthea.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.text.html.parser.Entity;

import org.mitre.synthea.modules.Person;

import com.google.gson.internal.LinkedTreeMap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class Provider {
	
	public static final String AMBULATORY = "ambulatory";
	public static final String INPATIENT = "inpatient";
	public static final String EMERGENCY = "emergency";
	public static final String ENCOUNTERS = "encounters";
	public static final String PROCEDURES = "procedures";
	public static final String LABS = "labs";
	public static final String PRESCRIPTIONS = "prescriptions";
	
	// ArrayList of all providers imported
	private static ArrayList<Provider> providerList = new ArrayList<Provider>();
	// Hash of services to Providers that provide them
	private static HashMap<String, ArrayList<Provider>> services = new HashMap<String, ArrayList<Provider>>();
	
	private LinkedTreeMap attributes;
	private Point coordinates;
	private ArrayList<String> services_provided;
	private Map<String, Integer> utilization;
	
	public Provider(LinkedTreeMap p) {
		LinkedTreeMap properties = (LinkedTreeMap) p.get("properties");
		attributes = properties;

		ArrayList<Double> coorList = (ArrayList<Double>) p.get("coordinates");
		Point coor = new GeometryFactory().createPoint(new Coordinate(coorList.get(0), coorList.get(1)));
		coordinates = coor;
		
		services_provided = new ArrayList<String>();
		String[] servicesList = ( (String) properties.get("services_provided") ).split(" ");
		for(String s : servicesList){
			services_provided.add(s);
			// add provider to hash of services
			if (services.containsKey(s)){
				ArrayList<Provider> l = services.get(s);
				l.add(this);
				services.put(s, l);
			} else{
				ArrayList<Provider> l = new ArrayList<Provider>();
				l.add(this);
				services.put(s, l);
			}
		}
		
		utilization = new HashMap<String, Integer>();
		// TODO: person begins with one wellness encounter, change to 0 when code to count wellness encounters is added
		utilization.put(ENCOUNTERS, 1);
		utilization.put(PROCEDURES, 0);
		utilization.put(LABS, 0);
		utilization.put(PRESCRIPTIONS, 0);
	}
	
	public static void clear(){
		providerList.clear();
		services.clear();
	}
	
	public Point getCoordinates(){
		return coordinates;
	}
	
	public boolean hasService(String service){
		return services_provided.contains(service);
	}
	
	public void incrementEncounters(){
		int count = utilization.get(ENCOUNTERS) + 1;
		utilization.put(ENCOUNTERS, count);
	}
	
	public void incrementProcedures(){
		int count = utilization.get(PROCEDURES) + 1;
		utilization.put(PROCEDURES, count);
	}
	
	// TODO: increment labs when there are reports
	public void incrementLabs(){
		int count = utilization.get(LABS) + 1;
		utilization.put(LABS, count);
	}
	
	public void incrementPrescriptions(){
		int count = utilization.get(PRESCRIPTIONS) + 1;
		utilization.put(PRESCRIPTIONS, count);
	}
	
	public Map<String, Integer> getUtilization(){
		return utilization;
	}
	
	public static Provider findClosestService(Person person, String service){
		if( service == "outpatient"){
			service = AMBULATORY;
		}
		switch(service) {
		case AMBULATORY :
			if( person.getAmbulatoryProvider() == null ){
				person.setAmbulatoryProvider(null);
			}
			return person.getAmbulatoryProvider();
		case INPATIENT :
			if( person.getInpatientProvider() == null ){
				person.setInpatientProvider(null);
			}
			return person.getInpatientProvider();
		case EMERGENCY :
			if( person.getEmergencyProvider() == null ){
				person.setEmergencyProvider(null);
			}
			return person.getEmergencyProvider();
		}
		// if service is null or not supported by simulation, patient goes to ambulatory hospital
		return person.getAmbulatoryProvider();
	}
	
	public static HashMap<String, ArrayList<Provider>> getServices(){
		return services;
	}
}