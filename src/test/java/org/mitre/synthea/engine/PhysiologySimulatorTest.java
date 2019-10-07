package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math.ode.DerivativeException;
import org.junit.Test;
import org.simulator.math.odes.MultiTable;
import org.simulator.math.odes.MultiTable.Block.Column;

public class PhysiologySimulatorTest {

  @Test
  public void testCvsSimulation() {
    try {
      
      PhysiologySimulator physio = new PhysiologySimulator("circulation/Smith2004_CVS_human.xml",
          "runge_kutta", 0.01, 4);
      
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
}