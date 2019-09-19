package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math.ode.DerivativeException;
import org.junit.Test;
import org.simulator.math.odes.MultiTable;

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
      
      List<Double> pao = Lists.newArrayList(results.getColumn("P_ao"));
      Double sys = Collections.max(pao);
      Double dia = Collections.min(pao);
      
      assertTrue("sys > 110", sys > 110);
      assertTrue("sys < 120", sys < 120);
      assertTrue("dia > 70", dia > 70);
      assertTrue("dia < 80", dia < 80);
      
      Map<String,Double> inputs = new HashMap<String,Double>();
      inputs.put("R_sys", 1.814);
      
      // Run with some inputs
      results = physio.run(inputs);
      
      pao = Lists.newArrayList(results.getColumn("P_ao"));
      sys = Collections.max(pao);
      dia = Collections.min(pao);
      
      assertEquals(results.getColumn("R_sys").getValue(0), 1.814, 0.0001);
      
      // Check that levels have appropriately changed
      assertTrue("sys > 140", sys > 140);
      assertTrue("sys < 150", sys < 150);
      assertTrue("dia > 80", dia > 80);
      assertTrue("dia < 90", dia < 90);
      
    } catch (DerivativeException ex) {
      throw new RuntimeException(ex);
    }
  }
}