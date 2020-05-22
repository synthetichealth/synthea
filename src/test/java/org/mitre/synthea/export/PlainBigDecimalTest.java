package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Testing PlainBigDecimal string formatting.
 * @author mhadley
 */
public class PlainBigDecimalTest {
  
  /**
   * Test of toString method, of class PlainBigDecimal.
   * Should produce 5 significant digits, no trailing zeros and no scientific notation.
   */
  @Test
  public void testToString() {
    PlainBigDecimal bd = new PlainBigDecimal(120.001);
    assertEquals("120", bd.toString());
    bd = new PlainBigDecimal(120.00);
    assertEquals("120", bd.toString());
    bd = new PlainBigDecimal(120.006);
    assertEquals("120.01", bd.toString());
    bd = new PlainBigDecimal(120000.00);
    assertEquals("120000", bd.toString());
    bd = new PlainBigDecimal(0.0012345);
    assertEquals("0.0012345", bd.toString());
    bd = new PlainBigDecimal(0.00123456);
    assertEquals("0.0012346", bd.toString());
  }
  
}
