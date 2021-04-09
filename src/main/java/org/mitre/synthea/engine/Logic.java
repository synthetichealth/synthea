package org.mitre.synthea.engine;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;

import org.mitre.synthea.engine.Components.DateInput;
import org.mitre.synthea.engine.Components.ExactWithUnit;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

/**
 * Logic represents any portion of a generic module that requires a logical
 * expression. This class is stateless, and calling 'test' on an instance
 * must not modify state as instances of Logic within Modules are shared
 * across the population.
 */
public abstract class Logic implements Serializable {
  public List<String> remarks;

  /**
   * Test whether the logic is true for the given person at the given time.
   * 
   * @param person Person to execute logic against
   * @param time Timestamp to execute logic against
   * @return boolean - whether or not the given condition is true or not
   */
  public abstract boolean test(Person person, long time);

  /**
   * Find the most recent entry, of a specific type of HealthRecord.Entry
   * within the patient history. May return null.
   * @param person Person the logic is executing against
   * @param classType Must be a HealthRecord.Entry or subclass.
   * @param code The code being searched for.
   * @return The HealthRecord.Entry (or subclass) that was found.
   */
  @SuppressWarnings("unchecked")
  private static <T extends HealthRecord.Entry> HealthRecord.Entry findEntryFromHistory(
      Person person, Class<T> classType, Code code) {
    // Find the most recent health record entry from the patient history
    HealthRecord.Entry entry = null;
    for (State state : person.history) {
      if (state.entry != null && classType.isInstance(state.entry)) {
        T candidate = (T) state.entry;
        for (Code candidateCode : candidate.codes) {
          if (candidateCode.equals(code)) {
            entry = candidate;
            break;
          }
        }
      }
      if (entry != null) {
        break;
      }
    }
    return entry;
  }

  /**
   * The Gender condition type tests the patient's gender. (M or F)
   */
  public static class Gender extends Logic {
    private String gender;

    @Override
    public boolean test(Person person, long time) {
      return gender.equals(person.attributes.get(Person.GENDER));
    }
  }
  
  /**
   * The Age condition type tests the patient's age, in a given unit. 
   * (Ex, years for adults or months for young children)
   */
  public static class Age extends Logic {
    private Double quantity;
    private String unit;
    private String operator;

    @Override
    public boolean test(Person person, long time) {
      double age;

      switch (unit) {
        case "years":
          age = person.ageInYears(time);
          break;
        case "months":
          age = person.ageInMonths(time);
          break;
        default:
          // TODO - add more unit types if we determine they are necessary
          throw new UnsupportedOperationException("Units '" + unit
            + "' not currently supported in Age logic.");
      }

      return Utilities.compare(age, quantity, operator);
    }
  }
  
  /**
   * The Date condition type tests the current year, month, or date being simulated.
   * For example, this may be used to drive different logic depending on the suggested
   * medications or procedures of different time periods, or model different frequency
   * of conditions.
   */
  public static class Date extends Logic {
    private Integer year;
    private Integer month;
    private DateInput date;
    private String operator;

    @Override
    public boolean test(Person person, long time) {
      if (year != null) {
        int currentyear = Utilities.getYear(time);
        return Utilities.compare(currentyear, year, operator);
      } else if (month != null) {
        int currentmonth = Utilities.getMonth(time);
        return Utilities.compare(currentmonth, month, operator);
      } else if (date != null) {
        Calendar testDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        testDate.set(date.year, date.month - 1, date.day, date.hour, date.minute, date.second);
        testDate.set(Calendar.MILLISECOND,date.millisecond);
        long testTime = testDate.getTimeInMillis();
        return Utilities.compare(time, testTime, operator);
      } else {
        throw new UnsupportedOperationException("Date type "
            + "not currently supported in Date logic.");
      }
    }
  }

  /**
   * The Socioeconomic Status condition type tests the patient's socioeconomic status. Socioeconomic
   * status is based on income, education, and occupation, and is categorized in Synthea as "High",
   * "Middle", or "Low".
   */
  public static class SocioeconomicStatus extends Logic {
    private String category;

    @Override
    public boolean test(Person person, long time) {
      return category.equals(person.attributes.get(Person.SOCIOECONOMIC_CATEGORY));
    }
  }
  
  /**
   * The Race condition type tests a patient's race. Synthea supports the following races:
   * "White", "Native" (Native American), "Hispanic", "Black", "Asian", and "Other".
   */
  public static class Race extends Logic {
    private String race;

    @Override
    public boolean test(Person person, long time) {
      return race.equalsIgnoreCase((String) person.attributes.get(Person.RACE));
    }
  }

  /**
   * The Symptom condition type tests a patient's current symptoms. Synthea tracks symptoms in order
   * to drive a patient's encounters, on a scale of 1-100. A symptom may be tracked for multiple
   * conditions, in these cases only the highest value is considered. See also the Symptom State.
   */
  public static class Symptom extends Logic {
    private String symptom;
    private String operator;
    private double value;

    @Override
    public boolean test(Person person, long time) {
      return Utilities.compare((double) person.getSymptom(symptom), value, operator);
    }
  }

  /**
   * The Observation condition type tests the most recent observation of a given type against a
   * given value. 
   * Implementation Warnings:
   * - Synthea does not support conversion between arbitrary units, so all observations of a given
   *   type are expected to be made in the same units. 
   * - The given observation must have been recorded prior to performing this logical check, 
   *   unless the operator is is nil or is not nil. Otherwise, the GMF will raise an exception
   *   that the observation value cannot be compared as there has been no observation made.
   */
  public static class Observation extends Logic {
    private String operator;
    private List<Code> codes;
    private String referencedByAttribute;
    private Object value;
    private Code valueCode;

    @Override
    public boolean test(Person person, long time) {
      HealthRecord.Observation observation = null;
      if (this.codes != null) {
        for (Code code : this.codes) {
          // First, look in the current health record for the latest observation
          HealthRecord.Observation last = person.record.getLatestObservation(code.code);
          if (person.lossOfCareEnabled) {
            if (last == null) {
              // If the observation is not in the current record,
              // it could be in the uncovered health record.
              last = person.lossOfCareRecord.getLatestObservation(code.code);
            }
            if (last == null) {
              // If the observation still is not in the uncovered health record,
              // it could be in the covered health record.
              last = person.defaultRecord.getLatestObservation(code.code);
            }
          }
          if (last == null && person.hasMultipleRecords) {
            // If the latest observation is not in the covered/uncovered health record,
            // then look in the module history.
            last = (HealthRecord.Observation)
                findEntryFromHistory(person, HealthRecord.Observation.class, code);
            if (Config.getAsBoolean("exporter.split_records.duplicate_data", false)) {
              person.record.currentEncounter(time).observations.add(last);
            }
          }
          if (last != null) {
            observation = last;
            break;
          }
        }
      } else if (this.referencedByAttribute != null) {
        if (person.attributes.containsKey(this.referencedByAttribute)) {
          observation = 
              (HealthRecord.Observation) person.attributes.get(this.referencedByAttribute);
        } else {
          return false;
        }
      }
      if (valueCode != null) {
        value = valueCode;
      } 
      if (operator.equals("is nil")) {
        return observation == null;
      } else if (operator.equals("is not nil")) {
        return observation != null;
      } else if (observation == null) {
        if (this.codes != null) {
          throw new NullPointerException("Required observation " + this.codes + " is null.");
        } else if (this.referencedByAttribute != null) {
          throw new NullPointerException("Required observation \""
              + this.referencedByAttribute + "\" is null.");
        } else {
          throw new NullPointerException("Required observation is null.");
        }
      } else {
        return Utilities.compare(observation.value, this.value, operator);
      }
    }
  }
  
  /**
   * The Attribute condition type tests a named attribute on the patient entity.
   */
  public static class Attribute extends Logic {
    private String attribute;
    private String operator;
    
    private Object value;

    @Override
    public boolean test(Person person, long time) {
      try {
        return Utilities.compare(person.attributes.get(attribute), value, operator);
      } catch (Exception e) {
        String message = "Attribute Logic error: " + attribute + " " + operator + " " + value;
        message += ": " + e.getMessage();
        throw new RuntimeException(message, e);
      }
    }
  }

  /**
   * GroupedCondition is the parent class for Logic that aggregates multiple conditions.
   * It should never be used directly in a JSON file.
   */
  private abstract static class GroupedCondition extends Logic {
    protected Collection<Logic> conditions;
  }
  
  /**
   * The And condition type tests that a set of sub-conditions are all true. 
   * If all sub-conditions are true, it will return true, 
   * but if any are false, it will return false.
   */
  public static class And extends GroupedCondition {
    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().allMatch(c -> c.test(person, time));
    }
  }

  /**
   * The Or condition type tests that at least one of its sub-conditions is true. 
   * If any sub-condition is true, it will return true, 
   * but if all sub-conditions are false, it will return false.
   */
  public static class Or extends GroupedCondition {
    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().anyMatch(c -> c.test(person, time));
    }
  }

  /**
   * The Not condition type negates its sub-condition. 
   * If the sub-condition is true, it will return false; 
   * if the sub-condition is false, it will return true.
   */
  public static class Not extends Logic {
    private Logic condition;

    @Override
    public boolean test(Person person, long time) {
      return !condition.test(person, time);
    }
  }

  /**
   * The At Least condition type tests that a minimum number of conditions
   * from a set of sub-conditions are true.
   * If the minimum number or more sub-conditions are true, it will return true,
   * but if less than the minimum are true, it will return false.
   * (If the minimum is the same as the number of sub-conditions provided,
   * this is equivalent to the And condition. 
   * If the minimum is 1, this is equivalent to the Or condition.)
   */
  public static class AtLeast extends GroupedCondition {
    private Integer minimum;

    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().filter(c -> c.test(person, time)).count() >= minimum;
    }
  }

  /**
   * The At Most condition type tests that a maximum number of conditions
   * from a set of sub-conditions are true. If the maximum number or fewer sub-conditions are true,
   * it will return true, but if more than the maximum are true, it will return false.
   */
  public static class AtMost extends GroupedCondition {
    private Integer maximum;

    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().filter(c -> c.test(person, time)).count() <= maximum;
    }
  }
  

  /**
   * The True condition always returns true. 
   * This condition is mainly used for testing purposes
   * and is not expected to be used in any real module.
   */
  public static class True extends Logic {
    @Override
    public boolean test(Person person, long time) {
      return true;
    }
  }

  /**
   * The False condition always returns false.
   * This condition is mainly used for testing purposes
   * and is not expected to be used in any real module.
   */
  public static class False extends Logic {
    @Override
    public boolean test(Person person, long time) {
      return false;
    }
  }

  /**
   * The PriorState condition type tests the progression of the patient through the module, and
   * checks if a specific state has already been processed (in other words, the state is in the
   * module's state history). The search for the state may be limited by time or the name of another
   * state.
   */
  public static class PriorState extends Logic {
    private String name;
    private String since;
    private ExactWithUnit<Double> within;
    private Long window;

    @Override
    public boolean test(Person person, long time) {
      Long sinceTime = null;
      
      if (within != null) {
        if (window == null) {
          // cache the value since it doesn't depend on person or time
          window = Utilities.convertTime(within.unit, within.quantity);
        }
        sinceTime = time - window;
      }

      return person.hadPriorState(name, since, sinceTime);
    }
  }
  
  /**
   * Parent class for logics that look up "active" things.
   * This class should never be referenced directly.
   */
  private abstract static class ActiveLogic extends Logic {
    protected List<Code> codes;
    protected String referencedByAttribute;

    abstract boolean checkCode(Person person, HealthRecord.Code code);

    abstract boolean checkAttribute(Person person, HealthRecord.Entry entry);

    abstract HealthRecord.Entry findItemWhenMultipleRecords(Person person, HealthRecord.Code code);

    abstract void addItemWhenDataIsDuplicated(Person person, long time, HealthRecord.Entry entry);

    @Override
    public boolean test(Person person, long time) {
      if (this.codes != null) {
        for (Code code : this.codes) {
          if (checkCode(person, code)) {
            return true;
          }
          if (person.hasMultipleRecords) {
            HealthRecord.Entry entry = findItemWhenMultipleRecords(person, code);
            if (entry != null && entry.stop == 0L) {
              if (Config.getAsBoolean("exporter.split_records.duplicate_data", false)) {
                addItemWhenDataIsDuplicated(person, time, entry);
              }
              return true;
            }
          }
        }
        return false;
      } else if (this.referencedByAttribute != null) {
        if (person.attributes.containsKey(this.referencedByAttribute)) {
          return checkAttribute(person,
              (HealthRecord.Entry) person.attributes.get(this.referencedByAttribute));
        } else {
          return false;
        }
      }

      throw new RuntimeException("Active CarePlan logic must be specified by code or attribute");
    }
  }

  /**
   * The Active Condition condition type tests whether a given condition is currently diagnosed and
   * active on the patient.
   * Future Implementation Considerations:
   * Currently to check if a condition has been added but not diagnosed, it is possible to use the
   * PriorState condition to check if the state has been processed. In the future it may be
   * preferable to add a distinct "Present Condition" logical condition to clearly specify the
   * intent of looking for a present but not diagnosed condition.
   */
  public static class ActiveCondition extends ActiveLogic {
    @Override
    boolean checkCode(Person person, Code code) {
      return person.record.present.containsKey(code.code);
    }

    @Override
    boolean checkAttribute(Person person, Entry entry) {
      return person.record.present.containsKey(entry.type);
    }

    @Override
    Entry findItemWhenMultipleRecords(Person person, Code code) {
      return findEntryFromHistory(person, HealthRecord.Entry.class, code);
    }

    @Override
    void addItemWhenDataIsDuplicated(Person person, long time, Entry entry) {
      person.record.currentEncounter(time).conditions.add(entry);
    }
  }

  public static class ActiveAllergy extends ActiveLogic {
    @Override
    boolean checkCode(Person person, Code code) {
      return person.record.present.containsKey(code.code);
    }

    @Override
    boolean checkAttribute(Person person, Entry entry) {
      return person.record.present.containsKey(entry.type);
    }

    @Override
    Entry findItemWhenMultipleRecords(Person person, Code code) {
      return findEntryFromHistory(person, HealthRecord.Allergy.class, code);
    }

    @Override
    void addItemWhenDataIsDuplicated(Person person, long time, Entry entry) {
      person.record.currentEncounter(time).allergies.add((HealthRecord.Allergy) entry);
    }
  }

  /**
   * The Active Medication condition type tests whether a given medication is currently prescribed
   * and active for the patient.
   */
  public static class ActiveMedication extends ActiveLogic {
    @Override
    boolean checkCode(Person person, Code code) {
      return person.record.medicationActive(code.code);
    }

    @Override
    boolean checkAttribute(Person person, Entry entry) {
      return person.record.medicationActive(entry.type);
    }

    @Override
    Entry findItemWhenMultipleRecords(Person person, Code code) {
      return findEntryFromHistory(person, HealthRecord.Medication.class, code);
    }

    @Override
    void addItemWhenDataIsDuplicated(Person person, long time, Entry entry) {
      person.record.currentEncounter(time).medications.add((HealthRecord.Medication) entry);
    }
  }

  /**
   * The Active CarePlan condition type tests whether a given care plan is currently prescribed and
   * active for the patient.
   */
  public static class ActiveCarePlan extends ActiveLogic {
    @Override
    boolean checkCode(Person person, Code code) {
      return person.record.careplanActive(code.code);
    }

    @Override
    boolean checkAttribute(Person person, Entry entry) {
      return person.record.careplanActive(entry.type);
    }

    @Override
    Entry findItemWhenMultipleRecords(Person person, Code code) {
      return findEntryFromHistory(person, HealthRecord.CarePlan.class, code);
    }

    @Override
    void addItemWhenDataIsDuplicated(Person person, long time, Entry entry) {
      person.record.currentEncounter(time).careplans.add((HealthRecord.CarePlan) entry);
    }
  }
  
  /**
   * The Vital Sign condition type tests a patient's current vital signs. Synthea tracks vital signs
   * in order to drive a patient's physical condition, and are recorded in observations. See also
   * the Symptom State.
   */
  public static class VitalSign extends Logic {
    private org.mitre.synthea.world.concepts.VitalSign vitalSign;
    private String operator;
    private double value;

    @Override
    public boolean test(Person person, long time) {
      return Utilities.compare(person.getVitalSign(vitalSign, time), value, operator);
    }
  }
}
