package org.mitre.synthea.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math.ode.DerivativeException;

import org.mitre.synthea.engine.Components.Exact;
import org.mitre.synthea.engine.Components.ExactWithUnit;
import org.mitre.synthea.engine.Components.Range;
import org.mitre.synthea.engine.Components.RangeWithUnit;
import org.mitre.synthea.engine.Transition.ComplexTransition;
import org.mitre.synthea.engine.Transition.ComplexTransitionOption;
import org.mitre.synthea.engine.Transition.ConditionalTransition;
import org.mitre.synthea.engine.Transition.ConditionalTransitionOption;
import org.mitre.synthea.engine.Transition.DirectTransition;
import org.mitre.synthea.engine.Transition.DistributedTransition;
import org.mitre.synthea.engine.Transition.DistributedTransitionOption;
import org.mitre.synthea.engine.Transition.LookupTableTransition;
import org.mitre.synthea.engine.Transition.LookupTableTransitionOption;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.ExpressionProcessor;
import org.mitre.synthea.helpers.RandomValueGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.physiology.IoMapper;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Report;
import org.simulator.math.odes.MultiTable;

public abstract class State implements Cloneable, Serializable {
  public Module module;
  public String name;
  public Long entered;
  public Entry entry;
  public Long exited;

  private Transition transition;
  // note that these are not Transition objects, because they are JSON lists
  private String directTransition; // or in this case just a String
  private List<ConditionalTransitionOption> conditionalTransition;
  private List<DistributedTransitionOption> distributedTransition;
  private List<ComplexTransitionOption> complexTransition;
  private List<LookupTableTransitionOption> lookupTableTransition;
  public List<String> remarks;

  protected void initialize(Module module, String name, JsonObject definition) {
    this.module = module;
    this.name = name;

    if (directTransition != null) {
      this.transition = new DirectTransition(directTransition);
    } else if (distributedTransition != null) {
      this.transition = new DistributedTransition(distributedTransition);
    } else if (conditionalTransition != null) {
      this.transition = new ConditionalTransition(conditionalTransition);
    } else if (complexTransition != null) {
      this.transition = new ComplexTransition(complexTransition);
    } else if (lookupTableTransition != null) {
      this.transition = new LookupTableTransition(lookupTableTransition);
    } else if (!(this instanceof Terminal)) {
      throw new RuntimeException("State `" + name + "` has no transition.\n");
    }
  }

  /**
   * Construct a state object from the given definitions.
   *
   * @param module
   *          The module this state belongs to
   * @param name
   *          The name of the state
   * @param definition
   *          The JSON definition of the state
   * @return The constructed State object. The returned object will be of the appropriate subclass
   *         of State, based on the "type" parameter in the JSON definition.
   * @throws Exception
   *           if the state type does not exist
   */
  public static State build(Module module, String name, JsonObject definition) throws Exception {
    String className = State.class.getName() + "$" + definition.get("type").getAsString();

    Class<?> stateClass = Class.forName(className);

    Gson gson = Utilities.getGson();
    State state = (State) gson.fromJson(definition, stateClass);

    state.initialize(module, name, definition);

    return state;
  }

  /**
   * clone() should copy all the necessary variables of this State so that it can be correctly
   * executed and modified without altering the original copy. So for example, 'entered' and
   * 'exited' times should not be copied so the clone can be cleanly executed.
   */
  public State clone() {
    try {
      State clone = (State) super.clone();
      clone.module = this.module;
      clone.name = this.name;
      clone.transition = this.transition;
      clone.remarks = this.remarks;
      return clone;
    } catch (CloneNotSupportedException e) {
      // should not happen, and not something we can handle
      throw new RuntimeException(e);
    }
  }

  public String transition(Person person, long time) {
    return transition.follow(person, time);
  }

  public Transition getTransition() {
    return transition;
  }

  /**
   * Process this State with the given Person at the specified time within the simulation.
   * If this State generates a HealthRecord.Entry during processing, then the resulting data
   * will reside in the State.entry field.
   *
   * @param person
   *          : the person being simulated
   * @param time
   *          : the date within the simulated world
   * @return `true` if processing should continue to the next state, `false` if the processing
   *         should halt for this time step.
   */
  public abstract boolean process(Person person, long time);

  /**
   * Run the state. This processes the state, setting entered and exit times.
   *
   * @param person
   *          the person being simulated
   * @param time
   *          the date within the simulated world
   * @return `true` if processing should continue to the next state, `false` if the processing
   *         should halt for this time step.
   */
  public boolean run(Person person, long time) {
    // System.out.format("State: %s\n", this.name);
    if (!person.alive(time)) {
      return false;
    }
    if (this.entered == null) {
      this.entered = time;
    }
    boolean exit = process(person, time);

    if (exit) {
      // Delay state returns a special value for exited,
      // to indicate when the delay actually completed.
      if (this instanceof Delay) {
        this.exited = ((Delay)this).next;
      } else {
        this.exited = time;
      }
    }

    return exit;
  }

  public String toString() {
    return this.getClass().getSimpleName() + " '" + name + "'";
  }

  /**
   * The Initial state type is the first state that is processed in a generic module. It does not
   * provide any specific function except to indicate the starting point, so it has no properties
   * except its type. The Initial state requires the specific name "Initial". In addition, it is the
   * only state for which there can only be one in the whole module.
   */
  public static class Initial extends State {
    @Override
    public boolean process(Person person, long time) {
      return true;
    }
  }

  /**
   * The Simple state type indicates a state that performs no additional actions, adds no additional
   * information to the patient entity, and just transitions to the next state. As an example, this
   * state may be used to chain conditional or distributed transitions, in order to represent
   * complex logic.
   */
  public static class Simple extends State {
    @Override
    public boolean process(Person person, long time) {
      return true;
    }
  }

  /**
   * The CallSubmodule state immediately processes a reusable series of states contained in a
   * submodule. These states are processes in the same time step, starting with the submodule's
   * Initial state. Once the submodule's Terminal state is reached, execution of the calling module
   * resumes.
   */
  public static class CallSubmodule extends State {
    private String submodule;

    @Override
    public CallSubmodule clone() {
      CallSubmodule clone = (CallSubmodule) super.clone();
      clone.submodule = submodule;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      // e.g. "submodule": "medications/otc_antihistamine"
      List<State> moduleHistory = person.history;
      Module submod = Module.getModuleByPath(submodule);
      HealthRecord.Encounter encounter = person.getCurrentEncounter(module);
      if (encounter != null) {
        person.setCurrentEncounter(submod, encounter);
      }
      boolean completed = submod.process(person, time);

      if (completed) {
        // add the history from the submodule to this module's history, at the front
        moduleHistory.addAll(0, person.history);
        // clear the submodule history
        person.attributes.remove(submod.name);
        // reset person.history to this module's history
        person.history = moduleHistory;
        // add this state to history to indicate we returned to this module
        person.history.add(0, this);
        // start using the current encounter, it may have changed
        encounter = person.getCurrentEncounter(submod);
        if (encounter != null) {
          person.setCurrentEncounter(module, encounter);
        }
        return true;
      } else {
        // reset person.history to this module's history
        person.history = moduleHistory;
        // the submodule is still processing
        // next time we call this state it should pick up where it left off
        return false;
      }
    }
  }
  
  /**
   * The Physiology state executes a physiology simulation according to the provided
   * configuration options. Expressions can be used to map Patient attributes /
   * VitalSigns to model parameters, and vice versa, or they can be mapped directly.
   * This is an alternative way to get simulation results applicable for a specific
   * module. If a simulation is intended to provide VitalSign values, a physiology
   * value generator should be used instead.
   */
  public static class Physiology extends State {
    private String model;
    private String solver;
    private double stepSize;
    private double simDuration;
    private double leadTime;
    private List<IoMapper> inputs;
    private List<IoMapper> outputs;
    private transient PhysiologySimulator simulator;
    private transient Map<String,String> paramTypes;
    
    @Override
    protected void initialize(Module module, String name, JsonObject definition) {
      super.initialize(module, name, definition);
      
      if (leadTime > simDuration) {
        throw new IllegalArgumentException(
            "Simulation lead time cannot be greater than sim duration!");
      }

      setup();
    }
    
    private void setup() {
      simulator = new PhysiologySimulator(model, solver, stepSize, simDuration);
      paramTypes = new HashMap<String, String>();
      
      for (String param : simulator.getParameters()) {
        // Assume all physiology model inputs are lists of Decimal objects which is typically
        // the case
        // TODO: Look into whether SBML supports other parameter types, and if so, how we might map
        // those types to CQL types
        paramTypes.put(param, "List<Decimal>");
      }
      
      for (IoMapper mapper : inputs) {
        mapper.initialize(paramTypes);
      }
      for (IoMapper mapper : outputs) {
        mapper.initialize(paramTypes);
      }
    }

    @Override
    public Physiology clone() {
      super.clone();
      Physiology clone = (Physiology) super.clone();
      clone.model = model;
      clone.solver = solver;
      clone.stepSize = stepSize;
      clone.simDuration = simDuration;
      clone.leadTime = leadTime;
      
      List<IoMapper> inputList = new ArrayList<IoMapper>(inputs.size());
      for (IoMapper mapper : inputs) {
        inputList.add(new IoMapper(mapper));
      }
      clone.inputs = inputList;
      
      List<IoMapper> outputList = new ArrayList<IoMapper>(outputs.size());
      for (IoMapper mapper : outputs) {
        outputList.add(new IoMapper(mapper));
      }
      clone.outputs = outputList;
      
      clone.setup();
      
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      Map<String,Double> modelInputs = new HashMap<String,Double>();
      for (IoMapper mapper : inputs) {
        mapper.toModelInputs(person, time, modelInputs);
      }
      try {
        MultiTable results = simulator.run(modelInputs);
        for (IoMapper mapper : outputs) {
          switch (mapper.getType()) {
            default:
            case ATTRIBUTE:
              person.attributes.put(mapper.getTo(),
                  mapper.getOutputResult(results, leadTime));
              break;
            case VITAL_SIGN:
              throw new IllegalArgumentException(
                    "Mapping to VitalSigns is unsupported in the Physiology State. "
                    + "Define a physiology generator instead for \"" + mapper.getTo() + "\".");
          }
        }
      } catch (DerivativeException ex) {
        Logger.getLogger(State.class.getName()).log(Level.SEVERE, "Unable to solve simulation \""
            + model + "\" at time step " + time + " for person "
            + person.attributes.get(Person.ID), ex);
      }
      return true;
    }
    
  }

  /**
   * The Terminal state type indicates the end of the module progression. Once a Terminal state is
   * reached, no further progress will be made. As such, Terminal states cannot have any transition
   * properties. If desired, there may be multiple Terminal states with different names to indicate
   * different ending points; however, this has no actual effect on the records that are produced.
   */
  public static class Terminal extends State {
    @Override
    public boolean process(Person person, long time) {
      return false;
    }
  }

  /**
   * The Delay state type introduces a pre-configured temporal delay in the module's timeline. As a
   * simple example, a Delay state may indicate a one-month gap in time between an initial encounter
   * and a followup encounter. The module will not pass through the Delay state until the proper
   * amount of time has passed. The Delay state may define an exact time to delay (e.g. 4 days) or a
   * range of time to delay (e.g. 5 - 7 days).
   *
   * <p>Implementation Details Synthea generation occurs in time steps; the default time step is 7
   * days. This means that if a module is processed on a given date, the next time it is processed
   * will be exactly 7 days later. If a delay expiration falls between time steps (e.g. day 3 of a
   * 7-day time step), then the first time step after the delay expiration will effectively rewind
   * the clock to the delay expiration time and process states using that time. Once it reaches a
   * state that it can't pass through, it will process it once more using the original (7-day time
   * step) time.
   */
  public static class Delay extends State {
    // next is "transient" in the sense that it represents object state
    // as opposed to the other fields which represent object definition
    // hence it is not set in clone()
    public Long next;

    private RangeWithUnit<Long> range;
    private ExactWithUnit<Long> exact;


    @Override
    public Delay clone() {
      Delay clone = (Delay) super.clone();
      clone.exact = exact;
      clone.range = range;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (this.next == null) {
        if (exact != null) {
          // use an exact quantity
          this.next = time + Utilities.convertTime(exact.unit, exact.quantity);
        } else if (range != null) {
          // use a range
          this.next =
              time + Utilities.convertTime(range.unit, (long) person.rand(range.low, range.high));
        } else {
          throw new RuntimeException("Delay state has no exact or range: " + this);
        }
      }

      return ((time >= this.next) && person.alive(this.next));
    }
  }

  /**
   * The Guard state type indicates a point in the module through which a patient can only pass if
   * they meet certain logical conditions. For example, a Guard may block a workflow until the
   * patient reaches a certain age, after which the Guard allows the module to continue to progress.
   * Depending on the condition(s), a patient may be blocked by a Guard until they die - in which
   * case they never reach the module's Terminal state.
   *
   * <p>The Guard state's allow property provides the logical condition(s) which must be met to
   * allow the module to continue to the next state. Guard states are similar to conditional
   * transitions in some ways, but also have an important difference. A conditional transition
   * tests conditions once and uses the result to immediately choose the next state. A Guard
   * state will test the same condition on every time-step until the condition passes, at which
   * point it progresses to the next state.
   */
  public static class Guard extends State {
    private Logic allow;

    @Override
    public Guard clone() {
      Guard clone = (Guard) super.clone();
      clone.allow = allow;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      boolean exit = allow.test(person, time);
      if (exit) {
        this.exited = time;
      }
      return exit;
    }
  }

  /**
   * The SetAttribute state type sets a specified attribute on the patient entity. In addition to
   * the assign_to_attribute property on MedicationOrder/ConditionOnset/etc states, this state
   * allows for arbitrary text or values to be set on an attribute, or for the attribute to be
   * reset.
   */
  public static class SetAttribute extends State {
    private String attribute;
    private Object value;
    private Range<Double> range;
    private String expression;
    private transient ThreadLocal<ExpressionProcessor> threadExpProcessor;
    
    private ThreadLocal<ExpressionProcessor> getExpProcessor() {
      // If the ThreadLocal instance hasn't been created yet, create it now
      if (threadExpProcessor == null) {
        threadExpProcessor = new ThreadLocal<ExpressionProcessor>();
      }
      
      // If there's an expression, create the processor for it
      if (this.expression != null && threadExpProcessor.get() == null) { 
        threadExpProcessor.set(new ExpressionProcessor(this.expression));
      }

      return threadExpProcessor;
    }

    @Override
    protected void initialize(Module module, String name, JsonObject definition) {
      super.initialize(module, name, definition);
      
      // special handling for integers
      if (value instanceof Double) {
        double doubleVal = (double)value;

        if (doubleVal == Math.rint(doubleVal)) {
          value = (int) doubleVal;
        }
      }
    }

    @Override
    public SetAttribute clone() {
      SetAttribute clone = (SetAttribute) super.clone();
      clone.attribute = attribute;
      clone.value = value;
      clone.range = range;
      clone.expression = expression;
      clone.threadExpProcessor = threadExpProcessor;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      ThreadLocal<ExpressionProcessor> expProcessor = getExpProcessor();
      if (expProcessor.get() != null) {
        value = expProcessor.get().evaluate(person, time);
      } else if (range != null) {
        value = person.rand(range.low, range.high, range.decimals);
      }

      if (value != null) {
        person.attributes.put(attribute, value);
      } else if (person.attributes.containsKey(attribute)) {
        // intentionally clear out the variable
        person.attributes.remove(attribute);
      }

      return true;
    }
  }

  /**
   * The Counter state type increments or decrements a specified numeric attribute on the patient
   * entity. In essence, this state counts the number of times it is processed.
   *
   * <p>Note: The attribute is initialized with a default value of 0 if not previously set.
   */
  public static class Counter extends State {
    private String attribute;
    private String action;
    private boolean increment;
    private int amount;

    @Override
    protected void initialize(Module module, String name, JsonObject definition) {
      super.initialize(module, name, definition);
      increment = action.equals("increment");
      if (amount == 0) {
        // default to 1 for legacy compatibility
        amount = 1;
      }
    }

    @Override
    public Counter clone() {
      Counter clone = (Counter) super.clone();
      clone.attribute = attribute;
      clone.increment = increment;
      clone.amount = amount;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      int counter = 0;
      if (person.attributes.containsKey(attribute)) {
        // this cast as int from double is to handle cases where the attribute
        // is either a java.lang.Double or java.lang.Integer
        counter = (int) Double.parseDouble(person.attributes.get(attribute).toString());
      }

      if (increment) {
        counter = counter + amount;
      } else {
        counter = counter - amount;
      }
      person.attributes.put(attribute, counter);
      return true;
    }
  }

  /**
   * The Encounter state type indicates a point in the module where an encounter should take place.
   * Encounters are important in Synthea because they are generally the mechanism through which the
   * actual patient record is updated (a disease is diagnosed, a medication is prescribed, etc). The
   * generic module framework supports integration with scheduled wellness encounters from Synthea's
   * Encounters module, as well as creation of new stand-alone encounters.
   *
   * <p>Scheduled Wellness Encounters vs. Standalone Encounters An Encounter state with the wellness
   * property set to true will block until the next scheduled wellness encounter occurs. Scheduled
   * wellness encounters are managed by the Encounters module in Synthea and, depending on the
   * patient's age, typically occur every 1 - 3 years. When a scheduled wellness encounter finally
   * does occur, Synthea will search the generic modules for currently blocked Encounter states and
   * will immediately process them (and their subsequent states). An example where this might be
   * used is for a condition that onsets between encounters, but isn't found and diagnosed until the
   * next regularly scheduled wellness encounter.
   *
   * <p>An Encounter state without the wellness property set will be processed and recorded in the
   * patient record immediately. Since this creates an encounter, the encounter_class and one or
   * more codes must be specified in the state configuration. This is how generic modules can
   * introduce encounters that are not already scheduled by other modules.
   *
   * <p>Encounters and Related Events Encounters are typically the mechanism through which a
   * patient's record will be updated. This makes sense since most recorded events (diagnoses,
   * prescriptions, and procedures) should happen in the context of an encounter. When an Encounter
   * state is successfully processed, Synthea will look through the previously processed states for
   * un-recorded ConditionOnset or AllergyOnset instances that indicate that Encounter (by name) as
   * the target_encounter. If Synthea finds any, they will be recorded in the patient's record at
   * the time of the encounter. This is the mechanism for onsetting a disease before it is
   * discovered and diagnosed.
   */
  public static class Encounter extends State {
    private boolean wellness;
    private String encounterClass;
    private List<Code> codes;
    private String reason;

    @Override
    public Encounter clone() {
      Encounter clone = (Encounter) super.clone();
      clone.wellness = wellness;
      clone.encounterClass = encounterClass;
      clone.reason = reason;
      clone.codes = codes;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (wellness) {
        HealthRecord.Encounter encounter = person.record.currentEncounter(time);
        entry = encounter;
        String activeKey = EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + this.module.name;
        if (person.attributes.containsKey(activeKey)) {
          person.attributes.remove(activeKey);
          person.setCurrentEncounter(module, encounter);
          diagnosePastConditions(person, time);
          if (!encounter.chronicMedsRenewed && person.chronicMedications.size() > 0) {
            renewChronicMedicationsAtWellness(person, time);
            encounter.chronicMedsRenewed = true;
          }
          return true;
        } else {
          // Block until we're in a wellness encounter... then proceed.
          return false;
        }
      } else {
        EncounterType type = EncounterType.fromString(encounterClass);
        HealthRecord.Encounter encounter = EncounterModule.createEncounter(person, time, type,
            ClinicianSpecialty.GENERAL_PRACTICE, null);
        entry = encounter;
        if (codes != null) {
          encounter.codes.addAll(codes);
        }
        person.setCurrentEncounter(module, encounter);
        encounter.name = this.name;

        diagnosePastConditions(person, time);

        if (reason != null) {
          if (person.attributes.containsKey(reason)) {
            Entry condition = (Entry) person.attributes.get(reason);
            encounter.reason = condition.codes.get(0);
          } else if (person.hadPriorState(reason)) {
            // loop through the present conditions, the condition "name" will match
            // the name of the ConditionOnset state (aka "reason")
            for (Entry entry : person.record.present.values()) {
              if (reason.equals(entry.name)) {
                encounter.reason = entry.codes.get(0);
                break;
              }
            }
          }
        }

        return true;
      }
    }

    private void diagnosePastConditions(Person person, long time) {
      // reminder: history[0] is current state, history[size-1] is Initial
      for (State state : person.history) {
        if (state instanceof OnsetState) {
          OnsetState onset = (OnsetState) state;

          if (!onset.diagnosed && this.name.equals(onset.targetEncounter)) {
            onset.diagnose(person, time);
          }
        } else if (state instanceof Encounter && state != this && state.name.equals(this.name)) {
          // a prior instance of hitting this same state. no need to go back any further
          break;
        }
      }
    }

    private void renewChronicMedicationsAtWellness(Person person, long time) {
      // note that this code has some child codes for various different reasons,
      // eg "medical aim achieved", "ineffective", "avoid interaction", "side effect", etc
      Code expiredCode = new Code("SNOMED-CT", "182840001", 
          "Drug treatment stopped - medical advice");

      // We keep track of the meds we renewed to add them to the chronic list later
      // as we can't modify the list of chronic meds while iterating.
      List<Medication> renewedMedications =
          new ArrayList<Medication>(person.chronicMedications.values().size());

      // Go through each chronic medication and "reorder"
      for (Medication chronicMedication : person.chronicMedications.values()) {
        // RxNorm code
        String primaryCode = chronicMedication.type;

        // Removes from Chronic List as well; but won't affect iterator.
        person.record.medicationEnd(time, primaryCode, expiredCode);

        // IMPORTANT: 3rd par is false to prevent modification of chronic meds
        // list as we iterate over it According to the documentation, the
        // results of modifying the array (x remove) are undefined
        Medication medication = person.record.medicationStart(time, primaryCode,
            false);

        // Copy over the characteristics from old medication to new medication
        medication.name = chronicMedication.name;
        medication.codes.addAll(chronicMedication.codes);
        medication.reasons.addAll(chronicMedication.reasons);
        medication.prescriptionDetails = chronicMedication.prescriptionDetails;
        medication.administration = chronicMedication.administration;
        // NB: The next one isn't present. Normally done by
        // person.record.medicationStart, but we are avoiding modifying the
        // chronic meds list until we are done iterating
        medication.chronic = true;

        // increment number of prescriptions prescribed by respective hospital
        Provider medicationProvider = person.getCurrentProvider(module.name);
        if (medicationProvider == null) {
          // no provider associated with encounter or medication order
          medicationProvider = person.getProvider(EncounterType.WELLNESS, time);
        }
        int year = Utilities.getYear(time);
        medicationProvider.incrementPrescriptions(year);

        renewedMedications.add(medication);
      }

      // Reinitialize the chronic meds list with the meds we just created
      // Perhaps not technically necessary, as we can just keep the old ones
      // around, but this is safer.
      person.chronicMedications.clear();
      for (Medication renewedMedication : renewedMedications) {
        person.chronicMedications.put(renewedMedication.type, renewedMedication);
      }

    }

    public boolean isWellness() {
      return wellness;
    }
  }

  /**
   * The EncounterEnd state type indicates the end of the encounter the patient is currently in, for
   * example when the patient leaves a clinician's office, or is discharged from a hospital. The
   * time the encounter ended is recorded on the patient's record.
   *
   * <p>Note on Wellness Encounters Because wellness encounters are scheduled and initiated outside
   * the generic modules, and a single wellness encounter may contain observations or medications
   * from multiple modules, an EncounterEnd state will not record the end time for a wellness
   * encounter. Hence it is not strictly necessary to use an EncounterEnd state to end the wellness
   * encounter. Still, it is recommended to use an EncounterEnd state to mark a clear end to the
   * encounter.
   */
  public static class EncounterEnd extends State {
    private Code dischargeDisposition;

    @Override
    public EncounterEnd clone() {
      EncounterEnd clone = (EncounterEnd) super.clone();
      clone.dischargeDisposition = dischargeDisposition;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      HealthRecord.Encounter encounter = person.getCurrentEncounter(module);
      EncounterType type = EncounterType.fromString(encounter.type);
      if (type != EncounterType.WELLNESS) {
        person.record.encounterEnd(time, type);
      }
      encounter.discharge = dischargeDisposition;

      // reset current provider hash
      person.removeCurrentProvider(module.name);
      person.setCurrentEncounter(module, null);

      return true;
    }
  }

  /**
   * OnsetState is a parent class for ConditionOnset and AllergyOnset, where some common logic can
   * be shared. It is an implementation detail and should never be referenced directly in a JSON
   * module.
   */
  private abstract static class OnsetState extends State {
    public boolean diagnosed;

    protected List<Code> codes;
    protected String assignToAttribute;
    protected String targetEncounter;

    public OnsetState clone() {
      OnsetState clone = (OnsetState) super.clone();
      clone.codes = codes;
      clone.assignToAttribute = assignToAttribute;
      clone.targetEncounter = targetEncounter;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      HealthRecord.Encounter encounter = person.getCurrentEncounter(module);

      if (targetEncounter == null || targetEncounter.trim().length() == 0
          || (encounter != null && targetEncounter.equals(encounter.name))) {
        diagnose(person, time);
      } else if (assignToAttribute != null && codes != null) {
        // create a temporary coded entry to use for reference in the attribute,
        // which will be replaced if the thing is diagnosed
        HealthRecord.Entry codedEntry = person.record.new Entry(time, codes.get(0).code);
        codedEntry.codes.addAll(codes);

        person.attributes.put(assignToAttribute, codedEntry);
      }
      return true;
    }

    public abstract void diagnose(Person person, long time);
  }

  /**
   * The ConditionOnset state type indicates a point in the module where the patient acquires a
   * condition. This is not necessarily the same as when the condition is diagnosed and recorded in
   * the patient's record. In fact, it is possible for a condition to onset but never be discovered.
   *
   * <p>If the ConditionOnset state's target_encounter is set to the name of a future encounter,
   * then the condition will only be diagnosed when that future encounter occurs.
   */
  public static class ConditionOnset extends OnsetState {
    @Override
    public void diagnose(Person person, long time) {
      String primaryCode = codes.get(0).code;
      entry = person.record.conditionStart(time, primaryCode);
      entry.name = this.name;
      if (codes != null) {
        entry.codes.addAll(codes);
      }
      if (assignToAttribute != null) {
        person.attributes.put(assignToAttribute, entry);
      }

      diagnosed = true;
    }
  }

  /**
   * The ConditionEnd state type indicates a point in the module where a currently active condition
   * should be ended, for example if the patient has been cured of a disease.
   *
   * <p>The ConditionEnd state supports three ways of specifying the condition to end: By `codes[]`,
   * specifying the system, code, and display name of the condition to end By `condition_onset`,
   * specifying the name of the ConditionOnset state in which the condition was onset By
   * `referenced_by_attribute`, specifying the name of the attribute to which a previous
   * ConditionOnset state assigned a condition
   */
  public static class ConditionEnd extends State {
    private List<Code> codes;
    private String conditionOnset;
    private String referencedByAttribute;

    @Override
    public ConditionEnd clone() {
      ConditionEnd clone = (ConditionEnd) super.clone();
      clone.codes = codes;
      clone.conditionOnset = conditionOnset;
      clone.referencedByAttribute = referencedByAttribute;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (conditionOnset != null) {
        person.record.conditionEndByState(time, conditionOnset);
      } else if (referencedByAttribute != null) {
        Entry condition = (Entry) person.attributes.get(referencedByAttribute);
        condition.stop = time;
        person.record.conditionEnd(time, condition.type);
      } else if (codes != null) {
        codes.forEach(code -> person.record.conditionEnd(time, code.code));
      }
      return true;
    }
  }

  /**
   * The AllergyOnset state type indicates a point in the module where the patient acquires an
   * allergy. This is not necessarily the same as when the allergy is diagnosed and recorded in the
   * patient's record. In fact, it is possible for an allergy to onset but never be discovered.
   *
   * <p>If the AllergyOnset state's target_encounter is set to the name of a future encounter,
   * then the allergy will only be diagnosed when that future encounter occurs.
   */
  public static class AllergyOnset extends OnsetState {
    @Override
    public void diagnose(Person person, long time) {
      String primaryCode = codes.get(0).code;
      entry = person.record.allergyStart(time, primaryCode);
      entry.name = this.name;
      entry.codes.addAll(codes);

      if (assignToAttribute != null) {
        person.attributes.put(assignToAttribute, entry);
      }

      diagnosed = true;
    }
  }

  /**
   * The AllergyEnd state type indicates a point in the module where a currently active allergy
   * should be ended, for example if the patient's allergy subsides with time.
   *
   * <p>The AllergyEnd state supports three ways of specifying the allergy to end: By `codes[]`,
   * specifying the system, code, and display name of the allergy to end By `allergy_onset`,
   * specifying the name of the AllergyOnset state in which the allergy was onset By
   * `referenced_by_attribute`, specifying the name of the attribute to which a previous
   * AllergyOnset state assigned a condition
   *
   */
  public static class AllergyEnd extends State {
    private List<Code> codes;

    private String allergyOnset;
    private String referencedByAttribute;

    @Override
    public AllergyEnd clone() {
      AllergyEnd clone = (AllergyEnd) super.clone();
      clone.codes = codes;
      clone.allergyOnset = allergyOnset;
      clone.referencedByAttribute = referencedByAttribute;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (allergyOnset != null) {
        person.record.allergyEndByState(time, allergyOnset);
      } else if (referencedByAttribute != null) {
        Entry allergy = (Entry) person.attributes.get(referencedByAttribute);
        allergy.stop = time;
        person.record.allergyEnd(time, allergy.type);
      } else if (codes != null) {
        codes.forEach(code -> person.record.conditionEnd(time, code.code));
      }
      return true;
    }
  }

  /**
   * The MedicationOrder state type indicates a point in the module where a medication is
   * prescribed. MedicationOrder states may only be processed during an Encounter, and so must
   * occur after the target Encounter state and before the EncounterEnd. See the Encounter
   * section above for more details. The MedicationOrder state supports identifying a previous
   * ConditionOnset or the name of an attribute as the reason for the prescription. Adding a
   * 'administration' field allows for the MedicationOrder to also export a
   * MedicationAdministration into the exported FHIR record.
   */
  public static class MedicationOrder extends State {
    private List<Code> codes;
    private String reason;
    private transient JsonObject prescription; // TODO make this a Component
    private String assignToAttribute;
    private boolean administration;
    private boolean chronic;
    
    /**
     * Java Serialization support method to serialize the JsonObject prescription which isn't
     * natively serializable.
     * @param oos the stream to write to
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
      oos.defaultWriteObject();
      if (prescription != null) {
        oos.writeObject(prescription.toString());
      } else {
        oos.writeObject(null);
      }
    }
    
    /**
     * Java Serialization support method to deserialize the JsonObject prescription which isn't
     * natively serializable.
     * @param ois the stream to read from
     */
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
      ois.defaultReadObject();
      String prescriptionJson = (String) ois.readObject();
      if (prescriptionJson != null) {
        Gson gson = Utilities.getGson();
        this.prescription = gson.fromJson(prescriptionJson, JsonObject.class);
      }
    }

    @Override
    public MedicationOrder clone() {
      MedicationOrder clone = (MedicationOrder) super.clone();
      clone.codes = codes;
      clone.reason = reason;
      clone.prescription = prescription;
      clone.assignToAttribute = assignToAttribute;
      clone.administration = administration;
      clone.chronic = chronic;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      String primaryCode = codes.get(0).code;
      Medication medication = person.record.medicationStart(time, primaryCode, chronic);
      entry = medication;
      medication.name = this.name;
      medication.codes.addAll(codes);

      if (reason != null) {
        // "reason" is an attribute or stateName referencing a previous conditionOnset state
        if (person.attributes.containsKey(reason)) {
          Entry condition = (Entry) person.attributes.get(reason);
          medication.reasons.addAll(condition.codes);
        } else if (person.hadPriorState(reason)) {
          // loop through the present conditions, the condition "name" will match
          // the name of the ConditionOnset state (aka "reason")
          for (Entry entry : person.record.present.values()) {
            if (reason.equals(entry.name)) {
              medication.reasons.addAll(entry.codes);
            }
          }
        }
      }

      medication.prescriptionDetails = prescription;
      medication.administration = administration;

      if (assignToAttribute != null) {
        person.attributes.put(assignToAttribute, medication);
      }
      // increment number of prescriptions prescribed by respective hospital
      Provider medicationProvider = person.getCurrentProvider(module.name);
      if (medicationProvider == null) {
        // no provider associated with encounter or medication order
        medicationProvider = person.getProvider(EncounterType.WELLNESS, time);
      }

      int year = Utilities.getYear(time);
      medicationProvider.incrementPrescriptions(year);
      return true;
    }
  }

  /**
   * The MedicationEnd state type indicates a point in the module where a currently prescribed
   * medication should be ended.
   *
   * <p>The MedicationEnd state supports three ways of specifying the medication to end:
   * By `codes[]`, specifying the code system, code, and display name of the medication to end By
   * `medication_order`, specifying the name of the MedicationOrder state in which the medication
   * was prescribed By `referenced_by_attribute`, specifying the name of the attribute to which a
   * previous MedicationOrder state assigned a medication
   */
  public static class MedicationEnd extends State {
    private List<Code> codes;
    private String medicationOrder;
    private String referencedByAttribute;

    // note that this code has some child codes for various different reasons,
    // ex "medical aim achieved", "ineffective", "avoid interaction", "side effect", etc
    private static final Code EXPIRED = new Code("SNOMED-CT", "182840001",
        "Drug treatment stopped - medical advice");

    @Override
    public MedicationEnd clone() {
      MedicationEnd clone = (MedicationEnd) super.clone();
      clone.codes = codes;
      clone.medicationOrder = medicationOrder;
      clone.referencedByAttribute = referencedByAttribute;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (medicationOrder != null) {
        person.record.medicationEndByState(time, medicationOrder, EXPIRED);
      } else if (referencedByAttribute != null) {
        Medication medication = (Medication) person.attributes.get(referencedByAttribute);
        medication.stop = time;
        person.record.medicationEnd(time, medication.type, EXPIRED);
      } else if (codes != null) {
        codes.forEach(code -> person.record.medicationEnd(time, code.code, EXPIRED));
      }
      return true;
    }
  }

  /**
   * The CarePlanStart state type indicates a point in the module where a care plan should be
   * prescribed. CarePlanStart states may only be processed during an Encounter, and so must occur
   * after the target Encounter state and before the EncounterEnd. See the Encounter section above
   * for more details. One or more codes describes the care plan and a list of activities describes
   * what the care plan entails.
   */
  public static class CarePlanStart extends State {
    private List<Code> codes;
    private List<Code> activities;
    private transient List<JsonObject> goals; // TODO: make this a Component
    private String reason;
    private String assignToAttribute;

    @Override
    public CarePlanStart clone() {
      CarePlanStart clone = (CarePlanStart) super.clone();
      clone.codes = codes;
      clone.activities = activities;
      clone.goals = goals;
      clone.reason = reason;
      clone.assignToAttribute = assignToAttribute;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      String primaryCode = codes.get(0).code;
      CarePlan careplan = person.record.careplanStart(time, primaryCode);
      entry = careplan;
      careplan.name = this.name;
      careplan.codes.addAll(codes);

      if (activities != null) {
        careplan.activities.addAll(activities);
      }
      if (goals != null) {
        careplan.goals.addAll(goals);
      }
      if (reason != null) {
        // "reason" is an attribute or stateName referencing a previous conditionOnset state
        if (person.attributes.containsKey(reason)) {
          Entry condition = (Entry) person.attributes.get(reason);
          careplan.reasons.addAll(condition.codes);
        } else if (person.hadPriorState(reason)) {
          // loop through the present conditions, the condition "name" will match
          // the name of the ConditionOnset state (aka "reason")
          for (Entry entry : person.record.present.values()) {
            if (reason.equals(entry.name)) {
              careplan.reasons.addAll(entry.codes);
            }
          }
        }
      }
      if (assignToAttribute != null) {
        person.attributes.put(assignToAttribute, careplan);
      }
      return true;
    }
  }

  /**
   * The CarePlanEnd state type indicates a point in the module where a currently prescribed care
   * plan should be ended. The CarePlanEnd state supports three ways of specifying the care plan to
   * end: By `codes[]`, specifying the code system, code, and display name of the care plan to end
   * By `careplan`, specifying the name of the CarePlanStart state in which the care plan was
   * prescribed By `referenced_by_attribute`, specifying the name of the attribute to which a
   * previous CarePlanStart state assigned a care plan
   */
  public static class CarePlanEnd extends State {
    private List<Code> codes;
    private String careplan;
    private String referencedByAttribute;

    private static final Code FINISHED = new Code("SNOMED-CT", "385658003", "Done");

    @Override
    public CarePlanEnd clone() {
      CarePlanEnd clone = (CarePlanEnd) super.clone();
      clone.codes = codes;
      clone.careplan = careplan;
      clone.referencedByAttribute = referencedByAttribute;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (careplan != null) {
        person.record.careplanEndByState(time, careplan, FINISHED);
      } else if (referencedByAttribute != null) {
        CarePlan careplan = (CarePlan) person.attributes.get(referencedByAttribute);
        careplan.stop = time;
        person.record.careplanEnd(time, careplan.type, FINISHED);
      } else if (codes != null) {
        codes.forEach(code -> person.record.careplanEnd(time, code.code, FINISHED));
      }
      return true;
    }
  }

  /**
   * The Procedure state type indicates a point in the module where a procedure should be performed.
   * Procedure states may only be processed during an Encounter, and so must occur after the target
   * Encounter state and before the EncounterEnd. See the Encounter section above for more details.
   * Optionally, you may define a duration of time that the procedure takes. The Procedure also
   * supports identifying a previous ConditionOnset or an attribute as the reason for the procedure.
   */
  public static class Procedure extends State {
    private List<Code> codes;
    private String reason;
    private RangeWithUnit<Long> duration;
    private String assignToAttribute;

    @Override
    public Procedure clone() {
      Procedure clone = (Procedure) super.clone();
      clone.codes = codes;
      clone.reason = reason;
      clone.duration = duration;
      clone.assignToAttribute = assignToAttribute;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      String primaryCode = codes.get(0).code;
      HealthRecord.Procedure procedure = person.record.procedure(time, primaryCode);
      entry = procedure;
      procedure.name = this.name;
      procedure.codes.addAll(codes);

      if (reason != null) {
        // "reason" is an attribute or stateName referencing a previous conditionOnset state
        if (person.attributes.containsKey(reason)) {
          Entry condition = (Entry) person.attributes.get(reason);
          procedure.reasons.addAll(condition.codes);
        } else if (person.hadPriorState(reason)) {
          // loop through the present conditions, the condition "name" will match
          // the name of the ConditionOnset state (aka "reason")
          for (Entry entry : person.record.present.values()) {
            if (reason.equals(entry.name)) {
              procedure.reasons.addAll(entry.codes);
            }
          }
        }
      }
      if (duration != null) {
        double durationVal = person.rand(duration.low, duration.high);
        procedure.stop = procedure.start + Utilities.convertTime(duration.unit, (long) durationVal);
      }
      // increment number of procedures by respective hospital
      Provider provider;
      if (person.getCurrentProvider(module.name) != null) {
        provider = person.getCurrentProvider(module.name);
      } else { // no provider associated with encounter or procedure
        provider = person.getProvider(EncounterType.WELLNESS, time);
      }
      int year = Utilities.getYear(time);
      provider.incrementProcedures(year);

      if (assignToAttribute != null) {
        person.attributes.put(assignToAttribute, procedure);
      }

      return true;
    }
  }

  /**
   * The VitalSign state type indicates a point in the module where a patient's vital sign is set.
   * Vital Signs represent the actual physical state of the patient, in contrast to Observations
   * which are the recording of that physical state.
   *
   * <p>Usage Notes In general, the Vital Sign should be used if the value directly affects the
   * patient's physical condition. For example, high blood pressure directly increases the risk of
   * heart attack so any conditional logic that would trigger a heart attack should reference a
   * Vital Sign instead of an Observation. ' On the other hand, if the value only affects the
   * patient's care, using just an Observation would be more appropriate. For example, it is the
   * observation of MMSE that can lead to a diagnosis of Alzheimer's; MMSE is an observed value and
   * not a physical metric, so it should not be stored in a VitalSign.
   */
  public static class VitalSign extends State {
    private org.mitre.synthea.world.concepts.VitalSign vitalSign;
    private String unit;
    private Range<Double> range;
    private Exact<Double> exact;
    private String expression;
    private transient ThreadLocal<ExpressionProcessor> threadExpProcessor;
    
    private ThreadLocal<ExpressionProcessor> getExpProcessor() {
      // If the ThreadLocal instance hasn't been created yet, create it now
      if (threadExpProcessor == null) {
        threadExpProcessor = new ThreadLocal<ExpressionProcessor>();
      }
      
      // If there's an expression, create the processor for it
      if (this.expression != null && threadExpProcessor.get() == null) { 
        threadExpProcessor.set(new ExpressionProcessor(this.expression));
      }

      return threadExpProcessor;
    }
    
    @Override
    public VitalSign clone() {
      VitalSign clone = (VitalSign) super.clone();
      clone.range = range;
      clone.exact = exact;
      clone.vitalSign = vitalSign;
      clone.unit = unit;
      clone.expression = expression;
      clone.threadExpProcessor = threadExpProcessor;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (exact != null) {
        person.setVitalSign(vitalSign, new ConstantValueGenerator(person, exact.quantity));
      } else if (range != null) {
        person.setVitalSign(vitalSign, new RandomValueGenerator(person, range.low, range.high));
      } else if (getExpProcessor().get() != null) {
        Number value = (Number) getExpProcessor().get().evaluate(person, time);
        person.setVitalSign(vitalSign, value.doubleValue());
      } else {
        throw new RuntimeException(
            "VitalSign state has no exact quantity or low/high range: " + this);
      }

      return true;
    }
  }

  /**
   * The Observation state type indicates a point in the module where an observation is recorded.
   * Observations include clinical findings, vital signs, lab tests, etc. Observation states may
   * only be processed during an Encounter, and so must occur after the target Encounter state and
   * before the EncounterEnd. See the Encounter section above for more details.
   *
   * <p>Observation Categories Common observation categories include: "vital-signs" :
   * Clinical observations measure the body's basic functions such as such as blood pressure, heart
   * rate, respiratory rate, height, weight, body mass index, head circumference, pulse oximetry,
   * temperature, and body surface area.
   *
   * <p>"procedure" : Observations generated by other procedures. This category includes
   * observations resulting from interventional and non-interventional procedures excluding lab and
   * imaging (e.g. cardiology catheterization, endoscopy, electrodiagnostics, etc.). Procedure
   * results are typically generated by a clinician to provide more granular information about
   * component observations made during a procedure, such as where a gastroenterologist reports the
   * size of a polyp observed during a colonoscopy.
   *
   * <p>"laboratory" : The results of observations generated by laboratories. Laboratory results are
   * typically generated by laboratories providing analytic services in areas such as chemistry,
   * hematology, serology, histology, cytology, anatomic pathology, microbiology, and/or virology.
   * These observations are based on analysis of specimens obtained from the patient and submitted
   * to the laboratory.
   *
   * <p>"exam" : Observations generated by physical exam findings including direct observations made
   * by a clinician and use of simple instruments and the result of simple maneuvers performed
   * directly on the patient's body.
   *
   * <p>"social-history" : The Social History Observations define the patient's occupational,
   * personal (e.g. lifestyle), social, and environmental history and health risk factors, as well
   * as administrative data such as marital status, race, ethnicity and religious affiliation.
   */
  public static class Observation extends State {
    private List<Code> codes;
    private Range<Double> range;
    private Exact<Object> exact;
    private Code valueCode;
    private String attribute;
    private org.mitre.synthea.world.concepts.VitalSign vitalSign;
    private String category;
    private String unit;
    private String expression;
    private transient ThreadLocal<ExpressionProcessor> threadExpProcessor;
    
    private ThreadLocal<ExpressionProcessor> getExpProcessor() {
      // If the ThreadLocal instance hasn't been created yet, create it now
      if (threadExpProcessor == null) {
        threadExpProcessor = new ThreadLocal<ExpressionProcessor>();
      }
      
      // If there's an expression, create the processor for it
      if (this.expression != null && threadExpProcessor.get() == null) { 
        threadExpProcessor.set(new ExpressionProcessor(this.expression));
      }

      return threadExpProcessor;
    }
    
    @Override
    public Observation clone() {
      Observation clone = (Observation) super.clone();
      clone.codes = codes;
      clone.range = range;
      clone.exact = exact;
      clone.valueCode = valueCode;
      clone.attribute = attribute;
      clone.vitalSign = vitalSign;
      clone.category = category;
      clone.unit = unit;
      clone.expression = expression;
      clone.threadExpProcessor = threadExpProcessor;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      String primaryCode = codes.get(0).code;
      Object value = null;
      if (exact != null) {
        value = exact.quantity;
      } else if (range != null) {
        value = person.rand(range.low, range.high, range.decimals);
      } else if (attribute != null) {
        value = person.attributes.get(attribute);
      } else if (vitalSign != null) {
        value = person.getVitalSign(vitalSign, time);
      } else if (valueCode != null) {
        value = valueCode;
      } else if (getExpProcessor().get() != null) {
        value = getExpProcessor().get().evaluate(person, time);
      } 
      HealthRecord.Observation observation = person.record.observation(time, primaryCode, value);
      entry = observation;
      observation.name = this.name;
      observation.codes.addAll(codes);
      observation.category = category;
      observation.unit = unit;

      return true;
    }
  }

  /**
   * ObservationGroup is an internal parent class to provide common logic to state types that
   * package multiple observations into a single entity. It is an implementation detail and should
   * not be referenced by JSON modules directly.
   */
  private abstract static class ObservationGroup extends State {
    protected List<Code> codes;
    protected List<Observation> observations;

    public ObservationGroup clone() {
      ObservationGroup clone = (ObservationGroup) super.clone();
      clone.codes = codes;
      clone.observations = observations;
      return clone;
    }
  }

  /**
   * The MultiObservation state indicates that some number of Observations should be
   * grouped together as a single observation. This can be necessary when one observation records
   * multiple values, for example in the case of Blood Pressure, which is really 2 values, Systolic
   * and Diastolic Blood Pressure.  MultiObservation states may only be processed
   * during an Encounter, and so must occur after the target Encounter state and before the
   * EncounterEnd. See the Encounter section above for more details.
   */
  public static class MultiObservation extends ObservationGroup {
    private String category;

    @Override
    public MultiObservation clone() {
      MultiObservation clone = (MultiObservation) super.clone();
      clone.category = category;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      for (Observation o : observations) {
        o.process(person, time);
      }
      String primaryCode = codes.get(0).code;
      HealthRecord.Observation observation =
          person.record.multiObservation(time, primaryCode, observations.size());
      entry = observation;
      observation.name = this.name;
      observation.codes.addAll(codes);
      observation.category = category;

      return true;
    }
  }

  /**
   * The DiagnosticReport state indicates that some number of Observations should be
   * grouped together within a single Diagnostic Report. This can be used when multiple observations
   * are part of a single panel. DiagnosticReport states may only be processed during an Encounter,
   * and so must occur after the target Encounter state and before the EncounterEnd. See the
   * Encounter section above for more details.
   */
  public static class DiagnosticReport extends ObservationGroup {
    @Override
    public boolean process(Person person, long time) {
      for (Observation o : observations) {
        o.process(person, time);
      }
      String primaryCode = codes.get(0).code;
      Report report = person.record.report(time, primaryCode, observations.size());
      entry = report;
      report.name = this.name;
      report.codes.addAll(codes);

      // increment number of labs by respective provider
      Provider provider;
      if (person.getCurrentProvider(module.name) != null) {
        provider = person.getCurrentProvider(module.name);
      } else { // no provider associated with encounter or procedure
        provider = person.getProvider(EncounterType.WELLNESS, time);
      }
      int year = Utilities.getYear(time);
      provider.incrementLabs(year);

      return true;
    }
  }

  /**
   * The ImagingStudy state indicates a point in the module when an imaging study was performed.
   * An ImagingStudy consists of one or more Studies, where each Study contains one or more
   * Instances of an image. ImagingStudy states may only be processed during an Encounter,
   * and must occur after the target Encounter state and before the EncounterEnd. See the
   * Encounter section above for more details.
   */
  public static class ImagingStudy extends State {
    /** The equivalent SNOMED codes that describe this ImagingStudy as a Procedure. */
    private Code procedureCode;
    /** The Series of Instances that represent this ImagingStudy. */
    private List<HealthRecord.ImagingStudy.Series> series;
    /** Minimum and maximum number of series in this study.
     * Actual number is picked uniformly randomly from this range, copying series data from
     * the first series provided. */
    public int minNumberSeries = 0;
    public int maxNumberSeries = 0;

    @Override
    public ImagingStudy clone() {
      ImagingStudy clone = (ImagingStudy) super.clone();
      clone.procedureCode = procedureCode;
      clone.series = series;
      clone.minNumberSeries = minNumberSeries;
      clone.maxNumberSeries = maxNumberSeries;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      // Randomly pick number of series and instances if bounds were provided
      duplicateSeries(person);
      duplicateInstances(person);

      // The modality code of the first series is a good approximation
      // of the type of ImagingStudy this is
      String primaryModality = series.get(0).modality.code;
      entry = person.record.imagingStudy(time, primaryModality, series);

      // Also add the Procedure equivalent of this ImagingStudy to the patient's record
      String primaryProcedureCode = procedureCode.code;
      HealthRecord.Procedure procedure = person.record.procedure(time, primaryProcedureCode);
      procedure.name = this.name;
      procedure.codes.add(procedureCode);
      procedure.stop = procedure.start + TimeUnit.MINUTES.toMillis(30);
      return true;
    }

    private void duplicateSeries(Person person) {
      if (minNumberSeries > 0 && maxNumberSeries >= minNumberSeries
          && series.size() > 0) {

        // Randomly pick the number of series in this study
        int numberOfSeries = (int) person.rand(minNumberSeries, maxNumberSeries + 1);
        HealthRecord.ImagingStudy.Series referenceSeries = series.get(0);
        series = new ArrayList<HealthRecord.ImagingStudy.Series>();

        // Create the new series with random series UID
        for (int i = 0; i < numberOfSeries; i++) {
          HealthRecord.ImagingStudy.Series newSeries = referenceSeries.clone();
          newSeries.dicomUid = Utilities.randomDicomUid(i + 1, 0);
          series.add(newSeries);
        }
      } else {
        // Ensure series references are distinct (required if no. of instances is picked randomly)
        List<HealthRecord.ImagingStudy.Series> oldSeries = series;
        series = new ArrayList<HealthRecord.ImagingStudy.Series>();
        for (int i = 0; i < oldSeries.size(); i++) {
          HealthRecord.ImagingStudy.Series newSeries = oldSeries.get(i).clone();
          series.add(newSeries);
        }
      }
    }

    private void duplicateInstances(Person person) {
      for (int i = 0; i < series.size(); i++) {
        HealthRecord.ImagingStudy.Series s = series.get(i);
        if (s.minNumberInstances > 0 && s.maxNumberInstances >= s.minNumberInstances
            && s.instances.size() > 0) {

          // Randomly pick the number of instances in this series
          int numberOfInstances = (int) person.rand(s.minNumberInstances, s.maxNumberInstances + 1);
          HealthRecord.ImagingStudy.Instance referenceInstance = s.instances.get(0);
          s.instances = new ArrayList<HealthRecord.ImagingStudy.Instance>();

          // Create the new instances with random instance UIDs
          for (int j = 0; j < numberOfInstances; j++) {
            HealthRecord.ImagingStudy.Instance newInstance = referenceInstance.clone();
            newInstance.dicomUid = Utilities.randomDicomUid(i + 1, j + 1);
            s.instances.add(newInstance);
          }
        }
      }
    }
  }

  /**
   * The Symptom state type adds or updates a patient's symptom. Synthea tracks symptoms in order to
   * drive a patient's encounters, on a scale of 1-100. A symptom may be tracked for multiple
   * conditions, in these cases only the highest value is considered. See also the Symptom logical
   * condition type.
   */
  public static class Symptom extends State {
    private String symptom;
    private String cause;
    private Double probability;
    private Range<Integer> range;
    private Exact<Integer> exact;
    public boolean addressed;

    @Override
    protected void initialize(Module module, String name, JsonObject definition) {
      super.initialize(module, name, definition);
      if (cause == null) {
        cause = module.name;
      }
      if (probability == null || probability > 1 || probability < 0) {
        probability = 1.0;
      }
      addressed = false;
    }

    @Override
    public Symptom clone() {
      Symptom clone = (Symptom) super.clone();
      clone.symptom = symptom;
      clone.cause = cause;
      clone.probability = probability;
      clone.range = range;
      clone.exact = exact;
      clone.addressed = addressed;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      if (person.rand() <= probability) {
        if (exact != null) {
          person.setSymptom(cause, symptom, exact.quantity, addressed);
        } else if (range != null) {
          person.setSymptom(cause, symptom, (int) person.rand(range.low, range.high), addressed);
        } else {
          person.setSymptom(cause, symptom, 0, addressed);
        }
      }
      return true;
    }
  }
  
  /**
   * The Device state indicates the point that a permanent or semi-permanent device
   * (for example, a prosthetic, or pacemaker) is associated to a person.
   * The actual procedure in which the device is implanted is not automatically generated
   * and should be added separately. A Device may have a manufacturer or model listed
   * for cases where there is generally only one choice.
   */
  public static class Device extends State {
    public Code code;
    public String manufacturer;
    public String model;

    @Override
    public Device clone() {
      Device clone = (Device) super.clone();
      clone.code = code;
      clone.manufacturer = manufacturer;
      clone.model = model;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      HealthRecord.Device device = person.record.deviceImplant(time, code.code);
      device.codes.add(code);
      device.manufacturer = manufacturer;
      device.model = model;

      return true;
    }
  }
  
  /**
   * The SupplyList state includes a list of supplies that are needed for the current encounter.
   * Supplies may include things like PPE for the physician, or other resources and machines.
   *
   */
  public static class SupplyList extends State {
    // TODO: make a class for these, when needed beyond just exporting
    public List<JsonObject> supplies;

    @Override
    public SupplyList clone() {
      SupplyList clone = (SupplyList) super.clone();
      clone.supplies = supplies;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      HealthRecord.Encounter encounter = person.getCurrentEncounter(module);
      encounter.supplies.addAll(supplies);
      return true;
    }
  }
  
  /**
   * The Death state type indicates a point in the module at which the patient dies or the patient
   * is given a terminal diagnosis (e.g. "you have 3 months to live"). When the Death state is
   * processed, the patient's death is immediately recorded (the alive? method will return false)
   * unless range or exact attributes are specified, in which case the patient will die sometime in
   * the future. In either case the module will continue to progress to the next state(s) for the
   * current time-step. Typically, the Death state should transition to a Terminal state.
   *
   * <p>The Cause of Death listed on a Death Certificate can be specified in three ways:
   * By `codes[]`, specifying the system, code, and display name of the condition causing death.
   * By `condition_onset`, specifying the name of the ConditionOnset state in which the condition
   * causing death was onset. By `referenced_by_attribute`, specifying the name of the attribute to
   * which a previous ConditionOnset state assigned a condition that caused death.
   *
   * <p>Implementation Warning If a Death state is processed after a Delay, it may cause
   * inconsistencies in the record. This is because the Delay implementation must rewind time to
   * correctly honor the requested delay duration. If it rewinds time, and then the patient dies at
   * the rewinded time, then any modules that were processed before the generic module may have
   * created events and records with a timestamp after the patient's death.
   */
  public static class Death extends State {
    private List<Code> codes;
    private String conditionOnset;
    private String referencedByAttribute;
    private RangeWithUnit<Integer> range;
    private ExactWithUnit<Integer> exact;

    @Override
    public Death clone() {
      Death clone = (Death) super.clone();
      clone.codes = codes;
      clone.conditionOnset = conditionOnset;
      clone.referencedByAttribute = referencedByAttribute;
      clone.range = range;
      clone.exact = exact;
      return clone;
    }

    @Override
    public boolean process(Person person, long time) {
      Code reason = null;
      if (codes != null) {
        reason = codes.get(0);
      } else if (conditionOnset != null) {
        if (person.hadPriorState(conditionOnset)) {
          // loop through the present conditions, the condition "name" will match
          // the name of the ConditionOnset state (aka "reason")
          for (Entry entry : person.record.present.values()) {
            if (entry.name != null && entry.name.equals(conditionOnset)) {
              reason = entry.codes.get(0);
            }
          }
        }
      } else if (referencedByAttribute != null) {
        Entry entry = (Entry) person.attributes.get(referencedByAttribute);
        if (entry == null) {
          // condition referenced but not yet diagnosed
          throw new RuntimeException("Attribute '" + referencedByAttribute
              + "' was referenced by state '" + name + "' but not set");
        }
        reason = entry.codes.get(0);
      }
      if (exact != null) {
        long timeOfDeath = time + Utilities.convertTime(exact.unit, exact.quantity);
        person.recordDeath(timeOfDeath, reason);
        return true;
      } else if (range != null) {
        double duration = person.rand(range.low, range.high);
        long timeOfDeath = time + Utilities.convertTime(range.unit, (long) duration);
        person.recordDeath(timeOfDeath, reason);
        return true;
      } else {
        person.recordDeath(time, reason);
        return true;
      }
    }
  }
}
