package org.mitre.synthea.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
public abstract class Logic {
  private List<String> remarks;

  /**
   * Construct a logic object from the given definitions.

   * @param definition
   *          The JSON definition of the logic
   * @return The constructed Logic object. The returned object will be of the appropriate subclass
   *         of Logic, based on the "condition_type" parameter in the JSON definition.
   */
  public static Logic build(JsonObject definition) {
    try {
      String type = definition.get("condition_type").getAsString().replaceAll("\\s", "");
      String className = Logic.class.getName() + "$" + type;

      Class<?> logicClass = Class.forName(className);

      Logic logic = (Logic) logicClass.newInstance();

      logic.initialize(definition);

      return logic;
    } catch (Exception e) {
      throw new Error("Unable to instantiate logic", e);
    }
  }

  protected void initialize(JsonObject definition) throws Exception {
    remarks = new ArrayList<String>();
    if (definition.has("remarks")) {
      JsonElement jsonRemarks = definition.get("remarks");
      if (jsonRemarks.isJsonArray()) {
        for (JsonElement value : jsonRemarks.getAsJsonArray()) {
          remarks.add(value.getAsString());
        }
      } else {
        // must be a single string
        remarks.add(jsonRemarks.getAsString());
      }
    }
  }

  /**
   * Test whether the logic is true for the given person at the given time.
   * 
   * @param person Person to execute logic against
   * @param time Timestamp to execute logic against
   * @return boolean - whether or not the given condition is true or not
   */
  public abstract boolean test(Person person, long time);

  /**
   * GroupedCondition is the parent class for Logic that aggregates multiple conditions.
   * It should never be used directly in a JSON file.
   */
  private abstract static class GroupedCondition extends Logic {
    protected Collection<Logic> conditions;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      if (definition.has("conditions")) {
        conditions = new ArrayList<>();
        for (JsonElement value : definition.get("conditions").getAsJsonArray()) {
          conditions.add(Logic.build(value.getAsJsonObject()));
        }
      }
    }
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
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      if (definition.has("condition")) {
        this.condition = Logic.build(definition.get("condition").getAsJsonObject());
      }
    }

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
    private int minimum;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      this.minimum = definition.get("minimum").getAsInt();
    }

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
    private int maximum;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      this.maximum = definition.get("maximum").getAsInt();
    }

    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().filter(c -> c.test(person, time)).count() <= maximum;
    }
  }

  /**
   * The Gender condition type tests the patient's gender. (M or F)
   */
  public static class Gender extends Logic {
    private String gender;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);
      this.gender = definition.get("gender").getAsString();
    }

    @Override
    public boolean test(Person person, long time) {
      return gender.equals(person.attributes.get(Person.GENDER));
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
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      this.category = definition.get("category").getAsString();
    }

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
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      this.race = definition.get("race").getAsString();
    }

    @Override
    public boolean test(Person person, long time) {
      return race.equals(person.attributes.get(Person.RACE));
    }
  }

  /**
   * The Age condition type tests the patient's age, in a given unit. 
   * (Ex, years for adults or months for young children)
   */
  public static class Age extends Logic {
    private double quantity;
    private String unit;
    private String operator;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      this.unit = definition.get("unit").getAsString();
      this.operator = definition.get("operator").getAsString();
      this.quantity = definition.get("quantity").getAsDouble();
    }

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
   * The Date condition type tests the current year being simulated. For example, this may be used
   * to drive different logic depending on the suggested medications or procedures of different time
   * periods, or model different frequency of conditions.
   */
  public static class Date extends Logic {
    private int year;
    private String operator;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);
      this.year = definition.get("year").getAsInt();
      this.operator = definition.get("operator").getAsString();
    }

    @Override
    public boolean test(Person person, long time) {
      int current = Utilities.getYear(time);
      return Utilities.compare(current, year, operator);
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
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      symptom = definition.get("symptom").getAsString();
      operator = definition.get("operator").getAsString();
      value = definition.get("value").getAsDouble();
    }

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
    private Double value;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      if (definition.has("codes")) {
        codes = Code.fromJson(definition.get("codes").getAsJsonArray());
      }
      if (definition.has("referenced_by_attribute")) {
        referencedByAttribute = definition.get("referenced_by_attribute").getAsString();
      }
      if (definition.has("value")) {
        this.value = definition.get("value").getAsDouble();
      }
      operator = definition.get("operator").getAsString();
    }

    @Override
    public boolean test(Person person, long time) {

      HealthRecord.Observation observation = null;
      if (this.codes != null) {
        for (Code code : this.codes) {
          HealthRecord.Observation last = person.record.getLatestObservation(code.code);
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

      return Utilities.compare(observation.value, this.value, operator);
    }
  }

  /**
   * The Vital Sign condition type tests a patient's current vital signs. Synthea tracks vital signs
   * in order to drive a patient's physical condition, and are recorded in observations. See also
   * the Symptom State.
   */
  public static class VitalSign extends Logic {
    private org.mitre.synthea.world.concepts.VitalSign vs;
    private String operator;
    private double value;


    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      String vitalSignName = definition.get("vital_sign").getAsString();
      vs = org.mitre.synthea.world.concepts.VitalSign.fromString(vitalSignName);
      operator = definition.get("operator").getAsString();
      value = definition.get("value").getAsDouble();
    }

    @Override
    public boolean test(Person person, long time) {
      return Utilities.compare(person.getVitalSign(vs), value, operator);
    }
  }

  /**
   * Parent class for logics that look up "active" things.
   * This class should never be referenced directly.
   */
  private abstract static class ActiveLogic extends Logic {
    protected List<Code> codes;
    protected String referencedByAttribute;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      if (definition.has("codes")) {
        codes = Code.fromJson(definition.get("codes").getAsJsonArray());
      }
      if (definition.has("referenced_by_attribute")) {
        referencedByAttribute = definition.get("referenced_by_attribute").getAsString();
      }
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
    public boolean test(Person person, long time) {
      if (this.codes != null) {
        for (Code code : this.codes) {
          if (person.record.present.containsKey(code.code)) {
            return true;
          }
        }
        return false;
      } else if (referencedByAttribute != null) {
        if (person.attributes.containsKey(referencedByAttribute)) {
          Entry diagnosis = (Entry) person.attributes.get(referencedByAttribute);
          return person.record.present.containsKey(diagnosis.type);
        } else {
          return false;
        }
      }

      throw new RuntimeException("Active Condition logic must be specified by code or attribute");
    }
  }

  /**
   * The Active Medication condition type tests whether a given medication is currently prescribed
   * and active for the patient.
   */
  public static class ActiveMedication extends ActiveLogic {
    @Override
    public boolean test(Person person, long time) {
      if (this.codes != null) {
        for (Code code : this.codes) {
          if (person.record.medicationActive(code.code)) {
            return true;
          }
        }
        return false;
      } else if (this.referencedByAttribute != null) {
        if (person.attributes.containsKey(this.referencedByAttribute)) {
          Medication medication = (Medication) person.attributes.get(this.referencedByAttribute);
          return person.record.medicationActive(medication.type);
        } else {
          return false;
        }
      }

      throw new RuntimeException("Active Medication logic must be specified by code or attribute");
    }
  }

  /**
   * The Active CarePlan condition type tests whether a given care plan is currently prescribed and
   * active for the patient.
   */
  public static class ActiveCarePlan extends ActiveLogic {
    @Override
    public boolean test(Person person, long time) {
      if (this.codes != null) {
        for (Code code : this.codes) {
          if (person.record.careplanActive(code.code)) {
            return true;
          }
        }
        return false;
      } else if (this.referencedByAttribute != null) {
        if (person.attributes.containsKey(this.referencedByAttribute)) {
          CarePlan carePlan = (CarePlan) person.attributes.get(this.referencedByAttribute);
          return person.record.careplanActive(carePlan.type);
        } else {
          return false;
        }
      }

      throw new RuntimeException("Active CarePlan logic must be specified by code or attribute");
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
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);

      this.attribute = definition.get("attribute").getAsString();
      this.operator = definition.get("operator").getAsString();
      if (definition.has("value")) {
        this.value = Utilities.primitive(definition.get("value").getAsJsonPrimitive());
      }
    }

    @Override
    public boolean test(Person person, long time) {
      if (value instanceof String) {
        return value.equals(person.attributes.get(attribute));
      } else {
        return Utilities.compare(person.attributes.get(attribute), value, operator);
      }
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
    private Long window;

    @Override
    protected void initialize(JsonObject definition) throws Exception {
      super.initialize(definition);
      this.name = definition.get("name").getAsString();
      if (definition.has("since")) {
        this.since = definition.get("since").getAsString();
      }
      if (definition.has("within")) {
        String units = definition.get("within").getAsJsonObject().get("unit").getAsString();
        long quantity = definition.get("within").getAsJsonObject().get("quantity").getAsLong();
        this.window = Utilities.convertTime(units, quantity);
      }
    }

    @Override
    public boolean test(Person person, long time) {
      Long sinceTime = null;
      if (window != null) {
        sinceTime = time - window;
      }

      return person.hadPriorState(name, since, sinceTime);
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
}
