package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mitre.synthea.TestHelper.timestamp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.concepts.VitalSign;

public class PersonTest {
  private Person person;
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();
  
  /**
   * Create a person for use in each test.
   * @throws IOException if something goes wrong.
   */
  @Before
  public void setup() throws IOException {
    TestHelper.exportOff();
    Config.set("generate.only_dead_patients", "false");
    person = new Person(0L);
  }
  
  /**
   * Serialize a person, then deserialize and return the person. Note that when serializing
   * more than one person it is much more efficient to serialize them within a collection since
   * shared objects will only be serialized once and deserialization will not create duplicate
   * objects in memory.
   * @param original person to serialize
   * @return person following serialization and deserialization process
   */
  public static Person serializeAndDeserialize(Person original)
          throws IOException, ClassNotFoundException {
    // Serialize
    File tf = File.createTempFile("patient", "synthea");
    FileOutputStream fos = new FileOutputStream(tf);
    ObjectOutputStream oos = new ObjectOutputStream(fos);
    oos.writeObject(original);
    oos.close();
    fos.close();
    
    // Deserialize
    FileInputStream fis = new FileInputStream(tf);
    ObjectInputStream ois = new ObjectInputStream(fis);
    Person rehydrated = (Person) ois.readObject();
    ois.close();
    
    return rehydrated;
  }
  
  @Test
  public void testSerializationAndDeserialization() throws Exception {
    // Skip if physiology generators are enabled since they are incompatible with Java
    // serialization
    if (Boolean.valueOf(Config.get("physiology.generators.enabled", "false"))) {
      System.out.println("Skipping test PersonTest.testSerializationAndDeserialization");
      System.out.println("Set config physiology.generators.enabled=false to enable this test");
      return;
    }
    
    // Generate a filled-out patient record to test on
    Generator.GeneratorOptions opts = new Generator.GeneratorOptions();
    opts.population = 1;
    opts.minAge = 50;
    opts.maxAge = 100;
    Generator generator = new Generator(opts);
    int personSeed = 0;
    Random randomForDemographics = new Random(personSeed);
    Map<String, Object> demoAttributes = generator.randomDemographics(randomForDemographics);
    Person original = generator.createPerson(0, demoAttributes);
    
    Person rehydrated = serializeAndDeserialize(original);
    
    // Compare the original to the serialized+deserialized version
    assertEquals(original.randInt(), rehydrated.randInt());
    assertEquals(original.seed, rehydrated.seed);
    assertEquals(original.populationSeed, rehydrated.populationSeed);
    assertEquals(original.symptoms.keySet(), rehydrated.symptoms.keySet());
    assertEquals(
        original.getOnsetConditionRecord().getSources().keySet(),
        rehydrated.getOnsetConditionRecord().getSources().keySet()
    );
    assertEquals(original.hasMultipleRecords, rehydrated.hasMultipleRecords);
    assertEquals(original.attributes.keySet(), rehydrated.attributes.keySet());
    assertEquals(original.vitalSigns.keySet(), rehydrated.vitalSigns.keySet());
    assertEquals(original.chronicMedications.keySet(), rehydrated.chronicMedications.keySet());
    assertEquals(original.hasMultipleRecords, rehydrated.hasMultipleRecords);
    if (original.hasMultipleRecords) {
      assertEquals(original.records.keySet(), rehydrated.records.keySet());
    }
    assertTrue(Arrays.equals(original.payerHistory, rehydrated.payerHistory));
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
  
  @Test(expected = IllegalArgumentException.class)
  public void testVitalSignNaN() {
    person.setVitalSign(VitalSign.HEIGHT, Double.NaN);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testVitalSignInfinity() {
    person.setVitalSign(VitalSign.HEIGHT, Double.POSITIVE_INFINITY);
  }

  @Test()
  public void testVitalSign() {
    person.setVitalSign(VitalSign.HEIGHT, 6.02);
  }

  @Test()
  public void testPersonRecreationSerialDifferentGenerator() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.text.export", "true");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;

    List<List<String>> fileContents = new ArrayList<>();

    // Generate two patients that should be identical. Switch the output directory since
    // the file names should be identical
    for (int i = 0; i < 2; i++) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());

      Generator generator = new Generator(options);
      generator.generatePerson(0, 42L);
      
      File expectedExportFolder = tempOutputFolder.toPath().resolve("text").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());
    
      // Read the output files
      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".txt")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }
    
    // Check that there are exactly two files
    assertEquals("Expected 2 files, found " + fileContents.size(), 2,
        fileContents.size());

    // Check that the two files are identical
    for (int i = 0; i < fileContents.get(0).size(); i++) {
      assertEquals(fileContents.get(0).get(i), fileContents.get(1).get(i));
    }
  }

  @Test()
  public void testPersonRecreationSerialSameGenerator() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.text.export", "true");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;

    Generator generator = new Generator(options);
    List<List<String>> fileContents = new ArrayList<>();
    
    // Generate two patients that should be identical. Switch the output directory since
    // the file names should be identical
    for (int i = 0; i < 2; i++) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());
      
      generator.generatePerson(0, 42L);

      // Check that the output files exist
      File expectedExportFolder = tempOutputFolder.toPath().resolve("text").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".txt")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }
  }

  @Test()
  public void testPersonFhirR4Recreation() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.fhir.export", "true");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;

    Generator generator = new Generator(options);
    List<List<String>> fileContents = new ArrayList<>();
    
    // Generate two patients that should be identical. Switch the output directory since
    // the file names should be identical
    for (int i = 0; i < 2; i++) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());
      
      generator.generatePerson(0, 42L);

      // Check that the output files exist
      File expectedExportFolder = tempOutputFolder.toPath().resolve("fhir").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".json")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }

    
    // Check that there are exactly two files
    assertEquals("Expected 2 files, found " + fileContents.size(), 2,
        fileContents.size());

    // Check that the two files are identical
    for (int i = 0; i < fileContents.get(0).size(); i++) {
      assertEquals(fileContents.get(0).get(i), fileContents.get(1).get(i));
    }
  }

  @Test()
  public void testPersonFhirSTU3Recreation() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.fhir_stu3.export", "true");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;

    Generator generator = new Generator(options);
    List<List<String>> fileContents = new ArrayList<>();
    
    // Generate two patients that should be identical. Switch the output directory since
    // the file names should be identical
    for (int i = 0; i < 2; i++) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());
      
      generator.generatePerson(0, 42L);

      // Check that the output files exist
      File expectedExportFolder = tempOutputFolder.toPath().resolve("fhir_stu3").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".json")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }

    
    // Check that there are exactly two files
    assertEquals("Expected 2 files, found " + fileContents.size(), 2,
        fileContents.size());

    // Check that the two files are identical
    for (int i = 0; i < fileContents.get(0).size(); i++) {
      assertEquals(fileContents.get(0).get(i), fileContents.get(1).get(i));
    }
  }
  
  @Test()
  public void testPersonFhirDSTU2Recreation() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.fhir_dstu2.export", "true");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;

    Generator generator = new Generator(options);
    List<List<String>> fileContents = new ArrayList<>();
    
    // Generate two patients that should be identical. Switch the output directory since
    // the file names should be identical
    for (int i = 0; i < 2; i++) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());
      
      generator.generatePerson(0, 42L);

      // Check that the output files exist
      File expectedExportFolder = tempOutputFolder.toPath().resolve("fhir_dstu2").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".json")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }

    
    // Check that there are exactly two files
    assertEquals("Expected 2 files, found " + fileContents.size(), 2,
        fileContents.size());

    // Check that the two files are identical
    for (int i = 0; i < fileContents.get(0).size(); i++) {
      assertEquals(fileContents.get(0).get(i), fileContents.get(1).get(i));
    }
  }

  @Test()
  public void testPersonCcdaRecreation() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.ccda.export", "true");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;

    Generator generator = new Generator(options);
    List<List<String>> fileContents = new ArrayList<>();
    
    // Generate two patients that should be identical. Switch the output directory since
    // the file names should be identical
    for (int i = 0; i < 2; i++) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());
      
      generator.generatePerson(0, 42L);

      // Check that the output files exist
      File expectedExportFolder = tempOutputFolder.toPath().resolve("ccda").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".xml")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }

    
    // Check that there are exactly two files
    assertEquals("Expected 2 files, found " + fileContents.size(), 2,
        fileContents.size());

    // Check that the two files are identical
    for (int i = 0; i < fileContents.get(0).size(); i++) {
      assertEquals(fileContents.get(0).get(i), fileContents.get(1).get(i));
    }
  }

  @Test()
  public void testPersonRecreationParallel() throws Exception {
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");

    SimpleDateFormat format = new SimpleDateFormat("YYYYMMDD");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));

    GeneratorOptions options = new GeneratorOptions();
    options.clinicianSeed = 9L;
    options.seed = 9L;
    options.referenceTime = format.parse("20200704").getTime();
    options.overflow = false;
    Generator generator = new Generator(options);

    // Generate the patients...
    List<Future<Person>> generatedPatients = new ArrayList<>(10);
    ExecutorService threadPool = Executors.newFixedThreadPool(8);
    for (int i = 0; i < 10; i++) {
      generatedPatients.add(threadPool.submit(() -> generator.generatePerson(0, 42L)));
    }
    threadPool.shutdown();
    while (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
      /* do nothing */
    }
    
    long endTime = System.currentTimeMillis();
    Config.set("exporter.text.export", "true");
    List<List<String>> fileContents = new ArrayList<>();

    for (Future<Person> fp: generatedPatients) {
      File tempOutputFolder = tempFolder.newFolder();
      Config.set("exporter.baseDirectory", tempOutputFolder.toString());
    
      Person p = fp.get();
      Exporter.export(p, endTime);

      // Check that the output files exist
      File expectedExportFolder = tempOutputFolder.toPath().resolve("text").toFile();
      assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

      for (File txtFile : expectedExportFolder.listFiles()) {
        if (!txtFile.getName().endsWith(".txt")) {
          continue;
        }
        fileContents.add(Files.readAllLines(txtFile.toPath()));
      }
    }
    
    assertEquals("Expected 10 files, found " + fileContents.size(), 10,
        fileContents.size());

    // Check that all files are identical
    for (int f = 1; f < fileContents.size(); f++) {
      for (int l = 0; l < fileContents.get(f).size(); l++) {
        assertEquals(fileContents.get(f - 1).get(l), fileContents.get(f).get(l));
      }
    }
  }

  @Test()
  public void testPersonRandomStability() {
    Person personA = new Person(0L);
    Person personB = new Person(0L);

    double[] doubleRange = { 3.14159d, 42.4233d };
    int[] intRange = { 33, 333 };
    String[] choices = { "foo", "bar", "baz" };
    
    List<String> resultsA = new ArrayList<String>();
    List<String> resultsB = new ArrayList<String>();

    int mode = 0;
    for (int i = 0; i < 1000; i++) {
      if (mode == 0) {
        resultsA.add("" + personA.rand());
        resultsB.add("" + personB.rand());
      } else if (mode == 1) {
        resultsA.add("" + personA.rand(doubleRange));
        resultsB.add("" + personB.rand(doubleRange));
      } else if (mode == 2) {
        resultsA.add("" + personA.rand(intRange));
        resultsB.add("" + personB.rand(intRange));
      } else if (mode == 3) {
        resultsA.add("" + personA.rand(choices));
        resultsB.add("" + personB.rand(choices));
      } else if (mode == 4) {
        resultsA.add("" + personA.rand(101.101d, 333.333d));
        resultsB.add("" + personB.rand(101.101d, 333.333d));
      } else if (mode == 5) {
        resultsA.add("" + personA.rand(42.2345d, 98.7654d, 3));
        resultsB.add("" + personB.rand(42.2345d, 98.7654d, 3));
      } else if (mode == 6) {
        resultsA.add("" + personA.randInt());
        resultsB.add("" + personB.randInt());
      } else if (mode == 7) {
        resultsA.add("" + personA.randInt(3333));
        resultsB.add("" + personB.randInt(3333));
      } else if (mode == 8) {
        resultsA.add("" + personA.randGaussian());
        resultsB.add("" + personB.randGaussian());
      }
      mode += 1;
      if (mode > 8) {
        mode = 0;
      }
    }

    assertEquals(resultsA.size(), resultsB.size());
    for (int i = 0; i < resultsA.size(); i++) {
      assertEquals(resultsA.get(i), resultsB.get(i));
    }
  }
}
