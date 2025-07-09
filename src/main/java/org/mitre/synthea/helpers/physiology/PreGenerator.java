package org.mitre.synthea.helpers.physiology;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;

/** Class for handling pre-simulation outputs. **/
public class PreGenerator implements Serializable {
  /** Fully qualified class name for the generator. */
  private String className;
  /** List of arguments to pass to the generator's constructor. */
  private List<PreGeneratorArg> args;

  /**
   * Gets the fully qualified class name for the generator.
   * @return The class name.
   */
  public String getClassName() {
    return className;
  }

  /**
   * Sets the fully qualified class name for the generator.
   * @param className The class name to set.
   */
  public void setClassName(String className) {
    this.className = className;
  }

  /**
   * Gets the list of arguments to pass to the generator's constructor.
   * @return The list of arguments.
   */
  public List<PreGeneratorArg> getArgs() {
    return args;
  }

  /**
   * Sets the list of arguments to pass to the generator's constructor.
   * @param args The list of arguments to set.
   */
  public void setArgs(List<PreGeneratorArg> args) {
    this.args = args;
  }

  /**
   * Instantiates the ValueGenerator from the configuration options.
   * @param person The person for whom the generator is created.
   * @return A new ValueGenerator instance.
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
          parameterTypes[i + 1] = ClassUtils.wrapperToPrimitive(Class.forName(arg.getType()));
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

  /**
   * Represents an argument for the PreGenerator.
   */
  public static class PreGeneratorArg {
    /** Data type of the argument. */
    private String type;
    /** Value of the argument. */
    private String value;
    /** Indicates if the argument is a primitive type. */
    private boolean primitive;

    /**
     * Returns the data type of the argument.
     * @return The data type.
     */
    public String getType() {
      return type;
    }

    /**
     * Sets the data type of the argument.
     * @param type The data type.
     */
    public void setType(String type) {
      this.type = type;
    }

    /**
     * Returns the value of the argument.
     * @return The value.
     */
    public String getValue() {
      return value;
    }

    /**
     * Sets the value of the argument.
     * @param value The value.
     */
    public void setValue(String value) {
      this.value = value;
    }

    /**
     * Returns whether the argument is a primitive type.
     * @return True if the argument is primitive, false otherwise.
     */
    public boolean isPrimitive() {
      return primitive;
    }

    /**
     * Sets whether the argument is a primitive type.
     * @param primitive True if the argument is primitive, false otherwise.
     */
    public void setPrimitive(boolean primitive) {
      this.primitive = primitive;
    }
  }
}