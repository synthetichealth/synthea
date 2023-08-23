package org.mitre.synthea.world.agents.behaviors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planfinder.IPlanFinder;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderPriority;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderRandom;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

public class PlanFinderTest {

  private Person person;
  private Location location;

  /**
   * Setup for Plan Finder Tests.
   * @throws Exception on configuration loading errors
   */
  @BeforeClass
  public static void init() {
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "generic/payers/test_plans.csv");
    Config.set("generate.payers.insurance_plans.eligibilities_file",
        "generic/payers/test_insurance_eligibilities.csv");
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "1.0");
  }

  /**
   * Setup before each test.
   */
  @Before
  public void setup() throws Exception {
    person = new Person(0L);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.5);
    person.attributes.put(Person.INCOME, 100000);
    person.attributes.put(Person.BIRTHDATE, 0L);
    TestHelper.loadTestProperties();
    person.attributes.put(Person.STATE, Config.get("test_state.default", "Massachusetts"));
    location = new Location((String) person.attributes.get(Person.STATE), null);
  }

  /**
   * Cleanup after all tests complete.
   */
  @AfterClass
  public static void cleanup() throws Exception {
    Config.set("generate.payers.insurance_companies.default_file",
        "payers/insurance_companies.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "payers/insurance_plans.csv");
    Config.set("generate.payers.insurance_plans.eligibilities_file",
        "payers/insurance_eligibilities.csv");
    Config.set("generate.payers.insurance_plans.income_premium_ratio", "0.034");
  }

  @Test
  public void noPayersRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    PayerManager.clear();
    PayerManager.loadPayers(location);
    PlanFinderRandom finder = new PlanFinderRandom();
    List<InsurancePlan> options = new ArrayList<InsurancePlan>();
    Payer payer = finder.find(options, person, null, 0L).getPayer();
    assertNotNull(payer);
    assertTrue(payer.isNoInsurance());
  }

  @Test
  public void onePayerRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    PayerManager.clear();
    PayerManager.loadPayers(location);
    PlanFinderRandom finder = new PlanFinderRandom();
    List<Payer> privatePayers = PayerManager.getAllPayers().stream().filter(payer -> payer
        .getOwnership().equals(PayerManager.PRIVATE_OWNERSHIP)).collect(Collectors.toList());
    Payer payer = finder.find(PayerManager.getActivePlans(privatePayers, 0L),
        person, null, 0L).getPayer();
    assertNotNull(payer);
    assertFalse(payer.isNoInsurance());
  }

  @Test
  public void noPayersBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    PayerManager.clear();
    PayerManager.loadPayers(location);
    PlanFinderBestRates finder = new PlanFinderBestRates();
    List<InsurancePlan> options = new ArrayList<InsurancePlan>();
    Payer payer = finder.find(options, person, null, 0L).getPayer();
    assertNotNull(payer);
    assertTrue(payer.isNoInsurance());
  }

  @Test
  public void onePayerBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    PayerManager.clear();
    PayerManager.loadPayers(location);
    PlanFinderBestRates finder = new PlanFinderBestRates();
    List<Payer> privatePayers = PayerManager.getAllPayers().stream().filter(payer -> payer
        .getOwnership().equals(PayerManager.PRIVATE_OWNERSHIP)).collect(Collectors.toList());
    Payer payer = finder.find(PayerManager.getActivePlans(privatePayers, 0L),
        person, null, 0L).getPayer();
    assertNotNull(payer);
    assertFalse(payer.isNoInsurance());
  }

  @Test
  public void planFinderPriority() {
    long time = Utilities.convertCalendarYearsToTime(2023);
    long birthTime = Utilities.convertCalendarYearsToTime(2000);
    Config.set("generate.payers.selection_behavior", "priority");
    PayerManager.clear();
    PayerManager.loadPayers(location);
    List<InsurancePlan> plans = PayerManager.getActivePlans(PayerManager.getAllPayers(), time);
    IPlanFinder finder = new PlanFinderPriority();
    Person priorityPerson = new Person(0L);
    priorityPerson.attributes.put(Person.OCCUPATION_LEVEL, 0.0);
    priorityPerson.attributes.put(Person.BIRTHDATE, birthTime);
    priorityPerson.attributes.put(Person.INCOME,100000);
    priorityPerson.attributes.put(Person.GENDER,"M");
    priorityPerson.coverage.setPlanToNoInsurance(birthTime);
    for (int i = 1; i <= 23; i++) {
      person.coverage.newEnrollmentPeriod(birthTime + Utilities.convertTime("years", i));
    }
    priorityPerson.coverage.setPlanToNoInsurance(time);
    // Private insurance has the lowest priority levels.
    InsurancePlan plan = finder.find(plans, priorityPerson, null, time);
    assertTrue(plan.getPayer().getOwnership().equals(PayerManager.PRIVATE_OWNERSHIP));
    // Medicare and Medicaid have the same priority levels.
    priorityPerson.attributes.put(Person.BIRTHDATE, Utilities.convertCalendarYearsToTime(1950));
    priorityPerson.attributes.remove(Person.BIRTHDATE_AS_LOCALDATE);
    plan = finder.find(plans, priorityPerson, null, time);
    assertEquals(10001, plan.id); // Medicare Plan ID.
    priorityPerson.attributes.put(Person.BIRTHDATE, Utilities.convertCalendarYearsToTime(2000));
    priorityPerson.attributes.remove(Person.BIRTHDATE_AS_LOCALDATE);
    priorityPerson.attributes.put(Person.INCOME, 1000);
    plan = finder.find(plans, priorityPerson, null, time);
    assertEquals(20001, plan.id); // Medicaid Plan ID.
    // Dual Eligble has the highest priority level.
    priorityPerson.attributes.put(Person.BIRTHDATE, Utilities.convertCalendarYearsToTime(1950));
    priorityPerson.attributes.remove(Person.BIRTHDATE_AS_LOCALDATE);
    plan = finder.find(plans, priorityPerson, null, time);
    assertEquals(30001, plan.id); // Dual Eligible Plan ID.

  }

  @Test(expected = RuntimeException.class)
  public void invalidPayerFinderTest() {
    // Note that "bestrate" should be spelled "best_rate"
    Config.set("generate.payers.selection_behavior", "bestrate");
    PayerManager.clear();
    PayerManager.loadPayers(location);
  }
}