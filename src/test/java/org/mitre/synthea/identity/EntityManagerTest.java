package org.mitre.synthea.identity;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;

import java.io.IOException;

public class EntityManagerTest {

  @Test
  public void fromJSON() throws IOException {
    String rawJSON = Utilities.readResource("identity/test_records.json");
    EntityManager em = EntityManager.fromJSON(rawJSON);
    Assert.assertEquals(1, em.getRecords().size());
    Assert.assertEquals("F", em.getRecords().get(0).getGender());
    Seed firstSeed = em.getRecords().get(0).getSeeds().get(0);
    Assert.assertEquals("Rita Ebony", firstSeed.getGivenName());
    Assert.assertNotNull(firstSeed.getDateOfBirth());
    Variant firstVariant = firstSeed.getVariants().get(0);
    Assert.assertEquals("Margarita Ebony", firstVariant.getGivenName());
  }
}