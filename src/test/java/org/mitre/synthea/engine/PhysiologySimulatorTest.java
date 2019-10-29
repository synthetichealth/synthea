package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math.ode.DerivativeException;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.engine.PhysiologySimulator.ChartConfig;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;

public class PhysiologySimulatorTest {
  
  public static final TemporaryFolder outFolder = new TemporaryFolder();
  
  /**
   * Returns a mock MultiTable to test PhysiologySimulator methods.
   * @return mock MultiTable instance
   */
  public static MultiTable getMockTable() {
    double[] timePoints = {1.0,2.0,3.0,4.0,5.0};
    String[] identifiers = {"test1", "test2"};
    double[][] data = {{1.0,2.0,3.0,4.0,5.0},{1.0,1.0,1.0,1.0,1.0}};
    
    return new MultiTable(timePoints, data, identifiers);
  }
  
  /**
   * Sets up a temporary output folder.
   * @throws URISyntaxException when the paths are badly formed
   * @throws IOException when the output folder cannot be created
   */
  @Before
  public void setupTestPaths() throws URISyntaxException, IOException {
    // Create our temporary output directory
    outFolder.create();
    PhysiologySimulator.setOutputPath(outFolder.getRoot().toPath());
  }

  @Test
  public void testCvsSimulation() {
    try {
      
      PhysiologySimulator physio = new PhysiologySimulator(
          "circulation/Smith2004_CVS_human.xml", "runge_kutta", 0.01, 4);
      
      // Ensure we can get parameters. Check a couple
      List<String> params = physio.getParameters();
      assertTrue("V_lv", params.contains("V_lv"));
      assertTrue("P_rv", params.contains("P_rv"));
      assertTrue("period", params.contains("period"));
      assertTrue("P_ao", params.contains("P_ao"));
      
      // First run with all default parameters
      MultiTable results = physio.run(new HashMap<String,Double>());
      
      // Row 200 should be about 2 minutes into the simulation, which is where
      // we want to start capturing results
      List<Double> pao = new ArrayList<Double>();
      Column paoCol = results.getColumn("P_ao");
      for (int i = 200; i < paoCol.getRowCount(); i++) {
        pao.add(paoCol.getValue(i));
      }
      Double sys = Collections.max(pao);
      Double dia = Collections.min(pao);
      
      System.out.println("sys: " + sys);
      System.out.println("dia: " + dia);
      
      assertTrue("sys > 100", sys > 100);
      assertTrue("sys < 120", sys < 120);
      assertTrue("dia > 60", dia > 60);
      assertTrue("dia < 80", dia < 80);
      
      Map<String,Double> inputs = new HashMap<String,Double>();
      inputs.put("R_sys", 2.0);
      
      // Run with some inputs
      results = physio.run(inputs);
      
      pao = Lists.newArrayList(results.getColumn("P_ao"));
      sys = Collections.max(pao);
      dia = Collections.min(pao);
      
      assertEquals(results.getColumn("R_sys").getValue(0), 2.0, 0.0001);
      
      // Check that levels have appropriately changed
      assertTrue("sys > 120", sys > 120);
      assertTrue("sys < 150", sys < 150);
      assertTrue("dia > 80", dia > 80);
      assertTrue("dia < 100", dia < 100);
      
    } catch (DerivativeException ex) {
      throw new RuntimeException(ex);
    }
  }
  
  @Test
  public void testPhysiologyMain() throws DerivativeException, URISyntaxException, IOException {
    ClassLoader loader = getClass().getClassLoader();
    Path configPath = Paths.get(loader.getResource("config/simulations/Smith2004_CVS_test.yml")
        .toURI());
    
    String[] args = {configPath.toAbsolutePath().toString()};
    PhysiologySimulator.main(args);
    
    // Path to the folder the main function should have created
    Path testOut = Paths.get(outFolder.getRoot().getAbsolutePath(), "Smith2004_CVS");
    
    assertTrue("output folder created", testOut.toFile().exists());
    
    // Verify that the output folder contains the csv file
    File csvFile = Paths.get(testOut.toString(), "Smith2004_CVS.csv").toFile();
    
    assertTrue("csv file exported", csvFile.exists());
    assertTrue("non-empty csv file", csvFile.length() > 0);
    
    // Verify that the 4 images have written successfully
    int pngCount = 0;
    for (File file : testOut.toFile().listFiles()) {
      if (FilenameUtils.getExtension(file.getPath()).equals("png")) {
        pngCount++;
      }
    }
    
    assertEquals(4, pngCount);
  }
  
  @Test
  public void testGetSolvers() {
    Set<String> solvers = PhysiologySimulator.getSolvers();
    assertTrue(solvers.size() > 0);
  }
  
  @Test
  public void testGetParamDefault() {
    PhysiologySimulator physio = new PhysiologySimulator(
        "circulation/Smith2004_CVS_human.xml", "runge_kutta", 0.01, 4);
    
    assertEquals(1.0, physio.getParamDefault("period"), 0.001);
  }
  
  @Test(expected = RuntimeException.class)
  public void testInvalidModelFile() {
    new PhysiologySimulator("i_dont_exist.xml", "runge_kutta", 0.01, 4);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartConfigStart() throws URISyntaxException {
    ChartConfig config = new ChartConfig();
    
    config.setStartTime(-1);
    
    PhysiologySimulator.drawChart(getMockTable(), config);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartConfigEnd() throws URISyntaxException {
    ChartConfig config = new ChartConfig();
    
    // Mock only goes up to 5.0, so end is invalid
    config.setEndTime(10.0);

    PhysiologySimulator.drawChart(getMockTable(), config);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartTimeSpan() throws URISyntaxException {
    ChartConfig config = new ChartConfig();
    
    config.setEndTime(1.0);
    config.setStartTime(2.0);

    PhysiologySimulator.drawChart(getMockTable(), config);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChartAxis() throws URISyntaxException {
    ChartConfig config = new ChartConfig();
    
    config.setStartTime(0.0);
    config.setEndTime(1.0);
    config.setAxisParamX("oops");

    PhysiologySimulator.drawChart(getMockTable(), config);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testNoConfigArgument() throws URISyntaxException {
    String[] args = {};
    PhysiologySimulator.main(args);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testBadConfigArgument() throws URISyntaxException {
    String[] args = {"i_dont_exist.yml"};
    PhysiologySimulator.main(args);
  }
}