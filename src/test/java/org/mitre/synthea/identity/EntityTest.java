package org.mitre.synthea.identity;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

public class EntityTest {
  private Entity testEntity;

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
  }
}