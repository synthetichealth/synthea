package org.mitre.synthea.helpers.physiology;

import java.util.List;
import java.util.Map;

/**
 * ValueGenerator configuration for a physiology model file.
 * @author RSIVEK
 *
 */
public class PhysiologyGeneratorConfig {
  private String model;
  private String solver;
  private double stepSize;
  private double simDuration;
  private double leadTime;
  private boolean usePreGenerators;
  private List<IoMapper> inputs;
  private List<IoMapper> outputs;
  private Map<String, Object> personAttributeDefaults;

  /**
   * Validates that all inputs are appropriate and within bounds.
   */
  public void validate() {
    if (leadTime >= simDuration) {
      throw new IllegalArgumentException(
          "Simulation lead time must be less than simulation duration!");
    }
  }

  /**
   * Gets the model name.
   * @return the model name
   */
  public String getModel() {
    return model;
  }

  /**
   * Sets the model name.
   * @param model the model name to set
   */
  public void setModel(String model) {
    this.model = model;
  }

  /**
   * Gets the solver name.
   * @return the solver name
   */
  public String getSolver() {
    return solver;
  }

  /**
   * Sets the solver name.
   * @param solver the solver name to set
   */
  public void setSolver(String solver) {
    this.solver = solver;
  }

  /**
   * Gets the step size for the simulation.
   * @return the step size
   */
  public double getStepSize() {
    return stepSize;
  }

  /**
   * Sets the step size for the simulation.
   * @param stepSize the step size to set
   */
  public void setStepSize(double stepSize) {
    this.stepSize = stepSize;
  }

  /**
   * Gets the simulation duration.
   * @return the simulation duration
   */
  public double getSimDuration() {
    return simDuration;
  }

  /**
   * Sets the simulation duration.
   * @param simDuration the simulation duration to set
   */
  public void setSimDuration(double simDuration) {
    this.simDuration = simDuration;
  }

  /**
   * Gets the lead time for the simulation.
   * @return the lead time
   */
  public double getLeadTime() {
    return leadTime;
  }

  /**
   * Sets the lead time for the simulation.
   * @param leadTime the lead time to set
   */
  public void setLeadTime(double leadTime) {
    this.leadTime = leadTime;
  }

  /**
   * Checks if pre-generators are used.
   * @return true if pre-generators are used, false otherwise
   */
  public boolean isUsePreGenerators() {
    return usePreGenerators;
  }

  /**
   * Sets whether to use pre-generators.
   * @param usePreGenerators true to use pre-generators, false otherwise
   */
  public void setUsePreGenerators(boolean usePreGenerators) {
    this.usePreGenerators = usePreGenerators;
  }

  /**
   * Gets the input mappings for the simulation.
   * @return the input mappings
   */
  public List<IoMapper> getInputs() {
    return inputs;
  }

  /**
   * Sets the input mappings for the simulation.
   * @param inputs the input mappings to set
   */
  public void setInputs(List<IoMapper> inputs) {
    this.inputs = inputs;
  }

  /**
   * Gets the output mappings for the simulation.
   * @return the output mappings
   */
  public List<IoMapper> getOutputs() {
    return outputs;
  }

  /**
   * Sets the output mappings for the simulation.
   * @param outputs the output mappings to set
   */
  public void setOutputs(List<IoMapper> outputs) {
    this.outputs = outputs;
  }

  /**
   * Gets the default person attributes for the simulation.
   * @return the default person attributes
   */
  public Map<String, Object> getPersonAttributeDefaults() {
    return personAttributeDefaults;
  }

  /**
   * Sets the default person attributes for the simulation.
   * @param personAttributeDefaults the default person attributes to set
   */
  public void setPersonAttributeDefaults(Map<String, Object> personAttributeDefaults) {
    this.personAttributeDefaults = personAttributeDefaults;
  }

}
