package org.mitre.synthea.world.concepts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

public class CostsTest {

  private Person person;
  long time;

  /**
   * Setup for Costs Tests.
   */
  @Before
  public void setup() {
    Costs.loadCostData();
    PayerManager.loadNoInsurance();
    time = 0L;
    person = new Person(System.currentTimeMillis());
    Provider provider = TestHelper.buildMockProvider();
    for (EncounterType type : EncounterType.values()) {
      person.setProvider(type, provider);
    }
    person.attributes.put(Person.BIRTHDATE, time);
    person.coverage.setPlanToNoInsurance(time);
  }

  @Test public void testCostByKnownCode() {
    Code code = new Code("RxNorm","705129","Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray");
    // note: cost range = 1.01-66.30, with mode at 6.05
    double minCost = 1.01;
    double maxCost = 66.30;

    Medication fakeMedication = person.record.medicationStart(time, code.display, true);
    fakeMedication.administration = true; // quantity of 1
    fakeMedication.codes.add(code);

    double cost = Costs.determineCostOfEntry(fakeMedication, person);
    // at this point person has no state set, so there won't be a geographic factor applied

    assertTrue(cost <= maxCost);
    assertTrue(cost >= minCost);

    String state = Config.get("test_state.default", Generator.DEFAULT_STATE);
    person.attributes.put(Person.STATE, state);
    double adjFactor = 0.5096;
    cost = Costs.determineCostOfEntry(fakeMedication, person);
    assertTrue(cost <= (maxCost * adjFactor));
    assertTrue(cost >= (minCost * adjFactor));
  }

  @Test public void testDeviceCostByKnownCode() {
    Code code = new Code("SNOMED","363753007","Crutches");
    double minCost = 66.96;
    double maxCost = 66.96;

    Entry fakeDevice = person.record.deviceImplant(time, code.display);
    fakeDevice.codes.add(code);

    double cost = Costs.determineCostOfEntry(fakeDevice, person);
    // at this point person has no state set, so there won't be a geographic factor applied

    assertTrue(cost <= maxCost);
    assertTrue(cost >= minCost);

    String state = Config.get("test_state.default", Generator.DEFAULT_STATE);
    person.attributes.put(Person.STATE, state);
    double adjFactor = 0.8183;
    cost = Costs.determineCostOfEntry(fakeDevice, person);
    assertTrue(cost <= (maxCost * adjFactor));
    assertTrue(cost >= (minCost * adjFactor));
  }

  @Test public void testSupplyCostByKnownCode() {
    Code code = new Code("SNOMED","337388004","Blood glucose testing strips");
    double minCost = 8.32;
    double maxCost = 8.32;

    Entry fakeSupply = person.record.useSupply(time, code, 1);
    fakeSupply.codes.add(code);

    double cost = Costs.determineCostOfEntry(fakeSupply, person);
    // at this point person has no state set, so there won't be a geographic factor applied

    assertTrue(cost <= maxCost);
    assertTrue(cost >= minCost);

    String state = Config.get("test_state.default", Generator.DEFAULT_STATE);
    person.attributes.put(Person.STATE, state);
    double adjFactor = 0.8183;
    cost = Costs.determineCostOfEntry(fakeSupply, person);
    assertTrue(cost <= (maxCost * adjFactor));
    assertTrue(cost >= (minCost * adjFactor));
  }

  @Test public void testUpdatedCostsbyKnownCode() {
    // These tests test some costs added in the August 2020 Costs Update.

    // Test an updated medication cost.
    // 993452,816.12,1014.45,1237.68,1 ML denosumab 60 MG/ML Prefilled Syringe (Prolia)
    Code code = new Code("RxNorm","993452","1 ML denosumab 60 MG/ML Prefilled Syringe (Prolia)");
    double minCost = 816.12;
    double maxCost = 1237.68;

    Medication fakeMedication = person.record.medicationStart(time, code.display, true);
    fakeMedication.administration = true; // quantity of 1
    fakeMedication.codes.add(code);

    double cost = Costs.determineCostOfEntry(fakeMedication, person);
    // At this point there is no state set, so there is no geographic factor applied.
    assertTrue(cost <= maxCost);
    assertTrue(cost >= minCost);
    // Now test cost with adjustment factor.
    String state = Config.get("test_state.alternative", "California");
    person.attributes.put(Person.STATE, state);
    double adjFactor = 1.0227;
    cost = Costs.determineCostOfEntry(fakeMedication, person);
    assertTrue(cost <= (maxCost * adjFactor));
    assertTrue(cost >= (minCost * adjFactor));

    // Test an updated procedure cost.
    // 48387007,235,962.5,1690,"Incision of trachea (procedure)
    code = new Code("SNOMED","48387007","Incision of trachea (procedure)");
    minCost = 235;
    maxCost = 1690;

    Entry fakeEntry = person.record.procedure(time, code.display);
    fakeEntry.codes.add(code);

    cost = Costs.determineCostOfEntry(fakeEntry, person);
    adjFactor = 1.2010;
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
    double expectedCost = Config.getAsDouble("generate.costs.default_procedure_cost");
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }

  @Test public void testCostByUnknownCode() {
    Code code = new Code("RxNorm","111111111111111111","Exaplitol");
    Entry fakeMedication = person.record.medicationStart(time, code.display, false);
    fakeMedication.codes.add(code);

    double cost = Costs.determineCostOfEntry(fakeMedication, person);
    double expectedCost = Config.getAsDouble("generate.costs.default_medication_cost");
    assertEquals(expectedCost, cost, 0.01); // assert the cost is within $0.01
  }

  @Test public void testTriangularDistributionLimits() {
    Random random = new Random();
    double min = 0;
    double max = 1;
    double mode = 0.5;
    for (int i = 0; i < 10000; i++) {
      double value = Costs.CostData.triangularDistribution(min, max, mode, random.nextDouble());
      assertTrue(value >= min);
      assertTrue(value <= max);
    }
  }
}