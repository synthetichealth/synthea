package org.mitre.synthea.world.concepts;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public class HealthRecordTest {

  Payer noInsurance;
  long time;

  /**
   * Setup for HealthRecord Tests.
   */
  @Before
  public void setup() {
    Payer.loadNoInsurance();
    noInsurance = Payer.noInsurance;
    time = 0L;
  }

  @Test
  public void testReportAllObs() {
    Person person = new Person(0L);
    person.coverage.setPayerAtTime(time, noInsurance);
    HealthRecord record = new HealthRecord(person);
    Encounter encounter = record.encounterStart(time, EncounterType.WELLNESS);
    record.observation(time, "A", "A");
    record.observation(time, "B", "B");
    record.observation(time, "C", "C");
    Report report = record.report(time, "R", 3);
    
    Assert.assertEquals(3, encounter.observations.size());
    Assert.assertEquals(3, report.observations.size());
    Assert.assertEquals("A", report.observations.get(0).value);
    Assert.assertEquals("B", report.observations.get(1).value);
    Assert.assertEquals("C", report.observations.get(2).value);
  }

  @Test
  public void testReportSomeObs() {
    Person person = new Person(0L);
    person.coverage.setPayerAtTime(time, noInsurance);
    HealthRecord record = new HealthRecord(person);
    Encounter encounter = record.encounterStart(time, EncounterType.WELLNESS);
    record.observation(time, "A", "A");
    record.observation(time, "B", "B");
    record.observation(time, "C", "C");
    Report report = record.report(time, "R", 2);
    
    Assert.assertEquals(3, encounter.observations.size());
    Assert.assertEquals(2, report.observations.size());
    Assert.assertEquals("B", report.observations.get(0).value);
    Assert.assertEquals("C", report.observations.get(1).value);
  }

  @Test
  public void testReportTooManyObs() {
    Person person = new Person(0L);
    person.coverage.setPayerAtTime(time, noInsurance);
    HealthRecord record = new HealthRecord(person);
    Encounter encounter = record.encounterStart(time, EncounterType.WELLNESS);
    record.observation(time, "A", "A");
    record.observation(time, "B", "B");
    record.observation(time, "C", "C");
    Report report = record.report(time, "R", 4);
    
    Assert.assertEquals(3, encounter.observations.size());
    Assert.assertEquals(3, report.observations.size());
    Assert.assertEquals("A", report.observations.get(0).value);
    Assert.assertEquals("B", report.observations.get(1).value);
    Assert.assertEquals("C", report.observations.get(2).value);
  } 
}
