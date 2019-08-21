package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

//test calculate, conditionsInYear, weight

public class QualityOfLifeTest {

  private Person person;

  public static final long stopTime = ((long) (365.25 * 35)) + 1;

  /**
   * Setup for Quality calculation tests.
   */
  @Before
  public void init() {
    // Create Person
    person = new Person(0);
    person.attributes.put(Person.BIRTHDATE, 0L);

    // Ensure Person's payer is not null
    Payer.loadNoInsurance();
    person.setPayerAtTime(0L, Payer.noInsurance);
    person.setPayerAtTime(TimeUnit.DAYS.toMillis((long) (365.25 * 10)), Payer.noInsurance);

    // Diabetes - code = 44054006;  dw = 0.031, 0.049, 0.072
    // ADD      - code = 192127007; dw = 0.028, 0.045, 0.066
    // Asthma   - code = 195967001; dw = 0.015, 0.036, 0.133

    //                 asthma
    //           |-----------------|
    //             ADD             diabetes
    //           |-----|     |-----------------|
    // |---|-----|-----|-----|-----|-----|-----|
    // 0   5     10    15    20    25    30   35

    // ADD starts
    Entry addCondition = person.record.conditionStart(
        TimeUnit.DAYS.toMillis((long) (365.25 * 10)), "192127007");
    addCondition.name = "Child attention deficit disorder";
    Code addCode = new Code("SNOMED", "192127007", "Child attention deficit disorder");
    addCondition.codes.add(addCode);

    // Asthma starts
    Entry asthmaCondition = person.record.conditionStart(
        TimeUnit.DAYS.toMillis((long) (365.25 * 10)), "195967001");
    asthmaCondition.name = "Asthma";
    Code asthmaCode = new Code("SNOMED", "195967001", "Asthma");
    asthmaCondition.codes.add(asthmaCode);

    // ADD ends
    person.record.conditionEnd((TimeUnit.DAYS.toMillis((long) (365.25 * 15) - 1)), "192127007");

    // Diabetes starts
    Entry diabetesCondition = person.record.conditionStart(
        TimeUnit.DAYS.toMillis((long) (365.25 * 20)), "44054006");
    diabetesCondition.name = "Diabetes";
    Code diabetesCode = new Code("SNOMED", "44054006", "Diabetes");
    diabetesCondition.codes.add(diabetesCode);

    // Asthma ends
    person.record.conditionEnd((TimeUnit.DAYS.toMillis((long) (365.25 * 25) - 1)), "195967001");
  }

  @Test
  public void testCalculateLiving() {
    // living patient
    // + 1 ms because (365.25 * 35) = 12783.75 as double and 12783 as long
    double[] qol = QualityOfLifeModule.calculate(person, TimeUnit.DAYS.toMillis(stopTime));

    double dalyLiving = qol[0];
    double qalyLiving = qol[1];
    assertEquals(true, (dalyLiving > 2.2 && dalyLiving < 2.3));
    assertEquals(true, (qalyLiving > 32 && qalyLiving < 33));
  }

  @Test
  public void testCalculateDeceased() {
    // deceased patient
    long time = TimeUnit.DAYS.toMillis((long) (365.25 * 35));
    person.recordDeath(time, null);
    double[] qol = QualityOfLifeModule.calculate(person, TimeUnit.DAYS.toMillis(stopTime));

    double dalyDeceased = qol[0];
    double qalyDeceased = qol[1];
    assertEquals(true, (dalyDeceased > 54 && dalyDeceased < 55));
    assertEquals(true, (qalyDeceased > 32 && qalyDeceased < 33));
  }

  @Test
  public void testConditionsInYear() {
    List<Entry> allConditions = new ArrayList<Entry>();
    for (Encounter e : person.record.encounters) {
      for (Entry condition : e.conditions) {
        allConditions.add(condition);
      }
    }

    // conditions in year 5
    List<Entry> conditionsYear5 = QualityOfLifeModule.conditionsInYear(allConditions,
        TimeUnit.DAYS.toMillis((long) (365.25 * 5)),
        TimeUnit.DAYS.toMillis((long) (365.25 * 6)));
    List<Entry> empty = new ArrayList<Entry>();
    assertEquals(empty, conditionsYear5);

    // conditions in year 10
    List<Entry> conditionsYear10 = QualityOfLifeModule.conditionsInYear(allConditions,
        TimeUnit.DAYS.toMillis((long) (365.25 * 10)),
        TimeUnit.DAYS.toMillis((long) (365.25 * 11)));
    assertEquals(2, conditionsYear10.size());
    assertEquals("Child attention deficit disorder", conditionsYear10.get(0).name);
    assertEquals("Asthma", conditionsYear10.get(1).name);

    // conditions in year 30
    List<Entry> conditionsYear30 = QualityOfLifeModule.conditionsInYear(allConditions,
        TimeUnit.DAYS.toMillis((long) (365.25 * 30)),
        TimeUnit.DAYS.toMillis((long) (365.25 * 31)));
    assertEquals(1, conditionsYear30.size());
    assertEquals("Diabetes", conditionsYear30.get(0).name);
  }

  @Test
  public void testWeight() {
    // age 15 with disability weight of 0.45
    double weight = QualityOfLifeModule.weight(0.45, 15);
    assertEquals(true, (weight > 0.614 && weight < 0.615));
  }
}