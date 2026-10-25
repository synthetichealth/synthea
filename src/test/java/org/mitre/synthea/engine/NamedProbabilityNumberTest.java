package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class NamedProbabilityNumberTest {

  @Test
  public void distributedAcceptsIntegerOneFromSetAttribute() throws Exception {
    assertRoute("\"value\":1", false, 0.75, "Selected", Integer.class);
  }

  @Test
  public void distributedAcceptsIntegerZeroFromSetAttribute() throws Exception {
    assertRoute("\"value\":0", false, 0.25, "Fallback", Integer.class);
  }

  @Test
  public void distributedAcceptsExpressionProbability() throws Exception {
    assertRoute("\"expression\":\"0.5\"", false, 0.25, "Selected", BigDecimal.class);
    assertRoute("\"expression\":\"0.5\"", false, 0.75, "Fallback", BigDecimal.class);
  }

  @Test
  public void complexAcceptsIntegerFromSetAttribute() throws Exception {
    assertRoute("\"value\":1", true, 0.75, "Selected", Integer.class);
    assertRoute("\"value\":0", true, 0.25, "Fallback", Integer.class);
  }

  @Test
  public void complexAcceptsExpressionProbability() throws Exception {
    assertRoute("\"expression\":\"0.5\"", true, 0.25, "Selected", BigDecimal.class);
    assertRoute("\"expression\":\"0.5\"", true, 0.75, "Fallback", BigDecimal.class);
  }

  @Test
  public void fractionalDoubleKeepsItsProbability() throws Exception {
    assertRoute("\"value\":0.5", false, 0.25, "Selected", Double.class);
    assertRoute("\"value\":0.5", false, 0.75, "Fallback", Double.class);
  }

  @Test
  public void missingAttributeUsesDefaultProbability() throws Exception {
    assertRoute(null, false, 0.25, "Selected", null);
    assertRoute(null, false, 0.75, "Fallback", null);
    assertRoute(null, true, 0.25, "Selected", null);
    assertRoute(null, true, 0.75, "Fallback", null);
  }

  private void assertRoute(String valueDefinition, boolean complex, double draw,
      String expectedState, Class<?> expectedType) throws Exception {
    String options = "[{\"distribution\":{\"attribute\":\"probability\",\"default\":0.5},"
        + "\"transition\":\"Selected\"},{\"distribution\":1.0,"
        + "\"transition\":\"Fallback\"}]";
    String transition = complex
        ? "\"complex_transition\":[{\"distributions\":" + options + "}]"
        : "\"distributed_transition\":" + options;
    JsonObject definition = JsonParser.parseString("{\"name\":\"Named Probability Number\","
        + "\"states\":{\"Initial\":{\"type\":\"Initial\","
        + "\"direct_transition\":\"Set\"},\"Set\":{\"type\":\"SetAttribute\","
        + "\"attribute\":\"probability\",\"direct_transition\":\"Choose\"},"
        + "\"Choose\":{\"type\":\"Simple\"," + transition + "},"
        + "\"Selected\":{\"type\":\"Terminal\"},"
        + "\"Fallback\":{\"type\":\"Terminal\"}}}").getAsJsonObject();
    if (valueDefinition != null) {
      JsonObject value = JsonParser.parseString("{" + valueDefinition + "}").getAsJsonObject();
      value.entrySet().forEach(e -> definition.getAsJsonObject("states")
          .getAsJsonObject("Set").add(e.getKey(), e.getValue()));
    }
    Module module = new Module(definition, false);
    Person person = new Person(0L) {
      @Override
      public double rand() {
        return draw;
      }
    };
    person.attributes.put(Person.BIRTHDATE, 0L);
    assertTrue(module.process(person, 1L));
    assertEquals(expectedState, person.history.get(0).name);
    if (expectedType != null) {
      assertEquals(expectedType, person.attributes.get("probability").getClass());
    }
  }
}
