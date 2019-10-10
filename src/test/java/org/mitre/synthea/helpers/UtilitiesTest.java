package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonPrimitive;
import java.util.Date;
import org.junit.Test;

public class UtilitiesTest {

  @SuppressWarnings("deprecation")
  @Test
  public void testConvertTime() {
    long year = Utilities.convertCalendarYearsToTime(2005);
    long date = new Date(104, 0, 1).getTime(); // January 1, 2004
    System.out.println(year);
    System.out.println(date);
    assertTrue(date <= year);
    date = new Date(106, 0, 1).getTime(); // January 1, 2006
    System.out.println(date);
    assertTrue(date > year);
  }

  @Test
  public void testYears() {
    int gap = 75;
    long time = System.currentTimeMillis();
    int year = Utilities.getYear(time);
    long earlierTime = time - Utilities.convertTime("years", gap);
    int earlierYear = Utilities.getYear(earlierTime);
    assertEquals(gap, (year - earlierYear));
  }

  @Test
  public void testGetYear() {
    assertEquals(1970, Utilities.getYear(0L));
    // the time as of this writing, 2017-09-07
    assertEquals(2017, Utilities.getYear(1504783221000L));
    assertEquals(2009, Utilities.getYear(1234567890000L));
  }

  @Test
  public void testCompareObjects() {
    Object lhs = new String("foo");
    Object rhs = new String("foobar");
    assertTrue(Utilities.compare(lhs, rhs, "!="));
  }

  @Test
  public void testCompareDoubles() {
    Double lhs = 1.2;
    Double rhs = 1.8;
    assertTrue(Utilities.compare(lhs, rhs, "<"));
    assertTrue(Utilities.compare(lhs, rhs, "<="));
    assertTrue(Utilities.compare(lhs, lhs, "=="));
    assertFalse(Utilities.compare(lhs, rhs, "=="));
    assertFalse(Utilities.compare(lhs, rhs, ">"));
    assertFalse(Utilities.compare(lhs, rhs, ">="));
    assertTrue(Utilities.compare(lhs, rhs, "!="));
    assertTrue(Utilities.compare(lhs, rhs, "is not nil"));
    assertTrue(Utilities.compare(rhs, lhs, "is not nil"));
    assertFalse(Utilities.compare(lhs, rhs, "is nil"));
    assertFalse(Utilities.compare(rhs, lhs, "is nil"));
    lhs = null;
    rhs = null;
    assertTrue(Utilities.compare(lhs, rhs, "is nil"));
    assertTrue(Utilities.compare(rhs, lhs, "is nil"));
    assertFalse(Utilities.compare(lhs, rhs, "is not nil"));
    assertFalse(Utilities.compare(rhs, lhs, "is not nil"));
    // Unsupported operator goes down default branch
    assertFalse(Utilities.compare(lhs, rhs, "~="));
  }

  @Test
  public void testCompareBooleans() {
    Boolean lhs = Boolean.FALSE;
    Boolean rhs = Boolean.TRUE;
    assertTrue(Utilities.compare(lhs, rhs, "<"));
    assertTrue(Utilities.compare(lhs, rhs, "<="));
    assertTrue(Utilities.compare(lhs, rhs, ">"));
    assertTrue(Utilities.compare(lhs, rhs, ">="));
    assertTrue(Utilities.compare(lhs, rhs, "!="));
    assertFalse(Utilities.compare(lhs, rhs, "is nil"));
    assertTrue(Utilities.compare(lhs, rhs, "is not nil"));
    lhs = null;
    rhs = null;
    assertTrue(Utilities.compare(lhs, rhs, "is nil"));
    assertFalse(Utilities.compare(lhs, rhs, "is not nil"));
    // Unsupported operator goes down default branch
    assertFalse(Utilities.compare(lhs, rhs, "~="));
  }

  @Test
  public void testCompareStrings() {
    String lhs = "A";
    String rhs = "Z";
    assertTrue(Utilities.compare(lhs, rhs, "<"));
    assertTrue(Utilities.compare(lhs, rhs, "<="));
    assertTrue(Utilities.compare(lhs, lhs, "=="));
    assertFalse(Utilities.compare(lhs, rhs, "=="));
    assertFalse(Utilities.compare(lhs, rhs, ">"));
    assertFalse(Utilities.compare(lhs, rhs, ">="));
    assertTrue(Utilities.compare(lhs, rhs, "!="));
    assertTrue(Utilities.compare(lhs, rhs, "is not nil"));
    assertTrue(Utilities.compare(rhs, lhs, "is not nil"));
    lhs = null;
    rhs = null;
    assertTrue(Utilities.compare(lhs, rhs, "is nil"));
    assertTrue(Utilities.compare(rhs, lhs, "is nil"));
    // Unsupported operator goes down default branch
    assertFalse(Utilities.compare(lhs, rhs, "~="));
  }

  @Test(expected = RuntimeException.class)
  public void testCompareDifferentTypesThrowsException() {
    Object lhs = Boolean.FALSE;
    Object rhs = "Z";
    Utilities.compare(lhs, rhs, "==");
  }

  @Test(expected = RuntimeException.class)
  public void testConvertTimeInFortnightsThrowsException() {
    Utilities.convertTime("fortnights", 1);
  }

  @Test
  public void testJsonPrimitiveBooleans() {
    JsonPrimitive p = new JsonPrimitive(Boolean.TRUE);
    assertTrue(Boolean.TRUE == Utilities.primitive(p));
    p = new JsonPrimitive(Boolean.FALSE);
    assertTrue(Boolean.FALSE == Utilities.primitive(p));
  }

  @Test
  public void testJsonPrimitiveStrings() {
    JsonPrimitive p = new JsonPrimitive("Foo");
    assertTrue("Foo".equals(Utilities.primitive(p)));
  }

  @Test
  public void testJsonPrimitiveDoubles() {
    Double[] numbers = { 1.2, 1.5, 1.501, 1.7 };
    for (Double d : numbers) {
      JsonPrimitive p = new JsonPrimitive(d);
      String message = d + " equal to " + Utilities.primitive(p) + "?";
      assertTrue(message, d.equals(Utilities.primitive(p)));
    }
  }

  @Test
  public void testJsonPrimitiveIntegers() {
    Integer[] numbers = { -1, 0, 1 };
    for (Integer d : numbers) {
      JsonPrimitive p = new JsonPrimitive(d);
      String message = d + " equal to " + Utilities.primitive(p) + "?";
      assertTrue(message, d.equals(Utilities.primitive(p)));
    }
  }
  
  @Test
  public void testToPrimitiveClass() {
    // Test each of the primitive classes
    assertEquals(Boolean.TYPE, Utilities.toPrimitiveClass(Boolean.class));
    assertEquals(Byte.TYPE, Utilities.toPrimitiveClass(Byte.class));
    assertEquals(Short.TYPE, Utilities.toPrimitiveClass(Short.class));
    assertEquals(Integer.TYPE, Utilities.toPrimitiveClass(Integer.class));
    assertEquals(Long.TYPE, Utilities.toPrimitiveClass(Long.class));
    assertEquals(Float.TYPE, Utilities.toPrimitiveClass(Float.class));
    assertEquals(Double.TYPE, Utilities.toPrimitiveClass(Double.class));
    
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidToPrimitiveClass() {
    // Trying to get the primitive class for a type that doesn't have
    // a primitive class results in an IllegalArgumentException
    Utilities.toPrimitiveClass(Date.class);
  }
  
  @Test
  public void testStrToObject() {
    
    assertEquals(true, Utilities.strToObject(Boolean.class, "true"));
    assertEquals((byte) 2, Utilities.strToObject(Byte.class, "2"));
    assertEquals((short) 3, Utilities.strToObject(Short.class, "3"));
    assertEquals(5, Utilities.strToObject(Integer.class, "5"));
    assertEquals(7L, Utilities.strToObject(Long.class, "7"));
    assertEquals(2.5f, Utilities.strToObject(Float.class, "2.5"));
    assertEquals(4.8, Utilities.strToObject(Double.class, "4.8"));

  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidStrToObject() {
    // Trying to parse a non-primitive class type results in an
    // IllegalArgumentException
    Utilities.strToObject(Date.class, "oops");
  }
}
