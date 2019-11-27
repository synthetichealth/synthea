package org.mitre.synthea.modules;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class GrowthDataErrorsModuleTest {
  private HealthRecord record;
  private HealthRecord.Encounter first;
  private HealthRecord.Encounter second;

  @Before
  public void setup() {
    record = new HealthRecord(new Person(1));
    HealthRecord.Encounter e = record.encounterStart(1000, HealthRecord.EncounterType.OUTPATIENT);
    // Weight Observation
    e.addObservation(1000, "29463-7", 50d);
    // Height Observation
    e.addObservation(1000, "8302-2", 150d);
    e.addObservation(1000, "39156-5", 22.2d);
    e.codes.add(new HealthRecord.Code("http://snomed.info/sct", "183452005", ""));
    first = e;
    e = record.encounterStart(100000, HealthRecord.EncounterType.OUTPATIENT);
    // Weight Observation
    e.addObservation(100000, "29463-7", 60d);
    // Height Observation
    e.addObservation(100000, "8302-2", 160d);
    e.addObservation(100000, "39156-5", 23.4d);
    e.codes.add(new HealthRecord.Code("http://snomed.info/sct", "183452005", ""));
    second = e;
  }

  @Test
  public void process() {
    GrowthDataErrorsModule m = new GrowthDataErrorsModule();
    m.process(new Person(1), record.encounters, 100000, new Random());
  }


  @Test
  public void introduceWeightUnitError() {
    GrowthDataErrorsModule.introduceWeightUnitError(first);
    assertEquals(110.25, (Double) first.findObservation("29463-7").value, 0.1);
  }

  @Test
  public void introduceHeightUnitError() {
    GrowthDataErrorsModule.introduceHeightUnitError(first);
    assertEquals(59.1, (Double) first.findObservation("8302-2").value, 0.1);
  }

  @Test
  public void introduceTransposeError() {
    GrowthDataErrorsModule.introduceTransposeError(first, "weight");
    assertEquals(5, (Double) first.findObservation("29463-7").value, 0.1);

    GrowthDataErrorsModule.introduceTransposeError(second, "height");
    assertEquals(106, (Double) second.findObservation("8302-2").value, 0.1);
  }

  @Test
  public void introduceWeightSwitchError() {
    GrowthDataErrorsModule.introduceWeightSwitchError(first);
    assertEquals(150, (Double) first.findObservation("29463-7").value, 0.1);
  }

  @Test
  public void introduceHeightSwitchError() {
    GrowthDataErrorsModule.introduceHeightSwitchError(second);
    assertEquals(160, (Double) second.findObservation("29463-7").value, 0.1);
  }

  @Test
  public void introduceWeightExtremeError() {
    GrowthDataErrorsModule.introduceWeightExtremeError(first);
    assertEquals(500, (Double) first.findObservation("29463-7").value, 0.1);
  }

  @Test
  public void introduceHeightExtremeError() {
    GrowthDataErrorsModule.introduceHeightExtremeError(second);
    assertEquals(1600, (Double) second.findObservation("8302-2").value, 0.1);
  }

  @Test
  public void introduceHeightAbsoluteError() {
    GrowthDataErrorsModule.introduceHeightAbsoluteError(first, new Random());
    assertTrue((Double) first.findObservation("8302-2").value <= 147d);
    assertTrue((Double) first.findObservation("8302-2").value >= 144d);
  }

  @Test
  public void introduceWeightDuplicateError() {
    GrowthDataErrorsModule.introduceWeightDuplicateError(first, new Random());
    long obsCount = first.observations.stream()
        .filter(o -> o.containsCode("29463-7", "http://loinc.org"))
        .count();
    assertEquals(2, obsCount);
  }

  @Test
  public void introduceHeightDuplicateError() {
    GrowthDataErrorsModule.introduceHeightDuplicateError(first, new Random());
    long obsCount = first.observations.stream()
        .filter(o -> o.containsCode("8302-2", "http://loinc.org"))
        .count();
    assertEquals(2, obsCount);
  }

  @Test
  public void introduceWeightCarriedForwardError() {
    GrowthDataErrorsModule.introduceWeightCarriedForwardError(second);
    assertEquals(50, (Double) second.findObservation("29463-7").value, 0.1);
  }

  @Test
  public void introduceHeightCarriedForwardError() {
    GrowthDataErrorsModule.introduceHeightCarriedForwardError(second);
    assertEquals(150, (Double) second.findObservation("8302-2").value, 0.1);
  }

  @Test
  public void encountersWithObservationsOfCode() {
    GrowthDataErrorsModule mod = new GrowthDataErrorsModule();
    List<HealthRecord.Encounter> es = mod.encountersWithObservationsOfCode(record.encounters,
        "8302-2", "http://loinc.org");
    assertEquals(2, es.size());

  }
}