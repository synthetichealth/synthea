package org.mitre.synthea.modules;

import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.EncounterType;

public final class EncounterModule extends Module {
	
	public final static String ACTIVE_WELLNESS_ENCOUNTER = "active_wellness_encounter";
	public final static int SYMPTOM_THRESHOLD = 200;
	
	public EncounterModule() {
		this.name = "Encounter";
	}
	
	@Override
	public boolean process(Person person, long time) 
	{
		// add a wellness encounter if this is the right time
		if(person.record.timeSinceLastWellnessEncounter(time) >= recommendedTimeBetweenWellnessVisits(person, time)) {
			Encounter encounter = person.record.encounterStart(time, EncounterType.WELLNESS.toString());
			encounter.name = "Encounter Module Scheduled Wellness";
			person.attributes.put(ACTIVE_WELLNESS_ENCOUNTER, true);
		} else if(person.symptomTotal() > SYMPTOM_THRESHOLD) {
			// add a symptom driven encounter if symptoms are severe
			person.resetSymptoms();
			Encounter encounter = person.record.encounterStart(time, EncounterType.WELLNESS.toString());
			encounter.name = "Encounter Module Symptom Driven";
			person.attributes.put(ACTIVE_WELLNESS_ENCOUNTER, true);
		}
		
		// java modules will never "finish"
		return false;
	}
	
	public long recommendedTimeBetweenWellnessVisits(Person person, long time) {
		int ageInYears = person.ageInYears(time);
		if(ageInYears <= 3) {
			int ageInMonths = person.ageInMonths(time);
			if(ageInMonths <= 1) {
				return Utilities.convertTime("months", 1);
			} else if(ageInMonths <= 5) {
				return Utilities.convertTime("months", 2);
			} else if(ageInMonths <= 17) {
				return Utilities.convertTime("months", 3);
			} else {
				return Utilities.convertTime("months", 6);				
			}
		} else if(ageInYears <= 19) {
			return Utilities.convertTime("years", 1);
		} else if(ageInYears <= 39) {
			return Utilities.convertTime("years", 3);
		} else if(ageInYears <= 49) {
			return Utilities.convertTime("years", 2);
		} else {
			return Utilities.convertTime("years", 1);
		}
	}
	
	public void endWellnessEncounter(Person person, long time) {
		person.record.encounterEnd(time, EncounterType.WELLNESS.toString());
		person.attributes.remove(ACTIVE_WELLNESS_ENCOUNTER);
	}
	
}
