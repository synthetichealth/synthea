package org.mitre.synthea.engine;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.engine.Transition.TransitionType;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.world.agents.CommunityHealthWorker;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

import com.google.gson.JsonObject;

public abstract class State implements Cloneable {

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
	public Long entered;
	public Long exited;
	public Long next;
	protected List<Transition> transitions;
	protected JsonObject definition;

	protected void initialize(String module, String name, JsonObject definition) {
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
	
	public static State build(String module, String name, JsonObject definition) throws Exception
	{
		String className = State.class.getName() + "$" + definition.get("type").getAsString();
		
		Class<?> stateClass = Class.forName(className);
		
		State state = (State) stateClass.newInstance();
		
		state.initialize(module, name, definition);
		
		return state;
	}
	
	/**
	 * clone() should copy all the necessary variables of this State
	 * so that it can be correctly executed and modified without altering
	 * the original copy. So for example, 'entered', 'exited', and 'next' times
	 * should not be copied so the clone can be cleanly executed.
	 */
	public State clone() {
		// TODO -- implement this in subclasses
		try
		{
			return State.build(module, name, definition);
		} catch (Exception e)
		{
			// this should never happen.
			// if it built correctly the first time it should work every time
			return null;
		}
	}
	
	public String transition(Person person, long time) {
		// TODO - confirm this. always follow transition 0?
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
	public abstract boolean process(Person person, long time);
	
	public boolean run(Person person, long time)
	{
		// System.out.format("State: %s\n", this.name);
		if(this.entered == null) {
			this.entered = time;
		}
		boolean exit = process(person, time);
		
		if (exit)
		{
			this.exited = time;
		}
		
		return exit;
	}
		
	public String toString() {
		return type + " '" + name + "'";
	}
	
	protected static class Initial extends State 
	{
		@Override
		public boolean process(Person person, long time)
		{
			return true;
		}
	}


	protected static class Simple extends State 
	{
		@Override
		public boolean process(Person person, long time)
		{
			return true;
		}
	}


	protected static class CallSubmodule extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			// e.g. "submodule": "medications/otc_antihistamine"
			if(this.exited == null) {
				String submodulePath = definition.get("submodule").getAsString();
				Module submodule = Module.getModuleByPath(submodulePath);
				submodule.process(person, time);
				this.exited = time;
				return false;
			} else {
				return true;
			}
		}
	}


	protected static class Terminal extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			return false;
		}
	}


	protected static class Delay extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(this.next == null) {
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
		}
	}


	protected static class Guard extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			JsonObject logicDefinition = definition.get("allow").getAsJsonObject();
			Logic allow = new Logic(logicDefinition);
			boolean exit = allow.test(person, time);
			if(exit) 
			{
				this.exited = time;
			}
			return exit;
		}
	}


	protected static class SetAttribute extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
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
			
			return true;
		}
	}


	protected static class Counter extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String attribute = definition.get("attribute").getAsString();
			int counter = 0;
			if(person.attributes.containsKey(attribute)) {
				counter = (int) person.attributes.get(attribute);
			}
			String action = definition.get("action").getAsString();
			if(action.equals("increment")) {
				counter++;
			} else {
				counter--;
			}
			person.attributes.put(attribute, counter);
			return true;
		}
	}


	protected static class Encounter extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(definition.has("wellness") && definition.get("wellness").getAsBoolean()) {
				HealthRecord.Encounter encounter = person.record.currentEncounter(time);
				String activeKey = EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + this.module;
				if(person.attributes.containsKey(activeKey)) {
					person.attributes.remove(activeKey);

					// find closest provider and increment encounters count
					Provider provider = Provider.findClosestService(person, "wellness");
					person.addCurrentProvider(module, provider);
					int year = Utilities.getYear(time);
					provider.incrementEncounters("wellness", year);
					encounter.provider = provider;
			
					return true;
				} else {
					// Block until we're in a wellness encounter... then proceed.
					return false;
				}
			} else {				
				String encounter_class = definition.get("encounter_class").getAsString();
				HealthRecord.Encounter encounter = person.record.encounterStart(time, encounter_class);

				if(encounter_class.equals("emergency")) {
					// if emergency room encounter and CHW policy is enabled for emergency rooms, add CHW interventions
					person.chwEncounter(time, CommunityHealthWorker.DEPLOYMENT_EMERGENCY);
				}

				// find closest provider and increment encounters count
				Provider provider = Provider.findClosestService(person, encounter_class);
				person.addCurrentProvider(module, provider);
				int year = Utilities.getYear(time);
				provider.incrementEncounters(encounter_class, year);
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
				return true;
			}
		}
	}


	protected static class EncounterEnd extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			HealthRecord.Encounter encounter = person.record.currentEncounter(time);
			if(encounter.type != EncounterType.WELLNESS.toString()) {
				encounter.stop = time;
				// if CHW policy is enabled for discharge follow up, add CHW interventions for all non-wellness encounters
				person.chwEncounter(time, CommunityHealthWorker.DEPLOYMENT_POSTDISCHARGE);
			}
			if(definition.has("discharge_disposition")) {
				Code code = new Code((JsonObject) definition.get("discharge_disposition"));
				encounter.discharge = code;
			}
			// reset current provider hash
			person.removeCurrentProvider(module);
			
			return true;
		}
	}


	protected static class OnsetState extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			return true;
		}
	}


	protected static class ConditionOnset extends OnsetState 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			//TODO: ************ THIS IS BROKEN
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
				String attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, condition);
			}
			this.exited = time;
			return true;
		}
	}


	protected static class ConditionEnd extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(definition.has("condition_onset")) {
				String state_name = definition.get("condition_onset").getAsString();
				person.record.conditionEndByState(time, state_name);
			} else if(definition.has("referenced_by_attribute")) {
				String attribute = definition.get("referenced_by_attribute").getAsString();
				Entry condition = (Entry) person.attributes.get(attribute);
				condition.stop = time;
				person.record.conditionEnd(time, condition.type);
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.conditionEnd(time, item.getAsJsonObject().get("code").getAsString());
				});
			}
			return true;
		}
	}


	protected static class AllergyOnset extends OnsetState 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Entry allergy = person.record.allergyStart(time, primary_code);
			allergy.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					allergy.codes.add(code);
				});
			}
			if(definition.has("assign_to_attribute")) {
				String attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, allergy);
			}
			return true;
		}
	}


	protected static class AllergyEnd extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(definition.has("allergy_onset")) {
				String state_name = definition.get("allergy_onset").getAsString();
				person.record.allergyEndByState(time, state_name);
			} else if(definition.has("referenced_by_attribute")) {
				String attribute = definition.get("referenced_by_attribute").getAsString();
				Entry allergy = (Entry) person.attributes.get(attribute);
				allergy.stop = time;
				person.record.allergyEnd(time, allergy.type);
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.allergyEnd(time, item.getAsJsonObject().get("code").getAsString());
				});
			}
			return true;
		}
	}


	protected static class MedicationOrder extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
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
					Entry condition = (Entry) person.attributes.get(reason);
					medication.reasons.add(condition.type);
				} else if(person.hadPriorState(reason)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(reason.equals(entry.name)) {
							medication.reasons.add(entry.type);
						}
					}
				}
			}
			if(definition.has("prescription")) {
				medication.prescriptionDetails = definition.get("prescription").getAsJsonObject();
			}
			if(definition.has("assign_to_attribute")) {
				String attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, medication);
			}
			// increment number of prescriptions prescribed by respective hospital
			Provider medicationProvider;
			if(person.getCurrentProvider(module) != null){
				medicationProvider = person.getCurrentProvider(module);
			} else { // no provider associated with encounter or medication order
				medicationProvider = person.getAmbulatoryProvider();
			}
			int year = Utilities.getYear(time);
			medicationProvider.incrementPrescriptions( year );
			return true;
		}
	}


	protected static class MedicationEnd extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(definition.has("medication_order")) {
				String state_name = definition.get("medication_order").getAsString();
				person.record.medicationEndByState(time, state_name, "expired");
			} else if(definition.has("referenced_by_attribute")) {
				String attribute = definition.get("referenced_by_attribute").getAsString();
				Medication medication = (Medication) person.attributes.get(attribute);
				medication.stop = time;
				person.record.medicationEnd(time, medication.type, "expired");
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.medicationEnd(time, item.getAsJsonObject().get("code").getAsString(), "expired");
				});
			}
			return true;
		}
	}


	protected static class CarePlanStart extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
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
					Entry condition = (Entry) person.attributes.get(reason);
					careplan.reasons.add(condition.type);
				} else if(person.hadPriorState(reason)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(reason.equals(entry.name)) {
							careplan.reasons.add(entry.type);
						}
					}
				}
			}
			if(definition.has("assign_to_attribute")) {
				String attribute = definition.get("assign_to_attribute").getAsString();
				person.attributes.put(attribute, careplan);
			}
			return true;
		}
	}


	protected static class CarePlanEnd extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(definition.has("careplan")) {
				String state_name = definition.get("careplan").getAsString();
				person.record.careplanEndByState(time, state_name, "finished");
			} else if(definition.has("referenced_by_attribute")) {
				String attribute = definition.get("referenced_by_attribute").getAsString();
				CarePlan careplan = (CarePlan) person.attributes.get(attribute);
				careplan.stop = time;
				person.record.careplanEnd(time, careplan.type, "finished");
			} else if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					person.record.careplanEnd(time, item.getAsJsonObject().get("code").getAsString(), "finished");
				});
			}
			return true;
		}
	}


	protected static class Procedure extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			HealthRecord.Procedure procedure = person.record.procedure(time, primary_code);
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
					Entry condition = (Entry) person.attributes.get(reason);
					procedure.reasons.add(condition.type);
				} else if(person.hadPriorState(reason)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(reason.equals(entry.name)) {
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
			int year = Utilities.getYear(time);
			provider.incrementProcedures( year );

			return true;
		}
	}


	protected static class VitalSign extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			// TODO
			return true;
		}
	}


	protected static class Observation extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			Object value = null;
			if(definition.has("exact")) {
				value = Utilities.primitive( definition.get("exact").getAsJsonObject().get("quantity").getAsJsonPrimitive() );
			} else if(definition.has("range")) {
				double low = definition.get("range").getAsJsonObject().get("low").getAsDouble();
				double high = definition.get("range").getAsJsonObject().get("high").getAsDouble();
				value = person.rand(low, high);
			} else if(definition.has("attribute")) {
				String attribute = definition.get("attribute").getAsString();
				value = person.attributes.get(attribute);
			} else if(definition.has("vital_sign")) {
				String attribute = definition.get("vital_sign").getAsString();
				value = person.getVitalSign(org.mitre.synthea.world.concepts.VitalSign.fromString(attribute));
			}
			HealthRecord.Observation observation = person.record.observation(time, primary_code, value);
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
			return true;
		}
	}


	protected static class ObservationGroup extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			return true;
		}
	}


	protected static class MultiObservation extends ObservationGroup 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			int number_of_observations = definition.get("number_of_observations").getAsInt();
			HealthRecord.Observation observation = person.record.multiObservation(time, primary_code, number_of_observations);
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

			return true;
		}
	}


	protected static class DiagnosticReport extends ObservationGroup 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = definition.get("codes").getAsJsonArray().get(0).getAsJsonObject().get("code").getAsString();
			int number_of_observations = definition.get("number_of_observations").getAsInt();
			Report report = person.record.report(time, primary_code, number_of_observations);
			report.name = this.name;
			if(definition.has("codes")) {
				definition.get("codes").getAsJsonArray().forEach(item -> {
					Code code = new Code((JsonObject) item);
					report.codes.add(code);
				});
			}
			return true;
		}
	}


	protected static class Symptom extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
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
			return true;
		}
	}


	protected static class Death extends State 
	{
		@Override
		protected void initialize(String module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
		}

		@Override
		public boolean process(Person person, long time)
		{
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
						if(entry.name.equals(state_name)) {
							reason = entry.codes.get(0);
						}
					}
				}
			} else if(definition.has("referenced_by_attribute")) {
				String attribute = definition.get("referenced_by_attribute").getAsString();
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
		}
	}


}
