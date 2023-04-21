package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.mitre.synthea.world.agents.behaviors.planfinder.IPlanFinder;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

public class PayerTest {

  private static String testState = Config.get("test_state.default", "Massachusetts");
  // Covers all healthcare.
  private static Payer testPrivatePayer1;
  // Covers only wellness encounters.
  private static Payer testPrivatePayer2;
  private static HealthInsuranceModule healthInsModule;
  private static double medicaidLevel;
  private static long mandateTime;
  private static double minPrivateAffordability;
  private static Location location = new Location(testState, null);

  /**
   * Setup for Payer Tests.
   * @throws Exception on configuration loading error
   */
  @BeforeClass
  public static void setup() throws Exception {
    TestHelper.loadTestProperties();
    // Set up Medicaid numbers.
    healthInsModule = new HealthInsuranceModule();
    // In MA, the minimum poverty multiplier is 1.33.
    medicaidLevel = 1.33 * HealthInsuranceModule.povertyLevel;
    // Set up Mandate year.
    int mandateYear = Integer.parseInt(Config.get("generate.insurance.mandate.year", "2006"));
    mandateTime = Utilities.convertCalendarYearsToTime(mandateYear);
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
    Config.set("generate.payers.insurance_plans.default_file",
        "generic/payers/test_plans.csv");
    Config.set("generate.payers.insurance_plans.eligibilities_file",
        "generic/payers/test_insurance_eligibilities.csv");
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "1.0");
    // Clear and reset Payers that may have already been statically loaded.
    PayerManager.clear();
    PayerManager.loadPayers(location);
    // Load the two test payers.
    Set<Payer> privatePayers = PayerManager.getAllPayers().stream()
        .filter(payer -> payer.getOwnership().equals(PayerManager.PRIVATE_OWNERSHIP))
        .collect(Collectors.toSet());
    testPrivatePayer1 = privatePayers.stream().filter(payer ->
        payer.getName().equals("Test Private Payer 1")).iterator().next();
    testPrivatePayer2 = privatePayers.stream().filter(payer ->
        payer.getName().equals("Test Private Payer 2")).iterator().next();
    minPrivateAffordability = Math.max(testPrivatePayer1.getPlans().iterator().next()
        .getMonthlyPremium(0).multiply(new BigDecimal(12)).doubleValue(),
        testPrivatePayer2.getPlans().iterator().next().getMonthlyPremium(0)
        .multiply(new BigDecimal(12)).doubleValue()) * 2;
  }

  /**
   * Clean up after tests.
   */
  @AfterClass
  public static void cleanup() {
    Config.set("generate.payers.insurance_companies.default_file",
        "payers/insurance_companies.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "payers/insurance_plans.csv");
    Config.set("generate.payers.insurance_plans.eligibilities_file",
        "payers/insurance_eligibilities.csv");
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "0.034");
    // Clear and reset Payers that may have already been statically loaded.
    PayerManager.clear();
    PayerManager.loadPayers(location);
  }

  @Test
  public void incrementCustomers() {
    long time = Utilities.convertCalendarYearsToTime(1930);
    Person firstPerson = new Person(0L);
    firstPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    firstPerson.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    firstPerson.attributes.put(Person.BIRTHDATE, time);
    firstPerson.attributes.put(Person.GENDER, "F");
    firstPerson.attributes.put(Person.INCOME, (int) minPrivateAffordability);

    // Private payers should have firstPerson from the ages of 0 - 11.
    processInsuranceForAges(firstPerson, 0, 11);

    Person secondPerson = new Person(0L);
    secondPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    secondPerson.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    secondPerson.attributes.put(Person.BIRTHDATE, time);
    secondPerson.attributes.put(Person.GENDER, "F");
    secondPerson.attributes.put(Person.INCOME, (int) medicaidLevel - 1);

    // Second person should have medicaid from the ages of 0 - 9.
    processInsuranceForAges(secondPerson, 0, 9);
    // Second person should have private insurance from the ages of 10 - 23.
    secondPerson.attributes.put(Person.INCOME, (int) minPrivateAffordability);
    processInsuranceForAges(secondPerson, 10, 23);
    // Second person should have medicaid from the ages of 24 - 54.
    secondPerson.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    processInsuranceForAges(secondPerson, 24, 54);
    // Second person should have private insurance from the ages of 55 - 60.
    secondPerson.attributes.put(Person.INCOME, (int) minPrivateAffordability);
    processInsuranceForAges(secondPerson, 55, 60);

    // Ensure the first person was with the private payers for 12 years.
    String firstPersonId = (String) firstPerson.attributes.get(Person.ID);
    assertEquals(12, testPrivatePayer1.getCustomerUtilization(firstPersonId)
        + testPrivatePayer2.getCustomerUtilization(firstPersonId));
    // Ensure the second person was with the private payers for 20 years.
    String secondPersonId = (String) secondPerson.attributes.get(Person.ID);
    assertEquals(20, testPrivatePayer1.getCustomerUtilization(secondPersonId)
        + testPrivatePayer2.getCustomerUtilization(secondPersonId));
    assertEquals(41, getGovernmentPayer(PayerManager.MEDICAID)
        .getCustomerUtilization(secondPersonId));
    // Ensure that there were betwen 2 and 4 unique customers for the Payers.
    assertTrue(testPrivatePayer1.getUniqueCustomers()
        + testPrivatePayer2.getUniqueCustomers() >= 2);
    assertTrue(testPrivatePayer1.getUniqueCustomers()
        + testPrivatePayer2.getUniqueCustomers() <= 4);
  }

  /**
   * Sets the person's payer for the given age ranges.
   */
  private void processInsuranceForAges(Person person, int startAge, int endAge) {
    long timestep = Config.getAsLong("generate.timestep");
    long birthDate = (long) person.attributes.get(Person.BIRTHDATE);
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(birthDate);
    c.add(Calendar.YEAR, startAge);
    long currentTime = c.getTimeInMillis();
    for (int currentAge = startAge; currentAge <= endAge; currentAge++) {
      c.add(Calendar.YEAR, 1);
      while (currentTime < c.getTimeInMillis()) {
        healthInsModule.process(person, currentTime);
        currentTime += timestep;
      }
    }
  }

  @Test
  public void incrementEncounters() {
    long time = Utilities.convertCalendarYearsToTime(1968);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.INCOME, 0);
    healthInsModule.process(person, time);
    person.coverage.setPlanAtTime(time, testPrivatePayer1.getPlans()
        .iterator().next(), PayerManager.getNoInsurancePlan());
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
    fakeEncounter = healthRecord.encounterStart(time, EncounterType.EMERGENCY);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    healthRecord.encounterEnd(time, EncounterType.EMERGENCY);

    assertEquals(3, testPrivatePayer1.getEncountersCoveredCount());
  }

  @Test
  public void receiveMedicareAgeEligible() {
    TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    final int birthYear = 1900;
    long birthTime = Utilities.convertCalendarYearsToTime(birthYear);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put("end_stage_renal_disease", false);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);

    // Process the person's health insurance for 64 years, should have private insurance for all.
    for (int age = 0; age < 65; age++) {
      long currentTime = Utilities.convertCalendarYearsToTime(birthYear + age);
      assertTrue("Expected " + age + " but got " + person.age(currentTime),
          person.age(currentTime).getYears() == age);
      healthInsModule.process(person, currentTime);
      assertEquals(PayerManager.PRIVATE_OWNERSHIP,
          person.coverage.getPlanAtTime(currentTime).getPayer().getOwnership());
    }
    // Process their insurance for ages 65-69, should have medicare every year.
    for (int age = 65; age < 70; age++) {
      long currentTime = Utilities.convertCalendarYearsToTime(birthYear + age);
      healthInsModule.process(person, currentTime);
      String payerName = person.coverage.getPlanAtTime(currentTime).getPayer().getName();
      assertTrue(person.age(currentTime).getYears() == age);
      assertTrue("Expected Medicare but was " + payerName + ". Person is age "
          + person.age(currentTime) + ".", payerName.equals("Medicare"));
      assertTrue(person.coverage.getPlanAtTime(currentTime).accepts(person, currentTime));
    }
  }

  @Test
  public void receiveMedicareEsrdEligible() {
    // ESRD
    long time = Utilities.convertCalendarYearsToTime(1980);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put("end_stage_renal_disease", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsModule.process(person, time);
    Payer currentPayer = person.coverage.getPlanAtTime(time).getPayer();
    assertEquals("Expected Medicare but was " + currentPayer.getName() + ".",
        getGovernmentPayer(PayerManager.MEDICARE), currentPayer);
  }

  @Test
  public void receiveMedicareSsdBreastCancerEligible() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    person.setProvider(EncounterType.WELLNESS, TestHelper.buildMockProvider());
    // Process health insurance prior to condition start to prevent null pointer.
    healthInsModule.process(person, time);
    person.record.conditionStart(time, "254837009");
    // Process health insurance after condition start now that the person is eligible.
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(time);
    c.add(Calendar.YEAR, 1);
    time = c.getTimeInMillis();
    healthInsModule.process(person, time);
    assertEquals(getGovernmentPayer(PayerManager.MEDICARE),
        person.coverage.getPlanAtTime(time).getPayer());
  }

  @Test
  public void receiveMedicaidPregnancyEligible() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put("pregnant", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // A pregnant person is eligble in MA when their income is less than 2 * the poverty level.
    person.attributes.put(Person.INCOME, (int) (HealthInsuranceModule.povertyLevel * 2) - 1);
    healthInsModule.process(person, time);
    assertEquals(PayerManager.MEDICAID,
        person.coverage.getPlanAtTime(time).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(time).accepts(person, time));
  }

  @Test
  public void receiveMedicaidPovertyEligible() {
    // Poverty Level
    long time = 0L;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.BLINDNESS, false);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);

    healthInsModule.process(person, time);
    assertEquals("Medicaid", person.coverage.getPlanAtTime(time).getPayer().getName());
  }

  @Test
  public void receiveMedicaidBlindnessEligble() {
    // Blindness
    long time = 0L;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.BLINDNESS, true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsModule.process(person, time);
    assertEquals("Medicaid", person.coverage.getPlanAtTime(time).getPayer().getName());
  }

  @Test
  public void planTimeBoxRanges() {
    // There is a "Fake time-boxed Dual-Eligble Plan" that has an eligibility unique to this test:
    // If a patient has the attribute "time-boxed-test" as true, they will get Dual Eligible.
    // However, this unique path to Dual Eligble is only available from 1965-1968.
    // Load the time-boxed plans.
    PayerManager.clear();
    PayerManager.loadPayers(location);

    int currentYear = 1960;
    long time = Utilities.convertCalendarYearsToTime(currentYear);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put("time-boxed-test", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);

    // For the first 5 years, they should not have Dual Eligible (1960-1964).
    for (int i = 0; i < 5; i++) {
      healthInsModule.process(person, time);
      assertNotEquals("Dual Eligible", person.coverage.getPlanAtTime(time).getPayer().getName());
      currentYear++;
      time = Utilities.convertCalendarYearsToTime(currentYear);
    }
    // For the next 4 years, they should have Dual Eligible (1965-1968).
    for (int i = 0; i < 4; i++) {
      healthInsModule.process(person, time);
      assertEquals("Dual Eligible", person.coverage.getPlanAtTime(time).getPayer().getName());
      currentYear++;
      time = Utilities.convertCalendarYearsToTime(currentYear);
    }

    // After that, they should no longer have Dual Eligible (1969).
    healthInsModule.process(person, time);
    assertNotEquals("Dual Eligible", person.coverage.getPlanAtTime(time).getPayer().getName());
  }


  @Test
  public void receiveMedicaidMnilEligble() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    // Receive Medciare after having too high of an income, but later qualifying for MNIL
    // via spenddowns.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.1);
    // The minimum income that does not qualify for MA Medicaid at age 0 is FPL * 2.0 + 1
    int income = (int) (HealthInsuranceModule.povertyLevel * 2 + 1);
    person.attributes.put(Person.INCOME, income);

    healthInsModule.process(person, time);
    // They should not have Medicaid in this first year.
    assertNotEquals("Medicaid", person.coverage.getPlanAtTime(time).getPayer().getName());
    // The MA yearly spenddown amount is $6264. They need $(income - 6264) in healthcare expenses.
    person.coverage.getPlanRecordAtTime(time)
        .incrementOutOfPocketExpenses(BigDecimal.valueOf(income - 6264));
    // Now process their insurance and they should switch to Medicaid.
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(time);
    c.add(Calendar.YEAR, 1);
    time = c.getTimeInMillis();
    healthInsModule.process(person, time);
    assertEquals("Medicaid", person.coverage.getPlanAtTime(time).getPayer().getName());
  }

  @Test
  public void receiveDualEligible() {
    int birthYear = 1950;
    long birthTime = Utilities.convertCalendarYearsToTime(birthYear);
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(birthTime);

    // Below Poverty Level and Over 65, thus Dual Eligble.
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    // Process the person's health insurance for 64 years, should have Medicaid for all.
    for (int age = 0; age < 65; age++) {
      long currentTime = Utilities.convertCalendarYearsToTime(birthYear + age);
      assertTrue("Expected " + age + " but got " + person.age(currentTime),
          person.age(currentTime).getYears() == age);
      healthInsModule.process(person, currentTime);
      assertEquals(getGovernmentPayer(PayerManager.MEDICAID),
          person.coverage.getPlanAtTime(currentTime).getPayer());
    }
    c.setTimeInMillis(birthTime);
    c.add(Calendar.YEAR, 64);
    long age64Time = c.getTimeInMillis();
    assertEquals(PayerManager.MEDICAID,
        person.coverage.getPlanAtTime(age64Time).getPayer().getName());
    // The person is now 65 and qualifies for Medicare in addition to Medicaid.
    c.add(Calendar.YEAR, 1);
    long age65Time = c.getTimeInMillis();
    healthInsModule.process(person, age65Time);
    assertEquals(PayerManager.DUAL_ELIGIBLE,
        person.coverage.getPlanAtTime(age65Time).getPayer().getName());
    assertTrue(person.coverage.getPlanAtTime(age65Time)
        .accepts(person, age65Time));
  }

  @Test
  public void receiveNoInsurance() {
    // Person's income cannot afford the test private insurance.
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    BigDecimal monthlyPremium = plan.getMonthlyPremium(0);
    BigDecimal deductible = plan.getDeductible();
    BigDecimal totalYearlyCost = monthlyPremium.multiply(BigDecimal.valueOf(12)).add(deductible);
    long birthTime = Utilities.convertCalendarYearsToTime(1980);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.001);

    // Give the person an income lower than the totalYearlyCost divided by the max
    // income ratio a person is willing to spend on health insurance.
    // This value is greater than a 0-year-old's poverty multiplier in MA (2 * 12880).
    BigDecimal costThreshold = totalYearlyCost.divide(BigDecimal.valueOf(
        Config.getAsDouble("generate.payers.insurance_plans.income_premium_ratio")),
        RoundingMode.HALF_UP);
    costThreshold = costThreshold.subtract(BigDecimal.valueOf(10));
    person.attributes.put(Person.INCOME, costThreshold.intValue());

    healthInsModule.process(person, birthTime);
    InsurancePlan newPlan = person.coverage.getPlanAtTime(birthTime);
    assertTrue("Expected No Insurance but was " + newPlan.getPayer().getName()
        + ".", newPlan.isNoInsurance());
  }

  @Test
  public void receivePrivateInsurancePostMandate() {
    // Post 2006 Mandate.
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(mandateTime);
    c.add(Calendar.YEAR, 1);
    long time = c.getTimeInMillis();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Barely above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel + 100);
    healthInsModule.process(person, time);
    assertFalse(person.coverage.getPlanAtTime(time).isNoInsurance());
  }

  @Test
  public void receivePrivateInsuranceWithWealth() {
    // Wealthy Enough to Purchase Private Insurance.
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(mandateTime);
    c.add(Calendar.YEAR, -50);
    long time = c.getTimeInMillis();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);

    healthInsModule.process(person, time);
    assertFalse(person.coverage.getPlanAtTime(time).isNoInsurance());
  }

  @Test
  public void overwriteInsurance() {
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    person.coverage.setPlanAtTime(0L, plan, PayerManager.getNoInsurancePlan());
    person.coverage.setPlanAtTime(0L, plan, PayerManager.getNoInsurancePlan());
    assertEquals(2, person.coverage.getPlanHistory().size());
  }

  private Payer getGovernmentPayer(String payerName) {
    return PayerManager.getAllPayers().stream()
        .filter(payer -> payer.getName().equals(payerName)).iterator().next();
  }

  @Test
  public void loadGovernmentPayers() {
    Payer medicare = getGovernmentPayer(PayerManager.MEDICARE);
    Payer medicaid = getGovernmentPayer(PayerManager.MEDICAID);
    Payer dualEligible = getGovernmentPayer(PayerManager.DUAL_ELIGIBLE);

    assertNotNull(medicare);
    assertNotNull(medicaid);
    assertNotNull(dualEligible);
    assertEquals(PayerManager.getAllPayers().stream().filter(payer -> payer.getOwnership()
        .equals(PayerManager.GOV_OWNERSHIP)).count(), 3);
  }

  @Test
  public void loadAllPayers() {
    long numGovernmentPayers = PayerManager.getAllPayers().stream()
        .filter(payer -> payer.getOwnership().equals(PayerManager.GOV_OWNERSHIP)).count();
    long numPrivatePayers = PayerManager.getAllPayers().stream()
        .filter(payer -> payer.getOwnership().equals(PayerManager.PRIVATE_OWNERSHIP)).count();
    assertEquals(numGovernmentPayers + numPrivatePayers, PayerManager.getAllPayers().size());
  }

  @Test(expected = RuntimeException.class)
  public void nullPayerName() {
    PayerManager.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/bad_test_payers.csv");
    PayerManager.loadPayers(location);
  }

  @Test
  public void monthlyPremiumPayment() {
    Person person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.attributes.put(Person.INCOME, (int) minPrivateAffordability);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    person.attributes.put(Person.BIRTHDATE, Utilities.convertCalendarYearsToTime(1930));

    // Predetermine person's Payer.
    processInsuranceForAges(person, 0, 64);

    String personId = (String) person.attributes.get(Person.ID);
    BigDecimal payer1MemberYears
        = BigDecimal.valueOf(testPrivatePayer1.getCustomerUtilization(personId));
    BigDecimal payer2MemberYears
        = BigDecimal.valueOf(testPrivatePayer2.getCustomerUtilization(personId));
    assertEquals(new BigDecimal(65), payer1MemberYears.add(payer2MemberYears));

    BigDecimal totalMonthlyPremiumsOwed = BigDecimal.ZERO;
    BigDecimal payerOneYearlyPremium = testPrivatePayer1.getPlans().iterator()
        .next().getMonthlyPremium(0).multiply(BigDecimal.valueOf(12));
    totalMonthlyPremiumsOwed = totalMonthlyPremiumsOwed
        .add(payerOneYearlyPremium.multiply(payer1MemberYears));
    BigDecimal payerTwoYearlyPremium = testPrivatePayer2.getPlans().iterator()
        .next().getMonthlyPremium(0).multiply(BigDecimal.valueOf(12));
    totalMonthlyPremiumsOwed = totalMonthlyPremiumsOwed
        .add(payerTwoYearlyPremium.multiply(payer2MemberYears)).setScale(2, RoundingMode.CEILING);

    BigDecimal totalRevenue = BigDecimal.ZERO;
    totalRevenue = totalRevenue.add(testPrivatePayer1.getRevenue());
    totalRevenue = totalRevenue.add(testPrivatePayer2.getRevenue());
    // The payer's revenue should equal the total monthly premiums.
    assertTrue("Expected " + totalMonthlyPremiumsOwed + " But was "
        + totalRevenue + ",", totalMonthlyPremiumsOwed.compareTo(totalRevenue) == 0);
    // The person's health care expenses should equal the total monthly premiums.
    assertEquals(totalMonthlyPremiumsOwed, person.coverage.getTotalPremiumExpenses());
  }

  @Test(expected = RuntimeException.class)
  public void monthlyPremiumPaymentToNullPayer() {
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.checkToPayMonthlyPremium(0L);
  }

  @Test
  public void costsCoveredByPayer() {
    long time = 0L;
    Costs.loadCostData();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    healthInsModule.process(person, time);
    person.coverage.setPlanAtTime(time, plan, PayerManager.getNoInsurancePlan());
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(time, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();

    BigDecimal expectedTotalCost = fakeEncounter.getCost();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // Check that the deductibles are accurate.
    assertTrue(plan.getDeductible()
        .compareTo(fakeEncounter.claim.totals.deductiblePaidByPatient) == 0.0);
    // check that totals match
    assertEquals(expectedTotalCost, fakeEncounter.claim.totals.cost);
    BigDecimal resultCost = fakeEncounter.claim.totals.paidByPayer
        .add(fakeEncounter.claim.totals.patientOutOfPocket)
        .add(fakeEncounter.claim.totals.coinsurancePaidByPayer)
        .add(fakeEncounter.claim.totals.copayPaidByPatient)
        .add(fakeEncounter.claim.totals.deductiblePaidByPatient);
    assertEquals(expectedTotalCost, resultCost);
    // The total cost should equal the Cost to the Payer summed with the Payer's copay amount.
    assertEquals(expectedTotalCost, testPrivatePayer1.getAmountCovered()
        .add(fakeEncounter.claim.getTotalPatientCost()));
    // The total cost should equal the Payer's uncovered costs plus the Payer's covered costs.
    assertEquals(expectedTotalCost,
        testPrivatePayer1.getAmountCovered().add(testPrivatePayer1.getAmountUncovered()));
    // The total coverage by the payer should equal the person's covered costs.
    assertEquals(person.coverage.getTotalCoverage(), (testPrivatePayer1.getAmountCovered()));
  }

  @Test
  public void copayBeforeAndAfterMandate() {
    Costs.loadCostData();
    final long beforeMandateTime = mandateTime - 100;
    final long afterMandateTime = mandateTime + 100;
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Person person = new Person(beforeMandateTime);
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    person.attributes.put(Person.BIRTHDATE, beforeMandateTime);

    // Before Mandate.
    healthInsModule.process(person, beforeMandateTime);
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    person.coverage.setPlanAtTime(beforeMandateTime, testPrivatePayer1Plan,
        PayerManager.getNoInsurancePlan());
    Encounter wellnessBeforeMandate =
        person.record.encounterStart(beforeMandateTime, EncounterType.WELLNESS);
    wellnessBeforeMandate.codes.add(code);
    wellnessBeforeMandate.provider = new Provider();
    person.record.encounterEnd(beforeMandateTime, EncounterType.WELLNESS);
    // The copay before the mandate time should be greater than 0.
    assertTrue(testPrivatePayer1Plan.determineCopay(wellnessBeforeMandate.type,
        wellnessBeforeMandate.start).compareTo(BigDecimal.ZERO) == 1);

    Encounter inpatientBeforeMandate
        = person.record.encounterStart(beforeMandateTime, EncounterType.INPATIENT);
    inpatientBeforeMandate.codes.add(code);
    inpatientBeforeMandate.provider = new Provider();
    person.record.encounterEnd(beforeMandateTime, EncounterType.INPATIENT);
    // The copay for a non-wellness encounter should be greater than 0.
    assertTrue(testPrivatePayer1Plan.determineCopay(inpatientBeforeMandate.type,
        inpatientBeforeMandate.start).compareTo(BigDecimal.ZERO) == 1);

    // After Mandate.
    Encounter wellnessAfterMandate
        = person.record.encounterStart(afterMandateTime, EncounterType.WELLNESS);
    wellnessAfterMandate.codes.add(code);
    wellnessAfterMandate.provider = new Provider();
    person.record.encounterEnd(afterMandateTime, EncounterType.WELLNESS);
    // The copay after the mandate time should be 0.
    assertTrue(BigDecimal.ZERO.compareTo(testPrivatePayer1Plan
        .determineCopay(wellnessAfterMandate.type, wellnessAfterMandate.start)) == 0);

    Encounter inpatientAfterMandate
        = person.record.encounterStart(afterMandateTime, EncounterType.INPATIENT);
    inpatientAfterMandate.codes.add(code);
    inpatientAfterMandate.provider = new Provider();
    person.record.encounterEnd(afterMandateTime, EncounterType.INPATIENT);
    // The copay for a non-wellness encounter should be greater than 0.
    assertTrue(testPrivatePayer1Plan.determineCopay(
        inpatientAfterMandate.type, inpatientAfterMandate.start).compareTo(BigDecimal.ZERO) == 1);
  }

  @Test
  public void costsUncoveredByNoInsurance() {
    Costs.loadCostData();
    PayerManager.loadNoInsurance();
    long time = 0L;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    healthInsModule.process(person, time);
    person.coverage.setPlanToNoInsurance(time);
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(time, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    person.record.encounterEnd(time, EncounterType.WELLNESS);
    // The No Insurance payer should have $0.0 coverage.
    assertEquals(Claim.ZERO_CENTS, PayerManager.getNoInsurancePlan().getPayer().getAmountCovered());
    // The No Insurance's uncovered costs should equal the total cost.
    assertTrue(fakeEncounter.getCost()
        .equals(PayerManager.getNoInsurancePlan().getPayer().getAmountUncovered()));
    // The person's out of pocket expenses shoudl equal the total cost.
    assertTrue(fakeEncounter.getCost().equals(person.coverage.getTotalOutOfPocketExpenses()));
  }

  @Test
  public void determineCoveredCostWithNullPayer() {
    // Default to No Insurance
    long time = 0L;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    healthInsModule.process(person, time);
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    healthRecord.encounterEnd(time, EncounterType.INPATIENT);
  }

  @Test
  public void payerCoversEncounter() {
    long time = 0L;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    healthInsModule.process(person, time);
    person.coverage.setPlanAtTime(time, testPrivatePayer1.getPlans().iterator().next(),
        PayerManager.getNoInsurancePlan());
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    encounter.provider = new Provider();
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertTrue(testPrivatePayer1Plan.coversService(encounter));
    healthRecord.encounterEnd(time, EncounterType.INPATIENT);
    // Person's coverage should equal the cost of the encounter
    BigDecimal coverage = encounter.claim.totals.coinsurancePaidByPayer.add(
        encounter.claim.totals.paidByPayer);
    assertEquals(person.coverage.getTotalCoverage(), coverage);
    BigDecimal resultCost = encounter.claim.totals.paidByPayer
        .add(encounter.claim.totals.patientOutOfPocket)
        .add(encounter.claim.totals.coinsurancePaidByPayer)
        .add(encounter.claim.totals.copayPaidByPatient)
        .add(encounter.claim.totals.deductiblePaidByPatient);
    assertTrue("Test Failed: Encounter cost was " + encounter.getCost() + " and the result was "
        + resultCost + ". Expected equality.", encounter.getCost().compareTo(resultCost) == 0);
    // Person's expenses should equal the copay.
    BigDecimal expenses = encounter.claim.totals.patientOutOfPocket
        .add(encounter.claim.totals.deductiblePaidByPatient)
        .add(encounter.claim.totals.copayPaidByPatient);
    assertEquals(person.coverage.getTotalOutOfPocketExpenses(), expenses);
    assertEquals(encounter.claim.totals.copayPaidByPatient, expenses);
  }

  @Test
  public void payerDoesNotCoverEncounter() {
    long time = 0L;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 0);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    healthInsModule.process(person, time);
    person.coverage.setPlanAtTime(0L, testPrivatePayer2.getPlans().iterator().next(),
        PayerManager.getNoInsurancePlan());
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    encounter.provider = new Provider();
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    InsurancePlan testPrivatePayer2Plan = testPrivatePayer2.getPlans().iterator().next();
    assertFalse(testPrivatePayer2Plan.coversService(encounter));
    healthRecord.encounterEnd(time, EncounterType.INPATIENT);
    // Person's coverage should equal $0.0.
    assertTrue(person.coverage.getTotalCoverage().equals(Claim.ZERO_CENTS));
    // Person's expenses should equal the total cost of the encounter.
    assertEquals(person.coverage.getTotalOutOfPocketExpenses(), encounter.getCost());
  }

  @Test
  public void personCanAffordPayer() {
    String willingToSpend = Config.get("generate.payers.insurance_plans.income_premium_ratio");
    // Set their willinginess to spend to 100%.
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "1.0");
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    BigDecimal yearlyCostOfPayer = plan.getMonthlyPremium(0).multiply(BigDecimal.valueOf(12)).add(
        plan.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer.add(BigDecimal.ONE).intValue());
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertTrue(person.canAffordPlan(testPrivatePayer1Plan));
    // Reset the person's willingness to spend.
    Config.set("generate.payers.insurance_plans.income_premium_ratio", willingToSpend);
  }

  @Test
  public void personCannotAffordPayer() {
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    InsurancePlan plan = testPrivatePayer1.getPlans().iterator().next();
    BigDecimal yearlyCostOfPayer = plan.getMonthlyPremium(0).multiply(BigDecimal.valueOf(12)).add(
        plan.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer.subtract(BigDecimal.ONE).intValue());
    InsurancePlan testPrivatePayer1Plan = testPrivatePayer1.getPlans().iterator().next();
    assertFalse(person.canAffordPlan(testPrivatePayer1Plan));
  }

  @Test
  public void keepPreviousInsurance() {
    long time = Utilities.convertCalendarYearsToTime(1960);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, (int) minPrivateAffordability);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");
    healthInsModule.process(person, time);
    Payer inititalPayer = person.coverage.getPlanAtTime(time).getPayer();
    assertEquals("PRIVATE", inititalPayer.getOwnership());
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(time);
    c.add(Calendar.YEAR, 1);
    time = c.getTimeInMillis();
    healthInsModule.process(person, time);
    assertEquals("Person should keep the same insurance.",
        inititalPayer, person.coverage.getPlanAtTime(time).getPayer());
  }


  @Test
  public void payerMemberYears() {
    int startYear = 1950;
    long currentTime = Utilities.convertCalendarYearsToTime(startYear);
    Person person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.BIRTHDATE, currentTime);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.attributes.put(Person.INCOME, (int) HealthInsuranceModule.povertyLevel * 100);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);

    // Get private insurance for 55 years.
    int numberOfYears = 55;
    Calendar c = Calendar.getInstance();
    for (int age = 0; age < numberOfYears; age++) {
      healthInsModule.process(person, currentTime);
      c.setTimeInMillis(currentTime);
      c.add(Calendar.YEAR, 1);
      currentTime = c.getTimeInMillis();
    }
    int totalYearsCovered = testPrivatePayer1.getNumYearsCovered()
        + testPrivatePayer2.getNumYearsCovered();
    assertEquals(numberOfYears, totalYearsCovered);
  }

  @Test
  public void checkCopayPaid() {
    long time = Utilities.convertCalendarYearsToTime(1960);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");

    person.coverage.setPlanAtTime(time, testPrivatePayer1.getPlans().iterator().next(),
        PayerManager.getNoInsurancePlan());
    healthInsModule.process(person, time);
    InsurancePlan plan = person.coverage.getPlanAtTime(time);
    assertFalse("Person should have insurance.", plan.getPayer().isNoInsurance());
    assertFalse("Person should have private insurance.", plan.getPayer().isGovernmentPayer());

    HealthRecord healthRecord = new HealthRecord(person);
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    Encounter fakeEncounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.INPATIENT);

    BigDecimal patientCoinsurance = plan.getPatientCoinsurance();
    BigDecimal copay = plan.determineCopay(fakeEncounter.type, fakeEncounter.start);
    assertTrue(plan.isCopayBased());
    BigDecimal encounterCost = fakeEncounter.getCost();
    BigDecimal expectedPaid = patientCoinsurance.multiply(encounterCost).add(copay);
    BigDecimal coinsurancePaid = fakeEncounter.claim.getTotalCoinsurancePaid();
    BigDecimal copayPaid = fakeEncounter.claim.getTotalCopayPaid();

    assertEquals("The amount paid should be equal to the plan's coinsurance rate plus the copay."
        + " The payer is " + plan.getPayer().getName() + ".",
        expectedPaid, coinsurancePaid.add(copayPaid));
  }

  @Test
  public void checkCoinsurancePaid() {
    long time = Utilities.convertCalendarYearsToTime(1960);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");

    person.coverage.setPlanAtTime(time, testPrivatePayer2.getPlans().iterator().next(),
        PayerManager.getNoInsurancePlan());
    healthInsModule.process(person, time);
    InsurancePlan plan = person.coverage.getPlanAtTime(time);
    assertFalse("Person should have insurance.", plan.getPayer().isNoInsurance());
    assertFalse("Person should have private insurance.", plan.getPayer().isGovernmentPayer());

    HealthRecord healthRecord = new HealthRecord(person);
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    Encounter fakeEncounter = healthRecord.encounterStart(time, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.WELLNESS);

    BigDecimal patientCoinsurance = plan.getPatientCoinsurance();
    BigDecimal copay = plan.determineCopay(fakeEncounter.type, fakeEncounter.start);
    assertFalse(plan.isCopayBased());
    BigDecimal encounterCost = fakeEncounter.getCost();
    BigDecimal expectedPaid = patientCoinsurance.multiply(encounterCost).add(copay);
    BigDecimal coinsurancePaid = fakeEncounter.claim.getTotalCoinsurancePaid();
    BigDecimal copayPaid = fakeEncounter.claim.getTotalCopayPaid();

    assertEquals(BigDecimal.ONE.subtract(plan.getPatientCoinsurance()), plan.getPayerCoinsurance());
    assertTrue("The amount paid should be equal to the plan's coinsurance rate plus the copay."
        + " The payer is " + plan.getPayer().getName() + ". Expected " + expectedPaid + " but was "
        + coinsurancePaid.add(copayPaid) + ".",
        expectedPaid.compareTo(coinsurancePaid.add(copayPaid)) == 0);
  }

  @Test
  public void personKeepsPreviousInsurance() {
    long time = Utilities.convertCalendarYearsToTime(1980);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, (int) HealthInsuranceModule.povertyLevel * 100);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    healthInsModule.process(person, time);
    InsurancePlan firstPlan = person.coverage.getPlanAtTime(time);
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(time);
    c.add(Calendar.YEAR, 1);
    time = c.getTimeInMillis() + 1;
    healthInsModule.process(person, time);
    InsurancePlan secondPlan = person.coverage.getPlanAtTime(time);
    // For now, this returns true by default because it is not yet implememted.
    assertEquals(firstPlan, secondPlan);
  }

  @Test
  public void incomeBasedPremium() {
    long time = 0L;
    int income = 100000;
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    person.attributes.put(Person.INCOME, income);
    BigDecimal expectedMonthlyPremium = new BigDecimal(income).setScale(2)
        .divide(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(12), RoundingMode.HALF_UP);
    InsurancePlan incomeBasedPlan = PayerManager.getAllPayers().stream().filter(payer ->
        payer.getName().equals("Test Private Payer 3")).findFirst().get().getPlans()
        .stream().filter(plan -> plan.id == 60001).iterator().next();
    assertEquals(expectedMonthlyPremium, incomeBasedPlan.getMonthlyPremium(income));
    person.coverage.setPlanAtTime(time, incomeBasedPlan, PayerManager.getNoInsurancePlan());
    person.coverage.payMonthlyPremiumsAtTime(time, (double) person.attributes
        .get(Person.OCCUPATION_LEVEL), (int) person.attributes.get(Person.INCOME));
    assertEquals(expectedMonthlyPremium, person.coverage.getTotalPremiumExpenses());
  }

  @Test
  public void willingnessToPay() {
    int income = 100000;
    Person person = new Person(0L);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    person.attributes.put(Person.INCOME, income);
    InsurancePlan premiumRatioTestPlan = PayerManager.getAllPayers().stream().filter(payer ->
        payer.getName().equals("Test Private Payer 3")).findFirst().get().getPlans()
        .stream().filter(plan -> plan.id == 60002).iterator().next();
    BigDecimal yearlyCost = premiumRatioTestPlan.getMonthlyPremium(0)
        .multiply(BigDecimal.valueOf(12));
    assertEquals(BigDecimal.valueOf(12000), yearlyCost);
    // The income_premium_ratio flag dictates what percent of a person's yearly income they are
    // willing to spend on insurance. In this case, 12000 is 0.12 of 100000.
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "0.11");
    assertFalse(IPlanFinder
        .meetsAffordabilityRequirements(premiumRatioTestPlan, person, null, 0L));
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "0.12");
    assertTrue(IPlanFinder
        .meetsAffordabilityRequirements(premiumRatioTestPlan, person, null, 0L));
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "0.13");
    assertTrue(IPlanFinder
        .meetsAffordabilityRequirements(premiumRatioTestPlan, person, null, 0L));
  }

  @Test
  public void employerPremiumCoverage() {
    Person employerCovered = new Person(0L);
    employerCovered.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    employerCovered.attributes.put(Person.BIRTHDATE, 0L);
    employerCovered.attributes.put(Person.INCOME, 10000);
    Person nonEmployerCovered = new Person(0L);
    nonEmployerCovered.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    nonEmployerCovered.attributes.put(Person.BIRTHDATE, 0L);
    nonEmployerCovered.attributes.put(Person.INCOME, 10000);
    InsurancePlan plan = PayerManager.getAllPayers().stream().filter(payer -> payer.getName()
        .equals("Test Private Payer 3")).findFirst().get().getPlans()
        .stream().filter(tempPlan -> tempPlan.id == 60002).iterator().next();
    employerCovered.coverage.setPlanAtTime(0L, plan, PayerManager.getNoInsurancePlan());
    nonEmployerCovered.coverage.setPlanAtTime(0L, plan, PayerManager.getNoInsurancePlan());
    // Employers will cover some % of monthly premiums, 75% in this case.
    Config.set("generate.insurance.employer_coverage", "0.75");
    BigDecimal costToCoveredEmployee
        = plan.getMonthlyPremium(0).multiply(BigDecimal.valueOf(0.25));
    employerCovered.checkToPayMonthlyPremium(0L);
    assertEquals(costToCoveredEmployee, employerCovered.coverage.getTotalPremiumExpenses());
    // Patients without a covering occupation level should not get any premium discounts.
    nonEmployerCovered.checkToPayMonthlyPremium(0L);
    assertEquals(plan.getMonthlyPremium(0).setScale(2),
        nonEmployerCovered.coverage.getTotalPremiumExpenses());
  }

  @Test
  public void maxOutOfPocketTest() {
    InsurancePlan plan = PayerManager.getAllPayers().stream().filter(payer -> payer.getName()
        .equals("Test Private Payer 3")).findFirst().get().getPlans()
        .stream().filter(tempPlan -> tempPlan.id == 60002).iterator().next();
    assertEquals(BigDecimal.valueOf(200), plan.getMaxOop());
    assertEquals(BigDecimal.valueOf(0.1), plan.getPayerCoinsurance());

    long time = Utilities.convertCalendarYearsToTime(1960);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.GENDER, "F");
    person.coverage.setPlanAtTime(time, plan, PayerManager.getNoInsurancePlan());

    HealthRecord healthRecord = new HealthRecord(person);
    Code code = new Code("SNOMED-CT","705129","Fake Code");
    Encounter fakeEncounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.INPATIENT);

    BigDecimal expectedPaid = fakeEncounter.getCost()
        .multiply(BigDecimal.valueOf(0.9)).setScale(2);
    BigDecimal coinsurancePaid = fakeEncounter.claim.getTotalCoinsurancePaid();
    assertEquals(expectedPaid, coinsurancePaid);
    BigDecimal totalPaid = BigDecimal.ZERO.add(expectedPaid);
    assertEquals(totalPaid, person.coverage.getTotalOutOfPocketExpenses());
    assertEquals(BigDecimal.valueOf(112.50).setScale(2),
        person.coverage.getTotalOutOfPocketExpenses());
    assertEquals(BigDecimal.valueOf(12.50).setScale(2), person.coverage.getTotalCoverage());

    // The person has now accrued $112.50 in expenses. Even though their Max OOP is $200, their
    // next $112.50 expense will increase OOP to expenses $225. This should be fixed in future.
    fakeEncounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.INPATIENT);

    expectedPaid = fakeEncounter.getCost().multiply(BigDecimal.valueOf(0.9)).setScale(2);
    coinsurancePaid = fakeEncounter.claim.getTotalCoinsurancePaid();
    assertEquals(expectedPaid, coinsurancePaid);
    totalPaid = totalPaid.add(expectedPaid);
    assertEquals(totalPaid, person.coverage.getTotalOutOfPocketExpenses());
    assertEquals(BigDecimal.valueOf(225).setScale(2),
        person.coverage.getTotalOutOfPocketExpenses());
    assertEquals(BigDecimal.valueOf(25).setScale(2), person.coverage.getTotalCoverage());

    // The person's OOP costs ($225) exceed the max ($200). They should no longer have OOP costs.
    fakeEncounter = healthRecord.encounterStart(time, EncounterType.INPATIENT);
    fakeEncounter.codes.add(code);
    healthRecord.encounterEnd(time + 1, EncounterType.INPATIENT);

    BigDecimal coinsurancePaidAfterMaxOop = fakeEncounter.claim.getTotalCoinsurancePaid();
    assertEquals(BigDecimal.ZERO.setScale(2), coinsurancePaidAfterMaxOop);
    assertEquals(BigDecimal.valueOf(225).setScale(2),
        person.coverage.getTotalOutOfPocketExpenses());
    assertEquals(BigDecimal.valueOf(150).setScale(2), person.coverage.getTotalCoverage());
  }

  @Test
  public void qualifyingAttributesFileEligibility() {
    String fileName = "generic/payers/test_attributes_eligibility.csv";
    QualifyingAttributesEligibility qae = new QualifyingAttributesEligibility(fileName);
    long time = Utilities.convertCalendarYearsToTime(1975);
    Person person = new Person(0L);
    person.attributes.put("test2", "TEST");
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test1", true);
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test1", false);
    assertFalse(qae.isPersonEligible(person, time));
    person.attributes.put("test2", "NOT TEST");
    assertTrue(qae.isPersonEligible(person, time));
    person.attributes.put("test2", "TEST");
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