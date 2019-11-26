package org.mitre.synthea.modules;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class GrowthDataErrorsModuleTest {
  private HealthRecord record;
  private HealthRecord.Encounter first;
  private HealthRecord.Encounter second;
  private GrowthDataErrorsModule mod;

  @Before
  public void setup() {
    mod = new GrowthDataErrorsModule();
    record = new HealthRecord(new Person(1));
    HealthRecord.Encounter e = record.encounterStart(1000, HealthRecord.EncounterType.OUTPATIENT);
    // Weight Observation
    e.addObservation(1000, "29463-7", 50d);
    // Height Observation
    e.addObservation(1000, "8302-2", 150d);
    e.addObservation(1000, "39156-5", 22.2d);
    e.codes.add(new HealthRecord.Code("http://snomed.info/sct", "183452005", ""));
    //record.encounterEnd(1005, HealthRecord.EncounterType.OUTPATIENT);
    first = e;
    e = record.encounterStart(100000, HealthRecord.EncounterType.OUTPATIENT);
    // Weight Observation
    e.addObservation(100000, "29463-7", 60d);
    // Height Observation
    e.addObservation(100000, "8302-2", 160d);
    e.addObservation(100000, "39156-5", 23.4d);
    e.codes.add(new HealthRecord.Code("http://snomed.info/sct", "183452005", ""));
    //record.encounterEnd(100005, HealthRecord.EncounterType.OUTPATIENT);
    second = e;
  }

  @Test
  public void process() {
    GrowthDataErrorsModule m = new GrowthDataErrorsModule();
    m.process(null, new ArrayList<>(), 1, null);
  }


  @Test
  public void introduceWeightUnitError() {
    mod.introduceWeightUnitError(first);
    assertEquals(110.25, (Double) first.findObservation("29463-7").value, 0.1);
  }

  @Test
  public void introduceHeightUnitError() {
  }

  @Test
  public void introduceTransposeError() {
  }

  @Test
  public void introduceWeightSwitchError() {
  }

  @Test
  public void introduceHeightSwitchError() {
  }

  @Test
  public void introduceWeightExtremeError() {
  }

  @Test
  public void introduceHeightExtremeError() {
  }

  @Test
  public void introduceHeightAbsoluteError() {
  }

  @Test
  public void introduceWeightDuplicateError() {
  }

  @Test
  public void introduceHeightDuplicateError() {
  }

  @Test
  public void introduceWeightCarriedForwardError() {
  }

  @Test
  public void introduceHeightCarriedForwardError() {
  }
}