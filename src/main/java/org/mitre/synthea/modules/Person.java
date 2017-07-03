package org.mitre.synthea.modules;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Person {
	
	public static final String BIRTHDATE = "birthdate";
	public static final String NAME = "name";
	public static final String SOCIOECONOMIC_CATEGORY = "socioeconomic_category";
	public static final String RACE = "race";
	public static final String GENDER = "gender";
	
	private Random random;
	public Map<String,Object> attributes;
	private Map<String,Map<String,Integer>> symptoms;
	public EventList events;
	public HealthRecord record;
	
	public Person(long seed) {
		random = new Random(seed);
		attributes = new ConcurrentHashMap<String,Object>();
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
	
	public long ageInMilliseconds(long time) {
		long age = 0;
		if(attributes.containsKey(BIRTHDATE)) {
			age = time - (long)attributes.get(BIRTHDATE);
		}
		return age;
	}
	
	public int ageInYears(long time) {
		long age = ageInMilliseconds(time);
		return (int) (TimeUnit.MILLISECONDS.toDays(age) / 365);
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
}
