package org.mitre.synthea.helpers.physiology;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cqframework.cql.cql2elm.CqlSemanticException;
import org.mitre.synthea.helpers.ExpressionProcessor;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;

/** Class for handling simulation inputs and outputs. **/
public class IoMapper {
  private IoType type;
  private String from;
  private String to;
  private String fromList;
  private String fromExp;
  private double variance;
  private VitalSign vitalSign;
  
  // ExpressionProcessor instances are not thread safe, so we need
  // to have a separate processor for each thread
  private ThreadLocal<ExpressionProcessor> threadExpProcessor
      = new ThreadLocal<ExpressionProcessor>();
  private PreGenerator preGenerator;
  
  public IoMapper() {}
  
  /**
   * Copy constructor.
   * @param other other IoMapper instance
   */
  public IoMapper(IoMapper other) {
    type = other.type;
    from = other.from;
    fromList = other.fromList;
    to = other.to;
    fromExp = other.fromExp;
    threadExpProcessor = other.threadExpProcessor;
  }
  
  public enum IoType {
    @SerializedName("Attribute") ATTRIBUTE, 
    @SerializedName("Vital Sign") VITAL_SIGN
  }
  
  public IoType getType() {
    return type;
  }

  public void setType(IoType type) {
    this.type = type;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public String getTo() {
    return to;
  }

  public void setTo(String to) {
    this.to = to;
  }

  public String getFromList() {
    return fromList;
  }

  public void setFromList(String fromList) {
    this.fromList = fromList;
  }

  public String getFromExp() {
    return fromExp;
  }

  public void setFromExp(String fromExp) {
    this.fromExp = fromExp;
  }

  public double getVariance() {
    return variance;
  }

  public void setVariance(double varianceThreshold) {
    this.variance = varianceThreshold;
  }

  public PreGenerator getPreGenerator() {
    return preGenerator;
  }

  public void setPreGenerator(PreGenerator preGenerator) {
    this.preGenerator = preGenerator;
  }
  
  /**
   * Retrieves the VitalSign corresponding to this IoMapper's "to" field.
   * @return target VitalSign Enum
   */
  public VitalSign getVitalSignTarget() {
    // if this is a VitalSign, set the VitalSign value
    if (vitalSign == null && type == IoType.VITAL_SIGN) {
      vitalSign = VitalSign.fromString(this.to);
    }
    return vitalSign;
  }
  
  /**
   * Initializes the expression processor if needed with all inputs
   * set as the default type (Decimal).
   */
  public void initialize() {
    initialize(new HashMap<String,String>());
  }

  /**
   * Initializes the expression processor for each thread if needed.
   * @param paramTypes map of parameters to their CQL types
   */
  public void initialize(Map<String, String> paramTypes) {
    try {
      if (threadExpProcessor.get() == null && fromExp != null && !"".equals(fromExp)) {
        threadExpProcessor.set(new ExpressionProcessor(fromExp, paramTypes));
      }
    } catch (CqlSemanticException e) {
      throw new RuntimeException(e);
    }
    
  }
  
  /**
   * Populates model input parameters from the given person object.
   * @param person Person instance to get parameter values from
   * @param time Synthea simulation time
   * @param modelInputs map of input parameters to be populated
   */
  public double toModelInputs(Person person, long time, Map<String,Double> modelInputs) {
    double resultValue;
    
    ExpressionProcessor expProcessor = threadExpProcessor.get();
    
    // Evaluate the expression if one is provided
    if (expProcessor != null) {
      Map<String,Object> expParams = new HashMap<String,Object>();
      
      // Add all patient parameters to the expression parameter map
      for (String param : expProcessor.getParamNames()) {
        expParams.put(param, new BigDecimal(getPersonValue(param, person, time)));
      }
      
      // All physiology inputs should evaluate to numeric parameters
      BigDecimal result = expProcessor.evaluateNumeric(expParams);
      resultValue = result.doubleValue();
    } else if (fromList != null) {
      throw new IllegalArgumentException(
          "Cannot map lists from person attributes / vital signs to model parameters");
    } else {
      resultValue = getPersonValue(from, person, time);
    }
    
    modelInputs.put(to, resultValue);
    return resultValue;
  }
  
  /**
   * Evaluates the provided expression given the simulation results.
   * @param results simulation results
   * @param leadTime lead time in seconds before using simulation values
   * @return BigDecimal result value
   */
  private BigDecimal getExpressionResult(MultiTable results, double leadTime) {
    ExpressionProcessor expProcessor = threadExpProcessor.get();
    
    if (expProcessor == null) {
      throw new RuntimeException("No expression to process");
    }
    
    // Create our map of expression parameters
    Map<String,Object> expParams = new HashMap<String,Object>();
    
    // Get the index past the lead time to start getting values
    int leadTimeIdx = Arrays.binarySearch(results.getTimePoints(), leadTime);
    
    // Add all model outputs to the expression parameter map as lists of decimals
    for (String param : expProcessor.getParamNames()) {
      List<BigDecimal> paramList = new ArrayList<BigDecimal>(results.getRowCount());
      
      Column col = results.getColumn(param);
      if (col == null) {
        throw new IllegalArgumentException("Invalid model parameter \"" + param
            + "\" in expression \"" + from
            + "\" cannot be mapped to patient attribute \"" + to + "\"");
      }
      
      for (int i = leadTimeIdx; i < col.getRowCount(); i++) {
        paramList.add(new BigDecimal(col.getValue(i)));
      }
      expParams.put(param, paramList);
    }
    
    // Evaluate the expression
    return expProcessor.evaluateNumeric(expParams);
  }
  
  /**
   * Retrieves the numeric result for this IoMapper from simulation output.
   * @param results simulation results
   * @param leadTime lead time in seconds before using simulation values
   * @return double value or List of Double values
   */
  public Object getOutputResult(MultiTable results, double leadTime) {
    ExpressionProcessor expProcessor = threadExpProcessor.get();
    
    if (expProcessor != null) {
      // Evaluate the expression and return the result
      return getExpressionResult(results, leadTime).doubleValue();
      
    } else if (fromList != null) {
      // Get the column for the requested list
      Column col = results.getColumn(fromList);
      if (col == null) {
        throw new IllegalArgumentException("Invalid model parameter \"" + fromList
            + "\" cannot be mapped to patient value \"" + to + "\"");
      }
      
      // Make it an ArrayList for more natural usage throughout the rest of the application
      List<Double> valueList = new ArrayList<Double>();
      col.iterator().forEachRemaining(valueList::add);
      
      // Return the list
      return valueList;
    } else {
      // Result is the last value of the requested parameter
      int lastRow = results.getRowCount() - 1;
      Column col = results.getColumn(from);
      if (col == null) {
        throw new IllegalArgumentException("Invalid model parameter \"" + from
            + "\" cannot be mapped to patient value \"" + to + "\"");
      }
      return col.getValue(lastRow);
    }
  }
  
  /** 
   * Retrieve the desired value from a Person model. Check for a VitalSign first and
   * then an attribute if there is no VitalSign by the provided name.
   * Throws an IllegalArgumentException if neither exists.
   * @param param name of the VitalSign or attribute to retrieve from the Person
   * @param person Person instance to get the parameter from
   * @param time current time
   * @return value
   */
  private Double getPersonValue(String param, Person person, long time) {
    
    // Treat "age" as a special case. In expressions, age is represented in decimal years
    if (param.equals("age")) {
      return person.ageInDecimalYears(time);
    }
    
    org.mitre.synthea.world.concepts.VitalSign vs = null;
    try {
      vs = org.mitre.synthea.world.concepts.VitalSign.fromString(param);
    } catch (IllegalArgumentException ex) {
      // Ignore since it actually may not be a vital sign
    }

    if (vs != null) {
      return person.getVitalSign(vs, time);
    } else if (person.attributes.containsKey(param)) {
      Object value = person.attributes.get(param);
      
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
        
      } else if (value instanceof Boolean) {
        return (Boolean) value ? 1.0 : 0.0;
        
      } else {
        if (threadExpProcessor.get() != null) {
          throw new IllegalArgumentException("Unable to map person attribute \""
              + param + "\" in expression \"" + fromExp + "\" for parameter \""
              + to + "\": Attribute value is not a number.");
        } else {
          throw new IllegalArgumentException("Unable to map person attribute \""
              + param + "\" to parameter \"" + to + "\": Attribute value is not a number.");
        }
      }
    } else {
      if (threadExpProcessor.get() != null) {
        throw new IllegalArgumentException("Unable to map \"" + param
            + "\" in expression \"" + fromExp + "\" for parameter \"" + to
            + "\": Invalid person attribute or vital sign.");
      } else {
        throw new IllegalArgumentException("Unable to map \""
            + param + "\" to parameter \"" + to
            + "\": Invalid person attribute or vital sign.");
      }
    }
  }
}