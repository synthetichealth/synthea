package org.mitre.synthea.engine;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Synthea uses annotations to hold metadata for certain fields in the GMF.
 * All annotations should be defined within this class.
 */
public abstract class Annotations {

  /**
   * Define the valid values that a field may have.
   * Validation will return an error if the field has a value other than these.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface ValidValues {
    /**
     * The set of valid values that the field may have.
     */
    String[] value();
  }
  
  /**
   * Indicates that a field should be ignored by validation altogether.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface NoValidation {}
  
}
