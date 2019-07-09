package org.mitre.synthea.world.agents.behaviors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Location;

public class PayerFinderTest {
  
  private Person person;

  /**
   * Setup for Provider Finder Tests.
   */
  @Before
  public void setup() {
    person = new Person(0L);
    // Clear any Payers that may have already been statically loaded.
    Payer.clear();
    // Load in the .csv list of Payers for MA.
    Payer.loadPayers(new Location("Massachusetts", null));
    // Get the first Payer in the list for testing.
  }

  @Test
  public void testRandom() {
    PayerFinderRandom finder = new PayerFinderRandom();
    Payer payer = finder.find(Payer.getPrivatePayers(), person, null, 0L);
    Assert.assertNotNull(payer);
  }
}
