package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Report;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

public class HealthRecordTest {

  Provider provider;
  InsurancePlan noInsurance;
  long time;

  /**
   * Setup for HealthRecord Tests.
   */
  @Before
  public void setup() {
    provider = TestHelper.buildMockProvider();
    PayerManager.loadPayers(new Location("Massachusetts", null));
    noInsurance = PayerManager.getNoInsurancePlan();
    time = 0L;
  }

  private void setProvider(Person person) {
    for (EncounterType type : EncounterType.values()) {
      person.setProvider(type, provider);
    }
  }

  @Test
  public void testReportAllObs() {
    Person person = new Person(0L);
    setProvider(person);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(time);
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
    setProvider(person);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(time);
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
    setProvider(person);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(time);
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

  @Test
  public void testMedicationAdministrationQuantity() {
    Person person = new Person(0L);
    setProvider(person);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanToNoInsurance(time);
    Medication med = person.record.medicationStart(time, "foobar", false);
    med.administration = true;
    long quantity = med.getQuantity();
    Assert.assertEquals(1, quantity);
  }

  @Test
  public void testMedicationPrescriptionQuantity() {
    Person person = new Person(0L);
    setProvider(person);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanToNoInsurance(time);
    Medication med = person.record.medicationStart(time, "foobar", true);
    long quantity = med.getQuantity();
    Assert.assertEquals(30, quantity);
  }

  @Test
  public void testMedicationDetailedQuantity() throws Exception {
    Person person = new Person(0L);
    setProvider(person);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanToNoInsurance(time);

    Module module = TestHelper.getFixture("medication_order.json");

    // Now process the prescription
    State med = module.getState("Metformin_With_Dosage");
    assertTrue(med.process(person, time));

    // Verify that Metformin was added to the record, including dosage information
    Medication medication = person.record.encounters.get(0).medications.get(0);
    assertEquals(time, medication.start);

    Code code = medication.codes.get(0);
    assertEquals("860975", code.code);
    assertEquals("24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet", code.display);

    long quantity = medication.getQuantity();
    Assert.assertEquals(180, quantity);
  }
}
