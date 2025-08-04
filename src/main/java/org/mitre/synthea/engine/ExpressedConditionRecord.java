package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.engine.ExpressedSymptom.SymptomInfo;
import org.mitre.synthea.engine.ExpressedSymptom.SymptomSource;
import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.world.agents.Person;

/**
 * Represents a record of conditions expressed by a person, including onset and end times,
 * and associated symptoms.
 */
public class ExpressedConditionRecord implements Cloneable, Serializable {

  private static final long serialVersionUID = 4322116644425686900L;

  /**
   * this class contains basic info regarding an expressed conditions.
   * such as the onset time and end time
   */
  public class ConditionPeriod implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686901L;

    /** The time when the condition ended. */
    private Long endTime;

    /** The time when the condition started. */
    private Long onsetTime;

    /**
     * Constructs a ConditionPeriod with the specified onset time.
     * @param onsetTime the time the condition started
     */
    public ConditionPeriod(Long onsetTime) {
      this.onsetTime = onsetTime;
      this.endTime = null;
    }

    /**
     * Constructs a ConditionPeriod with the specified onset and end times.
     * @param onsetTime the time the condition started
     * @param endTime the time the condition ended
     */
    public ConditionPeriod(Long onsetTime, Long endTime) {
      this.onsetTime = onsetTime;
      this.endTime = endTime;
    }

    /**
     * Creates a clone of this ConditionPeriod.
     * @return a clone of this ConditionPeriod
     */
    public ConditionPeriod clone() {
      return new ConditionPeriod(this.onsetTime, this.endTime);
    }

    /**
     * Gets the end time of the condition.
     * @return the end time of the condition
     */
    public Long getEndTime() {
      return endTime;
    }

    /**
     * Sets the end time of the condition.
     * @param endTime the end time to set
     */
    public void setEndTime(Long endTime) {
      this.endTime = endTime;
    }

    /**
     * Gets the onset time of the condition.
     * @return the onset time of the condition
     */
    public Long getOnsetTime() {
      return onsetTime;
    }
  }

  /**
   * Represents a condition with a set of onset and end time entries.
   */
  public class OnsetCondition implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686902L;

    /** The name of the condition. */
    private String name;

    /** A list of condition periods with associated time information. */
    private List<ConditionPeriod> timeInfos;

    /**
     * Constructs an OnsetCondition with the specified name.
     *
     * @param name the name of the condition
     */
    public OnsetCondition(String name) {
      this.name = name;
      timeInfos = new LinkedList<ConditionPeriod>();
    }

    /**
     * Creates a shallow copy of this object.
     *
     * @return a clone of this OnsetCondition
     */
    public OnsetCondition clone() {
      OnsetCondition data = new OnsetCondition(this.name);
      data.timeInfos.addAll(this.timeInfos);
      return data;
    }

    /**
     * Gets the name of the condition.
     *
     * @return the name of the condition
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the list of time periods for the condition.
     * @return the list of time periods for the condition
     */
    public List<ConditionPeriod> getTimeInfos() {
      return timeInfos;
    }

    /**
     * Gets the last recorded onset time.
     * @return the last recorded onset time of the condition
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
     * Gets the last recorded end time.
     * @return the last recorded end time of the condition
     */
    public Long getLastEndTime() {
      if (timeInfos.isEmpty()) {
        return null;
      } else {
        int size = timeInfos.size();
        return timeInfos.get(size - 1).getEndTime();
      }
    }

    /**
     * Adds a new onset entry.
     * @param onsetTime the time to add as a new onset entry
     */
    public void addNewEntry(long onsetTime) {
      ConditionPeriod entry = new ConditionPeriod(Long.valueOf(onsetTime), null);
      timeInfos.add(entry);
    }

    /**
     * Sets the end time for the last entry.
     * @param time the time to set as the end time for the last entry
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

    /** The source of the condition record. */
    private String source;

    /** Data structure for storing onset conditions (init_time, end_time).*/
    private Map<String, OnsetCondition> onsetConditions;
    /** Data structure for storing mapping from state to condition names
     * This is useful when facing ConditionEnd.conditionOnSet attribute*/
    private Map<String, String> state2conditionMapping;

    /**
     * Create new instance for the specified module name.
     * @param source the name of the module that is recording conditions
     */
    public ModuleConditions(String source) {
      this.source = source;
      onsetConditions = new ConcurrentHashMap<String, OnsetCondition>();
      state2conditionMapping = new ConcurrentHashMap<String, String>();
    }

    /**
     * Create a shallow copy of this instance.
     * @return a shallow copy of this instance
     */
    public ModuleConditions clone() {
      ModuleConditions data = new ModuleConditions(this.source);
      data.state2conditionMapping.putAll(this.state2conditionMapping);
      data.onsetConditions.putAll(this.onsetConditions);
      return data;
    }

    /**
     * Records the onset of a condition.
     * @param condition the name of the condition
     * @param state the state associated with the condition
     * @param time the time the condition started
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
     * Records the end of a condition.
     * @param condition the name of the condition
     * @param time the time the condition ended
     */
    public void endCondition(String condition, long time) {
      if (onsetConditions.containsKey(condition)) {
        onsetConditions.get(condition).endLastEntry(time);
      }
    }

    /**
     * Gets the last recorded onset time of a condition.
     * @param condition the name of the condition
     * @return the last recorded onset time of the condition
     */
    public Long getConditionLastOnsetTime(String condition) {
      if (onsetConditions.containsKey(condition)) {
        return onsetConditions.get(condition).getLastOnsetTime();
      }
      return null;
    }

    /**
     * Gets the last recorded end time of a condition.
     * @param condition the name of the condition
     * @return the last recorded end time of the condition
     */
    public Long getConditionLastEndTime(String condition) {
      if (onsetConditions.containsKey(condition)) {
        return onsetConditions.get(condition).getLastEndTime();
      }
      return null;
    }

    /**
     * Gets the condition name associated with a state.
     * @param state the state name
     * @return the condition associated with the state
     */
    public String getConditionFromState(String state) {
      if (state2conditionMapping.containsKey(state)) {
        return state2conditionMapping.get(state);
      }
      return null;
    }

    /**
     * Gets the recorded conditions and onset/end information.
     * @return a map of condition names to their onset and end records
     */
    public Map<String, OnsetCondition> getOnsetConditions() {
      return onsetConditions;
    }
  }

  // this class represents a condition with its associated symptoms
  /**
   * Represents a condition with its associated symptoms.
   */
  public class ConditionWithSymptoms implements Cloneable, Serializable {
    private static final long serialVersionUID = 4322116644425686904L;

    /** The name of the condition. */
    private String conditionName;

    /** The time when the condition started. */
    private Long onsetTime;

    /** The time when the condition ended. */
    private Long endTime;

    /** A map of symptoms and their associated severity levels during the condition. */
    private Map<String, List<Integer>> symptoms;

    /**
     * Create a new instance for the supplied condition name, onset and end times.
     *
     * @param name the name of the condition
     * @param onsetTime the time the condition started
     * @param endTime the time the condition ended
     */
    public ConditionWithSymptoms(String name, Long onsetTime, Long endTime) {
      this.conditionName = name;
      this.onsetTime = onsetTime;
      this.endTime = endTime;
      this.symptoms = new ConcurrentHashMap<String, List<Integer>>();
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

    /**
     * Get the onset time of the condition.
     * @return the onset time of the condition
     */
    public Long getOnsetTime() {
      return onsetTime;
    }

    /**
     * Get the end time of the condition.
     * @return the end time of the condition
     */
    public Long getEndTime() {
      return endTime;
    }

    /**
     * Get the name of the condition.
     * @return the name of the condition
     */
    public String getConditionName() {
      return conditionName;
    }

    /**
     * Get the symptoms associated with the condition.
     * @return a map of symptoms and their associated values during the condition
     */
    public Map<String, List<Integer>> getSymptoms() {
      return symptoms;
    }
  }

  /** a map:  module.name to Conditions */
  private Map<String, ModuleConditions> sources;
  /** The person in context */
  @JSONSkip
  Person person;

  /**
   * Create a new instance of ExpressedConditionRecord.
   * @param person the person associated with this record
   */
  public ExpressedConditionRecord(Person person) {
    this.person = person;
    sources = new ConcurrentHashMap<String, ModuleConditions>();
  }

  /**
   * Create a shallow clone of this instance.
   */
  public ExpressedConditionRecord clone() {
    ExpressedConditionRecord data = new ExpressedConditionRecord(this.person);
    data.sources.putAll(this.sources);
    return data;
  }

  /**
   * Get the sources of conditions expressed by the person.
   * @return a map of module names to their associated conditions
   */
  public Map<String, ModuleConditions> getSources() {
    return sources;
  }

  /**
   * Method that is used to update the onsetConditions field when
   * a ConditionOnset state is processed.
   *
   * @param module the module name
   * @param state the state name
   * @param condition the condition name
   * @param time the time the condition started
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
   *
   * @param module the module name
   * @param condition the condition name
   * @return the last onset time of the condition
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
   *
   * @param module the module name
   * @param condition the condition name
   * @return the last end time of the condition
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
   *
   * @param module the module name
   * @param state the state name
   * @return the condition name associated with the state
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
   *
   * @param module the module name
   * @param condition the condition name
   * @param time the time the condition ended
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
   * @return a map of time to a list of conditions with symptoms
   */
  public Map<Long, List<ConditionWithSymptoms>> getConditionSymptoms() {
    Map<String, ExpressedSymptom> symptoms = person.getExpressedSymptoms();
    Map<Long, List<ConditionWithSymptoms>> result;
    result = new ConcurrentHashMap<Long, List<ConditionWithSymptoms>>();
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
