package org.mitre.synthea.helpers;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
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
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;

public class ExpressionProcessor implements Cloneable {
  private static final ModelManager MODEL_MANAGER = new ModelManager();
  private static final LibraryManager LIBRARY_MANAGER = new LibraryManager(MODEL_MANAGER);
  private static final String LIBRARY_NAME = "Synthea";
  String expression;
  Library library;
  Context context;
  String elm;
  Map<String,String> paramTypeMap;
  BiMap<String,String> cqlParamMap;

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

  private static String cqlToElm(String cql) {
    CqlTranslator translator = CqlTranslator.fromText(cql, MODEL_MANAGER, LIBRARY_MANAGER);
    
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
    // System.out.println("Wrapped Expression:");
    // System.out.println(wrappedExpression);
    
    this.elm = cqlToElm(wrappedExpression);
    try {
      this.library = CqlLibraryReader.read(new ByteArrayInputStream(
          elm.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException | JAXBException ex) {
      throw new RuntimeException(ex);
    }
    this.context = new Context(library);
  }
  
  @Override
  public ExpressionProcessor clone() {
    return new ExpressionProcessor(expression, paramTypeMap);
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
  private Object getPersonValue(String param, Person person, long time) {
    org.mitre.synthea.world.concepts.VitalSign vs = null;
    try {
      vs = org.mitre.synthea.world.concepts.VitalSign.fromString(param);
    } catch (IllegalArgumentException ex) {
      // Ignore since it actually may not be a vital sign
    }

    if (vs != null) {
      return new BigDecimal(person.getVitalSign(vs, time));
    } else if (person.attributes.containsKey(param)) {
      Object value = person.attributes.get(param);
      
      if (value instanceof Number) {
        return new BigDecimal(((Number) value).doubleValue());
        
      } else if (value instanceof Boolean) {
        return new BigDecimal((Boolean) value ? 1 : 0);
        
      } else {
        throw new IllegalArgumentException("Unable to map person attribute \""
            + param + "\" in expression \"" + expression + "\": Attribute value is not a number.");
      }
    } else {
      throw new IllegalArgumentException("Unable to map \"" + param
          + "\" in expression \"" + expression + "\": Invalid person attribute or vital sign.");
    }
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
      params.put(paramName, getPersonValue(paramName, person, time));
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
      // System.out.println("Evaluating library statement: " + statement.getName());
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
