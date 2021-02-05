package org.mitre.synthea.helpers;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.mitre.synthea.engine.Logic;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class Utilities {
  /**
   * Convert a quantity of time in a specified units into milliseconds.
   *
   * @param units
   *          : "hours", "minutes", "seconds", "days", "weeks", "years", or "months"
   * @param value
   *          : quantity of units
   * @return milliseconds
   */
  public static long convertTime(String units, long value) {
    switch (units) {
      case "hours":
        return TimeUnit.HOURS.toMillis(value);
      case "minutes":
        return TimeUnit.MINUTES.toMillis(value);
      case "seconds":
        return TimeUnit.SECONDS.toMillis(value);
      case "days":
        return TimeUnit.DAYS.toMillis(value);
      case "years":
        return TimeUnit.DAYS.toMillis(365 * value);
      case "months":
        return TimeUnit.DAYS.toMillis(30 * value);
      case "weeks":
        return TimeUnit.DAYS.toMillis(7 * value);
      default:
        throw new RuntimeException("Unexpected time unit: " + units);
    }
  }

  /**
   * Convert a quantity of time in a specified units into milliseconds.
   *
   * @param units
   *          : "hours", "minutes", "seconds", "days", "weeks", "years", or "months"
   * @param value
   *          : quantity of units
   * @return milliseconds
   */
  public static long convertTime(String units, double value) {
    switch (units) {
      case "hours":
        return TimeUnit.MINUTES.toMillis((long)(60.0 * value));
      case "minutes":
        return TimeUnit.SECONDS.toMillis((long)(60.0 * value));
      case "seconds":
        return (long)(1000.0 * value);
      case "days":
        return TimeUnit.HOURS.toMillis((long)(24.0 * value));
      case "years":
        return TimeUnit.DAYS.toMillis((long)(365.0 * value));
      case "months":
        return TimeUnit.DAYS.toMillis((long)(30.0 * value));
      case "weeks":
        return TimeUnit.DAYS.toMillis((long)(7.0 * value));
      default:
        throw new RuntimeException("Unexpected time unit: " + units);
    }
  }

  /**
   * Convert a calendar year (e.g. 2020) to a Unix timestamp
   */
  public static long convertCalendarYearsToTime(int years) {
    return convertTime("years", (long) (years - 1970));
  }

  /**
   * Get the year of a Unix timestamp.
   */
  public static int getYear(long time) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(time);
    return calendar.get(Calendar.YEAR);
  }

  /**
   * Get the month of a Unix timestamp.
   */
  public static int getMonth(long time) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(time);
    return calendar.get(Calendar.MONTH) + 1;
  }

  /**
   * Converts a JsonPrimitive into a primitive Boolean, Double, or String.
   *
   * @param p
   *          : JsonPrimitive
   * @return Boolean, Double, or String
   */
  public static Object primitive(JsonPrimitive p) {
    Object retVal = null;
    if (p.isBoolean()) {
      retVal = p.getAsBoolean();
    } else if (p.isNumber()) {
      double doubleVal = p.getAsDouble();

      if (doubleVal == Math.rint(doubleVal)) {
        retVal = (int) doubleVal;
      } else {
        retVal = doubleVal;
      }
    } else if (p.isString()) {
      retVal = p.getAsString();
    }
    return retVal;
  }

  /**
   * Calculates 1 - (1-risk)^(currTimeStepInMS/originalPeriodInMS).
   */
  public static double convertRiskToTimestep(double risk, double originalPeriodInMS) {
    double currTimeStepInMS = Double.parseDouble(Config.get("generate.timestep"));

    return 1 - Math.pow(1 - risk, currTimeStepInMS / originalPeriodInMS);
  }

  /**
   * Compare two objects. lhs and rhs must be of the same type (Number, Boolean, String or Code)
   * Numbers are converted to double prior to comparison.
   * Supported operators are: &lt;, &lt;=, ==, &gt;=, &gt;, !=, is nil, is not nil. 
   * Only lhs is checked for is nil and is not nil.
   */
  public static boolean compare(Object lhs, Object rhs, String operator) {
    if (operator.equals("is nil")) {
      return lhs == null;
    } else if (operator.equals("is not nil")) {
      return lhs != null;
    } else if (lhs == null) {
      return false;
    }
    if (lhs instanceof Number && rhs instanceof Number) {
      return compare(((Number) lhs).doubleValue(), ((Number) rhs).doubleValue(), operator);
    } else if (lhs instanceof Boolean && rhs instanceof Boolean) {
      return compare((Boolean) lhs, (Boolean) rhs, operator);
    } else if (lhs instanceof String && rhs instanceof String) {
      return compare((String) lhs, (String) rhs, operator);
    } else if (lhs instanceof Code && rhs instanceof Code) {
      return compare((Code) lhs, (Code) rhs, operator);
    } else {
      throw new RuntimeException(String.format("Cannot compare %s to %s.\n",
          lhs.getClass().getName(), rhs.getClass().getName()));
    }
  }

  /**
   * Compare two Doubles.
   * Supported operators are: &lt;, &lt;=, ==, &gt;=, &gt;, !=, is nil, is not nil.
   * Only lhs is checked for is nil and is not nil.
   */
  public static boolean compare(Double lhs, Double rhs, String operator) {
    switch (operator) {
      case "<":
        return lhs < rhs;
      case "<=":
        return lhs <= rhs;
      case "==":
        return lhs.doubleValue() == rhs.doubleValue();
      case ">=":
        return lhs >= rhs;
      case ">":
        return lhs > rhs;
      case "!=":
        return lhs.doubleValue() != rhs.doubleValue();
      case "is nil":
        return lhs == null;
      case "is not nil":
        return lhs != null;
      default:
        System.err.format("Unsupported operator: %s\n", operator);
        return false;
    }
  }

  /**
   * Compare two Booleans.
   * Supported operators are: &lt;, &lt;=, ==, &gt;=, &gt;, !=, is nil, is not nil.
   * Only lhs is checked for is nil and is not nil.
   */
  public static boolean compare(Boolean lhs, Boolean rhs, String operator) {
    switch (operator) {
      case "<":
        return lhs != rhs;
      case "<=":
        return lhs != rhs;
      case "==":
        return lhs == rhs;
      case ">=":
        return lhs != rhs;
      case ">":
        return lhs != rhs;
      case "!=":
        return lhs != rhs;
      case "is nil":
        return lhs == null;
      case "is not nil":
        return lhs != null;
      default:
        System.err.format("Unsupported operator: %s\n", operator);
        return false;
    }
  }

  /**
   * Compare two Strings.
   * Supported operators are: &lt;, &lt;=, ==, &gt;=, &gt;, !=, is nil, is not nil.
   * Only lhs is checked for is nil and is not nil.
   */
  public static boolean compare(String lhs, String rhs, String operator) {
    switch (operator) {
      case "<":
        return lhs.compareTo(rhs) < 0;
      case "<=":
        return lhs.compareTo(rhs) <= 0;
      case "==":
        return lhs.equals(rhs);
      case ">=":
        return lhs.compareTo(rhs) >= 0;
      case ">":
        return lhs.compareTo(rhs) > 0;
      case "!=":
        return !lhs.equals(rhs);
      case "is nil":
        return lhs == null;
      case "is not nil":
        return lhs != null;
      default:
        System.err.format("Unsupported operator: %s\n", operator);
        return false;
    }
  }

  /**
   * Compare two Integers.
   * Supported operators are: &lt;, &lt;=, ==, &gt;=, &gt;, !=, is nil, is not nil.
   * Only lhs is checked for is nil and is not nil.
   */
  public static boolean compare(Integer lhs, Integer rhs, String operator) {
    switch (operator) {
      case "<":
        return lhs < rhs;
      case "<=":
        return lhs <= rhs;
      case "==":
        return lhs.intValue() == rhs.intValue();
      case ">=":
        return lhs >= rhs;
      case ">":
        return lhs > rhs;
      case "!=":
        return lhs.intValue() != rhs.intValue();
      case "is nil":
        return lhs == null;
      case "is not nil":
        return lhs != null;
      default:
        System.err.format("Unsupported operator: %s\n", operator);
        return false;
    }
  }

  /**
   * Compare two Codes.
   * Supported operators are: !=, is nil, is not nil. Only lhs is checked for
   * is nil and is not nil.
   */
  public static boolean compare(Code lhs, Code rhs, String operator) {
    switch (operator) {
      case "==":
        return lhs.equals(rhs);
      case "!=":
        return !lhs.equals(rhs);
      case "is nil":
        return lhs == null;
      case "is not nil":
        return lhs != null;
      default:
        System.err.format("Unsupported operator: %s\n", operator);
        return false;
    }
  }

  /**
   * The version identifier from the current version of Synthea.
   * Pulled from an autogenerated version file.
   */
  public static final String SYNTHEA_VERSION = getSyntheaVersion();

  private static String getSyntheaVersion() {
    String version = "synthea-java"; // reasonable default if we can't find a better one

    try {
      // see build.gradle for version.txt format
      String text = readResource("version.txt");
      if (text != null && text.length() > 0) {
        version = text;
      }
    } catch (Exception e) {
      // don't crash if the file isn't there, or for any other reason
      e.printStackTrace();
    }
    return version;
  }

  /**
   * Read the entire contents of a file in resources into a String.
   * @param filename Path to the file, relative to src/main/resources.
   * @return The entire text contents of the file.
   * @throws IOException if any error occurs reading the file
   */
  public static final String readResource(String filename) throws IOException {
    URL url = Resources.getResource(filename);
    return Resources.toString(url, Charsets.UTF_8);
  }

  /**
   * Get a Gson object, preconfigured to load the GMF modules into classes.
   *
   * @return Gson object to unmarshal GMF JSON into objects
   */
  public static Gson getGson() {
    return new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .registerTypeAdapterFactory(InnerClassTypeAdapterFactory.of(Logic.class,"condition_type"))
      .registerTypeAdapterFactory(InnerClassTypeAdapterFactory.of(State.class, "type"))
      .create();
  }

  /**
   * Generate a random DICOM UID to uniquely identify an ImagingStudy, Series, or Instance.
   * Optionally add series and/or instance numbers to the UID to enhance its uniqueness.
   * Pass 0 for the series/instance number to omit it from the UID.
   *
   * @return a String DICOM UID
   */
  public static String randomDicomUid(RandomNumberGenerator random,
      long time, int seriesNo, int instanceNo) {

    // Add a random salt to increase uniqueness
    String salt = randomDicomUidSalt(random);

    String now = String.valueOf(Math.abs(time)); // note time is negative before 1970
    String uid = "1.2.840.99999999";  // 99999999 is an arbitrary organizational identifier

    if (seriesNo > 0) {
      uid += "." + String.valueOf(seriesNo);
    }

    if (instanceNo > 0) {
      uid += "." + String.valueOf(instanceNo);
    }

    return uid + "." + salt + "." + now;
  }

  /**
   * Generates a random string of 8 numbers to use as a salt for DICOM UIDs.
   * @param random the source of randomness
   * @return The 8-digit numeric salt, as a String
   */
  private static String randomDicomUidSalt(RandomNumberGenerator random) {
    final int MIN = 10000000;
    final int MAX = 99999999;

    int saltInt = random.randInt(MAX - MIN + 1) + MIN;
    return String.valueOf(saltInt);
  }
  
  /**
   * Utility function to convert from string to a base Java object type.
   * @param clazz type to convert to
   * @param value string value to convert
   * @return converted value
   */
  public static Object strToObject(Class<?> clazz, String value) {
    if (Boolean.class == clazz || Boolean.TYPE == clazz) {
      return Boolean.parseBoolean(value);
    }
    if (Byte.class == clazz || Byte.TYPE == clazz) {
      return Byte.parseByte(value);
    }
    if (Short.class == clazz || Short.TYPE == clazz) {
      return Short.parseShort(value);
    }
    if (Integer.class == clazz || Integer.TYPE == clazz) {
      return Integer.parseInt(value);
    }
    if (Long.class == clazz || Long.TYPE == clazz) {
      return Long.parseLong(value);
    }
    if (Float.class == clazz || Float.TYPE == clazz) {
      return Float.parseFloat(value);
    }
    if (Double.class == clazz || Double.TYPE == clazz) {
      return Double.parseDouble(value);
    }
    throw new IllegalArgumentException("Cannot parse value for class " + clazz);
  }

  /**
   * Walk the directory structure of the modules, and apply the given function for every module.
   * 
   * @param action Action to apply for every module. Function signature is 
   *        (topLevelModulesFolderPath, currentModulePath) -&gt; {...}
   */
  public static void walkAllModules(BiConsumer<Path, Path> action) throws Exception {
    URL modulesFolder = ClassLoader.getSystemClassLoader().getResource("modules");
    Path modulesPath = Paths.get(modulesFolder.toURI());

    walkAllModules(modulesPath, p -> action.accept(modulesPath, p));
  }

  /**
   * Walk the directory structure of the modules starting at the given location, and apply the given
   * function for every module underneath.
   * 
   * @param action Action to apply for every module. Function signature is 
   *        (currentModulePath) -&gt; {...}
   */
  public static void walkAllModules(Path modulesPath, Consumer<Path> action) throws Exception {
    Files.walk(modulesPath, Integer.MAX_VALUE)
        .filter(Files::isReadable)
        .filter(Files::isRegularFile)
        .filter(p -> p.toString().endsWith(".json"))
        .forEach(p -> action.accept(p));
  } 
}
