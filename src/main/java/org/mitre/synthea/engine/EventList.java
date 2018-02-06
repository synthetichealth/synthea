package org.mitre.synthea.engine;

import java.util.ArrayList;
import java.util.List;

public class EventList {
  private List<Event> events = new ArrayList<Event>();
  private final Object lock = new Object();

  /**
   * Get the last event of the given type.
   * 
   * @param type
   *          : the type of event
   * @return the last Event of the given type.
   */
  public Event event(String type) {
    Event retVal = null;
    synchronized (lock) {
      for (int i = events.size() - 1; i >= 0; i--) {
        Event event = events.get(i);
        if (event.type.equals(type)) {
          retVal = event;
          break;
        }
      }
    }
    return retVal;
  }

  public void create(long time, String type, String rule, boolean processed) {
    Event event = new Event(time, type, rule, processed);
    synchronized (lock) {
      events.add(event);
    }
  }

  /**
   * Get all events before the given time.
   * 
   * @param time
   *          : the cut off date
   * @return non-null list of events before the cutoff date.
   */
  public List<Event> before(long time) {
    List<Event> retVal = new ArrayList<Event>();
    synchronized (lock) {
      for (Event event : events) {
        if (event.time <= time) {
          retVal.add(event);
        } else if (event.time > time) {
          break;
        }
      }
    }
    return retVal;
  }

  /**
   * Get all events of given type before the given time.
   * 
   * @param time
   *          : the cut off date
   * @param type
   *          : the type of event
   * @return non-null list of events before the cutoff date.
   */
  public List<Event> before(long time, String type) {
    List<Event> retVal = new ArrayList<Event>();
    synchronized (lock) {
      for (Event event : events) {
        if (event.type.equals(type) && event.time <= time) {
          retVal.add(event);
        } else if (event.time > time) {
          break;
        }
      }
    }
    return retVal;
  }

  /**
   * Get all events after the given time.
   * 
   * @param time
   *          : the cut off date
   * @return non-null list of events after the cutoff date.
   */
  public List<Event> after(long time) {
    List<Event> retVal = new ArrayList<Event>();
    synchronized (lock) {
      for (Event event : events) {
        if (event.time >= time) {
          retVal.add(event);
        }
      }
    }
    return retVal;
  }

  /**
   * Get all events of given type after the given time.
   * 
   * @param time
   *          : the cut off date
   * @param type
   *          : the type of event
   * @return non-null list of events after the cutoff date.
   */
  public List<Event> after(long time, String type) {
    List<Event> retVal = new ArrayList<Event>();
    synchronized (lock) {
      for (Event event : events) {
        if (event.time >= time && event.type.equals(type)) {
          retVal.add(event);
        }
      }
    }
    return retVal;
  }

  public String toString() {
    return String.format("EventList (%d events)", events.size());
  }

}
