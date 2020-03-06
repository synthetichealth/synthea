package org.mitre.synthea.export;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Utility class to use in place of BigDecimal to ensure doubles are serialized with five
 * significant digits, no trailing zeros and without scientific notation.
 * @author mhadley
 */
public class PlainBigDecimal extends BigDecimal {
  
  private static final MathContext MATH_CTX = new MathContext(5, RoundingMode.HALF_UP);
  
  public PlainBigDecimal(double val) {
    super(val, MATH_CTX);
  }
  
  @Override
  public String toString() {
    return this.stripTrailingZeros().toPlainString();
  }
  
}
