package org.mitre.synthea.modules;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public class DeathModule extends Module 
{
	public static final Code DEATH_CERTIFICATION = new Code("SNOMED-CT", "308646001", "Death Certification");
	public static final Code CAUSE_OF_DEATH_CODE = new Code("LOINC", "69453-9", "Cause of Death [US Standard Certificate of Death]");
	public static final Code DEATH_CERTIFICATE = new Code("LOINC", "69409-1", "U.S. standard certificate of death - 2003 revision");
	
	public DeathModule() {
		this.name = "Encounter";
	}
	
	@Override
	public boolean process(Person person, long time) 
	{
		if (!person.alive(time) && person.attributes.containsKey(Person.CAUSE_OF_DEATH))
		{
			// create an encounter, diagnostic report, and observation
			
			Code causeOfDeath = (Code)person.attributes.get(Person.CAUSE_OF_DEATH);
			
			Encounter deathCertification = person.record.encounterStart(time, "ambulatory");
			deathCertification.codes.add(DEATH_CERTIFICATION);
			
			Observation codObs = person.record.observation(time, CAUSE_OF_DEATH_CODE.code, causeOfDeath);
			codObs.codes.add(CAUSE_OF_DEATH_CODE);
			
			Report deathCert = person.record.report(time, DEATH_CERTIFICATE.code, 1);
			deathCert.codes.add(DEATH_CERTIFICATE);
		}
		// java modules will never "finish"
		return false;
	}
}
