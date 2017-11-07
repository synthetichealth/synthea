package org.mitre.synthea.helpers;

import java.util.Map;

import org.yaml.snakeyaml.Yaml;


public class SimpleYML {

  private Map<String,Object> internalMap;
  
  public SimpleYML(String rawContent)
  {
    Yaml yaml = new Yaml();
    internalMap = (Map)yaml.load(rawContent);
  }
  
  public Object get(String path) {
    String[] pathSegments = path.split("\\.");
    
    Map<String,Object> current = internalMap;
    
    // note: length-2 because we don't want to get the last piece as a map
    for (int i = 0 ; i <= pathSegments.length - 2 ; i++) {
      current = (Map) current.get( pathSegments[i] );
    }
    
    return current.get(  pathSegments[ pathSegments.length - 1 ]  );
  }
}
