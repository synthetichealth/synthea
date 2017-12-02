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

  public static Logic build(JsonObject definition) {
    try {
      String className = Logic.class.getName() + "$" + definition.get("condition_type").getAsString().replaceAll("\\s", "");

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

  public abstract boolean test(Person person, long time);

  public static abstract class GroupedCondition extends Logic {
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

  public static class And extends GroupedCondition {
    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().allMatch(c -> c.test(person, time));
    }
  }

  public static class Or extends GroupedCondition {
    @Override
    public boolean test(Person person, long time) {
      return conditions.stream().anyMatch(c -> c.test(person, time));
    }
  }

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
          throw new UnsupportedOperationException("Units '" + unit + "' not currently supported in Age logic.");
      }

      return Utilities.compare(age, quantity, operator);
    }
  }

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
          observation = (HealthRecord.Observation) person.attributes.get(this.referencedByAttribute);
        } else {
          return false;
        }
      }

      return Utilities.compare(observation.value, this.value, operator);
    }
  }

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

      throw new RuntimeException("Active Condition condition must be specified by code or attribute");
    }
  }

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

      throw new RuntimeException("Active Medication condition must be specified by code or attribute");
    }
  }

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

      throw new RuntimeException("Active CarePlan condition must be specified by code or attribute");
    }
  }

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

  public static class True extends Logic {
    @Override
    public boolean test(Person person, long time) {
      return true;
    }
  }

  public static class False extends Logic {
    @Override
    public boolean test(Person person, long time) {
      return false;
    }
  }
}
