package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Tests for {@link ExportHelper}, in particular that numeric observation values
 * are not rounded so that all exporters of one run agree on the same value.
 * See https://github.com/synthetichealth/synthea/issues/1697
 */
public class ExportHelperTest {

  private HealthRecord record;

  @Before
  public void setUp() throws Exception {
    TestHelper.loadTestProperties();
    Person person = new Person(0);
    record = person.record;
  }

  @Test
  public void testGetObservationValueDoubleNotRounded() {
    // issue #1697: CSV export used to truncate Double observation values to 1 decimal
    HealthRecord.Observation observation = record.new Observation(0L, "test", 4.8075);
    assertEquals("4.8075", ExportHelper.getObservationValue(observation));
  }

  @Test
  public void testGetObservationValueDoubleMatchesFhirSerialization() {
    // the FHIR exporters serialize Double observation values via PlainBigDecimal,
    // this helper must produce the same string so the exports of one run agree
    double value = 123.456789;
    HealthRecord.Observation observation = record.new Observation(0L, "test", value);
    assertEquals(new PlainBigDecimal(value).toString(),
        ExportHelper.getObservationValue(observation));
  }

  @Test
  public void testGetObservationValueWholeDouble() {
    HealthRecord.Observation observation = record.new Observation(0L, "test", 3.0);
    assertEquals("3", ExportHelper.getObservationValue(observation));
  }

  @Test
  public void testGetObservationValueOtherTypesUnchanged() {
    HealthRecord.Observation stringObs = record.new Observation(0L, "test", "some value");
    assertEquals("some value", ExportHelper.getObservationValue(stringObs));

    HealthRecord.Observation intObs = record.new Observation(0L, "test", 42);
    assertEquals("42", ExportHelper.getObservationValue(intObs));

    HealthRecord.Observation nullObs = record.new Observation(0L, "test", null);
    assertEquals(null, ExportHelper.getObservationValue(nullObs));
  }
}
