package org.mitre.synthea.helpers.physiology;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.simulator.math.odes.MultiTable;

public class IoMapperTest {
  
  IoMapper testMapper;
  Person person;
  
  @Before
  public void setup() {
    testMapper = new IoMapper();
    person = new Person(0);
  }
  
  @Test
  public void ioMapperInputTest() {
    IoMapper testMapper = new IoMapper();
    
    testMapper.setFrom("test attribute");
    testMapper.setTo("model_param");
    testMapper.setType(IoMapper.IoType.ATTRIBUTE);
    testMapper.setVariance(0);
    
    person.setVitalSign(VitalSign.BMI, 22.0);
    
    Map<String,Double> modelInputs = new HashMap<String,Double>();
    
    // Attribute isn't set so this should give us an exception
    try {
      testMapper.toModelInputs(person, 0, modelInputs);
    } catch (IllegalArgumentException e) {
      assertEquals("Unable to map \"test attribute\": Invalid person "
          + "attribute or vital sign.", e.getMessage());
    }
    
    // set the attribute and try again
    person.attributes.put("test attribute", 1.0);
    testMapper.toModelInputs(person, 0, modelInputs);
    
    // input param should be set to the same value
    assertEquals(1.0, (double) modelInputs.get("model_param"), 0);
    
    // if it's a boolean, it should be converted to a double 0.0 for false and 1.0 for true
    person.attributes.put("test attribute", true);
    assertEquals(1.0, (double) modelInputs.get("model_param"), 0.000001);
    
    // If it's a non-numeric value, it should throw an exception
    person.attributes.put("test attribute", "i'm not a number");
    try {
      testMapper.toModelInputs(person, 0, modelInputs);
    } catch (IllegalArgumentException e) {
      assertEquals("Non-numeric attribute: \"test attribute\"", e.getMessage());
    }
    
    // Now use an expression with attributes and VitalSigns instead of a direct mapping
    testMapper.setFrom(null);
    testMapper.setFromExp("#{test attribute} * #{BMI} + #{attr2}");
    
    // Set the original attribute back to a valid value
    person.attributes.put("test attribute", 1.0);
    
    // Initialize the expression processor
    testMapper.initialize();
    
    // 'attr2' isn't set so this should give us an exception
    try {
      testMapper.toModelInputs(person, 0, modelInputs);
    } catch (IllegalArgumentException e) {
      assertEquals("Unable to map \"attr2\" in expression \"#{test attribute} * #{BMI} "
          + "+ #{attr2}\": Invalid person attribute or vital sign.", e.getMessage());
    }
    
    // Again, if it's a non numeric value, it should also throw an exception
    person.attributes.put("attr2", "not a number again");
    try {
      testMapper.toModelInputs(person, 0, modelInputs);
    } catch (IllegalArgumentException e) {
      assertEquals("Unable to map person attribute \"attr2\" in expression "
          + "\"#{test attribute} * #{BMI} + #{attr2}\": "
          + "Attribute value is not a number.", e.getMessage());
    }
    
    // now set 'attr2' and try again
    person.attributes.put("attr2", 1.0);
    testMapper.toModelInputs(person, 0, modelInputs);
    
    // input param should be the result of the expression
    assertEquals(23.0, (double) modelInputs.get("model_param"), 0.000001);
  }
  
  @Test
  public void ioMapperOutputTest() {
    
    testMapper.setFrom("invalid");
    testMapper.setTo("test attribute");
    testMapper.setType(IoMapper.IoType.ATTRIBUTE);
    testMapper.setVariance(0);
    
    // Add some mock model output values
    double[] timePoints = {0,1,2,3};
    double[][] mockData = new double[4][1];
    
    mockData[0][0] = 0.0;
    mockData[1][0] = 1.0;
    mockData[2][0] = 2.0;
    mockData[3][0] = 3.0;
    
    String[] params = {"model_output"};

    MultiTable mockResults = new MultiTable(timePoints, mockData, params);
    
    // Invalid parameter name so we should get an exception
    try {
      testMapper.getOutputResult(mockResults, 0);
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid model parameter \"invalid\" cannot be mapped to "
          + "patient value \"test attribute\"", e.getMessage());
    }
    
    // set to the correct value
    testMapper.setFrom("model_output");
    
    // Mapper should set the final value
    assertEquals(3.0, (double) testMapper.getOutputResult(mockResults, 0), 0);
    
    // Test setting an entire list of values
    testMapper.setFrom(null);
    testMapper.setFromList("invalid");
    
    // Invalid parameter name so we should get an exception
    try {
      testMapper.getOutputResult(mockResults, 0);
    } catch (IllegalArgumentException e) {
      assertEquals("Invalid model parameter \"invalid\" cannot be mapped to "
          + "patient value \"test attribute\"", e.getMessage());
    }
    
    testMapper.setFromList("model_output");
    
    // Mapper should provide a list of all values
    List<Double> expectedValues = Arrays.asList(0.0, 1.0, 2.0, 3.0);
    assertEquals(expectedValues, testMapper.getOutputResult(mockResults, 0));
    
    // Test an expression
    testMapper.setFromList(null);
    testMapper.setFromExp("Sum(#{model_output})");
    
    Map<String, String> paramTypes = new HashMap<String, String>();
    paramTypes.put("model_output", "List<Decimal>");
    testMapper.initialize(paramTypes);
    
    // Should result in the sum of all values for 'model_output'
    assertEquals(6.0, (double) testMapper.getOutputResult(mockResults, 0), 0.0001);
    
  }
}
