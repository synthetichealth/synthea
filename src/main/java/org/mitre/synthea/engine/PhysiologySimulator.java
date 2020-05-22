package org.mitre.synthea.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math.ode.DerivativeException;
import org.mitre.synthea.helpers.ChartRenderer;
import org.mitre.synthea.helpers.ChartRenderer.MultiTableChartConfig;
import org.mitre.synthea.helpers.ChartRenderer.MultiTableSeriesConfig;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.validator.ModelOverdeterminedException;
import org.sbml.jsbml.xml.stax.SBMLReader;
import org.simulator.math.odes.AbstractDESSolver;
import org.simulator.math.odes.AdamsBashforthSolver;
import org.simulator.math.odes.AdamsMoultonSolver;
import org.simulator.math.odes.DormandPrince54Solver;
import org.simulator.math.odes.DormandPrince853Solver;
import org.simulator.math.odes.EulerMethod;
import org.simulator.math.odes.GraggBulirschStoerSolver;
import org.simulator.math.odes.HighamHall54Solver;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.RosenbrockSolver;
import org.simulator.math.odes.RungeKutta_EventSolver;
import org.simulator.sbml.SBMLinterpreter;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Executes simulations of physiology models represented in SBML files.
 */
public class PhysiologySimulator {

  private static final URL MODELS_RESOURCE = ClassLoader.getSystemClassLoader()
      .getResource("physiology/models");
  private static final Map<String, Class<?>> SOLVER_CLASSES;
  private static Map<String, Model> MODEL_CACHE;
  private static Path SBML_PATH;
  private static Path OUTPUT_PATH = Paths.get("output", "physiology");
  
  private final Model model;
  private final SBMLinterpreter interpreter;
  private final AbstractDESSolver solver;
  private final String[] modelFields;
  private final double[] modelDefaults;
  private final double simDuration;

  /** POJO configuration for the simulation. **/
  public static class SimConfig {
    private String name;
    private String model;
    private String solver;
    private double stepSize;
    private double duration;
    private List<MultiTableChartConfig> charts;
    private Map<String, Double> inputs;

    public final Map<String, Double> getInputs() {
      return inputs;
    }

    public final void setInputs(Map<String, Double> inputs) {
      this.inputs = inputs;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
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

    public double getDuration() {
      return duration;
    }

    public void setDuration(double duration) {
      this.duration = duration;
    }

    public List<MultiTableChartConfig> getCharts() {
      return charts;
    }

    public void setCharts(List<MultiTableChartConfig> charts) {
      this.charts = charts;
    }
    
  }

  static {
    // Initialize our static map of solvers
    Map<String, Class<?>> initSolvers = new HashMap<String, Class<?>>();

    // Add all currently available solvers from the SBSCL library
    initSolvers.put("adams_bashforth", AdamsBashforthSolver.class);
    initSolvers.put("adams_moulton", AdamsMoultonSolver.class);
    initSolvers.put("dormand_prince_54", DormandPrince54Solver.class);
    initSolvers.put("dormand_prince_853", DormandPrince853Solver.class);
    initSolvers.put("euler", EulerMethod.class);
    initSolvers.put("gragg_bulirsch_stoer", GraggBulirschStoerSolver.class);
    initSolvers.put("higham_hall_54", HighamHall54Solver.class);
    initSolvers.put("rosenbrock", RosenbrockSolver.class);
    initSolvers.put("runge_kutta", RungeKutta_EventSolver.class);

    // Make unmodifiable so it doesn't change after initialization
    SOLVER_CLASSES = Collections.unmodifiableMap(initSolvers);
    
    try {
      SBML_PATH = Paths.get(MODELS_RESOURCE.toURI());
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
    
    // Initialize our model cache
    MODEL_CACHE = new HashMap<String, Model>();
  }
  
  /**
   * Sets the path to search for SBML model files.
   * @param newPath new path to use
   */
  public static void setModelsPath(Path newPath) {
    SBML_PATH = newPath;
  }
  
  /**
   * Sets the path to place main simulation results in.
   * @param newPath new path to use
   */
  public static void setOutputPath(Path newPath) {
    OUTPUT_PATH = newPath;
  }

  /**
   * PhysiologySimulator constructor.
   * @param modelPath Path to the SBML file to load relative to resources/physiology
   * @param solverName Name of the solver to use
   * @param stepSize Time step for the simulation
   * @param simDuration Amount of time to simulate
   */
  public PhysiologySimulator(String modelPath, String solverName, double stepSize,
      double simDuration) {
    
    // Get the model from cache if it has already been loaded
    if (MODEL_CACHE.containsKey(modelPath)) {
      model = MODEL_CACHE.get(modelPath);
    } else {
      // Load and instantiate the model from the SBML file
      Path modelFilepath = Paths.get(SBML_PATH.toString(), modelPath);
      SBMLReader reader = new SBMLReader();
      File inputFile = new File(modelFilepath.toString());
      SBMLDocument doc;
      try {
        doc = reader.readSBML(inputFile);
      } catch (IOException | XMLStreamException ex) {
        throw new RuntimeException(ex);
      }
      model = doc.getModel();
      
      // Add the loaded model to the cache so we don't need to load it again
      MODEL_CACHE.put(modelPath, model);
    }
    interpreter = getInterpreter(model);
    solver = getSolver(solverName);
    solver.setStepSize(stepSize);
    modelFields = interpreter.getIdentifiers();
    modelDefaults = interpreter.getInitialValues();
    this.simDuration = simDuration;
  }
  
  /**
   * Returns a list of all model parameters.
   * @return list of model parameters
   */
  public List<String> getParameters() {
    return Arrays.asList(modelFields);
  }
  
  /**
   * Solves the model at each time step for the specified duration using the provided inputs
   * as initial parameters. Provides the results as a map of value lists where each key is
   * a model parameter. In addition to the model parameters is a "Time" field which provides
   * a list of all simulated time points.
   * 
   * <p>Note that this method will throw a DerivativeException if the model encounters an error
   * while attempting to solve the system.
   * @param inputs Map of model parameter inputs. For any parameters which are not provided
   *               the default value from the model will be used. If null, all default
   *               parameter values will be used.
   * @return map of parameter names to value lists
   * @throws DerivativeException Exception if the solver encounters errors while computing the
   *        solution to differential equations
   */
  public MultiTable run(Map<String, Double> inputs) throws DerivativeException {
    try {
      // Reinitialize the interpreter to prevent old values from affecting the new simulation
      interpreter.init(true);
    } catch (ModelOverdeterminedException | SBMLException ex) {
      // This shouldn't ever happen here since the interpreter has already been instantiated
      // at least once
      throw new RuntimeException(ex);
    }
    
    // Create a copy of the default parameters to use
    double[] params = Arrays.copyOf(modelDefaults, modelDefaults.length);

    // Overwrite model defaults with the provided input parameters, if present
    if (inputs != null) {
      for (int i = 0; i < modelFields.length; i++) {
        String field = modelFields[i];
        if (inputs.containsKey(field)) {
          params[i] = inputs.get(field);
        }
      }
    }
    
    // Solve the ODE for the specified duration and return the results
    MultiTable results = solver.solve(interpreter, params, 0, simDuration);
    
    return results;
  }

  /**
   * Checks whether a string is a valid solver name.
   * @param solverName solver name string to check
   * @return true if valid false otherwise.
   */
  public static boolean checkValidSolver(String solverName) {
    return SOLVER_CLASSES.containsKey(solverName);
  }

  /**
   * Gets the set of valid solver names.
   * @return set of valid solver name strings
   */
  public static Set<String> getSolvers() {
    return SOLVER_CLASSES.keySet();
  }

  /**
   * Retrieves the solver for the given solver name. If the provided string is
   * invalid, a RuntimeException will be thrown
   * @param solverName user-facing name of the solver to instantiate
   * @return solver instance
   */
  private static AbstractDESSolver getSolver(String solverName) {

    // If the provided solver name doesn't exist in our map, it's an invalid
    // value that the programmer needs to correct.
    if (!checkValidSolver(solverName)) {
      throw new RuntimeException("Invalid Solver: \"" + solverName + "\"");
    }

    // Attempt to instantiate the solver.
    try {
      return (AbstractDESSolver) SOLVER_CLASSES.get(solverName).getDeclaredConstructor()
          .newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }

  }
  
  /**
   * Retrieves the interpreter for a given SBML Model.
   * @param bioModel SBML model to interpret
   * @return interpreter instance
   */
  private static SBMLinterpreter getInterpreter(Model bioModel) {
    try {
      SBMLinterpreter interpreter = new SBMLinterpreter(bioModel);
      return interpreter;
    } catch (ModelOverdeterminedException | SBMLException ex) {
      // If there are problems with the model, we can't proceed
      throw new RuntimeException(ex);
    }
  }
  
  /**
   * Writes the contents of a MultiTable to a CSV file.
   * @param table MultiTable to write
   * @param outputPath path to the output CSV file
   */
  private static void multiTableToCsvFile(MultiTable table, Path outputPath) {
    PrintWriter writer;
    // Open our output file for writing
    try {
      writer = new PrintWriter(outputPath.toString(), "UTF-8");
    } catch (FileNotFoundException | UnsupportedEncodingException ex) {
      throw new RuntimeException("Unable to open output file:" + outputPath);
    }

    // Get the number of rows and columns
    int numRows = table.getRowCount();
    int numCols = table.getColumnCount();

    // Write the header line
    for (int colIdx = 0; colIdx < numCols; colIdx++) {
      writer.print(table.getColumnIdentifier(colIdx));
      if (colIdx < numCols - 1) {
        writer.print(",");
      }
    }
    writer.println();

    // Write each of the row values in sequence
    for (int rowIdx = 0; rowIdx < numRows; rowIdx++) {
      for (int colIdx = 0; colIdx < numCols; colIdx++) {
        writer.print(table.getValueAt(rowIdx, colIdx));
        if (colIdx < numCols - 1) {
          writer.print(",");
        }
      }
      writer.println();
    }
    writer.close();
  }
  
  /**
   * Retrieves the default value for a model parameter.
   * @param param parameter to search for
   * @return initial value
   */
  public double getParamDefault(String param) {
    return modelDefaults[ArrayUtils.indexOf(interpreter.getIdentifiers(), param)];
  }

  /**
   * Executes a physiology simulation according to a given configuration file.
   * @param args command line arguments
   */
  public static void main(String [] args) {

    if (args.length < 1 || args[0].isEmpty()) {
      throw new IllegalArgumentException(
          "YAML simulation configuration file path must be provided.");
    }
    
    // Open the config file
    Path configFilePath = Paths.get(args[0]);
    
    File configFile = new File(configFilePath.toString());
    FileInputStream inputStream;
    
    // Try to open the configuration file as an input stream
    try {
      inputStream = new FileInputStream(configFile);
    } catch (FileNotFoundException ex) {
      throw new IllegalArgumentException("Configuration file not found: \""
          + configFilePath.toAbsolutePath() + "\".");
    }
    
    // Add type descriptions so Yaml knows how to instantiate our Lists
    Constructor constructor = new Constructor(SimConfig.class);
    TypeDescription simConfigDescription = new TypeDescription(SimConfig.class);
    simConfigDescription.addPropertyParameters("charts", MultiTableChartConfig.class);
    constructor.addTypeDescription(simConfigDescription);
    TypeDescription chartConfigDescription = new TypeDescription(MultiTableSeriesConfig.class);
    chartConfigDescription.addPropertyParameters("series", MultiTableSeriesConfig.class);
    constructor.addTypeDescription(chartConfigDescription);
    
    // Parse the SimConfig from the yaml file
    Yaml yaml = new Yaml(constructor);
    SimConfig config = (SimConfig) yaml.load(inputStream);
    
    // Instantiate our simulator
    PhysiologySimulator simulator = new PhysiologySimulator(
        config.getModel(),
        config.getSolver(),
        config.getStepSize(),
        config.getDuration()
    );
    
    // Create the output directory if it doesn't already exist
    Path outputDir = Paths.get(OUTPUT_PATH.toString(), config.getName());
    if (Files.notExists(outputDir)) {
      try {
        Files.createDirectories(outputDir);
      } catch (IOException ex) {
        System.out.println("Unable to write output directory. Check user permissions.");
        throw new RuntimeException(ex);
      }
    }
    
    try {
      
      // Run with all default parameters
      long startTime = System.nanoTime();
      MultiTable results = simulator.run(config.getInputs());
      long duration = System.nanoTime() - startTime;
      
      System.out.println("Simulation took " + (duration / 1000000.0) + " ms");
      
      // Write CSV data file
      multiTableToCsvFile(results, Paths.get(outputDir.toString(), config.getName() + ".csv"));
      
      // Draw all of the configured charts
      if (config.getCharts() != null) {
        int chartId = 1;
        for (MultiTableChartConfig chartConfig : config.getCharts()) {
          if (chartConfig.getFilename() == null || chartConfig.getFilename().isEmpty()) {
            chartConfig.setFilename("chart" + chartId + ".png");
          }
          chartConfig.setFilename(Paths.get(outputDir.toString(),
              chartConfig.getFilename()).toString());
          ChartRenderer.drawChartAsFile(results, chartConfig);
        }
      }

    } catch (DerivativeException ex) {
      throw new RuntimeException(ex);
    }
  }
}
