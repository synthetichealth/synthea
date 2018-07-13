package org.mitre.synthea.helpers;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.Library;
import org.mitre.synthea.world.agents.Person;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;

public abstract class ExpressionProcessor {

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

    wrappedExpression.append("library Synthea version '1'\n\ncontext Patient\n\n");

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

  private static boolean isIntegerValue(BigDecimal bd) {
    // https://stackoverflow.com/a/12748321
    return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
  }

  private static String cqlToElm(String cql) {
    ModelManager modelManager = new ModelManager();
    LibraryManager libraryManager = new LibraryManager(modelManager);

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
}
