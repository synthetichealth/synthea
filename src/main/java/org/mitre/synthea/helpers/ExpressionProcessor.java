package org.mitre.synthea.helpers;

import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;

public class ExpressionProcessor {
  private static final ModelManager MODEL_MANAGER = new ModelManager();
  private static final LibraryManager LIBRARY_MANAGER = new LibraryManager(MODEL_MANAGER);
  private static final String LIBRARY_NAME = "Synthea";
  String expression;
  Library library;
  Context context;
  String elm;
  Map<String,String> paramTypeMap;
  List<String> paramNames;

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
  public static Object evaluate(String expression, Person person, long time) {
    try {
      Map<String,String> attributes = new HashMap<>();
      
      String cleanExpression = replaceAttributes(expression, person, time, attributes);
      
      String wrappedExpression = convertExpressionToCQL(cleanExpression, attributes);

      // try and parse it as CQL.
      String elm = cqlToElm(wrappedExpression);

      Library library = CqlLibraryReader
          .read(new ByteArrayInputStream(elm.getBytes(StandardCharsets.UTF_8)));

      Context context = new Context(library);

      Object retVal = null;

      for (ExpressionDef statement : library.getStatements().getDef()) {
        retVal = statement.evaluate(context);
      }

      return wrap(retVal);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String replaceAttributes(String expression, Person person, long time,
      Map<String, String> attributes) {
    String cleanExpression = expression;
    
    // identify the attributes that are used
    // we identify person attributes with #{attr}
    // TODO: how to handle date logic? ex a formula by year?
    // TODO: how to handle vital signs?
    Pattern pattern = Pattern.compile("#\\{.+?\\}");
    Matcher matcher = pattern.matcher(expression);

    while (matcher.find()) {
      String key = matcher.group();
      String attr = key.substring(2, key.length() - 1).trim(); // lop off #{ and }
      String value = person.attributes.get(attr).toString();

      attributes.put(attr, value);

      // clean up the expression so we can plug it in later
      cleanExpression = cleanExpression.replace(key, attr);
    }
    
    return cleanExpression;
  }

  private static String convertExpressionToCQL(String expression, Map<String, String> attributes) {
    StringBuilder wrappedExpression = new StringBuilder();

    wrappedExpression.append("library "+ LIBRARY_NAME + " version '1'\n\ncontext Patient\n\n");

    for (Map.Entry<String, String> attr : attributes.entrySet()) {
      wrappedExpression
        .append("\ndefine ")
        .append(attr.getKey())
        .append(": ")
        .append(attr.getValue());
    }

    wrappedExpression.append("\ndefine result: ");
    wrappedExpression.append(expression);
    
    return wrappedExpression.toString();
  }
  
  private static Object wrap(Object o) {
    if (o instanceof BigDecimal) {
      // wrap BigDecimals as Longs or Doubles, to make logic elsewhere in the engine easier
      BigDecimal bd = (BigDecimal) o;

      if (isIntegerValue(bd)) {
        o = bd.longValue();
      } else {
        o = bd.doubleValue();
      }
    }

    return o;
  }
  
  private static double wrapDouble(Object o) {
    if (o instanceof BigDecimal) {
      // wrap BigDecimals as Longs or Doubles, to make logic elsewhere in the engine easier
      BigDecimal bd = (BigDecimal) o;

      return bd.doubleValue();
    }
    else {
      return 0.0;
    }
  }

  private static boolean isIntegerValue(BigDecimal bd) {
    // https://stackoverflow.com/a/12748321
    return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
  }

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
   * ExpressionProcessor constructor.
   * @param expression Expression to evaluate for each future set of parameters.
   * @param paramTypeMap Map of parameter names to their corresponding CQL types.
   */
  public ExpressionProcessor(String expression, Map<String,String> paramTypeMap) {
    this.paramNames = new ArrayList();
    this.paramTypeMap = paramTypeMap;
    this.expression = expression;
    
    String cleanExpression = replaceParameters(expression);
    String wrappedExpression = convertParameterizedExpressionToCQL(cleanExpression);
//    System.out.println("Wrapped Expression:");
//    System.out.println(wrappedExpression);
    
    this.elm = cqlToElm(wrappedExpression);
    try {
      this.library = CqlLibraryReader.read(new ByteArrayInputStream(elm.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException | JAXBException ex) {
      throw new RuntimeException(ex);
    }
    this.context = new Context(library);
  }
  
  /**
   * Returns the expression associated with this expression processor
   * @return expression
   */
  public String getExpression() {
    return expression;
  }
  
  /**
   * Returns a list of parameters in the expression associated with this processor
   * @return list of parameters
   */
  public List<String> getParamNames() {
    return paramNames;
  }
  
  /**
   * Returns a map of parameter names to their CQL types as provided to the constructor
   * @return map of parameter names to CQL type names
   */
  public Map getParamTypes() {
    return paramTypeMap;
  }
  
  /**
   * Evaluates the expression with the given numeric parameters, returning the result as a BigDecimal
   * @param params numeric parameters as a map of variable names to values
   * @return evaluation result
   */
  public BigDecimal evaluateNumeric(Map<String,Object> params) {
    return (BigDecimal) evaluate(params);
  }
  
  /**
   * Evaluates the expression with the given parameters
   * @param params parameters as a map of variable names to values
   * @return evaluation result
   */
  public Object evaluate(Map<String,Object> params) {
    // Keep track to make sure all parameters are set
    Set setParams = new HashSet();
    for (Entry<String,Object> entry : params.entrySet()) {
      context.setParameter(null, entry.getKey(), entry.getValue());
      setParams.add(entry.getKey());
    }
    
    Set missing = Sets.difference(paramTypeMap.keySet(), setParams);
    Set extra = Sets.difference(setParams, paramTypeMap.keySet());
    
    if(missing.size() > 0) {
      throw new RuntimeException("Missing evaluation parameters: " + missing);
    }
    if(extra.size() > 0) {
      Logger.getLogger(ExpressionProcessor.class.getName()).log(Level.WARNING,
              "unused parameters provided for expression \"{0}\": {1}",
              new Object[]{expression, extra});
    }
    
    Object retVal = null;

    for (ExpressionDef statement : library.getStatements().getDef()) {
//      System.out.println("Evaluating library statement: " + statement.getName());
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
      
      paramNames.add(param);

      // clean up the expression so we can plug it in later
      cleanExpression = cleanExpression.replace(key, param);
    }
    
    return cleanExpression;
  }
  
  private String convertParameterizedExpressionToCQL(String expression) {
    StringBuilder wrappedExpression = new StringBuilder();

    wrappedExpression.append("library " + LIBRARY_NAME + " version '1'\n");

    for (String paramName : paramNames) {
      wrappedExpression
        .append("\nparameter ")
        .append(paramName)
        .append(" ")
        .append(paramTypeMap.getOrDefault(paramName, "Decimal"));
    }

    wrappedExpression.append("\n\ncontext Patient\n\ndefine result: ");
    wrappedExpression.append(expression);
    
    return wrappedExpression.toString();
  }
}
