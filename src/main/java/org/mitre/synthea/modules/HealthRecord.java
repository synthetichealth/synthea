package org.mitre.synthea.modules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

/**
 * HealthRecord contains all the coded entries in a person's health record.
 * This class represents a logical health record. Exporters will convert
 * this health record into various standardized formats.
 */
public class HealthRecord {
	/**
	 * HealthRecord.Code represents a system, code, and display value.
	 */
	public class Code {
		/** Code System (e.g. LOINC, RxNorm, SNOMED) identifier (typically a URI) */
		public String system;
		/** The code itself */
		public String code;
		/** The human-readable description of the code */
		public String display;
		/**
		 * Create a new code.
		 * @param system the URI identifier of the code system
		 * @param code the code itself
		 * @param display human-readable description of the coe
		 */
		public Code(String system, String code, String display) {
			this.system = system;
			this.code = code;
			this.display = display;
		}
		/**
		 * Create a new code from JSON.
		 * @param definition JSON object that contains 'system', 'code', and 'display' attributes.
		 */
		public Code(JsonObject definition) {
			this.system = definition.get("system").getAsString();
			this.code = definition.get("code").getAsString();
			this.display = definition.get("display").getAsString();
		}
		public boolean equals(Code other) {
			return this.system == other.system && this.code == other.code;
		}
		public String toString() {
			return String.format("%s %s %s", system, code, display);
		}
	}
	
	/**
	 * All things within a HealthRecord are instances of Entry.
	 * For example, Observations, Reports, Medications, etc.
	 * All Entries have a name, start and stop times, a type, and a list
	 * of associated codes.
	 */
	public class Entry {
		public String name;
		public long start;
		public long stop;
		public String type;
		public List<Code> codes;
		
		public Entry(long start, String type) {
			this.start = start;
			this.type = type;
			this.codes = new ArrayList<Code>();
		}
		public String toString() {
			return String.format("%s %s", Instant.ofEpochMilli(start).toString(), type);
		}
	}
	
	public class Observation extends Entry {
		public Object value;
		
		public Observation(long time, String type, Object value) {
			super(time, type);
			this.value = value;
		}
	}
	
	public class Report extends Entry {
		public List<Observation> observations;
		public Report(long time, String type, List<Observation> observations) {
			super(time, type);
			this.observations = observations;
		}
	}
	
	public class Medication extends Entry {
		public Set<String> reasons;
		public String stopReason;
		public Medication(long time, String type, Set<String> reasons) {
			super(time, type);
			this.reasons = reasons;
		}
	}
	
	public class CarePlan extends Entry {
		public Set<String> activities;
		public Set<String> reasons;
		public Set<String> goals;
		public String stopReason;
		public CarePlan(long time, String type, Set<String> activities) {
			super(time, type);
			this.activities = activities;
			this.reasons = new LinkedHashSet<String>();
			this.goals = new LinkedHashSet<String>();
		}
	}
	
	public enum EncounterType { WELLNESS, EMERGENCY, INPATIENT, AMBULATORY };
	
	public class Encounter extends Entry {
		public List<Observation> observations;
		public List<Report> reports;
		public List<Entry> conditions;
		public List<Entry> procedures;
		public List<Entry> immunizations;
		public List<Medication> medications;
		public List<CarePlan> careplans;
		public String reason;
		public Code discharge;
		
		public Encounter(long time, String type) {
			super(time, type);
			observations = new ArrayList<Observation>();
			reports = new ArrayList<Report>();
			conditions = new ArrayList<Entry>();
			procedures = new ArrayList<Entry>();
			immunizations = new ArrayList<Entry>();
			medications = new ArrayList<Medication>();
			careplans = new ArrayList<CarePlan>();
		}
	}
	
	public List<Encounter> encounters;
	public Map<String,Entry> present;
	/** recorded death date/time */
	public long death;
	
	public HealthRecord() {
		encounters = new ArrayList<Encounter>();
		present = new HashMap<String,Entry>();
	}
	
	public Encounter currentEncounter(long time) {
		Encounter encounter = null;
		if(encounters.size() >= 1) {
			encounter = encounters.get(encounters.size() - 1);
		} else {
			encounter = new Encounter(time, EncounterType.WELLNESS.toString());
			encounters.add(encounter);
		}
		return encounter;
	}
	
	public void observation(long time, String type, Object value) {
		Observation observation = new Observation(time, type, value);
		currentEncounter(time).observations.add(observation);
	}
	
	public Entry conditionStart(long time, String primaryCode) {
		if(!present.containsKey(primaryCode)) {
			Entry condition = new Entry(time, primaryCode);
			currentEncounter(time).conditions.add(condition);
			present.put(primaryCode, condition);
		}
		return present.get(primaryCode);
	}
	
	public void conditionEnd(long time, String primaryCode) {
		if(present.containsKey(primaryCode)) {
			present.get(primaryCode).stop = time;
			present.remove(primaryCode);
		}
	}
	
	public void conditionEndByState(long time, String stateName) {
		Entry condition = null;
		Iterator<Entry> iter = present.values().iterator();
		while(iter.hasNext()) {
			Entry e = iter.next();
			if(e.name == stateName) {
				condition = e;
				break;
			}
		}
		if(condition != null) {
			condition.stop = time;
			present.remove(condition.type);
		}
	}
	
	public void procedure(long time, String type) {
		Entry procedure = new Entry(time, type);
		currentEncounter(time).procedures.add(procedure);
		present.put(type, procedure);
	}
	
	public void report(long time, String type, int numberOfObservations) {
		Encounter encounter = currentEncounter(time);
		List<Observation> observations = null;
		if(encounter.observations.size() > numberOfObservations) {
			int fromIndex = encounter.observations.size() - numberOfObservations - 1;
			int toIndex = encounter.observations.size() - 1;
			observations = encounter.observations.subList(fromIndex, toIndex);			
		}
		Report report = new Report(time, type, observations);
		encounter.reports.add(report);
	}
	
	public Encounter encounterStart(long time, String type) {
		Encounter encounter = new Encounter(time, type);
		encounters.add(encounter);
		return encounter;
	}
	
	public void encounterEnd(long time, String type) {
		for(int i=encounters.size()-1; i >= 0; i--) {
			Encounter encounter = encounters.get(i);
			if(encounter.type==type && encounter.stop==0l) {
				encounter.stop = time;
				return;
			}
		}
	}
	
	public void immunization(long time, String type) {
		Entry immunization = new Entry(time, type);
		currentEncounter(time).immunizations.add(immunization);	
	}
	
	public void medicationStart(long time, String type, Set<String> reasons) {
		if(!present.containsKey(type)) {
			Medication medication = new Medication(time, type, reasons);
			currentEncounter(time).medications.add(medication);
			present.put(type, medication);			
		} else {
			Medication medication = (Medication) present.get(type);
			medication.reasons.addAll(reasons);
		}
	}
	
	public void medicationUpdate(long time, String type, Set<String> reasons) {
		// TODO is this the correct behavior?
		// Perhaps we should end the previous prescription and create a new one
		if(present.containsKey(type)) {
			Medication medication = (Medication) present.get(type);
			medication.start = time;
			medication.reasons = reasons;
		}
	}
	
	public void medicationEnd(long time, String type, String reason) {
		if(present.containsKey(type)) {
			Medication medication = (Medication) present.get(type);
			medication.stop = time;
			medication.stopReason = reason;
			present.remove(type);
		}
	}
	
	public boolean medicationActive(String type) {
		return present.containsKey(type) && ((Medication)present.get(type)).stop==0l;
	}
	
	public void careplanStart(long time, String type, Set<String> activities) {
		if(!present.containsKey(type)) {
			CarePlan careplan = new CarePlan(time, type, activities);
			currentEncounter(time).careplans.add(careplan);
			present.put(type, careplan);			
		} else {
			CarePlan careplan = (CarePlan) present.get(type);
			careplan.activities = activities;
		}
	}
	
	public void careplanUpdate(long time, String type, Set<String> reasons) {
		// TODO is this the correct behavior?
		// Perhaps we should end the previous prescription and create a new one
		if(present.containsKey(type)) {
			CarePlan careplan = (CarePlan) present.get(type);
			careplan.start = time;
			careplan.reasons = reasons;
		}
	}
	
	public void careplanEnd(long time, String type, String reason) {
		if(present.containsKey(type)) {
			CarePlan careplan = (CarePlan) present.get(type);
			careplan.stop = time;
			careplan.stopReason = reason;
			present.remove(type);
		}
	}
	
	public boolean careplanActive(String type) {
		return present.containsKey(type) && ((CarePlan)present.get(type)).stop==0l;
	}
}
