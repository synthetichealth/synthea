package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
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

  /**
   * Process the death of a person at a given time.
   * @param person - the person who has died.
   * @param time - the time of the death exam and certification.
   */
  public static void process(Person person, long time) {
    if (person.alive(time)) {
      // remove the death from the record.
      // otherwise it's tough to tell after the fact whether this patient did die or will die
      person.attributes.remove(Person.CAUSE_OF_DEATH);
      person.record.death = null;
      // TODO: if stopping/restarting/reloading becomes a thing, this may have to be reworked
    } else if (person.attributes.containsKey(Person.CAUSE_OF_DEATH)) {
      // create an encounter, diagnostic report, and observation

      Code causeOfDeath = (Code) person.attributes.get(Person.CAUSE_OF_DEATH);

      Encounter deathCertification = person.encounterStart(time, EncounterType.WELLNESS);
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

  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String,Inventory> attributes) {
    String m = DeathModule.class.getSimpleName();
    // Read
    Attributes.inventory(attributes, m, Person.CAUSE_OF_DEATH, true, false, null);
  }
}
