package org.mitre.synthea.engine;

import java.util.HashMap;
import java.util.Map;

public class Event {

  public static final String BIRTH = "birth";
  public static final String DEATH = "death";

  public long time;
  public String type;
  public String rule;
  public boolean processed = false;
  private Map<String, Object> attributes;

  /**
   * Event constructor.
   * @param time The time the event occurred or should occur.
   * @param type The type of event. For example, "birth" or "death".
   * @param rule The name of the rule or method that created the event (for debugging).
   * @param processed Whether or not the event has been processed. For example, a "death"
   *     event may be set in the future, and only processed when that time has passed.
   */
  public Event(long time, String type, String rule, boolean processed) {
    this.time = time;
    this.type = type;
    this.rule = rule;
    this.processed = processed;
  }

  public boolean hasAttributes() {
    return attributes != null;
  }

  /**
   * Get any attributes associated with this Event.
   * Creates the attributes map if it does not exist.
   * @return Map of attributes.
   */
  public Map<String, Object> attributes() {
    if (attributes == null) {
      attributes = new HashMap<String, Object>();
    }
    return attributes;
  }

  public String toString() {
    return String.format("%d %s %s %s", time, type, rule, processed);
  }
}
