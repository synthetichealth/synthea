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
    Assert.assertEquals("Rita Ebony", em.getRecords().get(0).getSeeds().get(0).getGivenName());
    em.getRecords().get(0).getSeeds().get(0).getDateOfBirth();
  }
}