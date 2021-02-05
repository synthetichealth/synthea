package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonPrimitive;

import java.util.Calendar;
import java.util.Date;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

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
    Calendar calendar = Calendar.getInstance();
    calendar.set(2020, Calendar.FEBRUARY, 1);
    long time = calendar.getTimeInMillis();
    int year = Utilities.getYear(time);
    long earlierTime = time - Utilities.convertTime("years", gap);
    int earlierYear = Utilities.getYear(earlierTime);
    assertEquals(gap, (year - earlierYear));
  }
  
  @Test
  public void testFractionalDurations() {
    assertEquals(500, Utilities.convertTime("seconds", 0.5));
    assertEquals(Utilities.convertTime("minutes", 0.5), Utilities.convertTime("seconds", 30));
    assertEquals(Utilities.convertTime("hours", 0.5), Utilities.convertTime("minutes", 30));
    assertEquals(Utilities.convertTime("days", 0.5), Utilities.convertTime("hours", 12));
    assertEquals(Utilities.convertTime("weeks", 0.5), Utilities.convertTime("days", 3));
    assertEquals(Utilities.convertTime("months", 0.5), Utilities.convertTime("days", 15));
    assertEquals(Utilities.convertTime("years", 0.5), Utilities.convertTime("weeks", 26));
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
    Object lhs = "foo";
    Object rhs = "foobar";
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
  public void testStrToObject() {
    
    assertEquals(true, Utilities.strToObject(Boolean.class, "true"));
    assertEquals(true, Utilities.strToObject(Boolean.TYPE, "true"));
    assertEquals((byte) 2, Utilities.strToObject(Byte.class, "2"));
    assertEquals((byte) 2, Utilities.strToObject(Byte.TYPE, "2"));
    assertEquals((short) 3, Utilities.strToObject(Short.class, "3"));
    assertEquals((short) 3, Utilities.strToObject(Short.TYPE, "3"));
    assertEquals(5, Utilities.strToObject(Integer.class, "5"));
    assertEquals(5, Utilities.strToObject(Integer.TYPE, "5"));
    assertEquals(7L, Utilities.strToObject(Long.class, "7"));
    assertEquals(7L, Utilities.strToObject(Long.TYPE, "7"));
    assertEquals(2.5f, Utilities.strToObject(Float.class, "2.5"));
    assertEquals(2.5f, Utilities.strToObject(Float.TYPE, "2.5"));
    assertEquals(4.8, Utilities.strToObject(Double.class, "4.8"));
    assertEquals(4.8, Utilities.strToObject(Double.TYPE, "4.8"));

  }

  @Test
  public void testDicomUid() {
    // regex for FHIR OIDs: https://www.hl7.org/fhir/datatypes.html#oid
    // not including the starting "urn:oid:" here
    final String UID_REGEX = "[0-2](\\.(0|[1-9][0-9]*))+";
    String uid;
    Person person = new Person(0L);
    uid = Utilities.randomDicomUid(person, 0, 0, 0);
    assertTrue(uid.matches(UID_REGEX));

    uid = Utilities.randomDicomUid(person, -1, -1, -1);
    assertTrue(uid.matches(UID_REGEX));

    uid = Utilities.randomDicomUid(person, 1, 2, 3);
    assertTrue(uid.matches(UID_REGEX));

    uid = Utilities.randomDicomUid(person, Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    assertTrue(uid.matches(UID_REGEX));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidStrToObject() {
    // Trying to parse a non-primitive class type results in an
    // IllegalArgumentException
    Utilities.strToObject(Date.class, "oops");
  }
}
