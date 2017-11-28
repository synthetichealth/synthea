package org.mitre.synthea.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EventTest {
  @Test
  public void eventHasNoAttributes() {
    Event event = new Event(0L, Event.BIRTH, "eventHasNoAttributes", false);
    assertFalse(event.hasAttributes());
  }
  
  @Test
  public void eventHasAttributes() {
    Event event = new Event(0L, Event.BIRTH, "eventHasAttributes", false);
    assertNotNull(event.attributes());
    assertTrue(event.hasAttributes());
  }
  
  @Test
  public void eventToString() {
    Event event = new Event(0L, Event.BIRTH, "eventToString", false);
    assertNotNull(event.toString());
  }
}
