package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.engine.ExpressedSymptom.SymptomInfo;

public class ExpressedConditionRecord implements Cloneable, Serializable {
  
  private static final long serialVersionUID = 4322116644425686900L;
  
  // this class contains basic info regarding an expressed conditions.
  // such as the onset time and end time
  public class ConditionInfo implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686901L;    
    private Long onsetTime; 
    private Long endTime;
    
    public ConditionInfo(Long onsetTime) {
      this.onsetTime = onsetTime;
      this.endTime = null;
    }
    
    public ConditionInfo(Long onsetTime, Long endTime) {
      this.onsetTime = onsetTime;
      this.endTime = endTime;
    }
    
    public ConditionInfo clone() {
      return new ConditionInfo(this.onsetTime, this.endTime);
    }

    public Long getEndTime() {
      return endTime;
    }

    public void setEndTime(Long endTime) {
      this.endTime = endTime;
    }

    public Long getOnsetTime() {
      return onsetTime;
    }        
  }

  // this class encapsulates infos regarding onset conditions.
  public class OnsetCondition implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686902L;
    
    // name of the condition    
    private String name;
    public List<ConditionInfo> timeInfos;
    
    public OnsetCondition(String name) {
      this.name = name;
      timeInfos = new LinkedList<ConditionInfo>();
    }
    
    public OnsetCondition clone() {
      OnsetCondition data = new OnsetCondition(this.name);
      data.timeInfos.addAll(new LinkedList<ConditionInfo>(this.timeInfos));
      return data;
    }

    public String getName() {
      return name;
    }

    public List<ConditionInfo> getTimeInfos() {
      return timeInfos;
    }    
  }

  
  public class SourceCondition implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686903L;
    // source from which the conditions are onset
    private String source;
    /** Data structure for storing onset conditions (init_time, end_time).*/
    public Map<String, OnsetCondition> onsetConditions;
    /** Data structure for storing mapping from state to condition names
     * This is useful when facing ConditionEnd.conditionOnSet attribute*/
    public Map<String, String> state2conditionMapping;
    
    public SourceCondition(String source) {
      this.source = source;
      onsetConditions = new ConcurrentHashMap<String, OnsetCondition>();
      state2conditionMapping = new ConcurrentHashMap<String, String>();
    }
    
    public SourceCondition clone() {
      SourceCondition data = new SourceCondition(this.source);
      data.state2conditionMapping.putAll(
          new ConcurrentHashMap<String, String>(this.state2conditionMapping));
      data.onsetConditions.putAll(
          new ConcurrentHashMap<String, OnsetCondition>(this.onsetConditions));
      return data;
    }
  }
  
  public Map<String, SourceCondition> sources;
  
  public ExpressedConditionRecord() {
    sources = new ConcurrentHashMap<String, SourceCondition>();
  }
  
  public ExpressedConditionRecord clone() {
    ExpressedConditionRecord data = new ExpressedConditionRecord();
    data.sources.putAll(new ConcurrentHashMap<String, SourceCondition>(this.sources));
    return data;
  }
  
  /**
   * Method that is used to update the onsetConditions field when
   * a ConditionOnset state is processed.
   */
  public void onConditionOnset(String module, String state, String condition, long time) {
    if (!sources.containsKey(module)) {
      sources.put(module, new SourceCondition(module));
    }
    SourceCondition moduleConditions = sources.get(module);
    
    if (!moduleConditions.onsetConditions.containsKey(condition)) {
      moduleConditions.onsetConditions.put(condition, new OnsetCondition(condition));
    }
    OnsetCondition onsetCondition = moduleConditions.onsetConditions.get(condition);
    
    ConditionInfo entry = new ConditionInfo(Long.valueOf(time), null);
    onsetCondition.timeInfos.add(entry);
    moduleConditions.state2conditionMapping.put(state, condition);
  }
  

    
  /**
   * Method that is used to retrieve the last time a condition
   * has been onset from a given module.
   */
  public Long getConditionLastOnsetTimeFromModule(String module, String condition) {
    Long result = null;
    if (sources.containsKey(module)) {
      SourceCondition moduleConditions = sources.get(module);
      if (moduleConditions.onsetConditions.containsKey(condition)) {
        int size = moduleConditions.onsetConditions.get(condition).timeInfos.size();
        if (size > 0) {
          result = moduleConditions.onsetConditions.get(condition).timeInfos.get(
              size - 1).getOnsetTime();
        }
      }
    }
    return result;
  }
    
  /**
   * Method that is used to retrieve the last time a ConditionEnd state
   * has been processed for a given condition from a given module.
   */
  public Long getConditionLastEndTimeFromModule(String module, String condition) {
    Long result = null;
    if (sources.containsKey(module)) {
      SourceCondition moduleConditions = sources.get(module);
      if (moduleConditions.onsetConditions.containsKey(condition)) {
        int size = moduleConditions.onsetConditions.get(condition).timeInfos.size();
        if (size > 0) {
          result = moduleConditions.onsetConditions.get(condition).timeInfos.get(
              size - 1).getEndTime();
        }
      }
    }
    return result;
  }
    
  /**
   * Method for retrieving the condition name from a state name.
   * Useful when dealing with ConditionEnd.conditionOnSet attribute.
   */
  public String getConditionFromState(String module, String state) {
    String result = null;
    boolean isModulePresent = sources.containsKey(module);
    if (isModulePresent && sources.get(module).state2conditionMapping.containsKey(state)) {
      result = sources.get(module).state2conditionMapping.get(state);
    }
    return result;
  }
    
  /**
   * Method that is used to update the onsetConditions field when
   * a ConditionEnd state is processed.
   */
  public void onConditionEnd(String module, String condition, long time) {
    boolean isModulePresent = sources.containsKey(module);
    if (isModulePresent && sources.get(module).onsetConditions.containsKey(condition)) {
      int size = sources.get(module).onsetConditions.get(condition).timeInfos.size();
      sources.get(module).onsetConditions.get(condition).timeInfos.get(
          size - 1).setEndTime(Long.valueOf(time));
    }
  }
  
  
  /**
   * Get the symptoms that were expressed as parts of 
   * the conditions the person suffers from.
   */
  public Map<Long, Map<String, Map<String, Integer>>> getConditionSymptoms(
      Map<String, ExpressedSymptom> symptoms) {
    Map<Long, Map<String, Map<String, Integer>>> conditionSymtoms;
    conditionSymtoms = new ConcurrentHashMap<Long, Map<String, Map<String, Integer>>>();     
    for (String module : sources.keySet()) {
      for (String condition : sources.get(module).onsetConditions.keySet()) {
        List<ConditionInfo> infos = sources.get(module).onsetConditions.get(
            condition).timeInfos;
        for (ConditionInfo entry : infos) {
          Long begin = entry.getOnsetTime();
          Long end = entry.getEndTime();
          if (!conditionSymtoms.containsKey(begin)) {
            conditionSymtoms.put(
                begin, new ConcurrentHashMap<String, Map<String, Integer>>()
            );
          }
          if (!conditionSymtoms.get(begin).containsKey(condition)) {
            conditionSymtoms.get(begin).put(condition, new ConcurrentHashMap<String, Integer>());
          }
          for (String type : symptoms.keySet()) {
            if (symptoms.get(type).sources.containsKey(module)) {
              Map<Long, SymptomInfo> timedTypedSymptoms = symptoms.get(type).sources.get(
                  module).timeInfos;
              // get the value that correspond to the earliest time belonging
              // to the interval [begin, end] if any.
              Long minKey = null;
              for (Long time : timedTypedSymptoms.keySet()) {
                boolean greatThanBegin = time >= begin;
                boolean lowThanEnd = (end != null  && time <= end) || (end == null);
                boolean isEarliest = (minKey == null) || (minKey != null && time <= minKey);
                if (greatThanBegin && lowThanEnd && isEarliest) {
                  minKey = time;
                }
              }
              if (minKey != null) {
                Integer value = timedTypedSymptoms.get(minKey).getValue();
                conditionSymtoms.get(begin).get(condition).put(type, value);
              }        
            }
          }
        }
      }
    }
    return conditionSymtoms;
  }  
}
