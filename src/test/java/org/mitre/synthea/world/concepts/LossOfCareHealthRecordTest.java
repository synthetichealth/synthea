package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

public class LossOfCareHealthRecordTest {

  private InsurancePlan testPrivatePlan;
  private final double defaultEncounterCost
          = Config.getAsDouble("generate.costs.default_encounter_cost");

  /**
   * Setup for HealthRecord Tests.
   */
  @Before
  public void setup() throws Exception {
    // Clear any Payers that may have already been statically loaded.
    PayerManager.clear();
    TestHelper.loadTestProperties();
    String testState = Config.get("test_state.default", "Massachusetts");
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "generic/payers/test_plans.csv");
    Config.set("generate.payers.loss_of_care", "true");
    Config.set("generate.payers.insurance_plans.eligibilities_file",
        "generic/payers/test_insurance_eligibilities.csv");
    Config.set("lifecycle.death_by_loss_of_care", "true");
    // Load in the .csv list of Payers for MA.
    PayerManager.loadPayers(new Location(testState, null));
    // Load test payers.
    Set<Payer> privatePayers = PayerManager.getAllPayers().stream().filter(payer -> payer
        .getOwnership().equals(PayerManager.PRIVATE_OWNERSHIP)).collect(Collectors.toSet());
    Payer testPrivatePayer = privatePayers.stream().filter(payer ->
        payer.getName().equals("Test Private Payer 1")).iterator().next();
    testPrivatePlan = testPrivatePayer.getPlans().iterator().next();
  }

  /**
   * Cleanup after all tests complete.
   */
  @AfterClass
  public static void clean() {
    Config.set("generate.payers.insurance_companies.default_file",
        "payers/insurance_companies.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "payers/insurance_plans.csv");
    Config.set("generate.payers.insurance_plans.eligibilities_file",
        "payers/insurance_eligibilities.csv");
    Config.set("generate.payers.loss_of_care", "false");
    Config.set("lifecycle.death_by_loss_of_care", "false");
  }

  @Test
  public void personRunsOutOfIncomeWithNoInsurance() {
    long time = Utilities.convertCalendarYearsToTime(1900);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanToNoInsurance(time);
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
  public void personRunsOutOfIncomeDueToCopayOrCoinsurance() {
    long time = Utilities.convertCalendarYearsToTime(1900);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.01);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.INCOME, 1);
    person.coverage.setPlanAtTime(time, testPrivatePlan, PayerManager.getNoInsurancePlan());
    person.setProvider(EncounterType.INPATIENT, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Determine income

    double encounterCost = Config.getAsDouble("generate.costs.default_encounter_cost");
    BigDecimal patientCoinsurance = testPrivatePlan.getPatientCoinsurance();
    Encounter dummyInpatientEncounter = person.encounterStart(time, EncounterType.INPATIENT);
    BigDecimal planCopay = testPrivatePlan
        .determineCopay(dummyInpatientEncounter.type, dummyInpatientEncounter.start);
    BigDecimal income = BigDecimal.valueOf(encounterCost).multiply(patientCoinsurance)
        .multiply(BigDecimal.valueOf(2));
    if (testPrivatePlan.isCopayBased()) {
      income = planCopay.multiply(BigDecimal.valueOf(2));
    }
    income = income.subtract(BigDecimal.ONE);

    // Set person's income to be $1 lower than the cost of 2 visits.
    person.attributes.put(Person.INCOME, income.intValue());

    // First encounter is covered and copay is affordable.
    Encounter coveredEncounter1 = person.encounterStart(time, EncounterType.INPATIENT);
    coveredEncounter1.codes.add(code);
    coveredEncounter1.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.INPATIENT);
    // Person has enough income for one more copay.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounter1));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounter1));

    // Second encounter is covered and copay is affordable.
    Encounter coveredEncounter2 = person.encounterStart(time, EncounterType.INPATIENT);
    coveredEncounter2.codes.add(code);
    coveredEncounter2.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.INPATIENT);
    // Person is in debt $1. They should switch to no insurance not recieve any further care.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounter2));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounter2));

    // Third encounter is uncovered and unaffordable.
    Encounter uncoveredEncounter3 = person.encounterStart(time, EncounterType.INPATIENT);
    uncoveredEncounter3.codes.add(code);
    uncoveredEncounter3.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.INPATIENT);
    // Person should have this record in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounter3));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounter3));
    // Person should now have no insurance.
    assertTrue(person.coverage.getPlanAtTime(time)
        .equals(PayerManager.getNoInsurancePlan()));
  }

  @Test
  public void personRunsOutOfIncomeDueToMonthlyPremium() {
    long time = Utilities.convertCalendarYearsToTime(1900);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    person.coverage.setPlanAtTime(time, testPrivatePlan, PayerManager.getNoInsurancePlan());
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    // Set person's income to be $1 lower than the cost of 8 monthly premiums.
    person.attributes.put(Person.INCOME, testPrivatePlan.getMonthlyPremium(0)
            .multiply(BigDecimal.valueOf(8))
            .subtract(BigDecimal.ONE).intValue());

    // Pay monthly premium 8 times.
    long oneMonth = Utilities.convertTime("years", 1) / 12;
    long currTime = time;
    HealthInsuranceModule healthInsuranceModule = new HealthInsuranceModule();
    for (int i = 0; i < 8; i++) {
      currTime += oneMonth;
      healthInsuranceModule.process(person, currTime);
    }
    // Person should now have no insurance.
    assertTrue("Person should have no insurance but has "
        + person.coverage.getPlanAtTime(currTime).getPayer().getName()
        + ".", person.coverage.getPlanAtTime(currTime)
        .equals(PayerManager.getNoInsurancePlan()));

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
    long time = Utilities.convertCalendarYearsToTime(1980);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    // Set person's income to be $1 lower than the cost of an encounter.
    person.attributes.put(Person.INCOME, (int) defaultEncounterCost - 1);
    person.coverage.setPlanToNoInsurance(time);
    person.setProvider(EncounterType.WELLNESS, new Provider());
    Code code = new Code("SNOMED-CT","705129","Fake Code");

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
    time += Utilities.convertTime("years", 1);
    person.coverage.setPlanToNoInsurance(time);
    // First encounter of next year is uncovered but affordable.
    Encounter coveredEncounterYearTwo
        = person.encounterStart(time + 1, EncounterType.WELLNESS);
    coveredEncounterYearTwo.codes.add(code);
    coveredEncounterYearTwo.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person is in debt $1. They should not receive any more care.
    assertTrue(person.defaultRecord.encounters.contains(coveredEncounterYearTwo));
    assertFalse(person.lossOfCareRecord.encounters.contains(coveredEncounterYearTwo));

    // Second encounter of next year is uncovered and not affordable.
    Encounter uncoveredEncounterYearTwo
        = person.encounterStart(time, EncounterType.WELLNESS);
    uncoveredEncounterYearTwo.codes.add(code);
    uncoveredEncounterYearTwo.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // Person should have this encounter in the uncoveredHealthRecord.
    assertFalse(person.defaultRecord.encounters.contains(uncoveredEncounterYearTwo));
    assertTrue(person.lossOfCareRecord.encounters.contains(uncoveredEncounterYearTwo));
  }
}