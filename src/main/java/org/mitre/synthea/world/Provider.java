package org.mitre.synthea.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.text.html.parser.Entity;

import org.mitre.synthea.modules.Person;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
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
	private Table<Integer, String, AtomicInteger> utilization; // row: year, column: type, value: count
	
	public Provider(LinkedTreeMap p) {
		LinkedTreeMap properties = (LinkedTreeMap) p.get("properties");
		attributes = properties;
		String resourceID = (String) p.get("resourceID");
		attributes.put("resourceID", resourceID);

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
			} else{
				ArrayList<Provider> l = new ArrayList<Provider>();
				l.add(this);
				services.put(s, l);
			}
		}
		
		utilization = HashBasedTable.create();
	}
	
	public static void clear(){
		providerList.clear();
		services.clear();
	}
	
	public String getResourceID(){
		return attributes.get("resourceID").toString();
	}
	
	public LinkedTreeMap getAttributes(){
		return attributes;
	}
	
	public Point getCoordinates(){
		return coordinates;
	}
	
	public boolean hasService(String service){
		return services_provided.contains(service);
	}
	
	public void incrementEncounters(String encounterType, int year)
	{
		increment(year, ENCOUNTERS);
		increment(year, ENCOUNTERS + "-" + encounterType);
	}
	
	public void incrementProcedures(int year)
	{	
		increment(year, PROCEDURES);
	}
	
	// TODO: increment labs when there are reports
	public void incrementLabs(int year)
	{	
		increment(year, LABS);
	}
	
	public void incrementPrescriptions(int year)
	{	
		increment(year, PRESCRIPTIONS);
	}
	
	private void increment(Integer year, String key)
	{
		if (!utilization.contains(year, key))
		{
			utilization.put(year, key, new AtomicInteger(0));
		}
		
		utilization.get(year, key).incrementAndGet();
	}
	
	public Table<Integer, String, AtomicInteger> getUtilization(){
		return utilization;
	}
	
	public Integer getBedCount(){
		if(attributes.containsKey("bed_count")){
			return Integer.parseInt(attributes.get("bed_count").toString());
		} else {
			return null;
		}
	}
	
	public static Provider findClosestService(Person person, String service){
		if( service.equals("outpatient") || service.equals("wellness")){
			service = AMBULATORY;
		}
		switch(service) {
		case AMBULATORY :
			if( person.getAmbulatoryProvider() == null ){
				person.setAmbulatoryProvider();
			}
			return person.getAmbulatoryProvider();
		case INPATIENT :
			if( person.getInpatientProvider() == null ){
				person.setInpatientProvider();
			}
			return person.getInpatientProvider();
		case EMERGENCY :
			if( person.getEmergencyProvider() == null ){
				person.setEmergencyProvider();
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