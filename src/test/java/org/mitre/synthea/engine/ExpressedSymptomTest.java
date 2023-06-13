package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mitre.synthea.engine.ExpressedSymptom.SymptomInfo;
import org.mitre.synthea.engine.ExpressedSymptom.SymptomSource;

public class ExpressedSymptomTest {

  @Test
  public void testSymptomInfo() {
    String name = "pain";
    ExpressedSymptom symptom = new ExpressedSymptom(name);
    String cause = "test";
    Integer value = 100;
    Long time = 0L;

    SymptomInfo info = symptom.new SymptomInfo(cause, value, time);
    assertEquals(cause, info.getCause());
    assertEquals(value, info.getValue());
    assertEquals(time, info.getTime());

    SymptomInfo clone = info.clone();
    assertEquals(info.getCause(), clone.getCause());
    assertEquals(info.getValue(), clone.getValue());
    assertEquals(info.getTime(), clone.getTime());
  }

  @Test
  public void testSymptomSource() {
    String name = "pain";
    ExpressedSymptom symptom = new ExpressedSymptom(name);
    String sourceName = "unit_test";

    SymptomSource source = symptom.new SymptomSource(sourceName);
    assertEquals(sourceName, source.getSource());
    assertNull(source.getCurrentValue());
    assertNull(source.getLastUpdateTime());
    assertFalse(source.isResolved());
    assertTrue(source.getTimeInfos().isEmpty());

    source.resolve();
    assertTrue(source.isResolved());

    source.activate();
    assertFalse(source.isResolved());

    String causeA = "foo";
    Integer valueA = 100;
    Long timeA = 0L;

    source.addInfo(causeA, timeA, valueA, false);
    assertEquals(valueA, source.getCurrentValue());
    assertEquals(timeA, source.getLastUpdateTime());
    assertFalse(source.isResolved());
    assertFalse(source.getTimeInfos().isEmpty());
    assertEquals(valueA, source.getTimeInfos().get(timeA).getValue());

    String causeB = "bar";
    Integer valueB = 200;
    Long timeB = 1L;

    source.addInfo(causeB, timeB, valueB, true);
    assertEquals(valueB, source.getCurrentValue());
    assertEquals(timeB, source.getLastUpdateTime());
    assertTrue(source.isResolved());
    assertFalse(source.getTimeInfos().isEmpty());
    assertEquals(valueB, source.getTimeInfos().get(timeB).getValue());
    assertEquals(valueA, source.getTimeInfos().get(timeA).getValue());
  }

  @Test
  public void testExpressedSymtpom() {
    String name = "pain";
    ExpressedSymptom symptom = new ExpressedSymptom(name);
    assertTrue(symptom.getSources().isEmpty());
    assertEquals(0, symptom.getSymptom());
    assertNull(symptom.getSourceWithHighValue());
    assertNull(symptom.getValueFromSource(null));
    assertNull(symptom.getSymptomLastUpdatedTime(null));
    symptom.addressSource(null);

    String module = "testModule";
    String cause = "testCause";
    assertNull(symptom.getSourceWithHighValue());
    assertNull(symptom.getValueFromSource(module));
    assertNull(symptom.getSymptomLastUpdatedTime(module));
    symptom.addressSource(module);

    for (long l = 0L; l < 3L; l++) {
      symptom.onSet(module, cause, l, (int) (100 * l), false);
      assertEquals(module, symptom.getSourceWithHighValue());
      assertEquals(Integer.valueOf((int) (100 * l)), symptom.getValueFromSource(module));
      assertEquals((int) (100 * l), symptom.getSymptom());
      assertEquals(Long.valueOf(l), symptom.getSymptomLastUpdatedTime(module));
    }

    String anotherModule = "anotherModule";
    for (long l = 0L; l < 3L; l++) {
      symptom.onSet(anotherModule, cause, l, (int) (10 * l), false);
      assertEquals(module, symptom.getSourceWithHighValue());
      assertEquals(Integer.valueOf((int) (10 * l)), symptom.getValueFromSource(anotherModule));
      assertEquals(200, symptom.getSymptom());
      assertEquals(Long.valueOf(l), symptom.getSymptomLastUpdatedTime(anotherModule));
    }

    symptom.addressSource(module);
    assertEquals(anotherModule, symptom.getSourceWithHighValue());
    assertEquals(20, symptom.getSymptom());
    assertEquals(Integer.valueOf(20), symptom.getValueFromSource(anotherModule));

    symptom.addressSource(anotherModule);
    assertEquals(0, symptom.getSymptom());
    assertNull(symptom.getSourceWithHighValue());
  }
}
