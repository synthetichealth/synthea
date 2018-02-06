package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public class DeathModule {
  public static final Code DEATH_CERTIFICATION = new Code("SNOMED-CT", "308646001",
      "Death Certification");
  public static final Code CAUSE_OF_DEATH_CODE = new Code("LOINC", "69453-9",
      "Cause of Death [US Standard Certificate of Death]");
  public static final Code DEATH_CERTIFICATE = new Code("LOINC", "69409-1",
      "U.S. standard certificate of death - 2003 revision");
  // NOTE: if new codes are added, be sure to update getAllCodes below
  
  public static void process(Person person, long time) {
    if (!person.alive(time) && person.attributes.containsKey(Person.CAUSE_OF_DEATH)) {
      // create an encounter, diagnostic report, and observation

      Code causeOfDeath = (Code) person.attributes.get(Person.CAUSE_OF_DEATH);

      Encounter deathCertification = person.record.encounterStart(time, "ambulatory");
      deathCertification.codes.add(DEATH_CERTIFICATION);

      Observation codObs = person.record.observation(time, CAUSE_OF_DEATH_CODE.code, causeOfDeath);
      codObs.codes.add(CAUSE_OF_DEATH_CODE);
      codObs.category = "exam";

      Report deathCert = person.record.report(time, DEATH_CERTIFICATE.code, 1);
      deathCert.codes.add(DEATH_CERTIFICATE);
    }
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   * 
   * @return Collection of all codes and concepts this module uses
   */
  public static Collection<Code> getAllCodes() {
    return Arrays.asList(DEATH_CERTIFICATION, CAUSE_OF_DEATH_CODE, DEATH_CERTIFICATE);
  }
}
