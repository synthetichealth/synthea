package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
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

  private Payer testPrivatePayer;

  private long time;
  private double defaultEncounterCost = Double
      .parseDouble(Config.get("generate.costs.default_encounter_cost"));
  private double patientCost;

  /**
   * Setup for HealthRecord Tests.
   */
  @Before
  public void setup() throws Exception {
    // Clear any Payers that may have already been statically loaded.
    Payer.clear();
    TestHelper.loadTestProperties();
    String testState = Config.get("test_state.default", "Massachusetts");
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Config.set("generate.payers.loss_of_care", "true");
    Config.set("lifecycle.death_by_loss_of_care", "true");
    // Load in the .csv list of Payers for MA.
    Payer.loadPayers(new Location(testState, null));
    // Load test payers.
    testPrivatePayer = Payer.getPrivatePayers().get(0);

    // Parse out testPrivatePayer's Copay.
    Person person = new Person(0L);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    person.attributes.put(Person.INCOME, 1);
    Encounter encounter = person.encounterStart(time, EncounterType.WELLNESS);
//<<<<<<< HEAD
    testPrivatePayerCopay = testPrivatePayer.determineCopay(encounter);

    time = 0L; //Utilities.convertCalendarYearsToTime(1900);
//=======
//    patientCost = testPrivatePayer.determineCopay(encounter) + testPrivatePayer.getDeductible();
//    patientCost += (defaultEncounterCost - patientCost) * testPrivatePayer.getCoinsurance();
//    
//    time = Utilities.convertCalendarYearsToTime(1900);
//>>>>>>> Fix broken unit tests.
  }

  @AfterClass
  public static void clean() {
    Config.set("generate.payers.loss_of_care", "false");
    Config.set("lifecycle.death_by_loss_of_care", "false");
  }

  @Test
  public void personRunsOutOfIncomeWithNoInsurance() {

    Person person = new Person(0L);
    person.coverage.setPayerAtTime(time, Payer.noInsurance);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of encounter
    person.attributes.put(Person.INCOME, (int) defaultEncounterCost - 1);

    // First encounter is uncovered but affordable.
    Encounter coveredEncounter = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter.codes.add(code);
    coveredEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should not recieve any more care.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounter));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounter));

    // Second encounter is uncovered and not affordable.
    Encounter uncoveredEncounter = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounter.codes.add(code);
    uncoveredEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounter));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounter));
  }

  @Test
  public void personRunsOutOfIncomeDueToCopay() {
    Person person = new Person(0L);
    person.coverage.setPayerAtTime(time, testPrivatePayer);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
//<<<<<<< HEAD
    // Determine income
    double encCost = Double.parseDouble(Config.get("generate.costs.default_encounter_cost"));
    double coinsurance = 1 - testPrivatePayer.getCoinsurance();
    double deductible = testPrivatePayer.getDeductible();
    double income = deductible
        + (2 * (encCost - testPrivatePayerCopay) * coinsurance)
        + (2 * testPrivatePayerCopay) - 1;
    // Set person's income to be $1 lower than the cost of 2 visits.
    person.attributes.put(Person.INCOME, (int) income);
//=======
//    // Set person's income to be $1 lower than the cost of 2 copays.
//    person.attributes.put(Person.INCOME, (int) (patientCost * 2) - 1);
//>>>>>>> Fix broken unit tests.

    // First encounter is covered and copay is affordable.
    Encounter coveredEncounter1 = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter1.codes.add(code);
    coveredEncounter1.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person has enough income for one more copay.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounter1));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounter1));

    // Second encounter is covered and copay is affordable.
    Encounter coveredEncounter2 = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounter2.codes.add(code);
    coveredEncounter2.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should switch to no insurance not recieve any further care.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounter2));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounter2));

    // Third encounter is uncovered and unaffordable.
    Encounter uncoveredEncounter3 = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounter3.codes.add(code);
    uncoveredEncounter3.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this record in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounter3));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounter3));
    // Person should now have no insurance.
    assertTrue(person.coverage.getPayerAtTime(time).equals(Payer.noInsurance));
  }

  @Test
  public void personRunsOutOfIncomeDueToMonthlyPremium() {

    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.coverage.setPayerAtTime(time, testPrivatePayer);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of 8 monthly premiums.
    person.attributes.put(Person.INCOME, (int) (testPrivatePayer.getMonthlyPremium() * 8) - 1);

    // Pay monthly premium 8 times.
    long oneMonth = Utilities.convertTime("years", 1) / 12;
    long currTime = time;
    HealthInsuranceModule healthInsuranceModule = new HealthInsuranceModule();
    for (int i = 0; i < 8; i++) {
      currTime += oneMonth;
      healthInsuranceModule.process(person, currTime);
    }
    // Person should now have no insurance.
    assertTrue(person.coverage.getPayerAtTime(currTime).equals(Payer.noInsurance));

    // Encounter is uncovered and unaffordable.
    Encounter uncoveredEncounter3
        = person.encounterStart(time + oneMonth * 7, EncounterType.WELLNESS);
    uncoveredEncounter3.codes.add(code);
    uncoveredEncounter3.provider = new Provider();
    person.record.encounterEnd(time + oneMonth * 7, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounter3));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounter3));
  }

  @Test
  public void personRunsOutOfCurrentYearIncomeThenNextYearBegins() {

    Person person = new Person(0L);
    person.coverage.setPayerAtTime(time, Payer.noInsurance);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of an encounter.
    person.attributes.put(Person.INCOME, (int) defaultEncounterCost - 1);
    // Set person's birthdate
    person.attributes.put(Person.BIRTHDATE, time);

    // First encounter of current year is uncovered but affordable.
    Encounter coveredEncounterYearOne = person.encounterStart(time, EncounterType.WELLNESS);
    coveredEncounterYearOne.codes.add(code);
    coveredEncounterYearOne.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should not receive any more care.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounterYearOne));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounterYearOne));

    // Second encounter of current year is uncovered and not affordable.
    Encounter uncoveredEncounterYearOne = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounterYearOne.codes.add(code);
    uncoveredEncounterYearOne.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounterYearOne));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounterYearOne));

    // Next year begins. Person should enough income to cover one encounter for the year.
    long oneYear = Utilities.convertTime("years", 1) + 1;
    person.coverage.setPayerAtTime(time + oneYear, Payer.noInsurance);
    // First encounter of next year is uncovered but affordable.
    Encounter coveredEncounterYearTwo
        = person.encounterStart(time + oneYear, EncounterType.WELLNESS);
    coveredEncounterYearTwo.codes.add(code);
    coveredEncounterYearTwo.provider = new Provider();
    person.record.encounterEnd(time + oneYear, EncounterType.WELLNESS);
    // Person is in debt $1. They should not receive any more care.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounterYearTwo));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounterYearTwo));

    // Second encounter of next year is uncovered and not affordable.
    Encounter uncoveredEncounterYearTwo
        = person.encounterStart(time + oneYear, EncounterType.WELLNESS);
    uncoveredEncounterYearTwo.codes.add(code);
    uncoveredEncounterYearTwo.provider = new Provider();
    person.record.encounterEnd(time + oneYear, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounterYearTwo));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounterYearTwo));
  }
} 