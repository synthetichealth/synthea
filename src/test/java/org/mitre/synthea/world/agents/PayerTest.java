package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

public class PayerTest {

  private static String testState;
  // Covers all healthcare.
  private Payer testPrivatePayer1;
  // Covers only wellness encounters.
  private Payer testPrivatePayer2;
  private static HealthInsuranceModule healthInsuranceModule;
  private Person person;
  private static double medicaidLevel;
  private static long mandateTime;
  private static String medicareName;
  private static String medicaidName;
  private static String dualName;
  private static long sixMonths = Utilities.convertTime("months", 6);

  /**
   * Setup for Payer Tests.
   * @throws Exception on configuration loading error
   */
  @BeforeClass
  public static void setup() throws Exception {
    TestHelper.loadTestProperties();
    testState = Config.get("test_state.default", "Massachusetts");
    // Set up Medicaid numbers.
    healthInsuranceModule = new HealthInsuranceModule();
    double povertyLevel =
            Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);
    medicaidLevel = 1.33 * povertyLevel;
    // Set up Mandate year.
    int mandateYear = Integer.parseInt(Config.get("generate.insurance.mandate.year", "2006"));
    mandateTime = Utilities.convertCalendarYearsToTime(mandateYear);
    medicareName = Config.get("generate.payers.insurance_companies.medicare", "Medicare");
    medicaidName = Config.get("generate.payers.insurance_companies.medicaid", "Medicaid");
    dualName = Config.get("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");
  }

  /**
   * Setup before each test.
   */
  @Before
  public void before() {
    // Clear any Payers that may have already been statically loaded.
    PayerController.clear();
    // Load in the .csv list of Payers.
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    // Force medicare for test settings
    Config.set("generate.payers.insurance_companies.medicare", "Medicare");
    Config.set("generate.payers.insurance_companies.medicaid", "Medicaid");
    Config.set("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");
    PayerController.loadPayers(new Location(testState, null));
    // Load the two test payers.
    testPrivatePayer1 = PayerController.getPrivatePayers().get(0);
    testPrivatePayer2 = PayerController.getPrivatePayers().get(1);
  }

  /**
   * Clean up after tests.
   */
  @AfterClass
  public static void cleanup() {
    Config.set("generate.payers.insurance_companies.medicare", medicareName);
    Config.set("generate.payers.insurance_companies.medicaid", medicaidName);
    Config.set("generate.payers.insurance_companies.dual_eligible", dualName);
  }

  @Test
  public void incrementCustomers() {
    long time = 0L;
    Person firstPerson = new Person(0L);
    firstPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    firstPerson.attributes.put(Person.BIRTHDATE, time);
    // Payer has firstPerson customer from the ages of 0 - 11.
    setPayerForYears(firstPerson, 0, 11);

    Person secondPerson = new Person(0L);
    secondPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    secondPerson.attributes.put(Person.BIRTHDATE, time);
    // Payer has secondPerson customer from the ages of 10 - 23.
    setPayerForYears(secondPerson, 10, 23);
    // Gap of coverage. Person is with Payer again from ages 55 - 60.
    setPayerForYears(secondPerson, 55, 60);

    // Ensure the first person was with the Payer for 12 years.
    assertEquals(12, testPrivatePayer1.getCustomerUtilization(firstPerson));
    // Ensure the second person was with the Payer for 20 years.
    assertEquals(20, testPrivatePayer1.getCustomerUtilization(secondPerson));
    // Ensure that there were 2 unique customers for the Payer.
    assertEquals(2, testPrivatePayer1.getUniqueCustomers());
  }

  /**
   * Sets the person's payer for the given year range.
   */
  private void setPayerForYears(Person person, int startAge, int endAge) {
    for (int i = startAge; i <= endAge; i++) {
      long time = Utilities.convertTime("years", i);
      person.coverage.setPlanAtTime(time, testPrivatePayer1.getPlans().iterator().next());
      testPrivatePayer1.incrementCustomers(person);
    }
  }

  @Test
  public void incrementEncounters() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanAtTime(0L, testPrivatePayer1.getPlans().iterator().next());
    HealthRecord healthRecord = new HealthRecord(person);

    Code code = new Code("SNOMED-CT","705129","Fake Code");

    Encounter fakeEncounter = healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    healthRecord.encounterEnd(0L, EncounterType.INPATIENT);
    fakeEncounter = healthRecord.encounterStart(0L, EncounterType.AMBULATORY);
    fakeEncounter.provider = new Provider();
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(0L, EncounterType.AMBULATORY);
    fakeEncounter = healthRecord.encounterStart(0L, EncounterType.EMERGENCY);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    healthRecord.encounterEnd(0L, EncounterType.EMERGENCY);

    assertEquals(3, testPrivatePayer1.getEncountersCoveredCount());
  }

  @Test
  public void receiveMedicareAgeEligible() {
    long birthTime = System.currentTimeMillis();
    long age65Time = birthTime + Utilities.convertTime("years", 66) - sixMonths;
    // Older than 65
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put("end_stage_renal_disease", false);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    // The person should have private insurance before the age of 65.
    long age64Time = birthTime + Utilities.convertTime("years", 65) - sixMonths;
    person.coverage.setPlanAtTime(age64Time,
        testPrivatePayer1.getPlans().stream().iterator().next());
    healthInsuranceModule.process(person, age64Time);
    assertEquals(PayerController.PRIVATE_OWNERSHIP,
        person.coverage.getPlanAtTime(age64Time).getPayer().getOwnership());
    // The person is now 65 and qualifies for Medicare.
    healthInsuranceModule.process(person, age65Time);
    assertEquals(HealthInsuranceModule.MEDICARE,
        person.coverage.getPlanAtTime(age65Time).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(age65Time).getPayer().accepts(person, age65Time));
  }

  @Test
  public void receiveMedicareEsrdEligible() {
    // ESRD
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put("end_stage_renal_disease", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals(HealthInsuranceModule.MEDICARE,
        person.coverage.getPlanAtTime(0L).getPayer().getName());
  }

  @Test
  public void receiveMedicaidPregnancyElgible() {
    // Pregnancy
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put("pregnant", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals(HealthInsuranceModule.MEDICAID,
        person.coverage.getPlanAtTime(0L).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(0L).getPayer().accepts(person, 0L));
  }

  @Test
  public void receiveMedicaidPovertyEligible() {
    // Poverty Level
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.BLINDNESS, false);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.coverage.getPlanAtTime(0L).getPayer().getName());
  }

  @Test
  public void receiveMedicaidBlindnessEligble() {
    // Blindness
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.BLINDNESS, true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.coverage.getPlanAtTime(0L).getPayer().getName());
  }

  @Test
  public void receiveDualEligible() {
    long birthTime = System.currentTimeMillis();
    long age65Time = birthTime + Utilities.convertTime("years", 65) + sixMonths;

    // Below Poverty Level and Over 65, thus Dual Eligble.
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    // Check that their previous payer is Medicaid.
    long age64Time = birthTime + Utilities.convertTime("years", 64) + sixMonths;
    person.coverage.setPlanAtTime(age64Time,
        testPrivatePayer1.getPlans().stream().iterator().next());
    healthInsuranceModule.process(person, age64Time);
    assertEquals(HealthInsuranceModule.MEDICAID,
        person.coverage.getPlanAtTime(age64Time).getPayer().getName());
    // The person is now 65 and qualifies for Medicare in addition to.
    healthInsuranceModule.process(person, age65Time);
    assertEquals(HealthInsuranceModule.DUAL_ELIGIBLE,
        person.coverage.getPlanAtTime(age65Time).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(age65Time).getPayer()
        .accepts(person, age65Time));
  }

  @Test
  public void receiveNoInsurance() {
    // Person's income cannot afford the test private insurance.
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    double monthlyPremium = plan.getMonthlyPremium();
    double deductible = plan.getDeductible();
    double totalYearlyCost = (monthlyPremium * 12) + deductible;

    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.001);
    // Give the person an income lower than the totalYearlyCost.
    person.attributes.put(Person.INCOME, (int) totalYearlyCost - 1);
    // Set the medicaid poverty level to be lower than their income
    Config.set("generate.demographics.socioeconomic.income.poverty",
        Integer.toString((int) totalYearlyCost - 2));
    HealthInsuranceModule.medicaidLevel = Config.getAsDouble(
            "generate.demographics.socioeconomic.income.poverty", 11000);

    healthInsuranceModule.process(person, 0L);
    assertEquals("NO_INSURANCE", person.coverage.getPlanAtTime(0L).getPayer().getName());
  }

  @Test
  public void receivePrivateInsurancePostMandate() {
    // Post 2006 Mandate.
    long time = mandateTime + Utilities.convertTime("years", 50);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Barely above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel + 100);
    healthInsuranceModule.process(person, time);
    assertNotEquals("NO_INSURANCE", person.coverage.getPlanAtTime(time).getPayer().getName());
  }

  @Test
  public void receivePrivateInsuranceWithWealth() {
    // Wealthy Enough to Purchase Private Insurance.
    long time = mandateTime - Utilities.convertTime("years", 50);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, time);
    assertNotEquals("NO_INSURANCE", person.coverage.getPlanAtTime(time).getPayer().getName());
  }

  @Test
  public void overwriteInsurance() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    person.coverage.setPlanAtTime(0L, plan);
    person.coverage.setPlanAtTime(0L, plan);
    assertEquals(2, person.coverage.getPlanHistory().size());
  }

  @Test
  public void loadGovernmentPayers() {
    assertNotNull(PayerController.getGovernmentPayer(HealthInsuranceModule.MEDICARE));
    assertNotNull(PayerController.getGovernmentPayer(HealthInsuranceModule.MEDICAID));
    for (Payer payer : PayerController.getGovernmentPayers()) {
      assertEquals(PayerController.GOV_OWNERSHIP, payer.getOwnership());
    }
  }

  @Test
  public void invalidGovernmentPayer() {
    assertNull(PayerController.getGovernmentPayer("Hollywood Healthcare"));
  }

  @Test
  public void loadAllPayers() {
    int numGovernmentPayers = PayerController.getGovernmentPayers().size();
    int numPrivatePayers = PayerController.getPrivatePayers().size();
    assertEquals(numGovernmentPayers + numPrivatePayers, PayerController.getAllPayers().size());
  }

  @Test(expected = RuntimeException.class)
  public void nullPayerName() {
    PayerController.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/bad_test_payers.csv");
    PayerController.loadPayers(new Location("Massachusetts", null));
    PayerController.clear();
  }

  @Test
  public void monthlyPremiumPayment() {
    person = new Person(0L);
    // Give person an income to prevent null pointer.
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Predetermine person's Payer.
    setPayerForYears(person, 0, 65);
    // Pay premium for 65 years.
    long time = 0L;
    for (int year = 0; year <= 64; year++) {
      for (int month = 0; month < 24; month++) {
        // Person checks to pay twice a month. Only needs to pay once a month.
        time += (Utilities.convertTime("years", 1) / 24);
        healthInsuranceModule.process(person, time);
      }
    }
    int totalMonthlyPremiumsOwed
        = (int) (testPrivatePayer1.getPlans().iterator().next().getMonthlyPremium() * 12 * 65);
    // The payer's revenue should equal the total monthly premiums.
    assertEquals(totalMonthlyPremiumsOwed, testPrivatePayer1.getRevenue(), 0.001);
    // The person's health care expenses should equal the total monthly premiums.
    assertEquals(totalMonthlyPremiumsOwed, person.coverage.getTotalExpenses(), 0.001);
  }

  @Test(expected = RuntimeException.class)
  public void monthlyPremiumPaymentToNullPayer() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.checkToPayMonthlyPremium(0L);
  }

  @Test
  public void costsCoveredByPayer() {
    long time = 0L;
    Costs.loadCostData();
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    person.coverage.setPlanAtTime(time, plan);
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(time, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    double totalCost = fakeEncounter.getCost().doubleValue();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // check the copays match
    assertEquals(plan.getDeductible(), fakeEncounter.claim.totals.deductible, 0.001);
    // check that totals match
    assertEquals(totalCost, fakeEncounter.claim.totals.cost, 0.001);
    double result = fakeEncounter.claim.totals.coinsurance
        + fakeEncounter.claim.totals.copay
        + fakeEncounter.claim.totals.deductible
        + fakeEncounter.claim.totals.paidByPayer
        + fakeEncounter.claim.totals.paidByPatient;
    assertEquals(totalCost, result, 0.001);
    // The total cost should equal the Cost to the Payer summed with the Payer's copay amount.
    assertEquals(totalCost, testPrivatePayer1.getAmountCovered()
        + fakeEncounter.claim.getPatientCost(), 0.001);
    // The total cost should equal the Payer's uncovered costs plus the Payer's covered costs.
    assertEquals(totalCost, testPrivatePayer1.getAmountCovered()
        + testPrivatePayer1.getAmountUncovered(), 0.001);
    // The total coverage by the payer should equal the person's covered costs.
    assertEquals(person.coverage.getTotalCoverage(), testPrivatePayer1.getAmountCovered(), 0.001);
  }

  @Test
  public void copayBeforeAndAfterMandate() {
    Costs.loadCostData();
    final long beforeMandateTime = mandateTime - 100;
    final long afterMandateTime = mandateTime + 100;
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    person = new Person(beforeMandateTime);
    person.attributes.put(Person.BIRTHDATE, beforeMandateTime);

    // Before Mandate.
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    person.coverage.setPlanAtTime(beforeMandateTime, testPrivatePayer1Plan);
    Encounter wellnessBeforeMandate =
        person.record.encounterStart(beforeMandateTime, EncounterType.WELLNESS);
    wellnessBeforeMandate.codes.add(code);
    wellnessBeforeMandate.provider = new Provider();
    person.record.encounterEnd(beforeMandateTime, EncounterType.WELLNESS);
    // The copay before the mandate time should be greater than 0.
    assertTrue(testPrivatePayer1Plan.determineCopay(wellnessBeforeMandate) > 0.0);

    Encounter inpatientBeforeMandate
        = person.record.encounterStart(beforeMandateTime, EncounterType.INPATIENT);
    inpatientBeforeMandate.codes.add(code);
    inpatientBeforeMandate.provider = new Provider();
    person.record.encounterEnd(beforeMandateTime, EncounterType.INPATIENT);
    // The copay for a non-wellness encounter should be greater than 0.
    assertTrue(testPrivatePayer1Plan.determineCopay(inpatientBeforeMandate) > 0.0);

    // After Mandate.
    Encounter wellnessAfterMandate
        = person.record.encounterStart(afterMandateTime, EncounterType.WELLNESS);
    wellnessAfterMandate.codes.add(code);
    wellnessAfterMandate.provider = new Provider();
    person.record.encounterEnd(afterMandateTime, EncounterType.WELLNESS);
    // The copay after the mandate time should be 0.
    assertEquals(0.0, testPrivatePayer1Plan.determineCopay(wellnessAfterMandate), 0.000001);

    Encounter inpatientAfterMandate
        = person.record.encounterStart(afterMandateTime, EncounterType.INPATIENT);
    inpatientAfterMandate.codes.add(code);
    inpatientAfterMandate.provider = new Provider();
    person.record.encounterEnd(afterMandateTime, EncounterType.INPATIENT);
    // The copay for a non-wellness encounter should be greater than 0.
    assertTrue(testPrivatePayer1Plan.determineCopay(inpatientAfterMandate) > 0.0);
  }

  @Test
  public void costsUncoveredByNoInsurance() {
    Costs.loadCostData();
    PayerController.loadNoInsurance();
    long time = 0L;
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanAtTime(0L, PayerController.getNoInsurancePlan());
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(0L, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    double totalCost = fakeEncounter.getCost().doubleValue();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // The No Insurance payer should have $0.0 coverage.
    assertEquals(0, PayerController.noInsurance.getAmountCovered(), 0.001);
    // The No Insurance's uncovered costs should equal the total cost.
    assertEquals(totalCost, PayerController.noInsurance.getAmountUncovered(), 0.001);
    // The person's expenses shoudl equal the total cost.
    assertEquals(totalCost, person.coverage.getTotalExpenses(), 0.001);
  }

  @Test
  public void determineCoveredCostWithNullPayer() {
    // Default to No Insurance
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    healthRecord.encounterEnd(0L, EncounterType.INPATIENT);
  }

  @Test
  public void payerCoversEncounter() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanAtTime(0L, testPrivatePayer1.getPlans().iterator().next());
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    encounter.provider = new Provider();
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertTrue(testPrivatePayer1Plan.coversService(encounter));
    healthRecord.encounterEnd(0L, EncounterType.INPATIENT);
    // Person's coverage should equal the cost of the encounter
    double coverage = encounter.claim.totals.coinsurance + encounter.claim.totals.paidByPayer;
    assertEquals(person.coverage.getTotalCoverage(), coverage, 0.001);
    double result = encounter.claim.totals.coinsurance
        + encounter.claim.totals.copay
        + encounter.claim.totals.deductible
        + encounter.claim.totals.paidByPayer
        + encounter.claim.totals.paidByPatient;
    assertEquals(encounter.getCost().doubleValue(), result, 0.001);
    // Person's expenses should equal the copay.
    double expenses = encounter.claim.totals.copay
        + encounter.claim.totals.deductible
        + encounter.claim.totals.paidByPatient;
    assertEquals(person.coverage.getTotalExpenses(), expenses, 0.001);
  }

  @Test
  public void payerDoesNotCoverEncounter() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanAtTime(0L, testPrivatePayer2.getPlans().iterator().next());
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    encounter.provider = new Provider();
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    InsurancePlan testPrivatePayer2Plan = testPrivatePayer2.getPlans().iterator().next();
    assertFalse(testPrivatePayer2Plan.coversService(encounter));
    healthRecord.encounterEnd(0L, EncounterType.INPATIENT);
    // Person's coverage should equal $0.0.
    assertEquals(0.0, person.coverage.getTotalCoverage(), 0.001);
    // Person's expenses should equal the total cost of the encounter.
    assertEquals(person.coverage.getTotalExpenses(), encounter.getCost().doubleValue(), 0.001);
  }

  @Test
  public void personCanAffordPayer() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    int yearlyCostOfPayer = (int) ((plan.getMonthlyPremium() * 12)
        + plan.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer + 1);
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertTrue(person.canAffordPlan(testPrivatePayer1Plan));
  }

  @Test
  public void personCannotAffordPayer() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    int yearlyCostOfPayer = (int) ((plan.getMonthlyPremium() * 12)
        + plan.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer - 1);
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertFalse(person.canAffordPlan(testPrivatePayer1Plan));
  }

  @Test
  public void payerMemberYears() {
    int startYear = 1950;
    long currentTime = Utilities.convertCalendarYearsToTime(startYear);
    person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.BIRTHDATE, currentTime);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.attributes.put(Person.INCOME, (int) HealthInsuranceModule.medicaidLevel * 100);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);

    // Get private insurance for 55 years.
    int numberOfYears = 55;
    for (int age = 0; age < numberOfYears; age++) {
      healthInsuranceModule.process(person, currentTime);
      currentTime += Utilities.convertTime("years", 1);
    }
    int totalYearsCovered = testPrivatePayer1.getNumYearsCovered()
        + testPrivatePayer2.getNumYearsCovered();
    assertEquals(numberOfYears, totalYearsCovered);
  }

  @Test
  public void payerInProviderNetwork() {
    // For now, this returns true by default because it is not yet implememted.
    assertTrue(testPrivatePayer1.isInNetwork(null));
  }
}