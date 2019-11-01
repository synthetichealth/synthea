package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;

public class LossOfCareHealthRecordTest {

  Payer testPrivatePayer1;
  Payer testPrivatePayer2;

  long time;
  double DEFAULT_ENCOUNTER_COST;
  double testPrivatePayer1Copay;

  /**
   * Setup for HealthRecord Tests.
   */
  @Before
  public void setup() {
    // Clear any Payers that may have already been statically loaded.
    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    // Load in the .csv list of Payers for MA.
    Payer.loadPayers(new Location("Massachusetts", null));
    // Load the two test payers.
    testPrivatePayer1 = Payer.getPrivatePayers().get(0);
    testPrivatePayer2 = Payer.getPrivatePayers().get(1);
    // Get Default Encounter Cost
    DEFAULT_ENCOUNTER_COST = Double
        .parseDouble(Config.get("generate.costs.default_encounter_cost"));

    // Parse out testPrivatePayer1's Copay.
    Person person = new Person(0L);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    person.attributes.put(Person.INCOME, 1);
    Encounter encounter = person.encounterStart(time, EncounterType.WELLNESS);
    testPrivatePayer1Copay = testPrivatePayer1.determineCopay(encounter);

    time = Utilities.convertCalendarYearsToTime(1900);
  }

  @Test
  public void personRunsOutOfIncomeWithNoInsurance() {

    Person person = new Person(0L);
    person.setPayerAtTime(time, Payer.noInsurance);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of encounter
    person.attributes.put(Person.INCOME, (int) DEFAULT_ENCOUNTER_COST - 1);

    // First encounter is uncovered but affordable.
    Encounter coveredEncounter = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter.codes.add(code);
    coveredEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should not recieve any more care.
    assertTrue(person.coveredHealthRecord.encounters.contains(coveredEncounter));
    assertFalse(person.lossOfCareHealthRecord.encounters.contains(coveredEncounter));

    // Second encounter is uncovered and not affordable.
    Encounter uncoveredEncounter = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounter.codes.add(code);
    uncoveredEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.coveredHealthRecord.encounters.contains(uncoveredEncounter));
    assertTrue(person.lossOfCareHealthRecord.encounters.contains(uncoveredEncounter));
  }

  @Test
  public void personRunsOutOfIncomeDueToCopay() {

    Person person = new Person(0L);
    person.setPayerAtTime(time, testPrivatePayer1);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of 2 copays.
    person.attributes.put(Person.INCOME, (int) (testPrivatePayer1Copay * 2) - 1);

    // First encounter is covered and copay is affordable.
    Encounter coveredEncounter1 = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter1.codes.add(code);
    coveredEncounter1.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person has enough income for one more copay.
    assertTrue(person.coveredHealthRecord.encounters.contains(coveredEncounter1));
    assertFalse(person.lossOfCareHealthRecord.encounters.contains(coveredEncounter1));

    // Second encounter is covered and copay is affordable.
    Encounter coveredEncounter2 = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter2.codes.add(code);
    coveredEncounter2.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should switch to no insurance not recieve any further care.
    assertTrue(person.coveredHealthRecord.encounters.contains(coveredEncounter2));
    assertFalse(person.lossOfCareHealthRecord.encounters.contains(coveredEncounter2));

    // Third encounter is uncovered and unaffordable.
    Encounter uncoveredEncounter3 = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounter3.codes.add(code);
    uncoveredEncounter3.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this record in the uncoveredHealthRecord.
    assertFalse(person.coveredHealthRecord.encounters.contains(uncoveredEncounter3));
    assertTrue(person.lossOfCareHealthRecord.encounters.contains(uncoveredEncounter3));
    // Person should now have no insurance.
    assertTrue(person.getPayerAtTime(time).equals(Payer.noInsurance));
  }

  @Test
  public void personRunsOutOfIncomeDueToMonthlyPremium() {

    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.setPayerAtTime(time, testPrivatePayer1);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of 8 monthly premiums.
    person.attributes.put(Person.INCOME, (int) (testPrivatePayer1.payMonthlyPremium() * 8) - 1);

    // Pay monthly premium 8 times.
    long oneMonth = Utilities.convertTime("months", 1);
    HealthInsuranceModule healthInsuranceModule = new HealthInsuranceModule();
    for (int i = 0; i < 8; i++) {
      healthInsuranceModule.process(person, time + (oneMonth * i));
    }
    // Person should now have no insurance.
    assertTrue(person.getPayerAtTime(time).equals(Payer.noInsurance));

    // Encounter is uncovered and unaffordable.
    Encounter uncoveredEncounter3
        = person.encounterStart(time + oneMonth * 7, EncounterType.WELLNESS);
    uncoveredEncounter3.codes.add(code);
    uncoveredEncounter3.provider = new Provider();
    person.record.encounterEnd(time + oneMonth * 7, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.coveredHealthRecord.encounters.contains(uncoveredEncounter3));
    assertTrue(person.lossOfCareHealthRecord.encounters.contains(uncoveredEncounter3));
  }

  @Test
  public void personRunsOutOfCurrentYearIncomeThenNextYearBegins() {

    Person person = new Person(0L);
    person.setPayerAtTime(time, Payer.noInsurance);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of an encounter.
    person.attributes.put(Person.INCOME, (int) DEFAULT_ENCOUNTER_COST - 1);
    // Set person's birthdate
    person.attributes.put(Person.BIRTHDATE, time);

    // First encounter of current year is uncovered but affordable.
    Encounter coveredEncounterYearOne = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounterYearOne.codes.add(code);
    coveredEncounterYearOne.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should not recieve any more care.
    assertTrue(person.coveredHealthRecord.encounters.contains(coveredEncounterYearOne));
    assertFalse(person.lossOfCareHealthRecord.encounters.contains(coveredEncounterYearOne));

    // Second encounter of current year is uncovered and not affordable.
    Encounter uncoveredEncounterYearOne = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounterYearOne.codes.add(code);
    uncoveredEncounterYearOne.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.coveredHealthRecord.encounters.contains(uncoveredEncounterYearOne));
    assertTrue(person.lossOfCareHealthRecord.encounters.contains(uncoveredEncounterYearOne));

    // Next year begins. Person should enough income to cover one encounter for the year.
    long oneYear = Utilities.convertTime("years", 1) + 1;
    person.setPayerAtTime(time + oneYear, Payer.noInsurance);
    // First encounter of next year is uncovered but affordable.
    Encounter coveredEncounterYearTwo
        = person.encounterStart(time + oneYear, EncounterType.WELLNESS);
    coveredEncounterYearTwo.codes.add(code);
    coveredEncounterYearTwo.provider = new Provider();
    person.record.encounterEnd(time + oneYear, EncounterType.WELLNESS);
    // Person is in debt $1. They should not recieve any more care.
    assertTrue(person.coveredHealthRecord.encounters.contains(coveredEncounterYearTwo));
    assertFalse(person.lossOfCareHealthRecord.encounters.contains(coveredEncounterYearTwo));

    // Second encounter of next year is uncovered and not affordable.
    Encounter uncoveredEncounterYearTwo
        = person.encounterStart(time + oneYear, EncounterType.WELLNESS);
    uncoveredEncounterYearTwo.codes.add(code);
    uncoveredEncounterYearTwo.provider = new Provider();
    person.record.encounterEnd(time + oneYear, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.coveredHealthRecord.encounters.contains(uncoveredEncounterYearTwo));
    assertTrue(person.lossOfCareHealthRecord.encounters.contains(uncoveredEncounterYearTwo));

  }
} 