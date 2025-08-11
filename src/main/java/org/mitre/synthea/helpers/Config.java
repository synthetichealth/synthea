package org.mitre.synthea.helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * Utility class for managing configuration properties in Synthea.
 * Provides methods to load, retrieve, and manipulate configuration values.
 */
public abstract class Config {
  private static Properties properties = new Properties();

  static {
    try {
      load(Config.class.getResourceAsStream("/synthea.properties"));
    } catch (IOException e) {
      System.err.println("Unable to load default properties file");
      e.printStackTrace();
    }
  }

  /**
   * Load properties from a file.
   *
   * @param propsFile the file containing properties to load
   * @throws FileNotFoundException if the file is not found
   * @throws IOException if an error occurs while reading the file
   */
  public static void load(File propsFile) throws FileNotFoundException, IOException {
    properties.load(new FileReader(propsFile));
  }

  /**
   * Load properties from an input stream (e.g., when running inside a JAR).
   *
   * @param stream the input stream containing properties to load
   * @throws IOException if an error occurs while reading the stream
   */
  public static void load(InputStream stream) throws IOException {
    properties.load(stream);
  }

  /**
   * Get a named property.
   *
   * @param key property name
   * @return value for the property, or null if not found
   */
  public static String get(String key) {
    return properties.getProperty(key);
  }

  /**
   * Get a named property, or the default value if not found.
   *
   * @param key property name
   * @param defaultValue value to return if the property is not found in the list
   * @return value for the property, or defaultValue if not found
   */
  public static String get(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  /**
   * Get a named property as a double value.
   *
   * @param key property name
   * @return value for the property as a double, or null if not found
   */
  public static double getAsDouble(String key) {
    return Double.parseDouble(get(key));
  }

  /**
   * Get a named property as a double, or the default value if not found.
   *
   * @param key property name
   * @param defaultValue value to return if the property is not found in the list
   * @return value for the property as a double, or defaultValue if not found
   */
  public static double getAsDouble(String key, double defaultValue) {
    if (properties.containsKey(key)) {
      return getAsDouble(key);
    } else {
      return defaultValue;
    }
  }

  /**
   * Get a named property as a boolean value.
   *
   * @param key property name
   * @return value for the property as a boolean, or null if not found
   */
  public static boolean getAsBoolean(String key) {
    return Boolean.parseBoolean(get(key));
  }

  /**
   * Get a named property as a boolean, or the default value if not found.
   *
   * @param key property name
   * @param defaultValue value to return if the property is not found in the list
   * @return value for the property as a boolean, or defaultValue if not found
   */
  public static boolean getAsBoolean(String key, boolean defaultValue) {
    if (properties.containsKey(key)) {
      return getAsBoolean(key);
    } else {
      return defaultValue;
    }
  }

  /**
   * Get a named property as an int value.
   *
   * @param key property name
   * @return value for the property as an int, or null if not found
   */
  public static int getAsInteger(String key) {
    return Integer.parseInt(get(key));
  }

  /**
   * Get a named property as an int, or the default value if not found.
   *
   * @param key property name
   * @param defaultValue value to return if the property is not found in the list
   * @return value for the property as an int, or defaultValue if not found
   */
  public static int getAsInteger(String key, int defaultValue) {
    if (properties.containsKey(key)) {
      return getAsInteger(key);
    } else {
      return defaultValue;
    }
  }

  /**
   * Get a named property as a long value.
   *
   * @param key property name
   * @return value for the property as a long, or null if not found
   */
  public static long getAsLong(String key) {
    return Long.parseLong(get(key));
  }

  /**
   * Get a named property as a long, or the default value if not found.
   *
   * @param key property name
   * @param defaultValue value to return if the property is not found in the list
   * @return value for the property as a long, or defaultValue if not found
   */
  public static long getAsLong(String key, long defaultValue) {
    if (properties.containsKey(key)) {
      return getAsLong(key);
    } else {
      return defaultValue;
    }
  }

  /**
   * Manually set a property.
   *
   * @param key property name
   * @param value property value
   */
  public static void set(String key, String value) {
    properties.setProperty(key, value);
  }

  /**
   * Get a set of the names for all properties in the config file.
   *
   * <p>Returns a set of keys in this property list where the key and its corresponding value are
   * strings, including distinct keys in the default property list if a key of the same name has not
   * already been found from the main properties list. Properties whose key or value is not of type
   * String are omitted. The returned set is not backed by the Properties object. Changes to this
   * Properties are not reflected in the set, or vice versa.
   *
   * @return Set of property key names
   */
  public static Set<String> allPropertyNames() {
    return properties.stringPropertyNames();
  }

  /**
   * Unset a configuration value.
   *
   * @param key property name
   */
  public static void remove(String key) {
    if (properties.containsKey(key)) {
      properties.remove(key);
    }
  }
}
