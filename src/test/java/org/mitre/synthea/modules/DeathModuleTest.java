package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.engine.Event;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Hospital;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Report;
import org.mockito.Mockito;

public class DeathModuleTest {

  private Person person;
  private long time;

  @Before
  public void setup() throws IOException {
    person = new Person(0L);

    person.history = new LinkedList<>();
    person.setAmbulatoryProvider(Mockito.mock(Hospital.class));
    person.setEmergencyProvider(Mockito.mock(Hospital.class));
    person.setInpatientProvider(Mockito.mock(Hospital.class));

    long birthTime = time - Utilities.convertTime("years", 35);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.events.create(birthTime, Event.BIRTH, "Generator.run", true);

    time = System.currentTimeMillis();
  }

  @Test
  public void testDeathCertificate() {
    /*
     * death_certificate: { description: 'U.S. standard certificate of death - 2003 revision', code:
     * '69409-1' }, cause_of_death: { description: 'Cause of Death [US Standard Certificate of
     * Death]', code: '69453-9', value_type: 'condition' },
     * 
     * death_certification: {description: 'Death Certification', codes: {'SNOMED-CT' =>
     * ['308646001']}, class: 'ambulatory'}
     */

    Code causeOfDeath = new Code("SNOMED-CT", "12345", "Some disease");
    person.recordDeath(time, causeOfDeath, DeathModuleTest.class.getName());

    DeathModule.process(person, time);

    Encounter enc = person.record.encounters.get(0);
    assertEquals("ambulatory", enc.type);
    assertEquals(time, enc.start);

    Code code = enc.codes.get(0);
    assertEquals("308646001", code.code);
    assertEquals("Death Certification", code.display);

    Report report = enc.reports.get(0);
    assertEquals("69409-1", report.type);
    assertEquals(time, report.start);

    code = report.codes.get(0);
    assertEquals("69409-1", code.code);
    assertEquals("U.S. standard certificate of death - 2003 revision", code.display);

    Observation obs = report.observations.get(0);
    assertEquals("69453-9", obs.type);
    assertEquals(time, obs.start);

    code = (Code) obs.value;
    assertEquals(causeOfDeath.code, code.code);
    assertEquals(causeOfDeath.display, code.display);

    code = obs.codes.get(0);
    assertEquals("69453-9", code.code);
    assertEquals("Cause of Death [US Standard Certificate of Death]", code.display);
  }

}
