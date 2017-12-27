package org.mitre.synthea.helpers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.engine.Validation;
import org.mitre.synthea.engine.Validation.Metadata;

public final class ValidationHelper {

  /**
   * The object being validated.
   */
  private final Validation object;
  
  /**
   * The module that the object belongs to.
   */
  private final Module context;
  
  /**
   * The path within the module that leads to the given object.
   */
  private final List<String> path;
  
  /**
   * Contruct a ValidationHelper to help in validating the given object against various rules.
   * 
   * @param object Object to be validated
   * @param context Module that this object is part of
   * @param path Path within the module that leads to this object
   */
  public ValidationHelper(Validation object, Module context, List<String> path) {
    this.object = object;
    this.context = context;
    this.path = new LinkedList<String>(path);
    this.path.add(object.toString());
  }
  
  /**
   * Validate the object and get back a list of human-readable messages indicating where something
   * is wrong, if anything. Only errors are included, we do not define "warnings" in this model.
   * 
   * @return List of messages indicating any logical errors in the object. 
   */
  public List<String> validate() {
    List<String> messages = new ArrayList<>();

    List<Field> fields = new ArrayList<Field>();
    for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
      fields.addAll(Arrays.asList(c.getDeclaredFields()));
    }
    
    for (Field field : fields) {
      validateField(field, messages);
    }
    
    object.validateSpecial(context, path, messages);
    
    return messages;
  }
  
  private void validateField(Field field, List<String> messages) {
    Metadata metadata = field.getAnnotation(Metadata.class);
    if (metadata != null && metadata.ignore()) {
      return;
    }
    
    Object value;
    try {
      value = FieldUtils.readField(field, object, true); //field.get(this);
    } catch (Exception e) {
      String msg = "Exception occurred during validation: " + e.getMessage();
      messages.add(buildMessage(msg, path));
      e.printStackTrace();
      return; // try the rest of the fields
    }
    
    if (value == null) {
      if (metadata != null && metadata.required()) {
        String message = object + " - field " + field.getName() + " is required.";
        messages.add(buildMessage(message, path));
      }
      // value is null, nothing else we can do at this point
      return;
    }
    
    if (value instanceof Validation) {
      messages.addAll(((Validation) value).validate(context, path));
      
    } else if (value instanceof Collection<?>) {
      validateCollection(value, metadata, field, messages);
      
    } else if (value.getClass().isArray()) {
      validateArray(value, metadata, field, messages);
    }
    
    if (metadata != null) {
      if (metadata.validValues() != null && metadata.validValues().length > 0) {
        String strVal = (String)value;
        
        String[] validValues = metadata.validValues();
        
        if (!Arrays.asList(validValues).contains(strVal)) {
          String message = field.getName() + " has an invalid value: '" + strVal + "' . Valid values are: "
              + String.join(",", validValues);
          
          messages.add(buildMessage(message, path));
        }
      }
      
      if (!metadata.referenceToStateType().equals(State.class)) {
        String referencedStateName = (String)value;
        State referencedState = context.getState(referencedStateName);
        
        if (referencedState == null) {
          // TODO: add message about state that doesnt exist
        } else if (!referencedState.getClass().equals(metadata.referenceToStateType())) {
          
          String message = field.getName() + " is expected to reference a "
              + metadata.referenceToStateType() + " but actually references a "
              + referencedState.getClass().getSimpleName();          
          
          messages.add(buildMessage(message, path));
        }
      }
    }
  }
  
  private void validateCollection(Object value, Metadata metadata, Field field,
      List<String> messages) {
    Collection<?> valueCollection = (Collection<?>) value;
    
    for (Object object : valueCollection) {
      if (object instanceof Validation) {
        messages.addAll(((Validation) object).validate(context, path));
      }
    }
    
    if (metadata != null) {
      validateCollectionSize(valueCollection.size(), metadata.min(), metadata.max(),
          field.getName(), messages);
    }
  }
  
  private void validateArray(Object value, Metadata metadata, Field field, List<String> messages) {
    Object[] valueObjArray = (Object[]) value;
    if (Validation.class.isAssignableFrom(value.getClass().getComponentType())) {
      Validation[] valueArray = (Validation[]) valueObjArray;
      for (Validation object : valueArray) {
        messages.addAll(object.validate(context, path));
      }
    }
    
    if (metadata != null) {
      validateCollectionSize(valueObjArray.length, metadata.min(), metadata.max(),
          field.getName(), messages);
    }
  }
 
  
  private void validateCollectionSize(int size, int min, int max,
      String fieldName, List<String> messages) {
    
    if (min > size) {
      String message = fieldName +  " has fewer than the minimum number of elements"
          + ". Expected " + min + ",  Actual " + size;
      
      messages.add(buildMessage(message, path));
    }
    
    if (max < size) {
      String message = fieldName + " has more than the minimum number of elements"
          + ". Expected " + max + ",  Actual " + size;
      
      messages.add(buildMessage(message, path));
    }
  }
  
  private static long numberOfValues(Object... values) {
    return Arrays.asList(values).stream().filter(o -> o != null).count();
  }
  
  public static boolean exactlyOneOf(Object... values) {
    return numberOfValues(values) == 1;
  }
  
  public static boolean noMoreThanOneOf(Object... values) {
    return numberOfValues(values) <= 1;
  }
  
  public static boolean allOrNoneOf(Object... values) {
    long numValues = numberOfValues(values);
    return numValues == values.length || numValues == 0;
  }
  
  public static boolean allOf(Object... values) {
    return numberOfValues(values) == values.length;
  }

  
  public static String buildMessage(String msg, List<String> path) {
    return msg + "\n  Path: " + String.join(";", path);
  }
}
