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

public abstract class State implements Cloneable 
{
	public Module module;
	public String name;
	public Long entered;
	public Long exited;
	private Transition transition;
	private JsonObject definition;

	protected void initialize(Module module, String name, JsonObject definition) {
		this.module = module;
		this.name = name;

		if(definition.has("direct_transition")) 
		{
			this.transition = new Transition(TransitionType.DIRECT, definition.get("direct_transition"));
		} else if(definition.has("distributed_transition")) 
		{
			this.transition = new Transition(TransitionType.DISTRIBUTED, definition.get("distributed_transition"));
		} else if(definition.has("conditional_transition")) 
		{
			this.transition = new Transition(TransitionType.CONDITIONAL, definition.get("conditional_transition"));
		} else if(definition.has("complex_transition")) 
		{
			this.transition = new Transition(TransitionType.COMPLEX, definition.get("complex_transition"));
		} else if(!(this instanceof Terminal)) 
		{
			throw new RuntimeException("State `" + name + "` has no transition.\n");
		}
		this.definition = definition;
	}
	
	public static State build(Module module, String name, JsonObject definition) throws Exception
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
	 * the original copy. So for example, 'entered' and 'exited' times
	 * should not be copied so the clone can be cleanly executed.
	 */
	public State clone() {
		try {
			State clone = (State) super.clone();
			clone.module = this.module;
			clone.name = this.name;
			clone.transition = this.transition;
			clone.definition = this.definition;
			return clone;
		} catch (CloneNotSupportedException e) {
			// should not happen, and not something we can handle
			throw new RuntimeException(e);
		}
	}
	
	public String transition(Person person, long time) 
	{
		return transition.follow(person, time);
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
		return this.getClass().getSimpleName() + " '" + name + "'";
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
		private String submodulePath;
		
		
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			submodulePath = definition.get("submodule").getAsString();
		}
		
		public CallSubmodule clone()
		{
			CallSubmodule clone = (CallSubmodule)super.clone();
			clone.submodulePath = submodulePath;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			// e.g. "submodule": "medications/otc_antihistamine"
			if(this.exited == null) {
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
		public boolean process(Person person, long time)
		{
			return false;
		}
	}


	protected static class Delay extends State 
	{
		public Long next;
		
		private String unit;
		private Double quantity;
		private Double low;
		private Double high;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			
			JsonObject range = (JsonObject) definition.get("range");
			JsonObject exact = (JsonObject) definition.get("exact");
			
			if(range != null) 
			{
				unit = range.get("unit").getAsString();
				low = range.get("low").getAsDouble();
				high = range.get("high").getAsDouble();	
			} else if(exact != null) 
			{
				unit = exact.get("unit").getAsString();
				quantity = exact.get("quantity").getAsDouble();
			}
		}
		
		public Delay clone()
		{
			Delay clone = (Delay)super.clone();
			clone.unit = unit;
			clone.quantity = quantity;
			clone.low = low;
			clone.high = high;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(this.next == null) 
			{
				if (quantity != null)
				{
					// use an exact quantity
					this.next = time + Utilities.convertTime(unit, quantity.longValue());
				} else if (low != null && high != null)
				{
					// use a range
					this.next = time + Utilities.convertTime(unit, (long) person.rand(low, high));
				} else
				{
					throw new RuntimeException("Delay state has no exact or range: " + this);
				}
			}
			
			return time >= this.next;
		}
	}


	protected static class Guard extends State 
	{
		private Logic allow;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			JsonObject logicDefinition = definition.get("allow").getAsJsonObject();
			allow = new Logic(logicDefinition);
		}
		
		public Guard clone()
		{
			Guard clone = (Guard) super.clone();
			clone.allow = allow;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
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
		private String attribute;
		private Object value;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			
			attribute = definition.get("attribute").getAsString();
			
			if (definition.has("value"))
			{
				value = Utilities.primitive( definition.get("value").getAsJsonPrimitive() );
			}
		}
		
		public SetAttribute clone()
		{
			SetAttribute clone = (SetAttribute) super.clone();
			clone.attribute = attribute;
			clone.value = value;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if (value != null)
			{
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
		private String attribute;
		private boolean increment;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			
			attribute = definition.get("attribute").getAsString();
			
			String action = definition.get("action").getAsString();
			
			increment = action.equals("increment");
		}
		
		public Counter clone()
		{
			Counter clone = (Counter) super.clone();
			clone.attribute = attribute;
			clone.increment = increment;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			int counter = 0;
			if(person.attributes.containsKey(attribute)) {
				counter = (int) person.attributes.get(attribute);
			}
			
			if(increment) {
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
		private boolean wellness;
		private String encounterClass;
		private List<Code> codes;
		private String reason;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			wellness = definition.has("wellness") && definition.get("wellness").getAsBoolean();
			if (definition.has("encounter_class"))
			{
				encounterClass = definition.get("encounter_class").getAsString();
			}
			if (definition.has("reason"))
			{
				reason = definition.get("reason").getAsString();
			}
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
		}
		
		public Encounter clone()
		{
			Encounter clone = (Encounter) super.clone();
			clone.wellness = wellness;
			clone.encounterClass = encounterClass;
			clone.reason = reason;
			clone.codes = codes;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{		
			if(wellness) {
				HealthRecord.Encounter encounter = person.record.currentEncounter(time);
				String activeKey = EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + this.module;
				if(person.attributes.containsKey(activeKey)) {
					person.attributes.remove(activeKey);

					person.setCurrentEncounter(module, encounter);
					
					// find closest provider and increment encounters count
					Provider provider = Provider.findClosestService(person, "wellness");
					person.addCurrentProvider(module.name, provider);
					int year = Utilities.getYear(time);
					provider.incrementEncounters("wellness", year);
					encounter.provider = provider;
			
					diagnosePastConditions(person, time);
					
					return true;
				} else {
					// Block until we're in a wellness encounter... then proceed.
					return false;
				}
			} else {				
				
				HealthRecord.Encounter encounter = person.record.encounterStart(time, encounterClass);
				person.setCurrentEncounter(module, encounter);
				if(encounterClass.equals("emergency")) {
					// if emergency room encounter and CHW policy is enabled for emergency rooms, add CHW interventions
					person.chwEncounter(time, CommunityHealthWorker.DEPLOYMENT_EMERGENCY);
				}

				// find closest provider and increment encounters count
				Provider provider = Provider.findClosestService(person, encounterClass);
				person.addCurrentProvider(module.name, provider);
				int year = Utilities.getYear(time);
				provider.incrementEncounters(encounterClass, year);
				encounter.provider = provider;
				
				encounter.name = this.name;
				if(reason != null) {
					Object item = person.attributes.get(reason);
					if(item instanceof String) {
						encounter.reason = (String) item;						
					} else if(item instanceof Entry) {
						encounter.reason = ((Entry) item).type;
					}
				}
				if(codes != null) 
				{
					encounter.codes.addAll(codes);
				}
				
				diagnosePastConditions(person, time);
				return true;
			}
		}
		
		private void diagnosePastConditions(Person person, long time)
		{
			for (State state : person.history)
			{
				if (state instanceof OnsetState && !((OnsetState)state).diagnosed)
				{
					((OnsetState)state).diagnose(person, time);
				}
			}
		}
	}


	protected static class EncounterEnd extends State 
	{
		private Code dischargeDisposition;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if(definition.has("discharge_disposition")) 
			{
				dischargeDisposition = new Code((JsonObject) definition.get("discharge_disposition"));
			}
		}
		
		public EncounterEnd clone()
		{
			EncounterEnd clone = (EncounterEnd) super.clone();
			clone.dischargeDisposition = dischargeDisposition;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			HealthRecord.Encounter encounter = person.getCurrentEncounter(module);
			if(encounter.type != EncounterType.WELLNESS.toString()) {
				encounter.stop = time;
				// if CHW policy is enabled for discharge follow up, add CHW interventions for all non-wellness encounters
				person.chwEncounter(time, CommunityHealthWorker.DEPLOYMENT_POSTDISCHARGE);
			}
			
			encounter.discharge = dischargeDisposition;
			
			// reset current provider hash
			person.removeCurrentProvider(module.name);
			
			person.setCurrentEncounter(module, null);
			
			return true;
		}
	}


	protected abstract static class OnsetState extends State 
	{
		public boolean diagnosed;
		
		protected List<Code> codes;
		protected String assignToAttribute;
		protected String targetEncounter;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("assign_to_attribute")) 
			{
				assignToAttribute = definition.get("assign_to_attribute").getAsString();
			}
			
			if(definition.has("target_encounter")) 
			{
				targetEncounter = definition.get("target_encounter").getAsString();
			}
		}
		
		public OnsetState clone()
		{
			OnsetState clone = (OnsetState)super.clone();
			clone.codes = codes;
			clone.assignToAttribute = assignToAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			HealthRecord.Encounter encounter = person.getCurrentEncounter(module);
			
			if ( targetEncounter == null || 
					(encounter != null && targetEncounter.equals(encounter.name)) )
			{
				diagnose(person, time);
			}
			return true;
		}
		
		public abstract void diagnose(Person person, long time);
	}


	protected static class ConditionOnset extends OnsetState 
	{
		@Override
		public void diagnose(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			Entry condition = person.record.conditionStart(time, primary_code);
			condition.name = this.name;
			if(codes != null) {
				condition.codes.addAll(codes);
			}
			if(assignToAttribute != null) {
				person.attributes.put(assignToAttribute, condition);
			}
			
			diagnosed = true;
		}
	}


	protected static class ConditionEnd extends State 
	{
		private List<Code> codes;
		private String conditionOnset;
		private String referencedByAttribute;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes")) {
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("condition_onset")) {
				conditionOnset = definition.get("condition_onset").getAsString();
			}
			if(definition.has("referenced_by_attribute")) {
				referencedByAttribute = definition.get("referenced_by_attribute").getAsString();
			}
		}
		
		public ConditionEnd clone()
		{
			ConditionEnd clone = (ConditionEnd)super.clone();
			clone.codes = codes;
			clone.conditionOnset = conditionOnset;
			clone.referencedByAttribute = referencedByAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(conditionOnset != null) {
				person.record.conditionEndByState(time, conditionOnset);
			} else if(referencedByAttribute != null) {
				Entry condition = (Entry) person.attributes.get(referencedByAttribute);
				condition.stop = time;
				person.record.conditionEnd(time, condition.type);
			} else if(codes != null) {
				codes.forEach(code -> person.record.conditionEnd(time, code.code));
			}
			return true;
		}
	}


	protected static class AllergyOnset extends OnsetState 
	{
		@Override
		public void diagnose(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			Entry allergy = person.record.allergyStart(time, primary_code);
			allergy.name = this.name;
			allergy.codes.addAll(codes);

			if(assignToAttribute != null) {
				person.attributes.put(assignToAttribute, allergy);
			}
			
			diagnosed = true;
		}
	}


	protected static class AllergyEnd extends State 
	{
		private List<Code> codes;
		private String allergyOnset;
		private String referencedByAttribute;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			
			if (definition.has("codes")) {
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("allergy_onset")) {
				allergyOnset = definition.get("allergy_onset").getAsString();
			}
			if(definition.has("referenced_by_attribute")) {
				referencedByAttribute = definition.get("referenced_by_attribute").getAsString();
			}
		}
		
		public AllergyEnd clone()
		{
			AllergyEnd clone = (AllergyEnd)super.clone();
			clone.codes = codes;
			clone.allergyOnset = allergyOnset;
			clone.referencedByAttribute = referencedByAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(allergyOnset != null) {
				person.record.allergyEndByState(time, allergyOnset);
			} else if(referencedByAttribute != null) {
				Entry allergy = (Entry) person.attributes.get(referencedByAttribute);
				allergy.stop = time;
				person.record.allergyEnd(time, allergy.type);
			} else if(codes != null) {
				codes.forEach(code -> person.record.conditionEnd(time, code.code));
			}
			return true;
		}
	}


	protected static class MedicationOrder extends State 
	{
		private List<Code> codes;
		private String reason;
		private JsonObject prescription;
		private String assignToAttribute;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("reason")) {
				reason = definition.get("reason").getAsString();
			}
			if(definition.has("prescription")) {
				prescription = definition.get("prescription").getAsJsonObject();
			}
			if(definition.has("assign_to_attribute")) {
				assignToAttribute = definition.get("assign_to_attribute").getAsString();
			}
		}
		
		public MedicationOrder clone()
		{
			MedicationOrder clone = (MedicationOrder)super.clone();
			clone.codes = codes;
			clone.reason = reason;
			clone.prescription = prescription;
			clone.assignToAttribute = assignToAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			Medication medication = person.record.medicationStart(time, primary_code);
			medication.name = this.name;
			medication.codes.addAll(codes);
			
			if(reason != null) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
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

			medication.prescriptionDetails = prescription;
				
			if(assignToAttribute != null) {
				person.attributes.put(assignToAttribute, medication);
			}
			// increment number of prescriptions prescribed by respective hospital
			Provider medicationProvider = person.getCurrentProvider(module.name);
			if(medicationProvider == null){
				// no provider associated with encounter or medication order
				medicationProvider = person.getAmbulatoryProvider();
			}

			int year = Utilities.getYear(time);
			medicationProvider.incrementPrescriptions( year );
			return true;
		}
	}


	protected static class MedicationEnd extends State 
	{
		private List<Code> codes;
		private String medicationOrder;
		private String referencedByAttribute;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
		}
		
		public MedicationEnd clone()
		{
			MedicationEnd clone = (MedicationEnd)super.clone();
			clone.codes = codes;
			clone.medicationOrder = medicationOrder;
			clone.referencedByAttribute = referencedByAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(medicationOrder != null) {
				person.record.medicationEndByState(time, medicationOrder, "expired");
			} else if(referencedByAttribute != null) {
				Medication medication = (Medication) person.attributes.get(referencedByAttribute);
				medication.stop = time;
				person.record.medicationEnd(time, medication.type, "expired");
			} else if(codes != null) {
				codes.forEach(code -> person.record.medicationEnd(time, code.code, "expired"));
			}
			return true;
		}
	}


	protected static class CarePlanStart extends State 
	{
		
		private List<Code> codes;
		private List<Code> activities;
		private List<JsonObject> goals;
		private String reason;
		private String assignToAttribute;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("activities")) {
				activities = Code.fromJson( definition.get("activities").getAsJsonArray() );
			}
			if(definition.has("goals")) {
				goals = new ArrayList<>();
				definition.get("goals").getAsJsonArray().forEach(item -> {
					goals.add(item.getAsJsonObject());
				});
			}
			if(definition.has("reason")) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
				reason = definition.get("reason").getAsString();
			}
			if(definition.has("assign_to_attribute")) {
				assignToAttribute = definition.get("assign_to_attribute").getAsString();
			}
		}
		
		public CarePlanStart clone()
		{
			CarePlanStart clone = (CarePlanStart)super.clone();
			clone.codes = codes;
			clone.activities = activities;
			clone.goals = goals;
			clone.reason = reason;
			clone.assignToAttribute = assignToAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			CarePlan careplan = person.record.careplanStart(time, primary_code);
			careplan.name = this.name;
			careplan.codes.addAll(codes);

			if(activities != null) {
				careplan.activities.addAll(activities);
			}
			if(goals != null) {
				careplan.goals.addAll(goals);
			}
			if(reason != null) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
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
			if(assignToAttribute != null) {
				person.attributes.put(assignToAttribute, careplan);
			}
			return true;
		}
	}


	protected static class CarePlanEnd extends State 
	{
		private List<Code> codes;
		private String careplan;
		private String referencedByAttribute;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("careplan")) {
				careplan = definition.get("careplan").getAsString();
			}
			if(definition.has("referenced_by_attribute")) {
				referencedByAttribute = definition.get("referenced_by_attribute").getAsString();
			}
		}
		
		public CarePlanEnd clone()
		{
			CarePlanEnd clone = (CarePlanEnd)super.clone();
			clone.codes = codes;
			clone.careplan = careplan;
			clone.referencedByAttribute = referencedByAttribute;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if(careplan != null) {
				person.record.careplanEndByState(time, careplan, "finished");
			} else if(referencedByAttribute != null) {
				CarePlan careplan = (CarePlan) person.attributes.get(referencedByAttribute);
				careplan.stop = time;
				person.record.careplanEnd(time, careplan.type, "finished");
			}  else if(codes != null) {
				codes.forEach(code -> person.record.careplanEnd(time, code.code, "finished"));
			}
			return true;
		}
	}


	protected static class Procedure extends State 
	{
		private List<Code> codes;
		private String reason;
		private Double durationLow;
		private Double durationHigh;
		private String durationUnit;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("reason")) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
				reason = definition.get("reason").getAsString();
			}
			if(definition.has("duration")) {
				JsonObject duration = definition.get("duration").getAsJsonObject();
				durationLow = duration.get("low").getAsDouble();
				durationHigh  = duration.get("high").getAsDouble();
				durationUnit = duration.get("unit").getAsString();
			}
		}
		
		public Procedure clone()
		{
			Procedure clone = (Procedure)super.clone();
			clone.codes = codes;
			clone.reason = reason;
			clone.durationLow = durationLow;
			clone.durationHigh = durationHigh;
			clone.durationUnit = durationUnit;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			HealthRecord.Procedure procedure = person.record.procedure(time, primary_code);
			procedure.name = this.name;
			procedure.codes.addAll(codes);
			
			if(reason != null) {
				// "reason" is an attribute or stateName referencing a previous conditionOnset state
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
			if(durationLow != null) {
				double duration = person.rand(durationLow, durationHigh);
				procedure.stop = procedure.start + Utilities.convertTime(durationUnit, (long) duration);
			}
			// increment number of procedures by respective hospital
			Provider provider;
			if(person.getCurrentProvider(module.name) != null){
				provider = person.getCurrentProvider(module.name);
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
		private org.mitre.synthea.world.concepts.VitalSign vitalSign;
		
		private Double quantity;
		private Double low;
		private Double high;
		private String unit;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			
			String vsName = definition.get("vital_sign").getAsString();
			vitalSign = org.mitre.synthea.world.concepts.VitalSign.fromString(vsName);
			
			if(definition.has("exact")) {
				quantity = definition.get("exact").getAsJsonObject().get("quantity").getAsDouble();
			}
			
			if(definition.has("range")) {
				low = definition.get("range").getAsJsonObject().get("low").getAsDouble();
				high = definition.get("range").getAsJsonObject().get("high").getAsDouble();
			}
			
			if(definition.has("unit")) {
				unit = definition.get("unit").getAsString();
			}
		}
		
		public Observation clone()
		{
			Observation clone = (Observation)super.clone();
			clone.quantity = quantity;
			clone.low = low;
			clone.high = high;
			clone.vitalSign = vitalSign;
			clone.unit = unit;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if (quantity != null)
			{
				person.setVitalSign(vitalSign, quantity);
			} else if (low != null && high != null)
			{
				double value = person.rand(low, high);
				person.setVitalSign(vitalSign, value);
			} else
			{
				throw new RuntimeException("VitalSign state has no exact quantity or low/high range: " + this);
			}

			return true;
		}
	}


	protected static class Observation extends State
	{
		private List<Code> codes;
		private Object quantity;
		private Double low;
		private Double high;
		private String attribute;
		private org.mitre.synthea.world.concepts.VitalSign vitalSign;
		private String category;
		private String unit;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			if(definition.has("exact")) {
				quantity = Utilities.primitive( definition.get("exact").getAsJsonObject().get("quantity").getAsJsonPrimitive() );
			}
			if(definition.has("range")) {
				low = definition.get("range").getAsJsonObject().get("low").getAsDouble();
				high = definition.get("range").getAsJsonObject().get("high").getAsDouble();
			}
			if(definition.has("attribute")) {
				attribute = definition.get("attribute").getAsString();
			}
			if(definition.has("vital_sign")) {
				String vsName = definition.get("vital_sign").getAsString();
				vitalSign = org.mitre.synthea.world.concepts.VitalSign.fromString(vsName);
			}

			if(definition.has("category")) {
				category = definition.get("category").getAsString();
			}
			if(definition.has("unit")) {
				unit = definition.get("unit").getAsString();
			}
		}
		
		public Observation clone()
		{
			Observation clone = (Observation)super.clone();
			clone.codes = codes;
			clone.quantity = quantity;
			clone.low = low;
			clone.high = high;
			clone.attribute = attribute;
			clone.vitalSign = vitalSign;
			clone.category = category;
			clone.unit = unit;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			Object value = null;
			if(quantity != null) {
				value = quantity;
			} else if(low != null && high != null) {
				value = person.rand(low, high);
			} else if(attribute != null) {
				value = person.attributes.get(attribute);
			} else if(vitalSign != null) {
				value = person.getVitalSign(vitalSign);
			}
			HealthRecord.Observation observation = person.record.observation(time, primary_code, value);
			observation.name = this.name;
			observation.codes.addAll(codes);
			observation.category = category;
			observation.unit = unit;
			
			return true;
		}
	}


	protected static class ObservationGroup extends State 
	{
		protected List<Code> codes;
		protected int numberOfObservations;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
			numberOfObservations = definition.get("number_of_observations").getAsInt();
		}
		
		public ObservationGroup clone()
		{
			ObservationGroup clone = (ObservationGroup)super.clone();
			clone.codes = codes;
			clone.numberOfObservations = numberOfObservations;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			return true;
		}
	}


	protected static class MultiObservation extends ObservationGroup 
	{
		private String category;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if(definition.has("category")) {
				category = definition.get("category").getAsString();
			}
		}
		
		public MultiObservation clone()
		{
			MultiObservation clone = (MultiObservation)super.clone();
			clone.category = category;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			HealthRecord.Observation observation = person.record.multiObservation(time, primary_code, numberOfObservations);
			observation.name = this.name;
			observation.codes.addAll(codes);
			observation.category = category;

			return true;
		}
	}


	protected static class DiagnosticReport extends ObservationGroup 
	{
		@Override
		public boolean process(Person person, long time)
		{
			String primary_code = codes.get(0).code;
			Report report = person.record.report(time, primary_code, numberOfObservations);
			report.name = this.name;
			report.codes.addAll(codes);
			
			return true;
		}
	}


	protected static class Symptom extends State 
	{
		private String symptom;
		private String cause;
		private Integer quantity;
		private Integer low;
		private Integer high;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			symptom = definition.get("symptom").getAsString();
			if(definition.has("cause")) {
				cause = definition.get("cause").getAsString();
			} else {
				cause = this.module.name;
			}
			
			JsonObject range = (JsonObject) definition.get("range");
			JsonObject exact = (JsonObject) definition.get("exact");
			if(range != null) {
				low = range.get("low").getAsInt();
				high = range.get("high").getAsInt();
			} else if(exact != null) {
				quantity = exact.get("quantity").getAsInt();
			}
		}
		
		public Symptom clone()
		{
			Symptom clone = (Symptom)super.clone();
			clone.symptom = symptom;
			clone.cause = cause;
			clone.quantity = quantity;
			clone.low = low;
			clone.high = high;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			if (quantity != null)
			{
				person.setSymptom(cause, symptom, quantity);
			} else if (low != null && high != null)
			{
				person.setSymptom(cause, symptom, (int) person.rand(low, high));
			} else
			{
				person.setSymptom(cause, symptom, 0);
			}
			return true;
		}
	}


	protected static class Death extends State 
	{
		private List<Code> codes;
		private String conditionOnset;
		private String referencedByAttribute;
		private String unit;
		private Integer quantity;
		private Integer low;
		private Integer high;
		
		@Override
		protected void initialize(Module module, String name, JsonObject definition) 
		{
			super.initialize(module, name, definition);
			if (definition.has("codes"))
			{
				codes = Code.fromJson( definition.get("codes").getAsJsonArray() );
			}
		}
		
		public Death clone()
		{
			Death clone = (Death)super.clone();
			clone.codes = codes;
			clone.conditionOnset = conditionOnset;
			clone.referencedByAttribute = referencedByAttribute;
			clone.unit = unit;
			clone.quantity = quantity;
			clone.low = low;
			clone.high = high;
			return clone;
		}

		@Override
		public boolean process(Person person, long time)
		{
			Code reason = null;
			if(codes != null) {
				reason = codes.get(0);
			} else if(conditionOnset != null) {
				if(person.hadPriorState(conditionOnset)) {
					// loop through the present conditions, the condition "name" will match
					// the name of the ConditionOnset state (aka "reason")
					for(Entry entry : person.record.present.values()) {
						if(entry.name != null && entry.name.equals(conditionOnset)) {
							reason = entry.codes.get(0);
						}
					}
				}
			} else if(referencedByAttribute != null) {
				reason = ((Entry) person.attributes.get(referencedByAttribute)).codes.get(0);
			}
			String rule = String.format("%s %s", module, name);
			if(reason != null) {
				rule = String.format("%s %s", rule, reason.display);
			}
			if (quantity != null)
			{
				long timeOfDeath = time + Utilities.convertTime(unit, (long) quantity);
				person.recordDeath(timeOfDeath, reason, rule);
				return true;
			} else if (low != null && high != null)
			{
				double duration = person.rand(low, high);
				long timeOfDeath = time + Utilities.convertTime(unit, (long) duration);
				person.recordDeath(timeOfDeath, reason, rule);
				return true;
			} else
			{
				person.recordDeath(time, reason, rule);
				return false;
			}
		}
	}


}
