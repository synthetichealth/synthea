package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
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
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
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
    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    // Load in the .csv list of Payers.
    Payer.loadPayers(new Location(testState, null));
    // Load the two test payers.
    testPrivatePayer1 = Payer.getPrivatePayers().get(0);
    testPrivatePayer2 = Payer.getPrivatePayers().get(1);
    // Force medicare for test settings
    Config.set("generate.payers.insurance_companies.medicare", "Medicare");
    Config.set("generate.payers.insurance_companies.medicaid", "Medicaid");
    Config.set("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");
    HealthInsuranceModule.MEDICARE = "Medicare";
    HealthInsuranceModule.MEDICAID = "Medicaid";
    HealthInsuranceModule.DUAL_ELIGIBLE = "Dual Eligible";
  }

  /**
   * Clean up after tests.
   */
  @AfterClass
  public static void cleanup() {
    Config.set("generate.payers.insurance_companies.medicare", medicareName);
    Config.set("generate.payers.insurance_companies.medicaid", medicaidName);
    Config.set("generate.payers.insurance_companies.dual_eligible", dualName);
    HealthInsuranceModule.MEDICARE = medicareName;
    HealthInsuranceModule.MEDICAID = medicaidName;
    HealthInsuranceModule.DUAL_ELIGIBLE = dualName;
  }

  @Test
  public void incrementCustomers() {
    Person firstPerson = new Person(0L);
    firstPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Payer has firstPerson customer from the ages of 0 - 11.
    setPayerForYears(firstPerson, 0, 11);

    Person secondPerson = new Person(0L);
    secondPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
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
      person.coverage.setPayerAtTime(time, testPrivatePayer1);
      testPrivatePayer1.incrementCustomers(person);
    }
  }

  @Test
  public void incrementEncounters() {
    person = new Person(0L);
    person.coverage.setPayerAtTime(0L, testPrivatePayer1);
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
  public void receiveMedicare() {
    long birthTime = 0L;
    long olderThanSixtyFiveTime = birthTime + Utilities.convertTime("years", 66);

    /* Older than 65 */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put("end_stage_renal_disease", false);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    // QOLS cannot be null for the checked years.
    Map<Integer, Double> qolsByYear = new HashMap<Integer, Double>();
    qolsByYear.put(Utilities.getYear(olderThanSixtyFiveTime) - 1, 1.0);
    person.attributes.put(QualityOfLifeModule.QOLS, qolsByYear);
    // Their previous payer must not be null to prevent nullPointerExceptions.
    person.coverage.setPayerAtTime(olderThanSixtyFiveTime
        - Utilities.convertTime("years", 1), testPrivatePayer1);
    // At time olderThanSixtyFiveTime, the person is 65 and qualifies for Medicare.
    healthInsuranceModule.process(person, olderThanSixtyFiveTime);
    assertEquals("Medicare", person.coverage.getPayerAtTime(olderThanSixtyFiveTime).getName());
    assertTrue(person.coverage.getPayerAtTime(olderThanSixtyFiveTime)
        .accepts(person, olderThanSixtyFiveTime));

    /* ESRD */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put("end_stage_renal_disease", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicare", person.coverage.getPayerAtTime(0L).getName());
  }

  @Test
  public void receiveMedicaid() {
    /* Pregnancy */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put("pregnant", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.coverage.getPayerAtTime(0L).getName());
    assertTrue(person.coverage.getPayerAtTime(0L).accepts(person, 0L));

    /* Poverty Level */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(Person.BLINDNESS, false);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.coverage.getPayerAtTime(0L).getName());

    /* Blindness */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.BLINDNESS, true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.coverage.getPayerAtTime(0L).getName());
  }

  @Test
  public void receiveDualEligible() {
    long birthTime = 0L;
    long olderThanSixtyFiveTime = birthTime + Utilities.convertTime("years", 66);

    /* Below Poverty Level and Over 65 */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, birthTime);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    // QOLS cannot be null for the checked years.
    Map<Integer, Double> qolsByYear = new HashMap<Integer, Double>();
    qolsByYear.put(Utilities.getYear(olderThanSixtyFiveTime) - 1, 1.0);
    person.attributes.put(QualityOfLifeModule.QOLS, qolsByYear);
    // Their previous payer must not be null to prevent nullPointerExceptions.
    person.coverage.setPayerAtTime(olderThanSixtyFiveTime
        - Utilities.convertTime("years", 1), testPrivatePayer1);
    // At time olderThanSixtyFiveTime, the person is 65 and qualifies for Medicare.
    healthInsuranceModule.process(person, olderThanSixtyFiveTime);
    assertEquals("Dual Eligible",
        person.coverage.getPayerAtTime(olderThanSixtyFiveTime).getName());
    assertTrue(person.coverage.getPayerAtTime(olderThanSixtyFiveTime)
        .accepts(person, olderThanSixtyFiveTime));
  }

  @Test
  public void receiveNoInsurance() {
    /* Person is poorer than can afford the test insurance */
    BigDecimal monthlyPremium = testPrivatePayer1.getMonthlyPremium();
    BigDecimal deductible = testPrivatePayer1.getDeductible();
    BigDecimal totalYearlyCost = monthlyPremium.multiply(BigDecimal.valueOf(12)).add(deductible);

    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.001);
    // Give the person an income lower than the totalYearlyCost.
    BigDecimal income = totalYearlyCost.subtract(BigDecimal.ONE);
    person.attributes.put(Person.INCOME, income.intValue());
    // Set the medicaid poverty level to be lower than their income
    Config.set("generate.demographics.socioeconomic.income.poverty",
        Integer.toString(income.subtract(BigDecimal.ONE).intValue()));
    HealthInsuranceModule.medicaidLevel = Config.getAsDouble(
            "generate.demographics.socioeconomic.income.poverty", 11000);

    healthInsuranceModule.process(person, 0L);
    assertEquals("NO_INSURANCE", person.coverage.getPayerAtTime(0L).getName());
  }

  @Test
  public void receivePrivateInsurance() {
    /* First Test: Post 2006 Mandate */
    long time = mandateTime + 10000;
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Barely above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel + 100);
    healthInsuranceModule.process(person, time);
    assertNotEquals("NO_INSURANCE", person.coverage.getPayerAtTime(time).getName());

    /* Second Test: Wealthy Enough to Purchase Private */
    time = mandateTime - 10000;
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, time);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, time);
    assertNotEquals("NO_INSURANCE", person.coverage.getPayerAtTime(time).getName());
  }

  @Test
  public void overwriteInsurance() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPayerAtTime(0L, testPrivatePayer1);
    person.coverage.setPayerAtTime(0L, testPrivatePayer1);
    assertEquals(2, person.coverage.getPlanHistory().size());
  }

  @Test
  public void loadGovernmentPayers() {
    assertTrue(Payer.getGovernmentPayer("Medicare")
        != null && Payer.getGovernmentPayer("Medicaid") != null);

    for (Payer payer : Payer.getGovernmentPayers()) {
      assertEquals("Government", payer.getOwnership());
    }
  }

  @Test
  public void invalidGovernmentPayer() {
    assertNull(Payer.getGovernmentPayer("Hollywood Healthcare"));
  }

  @Test
  public void loadAllPayers() {
    int numGovernmentPayers = Payer.getGovernmentPayers().size();
    int numPrivatePayers = Payer.getPrivatePayers().size();
    assertEquals(numGovernmentPayers + numPrivatePayers, Payer.getAllPayers().size());
  }

  @Test(expected = RuntimeException.class)
  public void nullPayerName() {
    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/bad_test_payers.csv");
    Payer.loadPayers(new Location("Massachusetts", null));
    Payer.clear();
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
    BigDecimal totalMonthlyPremiumsOwed = testPrivatePayer1.getMonthlyPremium()
            .multiply(BigDecimal.valueOf(12 * 65));
    // The payer's revenue should equal the total monthly premiums.
    assertTrue(totalMonthlyPremiumsOwed.equals(testPrivatePayer1.getRevenue()));
    // The person's health care expenses should equal the total monthly premiums.
    assertTrue(totalMonthlyPremiumsOwed.equals(person.coverage.getTotalExpenses()));
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
    person = new Person(time);
    person.coverage.setPayerAtTime(time, testPrivatePayer1);
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(time, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // check the copays match
    assertTrue(testPrivatePayer1.getDeductible().equals(fakeEncounter.claim.totals.deductible));
    // check that totals match
    assertTrue(fakeEncounter.getCost().equals(fakeEncounter.claim.totals.cost));
    BigDecimal result = fakeEncounter.claim.totals.coinsurance
        .add(fakeEncounter.claim.totals.copay)
        .add(fakeEncounter.claim.totals.deductible)
        .add(fakeEncounter.claim.totals.payer)
        .add(fakeEncounter.claim.totals.pocket);
    assertTrue(fakeEncounter.getCost().equals(result));
    // The total cost should equal the Cost to the Payer summed with the Payer's copay amount.
    assertTrue(fakeEncounter.getCost().equals(testPrivatePayer1.getAmountCovered()
        .add(fakeEncounter.claim.getPatientCost())));
    // The total cost should equal the Payer's uncovered costs plus the Payer's covered costs.
    assertTrue(fakeEncounter.getCost().equals(testPrivatePayer1.getAmountCovered()
        .add(testPrivatePayer1.getAmountUncovered())));
    // The total coverage by the payer should equal the person's covered costs.
    assertTrue(person.coverage.getTotalCoverage().equals(testPrivatePayer1.getAmountCovered()));
  }

  @Test
  public void copayBeforeAndAfterMandate() {
    Costs.loadCostData();
    final long beforeMandateTime = mandateTime - 100;
    final long afterMandateTime = mandateTime + 100;
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    person = new Person(beforeMandateTime);

    /* Before Mandate */

    person.coverage.setPayerAtTime(beforeMandateTime, testPrivatePayer1);
    Encounter wellnessBeforeMandate =
        person.record.encounterStart(beforeMandateTime, EncounterType.WELLNESS);
    wellnessBeforeMandate.codes.add(code);
    wellnessBeforeMandate.provider = new Provider();
    person.record.encounterEnd(beforeMandateTime, EncounterType.WELLNESS);
    // The copay before the mandate time should be greater than 0.
    assertTrue(testPrivatePayer1.determineCopay(wellnessBeforeMandate)
            .compareTo(Claim.ZERO_CENTS) > 0);

    Encounter inpatientBeforeMandate
        = person.record.encounterStart(beforeMandateTime, EncounterType.INPATIENT);
    inpatientBeforeMandate.codes.add(code);
    inpatientBeforeMandate.provider = new Provider();
    person.record.encounterEnd(beforeMandateTime, EncounterType.INPATIENT);
    // The copay for a non-wellness encounter should be greater than 0.
    assertTrue(testPrivatePayer1.determineCopay(wellnessBeforeMandate)
            .compareTo(Claim.ZERO_CENTS) > 0.0);

    /* After Mandate */

    Encounter wellnessAfterMandate
        = person.record.encounterStart(afterMandateTime, EncounterType.WELLNESS);
    wellnessAfterMandate.codes.add(code);
    wellnessAfterMandate.provider = new Provider();
    person.record.encounterEnd(afterMandateTime, EncounterType.WELLNESS);
    // The copay after the mandate time should be 0.
    assertTrue(testPrivatePayer1.determineCopay(wellnessAfterMandate).equals(Claim.ZERO_CENTS));

    Encounter inpatientAfterMandate
        = person.record.encounterStart(afterMandateTime, EncounterType.INPATIENT);
    inpatientAfterMandate.codes.add(code);
    inpatientAfterMandate.provider = new Provider();
    person.record.encounterEnd(afterMandateTime, EncounterType.INPATIENT);
    // The copay for a non-wellness encounter should be greater than 0.
    assertTrue(testPrivatePayer1.determineCopay(inpatientAfterMandate)
            .compareTo(Claim.ZERO_CENTS) > 0);
  }

  @Test
  public void costsUncoveredByNoInsurance() {
    Costs.loadCostData();
    Payer.loadNoInsurance();
    person = new Person(0L);
    person.coverage.setPayerAtTime(0L, Payer.noInsurance);
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Encounter fakeEncounter = person.record.encounterStart(0L, EncounterType.WELLNESS);
    fakeEncounter.codes.add(code);
    fakeEncounter.provider = new Provider();
    person.record.encounterEnd(0L, EncounterType.WELLNESS);
    // The No Insurance payer should have $0.0 coverage.
    assertTrue(Payer.noInsurance.getAmountCovered().equals(Claim.ZERO_CENTS));
    // The No Insurance's uncovered costs should equal the total cost.
    assertTrue(fakeEncounter.getCost().equals(Payer.noInsurance.getAmountUncovered()));
    // The person's expenses shoudl equal the total cost.
    assertTrue(fakeEncounter.getCost().equals(person.coverage.getTotalExpenses()));
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
    person.coverage.setPayerAtTime(0L, testPrivatePayer1);
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    encounter.provider = new Provider();
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    assertTrue(testPrivatePayer1.coversService(encounter.type));
    healthRecord.encounterEnd(0L, EncounterType.INPATIENT);
    // Person's coverage should equal the cost of the encounter
    BigDecimal coverage = encounter.claim.totals.coinsurance.add(encounter.claim.totals.payer);
    assertTrue(person.coverage.getTotalCoverage().equals(coverage));
    BigDecimal result = encounter.claim.totals.coinsurance
        .add(encounter.claim.totals.copay)
        .add(encounter.claim.totals.deductible)
        .add(encounter.claim.totals.payer)
        .add(encounter.claim.totals.pocket);
    assertTrue(encounter.getCost().equals(result));
    // Person's expenses should equal the copay.
    BigDecimal expenses = encounter.claim.totals.copay
        .add(encounter.claim.totals.deductible)
        .add(encounter.claim.totals.pocket);
    assertTrue(person.coverage.getTotalExpenses().equals(expenses));
  }

  @Test
  public void payerDoesNotCoverEncounter() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPayerAtTime(0L, testPrivatePayer2);
    HealthRecord healthRecord = new HealthRecord(person);
    Encounter encounter = healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    encounter.provider = new Provider();
    encounter.codes.add(new Code("SNOMED-CT","705129","Fake SNOMED for null entry"));
    assertFalse(testPrivatePayer2.coversService(encounter.type));
    healthRecord.encounterEnd(0L, EncounterType.INPATIENT);
    // Person's coverage should equal $0.0.
    assertTrue(person.coverage.getTotalCoverage().equals(Claim.ZERO_CENTS));
    // Person's expenses should equal the total cost of the encounter.
    assertTrue(person.coverage.getTotalExpenses().equals(encounter.getCost()));
  }

  @Test
  public void personCanAffordPayer() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    BigDecimal yearlyCostOfPayer = testPrivatePayer1.getMonthlyPremium()
            .multiply(BigDecimal.valueOf(12))
            .add(testPrivatePayer1.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer.intValue() + 1);
    assertTrue(person.canAffordPayer(testPrivatePayer1));
  }

  @Test
  public void personCannotAffordPayer() {
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    BigDecimal yearlyCostOfPayer = testPrivatePayer1.getMonthlyPremium()
            .multiply(BigDecimal.valueOf(12))
            .add(testPrivatePayer1.getDeductible());
    person.attributes.put(Person.INCOME, yearlyCostOfPayer.intValue() - 1);
    assertFalse(person.canAffordPayer(testPrivatePayer1));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void payerMemberYears() {
    long startTime = Utilities.convertCalendarYearsToTime(2000);
    person = new Person(0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.BIRTHDATE, startTime);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    person.attributes.put(Person.INCOME, (int) HealthInsuranceModule.medicaidLevel * 100);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    person.attributes.put(QualityOfLifeModule.QOLS, new HashMap<Integer, Double>());

    // Get private insurance for 55 years.
    Map<Integer, Double> qols =
        (Map<Integer, Double>) person.attributes.get(QualityOfLifeModule.QOLS);
    for (int year = 0; year < 55; year++) {
      qols.put(2000 + year, 1.0);
      long currentTime = startTime + Utilities.convertTime("years", year);
      healthInsuranceModule.process(person, currentTime);
    }
    int totalYearsCovered = testPrivatePayer1.getNumYearsCovered()
        + testPrivatePayer2.getNumYearsCovered();
    assertEquals(55, totalYearsCovered);
  }

  @Test
  public void payerInProviderNetwork() {
    // For now, this returns true by default because it is not yet implememted.
    assertTrue(testPrivatePayer1.isInNetwork(null));
  }
}