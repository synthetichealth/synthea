package org.mitre.synthea.helpers;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBException;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.Library;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;

public class ExpressionProcessor {
  private static final String LIBRARY_NAME = "Synthea";
  private static final ModelManager modelManager = new ModelManager();
  private static final Map<String, VitalSign> vitalSignCache = new HashMap<String, VitalSign>();
  private final LibraryManager libraryManager = new LibraryManager(modelManager);
  private String expression;
  private Library library;
  private Context context;
  private String elm;
  private Map<String,String> paramTypeMap;
  private BiMap<String,String> cqlParamMap;

  /**
   * Evaluate the given expression, within the context of the given Person and timestamp.
   * The given expression will be wrapped in CQL and evaluated to produce, ideally, a Number.
   * Examples:
   *  - In: "10 + 3", Out: (Integer)13
   *  - In: "25 / 2", Out: (Double)12.5
   *  - In: "#{age} / 3", Person{age = 27}, Out: (Integer) 9
   * 
   * @param expression "CQL-lite" expression, with attribute references wrapped in "#{ attr }"
   * @param person Person to evaluate expression against.
   * @param time Timestamp
   * @return result of the expression
   */

  private String cqlToElm(String cql) {
    CqlTranslator translator = CqlTranslator.fromText(cql, modelManager, libraryManager);
    
    if (translator.getErrors().size() > 0) {
      throw translator.getErrors().get(0);
    }

    String elm = translator.toXml();

    if (translator.getErrors().size() > 0) {
      throw translator.getErrors().get(0);
    }

    return elm;
  }
  
  /**
   * ExpressionProcessor convenience constructor when all parameters are Decimals.
   * @param expression Expression to evaluate for each future set of parameters.
   */
  public ExpressionProcessor(String expression) {
    this(expression, new HashMap<String,String>());
  }
  
  /**
   * ExpressionProcessor constructor.
   * @param expression Expression to evaluate for each future set of parameters.
   * @param paramTypeMap Map of parameter names to their corresponding CQL types.
   */
  public ExpressionProcessor(String expression, Map<String,String> paramTypeMap) {
    this.cqlParamMap = HashBiMap.create();
    this.paramTypeMap = paramTypeMap;
    
    String cleanExpression = replaceParameters(expression);
    String wrappedExpression = convertParameterizedExpressionToCql(cleanExpression);
    
    // Compile our constructed CQL expression into elm once for execution
    this.elm = cqlToElm(wrappedExpression);
    try {
      this.library = CqlLibraryReader.read(new ByteArrayInputStream(
          elm.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException | JAXBException ex) {
      throw new RuntimeException(ex);
    }
    this.context = new Context(library);
    this.expression = expression;
  }
  
  /**
   * Returns the expression associated with this expression processor.
   * @return expression
   */
  public String getExpression() {
    return expression;
  }
  
  /**
   * Returns a list of parameters in the expression associated with this processor.
   * @return list of parameters
   */
  public Set<String> getParamNames() {
    return cqlParamMap.keySet();
  }
  
  /**
   * Returns a map of parameter names to their CQL types as provided to the constructor.
   * @return map of parameter names to CQL type names
   */
  public Map<String,String> getParamTypes() {
    return paramTypeMap;
  }
  
  /**
   * Evaluates the expression with the given numeric parameters, returning the result
   * as a BigDecimal.
   * @param params numeric parameters as a map of variable names to values
   * @return evaluation result
   */
  public BigDecimal evaluateNumeric(Map<String,Object> params) {
    return (BigDecimal) evaluate(params);
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
  public static Double getPersonValue(String param, Person person, long time) {
    return getPersonValue(param, person, time, null);
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
  public static Double getPersonValue(String param, Person person, long time, String expression) {
    
    // Treat "age" as a special case. In expressions, age is represented in decimal years
    if (param.equals("age")) {
      return person.ageInDecimalYears(time);
    }
    
    // If this param is in the cache, check if we have a VitalSign or not
    org.mitre.synthea.world.concepts.VitalSign vs = null;
    
    if (vitalSignCache.containsKey(param)) {
      vs = vitalSignCache.get(param);
    } else {
      try {
        vs = org.mitre.synthea.world.concepts.VitalSign.fromString(param);
        
        // Take note that this parameter is a VitalSign so we don't have to repeatedly
        // call fromString, which can get expensive
        vitalSignCache.put(param, vs);
      } catch (IllegalArgumentException ex) {
        // Take note that this parameter is not a VitalSign so we don't have to repeatedly
        // call fromString, which can get expensive
        vitalSignCache.put(param, null);
      }
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
        if (expression != null) {
          throw new IllegalArgumentException("Unable to map person attribute \""
              + param + "\" in expression \"" + expression
              + "\": Attribute value is not a number.");
        } else {
          throw new IllegalArgumentException("Unable to map person attribute \""
              + param + "\": Attribute value is not a number.");
        }
      }
    } else {
      if (expression != null) {
        throw new IllegalArgumentException("Unable to map \"" + param
            + "\" in expression \"" + expression
            + "\": Invalid person attribute or vital sign.");
      } else {
        throw new IllegalArgumentException("Unable to map \""
            + param + "\": Invalid person attribute or vital sign.");
      }
    }
  }
  
  /**
   * Evaluates the provided expression given the simulation results.
   * @param results table of simulation results
   * @param leadTime lead time in seconds before using table values
   * @return BigDecimal result value
   */
  public BigDecimal evaluateFromSimResults(MultiTable results, double leadTime) {
    
    // Create our map of expression parameters
    Map<String,Object> expParams = new HashMap<String,Object>();
    
    // Get the index past the lead time to start getting values
    int leadTimeIdx = Arrays.binarySearch(results.getTimePoints(), leadTime);
    
    // Add all model outputs to the expression parameter map as lists of decimals
    for (String param : getParamNames()) {
      List<BigDecimal> paramList = new ArrayList<BigDecimal>(results.getRowCount());
      
      Column col = results.getColumn(param);
      if (col == null) {
        throw new IllegalArgumentException("Invalid model parameter \"" + param
            + "\" in expression \"" + expression + "\".");
      }
      
      for (int i = leadTimeIdx; i < col.getRowCount(); i++) {
        paramList.add(new BigDecimal(col.getValue(i)));
      }
      expParams.put(param, paramList);
    }
    
    // Evaluate the expression
    return evaluateNumeric(expParams);
  }
  
  /**
   * Evaluates the expression with parameters derived from the given Person object.
   * @param person Person instance to get parameters from
   * @param time simulation time
   * @return evaluation result
   */
  public Object evaluate(Person person, long time) {
    Map<String,Object> params = new HashMap<String,Object>();
    
    for (String paramName : getParamNames()) {
      params.put(paramName, new BigDecimal(getPersonValue(paramName, person, time, expression)));
    }
    
    return evaluate(params);
  }
  
  /**
   * Evaluates the expression with the given parameters.
   * @param params parameters as a map of variable names to values
   * @return evaluation result
   */
  public Object evaluate(Map<String,Object> params) {
    // Keep track to make sure all parameters are set
    Set<String> setParams = new HashSet<String>();
    for (Entry<String,Object> entry : params.entrySet()) {
      // Set the CQL compatible parameter name in the context
      context.setParameter(null, cqlParamMap.get(entry.getKey()), entry.getValue());
      setParams.add(entry.getKey());
    }
    
    Set<String> missing = Sets.difference(cqlParamMap.keySet(), setParams);
    Set<String> extra = Sets.difference(setParams, cqlParamMap.keySet());
    
    if (missing.size() > 0) {
      throw new IllegalArgumentException("Missing parameter(s): " + String.join(", ", missing)
      + " for expression \"" + expression + "\"");
    }
    if (extra.size() > 0) {
      Logger.getLogger(ExpressionProcessor.class.getName()).log(Level.WARNING,
              "unused parameter(s) provided for expression \"{0}\": {1}",
              new Object[]{expression, String.join(", ",extra)});
    }
    
    Object retVal = null;

    for (ExpressionDef statement : library.getStatements().getDef()) {
      retVal = statement.evaluate(context);
    }

    try {
      return retVal;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private String replaceParameters(String expression) {
    String cleanExpression = expression;
    
    // identify the parameters that are used
    // we identify parameters with #{attr}
    Pattern pattern = Pattern.compile("#\\{.+?\\}");
    Matcher matcher = pattern.matcher(expression);

    while (matcher.find()) {
      String key = matcher.group();
      String param = key.substring(2, key.length() - 1).trim(); // lop off #{ and }
      String cqlParam = param.replace(" ", "_");
      
      // Add the bi-directional mapping from params to CQL compatible params
      cqlParamMap.put(param, cqlParam);

      // clean up the expression so we can plug it in later
      cleanExpression = cleanExpression.replace(key, cqlParam);
    }
    
    return cleanExpression;
  }
  
  private String convertParameterizedExpressionToCql(String expression) {
    StringBuilder wrappedExpression = new StringBuilder();

    wrappedExpression.append("library " + LIBRARY_NAME + " version '1'\n");

    for (Entry<String,String> paramEntry : cqlParamMap.entrySet()) {
      wrappedExpression
        .append("\nparameter ")
        .append(paramEntry.getValue())
        .append(" ")
        .append(paramTypeMap.getOrDefault(paramEntry.getKey(), "Decimal"));
    }

    wrappedExpression.append("\n\ncontext Patient\n\ndefine result: ");
    wrappedExpression.append(expression);
    
    return wrappedExpression.toString();
  }
}
