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
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Location;

public class PayerFinderTest {

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
    Payer.clear();
    Payer.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PayerFinderRandom finder = new PayerFinderRandom();
    List<Payer> options = new ArrayList<Payer>();
    Payer payer = finder.find(options, person, null, 0L);
    assertNotNull(payer);
    assertEquals("NO_INSURANCE", payer.getName());
  }

  @Test
  public void onePayerRandom() {
    Config.set("generate.payers.selection_behavior", "random");
    Payer.clear();
    Payer.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PayerFinderRandom finder = new PayerFinderRandom();
    Payer payer = finder.find(Payer.getPrivatePayers(), person, null, 0L);
    assertNotNull(payer);
    assertNotEquals("NO_INSURANCE", payer.getName());
  }

  @Test
  public void noPayersBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    Payer.clear();
    Payer.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PayerFinderBestRates finder = new PayerFinderBestRates();
    List<Payer> options = new ArrayList<Payer>();
    Payer payer = finder.find(options, person, null, 0L);
    assertNotNull(payer);
    assertEquals("NO_INSURANCE", payer.getName());
  }

  @Test
  public void onePayerBestRate() {
    Config.set("generate.payers.selection_behavior", "best_rate");
    Payer.clear();
    Payer.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
    PayerFinderBestRates finder = new PayerFinderBestRates();
    Payer payer = finder.find(Payer.getPrivatePayers(), person, null, 0L);
    assertNotNull(payer);
    assertNotEquals("NO_INSURANCE", payer.getName());
  }

  @Test(expected = RuntimeException.class)
  public void invalidPayerFinderTest() {
    // Note that "bestrate" should be spelled "best_rate"
    Config.set("generate.payers.selection_behavior", "bestrate");
    Payer.clear();
    Payer.loadPayers(new Location((String) person.attributes.get(Person.STATE), null));
  }
}