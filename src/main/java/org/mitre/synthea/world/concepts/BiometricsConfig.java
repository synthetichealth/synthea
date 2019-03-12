package org.mitre.synthea.world.concepts;

import java.util.List;

import org.mitre.synthea.helpers.SimpleYML;
import org.mitre.synthea.helpers.Utilities;

/**
 * Class exposing various configurable biometric settings, 
 * which are set in resources/biometrics.yml.
 * Unlike the standard "Config" and synthea.properties, 
 * it is unlikely that an end-user will want to tweak these often.
 */
public abstract class BiometricsConfig {

  /**
   * The SimpleYML containing the parsed 'biometrics.yml' file.
   */
  private static final SimpleYML biometrics = loadYML();
  
  /**
   * Load the 'biometrics.yml' resource file.
   * 
   * @return the parsed file
   */
  private static final SimpleYML loadYML() {
    try {
      String yml = Utilities.readResource("biometrics.yml");
      return new SimpleYML(yml);
      
    } catch (Exception e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    } 
  }
  
  /**
   * Get the object at the given path from the YML.
   * For example, given the following YML:<pre>
   * foo:
   *    bar: 2
   *    baz: 4
   *    qux:
   *      corge: 9
   *      grault: -1
   *  plugh: 7
   * </pre>
   * calling get("foo.qux.grault") will return Integer(-1)
   * 
   * @param path path to the desired object
   * @return the object at path
   */
  public static Object get(String path) {
    return biometrics.get(path);
  }
  
  /**
   * Get the object at the given path from the YML.
   * If not found, returns the default value.
   * @param path path to the desired object
   * @param defaultValue value returned if there is no object at the given path 
   * @return the object at path, or defaultValue if not found
   */
  public static Object get(String path, Object defaultValue) {
    Object result = get(path);
    return (result == null) ? defaultValue : result;
  }
  
  /**
   * Helper function to get a list of ints from a given location as an array.
   * Note that this function does not convert doubles to integers,
   * so if the YML contains doubles then this method may throw.
   * 
   * @param path path to the desired object
   * @return parsed integer array
   */
  public static int[] ints(String path) {
    List<Integer> ints = (List<Integer>) get(path);
    int[] array = ints.stream().mapToInt(i -> i).toArray();
    return array;
  }
  
  /**
   * Helper function to get a list of doubles from a given location as an array.
   * This function does convert integers to doubles, so it should be preferred over ints()
   * if the data type is unknown or may change.
   * 
   * @param path path to the desired object
   * @return parsed double array
   */
  public static double[] doubles(String path) {
    List<Number> doubles = (List<Number>) get(path);
    double[] array = doubles.stream().mapToDouble(i -> i.doubleValue()).toArray();
    return array;
  }
}
