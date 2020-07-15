package org.mitre.synthea.editors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.editors.GrowthDataErrorsEditor;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class GrowthDataErrorsEditorTest {
  private HealthRecord record;
  private HealthRecord.Encounter first;
  private HealthRecord.Encounter second;

  /**
   * Create a couple of encounters for use in later tests.
   */
  @Before
  public void setup() {
    record = new HealthRecord(new Person(1));
    HealthRecord.Encounter e = record.encounterStart(1000, HealthRecord.EncounterType.OUTPATIENT);
    // Weight Observation
    e.addObservation(1000, GrowthDataErrorsEditor.WEIGHT_LOINC_CODE, 50d, "Body Weight");
    // Height Observation
    e.addObservation(1000, GrowthDataErrorsEditor.HEIGHT_LOINC_CODE, 150d, "Body Height");
    e.addObservation(1000, GrowthDataErrorsEditor.BMI_LOINC_CODE, 22.2d,
        "Body Mass Index");
    e.codes.add(new HealthRecord.Code("http://snomed.info/sct", "183452005", ""));
    first = e;
    e = record.encounterStart(100000, HealthRecord.EncounterType.OUTPATIENT);
    // Weight Observation
    e.addObservation(100000, GrowthDataErrorsEditor.WEIGHT_LOINC_CODE, 60d,
        "Body Weight");
    // Height Observation
    e.addObservation(100000, GrowthDataErrorsEditor.HEIGHT_LOINC_CODE, 160d,
        "Body Height");
    e.addObservation(100000, GrowthDataErrorsEditor.BMI_LOINC_CODE, 23.4d,
        "Body Mass Index");
    e.codes.add(new HealthRecord.Code("http://snomed.info/sct", "183452005", ""));
    second = e;
  }

  @Test
  public void process() {
    GrowthDataErrorsEditor m = new GrowthDataErrorsEditor();
    m.process(new Person(1), record.encounters, 100000, new Random());
  }


  @Test
  public void introduceWeightUnitError() {
    GrowthDataErrorsEditor.introduceWeightUnitError(first);
    assertEquals(110.25, (Double) first.findObservation(
            GrowthDataErrorsEditor.WEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceHeightUnitError() {
    GrowthDataErrorsEditor.introduceHeightUnitError(first);
    assertEquals(59.1, (Double) first.findObservation(
            GrowthDataErrorsEditor.HEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceTransposeError() {
    GrowthDataErrorsEditor.introduceTransposeError(first, "weight");
    assertEquals(5, (Double) first.findObservation(
            GrowthDataErrorsEditor.WEIGHT_LOINC_CODE).value, 0.1);

    GrowthDataErrorsEditor.introduceTransposeError(second, "height");
    assertEquals(106, (Double) second.findObservation(
            GrowthDataErrorsEditor.HEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceWeightSwitchError() {
    GrowthDataErrorsEditor.introduceWeightSwitchError(first);
    assertEquals(150, (Double) first.findObservation(
            GrowthDataErrorsEditor.WEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceHeightSwitchError() {
    GrowthDataErrorsEditor.introduceHeightSwitchError(second);
    assertEquals(160, (Double) second.findObservation(
            GrowthDataErrorsEditor.WEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceWeightExtremeError() {
    GrowthDataErrorsEditor.introduceWeightExtremeError(first);
    assertEquals(500, (Double) first.findObservation(
            GrowthDataErrorsEditor.WEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceHeightExtremeError() {
    GrowthDataErrorsEditor.introduceHeightExtremeError(second);
    assertEquals(1600, (Double) second.findObservation(
            GrowthDataErrorsEditor.HEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceHeightAbsoluteError() {
    GrowthDataErrorsEditor.introduceHeightAbsoluteError(first, new Random());
    assertTrue((Double) first.findObservation(
            GrowthDataErrorsEditor.HEIGHT_LOINC_CODE).value <= 147d);
    assertTrue((Double) first.findObservation(
            GrowthDataErrorsEditor.HEIGHT_LOINC_CODE).value >= 144d);
  }

  @Test
  public void introduceWeightDuplicateError() {
    GrowthDataErrorsEditor.introduceWeightDuplicateError(first, new Random());
    long obsCount = first.observations.stream()
        .filter(o -> o.type.equals(GrowthDataErrorsEditor.WEIGHT_LOINC_CODE))
        .count();
    assertEquals(2, obsCount);
  }

  @Test
  public void introduceHeightDuplicateError() {
    GrowthDataErrorsEditor.introduceHeightDuplicateError(first, new Random());
    long obsCount = first.observations.stream()
        .filter(o -> o.type.equals(GrowthDataErrorsEditor.HEIGHT_LOINC_CODE))
        .count();
    assertEquals(2, obsCount);
  }

  @Test
  public void introduceWeightCarriedForwardError() {
    GrowthDataErrorsEditor.introduceWeightCarriedForwardError(second);
    assertEquals(50, (Double) second.findObservation(
            GrowthDataErrorsEditor.WEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void introduceHeightCarriedForwardError() {
    GrowthDataErrorsEditor.introduceHeightCarriedForwardError(second);
    assertEquals(150, (Double) second.findObservation(
            GrowthDataErrorsEditor.HEIGHT_LOINC_CODE).value, 0.1);
  }

  @Test
  public void encountersWithObservationsOfCode() {
    GrowthDataErrorsEditor mod = new GrowthDataErrorsEditor();
    List<HealthRecord.Encounter> es = mod.encountersWithObservationsOfCode(record.encounters,
        GrowthDataErrorsEditor.HEIGHT_LOINC_CODE);
    assertEquals(2, es.size());

  }
}