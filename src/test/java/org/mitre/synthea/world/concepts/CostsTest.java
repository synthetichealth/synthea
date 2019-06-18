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
  
  @Before
  public void setup() {
    Costs.loadCostData();
    person = new Person(System.currentTimeMillis());
    noInsurance = new Payer();
    noInsurance.uuid = null;
  }
  
  @Test public void testCostByKnownCode() {
    Code code = new Code("RxNorm","705129","Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray");
    // note: cost range = 8.5-400, with mode at 20
    double minCost = 8.5;
    double maxCost = 400;
    
    Entry fakeMedication = person.record.medicationStart(0L, code.display);
    fakeMedication.codes.add(code);
    
    double cost = Costs.calculateCost(fakeMedication, person, null, noInsurance);
    // at this point person has no state set, so there won't be a geographic factor applied
    
    assertTrue(cost <= maxCost);
    assertTrue(cost >= minCost);

    person.attributes.put(Person.STATE, "Massachusetts");
    double adjFactor = 1.0333;
    cost = Costs.calculateCost(fakeMedication, person, null, noInsurance);
    assertTrue(cost <= (maxCost * adjFactor));
    assertTrue(cost >= (minCost * adjFactor));
  }
  
  @Test public void testCostByCodeWithDifferentSystem() {
    Code code = new Code("SNOMED-CT","705129","Fake SNOMED with the same code as an RxNorm code");
    Entry fakeProcedure = person.record.procedure(0L, code.display);
    fakeProcedure.codes.add(code);
    
    // it's the same number as above, but a procedure not a medication,
    // so we don't expect the same result
    double cost = Costs.calculateCost(fakeProcedure, person, null, noInsurance);
    double expectedCost = Double.parseDouble(Config.get("generate.costs.default_procedure_cost"));
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }
  
  @Test public void testCostByUnknownCode() {
    Code code = new Code("RxNorm","111111111111111111","Exaplitol");
    Entry fakeMedication = person.record.medicationStart(0L, code.display);
    fakeMedication.codes.add(code);
    
    double cost = Costs.calculateCost(fakeMedication, person, null, noInsurance);
    double expectedCost = Double.parseDouble(Config.get("generate.costs.default_medication_cost"));
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  } 
}