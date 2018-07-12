package org.mitre.synthea.helpers;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.Library;
import org.mitre.synthea.world.agents.Person;
import org.opencds.cqf.cql.elm.execution.ExpressionDefEvaluator;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;

public abstract class ExpressionProcessor {

  public static void initialize() {

  }

  public static Object evaluate(String expression, Person person, long time) {
    try {
      StringBuilder wrappedExpression = new StringBuilder();

      wrappedExpression.append("library Synthea version '1'\n\ncontext Patient\n\n");

      // identify the attributes that are used
      Set<String> attributes = new HashSet<>();

      Pattern pattern = Pattern.compile("#\\{.+?\\}");

      Matcher matcher = pattern.matcher(expression);

      while (matcher.find()) {
        String key = matcher.group();
        String attr = key.substring(2, key.length() - 1).trim(); // lop off #{ and }
        attributes.add(attr);

        // clean up the expression so we can plug it in later
        expression = expression.replace(key, attr);
      }

      for (String attr : attributes) {
        Object value = person.attributes.get(attr);

        if (!(value instanceof Number)) {
          throw new IllegalArgumentException("attempted to use attribute " + attr + " with value "
              + value + " in calculation but it is not a number.");
        }

        wrappedExpression.append("\ndefine ").append(attr).append(": ").append(value);

      }

      wrappedExpression.append("\ndefine result: ");
      wrappedExpression.append(expression);

      // try and parse it as CQL.
      String elm = cqlToElm(wrappedExpression.toString());

      Library library = CqlLibraryReader
          .read(new ByteArrayInputStream(elm.getBytes(StandardCharsets.UTF_8)));

      Context context = new Context(library);

      Object retVal = null;

      for (ExpressionDef statement : library.getStatements().getDef()) {
        if (!(statement instanceof ExpressionDefEvaluator)) {
          // This skips over any FunctionDef statements for starters.
          continue;
        }
        // if (!statement.getAccessLevel().value().equals("Public")) {
        // // Note: It appears that Java interns the string "Public"
        // // since using != here also seems to work.
        // continue;
        // }

        retVal = statement.evaluate(context);
      }

      return wrap(retVal);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Object wrap(Object o) {
    if (o == null || o instanceof Number || o instanceof String) {
      return o;
    } else if (o instanceof BigDecimal) {
      // wrap BigDecimals as Longs or Doubles, to make logic elsewhere in the engine easier
      BigDecimal bd = (BigDecimal) o;

      if (isIntegerValue(bd)) {
        o = bd.longValue();
      } else {
        o = bd.doubleValue();
      }
    } else if (o instanceof BigInteger) {
      // wrap BigIntegers as Longs, same reason
      o = ((BigInteger) o).longValue();
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

    ArrayList<CqlTranslator.Options> options = new ArrayList<>();
    options.add(CqlTranslator.Options.EnableDateRangeOptimization);

    CqlTranslator translator = CqlTranslator.fromText(cql, modelManager, libraryManager,
        options.toArray(new CqlTranslator.Options[options.size()]));

    if (translator.getErrors().size() > 0) {
      return null;
    }

    String elm = translator.toXml();

    if (translator.getErrors().size() > 0) {
      return null;
    }

    return elm;
  }
}
