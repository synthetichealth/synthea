package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.Test;
import org.mitre.synthea.engine.Components.Attachment;
import org.mitre.synthea.helpers.TimeSeriesData;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.powermock.reflect.Whitebox;

public class ObservationAttachmentIsolationTest {

  @Test
  public void patientChartsHaveHashesMatchingTheirOwnImages() throws Exception {
    State template = observation(chartDefinition());
    Person first = person(0.0, 1.0, 0.0, 1.0);
    Person second = person(1.0, 0.0, 1.0, 0.0);
    Attachment firstAttachment = record(template.clone(), first);
    String firstImage = firstAttachment.data;
    Attachment secondAttachment = record(template.clone(), second);
    assertNotSame(firstAttachment, secondAttachment);
    assertNotEquals(firstAttachment.data, secondAttachment.data);
    assertEquals(firstImage, firstAttachment.data);
    assertChartIntegrity(firstAttachment);
    assertChartIntegrity(secondAttachment);
  }

  @Test
  public void renderingDoesNotPopulateTheSharedStateTemplate() throws Exception {
    State template = observation(chartDefinition());
    Attachment definition = Whitebox.getInternalState(template, "attachment");
    record(template.clone(), person(0.0, 1.0, 0.0));
    assertNull(definition.data);
    assertNull(definition.hash);
    assertEquals(0, definition.size);
    assertNull(definition.contentType);
    assertNull(definition.title);
  }

  @Test
  public void repeatedStateProcessingKeepsEachChartAndHashIndependent() throws Exception {
    State state = observation(chartDefinition());
    Person person = person(0.0, 1.0, 0.0);
    Attachment original = record(state, person);
    String originalImage = original.data;
    person.attributes.put("trace", series(1.0, 0.0, 1.0));
    Attachment updated = record(state, person);
    assertNotEquals(originalImage, updated.data);
    assertEquals(originalImage, original.data);
    assertChartIntegrity(original);
    assertChartIntegrity(updated);
  }

  @Test
  public void urlAttachmentKeepsItsExplicitMetadata() throws Exception {
    JsonObject definition = JsonParser.parseString("{\"url\":\"https://example.com/chart\","
        + "\"hash\":\"dGVzdC1oYXNo\",\"size\":123,\"content_type\":\"image/png\","
        + "\"title\":\"External chart\",\"language\":\"en-US\"}").getAsJsonObject();
    Attachment attachment = record(observation(definition), person(0.0, 1.0));
    assertEquals("https://example.com/chart", attachment.url);
    assertEquals("dGVzdC1oYXNo", attachment.hash);
    assertEquals(123, attachment.size);
    assertEquals("image/png", attachment.contentType);
    assertEquals("External chart", attachment.title);
    assertEquals("en-US", attachment.language);
    assertNull(attachment.data);
  }

  @Test
  public void inlineAttachmentKeepsItsProvidedDataAndHash() throws Exception {
    String data = Base64.getEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8));
    String hash = sha1(data);
    JsonObject definition = new JsonObject();
    definition.addProperty("data", data);
    definition.addProperty("hash", hash);
    definition.addProperty("content_type", "text/plain");
    definition.addProperty("size", 5);
    Attachment attachment = record(observation(definition), person(0.0, 1.0));
    assertEquals(data, attachment.data);
    assertEquals(hash, attachment.hash);
    assertEquals("text/plain", attachment.contentType);
    assertEquals(5, attachment.size);
    assertNull(attachment.url);
  }

  private void assertChartIntegrity(Attachment attachment) throws Exception {
    assertEquals(sha1(attachment.data), attachment.hash);
    assertEquals(Base64.getDecoder().decode(attachment.data).length, attachment.size);
    assertEquals("image/png", attachment.contentType);
    assertEquals("Patient trace", attachment.title);
    assertEquals(300, attachment.width);
    assertEquals(150, attachment.height);
  }

  private String sha1(String encodedData) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-1")
        .digest(Base64.getDecoder().decode(encodedData));
    return Base64.getEncoder().encodeToString(digest);
  }

  private Attachment record(State state, Person person) {
    assertTrue(state.process(person, 1L));
    return (Attachment) ((HealthRecord.Observation) state.entry).value;
  }

  private Person person(double... values) {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(0L);
    person.attributes.put("trace", series(values));
    person.record.encounterStart(1L, EncounterType.AMBULATORY);
    return person;
  }

  private TimeSeriesData series(double... values) {
    TimeSeriesData series = new TimeSeriesData(values.length, 0.25);
    for (double value : values) {
      series.addValue(value);
    }
    return series;
  }

  private JsonObject chartDefinition() {
    return JsonParser.parseString("{\"chart\":{\"type\":\"line\",\"title\":\"Patient trace\","
        + "\"axis_attribute_x\":\"time\",\"width\":300,\"height\":150,"
        + "\"series\":[{\"attribute\":\"trace\"}]}}").getAsJsonObject();
  }

  private State observation(JsonObject attachment) throws Exception {
    Module module = new Module(JsonParser.parseString("{\"name\":\"Attachment Isolation\","
        + "\"states\":{\"Initial\":{\"type\":\"Initial\","
        + "\"direct_transition\":\"Terminal\"},\"Terminal\":{\"type\":\"Terminal\"}}}")
        .getAsJsonObject(), false);
    JsonObject definition = JsonParser.parseString("{\"type\":\"Observation\","
        + "\"codes\":[{\"system\":\"SNOMED-CT\",\"code\":\"chart\",\"display\":\"Chart\"}],"
        + "\"direct_transition\":\"Terminal\"}").getAsJsonObject();
    definition.add("attachment", attachment);
    return State.build(module, "Chart", definition);
  }
}
