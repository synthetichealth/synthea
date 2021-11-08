package org.mitre.synthea.modules.covid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.modules.Immunizations;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.geography.Location;

public class C19ImmunizationModuleTest {

  @Test
  public void currentlyHasCOVID() {
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    assertFalse(C19ImmunizationModule.currentlyHasCOVID(person));
    person.record.conditionStart(decemberFifteenth, C19ImmunizationModule.COVID_CODE);
    assertTrue(C19ImmunizationModule.currentlyHasCOVID(person));
  }

  @Test
  public void eligibleForShot() {
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    assertTrue(C19ImmunizationModule.eligibleForShot(person, decemberFifteenth));
    long eleven = TestHelper.timestamp(2009, 8, 1, 0, 0, 0);
    person.attributes.put(Person.BIRTHDATE, eleven);
    assertFalse(C19ImmunizationModule.eligibleForShot(person, decemberFifteenth));
  }

  @Test
  public void selectVaccine() {
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    assertTrue(C19Vaccine.EUASet.PFIZER
        == C19ImmunizationModule.selectVaccine(person, decemberFifteenth));
    long januaryOne = TestHelper.timestamp(2021, 1, 1, 0, 0, 0);
    assertTrue(C19Vaccine.EUASet.JANSSEN
        != C19ImmunizationModule.selectVaccine(person, januaryOne));
  }

  @Test
  public void vaccinate() {
    long decemberFifteenth = TestHelper.timestamp(2020, 12, 15, 0, 0, 0);
    long birthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    Location here = new Location("Massachusetts", "Billerica");
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    person.attributes.put(C19ImmunizationModule.C19_VACCINE, C19Vaccine.EUASet.PFIZER);
    here.assignPoint(person, "Billerica");
    Provider.loadProviders(here, 1L);
    person.setProvider(HealthRecord.EncounterType.OUTPATIENT,
        Provider.findService(person, HealthRecord.EncounterType.OUTPATIENT, decemberFifteenth));
    Payer.loadPayers(here);
    person.coverage.setPayerAtTime(decemberFifteenth, Payer.getGovernmentPayer("Medicare"));
    C19ImmunizationModule.vaccinate(person, decemberFifteenth, 1);
    assertEquals(1, person.record.encounters.size());
    assertEquals(1, person.record.encounters.get(0).immunizations.size());
    Map<String, List<Long>> immunizationHistory =
        (Map<String, List<Long>>) person.attributes.get(Immunizations.IMMUNIZATIONS);
    long shotTime = immunizationHistory.get(C19ImmunizationModule.C19_PERSON_ATTRS_KEY).get(0);
    assertEquals(decemberFifteenth, shotTime);
  }

  @Test
  public void process() {
    long decemberFirst = TestHelper.timestamp(2020, 12, 1, 0, 0, 0);
    long birthday = TestHelper.timestamp(2010, 8, 1, 0, 0, 0);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthday);
    C19ImmunizationModule mod = new C19ImmunizationModule();
    mod.process(person, decemberFirst);
    assertNull(person.attributes.get(C19ImmunizationModule.C19_VACCINE_STATUS));
    long januaryOne = TestHelper.timestamp(2021, 1, 1, 0, 0, 0);
    mod.process(person, januaryOne);
    assertEquals(C19ImmunizationModule.VaccinationStatus.NOT_ELIGIBLE,
        person.attributes.get(C19ImmunizationModule.C19_VACCINE_STATUS));
    long newBirthday = TestHelper.timestamp(1978, 8, 1, 0, 0, 0);
    person.attributes.put(Person.BIRTHDATE, newBirthday);
    mod.process(person, januaryOne);
    C19ImmunizationModule.VaccinationStatus status =
        (C19ImmunizationModule.VaccinationStatus)
            person.attributes.get(C19ImmunizationModule.C19_VACCINE_STATUS);
    assertTrue((status == C19ImmunizationModule.VaccinationStatus.WAITING_FOR_SHOT)
        || (status == C19ImmunizationModule.VaccinationStatus.POTENTIAL_LATE_ADOPTER));
  }
}