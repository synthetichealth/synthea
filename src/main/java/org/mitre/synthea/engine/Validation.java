package org.mitre.synthea.engine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.mitre.synthea.helpers.ValidationHelper;

public interface Validation {
  public default List<String> validate(Module context, List<String> path) {
    // just handoff to ValidationHelper
    return new ValidationHelper(this, context, path).validate();
  }
  
  public default void validateSpecial(Module context, List<String> path, List<String> messages) {
    // do nothing. implementing classes can add something if they need to do any unique validation
  }
  
  /**
   * An annotation for metadata about fields.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface Metadata {
    /**
     * The set of valid values that the field may have.
     */
    String[] validValues() default {};
    
    /**
     * Indicates whether this field should be ignored by validation altogether.
     */
    boolean ignore() default false;
    
    /**
     * Indicates whether the field must have a value set.
     */
    boolean required() default false;
    
    /**
     * Indicates the minimum number of values a collection or array must contain.
     */
    int min() default 0;
    
    /**
     * Indicates the maximum number of values a collection or array must contain.
     */
    int max() default Integer.MAX_VALUE;
    
    /**
     * Indicates that this field is a reference to another state, which must be of a given type.
     */
    Class<? extends State> referenceToStateType() default State.class;
  }
}

