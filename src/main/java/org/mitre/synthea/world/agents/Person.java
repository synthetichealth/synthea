package org.mitre.synthea.world.agents;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.TemporalUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.engine.Event;
import org.mitre.synthea.engine.EventList;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.VitalSign;

import com.vividsolutions.jts.geom.Point;

public class Person implements Serializable
{
	private static final long serialVersionUID = 4322116644425686379L;

	public static final String BIRTHDATE = "birthdate";
	public static final String NAME = "name";
	public static final String RACE = "race";
	public static final String GENDER = "gender";
	public static final String ID = "id";
	public static final String ADDRESS = "address";
	public static final String CITY = "city";
	public static final String STATE = "state";
	public static final String ZIP = "zip";
	public static final String COORDINATE = "coordinate";
	public static final String SOCIOECONOMIC_SCORE = "socioeconomic_score";
	public static final String SOCIOECONOMIC_CATEGORY = "socioeconomic_category";
	public static final String INCOME = "income";
	public static final String INCOME_LEVEL = "income_level";
	public static final String EDUCATION = "education";
	public static final String EDUCATION_LEVEL = "education_level";
	public static final String OCCUPATION_LEVEL = "occupation_level";
	public static final String CHW_INTERVENTION = "CHW Intervention";
	public static final String SMOKER = "smoker";
	public static final String ALCOHOLIC = "alcoholic";
	public static final String ADHERENCE = "adherence";

	public final Random random;
	public final long seed;
	public Map<String,Object> attributes;
	public Map<VitalSign, Double> vitalSigns;
	private Map<String,Map<String,Integer>> symptoms;
	public EventList events;
	public HealthRecord record;
	/** history of the currently active module */
	public List<State> history;
	
	public Person(long seed) {
		this.seed = seed; // keep track of seed so it can be exported later
		random = new Random(seed);
		attributes = new ConcurrentHashMap<String,Object>();
		vitalSigns = new ConcurrentHashMap<VitalSign,Double>();
		symptoms = new ConcurrentHashMap<String,Map<String,Integer>>();
		events = new EventList();
		record = new HealthRecord();
	}

	public double rand() {
		return random.nextDouble();
	}
	
	public double rand(double low, double high) {
		return (low + ((high - low) * random.nextDouble()));
	}

	public Period age(long time)
	{
		Period age = Period.ZERO;
		
		if(attributes.containsKey(BIRTHDATE)) 
		{
			LocalDate now = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
			LocalDate birthdate = Instant.ofEpochMilli((long)attributes.get(BIRTHDATE)).atZone(ZoneId.systemDefault()).toLocalDate();
			age = Period.between(birthdate, now);
		}
		return age;
	}
	
	public int ageInMonths(long time)
	{
		return (int) age(time).toTotalMonths();
	}
	
	public int ageInYears(long time) 
	{
		return age(time).getYears();
	}
	
	public boolean alive(long time) {
		return (events.event(Event.BIRTH) != null && events.before(time, Event.DEATH).isEmpty());
	}
	
	public void setSymptom(String cause, String type, int value) {
		if(!symptoms.containsKey(type)) {
			symptoms.put(type, new ConcurrentHashMap<String,Integer>());
		}
		symptoms.get(type).put(cause, value);
	}
	
	public int getSymptom(String type) {
		int max = 0;
		if(symptoms.containsKey(type)) {
			Map<String,Integer> typedSymptoms = symptoms.get(type);
			for(String cause : typedSymptoms.keySet()) {
				if(typedSymptoms.get(cause) > max) {
					max = typedSymptoms.get(cause);
				}
			}
		}
		return max;
	}
	
	public Double getVitalSign(VitalSign vitalSign)
	{
		return vitalSigns.get(vitalSign);
	}
	
	public void setVitalSign(VitalSign vitalSign, double value)
	{
		vitalSigns.put(vitalSign, value);
	}
	
	public void recordDeath(long time, Code cause, String ruleName)
	{
		events.create(time, Event.DEATH, ruleName, true);

		if (record.death == null)
		{
			record.death = time;
		} else
		{
			// it's possible for a person to have a death date in the future 
			// (ex, a condition with some life expectancy sets a future death date)
			// but then the patient dies sooner because of something else
			record.death = Math.min(record.death, time);
		}
	}
	
	/**
	 * @return total : sum of all the symptom severities. This number drives care-seeking behaviors.
	 */
	public int symptomTotal() {
		int total = 0;
		for(String type : symptoms.keySet()) {
			total += getSymptom(type);			
		}
		return total;
	}

	public void resetSymptoms() {
		symptoms.clear();
	}

	public boolean hadPriorState(String name) {
		if(history == null) {
			return false;
		}
		for(State state : history) {
			if(state.name == name) {
				return true;
			}
		}
		return false;
	}

	public boolean hadPriorStateSince(String priorState, long time) {
		if(history == null) {
			return false;
		}
		for(State state : history) {
			if(state.name.equals(priorState)) {
				return true;
			} else if(state.exited > 0 && state.exited <= time) {
				// break out when we reach the desired time
				return false;
			}
		}
		return false;
	}

	public boolean hadPriorStateSince(String priorState, String sinceState) {
		if(history == null) {
			return false;
		}
		for(State state : history) {
			if(state.name.equals(priorState)) {
				return true;
			} else if(state.name.equals(sinceState)) {
				return false;
			}
		}
		return false;
	}
	
	public void chwEncounter(long time, String deploymentType)
	{
		CommunityHealthWorker chw = CommunityHealthWorker.findNearbyCHW(this, time, deploymentType);
		if(chw != null)
		{
			chw.performEncounter(this, time, deploymentType);
		}
	}
	
	// Providers API -----------------------------------------------------------
	public static final String CURRENTPROVIDER = "currentProvider";
	public static final String PREFERREDAMBULATORYPROVIDER = "preferredAmbulatoryProvider";
	public static final String PREFERREDINPATIENTPROVIDER = "preferredInpatientProvider";
	public static final String PREFERREDEMERGENCYPROVIDER = "preferredEmergencyProvider";
	
	
	public Provider getAmbulatoryProvider(){
		return (Provider) attributes.get(PREFERREDAMBULATORYPROVIDER);
	}
	
	public void setAmbulatoryProvider(){
		Point personLocation = (Point) attributes.get(Person.COORDINATE);
		Provider provider = Hospital.findClosestAmbulatory(personLocation);
		attributes.put(PREFERREDAMBULATORYPROVIDER, provider);
	}
	
	public void setAmbulatoryProvider(Provider provider){
		attributes.put(PREFERREDAMBULATORYPROVIDER, provider);
	}
	
	public Provider getInpatientProvider(){
		return (Provider) attributes.get(PREFERREDINPATIENTPROVIDER);
	}
	
	public void setInpatientProvider(){
		Point personLocation = (Point) attributes.get(Person.COORDINATE);
		Provider provider = Hospital.findClosestInpatient(personLocation);
		attributes.put(PREFERREDINPATIENTPROVIDER, provider);
	}
	
	public void setInpatientProvider(Provider provider){
		attributes.put(PREFERREDINPATIENTPROVIDER, provider);
	}
	
	public Provider getEmergencyProvider(){
		return (Provider) attributes.get(PREFERREDEMERGENCYPROVIDER);
	}

	public void setEmergencyProvider(){
		Point personLocation = (Point) attributes.get(Person.COORDINATE);
		Provider provider = Hospital.findClosestEmergency(personLocation);
		attributes.put(PREFERREDEMERGENCYPROVIDER, provider);
	}
	
	public void setEmergencyProvider(Provider provider){
		if(provider == null){
			Point personLocation = (Point) attributes.get(Person.COORDINATE);
			provider = Hospital.findClosestEmergency(personLocation);
		}
		attributes.put(PREFERREDEMERGENCYPROVIDER, provider);
	}
	
	public void addCurrentProvider(String context, Provider provider){
		if(attributes.containsKey(CURRENTPROVIDER)){
			Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
			currentProviders.put(context, provider);
			attributes.put(CURRENTPROVIDER, currentProviders);
		} else {
			Map<String, Provider> currentProviders = new HashMap<String, Provider>();
			currentProviders.put(context, provider);
			attributes.put(CURRENTPROVIDER, currentProviders);
		}
	}
	
	public void removeCurrentProvider(String module){
		Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
		currentProviders.remove(module);
	}
	
	public Provider getCurrentProvider(String module){
		Map<String, Provider> currentProviders = (Map) attributes.get(CURRENTPROVIDER);
		return currentProviders.get(module);
	}
}
