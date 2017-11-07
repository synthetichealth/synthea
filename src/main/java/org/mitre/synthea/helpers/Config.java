package org.mitre.synthea.helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

public abstract class Config
{
  private static Properties properties = new Properties();

  static
  {
    try
    {
      load(Config.class
          .getResourceAsStream("/synthea.properties"));
    } catch (IOException e)
    {
      System.err.println("Unable to load default properties file");
      e.printStackTrace();
    }
  }

  /**
   * Load properties from a file.
   */
  public static void load(File propsFile) throws FileNotFoundException,
      IOException
  {
    properties.load(new FileReader(propsFile));
  }

  /**
   * Load properties from an input stream. (ex, when running inside a JAR)
   */
  public static void load(InputStream stream) throws IOException
  {
    properties.load(stream);
  }

  /**
   * Get a named property.
   *
   * @param key
   *          property name
   * @return value for the property, or null if not found
   */
  public static String get(String key)
  {
    return properties.getProperty(key);
  }

  /**
   * Get a named property, or the default value if not found.
   *
   * @param key
   *          property name
   * @param defaultValue
   *          value to return if the property is not found in the list
   * @return value for the property, or defaultValue if not found
   */
  public static String get(String key, String defaultValue)
  {
    return properties.getProperty(key, defaultValue);
  }

  /**
   * Manually set a property.
   *
   * @param key
   *          property name
   * @param value
   *          property value
   */
  public static void set(String key, String value)
  {
    properties.setProperty(key, value);
  }
  
  /**
   * Get a set of the names for all properties in the config file.
   *
   * Returns a set of keys in this property list where the key and its corresponding value are strings,
   *  including distinct keys in the default property list if a key of the same name has not already been found from the main properties list.
   *  Properties whose key or value is not of type String are omitted.
   * The returned set is not backed by the Properties object. Changes to this Properties are not reflected in the set, or vice versa.
   *
   * @return Set of property key names
   */
  public static Set<String> allPropertyNames()
  {
	  return properties.stringPropertyNames();
  }
}
