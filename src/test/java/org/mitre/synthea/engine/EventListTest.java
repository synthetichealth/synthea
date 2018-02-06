package org.mitre.synthea.engine;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class EventListTest {
  private EventList list;
  
  /**
   * Setup EventList for Tests.
   */
  @Before
  public void setup() {
    list = new EventList();
    list.create(0L, "foo", "setup", false);
    list.create(100L, "bar", "setup", false);
    list.create(200L, "foo", "setup", false);
    list.create(300L, "bar", "setup", false);
  }
  
  @Test
  public void eventListBefore() {
    List<Event> results = list.before(150L);
    assertTrue(results.size() == 2);
  }
  
  @Test
  public void eventListAfter() {
    List<Event> results = list.after(150L);
    assertTrue(results.size() == 2);
  }
  
  @Test
  public void eventListBeforeType() {
    List<Event> results = list.before(150L, "foo");
    assertTrue(results.size() == 1);
  }
  
  @Test
  public void eventListAfterType() {
    List<Event> results = list.after(150L, "foo");
    assertTrue(results.size() == 1);
  }
  
  @Test
  public void eventListToString() {
    assertNotNull(list.toString());
  }
}
