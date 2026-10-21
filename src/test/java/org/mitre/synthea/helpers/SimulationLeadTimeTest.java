package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.helpers.physiology.IoMapper;
import org.simulator.math.odes.MultiTable;

public class SimulationLeadTimeTest {
  private static final String EXPRESSION = "Sum(#{x}) + Sum(#{y})";

  private MultiTable table() {
    // Uneven time spacing makes a row-count or step-size approximation incorrect.
    return new MultiTable(new double[] {0.0, 0.3, 1.1, 2.7},
        new double[][] {{100.0, 1000.0}, {2.0, 3.0}, {4.0, 5.0}, {8.0, 7.0}},
        new String[] {"x", "y"});
  }

  private MultiTable emptyTable() {
    MultiTable table = new MultiTable();
    table.setTimePoints(new double[0]);
    table.addBlock(new String[] {"x", "y"});
    return table;
  }

  private IoMapper mapper(String expression) {
    IoMapper mapper = new IoMapper();
    mapper.setFromExp(expression);
    mapper.setTo("simulation_result");
    mapper.setType(IoMapper.IoType.ATTRIBUTE);
    Map<String, String> types = new HashMap<>();
    types.put("x", "List<Decimal>");
    types.put("y", "List<Decimal>");
    types.put("missing", "List<Decimal>");
    mapper.initialize(types);
    return mapper;
  }

  @Test
  public void nonSampleLeadTimeStartsAtFollowingSampleInBothColumns() {
    assertEquals(24.0, (double) mapper(EXPRESSION).getOutputResult(table(), 0.5), 0.0);
  }

  @Test
  public void nonSampleLeadTimeUsesTheUnevenTimeGrid() {
    assertEquals(29.0, (double) mapper(EXPRESSION).getOutputResult(table(), 0.2), 0.0);
  }

  @Test
  public void leadTimeBetweenTheLastTwoSamplesUsesTheLastSample() {
    assertEquals(15.0, (double) mapper(EXPRESSION).getOutputResult(table(), 2.5), 0.0);
  }

  @Test
  public void exactLeadTimeIncludesTheBoundarySample() {
    assertEquals(24.0, (double) mapper(EXPRESSION).getOutputResult(table(), 1.1), 0.0);
  }

  @Test
  public void leadTimeImmediatelyBeforeASampleIncludesThatSample() {
    assertEquals(24.0, (double) mapper(EXPRESSION)
        .getOutputResult(table(), Math.nextDown(1.1)), 0.0);
  }

  @Test
  public void leadTimeImmediatelyAfterASampleExcludesThatSample() {
    assertEquals(15.0, (double) mapper(EXPRESSION)
        .getOutputResult(table(), Math.nextUp(1.1)), 0.0);
  }

  @Test
  public void leadTimeBeforeTheFirstSampleUsesAllSamples() {
    assertEquals(1129.0, (double) mapper(EXPRESSION).getOutputResult(table(), -0.1), 0.0);
  }

  @Test
  public void leadTimeAtTheFirstSampleUsesAllSamples() {
    assertEquals(1129.0, (double) mapper(EXPRESSION).getOutputResult(table(), 0.0), 0.0);
  }

  @Test
  public void leadTimeAtTheLastSampleIncludesTheLastSample() {
    assertEquals(15.0, (double) mapper(EXPRESSION).getOutputResult(table(), 2.7), 0.0);
  }

  @Test
  public void leadTimeAfterTheLastSampleReportsTheMissingOutputWindow() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> mapper(EXPRESSION).getOutputResult(table(), 3.0));
    assertTrue(error.getMessage().contains("No simulation samples"));
    assertTrue(error.getMessage().contains("3.0"));
    assertTrue(error.getMessage().contains(EXPRESSION));
  }

  @Test
  public void emptyTableReportsTheMissingOutputWindow() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> mapper(EXPRESSION).getOutputResult(emptyTable(), 0.0));
    assertTrue(error.getMessage().contains("No simulation samples"));
    assertTrue(error.getMessage().contains("0.0"));
    assertTrue(error.getMessage().contains(EXPRESSION));
  }

  @Test
  public void invalidColumnKeepsItsDiagnosticAfterTheLastSample() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> mapper("Sum(#{missing})").getOutputResult(table(), 3.0));
    assertEquals("Invalid model parameter \"missing\" in expression \"Sum(#{missing})\".",
        error.getMessage());
  }

  @Test
  public void invalidColumnKeepsItsDiagnosticForAnEmptyTable() {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> mapper("Sum(#{missing})").getOutputResult(emptyTable(), 0.0));
    assertEquals("Invalid model parameter \"missing\" in expression \"Sum(#{missing})\".",
        error.getMessage());
  }

  @Test
  public void constantExpressionDoesNotRequireSamples() {
    assertEquals(5.0, (double) mapper("2.0 + 3.0").getOutputResult(emptyTable(), 0.0), 0.0);
  }

  @Test
  public void constantExpressionDoesNotRequireASampleAfterLeadTime() {
    assertEquals(5.0, (double) mapper("2.0 + 3.0").getOutputResult(table(), 3.0), 0.0);
  }
}
