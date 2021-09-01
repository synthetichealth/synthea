package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.engine.ExpressedSymptom.SymptomInfo;
import org.mitre.synthea.engine.ExpressedSymptom.SymptomSource;
import org.mitre.synthea.world.agents.Person;

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

  /**
   * A condition with a set of onset and end time entries.
   */
  public class OnsetCondition implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686902L;

    // name of the condition
    private String name;
    private List<ConditionPeriod> timeInfos;

    public OnsetCondition(String name) {
      this.name = name;
      timeInfos = new LinkedList<ConditionPeriod>();
    }

    /**
     * Create a shallow copy of this object.
     */
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

    /**
     * Get the last recorded onset time.
     */
    public Long getLastOnsetTime() {
      if (timeInfos.isEmpty()) {
        return null;
      } else {
        int size = timeInfos.size();
        return timeInfos.get(size - 1).getOnsetTime();
      }
    }

    /**
     * Get the last recorded end time.
     */
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

    /**
     * Set the end time the last entry.
     */
    public void endLastEntry(long time) {
      int size = timeInfos.size();
      if (size > 0) {
        timeInfos.get(size - 1).setEndTime(Long.valueOf(time));
      }
    }
  }

  /**
   * Used to record condition onset by modules.
   */
  public class ModuleConditions implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686903L;
    // source from which the conditions are onset
    private String source;
    /** Data structure for storing onset conditions (init_time, end_time).*/
    private Map<String, OnsetCondition> onsetConditions;
    /** Data structure for storing mapping from state to condition names
     * This is useful when facing ConditionEnd.conditionOnSet attribute*/
    private Map<String, String> state2conditionMapping;

    /**
     * Create new instance for the specified module name.
     */
    public ModuleConditions(String source) {
      this.source = source;
      onsetConditions = new HashMap<String, OnsetCondition>();
      state2conditionMapping = new HashMap<String, String>();
    }

    /**
     * Create a shallow copy of this instance.
     */
    public ModuleConditions clone() {
      ModuleConditions data = new ModuleConditions(this.source);
      data.state2conditionMapping.putAll(this.state2conditionMapping);
      data.onsetConditions.putAll(this.onsetConditions);
      return data;
    }

    /**
     * Record the onset of a condition.
     */
    public void onsetCondition(String condition, String state, long time) {
      if (!onsetConditions.containsKey(condition)) {
        onsetConditions.put(condition, new OnsetCondition(condition));
      }
      OnsetCondition onsetCondition = onsetConditions.get(condition);
      onsetCondition.addNewEntry(time);
      state2conditionMapping.put(state, condition);
    }

    /**
     * Record the end of a condition.
     */
    public void endCondition(String condition, long time) {
      if (onsetConditions.containsKey(condition)) {
        onsetConditions.get(condition).endLastEntry(time);
      }
    }

    /**
     * Get the last recorded onset time.
     */
    public Long getConditionLastOnsetTime(String condition) {
      if (onsetConditions.containsKey(condition)) {
        return onsetConditions.get(condition).getLastOnsetTime();
      }
      return null;
    }

    /**
     * Get the last recorded end time.
     */
    public Long getConditionLastEndTime(String condition) {
      if (onsetConditions.containsKey(condition)) {
        return onsetConditions.get(condition).getLastEndTime();
      }
      return null;
    }

    /**
     * Get the condition for the supplied state.
     */
    public String getConditionFromState(String state) {
      if (state2conditionMapping.containsKey(state)) {
        return state2conditionMapping.get(state);
      }
      return null;
    }

    /**
     * Get the recorded conditions and onset/end information.
     * @return a map of condition name to onset/end records.
     */
    public Map<String, OnsetCondition> getOnsetConditions() {
      return onsetConditions;
    }
  }

  // this class represents a condition with its associated symptoms
  public class ConditionWithSymptoms implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686904L;

    private String conditionName;
    private Long onsetTime;
    private Long endTime;
    // Data structure for storing symptoms and associated values during the condition
    private Map<String, List<Integer>> symptoms;

    /**
     * Create a new instance for the supplied condition name, onset and end times.
     */
    public ConditionWithSymptoms(String name, Long onsetTime, Long endTime) {
      this.conditionName = name;
      this.onsetTime = onsetTime;
      this.endTime = endTime;
      this.symptoms = new HashMap<String, List<Integer>>();
    }

    /**
     * Create a shallow copy of this instance.
     */
    public ConditionWithSymptoms clone() {
      ConditionWithSymptoms data = new ConditionWithSymptoms(conditionName, onsetTime, endTime);
      data.symptoms.putAll(this.symptoms);
      return data;
    }

    /**
     * Record a symptom for the supplied module.
     * @param name symptom name.
     * @param symptomSource module origin of the symptom.
     */
    public void addSymptoms(String name, SymptomSource symptomSource) {
      Map<Long, SymptomInfo> timedTypedSymptoms = symptomSource.getTimeInfos();
      // get the value that correspond to the all times belonging
      // to the interval [begin, end] of the condition if any.
      List<Long> allTimes = new ArrayList<Long>();
      for (Long time : timedTypedSymptoms.keySet()) {
        boolean greatThanBegin = time >= onsetTime;
        boolean lowThanEnd = (endTime != null  && time <= endTime) || (endTime == null);
        if (greatThanBegin && lowThanEnd) {
          allTimes.add(time);
        }
      }
      if (allTimes.size() > 0) {
        Collections.sort(allTimes);
        if (!symptoms.containsKey(name)) {
          symptoms.put(name, new ArrayList<Integer>());
        }
        for (Long time : allTimes) {
          Integer value = timedTypedSymptoms.get(time).getValue();
          symptoms.get(name).add(value);
        }
      }
    }

    public Long getOnsetTime() {
      return onsetTime;
    }

    public Long getEndTime() {
      return endTime;
    }

    public String getConditionName() {
      return conditionName;
    }

    public Map<String, List<Integer>> getSymptoms() {
      return symptoms;
    }
  }

  // a map:  module.name -> Conditions
  private Map<String, ModuleConditions> sources;
  Person person;

  public ExpressedConditionRecord(Person person) {
    this.person = person;
    sources = new HashMap<String, ModuleConditions>();
  }

  /**
   * Create a shallow clone of this instance.
   */
  public ExpressedConditionRecord clone() {
    ExpressedConditionRecord data = new ExpressedConditionRecord(this.person);
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
   * The returned data is a map of [time: List of ConditionWithSymtoms].
   * It captures the conditions a person has suffered from together
   * with the related symptoms at different age/time.
   */
  public Map<Long, List<ConditionWithSymptoms>> getConditionSymptoms() {
    Map<String, ExpressedSymptom> symptoms = person.getExpressedSymptoms();
    Map<Long, List<ConditionWithSymptoms>> result;
    result = new HashMap<Long, List<ConditionWithSymptoms>>();    
    for (String module : sources.keySet()) {
      ModuleConditions moduleConditions = sources.get(module);
      for (String condition : moduleConditions.getOnsetConditions().keySet()) {
        List<ConditionPeriod> infos = moduleConditions.getOnsetConditions().get(
            condition).getTimeInfos();
        for (ConditionPeriod entry : infos) {
          Long begin = entry.getOnsetTime();
          Long end = entry.getEndTime();
          if (!result.containsKey(begin)) {
            result.put(begin, new LinkedList<ConditionWithSymptoms>());
          }
          ConditionWithSymptoms conditionWithSymptoms = new ConditionWithSymptoms(
              condition, begin, end
          );
          for (String type : symptoms.keySet()) {
            ExpressedSymptom expressedSymptom = symptoms.get(type);
            if (expressedSymptom.getSources().containsKey(module)) {
              SymptomSource symptomSource = expressedSymptom.getSources().get(module);
              conditionWithSymptoms.addSymptoms(type, symptomSource);
            }
          }
          result.get(begin).add(conditionWithSymptoms);
        }
      }
    }
    return result;
  }
}
