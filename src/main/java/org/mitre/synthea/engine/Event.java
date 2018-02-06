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

  public Event(long time, String type, String rule, boolean processed) {
    this.time = time;
    this.type = type;
    this.rule = rule;
    this.processed = processed;
  }

  public boolean hasAttributes() {
    return attributes != null;
  }

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
