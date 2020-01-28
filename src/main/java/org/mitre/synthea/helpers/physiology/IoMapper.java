package org.mitre.synthea.helpers.physiology;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        expParams.put(param, ExpressionProcessor
            .getPersonValue(param, person, time, expProcessor.getExpression()));
      }
      
      // All physiology inputs should evaluate to numeric parameters
      BigDecimal result = expProcessor.evaluateNumeric(expParams);
      resultValue = result.doubleValue();
    } else if (fromList != null) {
      throw new IllegalArgumentException(
          "Cannot map lists from person attributes / vital signs to model parameters");
    } else {
      Object personValue = ExpressionProcessor.getPersonValue(from, person, time, null);
      if (personValue instanceof Number) {
        resultValue = ((Number) personValue).doubleValue();
      }
      else {
        throw new IllegalArgumentException("Non-numeric attribute: \"" + from + "\"");
      }
    }
    
    modelInputs.put(to, resultValue);
    return resultValue;
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
      return expProcessor.evaluateFromSimResults(results, leadTime).doubleValue();
      
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
}