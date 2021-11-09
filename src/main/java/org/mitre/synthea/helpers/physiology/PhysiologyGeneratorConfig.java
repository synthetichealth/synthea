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

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getSolver() {
    return solver;
  }

  public void setSolver(String solver) {
    this.solver = solver;
  }

  public double getStepSize() {
    return stepSize;
  }

  public void setStepSize(double stepSize) {
    this.stepSize = stepSize;
  }

  public double getSimDuration() {
    return simDuration;
  }

  public void setSimDuration(double simDuration) {
    this.simDuration = simDuration;
  }

  public double getLeadTime() {
    return leadTime;
  }

  public void setLeadTime(double leadTime) {
    this.leadTime = leadTime;
  }

  public boolean isUsePreGenerators() {
    return usePreGenerators;
  }

  public void setUsePreGenerators(boolean usePreGenerators) {
    this.usePreGenerators = usePreGenerators;
  }

  public List<IoMapper> getInputs() {
    return inputs;
  }

  public void setInputs(List<IoMapper> inputs) {
    this.inputs = inputs;
  }

  public List<IoMapper> getOutputs() {
    return outputs;
  }

  public void setOutputs(List<IoMapper> outputs) {
    this.outputs = outputs;
  }

  public Map<String, Object> getPersonAttributeDefaults() {
    return personAttributeDefaults;
  }

  public void setPersonAttributeDefaults(Map<String, Object> personAttributeDefaults) {
    this.personAttributeDefaults = personAttributeDefaults;
  }

}
