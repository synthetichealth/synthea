package org.mitre.synthea.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;

public class EntityTest {
  private Entity testEntity;

  /**
   * Sets up the test suite.
   * @throws IOException If it can't the JSON fixture
   */
  @Before
  public void setUp() throws IOException {
    String rawJSON = Utilities.readResource("identity/test_records.json");
    EntityManager em = EntityManager.fromJSON(rawJSON);
    testEntity = em.getRecords().get(0);
  }

  @Test
  public void seedAt() {
    LocalDate before = LocalDate.parse("1943-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    Seed seed = testEntity.seedAt(before);
    assertNull(seed);
    LocalDate duringFirstSeed = LocalDate.parse("1945-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    seed = testEntity.seedAt(duringFirstSeed);
    assertEquals("5678", seed.getSeedId());
    LocalDate duringOpenEnd = LocalDate.parse("2020-04-17", DateTimeFormatter.ISO_LOCAL_DATE);
    seed = testEntity.seedAt(duringOpenEnd);
    assertEquals("1416", seed.getSeedId());
  }

  @Test
  public void seedAtTimestamp() {
    long before = LocalDate.parse("1943-04-17", DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    Seed seed = testEntity.seedAt(before);
    assertNull(seed);
    long duringFirstSeed = LocalDate.parse("1945-04-17", DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    seed = testEntity.seedAt(duringFirstSeed);
    assertEquals("5678", seed.getSeedId());
    long duringOpenEnd = LocalDate.parse("2020-04-17", DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    seed = testEntity.seedAt(duringOpenEnd);
    assertEquals("1416", seed.getSeedId());
    long startOfFirstSeed = -813960000000L; // Fri Mar 17 1944 04:00:00 GMT+0000
    // Note this timestamp is 1944-03-17 in EDT (GMT-4) but 1944-03-16 in EST (GMT-5)
    seed = testEntity.seedAt(startOfFirstSeed);
    assertEquals("5678", seed.getSeedId());
  }
}