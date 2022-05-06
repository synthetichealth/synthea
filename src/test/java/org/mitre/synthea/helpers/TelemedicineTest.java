package org.mitre.synthea.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class TelemedicineTest {

  @Test
  public void shouldEncounterBeVirtual() {
    long beforeTelemedicine = LocalDateTime.of(2015, 1, 1, 12, 0)
        .toInstant(ZoneOffset.UTC).toEpochMilli();
    assertFalse(Telemedicine.shouldEncounterBeVirtual(new Person(0), beforeTelemedicine));
    long duringPandemic = LocalDateTime.of(2021, 1, 1, 12, 0)
        .toInstant(ZoneOffset.UTC).toEpochMilli();
    assertTrue(Telemedicine.shouldEncounterBeVirtual(new Person(8456), duringPandemic));
  }
}