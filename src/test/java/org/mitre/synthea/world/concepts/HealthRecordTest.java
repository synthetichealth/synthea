package org.mitre.synthea.world.concepts;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public class HealthRecordTest {

  @Test
  public void testReportAllObs() {
    Person person = new Person(0L);
    HealthRecord record = new HealthRecord(person);
    Encounter encounter = record.encounterStart(0L, EncounterType.WELLNESS.toString());
    record.observation(0L, "A", "A");
    record.observation(0L, "B", "B");
    record.observation(0L, "C", "C");
    Report report = record.report(0L, "R", 3);
    
    Assert.assertEquals(3, encounter.observations.size());
    Assert.assertEquals(3, report.observations.size());
    Assert.assertEquals("A", report.observations.get(0).value);
    Assert.assertEquals("B", report.observations.get(1).value);
    Assert.assertEquals("C", report.observations.get(2).value);
  }

  @Test
  public void testReportSomeObs() {
    Person person = new Person(0L);
    HealthRecord record = new HealthRecord(person);
    Encounter encounter = record.encounterStart(0L, EncounterType.WELLNESS.toString());
    record.observation(0L, "A", "A");
    record.observation(0L, "B", "B");
    record.observation(0L, "C", "C");
    Report report = record.report(0L, "R", 2);
    
    Assert.assertEquals(3, encounter.observations.size());
    Assert.assertEquals(2, report.observations.size());
    Assert.assertEquals("B", report.observations.get(0).value);
    Assert.assertEquals("C", report.observations.get(1).value);
  }

  @Test
  public void testReportTooManyObs() {
    Person person = new Person(0L);
    HealthRecord record = new HealthRecord(person);
    Encounter encounter = record.encounterStart(0L, EncounterType.WELLNESS.toString());
    record.observation(0L, "A", "A");
    record.observation(0L, "B", "B");
    record.observation(0L, "C", "C");
    Report report = record.report(0L, "R", 4);
    
    Assert.assertEquals(3, encounter.observations.size());
    Assert.assertEquals(3, report.observations.size());
    Assert.assertEquals("A", report.observations.get(0).value);
    Assert.assertEquals("B", report.observations.get(1).value);
    Assert.assertEquals("C", report.observations.get(2).value);
  } 
}
