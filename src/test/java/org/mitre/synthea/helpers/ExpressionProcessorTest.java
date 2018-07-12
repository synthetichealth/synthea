package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class ExpressionProcessorTest {

  @Before
  public void setup() {
    ExpressionProcessor.initialize();
  }
  
  @Test
  public void testBasic() {
    String exp = "10 + 3";
    Object result = ExpressionProcessor.evaluate(exp, null, 0L);
    assertEquals(13, result);
  } 
  
  
  @Test
  public void testWithAttributes() {
    Person p = new Person(0L);
    p.attributes.put("age", 26);
    String exp = "#{age} / 2 + 7";
    Object result = ExpressionProcessor.evaluate(exp, p, 0L);
    assertTrue(result instanceof Number);
    assertEquals(20, ((Number)result).longValue());
  } 
}
