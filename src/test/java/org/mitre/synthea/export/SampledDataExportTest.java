package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.model.dstu2.composite.SampledDataDt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Locale;

import org.junit.Test;
import org.mitre.synthea.engine.Components.SampledData;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.TimeSeriesData;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/** Tests the numeric representation shared by the sampled data exporters. */
public class SampledDataExportTest {

  @Test
  public void defaultFormatUsesDecimalPointsWithoutGrouping() {
    assertFormatted(Locale.GERMANY, null, "1234.5 -1234.5 0.125 -0.125 0 -0",
        1234.5, -1234.5, 0.125, -0.125, 0.0, -0.0);
  }

  @Test
  public void defaultFormatUsesAsciiDigits() {
    assertFormatted(Locale.forLanguageTag("ar-EG"), null,
        "1234.5 -1234.5 0.125 -0.125 0 -0", 1234.5, -1234.5, 0.125, -0.125, 0.0, -0.0);
  }

  @Test
  public void defaultFormatRetainsExistingThreeDecimalRounding() {
    assertFormatted(Locale.US, null, "1.235 -1.235", 1.23456, -1.23456);
  }

  @Test
  public void configuredFormatRetainsPrecisionWithoutGrouping() {
    assertFormatted(Locale.GERMANY, "#,##0.0000", "1234.5000 -0.1250 0.0001",
        1234.5, -0.125, 0.0001);
  }

  @Test
  public void configuredScientificFormatIsRetained() {
    assertFormatted(Locale.FRANCE, "0.00E0", "1.25E3 -1.25E-1", 1250.0, -0.125);
  }

  @Test
  public void multidimensionalSamplesRemainInterlaced() {
    SampledData data = samples("0.00000", 1.23456, -2.34567);
    data.series = Arrays.asList(data.series.get(0),
        new TimeSeriesData(Arrays.asList(1000.5, -0.00012), 0.01));
    assertEquals("1.23456 1000.50000 -2.34567 -0.00012",
        ExportHelper.sampledDataToValueString(data));
  }

  @Test
  public void observationStateRetainsConfiguredFormat() throws Exception {
    SampledData data = observationValue("0.0000");
    assertEquals("0.0000", data.decimalFormat);
    assertEquals("1234.5000 -0.1250 0.0001", ExportHelper.sampledDataToValueString(data));
    assertEquals(2.0, data.originValue, 0.0);
    assertEquals(0.5, data.factor, 0.0);
    assertEquals(-10.0, data.lowerLimit, 0.0);
    assertEquals(2000.0, data.upperLimit, 0.0);
  }

  @Test
  public void observationStateKeepsDefaultWhenFormatIsAbsent() throws Exception {
    SampledData data = observationValue(null);
    assertNull(data.decimalFormat);
    assertEquals("1234.5 -0.125 0", ExportHelper.sampledDataToValueString(data));
  }

  @Test
  public void dstu2ExportUsesRecordedPrecisionInGermanLocale() throws Exception {
    Locale original = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
      SampledDataDt data = FhirDstu2.mapValueToSampledData(observationValue("0.0000"), "mV");
      assertEquals("1234.5000 -0.1250 0.0001", data.getData());
      assertEquals(10.0, data.getPeriod().doubleValue(), 0.0);
      assertEquals(1, data.getDimensions().intValue());
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, original);
    }
  }

  @Test
  public void stu3ExportUsesRecordedPrecisionInGermanLocale() throws Exception {
    Locale original = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
      org.hl7.fhir.dstu3.model.SampledData data =
          FhirStu3.mapValueToSampledData(observationValue("0.0000"), "mV");
      assertEquals("1234.5000 -0.1250 0.0001", data.getData());
      assertEquals(10.0, data.getPeriod().doubleValue(), 0.0);
      assertEquals(1, data.getDimensions());
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, original);
    }
  }

  @Test
  public void r4ExportUsesRecordedPrecisionInGermanLocale() throws Exception {
    Locale original = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
      org.hl7.fhir.r4.model.SampledData data =
          FhirR4.mapValueToSampledData(observationValue("0.0000"), "mV");
      assertEquals("1234.5000 -0.1250 0.0001", data.getData());
      assertEquals(10.0, data.getPeriod().doubleValue(), 0.0);
      assertEquals(1, data.getDimensions());
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, original);
    }
  }

  private static void assertFormatted(Locale locale, String format, String expected,
      Double... values) {
    Locale original = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.Category.FORMAT, locale);
      assertEquals(expected, ExportHelper.sampledDataToValueString(samples(format, values)));
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, original);
    }
  }

  private static SampledData samples(String format, Double... values) {
    SampledData data = new SampledData();
    data.decimalFormat = format;
    data.series = Arrays.asList(new TimeSeriesData(Arrays.asList(values), 0.01));
    return data;
  }

  private static SampledData observationValue(String format) throws Exception {
    JsonObject definition = new JsonObject();
    definition.addProperty("type", "Observation");
    definition.addProperty("direct_transition", "Terminal");
    definition.addProperty("unit", "mV");
    JsonObject code = new JsonObject();
    code.addProperty("system", "LOINC");
    code.addProperty("code", "sampled-data-test");
    code.addProperty("display", "Test waveform");
    JsonArray codes = new JsonArray();
    codes.add(code);
    definition.add("codes", codes);
    JsonObject sampled = new JsonObject();
    sampled.addProperty("origin_value", 2.0);
    sampled.addProperty("factor", 0.5);
    sampled.addProperty("lower_limit", -10.0);
    sampled.addProperty("upper_limit", 2000.0);
    if (format != null) {
      sampled.addProperty("decimal_format", format);
    }
    JsonArray attributes = new JsonArray();
    attributes.add("waveform");
    sampled.add("attributes", attributes);
    definition.add("sampled_data", sampled);

    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    PayerManager.loadNoInsurance();
    person.coverage.setPlanToNoInsurance(0L);
    person.record.encounterStart(0L, EncounterType.AMBULATORY);
    person.attributes.put("waveform", new TimeSeriesData(
        Arrays.asList(1234.5, -0.125, 0.0001), 0.01));
    State observation = State.build(null, "Waveform", definition).clone();
    assertTrue(observation.process(person, 0L));
    HealthRecord.Observation recorded = person.record.getLatestObservation("sampled-data-test");
    assertTrue(recorded.value instanceof SampledData);
    return (SampledData) recorded.value;
  }
}
