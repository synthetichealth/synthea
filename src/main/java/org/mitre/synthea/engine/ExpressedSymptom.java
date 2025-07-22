package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.export.JSONSkip;

/**
 * Represents an expressed symptom with associated sources and their details.
 */
public class ExpressedSymptom implements Cloneable, Serializable {

  private static final long serialVersionUID = 4322116644425686800L;

  /**
   * Contains basic information regarding an expressed symptom, such as the cause,
   * value, and the time it was set.
   */
  public class SymptomInfo implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686801L;
    /** The cause of the symptom. */
    private String cause;
    /** The value associated with the symptom. */
    private Integer value;
    /** The time the symptom was set. */
    private Long time;

    /**
     * Create a new instance for the supplied cause, value, and time.
     *
     * @param cause the cause of the symptom
     * @param value the value associated with the symptom
     * @param time the time the symptom was set
     */
    public SymptomInfo(String cause, Integer value, Long time) {
      this.cause = cause;
      this.value = value;
      this.time = time;
    }

    /**
     * Create a clone of this SymptomInfo instance.
     *
     * @return a cloned instance of SymptomInfo
     */
    public SymptomInfo clone() {
      return new SymptomInfo(this.cause, this.value, this.time);
    }

    /**
     * Get the cause of the symptom.
     *
     * @return the cause of the symptom
     */
    public String getCause() {
      return cause;
    }

    /**
     * Get the value associated with the symptom.
     *
     * @return the value of the symptom
     */
    public Integer getValue() {
      return value;
    }

    /**
     * Get the time the symptom was set.
     *
     * @return the time the symptom was set
     */
    public Long getTime() {
      return time;
    }
  }

  /**
   * Encapsulates module-based information regarding an expressed symptom.
   */
  public class SymptomSource implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686802L;
    /** The current instance of ExpressedSymptom */
    @JSONSkip
    ExpressedSymptom symptom = ExpressedSymptom.this;
    /** From which module the expressed symptom was set */
    private String source;
    /** what is the status from a given expressed symptom from a given module */
    private boolean resolved;
    /** when the expressed was last updated from the a given module */
    private Long lastUpdateTime;
    /** the time on which the expressed symptom was updated and the associated info. */
    private Map<Long, SymptomInfo> timeInfos;

    /**
     * Create a new instance for the supplied module source.
     *
     * @param source the module source of the symptom
     */
    public SymptomSource(String source) {
      this.source = source;
      timeInfos = new ConcurrentHashMap<Long, ExpressedSymptom.SymptomInfo>();
      resolved = false;
      lastUpdateTime = null;
    }

    /**
     * Create a shallow copy of this instance.
     *
     * @return a cloned instance of SymptomSource
     */
    public SymptomSource clone() {
      SymptomSource data = new SymptomSource(this.source);
      data.resolved = this.resolved;
      data.lastUpdateTime = this.lastUpdateTime;
      data.timeInfos.putAll(this.timeInfos);
      return data;
    }

    /**
     * Check if the symptom is resolved.
     *
     * @return true if resolved, false otherwise
     */
    public boolean isResolved() {
      return resolved;
    }

    /**
     * Mark the symptom as resolved.
     */
    public void resolve() {
      this.resolved = true;
    }

    /**
     * Activate the symptom, marking it as unresolved.
     */
    public void activate() {
      this.resolved = false;
    }

    /**
     * Get the last update time of the symptom.
     *
     * @return the last update time
     */
    public Long getLastUpdateTime() {
      return lastUpdateTime;
    }

    /**
     * Get the source of the symptom.
     *
     * @return the source of the symptom
     */
    public String getSource() {
      return source;
    }

    /**
     * Record a new symptom.
     *
     * @param cause the cause of the symptom
     * @param time the time the symptom was recorded
     * @param value the value of the symptom
     * @param addressed whether the symptom is addressed
     */
    public void addInfo(String cause, long time, int value, Boolean addressed) {
      SymptomInfo info = new SymptomInfo(cause, value, time);
      timeInfos.put(Long.valueOf(time), info);
      lastUpdateTime = time;
      resolved = addressed;
    }

    /**
     * Get the current value of the symptom.
     *
     * @return the current value of the symptom
     */
    public Integer getCurrentValue() {
      if (lastUpdateTime != null && timeInfos.containsKey(lastUpdateTime)) {
        return timeInfos.get(lastUpdateTime).getValue();
      }
      return null;
    }

    /**
     * Get the times and associated information for this symptom.
     *
     * @return a map of times to SymptomInfo
     */
    public Map<Long, SymptomInfo> getTimeInfos() {
      return timeInfos;
    }
  }

  /** The sources of the expressed conditions. */
  private Map<String, SymptomSource> sources;
  /** The name of the symptom */
  private String name;

  /**
   * Create a new ExpressedSymptom instance with the given name.
   *
   * @param name the name of the symptom
   */
  public ExpressedSymptom(String name) {
    this.name = name;
    sources = new ConcurrentHashMap<String, SymptomSource>();
  }

  /**
   * Create a shallow copy of this instance.
   *
   * @return a cloned instance of ExpressedSymptom
   */
  public ExpressedSymptom clone() {
    ExpressedSymptom data = new ExpressedSymptom(this.name);
    data.sources.putAll(this.sources);
    return data;
  }

  /**
   * Get the sources of the symptom.
   *
   * @return a map of sources to SymptomSource
   */
  public Map<String, SymptomSource> getSources() {
    return sources;
  }

  /**
   * Update the data structure with a symptom being onset from a module.
   *
   * @param module the module setting the symptom
   * @param cause the cause of the symptom
   * @param time the time the symptom was set
   * @param value the value of the symptom
   * @param addressed whether the symptom is addressed
   */
  public void onSet(String module, String cause, long time, int value, Boolean addressed) {
    if (!sources.containsKey(module)) {
      sources.put(module, new SymptomSource(module));
    }
    sources.get(module).addInfo(cause, time, value, addressed);
  }

  /**
   * Retrieve the value associated with a given symptom.
   * This corresponds to the maximum value across all potential causes.
   *
   * @return the maximum value of the symptom
   */
  public int getSymptom() {
    int max = 0;
    for (String module : sources.keySet()) {
      Integer value = sources.get(module).getCurrentValue();
      Boolean isResolved = sources.get(module).isResolved();
      if (value != null && value.intValue() > max && !isResolved) {
        max = value.intValue();
      }
    }
    return max;
  }

  /**
   * Retrieve the source with the highest value not yet addressed.
   *
   * @return the source with the highest value
   */
  public String getSourceWithHighValue() {
    String result = null;
    int max = 0;
    for (String module : sources.keySet()) {
      Boolean isResolved = sources.get(module).isResolved();
      Integer value = sources.get(module).getCurrentValue();
      if (result == null && value != null && !isResolved) {
        result = module;
        max = value.intValue();
      } else if (value != null && value.intValue() > max && !isResolved) {
        result = module;
        max = value.intValue();
      }
    }
    return result;
  }

  /**
   * Retrieve the value associated with a given source.
   *
   * @param source the source of the symptom
   * @return the value of the symptom from the source
   */
  public Integer getValueFromSource(String source) {
    if (source == null || !sources.containsKey(source)) {
      return null;
    }
    return sources.get(source).getCurrentValue();
  }

  /**
   * Address a given source.
   * Resolves the source if it exists in the symptom's sources.
   *
   * @param source the source to address
   */
  public void addressSource(String source) {
    if (source != null && sources.containsKey(source)) {
      sources.get(source).resolve();
    }
  }

  /**
   * Retrieve the last time the symptom was updated from a given module.
   *
   * @param module the module of the symptom
   * @return the last update time of the symptom
   */
  public Long getSymptomLastUpdatedTime(String module) {
    Long result = null;
    if (module != null && sources.containsKey(module)) {
      result = sources.get(module).getLastUpdateTime();
    }
    return result;
  }
}
