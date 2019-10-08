package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class CostsTest {

  private Person person;
  private Payer noInsurance;
  long time;
  
  /**
   * Setup for Costs Tests.
   */
  @Before
  public void setup() {
    Costs.loadCostData();
    person = new Person(System.currentTimeMillis());
    Payer.loadNoInsurance();
    noInsurance = Payer.noInsurance;
    time = 0L;
    person.setPayerAtTime(time, noInsurance);
  }
  
  @Test public void testCostByKnownCode() {
    Code code = new Code("RxNorm","705129","Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray");
    // note: cost range = 8.5-400, with mode at 20
    double minCost = 8.5;
    double maxCost = 400;
    
    Entry fakeMedication = person.record.medicationStart(time, code.display, true);
    fakeMedication.codes.add(code);
    
    double cost = Costs.determineCostOfEntry(fakeMedication, person);
    // at this point person has no state set, so there won't be a geographic factor applied
    
    assertTrue(cost <= maxCost);
    assertTrue(cost >= minCost);

    person.attributes.put(Person.STATE, "Massachusetts");
    double adjFactor = 1.0333;
    cost = Costs.determineCostOfEntry(fakeMedication, person);
    assertTrue(cost <= (maxCost * adjFactor));
    assertTrue(cost >= (minCost * adjFactor));
  }
  
  @Test public void testCostByCodeWithDifferentSystem() {
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Entry fakeProcedure = person.record.procedure(time, code.display);
    fakeProcedure.codes.add(code);
    
    // it's the same number as above, but a procedure not a medication,
    // so we don't expect the same result
    double cost = Costs.determineCostOfEntry(fakeProcedure, person);
    double expectedCost = Double.parseDouble(Config.get("generate.costs.default_procedure_cost"));
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }
  
  @Test public void testCostByUnknownCode() {
    Code code = new Code("RxNorm","111111111111111111","Exaplitol");
    Entry fakeMedication = person.record.medicationStart(time, code.display, false);
    fakeMedication.codes.add(code);
    
    double cost = Costs.determineCostOfEntry(fakeMedication, person);
    double expectedCost = Double.parseDouble(Config.get("generate.costs.default_medication_cost"));
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }
}