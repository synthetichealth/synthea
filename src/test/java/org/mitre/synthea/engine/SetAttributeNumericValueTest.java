package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class SetAttributeNumericValueTest {
  private static final String ATTRIBUTE = "numeric_value";

  private State buildAttribute(String properties) throws Exception {
    JsonObject definition = JsonParser.parseString("{\"type\":\"SetAttribute\","
        + "\"attribute\":\"" + ATTRIBUTE + "\",\"direct_transition\":\"Terminal\""
        + properties + "}").getAsJsonObject();
    return State.build(null, "Set Value", definition);
  }

  private Object processValue(String literal) throws Exception {
    Person person = new Person(0L);
    State state = buildAttribute(",\"value\":" + literal);
    assertTrue(state.process(person, 0L));
    return person.attributes.get(ATTRIBUTE);
  }

  private void assertDoubleLiteral(String literal) throws Exception {
    Object value = processValue(literal);
    assertEquals(literal, Double.class, value.getClass());
    assertEquals(literal, Double.valueOf(literal), value);
  }

  @Test
  public void integersWithinIntRangeKeepTheirIntegerType() throws Exception {
    int[] values = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
    for (int value : values) {
      assertEquals(Integer.valueOf(value), processValue(Integer.toString(value)));
    }
  }

  @Test
  public void valueAboveIntegerMaximumRemainsDouble() throws Exception {
    assertDoubleLiteral("2147483648");
  }

  @Test
  public void valueBelowIntegerMinimumRemainsDouble() throws Exception {
    assertDoubleLiteral("-2147483649");
  }

  @Test
  public void largerIntegralValuesKeepTheirMagnitude() throws Exception {
    String[] literals = {"4294967296", "-4294967296", "1650000000000", "-1650000000000"};
    for (String literal : literals) {
      assertDoubleLiteral(literal);
    }
  }

  @Test
  public void fractionalValuesRemainDouble() throws Exception {
    String[] literals = {"1.5", "-1.5", "2147483647.5", "-2147483648.5"};
    for (String literal : literals) {
      assertDoubleLiteral(literal);
    }
  }

  @Test
  public void booleanAndStringValuesKeepTheirTypes() throws Exception {
    assertEquals(Boolean.TRUE, processValue("true"));
    assertEquals(Boolean.FALSE, processValue("false"));
    assertEquals("2147483648", processValue("\"2147483648\""));
  }

  @Test
  public void omittedValueStillClearsTheAttribute() throws Exception {
    Person person = new Person(0L);
    person.attributes.put(ATTRIBUTE, 42);
    assertTrue(buildAttribute("").process(person, 0L));
    assertFalse(person.attributes.containsKey(ATTRIBUTE));
  }

  @Test
  public void codeValueStillSetsACode() throws Exception {
    Person person = new Person(0L);
    State state = buildAttribute(",\"value_code\":{\"system\":\"SNOMED-CT\","
        + "\"code\":\"44054006\",\"display\":\"Diabetes mellitus type 2\"}");
    assertTrue(state.process(person, 0L));
    assertEquals(new Code("SNOMED-CT", "44054006", "Diabetes mellitus type 2"),
        person.attributes.get(ATTRIBUTE));
  }

  @Test
  public void attributeLogicUsesTheLargeLiteralValue() throws Exception {
    Person person = new Person(0L);
    assertTrue(buildAttribute(",\"value\":4294967296").process(person, 0L));
    JsonObject definition = JsonParser.parseString("{\"condition_type\":\"Attribute\","
        + "\"attribute\":\"" + ATTRIBUTE + "\",\"operator\":\"==\",\"value\":4294967296}")
        .getAsJsonObject();
    Logic condition = Utilities.getGson().fromJson(definition, Logic.class);
    assertTrue(condition.test(person, 0L));
  }

  @Test
  public void moduleCounterCanIncrementALargeLiteralAndPassItsGuard() throws Exception {
    JsonObject definition = JsonParser.parseString("{\"name\":\"Large Attribute\",\"states\":{"
        + "\"Initial\":{\"type\":\"Initial\",\"direct_transition\":\"Set Value\"},"
        + "\"Set Value\":{\"type\":\"SetAttribute\",\"attribute\":\"" + ATTRIBUTE + "\","
        + "\"value\":4294967296,\"direct_transition\":\"Increment\"},"
        + "\"Increment\":{\"type\":\"Counter\",\"attribute\":\"" + ATTRIBUTE + "\","
        + "\"action\":\"increment\",\"direct_transition\":\"Check Value\"},"
        + "\"Check Value\":{\"type\":\"Guard\",\"allow\":{\"condition_type\":\"Attribute\","
        + "\"attribute\":\"" + ATTRIBUTE + "\",\"operator\":\"==\",\"value\":4294967297},"
        + "\"direct_transition\":\"Terminal\"},\"Terminal\":{\"type\":\"Terminal\"}}}")
        .getAsJsonObject();
    Module module = new Module(definition, false);
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    assertTrue(module.process(person, 1000L));
    assertEquals(Double.valueOf(4294967297.0), person.attributes.get(ATTRIBUTE));
  }
}
