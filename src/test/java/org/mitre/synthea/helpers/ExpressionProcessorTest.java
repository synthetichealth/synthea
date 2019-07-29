package org.mitre.synthea.helpers;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBException;
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
  
  @Test
  public void testInstance() {
    Map<String,String> typeMap = new HashMap();
    typeMap.put("var_one", "Decimal");
    typeMap.put("var_two", "Decimal");
    typeMap.put("var_three", "List<Decimal>");
    ExpressionProcessor expProcessor = new ExpressionProcessor("#{var_one} * (#{var_two} + 3.0) + Max(#{var_three})", typeMap);
    
    Map<String,Object> params = new HashMap();
    
    List<BigDecimal> var_three = new ArrayList();
    var_three.add(new BigDecimal(3.0));
    var_three.add(new BigDecimal(1.2342));
    var_three.add(new BigDecimal(5.25512));
    var_three.add(new BigDecimal(12.0));
    
    params.put("var_one", new BigDecimal(2.0));
    params.put("var_two", new BigDecimal(3.0));
    params.put("var_three", var_three);
    
    double result = expProcessor.evaluateNumeric(params).doubleValue();
    
    assertEquals(24.0, result, 0.0001);
  }
  
  @Test
  public void testWithSpaces() {
    Map<String,String> typeMap = new HashMap();
    typeMap.put("var one", "Decimal");
    typeMap.put("var two", "Decimal");
    ExpressionProcessor expProcessor = new ExpressionProcessor("#{var one} * (#{var two} + 3.0)", typeMap);
    
    Map<String,Object> params = new HashMap();
    
    params.put("var one", new BigDecimal(2.0));
    params.put("var two", new BigDecimal(3.0));
    
    double result = expProcessor.evaluateNumeric(params).doubleValue();
    
    assertEquals(12.0, result, 0.0001);
  }
}
