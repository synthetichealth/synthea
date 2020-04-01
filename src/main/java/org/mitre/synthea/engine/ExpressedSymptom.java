package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExpressedSymptom implements Cloneable, Serializable {
  
  private static final long serialVersionUID = 4322116644425686800L;
  
  // this class contains basic info regarding an expressed symptoms.
  // such as the cause and the associated value
  public class SymptomInfo implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686801L;
    // what is the cause of the symptom
    public String cause; 
    // what is the value associated to that symptom
    public Integer value;
    
    public SymptomInfo(String cause, Integer value) {
      this.cause = cause;
      this.value = value;
    }
    
    public SymptomInfo clone() {
      return new SymptomInfo(this.cause, this.value);
    }
    
    public String getCause() {
      return cause;
    }

    public Integer getValue() {
      return value;
    }    
  }

  // this class encapsulates module-based infos regarding an expressed symptoms.
  public class SymptomSource implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686802L;
    
    ExpressedSymptom symptom = ExpressedSymptom.this;
    // From which module the expressed symptom was set    
    private String source;
    // what is the status from a given expressed symptom from a given module
    private boolean status;
    // when the expressed was last updated from the a given module
    private Long lastUpdateTime;
    // the time on which the expressed symtom was updated and the associated info.
    public Map<Long, SymptomInfo> timeInfos;  
    
    public SymptomSource(String source) {
      this.source = source;
      timeInfos = new ConcurrentHashMap<Long, ExpressedSymptom.SymptomInfo>();
      status = false;
      lastUpdateTime = null;
    }
    
    public SymptomSource clone() {
      SymptomSource data = new SymptomSource(this.source);
      data.status = this.status;
      data.lastUpdateTime = this.lastUpdateTime;
      data.timeInfos.putAll(new ConcurrentHashMap<Long, SymptomInfo>(this.timeInfos));
      return data;
    }

    public boolean isStatus() {
      return status;
    }

    public void setStatus(boolean status) {
      this.status = status;
    }

    public Long getLastUpdateTime() {
      return lastUpdateTime;
    }

    public void setLastUpdateTime(Long lastUpdateTime) {
      this.lastUpdateTime = lastUpdateTime;
    }

    public String getSource() {
      return source;
    }
  }

  //keep track of the different sources of the expressed conditions
  public Map<String, SymptomSource> sources;
  private String name;
  
  public ExpressedSymptom(String name) {
    this.name = name;  
    sources = new ConcurrentHashMap<String, SymptomSource>();
  }
  
  public ExpressedSymptom clone() {
    ExpressedSymptom data = new ExpressedSymptom(this.name);
    data.sources.putAll(new ConcurrentHashMap<String, SymptomSource>(this.sources));
    return data;
  }
  
  /** this method updates the data structure wit a symptom being onset from a module.
   */
  public void onSet(String module, String cause, long time, int value, Boolean addressed) {    
    if (!sources.containsKey(module)) {
      sources.put(module, new SymptomSource(module));
    }
    SymptomInfo info = new SymptomInfo(cause, value);
    sources.get(module).timeInfos.put(Long.valueOf(time), info);
    sources.get(module).setLastUpdateTime(Long.valueOf(time));
    sources.get(module).setStatus(addressed);
  }
  
  /**
   * Method for retrieving the value associated to a given symptom. 
   * This correspond to the maximum value across all potential causes.
   */
  public int getSymptom() {
    int max = 0;
    for (String module : sources.keySet()) {
      Long lastUpdateTime = sources.get(module).getLastUpdateTime();
      Boolean status = sources.get(module).isStatus();
      if (sources.get(module).timeInfos.get(lastUpdateTime).getValue() > max && !status) {
        max = sources.get(module).timeInfos.get(lastUpdateTime).getValue();
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
      Long lastUpdateTime = sources.get(module).getLastUpdateTime();
      Boolean status = sources.get(module).isStatus();
      if (result == null && !status) {
        result = module;
        max = sources.get(module).timeInfos.get(lastUpdateTime).getValue();
      } else if (sources.get(module).timeInfos.get(lastUpdateTime).getValue() > max && !status) {
        result = module;
        max = sources.get(module).timeInfos.get(lastUpdateTime).getValue();
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
    Long lastUpdateTime = sources.get(source).getLastUpdateTime();
    return sources.get(source).timeInfos.get(lastUpdateTime).getValue();    
  }
  
  /**
   * Method for addressing a given source. 
   */  
  public void addressSource(String source) {
    if (sources.containsKey(source)) {
      sources.get(source).setStatus(true);
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
