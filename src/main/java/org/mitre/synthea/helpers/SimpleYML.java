package org.mitre.synthea.helpers;

import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Helper class to parse a YAML file into a (hopefully) useable map format.
 * This is useful for arbitrary YAML which doesn't try to adhere to any schema.
 * (If it does adhere to a schema, there are other methods to parse YAML to objects which should be preferred)
 * 
 * Java doesn't have dynamic typing so this class exposes a "get" method which takes a path string,
 * and walks the map to find the desired item.
 */
public class SimpleYML {

  /**
   * The internal representation of the YAML file.
   */
  private Map<String,?> internalMap;
  
  /**
   * Create a new SimpleYML from the String contents from a file.
   * 
   * @param rawContent YML file as a String
   */
  public SimpleYML(String rawContent)
  {
    Yaml yaml = new Yaml();
    internalMap = (Map<String,?>)yaml.load(rawContent);
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
   * 
   * calling get("foo.qux.grault") will return Integer(-1)
   * 
   * @param path
   * @return the object at path
   */
  public Object get(String path) {
    String[] pathSegments = path.split("\\.");
    
    Map<String,?> current = internalMap;
    
    // note: length-2 because we don't want to get the last piece as a map
    for (int i = 0 ; i <= pathSegments.length - 2 ; i++) {
      current = (Map<String,?>) current.get( pathSegments[i] );
    }
    
    return current.get(  pathSegments[ pathSegments.length - 1 ]  );
  }
}
