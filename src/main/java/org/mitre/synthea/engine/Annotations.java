package org.mitre.synthea.engine;

/**
 * Synthea uses annotations to hold metadata for certain fields in the GMF.
 * All annotations should be defined within this class.
 */
public abstract class Annotations {

  /**
   * Define the valid values that a field may have.
   * Validation will return an error if the field has a value other than these.
   */
  public @interface ValidValues {
    /**
     * The set of valid values that the field may have.
     */
    String[] value();
  }
  
}
