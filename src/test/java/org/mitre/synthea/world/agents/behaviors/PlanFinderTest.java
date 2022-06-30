package org.mitre.synthea.world.agents.behaviors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderRandom;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

public class PlanFinderTest {

  private Person person;

  /**
   * Setup for Payer Finder Tests.
   * @throws Exception on configuration loading errors
   */
  @Before
  public void setup() throws Exception {
    person = new Person(0L);
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.5);
    person.attributes.put(Person.INCOME, 100000);
    // Load in the .csv test list of payers.
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    Config.set("generate.payers.insurance_plans.default_file",
        "generic/payers/test_plans.csv");
    TestHelper.loadTestProperties();
    person.attributes.put(Person.STATE, Config.get("test_state.default", "Massachusetts"));
  }

  @Test
  public void noPayersRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    PayerManager.clear();
    PayerManager.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderRandom finder = new PlanFinderRandom();
    Set<InsurancePlan> options = new HashSet<InsurancePlan>();
    Payer payer = finder.find(options, person, null, 0L).getPayer();
    assertNotNull(payer);
    assertTrue(payer.isNoInsurance());
  }

  @Test
  public void onePayerRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    PayerManager.clear();
    PayerManager.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderRandom finder = new PlanFinderRandom();
    Payer payer = finder.find(PayerManager.getActivePlans(PayerManager.getPrivatePayers(), 0L),
        person, null, 0L).getPayer();
    assertNotNull(payer);
    assertFalse(payer.isNoInsurance());
  }

  @Test
  public void noPayersBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    PayerManager.clear();
    PayerManager.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderBestRates finder = new PlanFinderBestRates();
    Set<InsurancePlan> options = new HashSet<InsurancePlan>();
    Payer payer = finder.find(options, person, null, 0L).getPayer();
    assertNotNull(payer);
    assertTrue(payer.isNoInsurance());
  }

  @Test
  public void onePayerBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    PayerManager.clear();
    PayerManager.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderBestRates finder = new PlanFinderBestRates();
    Payer payer = finder.find(PayerManager.getActivePlans(PayerManager.getPrivatePayers(), 0L),
        person, null, 0L).getPayer();
    assertNotNull(payer);
    assertFalse(payer.isNoInsurance());
  }

  @Test(expected = RuntimeException.class)
  public void invalidPayerFinderTest() {
    // Note that "bestrate" should be spelled "best_rate"
    Config.set("generate.payers.selection_behavior", "bestrate");
    PayerManager.clear();
    PayerManager.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
  }
}