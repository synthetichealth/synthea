package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.helpers.physiology.IoMapper;
import org.mitre.synthea.world.agents.Person;
import org.simulator.math.odes.MultiTable;

public class ExpressionParameterTypesTest {

  @Test
  public void spacedBooleanUsesCurrentPersonValue() {
    Person person = new Person(0L);
    person.attributes.put("patient flag", true);
    ExpressionProcessor processor = new ExpressionProcessor(
        "if #b{patient flag} then 1.0 else 0.0");

    assertEquals(1.0, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
    person.attributes.put("patient flag", false);
    assertEquals(0.0, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
  }

  @Test
  public void spacedStringUsesCurrentPersonValue() {
    Person person = new Person(0L);
    person.attributes.put("risk group", "high");
    ExpressionProcessor processor = new ExpressionProcessor(
        "if #s{risk group} = 'high' then 2.0 else 1.0");

    assertEquals(2.0, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
    person.attributes.put("risk group", "low");
    assertEquals(1.0, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
  }

  @Test
  public void spacedDecimalUsesCurrentPersonValue() {
    Person person = new Person(0L);
    person.attributes.put("dose amount", 12);
    ExpressionProcessor processor = new ExpressionProcessor("#d{dose amount} / 2.0");

    assertEquals(6.0, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
    person.attributes.put("dose amount", 7.5);
    assertEquals(3.75, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
  }

  @Test
  public void spacedListUsesProvidedValues() {
    ExpressionProcessor processor = new ExpressionProcessor("Sum(#l{test results})");
    Map<String, Object> params = Map.of("test results", List.of(
        new BigDecimal("2.0"), new BigDecimal("3.0"), new BigDecimal("5.0")));

    assertEquals(10.0, processor.evaluateNumeric(params).doubleValue(), 0.0);
  }

  @Test
  public void spacedListWorksThroughPhysiologyOutputMapper() {
    IoMapper mapper = new IoMapper();
    mapper.setFromExp("Sum(#l{test results})");
    mapper.initialize();
    MultiTable results = new MultiTable(new double[] {0, 1, 2},
        new double[][] {{2}, {3}, {5}}, new String[] {"test results"});

    assertEquals(10.0, (double) mapper.getOutputResult(results, 0.0), 0.0);
  }

  @Test
  public void spacedExplicitDecimalOverridesExternalType() {
    Person person = new Person(0L);
    person.attributes.put("dose amount", 12);
    Map<String, String> types = new HashMap<>();
    types.put("dose amount", "String");
    ExpressionProcessor processor = new ExpressionProcessor("#d{dose amount} / 2.0", types);

    assertEquals(6.0, ((Number) processor.evaluate(person, 0L)).doubleValue(), 0.0);
  }

  @Test
  public void explicitTypesWithoutSpacesRemainSupported() {
    ExpressionProcessor processor = new ExpressionProcessor(
        "if #b{patient_flag} and #s{risk_group} = 'high' "
        + "then Sum(#l{test_results}) * #d{factor} else 0.0");
    Map<String, Object> params = Map.of("patient_flag", true, "risk_group", "high",
        "test_results", List.of(new BigDecimal("2.0"), new BigDecimal("3.0")),
        "factor", new BigDecimal("4.0"));

    assertEquals(20.0, processor.evaluateNumeric(params).doubleValue(), 0.0);
  }

  @Test
  public void externalTypesKeepOriginalSpacedParameterNames() {
    Map<String, String> types = new HashMap<>();
    types.put("patient flag", "Boolean");
    types.put("risk group", "String");
    types.put("test results", "List<Decimal>");
    types.put("scale factor", "Decimal");
    ExpressionProcessor processor = new ExpressionProcessor(
        "if #{patient flag} and #{risk group} = 'high' "
        + "then Sum(#{test results}) * #{scale factor} else 0.0", types);
    Map<String, Object> params = Map.of("patient flag", true, "risk group", "high",
        "test results", List.of(new BigDecimal("2.0"), new BigDecimal("3.0")),
        "scale factor", new BigDecimal("4.0"));

    assertEquals(20.0, processor.evaluateNumeric(params).doubleValue(), 0.0);
  }
}
