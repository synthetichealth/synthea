package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ExpressedSymptom implements Cloneable, Serializable {
  
  private static final long serialVersionUID = 4322116644425686800L;
  
  // this class contains basic info regarding an expressed symptoms.
  // such as the cause and the associated value
  public class SymptomInfo implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686801L;
    // what is the cause of the symptom
    private String cause; 
    // what is the value associated to that symptom
    private Integer value;
    // At which time the symptom was set
    private Long time;
    
    /**
     * Create a new instance for the supplied cause, value and time.
     */
    public SymptomInfo(String cause, Integer value, Long time) {
      this.cause = cause;
      this.value = value;
      this.time = time;
    }
    
    public SymptomInfo clone() {
      return new SymptomInfo(this.cause, this.value, this.time);
    }
    
    public String getCause() {
      return cause;
    }

    public Integer getValue() {
      return value;
    }

    public Long getTime() {
      return time;
    }    
  }

  // this class encapsulates module-based infos regarding an expressed symptoms.
  public class SymptomSource implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686802L;
    
    ExpressedSymptom symptom = ExpressedSymptom.this;
    // From which module the expressed symptom was set    
    private String source;
    // what is the status from a given expressed symptom from a given module
    private boolean resolved;
    // when the expressed was last updated from the a given module
    private Long lastUpdateTime;
    // the time on which the expressed symptom was updated and the associated info.
    private Map<Long, SymptomInfo> timeInfos;  
    
    /**
     * Create a new instance for the supplied module source.
     */
    public SymptomSource(String source) {
      this.source = source;
      timeInfos = new HashMap<Long, ExpressedSymptom.SymptomInfo>();
      resolved = false;
      lastUpdateTime = null;
    }
    
    /**
     * Create shallow copy of this instance.
     */
    public SymptomSource clone() {
      SymptomSource data = new SymptomSource(this.source);
      data.resolved = this.resolved;
      data.lastUpdateTime = this.lastUpdateTime;
      data.timeInfos.putAll(this.timeInfos);
      return data;
    }

    public boolean isResolved() {
      return resolved;
    }

    public void resolve() {
      this.resolved = true;
    }

    public void activate() {
      this.resolved = false;
    }

    public Long getLastUpdateTime() {
      return lastUpdateTime;
    }

    public String getSource() {
      return source;
    }

    /**
     * Record a new symptom.
     */
    public void addInfo(String cause, long time, int value, Boolean addressed) {
      SymptomInfo info = new SymptomInfo(cause, value, time);
      timeInfos.put(Long.valueOf(time), info);
      lastUpdateTime = time;
      resolved = addressed;
    }

    /**
     * Get the current value of the symptom.
     */
    public Integer getCurrentValue() {
      if (timeInfos.containsKey(lastUpdateTime)) {
        return timeInfos.get(lastUpdateTime).getValue();
      }
      return null;
    }

    /**
     * Get the times for this symptom.
     */
    public Map<Long, SymptomInfo> getTimeInfos() {
      return timeInfos;
    }    
  }

  //keep track of the different sources of the expressed conditions
  private Map<String, SymptomSource> sources;
  private String name;
  
  public ExpressedSymptom(String name) {
    this.name = name;  
    sources = new HashMap<String, SymptomSource>();
  }
  
  /**
   * Create a shallow copy of this instance.
   */
  public ExpressedSymptom clone() {
    ExpressedSymptom data = new ExpressedSymptom(this.name);
    data.sources.putAll(this.sources);
    return data;
  }
  
  public Map<String, SymptomSource> getSources() {
    return sources;
  }
  
  /** this method updates the data structure wit a symptom being onset from a module.
   */
  public void onSet(String module, String cause, long time, int value, Boolean addressed) {    
    if (!sources.containsKey(module)) {
      sources.put(module, new SymptomSource(module));
    }
    sources.get(module).addInfo(cause, time, value, addressed);
  }
  
  /**
   * Method for retrieving the value associated to a given symptom. 
   * This correspond to the maximum value across all potential causes.
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
   * Method for retrieving the source with the high value not yet addressed. 
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
   * Method for retrieving the value associated to a given source. 
   */  
  public Integer getValueFromSource(String source) {
    if (!sources.containsKey(source)) {
      return null;
    }
    return sources.get(source).getCurrentValue();    
  }
  
  /**
   * Method for addressing a given source. 
   */  
  public void addressSource(String source) {
    if (sources.containsKey(source)) {
      sources.get(source).resolve();
    }     
  }
  
  /**
   * Method for retrieving the last time the symptom has been updated from a given module.
   */
  public Long getSymptomLastUpdatedTime(String module) {
    Long result = null;
    if (sources.containsKey(module)) {
      result = sources.get(module).getLastUpdateTime();
    }
    return result;
  }  
}
