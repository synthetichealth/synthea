package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.behaviors.planeligibility.PlanEligibilityFinder;
import org.mitre.synthea.world.agents.behaviors.planeligibility.QualifyingAttributesEligibility;
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
  private static Payer testPrivatePayer1;
  // Covers only wellness encounters.
  private static Payer testPrivatePayer2;
  private static HealthInsuranceModule healthInsuranceModule;
  private static Person person;
  private static double medicaidLevel;
  private static double povertyLevel;
  private static long mandateTime;
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
    povertyLevel =
            Config.getAsDouble("generate.demographics.socioeconomic.income.poverty", 11000);
    medicaidLevel = 1.33 * povertyLevel;
    // Set up Mandate year.
    int mandateYear = Integer.parseInt(Config.get("generate.insurance.mandate.year", "2006"));
    mandateTime = Utilities.convertCalendarYearsToTime(mandateYear);
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "generic/payers/test_plans.csv");
    // Force medicare for test settings
    Config.set("generate.payers.insurance_companies.medicare", "Medicare");
    Config.set("generate.payers.insurance_companies.medicaid", "Medicaid");
    Config.set("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");
  }

  /**
   * Setup before each test.
   */
  @Before
  public void before() {
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    // Clear any Payers that may have already been statically loaded.
    PayerManager.clear();
    PayerManager.loadPayers(new Location(testState, null));
    // Load the two test payers.
    testPrivatePayer1 = PayerManager.getPrivatePayers().get(0);
    testPrivatePayer2 = PayerManager.getPrivatePayers().get(1);
  }

  /**
   * Clean up after tests.
   */
  @AfterClass
  public static void cleanup() {
    Config.set("generate.payers.insurance_companies.default_file",
        "payers/insurance_companies.csv");
    Config.set("generate.payers.insurance_plans.default_file", "payers/insurance_plans.csv");
  }

  @Test
  public void incrementCustomers() {
    long time = Utilities.convertCalendarYearsToTime(1930);
    Person firstPerson = new Person(0L);
    firstPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    firstPerson.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    firstPerson.attributes.put(Person.BIRTHDATE, time);
    firstPerson.attributes.put(Person.GENDER, "F");
    firstPerson.attributes.put(Person.INCOME, 100000);
    // Payer has firstPerson customer from the ages of 0 - 11.
    processInsuranceForAges(firstPerson, 0, 11);

    Person secondPerson = new Person(0L);
    secondPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    secondPerson.attributes.put(Person.OCCUPATION_LEVEL, 0.001);
    secondPerson.attributes.put(Person.BIRTHDATE, time);
    secondPerson.attributes.put(Person.GENDER, "F");
    secondPerson.attributes.put(Person.INCOME, (int) medicaidLevel - 10);
    // Person should have medicaid from the ages of 0 - 9.
    processInsuranceForAges(secondPerson, 0, 9);
    // Person should have private insurance from the ages of 10 - 23.
    secondPerson.attributes.put(Person.INCOME, 100000);
    processInsuranceForAges(secondPerson, 10, 23);
    // Person should have medicaid from the ages of 24 - 54.
    secondPerson.attributes.put(Person.INCOME, (int) medicaidLevel - 10);
    processInsuranceForAges(secondPerson, 24, 54);
    // Person should have private insurance from the ages of 55 - 60.
    secondPerson.attributes.put(Person.INCOME, 100000);
    processInsuranceForAges(secondPerson, 55, 60);

    // Ensure the first person was with the Payers for 12 years.
    assertEquals(12, testPrivatePayer1.getCustomerUtilization(firstPerson)
        + testPrivatePayer2.getCustomerUtilization(firstPerson));
    // Ensure the second person was with the Payers for 20 years.
    assertEquals(20, testPrivatePayer1.getCustomerUtilization(secondPerson)
        + testPrivatePayer2.getCustomerUtilization(secondPerson));
    assertEquals(41, PayerManager.getGovernmentPayer(PayerManager.MEDICAID)
        .getCustomerUtilization(secondPerson));
    // Ensure that there were betwen 2 and 4 unique customers for the Payers.
    assertTrue(testPrivatePayer1.getUniqueCustomers()
        + testPrivatePayer2.getUniqueCustomers() >= 2);
    assertTrue(testPrivatePayer1.getUniqueCustomers()
        + testPrivatePayer2.getUniqueCustomers() <= 4);
  }

  /**
   * Sets the person's payer for the given year range.
   */
  private void processInsuranceForAges(Person person, int startAge, int endAge) {
    HealthInsuranceModule him = new HealthInsuranceModule();
    long birthDate = (long) person.attributes.get(Person.BIRTHDATE);
    long currentTime = birthDate;
    for (int currentAge = startAge; currentAge <= endAge; currentAge++) {
      currentTime = birthDate + Utilities.convertTime("years", currentAge);
      for (int week = 0; week < 52; week++) {
        // Person checks to pay premiums every week.
        long ctime = currentTime + Utilities.convertTime("weeks", week);
        him.process(person, ctime);
      }
    }
  }

  @Test
  public void incrementEncounters() {
    long time = Utilities.convertCalendarYearsToTime(1968);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanAtTime(time, testPrivatePayer1.getPlans().iterator().next());
    HealthRecord healthRecord = new HealthRecord(person);

    Code code = new Code("SNOMED-CT","705129","Fake Code");

    Encounter fakeEncounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    healthRecord.encounterEnd(time, EncounterType.INPATIENT);
    fakeEncounter = healthRecord.encounterStart(time, EncounterType.AMBULATORY);
    fakeEncounter.provider = new Provider();
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time, EncounterType.AMBULATORY);
    fakeEncounter = healthRecord.encounterStart(0L, EncounterType.EMERGENCY);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    healthRecord.encounterEnd(time, EncounterType.EMERGENCY);

    assertEquals(3, testPrivatePayer1.getEncountersCoveredCount());
  }

  @Test
  public void receiveMedicareAgeEligible() {
    int currentYear = 1900;
    long birthTime = Utilities.convertCalendarYearsToTime(currentYear);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put("end_stage_renal_disease", false);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    long timestep = Config.getAsLong("generate.timestep");
    // Process the person's health insurance for 64 years, should have private insurance for all.
    for (int age = 0; age < 65; age++) {
      long currentTime = Utilities.convertCalendarYearsToTime(currentYear) + timestep;
      healthInsuranceModule.process(person, currentTime);
      assertEquals(PayerManager.PRIVATE_OWNERSHIP,
          person.coverage.getPlanAtTime(currentTime).getPayer().getOwnership());
      currentYear++;
    }
    // Process their insurance for ages 65-69, should have medicare every year.
    for (int age = 65; age < 70; age++) {
      long currentTime = Utilities.convertCalendarYearsToTime(currentYear) + timestep * 3;
      healthInsuranceModule.process(person, currentTime);
      assertTrue(person.coverage.getPlanAtTime(currentTime).isMedicarePlan());
      assertTrue(person.coverage.getPlanAtTime(currentTime).accepts(person, currentTime));
      currentYear++;
    }
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
    assertEquals(PayerManager.getGovernmentPayer(PayerManager.MEDICARE),
        person.coverage.getPlanAtTime(0L).getPayer());
  }

  @Test
  public void receiveMedicareSsdBreastCancerEligible() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.record.conditionStart(time, "254837009");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, time);
    assertEquals(PayerManager.getGovernmentPayer(PayerManager.MEDICARE),
        person.coverage.getPlanAtTime(time).getPayer());
  }

  @Test
  public void receiveMedicaidPregnancyElgible() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put("pregnant", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // A pregnant person is eligble in MA when their income is less than 2 * the poverty level.
    person.attributes.put(Person.INCOME, (int) (povertyLevel * 2) - 1);
    healthInsuranceModule.process(person, time);
    assertEquals(PayerManager.MEDICAID,
        person.coverage.getPlanAtTime(time).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(time).accepts(person, time));
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
  public void receiveMedicaidMnilEligble() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    // Receive Medciare after having too high of an income, but later qualifying for MNIL.
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.1);

    // The minimum income that does not qualify for MA Medicaid at age 0 is 12880 * 2.0 + 1 = 25761
    person.attributes.put(Person.INCOME, (int) 25761);
    healthInsuranceModule.process(person, time);
    // They should have not have Medicaid in this first year.
    assertNotEquals("Medicaid", person.coverage.getPlanAtTime(time).getPayer().getName());
    // The MA yearly spenddown amount is $6264. They need to incur $19499 in healthcare expenses.
    person.coverage.getPlanRecordAtTime(time).incrementExpenses(19699);
    // Now process their insurance and they should switch to Medicaid.
    time += Utilities.convertTime("years", 1.001);
    healthInsuranceModule.process(person, time);
    assertEquals("Medicaid", person.coverage.getPlanAtTime(time).getPayer().getName());
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
    assertEquals(PayerManager.MEDICAID,
        person.coverage.getPlanAtTime(age64Time).getPayer().getName());
    // The person is now 65 and qualifies for Medicare in addition to Medicaid.
    healthInsuranceModule.process(person, age65Time);
    assertEquals(PayerManager.DUAL_ELIGIBLE,
        person.coverage.getPlanAtTime(age65Time).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(age65Time)
        .accepts(person, age65Time));
  }

  @Test
  public void receiveNoInsurance() {
    // Person's income cannot afford the test private insurance.
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    double monthlyPremium = plan.getMonthlyPremium();
    double deductible = plan.getDeductible();
    double totalYearlyCost = (monthlyPremium * 12) + deductible;
    long birthTime = Utilities.convertCalendarYearsToTime(1980);

    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.001);
    // Give the person an income lower than the totalYearlyCost divided by the max
    // income ratio a person is willing to spend on health insurance.
    // This value is greater than a 0-year-old's poverty multiplier in MA (2 * 12880).
    double costThreshold = ((double) totalYearlyCost)
        / Config.getAsDouble("generate.payers.insurance_plans.income_premium_ratio");
    person.attributes.put(Person.INCOME, (int) costThreshold - 10);

    healthInsuranceModule.process(person, birthTime);
    InsurancePlan newPlan = person.coverage.getPlanAtTime(birthTime);
    assertTrue(newPlan.isNoInsurance());

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
    assertFalse(person.coverage.getPlanAtTime(time).isNoInsurance());
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
    assertFalse(person.coverage.getPlanAtTime(time).isNoInsurance());
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
    assertNotNull(PayerManager.getGovernmentPayer(PayerManager.MEDICARE));
    assertNotNull(PayerManager.getGovernmentPayer(PayerManager.MEDICAID));
    for (Payer payer : PayerManager.getGovernmentPayers()) {
      assertEquals(PayerManager.GOV_OWNERSHIP, payer.getOwnership());
    }
  }

  @Test
  public void invalidGovernmentPayer() {
    assertNull(PayerManager.getGovernmentPayer("Hollywood Healthcare"));
  }

  @Test
  public void loadAllPayers() {
    int numGovernmentPayers = PayerManager.getGovernmentPayers().size();
    int numPrivatePayers = PayerManager.getPrivatePayers().size();
    assertEquals(numGovernmentPayers + numPrivatePayers, PayerManager.getAllPayers().size());
  }

  @Test(expected = RuntimeException.class)
  public void nullPayerName() {
    PayerManager.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/bad_test_payers.csv");
    PayerManager.loadPayers(new Location("Massachusetts", null));
  }

  @Test
  public void monthlyPremiumPayment() {
    person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.BIRTHDATE, Utilities.convertCalendarYearsToTime(1930));
    // Predetermine person's Payer.
    processInsuranceForAges(person, 0, 64);

    int payer1MemberYears = testPrivatePayer1.getCustomerUtilization(person);
    int payer2MemberYears = testPrivatePayer2.getCustomerUtilization(person);

    double totalMonthlyPremiumsOwed = 0.0;
    totalMonthlyPremiumsOwed += testPrivatePayer1.getPlans().iterator().next().getMonthlyPremium()
        * 12 * payer1MemberYears;
    totalMonthlyPremiumsOwed += testPrivatePayer2.getPlans().iterator().next().getMonthlyPremium()
        * 12 * payer2MemberYears;
    double totalRevenue = 0.0;
    totalRevenue += testPrivatePayer1.getRevenue();
    totalRevenue += testPrivatePayer2.getRevenue();
    // The payer's revenue should equal the total monthly premiums.
    assertEquals(totalMonthlyPremiumsOwed, totalRevenue, 0.001);
    // The person's health care expenses should equal the total monthly premiums.
    assertEquals(totalMonthlyPremiumsOwed, person.coverage.getTotalPremiumExpenses(), 0.001);
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
    double expectedTotalCost = fakeEncounter.getCost().doubleValue();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // Check that the deductibles are accurate.
    assertEquals(plan.getDeductible(), fakeEncounter.claim.totals.paidByDeductible, 0.001);
    // check that totals match
    assertEquals(expectedTotalCost, fakeEncounter.claim.totals.cost, 0.001);
    double resultCost = fakeEncounter.claim.totals.paidByPayer
        + fakeEncounter.claim.totals.paidByPatient;
    assertEquals(expectedTotalCost, resultCost, 0.001);
    assertEquals(fakeEncounter.claim.totals.paidByPatient, fakeEncounter.claim.totals.copay, 0.001);
    // The total cost should equal the Cost to the Payer summed with the Payer's copay amount.
    assertEquals(expectedTotalCost, testPrivatePayer1.getAmountCovered()
        + fakeEncounter.claim.getPatientCost(), 0.001);
    // The total cost should equal the Payer's uncovered costs plus the Payer's covered costs.
    assertEquals(expectedTotalCost,
        testPrivatePayer1.getAmountCovered() + testPrivatePayer1.getAmountUncovered(), 0.001);
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
    PayerManager.loadNoInsurance();
    long time = 0L;
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanAtTime(0L, PayerManager.getNoInsurancePlan());
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(0L, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    double totalCost = fakeEncounter.getCost().doubleValue();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // The No Insurance payer should have $0.0 coverage.
    assertEquals(0, PayerManager.noInsurance.getAmountCovered(), 0.001);
    // The No Insurance's uncovered costs should equal the total cost.
    assertEquals(totalCost, PayerManager.noInsurance.getAmountUncovered(), 0.001);
    // The person's expenses shoudl equal the total cost.
    assertEquals(totalCost, person.coverage.getTotalHealthcareExpenses(), 0.001);
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
    double coverage = encounter.claim.totals.coinsurancePaidByPayer
        + encounter.claim.totals.paidByPayer;
    assertEquals(person.coverage.getTotalCoverage(), coverage, 0.001);
    double result = encounter.claim.totals.paidByPayer
        + encounter.claim.totals.paidByPatient;
    assertEquals(encounter.getCost().doubleValue(), result, 0.001);
    // Person's expenses should equal the copay.
    double expenses = encounter.claim.totals.paidByPatient;
    assertEquals(person.coverage.getTotalHealthcareExpenses(), expenses, 0.001);
    assertEquals(encounter.claim.totals.copay, expenses, 0.001);
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
    assertEquals(person.coverage.getTotalHealthcareExpenses(),
        encounter.getCost().doubleValue(), 0.001);
  }

  @Test
  public void personCanAffordPayer() {
    String willingToSpend = Config.get("generate.payers.insurance_plans.income_premium_ratio");
    // Set their willinginess to spend to 100%.
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "1.0");
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    int yearlyCostOfPayer = (int) ((plan.getMonthlyPremium() * 12)
        + plan.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer + 1);
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertTrue(person.canAffordPlan(testPrivatePayer1Plan));
    // Reset the person's willingness to spend.
    Config.set("generate.payers.insurance_plans.income_premium_ratio", willingToSpend);
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
  public void keepPreviousInsurance() {
    long time = Utilities.convertCalendarYearsToTime(1960);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");
    HealthInsuranceModule hm = new HealthInsuranceModule();
    hm.process(person, time);
    Payer inititalPayer = person.coverage.getPlanAtTime(time).getPayer();
    assertFalse("Person should have insurance.", inititalPayer.isNoInsurance());
    assertFalse("Person should have private insurance.", inititalPayer.isGovernmentPayer());
    time += Utilities.convertTime("years", 1);
    hm.process(person, time);
    assertEquals("Person should keep the same insurance.",
        inititalPayer, person.coverage.getPlanAtTime(time).getPayer());
  }


  @Test
  public void payerMemberYears() {
    int startYear = 1950;
    long currentTime = Utilities.convertCalendarYearsToTime(startYear);
    person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.BIRTHDATE, currentTime);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.attributes.put(Person.INCOME, (int) HealthInsuranceModule.povertyLevel * 100);
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
  public void checkCopayPaid() {
    long time = Utilities.convertCalendarYearsToTime(1960);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");
    HealthInsuranceModule hm = new HealthInsuranceModule();

    person.coverage.setPlanAtTime(time, testPrivatePayer1.getPlans().iterator().next());
    hm.process(person, time);
    InsurancePlan plan = person.coverage.getPlanAtTime(time);
    assertFalse("Person should have insurance.", plan.getPayer().isNoInsurance());
    assertFalse("Person should have private insurance.", plan.getPayer().isGovernmentPayer());

    HealthRecord healthRecord = new HealthRecord(person);
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    Encounter fakeEncounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.INPATIENT);

    double patientCoinsurance = plan.getPatientCoinsurance();
    double copay = plan.determineCopay(fakeEncounter);
    assertTrue(plan.isCopayBased());
    double encounterCost = fakeEncounter.getCost().doubleValue();
    double expectedPaid = (patientCoinsurance * encounterCost) + copay;
    double coinsurancePaid = fakeEncounter.claim.getCoinsurancePaid();
    double copayPaid = fakeEncounter.claim.getCopayPaid();

    assertEquals("The amount paid should be equal to the plan's coinsurance rate plus the copay."
        + " The payer is " + plan.getPayer().getName() + ".",
        expectedPaid, coinsurancePaid + copayPaid, 0.01);
  }

  @Test
  public void checkCoinsurancePaid() {
    long time = Utilities.convertCalendarYearsToTime(1960);
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");
    HealthInsuranceModule hm = new HealthInsuranceModule();

    person.coverage.setPlanAtTime(time, testPrivatePayer2.getPlans().iterator().next());
    hm.process(person, time);
    InsurancePlan plan = person.coverage.getPlanAtTime(time);
    assertFalse("Person should have insurance.", plan.getPayer().isNoInsurance());
    assertFalse("Person should have private insurance.", plan.getPayer().isGovernmentPayer());

    HealthRecord healthRecord = new HealthRecord(person);
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    Encounter fakeEncounter = healthRecord.encounterStart(time, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.WELLNESS);

    double patientCoinsurance = plan.getPatientCoinsurance();
    double copay = plan.determineCopay(fakeEncounter);
    assertFalse(plan.isCopayBased());
    double encounterCost = fakeEncounter.getCost().doubleValue();
    double expectedPaid = (patientCoinsurance * encounterCost) + copay;
    double coinsurancePaid = fakeEncounter.claim.getCoinsurancePaid();
    double copayPaid = fakeEncounter.claim.getCopayPaid();

    assertEquals(1 - plan.getPatientCoinsurance(), plan.getPayerCoinsurance(), 0.01);
    assertEquals("The amount paid should be equal to the plan's coinsurance rate plus the copay."
        + " The payer is " + plan.getPayer().getName() + ".",
        expectedPaid, coinsurancePaid + copayPaid, 0.01);
  }

  @Test
  public void payerInProviderNetwork() {
    // For now, this returns true by default because it is not yet implememted.
    assertTrue(testPrivatePayer1.isInNetwork(null));
  }

  @Test
  public void personKeepsPreviousInsurance() {
    HealthInsuranceModule him = new HealthInsuranceModule();
    long time = Utilities.convertCalendarYearsToTime(1980);
    person = new Person(0L);
    him.process(person, time);
    InsurancePlan firstPlan = person.coverage.getPlanAtTime(time);
    time += Utilities.convertTime("years", 1.5);
    InsurancePlan secondPlan = person.coverage.getPlanAtTime(time);
    // For now, this returns true by default because it is not yet implememted.
    assertEquals(firstPlan, secondPlan);
  }

  @Test
  public void qualifyingAttributesFileEligibility() {
    String fileName = "generic/payers/test_attributes_eligibility.csv";
    QualifyingAttributesEligibility qae = new QualifyingAttributesEligibility(fileName);
    long time = Utilities.convertCalendarYearsToTime(1975);
    person = new Person(0L);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test1", true);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test1", false);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test3", 5.5);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test3", 5.49);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test3", 5.501);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.remove("test3");
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test4", 4.2);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test4", 4.21);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test5", "TEST");
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test5", "INVALID");
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test6", -50);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test6", 12.1);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test6", 12.09);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test6", 14);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test7", "FALSE");
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test7", "TRUE");
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test8", true);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test8", false);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test9", 10.0);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test9", 10);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test9", 9.9);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test10", 5);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test10", 5.0);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test10", 6);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test11", 456);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test11", 456.0);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test11", 457);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test11", 455);
    assertFalse(qae.isPersonEligible(person, time));
  }

  @Test(expected = RuntimeException.class)
  public void getGovPlanFromPrivatePayer() {
    testPrivatePayer1.getGovernmentPayerPlan();
  }

  @Test(expected = RuntimeException.class)
  public void getNonexistantEligibility() {
    PlanEligibilityFinder.getEligibilityAlgorithm("FAKE");
  }

}