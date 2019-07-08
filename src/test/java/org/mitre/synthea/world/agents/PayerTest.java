package org.mitre.synthea.world.agents;

import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.geography.Location;

public class PayerTest {

  Payer randomPrivatePayer;

  /**
   * Setup for Payer Tests
   */
  @Before
  public void setup() throws IOException {
    // Clear any Payers that may have been statically loaded
    Payer.clear();
    // Load in the actual list of Payers for MA.
    Payer.loadPayers(new Location("Massachusetts", null));
    // Get the first Payer in the list for testing.
    randomPrivatePayer = Payer.getPrivatePayers().get(0);
  }

  @Test
  public void incrementCustomersTest() {

    Person firstPerson = new Person(0L);
    firstPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Payer has firstPerson customer from the ages of 0 - 11.
    for (int i = 0; i < 12; i++) {
      if (firstPerson.getPayerAtAge(i) == null) {
        firstPerson.setPayerAtAge(i, randomPrivatePayer);
        randomPrivatePayer.incrementCustomers(firstPerson);
      }
    }

    Person secondPerson = new Person(0L);
    secondPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Payer has secondPerson customer from the ages of 10 - 23.
    for (int i = 10; i < 24; i++) {
      if (secondPerson.getPayerAtAge(i) == null) {
        secondPerson.setPayerAtAge(i, randomPrivatePayer);
        randomPrivatePayer.incrementCustomers(secondPerson);
      }
    }
    // Gap of coverage. Person is with Payer again from ages 55 - 60.
    for (int i = 55; i < 61; i++) {
      if (secondPerson.getPayerAtAge(i) == null) {
        secondPerson.setPayerAtAge(i, randomPrivatePayer);
        randomPrivatePayer.incrementCustomers(secondPerson);
      }
    }

    // Ensure the first person was with the Payer for 12 years.
    assertEquals(12, randomPrivatePayer.getCustomerUtilization(firstPerson));
    // Ensure the second person was with the Payer for 20 years.
    assertEquals(20, randomPrivatePayer.getCustomerUtilization(secondPerson));
    // Ensure that there were 2 unique customers for the Payer.
    assertEquals(2, randomPrivatePayer.getUniqueCustomers());
  }

  @Test
  public void incrementEncountersTest() {

    Person person = new Person(0L);
    person.setPayerAtTime(0, randomPrivatePayer);
    HealthRecord healthRecord = new HealthRecord(person);

    healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    healthRecord.encounterStart(0L, EncounterType.AMBULATORY);
    healthRecord.encounterStart(0L, EncounterType.EMERGENCY);

    assertEquals(3, randomPrivatePayer.getEncounterCount());
  }

  @Test
  public void medicareAcceptanceTest() {
    
  }

  @Test
  public void medicaidAcceptanceTest() {
    
  }

  @Test
  public void dualEligibleAcceptanceTest() {
    
  }
}
