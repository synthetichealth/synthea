package org.mitre.synthea.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.math.ode.DerivativeException;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.validator.ModelOverdeterminedException;
import org.sbml.jsbml.xml.stax.SBMLReader;
import org.simulator.math.odes.AdamsBashforthSolver;
import org.simulator.math.odes.AdamsMoultonSolver;
import org.simulator.math.odes.AbstractDESSolver;
import org.simulator.math.odes.DormandPrince54Solver;
import org.simulator.math.odes.DormandPrince853Solver;
import org.simulator.math.odes.EulerMethod;
import org.simulator.math.odes.GraggBulirschStoerSolver;
import org.simulator.math.odes.HighamHall54Solver;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block;
import org.simulator.math.odes.MultiTable.Block.Column;
import org.simulator.math.odes.RosenbrockSolver;
import org.simulator.math.odes.RungeKutta_EventSolver;
import org.simulator.sbml.SBMLinterpreter;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * PhysiologySimulator represents the entry point of a physiology simulation submodule.
 */
public class PhysiologySimulator {

  private static final Map<String, Class> SOLVER_CLASSES;
  private static final Map<String, AbstractDESSolver> SOLVERS = new HashMap();
  private static Path sbmlPath;
  private final SBMLinterpreter interpreter;
  private final String[] modelFields;
  private final double[] modelDefaults;
  private final AbstractDESSolver solver;
  private final double simDuration;
  private final double leadTime;
  
  public static enum ChartType {
    SCATTER,
    LINE
  }
  
  public static class SimConfig {
    private String name;
    private String model;
    private String solver;
    private double stepSize;
    private double duration;
    private List<ChartConfig> charts;
    private Map<String,Double> inputs;

    public Map<String, Double> getInputs() {
      return inputs;
    }

    public void setInputs(Map<String, Double> inputs) {
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

    public List<ChartConfig> getCharts() {
      return charts;
    }

    public void setCharts(List<ChartConfig> charts) {
      this.charts = charts;
    }
    
  }
  
  public static class ChartConfig {
    private String filename;
    private String type;
    private String title;
    private String xAxis;
    private String xAxisLabel;
    private String yAxisLabel;
    private List<SeriesConfig> series;

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      this.filename = filename;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getxAxis() {
      return xAxis;
    }

    public void setxAxis(String xAxis) {
      this.xAxis = xAxis;
    }

    public String getxAxisLabel() {
      return xAxisLabel;
    }

    public void setxAxisLabel(String xAxisLabel) {
      this.xAxisLabel = xAxisLabel;
    }

    public String getyAxisLabel() {
      return yAxisLabel;
    }

    public void setyAxisLabel(String yAxisLabel) {
      this.yAxisLabel = yAxisLabel;
    }

    public List<SeriesConfig> getSeries() {
      return series;
    }

    public void setSeries(List<SeriesConfig> series) {
      this.series = series;
    }
  }

  public static class SeriesConfig {
    private String param;
    private String label;

    public String getParam() {
      return param;
    }

    public void setParam(String param) {
      this.param = param;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

  }

  static {
    Map<String, Class> initSolvers = new HashMap();

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
    
    // get the path to our physiology models directory containing SBML files
    URL physiologyFolder = ClassLoader.getSystemClassLoader().getResource("physiology");
    try {
      sbmlPath = Paths.get(physiologyFolder.toURI());
    } catch (URISyntaxException ex) {
      Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE, null, ex);
    }
    
  }
  
  /**
   * PhysiologySimulator constructor.
   * @param modelPath Path to the SBML file to load relative to resources/physiology
   * @param solverName Name of the solver to use
   * @param stepSize Time step for the simulation
   * @param simDuration Amount of time to simulate
   */
  public PhysiologySimulator(String modelPath, String solverName, double stepSize, double simDuration) {
    this(modelPath, solverName, stepSize, simDuration, 0);
  }

  /**
   * PhysiologySimulator constructor.
   * @param modelPath Path to the SBML file to load relative to resources/physiology
   * @param solverName Name of the solver to use
   * @param stepSize Time step for the simulation
   * @param simDuration Amount of time to simulate
   * @param leadTime Amount of time to run the simulation before capturing results
   */
  public PhysiologySimulator(String modelPath, String solverName, double stepSize, double simDuration, double leadTime) {
    Path modelFilepath = Paths.get(sbmlPath.toString(), modelPath);
    interpreter = getInterpreter(modelFilepath.toString());
    modelFields = interpreter.getIdentifiers();
    modelDefaults = interpreter.getInitialValues();
    solver = getSolver(solverName);
    solver.setStepSize(stepSize);
    this.simDuration = simDuration;
    this.leadTime = leadTime;
  }
  
  /**
   * Returns a list of all model parameters
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
   * <p>
   * Note that this method will throw a DerivativeException if the model encounters an error
   * while attempting to solve the system.
   * @param inputs Map of model parameter inputs. For any parameters which are not provided
   *               the default value from the model will be used. If null, all default
   *               parameter values will be used.
   * @return map of parameter names to value lists
   * @throws DerivativeException 
   */
  public MultiTable run(Map<String, Double> inputs) throws DerivativeException {
    // Reset the solver to its initial state
    solver.reset();
    try {
      // Need to reinitialize the interpreter to prevent old values from affecting the new simulation
      interpreter.init(true);
    } catch (ModelOverdeterminedException | SBMLException ex) {
      // This shouldn't ever happen here since the interpreter has already been instantiated at least once
      Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE, "Error reinitializing SBML interpreter", ex);
    }
    
    // Create a copy of the default parameters to use
    double[] params = Arrays.copyOf(modelDefaults, modelDefaults.length);

    // Overwrite model defaults with the provided input parameters, if present
    if(inputs != null) {
      for(int i=0; i < modelFields.length; i++) {
        String field = modelFields[i];
        if(inputs.containsKey(field)) {
          params[i] = inputs.get(field);
        }
      }
    }
    
    // Solve the ODE for the specified duration and return the results
    return solver.solve(interpreter, params, -leadTime, simDuration);
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
   * Gets the set of valid solver names
   * @return set of valid solver name strings
   */
  public static Set<String> getSolvers() {
    return SOLVER_CLASSES.keySet();
  }

  private static AbstractDESSolver getSolver(String solverName) throws RuntimeException {

    // If the provided solver name doesn't exist in our map, it's an invalid
    // value that the programmer needs to correct.
    if(!checkValidSolver(solverName)) {
      throw new RuntimeException("Invalid Solver: \"" + solverName + "\"");
    }

    // If this solver has already been instantiated, retrieve it
    if(SOLVERS.containsKey(solverName)) {
      return SOLVERS.get(solverName);
    }

    // It hasn't been instantiated yet so we do so now
    try {
      SOLVERS.put(solverName, (AbstractDESSolver) SOLVER_CLASSES.get(solverName).newInstance());
    } catch (InstantiationException | IllegalAccessException ex) {
      Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE, null, ex);
      throw new RuntimeException("Unable to instantiate " + solverName + " solver");
    }

    // Retrieve the solver we just instantiated
    return SOLVERS.get(solverName);
  }
  
  private static SBMLinterpreter getInterpreter(String filepath) {
    SBMLReader reader = new SBMLReader();
    File inputFile = new File(filepath);
    try {
      SBMLDocument doc = reader.readSBML(inputFile);
//      System.out.println("Loaded SBML Document successfully!");
      Model model = doc.getModel();
      try {
        SBMLinterpreter interpreter = new SBMLinterpreter(model);
//        System.out.println("Interpreted SBML Model successfully!");
        return interpreter;

      } catch (ModelOverdeterminedException | SBMLException ex) {
        Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE, "Error interpreting SBML Model from \""+inputFile+"\".", ex);
      }

    } catch (IOException | XMLStreamException ex) {
      Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE, "Failed to load SBML document \""+inputFile+"\".", ex);
    }
    return null;
  }
  
  private static void testModel(PhysiologySimulator physio, Path outputFile) {
    // Use all default parameters
    testModel(physio, outputFile, new HashMap());
  }

  private static void testModel(PhysiologySimulator physio, Path outputPath, Map<String,Double> inputs) {

    try {
      MultiTable solution = physio.run(inputs);

      multiTableToCsvFile(solution, outputPath);
      System.out.println("Success!");

    } catch (DerivativeException ex) {
      throw new RuntimeException(ex);
    }
  }
  
  private static void multiTableToCsvFile(MultiTable table, Path outputPath) {
    PrintWriter writer;
    try {
      writer = new PrintWriter(outputPath.toString(), "UTF-8");
    } catch (FileNotFoundException | UnsupportedEncodingException ex) {
      throw new RuntimeException("Unable to open output file:" + outputPath);
    }

    int numRows = table.getRowCount();
    int numCols = table.getColumnCount();

    for(int colIdx = 0; colIdx < numCols; colIdx++) {
      writer.print(table.getColumnIdentifier(colIdx));
      if(colIdx < numCols -1) {
        writer.print(",");
      }
    }
    writer.println();

    for(int rowIdx = 0; rowIdx < numRows; rowIdx++) {
      for(int colIdx = 0; colIdx < numCols; colIdx++) {
        writer.print(table.getValueAt(rowIdx, colIdx));
        if(colIdx < numCols -1) {
          writer.print(",");
        }
      }
      writer.println();
    }
    writer.close();
  }

//  private static void getPerfStats(Path modelPath, String solverName) {
//    SBMLinterpreter interpreter = getInterpreter(modelPath.toString());
//    AbstractDESSolver solver = getSolver(solverName);
////    solver.setStepSize(stepSize);
//    try {
//      double[] defaultValues = interpreter.getInitialValues();
//
//      PrintWriter statWriter;
//
//      try {
//        statWriter = new PrintWriter("stats.csv", "UTF-8");
//      } catch (FileNotFoundException | UnsupportedEncodingException ex) {
//        Logger.getLogger(PhysiologySimulator.class.getName()).log(Level.SEVERE, null, ex);
//        return;
//      }
//
//      statWriter.print("index,step_size (s),exec_time (ms)");
//      statWriter.println();
//
//      Random r = new Random();
//
//      for(int i=0; i < 100; i++) {
//        System.out.println("Running iteration " + (i+1));
//        double[] initialVals = defaultValues.clone();
//        // Generate some random resistance values and initial parameters
//        initialVals[7] = randValue(0.98, 2);
//        initialVals[6] = randValue(0.13, 0.17);
//        initialVals[26] = randValue(10, 80);
//        initialVals[27] = randValue(2, 130);
//
//        double stepSize;
//        if(r.nextDouble() < 0.4) {
//          stepSize = randValue(0.001, 0.1);
//        }
//        else {
//          stepSize = randValue(0.001, 0.01);
//        }
//
//        solver.setStepSize(stepSize);
//
//        long startTime = System.nanoTime();
//        solver.solve(interpreter, initialVals, 0, 2);
//        long endTime = System.nanoTime();
//        statWriter.print(i + "," + stepSize + "," + ((endTime-startTime)/1000000.0));
//        statWriter.println();
//      }
//
//      statWriter.close();
//      System.out.println("Success!");
//
//    } catch (DerivativeException ex) {
//      throw new RuntimeException(ex);
//    }
//  }
  
  private static void drawChart(Path filePath, MultiTable table, ChartType chartType, String title, List<SeriesConfig> seriesConfigs, String yAxisLabel,
          String xIdentifier, String xAxisLabel) {
    Platform.runLater(() -> {
      NumberAxis xAxis = new NumberAxis();
      xAxis.setLabel(xAxisLabel);
      xAxis.setAnimated(false); // Need to disable animations for the axis to show up in the image
      NumberAxis yAxis = new NumberAxis();
      yAxis.setLabel(yAxisLabel);
      yAxis.setAnimated(false); // Need to disable animations for the axis to show up in the image

      XYChart<Number,Number> chart;
      
      // Initialize the chart based on the provided type argument
      switch(chartType) {
        case LINE: chart = new LineChart(xAxis,yAxis); break;
        default:
        case SCATTER: chart = new ScatterChart(xAxis,yAxis); break;
      }
      
      chart.setTitle(title);
      
      // If there's only one series, and there's a title, hide the legend
      if(title != null && !"".equals(title) && seriesConfigs.size() == 1) {
        chart.setLegendVisible(false);
      }
      
      // Get the list of x values. Time is treated specially since it doesn't have a param identifier
      List<Double> xValues = new ArrayList(table.getRowCount());;
      if("time".equalsIgnoreCase(xIdentifier)) {
        for(double timePoint : table.getTimePoints()) {
          xValues.add(timePoint);
        }
      }
      else {
        Column xCol = table.getColumn(xIdentifier);
        if(xCol == null) {
          throw new RuntimeException("Invalid X axis identifier");
        }
        xCol.iterator().forEachRemaining(xValues::add);
      }

      // Add each series to the chart
      for(SeriesConfig config : seriesConfigs) {
        XYChart.Series series = new XYChart.Series();
        series.setName(config.label);

        Column col = table.getColumn(config.param);

        for(int i=0; i < xValues.size(); i++) {
          series.getData().add(new XYChart.Data(xValues.get(i), col.getValue(i)));
        }

        chart.getData().add(series);
      }

      // Render the chart to a scene and capture it as an image
      Scene scene = new Scene(chart, 1280, 720);
      WritableImage image = scene.snapshot(null);

      try {
        // Write the image to a png file
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(filePath.toString()));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public static double randValue(double rangeMin, double rangeMax) {
    Random r = new Random();
    return rangeMin + (rangeMax - rangeMin) * r.nextDouble();
  }

  public static void main(String [] args) throws DerivativeException {

    if(args.length < 1 || args[0].isEmpty()) {
      System.out.println("YAML simulation configuration file path must be provided.");
      System.exit(1);
      return;
    }
    
    // Open the config file
    Path configFilePath = Paths.get(args[0]);
    
    File configFile = new File(configFilePath.toString());
    FileInputStream inputStream;
    
    // Try to open the configuration file as an input stream
    try {
      inputStream = new FileInputStream(configFile);
    } catch (FileNotFoundException ex) {
      System.out.println("Configuration file not found: \""+configFilePath.toAbsolutePath()+"\".");
      System.exit(2);
      return;
    }
    
    // Add type descriptions so Yaml knows how to instantiate our Lists
    Constructor constructor = new Constructor(SimConfig.class);
    TypeDescription simConfigDescription = new TypeDescription(SimConfig.class);
    simConfigDescription.addPropertyParameters("charts", ChartConfig.class);
    constructor.addTypeDescription(simConfigDescription);
    TypeDescription chartConfigDescription = new TypeDescription(ChartConfig.class);
    chartConfigDescription.addPropertyParameters("series", SeriesConfig.class);
    constructor.addTypeDescription(chartConfigDescription);
    
    // Parse the SimConfig from the yaml file
    Yaml yaml = new Yaml(constructor);
    SimConfig config = (SimConfig) yaml.load(inputStream);
    
    // Instantiate our simulator
    PhysiologySimulator simulator = new PhysiologySimulator(config.getModel(), config.getSolver(), config.getStepSize(), config.getDuration());
    
    // Create the output directory if it doesn't already exist
    Path outputDir = Paths.get("output", config.getName());
    if(Files.notExists(outputDir)) {
      try {
        Files.createDirectories(outputDir);
      } catch (IOException ex) {
        System.out.println("Unable to write output directory. Check user permissions.");
      }
    }
    
    try {
      
      // Run with all default parameters
      MultiTable results = simulator.run(config.getInputs());
      
      // Write CSV data file
      multiTableToCsvFile(results, Paths.get(outputDir.toString(), config.getName()+".csv"));
      
      // Initialize FX Toolkit
      new JFXPanel();
      
      // Draw all of the charts
      for(ChartConfig chartConfig : config.getCharts()) {
        PhysiologySimulator.drawChart(
                Paths.get(outputDir.toString(), chartConfig.getFilename()),
                results,
                ChartType.valueOf(chartConfig.getType().toUpperCase()),
                chartConfig.getTitle(),
                chartConfig.getSeries(),
                chartConfig.getyAxisLabel(),
                chartConfig.getxAxis(),
                chartConfig.getxAxisLabel());
      }

      // Stop the JavaFX thread
      Platform.exit();
    } catch (DerivativeException ex) {
      Platform.exit();
      throw new RuntimeException(ex);
    }
  }
}
