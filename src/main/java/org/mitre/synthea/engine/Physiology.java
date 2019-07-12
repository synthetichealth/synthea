package org.mitre.synthea.engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.math.ode.DerivativeException;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.validator.ModelOverdeterminedException;
import org.sbml.jsbml.xml.stax.SBMLReader;
import org.simulator.math.odes.AdamsBashforthSolver;
import org.simulator.math.odes.AdamsMoultonSolver;
import org.simulator.math.odes.DESSolver;
import org.simulator.math.odes.DormandPrince54Solver;
import org.simulator.math.odes.DormandPrince853Solver;
import org.simulator.math.odes.EulerMethod;
import org.simulator.math.odes.GraggBulirschStoerSolver;
import org.simulator.math.odes.HighamHall54Solver;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block;
import org.simulator.math.odes.RosenbrockSolver;
import org.simulator.math.odes.RungeKutta_EventSolver;
import org.simulator.sbml.SBMLinterpreter;

/**
 * Physiology represents the entry point of a physiology simulation submodule.
 */
public class Physiology {

  private static final Map<String, Class> SOLVER_CLASSES;
  private static final Map<String, DESSolver> SOLVERS = new HashMap();
  private static Path sbmlPath;
  private final SBMLinterpreter interpreter;
  private final String[] modelFields;
  private final double[] modelDefaults;
  private final DESSolver solver;
  private final double simDuration;


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
      Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
    }
    
  }

  /**
   * Physiology constructor.
   * @param modelPath Path to the SBML file to load relative to resources/physiology
   * @param solverName Name of the solver to use
   * @param stepSize Time step for the simulation
   * @param simDuration Amount of time to simulate
   */
  public Physiology(String modelPath, String solverName, double stepSize, double simDuration) {
    Path modelFilepath = Paths.get(sbmlPath.toString(), modelPath);
    interpreter = getInterpreter(modelFilepath.toString());
    modelFields = interpreter.getIdentifiers();
    modelDefaults = interpreter.getInitialValues();
    solver = getSolver(solverName);
    solver.setStepSize(stepSize);
    this.simDuration = simDuration;
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
   *               the default value from the model will be used.
   * @return map of parameter names to value lists
   * @throws DerivativeException 
   */
  public MultiTable run(Map<String, Double> inputs) throws DerivativeException {
    
    // Create a copy of the default parameters to use
    double[] params = Arrays.copyOf(modelDefaults, modelDefaults.length);

    // Overwrite model defaults with the provided input parameters
    for(int i=0; i < modelFields.length; i++) {
      String field = modelFields[i];
      if(inputs.containsKey(field)) {
        params[i] = inputs.get(field);
      }
    }
    
    // Solve the ODE for the specified duration and return the results
    return solver.solve(interpreter, params, 0, simDuration);

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

  private static DESSolver getSolver(String solverName) throws RuntimeException {

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
      SOLVERS.put(solverName, (DESSolver) SOLVER_CLASSES.get(solverName).newInstance());
    } catch (InstantiationException | IllegalAccessException ex) {
      Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
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
      System.out.println("Loaded SBML Document successfully!");
      Model model = doc.getModel();
      try {
        SBMLinterpreter interpreter = new SBMLinterpreter(model);
        System.out.println("Interpreted SBML Model successfully!");
        return interpreter;

      } catch (ModelOverdeterminedException | SBMLException ex) {
        Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
        System.out.println("Error interpreting SBML Model...");
      }

    } catch (IOException | XMLStreamException ex) {
      Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
      System.out.println("Failed to load SBML document...");
    }
    return null;
  }
  
  private static void testModel(Physiology physio, Path outputFile) {
    // Use all default parameters
    testModel(physio, outputFile, new HashMap());
  }

  private static void testModel(Physiology physio, Path outputPath, Map<String,Double> inputs) {

    try {
      MultiTable solution = physio.run(inputs);

      PrintWriter writer;
      try {
        writer = new PrintWriter(outputPath.toString(), "UTF-8");
      } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
        System.out.println("Unable to open output file:" + outputPath);
        return;
      }
      
      System.out.println("Block 1: " + Arrays.toString(solution.getBlock(0).getColumnNames()));
      System.out.println("Block 2: " + Arrays.toString(solution.getBlock(1).getColumnNames()));

      int numRows = solution.getRowCount();
      int numCols = solution.getColumnCount();

      for(int colIdx = 0; colIdx < numCols; colIdx++) {
        writer.print(solution.getColumnIdentifier(colIdx));
        if(colIdx < numCols -1) {
          writer.print(",");
        }
      }
      writer.println();

      for(int rowIdx = 0; rowIdx < numRows; rowIdx++) {
        for(int colIdx = 0; colIdx < numCols; colIdx++) {
          writer.print(solution.getValueAt(rowIdx, colIdx));
          if(colIdx < numCols -1) {
            writer.print(",");
          }
        }
        writer.println();
      }
      writer.close();
      System.out.println("Success!");

    } catch (DerivativeException ex) {
      Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
      System.out.println("Error solving Model...");
    }
  }

  private static void getPerfStats(Path modelPath, String solverName) {
    SBMLinterpreter interpreter = getInterpreter(modelPath.toString());
    System.out.println("Interpreted SBML Model successfully!");
    DESSolver solver = getSolver(solverName);
//    solver.setStepSize(stepSize);
    try {
      double[] defaultValues = interpreter.getInitialValues();

      PrintWriter statWriter;

      try {
        statWriter = new PrintWriter("stats.csv", "UTF-8");
      } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
        return;
      }

      statWriter.print("index,step_size (s),exec_time (ms)");
      statWriter.println();

      Random r = new Random();

      for(int i=0; i < 100; i++) {
        System.out.println("Running iteration " + (i+1));
        double[] initialVals = defaultValues.clone();
        // Generate some random resistance values and initial parameters
        initialVals[7] = randValue(0.98, 2);
        initialVals[6] = randValue(0.13, 0.17);
        initialVals[26] = randValue(10, 80);
        initialVals[27] = randValue(2, 130);

        double stepSize;
        if(r.nextDouble() < 0.4) {
          stepSize = randValue(0.001, 0.1);
        }
        else {
          stepSize = randValue(0.001, 0.01);
        }

        solver.setStepSize(stepSize);

        long startTime = System.nanoTime();
        solver.solve(interpreter, initialVals, 0, 2);
        long endTime = System.nanoTime();
        statWriter.print(i + "," + stepSize + "," + ((endTime-startTime)/1000000.0));
        statWriter.println();
      }

      statWriter.close();
      System.out.println("Success!");

    } catch (DerivativeException ex) {
      Logger.getLogger(Physiology.class.getName()).log(Level.SEVERE, null, ex);
      System.out.println("Error solving Model...");
    }
  }

  public static double randValue(double rangeMin, double rangeMax) {
    Random r = new Random();
    return rangeMin + (rangeMax - rangeMin) * r.nextDouble();
  }

  public static void main(String [] args) throws DerivativeException {
    
    Map<String,Double> inputs = new HashMap();
//    inputs.put("R_sys", 1.814);
    inputs.put("E_es_lvf", 1.734);

    Physiology physio = new Physiology("circulation/Smith2004_CVS_human.xml", "runge_kutta", 0.01, 4);
    MultiTable results = physio.run(inputs);
    
    Physiology.testModel(physio, Paths.get("circ_model_pathology_results.csv"), inputs);
    
//    try {
//      // Run with all default parameters
//      MultiTable results = physio.run(new HashMap());
//      Block mainBlock = results.getBlock(0);
//      int numRows = mainBlock.getRowCount();
//      for(String param : mainBlock.getIdentifiers()) {
//        System.out.println("Param: \"" + param + "\": " + mainBlock.getColumn(param).getValue(numRows-1));
//      }
//    } catch (DerivativeException ex) {
//      System.out.println("Error solving the differential equation");
//    }
  }
}
