package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.mitre.synthea.TestHelper.timestamp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Before;
import org.junit.Test;

import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;

public class PersonTest {
  private Person person;

  @Before
  public void setup() throws IOException {
    TestHelper.exportOff();
    Config.set("generate.only_dead_patients", "false");
    person = new Person(0L);
  }
  
  @Test
  public void testSerializationAndDeserialization() throws Exception {
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = 1;
    opts.minAge = 50;
    opts.maxAge = 100;
    Generator generator = new Generator(opts);

    Person original = generator.generatePerson(0, 0);
    File tf = File.createTempFile("patient", "synthea");
    FileOutputStream fos = new FileOutputStream(tf);
    ObjectOutputStream oos = new ObjectOutputStream(fos);
    oos.writeObject(original);
    oos.close();
    fos.close();
    FileInputStream fis = new FileInputStream(tf);
    ObjectInputStream ois = new ObjectInputStream(fis);
    Person rehydrated = (Person)ois.readObject();
    assertEquals(original.random.nextInt(), rehydrated.random.nextInt());
    assertEquals(original.seed, rehydrated.seed);
    assertEquals(original.populationSeed, rehydrated.populationSeed);
    assertEquals(original.symptoms, rehydrated.symptoms);
    assertEquals(original.symptomStatuses, rehydrated.symptomStatuses);
    assertEquals(original.hasMultipleRecords, rehydrated.hasMultipleRecords);
    assertEquals(original.attributes.keySet(), rehydrated.attributes.keySet());
  }

  @Test
  public void testAge() {
    long birthdate;
    long now;

    // first set of test cases, birthdate = 0, (1/1/1970)
    birthdate = 0;

    now = 0;
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 0);

    now = timestamp(2017, 10, 10, 10, 10, 10);
    testAgeYears(birthdate, now, 47);

    now = timestamp(1970, 1, 29, 5, 5, 5); // less than a month has passed
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 0);

    // second set of test cases, birthdate = Apr 7, 2016 (Synthea repo creation date)
    birthdate = timestamp(2016, 4, 7, 17, 14, 0);

    now = birthdate;
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 0);

    now = timestamp(2016, 5, 7, 17, 14, 0);
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 1);

    now = timestamp(2017, 4, 6, 17, 14, 0);
    testAgeYears(birthdate, now, 0);
    testAgeMonths(birthdate, now, 11);
  }

  private void testAgeYears(long birthdate, long now, long expectedAge) {
    person.attributes.put(Person.BIRTHDATE, birthdate);
    assertEquals(expectedAge, person.ageInYears(now));
  }

  private void testAgeMonths(long birthdate, long now, long expectedAge) {
    person.attributes.put(Person.BIRTHDATE, birthdate);
    assertEquals(expectedAge, person.ageInMonths(now));
  }
}
