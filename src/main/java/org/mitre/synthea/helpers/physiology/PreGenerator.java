package org.mitre.synthea.helpers.physiology;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;

/** Class for handling pre-simulation outputs. **/
public class PreGenerator {
  private String className;
  private List<PreGeneratorArg> args;
  
  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public List<PreGeneratorArg> getArgs() {
    return args;
  }

  public void setArgs(List<PreGeneratorArg> args) {
    this.args = args;
  }

  /**
   * Instantiates the ValueGenerator from the configuration options.
   * @return new ValueGenerator instance
   */
  public ValueGenerator getGenerator(Person person) {
    
    // Check that all input parameters were provided
    if (className == null) {
      throw new IllegalArgumentException("Each preGenerator must provide a 'className'");
    }
    if (args == null) {
      // If args isn't provided, assume the only constructor arg is the Person
      args = new ArrayList<PreGeneratorArg>();
    }
    
    Class<?>[] parameterTypes = new Class<?>[args.size() + 1];
    
    // First argument is always the person instance
    parameterTypes[0] = Person.class;
    
    // Add the rest of the parameter types from the configuration
    for (int i = 0; i < args.size(); i++) {
      PreGeneratorArg arg = args.get(i);
      try {
        if (arg.isPrimitive()) {
          parameterTypes[i + 1] = Utilities.toPrimitiveClass(Class.forName(arg.getType()));
        } else {
          parameterTypes[i + 1] = Class.forName(arg.getType());
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    
    // Our list of arguments for the constructor
    List<Object> objArgs = new ArrayList<Object>();
    
    // First argument is always the person instance
    objArgs.add(person);
    
    // convert our string arguments to the correct types for the constructor
    for (int i = 0; i < args.size(); i++) {
      // Off by one since the inherent person arg isn't explicitly defined in the config
      Class<?> argClass = parameterTypes[i + 1];
      
      PreGeneratorArg arg = args.get(i);
      
      if (argClass.isEnum()) {
        objArgs.add(Enum.valueOf((Class<Enum>) argClass, arg.getValue()));
      } else if (String.class == argClass) {
        objArgs.add(args.get(i));
      } else {
        objArgs.add(Utilities.strToObject(argClass, arg.getValue()));
      }
    }
    
    try {
      // Get the constructor that corresponds to the provided argument types
      java.lang.reflect.Constructor<?> constructor = Class.forName(className)
          .getConstructor(parameterTypes);
      
      // Call the constructor with our argument objects
      return (ValueGenerator) constructor.newInstance(objArgs.toArray());
      
    } catch (NoSuchMethodException | SecurityException | ClassNotFoundException
        | InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    
  }

  public static class PreGeneratorArg {
    private String type;
    private String value;
    private boolean primitive;
    
    public String getType() {
      return type;
    }
    
    public void setType(String type) {
      this.type = type;
    }
    
    public String getValue() {
      return value;
    }
    
    public void setValue(String value) {
      this.value = value;
    }
    
    public boolean isPrimitive() {
      return primitive;
    }
    
    public void setPrimitive(boolean primitive) {
      this.primitive = primitive;
    }
  }
}