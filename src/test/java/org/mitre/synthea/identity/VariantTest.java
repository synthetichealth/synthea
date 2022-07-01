package org.mitre.synthea.identity;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class VariantTest {

  @Test
  public void demographicAttributesForPerson() throws IOException {
    String rawJSON = Utilities.readResource("identity/test_records.json");
    EntityManager em = EntityManager.fromJSON(rawJSON);
    Entity e = em.getRecords().get(0);
    Seed firstSeed = e.getSeeds().get(0);
    Variant firstVariant = firstSeed.getVariants().get(0);
    Map<String, Object> attrs = firstVariant.demographicAttributesForPerson();
    assertEquals("Margarita Ebony", attrs.get(Person.FIRST_NAME));
  }
}