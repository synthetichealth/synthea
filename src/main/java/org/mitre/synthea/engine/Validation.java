package org.mitre.synthea.engine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.mitre.synthea.engine.Annotations.NoValidation;
import org.mitre.synthea.engine.Annotations.ValidValues;

public interface Validation {

  public default List<String> validate(Module context, List<String> path) {
    List<String> messages = new ArrayList<>();
    
    path = new LinkedList<String>(path);
    path.add(this.toString());
    
    List<Field> fields = new ArrayList<Field>();
    for (Class<?> c = this.getClass(); c != null; c = c.getSuperclass()) {
      fields.addAll(Arrays.asList(c.getDeclaredFields()));
    }
    
    for (Field field : fields) {
      
      if (field.isAnnotationPresent(NoValidation.class)) {
        continue;
      }
      
      Object value;
      try {
        value = FieldUtils.readField(field, this, true); //field.get(this);
      } catch (Exception e) {
        String msg = "Exception occurred during validation: " + e.getMessage();
        messages.add(buildMessage(msg, path));
        e.printStackTrace();
        continue; // try the rest of the fields
      }
      
      if (value instanceof Validation) {
        messages.addAll(((Validation) value).validate(context, path));
      } else if (value instanceof Collection<?>) {
        Collection<?> valueCollection = (Collection<?>) value;
        
        for (Object object : valueCollection) {
          if (object instanceof Validation) {
            messages.addAll(((Validation) object).validate(context, path));
          }
        }
        
      } else if (value != null && value.getClass().isArray()) {
        Object[] valueArray = (Object[]) value;
        for (Object object : valueArray) {
          if (object instanceof Validation) {
            messages.addAll(((Validation) object).validate(context, path));
          }
        }
      }
      
      if (field.isAnnotationPresent(ValidValues.class)) {
        String strVal = (String)value;
        
        String[] validValues = field.getAnnotation(ValidValues.class).value();
        
        if (!Arrays.asList(validValues).contains(strVal)) {
          String message = this + " has an invalid value: '" + strVal + "' . Valid values are: "
              + String.join(",", validValues);
          
          messages.add(buildMessage(message, path));
        }
        
      }
    }
    
    return messages;
  }
  
  public default String buildMessage(String msg, List<String> path) {
    return msg + "\n  Path: " + String.join(";", path);
  }
  
}
