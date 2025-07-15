package org.mitre.synthea.helpers.physiology;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.cqframework.cql.cql2elm.CqlSemanticException;
import org.mitre.synthea.helpers.ExpressionProcessor;
import org.mitre.synthea.helpers.TimeSeriesData;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;

/** Class for handling simulation inputs and outputs. **/
public class IoMapper implements Serializable {
  /** Type of input/output mapping. */
  private IoType type;
  /** Source field for mapping. */
  private String from;
  /** Target field for mapping. */
  private String to;
  /** Source list for mapping. */
  private String fromList;
  /** Expression for mapping. */
  private String fromExp;
  /** Variance threshold for mapping. */
  private double variance;
  /** Vital sign associated with the mapping. */
  private VitalSign vitalSign;
  /**
   * ExpressionProcessor instances are not thread safe, so we need
   * to have a separate processor for each thread
   */
  private transient ThreadLocal<ExpressionProcessor> threadExpProcessor;
  /**
   * PreGenerator instance for pre-simulation outputs.
   */
  private PreGenerator preGenerator;

  private ExpressionProcessor getThreadExpProcessor() {
    if (threadExpProcessor == null) {
      threadExpProcessor = new ThreadLocal<ExpressionProcessor>();
    }
    return threadExpProcessor.get();
  }

  private void setThreadExpProcessor(ExpressionProcessor exp) {
    if (threadExpProcessor == null) {
      threadExpProcessor = new ThreadLocal<ExpressionProcessor>();
    }
    threadExpProcessor.set(exp);
  }

  /**
   * Default constructor for IoMapper.
   */
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
    setThreadExpProcessor(other.getThreadExpProcessor());
  }

  /**
   * Represents the type of input/output mapping.
   */
  public enum IoType {
    /** Attribute type mapping. */
    @SerializedName("Attribute") ATTRIBUTE,
    /** Vital sign type mapping. */
    @SerializedName("Vital Sign") VITAL_SIGN
  }

  /**
   * Retrieves the type of this IoMapper.
   * @return The IoType of this IoMapper.
   */
  public IoType getType() {
    return type;
  }

  /**
   * Sets the type of this IoMapper.
   * @param type The IoType to set.
   */
  public void setType(IoType type) {
    this.type = type;
  }

  /**
   * Retrieves the "from" field of this IoMapper.
   * @return The "from" field value.
   */
  public String getFrom() {
    return from;
  }

  /**
   * Sets the "from" field of this IoMapper.
   * @param from The "from" field value to set.
   */
  public void setFrom(String from) {
    this.from = from;
  }

  /**
   * Retrieves the "to" field of this IoMapper.
   * @return The "to" field value.
   */
  public String getTo() {
    return to;
  }

  /**
   * Sets the "to" field of this IoMapper.
   * @param to The "to" field value to set.
   */
  public void setTo(String to) {
    this.to = to;
  }

  /**
   * Retrieves the "fromList" field of this IoMapper.
   * @return The "fromList" field value.
   */
  public String getFromList() {
    return fromList;
  }

  /**
   * Sets the "fromList" field of this IoMapper.
   * @param fromList The "fromList" field value to set.
   */
  public void setFromList(String fromList) {
    this.fromList = fromList;
  }

  /**
   * Gets the expression for the 'from' field.
   * @return The 'from' expression.
   */
  public String getFromExp() {
    return fromExp;
  }

  /**
   * Sets the expression for the 'from' field.
   * @param fromExp The 'from' expression to set.
   */
  public void setFromExp(String fromExp) {
    this.fromExp = fromExp;
  }

  /**
   * Gets the variance threshold.
   * @return The variance threshold.
   */
  public double getVariance() {
    return variance;
  }

  /**
   * Sets the variance threshold.
   * @param varianceThreshold The variance threshold to set.
   */
  public void setVariance(double varianceThreshold) {
    this.variance = varianceThreshold;
  }

  /**
   * Gets the pre-generator instance.
   * @return The pre-generator instance.
   */
  public PreGenerator getPreGenerator() {
    return preGenerator;
  }

  /**
   * Sets the pre-generator instance.
   * @param preGenerator The pre-generator instance to set.
   */
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
      if (getThreadExpProcessor() == null && fromExp != null && !"".equals(fromExp)) {
        setThreadExpProcessor(new ExpressionProcessor(fromExp, paramTypes));
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
   * @return double value of the mapped parameter
   */
  public double toModelInputs(Person person, long time, Map<String,Double> modelInputs) {
    double resultValue;

    ExpressionProcessor expProcessor = getThreadExpProcessor();

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
      } else {
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
    ExpressionProcessor expProcessor = getThreadExpProcessor();

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

      // Make it a TimeSeriesData object, which is just an ArrayList with sample
      // frequency information
      TimeSeriesData seriesData = new TimeSeriesData(results.getRowCount(),
          results.getTimePoint(1) - results.getTimePoint(0));
      col.iterator().forEachRemaining(seriesData::addValue);

      // Return the sampled values
      return seriesData;
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