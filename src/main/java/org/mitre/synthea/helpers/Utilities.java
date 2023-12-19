package org.mitre.synthea.helpers;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.Range;
import org.mitre.synthea.engine.Logic;
import org.mitre.synthea.engine.Module;
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
        return TimeUnit.DAYS.toMillis((long) 365.25 * value);
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
        return TimeUnit.DAYS.toMillis((long)(365.25 * value));
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
    Calendar c = Calendar.getInstance();
    c.clear();
    c.setTimeZone(TimeZone.getTimeZone("GMT"));
    c.set(years, 0, 1, 0, 0, 0);
    return c.getTimeInMillis();
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
   * Convert the given LocalDate into a Unix timestamp.
   * The LocalDate is assumed to be interpreted in the UTC time zone,
   * and a timestamp is created of the start of the day (00:00:00, or 12:00 midnight).
   * @param date the local date
   * @return the timestamp
   */
  public static long localDateToTimestamp(LocalDate date) {
    return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  /**
   * Convert the given Unix timestamp into a LocalDate.
   * The timestamp is assumed to be interpreted in the UTC time zone.
   */
  public static LocalDate timestampToLocalDate(long timestamp) {
    return Instant.ofEpochMilli(timestamp).atOffset(ZoneOffset.UTC).toLocalDate();
  }

  /**
   * Get the timestamp of the nth anniversary of the supplied timestamp.
   * @param date the timestamp
   * @param anniversary the number of years after
   * @return the anniversary timestamp
   */
  public static long getAnniversary(long date, int anniversary) {
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(date);
    calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) + anniversary);
    return calendar.getTimeInMillis();
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
    double currTimeStepInMS = Config.getAsDouble("generate.timestep");

    return convertRiskToTimestep(risk, originalPeriodInMS, currTimeStepInMS);
  }

  /**
   * Calculates 1 - (1-risk)^(newTimeStepInMS/originalPeriodInMS).
   */
  public static double convertRiskToTimestep(double risk, double originalPeriodInMS,
      double newTimeStepInMS) {
    return 1 - Math.pow(1 - risk, newTimeStepInMS / originalPeriodInMS);
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
   * Parse a range of Synthea timestamps (milliseconds since the epoch) from a String. The string
   * can be in one of two formats:
   * <p>
   * "milliseconds start-milliseconds end" example: "1599264000000-1627948800000"
   * or
   * "ISO date start-ISO date end" example: "2020-09-05-2021-08-03"
   * </p><p>
   * If using the ISO date format, the time range will be from the start of the day at the beginning
   * of the range until the end of the day for the last day of the range.
   * </p>
   * @param range String containing the range
   * @return A Range with the min and max set to Synthea timestamps, longs containing milliseconds
   *     since the epoch
   * @throws IllegalArgumentException If the string is not in one of the expected formats
   */
  public static Range<Long> parseDateRange(String range) throws IllegalArgumentException {
    if (!range.contains("-")
        || range.substring(0, range.indexOf("-")).length() < 1
        || range.substring(range.indexOf("-") + 1).length() < 1) {
      throw new IllegalArgumentException("Time range format error. Expect low-high. Found '"
              + range + "'");
    }

    Pattern dateRangeRegex =
        Pattern.compile("^(\\d{4}\\-\\d{2}\\-\\d{2})\\-(\\d{4}\\-\\d{2}\\-\\d{2})$");

    Matcher matcher = dateRangeRegex.matcher(range);

    Range<Long> parsedRange;
    if (matcher.matches()) {
      parsedRange = Range.between(
        LocalDate.parse(matcher.group(1), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
            .toInstant(ZoneOffset.UTC).toEpochMilli(),
        // adding a day and subtracting 1 to get the millisecond before midnight at the end of the
        // range
        LocalDate.parse(matcher.group(2), DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1)
            .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1);
    } else {
      parsedRange = Range.between(
          Long.parseLong(range.substring(0, range.indexOf("-"))),
          Long.parseLong(range.substring(range.indexOf("-") + 1)));
    }

    return parsedRange;
  }

  /**
   * Read the entire contents of a file in resources into a String.
   * @param filename Path to the file, relative to src/main/resources.
   * @return The entire text contents of the file.
   * @throws IOException if any error occurs reading the file
   */
  public static final String readResource(String filename) throws IOException {
    return readResource(filename, false, false);
  }

  /**
   * Read the entire contents of a file into a String.
   * @param filename Path to the file.
   * @param stripBOM Whether or not to check for and strip a BOM
   *     -- see {@link #readResourceAndStripBOM(String)} for more info
   * @param allowFreePath If false, the file must be within src/main/resources.
   *     If true, the file may be anywhere on the filesystem.
   * @return The entire text contents of the file.
   * @throws IOException if any error occurs reading the file
   */
  public static final String readResource(String filename, boolean stripBOM, boolean allowFreePath)
      throws IOException {
    String contents;

    try {
      URL url = Resources.getResource(filename);
      contents = Resources.toString(url, Charsets.UTF_8);
    } catch (IllegalArgumentException e) {
      // Resources throws an IllegalArgumentException instead of FileNotFoundException
      // when the resource is not found - this may be a full path
      if (!allowFreePath) {
        throw e;
      }
      try {
        Path path = new File(filename).toPath();
        contents = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      } catch (FileNotFoundException fnfe) {
        throw new IllegalArgumentException("Unable to locate or read " + filename);
      }
    }

    if (stripBOM && contents.startsWith("\uFEFF")) {
      contents = contents.substring(1); // Removes BOM.
    }
    return contents;
  }

  /**
   * Read the entire contents of a file into a String.
   * The file may be relative to src/main/resources or anywhere on the filesystem.
   * @param filename Path to the files.
   * @return The entire text contents of the file.
   * @throws IOException if any error occurs reading the file
   */
  public static final String readResourceOrPath(String filename) throws IOException {
    return readResource(filename, false, true);
  }

  /**
   * Read the entire contents of a file in resources into a String and strip the BOM if present.
   * This method is intended for use when reading CSV files that may have been created by
   * spreadsheet programs that sometimes automatically add a BOM though it could also be used for
   * any other type of file that may optionally include a BOM.
   * @param filename Path to the file, relative to src/main/resources.
   * @return The entire text contents of the file minus the leading BOM if present.
   * @throws IOException if any error occurs reading the file
   */
  public static final String readResourceAndStripBOM(String filename) throws IOException {
    return readResource(filename, true, false);
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
      // as of JDK16, GSON can no longer handle certain sdk classes
      .registerTypeAdapter(Random.class, new SerializableTypeAdapter<Random>())
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
    Path modulesPath = Module.getModulesPath();

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

  /**
   * Iterate through a Map and remove any entries where there is a key, but the value is null.
   * This method modifies the input Map.
   * @param input The Map to clean
   * @return a null-free Map
   */
  public static Map cleanMap(Map input) {
    List keysToRemove = new ArrayList();
    Set keys = input.keySet();
    keys.forEach(key -> {
      if (input.get(key) == null) {
        keysToRemove.add(key);
      }
    });
    keysToRemove.forEach(key -> {
      input.remove(key);
    });
    return input;
  }

  /**
   * Enable reading the given URI from within a JAR file.
   * For example, for command-line args which may refer to internal or external paths.
   * Note that it's not always possible to know when a user-provided path
   * is within a JAR file, so this method should be called if it is possible the
   * path refers to an internal location.
   * @param uri URI to be accessed
   */
  public static void enableReadingURIFromJar(URI uri) throws IOException {
    // this function is a hack to enable reading modules from within a JAR file
    // see https://stackoverflow.com/a/48298758
    if ("jar".equals(uri.getScheme())) {
      for (FileSystemProvider provider: FileSystemProvider.installedProviders()) {
        if (provider.getScheme().equalsIgnoreCase("jar")) {
          try {
            provider.getFileSystem(uri);
          } catch (FileSystemNotFoundException e) {
            // in this case we need to initialize it first:
            provider.newFileSystem(uri, Collections.emptyMap());
          }
        }
      }
    }
  }
}