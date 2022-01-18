package org.mitre.synthea.world.agents.behaviors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerController;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.behaviors.plan_finder.PlanFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.plan_finder.PlanFinderRandom;
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
    TestHelper.loadTestProperties();
    person.attributes.put(Person.STATE, Config.get("test_state.default", "Massachusetts"));
  }

  @Test
  public void noPayersRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    PayerController.clear();
    PayerController.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderRandom finder = new PlanFinderRandom();
    List<Payer> options = new ArrayList<Payer>();
    Payer payer = finder.find(options, person, null, 0L).getPayer();
    assertNotNull(payer);
    assertEquals("NO_INSURANCE", payer.getName());
  }

  @Test
  public void onePayerRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    PayerController.clear();
    PayerController.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderRandom finder = new PlanFinderRandom();
    Payer payer = finder.find(PayerController.getPrivatePayers(), person, null, 0L).getPayer();
    assertNotNull(payer);
    assertNotEquals("NO_INSURANCE", payer.getName());
  }

  @Test
  public void noPayersBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    PayerController.clear();
    PayerController.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderBestRates finder = new PlanFinderBestRates();
    List<Payer> options = new ArrayList<Payer>();
    Payer payer = finder.find(options, person, null, 0L).getPayer();
    assertNotNull(payer);
    assertEquals("NO_INSURANCE", payer.getName());
  }

  @Test
  public void onePayerBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    PayerController.clear();
    PayerController.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PlanFinderBestRates finder = new PlanFinderBestRates();
    Payer payer = finder.find(PayerController.getPrivatePayers(), person, null, 0L).getPayer();
    assertNotNull(payer);
    assertNotEquals("NO_INSURANCE", payer.getName());
  }

  @Test(expected = RuntimeException.class)
  public void invalidPayerFinderTest() {
    // Note that "bestrate" should be spelled "best_rate"
    Config.set("generate.payers.selection_behavior", "bestrate");
    PayerController.clear();
    PayerController.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
  }
}