package org.mitre.synthea.world.concepts;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.SimpleYML;

public abstract class BiometricsConfig {

  private static final SimpleYML biometrics;
  
  static {
    try {
      InputStream stream = BiometricsConfig.class.getResourceAsStream("/biometrics.yml");
      //read all text into a string
      String yml = new BufferedReader(new InputStreamReader(stream)).lines()
          .collect(Collectors.joining("\n"));

      biometrics = new SimpleYML(yml);
      
    } catch (Exception e) {
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    } 
  }
  
  public static Object get(String path) {
    return biometrics.get(path);
  }
  
  public static Object get(String path, Object defaultValue) {
    Object result = get(path);
    return (result == null) ? defaultValue : result;
  }
  
  public static int[] ints(String path)
  {
    List<Integer> ints = (List<Integer>)get(path);
    int[] array = ints.stream().mapToInt(i->i).toArray();
    return array;
  }
  
  public static double[] doubles(String path)
  {
    List<Double> doubles = (List<Double>)get(path);
    double[] array = doubles.stream().mapToDouble(i->i).toArray();
    return array;
  }
}
