package org.mitre.synthea.modules;

import java.util.Calendar;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Logic represents any portion of a generic module that requires a logical
 * expression. This class is stateless, and calling 'test' on an instance
 * must not modify state as instances of Logic within Modules are shared 
 * across the population.
 */
public class Logic {

	public enum ConditionType {
		GENDER("Gender"), 
		SOCIOECONOMIC_STATUS("Socioeconomic Status"),
		RACE("Race"),
		AGE("Age"),
		DATE("Date"),
		SYMPTOM("Symptom"),
		OBSERVATION("Observation"),
		VITAL_SIGN("Vital Sign"),
		ACTIVE_CONDITION("Active Condition"),
		ACTIVE_MEDICATION("Active Medication"),
		ACTIVE_CAREPLAN("Active CarePlan"),
		ATTRIBUTE("Attribute"),
		PRIOR_STATE("PriorState"),
		AND("And"),
		OR("Or"),
		NOT("Not"),
		AT_LEAST("At Least"),
		AT_MOST("At Most"),
		TRUE("True"),
		FALSE("False");
		
		private String text;
		
		ConditionType(String text) {
			this.text = text;
		}
		
		public static ConditionType fromString(String text) {
			for(ConditionType type : ConditionType.values()) {
				if(type.text.equalsIgnoreCase(text)) {
					return type;
				}
			}
			return ConditionType.valueOf(text);
		}
	}
	
	public ConditionType type;
	public JsonObject definition;
	
	public Logic(JsonObject definition) {
		this.type = ConditionType.fromString( definition.get("condition_type").getAsString() );
		this.definition = definition;
	}
	
	public boolean test(Person person, long time) {
		switch(type) {
		case GENDER:
			String gender = definition.get("gender").getAsString();
			return gender.equals(person.attributes.get(Person.GENDER));
		case AGE:
			long age = person.ageInMilliseconds(time);
			String operator = definition.get("operator").getAsString();
			long quantity = definition.get("quantity").getAsLong();
			String units = definition.get("unit").getAsString();
			quantity = Utilities.convertTime(units, quantity);
			return Utilities.compare((double)age, (double)quantity, operator);
		case DATE:
			operator = definition.get("operator").getAsString();
			quantity = definition.get("year").getAsLong();
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(time);
			double year = calendar.get(Calendar.YEAR) - 1900;
			return Utilities.compare(year, (double)quantity, operator);
		case SOCIOECONOMIC_STATUS:
			String category = definition.get("category").getAsString();
			return category.equals(person.attributes.get(Person.SOCIOECONOMIC_CATEGORY));
		case RACE:
			String race = definition.get("race").getAsString();
			return race.equals(person.attributes.get(Person.RACE));
		case SYMPTOM:
			String symptom = definition.get("symptom").getAsString();
			operator = definition.get("operator").getAsString();
			double value = definition.get("value").getAsDouble();
			return Utilities.compare((double)person.getSymptom(symptom), value, operator);
		case ATTRIBUTE:
			String attribute = definition.get("attribute").getAsString();
			operator = definition.get("operator").getAsString();
			if(definition.has("value")) {
				Object val = Utilities.primitive(definition.get("value").getAsJsonPrimitive());
				if(val instanceof String) {
					return val.equals(person.attributes.get(attribute));
				} else {
					return Utilities.compare(person.attributes.get(attribute), val, operator);
				}				
			} else {
				return Utilities.compare(person.attributes.get(attribute), null, operator);
			}
		case AND:
			JsonArray conditions = definition.get("conditions").getAsJsonArray();
			boolean allTrue = true;
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				allTrue = allTrue && condition.test(person, time);
			}
			return allTrue;
		case OR:
			conditions = definition.get("conditions").getAsJsonArray();
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				if(condition.test(person, time)) {
					return true;
				}
			}
			return false;
		case AT_LEAST:
			int count = 0;
			int minimum = definition.get("minimum").getAsInt();
			conditions = definition.get("conditions").getAsJsonArray();
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				if(condition.test(person, time)) {
					count++;
				}
			}
			return count >= minimum;
		case AT_MOST:
			count = 0;
			int maximum = definition.get("maximum").getAsInt();
			conditions = definition.get("conditions").getAsJsonArray();
			for(int i=0; i < conditions.size(); i++) {
				Logic condition = new Logic(conditions.get(i).getAsJsonObject());
				if(condition.test(person, time)) {
					count++;
				}
			}
			return count <= maximum;
		case NOT:
			JsonObject not = definition.get("condition").getAsJsonObject();
			Logic condition = new Logic(not);
			return !condition.test(person, time);
		case TRUE:
			return true;
		case FALSE:
			return false;
		default:
			System.err.format("Unhandled Logic: %s\n", type);
			return false;
		}
	}
}
