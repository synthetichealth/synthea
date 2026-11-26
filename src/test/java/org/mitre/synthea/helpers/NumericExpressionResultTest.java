package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.helpers.physiology.IoMapper;
import org.mitre.synthea.world.agents.Person;
import org.simulator.math.odes.MultiTable;

public class NumericExpressionResultTest {

  @Test
  public void integerArithmeticReturnsDecimal() {
    ExpressionProcessor processor = new ExpressionProcessor("10 + 3");
    assertEquals(new BigDecimal("13"), processor.evaluateNumeric(Collections.emptyMap()));
  }

  @Test
  public void negativeIntegerReturnsDecimal() {
    ExpressionProcessor processor = new ExpressionProcessor("-17");
    assertEquals(new BigDecimal("-17"), processor.evaluateNumeric(Collections.emptyMap()));
  }

  @Test
  public void longResultKeepsDigitsBeyondDoublePrecision() {
    ExpressionProcessor processor = new ExpressionProcessor("ToLong('9007199254740993')");
    assertEquals(new BigDecimal("9007199254740993"),
        processor.evaluateNumeric(Collections.emptyMap()));
  }

  @Test
  public void decimalArithmeticRetainsFraction() {
    ExpressionProcessor processor = new ExpressionProcessor("25 / 2");
    assertEquals(0, new BigDecimal("12.5")
        .compareTo(processor.evaluateNumeric(Collections.emptyMap())));
  }

  @Test
  public void integerExpressionPopulatesModelInputs() {
    IoMapper mapper = new IoMapper();
    mapper.setFromExp("10 + 3");
    mapper.setTo("model_input");
    mapper.initialize();
    Map<String, Double> inputs = new HashMap<>();
    assertEquals(13.0, mapper.toModelInputs(new Person(0L), 0L, inputs), 0.0);
    assertEquals(13.0, inputs.get("model_input"), 0.0);
  }

  @Test
  public void integerBranchUsesPersonAttributes() {
    IoMapper mapper = new IoMapper();
    mapper.setFromExp("if #b{enabled} then 3 else 5");
    mapper.setTo("model_input");
    mapper.initialize();
    Person person = new Person(0L);
    person.attributes.put("enabled", true);
    Map<String, Double> inputs = new HashMap<>();
    assertEquals(3.0, mapper.toModelInputs(person, 0L, inputs), 0.0);
    person.attributes.put("enabled", false);
    assertEquals(5.0, mapper.toModelInputs(person, 0L, inputs), 0.0);
    assertEquals(5.0, inputs.get("model_input"), 0.0);
  }

  @Test
  public void countExpressionReadsSimulationOutput() {
    IoMapper mapper = new IoMapper();
    mapper.setFromExp("Count(#{model_output})");
    mapper.setTo("sample_count");
    mapper.initialize(Collections.singletonMap("model_output", "List<Decimal>"));
    MultiTable results = new MultiTable(new double[] {0.0, 1.0, 2.0},
        new double[][] {{4.0}, {6.0}, {8.0}}, new String[] {"model_output"});
    assertEquals(3.0, (double) mapper.getOutputResult(results, 0.0), 0.0);
    assertEquals(2.0, (double) mapper.getOutputResult(results, 1.0), 0.0);
  }

  @Test
  public void nonNumericResultsHaveExpressionContext() {
    for (String expression : new String[] {"true", "'text'", "null"}) {
      ExpressionProcessor processor = new ExpressionProcessor(expression);
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
          () -> processor.evaluateNumeric(Collections.emptyMap()));
      assertTrue(error.getMessage().contains(expression));
      assertTrue(error.getMessage().contains("must evaluate to a number"));
    }
  }

  @Test
  public void generalEvaluationKeepsOriginalTypes() {
    assertEquals(Integer.valueOf(13),
        new ExpressionProcessor("10 + 3").evaluate(Collections.emptyMap()));
    assertEquals(Boolean.TRUE,
        new ExpressionProcessor("true").evaluate(Collections.emptyMap()));
    assertEquals("text",
        new ExpressionProcessor("'text'").evaluate(Collections.emptyMap()));
  }
}
