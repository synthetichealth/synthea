package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class ExpressionProcessorTest {
  @Test
  public void testBasic() {
    ExpressionProcessor expProcessor = new ExpressionProcessor("10 + 3");
    Number result = (Number) expProcessor.evaluate(null, 0L);
    assertEquals(13, result.intValue());
    
    expProcessor = new ExpressionProcessor("25 / 2");
    result = (Number) expProcessor.evaluate(null, 0L);
    assertEquals(12.5, result.doubleValue(), 0.001);
    
    expProcessor = new ExpressionProcessor("2 * (10 + 6) + 4 + 3 * ((10 / 2) * (9 / 3))");
    result = (Number) expProcessor.evaluate(null, 0L);
    assertEquals(81L, result.doubleValue(), 0.001);
  } 
  
  @Test
  public void testWithPersonAttributes() {
    Person p = new Person(0L);
    p.attributes.put("age_attr", 26);
    String exp = "#{age_attr} / 2 + 7";
    ExpressionProcessor expProcessor = new ExpressionProcessor(exp);
    Number result = (Number) expProcessor.evaluate(p, 0L);
    assertNotNull(result);
    assertEquals(20L, result.longValue());
  }
  
  @Test
  public void testInstance() {
    Map<String,String> typeMap = new HashMap<String,String>();
    typeMap.put("var_one", "Decimal");
    typeMap.put("var_two", "Decimal");
    typeMap.put("var_three", "List<Decimal>");
    
    List<BigDecimal> varThree = new ArrayList<BigDecimal>();
    varThree.add(new BigDecimal(3.0));
    varThree.add(new BigDecimal(1.2342));
    varThree.add(new BigDecimal(5.25512));
    varThree.add(new BigDecimal(12.0));
    
    Map<String,Object> params = new HashMap<String,Object>();
    
    params.put("var_one", new BigDecimal(2.0));
    params.put("var_two", new BigDecimal(3.0));
    params.put("var_three", varThree);
    
    ExpressionProcessor expProcessor = new ExpressionProcessor(
        "#{var_one} * (#{var_two} + 3.0) + Max(#{var_three})", typeMap);
    
    double result = expProcessor.evaluateNumeric(params).doubleValue();
    
    assertEquals(24.0, result, 0.0001);
  }
  
  @Test
  public void testWithSpaces() {
    Map<String,String> typeMap = new HashMap<String,String>();
    typeMap.put("var one", "Decimal");
    typeMap.put("var two", "Decimal");
    ExpressionProcessor expProcessor = new ExpressionProcessor(
        "#{var one} * (#{var two} + 3.0)", typeMap);
    
    Map<String,Object> params = new HashMap<String,Object>();
    
    params.put("var one", new BigDecimal(2.0));
    params.put("var two", new BigDecimal(3.0));
    
    double result = expProcessor.evaluateNumeric(params).doubleValue();
    
    assertEquals(12.0, result, 0.0001);
  }
  
  @Test
  public void testStringInput() {
    
    Map<String,Object> params = new HashMap<String,Object>();
    
    params.put("var_one", new BigDecimal(2.0));
    params.put("var_two", "male");
    
    ExpressionProcessor expProcessor = new ExpressionProcessor(
        "if #{var_two} = 'male' then 1.0 else #{var_one}*2.0");
    
    BigDecimal result = expProcessor.evaluateNumeric(params);
    
    assertNotNull(result);
    assertEquals(1.0, result.doubleValue(), 0.0001);
    
    params.put("var_two", "female");
    
    result = expProcessor.evaluateNumeric(params);
    
    assertNotNull(result);
    assertEquals(4.0, result.doubleValue(), 0.0001);
    
  }
}
