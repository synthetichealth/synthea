package org.mitre.synthea.helpers.physiology;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math.ode.DerivativeException;
import org.mitre.synthea.engine.PhysiologySimulator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.simulator.math.odes.MultiTable;

/** Class for handling execution of a PhysiologySimulator. **/
public class SimRunner {
  private PhysiologyGeneratorConfig config;
  private Person person;
  private PhysiologySimulator simulator;
  private Map<String,String> paramTypes = new HashMap<String, String>();
  private Map<String,Double> prevInputs = new HashMap<String, Double>();
  private Map<VitalSign,Double> vitalSignResults = new HashMap<VitalSign,Double>();
  private Map<String,Double> modelInputs = new HashMap<String,Double>();
  boolean firstExecution;

  /**
   * Handles execution of a PhysiologySimulator.
   * @param config simulation configuration
   */
  public SimRunner(PhysiologyGeneratorConfig config, Person person) {
    this.config = config;
    this.person = person;
    simulator = new PhysiologySimulator(
        config.getModel(),
        config.getSolver(),
        config.getStepSize(),
        config.getSimDuration()
    );

    // All Patient parameters are set to the default Decimal type
    // TODO: May need to find a way to handle alternative types in the future
    for (IoMapper mapper : config.getInputs()) {
      mapper.initialize();
    }

    for (String param : simulator.getParameters()) {
      // Assume all physiology model parameters are numeric
      // TODO: May need to handle alternative types in the future
      paramTypes.put(param, "List<Decimal>");
    }

    for (IoMapper mapper : config.getOutputs()) {
      mapper.initialize(paramTypes);
    }
  }

  /**
   * Retrieves the simulation configuration.
   * @return simulation configuration
   */
  public PhysiologyGeneratorConfig getConfig() {
    return config;
  }

  public double getVitalSignValue(VitalSign parameter) {
    return vitalSignResults.get(parameter);
  }

  public boolean hasExecuted() {
    return firstExecution;
  }

  /**
   * Sets the inputs to compare to the model default inputs.
   * Necessary to prevent initial execution if the initial input
   * parameters are within threshold ranges.
   */
  public void compareDefaultInputs() {
    for (IoMapper mapper : config.getInputs()) {
      prevInputs.put(mapper.getTo(), simulator.getParamDefault(mapper.getTo()));
    }
  }

  /**
   * Sets up the model inputs for execution and determines if the simulation
   * needs to run due to sufficient change in inputs.
   * @param time synthea time
   * @return true if inputs have sufficiently changed to warrant sim execution.
   */
  public boolean setInputs(long time) {
    boolean sufficientChange = prevInputs.isEmpty();
    // Get our map of inputs
    for (IoMapper mapper : config.getInputs()) {
      double inputResult = mapper.toModelInputs(person, time, modelInputs);

      // If we have previous results, check if there has been a sufficient
      // change in the input parameter
      if (!prevInputs.isEmpty() && Math.abs(inputResult
          - prevInputs.get(mapper.getTo()))
          > mapper.getVariance()) {
        sufficientChange = true;
      }
    }
    return sufficientChange;
  }

  /**
   * Executes the simulation if any input values are beyond the variance threshold.
   * @param time simulation time
   */
  public void execute(long time) {
    // Copy our input parameters for future threshold checks
    prevInputs = new HashMap<String,Double>(modelInputs);
    MultiTable results = runSim(time, modelInputs);

    firstExecution = true;

    // Set all of the results
    for (IoMapper mapper : config.getOutputs()) {
      switch (mapper.getType()) {
        default:
        case ATTRIBUTE:
          person.attributes.put(mapper.getTo(),
              mapper.getOutputResult(results, config.getLeadTime()));
          break;
        case VITAL_SIGN:
          VitalSign vs = mapper.getVitalSignTarget();
          Object result = mapper.getOutputResult(results, config.getLeadTime());
          if (result instanceof List) {
            throw new IllegalArgumentException(
                "Mapping lists to VitalSigns is currently unsupported. "
                + "Cannot map list to VitalSign \"" + mapper.getTo() + "\".");
          }
          vitalSignResults.put(vs, (double) result);
          break;
      }
    }
  }

  /**
   * Runs the simulation and returns the results.
   * @param time simulation time
   * @return simulation results
   */
  private MultiTable runSim(long time, Map<String,Double> modelInputs) {
    try {
      MultiTable results = simulator.run(modelInputs);
      return results;
    } catch (DerivativeException ex) {
      Logger.getLogger(this.getClass().getName()).log(
          Level.SEVERE, "Unable to solve simulation \""
          + config.getModel() + "\" at time step " + time + " for person "
          + person.attributes.get(Person.ID), ex);
    }
    return null;
  }
}
