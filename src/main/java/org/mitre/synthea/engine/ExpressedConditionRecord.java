package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.engine.ExpressedSymptom.SymptomInfo;
import org.mitre.synthea.engine.ExpressedSymptom.SymptomSource;

public class ExpressedConditionRecord implements Cloneable, Serializable {
  
  private static final long serialVersionUID = 4322116644425686900L;
  
  // this class contains basic info regarding an expressed conditions.
  // such as the onset time and end time
  public class ConditionPeriod implements Cloneable, Serializable {    
    private static final long serialVersionUID = 4322116644425686901L;    
    private Long onsetTime; 
    private Long endTime;
    
    public ConditionPeriod(Long onsetTime) {
      this.onsetTime = onsetTime;
      this.endTime = null;
    }
    
    public ConditionPeriod(Long onsetTime, Long endTime) {
      this.onsetTime = onsetTime;
      this.endTime = endTime;
    }
    
    public ConditionPeriod clone() {
      return new ConditionPeriod(this.onsetTime, this.endTime);
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
    private List<ConditionPeriod> timeInfos;
    
    public OnsetCondition(String name) {
      this.name = name;
      timeInfos = new LinkedList<ConditionPeriod>();
    }
    
    public OnsetCondition clone() {
      OnsetCondition data = new OnsetCondition(this.name);
      data.timeInfos.addAll(this.timeInfos);
      return data;
    }

    public String getName() {
      return name;
    }

    public List<ConditionPeriod> getTimeInfos() {
      return timeInfos;
    }
    
    public Long getLastOnsetTime() {
      if (timeInfos.isEmpty()) {
        return null;
      } else {
        int size = timeInfos.size();
        return timeInfos.get(size - 1).getOnsetTime();
      }
    }
    
    public Long getLastEndTime() {
      if (timeInfos.isEmpty()) {
        return null;
      } else {
        int size = timeInfos.size();
        return timeInfos.get(size - 1).getEndTime();
      }
    }
    
    public void addNewEntry(long onsetTime) {
      ConditionPeriod entry = new ConditionPeriod(Long.valueOf(onsetTime), null);
      timeInfos.add(entry);
    }
    
    public void endLastEntry(long time) {
      int size = timeInfos.size();
      if (size > 0) {
        timeInfos.get(size - 1).setEndTime(Long.valueOf(time));  
      }
    }
  }

  
  public class ModuleConditions implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686903L;
    // source from which the conditions are onset
    private String source;
    /** Data structure for storing onset conditions (init_time, end_time).*/
    private Map<String, OnsetCondition> onsetConditions;
    /** Data structure for storing mapping from state to condition names
     * This is useful when facing ConditionEnd.conditionOnSet attribute*/
    private Map<String, String> state2conditionMapping;
    
    public ModuleConditions(String source) {
      this.source = source;
      onsetConditions = new ConcurrentHashMap<String, OnsetCondition>();
      state2conditionMapping = new ConcurrentHashMap<String, String>();
    }
    
    public ModuleConditions clone() {
      ModuleConditions data = new ModuleConditions(this.source);
      data.state2conditionMapping.putAll(this.state2conditionMapping);
      data.onsetConditions.putAll(this.onsetConditions);
      return data;
    }
    
    public void onsetCondition(String condition, String state, long time) {
      if (!onsetConditions.containsKey(condition)) {
        onsetConditions.put(condition, new OnsetCondition(condition));
      }
      OnsetCondition onsetCondition = onsetConditions.get(condition);
      onsetCondition.addNewEntry(time);
      state2conditionMapping.put(state, condition);
    }
    
    public void endCondition(String condition, long time) {
      if (onsetConditions.containsKey(condition)) {
        onsetConditions.get(condition).endLastEntry(time);
      }
    }
    
    public Long getConditionLastOnsetTime(String condition) {
      if (onsetConditions.containsKey(condition)) {
        return onsetConditions.get(condition).getLastOnsetTime();
      }
      return null;
    }
    
    public Long getConditionLastEndTime(String condition) {
      if (onsetConditions.containsKey(condition)) {
        return onsetConditions.get(condition).getLastEndTime();
      }
      return null;
    }
    
    public String getConditionFromState(String state) {
      if (state2conditionMapping.containsKey(state)) {
        return state2conditionMapping.get(state);
      }
      return null;
    }

    public Map<String, OnsetCondition> getOnsetConditions() {
      return onsetConditions;
    }
  }
  
  // a map:  module.name -> Conditions
  private Map<String, ModuleConditions> sources;
  
  public ExpressedConditionRecord() {
    sources = new ConcurrentHashMap<String, ModuleConditions>();
  }
  
  public ExpressedConditionRecord clone() {
    ExpressedConditionRecord data = new ExpressedConditionRecord();
    data.sources.putAll(this.sources);
    return data;
  }
  
  public Map<String, ModuleConditions> getSources() {
    return sources;
  }
  
  /**
   * Method that is used to update the onsetConditions field when
   * a ConditionOnset state is processed.
   */
  public void onConditionOnset(String module, String state, String condition, long time) {
    if (!sources.containsKey(module)) {
      sources.put(module, new ModuleConditions(module));
    }
    ModuleConditions moduleConditions = sources.get(module);
    moduleConditions.onsetCondition(condition, state, time);
  }
    
  /**
   * Method that is used to retrieve the last time a condition
   * has been onset from a given module.
   */
  public Long getConditionLastOnsetTimeFromModule(String module, String condition) {
    Long result = null;
    if (sources.containsKey(module)) {
      ModuleConditions moduleConditions = sources.get(module);
      result = moduleConditions.getConditionLastOnsetTime(condition);
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
      ModuleConditions moduleConditions = sources.get(module);
      result = moduleConditions.getConditionLastEndTime(condition);
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
    if (isModulePresent) {
      result = sources.get(module).getConditionFromState(state);
    }
    return result;
  }
    
  /**
   * Method that is used to update the onsetConditions field when
   * a ConditionEnd state is processed.
   */
  public void onConditionEnd(String module, String condition, long time) {
    boolean isModulePresent = sources.containsKey(module);
    if (isModulePresent) {
      sources.get(module).endCondition(condition, time);
    }
  }
  
  
  /**
   * Get the symptoms that were expressed as parts of 
   * the conditions the person suffers from.
   * The returned data is a map of [Age/time: Condition: Symptom: Value].
   * It captures the conditions a person has suffered from together 
   * with the related symptoms at different age/time. Only the value onset 
   * at the first time for a given symptom is saved.
   * The parameter symptoms contains a map of [symptom name: ExpressedSymptom info] is
   * the set of expressed symptoms by a person during his/her lifetime.
   */
  public Map<Long, Map<String, Map<String, Integer>>> getConditionSymptoms(
      Map<String, ExpressedSymptom> symptoms) {
    Map<Long, Map<String, Map<String, Integer>>> conditionSymtoms;
    conditionSymtoms = new ConcurrentHashMap<Long, Map<String, Map<String, Integer>>>();     
    for (String module : sources.keySet()) {
      ModuleConditions moduleConditions = sources.get(module);
      for (String condition : moduleConditions.getOnsetConditions().keySet()) {
        List<ConditionPeriod> infos = moduleConditions.getOnsetConditions().get(
            condition).getTimeInfos();
        for (ConditionPeriod entry : infos) {
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
            ExpressedSymptom expressedSymptom = symptoms.get(type);
            if (expressedSymptom.getSources().containsKey(module)) {
              SymptomSource symptomSource = expressedSymptom.getSources().get(module);
              Map<Long, SymptomInfo> timedTypedSymptoms = symptomSource.getTimeInfos();
              // get the value that correspond to the earliest time belonging
              // to the interval [begin, end] of the condition if any.
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
