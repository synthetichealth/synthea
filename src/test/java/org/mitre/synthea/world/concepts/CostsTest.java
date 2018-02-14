package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class CostsTest {

  private HealthRecord record;
  
  @Before
  public void setup() {
    Costs.loadCostData();
    record = new HealthRecord();
  }
  
  @Test public void testCostByKnownCode() {
    Code code = new Code("RxNorm","564666","Nitroglycerin 0.4 MG/ACTUAT [Nitrolingual]");
    Entry fakeMedication = record.medicationStart(0L, code.display);
    fakeMedication.codes.add(code);
    
    double cost = Costs.calculateCost(fakeMedication, false);
    assertEquals(20.00, cost, 0.01); // assert the cost is within $0.01
  }
  
  @Test public void testCostByCodeWithDifferentSystem() {
    Code code = new Code("SNOMED-CT","564666","Fake SNOMED with the same code as an RxNorm code");
    Entry fakeProcedure = record.procedure(0L, code.display);
    fakeProcedure.codes.add(code);
    
    // it's the same number as above, but a procedure not a medication,
    // so we don't expect the same result
    double cost = Costs.calculateCost(fakeProcedure, false);
    double expectedCost = Double.parseDouble(Config.get("generate.costs.default_procedure_cost"));
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }
  
  @Test public void testCostByUnknownCode() {
    Code code = new Code("RxNorm","111111111111111111","Exaplitol");
    Entry fakeMedication = record.medicationStart(0L, code.display);
    fakeMedication.codes.add(code);
    
    double cost = Costs.calculateCost(fakeMedication, false);
    double expectedCost = Double.parseDouble(Config.get("generate.costs.default_medication_cost"));
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }
  
}
