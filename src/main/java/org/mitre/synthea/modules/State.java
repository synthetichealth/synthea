package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.modules.HealthRecord.CarePlan;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.EncounterType;
import org.mitre.synthea.modules.HealthRecord.Entry;
import org.mitre.synthea.modules.HealthRecord.Medication;
import org.mitre.synthea.modules.HealthRecord.Observation;
import org.mitre.synthea.modules.HealthRecord.Procedure;
import org.mitre.synthea.modules.HealthRecord.Report;
import org.mitre.synthea.modules.Transition.TransitionType;
import org.mitre.synthea.world.Provider;

import com.google.gson.JsonObject;

public class State {

	public enum StateType {
		INITIAL, SIMPLE, CALLSUBMODULE, TERMINAL, DELAY, GUARD,
		SETATTRIBUTE, COUNTER, ENCOUNTER, ENCOUNTEREND, 
		CONDITIONONSET, CONDITIONEND, 
		ALLERGYONSET, ALLERGYEND, 
		MEDICATIONORDER, MEDICATIONEND, 
		CAREPLANSTART, CAREPLANEND, PROCEDURE,
		VITALSIGN, OBSERVATION, OBSERVATIONGROUP, MULTIOBSERVATION,
		DIAGNOSTICREPORT, SYMPTOM, DEATH
	}

	public String module;
	public String name;
	public StateType type;
	public long entered;
	public long exited;
	public long next;
	private List<Transition> transitions;
	private JsonObject definition;
	
	private State() { /* empty */ }
	public State(String module, String name, JsonObject definition) {
		this.module = module;
		this.name = name;
		this.type = StateType.valueOf(definition.get("type").getAsString().toUpperCase());
		this.transitions = new ArrayList<Transition>();
		if(definition.has("direct_transition")) {
			this.transitions.add(new Transition(TransitionType.DIRECT, definition.get("direct_transition")));
		} else if(definition.has("distributed_transition")) {
			this.transitions.add(new Transition(TransitionType.DISTRIBUTED, definition.get("distributed_transition")));
		} else if(definition.has("conditional_transition")) {
			this.transitions.add(new Transition(TransitionType.CONDITIONAL, definition.get("conditional_transition")));
		} else if(definition.has("complex_transition")) {
			this.transitions.add(new Transition(TransitionType.COMPLEX, definition.get("complex_transition")));
		} else if(type != StateType.TERMINAL && type != StateType.DEATH) {
			System.err.format("State `%s` has no transition.\n", name);
		}
		this.definition = definition;
	}
	
	/**
	 * clone() should copy all the necessary variables of this State
	 * so that it can be correctly executed and modified without altering
	 * the original copy. So for example, 'entered', 'exited', and 'next' times
	 * should not be copied so the clone can be cleanly executed.
	 */
	public State clone() {
		State clone = new State();
		clone.module = this.module;
		clone.name = this.name;
		clone.type = this.type;
		clone.transitions = this.transitions;
		clone.definition = this.definition;
		return clone;
	}
	
	public String transition(Person person, long time) {
		return transitions.get(0).follow(person, time);
	}

	/**
	 * Process this State with the given Person at the specified time
	 * within the simulation.
	 * @param person : the person being simulated
	 * @param time : the date within the simulated world
	 * @return `true` if processing should continue to the next state,
	 * `false` if the processing should halt for this time step.
	 */
	public boolean process(Person person, long time) {
		// System.out.format("State: %s\n", this.name);
		if(this.entered == 0) {
			this.entered = time;
		}
		switch(type) {
		case TERMINAL:
			return false;
		case INITIAL:
		case SIMPLE:
			return true;
		case CALLSUBMODULE:
			// e.g. "submodule": "medications/otc_antihistamine"
			if(this.exited == 0) {
				String submodulePath = definition.get("submodule").getAsString();
				Module submodule = Module.getModuleByPath(submodulePath);
				submodule.process(person, time);
				this.exited = time;
				return false;
			} else {
				return true;
			}
		case DELAY:
			if(this.next == 0) {
				JsonObject range = (JsonObject) definition.get("range");
				JsonObject exact = (JsonObject) definition.get("exact");
				if(range != null) {
					String units = range.get("unit").getAsString();
					double low = range.get("low").getAsDouble();
					double high = range.get("high").getAsDouble();
					this.next = time + Utilities.convertTime(units, (long) person.rand(low, high));
				} else if(exact != null) {
					String units = exact.get("unit").getAsString();
					double quantity = exact.get("quantity").getAsDouble();
					this.next = time + Utilities.convertTime(units, (long) quantity);
				} else {
					this.next = time;					
				}
			}
			if(time > this.next) {
				this.exited = time;
				return true;
			} else {
				return false;
			}
		case GUARD:
			JsonObject logicDefinition = definition.get("allow").getAsJsonObject();
			Logic allow = new Logic(logicDefinition);
			boolean exit = allow.test(person, time);
			if(exit) {
				this.exited = time;
			}
			return exit;
		case SETATTRIBUTE:
			String attribute = definition.get("attribute").getAsString();
			if (definition.has("value"))
			{
				Object value = Utilities.primitive( definition.get("value").getAsJsonPrimitive() );
				person.attributes.put(attribute, value);
			} else if (person.attributes.containsKey(attribute))
			{
				// intentionally clear out the variable
				person.attributes.remove(attribute);
			}
			
			this.exited = time;
			return true;
		case COUNTER:
			attribute = definition.get("attribute").getAsString();
			int counter = 0;
			if(person.attributes.containsKey(attribute)) {
				counter = (int) person.attributes.get(attribute);
			}
			String action = definition.get("action").getAsString();
			if(action == "increment") {
				counter++;
			} else {
				counter--;
			}
			person.attributes.put(attribute, counter);
			this.exited = time;
			return true;
		case SYMPTOM:
			String symptom = definition.get("symptom").getAsString();
			String cause = null;
			if(definition.has("cause")) {
				cause = definition.get("cause").getAsString();
			} else {
				cause = this.module;
			}
			JsonObject range = (JsonObject) definition.get("range");
			JsonObject exact = (JsonObject) definition.get("exact");
			if(range != null) {
				double low = range.get("low").getAsDouble();
				double high = range.get("high").getAsDouble();
				person.setSymptom(cause, symptom, (int) person.rand(low, high));
			} else if(exact != null) {
				int quantity = exact.get("quantity").getAsInt();
				person.setSymptom(cause, symptom, quantity);
			} else {
				person.setSymptom(cause, symptom, 0);					
			}
			this.exited = time;
			return true;
		case ENCOUNTER:
			if(definition.has("wellness") && definition.get("wellness").getAsBoolean()) {
				Encounter encounter = person.record.currentEncounter(time);
				String activeKey = String.format("%s %s", EncounterModule.ACTIVE_WELLNESS_ENCOUNTER, this.module);
				if(person.attributes.containsKey(activeKey)) {
					person.attributes.remove(activeKey);
					
					// find closest provider and increment encounters count
					Provider provider = Provider.findClosestService(person, "wellness");
					person.addCurrentProvider(module, provider);
					provider.incrementEncounters();
					encounter.provider = provider;
			
					this.exited = time;
					return true;
				} else {
					// Block until we're in a wellness encounter... then proceed.
					return false;
				}
			} else {
				// TODO: if emergency room encounter and CHW policy is enabled for emergency rooms, add CHW interventions
				String encounter_class = definition.get("encounter_class").getAsString();
				Encounter encounter = person.record.encounterStart(time, encounter_class);
				
				// find closest provider and increment encounters count
				Provider provider = Provider.findClosestService(person, encounter_class);
				person.addCurrentProvider(module, provider);
				provider.incrementEncounters();
				encounter.provider = provider;
				
				encounter.name = this.name;
				if(definition.has("reason")) {
					String reason = definition.get("reason").getAsString();
					Object item = person.attributes.get(reason);
					if(item instanceof String) {
						encounter.reason = (String) item;						
					} else if(item instanceof Entry) {
						encounter.reason = ((Entry) item).type;
					}
				}
				if(definition.has("codes")) {
					definition.get("codes").getAsJsonArray().forEach(item -> {
						Code code = new Code((JsonObject) item);
						encounter.codes.add(code);
					});
				}
				this.exited = time;
				return true;
			}
		case ENCOUNTEREND:
			// TODO: if CHW policy is enabled for discharge follow up, add CHW interventions
			Encounter encounter = person.record.currentEncounter(time);
			if(encounter.type != EncounterType.WELLNESS.toString()) {
				encounter.stop = time;
			}
			if(definition.has("discharge_disposition")) {
				Code code = new Code((JsonObject) definition.get("discharge_disposition"));
				encounter.discharge = code;
			}
			// reset current provider hash
			person.removeCurrentProvider(module);
			
			this.exited = time;
			return true;
		case CONDITIONONSET:
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Entry condition = person.record.conditionStart(time, primary_code);
			condition.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					condition.codes.add(code);
				});
			}
			if(definition.has("assign_to_attribute")) {
				attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, condition);
			}
			this.exited = time;
			return true;
		case CONDITIONEND:
			if(definition.has("condition_onset")) {
				String state_name = definition.get("condition_onset").getAsString();
				person.record.conditionEndByState(time, state_name);
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				condition = (Entry) person.attributes.get(attribute);
				condition.stop = time;
				person.record.conditionEnd(time, condition.type);
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.conditionEnd(time, item.getAsJsonObject().get("code").getAsString());
				});
			}
			this.exited = time;
			return true;
		case ALLERGYONSET:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Entry allergy = person.record.allergyStart(time, primary_code);
			allergy.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					allergy.codes.add(code);
				});
			}
			if(definition.has("assign_to_attribute")) {
				attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, allergy);
			}
			this.exited = time;
			return true;
		case ALLERGYEND:
			if(definition.has("allergy_onset")) {
				String state_name = definition.get("allergy_onset").getAsString();
				person.record.allergyEndByState(time, state_name);
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				allergy = (Entry) person.attributes.get(attribute);
				allergy.stop = time;
				person.record.allergyEnd(time, allergy.type);
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.allergyEnd(time, item.getAsJsonObject().get("code").getAsString());
				});
			}
			this.exited = time;
			return true;
		case OBSERVATION:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Object value = null;
			if(definition.has("exact")) {
				value = Utilities.primitive( definition.get("exact").getAsJsonObject().get("quantity").getAsJsonPrimitive() );
			} else if(definition.has("range")) {
				double low = definition.get("range").getAsJsonObject().get("low").getAsDouble();
				double high = definition.get("range").getAsJsonObject().get("high").getAsDouble();
				value = person.rand(low, high);
			} else if(definition.has("attribute")) {
				attribute = definition.get("attribute").getAsString();
				value = person.attributes.get(attribute);
			} else if(definition.has("vital_sign")) {
				attribute = definition.get("vital_sign").getAsString();
				value = person.getVitalSign(VitalSign.fromString(attribute));
			}
			Observation observation = person.record.observation(time, primary_code, value);
			observation.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					observation.codes.add(code);
				});
			}
			if(definition.has("category")) {
				observation.category = definition.get("category").getAsString();
			}
			if(definition.has("unit")) {
				observation.unit = definition.get("unit").getAsString();
			}
			this.exited = time;
			return true;
		case MULTIOBSERVATION:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			int number_of_observations = definition.get("number_of_observations").getAsInt();
			observation = person.record.multiObservation(time, primary_code, number_of_observations);
			observation.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					observation.codes.add(code);
				});
			}
			if(definition.has("category")) {
				observation.category = definition.get("category").getAsString();
			}
			this.exited = time;
			return true;
		case DIAGNOSTICREPORT:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			number_of_observations = definition.get("number_of_observations").getAsInt();
			Report report = person.record.report(time, primary_code, number_of_observations);
			report.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					report.codes.add(code);
				});
			}
			this.exited = time;
			return true;
		case MEDICATIONORDER:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Medication medication = person.record.medicationStart(time, primary_code);
			medication.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					medication.codes.add(code);
				});
			}
			if(definition.has("reason")) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
				String reason = definition.get("reason").getAsString();
				if(person.attributes.containsKey(reason)) {
					condition = (Entry) person.attributes.get(reason);
					medication.reasons.add(condition.type);
				} else if(person.hadPriorState(reason)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(entry.name == reason) {
							medication.reasons.add(entry.type);
						}
					}
				}
			}
			if(definition.has("prescription")) {
				medication.prescriptionDetails = definition.get("prescription").getAsJsonObject();
			}
			if(definition.has("assign_to_attribute")) {
				attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, medication);
			}
			// increment number of prescriptions prescribed by respective hospital
			Provider medicationProvider;
			if(person.getCurrentProvider(module) != null){
				medicationProvider = person.getCurrentProvider(module);
			} else { // no provider associated with encounter or medication order
				medicationProvider = person.getAmbulatoryProvider();
			}
			medicationProvider.incrementPrescriptions();
			this.exited = time;
			return true;
		case MEDICATIONEND:
			if(definition.has("medication_order")) {
				String state_name = definition.get("medication_order").getAsString();
				person.record.medicationEndByState(time, state_name, "expired");
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				medication = (Medication) person.attributes.get(attribute);
				medication.stop = time;
				person.record.medicationEnd(time, medication.type, "expired");
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.medicationEnd(time, item.getAsJsonObject().get("code").getAsString(), "expired");
				});
			}
			this.exited = time;
			return true;
		case CAREPLANSTART:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			CarePlan careplan = person.record.careplanStart(time, primary_code);
			careplan.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					careplan.codes.add(code);
				});
			}
			if(definition.has("activities")) {
				definition.get("activities").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					careplan.activities.add(code);
				});
			}
			if(definition.has("goals")) {
				definition.get("goals").getAsJsonArray().forEach(item -> {
					careplan.goals.add(item.getAsJsonObject());
				});
			}
			if(definition.has("reason")) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
				String reason = definition.get("reason").getAsString();
				if(person.attributes.containsKey(reason)) {
					condition = (Entry) person.attributes.get(reason);
					careplan.reasons.add(condition.type);
				} else if(person.hadPriorState(reason)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(entry.name == reason) {
							careplan.reasons.add(entry.type);
						}
					}
				}
			}
			if(definition.has("assign_to_attribute")) {
				attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, careplan);
			}
			this.exited = time;
			return true;
		case CAREPLANEND:
			if(definition.has("careplan")) {
				String state_name = definition.get("careplan").getAsString();
				person.record.careplanEndByState(time, state_name, "finished");
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				careplan = (CarePlan) person.attributes.get(attribute);
				careplan.stop = time;
				person.record.careplanEnd(time, careplan.type, "finished");
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.careplanEnd(time, item.getAsJsonObject().get("code").getAsString(), "finished");
				});
			}
			this.exited = time;
			return true;
		case PROCEDURE:
			primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Procedure procedure = person.record.procedure(time, primary_code);
			procedure.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					procedure.codes.add(code);
				});
			}
			if(definition.has("reason")) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
				String reason = definition.get("reason").getAsString();
				if(person.attributes.containsKey(reason)) {
					condition = (Entry) person.attributes.get(reason);
					procedure.reasons.add(condition.type);
				} else if(person.hadPriorState(reason)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(entry.name == reason) {
							procedure.reasons.add(entry.type);
						}
					}
				}
			}
			if(definition.has("duration")) {
				double low = definition.get("duration").getAsJsonObject().get("low").getAsDouble();
				double high = definition.get("duration").getAsJsonObject().get("high").getAsDouble();
				double duration = person.rand(low, high);
				String units = definition.get("duration").getAsJsonObject().get("unit").getAsString();
				procedure.stop = procedure.start + Utilities.convertTime(units, (long) duration);
			}
			// increment number of procedures by respective hospital
			Provider provider;
			if(person.getCurrentProvider(module) != null){
				provider = person.getCurrentProvider(module);
			} else { // no provider associated with encounter or procedure
				provider = person.getAmbulatoryProvider();
			}
			provider.incrementProcedures();

			this.exited = time;
			return true;
		case DEATH:
			Code reason = null;
			if(definition.has("codes")) {
				JsonObject item = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject();
				reason = new Code(item);
			} else if(definition.has("condition_onset")) {
				String state_name = definition.get("condition_onset").getAsString();
				if(person.hadPriorState(state_name)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(entry.name == state_name) {
							reason = entry.codes.get(0);
						}
					}
				}
			} else if(definition.has("referenced_by_attribute")) {
				attribute = definition.get("referenced_by_attribute").getAsString();
				reason = ((Entry) person.attributes.get(attribute)).codes.get(0);
			}
			String rule = String.format("%s %s", module, name);
			if(reason != null) {
				rule = String.format("%s %s", rule, reason.display);
			}
			if(definition.has("range")) {
				double low = definition.get("range").getAsJsonObject().get("low").getAsDouble();
				double high = definition.get("range").getAsJsonObject().get("high").getAsDouble();
				double duration = person.rand(low, high);
				String units = definition.get("range").getAsJsonObject().get("unit").getAsString();
				long timeOfDeath = time + Utilities.convertTime(units, (long) duration);
				// TODO person.recordDeath(time, reason, rule);
				person.events.create(timeOfDeath, Event.BIRTH, rule, false);
				return true;
			} else if(definition.has("exact")) {
				double quantity = definition.get("exact").getAsJsonObject().get("quantity").getAsDouble();
				String units = definition.get("exact").getAsJsonObject().get("unit").getAsString();
				long timeOfDeath = time + Utilities.convertTime(units, (long) quantity);
				// TODO person.recordDeath(time, reason, rule);
				person.events.create(timeOfDeath, Event.BIRTH, rule, false);
				return true;
			} else {
				// TODO person.recordDeath(time, reason, rule);
				person.events.create(time, Event.BIRTH, rule, true);
				return false;
			}
		default:
			System.err.format("Unhandled State Type: %s\n", type);
			return true;
		}
	}
		
	public String toString() {
		return String.format("%s '%s'", type, name);
	}
}
