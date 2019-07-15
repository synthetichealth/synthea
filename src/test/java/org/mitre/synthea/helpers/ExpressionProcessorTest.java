package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class ExpressionProcessorTest {
  @Test
  public void testBasic() {
    String exp = "10 + 3";
    Object result = ExpressionProcessor.evaluate(exp, null, 0L);
    assertEquals(13, result);
    
    exp = "25 / 2";
    result = ExpressionProcessor.evaluate(exp, null, 0L);
    assertEquals(12.5, result);
    
    exp = "2 * (10 + 6) + 4 + 3 * ((10 / 2) * (9 / 3))";
    result = ExpressionProcessor.evaluate(exp, null, 0L);
    assertEquals(81L, result);
  } 
  
  @Test
  public void testWithPersonAttributes() {
    Person p = new Person(0L);
    p.attributes.put("age", 26);
    String exp = "#{age} / 2 + 7";
    Object result = ExpressionProcessor.evaluate(exp, p, 0L);
    assertTrue(result instanceof Number);
    assertEquals(20L, ((Number)result).longValue());
  }
}
